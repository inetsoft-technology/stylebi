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
const through = require("through2");

// Bug #76468: a gulp.src() glob that matches zero files feeds gulp-concat an empty stream,
// which silently writes nothing while the task still reports "Finished" -- exactly what
// happened here for ~4 months after an Angular builder migration changed the hashed output
// filename convention and these tasks' glob patterns went stale. Insert this immediately
// after gulp.src() to fail the task loudly instead, so a future naming-convention change is
// caught at build time rather than producing a silently-missing bundle.
module.exports = function requireNonEmptyStream(taskName) {
   let matchedCount = 0;

   return through.obj(
      function(file, encoding, callback) {
         matchedCount++;
         callback(null, file);
      },
      function(callback) {
         if(matchedCount === 0) {
            callback(new Error(
               `Task '${taskName}' matched zero input files -- its glob patterns likely no ` +
               "longer match the real build output filenames (e.g. after an Angular builder " +
               "change). Check the patterns against the actual output directory."));
         }
         else {
            callback();
         }
      }
   );
};
