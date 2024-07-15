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
package inetsoft.report.script;

import inetsoft.report.*;
import inetsoft.report.filter.*;
import inetsoft.report.internal.Util;
import inetsoft.report.script.formula.*;
import inetsoft.uql.VariableTable;
import inetsoft.uql.schema.*;
import inetsoft.uql.script.VariableScriptable;
import inetsoft.uql.util.XUtil;
import inetsoft.util.*;
import inetsoft.util.script.*;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.*;
import java.lang.reflect.Array;
import java.security.Principal;
import java.util.*;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

/**
 * The script engine represents the script runtime environment. It is
 * created by each report object to process the scripts in a report.
 * All constants in StyleConstants are defined in the runtime
 * environment in the StyleReport scope (e.g. StyleReport.THIN_LINE).
 *
 * @version 6.1, 6/20/2004
 * @author InetSoft Technology Corp
 */
public class ReportJavaScriptEngine extends JavaScriptEngine {
   /**
    * Id for the onInit scope. It is not reset by a report.
    */
   public static final String INIT_SCOPE = ScriptEnv.INIT_SCOPE;

   /**
    * Set the script engine containing this engine. All objects in the
    * container engine are accessible in this engine.
    */
   @Override
   public void setParent(Scriptable parent) {
      // parent scope is a rscope of the parent, link them so report[]
      // would contain elements from both report and bean
      // rscope.setPrototype(parent.getParentScope());
      // @by larryl, the above commented code allows report[] to be used to
      // access elements in thep arent report, but it causes a couple of
      // problems (7.0)
      // 1. If a variable is defined in the onLoad of the parent report, the
      // variable will be found on the rscope's prototype. It can be accessed
      // correct. But if it's modified, it creates a local copy in the current
      // rscope and the parent scope's value is untouched. This is not correct
      // according to the scope rules.
      // 2. Using one report[] to access all elements does not allow parent
      // elements to be accessed if there are elements with the same name in
      // the bean.
      // By explicitly adding a parentReport, the elements in bean and parent
      // reports are clearly separated and can be referenced with consistent
      // logic. It also avoids the scoping problem.
      rscope.put("parentReport", rscope, parent.getParentScope());

      // chain main report chain
      topscope.setParentScope(parent);
   }

   /**
    * Sets the parent scope of <tt>child</tt> to the top-level scope.
    *
    * @param child the scriptable to be modified.
    */
   @Override
   public void addTopLevelParentScope(Scriptable child) {
      child.setParentScope(rscope);
   }

   /**
    * Initialize the script runtime environment for a report.
    */
   public void init(ReportSheet report, Map vars) throws Exception {
      Context cx = Context.enter();
      Scriptable globalscope = initScope();

      // scopes are organized as:
      //   report global scope  -- prototype: --> global scope --> CALC
      //         |                                    \
      //    report scope                             CALC
      //
      // CALC functions can be accessed as CALC.func(), or func() if it's
      // not hidden by other symbols in the lower scopes

      // create a scope for the report so the global vars (vars used without
      // being declared) are not mixed
      ScriptableObject reportglobal = new ScriptableObject() {
         @Override
         public String getClassName() {
            return "ReportGlobal";
         }
      };

      reportglobal.setPrototype(globalscope);

      if(topscope == null) {
         topscope = createEngineScope(report, this);
      }

      // this may be changed by bean, reset to top global scope
      topscope.setParentScope(reportglobal);

      rscope = cx.newObject(topscope);
      rscope.setParentScope(topscope);
      // make sure functions can access report functions (e.g. runQuery)
      LibScriptable lib = new LibScriptable(rscope);
      // this is needed so runQuery() can be accessed in lib functions
      lib.setPrototype(null);
      rscope.setPrototype(lib);

      // initialize script variables
      Iterator names = vars.keySet().iterator();

      while(names.hasNext()) {
         String name = (String) names.next();
         put(name, vars.get(name));
      }

      topscope.put("report", topscope, rscope);

      Context.exit();
   }

   /**
    * Create an engine scope for a report.
    */
   protected EngineScope createEngineScope(ReportSheet report,
                                           JavaScriptEngine engine) {
      return new EngineScope2(report);
   }

   /**
    * Clear this engine.
    */
   public static void clear() {
      ConfigurationContext.getContext().remove(GLOBAL_SCOPE_KEY);
   }

