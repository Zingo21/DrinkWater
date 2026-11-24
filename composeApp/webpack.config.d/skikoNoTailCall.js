// Safari 27.2 (iOS and macOS) shows the web app as a blank white page and reports no error: once its JIT has
// optimized a caller, floating-point arguments passed through a tail call can arrive as NaN (WebKit bug 329421,
// JetBrains CMP-10986), and Skiko passes every Skia call through such a tail call, so Compose measures and draws
// garbage. The loader in composeApp/webpack/skikoNoTailCall.cjs moves those calls out of tail position. WebKit has
// fixed the bug; remove this file and the loader once Safari releases with the fix are what people run.
(function () {
    const path = require("path");
    const fs = require("fs");

    // This file is copied into the generated webpack config under the build directory; find the loader from there.
    let loader = null;
    for (let dir = __dirname; ; dir = path.dirname(dir)) {
        const candidate = path.join(dir, "composeApp", "webpack", "skikoNoTailCall.cjs");
        if (fs.existsSync(candidate)) {
            loader = candidate;
            break;
        }
        if (path.dirname(dir) === dir) throw new Error("skikoNoTailCall: can't find composeApp/webpack/skikoNoTailCall.cjs");
    }

    config.module.rules.push({ test: /[\\/]skiko\.mjs$/, use: [loader] });

    // Fails the build if a tail-call wrapper survived into the output, unminified or minified, e.g. because the
    // minifier turned the rewritten wrappers back into tail calls.
    const tailCallWrappers = [
        /\(\.\.\.a\) => \([A-Za-z0-9_$]+ = loadedWasm\._\[/,
        /=>\([A-Za-z0-9_$]+=[A-Za-z0-9_$]+\._(?:\.[A-Za-z0-9_$]+|\["[^"]+"\])\)\(\.\.\.[A-Za-z0-9_$]+\)/,
    ];
    config.plugins.push({
        apply(compiler) {
            const { Compilation, WebpackError } = compiler.webpack;
            compiler.hooks.thisCompilation.tap("SkikoNoTailCall", (compilation) => {
                compilation.hooks.processAssets.tap(
                    { name: "SkikoNoTailCall", stage: Compilation.PROCESS_ASSETS_STAGE_ANALYSE },
                    (assets) => {
                        for (const [name, asset] of Object.entries(assets)) {
                            if (!/\.m?js$/.test(name)) continue;
                            const code = asset.source().toString();
                            if (tailCallWrappers.some((pattern) => pattern.test(code))) {
                                compilation.errors.push(new WebpackError(
                                    `skikoNoTailCall: ${name} still calls Skia through a tail call, which leaves Safari 27.2 ` +
                                    "with a white page; see composeApp/webpack.config.d/skikoNoTailCall.js",
                                ));
                            }
                        }
                    },
                );
            });
        },
    });
})();
