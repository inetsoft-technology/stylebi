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
import { HttpUrlEncodingCodec } from "@angular/common/http";
import { AbstractControl } from "@angular/forms";
import { Observable, of as observableOf, Subject } from "rxjs";
import { BAggregateRef } from "../../portal/src/app/binding/data/b-aggregate-ref";
import { BDimensionRef } from "../../portal/src/app/binding/data/b-dimension-ref";
import { BindingModel } from "../../portal/src/app/binding/data/binding-model";
import { ChartBindingModel } from "../../portal/src/app/binding/data/chart/chart-binding-model";
import { SourceInfo } from "../../portal/src/app/binding/data/source-info";
import { CrosstabBindingModel } from "../../portal/src/app/binding/data/table/crosstab-binding-model";
import { TableBindingModel } from "../../portal/src/app/binding/data/table/table-binding-model";
import { ChartRef } from "../../portal/src/app/common/data/chart-ref";
import { DataRef } from "../../portal/src/app/common/data/data-ref";
import { XSchema } from "../../portal/src/app/common/data/xschema";
import { LocalStorage } from "../../portal/src/app/common/util/local-storage.util";
import { BaseTableCellModel } from "../../portal/src/app/vsobjects/model/base-table-cell-model";
import { VSCrosstabModel } from "../../portal/src/app/vsobjects/model/vs-crosstab-model";
import { TreeNodeModel } from "../../portal/src/app/widget/tree/tree-node-model";
import { FileData } from "./model/file-data";
import { SortOptions } from "./sort/sort-options";
import { SortTypes } from "./sort/sort-types";

/**
 * Common utility methods
 */
export namespace Tool {
   export const MY_REPORTS = "My Dashboards";
   export const WORKSHEET = "Worksheets";
   export const TEXT_LIMIT_PREFIX = "__text__limit__start__";
   export const TEXT_LIMIT_SUFFIX = "__text__limit__end__";
   export const COLUMN_LIMIT_PREFIX = "__column__limit__start__";
   export const COLUMN_LIMIT_SUFFIX = "__column__limit__end__";
   export const BUILT_IN_ADMIN_REPORTS = "Built-in Admin Reports";
   export const RECYCLE_BIN = "Recycle Bin";
   export const TRASHCAN_FOLDER = "Trashcan";
   export const MY_REPORTS_RECYCLE_BIN = "My Dashboards/Recycle Bin";
   export const USERS_REPORTS = "Users' Reports";
   export const MY_DASHBOARDS = "My Dashboards";
   export const SNACKBAR_DURATION = 5000;
   export const USER_SUFFIX = "(User)";
   export const GROUP_SUFFIX = "(Group)";

   export const isEquals: (obj0, obj1) => boolean = require("lodash/isEqual");
   export const clone: <T>(v: T) => T = require("lodash/cloneDeep");
   export const shallowClone: <T>(v: T) => T = require("lodash/clone");
   export const isEmpty: (any) => boolean = require("lodash/isEmpty");
   export const unescapeHTML: (string) => string = require("lodash/unescape");
   export const union: <T>(...arr: T[]) => T[] = require("lodash/union");
   export const orderBy = require("lodash/orderBy");
   export const get = require("lodash/get");

   // correct typing of first parameter - varargs isn't possible in typescript
   export const intersectionWith: <T>(obj: T[], vals: T[], comparator: (v1: T, v2: T) => boolean) => T[] = require("lodash/intersectionWith");
   export const uniq: <T>(arr: T[]) => T[] = require("lodash/uniq");
   export const startCase: (string) => string = require("lodash/startCase");

   export function getServletRepositoryPath(): string {
      return "../reports";
   }

