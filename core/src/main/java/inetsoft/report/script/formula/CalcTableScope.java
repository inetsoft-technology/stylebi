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
package inetsoft.report.script.formula;

import inetsoft.report.TableLens;
import inetsoft.report.filter.*;
import inetsoft.report.internal.Util;
import inetsoft.report.internal.table.RuntimeCalcTableLens;
import inetsoft.report.lens.CalcTableLens;
import inetsoft.report.script.*;
import inetsoft.util.script.DynamicScope;
import inetsoft.util.script.JavaScriptEngine;
import org.mozilla.javascript.Scriptable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.Iterator;

/**
 * A scriptable used as the execution scope for formulas in a CalcTableLens.
 */
public class CalcTableScope extends PropertyScriptable implements DynamicScope {
   /**
    * Create a scope for a freehand table.
    */
   public CalcTableScope(CalcTableLens table) {
      this.table = table;

      field = new TableRow(table, 0);
      addProperty("field", field);

      if(table instanceof RuntimeCalcTableLens) {
         initRuntimeReferences();
      }
      else {
         initReferences();
      }

      try {
         Class[] params = {Object.class, String.class, String.class};
         Class[] nparams = {Object.class, Object.class, String.class, String.class};
         addFunctionProperty(getClass(), "none", params);
         addFunctionProperty(getClass(), "sum", params);
         addFunctionProperty(getClass(), "average", params);
         addFunctionProperty(getClass(), "count", params);
         addFunctionProperty(getClass(), "countDistinct", params);
         addFunctionProperty(getClass(), "max", params);
         addFunctionProperty(getClass(), "min", params);
         addFunctionProperty(getClass(), "product", params);
         addFunctionProperty(getClass(), "concat", params);
         addFunctionProperty(getClass(), "standardDeviation", params);
         addFunctionProperty(getClass(), "variance", params);
         addFunctionProperty(getClass(), "populationVariance", params);
         addFunctionProperty(getClass(), "populationStandardDeviation", params);
         addFunctionProperty(getClass(), "median", params);
         addFunctionProperty(getClass(), "mode", params);
         addFunctionProperty(getClass(), "pthPercentile", nparams);
         addFunctionProperty(getClass(), "nthLargest", nparams);
         addFunctionProperty(getClass(), "nthSmallest", nparams);
         addFunctionProperty(getClass(), "nthMostFrequent", nparams);

         Class[] sparams = {Object.class, Object.class, Object.class, Object.class};
         addFunctionProperty(getClass(), "correlation", sparams);
         addFunctionProperty(getClass(), "covariance", sparams);
         addFunctionProperty(getClass(), "weightedAverage", sparams);

         addFunctionProperty(getClass(), "first", sparams);
         addFunctionProperty(getClass(), "last", sparams);
      }
      catch(Exception e) {
         LOG.error("Failed to register formula table properties and functions", e);
      }
   }

   /**
    * Initialize the runtime named cell references.
    */
   private void initRuntimeReferences() {
      RuntimeCalcTableLens rtable = (RuntimeCalcTableLens) table;
      String[] names = rtable.getCalcTableLens().getCellNames();

      for(int i = 0; i < names.length; i++) {
         CalcRef ref = new CalcRef(rtable, names[i]);

         addProperty("$" + names[i], ref);
      }
   }

   /**
    * Initialize the static named cell references.
    */
   private void initReferences() {
      for(int i = 0; i < table.getRowCount(); i++) {
         for(int j = 0; j < table.getColCount(); j++) {
            String name = table.getCellName(i, j);

            if(name != null) {
               addProperty("$" + name, new DelayedRef(i, j));
            }
         }
      }
   }

   /**
    * Set the current row of the script.
    */
   public void setRow(int row) {
      field.setRow(row);
      addProperty("row", row);
   }

   /**
    * Get the object for getting and setting properties.
    */
   @Override
   protected Object getObject() {
      return table;
   }

   /**
    * Get the name of this scriptable.
    */
   @Override
   public String getClassName() {
      return "CalcTableLens";
   }

   /**
    * Get a property value.
    */
   @Override
   public Object get(String id, Scriptable start) {
      Object val = super.get(id, start);

      if(val instanceof DelayedRef) {
         val = ((DelayedRef) val).get();
      }

      return val;
   }

