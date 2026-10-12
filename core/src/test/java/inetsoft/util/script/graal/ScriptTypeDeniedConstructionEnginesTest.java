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
import inetsoft.graph.mxgraph.io.mxCodecRegistry;
import inetsoft.graph.mxgraph.util.mxXmlUtils;
import inetsoft.report.script.graal.ReportGraalJavaScriptEngine;
import inetsoft.test.*;
import inetsoft.util.script.JavaScriptEngine;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.HashMap;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #78064: the helpers that turn a script-supplied class name into an instance
 * (the newInstance global, the mxgraph codec registry and codec, and mxUtils.eval
 * for statics) must refuse a class the script host access denies by type, as
 * {@code new (Java.type(name))()} does. Runs through both production engines. The
 * refused objects are never used beyond a typeof or null check.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class ScriptTypeDeniedConstructionEnginesTest {
   // denied directly, through an interface (ConnectionPoolFactory), through a
   // superclass (XDataSource), as a Principal, and by exact type
   private static final String[] DENIED = {
      "inetsoft.uql.viewsheet.BookmarkLockManager",
      "inetsoft.uql.jdbc.DefaultConnectionPoolFactory",
      "inetsoft.uql.jdbc.JDBCDataSource",
      "inetsoft.sree.security.DestinationUserNameProviderPrincipal",
      "java.lang.Object"
   };
   private static final String UTILS = "Java.type('inetsoft.graph.mxgraph.util.mxUtils')";

   private GraalJavaScriptEngine engine;

   private void open(String kind) throws Exception {
      engine = "report".equals(kind) ? new ReportGraalJavaScriptEngine() : new GraalJavaScriptEngine();
      engine.init(new HashMap<>());
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
   void newInstanceRefusesWhatJavaTypeRefuses(String kind) throws Exception {
      open(kind);

      for(String name : DENIED) {
         String type = run("typeof new (Java.type('" + name + "'))()");
         assertTrue(type.startsWith("ERR:"), name + " Java.type+new: " + type);

         // a refused call throws, or the report engine's error handling gives null
         String created = run("newInstance('" + name + "') == null");
         assertTrue(created.startsWith("ERR:") || created.equals("OK:true"),
                    name + " newInstance: " + created);
         assertThrows(SecurityException.class, () -> JavaScriptEngine.newInstance(name), name);
      }
   }

   @ParameterizedTest
   @ValueSource(strings = { "base", "report" })
   void newInstanceStillCreatesAdmittedClasses(String kind) throws Exception {
      open(kind);

      assertEquals("OK:1", run("(function(){var l=newInstance('java.util.ArrayList');" +
                                  "l.add('a');return l.size();})()"));
      assertEquals("OK:true", run("newInstance('inetsoft.uql.XFormatInfo') != null"));
      assertEquals("OK:true", run("newInstance('inetsoft.graph.EGraph') != null"));
      assertEquals("OK:true", run("newInstance('inetsoft.graph.mxgraph.model.mxCell') != null"));
   }

   @ParameterizedTest
   @ValueSource(strings = { "base", "report" })
   void codecIsNotReachableFromScript(String kind) throws Exception {
      open(kind);
      String registry = "Java.type('inetsoft.graph.mxgraph.io.mxCodecRegistry')";

      for(String name : DENIED) {
         String instance = run("typeof " + registry + ".getInstanceForName('" + name + "')");
         assertTrue(instance.startsWith("ERR:"), name + " getInstanceForName: " + instance);
         String template = run("typeof " + registry + ".getCodec('" + name + "').getTemplate()");
         assertTrue(template.startsWith("ERR:"), name + " getCodec: " + template);
      }

      String decode = run("typeof new (Java.type('inetsoft.graph.mxgraph.io.mxCodec'))()");
      assertTrue(decode.startsWith("ERR:"), decode);
   }

   /**
    * The construction is refused in the codec itself too, which still serves Java
    * callers, so a name or an XML node never makes it build a denied class.
    */
   @Test
   void codecRefusesDeniedClassesForJavaCallers() {
      for(String name : DENIED) {
         assertNull(mxCodecRegistry.getClassForName(name), name);
         assertNull(mxCodecRegistry.getInstanceForName(name), name);
         assertNull(mxCodecRegistry.getCodec(name), name);
      }

      // java.lang.Object through the package-prefixed retry
      assertNull(mxCodecRegistry.getInstanceForName("Object"));

      org.w3c.dom.Document doc = mxXmlUtils.parseXml(
         "<inetsoft.uql.jdbc.JDBCDataSource name=\"probe78064\"/>");
      // with no codec for the name, decode returns a copy of the XML element
      Object decoded = new mxCodec(doc).decode(doc.getDocumentElement());
      assertFalse(decoded instanceof inetsoft.uql.XDataSource, String.valueOf(decoded));
      assertTrue(decoded instanceof org.w3c.dom.Element, String.valueOf(decoded));

      assertSame(java.util.ArrayList.class, mxCodecRegistry.getClassForName("ArrayList"));
      assertNotNull(mxCodecRegistry.getCodec("mxCell"));
   }

   @ParameterizedTest
   @ValueSource(strings = { "base", "report" })
   void mxUtilsEvalRefusesStaticsOfDeniedTypes(String kind) throws Exception {
      open(kind);

      // declared on a denied class; Graal refuses the same static
      assertEquals("OK:undefined", run("typeof Java.type('inetsoft.uql.XDataSource').JDBC"));
      assertEquals("OK:inetsoft.uql.XDataSource.JDBC",
                   run(UTILS + ".eval('inetsoft.uql.XDataSource.JDBC')"));

      // inherited by an admitted class from an exact-type deny (StyleConstants)
      assertEquals("OK:undefined", run("typeof Java.type('inetsoft.report.internal.Util').H_LEFT"));
      assertEquals("OK:inetsoft.report.internal.Util.H_LEFT",
                   run(UTILS + ".eval('inetsoft.report.internal.Util.H_LEFT')"));

      assertEquals("OK:true", run(UTILS + ".eval('mxEdgeStyle.ElbowConnector') === " +
                                     "Java.type('inetsoft.graph.mxgraph.view.mxEdgeStyle').ElbowConnector"));
   }

   @Test
   void scriptVisibleLoadRefusesDeniedTypes() throws Exception {
      ClassLoader loader = getClass().getClassLoader();

      for(String name : DENIED) {
         assertThrows(SecurityException.class,
                      () -> ScriptHostAccess.loadScriptVisibleClass(name, loader), name);
      }

      assertSame(java.util.ArrayList.class,
                 ScriptHostAccess.loadScriptVisibleClass("java.util.ArrayList", loader));
      assertSame(inetsoft.uql.XFormatInfo.class,
                 ScriptHostAccess.loadScriptVisibleClass("inetsoft.uql.XFormatInfo", loader));
   }
}
