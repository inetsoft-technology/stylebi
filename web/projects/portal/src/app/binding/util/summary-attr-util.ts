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
import { AggregateFormula } from "./aggregate-formula";
import { DataRef } from "../../common/data/data-ref";
import { XSchema } from "../../common/data/xschema";
import { StyleConstants } from "../../common/util/style-constants";
import { XConstants } from "../../common/util/xconstants";

export class SummaryAttrUtil {
   public static NONE_FORMULA: number = 0;
   public static WITH_FORMULA: number = 1;
   public static N_FORMULA: number = 2;
   public static P_FORMULA: number = 3;
   public static PERCENTAGE_FORMULA: number = 4;
   public static WITH_PERCENTAGE_FORMULA: number = 5;
   public static N_P_DEFAULT_NUM = 1;
   private static formulasMap: Map<string, number>;

   public static isPercentageFormula(formulaName: string): boolean {
      return !formulaName ? false :
         this.FORMULA_MAP.get(formulaName.toLowerCase()) == this.PERCENTAGE_FORMULA ||
         this.FORMULA_MAP.get(formulaName.toLowerCase()) == this.WITH_PERCENTAGE_FORMULA;
   }

   public static isWithFormula(formulaName: string): boolean {
      return !formulaName ? false :
         this.FORMULA_MAP.get(formulaName.toLowerCase()) == this.WITH_FORMULA ||
         this.FORMULA_MAP.get(formulaName.toLowerCase()) == this.WITH_PERCENTAGE_FORMULA;
   }

   public static isByFormula(formulaName: string): boolean {
      return !formulaName ? false :
         this.FORMULA_MAP.get(formulaName.toLowerCase()) == this.WITH_PERCENTAGE_FORMULA;
   }

   public static isPthFormula(formulaName: string): boolean {
      return !formulaName ? false :
         this.FORMULA_MAP.get(formulaName.toLowerCase()) == this.P_FORMULA;
   }

   public static isNthFormula(formulaName: string): boolean {
      return !formulaName ? false :
         this.FORMULA_MAP.get(formulaName.toLowerCase()) == this.N_FORMULA;
   }

   /**
    * is NthFormula or PthFormula
    * @param formulaName
    */
   public static npVisible(formulaName: string): boolean {
      return SummaryAttrUtil.isNthFormula(formulaName) || SummaryAttrUtil.isPthFormula(formulaName);
   }

   private static addFormulaMapping(map: Map<string, number>,
      formula: AggregateFormula, value: number): void
   {
      if(map == null) {
         map = new Map<string, number>();
      }

      if(formula == null) {
         return;
      }

      map.set(formula.name.toLowerCase(), value);
      map.set(formula.label.toLowerCase(), value);
      map.set(formula.formulaName.toLowerCase(), value);
   }

