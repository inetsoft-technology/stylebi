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
package inetsoft.util.script;

import inetsoft.report.*;
import inetsoft.report.internal.ImageLocation;
import inetsoft.report.internal.MetaImage;
import inetsoft.report.internal.license.LicenseManager;
import inetsoft.report.script.LibScriptable;
import inetsoft.report.script.formula.FormulaFunctions;
import inetsoft.sree.SreeEnv;
import inetsoft.uql.viewsheet.TimeInfo;
import inetsoft.uql.viewsheet.VSFormat;
import inetsoft.uql.viewsheet.internal.DateComparisonUtil;
import inetsoft.util.*;
import inetsoft.util.graphics.SVGSupport;
import inetsoft.web.viewsheet.command.MessageCommand;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.*;
import org.pojava.datetime.DateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.*;
import java.io.*;
import java.lang.reflect.*;
import java.net.URL;
import java.text.*;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The javascript engine represents the script runtime environment.
 *
 * @version 6.1, 5/27/2004
 * @author InetSoft Technology Corp
 */
public class JavaScriptEngine {
   // increment script error count
   public static void incrementError(Object error) {
      scriptErrors.get().compute(error, (o, i) -> (i == null) ? 1 : ++i);
   }

   /**
    * Set the script engine containing this engine. All objects in the
    * container engine are accessible in this engine.
    */
   public void setParent(Scriptable parent) {
      topscope.setParentScope(parent);
   }

   /**
    * Sets the parent scope of <tt>child</tt> to the top-level scope.
    *
    * @param child the scriptable to be modified.
    */
   public void addTopLevelParentScope(Scriptable child) {
      child.setParentScope(topscope);
   }

   /**
    * Initialize the script runtime environment.
    */
   public void init(Map vars) throws Exception {
      Context cx = Context.enter();
      Scriptable globalscope = initScope();

      if(topscope == null) {
         topscope = new EngineScope();
      }

      topscope.setParentScope(globalscope);

      // initialize script variables
      Iterator names = vars.keySet().iterator();

      while(names.hasNext()) {
         String name = (String) names.next();
         put(name, vars.get(name));
      }

      Context.exit();
   }

   /**
    * Initialize the function object.
    */
   public Scriptable initFunction(Scriptable globalscope) throws Exception {
      if(sql || globalscope.has("newInstance", globalscope)) {
         return globalscope;
      }

      Class[] params = { String.class };
      FunctionObject func;

      func = new FunctionObject2(globalscope, JavaScriptEngine.class, "newInstance", params);
      globalscope.put("newInstance", globalscope, func);

      func = new FunctionObject2(globalscope, JavaScriptEngine.class, "isNull", Object.class);
      globalscope.put("isNull", globalscope, func);

      func = new FunctionObject2(globalscope, JavaScriptEngine.class, "isArray", Object.class);
      globalscope.put("isArray", globalscope, func);

      func = new FunctionObject2(globalscope, JavaScriptEngine.class, "indexOf",
                                 Object.class, Object.class);
      globalscope.put("indexOf", globalscope, func);

      func = new FunctionObject2(globalscope, JavaScriptEngine.class, "getDate", Object.class);
      globalscope.put("getDate", globalscope, func);

      func = new FunctionObject2(globalscope, JavaScriptEngine.class, "isDate", Object.class);
      globalscope.put("isDate", globalscope, func);

      func = new FunctionObject2(globalscope, JavaScriptEngine.class, "isNumber", Object.class);
      globalscope.put("isNumber", globalscope, func);

      func = new FunctionObject2(globalscope, JavaScriptEngine.class, "formatDate",
                                 Object.class, String.class);
      globalscope.put("formatDate", globalscope, func);

      func = new FunctionObject2(globalscope, JavaScriptEngine.class, "formatNumber",
          double.class, String.class, Object.class);
      globalscope.put("formatNumber", globalscope, func);

      func = new FunctionObject2(globalscope, JavaScriptEngine.class, "parseDate",
         String.class, Object.class);
      globalscope.put("parseDate", globalscope, func);

      func = new FunctionObject2(globalscope, JavaScriptEngine.class, "dateAdd",
         String.class, int.class, Object.class);
      globalscope.put("dateAdd", globalscope, func);

      func = new FunctionObject2(globalscope, JavaScriptEngine.class, "dateDiff",
         String.class, Object.class, Object.class);
      globalscope.put("dateDiff", globalscope, func);

      func = new FunctionObject2(globalscope, JavaScriptEngine.class, "datePart",
         String.class, Object.class, boolean.class);
      globalscope.put("datePart", globalscope, func);

      func = new FunctionObject2(globalscope, JavaScriptEngine.class,
         "datePartForceWeekOfMonth", String.class, Object.class, boolean.class, int.class);
      globalscope.put("datePartForceWeekOfMonth", globalscope, func);

      func = new FunctionObject2(globalscope, JavaScriptEngine.class, "trim", String.class);
      globalscope.put("trim", globalscope, func);

      func = new FunctionObject2(globalscope, JavaScriptEngine.class, "ltrim", String.class);
      globalscope.put("ltrim", globalscope, func);

      func = new FunctionObject2(globalscope, JavaScriptEngine.class, "rtrim", String.class);
      globalscope.put("rtrim", globalscope, func);

      func = new FunctionObject2(globalscope, JavaScriptEngine.class, "split",
                                 String.class, Object.class, Object.class);
      globalscope.put("split", globalscope, func);

      func = new FunctionObject2(globalscope, JavaScriptEngine.class, "log", Object.class);
      globalscope.put("log", globalscope, func);

      func = new FunctionObject2(globalscope, JavaScriptEngine.class, "alert", Object.class, Object.class);
      globalscope.put("alert", globalscope, func);

      func = new FunctionObject2(globalscope, JavaScriptEngine.class, "confirm", String.class);
      globalscope.put("confirm", globalscope, func);

      func = new FunctionObject2(globalscope, JavaScriptEngine.class, "registerPackage",
                                 String.class);
      globalscope.put("registerPackage", globalscope, func);

      func = new FunctionObject2(globalscope, JavaScriptEngine.class, "getImageJS", Object.class);
      globalscope.put("getImage", globalscope, func);

      func = new FunctionObject2(globalscope, JavaScriptEngine.class, "numberToString",
                                 Object.class);
      globalscope.put("numberToString", globalscope, func);

      func = new FunctionObject2(globalscope, GoogleMapsFunctions.class, "setupGoogleMapsPlot",
         Object.class, String.class, Object.class, String.class, String.class, int.class,
         int.class, int.class, int.class);
      globalscope.put("setupGoogleMapsPlot", globalscope, func);

      globalscope.put("eval", globalscope, new EvalFunc(globalscope));

      // formula related functions
      addFunctions(FormulaFunctions.class, globalscope);

      return globalscope;
   }

