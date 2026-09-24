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

import java.awt.Insets;

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
   void storedDataRowHeightMatrix() {
      // STORED, not rendered - DensityRowHeightInvariantTest owns the rendered contract
      assertEquals(16, VSDensityDefaults.rowHeightForMode("comfortable"));
      assertEquals(16, VSDensityDefaults.rowHeightForMode("compact"));
      assertEquals(14, VSDensityDefaults.rowHeightForMode("dense"));
   }

   @Test
   void storedHeaderRowHeightMatrix() {
      assertEquals(18, VSDensityDefaults.headerRowHeightForMode("comfortable"));
      assertEquals(18, VSDensityDefaults.headerRowHeightForMode("compact"));
      assertEquals(16, VSDensityDefaults.headerRowHeightForMode("dense"));
   }

   @Test
   void unrecognizedModeFallsBackToDense() {
      // values are case-sensitive lowercase; anything else falls back to dense
      assertEquals(14, VSDensityDefaults.rowHeightForMode("Comfortable"));
      assertEquals(16, VSDensityDefaults.headerRowHeightForMode("bogus"));
   }

   @Test
   void denseControlHeightIsNotLegacyDefh() {
      // control height is the one tier that steps up even at dense - a standalone form input
      // reads as cramped at the tightest data-row height, unlike row/header/title height
      assertEquals(24, VSDensityDefaults.controlHeightForMode("dense"));
      assertNotEquals(AssetUtil.defh, VSDensityDefaults.controlHeightForMode("dense"));
   }

   @Test
   void compactAndComfortableControlHeight() {
      assertEquals(28, VSDensityDefaults.controlHeightForMode("compact"));
      assertEquals(30, VSDensityDefaults.controlHeightForMode("comfortable"));
   }

   @Test
   void unrecognizedControlModeFallsBackToDense() {
      assertEquals(24, VSDensityDefaults.controlHeightForMode("bogus"));
   }

   @Test
   void controlHeightIsDefhWhenGateIsOff() {
      // modern is now the shipped default for a new install, so the gate must be turned off
      // explicitly - VizContext.ofGate() no longer resolves to legacy just because the property
      // is unset.
      SreeEnv.setProperty("viewsheet.modernVisualization", "false");
      assertEquals(AssetUtil.defh, VSDensityDefaults.controlHeight(VizContext.ofGate()));
   }

   @Test
   void aLegacyContextYieldsLegacyControlHeight() {
      assertEquals(AssetUtil.defh, VSDensityDefaults.controlHeight(VizContext.LEGACY));
   }

   @Test
   void aModernContextYieldsItsDensityControlHeight() {
      SreeEnv.setProperty("viewsheet.modernVisualization", "true");
      SreeEnv.setProperty("viewsheet.density", "comfortable");
      assertEquals(30, VSDensityDefaults.controlHeight(VizContext.of(VizMark.MODERN_LIGHT)));
   }

   @Test
   void isControlHeightMatchesAllThreeTiers() {
      assertTrue(VSDensityDefaults.isControlHeight(24), "dense");
      assertTrue(VSDensityDefaults.isControlHeight(28), "compact");
      assertTrue(VSDensityDefaults.isControlHeight(30), "comfortable");
   }

   @Test
   void isControlHeightRejectsEverythingElse() {
      assertFalse(VSDensityDefaults.isControlHeight(AssetUtil.defh));
      assertFalse(VSDensityDefaults.isControlHeight(20));
      assertFalse(VSDensityDefaults.isControlHeight(40));
      assertFalse(VSDensityDefaults.isControlHeight(0));
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
      // of(VizMark) does not consult the gate: modern = mark != null
      SreeEnv.setProperty("viewsheet.modernVisualization", "true");
      SreeEnv.setProperty("viewsheet.density", "comfortable");
      VizContext ctx = VizContext.of(VizMark.MODERN_LIGHT);
      // row/header are the STORED heights; the cell padding is added at render
      assertEquals(16, VSDensityDefaults.rowHeight(ctx));
      assertEquals(18, VSDensityDefaults.headerRowHeight(ctx));
      assertEquals(30, VSDensityDefaults.titleHeight(ctx));
      // the selection family keeps the pre-rebalance matrix
      assertEquals(28, VSDensityDefaults.cellHeight(ctx));
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

   @Test
   void chartPaddingMatrix() {
      assertEquals(new Insets(16, 16, 16, 16), VSDensityDefaults.chartPaddingForMode("comfortable"));
      assertEquals(new Insets(12, 12, 12, 12), VSDensityDefaults.chartPaddingForMode("compact"));
      assertEquals(new Insets(8, 8, 8, 8), VSDensityDefaults.chartPaddingForMode("dense"));
   }

   @Test
   void compactChartPaddingHoldsTheShippedFlatInset() {
      // compact is the org default (defaults.properties: viewsheet.density=compact), so holding
      // 12 here is what makes a default-density dashboard reflow nothing
      assertEquals(new Insets(12, 12, 12, 12), VSDensityDefaults.chartPaddingForMode("compact"));
   }

   @Test
   void tablePaddingSharesTheChartMatrix() {
      // one card inset concept, one set of numbers - asserted literally rather than against
      // chartPaddingForMode, since tablePaddingForMode is just a delegation to it and comparing
      // the two can never fail
      assertEquals(new Insets(16, 16, 16, 16), VSDensityDefaults.tablePaddingForMode("comfortable"));
      assertEquals(new Insets(12, 12, 12, 12), VSDensityDefaults.tablePaddingForMode("compact"));
      assertEquals(new Insets(8, 8, 8, 8), VSDensityDefaults.tablePaddingForMode("dense"));
   }

   @Test
   void cellPaddingMatrix() {
      assertEquals(new Insets(6, 8, 6, 8), VSDensityDefaults.cellPaddingForMode("comfortable"));
      assertEquals(new Insets(4, 6, 4, 6), VSDensityDefaults.cellPaddingForMode("compact"));
      assertEquals(new Insets(3, 4, 3, 4), VSDensityDefaults.cellPaddingForMode("dense"));
   }

   @Test
   void unrecognizedPaddingModeFallsBackToDense() {
      assertEquals(new Insets(8, 8, 8, 8), VSDensityDefaults.chartPaddingForMode("Comfortable"));
      assertEquals(new Insets(3, 4, 3, 4), VSDensityDefaults.cellPaddingForMode("bogus"));
   }

   @Test
   void paddingAccessorsReturnFreshInstances() {
      // Insets is mutable; a shared constant would let one caller's edit reach every other
      Insets first = VSDensityDefaults.chartPaddingForMode("compact");
      Insets second = VSDensityDefaults.chartPaddingForMode("compact");
      assertNotSame(first, second);

      first.left = 99;
      assertEquals(12, VSDensityDefaults.chartPaddingForMode("compact").left);
   }

   @Test
   void unmarkedContextTakesLegacyPadding() {
      SreeEnv.setProperty("viewsheet.density", "comfortable");
      VizContext legacy = VizContext.of((VizMark) null);

      assertEquals(new Insets(10, 10, 10, 10), VSDensityDefaults.chartPadding(legacy));
      assertEquals(new Insets(0, 0, 0, 0), VSDensityDefaults.tablePadding(legacy));
      assertNull(VSDensityDefaults.cellPadding(legacy));
   }

   @Test
   void markedContextTakesTheDensityPadding() {
      SreeEnv.setProperty("viewsheet.density", "comfortable");
      VizContext modern = VizContext.of(VizMark.MODERN_LIGHT);

      assertEquals(new Insets(16, 16, 16, 16), VSDensityDefaults.chartPadding(modern));
      assertEquals(new Insets(16, 16, 16, 16), VSDensityDefaults.tablePadding(modern));
      assertEquals(new Insets(6, 8, 6, 8), VSDensityDefaults.cellPadding(modern));
   }
}
