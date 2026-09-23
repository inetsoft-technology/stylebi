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
import inetsoft.graph.aesthetic.StackTextFrame;
import inetsoft.graph.aesthetic.TextFrame;
import inetsoft.graph.element.GraphElement;
import inetsoft.graph.element.IntervalElement;
import inetsoft.graph.element.LineElement;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.EnvironmentAccess;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifies that {@link HostBeanProxy} restores Rhino-style JavaBeans property
 * access for the graph objects chart scripts manipulate directly, so that
 * {@code element.endArrow = true} reaches {@code LineElement.setEndArrow(true)}
 * under GraalJS. (#75577)
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class HostBeanProxyTest {
   private Context ctx;

   @BeforeEach
   void setup() {
      // mirror the engine's Context configuration so the test exercises the real
      // HostAccess policy (public access on, bean properties NOT auto-exposed).
      ctx = Context.newBuilder("js")
         .allowHostAccess(ScriptHostAccess.hostAccess())
         .allowHostClassLookup(ScriptHostAccess.classFilter())
         .allowIO(false)
         .allowCreateThread(false)
         .allowNativeAccess(false)
         .allowCreateProcess(false)
         .allowEnvironmentAccess(EnvironmentAccess.NONE)
         .build();
   }

   @AfterEach
   void teardown() {
      ctx.close();
   }

   @Test
   void beanWriteReachesSetterOnScriptConstructedGraph() {
      // (#76915) `graph`/`elem` here are never handed to script by Java code --
      // the script builds them itself via `new EGraph()`/`new LineElement(...)`,
      // the exact idiom from the reported chart binding script and from
      // datatest/vsscript's Egraph_Spec.groovy examples and the built-in
      // createBulletGraph Script Library sample. This exercises the real
      // dispatch path a chart script actually takes (LegacyJavaShim's package-root
      // navigation -> JavaClassProxy -> GraalVM's native `new`), not a
      // hand-wrapped object, so it fails if wrapping happens only on
      // handoff (ScriptValueConverter.toGuest) and not on construction.
      LegacyJavaShim.install(ctx, ctx.getBindings("js"), ScriptHostAccess.classFilter());

      ctx.eval("js",
               "var graph = new inetsoft.graph.EGraph();" +
               "var elem = new inetsoft.graph.element.LineElement('State', 'Quantity');" +
               "elem.endArrow = true;" +
               "graph.addElement(elem);" +
               "globalThis.__graph = graph;");

      Object hostGraph = ScriptValueConverter.toHost(ctx.getBindings("js").getMember("__graph"));
      assertTrue(hostGraph instanceof EGraph, "expected the unwrapped host EGraph, got " + hostGraph);
      EGraph graph = (EGraph) hostGraph;
      assertEquals(1, graph.getElementCount());
      assertTrue(((LineElement) graph.getElement(0)).isEndArrow(),
                 "bean-style `elem.endArrow = true` on a script-constructed LineElement " +
                 "must reach setEndArrow(true), same as one handed to script by the platform");
   }

   @Test
   void scriptConstructedElementPassesToGraphElementConstructor() {
      // (#76969) the reported chart script: since #76915 a script-built `elem` is a
      // HostBeanProxy, and a host constructor is not a HostBeanProxy receiver, so
      // nothing unwrapped it -- StackTextFrame(GraphElement, String...) failed with
      // "Invalid argument when instantiating ... [HostBeanProxy, TruffleString]".
      LegacyJavaShim.install(ctx, ctx.getBindings("js"), ScriptHostAccess.classFilter());

      ctx.eval("js",
               "var graph = new inetsoft.graph.EGraph();" +
               "var elem = new inetsoft.graph.element.IntervalElement('State', 'Quantity');" +
               "elem.setTextFrame(new inetsoft.graph.aesthetic.StackTextFrame(elem, 'Quantity'));" +
               "graph.addElement(elem);" +
               "globalThis.__graph = graph;");

      EGraph graph = (EGraph) ScriptValueConverter.toHost(
         ctx.getBindings("js").getMember("__graph"));
      assertEquals(1, graph.getElementCount());
      assertStackTextFrameOn(graph.getElement(0));
   }

   @Test
   void scriptConstructedElementPassesToJavaTypeConstructor() {
      // (#76969) Java.type construction bypasses JavaClassProxy entirely, so the
      // unwrapping must happen in GraalJS's own argument conversion.
      LegacyJavaShim.install(ctx, ctx.getBindings("js"), ScriptHostAccess.classFilter());

      ctx.eval("js",
               "var StackTextFrame = Java.type('inetsoft.graph.aesthetic.StackTextFrame');" +
               "var elem = new inetsoft.graph.element.IntervalElement('State', 'Quantity');" +
               "elem.setTextFrame(new StackTextFrame(elem, 'Quantity'));" +
               "globalThis.__elem = elem;");

      assertStackTextFrameOn(
         (GraphElement) ScriptValueConverter.toHost(ctx.getBindings("js").getMember("__elem")));
   }

   @Test
   void methodReturnedElementPassesToGraphElementConstructor() {
      // (#76969) an element returned by a wrapped graph method is wrapped too
      // (HostBeanProxy.wrapResult); this has failed since #75577.
      LegacyJavaShim.install(ctx, ctx.getBindings("js"), ScriptHostAccess.classFilter());

      ctx.eval("js",
               "var graph = new inetsoft.graph.EGraph();" +
               "graph.addElement(new inetsoft.graph.element.IntervalElement('State', 'Quantity'));" +
               "graph.getElement(0).setTextFrame(" +
               "   new inetsoft.graph.aesthetic.StackTextFrame(graph.getElement(0), 'Quantity'));" +
               "globalThis.__graph = graph;");

      EGraph graph = (EGraph) ScriptValueConverter.toHost(
         ctx.getBindings("js").getMember("__graph"));
      assertStackTextFrameOn(graph.getElement(0));
   }

   @Test
   void elementRoundTripsThroughJavaCollectionAsSameWrapper() {
      // (#76969 regression guard) the GraphElement/EGraph unwrap mappings must not
      // extend to Object: an Object parameter has to keep receiving GraalJS's view of
      // the wrapper so it comes back as the same wrapper. Unwrapping there would store
      // the raw element, which ArrayList.get returns unwrapped, so === fails and a
      // bean write becomes the silent no-op #76915 fixed.
      LegacyJavaShim.install(ctx, ctx.getBindings("js"), ScriptHostAccess.classFilter());

      ctx.eval("js",
               "var elem = new inetsoft.graph.element.LineElement('State', 'Quantity');" +
               "var l = new java.util.ArrayList();" +
               "l.add(elem);" +
               "globalThis.__same = l.get(0) === elem;" +
               "l.get(0).endArrow = true;" +
               "globalThis.__elem = elem;");

      assertTrue(ctx.getBindings("js").getMember("__same").asBoolean(),
                 "an element read back from a Java list must be the same wrapper");
      LineElement elem = (LineElement) ScriptValueConverter.toHost(
         ctx.getBindings("js").getMember("__elem"));
      assertTrue(elem.isEndArrow(),
                 "a bean write on an element read back from a Java list must reach setEndArrow");
   }

   private static void assertStackTextFrameOn(GraphElement elem) {
      assertTrue(elem instanceof IntervalElement, "expected the host IntervalElement, got " + elem);
      TextFrame frame = elem.getTextFrame();
      assertTrue(frame instanceof StackTextFrame, "expected a StackTextFrame, got " + frame);
      // the constructor read the dimensions from the real element
      assertArrayEquals(new String[] { "State" }, ((StackTextFrame) frame).getDimensions());
   }

   @Test
   void shouldWrapOnlyGraphObjects() {
      assertTrue(HostBeanProxy.shouldWrap(new EGraph()));
      assertTrue(HostBeanProxy.shouldWrap(new LineElement("d", "m")));
      assertFalse(HostBeanProxy.shouldWrap("not a graph object"));
      assertFalse(HostBeanProxy.shouldWrap(new Object()));
   }

   @Test
   void beanWriteReachesSetter() {
      EGraph graph = new EGraph();
      graph.addElement(new LineElement("State", "Total 1"));
      graph.addElement(new LineElement("State", "Total 2"));
      ctx.getBindings("js").putMember("graph", HostBeanProxy.wrap(graph));

      // the exact pattern from the reported chart binding script
      ctx.eval("js",
               "for(var i = 0; i < graph.getElementCount(); i++) {" +
               "   graph.getElement(i).endArrow = true;" +
               "}");

      assertTrue(((LineElement) graph.getElement(0)).isEndArrow());
      assertTrue(((LineElement) graph.getElement(1)).isEndArrow());
   }

   /**
    * A bean write goes through {@code Value.invokeMember}, i.e. GraalJS's own
    * interop rather than {@link ScriptFunction}, so it depends on the
    * Rhino-parity numeric target-type mappings in {@link ScriptHostAccess} to
    * narrow a computed non-integral number to the setter's declared primitive.
    * See ScriptHostAccessNumberCoercionTest for the mappings themselves; this
    * pins that the bean path actually reaches them.
    */
   @Test
   void beanWriteNarrowsAFractionalNumberToAnIntSetter() {
      EGraph graph = new EGraph();
      graph.addElement(new LineElement("State", "Total 1"));
      ctx.getBindings("js").putMember("graph", HostBeanProxy.wrap(graph));

      // setStartRow(int) is the only signature, so 5.7 must truncate to 5 as it
      // did under Rhino rather than fail as a lossy primitive coercion
      ctx.eval("js", "graph.getElement(0).startRow = 5.7;");

      assertEquals(5, graph.getElement(0).getStartRow());
   }

   @Test
   void beanWriteSilentlyNoOpsWithoutProxy() {
      // documents the underlying GraalJS behavior the proxy compensates for:
      // a bare host object does not expose the 'endArrow' bean property.
      EGraph graph = new EGraph();
      graph.addElement(new LineElement("State", "Total 1"));
      ctx.getBindings("js").putMember("graph", graph);

      ctx.eval("js", "graph.getElement(0).endArrow = true;");

      assertFalse(((LineElement) graph.getElement(0)).isEndArrow());
   }

   @Test
   void beanReadReflectsGetter() {
      EGraph graph = new EGraph();
      LineElement line = new LineElement("State", "Total 1");
      line.setEndArrow(true);
      graph.addElement(line);
      ctx.getBindings("js").putMember("graph", HostBeanProxy.wrap(graph));

      assertTrue(ctx.eval("js", "graph.getElement(0).endArrow").asBoolean());
      assertFalse(ctx.eval("js", "graph.getElement(0).startArrow").asBoolean());
   }

   @Test
   void objectMethodsAreNotExposedAsBeanProperties() {
      // getClass() must not surface as a "class" property; invoking it is blocked
      // by the HostAccess policy, and generic enumeration (JSON.stringify) must not
      // crash on it. (regression guard, #75577)
      EGraph graph = new EGraph();
      graph.addElement(new LineElement("State", "Total 1"));
      ctx.getBindings("js").putMember("graph", HostBeanProxy.wrap(graph));

      assertTrue(ctx.eval("js", "typeof graph.class === 'undefined'").asBoolean());
      // enumerate + read every advertised member without throwing
      assertDoesNotThrow(() -> ctx.eval(
         "js", "Object.keys(graph).forEach(function(k){ var v = graph[k]; });"));
   }

   @Test
   void wrapperIdentityIsPreservedAcrossAccesses() {
      // repeated access to the same underlying element yields the same proxy so
      // JS reference equality holds. (regression guard, #75577)
      EGraph graph = new EGraph();
      graph.addElement(new LineElement("State", "Total 1"));
      ctx.getBindings("js").putMember("graph", HostBeanProxy.wrap(graph));

      assertTrue(ctx.eval("js", "graph.getElement(0) === graph.getElement(0)").asBoolean());
   }

   @Test
   void methodDelegationAndProxyArgUnwrapping() {
      EGraph graph = new EGraph();
      graph.addElement(new LineElement("State", "Total 1"));
      graph.addElement(new LineElement("State", "Total 2"));
      ctx.getBindings("js").putMember("graph", HostBeanProxy.wrap(graph));

      // addElement receives a wrapped element (from getElement) and must still
      // resolve against the host GraphElement param (proxy arg unwrapped), not
      // fail with the proxy as an incompatible argument.
      ctx.eval("js", "graph.addElement(graph.getElement(0));");

      assertEquals(3, graph.getElementCount());
      assertSame(graph.getElement(0), graph.getElement(2));
   }
}
