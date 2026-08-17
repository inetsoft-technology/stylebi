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
package inetsoft.uql.viewsheet.internal;

import inetsoft.sree.SreeEnv;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class VizContextTest {
   @AfterEach
   void reset() {
      SreeEnv.setProperty("viewsheet.modernVisualization", null);
      SreeEnv.setProperty("viewsheet.darkMode", null);
      SreeEnv.setProperty("viewsheet.density", null);
   }

   @Test
   void legacyIsOffOnEveryAxis() {
      assertFalse(VizContext.LEGACY.modern);
      assertFalse(VizContext.LEGACY.dark);
      assertEquals("dense", VizContext.LEGACY.density);
   }

   @Test
   void ofGateMatchesTheMasterGate() {
      SreeEnv.setProperty("viewsheet.modernVisualization", "false");
      assertFalse(VizContext.ofGate().modern);

      SreeEnv.setProperty("viewsheet.modernVisualization", "true");
      assertTrue(VizContext.ofGate().modern);
   }

   @Test
   void ofGateAgreesWithTheStaticsItReplaces() {
      // the whole safety argument of this phase: ofGate() must equal today's predicates
      for(String modern : new String[]{ "true", "false" }) {
         for(String dark : new String[]{ "true", "false" }) {
            SreeEnv.setProperty("viewsheet.modernVisualization", modern);
            SreeEnv.setProperty("viewsheet.darkMode", dark);
            VizContext ctx = VizContext.ofGate();
            assertEquals(VSDensityDefaults.isModern(), ctx.modern,
                         "modern must match VSDensityDefaults.isModern()");
            assertEquals(VSDensityDefaults.isDark(), ctx.dark,
                         "dark must match VSDensityDefaults.isDark()");
            assertEquals(VSDensityDefaults.mode(), ctx.density,
                         "density must match VSDensityDefaults.mode()");
         }
      }
   }

   @Test
   void darkRequiresModern() {
      SreeEnv.setProperty("viewsheet.modernVisualization", "false");
      SreeEnv.setProperty("viewsheet.darkMode", "true");
      assertFalse(VizContext.ofGate().dark, "dark is a modifier of modern, never standalone");
   }

   @Test
   void densityFallsBackToDense() {
      SreeEnv.setProperty("viewsheet.modernVisualization", "true");
      assertEquals("dense", VizContext.ofGate().density);

      SreeEnv.setProperty("viewsheet.density", "comfortable");
      assertEquals("comfortable", VizContext.ofGate().density);
   }

   @Test
   void ofAMarkIsLegacyWhenTheGateIsOff() {
      // modern = gate && mark != null, so a mark alone can't make it modern
      SreeEnv.setProperty("viewsheet.modernVisualization", "false");
      assertFalse(VizContext.of(VizMark.MODERN_LIGHT).modern);
      assertFalse(VizContext.of(VizMark.MODERN_DARK).modern);
      assertFalse(VizContext.of(VizMark.MODERN_DARK).dark, "dark is never true without modern");
   }

   @Test
   void ofAMarkIsModernWhenTheGateIsOn() {
      SreeEnv.setProperty("viewsheet.modernVisualization", "true");
      assertTrue(VizContext.of(VizMark.MODERN_LIGHT).modern);
      assertFalse(VizContext.of(VizMark.MODERN_LIGHT).dark);
      assertTrue(VizContext.of(VizMark.MODERN_DARK).dark);
   }

   @Test
   void ofAnAbsentMarkIsLegacy() {
      SreeEnv.setProperty("viewsheet.modernVisualization", "true");
      assertFalse(VizContext.of((VizMark) null).modern, "unmarked is never modern");
   }

   @Test
   void ofAnAssemblyReadsItsMark() {
      SreeEnv.setProperty("viewsheet.modernVisualization", "true");
      TextVSAssemblyInfo unmarked = new TextVSAssemblyInfo();
      assertFalse(VizContext.of(unmarked).modern);

      TextVSAssemblyInfo marked = new TextVSAssemblyInfo();
      marked.setVizMark(VizMark.MODERN_DARK);
      assertTrue(VizContext.of(marked).modern);
      assertTrue(VizContext.of(marked).dark);
   }

   @Test
   void ofANullAssemblyIsLegacy() {
      SreeEnv.setProperty("viewsheet.modernVisualization", "true");
      assertFalse(VizContext.of((VSAssemblyInfo) null).modern);
   }

   @Test
   void ofGateNeverReturnsTheLegacySingletonEvenWhenGateIsOff() {
      // descriptor font lines compare identity against LEGACY to mean "is a viewsheet chart" -
      // a value-equal instance must still count as one
      SreeEnv.setProperty("viewsheet.modernVisualization", "false");
      assertNotSame(VizContext.LEGACY, VizContext.ofGate());
   }

   @Test
   void ofANullMarkNeverReturnsTheLegacySingleton() {
      SreeEnv.setProperty("viewsheet.modernVisualization", "false");
      assertNotSame(VizContext.LEGACY, VizContext.of((VizMark) null));
   }

   @Test
   void densityAlwaysComesFromTheOrgNotTheMark() {
      // density is a live preference; the mark decides only whether an assembly honours it
      SreeEnv.setProperty("viewsheet.modernVisualization", "true");
      SreeEnv.setProperty("viewsheet.density", "compact");
      assertEquals("compact", VizContext.of(VizMark.MODERN_LIGHT).density);
      assertEquals("compact", VizContext.of((VizMark) null).density);
   }
}
