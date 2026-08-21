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
package inetsoft.report.composition.execution;

import inetsoft.report.TableDataPath;
import inetsoft.report.TableLens;
import inetsoft.report.filter.CrossTabFilter;
import inetsoft.report.internal.binding.Field;
import inetsoft.report.internal.table.TableHighlightAttr;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.IntegrationTestConfiguration;
import inetsoft.test.SreeHome;
import inetsoft.test.SwapperTestConfiguration;
import inetsoft.uql.ColumnSelection;
import inetsoft.uql.VariableTable;
import inetsoft.uql.asset.AbstractSheet;
import inetsoft.uql.asset.AggregateFormula;
import inetsoft.uql.asset.ColumnRef;
import inetsoft.uql.asset.EmbeddedTableAssembly;
import inetsoft.uql.asset.SourceInfo;
import inetsoft.uql.asset.Worksheet;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.util.XEmbeddedTable;
import inetsoft.uql.viewsheet.CrosstabVSAssembly;
import inetsoft.uql.viewsheet.VSAggregateRef;
import inetsoft.uql.viewsheet.VSCrosstabInfo;
import inetsoft.uql.viewsheet.VSDimensionRef;
import inetsoft.uql.viewsheet.Viewsheet;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.lang.reflect.Method;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Regression test for Finding 2b of the composer plugin test plan: {@code list_highlights} on a
 * crosstab with 2+ aggregates and no column dimension reported the row dimension only, missing
 * every aggregate, at the cell {@code AssemblyHighlightService.Region.firstDataCell()} (row=1,
 * col=1) assumes is the first data cell.
 *
 * <p>{@code AbstractCrosstabVSAQuery} sets {@code showSummaryHeaders} to {@code true} whenever
 * 2 or more aggregates are bound. {@code VSCrosstabInfo}'s {@code sideBySide} option defaults to
 * {@code false} (aggregates stacked as separate rows, not laid out side by side as columns), so
 * {@code CrossTabFilter} inserts one extra header COLUMN (not row) labeling which aggregate each
 * row belongs to. That shifts {@code CrossTabFilter#getHeaderColCount()} by exactly one for 2+
 * aggregates versus a single aggregate, so the literal cell (1, 1) -- correctly the first DATA
 * cell for one aggregate -- lands on that extra header column for two or more, and {@code
 * CrossFilterDataDescriptor#getAvailableFields} on a header row/col returns only the row
 * dimension fields, not the aggregates.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class, IntegrationTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class CrosstabHighlightSummaryHeaderColumnShiftTest {
   private static final String ENTITY = "SALES";

   /** Builds a crosstab with row dimension STATE and the given aggregate column names. */
   private CrossTabFilter run(String... aggregateCols) throws Exception {
      Worksheet ws = new Worksheet();
      EmbeddedTableAssembly table = new EmbeddedTableAssembly(ws, "Query1");
      ColumnSelection cs = new ColumnSelection();
      cs.addAttribute(new ColumnRef(new AttributeRef(ENTITY, "STATE")));
      cs.addAttribute(new ColumnRef(new AttributeRef(ENTITY, "DISCOUNT")));
      cs.addAttribute(new ColumnRef(new AttributeRef(ENTITY, "ORDER_ID")));
      table.setColumnSelection(cs, false);

      Object[][] rows = new Object[][]{
         {"STATE", "DISCOUNT", "ORDER_ID"},
         {"NJ", 1.5, "O1"}, {"NJ", 2.5, "O2"}, {"NJ", 3.0, "O3"},
         {"CA", 4.0, "O4"}, {"CA", 5.0, "O5"},
         {"MA", 6.0, "O6"},
      };
      table.setEmbeddedData(new XEmbeddedTable(new String[]{ "string", "double", "string" }, rows));
      ws.addAssembly(table);

      Viewsheet vs = new Viewsheet();
      CrosstabVSAssembly crosstab = new CrosstabVSAssembly(vs, "Crosstab1");
      crosstab.setSourceInfo(new SourceInfo(SourceInfo.ASSET, null, "Query1"));
      vs.addAssembly(crosstab);

      VSDimensionRef stateDim = new VSDimensionRef();
      stateDim.setDataRef(new AttributeRef(ENTITY, "STATE"));
      stateDim.setGroupColumnValue("STATE");

      List<VSAggregateRef> aggs = new java.util.ArrayList<>();

      for(String col : aggregateCols) {
         VSAggregateRef agg = new VSAggregateRef();
         agg.setDataRef(new AttributeRef(ENTITY, col));
         agg.setColumnValue(col);
         agg.setFormulaValue("string".equals(col) ? AggregateFormula.COUNT_ALL.getFormulaName()
            : AggregateFormula.SUM.getFormulaName());
         aggs.add(agg);
      }

      VSCrosstabInfo cinfo = new VSCrosstabInfo();
      cinfo.setDesignRowHeaders(new VSDimensionRef[]{ stateDim });
      cinfo.setDesignColHeaders(new VSDimensionRef[0]);
      cinfo.setDesignAggregates(aggs.toArray(new VSAggregateRef[0]));
      crosstab.setVSCrosstabInfo(cinfo);

      Method setBaseWorksheet = Viewsheet.class.getDeclaredMethod("setBaseWorksheet", Worksheet.class);
      setBaseWorksheet.setAccessible(true);
      setBaseWorksheet.invoke(vs, ws);

      ViewsheetSandbox box = new ViewsheetSandbox(vs, AbstractSheet.SHEET_RUNTIME_MODE, null, false, null);
      AssetQuerySandbox wbox = new AssetQuerySandbox(ws, null, new VariableTable());
      java.lang.reflect.Field wboxField = ViewsheetSandbox.class.getDeclaredField("wbox");
      wboxField.setAccessible(true);
      wboxField.set(box, wbox);

      cinfo.update(vs, cs, null, false, "Query1", null);

      TableLens lens = new CrosstabVSAQuery(box, "Crosstab1", false).getTableLens(false);
      assertTrue(lens instanceof CrossTabFilter, "expected a raw CrossTabFilter with no sort/max-row wrapping");
      CrossTabFilter crossTabFilter = (CrossTabFilter) lens;
      crossTabFilter.moreRows(TableLens.EOT);
      return crossTabFilter;
   }

   private static List<String> fieldNamesAt(CrossTabFilter table, int row, int col) {
      TableDataPath dpath = table.getDescriptor().getCellDataPath(row, col);
      Field[] fields = TableHighlightAttr.getAvailableFields(table, dpath);
      return java.util.Arrays.stream(fields).map(Field::getName).collect(Collectors.toList());
   }

   @Test
   void oneAggregateHasNoExtraHeaderColumn() throws Exception {
      CrossTabFilter table = run("ORDER_ID");
      assertEquals(1, table.getHeaderColCount(),
         "single aggregate: header col count is just the dimension header col");
   }

   @Test
   void twoAggregatesAddOneExtraHeaderColumn() throws Exception {
      CrossTabFilter table = run("DISCOUNT", "ORDER_ID");
      assertEquals(2, table.getHeaderColCount(),
         "2+ aggregates default to sideBySide=false (stacked vertically), which inserts one " +
         "extra header COLUMN (not row) labeling which aggregate each row belongs to");
   }

   @Test
   void oneAggregateFieldsAtFirstDataCellIncludeTheAggregate() throws Exception {
      CrossTabFilter table = run("ORDER_ID");
      List<String> fields = fieldNamesAt(table, 1, 1);
      assertTrue(fields.stream().anyMatch(n -> n.contains("ORDER_ID")),
         "(1,1) is a genuine data cell for a single aggregate, so its aggregate must be listed: " + fields);
   }

   @Test
   void twoAggregatesFieldsAtLiteralRowOneColOneMissBothAggregates() throws Exception {
      CrossTabFilter table = run("DISCOUNT", "ORDER_ID");
      List<String> fields = fieldNamesAt(table, 1, 1);
      assertFalse(fields.stream().anyMatch(n -> n.contains("DISCOUNT") || n.contains("ORDER_ID")),
         "col 1 is the extra summary-header column for 2 aggregates, not a data cell, so neither " +
         "aggregate should appear here -- reproduces the list_highlights bug numerically: " + fields);
   }

   @Test
   void twoAggregatesFieldsAtTheRealFirstDataColumnIncludeBothAggregates() throws Exception {
      CrossTabFilter table = run("DISCOUNT", "ORDER_ID");
      int headerColCount = table.getHeaderColCount();
      List<String> fields = fieldNamesAt(table, 1, headerColCount);
      assertTrue(fields.stream().anyMatch(n -> n.contains("DISCOUNT")), "missing DISCOUNT: " + fields);
      assertTrue(fields.stream().anyMatch(n -> n.contains("ORDER_ID")), "missing ORDER_ID: " + fields);
   }
}
