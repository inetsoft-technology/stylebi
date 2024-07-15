/*
 * inetsoft-core - StyleBI is a business intelligence web application.
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
package inetsoft.web.binding.model.graph;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import inetsoft.uql.viewsheet.graph.AbstractCalc.CustomCalc;
import inetsoft.uql.viewsheet.graph.Calculator;
import inetsoft.util.Catalog;
import inetsoft.web.binding.model.graph.calc.*;

@JsonTypeInfo(
   use = JsonTypeInfo.Id.NAME,
   include = JsonTypeInfo.As.PROPERTY,
   property = "classType")
@JsonSubTypes({
   @JsonSubTypes.Type(value = PercentCalcInfo.class, name = "PERCENT"),
   @JsonSubTypes.Type(value = ChangeCalcInfo.class, name = "CHANGE"),
   @JsonSubTypes.Type(value = MovingCalcInfo.class, name = "MOVING"),
   @JsonSubTypes.Type(value = RunningTotalCalcInfo.class, name = "RUNNINGTOTAL"),
   @JsonSubTypes.Type(value = CalculateInfo.CustomCalcInfo.class, name = "CUSTOM"),
   @JsonSubTypes.Type(value = ValueOfCalcInfo.class, name = "VALUE"),
   @JsonSubTypes.Type(value = CompoundGrowthCalcInfo.class, name = "COMPOUNDGROWTH")
})
public abstract class CalculateInfo {
   /**
    * Get type.
    * @return type.
    */
   public int getType() {
      return type;
   }

   /**
    * Set type.
    * @param type to be set.
    */
   public void setType(int type) {
      this.type = type;
   }

   /**
    * Get alias.
    * @return alias.
    */
   public String getAlias() {
      return alias;
   }

   /**
    * Set alias.
    * @param alias to be set.
    */
   public void setAlias(String alias) {
      this.alias = alias;
   }

   /**
    * Get name.
    * @return name.
    */
   public String getName() {
      return name;
   }

   /**
    * Set name.
    * @param name to be set.
    */
   public void setName(String name) {
      this.name = name;
   }

   /**
    * Get prefix.
    */
   public String getPrefix() {
      return prefix;
   }

   /**
    * Set prefix.
    */
   public void setPrefix(String prefix) {
      this.prefix = prefix;
   }

   /**
    * Get prefix.
    */
   public String getPrefixView() {
      return prefixView;
   }

   /**
    * Set prefix.
    */
   public void setPrefixView(String prefixView) {
      this.prefixView = prefixView;
   }

   
   /**
    * Get view.
    */
   public String getView() {
      return view;
   }
   
   
   /**
    * Get view.
    */
   public void setView(String view) {
      this.view = view;
   }

   public boolean isSupportSortByValue() {
      return supportSortByValue;
   }

   public void setSupportSortByValue(boolean supportSortByValue) {
      this.supportSortByValue = supportSortByValue;
   }

   /**
    * Load the calc info from Calculator
    * @param calc an object to be loaded into the calc info.
    */
   protected abstract void loadCalcInfo(Calculator calc);

   /**
    * Create a calculate info from an calculator.
    * @param calc the specified calculator.
    * @return the created calculate info.
    */
   public static CalculateInfo createCalcInfo(Calculator calc) {
      if(calc == null) {
         return null;
      }

      CalculateInfo calcInfo = null;
      int type = calc.getType();

      if(type == Calculator.PERCENT) {
         calcInfo = new PercentCalcInfo();
      }
      else if(type == Calculator.CHANGE) {
         calcInfo = new ChangeCalcInfo();
      }
      else if(type == Calculator.RUNNINGTOTAL) {
         calcInfo = new RunningTotalCalcInfo();
      }
      else if(type == Calculator.MOVING) {
         calcInfo = new MovingCalcInfo();
      }
      else if(type == Calculator.CUSTOM) {
         calcInfo = new CustomCalcInfo();
      }
      else if(type == Calculator.VALUE) {
         calcInfo = new ValueOfCalcInfo();
      }
      else if(type == Calculator.COMPOUNDGROWTH) {
         calcInfo = new CompoundGrowthCalcInfo();
      }

      if(calcInfo != null) {
         calcInfo.loadCalcInfo(calc);
         calcInfo.setType(calc.getType());
         calcInfo.setAlias(calc.getAlias());
         calcInfo.setName(calc.getName());
         calcInfo.setPrefix(calc.getPrefix());
         calcInfo.setPrefixView(calc.getPrefixView());
         calcInfo.setView(calc.toView());
         calcInfo.setSupportSortByValue(calc.supportSortByValue());
      }

      return calcInfo;
   }

   /**
    * Create calculator.
    * @return the created calculator.
    */
   public Calculator toCalculator() {
      Calculator calc = toCalculator0();

      if(calc != null) {
         calc.setAlias(getAlias());
      }

      return calc;
   }

   /**
    * Create calculator.
    */
   protected abstract Calculator toCalculator0();

   /**
    * Check if equals another object.
    */
   public boolean equals(Object obj) {
      return obj instanceof CalculateInfo;
   }

   /**
    * "Custom..." option in built-in comboBox.
    */
   static class CustomCalcInfo extends CalculateInfo {
      /**
       * Load the calc info from Calculator
       * @param calc an object to be loaded into the calc info.
       */
      @Override
      protected void loadCalcInfo(Calculator calc) {
      }

      /**
       * Create calculator.
       */
      @Override
      protected  Calculator toCalculator0() {
         CustomCalc customCalc = new CustomCalc();
         return customCalc;
      }

      /**
       * Get name.
       * @return name.
       */
      @Override
      public String getName() {
         return Catalog.getCatalog().getString("Custom") + "...";
      }

      /**
       * Check if equals another object.
       */
      public boolean equals(Object obj) {
         return obj instanceof CustomCalcInfo;
      }
   }

   private int type;
   private String alias;
   private String name;
   private String prefix;
   private String prefixView;
   private String view;
   private boolean supportSortByValue;
}