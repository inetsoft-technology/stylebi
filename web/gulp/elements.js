/*
 * inetsoft-web - StyleBI is a business intelligence web application.
 * Copyright © 2024 InetSoft Technology (info@inetsoft.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
const gulp = require("gulp");
const concat = require("gulp-concat");
const sass = require("gulp-dart-sass");
const postcss = require("gulp-postcss");
const replace = require("gulp-replace");
const cssnano = require("cssnano");

const scriptFiles = [
   "target/generated-resources/ng/inetsoft/web/resources/elements/runtime.*.js",
   "target/generated-resources/ng/inetsoft/web/resources/elements/polyfills.*.js",
   "target/generated-resources/ng/inetsoft/web/resources/elements/scripts.*.js",
   "target/generated-resources/ng/inetsoft/web/resources/elements/main.*.js"
];

const cssFiles = [
   "target/generated-resources/gulp/inetsoft/web/resources/app/global.css",
   "target/generated-resources/ng/inetsoft/web/resources/elements/styles.*.css"
];

gulp.task("elements:scripts", function () {
   return gulp.src(scriptFiles)
      .pipe(concat("elements.js"))
      .pipe(gulp.dest("target/generated-resources/gulp/inetsoft/web/resources/elements/"));
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
      .pipe(gulp.dest("target/generated-resources/gulp/inetsoft/web/resources/elements/"));
});

gulp.task("elements:css", gulp.series(["elements:concat-css", "elements:sass"]));
