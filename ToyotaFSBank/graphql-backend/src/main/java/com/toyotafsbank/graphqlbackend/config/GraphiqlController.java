package com.toyotafsbank.graphqlbackend.config;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Replaces Spring Boot's built-in GraphiQL page (spring.graphql.graphiql.enabled=false
 * in application.yml) with one served from locally vendored assets under
 * static/vendor/graphiql/, so it works without reaching any external CDN at
 * request time. Boot's own page hardcodes unpkg.com URLs with no override
 * property, and unpkg has intermittent outages (same fix as spring-graphql-app).
 */
@RestController
public class GraphiqlController {

    private static final String PAGE = """
            <!DOCTYPE html>
            <html lang="en">
            <head>
                <title>GraphiQL</title>
                <style>
                    body { height: 100%; margin: 0; width: 100%; overflow: hidden; }
                    #graphiql { height: 100vh; }
                </style>
                <script src="/vendor/graphiql/react.production.min.js" crossorigin></script>
                <script src="/vendor/graphiql/react-dom.production.min.js" crossorigin></script>
                <script src="/vendor/graphiql/graphiql.min.js" type="application/javascript"></script>
                <link rel="stylesheet" href="/vendor/graphiql/graphiql.min.css" />
                <script src="/vendor/graphiql/plugin-explorer.umd.js" crossorigin></script>
                <link rel="stylesheet" href="/vendor/graphiql/plugin-explorer.css"/>
            </head>
            <body>
            <div id="graphiql">Loading...</div>
            <script>
                const params = new URLSearchParams(window.location.search);
                const path = params.get("path") || "/graphql";
                const url = `${location.protocol}//${location.host}${path}`;
                const gqlFetcher = GraphiQL.createFetcher({ url: url });
                const explorerPlugin = GraphiQLPluginExplorer.explorerPlugin();
                ReactDOM.render(
                    React.createElement(GraphiQL, {
                        fetcher: gqlFetcher,
                        defaultVariableEditorOpen: true,
                        headerEditorEnabled: true,
                        shouldPersistHeaders: true,
                        plugins: [explorerPlugin]
                    }),
                    document.getElementById("graphiql"),
                );
            </script>
            </body>
            </html>
            """;

    @GetMapping(value = "/graphiql", produces = MediaType.TEXT_HTML_VALUE)
    public String graphiql() {
        return PAGE;
    }
}
