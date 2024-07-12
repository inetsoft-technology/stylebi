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
package inetsoft.uql.viewsheet.graph;

import inetsoft.graph.data.CalcColumn;
import inetsoft.report.composition.graph.calc.ChangeCalc;
import inetsoft.report.composition.graph.calc.ValueOfCalc;
import inetsoft.report.internal.binding.GroupField;
import inetsoft.report.internal.binding.OrderInfo;
import inetsoft.sree.SreeEnv;
import inetsoft.uql.asset.DateRangeRef;
import inetsoft.uql.erm.DataRef;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.viewsheet.VSDimensionRef;
import inetsoft.uql.viewsheet.XDimensionRef;
import inetsoft.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.*;

import java.io.*;
import java.util.*;
import java.util.stream.Collectors;

/**
 * AbstractCalc.
 *
 * @version 10.3
 * @author InetSoft Technology Corp
 */
public abstract class AbstractCalc implements Calculator {
   /**
    * Compute for inner row dimension of crosstab.
    */
   public static final String ROW_INNER = "0";
   /**
    * Compute for inner column dimension of crosstab.
    */
   public static final String COLUMN_INNER = "1";
   /**
    * Label for inner row which may display in calc prefix.
    */
   public static final String ROW_INNER_LABEL = "by row";
   /**
    * Label for inner column which may display in calc prefix.
    */
   public static final String COLUMN_INNER_LABEL = "by column";

   public static final Calculator createCalc(Element element) {
      if(element == null) {
         return null;
      }

      String cls = Tool.getAttribute(element, "class");
      Calculator calc = null;

      try {
         calc = (Calculator) Class.forName(cls).newInstance();
         calc.parseXML(element);
      }
      catch(Exception e) {
         // ignore it
      }

      return calc;
   }

   /**
    * Write the xml segment to print writer.
    * @param writer the destination print writer.
    */
   @Override
   public final void writeXML(PrintWriter writer) {
      writer.print("<calc class=\"" + getClass().getName() + "\"");
      writeAttribute(writer);
      writer.println(">");
      writeContent(writer);
      writer.println("</calc>");
   }

   /**
    * Method to parse an xml segment.
    */
   @Override
   public final void parseXML(Element tag) throws Exception {
      parseAttribute(tag);
      parseContent(tag);
   }

   /**
    * Get calc prefix.
    */
   @Override
   public final String getPrefix() {
      return getPrefix0() + ": ";
   }

   /**
    * Get calc prefix.
    */
   @Override
   public final String getPrefixView() {
      return getPrefixView0() + ": ";
   }

   /**
    * Get calc prefix.
    */
   @Override
   public final String toView() {
      if(getAlias() != null) {
         return getAlias();
      }

      return toView0();
   }

   /**
    * Get name.
    */
   @Override
   public String getName() {
      if(getAlias() != null) {
         return getAlias();
      }

      return getName0();
   }

   /**
    * Get alias.
    * @return alias.
    */
   @Override
   public String getAlias() {
      return catalog.getString(alias);
   }

   /**
    * Set alias.
    * @param alias to be set.
    */
   @Override
   public void setAlias(String alias) {
      this.alias = alias;
   }

   @Override
   public Object clone() {
      try {
         AbstractCalc calc = (AbstractCalc) super.clone();
         calc.alias = alias;
         return calc;
      }
      catch(CloneNotSupportedException e) {
         return null;
      }
   }

   /**
    * Check if equals another object.
    */
   public boolean equals(Object obj) {
      return obj instanceof AbstractCalc;
   }

   /**
    * Write attributes.
    */
   protected void writeAttribute(PrintWriter writer) {
      if(getAlias() != null) {
         writer.print(" alias=\"" +  getAlias() + "\"");
      }

      if(getPrefix() != null) {
         writer.print(" prefix=\"" + getPrefix() + "\"");
      }
   }

   /**
    * Write content.
    */
   protected void writeContent(PrintWriter writer) {
      // do nothing
   }

   /**
    * Parse attributes.
    */
   protected void parseAttribute(Element root) throws Exception {
      alias = Tool.getAttribute(root, "alias");
   }

   /**
    * Parse content.
    */
   protected void parseContent(Element root) throws Exception {
      // do nothing
   }

