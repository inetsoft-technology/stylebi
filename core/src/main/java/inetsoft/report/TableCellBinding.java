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
package inetsoft.report;

import inetsoft.report.internal.binding.OrderInfo;
import inetsoft.report.internal.binding.TopNInfo;
import inetsoft.uql.VariableTable;
import inetsoft.uql.asset.DateRangeRef;
import inetsoft.uql.util.XSourceInfo;
import inetsoft.util.Tool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.io.PrintWriter;
import java.util.Vector;

/**
 * Defines the cell binding for table layout. A cell can be bound to a static
 * text, a column, a formula.
 *
 * @hidden
 * @version 10.3
 * @author InetSoft Technology Corp
 */
public class TableCellBinding extends GroupableCellBinding {
   /**
    * Default row or column group. If the group is set to default, the left or
    * upper group is used as the row/column group at runtime. To explicitly
    * set parent group to empty, the row/column group should be set to null.
    */
   public static final String DEFAULT_GROUP = "(default)";

   /**
    * Get a default cell binding, no expansion, detail btype.
    */
   public static TableCellBinding getDefaultBinding(String name) {
      TableCellBinding binding = new TableCellBinding(BIND_COLUMN, name);
      binding.setBType(DETAIL);
      binding.setExpansion(EXPAND_NONE);
      return binding;
   }

   /**
    * Create a group cell binding.
    */
   public static TableCellBinding getGroupBinding(String gname) {
      TableCellBinding binding = new TableCellBinding(BIND_COLUMN, gname);
      binding.setBType(GROUP);
      binding.setExpansion(EXPAND_V);
      return binding;
   }

   /**
    * Create a summary cell binding.
    */
   public static TableCellBinding getSummaryBinding(String sname) {
      TableCellBinding binding = new TableCellBinding(BIND_COLUMN, sname);
      binding.setBType(SUMMARY);
      binding.setExpansion(EXPAND_V);
      return binding;
   }

   /**
    * Create a detail cell binding.
    */
   public static TableCellBinding getDetailBinding(String dname) {
      TableCellBinding binding = new TableCellBinding(BIND_COLUMN, dname);
      binding.setBType(DETAIL);
      binding.setExpansion(EXPAND_V);
      return binding;
   }

   /**
    * Default constructor.
    */
   public TableCellBinding() {
   }

   /**
    * Create a binding with specified type.
    * @param type one of the binding types defined in TableLayout.
    * @param value the binding value. See getValue().
    */
   public TableCellBinding(int type, String value) {
      super(type, value);
   }

   /**
    * Get the cell name. If the name is not explicitly set, and this cell is
    * defined as a grouping cell, the column name (plus date option) is used
    * as the default cell name.
    */
   public String getRuntimeCellName() {
      if(name != null) {
         return name;
      }

      if(getBType() == GROUP && !isEmpty()) {
         String val = getValue();

         if(getDateOption() <= 0) {
            // none date group set to 'None(...)' from CalcTableVSAQuery.createCrosstabAssemblies
            // but it doesn't include 'None()' from other places. (50153, 50190)
            if(getDateOption() == 0 && val.startsWith("None(") && val.endsWith(")")) {
               val = val.substring(5, val.length() - 1);
            }

            return getValidName(val);
         }

         String str = DateRangeRef.getName("", getDateOption());

         if(str != null && val.startsWith(str.substring(0, str.length() - 1))) {
            int idx2 = val.lastIndexOf(")");
            val = val.substring(str.length() - 1, idx2 >= 0 ?
               idx2 : val.length() - 1);
         }

         val = getValidName(val);
         return val + "_" + str.substring(0, str.indexOf('('));
      }

      if(getBType() == SUMMARY && !isEmpty()) {
         return getValidName(getDisplayName(getFormula(), getValue()));
      }

      return null;
   }