   public static get FORMULA_MAP(): Map<string, number> {
      if(SummaryAttrUtil.formulasMap == null) {
         const map = new Map<string, number>();
         SummaryAttrUtil.addFormulaMapping(map, AggregateFormula.CORRELATION, this.WITH_FORMULA);
         SummaryAttrUtil.addFormulaMapping(map, AggregateFormula.COVARIANCE, this.WITH_FORMULA);
         SummaryAttrUtil.addFormulaMapping(map, AggregateFormula.WEIGHTED_AVG, this.WITH_FORMULA);
         SummaryAttrUtil.addFormulaMapping(map, AggregateFormula.FIRST, this.WITH_PERCENTAGE_FORMULA);
         SummaryAttrUtil.addFormulaMapping(map, AggregateFormula.LAST, this.WITH_PERCENTAGE_FORMULA);

         SummaryAttrUtil.addFormulaMapping(map, AggregateFormula.NTH_LARGEST, this.N_FORMULA);
         SummaryAttrUtil.addFormulaMapping(map, AggregateFormula.NTH_MOST_FREQUENT, this.N_FORMULA);
         SummaryAttrUtil.addFormulaMapping(map, AggregateFormula.NTH_SMALLEST, this.N_FORMULA);

         SummaryAttrUtil.addFormulaMapping(map, AggregateFormula.PTH_PERCENTILE, this.P_FORMULA);

         SummaryAttrUtil.addFormulaMapping(map, AggregateFormula.AVG, this.PERCENTAGE_FORMULA);
         SummaryAttrUtil.addFormulaMapping(map, AggregateFormula.COUNT_ALL, this.PERCENTAGE_FORMULA);
         SummaryAttrUtil.addFormulaMapping(map, AggregateFormula.COUNT_DISTINCT, this.PERCENTAGE_FORMULA);
         SummaryAttrUtil.addFormulaMapping(map, AggregateFormula.MAX, this.PERCENTAGE_FORMULA);
         SummaryAttrUtil.addFormulaMapping(map, AggregateFormula.MIN, this.PERCENTAGE_FORMULA);
         SummaryAttrUtil.addFormulaMapping(map, AggregateFormula.SUM, this.PERCENTAGE_FORMULA);
         SummaryAttrUtil.addFormulaMapping(map, AggregateFormula.PRODUCT, this.PERCENTAGE_FORMULA);
         SummaryAttrUtil.addFormulaMapping(map, AggregateFormula.STANDARD_DEVIATION, this.PERCENTAGE_FORMULA);
         SummaryAttrUtil.addFormulaMapping(map, AggregateFormula.VARIANCE, this.PERCENTAGE_FORMULA);
         SummaryAttrUtil.addFormulaMapping(map, AggregateFormula.POPULATION_STANDARD_DEVIATION, this.PERCENTAGE_FORMULA);
         SummaryAttrUtil.addFormulaMapping(map, AggregateFormula.POPULATION_VARIANCE, this.PERCENTAGE_FORMULA);
         SummaryAttrUtil.addFormulaMapping(map, AggregateFormula.MEDIAN, this.PERCENTAGE_FORMULA);
         SummaryAttrUtil.addFormulaMapping(map, AggregateFormula.MODE, this.PERCENTAGE_FORMULA);
         SummaryAttrUtil.addFormulaMapping(map, AggregateFormula.NONE, this.PERCENTAGE_FORMULA);
         SummaryAttrUtil.formulasMap = map;
      }

      return SummaryAttrUtil.formulasMap;
   }

   public static get NUMBER_FORMULAS(): AggregateFormula[] {
      return [
         AggregateFormula.NONE,
         AggregateFormula.SUM,
         AggregateFormula.AVG,
         AggregateFormula.MAX,
         AggregateFormula.MIN,
         AggregateFormula.COUNT_ALL,
         AggregateFormula.COUNT_DISTINCT,
         AggregateFormula.FIRST,
         AggregateFormula.LAST,
         AggregateFormula.PRODUCT,
         AggregateFormula.CONCAT,
         AggregateFormula.STANDARD_DEVIATION,
         AggregateFormula.VARIANCE,
         AggregateFormula.POPULATION_STANDARD_DEVIATION,
         AggregateFormula.POPULATION_VARIANCE,
         AggregateFormula.CORRELATION,
         AggregateFormula.COVARIANCE,
         AggregateFormula.MEDIAN,
         AggregateFormula.MODE,
         AggregateFormula.NTH_LARGEST,
         AggregateFormula.NTH_MOST_FREQUENT,
         AggregateFormula.NTH_SMALLEST,
         AggregateFormula.PTH_PERCENTILE,
         AggregateFormula.WEIGHTED_AVG
      ];
   }

   public static get DATE_FORMULAS(): AggregateFormula[] {
      return [
         AggregateFormula.NONE, AggregateFormula.MAX, AggregateFormula.MIN,
         AggregateFormula.COUNT_ALL, AggregateFormula.COUNT_DISTINCT,
         AggregateFormula.FIRST, AggregateFormula.LAST, AggregateFormula.NTH_LARGEST,
         AggregateFormula.NTH_MOST_FREQUENT, AggregateFormula.NTH_SMALLEST,
         AggregateFormula.PTH_PERCENTILE
      ];
   }

