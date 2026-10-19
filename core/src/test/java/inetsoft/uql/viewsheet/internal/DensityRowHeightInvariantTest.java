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

import inetsoft.report.composition.VSTableLens;
import inetsoft.sree.SreeEnv;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import inetsoft.test.XTableUtil;
import inetsoft.uql.CompositeValue;
import inetsoft.uql.asset.internal.AssetUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.awt.Insets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * What a reader sees is the density matrix - 28/24/20 for data rows and 30/26/22 for headers -
 * whatever padding the table carries. A table stores the rendered height less its seeded cell
 * padding and render adds the padding back, so the sum holds even when the padding was seeded
 * under a tier other than the one in force: an org density change reseeds nothing. An author
 * padding adds to this tier's content box, not to a box sized for the tier that seeded it.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class DensityRowHeightInvariantTest {
   @AfterEach
   void reset() {
      SreeEnv.setProperty("viewsheet.density", null);
   }

   @Test
   void renderedRowHeightsMatchTheMatrixAtEveryTier() {
      assertRendered("comfortable", "comfortable", 28, 30);
      assertRendered("compact", "compact", 24, 26);
      assertRendered("dense", "dense", 20, 22);
   }

   @Test
   void denseRenderedRowStillEqualsTheLegacyDefault() {
      // the anchoring promise: at dense, a marked table's row is the height it always was
      assertRendered("dense", "dense", AssetUtil.defh, 22);
   }

   @Test
   void paddingSeededUnderAnotherTierRendersTheTierInForce() {
      assertRendered("compact", "dense", 20, 22);
      assertRendered("compact", "comfortable", 28, 30);
      assertRendered("dense", "comfortable", 28, 30);
      assertRendered("comfortable", "dense", 20, 22);
   }

   @Test
   void aTableWithNoSeededPaddingRendersTheMatrix() {
      // an asset saved before the field existed carries no padding at all
      TableVSAssemblyInfo info = new TableVSAssemblyInfo();
      info.setCellPadding(null, CompositeValue.Type.DEFAULT);

      assertEquals(24, renderedDataRow(info, "compact"));
   }

   @Test
   void anAuthorPaddingAddsToTheRenderedHeight() {
      // additive, as a stylesheet's padding is: 24 - 8 seeded + 20 authored
      TableVSAssemblyInfo info = seededAt("compact");
      info.setCellPadding(new Insets(10, 6, 10, 6), CompositeValue.Type.USER);

      assertEquals(36, renderedDataRow(info, "compact"));
   }

   @Test
   void anAuthorPaddingRendersTheTierInForceWhateverTierSeededIt() {
      // no reseed between seed and render: the org-density path. 10/6/10/6 adds 20 to each tier's
      // content box
      assertAuthorRendered("compact", "comfortable", 36, 38);
      assertAuthorRendered("comfortable", "dense", 34, 36);
      assertAuthorRendered("dense", "compact", 36, 38);
   }

   @Test
   void aSmallAuthorPaddingIsNotClippedByAShrinkingTier() {
      TableVSAssemblyInfo info = seededAt("comfortable");
      info.setCellPadding(new Insets(1, 1, 1, 1), CompositeValue.Type.USER);

      assertEquals(16, renderedDataRow(info, "dense"));
   }

   @Test
   void anAuthorPaddingSurvivesADashboardReseedOnTheTier() {
      // a dashboard density change re-runs the seed, which leaves the DEFAULT tier alone under an
      // author value - the row must not depend on it
      TableVSAssemblyInfo info = seededAt("compact");
      info.setCellPadding(new Insets(10, 6, 10, 6), CompositeValue.Type.USER);
      info.seedChromeDefaults(contextAt("comfortable"));

      assertEquals(new Insets(4, 6, 4, 6), info.getDefaultCellPadding(), "precondition: DEFAULT kept");
      assertEquals(36, renderedDataRow(info, "comfortable"));
      assertEquals(38, renderedHeaderRow(info, "comfortable"));
   }

   @Test
   void anAuthorPaddingSetBeforeModernizeRendersTheTier() {
      // set while unmarked, so Modernize's seed skips it and no DEFAULT tier is ever written
      TableVSAssemblyInfo info = new TableVSAssemblyInfo();
      info.seedChromeDefaults(VizContext.of((VizMark) null));
      info.setCellPadding(new Insets(4, 6, 4, 6), CompositeValue.Type.USER);
      info.seedChromeDefaults(contextAt("compact"));

      assertNull(info.getDefaultCellPadding(), "precondition: no DEFAULT tier was written");
      assertEquals(24, renderedDataRow(info, "compact"));
      assertEquals(26, renderedHeaderRow(info, "compact"));
      assertEquals(22, renderedDataRow(info, "dense"));
      assertEquals(24, renderedDataRow(info, "comfortable"));
   }

   @Test
   void anAuthorPaddingEqualToTheTierRendersTheTier() {
      TableVSAssemblyInfo info = seededAt("dense");
      info.setCellPadding(new Insets(6, 8, 6, 8), CompositeValue.Type.USER);

      assertEquals(28, renderedDataRow(info, "comfortable"));
      assertEquals(30, renderedHeaderRow(info, "comfortable"));
   }

   @Test
   void onlyTheVerticalSidesOfAnAuthorPaddingReachTheRow() {
      // 10 on top alone: comfortable's content box 16 plus 10
      TableVSAssemblyInfo info = seededAt("compact");
      info.setCellPadding(new Insets(10, 0, 0, 0), CompositeValue.Type.USER);

      assertEquals(26, renderedDataRow(info, "comfortable"));
   }

   @Test
   void crosstabAndCalcTableTakeTheSameRule() {
      TableDataVSAssemblyInfo[] infos = { new CrosstabVSAssemblyInfo(), new CalcTableVSAssemblyInfo() };

      for(TableDataVSAssemblyInfo info : infos) {
         String label = info.getClass().getSimpleName();
         info.seedChromeDefaults(contextAt("compact"));
         info.setCellPadding(new Insets(10, 6, 10, 6), CompositeValue.Type.USER);

         assertEquals(36, renderedDataRow(info, "comfortable"), label + " data row");
         assertEquals(38, renderedHeaderRow(info, "comfortable"), label + " header row");
      }
   }

   @Test
   void aTableTheDensityOwnsStillLeavesOutItsSeed() {
      // with no author value the cell draws the DEFAULT tier, so that is what the stored height
      // leaves out, whatever tier is in force
      TableVSAssemblyInfo info = seededAt("compact");
      VizContext ctx = contextAt("comfortable");

      assertEquals(8, VSDensityDefaults.excludedCellPaddingHeight(ctx, info));
      assertEquals(20, VSDensityDefaults.rowHeight(ctx, info));
      assertEquals(28, renderedDataRow(info, "comfortable"));
   }

   @Test
   void anUnmarkedContextLeavesNothingOut() {
      TableVSAssemblyInfo info = seededAt("compact");
      info.setCellPadding(new Insets(10, 6, 10, 6), CompositeValue.Type.USER);

      assertEquals(0, VSDensityDefaults.excludedCellPaddingHeight(VizContext.of((VizMark) null), info));
   }

   private void assertAuthorRendered(String seedMode, String renderMode, int data, int header) {
      TableVSAssemblyInfo info = seededAt(seedMode);
      info.setCellPadding(new Insets(10, 6, 10, 6), CompositeValue.Type.USER);
      String label = "author padding, " + seedMode + " seeded, " + renderMode + " rendered";

      assertEquals(data, renderedDataRow(info, renderMode), label + " data row");
      assertEquals(header, renderedHeaderRow(info, renderMode), label + " header row");
   }

   private int renderedHeaderRow(TableDataVSAssemblyInfo info, String renderMode) {
      VizContext ctx = contextAt(renderMode);
      VSTableLens lens = new VSTableLens(XTableUtil.getDefaultTableLens());
      return VSDensityDefaults.headerRowHeight(ctx, info) + lens.getRowPadding(0, info);
   }

   private void assertRendered(String seedMode, String renderMode, int data, int header) {
      TableVSAssemblyInfo info = seededAt(seedMode);
      VizContext ctx = contextAt(renderMode);
      VSTableLens lens = new VSTableLens(XTableUtil.getDefaultTableLens());
      String label = seedMode + " seeded, " + renderMode + " rendered";

      assertEquals(data, VSDensityDefaults.rowHeight(ctx, info) + lens.getRowPadding(1, info),
                   label + " data row");
      assertEquals(header,
                   VSDensityDefaults.headerRowHeight(ctx, info) + lens.getRowPadding(0, info),
                   label + " header row");
   }

   private int renderedDataRow(TableDataVSAssemblyInfo info, String renderMode) {
      VizContext ctx = contextAt(renderMode);
      VSTableLens lens = new VSTableLens(XTableUtil.getDefaultTableLens());
      return VSDensityDefaults.rowHeight(ctx, info) + lens.getRowPadding(1, info);
   }

   private TableVSAssemblyInfo seededAt(String mode) {
      TableVSAssemblyInfo info = new TableVSAssemblyInfo();
      info.seedChromeDefaults(contextAt(mode));
      return info;
   }

   private VizContext contextAt(String mode) {
      SreeEnv.setProperty("viewsheet.density", mode);
      return VizContext.of(VizMark.MODERN_LIGHT);
   }
}
