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
import inetsoft.uql.viewsheet.BorderColors;
import inetsoft.uql.viewsheet.VSCompositeFormat;
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
 * (byte-identical export), gate on returns the modern warm-neutrals that match vs-slider.component.scss,
 * and the shared modernObjectChrome escape hatch opts out while the rest of modern stays on.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class VSOutputChromeDefaultsTest {
   // gate off must return the exact pre-modern VSSlider constants (ARGB, so the tick's ~38% alpha is
   // preserved) — this is the export parity guarantee
   @Test
   void gateOffReturnsLegacySliderColors() {
      withProperty("viewsheet.modernVisualization", "false", () -> {
         assertEquals(new Color(224, 224, 224).getRGB(),
                      VSOutputChromeDefaults.sliderInactiveTrack().getRGB(), "inactive track");
         assertEquals(new Color(158, 158, 158).getRGB(),
                      VSOutputChromeDefaults.sliderActiveTrack().getRGB(), "active track");
         assertEquals(new Color(158, 158, 158).getRGB(),
                      VSOutputChromeDefaults.sliderHandle().getRGB(), "handle");
         assertEquals(new Color(0, 0, 0, 97).getRGB(),
                      VSOutputChromeDefaults.sliderTick().getRGB(), "tick keeps ~38% alpha");
      });
   }

   // gate on pins the modern warm-neutrals; a change here is an export-visible change
   @Test
   void gateOnReturnsModernSliderColors() {
      withProperty("viewsheet.modernVisualization", "true", () -> {
         assertEquals(0xE8E5DE, rgb(VSOutputChromeDefaults.sliderInactiveTrack()), "inactive track");
         assertEquals(0xC8C2B7, rgb(VSOutputChromeDefaults.sliderActiveTrack()), "active track");
         assertEquals(0x6A685F, rgb(VSOutputChromeDefaults.sliderHandle()), "handle");
         assertEquals(0x6A685F, rgb(VSOutputChromeDefaults.sliderTick()), "tick");
      });
   }

   // the shared object-chrome escape hatch opts slider chrome out even with modern on
   @Test
   void escapeHatchOptsOut() {
      withProperty("viewsheet.modernVisualization", "true", () ->
         withProperty("viewsheet.modernObjectChrome", "false", () -> {
            assertFalse(VSOutputChromeDefaults.isModern(), "modernObjectChrome=false opts out");
            assertEquals(new Color(224, 224, 224).getRGB(),
                         VSOutputChromeDefaults.sliderInactiveTrack().getRGB(),
                         "legacy color when opted out");
         }));
   }

   // pins the modern KPI value palette; a change here is an export-visible change
   @Test
   void valuePaletteValues() {
      assertEquals(0x35342F, rgb(VSOutputChromeDefaults.valueForeground()), "primary value foreground");
      assertEquals(0xD9D5CC, rgb(VSOutputChromeDefaults.valueBorderColor()), "value/output border");
   }

   // a bare default value format (like TextVSAssemblyInfo.setDefaultFormat: default-tier fg 0x2b2b2b,
   // border 0xDADADA) resolves to the modern neutrals after applyModernDefaults; the source is untouched
   @Test
   void gateOnModernizesBareValueDefault() {
      withProperty("viewsheet.modernVisualization", "true", () -> {
         VSCompositeFormat fmt = bareValueDefault();
         VSCompositeFormat modern = VSOutputChromeDefaults.applyModernDefaults(fmt);

         assertNotSame(fmt, modern, "a modern clone is returned");
         assertEquals(0x35342F, rgb(modern.getForeground()), "foreground resolves modern");
         assertEquals(0xD9D5CC, rgb(modern.getBorderColors().topColor), "border resolves modern");
         // original untouched (no serialization / mutation)
         assertEquals(0x2B2B2B, rgb(fmt.getForeground()), "source foreground not mutated");
      });
   }

   // a user-set foreground / border (USER tier) still wins over the modern default
   @Test
   void gateOnPreservesUserValue() {
      withProperty("viewsheet.modernVisualization", "true", () -> {
         VSCompositeFormat fmt = bareValueDefault();
         fmt.getUserDefinedFormat().setForegroundValue("0x123456");
         fmt.getUserDefinedFormat().setBorderColorsValue(
            new BorderColors(new Color(0x654321), new Color(0x654321),
                             new Color(0x654321), new Color(0x654321)));

         VSCompositeFormat modern = VSOutputChromeDefaults.applyModernDefaults(fmt);
         assertEquals(0x123456, rgb(modern.getForeground()), "user foreground still wins");
         assertEquals(0x654321, rgb(modern.getBorderColors().topColor), "user border still wins");
      });
   }

   @Test
   void gateOffValueNoop() {
      withProperty("viewsheet.modernVisualization", "false", () -> {
         VSCompositeFormat fmt = bareValueDefault();
         assertSame(fmt, VSOutputChromeDefaults.applyModernDefaults(fmt), "gate off returns original");
         assertEquals(0x2B2B2B, rgb(fmt.getForeground()), "foreground unchanged");
      });
   }

   private static VSCompositeFormat bareValueDefault() {
      VSCompositeFormat fmt = new VSCompositeFormat();
      fmt.getDefaultFormat().setForegroundValue("0x2b2b2b");
      fmt.getDefaultFormat().setBorderColorsValue(
         new BorderColors(new Color(0xDADADA), new Color(0xDADADA),
                          new Color(0xDADADA), new Color(0xDADADA)));
      return fmt;
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
