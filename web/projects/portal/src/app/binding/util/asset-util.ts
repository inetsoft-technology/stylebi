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
import { AssetEntry } from "../../../../../shared/data/asset-entry";
import { AggregateRef } from "../../common/data/aggregate-ref";
import { DataRef } from "../../common/data/data-ref";
import { DataRefType } from "../../common/data/data-ref-type";
import { DateRangeRef } from "../../common/data/date-range-ref";
import { XSchema } from "../../common/data/xschema";
import { AggregateInfo } from "../data/aggregate-info";
import { ChartAggregateRef } from "../data/chart/chart-aggregate-ref";
import { ColumnRef } from "../data/column-ref";
import { AggregateFormula } from "./aggregate-formula";

export class AssetUtil {

   public static get STRING_FORMULAS(): AggregateFormula[] {
      return this.NUMBER_FORMULAS;
   }

   public static get NUMBER_FORMULAS(): AggregateFormula[] {
      return [
         AggregateFormula.SUM,
         AggregateFormula.AVG,
         AggregateFormula.MAX,
         AggregateFormula.MIN,
         AggregateFormula.COUNT_ALL,
         AggregateFormula.COUNT_DISTINCT,
         AggregateFormula.FIRST,
         AggregateFormula.LAST,
         AggregateFormula.STANDARD_DEVIATION,
         AggregateFormula.VARIANCE,
         AggregateFormula.POPULATION_STANDARD_DEVIATION,
         AggregateFormula.POPULATION_VARIANCE,
         AggregateFormula.CORRELATION,
         AggregateFormula.COVARIANCE,
         AggregateFormula.MEDIAN,
         AggregateFormula.MODE,
         AggregateFormula.WEIGHTED_AVG,
         AggregateFormula.PRODUCT,
         AggregateFormula.CONCAT,
         AggregateFormula.NTH_LARGEST,
         AggregateFormula.NTH_SMALLEST,
         AggregateFormula.NTH_MOST_FREQUENT,
         AggregateFormula.PTH_PERCENTILE
      ];
   }

   public static get CUBE_FORMULAS(): AggregateFormula[] {
      return [
         AggregateFormula.SUM, AggregateFormula.AVG,
         AggregateFormula.AGGREGATE, AggregateFormula.MAX,
         AggregateFormula.MIN, AggregateFormula.COUNT_ALL,
         AggregateFormula.COUNT_DISTINCT,
         AggregateFormula.MEDIAN,
         AggregateFormula.VARIANCE,
         AggregateFormula.STANDARD_DEVIATION,
         AggregateFormula.POPULATION_VARIANCE,
         AggregateFormula.POPULATION_STANDARD_DEVIATION
      ];
   }

   public static get DATE_FORMULAS(): AggregateFormula[] {
      return [
         AggregateFormula.MAX, AggregateFormula.MIN,
         AggregateFormula.COUNT_ALL, AggregateFormula.COUNT_DISTINCT,
         AggregateFormula.FIRST, AggregateFormula.LAST,
         AggregateFormula.CONCAT,
         AggregateFormula.NTH_LARGEST,
         AggregateFormula.NTH_SMALLEST,
         AggregateFormula.NTH_MOST_FREQUENT,
         AggregateFormula.PTH_PERCENTILE
      ];
   }

   public static get BOOL_FORMULAS(): AggregateFormula[] {
      return [
         AggregateFormula.COUNT_ALL, AggregateFormula.COUNT_DISTINCT,
         AggregateFormula.FIRST, AggregateFormula.LAST
      ];
   }

   public static getDefaultFormula(ref: DataRef): AggregateFormula {
      let refType: number = ref.refType;

      if(refType & DataRefType.AGG_CALC) {
         return AggregateFormula.NONE;
      }

      if((refType & DataRefType.CUBE_MEASURE) == DataRefType.CUBE_MEASURE) {
         return AggregateFormula.NONE;
      }

      // measure?
      if((refType & DataRefType.MEASURE) != 0) {
         let formula = AggregateFormula.getFormula(ref.defaultFormula);

         if(formula != null) {
            return formula;
         }
      }

      // dimesion?
      if((refType & DataRefType.DIMENSION) != 0) {
         return AggregateFormula.COUNT_ALL;
      }
      else {
         return AggregateFormula.getDefaultFormula(ref.dataType);
      }
   }

   public static isStringAggCalcField(ref: ChartAggregateRef): boolean {
      return ref.refType == DataRefType.AGG_CALC;
   }

   public static findAggregateRef(ainfo: AggregateInfo, name: string): AggregateRef {
      for(let i = 0; ainfo != null && i < ainfo.aggregates.length; i++) {
         let ref: AggregateRef = ainfo.aggregates[i];

         if(ref != null && ref.name == name) {
            return ref;
         }
      }

      return null;
   }

   public static getDefaultFormulas(): AggregateFormula[] {
      return this.getFormulas(null);
   }

