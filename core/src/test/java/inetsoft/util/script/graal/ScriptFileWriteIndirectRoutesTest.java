/*
 * This file is part of StyleBI.
 * Copyright (C) 2026  InetSoft Technology
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

import inetsoft.report.script.graal.ReportGraalJavaScriptEngine;
import inetsoft.test.*;
import inetsoft.util.script.FormulaContext;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #79846: indirect routes to the {@code java.util.Formatter} and
 * {@code java.awt.color.ICC_Profile} file primitives that the name-based routes in
 * {@code ScriptIoBoundaryTest} do not cover: reflection ({@code Class.forName},
 * {@code getClass()}, class loaders), constructor aliases, explicit overload
 * selection, and ICC_Profile instances handed out by other admitted
 * {@code java.awt.color}/{@code java.awt.image} APIs. Every route must be refused
 * on both engines, restricted and unrestricted, and must leave no file behind.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class ScriptFileWriteIndirectRoutesTest {
   private static final String ORIGINAL = "ORIGINAL-79846-INDIRECT";

   private GraalJavaScriptEngine baseEngine;
   private ReportGraalJavaScriptEngine reportEngine;

   @TempDir
   Path tmp;

   @BeforeEach
   void setup() throws Exception {
      FormulaContext.setRestricted(false);
      baseEngine = new GraalJavaScriptEngine();
      baseEngine.init(new HashMap<>());
      reportEngine = new ReportGraalJavaScriptEngine();
      reportEngine.init(new HashMap<>());
   }

   @AfterEach
   void teardown() {
      FormulaContext.setRestricted(false);

      if(baseEngine != null) {
         baseEngine.close();
      }

      if(reportEngine != null) {
         reportEngine.close();
      }
   }

   private record Eng(String name, GraalJavaScriptEngine engine) {}

   private List<Eng> engines() {
      return List.of(new Eng("base", baseEngine), new Eng("report", reportEngine));
   }

   private String run(GraalJavaScriptEngine engine, String src, boolean restricted) {
      boolean old = FormulaContext.isRestricted();

      try {
         FormulaContext.setRestricted(restricted);
         Object o = engine.exec(engine.compile(src), null, null);
         return o == null ? null : o.toString();
      }
      catch(Throwable t) {
         return null;
      }
      finally {
         FormulaContext.setRestricted(old);
      }
   }

   /** A refusal comes back as "REFUSED:..." (base) or null (report). Handles are closed. */
   private static String guarded(String body) {
      return "var f = null; try { " + body + "; 'DONE'; } catch(ex) { 'REFUSED:' + String(ex); }" +
         " finally { try { if(f != null && f.close) f.close(); } catch(e2) {} }";
   }

   private static String js(File f) {
      return f.getAbsolutePath().replace('\\', '/');
   }

   private static final String STR = "Java.type('java.lang.String')";

   /** Formatter reached without naming it through Java.type/the package shim. */
   private static List<String> formatterRoutes(String p) {
      String use = "; f.format('%s', 'pwned'); f.flush()";
      return List.of(
         "var F = java.util.Formatter; f = new F('" + p + "')" + use,
         "f = new (Java.type('java.util.Formatter'))('" + p + "')" + use,
         "f = Packages.java.util.Formatter('" + p + "')" + use,
         "f = java.lang.Class.forName('java.util.Formatter').getConstructor(" + STR +
            ").newInstance('" + p + "')" + use,
         "f = Java.type('java.lang.Class').forName('java.util.Formatter').getConstructor(" +
            STR + ").newInstance('" + p + "')" + use,
         "f = new java.awt.Color(1, 2, 3).getClass().forName('java.util.Formatter')" +
            ".getConstructor(" + STR + ").newInstance('" + p + "')" + use,
         "f = new java.awt.Color(1, 2, 3).getClass().getClassLoader()" +
            ".loadClass('java.util.Formatter').getConstructor(" + STR +
            ").newInstance('" + p + "')" + use,
         "f = java.lang.Thread.currentThread().getContextClassLoader()" +
            ".loadClass('java.util.Formatter').getConstructor(" + STR +
            ").newInstance('" + p + "')" + use,
         "f = new java.util.Formatter(new java.lang.StringBuilder()); " +
            "f.out().append('x')",
         "f = java.util.Formatter['(java.lang.String)']('" + p + "')" + use);
   }

   /** ICC_Profile instances handed out by admitted APIs, never named directly. */
   private static final List<String> ICC_SOURCES = List.of(
      "java.awt.color.ColorSpace.getInstance(java.awt.color.ColorSpace.CS_GRAY).getProfile()",
      "java.awt.color.ColorSpace.getInstance(java.awt.color.ColorSpace.CS_LINEAR_RGB)" +
         ".getProfile()",
      "java.awt.color.ColorSpace.getInstance(java.awt.color.ColorSpace.CS_CIEXYZ).getProfile()",
      "java.awt.color.ColorSpace.getInstance(java.awt.color.ColorSpace.CS_PYCC).getProfile()",
      "new java.awt.image.BufferedImage(1, 1, 10).getColorModel().getColorSpace().getProfile()",
      "java.awt.image.ColorModel.getRGBdefault().getColorSpace().getProfile()");

   /**
    * The ICC sources must still hand out a profile object, otherwise the write probes
    * would pass for the wrong reason (the instance route is what the type deny covers).
    */
   @Test
   void iccSourcesStillReturnAProfileInstance() {
      List<Executable> checks = new ArrayList<>();

      for(Eng e : engines()) {
         for(boolean restricted : new boolean[] { true, false }) {
            for(String src : ICC_SOURCES) {
               checks.add(() -> assertEquals(
                  "object", run(e.engine(), "var prof = " + src + "; typeof prof", restricted),
                  e.name() + " restricted=" + restricted + ": " + src));
            }
         }
      }

      assertAll(checks);
   }

   private static List<String> iccWriteRoutes(String p) {
      List<String> routes = new ArrayList<>();

      for(String src : ICC_SOURCES) {
         routes.add("var prof = " + src + "; prof.write('" + p + "')");
         routes.add("var prof = " + src + "; prof['write(java.lang.String)']('" + p + "')");
      }

      return routes;
   }

   @Test
   void indirectFormatterRoutesCannotWriteFiles() {
      assertAll(probe("formatter", ScriptFileWriteIndirectRoutesTest::formatterRoutes));
   }

   @Test
   void indirectIccProfileRoutesCannotWriteFiles() {
      assertAll(probe("icc", ScriptFileWriteIndirectRoutesTest::iccWriteRoutes));
   }

   private List<Executable> probe(String kind, java.util.function.Function<String,
      List<String>> routesFor)
   {
      List<Executable> checks = new ArrayList<>();

      for(Eng e : engines()) {
         for(boolean restricted : new boolean[] { true, false }) {
            String what = e.name() + (restricted ? "-restricted" : "-unrestricted");
            int n = routesFor.apply("x").size();

            for(int i = 0; i < n; i++) {
               int idx = i;
               checks.add(() -> {
                  File out = tmp.resolve(kind + "-new-" + what + "-" + idx + ".bin").toFile();
                  File victim = tmp.resolve(kind + "-victim-" + what + "-" + idx + ".txt")
                     .toFile();
                  Files.writeString(victim.toPath(), ORIGINAL, StandardCharsets.UTF_8);

                  String route = routesFor.apply(js(out)).get(idx);
                  String r1 = run(e.engine(), guarded(route), restricted);
                  String r2 = run(e.engine(), guarded(routesFor.apply(js(victim)).get(idx)),
                                  restricted);
                  System.out.println("V79846 " + what + " " + kind + "#" + idx + " r1=" + r1 +
                                        " r2=" + r2 + " exists=" + out.exists());

                  assertTrue(r1 == null || r1.startsWith("REFUSED:"),
                             what + " " + kind + "#" + idx + " not refused: " + r1 +
                                " :: " + route);
                  assertTrue(r2 == null || r2.startsWith("REFUSED:"),
                             what + " " + kind + "#" + idx + " (victim) not refused: " + r2);
                  assertFalse(out.exists(), what + " " + kind + "#" + idx + " created " + out +
                     " :: " + route);
                  assertEquals(ORIGINAL,
                               Files.readString(victim.toPath(), StandardCharsets.UTF_8),
                               what + " " + kind + "#" + idx + " changed the victim file");
               });
            }
         }
      }

      return checks;
   }

   /** Neighbours of the blocked classes keep working on both engines and modes. */
   @Test
   void controlsStillWork() {
      Map<String, String> controls = new LinkedHashMap<>();
      controls.put("java.lang.String.format('%s-%d', 'a', 7)", "a-7");
      controls.put("String(java.lang.String.format('%.2f', 3.14159))", "3.14");
      controls.put("new java.awt.Color(10, 20, 30).getBlue()", "30");
      controls.put("java.awt.color.ColorSpace.getInstance(" +
                      "java.awt.color.ColorSpace.CS_sRGB).isCS_sRGB()", "true");
      controls.put("new java.awt.image.BufferedImage(2, 3, 1).getHeight()", "3");
      controls.put("var p = new java.util.Properties(); p.setProperty('a', 'b');" +
                      " p.getProperty('a')", "b");
      controls.put("var s = new java.util.Scanner('1 2'); var v = s.nextInt() + s.nextInt();" +
                      " s.close(); v", "3");

      List<Executable> checks = new ArrayList<>();

      for(Eng e : engines()) {
         for(boolean restricted : new boolean[] { true, false }) {
            String what = e.name() + (restricted ? "-restricted" : "-unrestricted");

            for(Map.Entry<String, String> c : controls.entrySet()) {
               checks.add(() -> {
                  String r = run(e.engine(), c.getKey(), restricted);
                  assertNotNull(r, what + ": control failed: " + c.getKey());
                  assertEquals(c.getValue(), r.replaceAll("\\.0$", ""),
                               what + ": " + c.getKey());
               });
            }
         }
      }

      assertAll(checks);
   }
}
