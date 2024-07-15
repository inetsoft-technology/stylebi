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
import {
   AbstractControl,
   UntypedFormControl,
   UntypedFormGroup,
   ValidationErrors,
   ValidatorFn
} from "@angular/forms";
import { Tool } from "./tool";
import { Injectable } from "@angular/core";

// copied from Angular's validators
const EMAIL_REGEXP =
   /^(?=.{1,254}$)(?=.{1,64}@)[-!#$%&'*+/0-9=?A-Z^_`a-z{|}~]+(\.[-!#$%&'*+/0-9=?A-Z^_`a-z{|}~]+)*@[A-Za-z0-9]([A-Za-z0-9-]{0,61}[A-Za-z0-9])?(\.[A-Za-z0-9]([A-Za-z0-9-]{0,61}[A-Za-z0-9])?)*$/;

@Injectable()
export class FormValidators {
   public static DATASOURCE_NAME_REGEXP = /^[^\\\/?"*:<>|. ^][^\\\/?"*:<>|.^]*$/;

   public static passwordComplexity(control: AbstractControl): ValidationErrors | null {
      if(control.value) {
         if(control.value.length < 8 || !/[A-Za-z]/g.test(control.value) ||
            !/[0-9]/g.test(control.value))
         {
            return { passwordComplexity: true };
         }
      }

      return null;
   }

   public static required(control: UntypedFormControl): ValidationErrors {
      const str = control.value;

      if(!str || !str.trim()) {
         return { required: true };
      }

      return null;
   }

   public static requiredNumber(control: UntypedFormControl): ValidationErrors {
      // 0 is valid.
      if(!Tool.isNumber(control.value)) {
         return { requiredNumber: true };
      }

      return null;
   }

   public static containsSpecialChars(control: UntypedFormControl): ValidationErrors {
      if(/[~`!#$%^&*+=\-\[\]\\';,./{}|":<>?_()]/g.test(control.value)) {
         return {containsSpecialChars: true};
      }

      return null;
   }

   public static matchReservedModelName(control: UntypedFormControl): ValidationErrors {
      return control.value == "_#(js:Domain)" || control.value == "_#(js:Data Model)" ?
         {matchReservedModelName: true} : null;
   }

   public static invalidDataModelName(control: UntypedFormControl): ValidationErrors {
      let validName: boolean = /^[^\\\/^:*"|<>?]*$/.test(control.value);

      return !validName ? {invalidDataModelName: true} : null;
   }

   public static containsDashboardSpecialCharsForName(control: UntypedFormControl): ValidationErrors {
      if(/[~`!#%^*=\[\]\\;,./{}|":<>?()]/g.test(control.value)) {
         return {containsSpecialCharsForName: true};
      }

      return null;
   }

   /**
    * Same as {@link containsDashboardSpecialCharsForName} except allow periods for email addresses
    */
   public static validUserName(control: UntypedFormControl): ValidationErrors {
      let trimmedValue = control.value.trim();

      if(trimmedValue == "") {
         return {required: true};
      }

      if(/[~`!#%^*=\[\]\\;,/{}|":<>?()]/g.test(control.value)) {
         return {containsSpecialCharsForName: true};
      }

      return null;
   }

   /**
    * Because organization id's are appended to url, limit to alphanumeric chars and hyphen
    */
   public static validOrgID(control: UntypedFormControl): ValidationErrors {
      let trimmedValue = control.value.trim();

      if(trimmedValue == "") {
         return {required: true};
      }

      if(!/^[a-zA-Z0-9-]*$/.test(control.value)) {
         return {containsSpecialCharsForName: true};
      }

      return null;
   }

   public static validGroupName(control: UntypedFormControl): ValidationErrors {
      let trimmedValue = control.value.trim();

      if(trimmedValue == "") {
         return {required: true};
      }

      if(/[~`!#%^*=\[\]\\;,/{}|":<>?]/g.test(control.value)) {
         return {containsSpecialCharsForName: true};
      }

      return null;
   }

   public static invalidAssetItemName(control: UntypedFormControl): ValidationErrors {
      const str = control.value;
      let validName: boolean = str && /^[^\\\/"<'%^]+$/.test(str);

      return !validName ? {invalidAssetItemName: true} : null;
   }

   public static mustBeValidReportIdentifier(control: UntypedFormControl): ValidationErrors {
      const value: string = control.value;

      if(value && value.indexOf("{") == -1 && value.indexOf("}") == -1 &&
         /[~`!#%^*=\[\]\\';,./|":<>?()]/g.test(value)) {
         return {containsInvalidCharacter: true};
      }

      return null;
   }

   public static containsSpecialCharsForName(control: UntypedFormControl): ValidationErrors {
      if(/[~`!#%^&*+=\-\[\]\\';,./{}|":<>?()]/g.test(control.value) ||
         !/^.*([\u4e00-\u9fa5])$/.test(control.value) &&
         control.value.split("").some(c => c.charCodeAt(0) > 127))
      {
         return {containsSpecialCharsForName: true};
      }

      return null;
   }

   public static containsSpecialCharsForCommonName(control: UntypedFormControl): ValidationErrors {
      if(/[~`!#%^&*+=\-\[\]\\;,./{}|":<>?()]/g.test(control.value))
      {
         return {containsSpecialCharsForCommonName: true};
      }

      return null;
   }

   public static invalidTaskName(control: UntypedFormControl): ValidationErrors {
      if(!!control && !!control.value &&
         !/^[A-Za-z?0-9$ &?#!*`;>|~={}()@+_:.,'\-\[\]\u4e00-\u9fa5\u00c0-\u022a\u00f6-\u01fe\u00f8-\u00ff]+$/.test(control.value))
      {
         return {invalidTaskName: true};
      }

      return null;
   }

   public static isValidWindowsFileName(control: UntypedFormControl): ValidationErrors {
      let validWindowsFileChars = /^[^\\/:*?"<>|]+$/;
      let startsWithDot = /^\s*\./;

      if(control.value &&
         (!validWindowsFileChars.test(control.value) || startsWithDot.test(control.value))) {
         return {containsInvalidWindowsChars: true};
      }

      return null;
   }

   public static isValidFileNameAndXMLSafe(control: UntypedFormControl): ValidationErrors {
      let containsInvalid = /^[^*\[:\\<>?|#'%\/,&\]^"]+$/;

      if(control.value && !containsInvalid.test(control.value)) {
         return {containsInvalidForFileAndXML: true};
      }

      return null;
   }

   public static isValidReportName(control: UntypedFormControl): ValidationErrors {
      if(control.value && !/^[a-zA-Z0-9$\-&@+_',.\u4e00-\u9fa5\s]+$/.test(control.value)) {
         return {containsSpecialCharsForName: true};
      }

      return null;
   }

   public static isInvalidBackupName(control: UntypedFormControl): ValidationErrors {
      if(control.value && !/^[^#%^&*\[\]|\\'":,<>\/?]*$/.test(control.value)) {
         return {containsSpecialCharsForName: true};
      }

      return null;
   }

   public static isInvalidHierarchyName(control: UntypedFormControl): ValidationErrors {
      if(control.value && !/^[^^\[\]]*$/.test(control.value)) {
         return {containsSpecialCharsForName: true};
      }

      return null;
   }

   public static isInvalidHierarchyMemberName(control: UntypedFormControl): ValidationErrors {
      if(control.value && !/^[^^.]*$/.test(control.value)) {
         return {invalidHierarchyMemberName: true};
      }

      return null;
   }

   public static isValidDataSpaceName(control: UntypedFormControl): ValidationErrors {
      if(control.value && !/^[a-zA-Z0-9$\-&@_.]+$/.test(control.value)) {
         return {containsSpecialCharsForName: true};
      }

      return null;
   }

   public static isValidDataSpaceFileName(control: UntypedFormControl): ValidationErrors {
      if(control.value && (/[*+:"<>?|\\#'/%,]/g.test(control.value) ||
         control.value.lastIndexOf(".") === control.value.length - 1 ||
         (control.value.indexOf(".") === 0 && control.value !== ".stylereport"))) {
         return {containsSpecialCharsForName: true};
      }

      return null;
   }

   public static isValidPrototypeName(control: UntypedFormControl): ValidationErrors {
      if(control.value && /[*$&:+<>?|\\#'/%,."\[\]]/g.test(control.value)) {
         return {containsSpecialCharsForName: true};
      }

      return null;
   }

   public static isValidTemplateName(templateUpload: boolean): ValidatorFn {
      return (control) => {
         return FormValidators.checkTemplateName(templateUpload, control.value);
      };
   }

   public static checkTemplateName(templateUpload: boolean, value: any): ValidationErrors | null {
      if(value && /^[*]+$/.test(value)) {
         return {containsSpecialCharsForName: true};
      }

      const length = value ? value.length : 0;

      if(length < 5 ||
         (value.substring(length - 4, length).toLowerCase() != ".srt" &&
            value.substring(length - 4, length).toLowerCase() != ".sro" &&
            value.substring(length - 4, length).toLowerCase() != ".xml"))
      {
         return templateUpload ? {illegalUploadTemplateName: true} :
            {illegalTextTemplateName: true};
      }

      if(!templateUpload) {
         if(value.charAt(0) != "/") {
            return {notStartWithBackslashTemplate: true};
         }

         if(value == "/") {
            return {illegalTextTemplateName: true};
         }
      }

      return null;
   }

   public static isValidScreenshotName(screenshotUpload: boolean): ValidatorFn {
      return (control) => {
         const length = control.value.length;

         if(length < 5) {
            return screenshotUpload ? {illegalUploadScreenshotName: true} :
               {illegalTextScreenshotName: true};
         }
         else {
            const ext = control.value.substring(length - 4, length).toLowerCase();

            if(!(ext == ".png" || ext == ".jpg" || ext == ".gif" || ext == ".bmp")) {
               return screenshotUpload ? {illegalUploadScreenshotName: true} :
                  {illegalTextScreenshotName: true};
            }
         }

         if(!screenshotUpload) {
            if(control.value.charAt(0) != "/") {
               return {notStartWithBackslashScreenshot: true};
            }

            if(control.value == "/") {
               return {illegalTextScreenshotName: true};
            }
         }

         return null;
      };
   }

   public static isValidDataSourceFolderName(control: UntypedFormControl): ValidationErrors {
      const str = control.value as string;

      if(str && !/^[^\\/?"*:<>|^]*$/.test(str)) {
         return {containsSpecialCharsForName: true};
      }

      if(str && str === "Cubes") {
         return {isDefaultCubesName: true};
      }

      return null;
   }

   public static positiveNonZeroOrNull(control: UntypedFormControl): ValidationErrors {
      if(control.value != null && control.value + "" != "" &&
         (control.value <= 0 || control.value > 2147483647)) {
         return {lessThanEqualToZero: true};
      }

      return null;
   }

   public static positiveNonZeroInRange(control: UntypedFormControl): ValidationErrors {
      if(isNaN(Number(control.value)) || control.value <= 0 || control.value > 2147483647) {
         return {positiveNonZeroInRange: true};
      }

      return null;
   }

   public static positiveNonZeroIntegerInRange(control: UntypedFormControl): ValidationErrors {
      if(isNaN(Number(control.value)) || control.value <= 0
         || control.value > 2147483647 || /\./g.test(control.value))
      {
         return {lessThanEqualToZero: true};
      }

      return null;
   }

   public static positiveIntegerInRange(control: UntypedFormControl): ValidationErrors {
      if(isNaN(Number(control.value)) || control.value < 0 || control.value > 2147483647) {
         return {positiveIntegerInRange: true};
      }

      return null;
   }

   private static numberInRangeTemplate(max: number, min: number, error: ValidationErrors,
                                        split: boolean = false, delimiter: string = ",",
                                        ignoreWhitespace: boolean = true
                                        ): (FormControl) => ValidationErrors | null
   {
      return (control: UntypedFormControl) => {
         if (control.value != null) {
            let values: string[] = split ? control.value.toString().split(delimiter) : [control.value];

            if(values.map((value) => +(ignoreWhitespace ? value.toString().trim() : value))
               .some((value) => isNaN(Number(value)) || value < min || value > max)) {
               return error;
            }
         }

         return null;
      };
   }

   public static integerInRange(split: boolean = false, delimiter: string = ",",
                                ignoreWhitespace: boolean = true): (FormControl) => ValidationErrors | null
   {
      return FormValidators.numberInRangeTemplate(2147483647, -2147483648, {integerInRange: true},
         split, delimiter, ignoreWhitespace);
   }

   public static shortInRange(split: boolean = false, delimiter: string = ",",
                              ignoreWhitespace: boolean = true): (FormControl) => ValidationErrors | null
   {
      return FormValidators.numberInRangeTemplate(32767, -32768, {shortInRange: true},
         split, delimiter, ignoreWhitespace);
   }

   public static longInRange(split: boolean = false, delimiter: string = ",",
                             ignoreWhitespace: boolean = true): (FormControl) => ValidationErrors | null
   {
      return FormValidators.numberInRangeTemplate(Math.pow(2, 63) - 1, -Math.pow(2, 63), {longInRange: true},
         split, delimiter, ignoreWhitespace);
   }

   public static byteInRange(split: boolean = false, delimiter: string = ",",
                             ignoreWhitespace: boolean = true): (FormControl) => ValidationErrors | null
   {
      return FormValidators.numberInRangeTemplate(127, -128, {byteInRange: true},
         split, delimiter, ignoreWhitespace);
   }

   public static inRangeOrNull(min: number, max: number): (FormControl) => ValidationErrors | null {
      return (control: UntypedFormControl) => {
         if(!!control && !!control.value) {
            let value = control.value.toString().trim();

            if(!!value) {
               value = parseInt(value, 10);

               if(isNaN(value) || value < min || value > max) {
                  return {inRangeOrNull: true};
               }
            }
         }

         return null;
      };
   }

   public static inSanpGridRangeOrNull(control: UntypedFormControl): ValidationErrors {
      if(control.value <= 0 || control.value %5 != 0) {
         return {inSanpGridRangeOrNull: true};
      }

      return null;
   }

   private static isTypeDataTemplate(pattern: RegExp, error: ValidationErrors, split: boolean = false,
                                     delimiter: string = ",", ignoreWhitespace: boolean = true
                                     ): (FormControl) => ValidationErrors | null
   {
      return (control) => {
         if (control.value != null) {
            let values: string[] = split ? control.value.toString().split(delimiter) : [control.value];

            if(values.map((value) => ignoreWhitespace ? value.toString().trim() : value)
               .some((value) => !pattern.test(value)))
            {
               return error;
            }
         }

         return null;
      };
   }

   public static isInteger(split: boolean = false, delimiter: string = ",",
                           ignoreWhitespace: boolean = true): (FormControl) => ValidationErrors | null
   {
      return FormValidators.isTypeDataTemplate(/^[-+]?[0-9]+$/, {isInteger: true}, split,
         delimiter, ignoreWhitespace);
   }

   public static isFloat(split: boolean = false, delimiter: string = ",",
                         ignoreWhitespace: boolean = true): (FormControl) => ValidationErrors | null
   {
      return FormValidators.isTypeDataTemplate(/^\d*(\.\d*)?$/, {isFloat: true},
         split, delimiter, ignoreWhitespace);
   }

   public static isFloatNumber(split: boolean = false, delimiter: string = ",",
                               ignoreWhitespace: boolean = true): (FormControl) => ValidationErrors | null
   {
      return FormValidators.isTypeDataTemplate(/^[-+]?\d*(\.\d*)?$/, {isFloatNumber: true},
         split, delimiter, ignoreWhitespace);
   }

   public static isDate(split: boolean = false, delimiter: string = ",",
                        ignoreWhitespace: boolean = true): (FormControl) => ValidationErrors | null
   {
      // This regular expression validates the format and data, such as 2019-13-1 is illegal
      return FormValidators.isTypeDataTemplate(/^(?:(?!0000)[0-9]{4}(-?)(?:(?:0?[1-9]|1[0-2])\1(?:0?[1-9]|1[0-9]|2[0-8])|(?:0?[13-9]|1[0-2])\1(?:29|30)|(?:0?[13578]|1[02])\1(?:31))|(?:[0-9]{2}(?:0[48]|[2468][048]|[13579][26])|(?:0[48]|[2468][048]|[13579][26])00)(-?)0?2\2(?:29))$/,
         {isDate: true}, split, delimiter, ignoreWhitespace);
   }

   public static isTime(split: boolean = false, delimiter: string = ",",
                        ignoreWhitespace: boolean = true): (FormControl) => ValidationErrors | null
   {
      // This regular expression validates the format and data, such as 12:00:60 is illegal
      return FormValidators.isTypeDataTemplate(/^([01]?[0-9]|2[0-3]):[0-5]?[0-9]:[0-5]?[0-9]$/,
         {isTime: true}, split, delimiter, ignoreWhitespace);
   }

   public static isDateTime(split: boolean = false, delimiter: string = ",",
                            ignoreWhitespace: boolean = true): (FormControl) => ValidationErrors | null
   {
      // This regular expression validates the format and data, such as 2019-13-1 is illegal
      return FormValidators.isTypeDataTemplate(/^(?:(?!0000)[0-9]{4}(-?)(?:(?:0?[1-9]|1[0-2])\1(?:0?[1-9]|1[0-9]|2[0-8])|(?:0?[13-9]|1[0-2])\1(?:29|30)|(?:0?[13578]|1[02])\1(?:31))|(?:[0-9]{2}(?:0[48]|[2468][048]|[13579][26])|(?:0[48]|[2468][048]|[13579][26])00)(-?)0?2\2(?:29))([T|\s+])([01]?[0-9]|2[0-3]):[0-5]?[0-9]:[0-5]?[0-9]$/,
         {isDateTime: true}, split, delimiter, ignoreWhitespace);
   }

   public static isBoolean(split: boolean = false, delimiter: string = ",",
                           ignoreWhitespace: boolean = true): (FormControl) => ValidationErrors | null
   {
      return FormValidators.isTypeDataTemplate(/^(true|false)$/, {isBoolean: true}, split,
         delimiter, ignoreWhitespace);
   }

   public static doesNotStartWithNumber(control: UntypedFormControl): ValidationErrors {
      if(!!control.value && /^\d.*$/.test(control.value)) {
         return {doesNotStartWithNumber: true};
      }

      return null;
   }

   public static doesNotStartWithBlankSpace(control: UntypedFormControl): ValidationErrors {
      if(!!control.value && /^\s.*$/.test(control.value)) {
         return {doesNotStartWithBlankSpace: true};
      }

      return null;
   }

   public static doesNotStartWithNumberOrLetter(control: UntypedFormControl): ValidationErrors {
      if(!!control.value && /^[\da-zA-Z].*$/.test(control.value)) {
         return null;
      }

      if(!control.value) {
         return null;
      }

      return {doesNotStartWithNumberOrLetter: true};
   }

   public static notWhiteSpace(control: UntypedFormControl): ValidationErrors {
      if(!!control.value && /^\s+$/.test(control.value)) {
         return {notWhiteSpace: true};
      }

      return null;
   }

   public static variableSpecialCharacters(control: UntypedFormControl): ValidationErrors {
      if(!!control.value && !/^[\w\uFF00-\uFFEF\u4e00-\u9fa5@$&+\- ]*$/.test(control.value)) {
         return {variableSpecialCharacters: true};
      }

      return null;
   }

   public static alphanumericalCharacters(control: UntypedFormControl): ValidationErrors {
      if(!!control.value && !/^[a-zA-Z0-9]*$/.test(control.value)) {
         return {alphanumericalCharacters: true};
      }

      return null;
   }

   public static bookmarkSpecialCharacters(control: UntypedFormControl): ValidationErrors {
      const dtReg: RegExp = /^\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2}\w*$/;
      const dateReg: RegExp = /^\d{4}-\d{2}-\d{2}\w*$/;
      const timeReg: RegExp = /^\d{2}:\d{2}:\d{2}\w*$/;
      const valid: boolean = dtReg.test(control.value) || dateReg.test(control.value) ||
         timeReg.test(control.value);

      const bookmarkSpecialChars: boolean = !valid ?
         !(/^[a-zA-Z0-9\u4e00-\u9fa5@$& _+\-]*$/.test(control.value)) : false;

      return bookmarkSpecialChars ? {bookmarkSpecialCharacters: true} : null;
   }

   public static emailSpecialCharacters(control: UntypedFormControl): ValidationErrors {
      if(!!control.value && !/^\w+([.-]?\w+)*@\w+([.-]?\w+)*(\.\w{2,3})+$/.test(control.value)) {
         return {emailSpecialCharacters: true};
      }

      return null;
   }

   public static firstLetter(control: UntypedFormControl): ValidationErrors {
      if(!!control && /^[^a-zA-Z0-9 ].*$/.test(control.value)) {
         return {firstLetter: true};
      }

      return null;
   }

   public static calcSpecialCharacters(control: UntypedFormControl): ValidationErrors {
      if(control && FormValidators.matchCalcSpecialCharacters(control.value)) {
         return {calcSpecialCharacters: true};
      }

      return null;
   }

   private static matchNameSpecialCharacters(str: string): boolean {
      if(str) {
         if(!/^[\uFF00-\uFFEF\u4e00-\u9fa5a-zA-Z0-9 $#_%&\-,?!@']*$/.test(str)
            // check chinese colon and question mark.
            || /^.*([\uff1a|[\uff1f])$/.test(str))
         {
            return true;
         }

         // we have special handling for name surrounded by ' and it will cause problem
         if(str.startsWith("'") && str.endsWith("'")) {
            return true;
         }
      }

      return false;
   }

   public static matchCalcSpecialCharacters(str: string): boolean {
      if(str) {
         if(!/^[\uFF00-\uFFEF\u4e00-\u9fa5a-zA-Z0-9 #_&\-.!%]*$/.test(str)) {
            return true;
         }

         // we have special handling for name surrounded by ' and it will cause problem
         if(str.startsWith("'") && str.endsWith("'")) {
            return true;
         }
      }

      return false;
   }

   /**
    * ws/vs object name rule.
    */
   public static nameSpecialCharacters(control: UntypedFormControl): ValidationErrors {
      if(control && FormValidators.matchNameSpecialCharacters(control.value)) {
         return {nameSpecialCharacters: true};
      }

      return null;
   }

   public static assetEntryBannedCharacters(control: UntypedFormControl): ValidationErrors {
      const name: string = control.value;
      const pattern = /[\\\/"<%^]/;
      const containsSpecialChars: boolean = name ?
         pattern.test(name) : false;

      return containsSpecialChars ? {assetEntryBannedCharacters: pattern.exec(name)} : null;
   }

   /**
    * Returns a ValidatorFn which checks if the control value is found in names.
    *
    * @param names the strings to compare the control value to.
    * @param existsOptions the extra validation options
    */
   public static exists(names: string[], existsOptions: ExistsOptions = {}): ValidatorFn {
      return (control: UntypedFormControl): ValidationErrors => {
         const value: string = existsOptions.trimSurroundingWhitespace && control.value != null
            ? control.value.trim() : control.value;
         let exists: boolean;

         if(!!!value) {
            return null;
         }

         if(existsOptions.hasOwnProperty("originalValue") &&
            existsOptions.originalValue === value) {
            exists = false;
         }
         else if(existsOptions.ignoreCase) {
            exists = !!names
               && names.findIndex((name) => FormValidators.equalsIgnoreCase(name, value)) !== -1;
         }
         else {
            exists = names && names.indexOf(value) !== -1;
         }

         return exists ? {exists: true} : null;
      };
   }

   /** Validator for table identifiers adapted from AlertManager.as */
   public static tableIdentifier(control: UntypedFormControl): ValidationErrors {
      if(/[\\/:*?"<>|.'&%,`~!@#=\-+(){}\[\]^;]/g.test(control.value)) {
         return {tableIdentifier: true};
      }

      return null;
   }

   public static smallerThan(min: string, max: string, orEqualTo = true): ValidatorFn {
      return (group: UntypedFormGroup): ValidationErrors => {
         let minimum = group.controls[min];
         let maximum = group.controls[max];

         if(minimum.value == null || maximum.value == null) {
            return null;
         }

         if(orEqualTo) {
            return minimum.value >= maximum.value ? {greaterThan: true} : null;
         }
         else {
            return minimum.value > maximum.value ? {greaterThan: true} : null;
         }
      };
   }

   public static dateSmallerThan(min: string, max: string): ValidatorFn {
      return (group: UntypedFormGroup): ValidationErrors => {
         let minimum: any = group.controls[min].value;
         let maximum: any = group.controls[max].value;

         if(group === null) {
            return null;
         }

         if(minimum && maximum) {
            let minDate: Date = new Date(minimum.year, minimum.month, minimum.day, 0, 0, 0, 0);
            let maxDate: Date = new Date(maximum.year, maximum.month, maximum.day, 0, 0, 0, 0);

            if(minimum.year && maximum.year) {
               minDate = new Date(minimum.year, minimum.month, minimum.day, 0, 0, 0, 0);
               maxDate = new Date(maximum.year, maximum.month, maximum.day, 0, 0, 0, 0);
            }
            else if((minimum instanceof Date) && (maximum instanceof Date)) {
               minDate = minimum;
               maxDate = maximum;
            }
            else {
               return null;
            }

            return minDate >= maxDate ? {dateGreaterThan: true} : null;
         }
         else {
            return null;
         }
      };
   }

   // Support starts with number, character, chinese. Using original of angular's
   // Validator, it can only starts with number and character.
   public static nameStartWithCharDigit(control: UntypedFormControl): ValidationErrors {
      let name: string = control.value;
      let firstCharNumber: boolean = name ?
         /[0-9A-Za-z]+/.test(name.charAt(0)) : false;

      return !firstCharNumber ? {nameStartWithCharDigit: true} : null;
   }

   public static assetNameStartWithCharDigit(control: UntypedFormControl): ValidationErrors {
      if(!!control.value && !/^([a-zA-Z0-9\u00c0-\u024f\u4e00-\u9fa5])/.test(control.value)) {
         return {assetNameStartWithCharDigit: true};
      }

      return null;
   }

   public static assetNameMyReports(control: UntypedFormControl): ValidationErrors {
      if(control.value == Tool.MY_REPORTS || control.value == "My Alerts") {
         return {assetNameMyReports: true};
      }

      return null;
   }

   public static containsNumberAndLetterOrNonAlphanumeric(control: UntypedFormControl): ValidationErrors {
      const includesNumeric: boolean = /\d/.test(control.value);
      const includesLetterOrNonAlphanumeric: boolean =
         /[a-z!"#$%&'()*+,\-./:;<=>?@[\\\]^_`{|}~]/i.test(control.value);

      return includesNumeric && includesLetterOrNonAlphanumeric ? null : {missingCharType: true};
   }

   public static emailListRequired(): ValidatorFn {
      return (control) => {
         if(control.value == null || control.value == "") {
            return { "required": true };
         }

         const value = typeof control.value == "object" ? control.value.value : control.value;

         if(!value) {
            return { "required": true };
         }

         return null;
      };
   }

   public static emailList(delimiter: string = ",", ignoreWhitespace: boolean = true,
                           splitByColon: boolean = false, users?: string[], allowVariable = false
                           ): (FormControl) => ValidationErrors | null
   {
      return (control) => {
         if(control.value != null && control.value !== "") {
            const value = typeof control.value == "object" ? control.value.value : control.value;

            if(!value) {
               return null;
            }

            if(allowVariable && this.matchesVariable(value)) {
               return null;
            }

            let delimiter0 = delimiter;

            //Match at least one of the provided delimiters
            if(!!delimiter && delimiter.length > 1) {
               for(let i = 0; i < delimiter.length; i ++) {
                  let delimiterChar = delimiter.charAt(i);

                  if(value.indexOf(delimiterChar) != -1) {
                     delimiter0 = delimiterChar;
                     break;
                  }
               }
            }

            const emails = value.split(delimiter0).map(e => ignoreWhitespace ? e.trim() : e);

            if(!!emails && emails.some((email) => {
               let addr = splitByColon && !!email && email.indexOf(":") > 0
                  ? email.substring(email.indexOf(":") + 1) : email;

               return !!addr
                  ? (!EMAIL_REGEXP.test(addr)
                     && (!users || users.indexOf(addr) < 0)
                     && (splitByColon && !!users && email.indexOf(":") > 0
                        ? users.indexOf(email.substring(0, email.indexOf(":"))) < 0
                        : true))
                  : true;
            }))
            {
               return {"email": true};
            }
         }

         return null;
      };
   }

   public static matchesVariable(value: string): boolean {
      return /^\$\(.+\)$/.test(value);
   }

   public static duplicateTokens(delimiter: string = ",;",
      ignoreWhitespace: boolean = true): (FormControl) => ValidationErrors | null
   {
      return (control) => {
         if(control.value != null && control.value !== "") {
            const value = typeof control.value == "object" ? control.value.value : control.value;

            if(!value) {
               return null;
            }

            let delimiter0 = delimiter;

            //Match at least one of the provided delimiters
            if(!!delimiter && delimiter.length > 1) {
               for(let i = 0; i < delimiter.length; i ++) {
                  let delimiterChar = delimiter.charAt(i);

                  if(value.indexOf(delimiterChar) != -1) {
                     delimiter0 = delimiterChar;
                     break;
                  }
               }
            }

            const tokens = value.split(delimiter0).map(e => ignoreWhitespace ? e.trim() : e);

            if(new Set(tokens).size < tokens.length) {
               return {"duplicateTokens": true};
            }
         }

         return null;
      };
   }

   public static passwordsMatch(passwordFieldName: string,
      verifyFieldName: string): (FormGroup) => ValidationErrors | null
   {
      return (group) => {
         if(!group) {
            return null;
         }

         const passwordControl = group.get(passwordFieldName);
         const verifyControl = group.get(verifyFieldName);

         if(!!passwordControl && !!verifyControl && passwordControl.enabled &&
            verifyControl.enabled && passwordControl.value !== verifyControl.value) {
            return {passwordsMatch: true};
         }

         return null;
      };
   }

   public static duplicateName(names: () => string[]): (FormControl) => ValidationErrors | null {
      return (control) => {
         const controlName: string = control.value;
         const found = names().find(name => name === controlName);
         return !!found ? {duplicateName: true} : null;
      };
   }

   // Checks whether the control value ends in the specific suffix, otherwise error
   public static hasSuffix(suffix: string): ValidatorFn {
      return (control: UntypedFormControl): ValidationErrors => {
         if(!!control && !!control.value) {
            const val: string = control.value;
            return val.endsWith(suffix) ? null : {incorrectSuffix: true};
         }

         return null;
      };
   }

   // Checks whether the control value contains the specific prefix, otherwise error
   public static hasPrefix(prefix: string): ValidatorFn {
      return (control: UntypedFormControl): ValidationErrors => {
         if(!!control && !!control.value) {
            const val: string = control.value;
            return val.startsWith(prefix) ? null : {incorrectPrefix: true};
         }

         return null;
      };
   }

   // Checks whether the control value does not contain the specified strings, error if it is in value
   public static cannotContain(illegalStr: string[], options: ExistsOptions = {}): ValidatorFn {
      return (control: UntypedFormControl): ValidationErrors => {
         if(!!control && !!control.value) {
            const val: string = options.ignoreCase ? control.value.toLowerCase() : control.value;
            const error: boolean = illegalStr.some((str) => {
               if(options.ignoreCase) {
                  str = str.toLowerCase();
               }

               return val.indexOf(str) >= 0;
            });

            return error ? {illegalStr: true} : null;
         }

         return null;
      };
   }

   /**
    * Checks whether two strings are equal, ignoring case.
    *
    * @param s1 the first string to check
    * @param s2 the second string to check
    * @returns true if the strings are equal when ignoring case, false otherwise
    */
   private static equalsIgnoreCase(s1: string, s2: string): boolean {
      if(s1 == null || s2 == null) {
         return s1 === s2;
      }

      return s1.toUpperCase() === s2.toUpperCase();
   }

   public static maxLength(max: number): ValidatorFn {
      return (control: AbstractControl): ValidationErrors => {
         const exceeded = !!control.value && control.value.length > max;
         return exceeded ? { maxLength: true } : null;
      };
   }
}

interface ExistsOptions {
   trimSurroundingWhitespace?: boolean; // if true, control value will be trimmed before comparison.
   ignoreCase?: boolean; // if true, control value comparison ignores case
   originalValue?: string; // if the control value matches the original value, short-circuit the validation
}
