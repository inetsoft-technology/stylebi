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
const sass = require("gulp-dart-sass");
const postcss = require("gulp-postcss");
const cssnano = require("cssnano");

gulp.task("sass:app", function() {
   const plugins = [ cssnano({discardComments: {removeAll: true}}) ];
   return gulp.src("projects/portal/src/global.scss")
      .pipe(sass({includePaths: ["node_modules"]}).on("error", sass.logError))
      .pipe(postcss(plugins))
      .pipe(gulp.dest("target/generated-resources/gulp/inetsoft/web/resources/app"));
});

gulp.task("sass:em", function() {
   const plugins = [ cssnano({discardComments: {removeAll: true}}) ];
   return gulp.src("projects/em/src/theme.scss")
      .pipe(sass({includePaths: ["node_modules"]}).on("error", sass.logError))
      .pipe(postcss(plugins))
      .pipe(gulp.dest("target/generated-resources/gulp/inetsoft/web/resources/em"));
});

gulp.task("sass:em-dark", function() {
   const plugins = [ cssnano({discardComments: {removeAll: true}}) ];
   return gulp.src("projects/em/src/theme-dark.scss")
      .pipe(sass({includePaths: ["node_modules"]}).on("error", sass.logError))
      .pipe(postcss(plugins))
      .pipe(gulp.dest("target/generated-resources/gulp/inetsoft/web/resources/em"));
});

gulp.task("sass:codemirror-themes", function() {
   const plugins = [ cssnano({discardComments: {removeAll: true}}) ];
   return gulp.src("projects/shared/codemirror/*.scss")
      .pipe(sass({includePaths: ["node_modules"]}).on("error", sass.logError))
      .pipe(postcss(plugins))
      .pipe(gulp.dest("target/generated-resources/gulp/inetsoft/web/resources"));
});

gulp.task("sass", gulp.series(["sass:app", "sass:em", "sass:em-dark", "sass:codemirror-themes"]));

gulp.task("sass:watch", gulp.series(["sass"], function() {
   return gulp.watch([
      "projects/portal/src/scss/**/*.scss",
      "projects/portal/src/assets/ineticons/*.scss",
      "projects/em/src/**.scss"],
      gulp.series(["sass"]))
}));
