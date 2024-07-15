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
import { TableDataPath } from "../data/table-data-path";
import { TableDataPathTypes } from "../data/table-data-path-types";
import { XSchema } from "../data/xschema";

export class DataPathConstants {
   /**
    * Data path for title.
    */
   static TITLE: TableDataPath = <TableDataPath> {
      level: -1,
      type: TableDataPathTypes.TITLE,
      index: 0,
      col: false,
      row: true,
      colIndex: -1,
      dataType: XSchema.STRING,
      path: []
   };

   /**
    * Data path for detail cells.
    */
   static DETAIL: TableDataPath = <TableDataPath> {
      level: -1,
      type: TableDataPathTypes.DETAIL,
      index: 0,
      col: false,
      row: true,
      colIndex: -1,
      dataType: XSchema.STRING,
      path: []
   };

   /**
    * Data path for entire object.
    */
   static OBJECT: TableDataPath = <TableDataPath> {
      level: -1,
      type: TableDataPathTypes.OBJECT,
      index: 0,
      col: false,
      row: true,
      colIndex: -1,
      dataType: XSchema.STRING,
      path: []
   };

   /**
    * Basic data path for measure bar (-) detail cells in selections.
    */
   static MEASURE_N_BAR: TableDataPath = <TableDataPath> {
      level: 0,
      type: TableDataPathTypes.DETAIL,
      index: 0,
      col: false,
      row: false,
      colIndex: -1,
      dataType: XSchema.STRING,
      path: ["Measure Bar(-)"]
   };

   /**
    * Basic data path for measure bar detail cells in selections.
    */
   static MEASURE_BAR: TableDataPath = <TableDataPath> {
      level: 0,
      type: TableDataPathTypes.DETAIL,
      index: 0,
      col: false,
      row: false,
      colIndex: -1,
      dataType: XSchema.STRING,
      path: ["Measure Bar"]
   };

   /**
    * Basic data path for measure text detail cells in selections.
    */
   static MEASURE_TEXT: TableDataPath = <TableDataPath> {
      level: 0,
      type: TableDataPathTypes.DETAIL,
      index: 0,
      col: false,
      row: false,
      colIndex: -1,
      dataType: XSchema.STRING,
      path: ["Measure Text"]
   };

   /**
    * Basic data path for header cells.
    */
   static GROUP_HEADER_CELL: TableDataPath = <TableDataPath> {
      level: 0,
      type: TableDataPathTypes.GROUP_HEADER,
      index: 0,
      col: false,
      row: true,
      colIndex: -1,
      dataType: XSchema.STRING,
      path: []
   };

   /**
    * Data path for calendar title.
    */
   static CALENDAR_TITLE: TableDataPath = <TableDataPath> {
      level: -1,
      type: TableDataPathTypes.CALENDAR_TITLE,
      index: 0,
      col: false,
      row: true,
      colIndex: -1,
      dataType: XSchema.STRING,
      path: []
   };

   /**
    * Data path for month calendar.
    */
   static MONTH_CALENDAR: TableDataPath = <TableDataPath> {
      level: -1,
      type: TableDataPathTypes.MONTH_CALENDAR,
      index: 0,
      col: false,
      row: true,
      colIndex: -1,
      dataType: XSchema.STRING,
      path: []
   };

   /**
    * Data path for year calendar.
    */
   static YEAR_CALENDAR: TableDataPath = <TableDataPath> {
      level: -1,
      type: TableDataPathTypes.YEAR_CALENDAR,
      index: 0,
      col: false,
      row: true,
      colIndex: -1,
      dataType: XSchema.STRING,
      path: []
   };

   /**
    * String representation of a TableDataPath object
    */
   static getTableDataPathString(path: TableDataPath): string {
      let str = DataPathConstants.getPathTypeString(path);

      if(path == null) {
         return "";
      }

      if(path.type === TableDataPathTypes.CALENDAR_TITLE ||
         path.type === TableDataPathTypes.YEAR_CALENDAR ||
         path.type === TableDataPathTypes.MONTH_CALENDAR ||
         path.type === TableDataPathTypes.TITLE)
      {
         return str;
      }

      str += " " + (path.row ? "Row" : path.col ? "Column" : "Cell");

      if(path.path && path.path.length > 0) {
         str += " [" + DataPathConstants.getPathString(path) + "]";
      }
      else if(path.row) {
         str += "-" + path.index;
      }
      else if(path.level) {
         str += "-" + path.level;
      }

      return str;
   }

   /**
    * Get the path string (colum names).
    */
   static getPathString(path: TableDataPath): string {
      let str: string = "";

      for(let i = 0; i < path.path.length; i++) {
         if(i > 0) {
            str += "-";
         }

         str += path.path[i];
      }

      return str;
   }

   /**
    * Get the string of the path type.
    */
   static getPathTypeString(path: TableDataPath): string {
      let str = "";

      if(path == null) {
         return str;
      }

      switch(path.type) {
         case TableDataPathTypes.HEADER:
            str = "Header";
            break;
         case TableDataPathTypes.DETAIL:
            str = "Detail";
            break;
         case TableDataPathTypes.GROUP_HEADER:
            str = "Group Header";

            if(path.level >= 0) {
               str += "-" + path.level;
            }

            break;
         case TableDataPathTypes.SUMMARY:
            str = "Summary";

            if(path.level >= 0) {
               str += "-" + path.level;
            }

            break;
         case TableDataPathTypes.SUMMARY_HEADER:
            str = "Summary Header";
            break;
         case TableDataPathTypes.GRAND_TOTAL:
            str = "Grand Total";
            break;
         case TableDataPathTypes.UNKNOWN:
            str = "Unknown";
            break;
         case TableDataPathTypes.TITLE:
            str = "Title";
            break;
         case TableDataPathTypes.CALENDAR_TITLE:
            str = "Header";
            break;
         case TableDataPathTypes.YEAR_CALENDAR:
            str = "Detail";
            break;
         case TableDataPathTypes.MONTH_CALENDAR:
            str =  "Detail";
            break;
         case TableDataPathTypes.OBJECT:
            str = "Object";
            break;
         default:
            str = "";
      }

      return str;
   }
}
