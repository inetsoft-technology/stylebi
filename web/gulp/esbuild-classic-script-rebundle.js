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
const path = require("path");
const glob = require("glob");
const esbuild = require("esbuild");

// Bug #76473: Angular's production build of the elements/viewer-element projects emits a
// main-*.js that contains a literal import.meta.url reference (HeartbeatWorkerService's
// `new Worker(new URL("worker-<HASH>.js", import.meta.url))`, reached via StompClientService --
// see docs/teams/2026-09-04-bug-76473/00-map.md and 02-root-cause.md). That is correct code for
// an ES module, but a parse-time SyntaxError once gulp-concat'd into the classic (non-module)
// <script src="..."> these two projects' bundles are meant to be loaded as.
//
// A plain `esbuild --bundle --format=iife` pass over main-*.js does make the SyntaxError go
// away, but silently: esbuild substitutes an empty-object stub for import.meta
// ("var import_meta = {}", every import.meta.url read site rewritten to import_meta.url), which
// makes the Worker construction throw a caught TypeError and permanently, invisibly degrade to
// the setInterval heartbeat fallback -- no build or parse error to notice it by. This banner
// captures the classic script's own URL synchronously (the one moment document.currentScript is
// guaranteed non-null, per MDN) into a plain variable, and --define substitutes every
// import.meta.url reference with that variable instead of esbuild's stub, so the Worker
// construction resolves to a real, correct URL at runtime. Demonstrated working end-to-end
// (both the naive stub trap and this fix) in
// docs/teams/2026-09-04-bug-76473/01-hypothesis-esbuild-rebundle.md -- this is that exact
// invocation, wired into a real gulp task instead of an ad hoc CLI command.
const ESM_SCRIPT_URL_BANNER =
   "var __esm_script_url = (typeof document!==\"undefined\" && document.currentScript) " +
   "? document.currentScript.src : undefined;";

// Resolves the real, hashed main-*.js in ngOutputDir (Angular's production build output for
// the elements/viewer-element project) and re-bundles it in place into rebundleDir, under the
// same filename, so callers concatenating scripts can glob for "main-*.js" in rebundleDir
// exactly as they previously did against ngOutputDir directly.
function rebundleMainForClassicScript(ngOutputDir, rebundleDir) {
   const matches = glob.sync(path.join(ngOutputDir, "main-*.js"));

   if(matches.length !== 1) {
      throw new Error(
         `Expected exactly one main-*.js in ${ngOutputDir}, found ${matches.length} ` +
         `(${matches.join(", ")}) -- check the Angular production build actually ran and its ` +
         "output filename convention hasn't changed.");
   }

   const mainFile = matches[0];
   const outFile = path.join(rebundleDir, path.basename(mainFile));
   fs.mkdirSync(rebundleDir, { recursive: true });

   esbuild.buildSync({
      entryPoints: [mainFile],
      bundle: true,
      format: "iife",
      outfile: outFile,
      allowOverwrite: true,
      banner: { js: ESM_SCRIPT_URL_BANNER },
      define: { "import.meta.url": "__esm_script_url" }
   });

   return outFile;
}

module.exports = {
   ESM_SCRIPT_URL_BANNER,
   rebundleMainForClassicScript
};
