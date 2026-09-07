/*
 * This file is part of StyleBI.
 * Copyright (C) 2024  InetSoft Technology
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
const gulp = require("gulp");
const concat = require("gulp-concat");
const sass = require("gulp-dart-sass");
const postcss = require("gulp-postcss");
const replace = require("gulp-replace");
const cssnano = require("cssnano");
const glob = require("glob");
const path = require("path");
const requireNonEmptyStream = require("./require-non-empty-stream");
const { verifyClassicScriptBundle } = require("./verify-classic-script");
const { rebundleMainForClassicScript } = require("./esbuild-classic-script-rebundle");

// The esbuild @angular/build:application builder (in use for this project since the
// Angular 17->18/esbuild migration) hashes output filenames as "<name>-<HASH>.js" and does
// not emit a separate runtime chunk -- unlike the older Webpack builder's "<name>.<hash>.js"
// convention these patterns used to target.
const ngOutputDir = "target/generated-resources/ng/inetsoft/web/resources/elements";
const rebundleDir = "target/generated-resources/gulp/tmp/elements";
const appDir = "target/generated-resources/gulp/inetsoft/web/resources/app";

const scriptFiles = [
   path.join(ngOutputDir, "polyfills-*.js"),
   path.join(ngOutputDir, "scripts-*.js"),
   // Bug #76473: this is the esbuild-rebundled (format=iife, import.meta.url captured via
   // ESM_SCRIPT_URL_BANNER) copy of Angular's main-*.js, not the raw ng build output -- see
   // esbuild-classic-script-rebundle.js and docs/teams/2026-09-04-bug-76473/02b-fix-approach-decision.md.
   path.join(rebundleDir, "main-*.js")
];

const cssFiles = [
   "target/generated-resources/gulp/inetsoft/web/resources/app/global.css",
   "target/generated-resources/ng/inetsoft/web/resources/elements/styles-*.css"
];

// Bug #76473, piece 1+2: Angular's built main-*.js contains a literal import.meta.url
// reference (HeartbeatWorkerService's Worker construction, reached via StompClientService),
// which is a parse-time SyntaxError once concatenated into a classic (non-module) <script>.
// Re-bundling it with esbuild --format=iife removes the literal import.meta, but only the
// banner/define pair below makes the substitution a real, working script URL instead of
// esbuild's silent empty-object stub (which would make the Worker construction throw a
// caught TypeError and permanently, invisibly degrade to the setInterval fallback -- see
// docs/teams/2026-09-04-bug-76473/01-hypothesis-esbuild-rebundle.md and 02b-fix-approach-decision.md).
gulp.task("elements:scripts:bundle-main", function (callback) {
   rebundleMainForClassicScript(ngOutputDir, rebundleDir);
   callback();
});

// Bug #76473, piece 3: worker-*.js (Angular's own build output) must be a sibling of the
// concatenated elements.js at serve time, since the rebundled main-*.js resolves the
// Worker's URL relative to elements.js's own script URL (document.currentScript.src,
// captured by ESM_SCRIPT_URL_BANNER). It is not a sibling today -- it lands under
// .../resources/elements/ (a separate, unmapped Maven <resource> root from
// .../resources/app/, per web/pom.xml, and for this project specifically that root is also
// excluded entirely from the packaged jar by the maven-jar-plugin config in the same pom.xml).
gulp.task("elements:scripts:copy-worker", function () {
   const workerFiles = glob.sync(path.join(ngOutputDir, "worker-*.js"));

   if(workerFiles.length === 0) {
      return Promise.reject(new Error(
         `elements:scripts:copy-worker found no worker-*.js in ${ngOutputDir} -- ` +
         "expected Angular's production build to emit HeartbeatWorkerService's worker chunk."));
   }

   return gulp.src(workerFiles)
      .pipe(gulp.dest(appDir));
});

gulp.task("elements:scripts:concat", function () {
   return gulp.src(scriptFiles)
      .pipe(requireNonEmptyStream("elements:scripts"))
      .pipe(concat("elements.js"))
      .pipe(gulp.dest(appDir));
});

// Bug #76473: fail the build loudly if the concatenated bundle regresses back to being
// unparseable as a classic script, or silently degrades via esbuild's import.meta stub --
// same discipline as requireNonEmptyStream() above (added for Bug #76468), for a different
// check.
gulp.task("elements:scripts:verify", function (callback) {
   verifyClassicScriptBundle(path.join(appDir, "elements.js"));
   callback();
});

gulp.task("elements:scripts", gulp.series([
   "elements:scripts:bundle-main",
   "elements:scripts:copy-worker",
   "elements:scripts:concat",
   "elements:scripts:verify"
]));

gulp.task("elements:concat-css", function () {
   return gulp.src(cssFiles)
      .pipe(concat("_concat-elements.scss"))
      .pipe(replace(/(@font-face[^}]*})/g, "@at-root{$1}"))
      .pipe(gulp.dest("target/generated-resources/gulp/inetsoft/web/resources/elements/"));
});

gulp.task("elements:sass", function () {
   return gulp.src("projects/portal/src/elements.scss")
      .pipe(sass())
      .pipe(replace("inetsoft-chart :root", "inetsoft-chart"))
      .pipe(replace("inetsoft-chart body", "inetsoft-chart"))
      .pipe(postcss([cssnano()]))
      .pipe(gulp.dest("target/generated-resources/gulp/inetsoft/web/resources/app/"));
});

gulp.task("elements:css", gulp.series(["elements:concat-css", "elements:sass"]));
