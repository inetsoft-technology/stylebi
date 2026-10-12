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

import inetsoft.graph.aesthetic.*;
import inetsoft.report.StyleConstants;
import inetsoft.report.composition.region.ChartConstants;
import inetsoft.report.script.SharedStaticFixture;
import inetsoft.util.CoreTool;
import inetsoft.util.script.ScriptEnv;
import inetsoft.util.script.graal.pool.PoolTestSupport;
import org.junit.jupiter.api.*;

import java.awt.Color;
import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #77497: a public static that holds a mutable object is shared by every script context
 * and by Java code in the JVM. A script can reach the statics of a class by more than one route
 * (a class global, {@code Java.type}, the legacy package shim, the class object's static view),
 * so each check here runs over all of them. Checked: the objects of the
 * constant-holder types the {@code Chart}/{@code StyleConstant} scopes are built from, the shared
 * chart shape constants and default palette, and thread-local statics. Identity of the shared
 * constants is kept. Failure messages report counts only.
 */
@Tag("core")
class ScriptSharedStaticStateTest {
   private GraalJavaScriptEngine engine;
   private final Map<GShape, Object[]> shapes = new IdentityHashMap<>();
   private Color[] palette;

   @BeforeEach
   void setup() throws Exception {
      engine = newEngine();

      for(Field field : shapeFields()) {
         GShape shape = (GShape) field.get(null);
         shapes.put(shape, shapeState(shape));
      }

      palette = paletteColors();
   }

   @AfterEach
   void teardown() throws Exception {
      engine.close();
      // put back any state a write changed, so other tests are not affected
      for(Map.Entry<GShape, Object[]> e : shapes.entrySet()) {
         restoreShapeState(e.getKey(), e.getValue());
      }

      Object current = CategoricalColorFrame.class.getField("COLOR_PALETTE").get(null);

      if(current instanceof Color[] arr) {
         System.arraycopy(palette, 0, arr, 0, palette.length);
      }

      SharedStaticFixture.LOCAL.remove();
      SharedStaticFixture.SUPPLIED.remove();
   }

   /** The class-lookup routes to the static members of {@code cls}, as script expressions. */
   private static List<String> routes(Class<?> cls) {
      String name = cls.getName();
      return List.of(
         "Java.type('" + name + "')",
         name,
         "Java.type('" + name + "')['class'].static");
   }

   /** As {@link #routes}, plus the class global the engine installs under the simple name. */
   private static List<String> routesWithGlobal(Class<?> cls) {
      List<String> list = new ArrayList<>(routes(cls));
      list.add(cls.getSimpleName());
      return list;
   }

   /**
    * The mutable values of the classes the constant scopes are built from are not usable as the
    * shared object by class lookup on the declaring class or on a class that inherits them: the
    * value is not readable, or it reads as an object whose type exposes no members.
    */
   @Test
   void constantHolderObjectsAreNotUsableByClassLookup() throws Exception {
      int checked = 0;
      int reachable = 0;

      for(Class<?> cls : CONSTANT_SCOPE_CLASSES) {
         for(Field field : cls.getFields()) {
            int mod = field.getModifiers();

            if(!Modifier.isStatic(mod) || !Modifier.isFinal(mod)) {
               continue;
            }

            Object value = field.get(null);

            if(ConstantScope.isImmutable(value)) {
               continue;
            }

            Set<String> routes = new LinkedHashSet<>(routes(field.getDeclaringClass()));
            routes.addAll(routes(cls));

            for(String route : routes) {
               String member = read(route, field.getName());
               checked++;
               reachable += eval(member) == value && Boolean.TRUE.equals(
                  eval("Object.keys(" + member + ").length > 0")) ? 1 : 0;
            }
         }
      }

      assertTrue(checked > 0, "no mutable value checked");
      assertEquals(0, reachable, reachable + " of " + checked +
         " class lookup(s) returned a usable shared mutable value of a constant holder");
   }

   /** A script cannot change a shared shape constant through any route. */
   @Test
   void sharedShapeConstantsCannotBeChanged() throws Exception {
      int attempts = 0;

      for(Field field : shapeFields()) {
         for(String route : routesWithGlobal(field.getDeclaringClass())) {
            String shape = read(route, field.getName());
            attempts++;
            eval("try { var s = " + shape + "; s.setLineColor(java.awt.Color.RED); } catch(e) {}" +
                 "try { " + shape + ".setFillColor(java.awt.Color.RED); } catch(e) {}" +
                 "try { " + shape + ".setLineStyle(77); } catch(e) {}" +
                 "try { " + shape + ".lineColor = java.awt.Color.RED; } catch(e) {}" +
                 "try { " + shape + ".setSVG('x'); } catch(e) {} 1");
         }
      }

      assertTrue(attempts > 0, "no shape constant checked");
      assertEquals(0, changedShapes(), changedShapes() + " shared shape constant(s) changed");
   }

   /** Scripts compare shapes by identity and hand them to Java; both keep working. */
   @Test
   void shapeConstantIdentityIsKept() throws Exception {
      assertEquals(Boolean.TRUE, eval(
         "var T = Java.type('inetsoft.graph.aesthetic.GShape');" +
         "GShape.CIRCLE === T.CIRCLE && GShape.CIRCLE == inetsoft.graph.aesthetic.GShape.CIRCLE &&" +
         "T['class'].static.CIRCLE === GShape.CIRCLE && SVGShape.STAR === Java.type(" +
         "'inetsoft.graph.aesthetic.SVGShape').STAR &&" +
         "new StaticShapeFrame(GShape.CIRCLE).getShape() === GShape.CIRCLE &&" +
         "new StaticShapeFrame(GShape.CIRCLE).getShape() == GShape.CIRCLE"));
      assertSame(GShape.CIRCLE, eval("GShape.CIRCLE"));
      assertSame(SVGShape.STAR, eval("SVGShape.STAR"));

      StaticShapeFrame frame = (StaticShapeFrame) eval(
         "var f = new StaticShapeFrame(); f.setShape(GShape.FILLED_SQUARE); f");
      assertSame(GShape.FILLED_SQUARE, frame.getShape());
   }

   /** A copy made from a shape constant is the script's own, and can be changed. */
   @Test
   void copiesOfShapeConstantsCanBeChanged() throws Exception {
      assertEquals(Boolean.TRUE, eval(
         "var s = GShape.CIRCLE.create(true, true); s.setLineColor(java.awt.Color.RED);" +
         "s.getLineColor().getRed() == 255"));
      assertEquals(Boolean.TRUE, eval(
         "var s = GShape.CIRCLE.clone(); s.setLineStyle(2); s.getLineStyle() == 2 && " +
         "s !== GShape.CIRCLE"));
      assertEquals(0, changedShapes(), changedShapes() + " shared shape constant(s) changed");
   }

   /** The default palette reads as before but cannot be changed through any route. */
   @Test
   void defaultPaletteCannotBeChanged() throws Exception {
      for(String route : routesWithGlobal(CategoricalColorFrame.class)) {
         String p = read(route, "COLOR_PALETTE");
         eval("try { " + p + "[0] = java.awt.Color.RED; } catch(e) {} 1");
         eval("try { " + p + "[1] = null; } catch(e) {} 1");
         assertEquals(Boolean.TRUE, eval(p + ".length == " + palette.length), "palette length");
         assertEquals(Boolean.TRUE, eval(p + "[2].getRGB() == " + palette[2].getRGB()),
                      "palette color");
      }

      assertArrayEquals(palette, paletteColors(), "default palette changed");
   }

   /** A thread-local static cannot be read or set by a script, so it cannot carry state over. */
   @Test
   void threadLocalStaticsCannotBeUsed() throws Exception {
      int used = 0;
      int checked = 0;

      for(String route : routes(SharedStaticFixture.class)) {
         checked++;
         eval("try { " + read(route, "LOCAL") + ".set('script'); } catch(e) {} 1");
         used += SharedStaticFixture.LOCAL.get() != null ? 1 : 0;
         SharedStaticFixture.LOCAL.remove();
         used += "initial".equals(
            eval("try { " + read(route, "SUPPLIED") + ".get(); } catch(e) { null }")) ? 1 : 0;
      }

      assertEquals(0, used, used + " of " + (checked * 2) + " thread-local use(s) succeeded");
   }

   /**
    * A public static thread-local field cannot be reassigned or cleared by a script: Java code
    * reads it on every thread, so a write would reach every user. Such fields are final.
    * The final check covers every class of the product. The script writes are tried, on every
    * class-lookup route, only for the classes that are already initialized: reading the field
    * or reaching the class from a script initializes it, and a class whose static initializer
    * fails here (for example one that needs a Spring bean) cannot be used again by any later
    * test in the JVM.
    */
   @Test
   void threadLocalStaticFieldsCannotBeReassigned() throws Exception {
      List<Field> fields = publicStaticThreadLocalFields();
      int writable = 0;
      int attempts = 0;
      int reassigned = 0;
      // CoreTool needs no Spring context, so there is always an initialized class to write to
      Objects.requireNonNull(CoreTool.yearFmt);

      for(Field field : fields) {
         writable += Modifier.isFinal(field.getModifiers()) ? 0 : 1;

         if(!isInitialized(field.getDeclaringClass())) {
            continue;
         }

         Object before;

         try {
            field.setAccessible(true);
            before = field.get(null);
         }
         catch(Throwable ex) {
            // a class that cannot be initialized here cannot be reached by a script either
            continue;
         }

         for(String route : routes(field.getDeclaringClass())) {
            attempts++;
            eval("try { " + read(route, field.getName()) + " = null; } catch(e) {} 1");

            if(field.get(null) != before) {
               reassigned++;
               // put it back, so other tests are not affected
               field.set(null, before);
            }
         }
      }

      assertTrue(attempts > 0, "no thread-local static field checked");
      assertEquals(0, reassigned, reassigned + " of " + attempts +
         " script write(s) reassigned a thread-local static field");
      assertEquals(0, writable, writable + " of " + fields.size() +
         " public static thread-local field(s) are not final");
   }

   /** The same through a pooled worksheet context, read back from another context and Java. */
   @Test
   void pooledContextChangesDoNotReachOtherContexts() throws Exception {
      ScriptEnv env = PoolTestSupport.env();
      PoolTestSupport.run(env,
         "var T = Java.type('inetsoft.graph.aesthetic.GShape');" +
         "try { T.CIRCLE.setLineColor(java.awt.Color.RED); } catch(e) {}" +
         "var F = Java.type('inetsoft.graph.aesthetic.CategoricalColorFrame');" +
         "try { F.COLOR_PALETTE[0] = java.awt.Color.RED; } catch(e) {}" +
         "try { Java.type('" + SharedStaticFixture.class.getName() +
         "').LOCAL.set('script'); } catch(e) {} 1");

      assertEquals(0, changedShapes(), changedShapes() + " shared shape constant(s) changed");
      assertArrayEquals(palette, paletteColors(), "default palette changed");
      assertNull(SharedStaticFixture.LOCAL.get(), "thread-local set by a script");

      GraalJavaScriptEngine other = newEngine();

      try {
         assertEquals(Boolean.TRUE, other.exec(other.compile(
            "GShape.CIRCLE.getLineColor() == null && " +
            "CategoricalColorFrame.COLOR_PALETTE[0].getRGB() == " + palette[0].getRGB()),
            null, null));
      }
      finally {
         other.close();
      }
   }

   private static GraalJavaScriptEngine newEngine() throws Exception {
      GraalJavaScriptEngine engine = new GraalJavaScriptEngine();
      engine.init(new HashMap<>());
      return engine;
   }

   private Object eval(String src) throws Exception {
      return engine.exec(engine.compile(src), null, null);
   }

   private static String read(String route, String member) {
      return "(" + route + ")['" + member + "']";
   }

   /**
    * The public static fields of a thread-local type declared by the product classes (the
    * classes directory that holds this engine), found without initializing the classes.
    */
   private static List<Field> publicStaticThreadLocalFields() throws Exception {
      Path root = Paths.get(GraalJavaScriptEngine.class.getProtectionDomain()
                               .getCodeSource().getLocation().toURI());
      ClassLoader loader = ScriptSharedStaticStateTest.class.getClassLoader();
      List<Field> fields = new ArrayList<>();
      List<Path> files;

      try(Stream<Path> walk = Files.walk(root)) {
         files = walk.filter(f -> f.toString().endsWith(".class")).toList();
      }

      for(Path file : files) {
         String path = root.relativize(file).toString();
         String name = path.substring(0, path.length() - 6).replace(File.separatorChar, '.');

         if(!name.startsWith("inetsoft.") || name.endsWith("module-info")) {
            continue;
         }

         try {
            for(Field field : Class.forName(name, false, loader).getDeclaredFields()) {
               int mod = field.getModifiers();

               if(Modifier.isPublic(mod) && Modifier.isStatic(mod) &&
                  ThreadLocal.class.isAssignableFrom(field.getType()))
               {
                  fields.add(field);
               }
            }
         }
         catch(Throwable ignore) {
            // a class whose dependencies are not on the test class path
         }
      }

      return fields;
   }

   /**
    * If a class has been initialized, checked without initializing it. Needs the
    * --add-opens=java.base/jdk.internal.misc=ALL-UNNAMED of the surefire argLine.
    */
   private static boolean isInitialized(Class<?> cls) throws Exception {
      Class<?> unsafe = Class.forName("jdk.internal.misc.Unsafe");
      Object instance = unsafe.getMethod("getUnsafe").invoke(null);
      Method method = unsafe.getMethod("shouldBeInitialized", Class.class);
      return !(Boolean) method.invoke(instance, cls);
   }

   /** The public static shape constants of the shape classes. */
   private static List<Field> shapeFields() {
      List<Field> fields = new ArrayList<>();

      for(Class<?> cls : new Class<?>[] { GShape.class, SVGShape.class }) {
         for(Field field : cls.getDeclaredFields()) {
            int mod = field.getModifiers();

            if(Modifier.isPublic(mod) && Modifier.isStatic(mod) &&
               GShape.class.isAssignableFrom(field.getType()))
            {
               fields.add(field);
            }
         }
      }

      return fields;
   }

   private static final String[] SHAPE_FIELDS = {
      "linecolor", "fillcolor", "lineStyle", "fill", "outline" };

   private static Object[] shapeState(GShape shape) throws Exception {
      Object[] state = new Object[SHAPE_FIELDS.length + 1];

      for(int i = 0; i < SHAPE_FIELDS.length; i++) {
         state[i] = shapeField(SHAPE_FIELDS[i]).get(shape);
      }

      state[SHAPE_FIELDS.length] = shape instanceof SVGShape svg ? svgField().get(svg) : null;
      return state;
   }

   private static void restoreShapeState(GShape shape, Object[] state) throws Exception {
      for(int i = 0; i < SHAPE_FIELDS.length; i++) {
         shapeField(SHAPE_FIELDS[i]).set(shape, state[i]);
      }

      if(shape instanceof SVGShape svg) {
         svgField().set(svg, state[SHAPE_FIELDS.length]);
      }
   }

   private int changedShapes() throws Exception {
      int changed = 0;

      for(Map.Entry<GShape, Object[]> e : shapes.entrySet()) {
         changed += Arrays.equals(e.getValue(), shapeState(e.getKey())) ? 0 : 1;
      }

      return changed;
   }

   private static Field shapeField(String name) throws Exception {
      Field field = GShape.class.getDeclaredField(name);
      field.setAccessible(true);
      return field;
   }

   private static Field svgField() throws Exception {
      Field field = SVGShape.class.getDeclaredField("resource");
      field.setAccessible(true);
      return field;
   }

   /** The default palette's colors, whatever collection type holds them. */
   private static Color[] paletteColors() throws Exception {
      Object value = CategoricalColorFrame.class.getField("COLOR_PALETTE").get(null);

      if(value instanceof Color[] arr) {
         return arr.clone();
      }

      return ((Collection<?>) value).toArray(new Color[0]);
   }

   // the classes GraalJavaScriptEngine builds the Chart and StyleConstant scopes from
   private static final Class<?>[] CONSTANT_SCOPE_CLASSES = {
      inetsoft.uql.viewsheet.graph.GraphTypes.class, ChartConstants.class,
      inetsoft.uql.viewsheet.graph.GeographicOption.class, StyleConstants.class,
      inetsoft.report.ReportSheet.class, inetsoft.report.TableLens.class,
      inetsoft.uql.viewsheet.VSFormat.class, inetsoft.uql.viewsheet.TimeInfo.class
   };
}