   /**
    * Encode a single non-ascii character to unicode enclosed in '[]'
    * @param ch single-character string
    * @param encodeDot whether to encode "."
    * @returns encoded string
    */
   function byteEncodeChar(ch: string, encodeDot = true): string {
      if(!ch) {
         return "";
      }

      let charCode: number = ch.charCodeAt(0);

      if(charCode < 128 && ch !== "[" && ch !== "]" && ch !== "/" && ch !== "'" &&
         ch !== "=" && ch !== "%" && ch !== "&" && ch !== "?" &&
         ch !== "#" && ch !== '"' && ch !== "<" && ch !== ">" &&
         ch !== "," && ch !== "\\" && ch !== "+" && ch !== ";" &&
         ch !== "(" && ch !== ")" && ch !== "{" && ch !== "}" && ch !== "`" &&
         (ch !== "." || !encodeDot) && ch !== "|") {
         return ch;
      }
      else {
         return "~_" + charCode.toString(16) + "_~";
      }
   }

   /**
    * Encode non-ascii characters to unicode enclosed in '[]'.
    * @param source string
    * @param encodeDot whether to encode "."
    * @returns encoded string.
    */
   export function byteEncode(source: string, encodeDot = true): string {
      if(!source) {
         return "";
      }

      let ret: string = "";

      for(let ch of source) {
         ret += byteEncodeChar(ch, encodeDot);
      }

      return ret;
   }

   export function byteEncodeURLComponent(source: string): string {
      return encodeURIComponentExceptSlash(byteEncode(source));
   }

   export function byteDecode(source: string): string {
      if((source == null) || (source == "")) {
         return source;
      }

      let arr = [];

      for(let i = 0; i < source.length; i++) {
         let ch = source.charAt(i);

         if(ch == "~" && i < source.length - 1 && source.charAt(i + 1) == "_") {
            let idx = source.indexOf("_~", i + 2);

            if(idx > i + 2) {
               ch = String.fromCharCode(parseInt(source.substring(i + 2, idx), 16));
               i = idx + 1;
            }
         }

         arr[i] = ch;
      }

      return arr.join("").replace(/%20/g, " ");
   }

   export function setFormControlDisabled(control: AbstractControl, val: boolean): void {
      if(val) {
         control.disable({emitEvent: false});
      }
      else {
         control.enable({emitEvent: false});
      }
   }

   export function replaceStr(val: string, ostr: string, nstr: string): string {
      // Replace all ostr to nstr.
      let reg: RegExp = new RegExp(ostr, "g");

      return val.replace(reg, nstr);
   }

   export function isDate(type: string): boolean {
      return type === XSchema.TIME_INSTANT ||
         type == XSchema.TIME || type == XSchema.DATE;
   }

   export function hasKey(obj: Object, key: string): boolean {
      return obj ? Object.prototype.hasOwnProperty.call(obj, key) : false;
   }

   export function formatCatalogString(format: string, strs: any[]): string {
      let result = format;

      if(result == null) {
         return result;
      }

      strs.forEach((val, idx) => {
         let pattern = "%s$" + idx;
         result = result.split(pattern).join(val);
      });

      return result;
   }

   /**
    * Flattens a multidimensional array into a 1d array
    *
    * @param arr the array to flatten
    * @returns the flattened array
    */
   export function flatten(arr: any[]): any[] {
      return arr.reduce((a, b) => a.concat(Array.isArray(b) ? flatten(b) : b), []);
   }

   /**
    * Returns the result of n modulus m.
    */
   export function mod(n: number, m: number): number {
      return ((n % m) + m) % m;
   }

   /**
    * Checks whether two strings are equal, ignoring case.
    *
    * @param s1 the first string to check
    * @param s2 the second string to check
    * @returns true if the strings are equal when ignoring case, false otherwise
    */
   export function equalsIgnoreCase(s1: string, s2: string): boolean {
      if(s1 == null || s2 == null) {
         return s1 === s2;
      }

      return s1.toUpperCase() === s2.toUpperCase();
   }

   export function setTransferData(dataTransfer: any, data: any): void {
      try {
         const oldJson: string = dataTransfer.getData("text");
         const oldData: any = oldJson ? JSON.parse(oldJson) : {};
         const json = JSON.stringify(Object.assign(data, oldData));
         dataTransfer.setData("text", json);
      }
      catch(error) {
         dataTransfer.setData("text", JSON.stringify(data));
      }
   }

