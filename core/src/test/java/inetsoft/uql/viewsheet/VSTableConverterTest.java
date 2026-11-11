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

   /**
    * Build a crosstab with the given row and column dimensions and Sum(sales), set TopN with
    * Others on {@code topNDim} (if not null), and convert it to a calc layout.
    */
   private static TableLayout convert(String[] rowDims, String[] colDims, String topNDim) {
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

      // two rows of base data, only the structure matters for the layout
      Object[][] data = new Object[3][headers.size()];
      data[0] = headers.toArray();

      for(int r = 1; r <= 2; r++) {
         for(int c = 0; c < headers.size() - 1; c++) {
            data[r][c] = headers.get(c) + r;
         }

         data[r][headers.size() - 1] = 10.0 * r;
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

      TableLens meta = new CrossTabFilter(base, rowh, colh, new int[] { headers.size() - 1 },
                                          new inetsoft.report.filter.Formula[] { new SumFormula() });
      TableLayout olayout = VSLayoutTool.generateLayout(crosstab, meta, vs);
      assertEquals(TableLayout.CROSSTAB, olayout.getMode());
      TableLayout nlayout = VSTableConverter.changeTableMode(crosstab, olayout, TableLayout.CALC);
      assertEquals(TableLayout.CALC, nlayout.getMode());
      return nlayout;
   }

   private static VSDimensionRef dimension(String name, boolean topNWithOthers) {
      VSDimensionRef dim = new VSDimensionRef(new ColumnRef(new AttributeRef(null, name)));
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
