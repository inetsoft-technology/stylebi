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
package inetsoft.report.composition;

import inetsoft.sree.SreeEnv;
import inetsoft.test.*;
import inetsoft.uql.CompositeValue;
import inetsoft.uql.asset.internal.AssetUtil;
import inetsoft.uql.viewsheet.internal.*;
import inetsoft.util.css.CSSTableStyle;
import inetsoft.util.css.CSSParameter;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.awt.Insets;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A format.css table padding decides where a cell's text sits. It must not leave the row below
 * its density tier.
 *
 * A marked table stores the tier height less its seeded cell padding and render adds the padding
 * back, so the two have to describe the same padding or the row loses the difference. A
 * stylesheet declaring padding and no height would otherwise leave every row short by the seeded
 * amount it never gets back.
 *
 * Padding may still make a row taller - a stylesheet or author value above the seed is additive,
 * which these tests pin alongside the floor. What it may not do is make the row shorter than the
 * tier. The two places there is nothing to give back, because nothing was subtracted, are a
 * stylesheet that sets the row height and a table with no seed at all.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class TableCellPaddingCssCoverageTest {
   @AfterEach
   void reset() {
      SreeEnv.setProperty("viewsheet.density", null);
   }

   @Test
   void fullCssCoverageStillRendersTheTierDataRow() {
      // a body padding covers every column of every row - XTableStyle.getInsets falls through to
      // the body entry for all c - so this is the shape a stylesheet most naturally writes, and
      // the one that leaves nothing to fall back to the seeded value
      assertDataRow("comfortable", bodyPadded(1), 28);
      assertDataRow("compact", bodyPadded(1), 24);
      assertDataRow("dense", bodyPadded(1), 20);
   }

   @Test
   void fullCssCoverageStillRendersTheTierHeaderRow() {
      assertHeaderRow("comfortable", headerPadded(1), 30);
      assertHeaderRow("compact", headerPadded(1), 26);
      assertHeaderRow("dense", headerPadded(1), 22);
   }

   @Test
   void partialCssCoverageLeavesTheUncoveredRowOnTheTier() {
      // the header row is padded and the body is not, so one row of the same table takes each
      // branch. Both must land on their tier height
      VSTableLens lens = lens(headerPadded(1));
      TableVSAssemblyInfo info = markedTable("comfortable");
      VizContext ctx = context("comfortable");

      assertEquals(30, VSDensityDefaults.headerRowHeight(ctx, info) + lens.getRowPadding(0, info),
                   "header row, covered by the stylesheet");
      assertEquals(28, VSDensityDefaults.rowHeight(ctx, info) + lens.getRowPadding(1, info),
                   "data row, falls back to the seeded padding");
   }

   @Test
   void aCssRowHeightTakesTheStylesheetsPaddingAlone() {
      // the stylesheet supplies the height, so nothing was subtracted and nothing has to come
      // back. Adding the seed on top would grow a row that neither the stylesheet nor the tier
      // asked for, and the row-height channel is the remedy this feature points a stylesheet at
      assertRenderedDataRow("comfortable", bodyHeightAndPadding(20, 1), 22);
      assertRenderedDataRow("compact", bodyHeightAndPadding(20, 1), 22);
      assertRenderedDataRow("dense", bodyHeightAndPadding(20, 1), 22);
   }

   @Test
   void anUnmarkedTableWithAnAuthorPaddingTakesTheCssPaddingAlone() {
      // an unmarked table has no DEFAULT tier, so its stored height was never shrunk. The floor
      // tracks what was subtracted, not what is drawn, so an author padding must not raise it
      VSTableLens lens = lens(bodyPadded(1));
      TableVSAssemblyInfo info = unmarkedTable();
      info.setCellPadding(new Insets(10, 10, 10, 10), CompositeValue.Type.USER);

      assertEquals(2, lens.getRowPadding(1, info));
   }

   @Test
   void aCssPaddingLargerThanTheSeedStillGrowsTheRow() {
      // a guard, not a restatement: it passes before the fix as well, and it is what would catch
      // a fix that clamped the row to the tier instead of taking the larger of the two
      assertDataRow("comfortable", bodyPadded(10), 36);
   }

   @Test
   void anUnmarkedTableTakesTheCssPaddingAlone() {
      // the other guard: with no seeded padding there is nothing to conserve, so the stylesheet
      // is the whole story and this row must not move
      VSTableLens lens = lens(bodyPadded(1));
      TableVSAssemblyInfo info = unmarkedTable();

      assertEquals(2, lens.getRowPadding(1, info));
   }

   /**
    * The rendered data row, mirroring BaseTableService:459-493: the density substitution, then a
    * stylesheet height replacing it outright, then the padding on top. The row-height source is a
    * caller decision, so a lens-level assertion alone cannot see the case this pins.
    */
   private void assertRenderedDataRow(String density, CSSTableStyle style, int expected) {
      VSTableLens lens = lens(style);
      TableVSAssemblyInfo info = markedTable(density);
      VizContext ctx = context(density);
      int row = lens.getHeaderRowCount();
      int height = info.getDataRowHeight(row);

      if(ctx.modern && !info.isUserDataRowHeight() && height == AssetUtil.defh) {
         height = VSDensityDefaults.rowHeight(ctx, info);
      }

      int css = lens.getCSSDataRowHeight(info);
      height = css > 0 ? css : height;

      assertEquals(expected, height + lens.getRowPadding(row, info), density + " rendered data row");
   }

   private void assertDataRow(String density, CSSTableStyle style, int expected) {
      VSTableLens lens = lens(style);
      TableVSAssemblyInfo info = markedTable(density);
      VizContext ctx = context(density);

      assertEquals(expected, VSDensityDefaults.rowHeight(ctx, info) + lens.getRowPadding(1, info),
                   density + " data row");
   }

   private void assertHeaderRow(String density, CSSTableStyle style, int expected) {
      VSTableLens lens = lens(style);
      TableVSAssemblyInfo info = markedTable(density);
      VizContext ctx = context(density);

      assertEquals(expected,
                   VSDensityDefaults.headerRowHeight(ctx, info) + lens.getRowPadding(0, info),
                   density + " header row");
   }

   private VSTableLens lens(CSSTableStyle style) {
      return new VSTableLens(style);
   }

   private CSSTableStyle bodyPadded(int padding) {
      return padded("body.padding", padding);
   }

   private CSSTableStyle headerPadded(int padding) {
      return padded("header-row.padding", padding);
   }

   private CSSTableStyle padded(String attribute, int padding) {
      CSSTableStyle style = newStyle();
      style.put(attribute, new Insets(padding, padding, padding, padding));
      style.setApplyInsets(true);
      return style;
   }

   /** A stylesheet that sets the row height as well as the padding - the supported way to ask
    *  for a shorter row, and the case the padding floor must keep its hands off. */
   private CSSTableStyle bodyHeightAndPadding(int height, int padding) {
      CSSTableStyle style = padded("body.padding", padding);
      style.put("body.height", height);
      style.setApplyRowHeight(true);
      return style;
   }

   private CSSTableStyle newStyle() {
      CSSTableStyle style = new CSSTableStyle(
         new CSSParameter("Table", null, null, null), XTableUtil.getDefaultTableLens());
      style.setTable(XTableUtil.getDefaultTableLens());
      return style;
   }

   private TableVSAssemblyInfo markedTable(String density) {
      TableVSAssemblyInfo info = new TableVSAssemblyInfo();
      info.setCellPadding(VSDensityDefaults.cellPadding(context(density)),
                          CompositeValue.Type.DEFAULT);
      return info;
   }

   private TableVSAssemblyInfo unmarkedTable() {
      TableVSAssemblyInfo info = new TableVSAssemblyInfo();
      info.setCellPadding(null, CompositeValue.Type.DEFAULT);
      return info;
   }

   private VizContext context(String density) {
      SreeEnv.setProperty("viewsheet.density", density);
      return VizContext.of(VizMark.MODERN_LIGHT);
   }
}
