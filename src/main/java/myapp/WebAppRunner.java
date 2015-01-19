package myapp;

import java.net.URL;
import java.security.ProtectionDomain;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import javax.servlet.ServletContainerInitializer;

import org.eclipse.jetty.annotations.AnnotationConfiguration;
import org.eclipse.jetty.annotations.AnnotationDecorator;
import org.eclipse.jetty.annotations.WebFilterAnnotationHandler;
import org.eclipse.jetty.annotations.WebListenerAnnotationHandler;
import org.eclipse.jetty.annotations.WebServletAnnotationHandler;
import org.eclipse.jetty.plus.annotation.ContainerInitializer;
import org.eclipse.jetty.plus.webapp.PlusConfiguration;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.util.log.Log;
import org.eclipse.jetty.util.log.Logger;
import org.eclipse.jetty.util.resource.Resource;
import org.eclipse.jetty.webapp.Configuration;
import org.eclipse.jetty.webapp.JettyWebXmlConfiguration;
import org.eclipse.jetty.webapp.MetaInfConfiguration;
import org.eclipse.jetty.webapp.WebAppContext;
import org.eclipse.jetty.webapp.WebInfConfiguration;
import org.eclipse.jetty.webapp.WebXmlConfiguration;


public class WebAppRunner {

    private static final Logger LOG = Log.getLogger( WebAppRunner.class );

    public static void main( String[] args ) {
        new WebAppRunner().start();
    }

    private void start() {

        ProtectionDomain domain = WebAppRunner.class.getProtectionDomain();
        URL location = domain.getCodeSource().getLocation();

        WebAppContext context = new WebAppContext();
        context.setContextPath( "/" );
        context.setWar( location.toExternalForm() );
        context.setParentLoaderPriority( true );
        context.setConfigurations( new Configuration[] { 
                        new MyAnnotationConfiguration(),
                        new WebInfConfiguration(), 
                        new WebXmlConfiguration(),
                        new MetaInfConfiguration(),
                        new PlusConfiguration(), 
                        new JettyWebXmlConfiguration() 
        } );

        Server server = new Server( 8080 );
        server.dumpStdErr();
        server.setHandler( context );
        try {
            server.start();
            server.join();
        }
        catch ( Exception e ) {
            LOG.warn( e );
        }
    }

    public static class MyAnnotationConfiguration extends AnnotationConfiguration {

        @Override
        public void configure( WebAppContext context ) throws Exception {
            context.addDecorator( new AnnotationDecorator( context ) );

            // Even if metadata is complete, we still need to scan for ServletContainerInitializers - if there are any

            if ( !context.getMetaData().isMetaDataComplete() ) {
                // If metadata isn't complete, if this is a servlet 3 webapp or isConfigDiscovered is true, we need to
                // search for annotations
                if ( context.getServletContext().getEffectiveMajorVersion() >= 3 || context.isConfigurationDiscovered() ) {
                    _discoverableAnnotationHandlers.add( new WebServletAnnotationHandler( context ) );
                    _discoverableAnnotationHandlers.add( new WebFilterAnnotationHandler( context ) );
                    _discoverableAnnotationHandlers.add( new WebListenerAnnotationHandler( context ) );
                }
            }

            // Regardless of metadata, if there are any ServletContainerInitializers with @HandlesTypes, then we need to
            // scan all the
            // classes so we can call their onStartup() methods correctly
            List<ServletContainerInitializer> nonExcludedInitializers = getNonExcludedInitializers( context );
            LOG.info( "HERE >>> Found {} nonExcludedInitializers", nonExcludedInitializers.size() );
            createServletContainerInitializerAnnotationHandlers( context, nonExcludedInitializers );

            if ( !_discoverableAnnotationHandlers.isEmpty() || _classInheritanceHandler != null
                            || !_containerInitializerAnnotationHandlers.isEmpty() )
                scanForAnnotations( context );

            // Resolve container initializers
            List<ContainerInitializer> initializers = (List<ContainerInitializer>)context
                            .getAttribute( AnnotationConfiguration.CONTAINER_INITIALIZERS );
            if ( initializers != null && initializers.size() > 0 ) {
                Map<String, Set<String>> map = (Map<String, Set<String>>)context
                                .getAttribute( AnnotationConfiguration.CLASS_INHERITANCE_MAP );
                if ( map == null )
                    LOG.warn( "ServletContainerInitializers: detected. Class hierarchy: empty" );
                for ( ContainerInitializer i : initializers )
                    i.resolveClasses( context, map );
            }
        }

