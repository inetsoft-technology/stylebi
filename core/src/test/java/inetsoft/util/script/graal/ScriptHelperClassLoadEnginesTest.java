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
package inetsoft.util.script.graal;

import inetsoft.graph.mxgraph.io.mxCodec;
import inetsoft.graph.mxgraph.model.*;
import inetsoft.graph.mxgraph.util.mxXmlUtils;
import inetsoft.report.script.graal.ReportGraalJavaScriptEngine;
import inetsoft.report.style.TableStyle;
import inetsoft.test.*;
import inetsoft.uql.util.XUtil;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.io.File;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #77421: complements {@link ScriptHostAccessHelperClassLoadTest}. It runs the
 * refused helper calls through both production engines (the base engine, which
 * throws script errors, and the report engine, which swallows them). It asserts
 * the side effects of calling the removed XUtil.call/field from script, and checks
 * that the helpers' legitimate uses still work: every public static XUtil member
 * stays visible, every built-in TableStyle can still be created by name, and the
 * mxgraph codec still round-trips a model for its Java callers (since #78064 it is
 * refused to scripts).
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class ScriptHelperClassLoadEnginesTest {
   private static final String XUTIL = "Java.type('inetsoft.uql.util.XUtil')";
   private static final String STYLE = "Java.type('inetsoft.report.style.TableStyle')";
   private static final String REGISTRY = "Java.type('inetsoft.graph.mxgraph.io.mxCodecRegistry')";
   private static final String UTILS = "Java.type('inetsoft.graph.mxgraph.util.mxUtils')";
   private static final String TABULAR = "Java.type('inetsoft.uql.tabular.TabularUtil')";

   private GraalJavaScriptEngine engine;

   private void open(String kind) throws Exception {
      engine = "report".equals(kind) ? new ReportGraalJavaScriptEngine() : new GraalJavaScriptEngine();
      engine.init(new HashMap<>());
      callInitialized = callInvoked = styleInitialized = styleConstructed = false;
      classInitialized = instanceInitialized = instanceConstructed = evalInitialized = false;
   }

   @AfterEach
   void teardown() {
      if(engine != null) {
         engine.close();
      }
   }

   /**
    * Runs the expression and returns "OK:" + its string value, or "ERR:" + the
    * error, the same way for both engines.
    */
   private String run(String expr) throws Exception {
      Object result = engine.exec(engine.compile(
         "(function(){try{return 'OK:'+(" + expr + ");}catch(x){return 'ERR:'+x;}})()"),
                                  null, null);
      return String.valueOf(result);
   }

   @ParameterizedTest
   @ValueSource(strings = { "base", "report" })
   void removedXUtilHelpersDoNothingFromScript(String kind) throws Exception {
      open(kind);

      String call = run(XUTIL + ".call(null,'" + CallSentinel.class.getName() + "','hit',null,null)");
      assertFalse(call.contains("hit"), call);
      assertFalse(callInitialized, "XUtil.call initialized a filtered class");
      assertFalse(callInvoked, "XUtil.call invoked a method on a filtered class");

      // Object.getClass is denied to scripts and must not be reachable through a
      // reflective helper named with an admitted class
      String getClass = run(XUTIL + ".call(new (Java.type('java.util.ArrayList'))()," +
                               "'java.lang.Object','getClass',null,null)");
      assertFalse(getClass.contains("java.util.ArrayList"), getClass);

      String field = run(XUTIL + ".field('java.util.concurrent.TimeUnit','SECONDS')");
      assertFalse(field.contains("SECONDS"), field);
      assertEquals("OK:undefined", run("typeof inetsoft.uql.util.XUtil.field"));
   }

   @ParameterizedTest
   @ValueSource(strings = { "base", "report" })
   void helpersRefuseFilteredClassesInBothEngines(String kind) throws Exception {
      open(kind);

      assertEquals("OK:null", run(STYLE + ".createTableStyle('" + StyleSentinel.class.getName() + "')"));
      assertFalse(styleInitialized || styleConstructed, "createTableStyle ran a non-TableStyle class");

      // Bug #78064: the codec registry is no longer script API
      String registry = run(REGISTRY + ".getInstanceForName('" + InstanceSentinel.class.getName() + "')");
      assertTrue(registry.startsWith("ERR:"), registry);
      assertFalse(instanceInitialized || instanceConstructed,
                  "getInstanceForName ran a filtered class");

      String expr = EvalSentinel.class.getName() + ".VALUE";
      assertEquals("OK:" + expr, run(UTILS + ".eval('" + expr + "')"));
      assertFalse(evalInitialized, "mxUtils.eval initialized a filtered class");

      assertEquals("OK:undefined", run("typeof Java.type('inetsoft.uql.util.Drivers').getInstance"));
      assertEquals("OK:undefined", run("typeof Java.type('inetsoft.uql.util.Config').getConfig"));
   }

   /**
    * The tabular view helpers reflectively invoke the method a view names on the bean
    * they are given. From script, neither may be chosen: a view naming a method on a
    * non-tabular object must not get that method invoked.
    */
   @ParameterizedTest
   @ValueSource(strings = { "base", "report" })
   void tabularViewHelpersDoNotInvokeScriptChosenMethods(String kind) throws Exception {
      open(kind);

      String setup =
         "var TV=Java.type('inetsoft.uql.tabular.TabularView');" +
         "var TB=Java.type('inetsoft.uql.tabular.TabularButton');" +
         "var BT=Java.type('inetsoft.uql.tabular.ButtonType');" +
         "var list=new (Java.type('java.util.ArrayList'))();list.add('x');" +
         "var root=new TV();var v=new TV();";
      // a URL button's method is invoked on every refresh
      String button = setup + "var b=new TB();b.setType(BT.URL);b.setMethod('clear');" +
         "v.setButton(b);root.addTabularView(v);" +
         "try{" + TABULAR + ".refreshView(root,list);}catch(e){}";
      assertEquals("OK:1", run("(function(){" + button + "return list.size();})()"));

      // a view's visible method is invoked by callViewMethods
      String visible = setup + "v.setVisibleMethod('clear');" +
         "try{" + TABULAR + ".callViewMethods(root.getViews(),list);}catch(e){}";
      assertEquals("OK:1", run("(function(){" + visible + "return list.size();})()"));

      for(String name : new String[] { "refreshView", "callViewMethods", "callButtonMethods",
                                       "callEditorMethods", "setOAuthTokens", "createQuery",
                                       "getOAuthParameters", "findProperties", "setSessionId" })
      {
         assertEquals("OK:undefined", run("typeof " + TABULAR + "." + name), name);
      }
   }

   /**
    * Java callers (the data source and tabular query editors) are unaffected: the
    * restriction is on script access only.
    */
   @Test
   void tabularViewHelpersStillServeJavaCallers() {
      java.util.List<String> list = new ArrayList<>(java.util.List.of("x"));
      inetsoft.uql.tabular.TabularView root = new inetsoft.uql.tabular.TabularView();
      inetsoft.uql.tabular.TabularView view = new inetsoft.uql.tabular.TabularView();
      view.setVisibleMethod("clear");
      root.addTabularView(view);
      inetsoft.uql.tabular.TabularUtil.callViewMethods(root.getViews(), list);
      assertTrue(list.isEmpty(), "Java-side callViewMethods no longer invokes the view method");
   }

   /**
    * JDBCHandler's public statics load driver classes by name through the plugin
    * loaders and hand out live drivers and connections; none is script API.
    */
   @ParameterizedTest
   @ValueSource(strings = { "base", "report" })
   void jdbcHandlerIsNotScriptVisible(String kind) throws Exception {
      open(kind);
      String handler = "Java.type('inetsoft.uql.jdbc.JDBCHandler')";

      for(String name : new String[] { "getDriver", "isDriverAvailable", "getDrivers",
                                       "setDriverClassName", "connect", "connect0",
                                       "getJDBCConnection", "getConnectionPool" })
      {
         assertEquals("OK:undefined", run("typeof " + handler + "." + name), name);
      }
   }

   /**
    * Bug #78064: the mxgraph codec constructs classes by name and reads and writes
    * object state by Java reflection, so it is refused to scripts. Its Java callers
    * still resolve the same names, and mxUtils.eval stays script API.
    */
   @ParameterizedTest
   @ValueSource(strings = { "base", "report" })
   void mxgraphCodecIsRefusedToScripts(String kind) throws Exception {
      open(kind);

      for(String name : new String[] { "mxCodec", "mxCodecRegistry", "mxObjectCodec",
                                       "mxCellCodec", "mxGdCodec" })
      {
         String result = run("Java.type('inetsoft.graph.mxgraph.io." + name + "')");
         assertTrue(result.startsWith("ERR:"), name + ": " + result);
      }

      for(String name : new String[] { "mxCell", "mxGraphModel", "mxGeometry", "mxPoint",
                                       "mxRectangle", "mxStylesheet", "mxChildChange", "ArrayList" })
      {
         assertNotNull(inetsoft.graph.mxgraph.io.mxCodecRegistry.getCodec(name), name);
      }

      assertEquals("OK:true", run(UTILS + ".eval('mxEdgeStyle.ElbowConnector') === " +
                                     "Java.type('inetsoft.graph.mxgraph.view.mxEdgeStyle').ElbowConnector"));
   }

   @Test
   void everyPublicStaticXUtilMemberStaysScriptVisible() throws Exception {
      open("report");
      Set<String> names = new TreeSet<>();

      for(Method method : XUtil.class.getMethods()) {
         if(Modifier.isStatic(method.getModifiers()) && method.getDeclaringClass() == XUtil.class) {
            names.add(method.getName());
         }
      }

      assertFalse(names.isEmpty());
      List<String> hidden = new ArrayList<>();

      for(String name : names) {
         if(!"OK:function".equals(run("typeof " + XUTIL + "['" + name + "']"))) {
            hidden.add(name);
         }
      }

      assertEquals(List.of(), hidden, "XUtil script API members no longer visible");
   }

   @Test
   void everyBuiltInTableStyleCanStillBeCreatedByName() throws Exception {
      open("report");
      File dir = new File(TableStyle.class.getResource("TableStyle.class").toURI()).getParentFile();
      List<String> failed = new ArrayList<>();
      int count = 0;

      for(File file : Objects.requireNonNull(dir.listFiles())) {
         String fileName = file.getName();

         if(!fileName.endsWith(".class") || fileName.contains("$")) {
            continue;
         }

         String name = "inetsoft.report.style." + fileName.substring(0, fileName.length() - 6);
         Class<?> cls = Class.forName(name, false, getClass().getClassLoader());

         if(!TableStyle.class.isAssignableFrom(cls) || Modifier.isAbstract(cls.getModifiers()) ||
            !Modifier.isPublic(cls.getModifiers()) ||
            Arrays.stream(cls.getConstructors()).noneMatch(c -> c.getParameterCount() == 0))
         {
            continue;
         }

         count++;

         if(TableStyle.createTableStyle(name) == null ||
            !"OK:true".equals(run(STYLE + ".createTableStyle('" + name + "') != null")))
         {
            failed.add(name);
         }
      }

      assertTrue(count > 50, "expected the built-in table styles, found " + count);
      assertEquals(List.of(), failed);
   }

   @Test
   void mxgraphCodecStillRoundTripsAModel() throws Exception {
      mxGraphModel model = new mxGraphModel();
      Object parent = ((mxICell) model.getRoot()).getChildAt(0);
      mxCell vertex = new mxCell("v1", new mxGeometry(10, 20, 30, 40), "shape=rectangle");
      vertex.setVertex(true);
      model.add(parent, vertex, 0);

      String xml = mxXmlUtils.getXml(new mxCodec().encode(model));
      org.w3c.dom.Document doc = mxXmlUtils.parseXml(xml);
      mxGraphModel decoded = (mxGraphModel) new mxCodec(doc).decode(doc.getDocumentElement());
      mxCell cell = (mxCell) ((mxICell) ((mxICell) decoded.getRoot()).getChildAt(0)).getChildAt(0);

      assertEquals("v1", cell.getValue());
      assertEquals("shape=rectangle", cell.getStyle());
      assertEquals(10, cell.getGeometry().getX());
      assertEquals(30, cell.getGeometry().getWidth());
   }

   static volatile boolean callInitialized;
   static volatile boolean callInvoked;
   static volatile boolean styleInitialized;
   static volatile boolean styleConstructed;
   static volatile boolean classInitialized;
   static volatile boolean instanceInitialized;
   static volatile boolean instanceConstructed;
   static volatile boolean evalInitialized;

   // The sentinels live in inetsoft.util.script.graal, which the class filter blocks.
   // A class initializer runs at most once per JVM, so a sentinel reached by the
   // first engine would hide a failure in the second one only if the first had
   // already failed.

   public static class CallSentinel {
      static {
         callInitialized = true;
      }

      public static String hit() {
         callInvoked = true;
         return "hit";
      }
   }

   public static class StyleSentinel {
      static {
         styleInitialized = true;
      }

      public StyleSentinel() {
         styleConstructed = true;
      }
   }

   public static class ClassSentinel {
      static {
         classInitialized = true;
      }
   }

   public static class InstanceSentinel {
      static {
         instanceInitialized = true;
      }

      public InstanceSentinel() {
         instanceConstructed = true;
      }
   }

   public static class EvalSentinel {
      public static final Object VALUE = new Object();

      static {
         evalInitialized = true;
      }
   }
}
