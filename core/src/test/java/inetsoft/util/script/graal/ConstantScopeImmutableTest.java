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

import inetsoft.report.Size;
import inetsoft.report.StyleConstants;
import inetsoft.report.composition.region.ChartConstants;
import inetsoft.util.script.ScriptEnv;
import inetsoft.util.script.graal.pool.PoolTestSupport;
import org.junit.jupiter.api.*;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Testing #77123: the constant scopes ({@code Chart}, {@code StyleConstant}) are shared by every
 * script context, so they expose only immutable constant values. Strings and boxed primitives
 * are the same objects as the fields; arrays and {@link Size} are handed out as a copy per read;
 * values that are not constants are not exposed. Checked on the base engine (viewsheets, pool
 * off) and on a pooled worksheet env.
 */
@Tag("core")
class ConstantScopeImmutableTest {
   private GraalJavaScriptEngine engine;
   private int[] texture;
   private int[] lineStyles;
   private int[] trendlines;
   private float a4Width;
   private float a4Height;

   @BeforeEach
   void setup() throws Exception {
      engine = new GraalJavaScriptEngine();
      engine.init(new HashMap<>());
      texture = ChartConstants.TEXTURE_STYLES.clone();
      lineStyles = ChartConstants.M_LINE_STYLES.clone();
      trendlines = ChartConstants.TRENDLINE_TYPES.clone();
      a4Width = StyleConstants.PAPER_A4.width;
      a4Height = StyleConstants.PAPER_A4.height;
   }

   @AfterEach
   void teardown() {
      engine.close();
      // restore the statics in case a write got through, so other tests are not affected
      System.arraycopy(texture, 0, ChartConstants.TEXTURE_STYLES, 0, texture.length);
      System.arraycopy(lineStyles, 0, ChartConstants.M_LINE_STYLES, 0, lineStyles.length);
      System.arraycopy(trendlines, 0, ChartConstants.TRENDLINE_TYPES, 0, trendlines.length);
      StyleConstants.PAPER_A4.width = a4Width;
      StyleConstants.PAPER_A4.height = a4Height;
   }

   private static boolean isImmutable(Object value) {
      return value == null || value instanceof String || value instanceof Number &&
         value.getClass().getName().startsWith("java.") || value instanceof Boolean ||
         value instanceof Character || value instanceof Enum && ConstantScope.isImmutable(value);
   }

   private Object eval(String src) throws Exception {
      return engine.exec(engine.compile(src), null, null);
   }

   @Test
   void everyExposedMemberIsImmutableOrACopy() throws Exception {
      for(String name : new String[] { "Chart", "StyleConstant" }) {
         ConstantScope scope = assertInstanceOf(ConstantScope.class, eval(name), name);
         Object[] keys = scope.getMemberKeys();
         assertTrue(keys.length > 200, name + " has " + keys.length + " members");
         int bad = 0;

         for(Object key : keys) {
            Object value = scope.getMember((String) key);

            if(isImmutable(value)) {
               continue;
            }

            Object again = scope.getMember((String) key);

            if(value instanceof Size) {
               bad += value != again && value.equals(again) ? 0 : 1;
            }
            else if(value.getClass().isArray()) {
               bad += value != again ? 0 : 1;
            }
            else {
               bad++;
            }
         }

         assertEquals(0, bad, bad + " member(s) of " + name +
            " are neither immutable nor a per-read copy");
      }
   }