   protected abstract String getName0();

   /**
    * Get calc prefix view.
    */
   public abstract String getColumnName();

   /**
    * Get calc prefix view.
    */
   protected String getColumnView() {
      String columnName = getColumnName();

      if(columnName == null || columnName.isEmpty()) {
         return null;
      }

      if(Tool.equals(AbstractCalc.ROW_INNER, columnName)) {
         return ROW_INNER_LABEL;
      }

      if(Tool.equals(AbstractCalc.COLUMN_INNER, columnName)) {
         return COLUMN_INNER_LABEL;
      }

      return columnName;
   }

   /**
    * Get calc prefix.
    */
   protected abstract String getPrefix0();

   /**
    * Get calc prefix view.
    */
   protected abstract String getPrefixView0();

   /**
    * Get calc prefix view.
    */
   protected abstract String toView0();

   /**
    * Build in calcs
    */
   private static List<Calculator> getBuiltinCalculators() {
      parseBuiltinCalculators();
      return buildinCalcs;
   }

   /**
    * Get default built-in-calculations.
    */
   public static Calculator[] getDefaultCalcs(DataRef...dateDims) {
      return getDefaultCalcs(true, dateDims);
   }

   /**
    * Get default built-in-calculations.
    */
   public static Calculator[] getDefaultCalcs(boolean clone, DataRef...dateDims) {
      List<Calculator> parsedCalcs = AbstractCalc.getBuiltinCalculators();
      List<Calculator> calcList = new ArrayList<>();
      calcList.add(null); // "None" option in comboBox

      for(DataRef ref : dateDims) {
         int dlevel = -1;
         String fullName = null;

         if(ref instanceof XDimensionRef) {
            dlevel = ((XDimensionRef) ref).getDateLevel();
            fullName = ((XDimensionRef) ref).getFullName();
         }
         else if(ref instanceof GroupField) {
            OrderInfo order = ((GroupField) ref).getOrderInfo();
            dlevel = order.getOption();
            fullName = ((GroupField) ref).getFullName();
         }
         else {
            continue;
         }

         String dtype = DateRangeRef.getDataType(dlevel);
         boolean dateCalc = XSchema.isDateType(dtype) &&
            (dlevel == DateRangeRef.QUARTER_INTERVAL ||
             dlevel == DateRangeRef.MONTH_INTERVAL ||
             dlevel == DateRangeRef.DAY_INTERVAL);

         if(dateCalc) {
            boolean weekly = dlevel == DateRangeRef.DAY_INTERVAL;
            String columnPrefix = weekly ? "common.changeOfWeek" : "common.changeOfYear";
            String columnLabel = ref.getName();
            int from = weekly ? ChangeCalc.PREVIOUS_WEEK : ChangeCalc.PREVIOUS_YEAR;

            String alias = catalog.getString(columnPrefix) + " " + columnLabel;
            addUnique(calcList, createChangeCalc(from, fullName, true, alias));
            alias = catalog.getString(weekly ? "common.changeOfWeekOne" :
               "common.changeOfYearOne") + " " + columnLabel;
            addUnique(calcList, createChangeCalc(from, fullName, false, alias));
         }

         String columnLabel = fullName;
         int from = ChangeCalc.PREVIOUS;

         String alias = catalog.getString("common.change") + " " + columnLabel;
         addUnique(calcList, createChangeCalc(from, fullName, true, alias));
         alias = catalog.getString("Change from previous") + " " + columnLabel;
         addUnique(calcList, createChangeCalc(from, fullName, false, alias));
         alias = catalog.getString("Value of previous") + " " + columnLabel;
         addUnique(calcList, createValueOfCalc(from, fullName, alias));
      }

      if(parsedCalcs != null) {
         if(dateDims.length == 0) {
            calcList.addAll(parsedCalcs);
         }
         else {
            calcList.addAll(parsedCalcs.stream().filter(c -> !(c instanceof ValueOfCalc))
                            .collect(Collectors.toList()));
         }
      }

      calcList.add(getCustomCalc());

      if(!clone) {
         return calcList.toArray(new Calculator[calcList.size()]);
      }

      Calculator[] calcs = new Calculator[calcList.size()];

      for(int i = 0; i < calcList.size(); i++) {
         if(calcList.get(i) != null) {
            calcs[i] = (Calculator) calcList.get(i).clone();
         }
      }

      return calcs;
   }


