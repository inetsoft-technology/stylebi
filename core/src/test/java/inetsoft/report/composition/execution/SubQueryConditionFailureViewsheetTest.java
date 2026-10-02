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

import inetsoft.report.TableLens;
import inetsoft.report.composition.graph.VSDataSet;
import inetsoft.test.*;
import inetsoft.uql.*;
import inetsoft.uql.asset.*;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.erm.DataRef;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.util.XEmbeddedTable;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.graph.*;
import inetsoft.util.MessageException;
import inetsoft.web.viewsheet.event.OpenViewsheetEvent;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #77123 at the viewsheet level: a table and a chart bound to a worksheet table whose
 * pre-condition is a sub query ("type one of (S.type)") whose sub table fails while it is
 * built (as a query timeout would). The viewsheet sandbox must pass the failure on to the
 * caller (the table/chart controllers turn it into an error message) instead of returning
 * the worksheet table unfiltered, and once the sub query recovers and the data is
 * refreshed the assemblies show the filtered rows.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, IntegrationTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome(importResources = "/inetsoft/report/composition/EmbeddedVS1.zip")
@Tag("core")
@Tag("integration")
class SubQueryConditionFailureViewsheetTest {
   @AfterEach
   void tearDown() {
      FAIL.set(false);
   }

   @Test
   void failingSubQueryIsReportedNotUnfiltered() throws Exception {
      ViewsheetSandbox box = vsResource.getRuntimeViewsheet().getViewsheetSandbox().orElseThrow();
      Viewsheet vs = box.getViewsheet();
      SourceInfo source =
         (SourceInfo) ((TableVSAssembly) vs.getAssembly(TABLE)).getSourceInfo().clone();
      chart(vs, CHART, source);

      Set<String> all = types(box.getData(TABLE));
      assertEquals(Set.of("NAMED", "UNNAMED"), all, "fixture data");

      addSubQueryCondition(box.getWorksheet(), source.getSource());
      FAIL.set(true);
      refresh(box);

      for(String name : new String[] { TABLE, CHART }) {
         List<Set<String>> rows = new ArrayList<>();
         MessageException ex = assertThrows(MessageException.class,
            () -> rows.add(types(box.getData(name))),
            () -> name + ": the sub-query condition was dropped, rows of types " + rows);
         assertEquals(TIMEOUT, ex.getMessage());
      }

      // the sub query recovers; refreshing the data (as the viewsheet refresh does) returns
      // the filtered rows, so nothing below the viewsheet cached the failure
      FAIL.set(false);
      refresh(box);
      assertEquals(Set.of("UNNAMED"), types(box.getData(TABLE)), TABLE);
      assertEquals(Set.of("UNNAMED"), types(box.getData(CHART)), CHART);
   }

   private static void refresh(ViewsheetSandbox box) {
      box.resetDataMap(TABLE);
      box.resetDataMap(CHART);
   }

   /**
    * Add the embedded sub table S {@code (type = UNNAMED)} and the pre-condition
    * {@code type one of (S.type)} to the worksheet table.
    */
   private static void addSubQueryCondition(Worksheet ws, String tableName) {
      TableAssembly table = (TableAssembly) ws.getAssembly(tableName);
      assertNotNull(table, tableName);
      EmbeddedTableAssembly s = new EmbeddedTableAssembly(ws, "S77123");
      s.setEmbeddedData(new FlakyData(new String[] { XSchema.STRING },
                                      new Object[][] { { "type" }, { "UNNAMED" } }));
      ws.addAssembly(s);

      SubQueryValue sub = new SubQueryValue();
      sub.setQuery(s.getName());
      sub.setAttribute(s.getColumnSelection(false).getAttribute("type"));
      assertTrue(sub.update(ws));
      AssetCondition cond = new AssetCondition();
      cond.setOperation(XCondition.ONE_OF);
      cond.setType(XSchema.STRING);
      cond.addValue(sub);
      DataRef type = table.getColumnSelection(false).getAttribute("type");
      assertNotNull(type, "type column of " + tableName);
      ConditionList list = new ConditionList();
      list.append(new ConditionItem(type, cond, 0));
      table.setPreConditionList(list);
   }

   private static ChartVSAssembly chart(Viewsheet vs, String name, SourceInfo source) {
      ChartVSAssembly chart = new ChartVSAssembly(vs, name);
      chart.setSourceInfo(source);
      VSChartInfo info = chart.getVSChartInfo();
      VSChartDimensionRef dim =
         new VSChartDimensionRef(new ColumnRef(new AttributeRef(null, "type")));
      dim.setGroupColumnValue("type");
      info.addXField(dim);
      VSChartAggregateRef agg = new VSChartAggregateRef();
      agg.setColumnValue("wind");
      agg.setFormulaValue("Sum");
      info.addYField(agg);
      vs.addAssembly(chart);
      return chart;
   }

   /**
    * The distinct values of the {@code type} column.
    */
   private static Set<String> types(Object data) {
      TableLens lens = data instanceof VSDataSet ? ((VSDataSet) data).getTable() : (TableLens) data;
      assertNotNull(lens, "no data");
      int col = -1;

      for(int c = 0; c < lens.getColCount(); c++) {
         if("type".equals(String.valueOf(lens.getObject(0, c)))) {
            col = c;
         }
      }

      assertTrue(col >= 0, "no type column");
      Set<String> types = new TreeSet<>();

      for(int r = lens.getHeaderRowCount(); lens.moreRows(r); r++) {
         types.add(String.valueOf(lens.getObject(r, col)));
      }

      return types;
   }

   private static OpenViewsheetEvent openViewsheetEvent() {
      OpenViewsheetEvent event = new OpenViewsheetEvent();
      event.setEntryId("1^128^__NULL__^EmbeddedVS1^host-org");
      event.setViewer(true);
      return event;
   }

   /**
    * Embedded data whose rows can't be read while {@link #FAIL} is set, as a query that
    * timed out.
    */
   private static final class FlakyData extends XEmbeddedTable {
      FlakyData(String[] types, Object[][] data) {
         super(types, data);
      }

      @Override
      public boolean moreRows(int row) {
         check();
         return super.moreRows(row);
      }

      @Override
      public Object getObject(int r, int c) {
         check();
         return super.getObject(r, c);
      }

      private static void check() {
         if(FAIL.get()) {
            throw new MessageException(TIMEOUT);
         }
      }
   }

   @RegisterExtension
   RuntimeViewsheetExtension vsResource = new RuntimeViewsheetExtension(openViewsheetEvent());

   private static final String TABLE = "TableView1";
   private static final String CHART = "Chart77123";
   private static final String TIMEOUT = "Query timeout";
   private static final AtomicBoolean FAIL = new AtomicBoolean();
}
