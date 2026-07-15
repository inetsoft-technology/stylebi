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
 * Verifies that the base engine installs the built-in global script functions
 * and constant objects (Feature #75423). Without these, every formula/expression
 * script fails with ReferenceError: isNull/dateAdd/datePart/formatDate not defined.
 */
@Tag("core")
class GraalJavaScriptEngineGlobalsTest {
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

   private Object eval(String src) throws Exception {
      Object s = engine.compile(src);
      return engine.exec(s, null, null);
   }

   @Test
   void isNullGlobal() throws Exception {
      assertEquals(Boolean.TRUE, eval("isNull(null)"));
      assertEquals(Boolean.FALSE, eval("isNull('x')"));
   }

   @Test
   void dateAddGlobalReturnsDate() throws Exception {
      Object result = eval("dateAdd('m', -12, new Date(2020,8,1))");
      assertNotNull(result);
   }

   @Test
   void datePartGlobalReturnsNumber() throws Exception {
      Object result = eval("datePart('m', new Date(2020,8,1), false)");
      assertInstanceOf(Double.class, result);
   }

   @Test
   void formatDateGlobalReturnsString() throws Exception {
      Object result = eval("formatDate(new Date(0), 'yyyy')");
      assertInstanceOf(String.class, result);
   }

   @Test
   void calcQualifiedAndUnqualified() throws Exception {
      // CALC object defined and a qualified call works
      assertEquals(5.0, eval("CALC.abs(-5)"));
      // unqualified CALC functions are also callable
      assertEquals(5.0, eval("abs(-5)"));
   }

   @Test
   void constantObjectResolves() throws Exception {
      // StyleConstants.PORTRAIT == 1 (a real public static final int)
      Object result = eval("StyleConstant.PORTRAIT");
      assertInstanceOf(Double.class, result);
      assertEquals(1.0, result);
   }

   // Bug: chart scripts construct these aesthetic classes, e.g.
   // elem.setLineFrame(new StaticLineFrame(new GLine(3))). GLine/GTexture/SVGShape
   // are Java classes with both public constructors and public static final
   // constants; Rhino exposed them as NativeJavaClass (both new and constants).
   @Test
   void gLineIsConstructable() throws Exception {
      Object result = eval("new GLine(3)");
      assertInstanceOf(inetsoft.graph.aesthetic.GLine.class, result);
   }

   @Test
   void gLineConstantStillResolves() throws Exception {
      Object result = eval("GLine.THIN_LINE");
      assertInstanceOf(inetsoft.graph.aesthetic.GLine.class, result);
   }

   // Bug #75682: GraphElement (abstract base of the element classes) holds the
   // HINT_* constants used by elem.setHint(...); it must resolve as a bare global.
   @Test
   void graphElementHintConstantResolves() throws Exception {
      assertEquals("shine", eval("GraphElement.HINT_SHINE"));
      assertEquals("alpha", eval("GraphElement.HINT_ALPHA"));
   }

   @Test
   void gTextureIsConstructable() throws Exception {
      Object result = eval("new GTexture()");
      assertInstanceOf(inetsoft.graph.aesthetic.GTexture.class, result);
   }

   @Test
   void svgShapeIsConstructable() throws Exception {
      Object result = eval("new SVGShape()");
      assertInstanceOf(inetsoft.graph.aesthetic.SVGShape.class, result);
   }
}
