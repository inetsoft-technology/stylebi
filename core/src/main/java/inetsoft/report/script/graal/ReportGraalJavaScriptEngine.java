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
package inetsoft.report.script.graal;

import inetsoft.report.ReportSheet;
import inetsoft.report.TableLens;
import inetsoft.report.filter.*;
import inetsoft.report.internal.Util;
import inetsoft.report.script.PropertyDescriptor;
import inetsoft.report.script.formula.CellRange;
import inetsoft.report.script.formula.PositionalCellRange;
import inetsoft.report.script.formula.TableRangeProcessor;
import inetsoft.uql.VariableTable;
import inetsoft.uql.schema.*;
import inetsoft.uql.util.XUtil;
import inetsoft.util.CoreTool;
import inetsoft.util.ThreadContext;
import inetsoft.util.script.*;
import inetsoft.util.script.graal.GraalJavaScriptEngine;
import inetsoft.util.script.graal.ScriptScope;
import inetsoft.util.script.graal.ScriptValueConverter;
import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.proxy.ProxyExecutable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.*;
import java.lang.reflect.Array;
import java.security.Principal;
import java.util.*;
import java.util.stream.Collectors;

/**
 * The GraalJS-based report script engine. Replaces the Rhino
 * {@code ReportJavaScriptEngine}. Installs report-level global functions
 * (runQuery, addParameter, and the aggregate functions) and swallows script
 * execution errors at the report level (returns null on failure).
 */
public class ReportGraalJavaScriptEngine extends GraalJavaScriptEngine {
   /**
    * Set the report this engine evaluates scripts for.
    */
   public void setReport(ReportSheet report) {
      this.report = report;
   }

   /**
    * Get the report this engine evaluates scripts for.
    */
   public ReportSheet getReport() {
      return report;
   }

   /**
    * Install report globals into the engine scope.
    */
   @Override
   protected void initScope(Map<String, Object> vars) {
      super.initScope(vars);
      installReportGlobals();
   }

   /**
    * Install the report-level global functions.
    */
   private void installReportGlobals() {
      if(context == null) {
         return;
      }

      Value bindings = context.getBindings("js");

      if(report != null) {
         bindings.putMember("reportsheet", report);
         bindings.putMember("stylesheet", report); // deprecated
      }

      // report global functions
      bindings.putMember("runQuery", (ProxyExecutable) args ->
         runQueryGlobal(args.length > 0 ? args[0].asString() : null,
                        args.length > 1 ? ScriptValueConverter.toHost(args[1]) : null));
      bindings.putMember("addParameter", (ProxyExecutable) args -> {
         addParameterGlobal(arg(args, 0), ScriptValueConverter.toHost(at(args, 1)),
                            argStr(args, 2), argStr(args, 3), argBool(args, 4));
         return null;
      });

      // aggregate functions: (lens, column, cond)
      bindings.putMember("none", agg("none", NoneFormula::new));
      bindings.putMember("sum", agg("sum", SumFormula::new));
      bindings.putMember("average", agg("average", AverageFormula::new));
      bindings.putMember("count", agg("count", CountFormula::new));
      bindings.putMember("countDistinct", agg("distinct count", DistinctCountFormula::new));
      bindings.putMember("max", agg("max", MaxFormula::new));
      bindings.putMember("min", agg("min", MinFormula::new));
      bindings.putMember("product", agg("product", ProductFormula::new));
      bindings.putMember("concat", agg("concat", ConcatFormula::new));
      bindings.putMember("standardDeviation",
         agg("standard deviation", StandardDeviationFormula::new));
      bindings.putMember("variance", agg("variance", VarianceFormula::new));
      bindings.putMember("populationVariance",
         agg("population variance", PopulationVarianceFormula::new));
      bindings.putMember("populationStandardDeviation",
         agg("population standard deviation", PopulationStandardDeviationFormula::new));
      bindings.putMember("median", agg("median", MedianFormula::new));
      bindings.putMember("mode", agg("mode", ModeFormula::new));

      // two-column aggregate functions: (lens, column, column2, cond)
      bindings.putMember("correlation", agg2("correlation", CorrelationFormula::new));
      bindings.putMember("covariance", agg2("covariance", CovarianceFormula::new));
      bindings.putMember("weightedAverage", agg2("weightedAverage", WeightedAverageFormula::new));
      bindings.putMember("first", agg2("first", FirstFormula::new));
      bindings.putMember("last", agg2("last", LastFormula::new));

      // nth aggregate functions: (lens, n, column, cond)
      bindings.putMember("pthPercentile", aggN("pth percentile", PthPercentileFormula::new));
      bindings.putMember("nthLargest", aggN("nth largest", NthLargestFormula::new));
      bindings.putMember("nthSmallest", aggN("nth smallest", NthSmallestFormula::new));
      bindings.putMember("nthMostFrequent", aggN("nth most frequent", NthMostFrequentFormula::new));
   }

