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

import java.awt.Color;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The modern slider-chrome palette and its gate. Gate off returns the legacy VSSlider colors exactly
 * (byte-identical export), gate on returns the modern warm-neutrals that match vs-slider.component.scss.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class VSOutputChromeDefaultsTest {
   @AfterEach
   void reset() {
      SreeEnv.setProperty("viewsheet.modernVisualization", null);
      SreeEnv.setProperty("viewsheet.darkMode", null);
   }

   // gate off must return the exact pre-modern VSSlider constants (ARGB, so the tick's ~38% alpha is
   // preserved) — this is the export parity guarantee
   @Test
   void gateOffReturnsLegacySliderColors() {
      withProperty("viewsheet.modernVisualization", "false", () -> {
         VizContext ctx = VizContext.ofGate();
         assertEquals(new Color(224, 224, 224).getRGB(),
                      VSOutputChromeDefaults.sliderInactiveTrack(ctx).getRGB(), "inactive track");
         assertEquals(new Color(158, 158, 158).getRGB(),
                      VSOutputChromeDefaults.sliderActiveTrack(ctx).getRGB(), "active track");
         assertEquals(new Color(158, 158, 158).getRGB(),
                      VSOutputChromeDefaults.sliderHandle(ctx).getRGB(), "handle");
         assertEquals(new Color(0, 0, 0, 97).getRGB(),
                      VSOutputChromeDefaults.sliderTick(ctx).getRGB(), "tick keeps ~38% alpha");
      });
   }

   // gate on pins the modern warm-neutrals; a change here is an export-visible change
   @Test
   void gateOnReturnsModernSliderColors() {
      withProperty("viewsheet.modernVisualization", "true", () -> {
         VizContext ctx = VizContext.ofGate();
         assertEquals(0xE8E5DE, rgb(VSOutputChromeDefaults.sliderInactiveTrack(ctx)), "inactive track");
         assertEquals(0xC8C2B7, rgb(VSOutputChromeDefaults.sliderActiveTrack(ctx)), "active track");
         assertEquals(0x6A685F, rgb(VSOutputChromeDefaults.sliderHandle(ctx)), "handle");
         assertEquals(0x6A685F, rgb(VSOutputChromeDefaults.sliderTick(ctx)), "tick");
      });
   }

   // dark mode substitutes the dark slider neutrals
   @Test
   void sliderChromeDark() {
      SreeEnv.setProperty("viewsheet.modernVisualization", "true");
      SreeEnv.setProperty("viewsheet.darkMode", "true");
      VizContext ctx = VizContext.ofGate();
      assertEquals(0x3A383D, rgb(VSOutputChromeDefaults.sliderInactiveTrack(ctx)));
      assertEquals(0x49454F, rgb(VSOutputChromeDefaults.sliderActiveTrack(ctx)));
      assertEquals(0xCAC4D0, rgb(VSOutputChromeDefaults.sliderHandle(ctx)));
      assertEquals(0xCAC4D0, rgb(VSOutputChromeDefaults.sliderTick(ctx)));
   }

   // dark mode substitutes the dark value foreground/border
   @Test
   void valueChromeDark() {
      SreeEnv.setProperty("viewsheet.modernVisualization", "true");
      SreeEnv.setProperty("viewsheet.darkMode", "true");
      VizContext ctx = VizContext.ofGate();
      assertEquals(0xE6E0E9, rgb(VSOutputChromeDefaults.valueForeground(ctx)));
      assertEquals(0x49454F, rgb(VSOutputChromeDefaults.valueBorderColor(ctx)));
   }

   // the string supplier a seed writes directly must agree with the Color accessor it is derived
   // from, for both the modern and the legacy branch
   @Test
   void valueForegroundValueMatchesTheModernColor() {
      SreeEnv.setProperty("viewsheet.modernVisualization", "true");
      SreeEnv.setProperty("viewsheet.darkMode", "true");
      VizContext ctx = VizContext.ofGate();
      assertEquals("0x" + Integer.toHexString(rgb(VSOutputChromeDefaults.valueForeground(ctx))),
                   VSOutputChromeDefaults.valueForegroundValue(ctx));
   }

   @Test
   void valueForegroundValueIsTheLegacyNearBlackOffTheGate() {
      SreeEnv.setProperty("viewsheet.modernVisualization", "false");
      VizContext ctx = VizContext.ofGate();
      assertEquals("0x2b2b2b", VSOutputChromeDefaults.valueForegroundValue(ctx));
   }

   // pins the modern KPI value palette; a change here is an export-visible change
   @Test
   void valuePaletteValues() {
      VizContext ctx = VizContext.ofGate();
      assertEquals(0x35342F, rgb(VSOutputChromeDefaults.valueForeground(ctx)), "primary value foreground");
      assertEquals(0xD9D5CC, rgb(VSOutputChromeDefaults.valueBorderColor(ctx)), "value/output border");
   }

   private static void withProperty(String name, String value, Runnable body) {
      String saved = SreeEnv.getProperty(name);

      try {
         SreeEnv.setProperty(name, value);
         body.run();
      }
      finally {
         SreeEnv.setProperty(name, saved);
      }
   }

   private static int rgb(Color c) {
      return c.getRGB() & 0xFFFFFF;
   }
}