        @Override
        public List<ServletContainerInitializer> getNonExcludedInitializers( WebAppContext context ) throws Exception {
            ArrayList<ServletContainerInitializer> nonExcludedInitializers = new ArrayList<ServletContainerInitializer>();

            // We use the ServiceLoader mechanism to find the ServletContainerInitializer classes to inspect
            long start = 0;

            ClassLoader old = Thread.currentThread().getContextClassLoader();
            ServiceLoader<ServletContainerInitializer> loadedInitializers = null;
            try {
                if ( LOG.isDebugEnabled() )
                    start = System.nanoTime();
                Thread.currentThread().setContextClassLoader( context.getClassLoader() );
                loadedInitializers = ServiceLoader.load( ServletContainerInitializer.class );
            }
            finally {
                Thread.currentThread().setContextClassLoader( old );
            }

            LOG.info( "HERE >>> ServletContainerInitializer found: {}", loadedInitializers.iterator().hasNext() );

            if ( LOG.isDebugEnabled() )
                LOG.debug( "Service loaders found in {}ms",
                                ( TimeUnit.MILLISECONDS.convert( ( System.nanoTime() - start ), TimeUnit.NANOSECONDS ) ) );

            Map<ServletContainerInitializer, Resource> sciResourceMap = new HashMap<ServletContainerInitializer, Resource>();
            ServletContainerInitializerOrdering initializerOrdering = getInitializerOrdering( context );

            // Get initial set of SCIs that aren't from excluded jars or excluded by the containerExclusionPattern, or
            // excluded
            // because containerInitializerOrdering omits it
            for ( ServletContainerInitializer sci : loadedInitializers ) {
                if ( matchesExclusionPattern( sci ) ) {
                    if ( LOG.isDebugEnabled() )
                        LOG.debug( "{} excluded by pattern", sci );
                    continue;
                }

                Resource sciResource = getJarFor( sci );
                if ( isFromExcludedJar( context, sci, sciResource ) ) {
                    if ( LOG.isDebugEnabled() )
                        LOG.debug( "{} is from excluded jar", sci );
                    continue;
                }

                // check containerInitializerOrdering doesn't exclude it
                String name = sci.getClass().getName();
                if ( initializerOrdering != null
                                && ( !initializerOrdering.hasWildcard() && initializerOrdering.getIndexOf( name ) < 0 ) ) {
                    if ( LOG.isDebugEnabled() )
                        LOG.debug( "{} is excluded by ordering", sci );
                    continue;
                }

                sciResourceMap.put( sci, sciResource );
            }

            // Order the SCIs that are included
            if ( initializerOrdering != null && !initializerOrdering.isDefaultOrder() ) {
                if ( LOG.isDebugEnabled() )
                    LOG.debug( "Ordering ServletContainerInitializers with " + initializerOrdering );

                // There is an ordering that is not just "*".
                // Arrange ServletContainerInitializers according to the ordering of classnames given, irrespective of
                // coming from container or webapp classpaths
                nonExcludedInitializers.addAll( sciResourceMap.keySet() );
                Collections.sort( nonExcludedInitializers, new ServletContainerInitializerComparator(
                                initializerOrdering ) );
            }
            else {
                // No jetty-specific ordering specified, or just the wildcard value "*" specified.
                // Fallback to ordering the ServletContainerInitializers according to:
                // container classpath first, WEB-INF/classes then WEB-INF/lib (obeying any web.xml jar ordering)

                // no web.xml ordering defined, add SCIs in any order
                if ( context.getMetaData().getOrdering() == null ) {
                    if ( LOG.isDebugEnabled() )
                        LOG.debug( "No web.xml ordering, ServletContainerInitializers in random order" );
                    nonExcludedInitializers.addAll( sciResourceMap.keySet() );
                }
                else {
                    if ( LOG.isDebugEnabled() )
                        LOG.debug( "Ordering ServletContainerInitializers with ordering {}", context.getMetaData()
                                        .getOrdering() );
                    for ( Map.Entry<ServletContainerInitializer, Resource> entry : sciResourceMap.entrySet() ) {
                        // add in SCIs from the container classpath
                        if ( entry.getKey().getClass().getClassLoader() == context.getClassLoader().getParent() )
                            nonExcludedInitializers.add( entry.getKey() );
                        else if ( entry.getValue() == null ) // add in SCIs not in a jar, as they must be from
                                                             // WEB-INF/classes and can't be ordered
                            nonExcludedInitializers.add( entry.getKey() );
                    }

                    // add SCIs according to the ordering of its containing jar
                    for ( Resource webInfJar : context.getMetaData().getOrderedWebInfJars() ) {
                        for ( Map.Entry<ServletContainerInitializer, Resource> entry : sciResourceMap.entrySet() ) {
                            if ( webInfJar.equals( entry.getValue() ) )
                                nonExcludedInitializers.add( entry.getKey() );
                        }
                    }
                }
            }

            if ( LOG.isDebugEnabled() ) {
                int i = 0;
                for ( ServletContainerInitializer sci : nonExcludedInitializers )
                    LOG.debug( "ServletContainerInitializer: {} {}", ( ++i ), sci.getClass().getName() );
            }
            return nonExcludedInitializers;
        }

    }

}
