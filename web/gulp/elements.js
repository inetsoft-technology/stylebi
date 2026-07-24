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
const through = require("through2");
const { bundleMainScript, appendText } = require("./lib/bundle-main-script");

const NG_ELEMENTS_DIR = "target/generated-resources/ng/inetsoft/web/resources/elements";

// main-*.js is bundled separately below (see bundleMainScript) so that any chunk-*.js it
// imports is inlined rather than left as a dangling cross-file `import`.
const scriptFiles = [
   `${NG_ELEMENTS_DIR}/polyfills-*.js`,
   `${NG_ELEMENTS_DIR}/scripts-*.js`
];

const cssFiles = [
   "target/generated-resources/gulp/inetsoft/web/resources/app/global.css",
   `${NG_ELEMENTS_DIR}/styles-*.css`
];

// Wrap each file's content in an IIFE to prevent variable name collisions.
// Use .call(window) so that UMD libraries (e.g. tern) that rely on `this`
// pointing to the global object still work in strict-mode Angular bundles.
const wrapInIIFE = function() {
   return through.obj(function(file, encoding, callback) {
      if(file.isBuffer()) {
         const content = file.contents.toString(encoding);
         file.contents = Buffer.from(`(function(){${content}}).call(window);\n`, encoding);
      }
      callback(null, file);
   });
};

gulp.task("elements:scripts", function () {
   return gulp.src(scriptFiles)
      .pipe(wrapInIIFE())
      .pipe(concat("elements.js"))
      .pipe(appendText(`\n${bundleMainScript(NG_ELEMENTS_DIR)}\n`))
      .pipe(gulp.dest("target/generated-resources/gulp/inetsoft/web/resources/app/"));
});

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