   /**
    * Get a global scope for the current context.
    */
   private static Scriptable getGlobalScope() {
      Scriptable s = null;

      GLOBAL_SCOPE_LOCK.lock();

      try {
         s = ConfigurationContext.getContext().get(GLOBAL_SCOPE_KEY);
      }
      finally {
         GLOBAL_SCOPE_LOCK.unlock();
      }

      return s;
   }

   /**
    * Set the global scope for the current context.
    */
   private void setGlobalScope(Scriptable scope) {
      GLOBAL_SCOPE_LOCK.lock();

      try {
         ConfigurationContext.getContext().put(GLOBAL_SCOPE_KEY, scope);
      }
      finally {
         GLOBAL_SCOPE_LOCK.unlock();
      }
   }

   /**
    * Initialize the top scope.
    */
   @Override
   public Scriptable initScope() throws Exception {
      // register event to listener for the lib change
      Scriptable globalscope = getGlobalScope();

      if(globalscope == null) {
         boolean first = false;
         GLOBAL_SCOPE_LOCK.lock();

         try {
            globalscope = getGlobalScope();

            // @by billh, fix customer bug bug1347906798630
            // release lock<globalscopes> in time, so that there is no deadlock
            if(first = (globalscope == null)) {
               globalscope = super.initScope();
               setGlobalScope(globalscope);
            }
         }
         finally {
            GLOBAL_SCOPE_LOCK.unlock();
         }

         if(first) {
            // make sure functions are accessible from global scope too
            LibScriptable lib = new LibScriptable(globalscope);
            lib.setPrototype(globalscope.getPrototype());
            globalscope.setPrototype(lib);

            Context cx = Context.getCurrentContext();
            Scriptable cons = cx.newObject(globalscope);
            cons.setParentScope(globalscope);

            Class[] reportcls = {
               StyleConstants.class, ReportSheet.class, TableLens.class};

            // cons contains all the constant definitions in StyleConstants
            // and ReportSheet
            addFields(cons, reportcls);

            // define Style Report constants
            globalscope.put("StyleReport", globalscope, cons);

            // add UQL constants
            cons = cx.newObject(globalscope);
            cons.setParentScope(globalscope);
            addFields(cons, XSchema.class);
            globalscope.put("XType", globalscope, cons);

            // StyleConstant holds all constants
            // All class constants should be added to the StyleConstant
            cons = (Scriptable) globalscope.get("StyleConstant", globalscope);

            addFields(cons, reportcls);
            globalscope.put("StyleConstant", globalscope, cons);

            setGlobalScope(globalscope);
         }
      }

      return globalscope;
   }

   @Override
   protected void finalize() throws Throwable {
      dispose();
      super.finalize();
   }

   public void dispose() {
   }

   /**
    * Find a Javascript object in a scope. If the id does not have a
    * scriptable object, this function returns null;
    * @id element id.
    * @param scope Javascript scope.
    * @return Javascript object for an element or null.
    */
   @Override
   public Scriptable getScriptable(Object id, Scriptable scope) {
      Context.enter();
      scope = (scope == null) ? rscope : scope;

      try {
         if(id == null) {
            return scope;
         }

         Object val = findObject(id, scope, new Vector());

         if(val == Undefined.instance || val == Scriptable.NOT_FOUND) {
            return null;
         }

         if(val instanceof Scriptable) {
            return (Scriptable) val;
         }
         else if(val != null) {
            return new NativeJavaObject(rscope, val, val.getClass());
         }

         return null;
      }
      finally {
         Context.exit();
      }
   }

   /**
    * Execute a script.
    * @param script script object.
    * @param scope scope this script should execute in. Using report
    * scope if the value is null. If the value is INIT_SCOPE, the
    * script is executed in report scope with topscope as the variable
    * holding scope.
    * @param rscope0 report scope.
    */
   @Override
   public synchronized Object exec(Script script, Object scope, Scriptable rscope0)
         throws Exception
   {
      boolean init = scope != null && scope.equals(INIT_SCOPE);
      Scriptable topscopeParent = null;
      Scriptable rscopeParent = null;

      // if init scope, run in the initscope
      // this is done to force all variables declared in onInit to be placed
      // in topscope instead of rscope
      if(init) {
         scope = topscope;
         topscopeParent = topscope.getParentScope();
         rscopeParent = rscope.getParentScope();

         topscope.setParentScope(rscope);
         rscope.setParentScope(topscopeParent);
         addToPrototype(rscope, topscope);
      }

      try {
         return super.exec(script, (scope == null) ? rscope : scope,
                           rscope0 != null ? rscope0 : rscope);
      }
      catch(ScriptException ex) {
         LOG.warn(ex.getMessage());
         return null;
      }
      catch(Exception ex) {
         return null;
      }
      finally {
         if(init) {
            topscope.setParentScope(topscopeParent);
            rscope.setParentScope(rscopeParent);
            removeFromPrototype(rscope, topscope);
         }
      }
   }

