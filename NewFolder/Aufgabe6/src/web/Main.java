package web;

import com.google.inject.Guice;
import com.google.inject.Injector;
import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.Server;


public class Main {

    public static void main(String[] args) throws Exception {
        // Start Dependency Injection
        Injector injector = Guice.createInjector(new MongoModule(), new RestServerModule());

        // Resolve server instance
        Server server = injector.getInstance(Server.class);

        // Resolve server handler
        server.setHandler(injector.getInstance(Handler.class));

        // Start server
        server.start();
        server.join();
    }
}
