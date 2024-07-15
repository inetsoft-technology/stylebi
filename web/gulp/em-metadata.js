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
const through = require("through2");
const fs = require("fs");
const path = require("path");
const File = require("vinyl");

const generateMetadata = function() {
   const helpEntries = [];
   const securityEntries = [];
   const searchEntries = [];

   function generateHelpMetadata(file) {
      const content = file.contents.toString("utf8");
      const expr = /@ContextHelp\s*\(\s*({[^}]+})\s*\)/;
      const match = expr.exec(content);

      if(match != null) {
         const metadata = eval("(" + match[1] + ")");
         const {route, link} = metadata;
         helpEntries.push({route, link});
      }
   }

   const createSecurityTree = () => {
      const tree = {
         "name": "",
         "label": "",
         "requiredLicenses": [],
         "children": {}
      };

      securityEntries
         .sort((a, b) => a.route < b.route ? -1 : a.route > b.route ? 1 : 0)
         .forEach(entry => {
            const path = entry.route.substring(1).split("/");
            let parent = tree;

            for(let i = 0; i < path.length; i++) {
               let child = parent.children[path[i]];

               if(!child) {
                  child = {
                     name: path[i],
                     label: "",
                     requiredLicenses: [],
                     children: {}
                  };
                  parent.children[path[i]] = child;
               }

               if(i === path.length - 1) {
                  child.label = entry.label;
                  child.requiredLicenses = entry.requiredLicenses;
                  child.hiddenForMultiTenancy = entry.hiddenForMultiTenancy;
               } else {
                  parent = child;
               }
            }
         });

      return tree;
   };

   function generateSecurityMetadata(file) {
      const content = file.contents.toString("utf8");
      const expr = /@Secured\s*\(\s*({[^}]+\s*(children:\s*\[([^\]]|(\[[^\]]+]))+])?[^}]*})\s*\)/;
      const match = expr.exec(content);

      if(match != null) {
         const addEntries = (md => {
            const {route, label, requiredLicenses, children, hiddenForMultiTenancy} = md;
            securityEntries.push({route, label, requiredLicenses, hiddenForMultiTenancy});

            if(children) {
               children.forEach(c => addEntries(c));
            }
         });
         addEntries(eval("(" + match[1] + ")"));
      }
   }

   const makeUnique = (values) => {
      return values.sort().filter((item, pos, arr) => {
         return !pos || item !== arr[pos - 1];
      });
   };

   const extractI18nKeys = (content, html) => {
      const keys = [];
      let expr;
      let match;

      if(html) {
         expr = /_#\(([^)]+)\)/mg;
      }
      else {
         expr = /_#\(js:([^)]+)\)/mg;
      }

      while((match = expr.exec(content)) != null) {
         keys.push(match[1]);
      }

      return makeUnique(keys);
   };

   function generateSearchMetadata(file) {
      const content = file.contents.toString("utf8");
      const expr = /@Searchable\s*\(\s*({[^}]+})\s*\)/;
      const match = expr.exec(content);

      if(match != null) {
         const metadata = eval("(" + match[1] + ")");
         const route = metadata.route.replace(/^_#\(js:/, "").replace(/\)$/, "");
         const title = metadata.title.replace(/^_#\(js:/, "").replace(/\)$/, "");
         const keywords = metadata.keywords.map(value => {
            if(/^_#\(js:[^)]+\)$/.test(value)) {
               return value.substring(0, value.length - 1).substring(3);
            }

            return value;
         });

         let strings = extractI18nKeys(content, false);
         const htmlFile = path.resolve(path.dirname(file.path), path.basename(file.path, ".ts") + ".html");

         if(fs.existsSync(htmlFile)) {
            strings = makeUnique(strings.concat(extractI18nKeys(fs.readFileSync(htmlFile, "utf8"), true)));
         }

         searchEntries.push({route, title, keywords, strings});
      }
   }

   function generateFileMetadata(file, encoding, callback) {
      if(file.isNull()) {
         callback();
         return;
      }

      if(file.isStream()) {
         this.emit("error", new Error("em-metadata: Streaming not supported"));
         callback();
         return;
      }

      generateHelpMetadata(file);
      generateSecurityMetadata(file);
      generateSearchMetadata(file);
      return callback();
   }

   function endStream(callback) {
      this.push(new File({
         path: "inetsoft/web/admin/help/help-links.json",
         contents: Buffer.from(JSON.stringify({links: helpEntries}), "utf8")
      }));

      this.push(new File({
         path: "inetsoft/web/admin/authz/view-components.json",
         contents: Buffer.from(JSON.stringify(createSecurityTree()), "utf8")
      }));

      this.push(new File({
         path: "inetsoft/web/admin/search/search-index.json",
         contents: Buffer.from(JSON.stringify({entries: searchEntries}), "utf8")
      }));

      callback();
   }

   return through({objectMode: true}, generateFileMetadata, endStream);
};

gulp.task("em:metadata", function() {
   return gulp.src("projects/em/src/app/**/*.component.ts")
      .pipe(generateMetadata())
      .pipe(gulp.dest("target/generated-resources/gulp"));
});

gulp.task("em:metadata:watch", gulp.series([ "em:metadata" ], function() {
   return gulp.watch([
      "projects/em/src/app/**/*.components.ts",
      "projects/em/src/app/**/*.component.html"],
      gulp.series(["em:metadata"]))
}));
