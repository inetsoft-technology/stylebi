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

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class VSTableStructureDefaultsTest {
   @AfterEach
   void reset() {
      SreeEnv.setProperty("viewsheet.modernVisualization", null);
      SreeEnv.setProperty("viewsheet.darkMode", null);
   }

   // pins the modern structure palette; the values are overlaid onto the Default Style clone in
   // DataVSAQuery, so a change here is an export-visible change and must be intentional
   @Test
   void gridlineColorValue() {
      assertEquals(0xE8E5DE, rgb(VSTableStructureDefaults.gridlineColor()));
   }

   @Test
   void headerBackgroundValue() {
      assertEquals(0xF1EFEA, rgb(VSTableStructureDefaults.headerBackground()));
   }

   @Test
   void headerForegroundValue() {
      assertEquals(0x6A685F, rgb(VSTableStructureDefaults.headerForeground()));
   }

   @Test
   void headerSeparatorMatchesShellBorder() {
      // stronger than the gridline; equals $shell-border-default so the header rule matches the product
      assertEquals(0xD9D5CC, rgb(VSTableStructureDefaults.headerSeparator()));
   }

   @Test
   void totalBackgroundValue() {
      assertEquals(0xE9E4DA, rgb(VSTableStructureDefaults.totalBackground()));
   }

   @Test
   void subtotalBackgroundValue() {
      // interior group subtotals; lighter than the grand-total #E9E4DA so the total hierarchy reads
      assertEquals(0xEEEAE1, rgb(VSTableStructureDefaults.subtotalBackground()));
   }

   @Test
   void gridlineColorDark() {
      SreeEnv.setProperty("viewsheet.modernVisualization", "true");
      SreeEnv.setProperty("viewsheet.darkMode", "true");
      assertEquals(0x3A383D, rgb(VSTableStructureDefaults.gridlineColor()));
   }

   @Test
   void headerBackgroundDark() {
      SreeEnv.setProperty("viewsheet.modernVisualization", "true");
      SreeEnv.setProperty("viewsheet.darkMode", "true");
      assertEquals(0x2D2B30, rgb(VSTableStructureDefaults.headerBackground()));
   }

   @Test
   void headerForegroundDark() {
      SreeEnv.setProperty("viewsheet.modernVisualization", "true");
      SreeEnv.setProperty("viewsheet.darkMode", "true");
      assertEquals(0xCAC4D0, rgb(VSTableStructureDefaults.headerForeground()));
   }

   @Test
   void structuralBandsDark() {
      SreeEnv.setProperty("viewsheet.modernVisualization", "true");
      SreeEnv.setProperty("viewsheet.darkMode", "true");
      assertEquals(0x49454F, rgb(VSTableStructureDefaults.headerSeparator()));
      assertEquals(0x35333A, rgb(VSTableStructureDefaults.totalBackground()));
      assertEquals(0x302E34, rgb(VSTableStructureDefaults.subtotalBackground()));
   }

   @Test
   void bandForegroundNullInLightModern() {
      // light bands keep their default dark-on-light text, so no band foreground is imposed
      SreeEnv.setProperty("viewsheet.modernVisualization", "true");
      assertNull(VSTableStructureDefaults.bandForeground());
   }

   @Test
   void bandForegroundDark() {
      // dark bands need a light text lift (= header foreground) to avoid dark-on-dark totals
      SreeEnv.setProperty("viewsheet.modernVisualization", "true");
      SreeEnv.setProperty("viewsheet.darkMode", "true");
      assertEquals(0xCAC4D0, rgb(VSTableStructureDefaults.bandForeground()));
   }

   @Test
   void bodyInteriorNullInLightModern() {
      // light/legacy keep the shipped body text, transparent body, and light zebra unchanged
      SreeEnv.setProperty("viewsheet.modernVisualization", "true");
      assertNull(VSTableStructureDefaults.bodyForeground());
      assertNull(VSTableStructureDefaults.bodyBackground());
      assertNull(VSTableStructureDefaults.zebraBackground());
   }

   @Test
   void bodyInteriorDark() {
      SreeEnv.setProperty("viewsheet.modernVisualization", "true");
      SreeEnv.setProperty("viewsheet.darkMode", "true");
      assertEquals(0xE6E0E9, rgb(VSTableStructureDefaults.bodyForeground()));
      assertEquals(0x252428, rgb(VSTableStructureDefaults.bodyBackground()));
      assertEquals(0x2D2B30, rgb(VSTableStructureDefaults.zebraBackground()));
   }

   @Test
   void darkInertWithoutModern() {
      // dark set but modern off => still light-modern constants are irrelevant; isDark() is false
      SreeEnv.setProperty("viewsheet.darkMode", "true");
      assertEquals(0xE8E5DE, rgb(VSTableStructureDefaults.gridlineColor()));
   }

   private static int rgb(Color c) {
      return c.getRGB() & 0xFFFFFF;
   }
}
