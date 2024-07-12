/*
 * inetsoft-core - StyleBI is a business intelligence web application.
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
package inetsoft.web.binding.model;

import inetsoft.report.StyleConstants;
import inetsoft.report.composition.graph.calc.PercentCalc;
import inetsoft.report.internal.binding.*;
import inetsoft.uql.asset.AggregateFormula;
import inetsoft.uql.erm.DataRef;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.viewsheet.VSAggregateRef;
import inetsoft.uql.viewsheet.XAggregateRef;
import inetsoft.uql.viewsheet.graph.Calculator;
import inetsoft.uql.viewsheet.graph.VSChartAggregateRef;
import inetsoft.util.Tool;
import inetsoft.web.binding.drm.DataRefModel;
import inetsoft.web.binding.model.graph.CalculateInfo;
import inetsoft.web.binding.service.DataRefModelWrapperFactory;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * A BDAggregateRef object represents a aggregate reference.
 *
 * @version 12.3
 * @author InetSoft Technology Corp
 */
public class BAggregateRefModel extends AbstractBDRefModel {
   /**
    * Create a default BAggregateRefModel.
    */
   public BAggregateRefModel() {
   }

   /**
    * Create a BAggregateRefModel accroding to AggregateField.
    */
   public BAggregateRefModel(AggregateField agg) {
      super(agg.getField());
      String formulaName = null;

      if(agg.getFormula() != null && AggregateFormula.getFormula(agg.getFormula()) == null) {
         formulaName = getBaseFormula(agg.getFormula());
      }
      else if(agg.getFormula() != null) {
         formulaName = AggregateFormula.getFormula(agg.getFormula()).getFormulaName();
      }

      if(formulaName != null && isNumberFormula(formulaName)) {
         numValue = getFormulaNum(agg.getFormula());
      }

      if(XSchema.isDateType(getDataType()) && SummaryAttr.COUNT_FORMULA.equals(formulaName) ||
         SummaryAttr.DISTINCTCOUNT_FORMULA.equals(formulaName))
      {
         setDataType(XSchema.INTEGER);
      }

      Calculator calc = agg.getCalculator();
      Calculator oldCalc = agg.getCalculator();

      if(calc == null && agg.getPercentageType() != StyleConstants.PERCENTAGE_NONE) {
         calc = new PercentCalc();

         if(agg.getPercentageType() == StyleConstants.PERCENTAGE_OF_GROUP) {
            ((PercentCalc) calc).setLevel(PercentCalc.SUB_TOTAL);
         }
         else if(agg.getPercentageType() == StyleConstants.PERCENTAGE_OF_GRANDTOTAL) {
            ((PercentCalc) calc).setLevel(PercentCalc.GRAND_TOTAL);
         }
      }

      if(calc != null) {
         if(calc instanceof PercentCalc && agg.getPercentageType() == StyleConstants.PERCENTAGE_NONE) {
            this.setCalculateInfo(null);
            calc = null;
         }
         else {
            this.setCalculateInfo(CalculateInfo.createCalcInfo(calc));
         }
      }

      if(oldCalc != calc) {
         agg.setCalculator(calc);
      }

      this.setFormula(formulaName);
      this.setView(Tool.localize(agg.toView()));
      this.setPercentage(agg.getPercentageType() + "");

      if(SummaryAttr.NONE_FORMULA.equals(this.getFormula()) ||
         "None".equalsIgnoreCase(this.getFormula()))
      {
         setFullName(agg.getName());
      }
      else {
         setFullName(agg.toView());
      }

      if(oldCalc != calc) {
         agg.setCalculator(oldCalc);
      }
   }

    /**
    * Create a BAggregateRefModel according to XAggregateRef.
    */
   public BAggregateRefModel(XAggregateRef ref) {
      super(ref);

      Calculator calc = ref.getCalculator();

      if(ref instanceof VSAggregateRef) {
         init((VSAggregateRef) ref);
         int ptype = StyleConstants.PERCENTAGE_NONE;

         try {
            ptype = Integer.parseInt(((VSAggregateRef) ref).getPercentageOptionValue());
         }
         catch(NumberFormatException ignore) {
         }

         if(calc == null && ptype != StyleConstants.PERCENTAGE_NONE) {
            calc = new PercentCalc();

            if(ptype == StyleConstants.PERCENTAGE_OF_GROUP) {
               ((PercentCalc) calc).setLevel(PercentCalc.SUB_TOTAL);
            }
            else if(ptype == StyleConstants.PERCENTAGE_OF_GRANDTOTAL) {
               ((PercentCalc) calc).setLevel(PercentCalc.GRAND_TOTAL);
            }
         }
      }

      setCalculateInfo(CalculateInfo.createCalcInfo(calc));
   }