   /**
    * Make a copy of this scope.
    */
   @Override
   public CalcTableScope clone() {
      try {
         return (CalcTableScope) super.clone();
      }
      catch(Exception ex) {
         LOG.error("Failed to clone object", ex);
      }

      return null;
   }

   /**
    * Calculate the none of a group on the summary column.
    * @param column summary column.
    * @param group group column.
    * @param cond optional condition expression for conditional summary.
    */
   public Object none(Object column, String group, String cond) {
      // because js does not support function overloading, the function
      // defined in the report scope would not be found if it's called from
      // this cope, we need to check for the object type and forward the call
      if(isCellRange(column)) {
         return summarize((String) column, "none", new NoneFormula());
      }

      TableLens lens = (TableLens)
         PropertyDescriptor.convert(column, TableLens.class);

      if(lens == null) {
         return summarize2(column, new NoneFormula());
      }
      else {
         // report scope function, (table, column)
         return ReportJavaScriptEngine.summarize(
            column, group, "none", new NoneFormula(), cond, this);
      }
   }

   /**
    * Calculate the sum of a group on the summary column.
    * @param column summary column.
    * @param group group column.
    * @param cond optional condition expression for conditional summary.
    */
   public Double sum(Object column, String group, String cond) {
      Formula formula = new SumFormula();
      formula.setDefaultResult(isDefaultToZero());

      // because js does not support function overloading, the function
      // defined in the report scope would not be found if it's called from
      // this cope, we need to check for the object type and forward the call
      if(isCellRange(column)) {
         return toDouble(summarize((String) column, "sum", formula));
      }
      else {
         // report scope function, (table, column)
         return toDouble(ReportJavaScriptEngine.summarize(
                            column, group, "sum", formula, cond, this));
      }
   }

   /**
    * Calculate the average of a group on the summary column.
    * @param column summary column.
    * @param group group column.
    * @param cond optional condition expression for conditional summary.
    */
   public Double average(Object column, String group, String cond) {
      Formula formula = new AverageFormula();
      formula.setDefaultResult(isDefaultToZero());

      // because js does not support function overloading, the function
      // defined in the report scope would not be found if it's called from
      // this cope, we need to check for the object type and forward the call
      if(isCellRange(column)) {
         return toDouble(summarize((String) column, "average", formula));
      }
      else {
         // report scope function, (table, column)
         return toDouble(ReportJavaScriptEngine.summarize(
                            column, group, "average", formula, cond, this));
      }
   }

   /**
    * Calculate the total count of a group on the summary column.
    * @param column summary column.
    * @param group group column.
    * @param cond optional condition expression for conditional summary.
    */
   public Double count(Object column, String group, String cond) {
      Formula formula = new CountFormula();
      formula.setDefaultResult(isDefaultToZero());

      // because js does not support function overloading, the function
      // defined in the report scope would not be found if it's called from
      // this cope, we need to check for the object type and forward the call
      if(isCellRange(column)) {
         return toDouble(summarize((String) column, "count", formula));
      }
      else {
         // report scope function, (table, column)
         return toDouble(ReportJavaScriptEngine.summarize(
                            column, group, "count", formula, cond, this));
      }
   }

   /**
    * Calculate the distinct count of a group on the summary column.
    * @param column summary column.
    * @param group group column.
    * @param cond optional condition expression for conditional summary.
    */
   public Double countDistinct(Object column, String group, String cond) {
      Formula formula = new DistinctCountFormula();
      formula.setDefaultResult(isDefaultToZero());

      // because js does not support function overloading, the function
      // defined in the report scope would not be found if it's called from
      // this cope, we need to check for the object type and forward the call
      if(isCellRange(column)) {
         return toDouble(summarize((String) column, "distinct count", formula));
      }
      else {
         // report scope function, (table, column)
         return toDouble(ReportJavaScriptEngine.summarize(
                            column, group, "distinct count", formula, cond, this));
      }
   }

