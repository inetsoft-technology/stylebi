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
package inetsoft.uql.viewsheet;

import inetsoft.report.CellBinding;
import inetsoft.report.TableLayout;
import inetsoft.report.TableLens;
import inetsoft.report.filter.CrossTabFilter;
import inetsoft.report.filter.SumFormula;
import inetsoft.report.lens.DefaultTableLens;
import inetsoft.test.*;
import inetsoft.uql.asset.AggregateInfo;
import inetsoft.uql.asset.ColumnRef;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.erm.DataRef;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import inetsoft.report.TableCellBinding;
import inetsoft.report.internal.binding.TopNInfo;
import inetsoft.uql.XCondition;
import inetsoft.uql.asset.DateRangeRef;
import inetsoft.uql.schema.XSchema;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests the crosstab to Freehand (calc) layout conversion in {@code VSLayoutTool.generateLayout}
 * and {@code VSTableConverter.changeTableMode}.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class VSTableConverterTest {
   // fixTopN: the Others group of a non-innermost dimension is turned off when the crosstab is
   // converted to a freehand table (VSTableConverter.fixTopN)
   @Test
   void outerRowDimensionOthersIsTurnedOff() {
      TableLayout layout = convert(new String[] { "year", "quarter" }, new String[0], "year");
      assertFalse(groupBinding(layout, "year").getTopN(false).isOthers());
   }

   @Test
   void innermostRowDimensionKeepsOthers() {
      TableLayout layout = convert(new String[] { "year", "quarter" }, new String[0], "quarter");
      assertTrue(groupBinding(layout, "quarter").getTopN(false).isOthers());
   }

   @Test
   void outerColumnDimensionOthersIsTurnedOff() {
      TableLayout layout = convert(new String[0], new String[] { "region", "channel" }, "region");
      assertFalse(groupBinding(layout, "region").getTopN(false).isOthers());
   }

   @Test
   void innermostColumnDimensionKeepsOthers() {
      TableLayout layout = convert(new String[0], new String[] { "region", "channel" }, "channel");
      assertTrue(groupBinding(layout, "channel").getTopN(false).isOthers());
   }

   @Test
   void singleDimensionKeepsOthers() {
      TableLayout layout = convert(new String[] { "year" }, new String[0], "year");
      assertTrue(groupBinding(layout, "year").getTopN(false).isOthers());
   }

   @Test
   void dimensionWithoutTopNIsNotAffected() {
      TableLayout layout = convert(new String[] { "year", "quarter" }, new String[0], null);
      TopNInfo topN = groupBinding(layout, "year").getTopN(false);
      assertTrue(topN == null || topN.isBlank());
   }

   // outer dimension is a date-level dimension: its full name (Year(date)) equals the group
   // name, so it is recognized as an outer dimension and Others is turned off
   @Test
   void outerDateLevelRowDimensionOthersIsTurnedOff() {
      Viewsheet vs = new Viewsheet();
      CrosstabVSAssembly crosstab = new CrosstabVSAssembly(vs, "Crosstab1");
      VSCrosstabInfo cinfo = crosstab.getVSCrosstabInfo();
      AggregateInfo ainfo = new AggregateInfo();

      ColumnRef dcol = new ColumnRef(new AttributeRef(null, "date"));
      dcol.setDataType(XSchema.DATE);
      VSDimensionRef year = new VSDimensionRef(dcol);
      year.setGroupColumnValue("date");
      year.setDateLevelValue(DateRangeRef.YEAR_INTERVAL + "");
      year.setRankingOptionValue(XCondition.TOP_N + "");
      year.setRankingNValue("3");
      year.setRankingColValue("Sum(sales)");
      year.setGroupOthersValue("true");
      VSDimensionRef quarter = dimension("quarter", false);
      assertEquals("Year(date)", year.getFullName());
      assertEquals(year.getFullName(), year.createGroupRef(null).getName());

      ainfo.addGroup(year.createGroupRef(null));
      ainfo.addGroup(quarter.createGroupRef(null));
      VSAggregateRef sales = new VSAggregateRef();
      sales.setDataRef(new ColumnRef(new AttributeRef(null, "sales")));
      sales.setColumnValue("sales");
      sales.setFormulaValue("Sum");
      ainfo.addAggregate(sales.createAggregateRef(null));
      DataRef[] rows = { year, quarter };
      cinfo.setDesignRowHeaders(rows);
      cinfo.setDesignAggregates(new DataRef[] { sales });
      cinfo.setRuntimeRowHeaders(rows);
      cinfo.setRuntimeColHeaders(new DataRef[0]);
      cinfo.setRuntimeAggregates(new DataRef[] { sales });
      cinfo.setAggregateInfo(ainfo);

      DefaultTableLens base = new DefaultTableLens(new Object[][] {
         { "Year(date)", "quarter", "sales" },
         { "2021", "q1", 10.0 },
         { "2022", "q2", 20.0 } });
      base.setHeaderRowCount(1);
      TableLens meta = new CrossTabFilter(base, new int[] { 0, 1 }, new int[0],
         new int[] { 2 }, new inetsoft.report.filter.Formula[] { new SumFormula() });
      TableLayout olayout = VSLayoutTool.generateLayout(crosstab, meta, vs);
      TableLayout nlayout = VSTableConverter.changeTableMode(crosstab, olayout, TableLayout.CALC);

      TopNInfo topN = groupBinding(nlayout, "Year(date)").getTopN(false);
      assertEquals(3, topN.getTopN());
      assertFalse(topN.isOthers());
   }

   // short circuits of changeTableMode
   @Test
   void sameModeOrNonCalcTargetReturnsEmptyLayout() {
      TableLayout olayout = new TableLayout();
      olayout.setMode(TableLayout.CALC);
      TableLayout result = VSTableConverter.changeTableMode(null, olayout, TableLayout.CALC);
      assertEquals(0, result.getRegionCount());
      olayout.setMode(TableLayout.CROSSTAB);
      result = VSTableConverter.changeTableMode(null, olayout, TableLayout.NORMAL);
      assertEquals(0, result.getRegionCount());
   }

   @Test
   void nullLayoutConvertsToDefaultCalcLayout() {
      TableLayout result = VSTableConverter.changeTableMode(null, null, TableLayout.CALC);
      assertEquals(TableLayout.CALC, result.getMode());
      assertTrue(result.getRegionCount() > 0);
   }

   // summary cell of a single-aggregate crosstab: bound as SUMMARY with the aggregate formula
   @Test
   void summaryCellKeepsAggregateColumnAndFormula() {
      TableLayout layout = convert(new String[] { "year" }, new String[] { "region" }, null);
      TableCellBinding summary = null;

      for(int r = 0; r < layout.getRowCount(); r++) {
         for(int c = 0; c < layout.getColCount(); c++) {
            TableCellBinding b = (TableCellBinding) layout.getCellBinding(r, c);

            if(b != null && b.getBType() == TableCellBinding.SUMMARY &&
               "sales".equals(b.getValue()))
            {
               summary = b;
            }
         }
      }

      assertNotNull(summary, "no summary cell");
      assertEquals("Sum", summary.getFormula());
      assertEquals(TableCellBinding.EXPAND_NONE, summary.getExpansion());
   }

   // ---- cell level: sub totals, grand totals, nesting, merge span, fake fields ----

   private static final String[] ROWS = { "year", "quarter" };
   private static final String[] COLS = { "region", "channel" };

   /** 2 row dimensions x 2 column dimensions with sub totals and grand totals not suppressed. */
   private static TableLayout totalsLayout() {
      return convert(ROWS, COLS, null, ci -> { }, f -> {
         f.setSuppressRowGrandTotal(false);
         f.setSuppressColumnGrandTotal(false);
         f.setSuppressRowGroupTotal(new boolean[] { false, false });
         f.setSuppressColumnGroupTotal(new boolean[] { false, false });
      });
   }

   private static List<TableCellBinding> summaryCells(TableLayout layout) {
      List<TableCellBinding> list = new ArrayList<>();

      for(int r = 0; r < layout.getRowCount(); r++) {
         for(int c = 0; c < layout.getColCount(); c++) {
            TableCellBinding b = (TableCellBinding) layout.getCellBinding(r, c);

            if(b != null && b.getBType() == TableCellBinding.SUMMARY) {
               list.add(b);
            }
         }
      }

      return list;
   }

   private static List<TableCellBinding> groupCells(TableLayout layout, String name) {
      List<TableCellBinding> list = new ArrayList<>();

      for(int r = 0; r < layout.getRowCount(); r++) {
         for(int c = 0; c < layout.getColCount(); c++) {
            TableCellBinding b = (TableCellBinding) layout.getCellBinding(r, c);

            if(b != null && b.getBType() == TableCellBinding.GROUP && name.equals(b.getValue())) {
               list.add(b);
            }
         }
      }

      return list;
   }

   private static boolean hasSummary(List<TableCellBinding> cells, String rowGroup,
                                     String colGroup)
   {
      return cells.stream().anyMatch(b -> Objects.equals(rowGroup, b.getRowGroup()) &&
         Objects.equals(colGroup, b.getColGroup()));
   }

   // detail summary cell: bound to the innermost row and column group
   @Test
   void detailSummaryCellIsBoundToInnermostGroups() {
      List<TableCellBinding> cells = summaryCells(totalsLayout());
      assertTrue(hasSummary(cells, "quarter", "channel"));
   }

   // column sub total (G_FOOTER of the column side): column group is the outer column dimension
   @Test
   void columnSubTotalCellIsBoundToOuterColumnGroup() {
      List<TableCellBinding> cells = summaryCells(totalsLayout());
      assertTrue(hasSummary(cells, "quarter", "region"));
   }

   // row sub total (G_FOOTER of the row side): row group is the outer row dimension
   @Test
   void rowSubTotalCellIsBoundToOuterRowGroup() {
      List<TableCellBinding> cells = summaryCells(totalsLayout());
      assertTrue(hasSummary(cells, "year", "channel"));
      assertTrue(hasSummary(cells, "year", "region"));
   }

   // grand total: the total column has no column group, and a "Grand Total" header text
   @Test
   void columnGrandTotalCellHasNoColumnGroup() {
      TableLayout layout = totalsLayout();
      assertTrue(hasSummary(summaryCells(layout), "quarter", TableCellBinding.DEFAULT_GROUP));
      assertTrue(hasSummary(summaryCells(layout), "year", TableCellBinding.DEFAULT_GROUP));
      boolean header = false;

      for(int r = 0; r < layout.getRowCount(); r++) {
         for(int c = 0; c < layout.getColCount(); c++) {
            TableCellBinding b = (TableCellBinding) layout.getCellBinding(r, c);
            header |= b != null && b.getType() == CellBinding.BIND_TEXT &&
               "Grand Total".equals(b.getValue());
         }
      }

      assertTrue(header);
   }

   // all summary cells keep the aggregate column and formula, none expands
   @Test
   void everySummaryCellKeepsColumnFormulaAndNoExpansion() {
      for(TableCellBinding b : summaryCells(totalsLayout())) {
         assertEquals("sales", b.getValue());
         assertEquals("Sum", b.getFormula());
         assertEquals(TableCellBinding.EXPAND_NONE, b.getExpansion());
      }
   }

   // two row dimensions: the outer cell defines the cell name, the inner cell refers to it
   @Test
   void nestedRowDimensionRefersToOuterCellName() {
      TableLayout layout = convert(ROWS, new String[] { "region" }, null);
      TableCellBinding year = groupCells(layout, "year").get(0);
      TableCellBinding quarter = groupCells(layout, "quarter").get(0);
      assertEquals("year", year.getCellName());
      assertEquals(TableCellBinding.DEFAULT_GROUP, year.getRowGroup());
      assertEquals("quarter", quarter.getCellName());
      assertEquals("year", quarter.getRowGroup());
      assertEquals(TableCellBinding.EXPAND_V, quarter.getExpansion());
   }

   // an expanded column header: only the first cell has the cell name, the repeated cells refer
   // to it through the column group
   @Test
   void repeatedColumnHeaderCellsReuseTheCellName() {
      TableLayout layout = convert(new String[] { "year" }, new String[] { "region" }, null);
      List<TableCellBinding> cells = groupCells(layout, "region");
      assertTrue(cells.size() > 1);
      assertEquals("region", cells.get(0).getCellName());

      for(int i = 1; i < cells.size(); i++) {
         assertNull(cells.get(i).getCellName());
         assertEquals("region", cells.get(i).getColGroup());
      }
   }

   @Test
   void mergeSpanIsCopiedToGroupCells() {
      TableLayout on = convert(ROWS, new String[0], null, ci -> ci.setMergeSpan(true));
      TableLayout off = convert(ROWS, new String[0], null, ci -> ci.setMergeSpan(false));

      for(String name : ROWS) {
         assertTrue(groupCells(on, name).get(0).isMergeCells());
         assertFalse(groupCells(off, name).get(0).isMergeCells());
      }
   }

   private static final String FAKE = "fakeDim";

   // a fake dimension is turned into a text cell, no group binding is created for it
   @Test
   void fakeDimensionBecomesTextCell() {
      TableLayout layout = convert(new String[] { FAKE, "year" }, new String[0], null);
      boolean text = false;

      for(int r = 0; r < layout.getRowCount(); r++) {
         for(int c = 0; c < layout.getColCount(); c++) {
            TableCellBinding b = (TableCellBinding) layout.getCellBinding(r, c);

            if(b != null && FAKE.equals(b.getValue())) {
               assertEquals(CellBinding.BIND_TEXT, b.getType());
               text = true;
            }
         }
      }

      assertTrue(text, "no cell for the fake dimension");
      assertFalse(groupCells(layout, "year").isEmpty());
   }

   // a real dimension in the same crosstab is not treated as fake
   @Test
   void realDimensionIsNotTreatedAsFake() {
      TableLayout layout = convert(new String[] { "year" }, new String[0], null);
      assertEquals(CellBinding.BIND_COLUMN, groupCells(layout, "year").get(0).getType());
   }

   @Test
   void mergeSpanDoesNotApplyToSummaryCells() {
      TableLayout on = convert(ROWS, new String[0], null, ci -> ci.setMergeSpan(true));

      for(TableCellBinding b : summaryCells(on)) {
         assertFalse(b.isMergeCells());
      }
   }

   /**
    * Build a crosstab with the given row and column dimensions and Sum(sales), set TopN with
    * Others on {@code topNDim} (if not null), and convert it to a calc layout.
    */
   private static TableLayout convert(String[] rowDims, String[] colDims, String topNDim) {
      return convert(rowDims, colDims, topNDim, cinfo -> { });
   }

   private static TableLayout convert(String[] rowDims, String[] colDims, String topNDim,
                                      Consumer<VSCrosstabInfo> customizer)
   {
      return convert(rowDims, colDims, topNDim, customizer, filter -> { });
   }

   private static TableLayout convert(String[] rowDims, String[] colDims, String topNDim,
                                      Consumer<VSCrosstabInfo> customizer,
                                      Consumer<CrossTabFilter> filterCustomizer)
   {
      Viewsheet vs = new Viewsheet();
      CrosstabVSAssembly crosstab = new CrosstabVSAssembly(vs, "Crosstab1");
      VSCrosstabInfo cinfo = crosstab.getVSCrosstabInfo();
      AggregateInfo ainfo = new AggregateInfo();
      List<DataRef> rows = new ArrayList<>();
      List<DataRef> cols = new ArrayList<>();
      List<String> headers = new ArrayList<>();

      for(String name : rowDims) {
         VSDimensionRef dim = dimension(name, name.equals(topNDim));
         rows.add(dim);
         ainfo.addGroup(dim.createGroupRef(null));
         headers.add(name);
      }

      for(String name : colDims) {
         VSDimensionRef dim = dimension(name, name.equals(topNDim));
         cols.add(dim);
         ainfo.addGroup(dim.createGroupRef(null));
         headers.add(name);
      }

      VSAggregateRef sales = new VSAggregateRef();
      sales.setDataRef(new ColumnRef(new AttributeRef(null, "sales")));
      sales.setColumnValue("sales");
      sales.setFormulaValue("Sum");
      ainfo.addAggregate(sales.createAggregateRef(null));
      headers.add("sales");

      DataRef[] rowArr = rows.toArray(new DataRef[0]);
      DataRef[] colArr = cols.toArray(new DataRef[0]);
      cinfo.setDesignRowHeaders(rowArr);
      cinfo.setDesignColHeaders(colArr);
      cinfo.setDesignAggregates(new DataRef[] { sales });
      cinfo.setRuntimeRowHeaders(rowArr);
      cinfo.setRuntimeColHeaders(colArr);
      cinfo.setRuntimeAggregates(new DataRef[] { sales });
      cinfo.setAggregateInfo(ainfo);
      customizer.accept(cinfo);

      // base data: every dimension has two values, all combinations present, so that sub totals
      // and grand totals exist when they are not suppressed
      int ndims = headers.size() - 1;
      int nrows = 1 << ndims;
      Object[][] data = new Object[nrows + 1][headers.size()];
      data[0] = headers.toArray();

      for(int r = 0; r < nrows; r++) {
         for(int c = 0; c < ndims; c++) {
            data[r + 1][c] = headers.get(c) + (((r >> c) & 1) + 1);
         }

         data[r + 1][ndims] = 10.0 * (r + 1);
      }

      DefaultTableLens base = new DefaultTableLens(data);
      base.setHeaderRowCount(1);
      int[] rowh = new int[rowDims.length];
      int[] colh = new int[colDims.length];

      for(int i = 0; i < rowh.length; i++) {
         rowh[i] = i;
      }

      for(int i = 0; i < colh.length; i++) {
         colh[i] = rowh.length + i;
      }

      CrossTabFilter meta = new CrossTabFilter(base, rowh, colh, new int[] { headers.size() - 1 },
                                          new inetsoft.report.filter.Formula[] { new SumFormula() });
      filterCustomizer.accept(meta);
      TableLayout olayout = VSLayoutTool.generateLayout(crosstab, meta, vs);
      assertEquals(TableLayout.CROSSTAB, olayout.getMode());
      TableLayout nlayout = VSTableConverter.changeTableMode(crosstab, olayout, TableLayout.CALC);
      assertEquals(TableLayout.CALC, nlayout.getMode());
      return nlayout;
   }

   private static VSDimensionRef dimension(String name, boolean topNWithOthers) {
      DataRef column = new ColumnRef(new AttributeRef(null, name));

      if(FAKE.equals(name)) {
         // a fake calculate field is a placeholder column, not a real data column
         CalculateRef calc = new CalculateRef();
         calc.setName(name);
         calc.setFake(true);
         calc.setDataRef(column);
         column = calc;
      }

      VSDimensionRef dim = new VSDimensionRef(column);
      dim.setGroupColumnValue(name);

      if(topNWithOthers) {
         dim.setRankingOptionValue(XCondition.TOP_N + "");
         dim.setRankingNValue("3");
         dim.setRankingColValue("Sum(sales)");
         dim.setGroupOthersValue("true");
      }

      return dim;
   }

   /** Find the group header cell bound to the column {@code name}. */
   private static TableCellBinding groupBinding(TableLayout layout, String name) {
      for(int r = 0; r < layout.getRowCount(); r++) {
         for(int c = 0; c < layout.getColCount(); c++) {
            TableCellBinding binding = (TableCellBinding) layout.getCellBinding(r, c);

            if(binding != null && binding.getType() == CellBinding.BIND_COLUMN &&
               binding.getBType() == TableCellBinding.GROUP && name.equals(binding.getValue()))
            {
               return binding;
            }
         }
      }

      fail("no group cell for " + name);
      return null;
   }
}
