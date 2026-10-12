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

import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * A {@link JavaClassProxy} construction GraalVM refuses (a HostAccess type deny,
 * an abstract class, a class with no public constructor) must reach the script
 * as an error its own {@code try/catch} can catch, naming the class. Before the
 * fix the proxy let {@code Value.newInstance}'s UnsupportedOperationException
 * escape, which under {@code -ea} (surefire's default) became an internal
 * AssertionError thrown out of {@code exec}, past the script's catch. (#77919)
 */
@Tag("core")
class JavaClassProxyRefusedConstructionTest {
   private static final String DENIED = "inetsoft.uql.asset.sync.RenameTransformQueue";
   private static final String REFUSED = "Message not supported";

   private GraalJavaScriptEngine engine;

   @BeforeEach
   void setup() throws Exception {
      engine = new GraalJavaScriptEngine();
      engine.init(new java.util.HashMap<>());
   }

   @AfterEach
   void teardown() {
      engine.close();
   }

   /** Runs {@code expr} inside a script try/catch and returns what the catch saw. */
   private String caught(String expr) throws Exception {
      String src = "var r; try { " + expr + "; r = 'not refused'; } " +
         "catch(e) { r = 'caught: ' + e.message; } r";
      return String.valueOf(engine.exec(engine.compile(src), null, null));
   }

   private void assertRefused(String fqcn, String expr) throws Exception {
      String result = caught(expr);
      assertTrue(result.startsWith("caught: "), () -> expr + " -> " + result);
      assertTrue(result.contains(fqcn), () -> expr + " -> " + result);
      assertTrue(result.contains(REFUSED), () -> expr + " -> " + result);
   }

   @Test
   void newOnDeniedTypeIsCatchable() throws Exception {
      assertRefused(DENIED, "new " + DENIED + "()");
   }

   @Test
   void noNewCallOnDeniedTypeIsCatchable() throws Exception {
      assertRefused(DENIED, DENIED + "()");
   }

   @Test
   void importClassNewOnDeniedTypeIsCatchable() throws Exception {
      assertRefused(DENIED, "importClass(" + DENIED + "); new RenameTransformQueue()");
   }

   @Test
   void importClassNoNewCallOnDeniedTypeIsCatchable() throws Exception {
      assertRefused(DENIED, "importClass(" + DENIED + "); RenameTransformQueue()");
   }

   @Test
   void importPackageNewOnDeniedTypeIsCatchable() throws Exception {
      assertRefused(DENIED, "importPackage(inetsoft.uql.asset.sync); new RenameTransformQueue()");
   }

   /** Not denied, but no public constructor: the refusal is not specific to type denies. */
   @Test
   void newOnPrivateConstructorTypeIsCatchable() throws Exception {
      assertRefused("java.lang.Math", "new java.lang.Math()");
   }

   /** The engine-global class proxies from putClassProxy; GShape is abstract. */
   @Test
   void newOnEngineGlobalAbstractClassIsCatchable() throws Exception {
      assertRefused("inetsoft.graph.aesthetic.GShape", "new GShape()");
   }

   /** Constructions GraalVM accepts are unchanged, including the #75807 retry. */
   @Test
   void allowedConstructionStillWorks() throws Exception {
      Object red = engine.exec(engine.compile(
         "java.awt.Color('0x' + 'ff0000').getRed()"), null, null);
      assertEquals(255.0, ((Number) red).doubleValue());
      assertEquals("not refused", caught("new java.lang.StringBuilder('x')"));
   }
}