   /**
    * Define a variable in the report scope.
    * @param name variable name. The name can be a simple name, in which case
    * the object is added to the report scope. If the name is qualified with
    * a scope name as "scope::name", the name is added to the named scope.
    * @param obj variable value.
    */
   @Override
   public void put(String name, Object obj) {
      int cc = name.indexOf("::");
      Scriptable scope = null;

      if(cc > 0) {
         String sname = name.substring(0, cc);

         name = name.substring(cc + 2);
         scope = getScriptable(sname, null);
      }

      if(scope == null) {
         scope = rscope;
      }

      if(obj instanceof VariableTable) {
         obj = new VariableScriptable((VariableTable) obj);
      }

      scope.put(name, scope, obj);

      if(obj instanceof Scriptable) {
         ((Scriptable) obj).setParentScope(scope);
      }
   }

   /**
    * Remove a variable from the scripting environment.
    * @param name variable name.
    */
   @Override
   public void remove(String name) {
      rscope.delete(name);
   }

   /**
    * Recursively find all IDS.
    */
   private void findIds(Scriptable obj, Set ids, Set checked, boolean parent) {
      // if object already processed, ignore
      if(obj == null || checked.contains(obj)) {
         return;
      }

      checked.add(obj);

      Object[] arr = obj.getIds();

      for(int i = 0; i < arr.length; i++) {
         // add () to function
         if(arr[i] instanceof String) {
            Object child = obj.get((String) arr[i], obj);

            if(child instanceof Scriptable) {
               Scriptable prop = (Scriptable) child;

               if(prop instanceof Function) {
                  arr[i] = toFunction(arr[i]);
               }
               else if(prop instanceof ArrayObject) {
                  arr[i] = toArray(arr[i]);
               }
            }

            ids.add(arr[i]);
         }
      }

      findIds(obj.getPrototype(), ids, checked, parent);

      if(parent) {
         findIds(obj.getParentScope(), ids, checked, parent);
      }
   }

   /**
    * Find the scope of the specified object.
    */
   public Class getType(Object id, Scriptable scope) {
      if(scope instanceof ArrayObject) {
         return ((ArrayObject) scope).getType();
      }

      if(scope instanceof AxisScriptable) {
         return ((AxisScriptable) scope).getType((String) id, scope);
      }

      return null;
   }

