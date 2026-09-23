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

import inetsoft.graph.EGraph;
import inetsoft.graph.Plotter;
import inetsoft.graph.aesthetic.StackTextFrame;
import inetsoft.graph.data.DefaultDataSet;
import inetsoft.report.composition.execution.ViewsheetSandbox;
import inetsoft.report.script.viewsheet.ChartVSAScriptable;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import inetsoft.uql.viewsheet.ChartVSAssembly;
import inetsoft.uql.viewsheet.Viewsheet;
import org.graalvm.polyglot.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Checks every guest-to-Java hand-off of a script-built (and so
 * {@link HostBeanProxy}-wrapped) graph object whose receiver is not itself a
 * {@code HostBeanProxy}: static and instance methods, varargs, arrays, the
 * numeric-string constructor retry, and overloads. All objects are constructed
 * by the script through the real {@link LegacyJavaShim}/{@link JavaClassProxy}
 * path. (#76969)
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class HostBeanProxyArgumentTest {
   private static final String SINK = "inetsoft.graph.script.GraphArgumentSink";
   private static final String SETUP =
      "var graph = new inetsoft.graph.EGraph();" +
      "var elem = new inetsoft.graph.element.IntervalElement('State', 'Quantity');" +
      "var elem2 = new inetsoft.graph.element.LineElement('State', 'Quantity');";
   private Context ctx;

   @BeforeEach
   void setup() {
      ctx = Context.newBuilder("js")
         .allowHostAccess(ScriptHostAccess.hostAccess())
         .allowHostClassLookup(ScriptHostAccess.classFilter())
         .allowIO(false)
         .allowCreateThread(false)
         .allowNativeAccess(false)
         .allowCreateProcess(false)
         .allowEnvironmentAccess(EnvironmentAccess.NONE)
         .build();
      LegacyJavaShim.install(ctx, ctx.getBindings("js"), ScriptHostAccess.classFilter());
   }

   @AfterEach
   void teardown() {
      ctx.close();
   }

   @Test
   void staticMethodTakingGraphElement() {
      assertEquals("IntervalElement", eval(SINK + ".element(elem)"));
      assertEquals("LineElement",
                   eval("graph.addElement(elem2);" + SINK + ".element(graph.getElement(0))"));
   }

   @Test
   void staticMethodTakingEGraph() {
      assertEquals("EGraph:1", eval("graph.addElement(elem);" + SINK + ".graph(graph)"));
   }

   @Test
   void instanceMethodOfPlainHostObject() {
      assertEquals("IntervalElement", eval("new " + SINK + "().instanceElement(elem)"));
   }

   @Test
   void varargsAndJsArray() {
      assertEquals("IntervalElement,LineElement,", eval(SINK + ".varargs(elem, elem2)"));
      assertEquals("IntervalElement,LineElement,", eval(SINK + ".array([elem, elem2])"));
   }

   @Test
   void numericStringRetryKeepsWrappedArgumentConvertible() {
      // '5' for an int parameter goes through JavaClassProxy's #75807 retry
      assertEquals("IntervalElement:5", eval("new " + SINK + "(elem, '5').getDesc()"));
      assertEquals("IntervalElement:5", eval("new " + SINK + "(elem, 5).getDesc()"));
   }

   @Test
   void overloadPrefersGraphElementOverObject() {
      // Rhino picked the GraphElement overload; before #76969 GraalJS picked Object
      assertEquals("GraphElement:IntervalElement", eval(SINK + ".overload(elem)"));
      // a wrapped EGraph must never be offered to the GraphElement overload
      assertEquals("Object", eval(SINK + ".overload(graph)"));
      assertEquals("Object", eval(SINK + ".overload('x')"));
   }

   @Test
   void wrappedGraphIsNotConvertedToGraphElement() {
      assertThrows(PolyglotException.class, () -> eval(SINK + ".element(graph)"));
   }

   @Test
   void reportedScriptGraphReachesChartAndPlots() {
      // the reported script, with the engine's simple-name class proxies, handed
      // to the chart through `chart.graph = graph` (ScopeProxy -> toHost ->
      // ChartVSAScriptable.putMember) and then plotted
      Viewsheet vs = new Viewsheet();
      vs.getVSAssemblyInfo().setName("vs-chart-1");
      ChartVSAssembly assembly = new ChartVSAssembly();
      assembly.getVSAssemblyInfo().setName("chart1");
      vs.addAssembly(assembly);
      ViewsheetSandbox box = mock(ViewsheetSandbox.class);
      when(box.getID()).thenReturn("vs-chart-1");
      when(box.getViewsheet()).thenReturn(vs);
      ChartVSAScriptable chart = new ChartVSAScriptable(box);
      chart.setAssembly("chart1");

      Value bindings = ctx.getBindings("js");
      installClassProxy(bindings, "EGraph", "inetsoft.graph.EGraph");
      installClassProxy(bindings, "IntervalElement", "inetsoft.graph.element.IntervalElement");
      installClassProxy(bindings, "StackTextFrame", "inetsoft.graph.aesthetic.StackTextFrame");
      bindings.putMember("chart", new ScopeProxy(chart));

      ctx.eval("js",
               "var graph = new EGraph();" +
               "var elem = new IntervalElement('State', 'Quantity');" +
               "elem.setTextFrame(new StackTextFrame(elem, 'Quantity'));" +
               "graph.addElement(elem);" +
               "chart.graph = graph;");

      Object value = chart.getMember("graph");
      assertTrue(value instanceof EGraph, "expected the host EGraph, got " + value);
      EGraph graph = (EGraph) value;
      StackTextFrame frame = (StackTextFrame) graph.getElement(0).getTextFrame();

      DefaultDataSet data = new DefaultDataSet(new Object[][] {
         { "State", "Quantity" }, { "NJ", 200 }, { "NJ", 100 }, { "NY", 300 } });
      Plotter.getPlotter(graph).plot(data).layout(0, 0, 400, 300);

      // the stack label sits on the top row of each State group
      assertNull(frame.getText(data, "Quantity", 0));
      assertEquals(300.0, ((Number) frame.getText(data, "Quantity", 1)).doubleValue());
      assertEquals(300.0, ((Number) frame.getText(data, "Quantity", 2)).doubleValue());
   }

   private String eval(String script) {
      return ctx.eval("js", SETUP + script).asString();
   }

   // same as GraalJavaScriptEngine.putClassProxy
   private static void installClassProxy(Value bindings, String name, String className) {
      Value hostType = bindings.getMember("Java").getMember("type").execute(className);
      bindings.putMember(name, new JavaClassProxy(hostType, className));
   }
}
