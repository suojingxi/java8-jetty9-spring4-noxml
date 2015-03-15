package myapp;

import java.net.URL;
import java.security.ProtectionDomain;

import org.eclipse.jetty.annotations.AnnotationConfiguration;
import org.eclipse.jetty.plus.webapp.PlusConfiguration;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.util.log.Log;
import org.eclipse.jetty.util.log.Logger;
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
                        new WebInfConfiguration(), 
                        new WebXmlConfiguration(),
                        new MetaInfConfiguration(),
                        new PlusConfiguration(), 
                        new JettyWebXmlConfiguration(),
                        new AnnotationConfiguration()
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

}