   private String getDisplayName(String formula, String cellName) {
      if(formula == null) {
         return "(" + cellName + ")";
      }

      int index = formula.indexOf("(");

      if(index > 0) {
         return formula.substring(0, index) + "(" + cellName + ")";
      }

      index = formula.indexOf("<");

      if(index > 0) {
         return formula.substring(0, index) + "(" + cellName + ")";
      }

      return formula + "(" + cellName + ")";
   }

   private static final char BACK = '_';

   private static String getValidName(String name) {
      StringBuilder vname = new StringBuilder();

      for(int i = 0; i < name.length(); i++) {
         char c = name.charAt(i);

         if(i == 0 && !Character.isJavaIdentifierStart(c) ||
            !Character.isJavaIdentifierPart(c))
         {
            c = BACK;
         }

         vname.append(c);
      }

      return vname.toString();
   }

   /**
    * Get the cell name.
    */
   public String getCellName() {
      return name;
   }

   /**
    * Set the cell name.
    */
   public void setCellName(String name) {
      this.name = name;
   }

   /**
    * Check if expanded cells should be merged.
    */
   public boolean isMergeCells() {
      return mergeCells;
   }

   /**
    * Set wheter expanded cells should be merged.
    */
   public void setMergeCells(boolean merge) {
      // @by davyc, for merge cell option will be merged to group filter,
      // so check table mode need to compare this option, each time we
      // set the merge cells option should be take care
      this.mergeCells = merge;
   }

   /**
    * Get the name of the row group cell for merging expanded cells.
    * If it's set, only the cells within the same group are merged.
    */
   public String getMergeRowGroup() {
      return mergeRowGroup;
   }

   /**
    * Set the row group for merging the cells.
    */
   public void setMergeRowGroup(String group) {
      this.mergeRowGroup = group;
   }

   /**
    * Get the name of the column group cell for merging expanded cells.
    * If it's set, only the cells within the same group are merged.
    */
   public String getMergeColGroup() {
      return mergeColGroup;
   }

   /**
    * Set the column group for merging the cells.
    */
   public void setMergeColGroup(String group) {
      this.mergeColGroup = group;
   }

   /**
    * Get the row group of this cell.
    */
   public String getRowGroup() {
      return rowGroup;
   }

   /**
    * Set the row group of this cell. Setting the row group of a cell makes
    * it a nested group/cell of the parent group. The parent group is
    * expanded first.
    */
   public void setRowGroup(String group) {
      this.rowGroup = group;
   }

   /**
    * Get the column group of this cell.
    */
   public String getColGroup() {
      return colGroup;
   }

   /**
    * Set the column group of this cell. Setting the column group of a
    * cell makes it a nested group/cell of the parent group.
    * The parent group is expanded first.
    */
   public void setColGroup(String group) {
      this.colGroup = group;
   }

   /**
    * Check if a page break should be inserted after this group.
    */
   public boolean isPageAfter() {
      return pageAfter;
   }

   /**
    * Set the page after flag. This should only be set on a vertically
    * expanding cell.
    */
   public void setPageAfter(boolean pageAfter) {
      this.pageAfter = pageAfter;
   }

   /**
    * Get the formula defined in which table mode.
    */
   public int getFormulaMode() {
      return formulaMode;
   }

   /**
    * Set the formula defined in which table mode.
    * This is only used in calc table.
    */
   public void setFormulaMode(int fmode) {
      this.formulaMode = fmode;
   }

   /**
    * Get the date grouping option.
    */
   public int getDateOption() {
      return getOrderInfo(true).getOption();
   }

   /**
    * Set the date grouping option.
    * This is only used in calc table.
    * @param op date grouping option defined in DateRangeRef.
    */
   public void setDateOption(int op) {
      getOrderInfo(true).setInterval(getOrderInfo(true).getInterval(), op);
   }

   /**
    * Get the aggregate formula.
    */
   public String getFormula() {
      return formula;
   }

   /**
    * Set the aggregate formula.
    * @param formula the formula defined in SummaryAttr.
    */
   @Override
   public void setFormula(String formula) {
      this.formula = formula;
   }

