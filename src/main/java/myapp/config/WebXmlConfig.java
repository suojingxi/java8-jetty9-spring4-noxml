package myapp.config;

import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.ServletRegistration;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.WebApplicationInitializer;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.servlet.DispatcherServlet;

@Configuration
public class WebXmlConfig implements WebApplicationInitializer {

    @Override
    public void onStartup( ServletContext servletContext ) throws ServletException {
        AnnotationConfigWebApplicationContext appContext = new AnnotationConfigWebApplicationContext();
        registerWebContext( appContext, servletContext );
    }

    void registerWebContext( AnnotationConfigWebApplicationContext appContext, ServletContext servletContext ) {
        appContext.scan( "myapp" );
        ServletRegistration.Dynamic dispatcher = servletContext.addServlet( "myservlet", new DispatcherServlet( appContext ) );
        dispatcher.setLoadOnStartup( 1 );
        dispatcher.addMapping( "/" );
    }

}