   /**
    * Every constant field of the scope classes is still a member: a string is the field's own
    * object, a primitive or boxed value an equal value, an array or {@code Size} an equal copy. A field holding any other
    * kind of object is the only kind that is no longer a member.
    */
   @Test
   void exposedNamesAndValuesMatchTheFields() throws Exception {
      Map<String, Class<?>[]> scopes = Map.of(
         "Chart", CHART_CLASSES, "StyleConstant", STYLE_CONSTANT_CLASSES);

      for(Map.Entry<String, Class<?>[]> e : scopes.entrySet()) {
         ConstantScope scope = (ConstantScope) eval(e.getKey());
         Map<String, Object> fields = constantFields(e.getValue());
         Set<String> expected = new HashSet<>();
         int wrongValues = 0;
         int exposedNonConstants = 0;

         for(Map.Entry<String, Object> field : fields.entrySet()) {
            String name = field.getKey();
            Object value = field.getValue();
            boolean ok;

            if(value instanceof String) {
               expected.add(name);
               ok = value == scope.getMember(name);
            }
            else if(isImmutable(value)) {
               // a primitive field reads as a new box each time, so compare by value
               expected.add(name);
               ok = Objects.equals(value, scope.getMember(name));
            }
            else if(value instanceof int[] arr) {
               expected.add(name);
               ok = scope.getMember(name) instanceof int[] copy && Arrays.equals(arr, copy);
            }
            else if(value != null && value.getClass() == Size.class) {
               expected.add(name);
               ok = value.equals(scope.getMember(name));
            }
            else {
               ok = true;
               exposedNonConstants += scope.hasMember(name) ? 1 : 0;
            }

            wrongValues += ok ? 0 : 1;
         }

         assertEquals(0, wrongValues, wrongValues + " member(s) of " + e.getKey() +
            " do not read back the field's value");
         assertEquals(0, exposedNonConstants, exposedNonConstants + " non-constant member(s) of " +
            e.getKey() + " are exposed");

         Set<String> keys = new HashSet<>();

         for(Object key : scope.getMemberKeys()) {
            // the map-type constants are added by name, not from a field
            if(!((String) key).startsWith("MAP_TYPE_")) {
               keys.add((String) key);
            }
         }

         Set<String> missing = new HashSet<>(expected);
         missing.removeAll(keys);
         Set<String> extra = new HashSet<>(keys);
         extra.removeAll(expected);
         assertEquals(0, missing.size() + extra.size(), e.getKey() + ": " + missing.size() +
            " expected member(s) missing, " + extra.size() + " unexpected member(s)");
         assertEquals(EXPECTED_SIZES.get(e.getKey()), keys.size(), e.getKey() + " member count");
      }
   }

   @Test
   void immutableConstantsKeepTheirIdentityInScripts() throws Exception {
      assertEquals(Boolean.TRUE, eval(
         "Chart.STRING === Chart.STRING && Chart.STRING === StyleConstant.STRING && " +
         "Chart.CHART_BAR === StyleConstant.CHART_BAR && StyleConstant.PORTRAIT == 1 && " +
         "typeof Chart.CHART_BAR === 'number' && typeof Chart.STRING === 'string'"));
   }

   @Test
   void arrayElementWriteDoesNotChangeTheStatic() throws Exception {
      assertEquals((double) texture[0],
                   eval("Chart.TEXTURE_STYLES[0] = 77; Chart.TEXTURE_STYLES[0]"));
      eval("StyleConstant.TEXTURE_STYLES[1] = 77; StyleConstant.M_LINE_STYLES[0] = 77; " +
           "Chart.TRENDLINE_TYPES[0] = 77; StyleConstant.TRENDLINE_TYPES[1] = 77; " +
           "var a = Chart.M_LINE_STYLES; a[1] = 77; 1");
      assertArrayEquals(texture, ChartConstants.TEXTURE_STYLES);
      assertArrayEquals(lineStyles, ChartConstants.M_LINE_STYLES);
      assertArrayEquals(trendlines, ChartConstants.TRENDLINE_TYPES);
   }

   @Test
   void sizeFieldWriteDoesNotChangeTheStatic() throws Exception {
      assertEquals((double) a4Width,
                   eval("StyleConstant.PAPER_A4.width = 1; StyleConstant.PAPER_A4.width"));
      eval("var p = StyleConstant.PAPER_A4; p.height = 2; 1");
      assertEquals(a4Width, StyleConstants.PAPER_A4.width);
      assertEquals(a4Height, StyleConstants.PAPER_A4.height);
   }

   /** A value that is not a constant value is not exposed, from a field or by name. */
   @Test
   void nonConstantValuesAreNotExposed() {
      ConstantScope scope = new ConstantScope(Holder.class);
      assertEquals(Set.of("TEXT", "NUMBER", "NUMBERS", "TEXTS"),
                   new HashSet<>(Arrays.asList(scope.getMemberKeys())));
      assertFalse(scope.hasMember("OBJECT"));
      assertFalse(scope.hasMember("BUILDER"));
      assertFalse(scope.hasMember("LIST"));
      assertFalse(scope.hasMember("MIXED"));
      assertNull(scope.getMember("OBJECT"));

      scope.putConstant("EXTRA", new StringBuilder());
      assertFalse(scope.hasMember("EXTRA"));

      // a later non-constant value replaces an earlier member of the same name
      scope.putConstant("TEXT", new StringBuilder());
      assertFalse(scope.hasMember("TEXT"));

      String[] texts = (String[]) scope.getMember("TEXTS");
      texts[0] = "changed";
      assertEquals("a", ((String[]) scope.getMember("TEXTS"))[0]);
      assertEquals("a", Holder.TEXTS[0]);
   }

