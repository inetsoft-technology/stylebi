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
 * The modern title-chrome palette and the read-time substitution the live model builder and the
 * export helpers apply. Gate off leaves the legacy title colors; gate on substitutes the modern
 * neutral only where the color is still the legacy default, so a user title format and a customer
 * format.css TITLE class both still win.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class VSTitleChromeDefaultsTest {
   // pins the modern title-chrome palette; the values are substituted onto the title format at read
   // time (live model + export), so a change here is an export-visible change and must be intentional
   @Test
   void titleBackgroundValue() {
      // equals VSTableStructureDefaults.headerBackground() so title bar and table header match
      assertEquals(0xF1EFEA, rgb(VSTitleChromeDefaults.titleBackground()));
   }

   @Test
   void titleForegroundValue() {
      // equals the table header / chart label foreground
      assertEquals(0x6A685F, rgb(VSTitleChromeDefaults.titleForeground()));
   }

   @Test
   void titleBorderMatchesShellBorder() {
      // the shared structural border, equal to VSTableStructureDefaults.headerSeparator()
      assertEquals(0xD9D5CC, rgb(VSTitleChromeDefaults.titleBorderColor()));
   }

   @Test
   void gateOffLeavesFormatUnchanged() {
      withProperty("viewsheet.modernVisualization", "false", () -> {
         VSCompositeFormat fmt = new VSCompositeFormat();
         fmt.getDefaultFormat().setBackgroundValue("0xf5f5f5");
         assertSame(fmt, VSTitleChromeDefaults.applyModernDefaults(fmt),
                    "gate off returns the original format");
      });
   }

   @Test
   void gateOnModernizesAnyDefaultButPreservesUser() {
      withProperty("viewsheet.modernVisualization", "true", () -> {
         // a white default (e.g. chart) is modernized...
         VSCompositeFormat white = new VSCompositeFormat();
         white.getDefaultFormat().setBackgroundValue("0xffffff");
         assertEquals(0xF1EFEA, rgb(VSTitleChromeDefaults.applyModernDefaults(white).getBackground()),
                      "a white default title bg is modernized");
         // ...as is an unset (transparent) bg (e.g. selection / checkbox) ...
         VSCompositeFormat none = new VSCompositeFormat();
         assertEquals(0xF1EFEA, rgb(VSTitleChromeDefaults.applyModernDefaults(none).getBackground()),
                      "an unset title bg is modernized");
         // ...but a user-set bg still wins (customized, not a bare default)
         VSCompositeFormat user = new VSCompositeFormat();
         user.getUserDefinedFormat().setBackgroundValue("0x123456");
         assertEquals(0x123456, rgb(VSTitleChromeDefaults.applyModernDefaults(user).getBackground()),
                      "a user-set title bg still wins");
      });
   }

   @Test
   void escapeHatchOptsOut() {
      withProperty("viewsheet.modernVisualization", "true", () ->
         withProperty("viewsheet.modernObjectChrome", "false", () -> {
            VSCompositeFormat fmt = new VSCompositeFormat();
            fmt.getDefaultFormat().setBackgroundValue("0xf5f5f5");
            assertSame(fmt, VSTitleChromeDefaults.applyModernDefaults(fmt),
                       "modernObjectChrome=false opts title chrome out while the rest of modern stays on");
         }));
   }

   // end-to-end: a title format seeded like VSAssemblyInfo.setDefaultFormat (default-tier bg =
   // DEFAULT_TITLE_BG 0xf5f5f5, no foreground) must resolve getBackground()/getForeground() to the
   // modern neutrals after applyModernDefaults, and be untouched with the gate off.
   @Test
   void applyModernDefaultsResolvesModernBackground() {
      withProperty("viewsheet.modernVisualization", "true", () -> {
         VSCompositeFormat fmt = new VSCompositeFormat();
         fmt.getDefaultFormat().setBackgroundValue("0xf5f5f5");
         assertEquals(0xF5F5F5, rgb(fmt.getBackground()), "precondition: legacy default bg");

         VSCompositeFormat modern = VSTitleChromeDefaults.applyModernDefaults(fmt);
         assertNotSame(fmt, modern, "a modern clone is returned");
         assertEquals(0xF1EFEA, rgb(modern.getBackground()), "getBackground() resolves modern");
         assertEquals(0x6A685F, rgb(modern.getForeground()), "getForeground() resolves modern");
         // original untouched (no serialization / mutation)
         assertEquals(0xF5F5F5, rgb(fmt.getBackground()), "source format not mutated");
      });
   }

   @Test
   void applyModernDefaultsNoopWhenGateOff() {
      withProperty("viewsheet.modernVisualization", "false", () -> {
         VSCompositeFormat fmt = new VSCompositeFormat();
         fmt.getDefaultFormat().setBackgroundValue("0xf5f5f5");
         assertSame(fmt, VSTitleChromeDefaults.applyModernDefaults(fmt), "gate off returns original");
      });
   }

   // the in-place variant used by the export prepareAssembly hook mutates the default tier directly
   @Test
   void applyModernDefaultsInPlaceMutatesDefaultTier() {
      withProperty("viewsheet.modernVisualization", "true", () -> {
         VSCompositeFormat fmt = new VSCompositeFormat();
         fmt.getDefaultFormat().setBackgroundValue("0xf5f5f5");
         VSTitleChromeDefaults.applyModernDefaultsInPlace(fmt);
         assertEquals(0xF1EFEA, rgb(fmt.getBackground()), "in-place modern bg");
         assertEquals(0x6A685F, rgb(fmt.getForeground()), "in-place modern fg");
      });
   }

   @Test
   void applyModernDefaultsInPlaceNoopGateOff() {
      withProperty("viewsheet.modernVisualization", "false", () -> {
         VSCompositeFormat fmt = new VSCompositeFormat();
         fmt.getDefaultFormat().setBackgroundValue("0xf5f5f5");
         VSTitleChromeDefaults.applyModernDefaultsInPlace(fmt);
         assertEquals(0xF5F5F5, rgb(fmt.getBackground()), "gate off unchanged");
      });
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
