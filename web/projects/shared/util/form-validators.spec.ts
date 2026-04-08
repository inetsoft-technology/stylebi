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
import { UntypedFormControl, UntypedFormGroup } from "@angular/forms";
import { FormValidators } from "./form-validators";

function ctrl(value: any): UntypedFormControl {
   return new UntypedFormControl(value);
}

describe("FormValidators", () => {
   describe("passwordComplexity", () => {
      it("returns null for valid password", () => {
         expect(FormValidators.passwordComplexity(ctrl("Password1!"))).toBeNull();
         expect(FormValidators.passwordComplexity(ctrl("Abc1234!"))).toBeNull();
      });

      it("returns error for password shorter than 8 chars", () => {
         expect(FormValidators.passwordComplexity(ctrl("Pass1"))).toEqual({ passwordComplexity: true });
      });

      it("returns error for password longer than 72 chars", () => {
         const long = "a".repeat(70) + "1X";
         expect(FormValidators.passwordComplexity(ctrl(long + "aa"))).toEqual({ passwordComplexity: true });
      });

      it("returns error for password with no letters", () => {
         expect(FormValidators.passwordComplexity(ctrl("12345678"))).toEqual({ passwordComplexity: true });
      });

      it("returns error for password with no digits", () => {
         expect(FormValidators.passwordComplexity(ctrl("password"))).toEqual({ passwordComplexity: true });
      });

      it("returns null for empty value", () => {
         expect(FormValidators.passwordComplexity(ctrl(""))).toBeNull();
         expect(FormValidators.passwordComplexity(ctrl(null))).toBeNull();
      });
   });

   describe("required", () => {
      it("returns null for non-empty value", () => {
         expect(FormValidators.required(ctrl("hello"))).toBeNull();
         expect(FormValidators.required(ctrl("  text  "))).toBeNull();
      });

      it("returns error for empty string", () => {
         expect(FormValidators.required(ctrl(""))).toEqual({ required: true });
      });

      it("returns error for whitespace-only string", () => {
         expect(FormValidators.required(ctrl("   "))).toEqual({ required: true });
         expect(FormValidators.required(ctrl("\t\n"))).toEqual({ required: true });
      });

      it("returns error for null/undefined", () => {
         expect(FormValidators.required(ctrl(null))).toEqual({ required: true });
      });
   });

   describe("requiredNumber", () => {
      it("returns null for valid number", () => {
         expect(FormValidators.requiredNumber(ctrl(0))).toBeNull();
         expect(FormValidators.requiredNumber(ctrl(42))).toBeNull();
         expect(FormValidators.requiredNumber(ctrl("3.14"))).toBeNull();
      });

      it("returns error for non-number", () => {
         expect(FormValidators.requiredNumber(ctrl("abc"))).toEqual({ requiredNumber: true });
         expect(FormValidators.requiredNumber(ctrl(null))).toEqual({ requiredNumber: true });
         expect(FormValidators.requiredNumber(ctrl(""))).toEqual({ requiredNumber: true });
      });
   });

   describe("containsSpecialChars", () => {
      it("returns null for clean string", () => {
         expect(FormValidators.containsSpecialChars(ctrl("hello world"))).toBeNull();
         expect(FormValidators.containsSpecialChars(ctrl("abc123"))).toBeNull();
      });

      it("returns error for string with special characters", () => {
         expect(FormValidators.containsSpecialChars(ctrl("hello!"))).toEqual({ containsSpecialChars: true });
         expect(FormValidators.containsSpecialChars(ctrl("test+value"))).toEqual({ containsSpecialChars: true });
         expect(FormValidators.containsSpecialChars(ctrl("a#b"))).toEqual({ containsSpecialChars: true });
         expect(FormValidators.containsSpecialChars(ctrl("x_y"))).toEqual({ containsSpecialChars: true });
      });

      it("does not flag @ as a special character", () => {
         expect(FormValidators.containsSpecialChars(ctrl("test@value"))).toBeNull();
      });
   });

   describe("matchReservedModelName", () => {
      it("returns error for reserved names", () => {
         expect(FormValidators.matchReservedModelName(ctrl("_#(js:Domain)"))).toEqual({ matchReservedModelName: true });
         expect(FormValidators.matchReservedModelName(ctrl("_#(js:Data Model)"))).toEqual({ matchReservedModelName: true });
      });

      it("returns null for other names", () => {
         expect(FormValidators.matchReservedModelName(ctrl("MyModel"))).toBeNull();
         expect(FormValidators.matchReservedModelName(ctrl(""))).toBeNull();
      });
   });

   describe("validUserName", () => {
      it("returns null for valid usernames", () => {
         expect(FormValidators.validUserName(ctrl("john.doe"))).toBeNull();
         expect(FormValidators.validUserName(ctrl("user123"))).toBeNull();
         expect(FormValidators.validUserName(ctrl("first last"))).toBeNull();
      });

      it("returns required error for blank/empty", () => {
         expect(FormValidators.validUserName(ctrl("   "))).toEqual({ required: true });
         expect(FormValidators.validUserName(ctrl(""))).toEqual({ required: true });
      });

      it("returns error for invalid special chars", () => {
         expect(FormValidators.validUserName(ctrl("user[name]"))).toEqual({ containsSpecialCharsForName: true });
         expect(FormValidators.validUserName(ctrl("user{name}"))).toEqual({ containsSpecialCharsForName: true });
      });
   });

   describe("validOrgID", () => {
      it("returns null for valid org IDs", () => {
         expect(FormValidators.validOrgID(ctrl("my-org"))).toBeNull();
         expect(FormValidators.validOrgID(ctrl("Org123"))).toBeNull();
         expect(FormValidators.validOrgID(ctrl("abc-def-123"))).toBeNull();
      });

      it("returns required error for blank", () => {
         expect(FormValidators.validOrgID(ctrl("   "))).toEqual({ required: true });
         expect(FormValidators.validOrgID(ctrl(""))).toEqual({ required: true });
      });

      it("returns error for special characters", () => {
         expect(FormValidators.validOrgID(ctrl("org_name"))).toEqual({ containsSpecialCharsForName: true });
         expect(FormValidators.validOrgID(ctrl("org.name"))).toEqual({ containsSpecialCharsForName: true });
         expect(FormValidators.validOrgID(ctrl("org name"))).toEqual({ containsSpecialCharsForName: true });
      });
   });

   describe("invalidAssetItemName", () => {
      it("returns null for valid name", () => {
         expect(FormValidators.invalidAssetItemName(ctrl("MyDashboard"))).toBeNull();
         expect(FormValidators.invalidAssetItemName(ctrl("Sales Report 2024"))).toBeNull();
      });

      it("returns error for invalid chars", () => {
         expect(FormValidators.invalidAssetItemName(ctrl("my/report"))).toEqual({ invalidAssetItemName: true });
         expect(FormValidators.invalidAssetItemName(ctrl('my"report'))).toEqual({ invalidAssetItemName: true });
         expect(FormValidators.invalidAssetItemName(ctrl("my<report>"))).toEqual({ invalidAssetItemName: true });
      });
   });

   describe("doesNotStartWithNumber", () => {
      it("returns null when not starting with digit", () => {
         expect(FormValidators.doesNotStartWithNumber(ctrl("name"))).toBeNull();
         expect(FormValidators.doesNotStartWithNumber(ctrl("_field"))).toBeNull();
         expect(FormValidators.doesNotStartWithNumber(ctrl(""))).toBeNull();
         expect(FormValidators.doesNotStartWithNumber(ctrl(null))).toBeNull();
      });

      it("returns error when starting with digit", () => {
         expect(FormValidators.doesNotStartWithNumber(ctrl("1name"))).toEqual({ doesNotStartWithNumber: true });
         expect(FormValidators.doesNotStartWithNumber(ctrl("0"))).toEqual({ doesNotStartWithNumber: true });
      });
   });

   describe("doesNotStartWithBlankSpace", () => {
      it("returns null for non-blank-starting values", () => {
         expect(FormValidators.doesNotStartWithBlankSpace(ctrl("name"))).toBeNull();
         expect(FormValidators.doesNotStartWithBlankSpace(ctrl(""))).toBeNull();
      });

      it("returns error when starting with whitespace", () => {
         expect(FormValidators.doesNotStartWithBlankSpace(ctrl(" name"))).toEqual({ doesNotStartWithBlankSpace: true });
         expect(FormValidators.doesNotStartWithBlankSpace(ctrl("\tname"))).toEqual({ doesNotStartWithBlankSpace: true });
      });
   });

   describe("doesNotStartWithNumberOrLetter", () => {
      it("returns null when starting with letter or digit", () => {
         expect(FormValidators.doesNotStartWithNumberOrLetter(ctrl("abc"))).toBeNull();
         expect(FormValidators.doesNotStartWithNumberOrLetter(ctrl("123"))).toBeNull();
         expect(FormValidators.doesNotStartWithNumberOrLetter(ctrl(null))).toBeNull();
         expect(FormValidators.doesNotStartWithNumberOrLetter(ctrl(""))).toBeNull();
      });

      it("returns error when starting with non-alphanumeric", () => {
         expect(FormValidators.doesNotStartWithNumberOrLetter(ctrl("_name"))).toEqual({ doesNotStartWithNumberOrLetter: true });
         expect(FormValidators.doesNotStartWithNumberOrLetter(ctrl("@name"))).toEqual({ doesNotStartWithNumberOrLetter: true });
      });
   });

   describe("notWhiteSpace", () => {
      it("returns null for non-whitespace content", () => {
         expect(FormValidators.notWhiteSpace(ctrl("a"))).toBeNull();
         expect(FormValidators.notWhiteSpace(ctrl("  a  "))).toBeNull();
         expect(FormValidators.notWhiteSpace(ctrl(""))).toBeNull();
         expect(FormValidators.notWhiteSpace(ctrl(null))).toBeNull();
      });

      it("returns error for all-whitespace string", () => {
         expect(FormValidators.notWhiteSpace(ctrl("   "))).toEqual({ notWhiteSpace: true });
         expect(FormValidators.notWhiteSpace(ctrl("\t\n"))).toEqual({ notWhiteSpace: true });
      });
   });

   describe("alphanumericalCharacters", () => {
      it("returns null for alphanumeric values", () => {
         expect(FormValidators.alphanumericalCharacters(ctrl("abc123"))).toBeNull();
         expect(FormValidators.alphanumericalCharacters(ctrl("ABC"))).toBeNull();
         expect(FormValidators.alphanumericalCharacters(ctrl(""))).toBeNull();
      });

      it("returns error for non-alphanumeric characters", () => {
         expect(FormValidators.alphanumericalCharacters(ctrl("abc-def"))).toEqual({ alphanumericalCharacters: true });
         expect(FormValidators.alphanumericalCharacters(ctrl("abc 123"))).toEqual({ alphanumericalCharacters: true });
      });
   });

   describe("positiveNonZeroOrNull", () => {
      it("returns null for valid positive numbers", () => {
         expect(FormValidators.positiveNonZeroOrNull(ctrl(1))).toBeNull();
         expect(FormValidators.positiveNonZeroOrNull(ctrl(100))).toBeNull();
         expect(FormValidators.positiveNonZeroOrNull(ctrl(null))).toBeNull();
         expect(FormValidators.positiveNonZeroOrNull(ctrl(""))).toBeNull();
      });

      it("returns error for zero or negative", () => {
         expect(FormValidators.positiveNonZeroOrNull(ctrl(0))).toEqual({ lessThanEqualToZero: true });
         expect(FormValidators.positiveNonZeroOrNull(ctrl(-5))).toEqual({ lessThanEqualToZero: true });
      });

      it("returns error for value exceeding max int", () => {
         expect(FormValidators.positiveNonZeroOrNull(ctrl(2147483648))).toEqual({ lessThanEqualToZero: true });
      });
   });

   describe("positiveNonZeroInRange", () => {
      it("returns null for values in range", () => {
         expect(FormValidators.positiveNonZeroInRange(ctrl(1))).toBeNull();
         expect(FormValidators.positiveNonZeroInRange(ctrl(2147483647))).toBeNull();
      });

      it("returns error for zero", () => {
         expect(FormValidators.positiveNonZeroInRange(ctrl(0))).toEqual({ positiveNonZeroInRange: true });
      });

      it("returns error for negative", () => {
         expect(FormValidators.positiveNonZeroInRange(ctrl(-1))).toEqual({ positiveNonZeroInRange: true });
      });

      it("returns error for value exceeding max int", () => {
         expect(FormValidators.positiveNonZeroInRange(ctrl(2147483648))).toEqual({ positiveNonZeroInRange: true });
      });
   });

   describe("positiveIntegerInRange", () => {
      it("returns null for 0 and positive values", () => {
         expect(FormValidators.positiveIntegerInRange(ctrl(0))).toBeNull();
         expect(FormValidators.positiveIntegerInRange(ctrl(100))).toBeNull();
         expect(FormValidators.positiveIntegerInRange(ctrl(2147483647))).toBeNull();
      });

      it("returns error for negative", () => {
         expect(FormValidators.positiveIntegerInRange(ctrl(-1))).toEqual({ positiveIntegerInRange: true });
      });

      it("returns error for value exceeding max int", () => {
         expect(FormValidators.positiveIntegerInRange(ctrl(2147483648))).toEqual({ positiveIntegerInRange: true });
      });
   });

   describe("integerInRange", () => {
      it("returns null for valid integers", () => {
         const validator = FormValidators.integerInRange();
         expect(validator(ctrl(0))).toBeNull();
         expect(validator(ctrl(-2147483648))).toBeNull();
         expect(validator(ctrl(2147483647))).toBeNull();
      });

      it("returns error for out-of-range values", () => {
         const validator = FormValidators.integerInRange();
         expect(validator(ctrl(2147483648))).toEqual({ integerInRange: true });
         expect(validator(ctrl(-2147483649))).toEqual({ integerInRange: true });
      });

      it("supports split mode", () => {
         const validator = FormValidators.integerInRange(true);
         expect(validator(ctrl("1,2,3"))).toBeNull();
         expect(validator(ctrl("1,abc,3"))).toEqual({ integerInRange: true });
      });
   });

   describe("isInteger", () => {
      it("returns null for valid integers", () => {
         const validator = FormValidators.isInteger();
         expect(validator(ctrl("42"))).toBeNull();
         expect(validator(ctrl("-5"))).toBeNull();
         expect(validator(ctrl("+10"))).toBeNull();
      });

      it("returns error for floats", () => {
         const validator = FormValidators.isInteger();
         expect(validator(ctrl("3.14"))).toEqual({ isInteger: true });
      });

      it("returns error for non-numeric", () => {
         const validator = FormValidators.isInteger();
         expect(validator(ctrl("abc"))).toEqual({ isInteger: true });
      });

      it("returns null for null value", () => {
         const validator = FormValidators.isInteger();
         expect(validator(ctrl(null))).toBeNull();
      });
   });

   describe("isFloat", () => {
      it("returns null for valid floats", () => {
         const validator = FormValidators.isFloat();
         expect(validator(ctrl("3.14"))).toBeNull();
         expect(validator(ctrl("42"))).toBeNull();
         expect(validator(ctrl("0.5"))).toBeNull();
      });

      it("returns error for non-float", () => {
         const validator = FormValidators.isFloat();
         expect(validator(ctrl("-3.14"))).toEqual({ isFloat: true });
         expect(validator(ctrl("abc"))).toEqual({ isFloat: true });
      });
   });

   describe("isBoolean", () => {
      it("returns null for true/false strings", () => {
         const validator = FormValidators.isBoolean();
         expect(validator(ctrl("true"))).toBeNull();
         expect(validator(ctrl("false"))).toBeNull();
      });

      it("returns error for other strings", () => {
         const validator = FormValidators.isBoolean();
         expect(validator(ctrl("yes"))).toEqual({ isBoolean: true });
         expect(validator(ctrl("1"))).toEqual({ isBoolean: true });
         expect(validator(ctrl("True"))).toEqual({ isBoolean: true });
      });
   });

   describe("isValidWindowsFileName", () => {
      it("returns null for valid file names", () => {
         expect(FormValidators.isValidWindowsFileName(ctrl("myfile.txt"))).toBeNull();
         expect(FormValidators.isValidWindowsFileName(ctrl("report 2024"))).toBeNull();
         expect(FormValidators.isValidWindowsFileName(ctrl(null))).toBeNull();
         expect(FormValidators.isValidWindowsFileName(ctrl(""))).toBeNull();
      });

      it("returns error for invalid Windows chars", () => {
         expect(FormValidators.isValidWindowsFileName(ctrl("file/name"))).toEqual({ containsInvalidWindowsChars: true });
         expect(FormValidators.isValidWindowsFileName(ctrl("file:name"))).toEqual({ containsInvalidWindowsChars: true });
         expect(FormValidators.isValidWindowsFileName(ctrl("file*name"))).toEqual({ containsInvalidWindowsChars: true });
         expect(FormValidators.isValidWindowsFileName(ctrl('file"name'))).toEqual({ containsInvalidWindowsChars: true });
      });

      it("returns error for names starting with dot", () => {
         expect(FormValidators.isValidWindowsFileName(ctrl(".hidden"))).toEqual({ containsInvalidWindowsChars: true });
         expect(FormValidators.isValidWindowsFileName(ctrl("  .name"))).toEqual({ containsInvalidWindowsChars: true });
      });
   });

   describe("exists", () => {
      it("returns null when value not in list", () => {
         const validator = FormValidators.exists(["existing1", "existing2"]);
         expect(validator(ctrl("newName"))).toBeNull();
      });

      it("returns error when value is in list", () => {
         const validator = FormValidators.exists(["existing1", "existing2"]);
         expect(validator(ctrl("existing1"))).toEqual({ exists: true });
         expect(validator(ctrl("existing2"))).toEqual({ exists: true });
      });

      it("returns null for empty value", () => {
         const validator = FormValidators.exists(["existing1"]);
         expect(validator(ctrl(""))).toBeNull();
         expect(validator(ctrl(null))).toBeNull();
      });

      it("supports case-insensitive mode", () => {
         const validator = FormValidators.exists(["ExistingName"], { ignoreCase: true });
         expect(validator(ctrl("existingname"))).toEqual({ exists: true });
         expect(validator(ctrl("EXISTINGNAME"))).toEqual({ exists: true });
      });

      it("supports originalValue exclusion", () => {
         const validator = FormValidators.exists(["existing1"], { originalValue: "existing1" });
         expect(validator(ctrl("existing1"))).toBeNull();
         expect(validator(ctrl("existing2"))).toBeNull();
      });

      it("supports trimSurroundingWhitespace", () => {
         const validator = FormValidators.exists(["existing1"], { trimSurroundingWhitespace: true });
         expect(validator(ctrl("  existing1  "))).toEqual({ exists: true });
      });
   });

   describe("smallerThan", () => {
      it("returns null when min < max", () => {
         const validator = FormValidators.smallerThan("min", "max");
         const group = new UntypedFormGroup({
            min: new UntypedFormControl(5),
            max: new UntypedFormControl(10)
         });
         expect(validator(group)).toBeNull();
      });

      it("returns error when min >= max (orEqualTo=true default)", () => {
         const validator = FormValidators.smallerThan("min", "max");
         const group = new UntypedFormGroup({
            min: new UntypedFormControl(10),
            max: new UntypedFormControl(5)
         });
         expect(validator(group)).toEqual({ greaterThan: true });
      });

      it("returns error when min equals max (orEqualTo=true)", () => {
         const validator = FormValidators.smallerThan("min", "max", true);
         const group = new UntypedFormGroup({
            min: new UntypedFormControl(5),
            max: new UntypedFormControl(5)
         });
         expect(validator(group)).toEqual({ greaterThan: true });
      });

      it("returns null when min equals max (orEqualTo=false)", () => {
         const validator = FormValidators.smallerThan("min", "max", false);
         const group = new UntypedFormGroup({
            min: new UntypedFormControl(5),
            max: new UntypedFormControl(5)
         });
         expect(validator(group)).toBeNull();
      });

      it("returns null when either value is null", () => {
         const validator = FormValidators.smallerThan("min", "max");
         const group = new UntypedFormGroup({
            min: new UntypedFormControl(null),
            max: new UntypedFormControl(10)
         });
         expect(validator(group)).toBeNull();
      });
   });

   describe("assetNameMyReports", () => {
      it("returns error for reserved names", () => {
         expect(FormValidators.assetNameMyReports(ctrl("My Dashboards"))).toEqual({ assetNameMyReports: true });
         expect(FormValidators.assetNameMyReports(ctrl("My Alerts"))).toEqual({ assetNameMyReports: true });
      });

      it("returns null for other names", () => {
         expect(FormValidators.assetNameMyReports(ctrl("Custom Report"))).toBeNull();
         expect(FormValidators.assetNameMyReports(ctrl(""))).toBeNull();
      });
   });


   describe("isValidDataSourceFolderName", () => {
      it("returns null for valid folder names", () => {
         expect(FormValidators.isValidDataSourceFolderName(ctrl("MyFolder"))).toBeNull();
         expect(FormValidators.isValidDataSourceFolderName(ctrl("Folder 123"))).toBeNull();
         expect(FormValidators.isValidDataSourceFolderName(ctrl(null))).toBeNull();
         expect(FormValidators.isValidDataSourceFolderName(ctrl(""))).toBeNull();
      });

      it("returns error for invalid chars", () => {
         expect(FormValidators.isValidDataSourceFolderName(ctrl("Folder/Name"))).toEqual({ containsSpecialCharsForName: true });
         expect(FormValidators.isValidDataSourceFolderName(ctrl("Folder*Name"))).toEqual({ containsSpecialCharsForName: true });
         expect(FormValidators.isValidDataSourceFolderName(ctrl("Folder?Name"))).toEqual({ containsSpecialCharsForName: true });
      });

      it("returns error for reserved name 'Cubes'", () => {
         expect(FormValidators.isValidDataSourceFolderName(ctrl("Cubes"))).toEqual({ isDefaultCubesName: true });
      });
   });

   describe("checkTemplateName", () => {
      it("returns null for valid .srt template path", () => {
         expect(FormValidators.checkTemplateName(false, "/my/template.srt")).toBeNull();
         expect(FormValidators.checkTemplateName(false, "/report.xml")).toBeNull();
         expect(FormValidators.checkTemplateName(false, "/a.sro")).toBeNull();
      });

      it("returns error for upload template missing valid extension", () => {
         expect(FormValidators.checkTemplateName(true, "report.txt")).toEqual({ illegalUploadTemplateName: true });
         expect(FormValidators.checkTemplateName(true, "report.pdf")).toEqual({ illegalUploadTemplateName: true });
      });

      it("returns error for non-upload template not starting with /", () => {
         expect(FormValidators.checkTemplateName(false, "mytemplate.srt")).toEqual({ notStartWithBackslashTemplate: true });
      });

      it("returns error for all-star name", () => {
         expect(FormValidators.checkTemplateName(true, "***")).toEqual({ containsSpecialCharsForName: true });
         expect(FormValidators.checkTemplateName(true, "*")).toEqual({ containsSpecialCharsForName: true });
      });
   });

   describe("tableIdentifier", () => {
      it("returns null for valid table identifiers", () => {
         expect(FormValidators.tableIdentifier(ctrl("MyTable"))).toBeNull();
         expect(FormValidators.tableIdentifier(ctrl("table123"))).toBeNull();
         expect(FormValidators.tableIdentifier(ctrl("MY_TABLE"))).toBeNull();
      });

      it("returns error for invalid identifier chars", () => {
         expect(FormValidators.tableIdentifier(ctrl("my.table"))).toEqual({ tableIdentifier: true });
         expect(FormValidators.tableIdentifier(ctrl("my-table"))).toEqual({ tableIdentifier: true });
         expect(FormValidators.tableIdentifier(ctrl("my table"))).toBeNull();
      });
   });

   describe("nameStartWithCharDigit", () => {
      it("returns null when name starts with letter or digit", () => {
         expect(FormValidators.nameStartWithCharDigit(ctrl("Sales"))).toBeNull();
         expect(FormValidators.nameStartWithCharDigit(ctrl("1stReport"))).toBeNull();
      });

      it("returns error when name starts with non-alphanumeric", () => {
         expect(FormValidators.nameStartWithCharDigit(ctrl("_private"))).toEqual({ nameStartWithCharDigit: true });
         expect(FormValidators.nameStartWithCharDigit(ctrl(" space"))).toEqual({ nameStartWithCharDigit: true });
      });
   });
});