   /**
    * Summarize a column.
    * @param lens table lens.
    * @param column summary column header or cell range.
    * @param func function name.
    * @param sum summary formula.
    */
   public static Object summarize(Object lens, String column, String func,
                                  Formula sum, String cond, Scriptable scope) {
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
    * Convert an object to a double value.
    */
   private static double toDouble(Object obj) {
      return (obj instanceof Number) ? ((Number) obj).doubleValue() : 0.0;
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

   /**
    * Scope to hold engine (per report) properties.
    */
   private class EngineScope2 extends EngineScope {
      public EngineScope2(ReportSheet report) {
         this.report = report;

         this.put("stylesheet", this, report); // deprecated
         this.put("reportsheet", this, report);

         try {
            FunctionObject func;

            // report global functions
            func = new FunctionObject2(this, EngineScope2.class, "runQuery",
                String.class, Object.class);
            this.put("runQuery", this, func);

            func = new FunctionObject2(this, EngineScope2.class, "addParameter",
               String.class, Object.class, String.class, String.class, boolean.class);
            this.put("addParameter", this, func);
         }
         catch(Exception ex) {
            LOG.error("Failed to register engine scope functions", ex);
         }

         // initialize aggregate functions
         try {
            Class[] params = {Object.class, String.class, String.class};
            Class[] nparams = {Object.class, int.class, String.class,
                               String.class};
            Class[] cparams = {Object.class, String.class, String.class,
                               String.class};
            Function func;

            func = new FunctionObject2(this, getClass(), "none", params);
            this.put("none", this, func);

            func = new FunctionObject2(this, getClass(), "sum", params);
            this.put("sum", this, func);

            func = new FunctionObject2(this, getClass(), "average", params);
            this.put("average", this, func);

            func = new FunctionObject2(this, getClass(), "count", params);
            this.put("count", this, func);

            func = new FunctionObject2(this, getClass(), "countDistinct", params);
            this.put("countDistinct", this, func);

            func = new FunctionObject2(this, getClass(), "max", params);
            this.put("max", this, func);

            func = new FunctionObject2(this, getClass(), "min", params);
            this.put("min", this, func);

            func = new FunctionObject2(this, getClass(), "product", params);
            this.put("product", this, func);

            func = new FunctionObject2(this, getClass(), "concat", params);
            this.put("concat", this, func);

            func = new FunctionObject2(this, getClass(), "standardDeviation", params);
            this.put("standardDeviation", this, func);

            func = new FunctionObject2(this, getClass(), "variance", params);
            this.put("variance", this, func);

            func = new FunctionObject2(this, getClass(), "populationVariance", params);
            this.put("populationVariance", this, func);

            func = new FunctionObject2(this, getClass(), "populationStandardDeviation", params);
            this.put("populationStandardDeviation", this, func);

            func = new FunctionObject2(this, getClass(), "median", params);
            this.put("median", this, func);

            func = new FunctionObject2(this, getClass(), "mode", params);
            this.put("mode", this, func);

            func = new FunctionObject2(this, getClass(), "correlation", cparams);
            this.put("correlation", this, func);

            func = new FunctionObject2(this, getClass(), "covariance", cparams);
            this.put("covariance", this, func);

            func = new FunctionObject2(this, getClass(), "weightedAverage", cparams);
            this.put("weightedAverage", this, func);

            func = new FunctionObject2(this, getClass(), "pthPercentile", nparams);
            this.put("pthPercentile", this, func);

            func = new FunctionObject2(this, getClass(), "nthLargest", nparams);
            this.put("nthLargest", this, func);

            func = new FunctionObject2(this, getClass(), "nthSmallest", nparams);
            this.put("nthSmallest", this, func);

            func = new FunctionObject2(this, getClass(), "nthMostFrequent", nparams);
            this.put("nthMostFrequent", this, func);

            func = new FunctionObject2(this, getClass(), "first", cparams);
            this.put("first", this, func);

            func = new FunctionObject2(this, getClass(), "last", cparams);
            this.put("last", this, func);
         }
         catch(Exception ex) {
            LOG.error("Failed to register aggregate functions", ex);
         }
      }

      /**
       * Execute a query. The query result is returned as a table (two
       * dimensional array). The query may be a query in the report local
       * query, query registry, or a worksheet.
       * <br>
       * A worksheet name must contain the scope, user (optional), and
       * worksheet path in the format: ws[: global | user_name]:worksheet_path.
       * For example, to reference a worksheet in the global scope under folder
       * f1, the name should be ws:global:f1/worksheet1. If no global or user
       * name is specified, the worksheet is in the report scope.
       *
       * @param qname query name. The query must be defined in the
       * query registry or local query. Or it can be an encoded worksheet name.
       * @param val query parameters as an array of pairs. Each pair is an
       * array of name and value. If parameter is not passed in, the report
       * parameters are used.
       */
      public Object runQuery(String qname, Object val) {
         if(FormulaContext.isRestricted()) {
            throw new SecurityException("runQuery can't be accessed from web");
         }

         Principal principal = ThreadContext.getContextPrincipal();
         VariableTable vars = report.getVariableTable();
         return XUtil.runQuery(qname, val, principal, vars, false, true);
      }

      /**
       * Define a new report parameter or replace the current definition
       * if the parameter already exists.
       */
      public void addParameter(String name, Object value, String type,
                               String alias, boolean hidden)
      {
         UserVariable var = new UserVariable(name);

         value = unwrap(value);

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
            LOG.error("Failed to add parameter + " + name +
               "[" + type + "] = " + value, ex);
         }
      }

      /**
       * Calculate the none of a group on the summary column.
       * @param lens table lens.
       * @param column summary column.
       * @param cond optional condition expression for conditional summary.
       */
      public double none(Object lens, String column, String cond) {
         return toDouble(summarize(lens, column, "none", new NoneFormula(),
                                   cond));
      }

      /**
       * Calculate the sum of a group on the summary column.
       * @param lens table lens.
       * @param column summary column.
       * @param cond optional condition expression for conditional summary.
       */
      public double sum(Object lens, String column, String cond) {
         return toDouble(summarize(lens, column, "sum", new SumFormula(),
                                   cond));
      }

      /**
       * Calculate the average of a group on the summary column.
       * @param lens table lens.
       * @param column summary column.
       * @param cond optional condition expression for conditional summary.
       */
      public double average(Object lens, String column, String cond) {
         return toDouble(summarize(lens, column, "average",
                                   new AverageFormula(), cond));
      }

      /**
       * Calculate the total count of a group on the summary column.
       * @param lens table lens.
       * @param column summary column.
       * @param cond optional condition expression for conditional summary.
       */
      public double count(Object lens, String column, String cond) {
         return toDouble(summarize(lens, column, "count", new CountFormula(),
                                   cond));
      }

      /**
       * Calculate the distinct count of a group on the summary column.
       * @param lens table lens.
       * @param column summary column.
       * @param cond optional condition expression for conditional summary.
       */
      public double countDistinct(Object lens, String column, String cond) {
         return toDouble(summarize(lens, column, "distinct count",
                                   new DistinctCountFormula(), cond));
      }

      /**
       * Calculate the max of a group on the summary column.
       * @param lens table lens.
       * @param column summary column.
       * @param cond optional condition expression for conditional summary.
       */
      public Object max(Object lens, String column, String cond) {
         return summarize(lens, column, "max", new MaxFormula(), cond);
      }

      /**
       * Calculate the min of a group on the summary column.
       * @param lens table lens.
       * @param column summary column.
       * @param cond optional condition expression for conditional summary.
       */
      public Object min(Object lens, String column, String cond) {
         return summarize(lens, column, "min", new MinFormula(), cond);
      }

      /**
       * Calculate the product of a group on the summary column.
       * @param lens table lens.
       * @param column summary column.
       * @param cond optional condition expression for conditional summary.
       */
      public double product(Object lens, String column, String cond) {
         return toDouble(summarize(lens, column, "product",
                                   new ProductFormula(), cond));
      }

      /**
       * Calculate the concatenation of a group on the summary column.
       * @param lens table lens.
       * @param column summary column.
       * @param cond optional condition expression for conditional summary.
       */
      public Object concat(Object lens, String column, String cond) {
         return summarize(lens, column, "concat", new ConcatFormula(), cond);
      }

      /**
       * Calculate the standard deviation of a group on the summary column.
       * @param lens table lens.
       * @param column summary column.
       * @param cond optional condition expression for conditional summary.
       */
      public double standardDeviation(Object lens, String column, String cond) {
         return toDouble(summarize(lens, column, "standard deviation",
                                   new StandardDeviationFormula(), cond));
      }

      /**
       * Calculate the variance of a group on a summary column.
       * @param lens table lens.
       * @param column summary column.
       * @param cond optional condition expression for conditional summary.
       */
      public double variance(Object lens, String column, String cond) {
         return toDouble(summarize(lens, column, "variance",
                                   new VarianceFormula(), cond));
      }

      /**
       * Calculate the population variance of a group on a summary column.
       * @param lens table lens.
       * @param column summary column.
       * @param cond optional condition expression for conditional summary.
       */
      public double populationVariance(Object lens, String column,
                                       String cond) {
         return toDouble(summarize(lens, column, "population variance",
                                   new PopulationVarianceFormula(), cond));
      }

      /**
       * Calculate the population standard deviation of a group on a
       * summary column.
       * @param lens table lens.
       * @param column summary column.
       * @param cond optional condition expression for conditional summary.
       */
      public double populationStandardDeviation(Object lens, String column,
                                                String cond) {
         return toDouble(summarize(lens, column,
                                   "population standard deviation",
                                   new PopulationStandardDeviationFormula(),
                                   cond));
      }

      /**
       * Calculate the median of a group on a summary column.
       * @param lens table lens.
       * @param column summary column.
       * @param cond optional condition expression for conditional summary.
       */
      public double median(Object lens, String column, String cond) {
         return toDouble(summarize(lens, column, "median", new MedianFormula(),
                                   cond));
      }

      /**
       * Calculate the mode of a group on a summary column.
       * @param lens table lens.
       * @param column summary column.
       * @param cond optional condition expression for conditional summary.
       */
      public Object mode(Object lens, String column, String cond) {
         return summarize(lens, column, "mode", new ModeFormula(), cond);
      }

      /**
       * Calculate the correlation of a group on a summary column.
       * @param lens table lens.
       * @param column summary column.
       * @param column2 second summary column.
       * @param cond optional condition expression for conditional summary.
       */
      public double correlation(Object lens, String column, String column2,
                                String cond) {
         lens = PropertyDescriptor.convert(lens, TableLens.class);
         int idx = Util.findColumn((TableLens) lens, column2);

         if(idx < 0) {
            return 0;
         }

         return toDouble(summarize(lens, column, "correlation",
                                   new CorrelationFormula(idx), cond));
      }

      /**
       * Calculate the covariance of a group on a summary column.
       * @param lens table lens.
       * @param column summary column.
       * @param column2 second summary column.
       * @param cond optional condition expression for conditional summary.
       */
      public double covariance(Object lens, String column, String column2,
                               String cond) {
         lens = PropertyDescriptor.convert(lens, TableLens.class);
         int idx = Util.findColumn((TableLens) lens, column2);

         if(idx < 0) {
            return 0;
         }

         return toDouble(summarize(lens, column, "covariance",
                                   new CovarianceFormula(idx), cond));
      }

      /**
       * Calculate the weighted average of a group on a summary column.
       * @param lens table lens.
       * @param column summary column.
       * @param column2 second summary column.
       * @param cond optional condition expression for conditional summary.
       */
      public double weightedAverage(Object lens, String column,
                                    String column2, String cond) {
         lens = PropertyDescriptor.convert(lens, TableLens.class);
         int idx = Util.findColumn((TableLens) lens, column2);

         if(idx < 0) {
            return 0;
         }

         return toDouble(summarize(lens, column, "weightedAverage",
                                   new WeightedAverageFormula(idx), cond));
      }

      public double first(Object lens, String column,
                          String column2, String cond) {
         lens = PropertyDescriptor.convert(lens, TableLens.class);
         int idx = Util.findColumn((TableLens) lens, column2);

         if(idx < 0) {
            return 0;
         }

         return toDouble(summarize(lens, column, "first",
                                   new FirstFormula(idx), cond));
      }

      public double last(Object lens, String column,
                         String column2, String cond) {
         lens = PropertyDescriptor.convert(lens, TableLens.class);
         int idx = Util.findColumn((TableLens) lens, column2);

         if(idx < 0) {
            return 0;
         }

         return toDouble(summarize(lens, column, "last",
                                   new LastFormula(idx), cond));
      }

      /**
       * Calculate the Pth percentile of a group on a summary column.
       * @param lens table lens.
       * @param p percentile
       * @param column summary column.
       * @param cond optional condition expression for conditional summary.
       */
      public Object pthPercentile(Object lens, int p, String column,
                                  String cond) {
         return summarize(lens, column, "pth percentile",
                          new PthPercentileFormula(p), cond);
      }

      /**
       * Calculate the Nth largest of a group on a summary column.
       * @param lens table lens.
       * @param n nth.
       * @param column summary column.
       * @param cond optional condition expression for conditional summary.
       */
      public Object nthLargest(Object lens, int n, String column, String cond) {
         return summarize(lens, column, "nth largest", new NthLargestFormula(n),
                          cond);
      }

      /**
       * Calculate the Nth smallest of a group on a summary column.
       * @param lens table lens.
       * @param n nth.
       * @param column summary column.
       * @param cond optional condition expression for conditional summary.
       */
      public Object nthSmallest(Object lens, int n, String column,
                                String cond) {
         return summarize(lens, column, "nth smallest",
                          new NthSmallestFormula(n), cond);
      }

      /**
       * Calculate the Nth frequent of a group on a summary column.
       * @param lens table lens.
       * @param n nth.
       * @param column summary column.
       * @param cond optional condition expression for conditional summary.
       */
      public Object nthMostFrequent(Object lens, int n, String column,
                                    String cond) {
         return summarize(lens, column, "nth most frequent",
                          new NthMostFrequentFormula(n), cond);
      }

      /**
       * Summarize a column.
       * @param lens table lens.
       * @param column summary column header.
       * @param func function name.
       * @param sum summary formula.
       */
      protected Object summarize(Object lens, String column, String func,
                                 Formula sum, String cond) {
         return ReportJavaScriptEngine.summarize(lens, column, func, sum, cond,
                                                 rscope);
      }

      @Override
      public String getClassName() {
         return "Engine";
      }

      ReportSheet report;
   }

   private Scriptable rscope = null; // the report scope in topscope

   private static final String GLOBAL_SCOPE_KEY =
      ReportJavaScriptEngine.class.getName() + ".globalScopes";
   private static final Lock GLOBAL_SCOPE_LOCK = new ReentrantLock();

   private static final Logger LOG =
      LoggerFactory.getLogger(ReportJavaScriptEngine.class);
}