   /**
    * Initialize the top scope.
    */
   protected synchronized Scriptable initScope() throws Exception {
      Scriptable globalscope = getGlobalScope();

      if(globalscope == null) {
         Context cx = Context.getCurrentContext();
         globalscope = (Scriptable) cx.initStandardObjects(new GlobalScope());

         // don't restrict access to graph and data classes
         // this allows inetsoft.* classes to be new'ed
         ContextJavaPackage inetpkg = new ContextJavaPackage(
            "inetsoft", "inetsoft.graph", "inetsoft.report",
            "inetsoft.report.painter", "inetsoft.report.lens",
            "inetsoft.report.filter", "inetsoft.uql",
            "inetsoft.util.audit.templates");
         // this allows common com. and org. packages
         ContextJavaPackage compkg = new ContextJavaPackage("com");
         ContextJavaPackage orgpkg = new ContextJavaPackage("org");
         // allow java.awt (color, font) to be accessed in all envs
         // java.text for formats
         ContextJavaPackage javapkg = new ContextJavaPackage(
            "java", "java.awt", "java.text", "java.util");

         // For Protecht, only expose java.sql to end user if Form is available.
         if(LicenseManager.isComponentAvailable(LicenseManager.LicenseComponent.FORM)) {
            javapkg.addUnrestricted("java.sql");
         }

         // all packages
         Map<String,ContextJavaPackage> pkgmap = new HashMap<>();
         ContextJavaPackage[] pkgs = {inetpkg, compkg, orgpkg, javapkg};

         // setup the packages
         for(ContextJavaPackage pkg : pkgs) {
            globalscope.put(pkg.getRootName(), globalscope, pkg);
            pkg.setParentScope(globalscope);
            pkgmap.put(pkg.getRootName(), pkg);
         }

         // find custom java packages to be accessed from JavaScript
         String prop = SreeEnv.getProperty("javascript.java.packages");

         if(prop != null) {
            String[] arr = Tool.split(prop, ',', -1);

            for(int i = 0; i < arr.length; i++) {
               String pkg = arr[i];
               int dot = pkg.indexOf('.');

               // when registering a package, only give the first node of
               // the package name, otherwise it would not work
               if(dot > 0) {
                  pkg = pkg.substring(0, dot);
               }

               ContextJavaPackage jpkg = pkgmap.get(pkg);

               if(jpkg == null) {
                  Scriptable custompkg = new ContextJavaPackage(pkg, arr[i]);
                  globalscope.put(pkg, globalscope, custompkg);
                  custompkg.setParentScope(globalscope);
               }
               else {
                  jpkg.addUnrestricted(arr[i]);
               }
            }
         }

         Scriptable vscons = cx.newObject(globalscope);
         vscons.setParentScope(globalscope);
         // add Chart here so it's accessible from vs, may want to refactor it
         // to a class outside of inetsoft.util.script if necessary
         // add Chart constants
         Scriptable cons = cx.newObject(globalscope);
         cons.setParentScope(globalscope);

         Class[] chartcls = {
            inetsoft.uql.viewsheet.graph.GraphTypes.class,
            inetsoft.report.composition.region.ChartConstants.class,
            inetsoft.uql.viewsheet.graph.GeographicOption.class};

         String[] types = inetsoft.report.internal.graph.MapData.getMapTypes();

         for(int i = 0; i < types.length; i++) {
            String value = types[i];
            String name = "MAP_TYPE_" + value.toUpperCase();
            cons.put(name, cons, value);
            vscons.put(name, cons, value);
         }

         addFields(cons, chartcls);
         globalscope.put("Chart", globalscope, cons);

         Scriptable linecons = cx.newObject(globalscope);
         addFields(linecons, inetsoft.graph.aesthetic.GLine.class);
         globalscope.put("GLine", globalscope, linecons);

         Scriptable textureCons = cx.newObject(globalscope);
         addFields(textureCons, inetsoft.graph.aesthetic.GTexture.class);
         globalscope.put("GTexture", globalscope, textureCons);

         Scriptable shapeCons = cx.newObject(globalscope);
         addFields(shapeCons, inetsoft.graph.aesthetic.GShape.class);
         globalscope.put("GShape", globalscope, shapeCons);

         Scriptable svShapeCons = cx.newObject(globalscope);
         addFields(svShapeCons, inetsoft.graph.aesthetic.SVGShape.class);
         globalscope.put("SVGShape", globalscope, svShapeCons);

         Class[] all = {
            StyleConstants.class, ReportSheet.class, TableLens.class,
            VSFormat.class, TimeInfo.class,
            // Don't merge constants from GLine, GTexture, or GShape, they shadow numeric constants
            // in StyleConstants and are added individually, anyway.
//            inetsoft.graph.aesthetic.GLine.class,
//            inetsoft.graph.aesthetic.GTexture.class,
//            inetsoft.graph.aesthetic.GShape.class
         };

         addFields(vscons, chartcls);
         addFields(vscons, all);
         // define viewsheet Report constants
         globalscope.put("StyleConstant", globalscope, vscons);

         setGlobalScope(globalscope);
         globalscope = initFunction(globalscope);

         // add calc functions to global scope
         Calc topcalc = new Calc();
         Calc calc = new Calc();

         globalscope.setPrototype(topcalc);

         calc.setParentScope(globalscope);
         globalscope.put("CALC", globalscope, calc);
      }

      return globalscope;
   }

   /**
    * Get a global scope for the current context.
    */
   private Scriptable getGlobalScope() {
      return ConfigurationContext.getContext().get(GLOBAL_SCOPE_KEY);
   }

   /**
    * Set the global scope for the current context.
    */
   private void setGlobalScope(Scriptable scope) {
      ConfigurationContext.getContext().put(GLOBAL_SCOPE_KEY, scope);
   }

   /**
    * Compile a function and add it to the scope.
    */
   public void compileFunction(String name, String source) throws Exception {
      if(source == null) {
         return;
      }

      Context cx = TimeoutContext.enter();
      Function script = null;
      Scriptable globalscope = initScope();

      script = cx.compileFunction(globalscope, source, "<" + name + ">", 1,
                                  null);

      if(script != null) {
         globalscope.put(name, globalscope, script);
      }

      Context.exit();
   }

   /**
    * Compile a function and throw an exception if there is any syntax error.
    */
   public void checkFunction(String name, String source) throws Exception {
      if(source == null) {
         return;
      }

      Context cx = TimeoutContext.enter();

      cx.compileFunction(topscope, source, "<function>", 1, null);
      Context.exit();
   }

   /**
    * Compile a script into a script object.
    */
   public Script compile(String cmd) throws Exception {
      return this.compile(cmd, false);
   }

   /**
    * Compile a script into a script object.
    */
   public Script compile(String cmd, boolean fieldOnly) throws Exception {
      Context cx = TimeoutContext.enter();

      try {
         try {
            return cx.compileReader(new StringReader(cmd), "<cmd>", 1, null);
         }
         // possible exception with (63516):
         // Encountered code generation error while compiling script:
         //  generated bytecode fro method exceeds 64K limit.
         catch(EvaluatorException ex) {
            cx.setOptimizationLevel(-1);
            return cx.compileReader(new StringReader(cmd), "<cmd>", 1, null);
         }
      }
      finally {
         Context.exit();
      }
   }

   /**
    * Execute a script.
    * @param script script object.
    * @param scope scope this script should execute in.
    * @param rscope report/viewsheet scope.
    */
   public Object exec(Script script, final Object scope, Scriptable rscope) throws Exception {
      if(errorsExceeded(script)) {
         return null;
      }

      ClassLoader loader = Thread.currentThread().getContextClassLoader();
      Thread.currentThread().setContextClassLoader(getClass().getClassLoader());
      Context cx = TimeoutContext.enter();
      Object val = null;
      Stack<Scriptable> stack = execScriptable.get();
      stack.push((Scriptable) scope);

      if(scope instanceof DynamicScope) {
         if(dynamicLib == null) {
            dynamicLib = new LibScriptable(null);
         }

         addToPrototype((Scriptable) scope, dynamicLib);
      }

      try {
         TimeoutContext.startClock();
         val = script.exec(cx, (Scriptable) scope);
         val = unwrap(val);
      }
      catch(Throwable ex) {
         incrementError(script);

         if(ex instanceof WrappedException) {
            ex = (Throwable) ((WrappedException) ex).unwrap();
         }

         if(ex instanceof JavaScriptException) {
            Object exval = ((JavaScriptException) ex).getValue();

            if(exval instanceof Throwable) {
               LOG.debug("Javascript exception in execution", exval);
            }
         }
         else if(ex instanceof EcmaError) {
            // ignore, will be printed later
         }
         // runtime error gives more information and is not a script problem
         else if(ex instanceof Error || ex instanceof RuntimeException) {
            LOG.debug("Script error or runtime exception", ex);
         }

         ByteArrayOutputStream buf = new ByteArrayOutputStream();
         PrintWriter writer = new PrintWriter(buf);

         ex.printStackTrace(writer);//NOSONAR
         writer.close();
         ByteArrayInputStream inbuf = new ByteArrayInputStream(buf.toByteArray());
         BufferedReader reader = new BufferedReader(new InputStreamReader(inbuf));
         String line;
         Vector elines = new Vector(); // error line numbers

         while((line = reader.readLine()) != null) {
            int idx = line.indexOf("<");

            if(idx > 0) {
               int eidx = line.indexOf(">:", idx + 1);

               if(eidx > 0) {
                  eidx = line.indexOf(')', idx);

                  if(idx > 0) {
                     String str = line.substring(idx, eidx);

                     // if <cmd>, delete it. it is used for regular script
                     if(str.startsWith("<cmd>:")) {
                        str = str.substring(6);
                     }

                     elines.addElement(str);
                  }
               }
            }
         }

         // construct line number info
         StringBuilder linemsg = new StringBuilder();

         for(int i = 0; i < elines.size(); i++) {
            if(i > 0) {
               linemsg.append(" " + Catalog.getCatalog().getString("called from line"));
            }
            else {
               linemsg.append(" " + Catalog.getCatalog().getString("at line"));
            }

            linemsg.append(" " + elines.elementAt(i));
         }

         throw new ScriptException("(" + ex.getMessage() + ") " + linemsg);
      }
      finally {
         stack.pop();

         if(stack.isEmpty()) {
            execScriptable.remove();
         }

         if(scope instanceof DynamicScope) {
            removeFromPrototype((Scriptable) scope, dynamicLib);
         }

         Thread.currentThread().setContextClassLoader(loader);
         Context.exit();
      }

      return val;
   }

