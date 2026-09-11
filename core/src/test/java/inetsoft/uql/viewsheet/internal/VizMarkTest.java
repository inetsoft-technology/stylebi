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
class VizMarkTest {
   @AfterEach
   void reset() {
      SreeEnv.setProperty("viewsheet.modernVisualization", null);
      SreeEnv.setProperty("viewsheet.darkMode", null);
   }

   @Test
   void persistedFormRoundTrips() {
      assertEquals("modern-light", VizMark.MODERN_LIGHT.value());
      assertEquals("modern-dark", VizMark.MODERN_DARK.value());
      assertEquals(VizMark.MODERN_LIGHT, VizMark.parse("modern-light"));
      assertEquals(VizMark.MODERN_DARK, VizMark.parse("modern-dark"));
   }

   @Test
   void absentOrUnrecognizedParsesToUnmarked() {
      // unmarked is the safe direction: a future state read by an older build degrades to legacy
      assertNull(VizMark.parse(null));
      assertNull(VizMark.parse(""));
      assertNull(VizMark.parse("modern"));
      assertNull(VizMark.parse("MODERN_LIGHT"));
      assertNull(VizMark.parse("modern-sepia"));
   }

   @Test
   void gateOffStampsNothing() {
      SreeEnv.setProperty("viewsheet.modernVisualization", "false");
      assertNull(VizMark.fromGate());

      // dark alone is not a gate: isDark() already requires the master gate
      SreeEnv.setProperty("viewsheet.darkMode", "true");
      assertNull(VizMark.fromGate());
   }

   @Test
   void gateOnStampsTheTuple() {
      SreeEnv.setProperty("viewsheet.modernVisualization", "true");
      assertEquals(VizMark.MODERN_LIGHT, VizMark.fromGate());

      SreeEnv.setProperty("viewsheet.darkMode", "true");
      assertEquals(VizMark.MODERN_DARK, VizMark.fromGate());
   }
}