   export function clearBindingData(objectType: string, bmodel: BindingModel): void {
      if(!objectType || !bmodel) {
         return;
      }

      if(objectType == "chart") {
         let chartBModel: ChartBindingModel = <ChartBindingModel> bmodel;
         chartBModel.xfields = new Array<ChartRef>();
         chartBModel.yfields = new Array<ChartRef>();
         chartBModel.geoFields = new Array<ChartRef>();
         chartBModel.groupFields = new Array<ChartRef>();
         chartBModel.geoCols = new Array<DataRef>();
         chartBModel.colorField = null;
         chartBModel.shapeField = null;
         chartBModel.sizeField = null;
         chartBModel.textField = null;
         chartBModel.pathField = null;
         chartBModel.openField = null;
         chartBModel.closeField = null;
         chartBModel.highField = null;
         chartBModel.lowField = null;
      }
      else if(objectType == "table") {
         let tableBModel: TableBindingModel = <TableBindingModel> bmodel;
         tableBModel.groups = new Array<BDimensionRef>();
         tableBModel.details = new Array<DataRef>();
         tableBModel.aggregates = new Array<BAggregateRef>();
      }
      else if(objectType == "crosstab") {
         let crosstabBModel: CrosstabBindingModel = <CrosstabBindingModel> bmodel;
         crosstabBModel.rows = new Array<BDimensionRef>();
         crosstabBModel.cols = new Array<BDimensionRef>();
         crosstabBModel.aggregates = new Array<BAggregateRef>();
      }
   }

   export function getCurrentSourceLabel(model: BindingModel): string {
      if(model && model.source) {
         let sourceName: string = !!model.source.view
            ? model.source.view
            : model.source.source;
         sourceName = sourceName.substring(sourceName.lastIndexOf("^") + 1);

         if(model.source.type == SourceInfo.EMBEDDED_DATA &&
            sourceName == "EMBEDED_DATA") {
            sourceName = "Embedded Data";
         }

         return sourceName;
      }

      return "";
   }

   /**
    * Check if the value is audit node.
    * @param node
    */
   export function isAuditNode(node: TreeNodeModel): boolean {
      const path: string = node.data ? node.data.path : null;

      if(path) {
         let pathArr: string[] = path.split("/");
         return pathArr[0] == Tool.BUILT_IN_ADMIN_REPORTS;
      }

      return false;
   }

   /**
    * Check if the value is dynamic.
    * @return {boolean} whether the string starts with "="/"$" or contains "($".
    */
   export function isDynamic(val: string): boolean {
      if(val == null || val.length == 0 || typeof val != "string") {
         return false;
      }

      return val.charAt(0) == "=" || val.charAt(0) == "$" || val.indexOf("($") >= 0;
   }

   /**
    * Check if the value is variable.
    */
   export function isVar(val: string): boolean {
      if(val == null || val.length == 0 || typeof val != "string") {
         return false;
      }

      return val.charAt(0) == "$";
   }

   /**
    * Check if the value is expression.
    */
   export function isExpr(val: string): boolean {
      if(val == null || val.length == 0 || typeof val != "string") {
         return false;
      }

      return val.charAt(0) == "=";
   }

   export function getMarginSize(border: string): number {
      // Parse the border width from the format string
      return border ? parseInt(border.substring(0, (border.indexOf("px"))), 10) : 0;
   }

   export function getBorderStyle(border: string): string {
      // Parse the border style from the format string
      let styles = border ? border.split(" ") : [];
      return styles.length > 1 ? styles[1] : "";
   }

   /**
    * Returns the browser-independent keycode of the keyboard event.
    *
    * @param event the keyboard event
    * @returns the keycode of the keyboard event
    */
   export function getKeyCode(event: KeyboardEvent): number {
      return event.which || event.keyCode || event.charCode;
   }

   /**
    * Checks whether or not the event target is an HTML element that is an editor.
    * This is particularly useful for filtering key events that are being typed into a text editor.
    *
    * @param event the event to check
    * @returns true if the event target is an editor, false otherwise
    */
   export function isEventTargetTextEditor(event: Event): boolean {
      return (event.target instanceof HTMLInputElement) ||
         (event.target instanceof HTMLTextAreaElement);
   }

