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
import inetsoft.report.TableCellBinding;
import inetsoft.report.TableLayout;
import inetsoft.report.TableLens;
import inetsoft.report.filter.CrossTabFilter;
import inetsoft.report.filter.Formula;
import inetsoft.report.filter.SumFormula;
import inetsoft.report.internal.binding.TopNInfo;
import inetsoft.report.lens.DefaultTableLens;
import inetsoft.test.*;
import inetsoft.uql.Condition;
import inetsoft.uql.ConditionItem;
import inetsoft.uql.ConditionList;
import inetsoft.uql.XCondition;
import inetsoft.uql.XConstants;
import inetsoft.report.TableDataPath;
import inetsoft.uql.viewsheet.internal.TableVSAssemblyInfo;
import inetsoft.uql.ColumnSelection;
import inetsoft.uql.asset.AggregateInfo;
import inetsoft.uql.asset.ColumnRef;
import inetsoft.uql.asset.GroupRef;
import inetsoft.uql.asset.SourceInfo;
import inetsoft.uql.asset.DateRangeRef;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.erm.DataRef;
import inetsoft.uql.schema.XSchema;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.awt.Dimension;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
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
         new int[] { 2 }, new Formula[] { new SumFormula() });
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

   /** The number of summary cells with this row group and column group. */
   private static long countSummary(List<TableCellBinding> cells, String rowGroup,
                                    String colGroup)
   {
      return cells.stream().filter(b -> Objects.equals(rowGroup, b.getRowGroup()) &&
         Objects.equals(colGroup, b.getColGroup())).count();
   }

   // detail summary cell: bound to the innermost row and column group
   @Test
   void detailSummaryCellIsBoundToInnermostGroups() {
      List<TableCellBinding> cells = summaryCells(totalsLayout());
      assertEquals(4, countSummary(cells, "quarter", "channel"));
   }

   // column sub total (G_FOOTER of the column side): column group is the outer column dimension
   @Test
   void columnSubTotalCellIsBoundToOuterColumnGroup() {
      List<TableCellBinding> cells = summaryCells(totalsLayout());
      assertEquals(2, countSummary(cells, "quarter", "region"));
   }

   // row sub total (G_FOOTER of the row side): row group is the outer row dimension
   @Test
   void rowSubTotalCellIsBoundToOuterRowGroup() {
      List<TableCellBinding> cells = summaryCells(totalsLayout());
      assertEquals(4, countSummary(cells, "year", "channel"));
      assertEquals(2, countSummary(cells, "year", "region"));
   }

   // grand total: the total column has no column group, and a "Grand Total" header text
   @Test
   void columnGrandTotalCellHasNoColumnGroup() {
      TableLayout layout = totalsLayout();
      assertEquals(1, countSummary(summaryCells(layout), "quarter",
                                   TableCellBinding.DEFAULT_GROUP));
      assertEquals(1, countSummary(summaryCells(layout), "year",
                                   TableCellBinding.DEFAULT_GROUP));
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

   // row grand total (FOOTER of the row side): the summary cells have no row group
   @Test
   void rowGrandTotalCellsHaveNoRowGroup() {
      List<TableCellBinding> cells = summaryCells(totalsLayout());
      assertEquals(4, countSummary(cells, TableCellBinding.DEFAULT_GROUP, "channel"));
      assertEquals(2, countSummary(cells, TableCellBinding.DEFAULT_GROUP, "region"));
      assertEquals(1, countSummary(cells, TableCellBinding.DEFAULT_GROUP,
                                   TableCellBinding.DEFAULT_GROUP));
   }

   // a crosstab whose aggregates were removed after the layout was made is converted as a plain
   // table: the detail cells expand vertically and have no group
   @Test
   void crosstabWithoutAggregatesConvertsAsPlainTable() {
      TableLayout layout = convert(new String[] { "year" }, new String[] { "region" }, null,
         ci -> { }, f -> { }, ci -> ci.setAggregateInfo(new AggregateInfo()));
      int cells = 0;

      for(int c = 0; c < layout.getColCount(); c++) {
         TableCellBinding b = (TableCellBinding) layout.getCellBinding(1, c);

         if(b != null) {
            cells++;
            assertEquals(TableCellBinding.EXPAND_V, b.getExpansion());
            assertEquals(TableCellBinding.DEFAULT_GROUP, b.getRowGroup());
            assertEquals(TableCellBinding.DEFAULT_GROUP, b.getColGroup());
         }
      }

      assertTrue(cells > 0, "no detail cell in the second row");
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

   // ---- percentage option remapping by "percentage by" ----

   // the percentage option of a summary cell is remapped to the row or column variant of the
   // crosstab's "percentage by" setting; the formula name carries the option as a suffix
   @ParameterizedTest(name = "percentage by {0} with option {1} writes formula {2}")
   @CsvSource({
      "1, 1, Sum<8>",
      "1, 2, Sum<32>",
      "2, 1, Sum<4>",
      "2, 2, Sum<16>",
      "1, 0, Sum"
   })
   void percentageOptionIsRemappedByPercentageBy(int by, int option, String formula) {
      Spec spec = new Spec(new String[] { "year" }, new String[] { "region" });
      spec.aggs(new Agg("sales", "Sum").percent(option));
      spec.customizer = ci -> ci.setPercentageByValue(by + "");
      List<TableCellBinding> cells = summaryCells(spec.run().nlayout);
      assertFalse(cells.isEmpty());

      for(TableCellBinding b : cells) {
         assertEquals(formula, b.getFormula());
      }
   }

   // ---- special characters in names ----

   // a column name with parentheses and a space is kept whole as the summary value
   @Test
   void summaryCellKeepsAggregateColumnNameWithParentheses() {
      Spec spec = new Spec(new String[] { "year" }, new String[0]);
      spec.aggs(new Agg("Sales (USD)", "Sum"));
      List<TableCellBinding> cells = summaryCells(spec.run().nlayout);
      assertFalse(cells.isEmpty());

      for(TableCellBinding b : cells) {
         assertEquals("Sales (USD)", b.getValue());
         assertEquals("Sum", b.getFormula());
      }
   }

   // the converted cell name of a dimension is the same as the name a freehand cell gets by
   // default (TableCellBinding.getValidName)
   @Disabled("Bug #81255: TopN/Others cell name keeps characters invalid in a script identifier")
   @Test
   void dimensionNameWithHyphenGetsTheDefaultCellName() {
      TableLayout layout = convert(new String[] { "Sales-Region" }, new String[0], null);
      assertEquals("Sales_Region", groupBinding(layout, "Sales-Region").getCellName());
   }

   @Disabled("Bug #81255: TopN/Others cell name keeps characters invalid in a script identifier")
   @ParameterizedTest(name = "dimension named [{0}] gets the default cell name")
   @ValueSource(strings = { "a/b", "a&b", "Qty(x)", "a\"b", "a,b" })
   void dimensionNameWithSpecialCharacterGetsTheDefaultCellName(String name) {
      TableLayout layout = convert(new String[] { name }, new String[0], null);
      StringBuilder expected = new StringBuilder();

      for(int i = 0; i < name.length(); i++) {
         char c = name.charAt(i);
         boolean ok = i == 0 ? Character.isJavaIdentifierStart(c) :
            Character.isJavaIdentifierPart(c);
         expected.append(ok ? c : '_');
      }

      assertEquals(expected.toString(), groupBinding(layout, name).getCellName());
   }

   // ---- several aggregates, titles, two column and N formulas ----

   @Test
   void eachSummaryCellKeepsItsOwnColumnAndFormula() {
      Spec spec = new Spec(new String[] { "year" }, new String[0]);
      spec.aggs(new Agg("sales", "Sum"), new Agg("qty", "Max"));
      Set<String> pairs = new TreeSet<>();

      for(TableCellBinding b : summaryCells(spec.run().nlayout)) {
         pairs.add(b.getValue() + "|" + b.getFormula());
      }

      assertEquals(Set.of("qty|Max", "sales|Sum"), pairs);
   }

   @Test
   void aggregateTitleCellsAreTextCells() {
      Spec spec = new Spec(new String[] { "year" }, new String[0]);
      spec.aggs(new Agg("sales", "Sum"), new Agg("qty", "Max"));
      TableLayout layout = spec.run().nlayout;
      int titles = 0;

      for(int r = 0; r < layout.getRowCount(); r++) {
         for(int c = 0; c < layout.getColCount(); c++) {
            TableCellBinding b = (TableCellBinding) layout.getCellBinding(r, c);

            if(b != null && b.getBType() != TableCellBinding.SUMMARY &&
               b.getBType() != TableCellBinding.GROUP && b.getValue() != null &&
               (b.getValue().contains("sales") || b.getValue().contains("qty")))
            {
               titles++;
               assertEquals(CellBinding.BIND_TEXT, b.getType());
            }
         }
      }

      assertTrue(titles > 0, "no aggregate title cell");

      // every cell of the header row, including the empty corner cell, is a text cell
      for(int c = 0; c < layout.getColCount(); c++) {
         TableCellBinding b = (TableCellBinding) layout.getCellBinding(0, c);
         assertNotNull(b);
         assertEquals(CellBinding.BIND_TEXT, b.getType(), "header cell " + c);
      }
   }

   @Test
   void twoColumnFormulaSummaryCellKeepsFirstColumn() {
      Spec spec = new Spec(new String[] { "year" }, new String[0]);
      spec.aggs(new Agg("sales", "Correlation").second("qty"));
      List<TableCellBinding> cells = summaryCells(spec.run().nlayout);
      assertFalse(cells.isEmpty());

      for(TableCellBinding b : cells) {
         assertEquals("sales", b.getValue());
         assertEquals("Correlation", b.getFormula());
      }
   }

   @Test
   void nthLargestSummaryCellKeepsTheColumn() {
      Spec spec = new Spec(new String[] { "year" }, new String[0]);
      spec.aggs(new Agg("sales", "NthLargest").n("2"));
      List<TableCellBinding> cells = summaryCells(spec.run().nlayout);
      assertFalse(cells.isEmpty());

      for(TableCellBinding b : cells) {
         assertEquals("sales", b.getValue());
      }
   }

   // ---- sort, nesting, multi level headers ----

   @Test
   void descendingRowDimensionKeepsDescendingOrder() {
      Spec spec = new Spec(new String[] { "year" }, new String[0]);
      spec.dimTweak = dim -> dim.setOrder(XConstants.SORT_DESC);
      TableLayout layout = spec.run().nlayout;
      assertEquals(XConstants.SORT_DESC, groupBinding(layout, "year").getOrderInfo(false).getOrder());
   }

   @Test
   void nestedDateRowDimensionKeepsDateColumnAndLevel() {
      Spec spec = new Spec(new String[] { "Year(date)", "Month(date)" }, new String[0]);
      spec.dimTweak = dim -> {
         int level = "Year(date)".equals(dim.getGroupColumnValue()) ?
            DateRangeRef.YEAR_INTERVAL : DateRangeRef.MONTH_INTERVAL;
         dim.setGroupColumnValue("date");
         ((ColumnRef) dim.getDataRef()).setDataType(XSchema.DATE);
         dim.setDateLevelValue(level + "");
      };
      TableLayout layout = spec.run().nlayout;
      List<TableCellBinding> months = new ArrayList<>();

      for(TableCellBinding b : groupCellsAny(layout)) {
         if(b.getDateOption() == DateRangeRef.MONTH_INTERVAL) {
            months.add(b);
         }
      }

      assertFalse(months.isEmpty(), "no month cell");
      TableCellBinding month = months.get(0);
      assertEquals("Month(date)", month.getValue());
      assertNotNull(month.getRowGroup());
      assertNotEquals(TableCellBinding.DEFAULT_GROUP, month.getRowGroup());
      boolean outer = false;

      for(TableCellBinding b : groupCellsAny(layout)) {
         outer |= b.getDateOption() == DateRangeRef.YEAR_INTERVAL &&
            month.getRowGroup().equals(b.getCellName());
      }

      assertTrue(outer, "the month cell does not refer to the year cell");
   }

   @Test
   void multiLevelHeadersChainOuterToInnerCellNames() {
      String[] rows = { "a", "b", "c" };
      String[] cols = { "d", "e" };
      TableLayout layout = convert(rows, cols, null);
      assertEquals("a", groupCells(layout, "a").get(0).getCellName());
      assertEquals("a", groupCells(layout, "b").get(0).getRowGroup());
      assertEquals("b", groupCells(layout, "c").get(0).getRowGroup());
      assertEquals("d", groupCells(layout, "d").get(0).getCellName());
      assertEquals("d", groupCells(layout, "e").get(0).getColGroup());
   }

   // pinned current behavior: a crosstab-mode layout without regions converts to a calc layout
   // with an empty header region and an empty detail region
   @Test
   void pinnedEmptyCrosstabLayoutConvertsToHeaderAndDetailRegions() {
      TableLayout olayout = new TableLayout();
      olayout.setMode(TableLayout.CROSSTAB);
      TableLayout result = VSTableConverter.changeTableMode(null, olayout, TableLayout.CALC);
      assertEquals(TableLayout.CALC, result.getMode());
      assertEquals(2, result.getRegionCount());
      assertEquals(1, result.getRegions(TableDataPath.HEADER).length);
      assertEquals(1, result.getRegions(TableDataPath.DETAIL).length);

      for(int r = 0; r < result.getRowCount(); r++) {
         for(int c = 0; c < result.getColCount(); c++) {
            assertNull(result.getCellBinding(r, c));
         }
      }
   }

   // ---- normal table to freehand ----

   private static TableLayout convertNormalTable(int headerRows, SourceInfo source) {
      Viewsheet vs = new Viewsheet();
      TableVSAssembly table = new TableVSAssembly(vs, "Table1");
      ColumnSelection columns = new ColumnSelection();
      columns.addAttribute(new ColumnRef(new AttributeRef(null, "x")));
      columns.addAttribute(new ColumnRef(new AttributeRef(null, "y")));
      ((TableVSAssemblyInfo) table.getInfo()).setColumnSelection(columns);
      table.setSourceInfo(source);
      Object[][] data = new Object[headerRows + 2][2];

      for(int r = 0; r < headerRows; r++) {
         data[r] = new Object[] { "x" + r, "y" + r };
      }

      data[headerRows] = new Object[] { 1, 2 };
      data[headerRows + 1] = new Object[] { 3, 4 };
      DefaultTableLens lens = new DefaultTableLens(data);
      lens.setHeaderRowCount(headerRows);
      TableLayout olayout = VSLayoutTool.generateLayout(table, lens, vs);
      assertEquals(TableLayout.NORMAL, olayout.getMode());
      return VSTableConverter.changeTableMode(table, olayout, TableLayout.CALC);
   }

   @Test
   void normalTableHeaderBecomesTextAndDetailExpandsVertically() {
      SourceInfo source = new SourceInfo(SourceInfo.ASSET, "prefix", "ws1:Query1");
      TableLayout layout = convertNormalTable(1, source);
      int headers = 0;
      int details = 0;

      for(int r = 0; r < layout.getRowCount(); r++) {
         for(int c = 0; c < layout.getColCount(); c++) {
            TableCellBinding b = (TableCellBinding) layout.getCellBinding(r, c);

            if(b == null) {
               continue;
            }

            TableDataPath path = layout.getCellDataPath(r, c);

            if(path.getType() == TableDataPath.HEADER) {
               headers++;
               assertEquals(CellBinding.BIND_TEXT, b.getType());
            }
            else if(path.getType() == TableDataPath.DETAIL) {
               details++;
               assertEquals(TableCellBinding.EXPAND_V, b.getExpansion());
               assertEquals("ws1:Query1", b.getSource());
               assertEquals(TableCellBinding.DEFAULT_GROUP, b.getRowGroup());
               assertEquals(TableCellBinding.DEFAULT_GROUP, b.getColGroup());
            }
         }
      }

      assertTrue(headers > 0, "no header cell");
      assertTrue(details > 0, "no detail cell");
   }

   @Test
   void normalTableWithTwoHeaderRowsHasOneHeaderRegionOfTwoRows() {
      TableLayout layout = convertNormalTable(2, null);
      TableLayout.Region[] headers = layout.getRegions(TableDataPath.HEADER);
      assertEquals(1, headers.length);
      assertEquals(2, headers[0].getRowCount());
   }

   // ---- sizes, spans, input immutability ----

   @Test
   void rowHeightsAndColumnWidthsAreCopied() {
      Spec spec = new Spec(new String[] { "year" }, new String[] { "region" });
      spec.layoutTweak = layout -> {
         for(int r = 0; r < layout.getRowCount(); r++) {
            layout.setRowHeight(r, 20 + r);
         }

         for(int c = 0; c < layout.getColCount(); c++) {
            layout.setColWidth(c, 50 + 10 * c);
         }
      };
      spec.run();
      assertTrue(spec.nlayout.getRowCount() > 0);

      for(int r = 0; r < spec.nlayout.getRowCount(); r++) {
         assertEquals(spec.olayout.getRowHeight(r), spec.nlayout.getRowHeight(r));
      }

      for(int c = 0; c < spec.nlayout.getColCount(); c++) {
         assertEquals(spec.olayout.getColWidth(c), spec.nlayout.getColWidth(c));
      }
   }

   @Test
   void spansAreCopiedAndBindingsInsideASpanAreCleared() {
      Spec spec = new Spec(ROWS, new String[0]);
      spec.customizer = ci -> ci.setMergeSpan(true);
      spec.run();
      TableLayout olayout = spec.olayout;
      TableLayout nlayout = spec.nlayout;
      int spans = 0;

      for(int r = 0; r < olayout.getRowCount(); r++) {
         for(int c = 0; c < olayout.getColCount(); c++) {
            Dimension span = olayout.getSpan(r, c);

            if(span == null) {
               continue;
            }

            spans++;
            assertEquals(span, nlayout.getSpan(r, c));

            if(olayout.getCellBinding(r, c) != null) {
               assertNotNull(nlayout.getCellBinding(r, c), "first cell of a span lost");
            }

            for(int rr = r; rr < r + span.height && rr < nlayout.getRowCount(); rr++) {
               for(int cc = c; cc < c + span.width && cc < nlayout.getColCount(); cc++) {
                  if(rr != r || cc != c) {
                     assertNull(nlayout.getCellBinding(rr, cc),
                                "binding inside a span at " + rr + "," + cc);
                  }
               }
            }
         }
      }

      assertTrue(spans > 0, "the crosstab layout has no span");
   }

   // the Others group follows the dimension order of the runtime headers
   @Disabled("Bug #81258: fixTopN keeps Others on the outer dimension when design and runtime headers differ")
   @Test
   void outerDimensionAtRuntimeTurnsOthersOffWhenDesignHasOnlyThatDimension() {
      Spec spec = new Spec(ROWS, new String[0]);
      spec.topNDim = "year";
      spec.customizer = ci -> {
         DataRef[] runtime = ci.getRuntimeRowHeaders();
         ci.setDesignRowHeaders(new DataRef[] { runtime[0] });
      };
      TableLayout layout = spec.run().nlayout;
      assertFalse(groupBinding(layout, "year").getTopN(false).isOthers());
   }

   // the conversion must not change the layout it reads from
   @Test
   void conversionDoesNotChangeTheCrosstabLayout() {
      List<String> before = new ArrayList<>();
      Spec spec = new Spec(ROWS, new String[] { "region" });
      spec.beforeConvert = ci -> { };
      spec.layoutTweak = layout -> before.addAll(snapshot(layout));
      spec.run();
      assertFalse(before.isEmpty());
      assertEquals(before, snapshot(spec.olayout));
   }

   private static List<String> snapshot(TableLayout layout) {
      List<String> list = new ArrayList<>();

      for(int r = 0; r < layout.getRowCount(); r++) {
         for(int c = 0; c < layout.getColCount(); c++) {
            TableCellBinding b = (TableCellBinding) layout.getCellBinding(r, c);

            list.add(r + "," + c + ":" + (b == null ? "null" : b.getType() + "/" + b.getBType() +
               "/" + b.getValue() + "/" + b.getCellName() + "/" + b.getRowGroup() + "/" +
               b.getColGroup() + "/" + b.getExpansion() + "/" + b.getFormula()));
         }
      }

      return list;
   }

   // ---- merged cells and different row / column total settings ----

   @Test
   void mergedGroupCellsMergeOnTheirOwnCellName() {
      TableLayout layout = convert(ROWS, new String[0], null, ci -> ci.setMergeSpan(true));
      int named = 0;

      for(TableCellBinding b : groupCellsAny(layout)) {
         if(b.getCellName() != null) {
            named++;
            assertEquals(b.getCellName(), b.getMergeRowGroup());
            assertEquals(b.getCellName(), b.getMergeColGroup());
         }
      }

      assertTrue(named > 0, "no named group cell");
   }

   @Test
   void summaryCellsExistOnlyForTheShownTotals() {
      TableLayout layout = convert(ROWS, COLS, null, ci -> { }, f -> {
         f.setSuppressRowGrandTotal(true);
         f.setSuppressRowGroupTotal(new boolean[] { true, true });
         f.setSuppressColumnGrandTotal(false);
         f.setSuppressColumnGroupTotal(new boolean[] { false, false });
      });
      List<TableCellBinding> cells = summaryCells(layout);
      assertEquals(4, countSummary(cells, "quarter", "channel"));
      assertEquals(2, countSummary(cells, "quarter", "region"));
      assertEquals(1, countSummary(cells, "quarter", TableCellBinding.DEFAULT_GROUP));
      assertEquals(0, countSummary(cells, "year", "channel"));
      assertEquals(0, countSummary(cells, TableCellBinding.DEFAULT_GROUP, "channel"));
      assertEquals(0, countSummary(cells, TableCellBinding.DEFAULT_GROUP,
                                   TableCellBinding.DEFAULT_GROUP));
   }

   private static List<TableCellBinding> groupCellsAny(TableLayout layout) {
      List<TableCellBinding> list = new ArrayList<>();

      for(int r = 0; r < layout.getRowCount(); r++) {
         for(int c = 0; c < layout.getColCount(); c++) {
            TableCellBinding b = (TableCellBinding) layout.getCellBinding(r, c);

            if(b != null && b.getBType() == TableCellBinding.GROUP) {
               list.add(b);
            }
         }
      }

      return list;
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
      return convert(rowDims, colDims, topNDim, customizer, filterCustomizer, cinfo -> { });
   }

   /**
    * @param beforeConvert runs after the crosstab layout was generated, before it is converted
    */
   private static TableLayout convert(String[] rowDims, String[] colDims, String topNDim,
                                      Consumer<VSCrosstabInfo> customizer,
                                      Consumer<CrossTabFilter> filterCustomizer,
                                      Consumer<VSCrosstabInfo> beforeConvert)
   {
      Spec spec = new Spec(rowDims, colDims);
      spec.topNDim = topNDim;
      spec.customizer = customizer;
      spec.filterCustomizer = filterCustomizer;
      spec.beforeConvert = beforeConvert;
      return spec.run().nlayout;
   }

   /** One aggregate of the crosstab under test. */
   private static final class Agg {
      Agg(String column, String formula) {
         this.column = column;
         this.formula = formula;
      }

      Agg second(String second) {
         this.second = second;
         return this;
      }

      Agg n(String n) {
         this.n = n;
         return this;
      }

      Agg percent(int percent) {
         this.percent = percent;
         return this;
      }

      final String column;
      final String formula;
      String second;
      String n;
      int percent;
   }

   /** Everything needed to build a crosstab, its crosstab layout and the converted layout. */
   private static final class Spec {
      Spec(String[] rows, String[] cols) {
         this.rows = rows;
         this.cols = cols;
      }

      Spec aggs(Agg... aggs) {
         this.aggs = List.of(aggs);
         return this;
      }

      Spec run() {
         Viewsheet vs = new Viewsheet();
         crosstab = new CrosstabVSAssembly(vs, "Crosstab1");
         VSCrosstabInfo cinfo = crosstab.getVSCrosstabInfo();
         AggregateInfo ainfo = new AggregateInfo();
         List<DataRef> rowRefs = new ArrayList<>();
         List<DataRef> colRefs = new ArrayList<>();
         List<String> headers = new ArrayList<>();

         for(String name : rows) {
            VSDimensionRef dim = dimension(name, name.equals(topNDim));
            dimTweak.accept(dim);
            rowRefs.add(dim);
            GroupRef group = dim.createGroupRef(null);
            groupTweak.accept(group);
            ainfo.addGroup(group);
            headers.add(name);
         }

         for(String name : cols) {
            VSDimensionRef dim = dimension(name, name.equals(topNDim));
            dimTweak.accept(dim);
            colRefs.add(dim);
            ainfo.addGroup(dim.createGroupRef(null));
            headers.add(name);
         }

         List<DataRef> sums = new ArrayList<>();

         for(Agg agg : aggs) {
            VSAggregateRef sum = new VSAggregateRef();
            sum.setDataRef(new ColumnRef(new AttributeRef(null, agg.column)));
            sum.setColumnValue(agg.column);
            sum.setFormulaValue(agg.formula);

            if(agg.second != null) {
               sum.setSecondaryColumnValue(agg.second);
            }

            if(agg.n != null) {
               sum.setNValue(agg.n);
            }

            if(agg.percent != 0) {
               sum.setPercentageOption(agg.percent);
            }

            sums.add(sum);
            ainfo.addAggregate(sum.createAggregateRef(null));
            headers.add(agg.column);
         }

         DataRef[] rowArr = rowRefs.toArray(new DataRef[0]);
         DataRef[] colArr = colRefs.toArray(new DataRef[0]);
         DataRef[] sumArr = sums.toArray(new DataRef[0]);
         cinfo.setDesignRowHeaders(rowArr);
         cinfo.setDesignColHeaders(colArr);
         cinfo.setDesignAggregates(sumArr);
         cinfo.setRuntimeRowHeaders(rowArr);
         cinfo.setRuntimeColHeaders(colArr);
         cinfo.setRuntimeAggregates(sumArr);
         cinfo.setAggregateInfo(ainfo);
         customizer.accept(cinfo);

         // base data: every dimension has two values, all combinations present, so that sub
         // totals and grand totals exist when they are not suppressed
         int ndims = rows.length + cols.length;
         int nrows = 1 << ndims;
         Object[][] data = new Object[nrows + 1][headers.size()];
         data[0] = headers.toArray();

         for(int r = 0; r < nrows; r++) {
            for(int c = 0; c < ndims; c++) {
               data[r + 1][c] = headers.get(c) + (((r >> c) & 1) + 1);
            }

            for(int a = 0; a < aggs.size(); a++) {
               data[r + 1][ndims + a] = 10.0 * (r + 1);
            }
         }

         DefaultTableLens base = new DefaultTableLens(data);
         base.setHeaderRowCount(1);
         int[] rowh = new int[rows.length];
         int[] colh = new int[cols.length];
         int[] measures = new int[aggs.size()];
         Formula[] formulas = new Formula[aggs.size()];

         for(int i = 0; i < rowh.length; i++) {
            rowh[i] = i;
         }

         for(int i = 0; i < colh.length; i++) {
            colh[i] = rowh.length + i;
         }

         for(int i = 0; i < measures.length; i++) {
            measures[i] = ndims + i;
            formulas[i] = new SumFormula();
         }

         CrossTabFilter meta = new CrossTabFilter(base, rowh, colh, measures, formulas);
         filterCustomizer.accept(meta);
         olayout = VSLayoutTool.generateLayout(crosstab, meta, vs);
         assertEquals(TableLayout.CROSSTAB, olayout.getMode());
         layoutTweak.accept(olayout);
         beforeConvert.accept(cinfo);
         nlayout = VSTableConverter.changeTableMode(crosstab, olayout, TableLayout.CALC);
         assertEquals(TableLayout.CALC, nlayout.getMode());
         return this;
      }

      final String[] rows;
      final String[] cols;
      String topNDim;
      List<Agg> aggs = List.of(new Agg("sales", "Sum"));
      Consumer<VSDimensionRef> dimTweak = dim -> { };
      Consumer<GroupRef> groupTweak = group -> { };
      Consumer<VSCrosstabInfo> customizer = cinfo -> { };
      Consumer<CrossTabFilter> filterCustomizer = filter -> { };
      Consumer<VSCrosstabInfo> beforeConvert = cinfo -> { };
      Consumer<TableLayout> layoutTweak = layout -> { };
      CrosstabVSAssembly crosstab;
      TableLayout olayout;
      TableLayout nlayout;
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
