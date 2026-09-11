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
 * The modern title-chrome palette suppliers: background, foreground and border color per VizContext.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class VSTitleChromeDefaultsTest {
   @AfterEach
   void reset() {
      SreeEnv.setProperty("viewsheet.modernVisualization", null);
      SreeEnv.setProperty("viewsheet.darkMode", null);
   }

   // dark mode substitutes the dark title chrome
   @Test
   void titleAccessorsDark() {
      SreeEnv.setProperty("viewsheet.modernVisualization", "true");
      SreeEnv.setProperty("viewsheet.darkMode", "true");
      VizContext ctx = VizContext.ofGate();
      assertEquals(0x2D2B30, rgb(VSTitleChromeDefaults.titleBackground(ctx)));
      assertEquals(0xCAC4D0, rgb(VSTitleChromeDefaults.titleForeground(ctx)));
      assertEquals(0x49454F, rgb(VSTitleChromeDefaults.titleBorderColor(ctx)));
   }

   // pins the modern title-chrome palette; a change here is export-visible and must be intentional
   @Test
   void titleBackgroundValue() {
      // equals VSTableStructureDefaults.headerBackground() so title bar and table header match
      assertEquals(0xF1EFEA, rgb(VSTitleChromeDefaults.titleBackground(VizContext.ofGate())));
   }

   @Test
   void titleForegroundValue() {
      // equals the table header / chart label foreground
      assertEquals(0x6A685F, rgb(VSTitleChromeDefaults.titleForeground(VizContext.ofGate())));
   }

   @Test
   void titleBorderMatchesShellBorder() {
      // the shared structural border, equal to VSTableStructureDefaults.headerSeparator()
      assertEquals(0xD9D5CC, rgb(VSTitleChromeDefaults.titleBorderColor(VizContext.ofGate())));
   }

   private static int rgb(Color c) {
      return c.getRGB() & 0xFFFFFF;
   }
}
