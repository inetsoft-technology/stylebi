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
import inetsoft.report.*;
import inetsoft.report.composition.ChangedAssemblyList;
import inetsoft.report.internal.table.TableFormat;
import inetsoft.report.lens.AbstractTableLens;
import inetsoft.report.lens.DefaultTableDataDescriptor;
import inetsoft.report.lens.DefaultTableLens;
import inetsoft.test.*;
import inetsoft.uql.*;
import inetsoft.uql.asset.*;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.service.DataSourceRegistry;
import inetsoft.uql.util.QueryManager;
import inetsoft.uql.util.XEmbeddedTable;
import inetsoft.uql.viewsheet.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.lang.reflect.Method;
import java.text.Format;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Bug #78092: the reported freehand table (one vertical group and one Sum summary with a
 * percentage) runs on the crosstab-optimized path: {@link CalcTableVSAQuery} builds a temp
 * crosstab and fills the calc table from its {@code CrossTabFilter}. The summary cell must get
 * the percent format, not the source column's {@code $#,##0} format that rendered every share
 * as "$0". Everything below the worksheet data is real: the viewsheet sandbox, the temp
 * crosstab query and the calc table query. Only the column format of the base data is added,
 * as the logical model's attribute format would be.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class,
                                  LibManagerTestConfiguration.class,
                                  PluginsTestConfiguration.class,
                                  CalcTableVSAQueryCrosstabPercentFormatTest.TestConfig.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("slow")
class CalcTableVSAQueryCrosstabPercentFormatTest {
   @Configuration
   static class TestConfig {
      @Bean
      public MVManager mvManager() {
         return mock(MVManager.class);
      }

      // gives the Total column of the fetched data the Orders model's format
      @Bean
      @SuppressWarnings("unchecked")
      public AssetDataCache assetDataCache() {
         ObjectProvider<DistributedTableCacheStore> provider = mock(ObjectProvider.class);
         when(provider.getObject()).thenReturn(mock(DistributedTableCacheStore.class));
         return new AssetDataCache(mock(DataSourceRegistry.class), provider) {
            @Override
            public TableLens getData(String id, TableAssembly table, AssetQuerySandbox box,
                                     Set ignoredVars, int mode, boolean limit, long ts,
                                     QueryManager qmgr) throws Exception
            {
               TableLens data = super.getData(id, table, box, ignoredVars, mode, limit, ts,
                                              qmgr);
               return data == null ? null : withColumnFormat(data);
            }
         };
      }
   }

