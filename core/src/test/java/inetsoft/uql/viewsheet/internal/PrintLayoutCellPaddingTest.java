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

import inetsoft.report.TabularSheet;
import inetsoft.report.composition.VSTableLens;
import inetsoft.report.internal.TableElementDef;
import inetsoft.report.lens.DefaultTableLens;
import inetsoft.sree.SreeEnv;
import inetsoft.test.*;
import inetsoft.uql.CompositeValue;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.awt.Insets;
import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Print layout reaches none of the sites that resolve a cell's padding. It reads the cell inset
 * through the generic TableLens.getInsets, and it treats a fixed row height as final. So the
 * converter has to answer on both channels: the lens it hands the element resolves the inset,
 * and the heights it hands setFixedHeights already carry the row's growth.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class PrintLayoutCellPaddingTest {
   @AfterEach
   void reset() {
      SreeEnv.setProperty("viewsheet.density", null);
   }

   @Test
   void theInsetChannelAnswersWithTheResolvedPadding() {
      VSTableLens lens = lens();

      assertNull(lens.getInsets(0, 0), "nothing in the lens chain defines one");
      assertEquals(new Insets(6, 8, 6, 8),
                   printLens(lens, markedTable("comfortable")).getInsets(0, 0));
      assertEquals(new Insets(4, 6, 4, 6),
                   printLens(lens, markedTable("compact")).getInsets(0, 0));
      assertEquals(new Insets(3, 4, 3, 4),
                   printLens(lens, markedTable("dense")).getInsets(0, 0));
   }

   @Test
   void theInsetChannelIsUnchangedForAnUnmarkedTable() {
      VSTableLens lens = lens();

      assertNull(printLens(lens, unmarkedTable()).getInsets(0, 0),
                 "same null the bare lens returns, so an unmarked table prints as before");
   }

   @Test
   void theElementResolvesTheInsetThroughItsOwnFilterChain() {
      // the element stacks MaxRowsTableLens2/AttributeTableLens2 over whatever it is given;
      // both fall through to the base, which is where TablePaintable's getInsets read lands
      TableElementDef elem = new TableElementDef(
         new TabularSheet(null, null), printLens(lens(), markedTable("comfortable")));

      assertEquals(new Insets(6, 8, 6, 8), elem.getTable().getInsets(0, 0));
   }

   @Test
   void theElementPaddingIsLeftAtTheReportDefault() {
      // calcColWidth adds getPadding().left + right to a computed column width, so this is the
      // lever that must not move: the horizontal padding stays non-additive
      TableElementDef elem = new TableElementDef(
         new TabularSheet(null, null), printLens(lens(), markedTable("comfortable")));

      assertEquals(new Insets(0, 1, 0, 1), elem.getPadding());
   }

   @Test
   void aFixedColumnWidthIsNotWidenedByTheInset() {
      // calcColWidth returns before it reads either the inset or the element padding for any
      // column whose fixed width is set, and addTable always sets them
      TableElementDef marked = new TableElementDef(
         new TabularSheet(null, null), printLens(lens(), markedTable("comfortable")));
      marked.setFixedWidths(new int[]{ 100, 80, 60 });

      TableElementDef unmarked = new TableElementDef(
         new TabularSheet(null, null), printLens(lens(), unmarkedTable()));
      unmarked.setFixedWidths(new int[]{ 100, 80, 60 });

      float[][] markedWs = marked.calcColWidth(80, marked.getTable());
      float[][] unmarkedWs = unmarked.calcColWidth(80, unmarked.getTable());

      for(int i = 0; i < 3; i++) {
         assertArrayEquals(unmarkedWs[i], markedWs[i], 0f,
                           "column " + i + " is the same width marked or not");
      }

      assertEquals(100f, markedWs[0][0], 0f);
      assertEquals(80f, markedWs[1][0], 0f);
      assertEquals(60f, markedWs[2][0], 0f);
   }

   @Test
   void anAutoWidthColumnReservesTheInsetInstead() {
      // addTable hands the element a positive fixed width per column, so this is the pathological
      // leftover: a column the element has to size from content. There the inset is reserved
      // rather than widening a width the browser held - which is what the browser does too
      TableElementDef marked = new TableElementDef(
         new TabularSheet(null, null), printLens(lens(), markedTable("comfortable")));
      marked.setFixedWidths(new int[]{ -1, 0, 60 });

      TableElementDef unmarked = new TableElementDef(
         new TabularSheet(null, null), printLens(lens(), unmarkedTable()));
      unmarked.setFixedWidths(new int[]{ -1, 0, 60 });

      float[][] markedWs = marked.calcColWidth(80, marked.getTable());
      float[][] unmarkedWs = unmarked.calcColWidth(80, unmarked.getTable());

      assertEquals(unmarkedWs[0][0] + 16, markedWs[0][0], 0f, "left 8 + right 8");
      assertEquals(0f, markedWs[1][0], 0f, "a zero fixed width still hides the column");
      assertEquals(60f, markedWs[2][0], 0f, "a positive fixed width is still untouched");
   }

   @Test
   void fixedRowHeightsCarryTheRowPadding() throws Exception {
      // the stored matrix at comfortable: 18 header, 16 data
      VSTableLens lens = lens();
      lens.setRowHeights(new int[]{ 18, 16, 16, 16, 16 });

      assertArrayEquals(new int[]{ 30, 28, 28, 28, 28 },
                        calculateRowHeights(markedTable("comfortable"), lens));
   }

   @Test
   void fixedRowHeightsFollowTheirOwnTier() throws Exception {
      VSTableLens compact = lens();
      compact.setRowHeights(new int[]{ 18, 16, 16, 16, 16 });
      assertArrayEquals(new int[]{ 26, 24, 24, 24, 24 },
                        calculateRowHeights(markedTable("compact"), compact));

      VSTableLens dense = lens();
      dense.setRowHeights(new int[]{ 16, 14, 14, 14, 14 });
      assertArrayEquals(new int[]{ 22, 20, 20, 20, 20 },
                        calculateRowHeights(markedTable("dense"), dense));
   }

   @Test
   void anUnmarkedTablesFixedHeightsAreUntouched() throws Exception {
      VSTableLens lens = lens();
      lens.setRowHeights(new int[]{ 18, 32, 32, 32, 32 });
      TableVSAssemblyInfo info = unmarkedTable();
      // a height the author typed, so calculateRowHeights keeps it rather than going auto
      info.setDataRowHeight(32);

      assertArrayEquals(new int[]{ 18, 32, 32, 32, 32 }, calculateRowHeights(info, lens));
   }

   @Test
   void theWrapMarkerSurvivesThePadding() throws Exception {
      // a wrapped row is auto-sized by the renderer, which adds the inset itself; -1 says so
      VSTableLens lens = lens();
      lens.setRowHeights(new int[]{ 18, 16, 16, 16, 16 });
      ((DefaultTableLens) lens.getTable()).setLineWrap(true);

      assertArrayEquals(new int[]{ -1, -1, -1, -1, -1 },
                        calculateRowHeights(markedTable("comfortable"), lens));
   }

   private VSTableLens lens() {
      DefaultTableLens base = new DefaultTableLens(XTableUtil.getDefaultData());
      base.setLineWrap(false);
      return new VSTableLens(base);
   }

   private VsToReportConverter.CellInsetTableLens printLens(VSTableLens lens,
                                                            TableDataVSAssemblyInfo info)
   {
      return new VsToReportConverter.CellInsetTableLens(lens, info);
   }

   private int[] calculateRowHeights(TableDataVSAssemblyInfo info, VSTableLens lens)
      throws Exception
   {
      VsToReportConverter converter = new VsToReportConverter(null, null, null, null, null);
      Method method = VsToReportConverter.class.getDeclaredMethod(
         "calculateRowHeights", TableDataVSAssemblyInfo.class, VSTableLens.class);
      method.setAccessible(true);

      return (int[]) method.invoke(converter, info, lens);
   }

   // mirrors TableCellPaddingResolutionTest: seedChromeDefaults is protected, so the value is
   // set at the tier the seed would have written it to
   private TableVSAssemblyInfo markedTable(String density) {
      SreeEnv.setProperty("viewsheet.density", density);
      TableVSAssemblyInfo info = new TableVSAssemblyInfo();
      info.setCellPadding(VSDensityDefaults.cellPadding(VizContext.of(VizMark.MODERN_LIGHT)),
                          CompositeValue.Type.DEFAULT);
      return info;
   }

   private TableVSAssemblyInfo unmarkedTable() {
      TableVSAssemblyInfo info = new TableVSAssemblyInfo();
      info.setCellPadding(null, CompositeValue.Type.DEFAULT);
      return info;
   }
}
