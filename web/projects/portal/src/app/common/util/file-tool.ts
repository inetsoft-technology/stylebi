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
 import { FileTypes } from "../data/file-types";

/**
 * Common file type
 */
export namespace FileTool {
   export function getFileType(fileName: string): number {
      let dot = fileName.lastIndexOf(".");
      let format = "";

      if(dot > 0) {
         format = fileName.substring(dot + 1).toLowerCase();
      }

      if(format == "html_bundle") {
         return FileTypes.HTML_BUNDLE;
      }
      else if(format == "html_bundle_no_pagination") {
         return FileTypes.HTML_BUNDLE_NO_PAGINATION;
      }
      else if(format == "txt") {
         return FileTypes.TEXT;
      }
      else if(format == "xml") {
         return FileTypes.XML;
      }
      else if(format == "pdf") {
         return FileTypes.PDF;
      }
      else if(format == "rtf") {
         return FileTypes.RTF;
      }
      else if(format === "xlsx") {
         return FileTypes.EXCEL_DATA;
      }
      else if(format == "csv") {
         return FileTypes.CSV;
      }
      else if(format == "svg") {
         return FileTypes.SVG;
      }
      else if(format == "pptx" || format == "ppt") {
         return FileTypes.POWERPOINT;
      }
      else if(format == "xgm") {
         return FileTypes.GENERATED_REPORT;
      }

      return 0;
   }
}
