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
import inetsoft.uql.viewsheet.VSCompositeFormat;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.awt.Color;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class VSCalendarChromeDefaultsTest {
   @AfterEach
   void reset() {
      SreeEnv.setProperty("viewsheet.modernVisualization", null);
      SreeEnv.setProperty("viewsheet.darkMode", null);
   }

   @Test
   void unchangedWhenNotDark() {
      // light modern and legacy leave the calendar body format untouched (same instance back)
      SreeEnv.setProperty("viewsheet.modernVisualization", "true");
      VSCompositeFormat fmt = new VSCompositeFormat();
      assertSame(fmt, VSCalendarChromeDefaults.applyModernDefaults(fmt, VizContext.ofGate()));
   }

   @Test
   void darkSubstitutesBareDefault() {
      SreeEnv.setProperty("viewsheet.modernVisualization", "true");
      SreeEnv.setProperty("viewsheet.darkMode", "true");
      VSCompositeFormat fmt = new VSCompositeFormat();
      VSCompositeFormat out = VSCalendarChromeDefaults.applyModernDefaults(fmt, VizContext.ofGate());
      assertNotSame(fmt, out, "returns a clone, never mutates the source");
      assertEquals(0xE6E0E9, rgb(out.getForeground()));
      assertEquals(0x252428, rgb(out.getBackground()));
   }

   @Test
   void darkPreservesUserForeground() {
      SreeEnv.setProperty("viewsheet.modernVisualization", "true");
      SreeEnv.setProperty("viewsheet.darkMode", "true");
      VSCompositeFormat fmt = new VSCompositeFormat();
      fmt.getUserDefinedFormat().setForegroundValue("0x123456");
      VSCompositeFormat out = VSCalendarChromeDefaults.applyModernDefaults(fmt, VizContext.ofGate());
      assertEquals(0x123456, rgb(out.getForeground()), "a user foreground still wins in dark");
   }

   private static int rgb(Color c) {
      return c.getRGB() & 0xFFFFFF;
   }
}