   /** An enum constant is a constant value only when it holds no mutable state. */
   @Test
   void onlyStatelessEnumsAreConstants() {
      ConstantScope scope = new ConstantScope(EnumHolder.class);
      assertEquals(Set.of("PLAIN", "FINAL_STATE", "JDK"),
                   new HashSet<>(Arrays.asList(scope.getMemberKeys())));
      assertSame(PlainEnum.A, scope.getMember("PLAIN"));
      assertSame(FinalStateEnum.A, scope.getMember("FINAL_STATE"));
      assertSame(java.time.DayOfWeek.MONDAY, scope.getMember("JDK"));
      assertFalse(ConstantScope.isImmutable(MutableStateEnum.A));
      assertFalse(ConstantScope.isImmutable(FinalMutableTypeEnum.A));
      assertFalse(ConstantScope.isImmutable(BodyStateEnum.A));
      assertFalse(ConstantScope.isImmutable(BodyStateEnum.B));
   }

   /**
    * An enum's methods can read or change its static fields, so an enum with a static field that
    * is not final, or of a type that is not immutable, is not a constant. Final statics of an
    * immutable type, the enum's own constants and the synthetic values array are not state.
    */
   @Test
   void enumsWithStaticStateAreNotConstants() {
      assertTrue(ConstantScope.isImmutable(StaticConstantEnum.A));
      assertFalse(ConstantScope.isImmutable(NonFinalStaticEnum.A));
      assertFalse(ConstantScope.isImmutable(StaticCollectionEnum.A));
      assertFalse(ConstantScope.isImmutable(StaticObjectEnum.A));
      assertFalse(ConstantScope.isImmutable(StaticArrayEnum.A));
      assertFalse(ConstantScope.isImmutable(BodyStaticEnum.A));
      assertFalse(ConstantScope.isImmutable(BodyStaticEnum.B));
      assertFalse(ConstantScope.isImmutable(NestedStaticStateEnum.A));

      ConstantScope scope = new ConstantScope(StaticEnumHolder.class);
      assertEquals(Set.of("STATIC_CONSTANT"), new HashSet<>(Arrays.asList(scope.getMemberKeys())));
   }

   /** The map-type names are added to both scopes by name, not from a field. */
   @Test
   void mapTypeConstantsArePresentInBothScopes() throws Exception {
      for(String name : new String[] { "Chart", "StyleConstant" }) {
         ConstantScope scope = (ConstantScope) eval(name);
         int count = 0;

         for(Object key : scope.getMemberKeys()) {
            if(((String) key).startsWith("MAP_TYPE_")) {
               count++;
               assertInstanceOf(String.class, scope.getMember((String) key), name + " map type");
            }
         }

         assertEquals(EXPECTED_MAP_TYPES, count, name + " map-type member count");
      }
   }

   @Test
   void readsReturnTheConstantValues() throws Exception {
      assertEquals((double) a4Width, eval("StyleConstant.PAPER_A4.width"));
      assertEquals((double) a4Height, eval("StyleConstant['PAPER_A4'].height"));
      assertEquals((double) StyleConstants.PAPER_LETTER.width, eval("StyleConstant.PAPER_LETTER.width"));
      assertEquals((double) texture.length, eval("Chart.TEXTURE_STYLES.length"));
      assertEquals((double) texture[texture.length - 1],
                   eval("StyleConstant.TEXTURE_STYLES[Chart.TEXTURE_STYLES.length - 1]"));
      assertEquals((double) trendlines[1], eval("Chart.TRENDLINE_TYPES[1]"));
      assertEquals(1.0, eval("StyleConstant.PORTRAIT"));
      assertEquals((double) ChartConstants.DRILL_UP_OP.length(), eval("Chart.DRILL_UP_OP.length"));
      assertEquals(StyleConstants.PAPER_A4, eval("StyleConstant.PAPER_A4"));
   }

   @Test
   void pooledWorksheetWriteDoesNotChangeTheStatic() throws Exception {
      ScriptEnv env = PoolTestSupport.env();
      assertEquals((double) texture[0], PoolTestSupport.run(
         env, "Chart.TEXTURE_STYLES[0] = 77; Chart.TEXTURE_STYLES[0]"));
      assertEquals((double) a4Width, PoolTestSupport.run(
         env, "StyleConstant.PAPER_A4.width = 1; StyleConstant.PAPER_A4.width"));
      assertArrayEquals(texture, ChartConstants.TEXTURE_STYLES);
      assertEquals(a4Width, StyleConstants.PAPER_A4.width);
   }

