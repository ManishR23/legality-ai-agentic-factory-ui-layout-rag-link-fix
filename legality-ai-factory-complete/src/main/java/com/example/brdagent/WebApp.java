package com.example.brdagent;

import com.example.brdagent.web.FactoryHttpServer;
import com.sun.net.httpserver.HttpServer;

import java.nio.file.Path;

/**
 * Local web server entry point for the factory pipeline -- an alternative to BrdAgentApp's
 * Swing UI, driving the same backend classes over a JSON API. Run from
 * legality-ai-factory-complete/ so .env and frontend/ resolve relative to the working
 * directory, same as BrdAgentApp.
 */
public class WebApp {
    public static void main(String[] args) throws Exception {
        try {
            JacksonRuntimeCheck.assertCompatible();
            System.out.println(JacksonRuntimeCheck.versionSummary());
        } catch (Exception ex) {
            ex.printStackTrace();
            System.exit(1);
        }
        int port = args.length > 0 ? Integer.parseInt(args[0]) : 8787;
        Path frontendRoot = Path.of("frontend").toAbsolutePath().normalize();
        if (!java.nio.file.Files.isDirectory(frontendRoot)) {
            System.err.println("frontend/ directory not found at " + frontendRoot + " -- run from legality-ai-factory-complete/");
            System.exit(1);
        }
        HttpServer server = new FactoryHttpServer(frontendRoot).start(port);
        System.out.println("Crew Legality Console running at http://localhost:" + server.getAddress().getPort());
        System.out.println("Serving frontend from " + frontendRoot);
    }
}