   /**
    * Calculate the max of a group on the summary column.
    * @param column summary column.
    * @param group group column.
    * @param cond optional condition expression for conditional summary.
    */
   public Object max(Object column, String group, String cond) {
      Formula formula = new MaxFormula();
      // default for max is not meaningul (and different from previous behavior). (56609, 58595)
      //formula.setDefaultResult(isDefaultToZero("max"));

      // because js does not support function overloading, the function
      // defined in the report scope would not be found if it's called from
      // this cope, we need to check for the object type and forward the call
      if(isCellRange(column)) {
         return summarize((String) column, "max", formula);
      }

      TableLens lens = (TableLens) PropertyDescriptor.convert(column, TableLens.class);

      if(lens == null) {
         return summarize2(column, formula);
      }
      else {
         // report scope function, (table, column)
         return ReportJavaScriptEngine.summarize(column, group, "max", formula, cond, this);
      }
   }

   /**
    * Calculate the min of a group on the summary column.
    * @param column summary column.
    * @param group group column.
    * @param cond optional condition expression for conditional summary.
    */
   public Object min(Object column, String group, String cond) {
      Formula formula = new MinFormula();
      // default is not meaningful.
      //formula.setDefaultResult(isDefaultToZero("min"));

      // because js does not support function overloading, the function
      // defined in the report scope would not be found if it's called from
      // this cope, we need to check for the object type and forward the call
      if(isCellRange(column)) {
         return summarize((String) column, "min", formula);
      }

      TableLens lens = (TableLens)
         PropertyDescriptor.convert(column, TableLens.class);

      if(lens == null) {
         return summarize2(column, formula);
      }
      else {
         // report scope function, (table, column)
         return ReportJavaScriptEngine.summarize(column, group, "min", formula, cond, this);
      }
   }

   private Object summarize2(Object obj, Formula formula) {
      if(obj == null) {
         return formula.isDefaultResult() ? formula.getResult() : null;
      }

      Object[] arr = JavaScriptEngine.split(obj);

      for(int i = 0; i < arr.length; i++) {
         formula.addValue(arr[i]);
      }

      return formula.getResult();
   }

   /**
    * Calculate the product of a group on the summary column.
    * @param column summary column.
    * @param group group column.
    * @param cond optional condition expression for conditional summary.
    */
   public Double product(Object column, String group, String cond) {
      Formula formula = new ProductFormula();
      formula.setDefaultResult(isDefaultToZero());

      // because js does not support function overloading, the function
      // defined in the report scope would not be found if it's called from
      // this cope, we need to check for the object type and forward the call
      if(isCellRange(column)) {
         return toDouble(summarize((String) column, "product", formula));
      }
      else {
         // report scope function, (table, column)
         return toDouble(ReportJavaScriptEngine.summarize(column, group, "product", formula,
                                                          cond, this));
      }
   }

   /**
    * Calculate the concatenation of a group on the summary column.
    * @param column summary column.
    * @param group group column.
    * @param cond optional condition expression for conditional summary.
    */
   public Object concat(Object column, String group, String cond) {
      Formula formula = new ConcatFormula();
      formula.setDefaultResult(isDefaultToZero());

      // because js does not support function overloading, the function
      // defined in the report scope would not be found if it's called from
      // this cope, we need to check for the object type and forward the call
      if(isCellRange(column)) {
         return summarize((String) column, "concat", formula);
      }
      else {
         // report scope function, (table, column)
         return ReportJavaScriptEngine.summarize(column, group, "concat", formula, cond, this);
      }
   }

   /**
    * Calculate the standard deviation of a group on the summary column.
    * @param column summary column.
    * @param group group column.
    * @param cond optional condition expression for conditional summary.
    */
   public Double standardDeviation(Object column, String group, String cond) {
      Formula formula = new StandardDeviationFormula();
      formula.setDefaultResult(isDefaultToZero());

      // because js does not support function overloading, the function
      // defined in the report scope would not be found if it's called from
      // this cope, we need to check for the object type and forward the call
      if(isCellRange(column)) {
         return toDouble(summarize((String) column, "standard deviation", formula));
      }
      else {
         // report scope function, (table, column)
         return toDouble(ReportJavaScriptEngine.summarize(
                            column, group, "standard deviation", formula, cond, this));
      }
   }

