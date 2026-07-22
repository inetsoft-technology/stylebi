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

   @Test
   void chartMapTypeConstantResolves() throws Exception {
      // MAP_TYPE_<TYPE> constants (e.g. Chart["MAP_TYPE_U.S."] == "U.S.") are
      // derived dynamically from the installed map data, not from static final
      // fields, so the reflected ConstantScope does not pick them up. The engine
      // must register them explicitly (as Rhino did) for map scripts such as
      // mapType = Chart["MAP_TYPE_U.S."] to work. (Bug #75679)
      String[] types = inetsoft.report.internal.graph.MapData.getMapTypes();
      Assumptions.assumeTrue(types.length > 0, "no map data installed");

      String type = types[0];
      Object result = eval("Chart['MAP_TYPE_" + type.toUpperCase() + "']");
      assertEquals(type, result);
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

   // Bug #75684: chart scripts reference the abstract Scale base class for its
   // scale-option constants, e.g. qscale.setScaleOption(Scale.TICKS). Scale was
   // not registered as a global (only its subclasses were), so the script failed
   // with "ReferenceError: Scale is not defined".
   @Test
   void scaleConstantResolves() throws Exception {
      // Scale.TICKS == 1 (a public static final int on the abstract base class)
      Object result = eval("Scale.TICKS");
      assertInstanceOf(Double.class, result);
      assertEquals(1.0, result);
   }

   // Bug #75685: Rhino resolved unqualified CALC/statistical functions
   // case-insensitively (Calc was the global scope's prototype and Calc.get()
   // lowercases the key). Existing scripts call them in PascalCase, e.g.
   // NthMostFrequent, PthPercentile, Sum. The GraalJS migration only copied the
   // functions into the (case-sensitive) global bindings under their lowercase
   // names, so PascalCase names threw "ReferenceError: X is not defined".
   @Test
   void calcFunctionsResolveCaseInsensitively() throws Exception {
      // exact lowercase resolves and executes (unchanged behavior)
      assertEquals(6.0, eval("sum([1,2,3])"));
      // PascalCase names used by existing scripts now resolve + execute
      assertEquals(6.0, eval("Sum([1,2,3])"));
      assertEquals(2.0, eval("Average([1,2,3])"));
      // formula-backed CALC functions (NthLargest/NthSmallest/NthMostFrequent/
      // PthPercentile) instantiate report Formulas that need the Spring context
      // to execute, which is unavailable in this unit test — but the bug is name
      // resolution, so assert they resolve to a callable regardless of case.
      assertEquals("function", eval("typeof NthLargest"));
      assertEquals("function", eval("typeof NthSmallest"));
      assertEquals("function", eval("typeof NthMostFrequent"));
      assertEquals("function", eval("typeof PthPercentile"));
      assertEquals("function", eval("typeof First"));
   }

   // The case-insensitive last-resort must not shadow JS builtins: Calc has a
   // 'date' function, but the global Date constructor is an own property of the
   // global object, so it must still win over the (prototype) Calc.date.
   @Test
   void builtinDateNotShadowedByCalc() throws Exception {
      assertInstanceOf(java.util.Date.class, eval("new Date(0)"));
   }

   // Bug: Rhino auto-imported the whole inetsoft.graph.element package, so every
   // element type resolved by simple name. The GraalJS class whitelist
   // (installChartClasses) omitted the newer element types, so chart scripts such
   // as elem.setOrientation(TreemapElement.Orientation.TOP_RIGHT) failed with
   // "TreemapElement is not defined".
   @Test
   void treemapElementResolves() throws Exception {
      // exact real-script usage: elem.setOrientation(TreemapElement.Orientation.TOP_RIGHT)
      assertEquals("TOP_RIGHT", eval("String(TreemapElement.Orientation.TOP_RIGHT)"));
   }

   // The element types resolve to class proxies (typeof != 'undefined'). Actually
   // constructing them pulls in inetsoft.graph.internal.GDefaults, whose static
   // init needs a full graphics environment unavailable in this headless test, so
   // only name resolution is asserted here.
   @Test
   void newChartElementTypesResolve() throws Exception {
      assertNotEquals("undefined", eval("typeof TreemapElement"));
      assertNotEquals("undefined", eval("typeof MekkoElement"));
      assertNotEquals("undefined", eval("typeof ParaboxElement"));
      assertNotEquals("undefined", eval("typeof PolygonElement"));
      assertNotEquals("undefined", eval("typeof RelationElement"));
   }

   // Bug #75704: the #75685 case-insensitive CALC last-resort must not hijack a
   // user variable whose name matches a CALC function name (CalcDateTime exposes
   // minDate/maxDate). Previously `var minDate = new Date()` was intercepted by
   // with(__scope__) (hasMember was true via the CALC builtin) and stored as a
   // detached java.util.Date, so the next line `minDate.setFullYear(...)` read it
   // back as a foreign host object and threw "TypeError: not a Date object". The
   // user's `var` must shadow the CALC function and keep its native JS Date
   // identity, exactly as Rhino's own-property-over-prototype semantics did.
   @Test
   void userVarShadowsCalcDateFunction() throws Exception {
      String script =
         "var minDate = new Date();\n" +
         "var maxDate = new Date();\n" +
         "minDate.setFullYear(2005, 8, 1);\n" +
         "maxDate.setFullYear(2011, 10, 1);\n" +
         "'' + minDate.getFullYear() + ',' + maxDate.getFullYear();\n";
      // no "not a Date object" error, and the mutations persist
      assertEquals("2005,2011", eval(script));
   }

   // Bug #75704: a CALC function name used as an unqualified call (never assigned)
   // must still resolve — the shadow only kicks in for names the script assigns.
   @Test
   void calcDateFunctionStillResolvesWhenNotShadowed() throws Exception {
      assertEquals("function", eval("typeof minDate"));
      assertEquals("function", eval("typeof maxDate"));
   }

   // Bug #75704: a CALC-colliding *primitive* assigned to a name that resolves
   // only via the case-insensitive CALC builtin scope (minDate/maxDate are such
   // names -- proven by the two tests above: they route through
   // BindingRootProxy.putMember rather than being real JS globals) must persist
   // on the reused root scope ACROSS separate exec() calls, the way a calc-table
   // running-total accumulator does. The #75704 shadow is per-exec (wiped by
   // swapAssigned) and is now scoped to guest values toHost() cannot round-trip;
   // a primitive falls through to global.putMember, so it survives to the next
   // exec sharing the same root ScriptScope. Before the narrowing, the broad
   // shadow diverted the primitive into the per-exec map, so the second exec read
   // back the CALC function ("function") instead of the accumulated value.
   @Test
   void calcCollidingPrimitivePersistsAcrossExecs() throws Exception {
      MapScope root = new MapScope();
      // first exec: bare assignment (no `var`, so it routes through __scope__ /
      // BindingRootProxy.putMember rather than becoming a wrapper-function local)
      engine.exec(engine.compile("minDate = 5;"), root, null);
      // second exec, same reused root scope: the primitive must still be visible
      Object result = engine.exec(
         engine.compile("'' + (typeof minDate) + ',' + minDate;"), root, null);
      assertEquals("number,5", result);
   }

   /** Minimal reusable, mutable {@link ScriptScope} for the cross-exec test above. */
   private static final class MapScope implements ScriptScope {
      private final java.util.Map<String, Object> members = new java.util.HashMap<>();

      @Override public Object getMember(String name) { return members.get(name); }
      @Override public boolean hasMember(String name) { return members.containsKey(name); }
      @Override public void putMember(String name, Object value) { members.put(name, value); }
      @Override public Object[] getMemberKeys() { return members.keySet().toArray(new String[0]); }
   }
}
