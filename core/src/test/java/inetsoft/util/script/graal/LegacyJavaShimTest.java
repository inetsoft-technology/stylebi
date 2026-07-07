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

import inetsoft.sree.SreeEnv;
import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Exercises the legacy Rhino-interop compatibility shim through the real engine
 * path: package-root navigation, no-{@code new} construction, static member
 * access, importClass/importPackage, and the allow-list security boundary.
 * (Feature #75423)
 */
@Tag("core")
class LegacyJavaShimTest {
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
      return engine.exec(engine.compile(src), null, null);
   }

   private static double num(Object o) {
      return ((Number) o).doubleValue();
   }

   @Test
   void qualifiedNavigationAndNoNewConstruction() throws Exception {
      // java.awt.Color(0x010203) -- constructed WITHOUT `new`, Rhino-style.
      assertEquals(3.0, num(eval("java.awt.Color(0x010203).getBlue()")));
      assertEquals(1.0, num(eval("java.awt.Color(0x010203).getRed()")));
   }

   @Test
   void newConstruction() throws Exception {
      assertEquals(255.0, num(eval("new java.awt.Color(255, 0, 0).getRed()")));
   }

   @Test
   void staticMethodAccessViaShim() throws Exception {
      assertEquals(5.0, num(eval("java.lang.Math.max(2, 5)")));
   }

   /**
    * Rhino parity: a JS Date passed where a numeric coordinate is expected
    * (e.g. LabelForm.setTuple(double[]) on a time axis) must coerce to epoch
    * millis, not error. Mirrors the Projection example viewsheet. (#75423)
    */
   @Test
   void jsDateCoercesToEpochMillisDouble() throws Exception {
      // double[]{ date-as-millis, 0 } round-trips through LabelForm.getTuple()[0].
      Object millis = eval(
         "var f = new inetsoft.graph.guide.form.LabelForm();" +
         "f.setTuple([new Date(Date.UTC(2024, 9, 1)), 0]);" +
         "f.getTuple()[0]");
      assertEquals((double) java.time.Instant.parse("2024-10-01T00:00:00Z").toEpochMilli(),
                   num(millis));
   }

   @Test
   void importClassBindsUnqualifiedName() throws Exception {
      assertEquals(7.0, num(eval(
         "importClass(java.awt.Color); new Color(0, 0, 7).getBlue()")));
   }

   @Test
   void importPackageResolvesUnqualifiedName() throws Exception {
      assertEquals(9.0, num(eval(
         "importPackage(java.awt); new Color(0, 9, 0).getGreen()")));
   }

   /**
    * A failed {@code Class.forName} probe is remembered so a repeat probe for
    * the same name doesn't rescan the classpath again. (#75551)
    */
   @Test
   void failedProbeIsCachedAsNegative() {
      String bogus = "inetsoft.does.not.Exist" + System.nanoTime();

      assertNull(LegacyJavaShim.tryLoad(bogus));
      assertTrue(LegacyJavaShim.isNegativelyCached(bogus));
      // second probe hits the cache and still correctly reports a miss.
      assertNull(LegacyJavaShim.tryLoad(bogus));
   }

   /**
    * Plugins/JDBC drivers can be installed at runtime without a restart, so a
    * name cached as unreachable must become probe-able again once the negative
    * cache is invalidated (wired to PluginsChangedEvent in production). (#75551)
    */
   @Test
   void invalidateNegativeCacheDropsPriorMisses() {
      String bogus = "inetsoft.does.not.Exist" + System.nanoTime();

      assertNull(LegacyJavaShim.tryLoad(bogus));
      assertTrue(LegacyJavaShim.isNegativelyCached(bogus));

      LegacyJavaShim.invalidateNegativeCache();

      assertFalse(LegacyJavaShim.isNegativelyCached(bogus));
   }

   @Test
   void inetsoftPackageNavigationResolves() throws Exception {
      // a real inetsoft class on an allowed prefix resolves to a usable type.
      assertEquals(Boolean.TRUE, eval(
         "typeof inetsoft.uql.asset.ColumnRef === 'function' || " +
         "typeof inetsoft.uql.asset.ColumnRef === 'object'"));
   }

   // --- security boundary: the shim resolves leaves through the same allow-list
   //     as Java.type, so blocked classes stay blocked even via package roots. ---

   @Test
   void blockedSystemClassDeniedViaShim() {
      assertThrows(Exception.class, () -> eval("java.lang.System.exit(1)"));
   }

   @Test
   void threadingClassDeniedViaShim() {
      assertThrows(Exception.class, () -> eval("new java.lang.Thread()"));
   }

   @Test
   void blockedReflectionDeniedViaShim() {
      assertThrows(Exception.class, () -> eval("java.lang.Runtime.getRuntime()"));
   }

   /**
    * The {@value LegacyJavaShim#GATE_PROPERTY} gate disables the shim live (no
    * re-init): package roots throw while disabled and work again once re-enabled.
    * Self-skips if SreeEnv property toggling is unavailable in this context.
    */
   @Test
   void gateDisablesShimLive() throws Exception {
      String prev;
      boolean toggled;

      // Writing SreeEnv properties needs a running server/Spring context; when it
      // is unavailable (standalone unit run), skip rather than fail.
      try {
         prev = SreeEnv.getProperty(LegacyJavaShim.GATE_PROPERTY);
         SreeEnv.setProperty(LegacyJavaShim.GATE_PROPERTY, "false");
         toggled = !LegacyJavaShim.isEnabled();
      }
      catch(Throwable t) {
         toggled = false;
         prev = null;
      }

      Assumptions.assumeTrue(toggled, "SreeEnv property toggle unavailable in this context");

      try {
         // disabled: navigation throws, no engine re-init required.
         assertThrows(Exception.class, () -> eval("java.awt.Color(0x010203)"));

         // re-enable live and confirm the shim works again.
         SreeEnv.setProperty(LegacyJavaShim.GATE_PROPERTY, "true");
         assertEquals(3.0, num(eval("java.awt.Color(0x010203).getBlue()")));
      }
      finally {
         try {
            if(prev == null) {
               SreeEnv.resetProperty(LegacyJavaShim.GATE_PROPERTY, false);
            }
            else {
               SreeEnv.setProperty(LegacyJavaShim.GATE_PROPERTY, prev);
            }
         }
         catch(Throwable ignore) {
         }
      }
   }
}
