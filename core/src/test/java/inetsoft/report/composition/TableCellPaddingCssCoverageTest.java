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
 * A format.css table padding decides where a cell's text sits. It must not also decide how tall
 * the row is.
 *
 * A marked table stores the tier height less its seeded cell padding and render adds the padding
 * back, so the two have to describe the same padding or the row loses the difference. The row
 * growth is the only half a stylesheet can reach, which is why a stylesheet that declares padding
 * and no height would otherwise leave every row short by the seeded amount it never gets back.
 * A stylesheet that wants a row height has getCSSDataRowHeight for that.
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
      CSSTableStyle style = new CSSTableStyle(
         new CSSParameter("Table", null, null, null), XTableUtil.getDefaultTableLens());
      style.setTable(XTableUtil.getDefaultTableLens());
      style.put(attribute, new Insets(padding, padding, padding, padding));
      style.setApplyInsets(true);
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
