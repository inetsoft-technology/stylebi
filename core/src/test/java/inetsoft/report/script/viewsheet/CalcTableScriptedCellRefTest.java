/*
 * This file is part of StyleBI.
 * Copyright (C) 2025  InetSoft Technology
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
package inetsoft.report.script.viewsheet;

import inetsoft.mv.MVManager;
import inetsoft.report.*;
import inetsoft.report.composition.ChangedAssemblyList;
import inetsoft.report.composition.VSTableLens;
import inetsoft.report.composition.execution.*;
import inetsoft.test.*;
import inetsoft.uql.asset.*;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.service.DataSourceRegistry;
import inetsoft.uql.util.XEmbeddedTable;
import inetsoft.uql.viewsheet.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Bug #81610: a {@code $name} formula of a freehand table must read the value a table script
 * wrote into the named Text cell with {@code table[r][c]}, not the cell's bound text (port of
 * the 13.8 fix of #79806).
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class,
                                  LibManagerTestConfiguration.class,
                                  PluginsTestConfiguration.class,
                                  CalcTableScriptedCellRefTest.TestConfig.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("slow")
class CalcTableScriptedCellRefTest {
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
      EmbeddedTableAssembly table = new EmbeddedTableAssembly(ws, "D");
      table.setEmbeddedData(new XEmbeddedTable(
         new String[] { XSchema.INTEGER, XSchema.DOUBLE },
         new Object[][] { { "Year", "Total" }, { 2020, 100.0 }, { 2021, 200.0 },
                          { 2022, 150.0 } }));
      ws.addAssembly(table);

      vs = new Viewsheet();
      Method setBase = Viewsheet.class.getDeclaredMethod("setBaseWorksheet", Worksheet.class);
      setBase.setAccessible(true);
      setBase.invoke(vs, ws);
   }

   @AfterEach
   void tearDown() {
      if(box != null) {
         box.dispose();
         box = null;
      }
   }

   @Test
   void formulaReadsValueWrittenByTableScript() throws Exception {
      VSTableLens lens = run("for(var r = 1; r < 4; r++) { table[r][1] = r; }");

      for(int r = 1; r <= 3; r++) {
         assertEquals(r, ((Number) lens.getObject(r, 1)).intValue(), "text cell, row " + r);
         assertNotNull(lens.getObject(r, 2), "formula cell, row " + r);
         assertEquals(r * 2, ((Number) lens.getObject(r, 2)).intValue(), "formula, row " + r);
      }
   }

   private VSTableLens run(String script) throws Exception {
      CalcTableVSAssembly calc = new CalcTableVSAssembly(vs, "Calc1");
      calc.setSourceInfo(new SourceInfo(SourceInfo.ASSET, null, "D"));
      TableLayout layout = calc.getTableLayout();
      layout.setColCount(3);
      TableCellBinding group = TableCellBinding.getGroupBinding("Year");
      group.setExpansion(GroupableCellBinding.EXPAND_V);
      layout.setCellBinding(1, 0, group);
      TableCellBinding text = new TableCellBinding(CellBinding.BIND_TEXT, "bound");
      text.setCellName("amt");
      layout.setCellBinding(1, 1, text);
      layout.setCellBinding(1, 2, new TableCellBinding(CellBinding.BIND_FORMULA, "$amt * 2"));
      calc.getVSAssemblyInfo().setScriptEnabled(true);
      calc.getVSAssemblyInfo().setScript(script);
      vs.addAssembly(calc);

      AssetEntry entry = new AssetEntry(AssetRepository.GLOBAL_SCOPE,
         AssetEntry.Type.VIEWSHEET, "test/Bug81610", null);
      vs.setEntry(entry);
      box = new ViewsheetSandbox(vs, AbstractSheet.SHEET_RUNTIME_MODE, null, false, entry);
      box.reset(null, vs.getAssemblies(), new ChangedAssemblyList(), true, true, null);

      VSTableLens lens = box.getVSTableLens("Calc1", false);
      assertNotNull(lens);
      lens.moreRows(Integer.MAX_VALUE);
      assertTrue(lens.getRowCount() >= 4, "rows: " + lens.getRowCount());
      return lens;
   }

   private Viewsheet vs;
   private ViewsheetSandbox box;
}