   /**
    * Encodes everything that is encoded by the encodeURIComponent function except the
    * slash. Tomcat does not allow encoded slash characters by default due to the
    * increased risk of directory traversal attack.
    *
    * @param str string to be encoded
    * @returns encoded string
    */
   export function encodeURIComponentExceptSlash(str: string): string {
      return encodeURIComponent(str).replace(/%2F/g, "/");
   }

   export function getAssetIdFromUrl(url: string[]): string {
      let assetId: string = null;

      if(url.length > 1) {
         if(url[1] === "global") {
            assetId = "1^128^__NULL__^";

            for(let i = 2; i < url.length; i++) {
               if(i > 2) {
                  assetId += "/";
               }

               assetId += url[i];
            }
         }
         else if(url[1] == "user") {
            assetId = "4^128^" + url[2] + "^";

            for(let i = 3; i < url.length; i++) {
               if(i > 3) {
                  assetId += "/";
               }

               assetId += url[i];
            }
         }
         else if(/^[14]\^128\^.+$/.test(url[1])) {
            if(url.length > 2) {
               assetId = url.slice(1).join("/");
            }
            else {
               assetId = url[1];
            }
         }
         else if(/^[14]%5E128%5E.+$/.test(url[1])) {
            url = url.slice(1).map((path) => decodeURIComponent(path));

            if(url.length > 1) {
               assetId = url.join("/");
            }
            else {
               assetId = url[1];
            }
         }
      }

      return assetId;
   }

   //remove '{d}/{t}/{ts}' tags
   export function transformDate(source: string): string {
      if((source == null) || (source == "")) {
         return "";
      }

      let arr = [], removeBrace = false;

      for(let i = 0; i < source.length; i++) {
         let ch = source.charAt(i);

         if(ch == "{") {
            let str = source.substring(i + 1);

            if(str.startsWith("ts")) {
               removeBrace = true;
               i += 2;
               continue;
            }
            else if(str.startsWith("t") || str.startsWith("d")) {
               removeBrace = true;
               i += 1;
               continue;
            }
         }

         if(ch == "}" && removeBrace) {
            removeBrace = false;
            continue;
         }

         arr[i] = ch;
      }

      return arr.join("");
   }

   // Encode url path by separating the /
   export function encodeURIPath(path: string): string {
      if(!path) {
         return path;
      }

      return path.split("/").map(p => encodeURIComponent(p)).join("/");
   }

   export function isArray(val: any): boolean {
      return Object.prototype.toString.call(val) === "[object Array]";
   }

