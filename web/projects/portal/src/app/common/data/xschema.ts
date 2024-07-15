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
export class XSchema {
   public static get STRING(): string {
      return "string";
   }

   /**
    * Boolean type.
    */
   public static get BOOLEAN(): string {
      return "boolean";
   }

   /**
    * Float type.
    */
   public static get FLOAT(): string {
      return "float";
   }

   /**
    * Double type.
    */
   public static get DOUBLE(): string {
      return "double";
   }

   /**
    * Character type.
    */
   public static get CHARACTER(): string {
      return "character";
   }

   /**
    * Character type.
    */
   public static get CHAR(): string {
      return "char";
   }

   /**
    * Byte type.
    */
   public static get BYTE(): string {
      return "byte";
   }

   /**
    * Short type.
    */
   public static get SHORT(): string {
      return "short";
   }

   /**
    * Integer type.
    */
   public static get INTEGER(): string {
      return "integer";
   }

   /**
    * Long type.
    */
   public static get LONG(): string {
      return "long";
   }

   /**
    * Time instant type.
    */
   public static get TIME_INSTANT(): string {
      return "timeInstant";
   }

   /**
    * Date type.
    */
   public static get DATE(): string {
      return "date";
   }

   /**
    * Time type.
    */
   public static get TIME(): string {
      return "time";
   }

   /**
    * Enum type.
    */
   public static get ENUM(): string {
      return "enum";
   }

   /**
    * User defined type.
    */
   public static get USER_DEFINED(): string {
      return "userDefined";
   }

   public static isNumericType(type: String): boolean {
      return XSchema.BYTE == type || XSchema.SHORT == type || XSchema.INTEGER == type ||
         XSchema.LONG == type || XSchema.FLOAT == type || XSchema.DOUBLE == type;
   }

   /**
    * Check if is a Integer/LONG/SHORT/BYTE type.
    * @param type the specified data type.
    * @return <tt>true</tt> if numeric, <tt>false</tt> otherwise.
    */
   public static isIntegerType(type: string) {
      return XSchema.BYTE == type || XSchema.SHORT == type ||
         XSchema.INTEGER == type || XSchema.LONG == type;
   }

   /**
    * Check if is a decimal type.
    * @param type the specified data type.
    * @return <tt>true</tt> if decimal, <tt>false</tt> otherwise.
    */
   public static isDecimalType(type: string) {
      return XSchema.FLOAT == type || XSchema.DOUBLE == type;
   }

   /**
    * Check if is a date type.
    * @param type the specified data type.
    * @return <tt>true</tt> if date, <tt>false</tt> otherwise.
    */
   public static isDateType(type: string): boolean {
      return XSchema.DATE == type || XSchema.TIME == type || XSchema.TIME_INSTANT == type;
   }

   /**
    * Check if is a string type.
    * @param type the specified data type.
    * @return <tt>true</tt> if string, <tt>false</tt> otherwise.
    */
   public static isStringType(type: string): boolean {
      return XSchema.STRING === type || XSchema.CHARACTER === type || XSchema.CHAR === type;
   }

   public static get standardDataTypeList(): any[] {
      return [
         { label: "_#(js:String)", data: XSchema.STRING },
         { label: "_#(js:Integer)", data: XSchema.INTEGER },
         { label: "_#(js:Double)", data: XSchema.DOUBLE },
         { label: "_#(js:Date)", data: XSchema.DATE },
         { label: "_#(js:Time)", data: XSchema.TIME },
         { label: "_#(js:TimeInstant)", data: XSchema.TIME_INSTANT },
         { label: "_#(js:Boolean)", data: XSchema.BOOLEAN },
         { label: "_#(js:Float)", data: XSchema.FLOAT },
         { label: "_#(js:Character)", data: XSchema.CHARACTER },
         { label: "_#(js:Byte)", data: XSchema.BYTE },
         { label: "_#(js:Short)", data: XSchema.SHORT },
         { label: "_#(js:Long)", data: XSchema.LONG }];
   }

   public static get scheduledTaskDataTypeList(): any[] {
      return [
         { label: "_#(js:String)", data: XSchema.STRING },
         { label: "_#(js:Integer)", data: XSchema.INTEGER },
         { label: "_#(js:Double)", data: XSchema.DOUBLE },
         { label: "_#(js:Date)", data: XSchema.DATE },
         { label: "_#(js:Time)", data: XSchema.TIME },
         { label: "_#(js:TimeInstant)", data: XSchema.TIME_INSTANT },
         { label: "_#(js:Boolean)", data: XSchema.BOOLEAN }];
   }
}
