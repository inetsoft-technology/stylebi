/*
 * inetsoft-web - StyleBI is a business intelligence web application.
 * Copyright © 2024 InetSoft Technology (info@inetsoft.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
import { XSchema } from "../../common/data/xschema";

export class AggregateFormula {
   static formulas: AggregateFormula[];

   constructor(public name: string, public formulaName: string,
               public label: string, public twoColumns: boolean,
               public hasN: boolean = false, public supportPercentage: boolean = false) {
   }

   public static get NONE(): AggregateFormula {
      return new AggregateFormula("None", "none", "_#(js:None)", false);
   }

   public static get AVG(): AggregateFormula {
      return new AggregateFormula("AVG", "Average", "_#(js:Average)", false, false, true);
   }

   public static get AGGREGATE(): AggregateFormula {
      return new AggregateFormula("Aggregate", "Aggregate", "_#(js:Aggregate)", false);
   }

   public static get COUNT_ALL(): AggregateFormula {
      return new AggregateFormula("COUNT ALL", "Count", "_#(js:Count)", false, false, true);
   }

   public static get COUNT_DISTINCT(): AggregateFormula {
      return new AggregateFormula("COUNT DISTINCT", "DistinctCount",
         "_#(js:DistinctCount)", false, false, true);
   }

   public static get MAX(): AggregateFormula {
      return new AggregateFormula("MAX", "Max", "_#(js:Max)", false, false, true);
   }

   public static get MIN(): AggregateFormula {
      return new AggregateFormula("MIN", "Min", "_#(js:Min)", false, false, true);
   }

   public static get SUM(): AggregateFormula {
      return new AggregateFormula("SUM", "Sum", "_#(js:Sum)", false, false, true);
   }

   public static get FIRST(): AggregateFormula {
      return new AggregateFormula("FIRST", "First", "_#(js:First)", true);
   }

   public static get LAST(): AggregateFormula {
      return new AggregateFormula("LAST", "Last", "_#(js:Last)", true);
   }

   public static get MEDIAN(): AggregateFormula {
      return new AggregateFormula("MEDIAN", "Median", "_#(js:Median)", false, false, true);
   }

   public static get MODE(): AggregateFormula {
      return new AggregateFormula("Mode", "Mode", "_#(js:Mode)", false);
   }

   public static get CORRELATION(): AggregateFormula {
      return new AggregateFormula("CORRELATION", "Correlation", "_#(js:Correlation)", true);
   }

   public static get COVARIANCE(): AggregateFormula {
      return new AggregateFormula("COVARIANCE", "Covariance", "_#(js:Covariance)", true);
   }

   public static get VARIANCE(): AggregateFormula {
      return new AggregateFormula("VARIANCE", "Variance", "_#(js:Variance)", false, false, true);
   }

   public static get STANDARD_DEVIATION(): AggregateFormula {
      return new AggregateFormula("STANDARD DEVIATION", "StandardDeviation",
         "_#(js:StandardDeviation)", false, false, true);
   }

   public static get POPULATION_VARIANCE(): AggregateFormula {
      return new AggregateFormula("POPULATION VARIANCE", "PopulationVariance",
         "_#(js:PopulationVariance)", false, false, true);
   }

   public static get POPULATION_STANDARD_DEVIATION(): AggregateFormula {
      return new AggregateFormula("POPULATION STANDARD DEVIATION",
         "PopulationStandardDeviation", "_#(js:PopulationStandardDeviation)", false, false, true);
   }

   public static get WEIGHTED_AVG(): AggregateFormula {
      return new AggregateFormula("WEIGHTED AVG", "WeightedAverage", "_#(js:WeightedAverage)", true);
   }

   public static get PRODUCT(): AggregateFormula {
      return new AggregateFormula("PRODUCT", "Product", "_#(js:Product)", false);
   }

   public static get CONCAT(): AggregateFormula {
      return new AggregateFormula("CONCAT", "Concat", "_#(js:Concat)", false);
   }

   public static get NTH_LARGEST(): AggregateFormula {
      return new AggregateFormula("NTH LARGEST", "NthLargest", "_#(js:NthLargest)", false, true);
   }

   public static get NTH_SMALLEST(): AggregateFormula {
      return new AggregateFormula("NTH SMALLEST", "NthSmallest", "_#(js:NthSmallest)", false, true);
   }

   public static get NTH_MOST_FREQUENT(): AggregateFormula {
      return new AggregateFormula("NTH MOST FREQUENT", "NthMostFrequent", "_#(js:NthMostFrequent)",
                                  false, true);
   }

   public static get PTH_PERCENTILE(): AggregateFormula {
      return new AggregateFormula("PTH PERCENTILE", "PthPercentile", "_#(js:PthPercentile)",
                                  false, true);
   }

   // return "N" or "P" for nth or pth formula
   public static getNPLabel(formula: string): string {
      return formula && formula.charAt(0) == "P" ? "_#(js:P)" : "_#(js:N)";
   }

   /**
    * Get all the available formulas.
    * @return all the available formulas.
    */
   static getFormulas() {
      if(AggregateFormula.formulas == null) {
         AggregateFormula.formulas = [
            AggregateFormula.AVG,
            AggregateFormula.AGGREGATE,
            AggregateFormula.COUNT_ALL,
            AggregateFormula.COUNT_DISTINCT,
            AggregateFormula.MAX,
            AggregateFormula.MIN,
            AggregateFormula.SUM,
            AggregateFormula.MEDIAN,
            AggregateFormula.MODE,
            AggregateFormula.CORRELATION,
            AggregateFormula.COVARIANCE,
            AggregateFormula.VARIANCE,
            AggregateFormula.STANDARD_DEVIATION,
            AggregateFormula.POPULATION_VARIANCE,
            AggregateFormula.POPULATION_STANDARD_DEVIATION,
            AggregateFormula.WEIGHTED_AVG,
            AggregateFormula.FIRST,
            AggregateFormula.LAST,
            AggregateFormula.PRODUCT,
            AggregateFormula.CONCAT,
            AggregateFormula.NTH_LARGEST,
            AggregateFormula.NTH_SMALLEST,
            AggregateFormula.NTH_MOST_FREQUENT,
            AggregateFormula.PTH_PERCENTILE,
            AggregateFormula.COVARIANCE,
            AggregateFormula.CORRELATION
         ];
      }

      return AggregateFormula.formulas;
   }

   /**
    * Get the aggregate formula.
    * @param name the specified formula name or identifier.
    * @return the aggregate formula of the name, <tt>null</tt>
    * if not found.
    */
   static getFormula(identifier: string): AggregateFormula {
      if(!identifier || identifier === "None" || identifier === "none" || identifier === "null") {
         return AggregateFormula.NONE;
      }

      let formulas = AggregateFormula.getFormulas();

      for(let i = 0; i < formulas.length; i++) {
         if(identifier === formulas[i].name || identifier === formulas[i].formulaName ||
            identifier === formulas[i].label)
         {
            return formulas[i];
         }
      }

      return null;
   }

   /**
    * Get the default formula for the data ref.
    * @param data type.
    * @return AggregateFormula
    */
   static getDefaultFormula(type: string): AggregateFormula {
      if(XSchema.isNumericType(type)) {
         return AggregateFormula.SUM;
      }

      return AggregateFormula.COUNT_ALL;
   }

   static getFormulaObjs(formulas: AggregateFormula[]): string[] {
      let objs: any[] = [];

      for(let i = 0; i < formulas.length; i++) {
         let formula: AggregateFormula = formulas[i];
         objs.push({value: formula.formulaName, label: formula.label});
      }

      return objs;
   }

   static isSameTypeFormula(formula: string): boolean {
      if(formula == this.MAX.formulaName ||
         formula == this.MIN.formulaName ||
         formula == this.AVG.formulaName ||
         formula == this.SUM.formulaName ||
         formula == this.FIRST.formulaName ||
         formula == this.LAST.formulaName ||
         formula == this.MEDIAN.formulaName ||
         formula == this.MODE.formulaName ||
         formula == this.NTH_LARGEST.formulaName ||
         formula == this.NTH_SMALLEST.formulaName ||
         formula == this.NTH_MOST_FREQUENT.formulaName ||
         formula == this.WEIGHTED_AVG.formulaName ||
         formula == this.POPULATION_STANDARD_DEVIATION.formulaName ||
         formula == this.POPULATION_VARIANCE.formulaName ||
         formula == this.PTH_PERCENTILE.formulaName ||
         formula == this.STANDARD_DEVIATION.formulaName ||
         formula == this.VARIANCE.formulaName ||
         formula == this.COVARIANCE.formulaName ||
         formula == this.CORRELATION.formulaName)
      {
         return true;
      }

      return false;
   }

   static isNumeric(formula: string): boolean {
      switch(formula) {
         case this.COUNT_ALL.formulaName:
         case this.COUNT_DISTINCT.formulaName:
         case this.FIRST.formulaName:
         case this.LAST.formulaName:
         case this.MAX.formulaName:
         case this.MEDIAN.formulaName:
         case this.MIN.formulaName:
         case this.MODE.formulaName:
         case this.NTH_LARGEST.formulaName:
         case this.NTH_SMALLEST.formulaName:
         case this.NTH_MOST_FREQUENT.formulaName:
         case this.PTH_PERCENTILE.formulaName:
            return false;
      }

      return true;
   }
}
