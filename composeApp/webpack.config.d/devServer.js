// Dev server for `./gradlew :composeApp:wasmJsBrowserDevelopmentRun`, on :8081 so it doesn't clash with
// the DrinkWater server on :8080. The web app talks to its own origin, so every request that isn't for one
// of the app's own files is passed on to the server, WebSockets included.
if (config.devServer) {
    config.devServer.port = 8081;
    config.devServer.proxy = [{
        // "/ws" is the dev server's own live-reload socket.
        context: (path) => path !== "/" && path !== "/ws" &&
            !/\.(html|js|mjs|wasm|map|css|png|ico|svg|json|webmanifest)$/.test(path),
        target: "http://localhost:8080",
        ws: true,
    }];
}