   /**
    * Get the scriptable executing the actual script.
    */
   public static Scriptable getExecScriptable() {
      Stack<Scriptable> stack = execScriptable.get();
      return stack.isEmpty() ? null : stack.peek();
   }

   /**
    * Check if the script already threw the max number of errors specified by the
    * <code>script.max.errors</code> property
    */
   public static boolean errorsExceeded(Object error) {
      final String maxErrorProp = maxErrorsProperty.get();
      final Integer maxErrors = Tool.getIntegerData(maxErrorProp);

      if(maxErrors == null) {
         return false;
      }

      int errorCount = scriptErrors.get().getOrDefault(error, 0);

      if(errorCount == maxErrors) {
         LOG.warn("Script Max Errors Exceeded (error count: {})", maxErrors);
         scriptErrors.get().put(error, ++errorCount);
      }

      return errorCount > maxErrors;
   }

   /**
    * Evaluate a Javascript command.
    */
   public Object evaluate(String cmd) throws Exception {
      Context cx = Context.enter();
      Object val = cx.evaluateString(topscope, cmd, "<cmd>", 1, null);

      Context.exit();

      return unwrap(val);
   }

   /**
    * Find a Javascript object in a scope. If the id does not have a
    * scriptable object, this function returns null;
    * @id element id.
    * @param scope Javascript scope.
    * @return Javascript object for an element or null.
    */
   public Scriptable getScriptable(Object id, Scriptable scope) {
      Context.enter();
      scope = (scope == null) ? topscope : scope;

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
            return new NativeJavaObject(topscope, val, val.getClass());
         }