   /**
    * Calculate the variance of a group on a summary column.
    * @param column summary column.
    * @param group group column.
    * @param cond optional condition expression for conditional summary.
    */
   public Double variance(Object column, String group, String cond) {
      Formula formula = new VarianceFormula();
      formula.setDefaultResult(isDefaultToZero());

      // because js does not support function overloading, the function
      // defined in the report scope would not be found if it's called from
      // this cope, we need to check for the object type and forward the call
      if(isCellRange(column)) {
         return toDouble(summarize((String) column, "variance", formula));
      }
      else {
         // report scope function, (table, column)
         return toDouble(ReportJavaScriptEngine.summarize(
                            column, group, "variance", formula, cond, this));
      }
   }

   /**
    * Calculate the population variance of a group on a summary column.
    * @param column summary column.
    * @param group group column.
    * @param cond optional condition expression for conditional summary.
    */
   public Double populationVariance(Object column, String group, String cond) {
      Formula formula = new PopulationVarianceFormula();
      formula.setDefaultResult(isDefaultToZero());

      // because js does not support function overloading, the function
      // defined in the report scope would not be found if it's called from
      // this cope, we need to check for the object type and forward the call
      if(isCellRange(column)) {
         return toDouble(summarize((String) column, "population variance", formula));
      }
      else {
         // report scope function, (table, column)
         return toDouble(ReportJavaScriptEngine.summarize(
                            column, group, "population variance", formula, cond, this));
      }
   }

   /**
    * Calculate the population standard deviation of a group on a
    * summary column.
    * @param column summary column.
    * @param group group column.
    * @param cond optional condition expression for conditional summary.
    */
   public Double populationStandardDeviation(Object column, String group, String cond) {
      Formula formula = new PopulationStandardDeviationFormula();
      formula.setDefaultResult(isDefaultToZero());

      // because js does not support function overloading, the function
      // defined in the report scope would not be found if it's called from
      // this cope, we need to check for the object type and forward the call
      if(isCellRange(column)) {
         return toDouble(summarize((String) column, "population standard deviation", formula));
      }
      else {
         // report scope function, (table, column)
         return toDouble(ReportJavaScriptEngine.summarize(
                            column, group, "population standard deviation", formula, cond, this));
      }
   }

   /**
    * Calculate the median of a group on a summary column.
    * @param column summary column.
    * @param group group column.
    * @param cond optional condition expression for conditional summary.
    */
   public Double median(Object column, String group, String cond) {
      Formula formula = new MedianFormula();
      // default is not meaningful.
      //formula.setDefaultResult(isDefaultToZero("median"));

      // because js does not support function overloading, the function
      // defined in the report scope would not be found if it's called from
      // this cope, we need to check for the object type and forward the call
      if(isCellRange(column)) {
         return toDouble(summarize((String) column, "median", formula));
      }
      else {
         // report scope function, (table, column)
         return toDouble(ReportJavaScriptEngine.summarize(
            column, group, "median", formula, cond, this));
      }
   }

   /**
    * Calculate the mode of a group on a summary column.
    * @param column summary column.
    * @param group group column.
    * @param cond optional condition expression for conditional summary.
    */
   public Object mode(Object column, String group, String cond) {
      Formula formula = new ModeFormula();
      // default is not meaningful.
      //formula.setDefaultResult(isDefaultToZero("mode"));

      // because js does not support function overloading, the function
      // defined in the report scope would not be found if it's called from
      // this cope, we need to check for the object type and forward the call
      if(isCellRange(column)) {
         return summarize((String) column, "mode", formula);
      }
      else {
         // report scope function, (table, column)
         return ReportJavaScriptEngine.summarize(column, group, "mode", formula, cond, this);
      }
   }

   /**
    * Calculate correlation result.
    */
   public Object correlation(Object data1, Object data2, Object column2,
                             Object cond) {
      CorrelationFormula formula = new CorrelationFormula(1);
      return calc(data1, data2, column2, cond, formula);
   }

   /**
    * Calculate covariance result.
    */
   public Object covariance(Object data1, Object data2, Object column2,
                            Object cond) {
      CovarianceFormula formula = new CovarianceFormula(1);
      return calc(data1, data2, column2, cond, formula);
   }

   /**
    * Calculate weightedAverage result.
    */
   public Object weightedAverage(Object data1, Object data2, Object column2,
                                 Object cond) {
      WeightedAverageFormula formula = new WeightedAverageFormula(1);
      return calc(data1, data2, column2, cond, formula);
   }

