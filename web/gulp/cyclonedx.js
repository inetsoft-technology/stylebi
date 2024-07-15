/*
 * inetsoft-web - StyleBI is a business intelligence web application.
 * Copyright © 2024 InetSoft Technology (info@inetsoft.com)
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
 * You should have received a copy of the GNU Affrero General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
const gulp = require("gulp");
const through = require("through2");
const Vinyl = require("vinyl");
const FormData = require("form-data");
const https = require("https");
const cyclonedx = require("@cyclonedx/bom");

const projectVersion = "13.8";
const apiKey = process.env.CYCLONEDX_API_KEY;

const generateBom = function() {
   return through({objectMode: true}, function(file, encoding, callback) {
      const options = { dev: false };
      cyclonedx.createbom("library", true, false, ".", options, (err, bom) => {
         if(err) {
            callback(new Error(err));
         }
         else {
            this.push(new Vinyl({
               cwd: "",
               base: undefined,
               path: "bom.xml",
               contents: Buffer.from(bom.toXML(), "utf8")
            }));
            callback();
         }
      });
   });
};

const uploadBom = function() {
   return through({objectMode: true}, function(file, encoding, callback) {
      const form = new FormData();
      form.append("autoCreate", "true");
      form.append("projectName", "inetsoft-web");
      form.append("projectVersion", projectVersion);
      form.append("bom", file.contents);
      const options = {
         method: "POST",
         host: "dependency-track.inetsoft.com",
         port: 443,
         path: "/api/v1/bom",
         headers: form.getHeaders({"X-Api-Key": apiKey})
      };
      const request = https.request(options, (response) => {
         if(response.statusCode < 200 || response.statusCode >= 400) {
            callback(new Error("Failed to upload BOM"));
         }
         else {
            callback();
         }
      });
      form.pipe(request);
      request.on("error", (err) => {
         callback(err);
      });
      request.end();
   });
};

const downloadVex = function() {
   return through({objectMode: true}, function(file, encoding, callback) {
      const oldVex = JSON.parse(file.contents.toString("utf8"));
      const projectId = oldVex.metadata.component["bom-ref"];
      const options = {
         headers: { "X-Api-Key": apiKey }
      }
      https.get("https://dependency-track.inetsoft.com/api/v1/vex/cyclonedx/project/" + projectId, options, response => {
         if(response.statusCode < 200 || response.statusCode >= 400) {
            callback(new Error("Failed to download VEX"));
         }

         let vexBody = "";

         response.on("data", data => {
            vexBody += data;
         });

         response.on("end", () => {
            const newVex = JSON.parse(vexBody);

            if(!!oldVex.vulnerabilities && !!newVex.vulnerabilities) {
               newVex.vulnerabilities.forEach(newVulnerability => {
                  const newId = newVulnerability.id;

                  if(!newVulnerability.analysis) {
                     const oldVulnerability = oldVex.vulnerabilities.find(v => v.id === newId);

                     if(!!oldVulnerability && !!oldVulnerability.analysis) {
                        newVulnerability.analysis = Object.assign({}, oldVulnerability.analysis);
                     }
                  }
               });
            }

            this.push(new Vinyl({
               cwd: "",
               base: undefined,
               path: "vex.cdx.json",
               contents: Buffer.from(JSON.stringify(newVex, null, 2), "utf8")
            }));
            callback();
         });
      }).on("error", err => callback(err));
   });
};

const applyVex = function() {
   return through({objectMode: true}, function(file, encoding, callback) {
      const projectId = JSON.parse(file.contents.toString("utf8")).metadata.component["bom-ref"];
      const form = new FormData();
      form.append("project", projectId);
      form.append("vex", file.contents);
      const options = {
         method: "POST",
         host: "dependency-track.inetsoft.com",
         port: 443,
         path: "/api/v1/vex",
         headers: form.getHeaders({"X-Api-Key": apiKey})
      };
      const request = https.request(options, (response) => {
         if(response.statusCode < 200 || response.statusCode >= 400) {
            callback(new Error("Failed to upload VEX"));
         }
         else {
            callback();
         }
      });
      form.pipe(request);
      request.on("error", (err) => {
         callback(err);
      });
      request.end();
   });
};

gulp.task("dependency:uploadBom", function() {
   return gulp.src("package.json")
      .pipe(generateBom())
      .pipe(uploadBom());
});

gulp.task("dependency:downloadVex", function() {
   return gulp.src("vex.cdx.json")
      .pipe(downloadVex())
      .pipe(gulp.dest("."));
});

gulp.task("dependency:applyVex", function() {
   return gulp.src("vex.cdx.json")
      .pipe(applyVex());
});