   /**
    * Get available aggregate formula model.
    */
   public static getAggregateModel(column: DataRef,
                                   excludeNone?: boolean): AggregateFormula[]
   {
      return this.getFormulas(column == null ? null : column.dataType, excludeNone);
   }

   public static getFormulas(type: string, excludeNone?: boolean) {
      let formulas: AggregateFormula[] = [];

      if(type == XSchema.STRING) {
         formulas = AssetUtil.STRING_FORMULAS;
      }
      else if(type == XSchema.DATE || type == XSchema.TIME_INSTANT ||
         type == XSchema.TIME)
      {
         formulas = AssetUtil.DATE_FORMULAS;
      }
      else if(type == XSchema.BOOLEAN) {
         formulas = AssetUtil.BOOL_FORMULAS;
      }
      else if(type == "cube") {
         formulas = AssetUtil.CUBE_FORMULAS;
      }
      else {
         formulas = AssetUtil.NUMBER_FORMULAS;
      }

      if(!excludeNone) {
         let nformulas: AggregateFormula[] = [];
         nformulas.push(AggregateFormula.NONE);
         return nformulas.concat(formulas);
      }

      return formulas;
   }

   public static getOriginalColumn(column: ColumnRef, cols: DataRef[]): ColumnRef {
      let ref = column.dataRefModel;

      if(ref == undefined || ref.classType !== "DateRangeRef") {
         return column;
      }

      let subRef = (<DateRangeRef> ref).ref;

      if(ref.name.indexOf("(") < 0 || ref.name.indexOf(")") < 0  || subRef == undefined) {
         return column;
      }

      for(let currRef of cols) {
         if(currRef.classType !== "ColumnRef") {
            continue;
         }

         let colRef = <ColumnRef> currRef;

         if(colRef === column) {
            continue;
         }

         // if base ref matches
         if(subRef.entity === colRef.entity && subRef.attribute === colRef.attribute) {
            return colRef;
         }
      }

      return column;
   }

   /**
    * Check if two data types are mergeable.
    *
    * @param dtype1 the specified data type a.
    * @param dtype2 the specified data type b.
    * @return <tt>true</tt> if mergeable, <tt>false</tt> otherwise.
    */
   public static isMergeable(dtype1: string, dtype2: string): boolean {
      if(dtype1 === dtype2) {
         return true;
      }
      else if(AssetUtil.isStringType(dtype1) && AssetUtil.isStringType(dtype2)) {
         return true;
      }
      else if(AssetUtil.isNumberType(dtype1) && AssetUtil.isNumberType(dtype2)) {
         return true;
      }
      else if(AssetUtil.isDateType(dtype1) && AssetUtil.isDateType(dtype2)) {
         return true;
      }

      return false;
   }

   /**
    * Check if is a string type.
    *
    * @param dtype the specified data type.
    * @return <tt>true</tt> if is, <tt>false</tt> otherwise.
    */
   public static isStringType(dtype: string): boolean {
      dtype = dtype != null ? dtype.toLocaleLowerCase() : dtype;

      if(dtype === XSchema.STRING || dtype === XSchema.CHAR) {
         return true;
      }

      return false;
   }

   /**
    * Check if is a number data type.
    *
    * @param dtype the specified data type.
    * @return <tt>true</tt> if is, <tt>false</tt> otherwise.
    */
   public static isNumberType(dtype: string): boolean {
      dtype = dtype != null ? dtype.toLocaleLowerCase() : dtype;

      if(dtype === XSchema.FLOAT || dtype === XSchema.DOUBLE ||
         dtype === XSchema.BYTE || dtype === XSchema.SHORT ||
         dtype === XSchema.INTEGER || dtype === XSchema.LONG)
      {
         return true;
      }

      return false;
   }

   /**
    * Check if is a date data type.
    *
    * @param dtype the specified data type.
    * @return <tt>true</tt> if is, <tt>false</tt> otherwise.
    */
   public static isDateType(dtype: string): boolean {
      dtype = dtype != null ? dtype.toLocaleLowerCase() : dtype;

      if(dtype === XSchema.DATE || dtype === XSchema.TIME_INSTANT) {
         return true;
      }

      return false;
   }

   public static findRefByName(refs: DataRef[] = [], name: string): DataRef {
      for(let ref of refs) {
         if(ref.name == name) {
            return ref;
         }
      }

      return null;
   }

   public static isMeasure(entry: AssetEntry): boolean {
      if(!!!entry || !!!entry.properties) {
         return false;
      }

      let refType: string = entry.properties["refType"];
      let rtype: number = refType == null ? DataRefType.NONE : parseInt(refType, 10);

      return rtype == DataRefType.NONE
         ? AssetUtil.isNumberType(entry.properties["dtype"])
         : (rtype & DataRefType.MEASURE) != 0 || rtype == DataRefType.AGG_CALC;
   }

   public static getParentPath(path: string): string {
      if(path === "/") {
         return null;
      }

      let index = path.lastIndexOf("/");
      return index >= 0 ? path.substring(0, index) : "/";
   }
}
