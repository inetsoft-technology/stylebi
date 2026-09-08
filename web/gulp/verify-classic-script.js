/*
 * This file is part of StyleBI.
 * Copyright (C) 2026  InetSoft Technology
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
const fs = require("fs");

// Bug #76473: elements.js/viewer-element.js are gulp-concatenated (plain text
// concatenation, no bundler pass) from Angular's production build output, and are meant to be
// loaded as a classic (non-module) <script src="..."> tag by third-party embed pages. Any
// ESM-only construct that survives into the concatenated output (e.g. import.meta, used by
// HeartbeatWorkerService's Worker construction) is a parse-time SyntaxError in that context,
// regardless of whether the code path that uses it is ever reached at runtime.
//
// Deliberately does NOT use `node --check`: community/web/package.json has "type": "module",
// so Node resolves these *.js files as ES modules by default, and import.meta is legal in a
// module -- `node --check` gives a false pass on exactly the bug this function exists to
// catch. new Function(source) forces true classic-script (non-module) parse semantics, which
// is the only reliable local check found so far (see docs/teams/2026-09-04-bug-76473/00-map.md).
function assertClassicScriptParses(filePath) {
   const source = fs.readFileSync(filePath, "utf8");

   try {
      new Function(source);
   }
   catch(e) {
      throw new Error(
         `${filePath} does not parse as a classic (non-module) script: ${e.name}: ${e.message}\n` +
         "This file is loaded via <script src=\"...\"> (not type=\"module\"), so any ESM-only " +
         "construct (import.meta, a top-level import/export statement) is a parse-time failure " +
         "here even if the code path that uses it is never reached.");
   }

   return source;
}

// Bug #76473 fix-approach trap (see docs/teams/2026-09-04-bug-76473/02b-fix-approach-decision.md
// and 01-hypothesis-esbuild-rebundle.md): a naive `esbuild --bundle --format=iife` re-bundle
// pass makes assertClassicScriptParses() above pass -- the parse-time SyntaxError is gone --
// but only because esbuild silently substitutes an empty-object stub for the now-unsupported
// import.meta ("var import_meta = {};", with every import.meta.url read site rewritten to
// import_meta.url instead). That stub makes HeartbeatWorkerService's
// `new Worker(new URL(..., import.meta.url))` throw a TypeError at runtime, caught by the same
// try/catch that already guards "Worker unavailable", so the Worker silently and permanently
// degrades to the setInterval heartbeat fallback -- no build error, no parse error, nothing to
// notice it by.
//
// This check is NOT redundant with assertClassicScriptParses(): that check only asks "does this
// parse," which a naive/incomplete fix satisfies while still being wrong. This check asks a
// different question -- "did esbuild quietly paper over an import.meta it couldn't actually
// support" -- by looking for the specific fingerprint esbuild leaves behind when it does. A
// correct fix (banner/define capturing the script's own URL synchronously, per
// 01-hypothesis-esbuild-rebundle.md) does not leave this fingerprint, because import.meta.url is
// substituted with a real expression instead of an empty stub.
function assertNoEsbuildImportMetaStub(filePath, source) {
   source = source === undefined ? fs.readFileSync(filePath, "utf8") : source;

   // esbuild's stub var is named "import_meta", suffixed ("import_meta2", ...) only if the
   // bundle already has a colliding identifier -- \d* covers that without being tied to a
   // specific suffix.
   const stubDeclaration = /\bimport_meta\d*\s*=\s*\{\}/;
   const stubUsage = /\bimport_meta\d*\.url\b/;

   if(stubDeclaration.test(source) && stubUsage.test(source)) {
      throw new Error(
         `${filePath} contains esbuild's silent import.meta stub substitution ` +
         "(an \"import_meta = {}\" declaration together with an \"import_meta.url\" read site). " +
         "The file parses as a classic script, but any code that read import.meta.url now reads " +
         "undefined instead -- e.g. HeartbeatWorkerService's " +
         "`new Worker(new URL(..., import.meta.url))` will throw a TypeError that silently " +
         "degrades to a fallback, with no build or parse error to notice it by. See " +
         "docs/teams/2026-09-04-bug-76473/02b-fix-approach-decision.md.");
   }
}

// Runs both checks. Throws on the first failure with a message identifying which check failed
// and why. Intended to be callable from a gulp task (let the throw/rejection fail the task) or
// from the CLI entry point below (e.g. from a CI step).
function verifyClassicScriptBundle(filePath) {
   const source = assertClassicScriptParses(filePath);
   assertNoEsbuildImportMetaStub(filePath, source);
}

module.exports = {
   assertClassicScriptParses,
   assertNoEsbuildImportMetaStub,
   verifyClassicScriptBundle
};

if(require.main === module) {
   const target = process.argv[2];

   if(!target) {
      console.error("Usage: node verify-classic-script.js <path-to-built-bundle.js>");
      process.exit(2);
   }

   try {
      verifyClassicScriptBundle(target);
      console.log(`OK: ${target} parses as a classic script and contains no import.meta stub.`);
   }
   catch(e) {
      console.error(`FAIL: ${e.message}`);
      process.exit(1);
   }
}