   /**
    * Build a (lens, column, cond) aggregate function global.
    */
   private ProxyExecutable agg(String func, java.util.function.Supplier<Formula> factory) {
      return args -> {
         Object lens = ScriptValueConverter.toHost(at(args, 0));
         String column = argStr(args, 1);
         String cond = argStr(args, 2);
         return summarize(lens, column, func, factory.get(), cond, null);
      };
   }

   /**
    * Build a (lens, column, column2, cond) two-column aggregate function global.
    */
   private ProxyExecutable agg2(String func,
                                java.util.function.IntFunction<Formula> factory)
   {
      return args -> {
         Object lens = PropertyDescriptor.convert(ScriptValueConverter.toHost(at(args, 0)),
                                                  TableLens.class);
         String column = argStr(args, 1);
         String column2 = argStr(args, 2);
         String cond = argStr(args, 3);

         if(!(lens instanceof TableLens)) {
            return 0.0;
         }

         int idx = Util.findColumn((TableLens) lens, column2);

         if(idx < 0) {
            return 0.0;
         }

         return summarize(lens, column, func, factory.apply(idx), cond, null);
      };
   }

   /**
    * Build a (lens, n, column, cond) nth aggregate function global.
    */
   private ProxyExecutable aggN(String func,
                                java.util.function.IntFunction<Formula> factory)
   {
      return args -> {
         Object lens = ScriptValueConverter.toHost(at(args, 0));
         int n = argInt(args, 1);
         String column = argStr(args, 2);
         String cond = argStr(args, 3);
         return summarize(lens, column, func, factory.apply(n), cond, null);
      };
   }

   /**
    * Execute a query for the runQuery() global.
    */
   private Object runQueryGlobal(String qname, Object val) {
      if(FormulaContext.isRestricted()) {
         throw new SecurityException("runQuery can't be accessed from web");
      }

      Principal principal = ThreadContext.getContextPrincipal();
      VariableTable vars = report == null ? null : report.getVariableTable();
      return XUtil.runQuery(qname, val, principal, vars, false, true);
   }

   /**
    * Define or replace a report parameter for the addParameter() global.
    */
   private void addParameterGlobal(String name, Object value, String type,
                                   String alias, boolean hidden)
   {
      if(report == null) {
         return;
      }

      UserVariable var = new UserVariable(name);
      value = JavaScriptEngine.unwrap(value);

      if(type != null) {
         value = JSObject.convert(value, CoreTool.getDataClass(type));
      }

      try {
         if(type == null) {
            type = XSchema.STRING;
         }

         XTypeNode tnode = XSchema.createPrimitiveType(type);

         if(tnode == null) {
            LOG.error("Invalid type code: " + type);
            return;
         }

         XValueNode vnode = (XValueNode) tnode.newInstance();

         vnode.setValue(value);
         var.setValueNode(vnode);
         var.setAlias(alias);
         var.setTypeNode(tnode);
         var.setPrompt(!hidden);

         report.addParameter(var);
      }
      catch(Exception ex) {
         LOG.error("Failed to add parameter " + name +
            "[" + type + "] = " + value, ex);
      }
   }