   /**
    * Create a BAggregateRefModel according to VSAggregateRef.
    */
   private void init(VSAggregateRef ref) {
      this.formula = ref.getFormulaValue();
      this.columnValue = ref.getColumnValue();
      this.caption = ref.getCaption();
      this.percentage = ref.getPercentageOptionValue();
      this.secondaryColumnValue = ref.getSecondaryColumnValue();
      this.numValue = ref.getNValue();
      this.setComboType(ref.getComboType());
      this.originalDataType = ref.getOriginalDataType();
   }

   /**
    * Get the formula value.
    * @return the formula value of the aggregate ref.
    */
   public String getFormula() {
      return formula;
   }

   /**
    * Set the formula value to the aggregate ref.
    * @param formula value the specified formula.
    */
   public void setFormula(String formula) {
      this.formula = formula;
   }

   /**
    * Get the formula secondary column.
    * @return secondary column.
    */
   public DataRefModel getSecondaryColumn() {
      return secondaryColumn;
   }

   /**
    * Set the secondary column to be used in the formula.
    * @param secondaryColumn formula secondary column.
    */
   public void setSecondaryColumn(DataRefModel secondaryColumn) {
      this.secondaryColumn = secondaryColumn;
   }

   /**
    * Get the formula secondary column.
    * @return secondary column value.
    */
   public String getSecondaryColumnValue() {
      return secondaryColumnValue;
   }

   /**
    * Set the secondary column to be used in the formula.
    * @param secondaryColumnValue formula secondary column value.
    */
   public void setSecondaryColumnValue(String secondaryColumnValue) {
      this.secondaryColumnValue = secondaryColumnValue;
   }

   /**
    * Set the percentage.
    * @param percent percentage value.
    */
   public void setPercentage(String percent) {
      this.percentage = percent;
   }

   /**
    * Get the percentage number.
    * @return percentage number.
    */
   public String getPercentage() {
      return percentage;
   }

   /**
    * Set the number for specifical formula.
    * @param num the number for specifical formula.
    */
   public void setNum(int num) {
      this.num = num;
   }

   /**
    * Get the number when formula with a number parameter(only for adhoc).
    * @return number.
    */
   public int getNum() {
      try {
         return numValue != null ? Integer.parseInt(numValue) : num;
      }
      catch(Exception ex) {
         return 1;
      }
   }

   public void setNumValue(String num) {
      this.numValue = num;
   }

   public String getNumValue() {
      return numValue;
   }

   /**
    * Get the column value.
    * @return the column value.
    */
   public String getColumnValue() {
      return columnValue;
   }

   /**
    * Set the column value.
    * @param value the column value.
    */
   public void setColumnValue(String value) {
      this.columnValue = value;
   }

   /**
    * Get caption.
    * @param caption the caption of the dimension.
    */
   public void setCaption(String caption) {
      this.caption = caption;
   }

   /**
    * Set caption.
    * @return the caption of the dimension.
    */
   public String getCaption() {
      return caption;
   }

   public void setFormulaOptionModel(FormulaOptionModel formulaOptionModel) {
      this.formulaOptionModel = formulaOptionModel;
   }

   public FormulaOptionModel getFormulaOptionModel() {
      return this.formulaOptionModel;
   }

   /**
    * Set runtime id.
    */
   public void setRuntimeID(int rid) {
      this.runtimeID = (byte) rid;
   }

   /**
    * Get runtime id.
    */
   public int getRuntimeID() {
      return runtimeID;
   }

   @Override
   public DataRef createDataRef() {
      VSAggregateRef agg = new VSAggregateRef();
      agg.setFormulaValue(this.getFormula());
      agg.setColumnValue(this.getColumnValue());
      agg.setCaption(this.getCaption());
      agg.setRuntimeID(this.runtimeID);
      agg.setComboType(this.getComboType());
      agg.setRefType(this.getRefType());

      try {
         int n = Integer.parseInt(this.numValue);
         agg.setNValue(Math.max(1, n) + "");
      }
      catch(Exception ex) {
         agg.setNValue(this.numValue);
      }

      DataRefModel refMode = getDataRefModel();

      if(refMode != null) {
         agg.setDataRef(refMode.createDataRef());
      }

      agg.setSecondaryColumnValue(secondaryColumnValue);

      if(percentage != null) {
         agg.setPercentageOptionValue(percentage + "");
      }

      CalculateInfo calcInfo = getCalculateInfo();
      agg.setCalculator(calcInfo != null ? calcInfo.toCalculator() : null);

      return agg;
   }