   @BeforeEach
   void setUp() throws Exception {
      boundTables.clear();
      Worksheet ws = new Worksheet();
      EmbeddedTableAssembly table = new EmbeddedTableAssembly(ws, "D");
      table.setEmbeddedData(new XEmbeddedTable(
         new String[] { XSchema.INTEGER, XSchema.DOUBLE },
         new Object[][] { { "Year", "Total" }, { 2020, 100.0 }, { 2021, 200.0 },
                          { 2022, 150.0 }, { 2020, 50.0 } }));
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

   @ParameterizedTest(name = "{0}")
   @ValueSource(strings = { "Sum<16>", "Sum<4>", "Count<16>" })
   void percentageSummaryGetsPercentFormat(String formula) throws Exception {
      TableLens lens = runQuery(formula);

      for(int r = 1; r <= 3; r++) {
         XMetaInfo minfo = getMeta(lens, r);
         assertNotNull(minfo, "row " + r + " meta");
         assertNotNull(minfo.getXFormatInfo(), "row " + r + " format");
         assertEquals(TableFormat.PERCENT_FORMAT, minfo.getXFormatInfo().getFormat(),
                      "row " + r);

         Format fmt = ((AbstractTableLens) lens).getDefaultFormat(r, 1);
         assertNotNull(fmt, "row " + r + " default format");
         String text = fmt.format(lens.getObject(r, 1));
         assertTrue(text.endsWith("%"), "row " + r + " renders as " + text);
      }
   }

   @Test
   void plainSummaryKeepsColumnFormat() throws Exception {
      TableLens lens = runQuery("Sum");

      for(int r = 1; r <= 3; r++) {
         XMetaInfo minfo = getMeta(lens, r);
         assertNotNull(minfo, "row " + r + " meta");
         assertEquals(COLUMN_FORMAT, minfo.getXFormatInfo().getFormatSpec(), "row " + r);
      }
   }

   private static XMetaInfo getMeta(TableLens lens, int r) {
      TableDataDescriptor desc = lens.getDescriptor();
      return desc.getXMetaInfo(desc.getCellDataPath(r, 1));
   }

   private TableLens runQuery(String formula) throws Exception {
      CalcTableVSAssembly calc = new CalcTableVSAssembly(vs, "Calc1");
      calc.setSourceInfo(new SourceInfo(SourceInfo.ASSET, null, "D"));
      TableLayout layout = calc.getTableLayout();
      TableCellBinding group = TableCellBinding.getGroupBinding("Year");
      group.setExpansion(GroupableCellBinding.EXPAND_V);
      layout.setCellBinding(1, 0, group);
      TableCellBinding summary = new TableCellBinding(CellBinding.BIND_COLUMN, "Total");
      summary.setBType(CellBinding.SUMMARY);
      summary.setFormula(formula);
      layout.setCellBinding(1, 1, summary);
      vs.addAssembly(calc);

      AssetEntry entry = new AssetEntry(AssetRepository.GLOBAL_SCOPE,
         AssetEntry.Type.VIEWSHEET, "test/Bug78092", null);
      vs.setEntry(entry);
      box = new ViewsheetSandbox(vs, AbstractSheet.SHEET_RUNTIME_MODE, null, false, entry) {
         @Override
         public TableAssembly getBoundTable(TableAssembly assembly, String vassembly,
                                            boolean detail)
            throws Exception
         {
            boundTables.add(vassembly + (detail ? " (detail)" : ""));
            return super.getBoundTable(assembly, vassembly, detail);
         }
      };
      box.reset(null, vs.getAssemblies(), new ChangedAssemblyList(), true, true, null);

      TableLens lens = new CalcTableVSAQuery(box, "Calc1", false).getTableLens();
      assertNotNull(lens);
      lens.moreRows(TableLens.EOT);

      // the data came from the temp crosstab, not from the detail table
      assertTrue(boundTables.stream().anyMatch(
                    n -> n.startsWith(CalcTableVSAQuery.TEMP_ASSEMBLY_PREFIX)),
                 "the crosstab-optimized path did not run: " + boundTables);
      assertTrue(lens.getRowCount() >= 4, "rows: " + lens.getRowCount());
      assertEquals(2020, ((Number) lens.getObject(1, 0)).intValue());
      return lens;
   }

   /**
    * A copy of the fetched data whose Total column carries the column format, which the
    * logical model attribute format gives the column in the product. The worksheet query
    * aggregates for the temp crosstab, so the column may be named Sum(Total); the meta is
    * found by the column name as well, as the copy in CalcTableVSAQuery looks it up.
    */
   private static TableLens withColumnFormat(TableLens data) {
      data.moreRows(TableLens.EOT);
      XMetaInfo minfo = new XMetaInfo();
      minfo.setXFormatInfo(new XFormatInfo(TableFormat.DECIMAL_FORMAT, COLUMN_FORMAT));

      return new DefaultTableLens(data) {
         @Override
         public TableDataDescriptor getDescriptor() {
            return new DefaultTableDataDescriptor(this) {
               @Override
               public XMetaInfo getXMetaInfo(TableDataPath path) {
                  if(path != null && path.getType() == TableDataPath.DETAIL &&
                     path.getPath().length == 1 &&
                     ("Total".equals(path.getPath()[0]) ||
                      "Sum(Total)".equals(path.getPath()[0])))
                  {
                     return minfo;
                  }

                  return super.getXMetaInfo(path);
               }
            };
         }
      };
   }

   // the Orders model's Total format, which renders a share as "$0"
   private static final String COLUMN_FORMAT = "$#,##0";
   private static final List<String> boundTables =
      Collections.synchronizedList(new ArrayList<>());
   private Viewsheet vs;
   private ViewsheetSandbox box;
}
