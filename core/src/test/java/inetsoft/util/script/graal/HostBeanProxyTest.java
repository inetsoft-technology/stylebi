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
