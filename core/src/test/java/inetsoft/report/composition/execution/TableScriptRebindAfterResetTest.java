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

import inetsoft.mv.MVManager;
import inetsoft.report.TableLens;
import inetsoft.report.composition.ChangedAssemblyList;
import inetsoft.report.composition.VSTableLens;
import inetsoft.test.*;
import inetsoft.uql.ColumnSelection;
import inetsoft.uql.util.XSourceInfo;
import inetsoft.uql.asset.*;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.service.DataSourceRegistry;
import inetsoft.uql.util.XEmbeddedTable;
import inetsoft.uql.viewsheet.*;
import inetsoft.util.SparseMatrix;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Bug #78458: a table script that writes a few cells ({@code table[r][c] = ...}) must keep
 * writing to the view table after the table is reset again (an input change, the composer
 * preview refresh, a refresh after the script changed). The reset calls syncFormData, which
 * cleared the script's table array but kept its table, so the script was bound to the data
 * table instead. The writes then went down into the worksheet SummaryFilter, which read every
 * cell that was not written as "undefined".
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class,
                                  TableScriptRebindAfterResetTest.TestConfig.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("slow")
class TableScriptRebindAfterResetTest {
   @Configuration
   static class TestConfig {
      @Bean
      public MVManager mvManager() {
         return mock(MVManager.class);
      }

      @Bean
      @SuppressWarnings("unchecked")
      public AssetDataCache assetDataCache() {
         ObjectProvider<DistributedTableCacheStore> provider = mock(ObjectProvider.class);
         when(provider.getObject()).thenReturn(mock(DistributedTableCacheStore.class));
         return new AssetDataCache(mock(DataSourceRegistry.class), provider);
      }
   }

   @BeforeEach
   void setUp() throws Exception {
      Worksheet ws = new Worksheet();
      EmbeddedTableAssembly data = new EmbeddedTableAssembly(ws, "D");
      data.setEmbeddedData(new XEmbeddedTable(
         new String[] { XSchema.STRING, XSchema.INTEGER, XSchema.INTEGER },
         new Object[][] { { "state", "customer_id", "price" },
                          { "FL", 16, 75 },
                          { "FL", 18, 145 },
                          { "I", 9, 235 },
                          { "I", 9, 79 } }));
      ws.addAssembly(data);

      // a worksheet table with an aggregate, so its data has a SummaryFilter (as Query11 of
      // the reported viewsheet)
      MirrorTableAssembly query = new MirrorTableAssembly(ws, "Q", data);
      query.setProperty("no_cache", "true");
      ws.addAssembly(query);
      query.update();
      ColumnSelection qcols = query.getColumnSelection(false);
      AggregateInfo ainfo = new AggregateInfo();
      ainfo.addGroup(new GroupRef((ColumnRef) qcols.getAttribute("state")));
      ainfo.addGroup(new GroupRef((ColumnRef) qcols.getAttribute("customer_id")));
      ainfo.addAggregate(new AggregateRef((ColumnRef) qcols.getAttribute("price"),
                                          AggregateFormula.SUM));
      query.setAggregateInfo(ainfo);

      vs = new Viewsheet();
      Method setBase = Viewsheet.class.getDeclaredMethod("setBaseWorksheet", Worksheet.class);
      setBase.setAccessible(true);
      setBase.invoke(vs, ws);

      table = new TableVSAssembly(vs, "TableView1");
      table.setSourceInfo(new SourceInfo(XSourceInfo.ASSET, null, "Q"));
      ColumnSelection cols = new ColumnSelection();

      for(String name : new String[] { "state", "customer_id", "price" }) {
         cols.addAttribute(new ColumnRef(new AttributeRef(null, name)));
      }

      table.setColumnSelection(cols);
      table.getVSAssemblyInfo().setScript(
         "table[0][0] = 'H0'; table[1][0] = 'R1'; table[1][1] = 'R1C1';");
      table.getVSAssemblyInfo().setScriptEnabled(true);
      vs.addAssembly(table);

      AssetEntry entry = new AssetEntry(AssetRepository.GLOBAL_SCOPE,
         AssetEntry.Type.VIEWSHEET, "test/Bug78458", null);
      vs.setEntry(entry);
      box = new ViewsheetSandbox(vs, AbstractSheet.SHEET_RUNTIME_MODE, null, false, entry);
      box.reset(null, vs.getAssemblies(), new ChangedAssemblyList(), true, true, null);
   }

   @AfterEach
   void tearDown() {
      if(box != null) {
         box.dispose();
      }
   }

   /**
    * The open renders the table with the three written cells and the data everywhere else.
    */
   @Test
   void openShowsTheScriptCellsOverTheData() throws Exception {
      assertExpectedView("open");
   }

   /**
    * A second reset of the whole viewsheet (composer preview refresh, refresh after the
    * script changed) runs syncFormData on the bound table and then the script again.
    */
   @Test
   void secondResetKeepsTheViewTable() throws Exception {
      assertExpectedView("open");
      box.reset(new ChangedAssemblyList());
      assertExpectedView("second reset");
      box.reset(new ChangedAssemblyList());
      assertExpectedView("third reset");
   }

   /**
    * A reset of the table alone, as an input change on an assembly it depends on does.
    */
   @Test
   void tableResetAfterAnInputChangeKeepsTheViewTable() throws Exception {
      assertExpectedView("open");
      box.reset(null, new Assembly[] { table }, new ChangedAssemblyList(), false, false, null);
      assertExpectedView("table reset");
   }

   /**
    * syncFormData called on its own (a form table edit, a click or brush event) with the view
    * table still cached, followed by the script.
    */
   @Test
   void syncFormDataWithTheCachedViewTableKeepsTheViewTable() throws Exception {
      assertExpectedView("open");
      box.syncFormData("TableView1");
      box.executeScript(table);
      assertExpectedView("after syncFormData");
   }

   /**
    * Reads the view table and the data table, and checks the written cells are in the view
    * table only, and no cell reads the "undefined" sentinel.
    */
   private void assertExpectedView(String step) throws Exception {
      VSTableLens view = box.getVSTableLens("TableView1", false);
      assertNotNull(view, step + ": no view table");
      assertEquals(EXPECTED_VIEW, cells(view), step + ": view table");

      TableLens data = box.getTableData("TableView1");
      assertNotNull(data, step + ": no data table");
      String dataCells = cells(data);
      assertFalse(dataCells.contains("undefined"), step + ": data table\n" + dataCells);
      assertFalse(dataCells.contains("H0") || dataCells.contains("R1"),
                  step + ": the script wrote to the data table\n" + dataCells);
   }

   private static String cells(TableLens lens) {
      StringBuilder sb = new StringBuilder();
      lens.moreRows(TableLens.EOT);

      for(int r = 0; r < lens.getRowCount(); r++) {
         for(int c = 0; c < lens.getColCount(); c++) {
            Object val = lens.getObject(r, c);
            assertNotSame(SparseMatrix.NULL, val, "the sentinel at [" + r + "," + c + "]");
            sb.append(val instanceof Number n ? n.intValue() : val)
               .append(c < lens.getColCount() - 1 ? "|" : "\n");
         }
      }

      return sb.toString();
   }

   private static final String EXPECTED_VIEW =
      "H0|customer_id|price\nR1|R1C1|75\nFL|18|145\nI|9|314\n";
   private Viewsheet vs;
   private TableVSAssembly table;
   private ViewsheetSandbox box;
}