   // Add if doesn't exist
   private static void addUnique(List<Calculator> calcList, Calculator calc) {
      if(!calcList.stream()
         .anyMatch(c -> c != null && Objects.equals(c.getAlias(), calc.getAlias())))
      {
         calcList.add(calc);
      }
   }

   private static ChangeCalc createChangeCalc(int from, String columnName, boolean percent,
                                              String alias)
   {
      ChangeCalc calc = new ChangeCalc();
      calc.setFrom(from);
      calc.setColumnName(columnName);
      calc.setAsPercent(percent);
      calc.setAlias(alias);
      return calc;
   }

   private static ValueOfCalc createValueOfCalc(int from, String columnName, String alias) {
      ValueOfCalc calc = new ValueOfCalc();
      calc.setFrom(from);
      calc.setColumnName(columnName);
      calc.setAlias(alias);

      return calc;
   }

   /**
    * Create a custom-define calculator.
    */
   private static Calculator getCustomCalc() {
      if(custom == null) {
         custom = new CustomCalc();
      }

      return custom;
   }

   protected String getDynamicColumn(Object value) {
      if(value instanceof Object[]) {
         Object[] objects = (Object[]) value;

         return String.valueOf(objects[objects.length - 1]);
      }

      return value == null ? null : String.valueOf(value);
   }

   /**
    * "Custom..." option in built-in comboBox.
    */
   public static class CustomCalc extends AbstractCalc {
      @Override
      protected String getName0() {
         return catalog.getString("Custom") + "...";
      }

      @Override
      public CalcColumn createCalcColumn(String column) {
         return null;
      }

      @Override
      public void updateRefs(List<VSDimensionRef> oldRefs, List<VSDimensionRef> newRefs) {
         // no-op
      }

      @Override
      public int getType() {
         return Calculator.CUSTOM;
      }

      public boolean equals(Object obj) {
         return obj instanceof CustomCalc;
      }

      @Override
      protected String getPrefix0() {
         return null;
      }

      @Override
      protected String getPrefixView0() {
         return getPrefix0();
      }

      @Override
      public String getColumnName() {
         return null;
      }

      @Override
      protected String getColumnView() {
         return null;
      }

      @Override
      protected String toView0() {
         return getName0();
      }
   }

   /**
    * Parse build in.
    */
   private static synchronized void parseBuiltinCalculators() {
      if(buildinCalcs != null && ts == -1L) {
         return;
      }

      catalog = Catalog.getCatalog();
      String path = SreeEnv.getProperty("calculator.file");
      DataSpace space = DataSpace.getDataSpace();
      boolean exists = space.exists(null, path);
      long lts = exists ? space.getLastModified(null, path) : -1L;

      if(buildinCalcs != null && lts == ts) {
         return;
      }

      ts = lts;

      try(InputStream in = getCalculatorInput(exists, path)) {
         if(in == null) {
            LOG.error("No date calculators available!");
            return;
         }

         Document doc = Tool.parseXML(in);
         Element dsnode = doc.getDocumentElement();
         NodeList cnodes = Tool.getChildNodesByTagName(dsnode, "calc");
         buildinCalcs = new ArrayList<>();

         for(int i = 0; i < cnodes.getLength(); i++) {
            Element dnode = (Element) cnodes.item(i);
            buildinCalcs.add(createCalc(dnode));
         }
      }
      catch(Exception ex) {
         LOG.error("Failed to load calculator.xml", ex);
         buildinCalcs = new ArrayList<>();
      }
   }

   private static InputStream getCalculatorInput(boolean exists, String path) throws IOException {
      InputStream in = exists ? DataSpace.getDataSpace().getInputStream(null, path) : null;

      if(in == null) {
         in = Calculator.class.getResourceAsStream("/inetsoft/uql/viewsheet/graph/calculator.xml");
      }

      return in;
   }

   protected String alias;
   private static Calculator custom;
   private static List<Calculator> buildinCalcs;
   private static long ts;
   protected static Catalog catalog = Catalog.getCatalog();
   private static final Logger LOG = LoggerFactory.getLogger(AbstractCalc.class);
}