   /**
    * Get the vs calc aggregate expression.
    */
   public String getExpression() {
      return expression;
   }

   /**
    * Set the vs calc aggregate expression.
    */
   public void setExpression(String expression) {
      this.expression = expression;
   }

   /**
    * Get the grouping ordering.
    */
   public OrderInfo getOrderInfo(boolean createIfNull) {
      if(orderInfo == null && createIfNull) {
         orderInfo = new OrderInfo();
         // clear default date option
         orderInfo.setInterval(0, 0);
      }

      return orderInfo;
   }

   /**
    * Set the grouping ordering.
    */
   public void setOrderInfo(OrderInfo orderInfo) {
      this.orderInfo = orderInfo;
   }

   /**
    * Get the ranking data
    */
   public TopNInfo getTopN(boolean createIfNull) {
      if(topN == null && createIfNull) {
         topN = new TopNInfo();
      }

      return topN;
   }

   /**
    * Sets the ranking data
    */
   public void setTopN(TopNInfo topN) {
      this.topN = topN;
   }

   /**
    * Gets the name of the source of the bound column.
    *
    * @return the column source.
    */
   public String getSource() {
      return source;
   }

   /**
    * Sets the name of the source of the bound column.
    *
    * @param source the column source.
    */
   public void setSource(String source) {
      this.source = source;
   }

   /**
    * Gets the prefix of the source of the bound column.
    *
    * @return the column source prefix.
    */
   public String getSourcePrefix() {
      return sourcePrefix;
   }

   /**
    * Sets the prefix of the source of the bound column.
    *
    * @param sourcePrefix the column source prefix.
    */
   public void setSourcePrefix(String sourcePrefix) {
      this.sourcePrefix = sourcePrefix;
   }

   /**
    * Gets the type of the source of the bound column.
    *
    * @return the column source type.
    */
   public int getSourceType() {
      return sourceType;
   }

   /**
    * Sets the type of the source of the bound column.
    *
    * @param sourceType the column source type.
    */
   public void setSourceType(int sourceType) {
      this.sourceType = sourceType;
   }

   public boolean isTimeSeries() {
      return timeSeries;
   }

   public void setTimeSeries(boolean timeSeries) {
      this.timeSeries = timeSeries;
   }

   /**
    * Write attributes.
    * @param writer the specified writer.
    */
   @Override
   protected void writeAttributes(PrintWriter writer) {
      super.writeAttributes(writer);
      writer.print(" pageAfter=\"" + pageAfter +
                   "\" mergeCells=\"" + mergeCells +
                   "\" timeSeries=\"" + timeSeries +
                   "\" fmode=\"" + formulaMode + "\"");
   }

   /**
    * Write contents.
    * @param writer the specified writer.
    */
   @Override
   protected void writeContents(PrintWriter writer) {
      super.writeContents(writer);

      if(name != null) {
         writer.println("<name><![CDATA[" + name + "]]></name>");
      }

      if(mergeRowGroup != null) {
         writer.println("<mergeRowGrp><![CDATA[" + mergeRowGroup +
            "]]></mergeRowGrp>");
      }

      if(mergeColGroup != null) {
         writer.println("<mergeColGrp><![CDATA[" + mergeColGroup +
            "]]></mergeColGrp>");
      }

      if(rowGroup != null) {
         writer.println("<rowGroup><![CDATA[" + rowGroup + "]]></rowGroup>");
      }

      if(colGroup != null) {
         writer.println("<colGroup><![CDATA[" + colGroup + "]]></colGroup>");
      }

      if(formula != null) {
         writer.print("<formula><![CDATA[" + formula + "]]></formula>");
      }

      if(expression != null) {
         writer.print("<expression><![CDATA[" + expression + "]]></expression>");
      }

      if(orderInfo != null) {
         orderInfo.writeXML(writer);
      }

      if(topN != null) {
         topN.writeXML(writer);
      }

      if(source != null) {
         writer.print("<source><![CDATA[" + source + "]]></source>");
         writer.print("<sourceType>" + sourceType + "</sourceType>");

         if(sourcePrefix != null) {
            writer.print(
               "<sourcePrefix><![CDATA[" + sourcePrefix + "]]></sourcePrefix>");
         }
      }
   }

