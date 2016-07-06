package de.dis2016;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.cfg.Configuration;
import org.hibernate.service.ServiceRegistry;
import org.hibernate.service.ServiceRegistryBuilder;

import java.util.Properties;

/**
 * @author Konstantin Simon Maria Moellers
 * @version 2015-05-04
 */
public class DataWarehouseModule extends AbstractModule {

    @Override
    protected void configure() {
    }

    /**
     * Provides a new Hibernate session.
     */
    @Provides
    Session provideSession(SessionFactory factory) {
        return factory.openSession();
    }

    /**
     * Provides the Hibernate session factory.
     */
    @Provides
    @Singleton
    SessionFactory provideSessionFactory() {
        try {
            Configuration configuration = new Configuration();
            configuration.configure();

            Properties properties = configuration.getProperties();
            ServiceRegistry serviceRegistry = new ServiceRegistryBuilder().applySettings(properties).buildServiceRegistry();

            return configuration.buildSessionFactory(serviceRegistry);
        } catch (Throwable ex) {
            throw new ExceptionInInitializerError(ex);
        }
    }
}
