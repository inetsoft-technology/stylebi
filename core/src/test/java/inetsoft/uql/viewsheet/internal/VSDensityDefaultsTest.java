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
import inetsoft.uql.asset.internal.AssetUtil;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class VSDensityDefaultsTest {
   @AfterEach
   void reset() {
      SreeEnv.setProperty("viewsheet.modernVisualization", null);
      SreeEnv.setProperty("viewsheet.darkMode", null);
      SreeEnv.setProperty("viewsheet.density", null);
   }

   @Test
   void unsetResolvesCompact() {
      // the shipped default, not the legacy dense mode
      SreeEnv.setProperty("viewsheet.density", null);
      assertEquals("compact", VSDensityDefaults.mode());
   }

   @Test
   void explicitDenseStillWins() {
      // an org that pinned dense stays on dense no matter what the shipped default is
      SreeEnv.setProperty("viewsheet.density", "dense");
      assertEquals("dense", VSDensityDefaults.mode());
   }

   @Test
   void denseModeMatchesLegacyDataRowHeight() {
      // dense == today's default, so enabling modern at the default mode reflows nothing
      assertEquals(AssetUtil.defh, VSDensityDefaults.rowHeightForMode("dense"));
      assertEquals(20, VSDensityDefaults.rowHeightForMode("dense"));
      assertEquals(22, VSDensityDefaults.headerRowHeightForMode("dense"));
   }

   @Test
   void compactMode() {
      assertEquals(24, VSDensityDefaults.rowHeightForMode("compact"));
      assertEquals(26, VSDensityDefaults.headerRowHeightForMode("compact"));
   }

   @Test
   void comfortableMode() {
      assertEquals(28, VSDensityDefaults.rowHeightForMode("comfortable"));
      assertEquals(30, VSDensityDefaults.headerRowHeightForMode("comfortable"));
   }

   @Test
   void unrecognizedModeFallsBackToDense() {
      // values are case-sensitive lowercase; anything else falls back to dense
      assertEquals(20, VSDensityDefaults.rowHeightForMode("Comfortable"));
      assertEquals(22, VSDensityDefaults.headerRowHeightForMode("bogus"));
   }

   @Test
   void normalizeModeKeepsRecognizedValues() {
      assertEquals("comfortable", VSDensityDefaults.normalizeMode("comfortable"));
      assertEquals("compact", VSDensityDefaults.normalizeMode("compact"));
      assertEquals("dense", VSDensityDefaults.normalizeMode("dense"));
   }

   @Test
   void normalizeModeClampsUnrecognizedToDense() {
      // guards the EM setModel write against hand-crafted API values
      assertEquals("dense", VSDensityDefaults.normalizeMode("Comfortable"));
      assertEquals("dense", VSDensityDefaults.normalizeMode("bogus"));
      assertEquals("dense", VSDensityDefaults.normalizeMode(""));
      assertEquals("dense", VSDensityDefaults.normalizeMode(null));
   }

   @Test
   void isDarkFalseByDefault() {
      assertFalse(VSDensityDefaults.isDark());
   }

   @Test
   void isDarkRequiresModern() {
      // dark alone, without modern, is inert
      SreeEnv.setProperty("viewsheet.modernVisualization", "false");
      SreeEnv.setProperty("viewsheet.darkMode", "true");
      assertFalse(VSDensityDefaults.isDark());
   }

   @Test
   void isDarkOnWhenModernAndDarkBothOn() {
      SreeEnv.setProperty("viewsheet.modernVisualization", "true");
      SreeEnv.setProperty("viewsheet.darkMode", "true");
      assertTrue(VSDensityDefaults.isDark());
   }

   @Test
   void isDarkOffWhenModernOnButDarkOff() {
      SreeEnv.setProperty("viewsheet.modernVisualization", "true");
      assertFalse(VSDensityDefaults.isDark());
   }

   @Test
   void titleHeightIsDefhWhenGateIsOff() {
      SreeEnv.setProperty("viewsheet.modernVisualization", "false");
      assertEquals(AssetUtil.defh, VSDensityDefaults.titleHeight(VizContext.ofGate()));
   }

   @Test
   void titleHeightIsDefhUnderDense() {
      SreeEnv.setProperty("viewsheet.modernVisualization", "true");
      SreeEnv.setProperty("viewsheet.density", "dense");
      assertEquals(AssetUtil.defh, VSDensityDefaults.titleHeight(VizContext.ofGate()));
   }

   @Test
   void titleHeightGrowsToHoldTheStripUnderCompact() {
      SreeEnv.setProperty("viewsheet.modernVisualization", "true");
      SreeEnv.setProperty("viewsheet.density", "compact");
      assertEquals(26, VSDensityDefaults.titleHeight(VizContext.ofGate()));
   }

   @Test
   void titleHeightIsThirtyUnderComfortable() {
      SreeEnv.setProperty("viewsheet.modernVisualization", "true");
      SreeEnv.setProperty("viewsheet.density", "comfortable");
      assertEquals(30, VSDensityDefaults.titleHeight(VizContext.ofGate()));
   }

   @Test
   void aLegacyContextYieldsLegacyHeights() {
      assertEquals(AssetUtil.defh, VSDensityDefaults.rowHeight(VizContext.LEGACY));
      assertEquals(AssetUtil.defh, VSDensityDefaults.headerRowHeight(VizContext.LEGACY));
      assertEquals(AssetUtil.defh, VSDensityDefaults.cellHeight(VizContext.LEGACY));
      assertEquals(AssetUtil.defh, VSDensityDefaults.titleHeight(VizContext.LEGACY));
   }

   @Test
   void aModernContextYieldsItsDensityHeights() {
      // of(VizMark) also requires the gate: modern = gate && mark != null
      SreeEnv.setProperty("viewsheet.modernVisualization", "true");
      SreeEnv.setProperty("viewsheet.density", "comfortable");
      VizContext ctx = VizContext.of(VizMark.MODERN_LIGHT);
      assertEquals(28, VSDensityDefaults.rowHeight(ctx));
      assertEquals(30, VSDensityDefaults.headerRowHeight(ctx));
      assertEquals(30, VSDensityDefaults.titleHeight(ctx));
   }

   @Test
   void titleHeightFollowsDensityForAMarkedDefaultAssembly() {
      SreeEnv.setProperty("viewsheet.density", "compact");
      ChartVSAssemblyInfo info = new ChartVSAssemblyInfo();
      info.setVizMark(VizMark.MODERN_LIGHT);
      assertEquals(26, VSDensityDefaults.titleHeight(info, AssetUtil.defh));
   }

   @Test
   void titleHeightResolvesEachDensityTier() {
      ChartVSAssemblyInfo info = new ChartVSAssemblyInfo();
      info.setVizMark(VizMark.MODERN_LIGHT);

      SreeEnv.setProperty("viewsheet.density", "dense");
      assertEquals(AssetUtil.defh, VSDensityDefaults.titleHeight(info, AssetUtil.defh), "dense");
      SreeEnv.setProperty("viewsheet.density", "compact");
      assertEquals(26, VSDensityDefaults.titleHeight(info, AssetUtil.defh), "compact");
      SreeEnv.setProperty("viewsheet.density", "comfortable");
      assertEquals(30, VSDensityDefaults.titleHeight(info, AssetUtil.defh), "comfortable");
   }

   @Test
   void titleHeightKeepsStoredWhenUnmarked() {
      SreeEnv.setProperty("viewsheet.density", "comfortable");
      ChartVSAssemblyInfo info = new ChartVSAssemblyInfo();
      assertEquals(AssetUtil.defh, VSDensityDefaults.titleHeight(info, AssetUtil.defh));
   }

   @Test
   void titleHeightKeepsStoredWhenTheAuthorSetIt() {
      SreeEnv.setProperty("viewsheet.density", "comfortable");
      ChartVSAssemblyInfo info = new ChartVSAssemblyInfo();
      info.setVizMark(VizMark.MODERN_LIGHT);
      info.setUserTitleHeight(true);
      assertEquals(AssetUtil.defh, VSDensityDefaults.titleHeight(info, AssetUtil.defh));
   }

   @Test
   void titleHeightKeepsStoredWhenNotAtTheLegacyDefault() {
      SreeEnv.setProperty("viewsheet.density", "comfortable");
      ChartVSAssemblyInfo info = new ChartVSAssemblyInfo();
      info.setVizMark(VizMark.MODERN_LIGHT);
      assertEquals(25, VSDensityDefaults.titleHeight(info, 25));
   }

   @Test
   void titleHeightAdmitsTheCalendarAtItsOwnLegacyDefault() {
      SreeEnv.setProperty("viewsheet.density", "compact");
      CalendarVSAssemblyInfo info = new CalendarVSAssemblyInfo();
      info.setVizMark(VizMark.MODERN_LIGHT);
      assertEquals(36, info.getLegacyTitleHeight(), "the calendar's legacy lane");
      assertEquals(26, VSDensityDefaults.titleHeight(info, 36));
   }

   @Test
   void titleHeightLeavesAnUnmarkedCalendarAlone() {
      SreeEnv.setProperty("viewsheet.density", "compact");
      CalendarVSAssemblyInfo info = new CalendarVSAssemblyInfo();
      assertEquals(36, VSDensityDefaults.titleHeight(info, 36));
   }

   @Test
   void titleHeightShrinksAMarkedCalendarAtDense() {
      // the one place dense stops equalling legacy: the calendar's legacy lane was never defh
      SreeEnv.setProperty("viewsheet.density", "dense");
      CalendarVSAssemblyInfo info = new CalendarVSAssemblyInfo();
      info.setVizMark(VizMark.MODERN_LIGHT);
      assertEquals(AssetUtil.defh, VSDensityDefaults.titleHeight(info, 36));
   }
}