   /**
    * Parse contents.
    * @param tag the specified xml element.
    */
   @Override
   protected void parseAttributes(Element tag) throws Exception {
      super.parseAttributes(tag);
      String val;

      if((val = Tool.getAttribute(tag, "pageAfter")) != null) {
         pageAfter = "true".equals(val);
      }

      if((val = Tool.getAttribute(tag, "timeSeries")) != null) {
         timeSeries = "true".equals(val);
      }

      if((val = Tool.getAttribute(tag, "mergeCells")) != null) {
         mergeCells = "true".equals(val);
      }

      if((val = Tool.getAttribute(tag, "fmode")) != null) {
         formulaMode = Integer.parseInt(val);
      }

      if((val = Tool.getAttribute(tag, "dateOp")) != null) {
         if(val != null) {
            setDateOption(Integer.parseInt(val));
         }
      }
   }

   /**
    * Parse contents.
    * @param elem the specified xml element.
    */
   @Override
   protected void parseContents(Element elem) throws Exception {
      super.parseContents(elem);

      name = Tool.getChildValueByTagName(elem, "name");
      mergeRowGroup = Tool.getChildValueByTagName(elem, "mergeRowGrp");
      mergeColGroup = Tool.getChildValueByTagName(elem, "mergeColGrp");
      rowGroup = Tool.getChildValueByTagName(elem, "rowGroup");
      colGroup = Tool.getChildValueByTagName(elem, "colGroup");
      formula = Tool.getChildValueByTagName(elem, "formula");
      expression = Tool.getChildValueByTagName(elem, "expression");

      Element node = Tool.getChildNodeByTagName(elem, "groupSort");

      if(node != null) {
         orderInfo = new OrderInfo();
         orderInfo.parseXML(node);
      }

      node = Tool.getChildNodeByTagName(elem, "topn");
      if(node != null) {
         topN = new TopNInfo();
         topN.parseXML(node);
      }

      if((node = Tool.getChildNodeByTagName(elem, "source")) != null) {
         source = Tool.getValue(node);
         node = Tool.getChildNodeByTagName(elem, "sourceType");
         sourceType = Integer.parseInt(Tool.getValue(node));

         if((node = Tool.getChildNodeByTagName(elem, "sourcePrefix")) != null) {
            sourcePrefix = Tool.getValue(node);
         }
      }

      // Process bc problem, in old version, the order and interval is save in
      // properties. We should fetch them and set them to the order info.
      Element propnode = Tool.getChildNodeByTagName(elem, "properties");

      if(propnode != null) {
         NodeList list = propnode.getChildNodes();

         for(int i = 0; i < list.getLength(); i++) {
            if(!(list.item(i) instanceof Element)) {
               continue;
            }

            node = (Element) list.item(i);
            String key = Tool.getChildValueByTagName(node, "key");
            String val = Tool.getChildValueByTagName(node, "value");

            if("order".equals(key)) {
               getOrderInfo(true).setOrder(Integer.parseInt(val));
            }
            else if("interval".equals(key)) {
               getOrderInfo(true).setInterval(Double.parseDouble(val),
                  getOrderInfo(true).getOption());
            }
         }
      }
   }

   /**
    * Make a copy of the object.
    */
   @Override
   public Object clone() {
      try {
         TableCellBinding obj = (TableCellBinding) super.clone();

         if(orderInfo != null) {
            obj.orderInfo = (OrderInfo) orderInfo.clone();
         }

         if(topN != null) {
            obj.topN = (TopNInfo) topN.clone();
         }

         return obj;
      }
      catch(Exception ex) {
         LOG.error("Failed to clone cell binding", ex);
      }

      return null;
   }

