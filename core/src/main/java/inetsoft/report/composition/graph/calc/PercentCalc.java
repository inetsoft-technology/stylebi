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
package inetsoft.report.composition.graph.calc;

import inetsoft.graph.data.CalcColumn;
import inetsoft.uql.XConstants;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.viewsheet.DynamicValue;
import inetsoft.uql.viewsheet.VSDimensionRef;
import inetsoft.uql.viewsheet.graph.AbstractCalc;
import inetsoft.util.Tool;
import org.w3c.dom.Element;

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;

/**
 * Percent Calculator.
 *
 * @version 10.3
 * @author InetSoft Technology Corp
 */
public class PercentCalc extends AbstractCalc implements DynamicCalc {
   /**
    * Grand total option.
    */
   public static final int GRAND_TOTAL = 1;
   /**
    * Sub total option.
    */
   public static final int SUB_TOTAL = 2;

   /**
    * All labels of "of" comboBox.
    */
   public static final String[] ALL_LEVEL_LABELS = new String[] {
      catalog.getString("Grand Total"),
      catalog.getString("Sub Total")};

   /**
    * All types of "of" comboBox.
    */
   public static final int[] ALL_LEVEL_TYPES = new int[] {
      PercentCalc.GRAND_TOTAL, PercentCalc.SUB_TOTAL};

   /**
    * create calculator column.
    * @param column column to be created.
    * @return the created column.
    */
   @Override
   public CalcColumn createCalcColumn(String column) {
      PercentColumn calc = new PercentColumn(column, getPrefix() + column);
      calc.setLevel(level);
      calc.setDim("".equals(getColumnName()) ? null : getColumnName());
      return calc;
   }

   @Override
   public void updateRefs(List<VSDimensionRef> oldRefs, List<VSDimensionRef> newRefs) {
      for(int i = 0; i < oldRefs.size(); i++) {
         final VSDimensionRef oldRef = oldRefs.get(i);

         if(oldRef.getFullName().equals(getColumnNameValue())) {
            setColumnName(newRefs.get(i).getFullName());
         }
      }
   }

   @Override
   public boolean supportSortByValue() {
      return true;
   }

   /**
    * Get prefix.
    */
   @Override
   protected String getPrefix0() {
      String str = "% of ";
      String column = getColumnView();

      if(column != null) {
         str += column;
      }
      else {
         switch(level) {
         case SUB_TOTAL:
            str += getSubTotalLabel();
            break;
         case GRAND_TOTAL:
            str += getTotalLabel();
            break;
         }
      }

      String cprefix = getCrosstabPrefix();

      if(cprefix != null) {
         str += " " + cprefix;
      }

      return str;
   }

   /**
    * Get prefix.
    */
   @Override
   protected String getPrefixView0() {
      String str = "Percent of";
      String column = getColumnView();

      if(column != null) {
         str += " " + column;
      }
      else {
         switch(level) {
         case SUB_TOTAL:
            str += " " + catalog.getString(getSubTotalLabel());
            str = catalog.getString(str);
            break;
         case GRAND_TOTAL:
            str += " " + catalog.getString(getTotalLabel());
            str = catalog.getString(str);
            break;
         }
      }

      String cprefix = getCrosstabPrefix();

      if(cprefix != null) {
         str += " " + cprefix;
      }

      return str;
   }

   private String getCrosstabPrefix() {
      if(isByRow()) {
         return ROW_INNER_LABEL;
      }

      if(isByColumn()) {
         return COLUMN_INNER_LABEL;
      }

      return null;
   }

   private String getSubTotalLabel() {
      return "subtotal";
   }

   private String getTotalLabel() {
      return "total";
   }

   /**
    * Get type.
    * @return type.
    */
   @Override
   public int getType() {
      return PERCENT;
   }

   /**
    * Get level.
    * @return level.
    */
   public int getLevel() {
      return level;
   }

   /**
    * Set level.
    * @param level to set.
    */
   public void setLevel(int level) {
      this.level = level;
   }

   /**
    * Get label.
    * @return label.
    */
   public String getLabel() {
      return null;
   }

   /**
    * Get columnName.
    * @return columnName.
    */
   public String getColumnName() {
      return getDynamicColumn(columnName.getRValue());
   }

   /**
    * Set column name.
    * @param columnName to be set.
    */
   public void setColumnName(String columnName) {
      this.columnName.setDValue(columnName);
   }

   public String getColumnNameValue() {
      return columnName.getDValue();
   }

   /**
    * @return true if calculate percent by for crosstab row header, else false.
    */
   public boolean isByRow() {
      return (XConstants.PERCENTAGE_BY_ROW + "").equals(getPercentageByOption() + "");
   }