   /** The public static final values of {@code classes}, a later class replacing a name. */
   private static Map<String, Object> constantFields(Class<?>[] classes) throws Exception {
      Map<String, Object> fields = new LinkedHashMap<>();

      for(Class<?> cls : classes) {
         for(Field field : cls.getFields()) {
            int mod = field.getModifiers();

            if(Modifier.isStatic(mod) && Modifier.isFinal(mod)) {
               fields.put(field.getName(), field.get(null));
            }
         }
      }

      return fields;
   }

   // the classes GraalJavaScriptEngine builds the two scopes from
   private static final Class<?>[] CHART_CLASSES = {
      inetsoft.uql.viewsheet.graph.GraphTypes.class, ChartConstants.class,
      inetsoft.uql.viewsheet.graph.GeographicOption.class
   };
   private static final Class<?>[] STYLE_CONSTANT_CLASSES = {
      inetsoft.uql.viewsheet.graph.GraphTypes.class, ChartConstants.class,
      inetsoft.uql.viewsheet.graph.GeographicOption.class, StyleConstants.class,
      inetsoft.report.ReportSheet.class, inetsoft.report.TableLens.class,
      inetsoft.uql.viewsheet.VSFormat.class, inetsoft.uql.viewsheet.TimeInfo.class
   };

   // member counts of the two scopes (less the map-type names), unchanged by this fix apart
   // from the non-constant values
   private static final Map<String, Integer> EXPECTED_SIZES = Map.of("Chart", 215, "StyleConstant", 509);
   // the map-type names added to each scope from the installed map data
   private static final int EXPECTED_MAP_TYPES = 6;

   enum PlainEnum { A }

   enum FinalStateEnum {
      A(1, "a", PlainEnum.A);

      FinalStateEnum(int n, String s, PlainEnum p) {
         this.n = n;
         this.s = s;
         this.p = p;
      }

      final int n;
      final String s;
      final PlainEnum p;
   }

   enum MutableStateEnum {
      A;
      int n;
   }

   enum FinalMutableTypeEnum {
      A;
      final StringBuilder b = new StringBuilder();
   }

   enum BodyStateEnum {
      A {
         int n;
      },
      B
   }

   enum StaticConstantEnum {
      A, B;
      static final int N = 1;
      static final String S = "s";
      static final StaticConstantEnum DEFAULT = A;
   }

   enum NonFinalStaticEnum {
      A;
      static int n;
   }

   enum StaticCollectionEnum {
      A;
      static final List<String> ITEMS = new ArrayList<>();
   }

   enum StaticObjectEnum {
      A;
      static final StringBuilder BUFFER = new StringBuilder();
   }

   enum StaticArrayEnum {
      A;
      static final int[] VALUES = { 1 };
   }

   enum BodyStaticEnum {
      A {
         static int n;
      },
      B
   }

   enum NestedStaticStateEnum {
      A(NonFinalStaticEnum.A);

      NestedStaticStateEnum(NonFinalStaticEnum e) {
         this.e = e;
      }

      final NonFinalStaticEnum e;
   }

   public static final class StaticEnumHolder {
      public static final StaticConstantEnum STATIC_CONSTANT = StaticConstantEnum.B;
      public static final NonFinalStaticEnum NON_FINAL_STATIC = NonFinalStaticEnum.A;
      public static final StaticCollectionEnum STATIC_COLLECTION = StaticCollectionEnum.A;
      public static final StaticObjectEnum STATIC_OBJECT = StaticObjectEnum.A;
      public static final StaticArrayEnum STATIC_ARRAY = StaticArrayEnum.A;
      public static final BodyStaticEnum BODY_STATIC = BodyStaticEnum.B;
   }

   public static final class EnumHolder {
      public static final PlainEnum PLAIN = PlainEnum.A;
      public static final FinalStateEnum FINAL_STATE = FinalStateEnum.A;
      public static final java.time.DayOfWeek JDK = java.time.DayOfWeek.MONDAY;
      public static final MutableStateEnum MUTABLE = MutableStateEnum.A;
      public static final FinalMutableTypeEnum FINAL_MUTABLE_TYPE = FinalMutableTypeEnum.A;
      public static final BodyStateEnum BODY_STATE = BodyStateEnum.B;
   }

   public static final class Holder {
      public static final String TEXT = "text";
      public static final Integer NUMBER = 1;
      public static final int[] NUMBERS = { 1, 2 };
      public static final String[] TEXTS = { "a", "b" };
      public static final Object OBJECT = new Object();
      public static final StringBuilder BUILDER = new StringBuilder();
      public static final List<String> LIST = new ArrayList<>();
      public static final Object[] MIXED = { "a", new StringBuilder() };
   }
}