   /**
    * Execute a script, swallowing errors at the report level.
    */
   @Override
   public Object exec(Object script, Object scope, Object rscope) throws Exception {
      try {
         return super.exec(script, scope, rscope);
      }
      catch(Exception ex) {
         LOG.warn("Script execution failed", ex);
         return null;
      }
   }

   // ----- argument helpers for ProxyExecutable globals -----

   private static Value at(Value[] args, int i) {
      return (args != null && i < args.length) ? args[i] : null;
   }

   private static String arg(Value[] args, int i) {
      Value v = at(args, i);
      return (v == null || v.isNull()) ? null : v.asString();
   }

   private static String argStr(Value[] args, int i) {
      Value v = at(args, i);
      return (v == null || v.isNull()) ? null : v.toString();
   }

   private static int argInt(Value[] args, int i) {
      Value v = at(args, i);
      return (v == null || v.isNull() || !v.isNumber()) ? 0 : v.asInt();
   }

   private static boolean argBool(Value[] args, int i) {
      Value v = at(args, i);
      return v != null && v.isBoolean() && v.asBoolean();
   }

   /**
    * Summarize a column.
    * @param lens table lens.
    * @param column summary column header or cell range.
    * @param func function name.
    * @param sum summary formula.
    */
   public static Object summarize(Object lens, String column, String func,
                                  Formula sum, String cond, ScriptScope scope) {
      TableLens table = (TableLens) PropertyDescriptor.convert(lens, TableLens.class);

      // if the first parameter is an array, it should be a call to the CALC
      if(table == null) {
         // match formula on table (also can be controlled by fillBlankWithZero table option).
         if(sum != null && !sum.isDefaultResult()) {
            if(lens == null || lens.getClass().isArray() && (Array.getLength(lens) == 0 ||
               Arrays.stream(((Object[]) lens)).filter(len -> len != null).collect(Collectors.toList()).size() == 0))
            {
               // sum/count's default value should be 0 for scripting. (62630)
               if(sum instanceof SumFormula || sum instanceof CountFormula ||
                  sum instanceof DistinctCountFormula)
               {
                  // drop through to null
               }
               // if null is returned and used in calculation (e.g. 100 * average(...)),
               // it's converted to 0 in js and resulting in 0 instead of null. returning
               // NaN ensures it's not treated as 0 in subsequent calculations. (53008)
               else if(sum.getResultType() != null && Number.class.isAssignableFrom(sum.getResultType()))
               {
                  return Double.NaN;
               }

               return null;
            }
         }

         if("none".equals(func)) {
            return CalcMath.none(lens);
         }
         else if("sum".equals(func)) {
            return CalcMath.sum(lens);
         }
         else if("average".equals(func)) {
            double avg = CalcStat.average(lens);
            return Double.isNaN(avg) && sum != null && sum.isDefaultResult() ? 0 : avg;
         }
         else if("count".equals(func)) {
            return (double) CalcStat.count(lens);
         }
         else if("distinct count".equals(func)) {
            return (double) CalcStat.countDistinct(lens);
         }
         else if("max".equals(func)) {
            if(isDateArray(lens)) {
               return CalcDateTime.maxDate(lens);
            }
            else {
               return CalcStat.max(lens);
            }
         }
         else if("min".equals(func)) {
            if(isDateArray(lens)) {
               return CalcDateTime.minDate(lens);
            }
            else {
               return CalcStat.min(lens);
            }
         }
         else if("product".equals(func)) {
            // return 0 (same as ProductFormula) instead of 1 (from MathCalc.product()).
            if(sum != null && sum.isDefaultResult() &&
               (lens == null || lens.getClass().isArray() && (Array.getLength(lens) == 0 ||
                Arrays.stream((Object[]) lens).allMatch(Objects::isNull))))
            {
               return 0;
            }

            return CalcMath.product(lens);
         }
         else if("median".equals(func)) {
            return CalcStat.median(lens);
         }
         else if("mode".equals(func)) {
            return CalcStat.mode(lens);
         }
         else if(sum != null && isFuncSupported(func)) {
            Object[] arr = JavaScriptEngine.split(lens);

            for(int i = 0; i < arr.length; i++) {
               sum.addValue(arr[i]);
            }

            return sum.getResult();
         }

         LOG.warn(
            "Aggregate function requires a TableLens as the first argument: " +
            lens);
         return null;
      }

      // empty table
      if(!table.moreRows(1)) {
         return null;
      }

      int sumC = Util.findColumn(table, column); // summary column index
      CellRange range = null;

      if(sumC < 0) {
         try {
            range = CellRange.parse(column);
         }
         catch(Exception ex) {
            LOG.debug("Failed to parse cell range: " + column, ex);
         }
      }
      else {
         table.moreRows(TableLens.EOT);
         range = new PositionalCellRange(table.getHeaderRowCount(), sumC,
                                    table.getRowCount() - 1, sumC);
      }

      // make sure sum column is in the table
      if(range == null) {
         LOG.warn(
            "Summary column not found, ignored: " + func + "('" +
            column + "')");
         return null;
      }

      sum.reset();

      if(range instanceof PositionalCellRange) {
         Insets reg = null;

         try {
            reg = ((PositionalCellRange) range).getCellRegion(table);
         }
         catch(Exception ex) {
            LOG.error("Failed to get cell region from range: " + range, ex);
            return null;
         }

         Vector cells = new Vector();
         TableRangeProcessor proc = new TableRangeProcessor(table, scope);

         proc.selectCells(cells, reg.left, reg.top, reg.bottom + 1,
                           1, null, cond, true);

         Iterator iter = cells.iterator();

         while(iter.hasNext()) {
            Point loc = (Point) iter.next();
            int i = loc.y;

            for(int j = reg.left; j <= reg.right; j++) {
               Object val = table.getObject(i, j);

               if(sum instanceof Formula2) {
                  int[] cols = ((Formula2) sum).getSecondaryColumns();
                  Object[] data = new Object[cols.length + 1];
                  data[0] = val;

                  for(int k = 0; k < cols.length; k++) {
                     data[k + 1] = table.getObject(i, cols[k]);
                  }

                  ((Formula2) sum).addValue(data);
               }
               else {
                  sum.addValue(val);
               }
            }
         }
      }
      else {
         try {
            Iterator iter = range.getCells(table).iterator();

            while(iter.hasNext()) {
               sum.addValue(iter.next());
            }
         }
         catch(Exception ex) {
            LOG.error("Failed to calculate sum for range: " + range, ex);
            return null;
         }
      }

      return sum.getResult();
   }

   /**
    * Check if a function is supported summarize on non-table lens.
    */
   private static boolean isFuncSupported(String func) {
      return func != null && !"correlation".equals(func) &&
         !"covariance".equals(func) && !"weightedAverage".equals(func) &&
         !"first".equals(func) && !"last".equals(func);
   }

   /**
    * To check if object array is a Date array
    */
   private static boolean isDateArray(Object dates) {
      Object[] objs = JavaScriptEngine.split(dates);

      if(objs == null || objs.length == 0) {
         return false;
      }

      for(int i = 0; i < objs.length; i++) {
         if(objs[i] != null && !Date.class.isAssignableFrom(objs[i].getClass()))
         {
            return false;
         }
      }

      return true;
   }

   private ReportSheet report;

   private static final Logger LOG =
      LoggerFactory.getLogger(ReportGraalJavaScriptEngine.class);
}