   /**
    * Check the obj is equals with this object or not.
    */
   @Override
   public boolean equalsContent(Object obj) {
      if(!super.equalsContent(obj)) {
         return false;
      }

      if(isEmpty() && mergeCells && obj == null) {
         return false;
      }

      if(isEmpty() && !mergeCells && obj == null) {
         return true;
      }

      if(!(obj instanceof TableCellBinding)) {
         return false;
      }

      TableCellBinding cell = (TableCellBinding) obj;
      return mergeCells == ((TableCellBinding) obj).mergeCells;
   }

   /**
    * Check the obj is equals with this object or not.
    */
   public boolean equals(Object obj) {
      if(!(obj instanceof TableCellBinding)) {
         return false;
      }

      TableCellBinding binding = (TableCellBinding) obj;
      boolean isSourceSame = true;

      if(getSource() != null && binding.getSource() != null) {
         isSourceSame = Tool.equals(getSource(), binding.getSource()) &&
            Tool.equals(getSourceType(), binding.getSourceType()) &&
            Tool.equals(getSourcePrefix(), binding.getSourcePrefix());
      }

      return super.equals(binding) && binding.pageAfter == pageAfter &&
         binding.timeSeries == timeSeries &&
         binding.mergeCells == mergeCells && Tool.equals(binding.name, name) &&
         Tool.equals(binding.mergeRowGroup, mergeRowGroup) &&
         Tool.equals(binding.mergeColGroup, mergeColGroup) &&
         Tool.equals(binding.rowGroup, rowGroup) &&
         Tool.equals(binding.colGroup, colGroup) &&
         Tool.equals(formula, binding.formula) &&
         Tool.equals(binding.orderInfo, orderInfo) &&
         Tool.equals(binding.topN, topN) && isSourceSame &&
         Tool.equals(binding.expression, expression);
   }

   public int hashCode() {
      int hash = super.hashCode();
      hash += 7 * (pageAfter ? 1 : 0);
      hash += 11 * (mergeCells ? 10 : 0);
      hash += 13 * (name != null ? name.hashCode() : 0);
      hash += 17 * (mergeRowGroup != null ? mergeRowGroup.hashCode() : 0);
      hash += 19 * (mergeColGroup != null ? mergeColGroup.hashCode() : 0);
      hash += 23 * (rowGroup != null ? rowGroup.hashCode() : 0);
      hash += 29 * (colGroup != null ? colGroup.hashCode() : 0);
      hash += 31 * formulaMode;
      hash += 41 * (orderInfo != null ? orderInfo.hashCode() : 0);
      hash += 43 * (topN != null ? topN.hashCode() : 0);
      hash += 47 * (timeSeries ? 1 : 0);

      if(formula != null) {
         hash = hash + formula.hashCode();
      }

      if(expression != null) {
         hash = hash + expression.hashCode();
      }

      return hash;
   }

   public String toString() {
      return super.toString() + ":" + rowGroup;
   }

   /**
    * Get all variables.
    */
   public Vector getAllVariables() {
      return orderInfo == null ? new Vector() : orderInfo.getAllVariables();
   }

   public boolean replaceVariables(VariableTable vars) {
      if(orderInfo != null) {
         return orderInfo.replaceVariables(vars);
      }

      return false;
   }

   public String getName() {
      return name;
   }

   // default is 0, so getRuntimeCellName can working correct for non-date
   // column, for date column, it should be maintain correct by outer
   private String formula = null;

   private String name;
   private String mergeRowGroup;
   private String mergeColGroup;
   private String rowGroup;
   private String colGroup;
   private boolean pageAfter;
   private boolean mergeCells;
   private OrderInfo orderInfo = null;
   private TopNInfo topN = null;
   private String source = null;
   private String sourcePrefix = null;
   private String expression = null; // vs calc aggregate field script
   private int sourceType = XSourceInfo.NONE;
   private boolean timeSeries;

   // if define formula, remember current table mode
   private int formulaMode = -1;

   private static final Logger LOG =
      LoggerFactory.getLogger(TableCellBinding.class);
}