   // Checks if list of emails delimited by semicolon (;) is valid by checking against regex
   // Empty strings are treated as valid
   export function isValidEmail(val: string): boolean {
      if(!val || val.length == 0) {
         return true;
      }

      const validEmailRegex = /^[\w\d!#$%&'*+\-/=?^_`{|}~]+(\.[\w\d!#$%&'*+\-/=?^_`{|}~]+)*@[\w\d\-_]+(\.[\w\d\-_]+)*$/;
      const addresses: string[] = val.split(";");

      // Return false if any of the emails are invalid
      return !addresses.map(str => str.trim()).some(str => str != "" && !validEmailRegex.test(str));
   }

   export function sortObjects(list: any[], sortOptions: SortOptions): any[] {
      if(!list || list.length === 0) {
         return list;
      }

      const order: string[] = sortOptions.type == SortTypes.ASCENDING ? ["asc"] : ["desc"];
      return orderBy(list, sortOptions.keys.map(column => {
         return row => {
            let value = get(row, column);

            if(!sortOptions.caseSensitive && value != null && typeof value === "string") {
               value = value.toString().toLowerCase();
            }

            return value;
         };
      }), order);
   }

   export function readFileData(event: any): Observable<FileData[]> {
      if(!event || !event.target || !event.target.files || !event.target.files.length) {
         return observableOf([]);
      }

      const subject = new Subject<FileData[]>();
      const files = [];
      let count = 0;

      for(let i = 0; i < event.target.files.length; i++) {
         const file = event.target.files[i];
         const data = {
            name: file.name,
            content: ""
         };
         files.push(data);
         const reader = new FileReader();
         reader.readAsDataURL(file);

         reader.onload = () => {
            let result = <string> reader.result;

            if(!!result) {
               if(result === "data:") {
                  // empty file
                  data.content = "";
               }
               else  if(result.startsWith("data:")) {
                  const index = result.indexOf(";base64,");

                  if(index < 0) {
                     data.content = "";
                  }
                  else {
                     data.content = result.substring(index + 8); // add length of ';base64,'
                  }
               }
               else {
                  data.content = result;
               }
            }

            count += 1;

            if(count == event.target.files.length) {
               subject.next(files);
               subject.complete();
            }
         };
      }

      return subject.asObservable();
   }

   /**
    * Calculates the Damerau-Levenshtein distance between two strings, normalized to a scale between
    * 0 and 1. A value of 0 indicates no match and a value 1 indicates an exact match.
    *
    * @param source        the first string to compare.
    * @param target        the second string to compare.
    * @param caseSensitive true to perform a case-sensitive comparison, false (the default) to
    *                      perform a case-insensitive comparison.
    */
   export function getDamerauLevenshteinDistance(source: string, target: string,
                                                 caseSensitive: boolean = false): number
   {
      if(!source || !target) {
         return 0;
      }

      if(!caseSensitive) {
         source = source.toUpperCase();
         target = target.toUpperCase();
      }

      const sourceLength = source.length;
      const targetLength = target.length;

      const dist: number[][] = [];

      for(let i = 0; i < sourceLength + 1; i++) {
         dist[i] = [i];
      }

      for(let j = 0; j < targetLength + 1; j++) {
         dist[0][j] = j;
      }

      for(let i = 1; i < sourceLength + 1; i++) {
         for(let j = 1; j < targetLength + 1; j++) {
            const cost = source[i - 1] === target[j - 1] ? 0 : 1;
            dist[i][j] = Math.min(Math.min(dist[i - 1][j] + 1, dist[i][j - 1] + 1), dist[i - 1][j - 1] + cost);

            if(i > 1 && j > 1 && source[i - 1] === target[j - 2] && source[1 - 2] === target[j - 1]) {
               dist[i][j] = Math.min(dist[i][j], dist[i - 2][j - 2] + cost);
            }
         }
      }

      const maxLength = Math.max(sourceLength, targetLength);
      return (maxLength - dist[sourceLength][targetLength]) / maxLength;
   }

   /**
    * Calculates a simple distance between two strings. If the strings are equal, 1 is returned.
    * If neither string is a substring of the either, 0 is returned. If one string is a substring of
    * the other a value, 0 > n < 1 is returned, being closer to 1, the closer the substring is to
    * the beginning of the larger string.
    *
    * @param s1            the first string to compare.
    * @param s2            the second string to compare.
    * @param caseSensitive true to perform a case-sensitive comparison, false (the default) to
    *                      perform a case-insensitive comparison.
    *
    * @return the distance between the strings.
    */
   export function getSubstringMatchDistance(s1: string, s2: string,
                                             caseSensitive: boolean = false): number
   {
      if(!s1 || !s2) {
         return 0;
      }

      if(!caseSensitive) {
         s1 = s1.toUpperCase();
         s2 = s2.toUpperCase();
      }

      if(s1 === s2) {
         return 1;
      }

      if(s1.length === s2.length) {
         return 0;
      }

      const larger = s1.length > s2.length ? s1 : s2;
      const smaller = s1.length > s2.length ? s2 : s1;
      const offset = larger.indexOf(smaller);

      if(offset < 0) {
         return 0;
      }

      const llength = larger.length;
      const slength = smaller.length;
      const dist = ((llength - slength) - offset) / (llength - slength);
      return Math.max(0.01, Math.min(0.99, dist));
   }

   /**
    * Calculates the Jaro-Winkler distance between two strings. The distance is a value between 0
    * and 1 where 0 is no match and 1 is an exact match. This is a fuzzy-matching algorithm based on
    * the number of edits required to transform one string to another, giving preference to strings
    * with shared substrings near the beginning of the strings.
    *
    * @param s1            the first string to compare.
    * @param s2            the second string to compare.
    * @param caseSensitive true to perform a case-sensitive comparison, false (the default) to
    *                      perform a case-insensitive comparison.
    *
    * @return the edit distance between the strings.
    */
   export function getJaroWinklerDistance(s1: string, s2: string,
                                          caseSensitive: boolean = false): number
   {
      let m = 0;

      // empty strings
      if(!s1 || !s2) {
         return 0;
      }

      if(!caseSensitive) {
         s1 = s1.toUpperCase();
         s2 = s2.toUpperCase();
      }

      // identical strings
      if(s1 === s2) {
         return 1;
      }

      let range = (Math.floor(Math.max(s1.length, s2.length) / 2)) - 1;
      const s1Matches = new Array<boolean>(s1.length);
      const s2Matches = new Array<boolean>(s2.length);

      // count the number of matches
      for(let i = 0; i < s1.length; i++) {
         const low = (i >= range) ? i - range : 0;
         const high = (i + range <= (s2.length - 1)) ? (i + range) : (s2.length - 1);

         for(let j = low; j <= high; j++) {
            if(s1Matches[i] !== true && s2Matches[j] !== true && s1[i] === s2[j]) {
               ++m;
               s1Matches[i] = s2Matches[i] = true;
               break;
            }
         }
      }

      // no matches
      if(m === 0) {
         return 0;
      }

      let k = 0;
      let transforms = 0;

      // count the number of transformations (edits)
      for(let i = 0; i < s1.length; i++) {
         if(s1Matches[i] === true) {
            let j: number;

            for(j = k; j < s2.length; j++) {
               if(s2Matches[j] == true) {
                  k = j + 1;
                  break;
               }
            }

            if(s1[i] !== s2[j]) {
               ++transforms;
            }
         }
      }

      let weight = (m / s1.length + m / s2.length + (m - (transforms / 2)) / m) / 3;

      if(weight > 0.7) {
         // increase the weight for substring matches at the beginning of the strings
         let l = 0;

         while(s1[l] === s2[l] && l < 4) {
            ++l;
         }

         weight = weight + l * 0.1 * (1 - weight);
      }

      return weight;
   }

   /**
    * Uses distance algorithm to find the best matches to a string in a list of values.
    *
    * @param search          the string to match.
    * @param values          the possible values.
    * @param numberOfResults the maximum number of results to return. A value of -1 (the default)
    *                        returns all results.
    * @param minimumDistance the minimum distance (closeness of match) to include in the results. A
    *                        value of 0 (the default) returns any distance.
    * @param caseSensitive   true to perform a case-sensitive comparison, false (the default) to
    *                        perform a case-insensitive comparison.
    * @param mapper          a function used to map from the values to a string. This is optional
    *                        when the value list are strings.
    * @param algorithm       the comparison algorithm to use. The is getJaroWinklerDistance() by
    *                        default.
    */
   export function findMatches<T>(search: string, values: T[], numberOfResults: number = -1,
                                  minimumDistance: number = 0, caseSensitive: boolean = false,
                                  mapper?: (value: T) => string,
                                  algorithm?: (s1: string, s2: string, b: boolean) => number): T[]
   {
      if(!mapper) {
         mapper = (value: any) => <string> value;
      }

      if(!algorithm) {
         algorithm = getJaroWinklerDistance;
      }

      let results = values
         .map(value => {
            return {
               value,
               distance: algorithm(search, mapper(value), caseSensitive)
            };
         })
         .sort((a, b) => -1 * (a.distance - b.distance));

      let length = numberOfResults < 0 ? results.length : numberOfResults;

      if(minimumDistance > 0) {
         for(let i = 0; i < length; i++) {
            if(results[i].distance < minimumDistance) {
               length = i;
               break;
            }
         }
      }

      if(length < results.length) {
         results = results.slice(0, length);
      }

      return results.map(item => item.value);
   }

   /**
    * Detect if an object is a numeric type.
    *    This type of detection is generally only string and number.
    * @param obj object
    */
   export function isNumber(obj: any): boolean {
      return (+obj + "" === obj || typeof obj === "number") && !isNaN(+obj);
   }

   /**
    * Calculate tow numbers. default execute add operation.
    * @param isSubtract if <tt>true</tt>, calc <i>num1 - num2</i>, default <tt>false</tt>.
    * @param digits default <tt>10</tt>.
    */
   export function numberCalculate(num1: number, num2: number,
                                   isSubtract = false, digits = 10): number
   {
      if(isSubtract) {
         num2 = -num2;
      }

      return parseFloat((num1 + num2).toFixed(digits));
   }

   export class URIExceptSlashEncoder extends HttpUrlEncodingCodec {
      encodeKey(key: string): string {
         return encodeURIComponentExceptSlash(key);
      }

      encodeValue(value: string): string {
         return encodeURIComponentExceptSlash(value);
      }
   }

   export class HttpFormEncodingCodec extends HttpUrlEncodingCodec {
      encodeValue(value: string): string {
         return super.encodeValue(value)
            .replace(/\+/g, "%2B")
            .replace(/[!'()*]/g, c => "%" + c.charCodeAt(0).toString(16))
            .replace(/%20/g, "+");
      }
   }

   export function is24HourTimeLocale(): boolean {
      let timeStr = new Date().toLocaleTimeString();
      return timeStr.indexOf("AM") < 0 && timeStr.indexOf("PM") < 0;
   }

   export function getSortedSelectedHeaderCell(model: VSCrosstabModel,
                                               sort: boolean = true,
                                               onlyDrillable = true): BaseTableCellModel[]
   {
      if(!!!model || !!!model.selectedHeaders || !!!model.cells || model.cells.length == 0) {
         return [];
      }

      let drillCells: BaseTableCellModel[] = [];
      let cell: BaseTableCellModel;
      // row is not start with 0 after loading data
      const startRow = model.cells[0][0].row;

      model.selectedHeaders.forEach((cols: number[], row: number) => {
         cols.forEach(col => {
            if(row - startRow >= 0) {
               cell = model.cells[row - startRow][col];
            }
            else if(row < model.headerRowCount) {
               cell = model.tableHeaderCells[row][col];
            }

            if(cell && (!!cell.drillOp || !onlyDrillable)) {
               drillCells.push(cell);
            }
         });
      });

      if(sort) {
         drillCells.sort((c1, c2) => {
            if(c1.row != c2.row) {
               return c1.row > c2.row ? 1 : -1;
            }
            else {
               return c1.col > c2.col ? 1 : -1;
            }
         });
      }

      return drillCells;
   }

   export function generateRandomUUID() {
      let uuid = "";

      for(let i: number = 0; i < 32; i++) {
         const random = getRandomByte();
         let append: number;

         if(i === 12) {
            append = 4;
         }
         else if(i === 16) {
            append = (random & 3 | 8);
         }
         else {
            append = random;
         }

         if(i === 8 || i === 12 || i === 16 || i === 20) {
            uuid += "-";
         }

         uuid += append.toString(16);
      }

      return uuid;
   }

   function getRandomByte() {
      const cryptoObj = window.crypto || (window as any).msCrypto;

      if(cryptoObj) {
         return cryptoObj.getRandomValues(new Uint8Array(1))[0] & 15;
      }
      else {
         return Math.random() * 16 | 0;
      }
   }

   export function isIdentifier(str: string): boolean {
      return /^[a-zA-Z_$][0-9a-zA-Z_$]*$/.test(str);
   }

   export function getDateParts(dataType: string): string[] {
      if(dataType == XSchema.DATE) {
         return ["Year", "QuarterOfYear", "MonthOfYear", "DayOfMonth", "DayOfWeek"];
      }
      else if(dataType == XSchema.TIME) {
         return ["HourOfDay", "MinuteOfHour", "SecondOfMinute"];
      }
      else if(dataType == XSchema.TIME_INSTANT) {
         return ["Year", "QuarterOfYear", "MonthOfYear", "DayOfMonth", "DayOfWeek",
            "HourOfDay", "MinuteOfHour", "SecondOfMinute"];
      }

      return null;
   }

   export function getDatePartFuncs(dataType: string): string[] {
      if(dataType == XSchema.DATE) {
         return ["year", "quarter", "month", "day", "weekday"];
      }
      else if(dataType == XSchema.TIME) {
         return ["hour", "minute", "second"];
      }
      else if(dataType == XSchema.TIME_INSTANT) {
         return ["year", "quarter", "month", "day", "weekday",
            "hour", "minute", "second"];
      }

      return null;
   }

   export function quoteString(s: string): string {
      if(!s.includes("'") && !s.includes('"') && !s.includes(",")) {
         return s;
      }

      return s.includes("'") ? '"' + s + '"' : "'" + s + "'";
   }

   export function getHistoryEmails(historyEnabled: boolean): string[] {
      if(!historyEnabled) {
         LocalStorage.setItem(LocalStorage.MAIL_HISTORY_KEY, null);

         return [];
      }
      else {
         return JSON.parse(LocalStorage.getItem(LocalStorage.MAIL_HISTORY_KEY)) || [];
      }
   }

   // If have text limit info, change textConfimed to true, else keep old value.
   export function getTextLimit(textLimitConfirmed: boolean, message: string): boolean {
      let originalValue = textLimitConfirmed;
      let hasTextLimit = message.indexOf(Tool.TEXT_LIMIT_PREFIX) >= 0;

      if(hasTextLimit) {
         return true;
      }

      return originalValue;
   }

   export function getColumnLimit(columnLimitConfirmed: boolean, message: string): boolean {
      let originalValue = columnLimitConfirmed;
      let hasColLimit = message.indexOf(Tool.COLUMN_LIMIT_PREFIX) >= 0;

      if(hasColLimit) {
         return true;
      }

      return originalValue;
   }

   export function shouldIgnoreMessage(textLimitConfirmed: boolean,
                                       columnLimitConfirmed: boolean,
                                       message: string): boolean
   {
      let hasTextLimit = message.indexOf(Tool.TEXT_LIMIT_PREFIX) >= 0;
      let hasColLimit = message.indexOf(Tool.COLUMN_LIMIT_PREFIX) >= 0;

      // If not limit message, should popup message directly.
      // If have other message not limit, should popup message directly.
      if(!hasTextLimit && !hasColLimit || hasOtherMessage(message)) {
         return false;
      }

      // has text and column limit together, only return when all confirmed before.
      if(hasTextLimit && hasColLimit) {
         if(textLimitConfirmed && columnLimitConfirmed) {
            return true;
         }
      }
      // only limit one, only check one confirmed. return null will not show message command.
      else if(hasTextLimit && textLimitConfirmed || hasColLimit && columnLimitConfirmed) {
         return true;
      }

      return false;
   }

   export function hasOtherMessage(message: string) {
      let textPattern = new RegExp(Tool.TEXT_LIMIT_PREFIX + '.*?' +
         Tool.TEXT_LIMIT_SUFFIX, "g");
      let columnPattern = new RegExp(Tool.COLUMN_LIMIT_PREFIX + '.*?' +
         Tool.COLUMN_LIMIT_SUFFIX, "g");
      message = message.replace(textPattern, "");
      message = message.replace(columnPattern, "");
      message = message.trim();

      return message.length > 0;
   }

   export function getLimitedMessage(message: string): string {
      message = message.replace(Tool.TEXT_LIMIT_PREFIX, "");
      message = message.replace(Tool.TEXT_LIMIT_SUFFIX, "");
      message = message.replace(Tool.COLUMN_LIMIT_PREFIX, "");
      message = message.replace(Tool.COLUMN_LIMIT_SUFFIX, "");

      return message;
   }
}