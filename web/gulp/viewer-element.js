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

const scriptFiles = [
   "target/generated-resources/ng/inetsoft/web/resources/viewer-element/runtime.*.js",
   "target/generated-resources/ng/inetsoft/web/resources/viewer-element/polyfills.*.js",
   "target/generated-resources/ng/inetsoft/web/resources/viewer-element/scripts.*.js",
   "target/generated-resources/ng/inetsoft/web/resources/viewer-element/main.*.js"
];

const cssFiles = [
   "target/generated-resources/gulp/inetsoft/web/resources/app/global.css",
   "target/generated-resources/ng/inetsoft/web/resources/viewer-element/styles.*.css"
];

gulp.task("viewer-element:scripts", function () {
   return gulp.src(scriptFiles)
      .pipe(concat("viewer-element.js"))
      .pipe(gulp.dest("target/generated-resources/gulp/inetsoft/web/resources/app/"));
});

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
      .pipe(postcss([cssnano()]))
      .pipe(gulp.dest("target/generated-resources/gulp/inetsoft/web/resources/app/"));
});

gulp.task("viewer-element:css", gulp.series(["viewer-element:concat-css", "viewer-element:sass"]));