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

import inetsoft.graph.mxgraph.io.mxCodecRegistry;
import inetsoft.report.script.graal.ReportGraalJavaScriptEngine;
import inetsoft.uql.tabular.TabularEditor;
import inetsoft.uql.tabular.TabularUtil;
import inetsoft.test.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #77421: helper methods on script-visible classes must not load, initialize or
 * construct a class by name that the script class filter refuses, and the generic
 * reflective XUtil helpers must not be reachable from scripts. Runs through the report
 * engine used by viewsheet and report scripts, which swallows script errors to null,
 * so the assertions are on side effects.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class ScriptHostAccessHelperClassLoadTest {
   private ReportGraalJavaScriptEngine engine;

   @BeforeEach
   void setup() throws Exception {
      engine = new ReportGraalJavaScriptEngine();
      engine.init(new java.util.HashMap<>());
   }

   @AfterEach
   void teardown() {
      engine.close();
   }

   private Object eval(String src) throws Exception {
      return engine.exec(engine.compile(src), null, null);
   }

   private static String type(String cls) {
      return "Java.type('" + cls + "')";
   }

   @Test
   void createTableStyleRefusesNonTableStyleClass() throws Exception {
      String style = type("inetsoft.report.style.TableStyle");

      assertNull(eval(style + ".createTableStyle('" + StyleSentinel.class.getName() + "')"));
      assertFalse(styleInitialized, "static initializer of a non-TableStyle class ran");
      assertFalse(styleConstructed, "constructor of a non-TableStyle class ran");

      Object name = eval(
         style + ".createTableStyle('inetsoft.report.style.Accounting1').getClass().getName()");
      assertNull(name, "Object.getClass must stay denied");
      Object created = eval(
         style + ".createTableStyle('inetsoft.report.style.Accounting1') != null");
      assertEquals(Boolean.TRUE, created);
   }

   @Test
   void codecRegistryRefusesFilteredClass() throws Exception {
      // Bug #78064: the codec is not script API at all; mxUtils.eval, which is,
      // still resolves classes through getClassForName, so it keeps the name gate
      assertNull(eval(type("inetsoft.graph.mxgraph.io.mxCodecRegistry")));

      assertNull(mxCodecRegistry.getClassForName(ClassSentinel.class.getName()));
      assertFalse(classInitialized, "getClassForName initialized a filtered class");

      assertNull(mxCodecRegistry.getInstanceForName(InstanceSentinel.class.getName()));
      assertFalse(instanceInitialized, "getInstanceForName initialized a filtered class");
      assertFalse(instanceConstructed, "getInstanceForName constructed a filtered class");

      assertNull(mxCodecRegistry.getInstanceForName("java.util.concurrent.ConcurrentHashMap"));
      // the package-prefixed retry is gated too
      assertNull(mxCodecRegistry.getInstanceForName("concurrent.ConcurrentHashMap"));

      // admitted mxgraph classes, by full and by short (package-prefixed) name
      assertNotNull(mxCodecRegistry.getClassForName("inetsoft.graph.mxgraph.view.mxEdgeStyle"));
      assertTrue(mxCodecRegistry.getInstanceForName("mxGeometry") instanceof
                    inetsoft.graph.mxgraph.model.mxGeometry);
   }

   @Test
   void codecRegistryStillServesInternalCallers() {
      assertNotNull(inetsoft.graph.mxgraph.io.mxCodecRegistry.getCodec("mxCell"));
      assertTrue(inetsoft.graph.mxgraph.io.mxCodecRegistry.getInstanceForName("ArrayList")
                    instanceof java.util.ArrayList);
      assertSame(inetsoft.graph.mxgraph.view.mxEdgeStyle.EntityRelation,
                 inetsoft.graph.mxgraph.util.mxUtils.eval("mxEdgeStyle.EntityRelation"));
   }

   @Test
   void mxUtilsEvalRefusesFilteredClass() throws Exception {
      String utils = type("inetsoft.graph.mxgraph.util.mxUtils");
      String expr = EvalSentinel.class.getName() + ".VALUE";

      // an unresolved expression is returned unchanged
      assertEquals(expr, eval(utils + ".eval('" + expr + "')"));
      assertFalse(evalInitialized, "mxUtils.eval initialized a filtered class");
   }

   @Test
   void editorTypeLookupDoesNotInitialize() throws Exception {
      // TabularUtil is not script API
      assertEquals("undefined", eval("typeof " + type("inetsoft.uql.tabular.TabularUtil") +
                                        ".getEditorTypeFromClassName"));

      assertEquals(TabularEditor.Type.TEXT,
                   TabularUtil.getEditorTypeFromClassName(EditorSentinel.class.getName()));
      assertFalse(editorInitialized, "getEditorTypeFromClassName initialized the class");

      // internal callers pass filter-refused types such as java.io.File
      assertEquals(TabularEditor.Type.FILE, TabularUtil.getEditorTypeFromClassName("java.io.File"));
      assertEquals(TabularEditor.Type.INT,
                   TabularUtil.getEditorTypeFromClassName("java.lang.Integer"));
   }

   @Test
   void reflectiveXUtilHelpersAreNotScriptVisible() throws Exception {
      String xutil = type("inetsoft.uql.util.XUtil");

      // "call" on a host class resolves to Function.prototype.call, so check the
      // Java members directly
      assertTrue(java.util.Arrays.stream(inetsoft.uql.util.XUtil.class.getMethods())
                    .noneMatch(m -> m.getName().equals("call") || m.getName().equals("field")),
                 "XUtil still has a public call/field method");
      assertEquals("undefined", eval("typeof " + xutil + ".field"));
      assertNull(eval(xutil + ".call(null, '" + CallSentinel.class.getName() +
                         "', 'hit', null, null)"));
      assertFalse(callInitialized, "XUtil.call initialized a filtered class");
      assertFalse(callInvoked, "XUtil.call invoked a method on a filtered class");
      assertNull(eval(xutil + ".field('java.util.concurrent.TimeUnit', 'SECONDS')"));

      // the rest of XUtil stays script API
      assertEquals("abc", eval(xutil + ".removeQuote('\"abc\"')"));
   }

   @Test
   void driverAndConfigClassLoadingIsNotScriptVisible() throws Exception {
      assertEquals("undefined", eval("typeof " + type("inetsoft.uql.util.Drivers") + ".getInstance"));
      assertEquals("undefined", eval("typeof " + type("inetsoft.uql.util.Config") + ".getConfig"));
      assertEquals("undefined",
                   eval("typeof " + type("inetsoft.uql.jdbc.JDBCHandler") + ".getDriver"));
   }

   @Test
   void scriptVisibleLoadRefusesWithoutInitializing() throws Exception {
      assertThrows(SecurityException.class, () -> ScriptHostAccess.loadScriptVisibleClass(
         LoadSentinel.class.getName(), getClass().getClassLoader()));
      assertFalse(loadInitialized);
      assertSame(java.util.ArrayList.class, ScriptHostAccess.loadScriptVisibleClass(
         "java.util.ArrayList", getClass().getClassLoader()));
   }

   static volatile boolean styleInitialized;
   static volatile boolean styleConstructed;
   static volatile boolean classInitialized;
   static volatile boolean instanceInitialized;
   static volatile boolean instanceConstructed;
   static volatile boolean evalInitialized;
   static volatile boolean editorInitialized;
   static volatile boolean callInitialized;
   static volatile boolean callInvoked;
   static volatile boolean loadInitialized;

   // The sentinels live in inetsoft.util.script.graal, which the class filter blocks.
   // Each helper gets its own sentinel so one helper's class initialization cannot
   // hide another's.

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

   public static class EditorSentinel {
      static {
         editorInitialized = true;
      }
   }

   public static class CallSentinel {
      static {
         callInitialized = true;
      }

      public static String hit() {
         callInvoked = true;
         return "hit";
      }
   }

   public static class LoadSentinel {
      static {
         loadInitialized = true;
      }
   }
}