   public AggregateField createAggregateField() {
      AggregateField agg = new AggregateField();
      DataRefModel refMode = getDataRefModel();

      if(refMode != null) {
         agg.setDataRef(refMode.createDataRef());
      }

      Field secondaryColumnField = null;

      if(secondaryColumn != null) {
         secondaryColumnField = (Field) secondaryColumn.createDataRef();
         agg.setSecondaryField(secondaryColumnField);
      }

      //fixed bug #22142 that setSecondaryField() will
      //affect the value of the agg'formula after setFormula().
      agg.setFormula(getCurrentFormula());

      //Bug #23412 secondaryField lost after agg.setFormula(getCurrentFormula()) if
      //secondaryField didn't change
      AggregateFormula formula = AggregateFormula.getFormula(this.getFormula());

      if(formula != null && formula.isTwoColumns() &&
         getCurrentFormula() != null &&
         getCurrentFormula().equals(getFormula()) &&
         secondaryColumnField != null)
      {
         String currentFormula = getCurrentFormula();
         int start = 0;

         if(currentFormula != null) {
            start = currentFormula.indexOf("(");
         }

         if(start > 0) {
            int end = currentFormula.indexOf(")");
            currentFormula = currentFormula.substring(0, start) + currentFormula.substring(end + 1);
         }

         currentFormula = currentFormula + "(" + secondaryColumnField.getName() + ")";
         agg.setFormula(currentFormula);
      }

      CalculateInfo calcInfo = getCalculateInfo();
      agg.setCalculator(calcInfo != null ? calcInfo.toCalculator() : null);

      return agg;
   }

   private String getCurrentFormula() {
      String formulaStr = this.getFormula();
      AggregateFormula formula = AggregateFormula.getFormula(formulaStr);

      if(formulaStr == null || formulaStr.equals(SummaryAttr.NONE_FORMULA)) {
         return null;
      }

      if(formula != null && formula.isTwoColumns()) {
         String second = getSecondaryColumnValue() != null ? getSecondaryColumnValue() :
            getSecondaryColumn() != null ? secondaryColumn.getName() : "";
         formulaStr += "(" + second + ")";
      }
      else if(isNumberFormula(formulaStr)) {
         formulaStr += "(" + (this.getNum() > 0 ? this.getNum() : null) + ")";
      }
      else if(percentage != null && Integer.parseInt(percentage) > 0) {
         formulaStr += "<" + percentage + ">";
      }

      return formulaStr;
   }

   private String getBaseFormula(String formula) {
      int idx = formula.indexOf("(");
      int idx0 = formula.indexOf("<");
      String base = formula;

      if(idx > 0) {
         base = formula.substring(0, idx);
      }
      else if(idx0 > 0) {
         base = formula.substring(0, idx0);
      }

      return base;
   }

   private String getFormulaNum(String formula) {
      int idx = formula.indexOf("(");
      int idx1 = formula.indexOf(")");
      String numString = formula;

      if(idx > 0) {
         numString = formula.substring(idx + 1, idx1);
      }

      return "null".equals(numString) ? null : numString;
   }

   private boolean isNumberFormula(String formula) {
      return "NthLargest".equals(formula) || "NthMostFrequent".equals(formula) ||
         "NthSmallest".equals(formula) || "PthPercentile".equals(formula);
   }

   /**
    * Get calculator
    */
   public CalculateInfo getCalculateInfo() {
      return calcInfo;
   }

   /**
    * Set calculator.
    */
   public void setCalculateInfo(CalculateInfo calcInfo) {
      this.calcInfo = calcInfo;
   }

   /**
    * Init BuildIn calculators.
    * @param buildInCalcs the value to be set.
    */
   public void setBuildInCalcs(List<CalculateInfo> buildInCalcs) {
      this.buildInCalcs = buildInCalcs;
   }

   /**
    * Get the buildIn calcs
    * @return the build calculats.
    */
   public List<CalculateInfo> getBuildInCalcs() {
      return buildInCalcs;
   }

   public String getOriginalDataType() {
      return originalDataType;
   }

   public void setOriginalDataType(String originalDataType) {
      this.originalDataType = originalDataType;
   }

   private String formula;
   private String percentage;
   private int num = 0;
   private String numValue;
   private String secondaryColumnValue;
   private DataRefModel secondaryColumn;
   private String columnValue;
   private String caption;
   private String originalDataType;
   private int runtimeID;
   private CalculateInfo calcInfo;
   private List<CalculateInfo> buildInCalcs;
   private FormulaOptionModel formulaOptionModel;

   @Component
   public static final class VSAggregateRefModelFactory
      extends DataRefModelWrapperFactory<VSAggregateRef, BAggregateRefModel>
   {
      @Override
      public Class<VSAggregateRef> getDataRefClass() {
         return VSAggregateRef.class;
      }

      @Override
      public BAggregateRefModel createDataRefModel0(VSAggregateRef ref) {
         BAggregateRefModel model = new BAggregateRefModel(ref);
         model.setRuntimeID(ref.getRuntimeID());

         return model;
      }
   }

   @Component
   public static final class VSChartAggregateRefModelFactory
      extends DataRefModelWrapperFactory<VSChartAggregateRef, BAggregateRefModel>
   {
      @Override
      public Class<VSChartAggregateRef> getDataRefClass() {
         return VSChartAggregateRef.class;
      }

      @Override
      public BAggregateRefModel createDataRefModel0(VSChartAggregateRef ref) {
         BAggregateRefModel model = new BAggregateRefModel(ref);
         model.setRuntimeID(ref.getRuntimeID());

         return model;
      }
   }
}
