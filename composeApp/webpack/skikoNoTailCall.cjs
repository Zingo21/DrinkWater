// Webpack loader for Skiko's skiko.mjs; webpack.config.d/skikoNoTailCall.js explains why and wires it in.
//
// skiko.mjs exposes every Skia function to the app's WebAssembly as a wrapper that returns the real call:
//   (...a) => (name = loadedWasm._["name"])(...a)
// Safari runs that call as a proper tail call, and Safari 27.2 corrupts floating-point arguments in it once the
// JIT has optimized the caller. A call inside try is never a tail call, so wrapping it there avoids the bug.

const WRAPPER = /\(\.\.\.a\) => \(([A-Za-z0-9_$]+) = loadedWasm\._\["\1"\]\)\(\.\.\.a\)/g;

/** [source] with every wrapper's call moved into a try block, and how many wrappers there were. */
function rewrite(source) {
    let count = 0;
    const result = source.replace(WRAPPER, (match, name) => {
        count++;
        return `(...a) => { try { return (${name} = loadedWasm._["${name}"])(...a); } catch (e) { throw e; } }`;
    });
    return { result, count };
}

module.exports = function skikoNoTailCall(source) {
    const { result, count } = rewrite(source);
    // A Skiko update that writes the wrappers differently would otherwise bring the white page back unnoticed.
    if (count === 0) {
        throw new Error("skikoNoTailCall: found none of Skiko's wrappers in skiko.mjs; see composeApp/webpack.config.d/skikoNoTailCall.js");
    }
    return result;
};

module.exports.rewrite = rewrite;
