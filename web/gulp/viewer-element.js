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
const ngOutputDir = "target/generated-resources/ng/inetsoft/web/resources/viewer-element";
const rebundleDir = "target/generated-resources/gulp/tmp/viewer-element";
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
   "target/generated-resources/ng/inetsoft/web/resources/viewer-element/styles-*.css"
];

// Bug #76473, piece 1+2 -- see elements.js's identical task for the full explanation.
gulp.task("viewer-element:scripts:bundle-main", function (callback) {
   rebundleMainForClassicScript(ngOutputDir, rebundleDir);
   callback();
});

// Bug #76473, piece 3 -- see elements.js's identical task for the full explanation.
gulp.task("viewer-element:scripts:copy-worker", function () {
   const workerFiles = glob.sync(path.join(ngOutputDir, "worker-*.js"));

   if(workerFiles.length === 0) {
      return Promise.reject(new Error(
         `viewer-element:scripts:copy-worker found no worker-*.js in ${ngOutputDir} -- ` +
         "expected Angular's production build to emit HeartbeatWorkerService's worker chunk."));
   }

   return gulp.src(workerFiles)
      .pipe(gulp.dest(appDir));
});

gulp.task("viewer-element:scripts:concat", function () {
   return gulp.src(scriptFiles)
      .pipe(requireNonEmptyStream("viewer-element:scripts"))
      .pipe(concat("viewer-element.js"))
      .pipe(gulp.dest(appDir));
});

// Bug #76473: fail the build loudly if the concatenated bundle regresses back to being
// unparseable as a classic script, or silently degrades via esbuild's import.meta stub --
// same discipline as requireNonEmptyStream() above (added for Bug #76468), for a different
// check.
gulp.task("viewer-element:scripts:verify", function (callback) {
   verifyClassicScriptBundle(path.join(appDir, "viewer-element.js"));
   callback();
});

gulp.task("viewer-element:scripts", gulp.series([
   "viewer-element:scripts:bundle-main",
   "viewer-element:scripts:copy-worker",
   "viewer-element:scripts:concat",
   "viewer-element:scripts:verify"
]));

gulp.task("viewer-element:concat-css", function () {
   return gulp.src(cssFiles)
      .pipe(concat("_concat-viewer-element.scss"))
      .pipe(replace(/(@font-face[^}]*})/g, "@at-root{$1}"))
      .pipe(gulp.dest("target/generated-resources/gulp/inetsoft/web/resources/viewer-element/"));
});

gulp.task("viewer-element:sass", function () {
   return gulp.src("projects/portal/src/viewer-element.scss")
      .pipe(sass())
      .pipe(replace("inetsoft-viewer :root", "inetsoft-viewer"))
      .pipe(replace("inetsoft-viewer body", "inetsoft-viewer"))
      .pipe(replace("#inetsoft-viewer-overlay :root", "#inetsoft-viewer-overlay"))
      .pipe(replace("#inetsoft-viewer-overlay body", "#inetsoft-viewer-overlay"))
      .pipe(postcss([cssnano()]))
      .pipe(gulp.dest("target/generated-resources/gulp/inetsoft/web/resources/app/"));
});

gulp.task("viewer-element:css", gulp.series(["viewer-element:concat-css", "viewer-element:sass"]));