   /**
    * @return true if calculate percent by for crosstab column header, else false.
    */
   public boolean isByColumn() {
      return (XConstants.PERCENTAGE_BY_COL + "").equals(getPercentageByOption() + "");
   }

   /**
    * Get the string representation.
    */
   public String toString() {
      return getClass().getName() + "@" + System.identityHashCode(this) +
         "[type=" + getType() + ", alias=" + getAlias() +
         ", label=" + getLabel() + ", name=" + getName() +
         ", level=" + getLevel() + ", columnName=" + getColumnName() + "]" +
         ", percentageByValue=" + percentageByValue.getRValue() + "]";
   }

   /**
    * Check if equals another object.
    */
   public boolean equals(Object obj) {
      if(!(obj instanceof PercentCalc)) {
         return false;
      }

      PercentCalc calc = (PercentCalc) obj;
      return super.equals(obj) && level == calc.level &&
         Tool.equals(percentageByValue.getDValue(), calc.percentageByValue.getDValue()) &&
         Tool.equals(columnName.getDValue(), calc.columnName.getDValue());
   }

   /**
    * Get hash code of this object.
    */
   public int hashCode() {
      return level * 31 + (columnName == null ? 0 : columnName.hashCode()) +
         (percentageByValue == null ? 0 : percentageByValue.hashCode());
   }

   /**
    * Write the xml segment to print writer.
    * @param writer the destination print writer.
    */
   @Override
   protected void writeAttribute(PrintWriter writer) {
      super.writeAttribute(writer);
      writer.print(" level=\"" + level + "\"");

      if(columnName.getDValue() != null) {
         writer.print(" columnName=\"" + Tool.escape(columnName.getDValue()) + "\"");
      }

      if(percentageByValue.getDValue() != null) {
         writer.print(" percentageByValue=\"" + Tool.escape(percentageByValue.getDValue()) + "\"");
      }
   }

   /**
    * Method to parse an xml segment.
    */
   @Override
   protected void parseAttribute(Element root) throws Exception {
      super.parseAttribute(root);
      level = Integer.parseInt(Tool.getAttribute(root, "level"));
      columnName.setDValue(Tool.getAttribute(root, "columnName"));
      percentageByValue.setDValue(Tool.getAttribute(root, "percentageByValue"));
   }

   @Override
   protected String getName0() {
      String name = catalog.getString("Percent") + " of ";

      if(getColumnName() != null) {
         name += getColumnName() + "";
      }
      else {
         switch(level) {
            case SUB_TOTAL:
               name += catalog.getString("Subtotal");
               break;
            case GRAND_TOTAL:
               name += catalog.getString("Grand Total");
               break;
         }
      }

      return name;
   }

   /**
    * Get view.
    */
   @Override
   protected String toView0() {
      return getName0();
   }

   @Override
   public boolean isPercent() {
      return true;
   }

   @Override
   public List<DynamicValue> getDynamicValues() {
      List<DynamicValue> list = new ArrayList<>();
      list.add(columnName);
      list.add(percentageByValue);

      return list;
   }

   @Override
   public Object clone() {
      PercentCalc calc = (PercentCalc) super.clone();
      calc.level = level;

      if(columnName != null) {
         calc.columnName = (DynamicValue) columnName.clone();
      }

      if(percentageByValue != null) {
         calc.percentageByValue = (DynamicValue) percentageByValue.clone();
      }

      return calc;
   }

   /**
    * Get the percentage by option.
    * @return the percentage by option.
    */
   public int getPercentageByOption() {
      Integer value = (Integer) percentageByValue.getRuntimeValue(true);
      return value.intValue();
   }

   /**
    * Get the percentage by value of this dimension reference.
    * @return the percentage by value of this dimension reference.
    */
   public String getPercentageByValue() {
      return percentageByValue.getDValue();
   }

   /**
    * Set the col subtotal visible value of this dimension reference.
    * @param percentage the col subtotal visible value of this dimension reference.
    */
   public void setPercentageByValue(String percentage) {
      this.percentageByValue.setDValue(percentage);
   }

   private int level = GRAND_TOTAL;
   private DynamicValue columnName = new DynamicValue();
   private DynamicValue percentageByValue = new DynamicValue(
      null, XSchema.INTEGER,
      new int[] {
         XConstants.PERCENTAGE_NONE,
         XConstants.PERCENTAGE_BY_COL,
         XConstants.PERCENTAGE_BY_ROW},
      new String[] {"none", "columns", "rows"});
}