         return null;
      }
      finally {
         Context.exit();
      }
   }

   /**
    * Find all functions in the current runtime.
    */
   public static Set getAllFunctions(JavaScriptEngine engine) {
      return getAllFunctions(engine, false);
   }

   /**
    * Find all functions in the current runtime.
    */
   public static Set getAllFunctions(JavaScriptEngine engine, boolean fieldOnly) {
      Context cx = Context.enter();
      Set funcs = new HashSet();
      Set proc = new HashSet(); // processed objects
      Scriptable scope = engine.getScriptable(null, null);

      if(scope != null && !fieldOnly) {
         findFunctions(scope, funcs, proc, true);
         findFunctions(scope.getParentScope(), funcs, proc, false); // topscope
      }

      try {
         findFunctions(engine.initScope(), funcs, proc, false); // globalscope
      }
      catch(Exception ex) {
         LOG.error(
                     "Failed to find functions in global scope", ex);
      }

      try {
         // find all element types
         Field[] fields = JavaScriptEngine.class.getDeclaredFields();

         for(int i = 0; i < fields.length; i++) {
            if(Modifier.isStatic(fields[i].getModifiers()) &&
               fields[i].getName().endsWith("Prototype")) {
               Object obj = fields[i].get(null);

               if(obj instanceof Scriptable) {
                  findFunctions((Scriptable) obj, funcs, proc, false);
               }
            }
         }
      }
      catch(Exception ex) {
         LOG.error("Failed to find static functions", ex);
      }

      Context.exit();

      return funcs;
   }

   /**
    * Find all functions in the scope.
    */
   private static void findFunctions(Scriptable obj, Set funcs, Set proc,
      boolean recursive) {
      if(proc.contains(obj) || obj instanceof Undefined) {
         return;
      }

      proc.add(obj);
      Object[] arr = obj.getIds();

      for(int i = 0; i < arr.length; i++) {
         if(arr[i] instanceof String) {
            Object child = obj.get((String) arr[i], obj);

            if(child instanceof Function) {
               funcs.add(arr[i]);
            }
            else if(recursive && (child instanceof Scriptable)) {
               findFunctions((Scriptable) child, funcs, proc, true);
            }
         }
      }
   }

   /**
    * Recursively find a scriptable object.
    */
   protected static Object findObject(Object name, Scriptable scope,
                                      Vector checked)
   {
      // if scope already processed, ignore
      if(scope == null || checked.indexOf(scope) >= 0) {
         return Scriptable.NOT_FOUND;
      }

      checked.addElement(scope);

      Object val = Scriptable.NOT_FOUND;

      if(name instanceof Integer) {
         val = scope.get(((Integer) name).intValue(), scope);
      }
      else {
         try {
            val = scope.get(name.toString(), scope);
         }
         catch(NoClassDefFoundError ex) {
            return Scriptable.NOT_FOUND;
         }
      }

      if(val != Undefined.instance && val != Scriptable.NOT_FOUND) {
         return val;
      }

      val = findObject(name, scope.getPrototype(), checked);

      if(val != Undefined.instance && val != Scriptable.NOT_FOUND) {
         return val;
      }

      return findObject(name, scope.getParentScope(), checked);
   }

   /**
    * Find a qualified name (e.g. java.lang).
    */
   private static Object findQualifiedObject(String name, Scriptable scope) {
      Vector checked = new Vector();
      int dot = name.indexOf('.');

      if(dot < 0) {
         return findObject(name, scope, checked);
      }

      String name2 = name.substring(dot + 1);
      name = name.substring(0, dot);

      Object obj = findObject(name, scope, checked);

      if(obj instanceof Scriptable) {
         return findQualifiedObject(name2, (Scriptable) obj);
      }

      return Scriptable.NOT_FOUND;
   }

   /**
    * Define a variable in the top scope.
    * @param name variable name. The name can be a simple name, in which case
    * the object is added to the report scope. If the name is qualified with
    * a scope name as "scope::name", the name is added to the named scope.
    * @param obj variable value.
    */
   public void put(String name, Object obj) {
      int cc = name.indexOf("::");
      Scriptable scope = null;

      if(cc > 0) {
         String sname = name.substring(0, cc);

         name = name.substring(cc + 2);
         scope = getScriptable(sname, null);
      }

      if(scope == null) {
         scope = topscope;
      }

      scope.put(name, scope, obj);

      if(obj instanceof Scriptable) {
         // @by larryl, don't override explicitly set parent scope, otherwise
         // the scope set by call will be lost
         if(((Scriptable) obj).getParentScope() == null) {
            ((Scriptable) obj).setParentScope(scope);
         }
      }
   }

   /**
    * Get the object from the top scope.
    */
   public Object get(String name) {
      return topscope.get(name, topscope);
   }

   /**
    * Remove a variable from the scripting environment.
    * @param name variable name.
    */
   public void remove(String name) {
      topscope.delete(name);
   }

   /**
    * Get all property ids of an element.
    *
    * @param id element id.
    * @param parent true to include all ids in the parents.
    * @return a list of object ids in the scope. Function objects are
    * added '()' at the end.
    */
   public Object[] getIds(Object id, Scriptable scope, boolean parent) {
      Context cx = Context.enter();
      Scriptable obj = getScriptable(id, scope);
      HashSet ids = new HashSet();

      findIds(obj, ids, new HashSet(), parent, ID);

      Object[] idarr = ids.toArray(new Object[ids.size()]);

      Arrays.sort(idarr);
      Context.exit();

      return idarr;
   }

   /**
    * Get all property display names of an element for property tree. If the
    * element does not exist in the scope, get all display names in the report.
    * @param id element id.
    * @param parent true to include all display names in the parents.
    * @return a list of object display names in the scope. Function objects are
    * added '()' at the end.
    */
   public Object[] getDisplayNames(Object id, Scriptable scope, boolean parent)
   {
      Context cx = Context.enter();
      Scriptable obj = getScriptable(id, scope);
      HashSet ids = new HashSet();

      findIds(obj, ids, new HashSet(), parent, ID_DISPLAY_NAME);

      Object[] idarr = ids.toArray(new Object[ids.size()]);

      Arrays.sort(idarr);
      Context.exit();

      return idarr;
   }

   /**
    * Get all property names of an element for property tree. If the
    * element does not exist in the scope, get all names in the report.
    * @param id element id.
    * @param parent true to include all names in the parents.
    * @return a list of object names in the scope. Function objects are
    * added '()' at the end.
    */
   public Object[] getNames(Object id, Scriptable scope, boolean parent) {
      Context cx = Context.enter();
      Scriptable obj = getScriptable(id, scope);
      HashSet ids = new HashSet();

      findIds(obj, ids, new HashSet(), parent, ID_NAME);

      Object[] idarr = ids.toArray(new Object[ids.size()]);

      Arrays.sort(idarr);
      Context.exit();

      return idarr;
   }

   /**
    * Recursively find all IDS.
    */
   private void findIds(Scriptable obj, Set ids, Set checked, boolean parent,
                        int type)
   {
      // if object already processed, ignore
      if(obj == null || checked.contains(obj)) {
         return;
      }

      checked.add(obj);

      // @by larryl, if this is a native java string, it will be converted to
      // the js string at runtime. If we use the NativeJavaObject to get the
      // members from the java string, the signature could be different or
      // missing from the js strings.
      if(obj instanceof Wrapper && ((Wrapper) obj).unwrap() instanceof String) {
         // convert NativeJavaObject of String to NativeString (js)
         obj = Context.toObject(((Wrapper) obj).unwrap(), obj,
                                ScriptRuntime.StringClass);
      }

      Object[] arr = {};
      boolean global = obj == getGlobalScope();

      // @by larryl, true getAllIds() first to try to get standard properties
      if(obj instanceof ScriptableObject) {
         arr = ((ScriptableObject) obj).getAllIds();
      }

      // @by larryl, if getAllIds() isn't implemented, we get the ids from the
      // regular ids
      if(arr.length == 0) {
         arr = obj.getIds();
      }

      for(int i = 0; i < arr.length; i++) {
         // add () to function
         if(arr[i] instanceof String) {
            Object child = obj.get((String) arr[i], obj);
            boolean idfunction = false;

            if(child instanceof Scriptable) {
               Scriptable prop = (Scriptable) child;

               if(prop instanceof Function) {
                  if(prop instanceof IdFunctionObject) {
                     idfunction = true;
                  }

                  if(!(prop instanceof NativeJavaMethod &&
                     ((String)arr[i]).endsWith("()")))
                  {
                     arr[i] = toFunction(arr[i]);
                  }
               }
               else if(prop instanceof ArrayObject) {
                  // is display name for property tree?
                  if(type == ID_DISPLAY_NAME) {
                     arr[i] = arr[i] + ((ArrayObject) prop).getDisplaySuffix();
                     arr[i] = translateArray((String) arr[i]);
                  }
                  // is name for auto complete?
                  else if(type == ID_NAME) {
                     arr[i] = arr[i] + ((ArrayObject) prop).getSuffix();
                     arr[i] = translateArray((String) arr[i]);
                  }
               }
            }

            // include all global functions, e.g. Date, isNaN
            if(!idfunction || global) {
               ids.add(arr[i]);
            }
         }
      }

      findIds(obj.getPrototype(), ids, checked, parent, type);

      if(parent) {
         findIds(obj.getParentScope(), ids, checked, parent, type);
      }
   }

   /**
    * Return a string in function call notation.
    */
   protected String toFunction(Object name) {
      return name + "()";
   }

   /**
    * Return a string in array reference notation.
    */
   protected String toArray(Object name) {
      return name + "[]";
   }

   /**
    * Normalize array name or display name.
    */
   protected String translateArray(String name) {
      return name;
   }

   /**
    * Create a new instance of an object.
    */
   public static Object newInstance(String cls) throws Exception {
      return Class.forName(cls).newInstance();
   }

   /**
    * Check if an object is an array.
    */
   public static boolean isArray(Object val) {
      val = unwrap(val);
      return (val instanceof NativeArray) ||
         val != null && val.getClass().isArray();
   }

   /**
    * get the index of an object in array.
    */
   public static int indexOf(Object arr, Object val) {
      arr = unwrap(arr);
      val = unwrap(val);

      if(!isArray(arr)) {
         return -1;
      }
      else {
         for(int i = 0; i < Array.getLength(arr); i++) {
            if(val != null && val.equals(Array.get(arr, i))) {
               return i;
            }
         }
      }

      return -1;
   }

   /**
    * Check if an object is null.
    */
   public static boolean isNull(Object val) {
      val = unwrap(val);
      return val == null || val == Undefined.instance ||
         (val instanceof Undefined);
   }

   /**
    * Check if an object is a date.
    */
   public static boolean isDate(Object val) {
      return getDate(val) != null;
   }

   /**
    * Check if an object is a number.
    */
   public static boolean isNumber(Object val) {
      val = unwrap(val);
      return val instanceof Number ||
    val instanceof Scriptable &&
         ((Scriptable) val).getClassName().equals("Number");
   }

   /**
    * Format a date object into a string.
    */
   public static String formatDate(Object val, String fmtstr) {
      Date date = getDate(val);
      SimpleDateFormat fmt = Tool.createDateFormat(fmtstr, ThreadContext.getLocale());

      return (date != null) ? fmt.format(date) : "NaD";
   }

   /**
    * Register a java package into a scope.
    */
   public static void registerPackage(String pkg) {
      Scriptable globalScope = ConfigurationContext.getContext().get(GLOBAL_SCOPE_KEY);
      registerPackage(pkg, globalScope);
   }

   /**
    * Register a java package into a scope.
    */
   public static void registerPackage(String pkg, Scriptable scope) {
      int dot = pkg.indexOf('.');

      // when registering a package, only give the first node of
      // the package name, otherwise it would not work
      if(dot > 0) {
         pkg = pkg.substring(0, dot);
      }

      try {
         ContextJavaPackage runtime = new ContextJavaPackage(pkg);
         runtime.setParentScope(scope);
         scope.put(pkg, scope, runtime);
      }
      catch(Exception ex) {
         LOG.error("Failed to register package: " + pkg, ex);
      }
   }

   /**
    * Format a number into a string.
    * @param round specify the rounding model. Use one of the following:
    * ROUND_UP, ROUND_DOWN, ROUND_CEILING, ROUND_FLOOR, ROUND_HALF_UP,
    * ROUND_HALF_DOWN, ROUND_HALF_EVEN, ROUND_UNNECESSARY. Control the
    * same behavior as BigDecimal.
    */
   public static String formatNumber(double num, String fmtstr, Object round) {
      DecimalFormat fmt = null;

      round = unwrap(round);

      if(round != null) {
         try {
            fmt = new RoundDecimalFormat(fmtstr);
            ((RoundDecimalFormat) fmt).setRoundingByName(round.toString());
         }
         catch(Exception ex) {
            LOG.warn("Failed to round number: " + fmtstr + ", " + round, ex);
         }
      }

      if(fmt == null) {
         fmt = new DecimalFormat(fmtstr);
      }

      return fmt.format(num);
   }

   /**
    * Parse a string as a date.
    */
   public static Date parseDate(String str, Object opt) {
      if(str == null || "null".equals(str)) {
         return null;
      }

      opt = unwrap(opt);

      boolean parseTime = false;
      String fmt = null;

      if(opt instanceof Boolean) {
         parseTime = (Boolean) opt;
      }
      else if(opt instanceof String) {
         fmt = (String) opt;
      }

      if(fmt != null) {
         try {
            Map<String, DateFormat> fmts = dateFormats.get();
            DateFormat datefmt = fmts.get(fmt);

            if(datefmt == null) {
               fmts.put(fmt, datefmt = Tool.createDateFormat(fmt));
            }

            return datefmt.parse(str);
         }
         catch(Exception ex) {
            // Bug #11456, format "yyyy-MMM-dd" can not support some locale, for example
            // locale china.
            try {
               return Tool.createDateFormat(fmt, Locale.US).parse(str);
            }
            catch(Exception e) {
               LOG.warn("Failed to parse date " + str + " using format: " + fmt + ": " + e);
               return null;
            }
         }
      }
      else if(!parseTime) {
         try {
            return DateFormat.getDateInstance().parse(str);
         }
         catch(Exception ex) {
            // If can't get proper format, use default format.
            try {
               DateTime dt = DateTime.parse(str, CoreTool.getDateTimeConfig());
               return dt.toDate();
            }
            catch(Exception e) {
               LOG.warn("Failed to parse date using default format: " + str + ": " + e);
               return null;
            }
         }
      }
      else {
         DateFormat df = DateFormat.getTimeInstance(DateFormat.LONG);

         try {
            return df.parse(str);
         }
         catch(Exception exl) {
            df = DateFormat.getTimeInstance(DateFormat.MEDIUM);
            try {
               return df.parse(str);
            }
            catch(Exception exm) {
               df = DateFormat.getTimeInstance(DateFormat.SHORT);

               try {
                  return df.parse(str);
               }
               catch(Exception exs) {
                  LOG.warn("Failed to parse date using SHORT format: " + str + ": " + exs);
                  return null;
               }
            }
         }
      }
   }

   /**
    * Add an interval to a date.
    */
   public static Date dateAdd(String interval, int amount, Object date) {
      Date dateVal = getDate(date);

      if(dateVal != null) {
         Calendar cal = CoreTool.calendar.get();
         int field = getDateInterval(interval);

         if(interval.equals("q")) {
            amount *= 3;
         }

         cal.setTime(dateVal);
         cal.add(field, amount);
         return cal.getTime();
      }

      return null;
   }

   /**
    * Calculate the difference of two dates on the specified interval.
    * Available interval include:
    *    yyyy   Year
    *    q      Quarter
    *    m      Month
    *    w      Week
    *    ws     Week
    *    y      Day
    *    d      Day
    *    h      Hour
    *    n      Minute
    *    s      Second
    */
   public static double dateDiff(String interval, Object date1, Object date2) {
      Date dateVal1 = getDate(date1);
      Date dateVal2 = getDate(date2);

      if(dateVal1 != null && dateVal2 != null) {
         final ZonedDateTime zonedDate1 = Instant.ofEpochMilli(dateVal1.getTime())
            .atZone(ZoneId.systemDefault());
         final ZonedDateTime zonedDate2 = Instant.ofEpochMilli(dateVal2.getTime())
            .atZone(ZoneId.systemDefault());

         if(interval.equals("y") || interval.equals("d") || interval.equals("w")) {
            return zonedDate1.until(zonedDate2, ChronoUnit.DAYS);
         }
         else if(interval.equals("ws") || interval.equals("ww")) {
            return zonedDate1.until(zonedDate2, ChronoUnit.WEEKS);
         }
         else if(interval.equals("h")) {
            return zonedDate1.until(zonedDate2, ChronoUnit.HOURS);
         }
         else if(interval.equals("n")) {
            return zonedDate1.until(zonedDate2, ChronoUnit.MINUTES);
         }
         else if(interval.equals("s")) {
            return zonedDate1.until(zonedDate2, ChronoUnit.SECONDS);
         }
         else {
            GregorianCalendar cal1 = CoreTool.calendar.get();
            GregorianCalendar cal2 = CoreTool.calendar2.get();
            int field = getDateInterval(interval);

            cal1.setTime(dateVal1);
            cal2.setTime(dateVal2);

            if(interval.equals("yyyy")) {
               return cal2.get(field) - cal1.get(field);
            }
            else if(interval.equals("q") || interval.equals("m")) {
               int diff = cal2.get(field) - cal1.get(field);

               diff = diff + (cal2.get(Calendar.YEAR) - cal1.get(Calendar.YEAR))
                  * 12;

               if(interval.equals("q")) {
                  diff /= 3;
               }

               return diff;
            }
         }
      }

      return 0;
   }

   /**
    * Extract a date interval.
    */
   public static double datePart(String interval, Object date, boolean applyWeekStart) {
      return datePartForceWeekOfMonth(interval, date, applyWeekStart, -1);
   }

   /**
    * Extract a date interval.
    */
   public static double datePartForceWeekOfMonth(String interval, Object date,
                                                 boolean applyWeekStart,
                                                 int forceDcToDateWeekOfMonth)
   {
      if(forceDcToDateWeekOfMonth > 0 && "wm".equals(interval)) {
         return forceDcToDateWeekOfMonth;
      }

      Date dateVal = getDate(date);

      if(dateVal != null) {
         Calendar cal = CoreTool.calendar.get();
         int oldStart = applyWeekStart ? cal.getFirstDayOfWeek() : 0;
         int minFirstWeek = cal.getMinimalDaysInFirstWeek();

         try {
            if(applyWeekStart) {
               cal.setFirstDayOfWeek(Tool.getFirstDayOfWeek());
            }

            cal.setMinimalDaysInFirstWeek(7);
            cal.setTime(dateVal);

            // month of quarter
            switch(interval) {
               // month of quarter
            case "mq":
               return cal.get(Calendar.MONTH) % 3 + 1;
            // month of quarter of full week
            case "wmq":
               cal.add(Calendar.DATE, -(cal.get(Calendar.DAY_OF_WEEK) - 1));
               DateComparisonUtil.adjustCalendarByForceWM(cal, forceDcToDateWeekOfMonth);

               return cal.get(Calendar.MONTH) % 3 + 1;
            // week of quarter
            case "wq":
               return DateComparisonUtil.getWeekOfQuarter(cal, cal.getFirstDayOfWeek());
               // week of year
            case "wy":
               int dayOfWeek = cal.get(Calendar.DAY_OF_WEEK);
               cal.add(Calendar.DATE, -(dayOfWeek - 1));
               int weekOfMonth = cal.get(Calendar.WEEK_OF_MONTH);

               if(DateComparisonUtil.adjustCalendarByForceWM(cal, forceDcToDateWeekOfMonth)) {
                  weekOfMonth = forceDcToDateWeekOfMonth;
               }

               return (cal.get(Calendar.MONTH) + 1) * 10 + weekOfMonth;
           // day of quarter
            case "dq":
               int endDayOfYear = cal.get(Calendar.DAY_OF_YEAR);
               cal.add(Calendar.MONTH, -cal.get(Calendar.MONTH) % 3);
               cal.set(Calendar.DAY_OF_MONTH, 1);
               int startDayOfYear = cal.get(Calendar.DAY_OF_YEAR);

               return endDayOfYear - startDayOfYear + 1;
            case "q":
               int month = cal.get(Calendar.MONTH);
               return (month / 3) + 1;
            default:
               int field = getDateInterval(interval);

               if(field == GregorianCalendar.MONTH) {
                  return cal.get(field) + 1;
               }

               int val = cal.get(field);

               // if week-of-month is before the 1st week, it's the last week of previous month.
               if(val < 1 && "wm".equals(interval)) {
                  cal.add(Calendar.DATE, -(cal.get(Calendar.DAY_OF_WEEK) - 1));
                  val = cal.get(field);
               }

               return val;
            }
         }
         finally {
            if(applyWeekStart) {
               cal.setFirstDayOfWeek(oldStart);
            }

            cal.setMinimalDaysInFirstWeek(minFirstWeek);
         }
      }

      return 0;
   }

   /**
    * Load an image from resource. This method is called if it originates from
    * a script/expression.
    */
   public static Image getImageJS(Object img) {
      // use getImageJS only from js. (54833)
      if("true".equals(SreeEnv.getProperty("javascript.getImageFunction.disabled"))) {
         return null;
      }

      return getImage(img);
   }

   /**
    * Load an image from resource.
    */
   public static Image getImage(Object img) {
      Image image = null;
      ImageLocation iloc = null;
      img = unwrap(img);
      boolean imageavaliable = false;

      // string is a resource, or ascii encoded string
      if((img instanceof String) || (img instanceof URL)) {
         // try string as resource first
         InputStream input = null;

         try {
            if((img instanceof String) && img.toString().indexOf("://") > 0) {
               iloc = new ImageLocation(".");
               iloc.setPath(img.toString());
               iloc.setPathType(ImageLocation.IMAGE_URL);
               img = new URL((String) img);
               input = ((URL) img).openStream();
            }
            else if(img instanceof String) {
               String str = (String) img;

               if(str.isEmpty()) {
                  return null;
               }

               File file = FileSystemService.getInstance().getFile(str);

               if(file.exists()) {
                  input = new FileInputStream(file);
                  iloc = new ImageLocation(".");
                  iloc.setPath(str);
                  iloc.setPathType(ImageLocation.FULL_PATH);
                  iloc.setLastModified(file.lastModified());
               }
               else {
                  input = JavaScriptEngine.class.getResourceAsStream(str);
                  iloc = new ImageLocation(".");
                  iloc.setPath(str);
                  iloc.setPathType(ImageLocation.RESOURCE);
               }
            }
            else {
               input = ((URL) img).openStream();
            }
         }
         catch(Throwable ex) {// ignore, may not be a resource
         }

         if(input != null) {
            // @by larryl, don't create an image here (memory issue). The image
            // can be used by MetaImage on demand with proper caching
            // @by stephenwebster, For Bug #33237
            // In order to prevent access to non image data, create the image from
            // end user on demand
            if(FormulaContext.isRestricted()) {
               if(iloc != null && iloc.getPath() != null &&
                  iloc.getPath().toLowerCase().endsWith(".svg"))
               {
                  // bug #652262, ImageIO (via Tool.getImage) does not support SVG
                  try {
                     image = SVGSupport.getInstance().getSVGImage(input);
                  }
                  catch(Exception e) {
                     LOG.warn("Failed to load SVG from {}", iloc.getPath(), e);
                     image = null;
                  }
               }
               else {
                  image = Tool.getImage(input);
               }
            }

            imageavaliable = true;

            try {
               input.close();
            }
            catch(Exception ex) {
               LOG.debug("Failed to close input stream", ex);
            }
         }
         else if(img instanceof String) {
            String str = (String) img;
            // check if it's ascii hex
            boolean hex = true;
            str = Tool.replace(str, "\n", "");

            // only check the first 100 for efficiency
            for(int i = 0; i < str.length() && i < 100; i++) {
               char ch = Character.toUpperCase(str.charAt(i));

               if(!Character.isDigit(ch) && (ch < 'A' || ch > 'F')) {
                  hex = false;
                  break;
               }
            }

            byte[] buf = null;

            if(hex) {
               buf = Encoder.decodeAsciiHex(str);
            }
            else {
               buf = Encoder.decodeAscii85(str);
            }

            image = Tool.getImage(new ByteArrayInputStream(buf));
         }
      }
      else if(img instanceof byte[]) {
         image = Tool.getImage(new ByteArrayInputStream((byte[]) img));
      }
      else if(img instanceof Image) {
         image = (Image) img;
      }

      boolean success = true;

      if(image != null) {
         success = Tool.waitForImage(image);
      }
      else if(!imageavaliable && iloc == null) {
         LOG.error("Failed to load image from script: " + img);
      }

      if(iloc != null) {
         try {
            MetaImage meta = new MetaImage(iloc);
            Image image0 = image;

            // @by stephenwebster, For Bug #33237
            // In order to prevent access to non image data by the end user
            // do not return a MetaImage object
            if(!FormulaContext.isRestricted()) {
               image = meta;
            }

            if(success) {
               meta.setImage(image0);
            }

            // @by billh, fix customer bug bug1312487574948
            // for invalid image location, return null rather than meta image
            // @by gregm fix bug1335822368895, do not override the MetaImage by
            // assigning MetaImage.getImage to image.
            if(!iloc.isImageExisting()) {
               return null;
            }

            if(image != null) {
               boolean val = Tool.waitForImage(image);
               image = !val ? null : image;
            }
         }
         catch(Exception ex) {
            LOG.error(
                        "Failed to load image from location: " + iloc, ex);
         }
      }

      return image;
   }

   /**
    * Trim a string on both ends.
    */
   public static String trim(String str) {
      return str.trim();
   }

   /**
    * Trim a string on left side.
    */
   public static String ltrim(String str) {
      int idx = 0;

      for(; idx < str.length(); idx++) {
         if(!Character.isSpace(str.charAt(idx))) {
            break;
         }
      }

      return (idx > 0) ? str.substring(idx) : str;
   }

   /**
    * Trim a string on right side.
    */
   public static String rtrim(String str) {
      int idx = str.length() - 1;

      for(; idx >= 0; idx--) {
         if(!Character.isSpace(str.charAt(idx))) {
            idx++;
            break;
         }
      }

      if(idx < 0) {
         return "";
      }

      return str.substring(0, idx);
   }

   /**
    * Trim a string on right side.
    */
   public static String[] split(String str, Object delim, Object count) {
      delim = unwrap(delim);
      if(delim == null) {
         delim = " ";
      }

      int iCount = -1;

      count = unwrap(count);
      if(count != null) {
         iCount = ((Number) count).intValue();
      }

      return (((String) delim).length() == 1) ?
         Tool.split(str, ((String) delim).charAt(0), iCount) :
         Tool.split(str, (String) delim, iCount);
   }

   /**
    * Print a message to the log.
    */
   public static void log(Object msg) {
      msg = unwrap(msg);

      if(msg instanceof Throwable) {
         Throwable ex = (Throwable) msg;
         LOG.warn(ex.getMessage(), ex);
      }
      else {
         LOG.info(String.valueOf(msg));
      }
   }

   /**
    * Show a message to user (only works for VS).
    */
   public static void alert(Object msg, Object level) {
      msg = unwrap(msg);

      if(level == null) {
         level = MessageCommand.Type.INFO;
      }
      else if(!(level instanceof MessageCommand.Type)) {
         level = unwrap(level);

         if(level instanceof String) {
            try {
               level = MessageCommand.Type.valueOf(((String) level).toUpperCase());
            }
            catch(Exception e) {
               level = MessageCommand.Type.INFO;
            }

            if(level == MessageCommand.Type.PROGRESS) {
               level = MessageCommand.Type.INFO;
            }
         }
         else {
            level = MessageCommand.Type.INFO;
         }
      }

      final UserMessage userMessage = new UserMessage(String.valueOf(msg), ((MessageCommand.Type) level).code());
      CoreTool.addUserMessage(userMessage);
   }

   /**
    * Show a message to user (only works for VS).
    */
   public static void confirm(String msg) {
      CoreTool.addConfirmMessage(msg);
   }

   /**
    * Get a Java date object from a date or a javascript date.
    */
   public static Date getDate(Object val) {
      val = unwrap(val);

      if(val instanceof Date) {
         return (Date) val;
      }

      return null;
   }

   /**
    * Converts date interval value to calendar field.
    */
   private static int getDateInterval(String interval) {
      int field = GregorianCalendar.DAY_OF_YEAR;

      if(interval.equals("yyyy")) {
         field = GregorianCalendar.YEAR;
      }
      else if(interval.equals("q")) {
         field = GregorianCalendar.MONTH;
      }
      else if(interval.equals("m")) {
         field = GregorianCalendar.MONTH;
      }
      else if(interval.equals("y")) {
         field = GregorianCalendar.DAY_OF_YEAR;
      }
      else if(interval.equals("d")) {
         field = GregorianCalendar.DAY_OF_MONTH;
      }
      else if(interval.equals("w")) {
         field = GregorianCalendar.DAY_OF_WEEK;
      }
      else if(interval.equals("ww")) {
         field = GregorianCalendar.WEEK_OF_YEAR;
      }
      else if(interval.equals("wm")) {
         field = GregorianCalendar.WEEK_OF_MONTH;
      }
      else if(interval.equals("h")) {
         field = GregorianCalendar.HOUR;
      }
      else if(interval.equals("n")) {
         field = GregorianCalendar.MINUTE;
      }
      else if(interval.equals("s")) {
         field = GregorianCalendar.SECOND;
      }

      return field;
   }

   /**
    * Get string object from a number or a javascript number.
    */
   public static String numberToString(Object val) {
      Object nval = unwrap(val);

      if(nval instanceof Number) {
         return Tool.toString(nval);
      }

      return val == null ? null : val.toString();
   }

   /**
    * Unwrapp a wrapper.
    */
   public static Object unwrap(Object obj) {
      return ScriptUtil.unwrap(obj);
   }

   /**
    * Add public constant static fields to the object as constants.
    */
   public static void addFields(Scriptable obj, Class... clss) throws Exception {
      for(Class cls : clss) {
         for(Field field : cls.getFields()) {
            int mod = field.getModifiers();

            if(Modifier.isPublic(mod) && Modifier.isStatic(mod) &&
               Modifier.isFinal(mod)) {
               obj.put(field.getName(), obj, field.get(null));
            }
         }
      }
   }

   /**
    * Convert a string to an identifier.
    */
   public String toIdentifier(String id) {
      return id.replace(' ', '_');
   }

   /**
    * Delete all properties of an object.
    */
   private void delete(Scriptable obj) {
      if(obj == null) {
         return;
      }

      Context cx = Context.enter();

      try {
         if(obj instanceof Undefined) {
            return;
         }

         obj.setPrototype(null); // clear link to parent from bean
         obj.setParentScope(null); // break recursive link

         // optimization, if not scriptable object, the reference is not
         // saved (most likely so we don't need to call all the get()
         if(obj instanceof ScriptableObject) {
            Object[] ids = obj.getIds();

            for(int i = 0; i < ids.length; i++) {
               Object val = null;

               if(ids[i] instanceof Integer) {
                  int idx = ((Integer) ids[i]).intValue();

                  val = obj.get(idx, obj);

                  if(val instanceof Scriptable) {
                     obj.delete(idx);
                  }
               }
               else if(ids[i] instanceof String) {
                  val = obj.get((String) ids[i], obj);

                  if(val instanceof Scriptable) {
                     obj.delete((String) ids[i]);
                  }
               }
            }
         }
      }
      finally {
         Context.exit();
      }
   }

   /**
    * Set whether is for sql only.
    */
   public void setSQL(boolean sql) {
      this.sql = sql;
   }

   /**
    * check if is for sql only.
    */
   public boolean isSQL() {
      return sql;
   }

   /**
    * The top-most scope shared by all engines. It is used to return
    * undefined if a symbol is not found in the environment anywhere.
    */
   public static class GlobalScope extends ScriptableObject {
      @Override
      public String getClassName() {
         return "GlobalScope";
      }

      public Object get(String name, Scriptable start) {
         // @by stephenwebster, For Bug #16190
         // Disable Access to unsafe top level Scriptables which can lead to the
         // bypassing of package security restrictions.  This has to be done before
         // the call to super.get
         if(name.equals("Packages") || name.equals("getClass") ||
            name.equals("JavaAdapter") || name.equals("JavaImporter"))
         {
            return NOT_FOUND;
         }

         return super.get(name, start);
      }

      @Override
      public Object[] getIds() {
         return getAllIds();
      }
   }

   /**
    * Split string into string array.
    */
   public static String[] splitStr(Object str) {
      Object arr = split(str);
      String[] ns = new String[Array.getLength(arr)];

      for(int i = 0; i < ns.length; i++) {
         Object item = Array.get(arr, i);

         if(item != null) {
            try {
               ns[i] = item.toString();
            }
            catch(Throwable e) {// ignored, defaults to 0
            }
         }
      }

      return ns;
   }

   /**
    * Split an object into an array.
    */
   public static Object[] split(Object str) {
      Object[] arr = null;

      str = unwrap(str);

      // @by larryl, for formula references, an empty value is returned as
      // a null. When converting an object back to an array, we treat null
      // as an empty array for consistency.
      if(str == null) {
         arr = new Object[0];
      }
      else if(str instanceof NativeArray) {
         NativeArray narr = (NativeArray) str;
         Object[] arr2 = new Object[(int) narr.jsGet_length()];

         for(int i = 0; i < arr2.length; i++) {
            arr2[i] = unwrap(narr.get(i, narr));
         }

         arr = arr2;
      }
      else if(str.getClass().isArray()) {
         arr = (Object[]) str;

         // @by larryl, rhino 1.7 may return mixed double/int array for numbers.
         // This would cause Arrays.sort to fail (cast exception in compareTo).
         Set<Class> types = new HashSet<>();

         for(int i = 0; i < arr.length; i++) {
            arr[i] = unwrap(arr[i]);

            if(arr[i] != null) {
               types.add(arr[i].getClass());
            }
         }

         if(types.size() > 1) {
            for(int i = 0; i < arr.length; i++) {
               if(arr[i] instanceof Number) {
                  arr[i] = Double.valueOf(((Number) arr[i]).doubleValue());
               }
            }
         }
      }
      else if(str instanceof String) {
         arr = Tool.split((String) str, ',');
      }
      else {
         arr = new Object[] { str };
      }

      return arr;
   }

   /**
    * Add all static functions from a class to a scope.
    */
   public static void addFunctions(Class cls, Scriptable scope) {
      Method[] methods = cls.getMethods();

      try {
         for(int j = 0; j < methods.length; j++) {
            if(methods[j].getDeclaringClass() != cls ||
               !Modifier.isStatic(methods[j].getModifiers()) ||
               !Modifier.isPublic(methods[j].getModifiers()))
            {
               continue;
            }

            FunctionObject func = new FunctionObject(
               methods[j].getName(), methods[j], scope);

            scope.put(methods[j].getName(), scope, func);
         }
      }
      catch(Exception ex) {
         LOG.error("Failed to add functions from: " + cls, ex);
      }
   }

   /**
    * Scope to hold engine (per report) properties.
    */
   protected static class EngineScope extends ImporterTopLevel {
      public EngineScope() {
         try {
            FunctionObject func;

            func = new FunctionObject("importPackage",
                          EngineScope.class.getMethod("importPackage",
                         new Class[] { Object.class }), this);
            this.put("importPackage", this, func);

            func = new FunctionObject(
               "importClass", EngineScope.class.getMethod(
               "importClass", new Class[] { Object.class }), this);
            this.put("importClass", this, func);

            // add all default (e.g. graph) packages so they don't need to
            // be imported individually
            DefaultPackages def = new DefaultPackages(this);
            addToPrototype(this, def);
         }
         catch(Exception ex) {
            LOG.error("Failed to init EngineScope", ex);
         }
      }

      /**
       * Import a package into a scope, e.g. importPackage(inetsoft.graph)
       */
      public void importPackage(Object pkg) {
         try {
            pkg = unwrap(pkg);

            if(pkg == null) {
               return;
            }

            if(pkg instanceof String) {
               pkg = findQualifiedObject((String) pkg, this);

               if(pkg == Scriptable.NOT_FOUND || pkg == null) {
                  pkg = new NativeJavaPackage((String) pkg);
                  ((Scriptable) pkg).setParentScope(this);
               }
            }

            if(pkg instanceof ContextJavaPackage) {
               throw new RuntimeException("Restricted package: " + pkg);
            }

            addToPrototype(this, new JavaPackageWrapper((Scriptable) pkg));
         }
         catch(Exception ex) {
            LOG.error("Failed to import package: " + pkg, ex);
         }
      }

      /**
       * Imports a class into this scope, e.g. importClass(java.awt.Font)
       *
       * @param cls the class to import.
       */
      public void importClass(Object cls) {
         try {
            if(!(cls instanceof NativeJavaClass)) {
               throw new RuntimeException("Not a class: " + cls);
            }

            NativeJavaClass jcls = (NativeJavaClass) cls;
            String fqn = jcls.getClassObject().getName();
            String name = fqn.substring(fqn.lastIndexOf('.') + 1);
            Object val = get(name, this);

            if(val != NOT_FOUND && val != jcls) {
               throw new RuntimeException("Class already defined: " + jcls);
            }

            put(name, this, jcls);
         }
         catch(Exception exc) {
            LOG.error("Failed to import class: " + cls, exc);
         }
      }

      @Override
      public String getClassName() {
         return "Engine";
      }
   }

   /**
    * Add a JS object to scope's prototype chain.
    * @param scope target obj to add to prototype.
    * @param jsobj prototype for scope.
    */
   public static void addToPrototype(Scriptable scope, Scriptable jsobj) {
      synchronized(scope) {
         Scriptable proto = scope;

         while(proto != null && proto.getPrototype() != null) {
            proto = proto.getPrototype();

            if(proto == jsobj) { // already added, ignore
               return;
            }
         }

         if(proto != null) {
            proto.setPrototype(jsobj);
         }
      }
   }

   /**
    * remove a JS object from scope's prototype chain.
    */
   public static void removeFromPrototype(Scriptable scope, Scriptable jsobj) {
      Scriptable proto = scope;

      synchronized(scope) {
         while(proto != null && proto.getPrototype() != null) {
            Scriptable proto2 = proto.getPrototype();

            if(proto2 == jsobj) { // found, remove
               proto.setPrototype(jsobj.getPrototype());
               break;
            }

            proto = proto2;
         }
      }
   }

   /**
    * A wrapper to prevent any name lookup to be returned as a symbol in the
    * scope. The NativeJavaPackage class returns another package object for
    * ANY name if it doesn't exist. This causes the name lookup to be totally
    * messaged up if a package is used as a prototype.
    */
   private static class JavaPackageWrapper extends ScriptableObject {
      public JavaPackageWrapper(Scriptable pkg) {
         this.pkg = pkg;

         // extract package name, this is extremely dependent on rhino version
         // but currently there is not api to get it nicely
         // [JavaPackage inetsoft.graph]
         String name = pkg.toString();

         if(!name.startsWith("[JavaPackage") || !name.endsWith("]")) {
            throw new RuntimeException("Import package only: " + pkg);
         }

         name = name.substring(0, name.length() - 1);
         name = name.substring(name.indexOf(' '));
         pkgname = name.trim();
      }

      @Override
      public String getClassName() {
         return "JavaPackageWrapper";
      }

      @Override
      public Object get(String name, Scriptable start) {
         Object val = clsmap.get(name);

         if(val != null) {
            return val;
         }

         // only return if the name is a class
         try {
            Class.forName(pkgname + "." + name);
            val = pkg.get(name, start);
         }
         catch(Throwable ex) {
            val = NOT_FOUND;
         }

         clsmap.put(name, val);
         return val;
      }

      @Override
      public void put(String name, Scriptable start, Object val) {
         clsmap.put(name, val);
      }

      private Scriptable pkg;
      private String pkgname;
      private Map clsmap = new HashMap(); // class name -> Class or NOT_FOUND
   }

   /**
    * This class contains a list of packages to be imported into the
    * top scope. It is more efficient than calling importPackage()
    * for each class since the prototype will be called by every
    * name lookup. This class combines them into a single lookup.
    */
   private static class DefaultPackages extends ScriptableObject {
      public DefaultPackages(Scriptable scope) {
         this.scope = scope;
         pkgs = new String[] {
            "inetsoft.graph",
            "inetsoft.graph.aesthetic",
            "inetsoft.graph.coord",
            "inetsoft.graph.data",
            "inetsoft.graph.element",
            "inetsoft.graph.geo",
            "inetsoft.graph.guide",
            "inetsoft.graph.guide.form",
            "inetsoft.graph.scale",
            "inetsoft.graph.schema"};
      }

      @Override
      public String getClassName() {
         return "DefaultPackages";
      }

      @Override
      public Object get(String name, Scriptable start) {
         Object val = clsmap.get(name);

         if(val != null) {
            return val;
         }

         synchronized(this) {
            val = clsmap.get(name);

            if(val != null) {
               return val;
            }

            val = NOT_FOUND;
            Object cached = name2cls.get(name);
            Class<?> cls = null;

            if(cached != null) {
               if(cached instanceof Class) {
                  val = new NativeJavaClass(scope, (Class<?>) cached);
                  clsmap.put(name, val);
                  return val;
               }
               else {
                  return cached;
               }
            }
            else if(KNOWN_CLASSES.containsKey(name)) {
               try {
                  val = new NativeJavaClass(scope, cls = Class.forName(KNOWN_CLASSES.get(name)));
               }
               catch(Exception ex) {
                  // ignore
               }
            }
            // optimization, Class.forName is expensive, don't try every name
            else if(isValidClassName(name)) {
               for(String pkg : pkgs) {
                  try {
                     cls = Class.forName(pkg + "." + name);
                     val = new NativeJavaClass(scope, cls);
                     break;
                  }
                  catch(Exception ex) {
                     // ignore
                  }
               }
            }

            name2cls.put(name, cls != null ? cls : NOT_FOUND);
            clsmap.put(name, val);
         }

         return val;
      }

      private final Scriptable scope; // parent scope
      private final String[] pkgs;
      private final Map<String, Object> clsmap = new HashMap<>(); // class name -> Class or NOT_FOUND
      private static final Map<String, String> KNOWN_CLASSES = new HashMap<>();
      // we should be able to use a global (static) clsmap to share the class instance
      // in js. adding a name2cls to minimize change in logic. this isn't necessary if
      // NativeJavaClass is immutable, which should be the case.
      // should merge this with clsmap (and change clsmap to static). (13.1)
      private static final Map<String, Object> name2cls = new ConcurrentHashMap<>();
      static {
         KNOWN_CLASSES.put("ExtendedDecimalFormat", "inetsoft.util.ExtendedDecimalFormat");
      }
   }

   /**
    * Check if the string is a valid name.
    */
   private static boolean isValidClassName(String cls) {
      return cls.length() > 1 && Character.isUpperCase(cls.charAt(0)) &&
         !Character.isUpperCase(cls.charAt(cls.length() - 1));
   }

   private static class EvalFunc extends BaseFunction {
      public EvalFunc(Scriptable top) {
         this.globalScope = top;
      }

      @Override
      public Object call(Context cx, Scriptable scope, Scriptable thisObj,
                         Object[] args)
      {
         if(FormulaContext.isRestricted()) {
            throw new RuntimeException("eval() not allowed in restricted mode");
         }
         return ScriptRuntime.evalSpecial(cx, scope, thisObj, args, "eval", 0);
      }

      private Scriptable globalScope;
   }

   private static SreeEnv.Value maxErrorsProperty = new SreeEnv.Value("script.max.errors", 30000);
   private static final int ID = 0; // id selector
   private static final int ID_NAME = 1; // id name selector
   private static final int ID_DISPLAY_NAME = 2; // id display name selector
   private static final ThreadLocal<Stack<Scriptable>> execScriptable =
      ThreadLocal.withInitial(Stack::new);
   private static final ThreadLocal<Map<Object, Integer>> scriptErrors =
      ThreadLocal.withInitial(HashMap::new);
   private static final ThreadLocal<Map<String, DateFormat>> dateFormats =
      ThreadLocal.withInitial(ConcurrentHashMap::new);

   private static final String GLOBAL_SCOPE_KEY =
      JavaScriptEngine.class.getName() + ".globalScope";

   // top level scope
   protected Scriptable topscope = null;
   protected boolean sql = false;

   // dynamic scope lib
   private LibScriptable dynamicLib;

   private static final Logger LOG =
      LoggerFactory.getLogger(JavaScriptEngine.class);
}