   /**
    * Calculate weightedAverage result.
    */
   public Object first(Object data1, Object data2, Object column2, Object cond) {
      FirstFormula formula = new FirstFormula(findSecondColumn(data1, column2));
      return calc(data1, data2, column2, cond, formula);
   }

   /**
    * Calculate weightedAverage result.
    */
   public Object last(Object data1, Object data2, Object column2, Object cond) {
      LastFormula formula = new LastFormula(findSecondColumn(data1, column2));
      return calc(data1, data2, column2, cond, formula);
   }

   private int findSecondColumn(Object data, Object column2) {
      TableLens table = (TableLens)
         PropertyDescriptor.convert(data, TableLens.class);
      Object ucolumn2 = JavaScriptEngine.unwrap(column2);

      if(table == null || ucolumn2 == null) {
         return 1;
      }

      String column = ucolumn2 + "";
      int cidx = Util.findColumn(table, column);
      return cidx < 0 ? 1 : cidx;
   }

   private Object calc(Object data1, Object data2, Object column2,
                       Object cond, Formula2 formula)
   {
      formula.setDefaultResult(isDefaultToZero());
      Object udata2 = JavaScriptEngine.unwrap(data2);
      Object ucolumn2 = JavaScriptEngine.unwrap(column2);
      Object ucond = JavaScriptEngine.unwrap(cond);

      if((udata2 == null || udata2 instanceof String) &&
         (column2 == null || column2 instanceof String) &&
         (ucond == null || ucond instanceof String))
      {
         return ReportJavaScriptEngine.summarize(data1, (String) udata2,
            (String) ucolumn2, formula, (String) ucond, this);
      }

      Object[] arr1 = JavaScriptEngine.split(data1);
      Object[] arr2 = JavaScriptEngine.split(data2);

      if(arr1 == null || arr2 == null) {
         return null;
      }

      int len = Math.min(arr1.length, arr2.length);

      for(int i = 0; i < len; i++) {
         Object[] val = {arr1[i], arr2[i]};
         formula.addValue(val);
      }

      return formula.getResult();
   }

   /**
    * Calculate the Pth percentile of a group on a summary column.
    * @param p percentile
    * @param column summary column.
    * @param group group column.
    * @param cond optional condition expression for conditional summary.
    */
   public Object pthPercentile(Object p, Object column, String group, String cond) {
      // because js does not support function overloading, the function
      // defined in the report scope would not be found if it's called from
      // this cope, we need to check for the object type and forward the call
      if(p instanceof Number && (column instanceof String || column == null)) {
         int pn = ((Number) p).intValue();
         Formula formula = new PthPercentileFormula(pn);
         formula.setDefaultResult(isDefaultToZero());

         return summarize((String) column, "pth percentile", formula);
      }
      else {
         int pn = ((Number) column).intValue();
         Formula formula = new PthPercentileFormula(pn);
         formula.setDefaultResult(isDefaultToZero());

         // report scope function, (table, n, column)
         return ReportJavaScriptEngine.summarize(p, group, "pth percentile", formula, cond, this);
      }
   }

   /**
    * Calculate the Nth largest of a group on a summary column.
    * @param n nth.
    * @param column summary column.
    * @param group group column.
    * @param cond optional condition expression for conditional summary.
    */
   public Object nthLargest(Object n, Object column, String group,
                            String cond) {
      // because js does not support function overloading, the function
      // defined in the report scope would not be found if it's called from
      // this cope, we need to check for the object type and forward the call
      // @by davyc, when n instanceof number not means it is the formula
      // parameter, because value also may be number too
      // fix bug1285667791839
      if(n instanceof Number && (column instanceof String || column == null)) {
         int pn = ((Number) n).intValue();
         Formula formula = new NthLargestFormula(pn);
         // default is not meaningful.
         //formula.setDefaultResult(isDefaultToZero("nthLargest"));

         return summarize((String) column, "nth largest", formula);
      }
      else {
         int pn = ((Number) column).intValue();
         Formula formula = new NthLargestFormula(pn);
         // default is not meaningful.
         //formula.setDefaultResult(isDefaultToZero("nthLargest"));

         // report scope function, (table, n, column)
         return ReportJavaScriptEngine.summarize(
            n, group, "nth largest", formula, cond, this);
      }
   }