   public static get STRING_FORMULAS(): AggregateFormula[] {
      return [
         AggregateFormula.NONE, AggregateFormula.MAX, AggregateFormula.MIN,
         AggregateFormula.COUNT_ALL, AggregateFormula.COUNT_DISTINCT,
         AggregateFormula.FIRST, AggregateFormula.LAST,
         AggregateFormula.PRODUCT, AggregateFormula.CONCAT,
         AggregateFormula.CORRELATION, AggregateFormula.COVARIANCE,
         AggregateFormula.MEDIAN, AggregateFormula.MODE,
         AggregateFormula.NTH_LARGEST, AggregateFormula.NTH_MOST_FREQUENT,
         AggregateFormula.NTH_SMALLEST, AggregateFormula.PTH_PERCENTILE,
         AggregateFormula.WEIGHTED_AVG
      ];
   }

   public static get BOOL_FORMULAS(): AggregateFormula[] {
      return [
         AggregateFormula.NONE, AggregateFormula.COUNT_ALL,
         AggregateFormula.COUNT_DISTINCT, AggregateFormula.FIRST,
         AggregateFormula.LAST, AggregateFormula.NTH_MOST_FREQUENT
      ];
   }

   public static getFormulaList(column: DataRef): AggregateFormula[] {
      if(!column) {
         return SummaryAttrUtil.NUMBER_FORMULAS;
      }

      let dtype: string = column.dataType;

      switch(dtype) {
         case XSchema.DATE:
         case XSchema.TIME:
         case XSchema.TIME_INSTANT:
            return SummaryAttrUtil.DATE_FORMULAS;
         case XSchema.CHAR:
         case XSchema.STRING:
         case XSchema.CHARACTER:
            return SummaryAttrUtil.STRING_FORMULAS;
         case XSchema.BOOLEAN:
            return SummaryAttrUtil.BOOL_FORMULAS;
         default:
            return SummaryAttrUtil.NUMBER_FORMULAS;
      }
   }

   /**
    * Get the aggregate formula.
    * @param identifier the specified formula name or identifier.
    * @return the aggregate formula of the name, <tt>null</tt>
    * if not found.
    */
   static getFormula(identifier: string): AggregateFormula {
      let formula: AggregateFormula = AggregateFormula.getFormula(identifier);

      if(formula != null) {
         return formula;
      }

      let formulas = SummaryAttrUtil.NUMBER_FORMULAS;

      for(let i = 0; i < formulas.length; i++) {
         if(identifier === formulas[i].name || identifier === formulas[i].formulaName ||
            identifier === formulas[i].label)
         {
            return formulas[i];
         }
      }

      return null;
   }

   public static get PERCENT_TYPE_NO_GROUPS(): any[] {
      return [
         { value: StyleConstants.PERCENTAGE_NONE + "", label: "_#(js:None)" },
         { value: StyleConstants.PERCENTAGE_OF_GRANDTOTAL + "", label: "_#(js:GrandTotal)" }
      ];
   }

   public static get PERCENT_TYPE_WITH_GROUPS(): any[] {
      return [
         { value: StyleConstants.PERCENTAGE_NONE + "", label: "_#(js:None)" },
         { value: StyleConstants.PERCENTAGE_OF_GROUP + "", label: "_#(js:Group)" },
         { value: StyleConstants.PERCENTAGE_OF_GRANDTOTAL + "", label: "_#(js:GrandTotal)" }
      ];
   }

   public static getMaxForLevel(order: number): number {
      if(order == XConstants.YEAR_DATE_GROUP) {
         return 3000;
      }
      else if(order == XConstants.QUARTER_DATE_GROUP) {
         return 4;
      }
      else if(order == XConstants.MONTH_DATE_GROUP) {
         return 12;
      }
      else if(order == XConstants.WEEK_DATE_GROUP) {
         return 52;
      }
      else if(order == XConstants.DAY_DATE_GROUP) {
         return 365;
      }
      else if(order == XConstants.HOUR_DATE_GROUP) {
         return 24;
      }
      else if(order == XConstants.MINUTE_DATE_GROUP) {
         return 60;
      }
      else if(order == XConstants.SECOND_DATE_GROUP) {
         return 60;
      }

      return null;
   }
}
