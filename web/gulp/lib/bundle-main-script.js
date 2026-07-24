/*
 * This file is part of StyleBI.
 * Copyright (C) 2025  InetSoft Technology
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
const glob = require("glob");
const esbuild = require("esbuild");
const through = require("through2");

/**
 * Re-bundles the Angular esbuild "application" builder's main-*.js with esbuild so that
 * any chunk-*.js it imports gets inlined into a single self-contained script.
 *
 * The application builder splits the entry point into main-*.js plus one or more
 * chunk-*.js files whenever anything reachable from it - even transitively, through a
 * dynamically-imported dialog/component that the embed elements never actually navigate
 * to - contains an `import()`. Those chunks are linked from main-*.js via native ES module
 * `import` statements. The embed package's runtime loader (loader.mjs) loads elements.js /
 * viewer-element.js as a single `<script type="module">`, so a leftover
 * `import ... from "./chunk-XXXX.js"` 404s once the file is copied into the published npm
 * package without its sibling chunks. That is a parse-time SyntaxError for the whole
 * module, which silently prevents every customElements.define() call in the file from
 * ever running - the custom elements (e.g. inetsoft-chart) are left undefined and render
 * as empty space.
 *
 * @param {string} ngOutputDir directory containing the `ng build` output (main-*.js and
 *    any chunk-*.js it depends on)
 * @returns {string} self-contained IIFE script text with no unresolved local imports
 */
function bundleMainScript(ngOutputDir) {
   const mainFiles = glob.sync(`${ngOutputDir}/main-*.js`);

   if(mainFiles.length === 0) {
      throw new Error(`No main-*.js found in ${ngOutputDir}. Run the corresponding "ng build" first.`);
   }

   if(mainFiles.length > 1) {
      throw new Error(
         `Found multiple main-*.js files in ${ngOutputDir}: ${mainFiles.join(", ")}. ` +
         "Clean the output directory and rebuild so a stale file isn't bundled by mistake."
      );
   }

   const result = esbuild.buildSync({
      entryPoints: mainFiles,
      bundle: true,
      // Bundled as ESM (no top-level export in an application entry point, so this comes
      // out as plain statements) rather than esbuild's own "iife" format, so it can be
      // wrapped below with the same `.call(window)` binding as wrapInIIFE - keeping the
      // "this" semantics consistent with the rest of elements.js/viewer-element.js in case
      // anything transitively bundled here relies on `this` pointing to the global object
      // (e.g. UMD-style code).
      format: "esm",
      platform: "browser",
      write: false,
      // angular.json sets extractLicenses: false for these builds, i.e. license comments
      // are meant to stay inline rather than be extracted separately - match that here so
      // third-party attribution notices aren't silently dropped from the published package.
      legalComments: "inline"
   });

   return `(function(){${result.outputFiles[0].text}}).call(window);\n`;
}

/**
 * Appends raw text after a vinyl stream's (already concatenated) file contents, without
 * wrapping it in another IIFE. Used to splice in output from {@link bundleMainScript},
 * which is already self-contained.
 * @param {string} text
 */
function appendText(text) {
   return through.obj(function(file, encoding, callback) {
      if(file.isBuffer()) {
         file.contents = Buffer.concat([ file.contents, Buffer.from(text, encoding) ]);
      }

      callback(null, file);
   });
}

module.exports = { bundleMainScript, appendText };