   /**
    * Calculate the Nth smallest of a group on a summary column.
    * @param n nth.
    * @param column summary column.
    * @param group group column.
    * @param cond optional condition expression for conditional summary.
    */
   public Object nthSmallest(Object n, Object column, String group, String cond) {
      // because js does not support function overloading, the function
      // defined in the report scope would not be found if it's called from
      // this cope, we need to check for the object type and forward the call
      if(n instanceof Number && (column instanceof String || column == null)) {
         int pn = ((Number) n).intValue();
         Formula formula = new NthSmallestFormula(pn);
         // default is not meaningful.
         //formula.setDefaultResult(isDefaultToZero("nthSmallest"));

         return summarize((String) column, "nth smallest", formula);
      }
      else {
         int pn = ((Number) column).intValue();
         Formula formula = new NthSmallestFormula(pn);
         // default is not meaningful.
         //formula.setDefaultResult(isDefaultToZero("nthSmallest"));

         // report scope function, (table, n, column)
         return ReportJavaScriptEngine.summarize(n, group, "nth smallest", formula, cond, this);
      }
   }

   /**
    * Calculate the Nth frequent of a group on a summary column.
    * @param n nth.
    * @param column summary column.
    * @param group group column.
    * @param cond optional condition expression for conditional summary.
    */
   public Object nthMostFrequent(Object n, Object column, String group, String cond) {
      // because js does not support function overloading, the function
      // defined in the report scope would not be found if it's called from
      // this cope, we need to check for the object type and forward the call
      if(n instanceof Number && (column instanceof String || column == null)) {
         int pn = ((Number) n).intValue();
         Formula formula = new NthMostFrequentFormula(pn);
         // default is not meaningful.
         //formula.setDefaultResult(isDefaultToZero("nthMostFrequent"));

         return summarize((String) column, "nth most frequent", formula);
      }
      else {
         int pn = ((Number) column).intValue();
         Formula formula = new NthMostFrequentFormula(pn);
         // default is not meaningful.
         //formula.setDefaultResult(isDefaultToZero("nthMostFrequent"));

         // report scope function, (table, n, column)
         return ReportJavaScriptEngine.summarize(n, group, "nth most frequent", formula,
            cond, this);
      }
   }

   /**
    * Summarize a cell range.
    * @param range cell range in the format of "[row,col]:[row,col]"
    * @param func function name.
    * @param sum summary formula.
    */
   private Object summarize(String range, String func, Formula sum) {
      try {
         CellRange cells = CellRange.parse(range);
         Collection list = cells.getCells(table);
         Iterator iter = list.iterator();

         while(iter.hasNext()) {
            sum.addValue(iter.next());
         }

         return sum.getResult();
      }
      catch(Exception ex) {
         LOG.warn("Failed to summarize range " + range +
            " using formula " + sum, ex);
      }

      return null;
   }

   // check if should default (e.g. sum) to zero if the values are empty.
   private boolean isDefaultToZero() {
      return table.isFillBlankWithZero();
   }

   /**
    * Check if a string is a valid cell range notation.
    */
   private static boolean isCellRange(Object obj) {
      if(obj instanceof String) {
         String str = (String) obj;

         if(str.startsWith("[") && str.endsWith("]")) {
            try {
               new PositionalCellRange(str);
               return true;
            }
            catch(Exception ex) {
            }
         }
      }

      return false;
   }

   /**
    * Convert an object to a double value.
    */
   private static Double toDouble(Object obj) {
      if(obj == null) {
         return null;
      }

      return (obj instanceof Number) ? ((Number) obj).doubleValue() : 0.0;
   }

   /**
    * A reference to a calc table cell.
    */
   private class DelayedRef {
      public DelayedRef(int row, int col) {
         this.row = row;
         this.col = col;
      }

      public Object get() {
         return table.getObject(row, col);
      }

      private final int row;
      private final int col;
   }

   private final CalcTableLens table;
   private final TableRow field;

   private static final Logger LOG = LoggerFactory.getLogger(CalcTableScope.class);
}
