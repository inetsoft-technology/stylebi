/*
 * This file is part of StyleBI.
 * Copyright (C) 2024  InetSoft Technology
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
import inetsoft.test.*;
import inetsoft.uql.ColumnSelection;
import inetsoft.uql.asset.*;
import inetsoft.uql.erm.ExpressionRef;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.service.DataSourceRegistry;
import inetsoft.uql.util.XEmbeddedTable;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.sql.Timestamp;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Expression columns of a worksheet table run end to end by a real {@link AssetQuerySandbox}
 * (Test Case #80957, vscalc G5): {@code AssetQuery.addExpression} collects them, including a
 * hidden calculated field another one refers to, and passes them to
 * {@code PostProcessor.formula}. The table is embedded, so it never merges into a database
 * query and every expression, SQL ones included, runs as script. The per-expression
 * semantics are in {@code PostProcessorTest}; expected values are computed by hand.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class, LibManagerTestConfiguration.class, PluginsTestConfiguration.class, AssetQueryExpressionColumnTest.TestConfig.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class AssetQueryExpressionColumnTest {
   @Configuration
   static class TestConfig {
      @Bean
      @SuppressWarnings("unchecked")
      public AssetDataCache assetDataCache() {
         ObjectProvider<DistributedTableCacheStore> provider = mock(ObjectProvider.class);
         when(provider.getObject()).thenReturn(mock(DistributedTableCacheStore.class));
         return new AssetDataCache(mock(DataSourceRegistry.class), provider);
      }

      @Bean
      public MVManager mvManager() {
         return mock(MVManager.class);
      }
   }

   @AfterEach
   void tearDown() {
      for(AssetQuerySandbox box : boxes) {
         box.dispose();
      }

      boxes.clear();
   }

   // a parameter in the expression (vscalc: field['city'] + '   ' + parameter.CheckBox1[0],
   // field['customer_id'] + parameter.RadioButton1)
   @Test
   void expressionWithParameters_usesTheVariableValues() throws Exception {
      Worksheet ws = ws();
      addExpression(ws, "cityAndRegion", "field['city'] + ' ' + parameter.region[0]",
                    XSchema.STRING, false, true);
      addExpression(ws, "idPlusOffset", "field['customer_id'] + parameter.offset",
                    XSchema.INTEGER, false, true);
      AssetQuerySandbox box = box(ws);
      box.getVariableTable().put("region", new Object[] { "East", "West" });
      box.getVariableTable().put("offset", 100);
      TableLens t = box.getTableLens("T", RUNTIME);

      assertEquals(Arrays.asList("Albany East", "Fresno East"), column(t, "cityAndRegion"));
      assertEquals(Arrays.asList(103, 110), column(t, "idPlusOffset"));
   }

   // a calculated field that refers to a hidden one (vscalc: field['calc_quarter'] +
   // field['CUSTOMER_ID']); addExpression adds the hidden one so it can be read
   @Test
   void expressionReferringToAHiddenExpression_computesBoth() throws Exception {
      Worksheet ws = ws();
      addExpression(ws, "calc_quarter", "CALC.quarter(field['orderdate'])",
                    XSchema.INTEGER, false, false);
      addExpression(ws, "quarterPlusId", "field['calc_quarter'] + field['customer_id']",
                    XSchema.INTEGER, false, true);
      TableLens t = box(ws).getTableLens("T", RUNTIME);

      // 2001-08-15 is quarter 3, 2001-02-01 quarter 1
      assertEquals(Arrays.asList(6, 11), column(t, "quarterPlusId"));
   }

   // a SQL expression on a table that cannot merge into the database runs as script (unless
   // it carries a /*script ... script*/ part): an operator works (vscalc:
   // field['customer_id']+10), a SQL-only function such as concat does not exist in script
   // (vscalc: concat(field['customers:state'],'a')). In runtime mode AssetQuery then falls
   // back to the design-time table (one sample row, "XXXXX" for text) and adds an
   // "expression failed" user warning, so the real rows are not shown. Current behavior
   @Test
   void sqlExpressionOnAnEmbeddedTable_runsAsScript() throws Exception {
      Worksheet ws = ws();
      addExpression(ws, "idPlusTen", "field['customer_id'] + 10", XSchema.INTEGER, true, true);
      assertEquals(Arrays.asList(13, 20), column(box(ws).getTableLens("T", RUNTIME),
                                                 "idPlusTen"));

      Worksheet concat = ws();
      addExpression(concat, "cityA", "concat(field['city'], 'a')", XSchema.STRING, true, true);
      TableLens t = box(concat).getTableLens("T", RUNTIME);

      // one sample row of X placeholders instead of the two real rows
      for(String name : new String[] { "cityA", "city" }) {
         List<Object> values = column(t, name);
         assertEquals(1, values.size(), name);
         assertTrue(String.valueOf(values.get(0)).matches("X+"), name + ": " + values);
      }
   }

   /** An embedded table T: customer_id, city, orderdate. */
   private static Worksheet ws() {
      Worksheet ws = new Worksheet();
      EmbeddedTableAssembly table = new EmbeddedTableAssembly(ws, "T");
      Object[][] data = {
         { "customer_id", "city", "orderdate" },
         { 3, "Albany", Timestamp.valueOf("2001-08-15 00:00:00") },
         { 10, "Fresno", Timestamp.valueOf("2001-02-01 00:00:00") }
      };
      table.setEmbeddedData(new XEmbeddedTable(
         new String[] { XSchema.INTEGER, XSchema.STRING, XSchema.TIME_INSTANT }, data));
      ws.addAssembly(table);
      return ws;
   }

   private static void addExpression(Worksheet ws, String name, String expression,
                                     String dataType, boolean sql, boolean visible)
   {
      TableAssembly table = (TableAssembly) ws.getAssembly("T");
      ExpressionRef exp = new ExpressionRef(null, name);
      exp.setExpression(expression);
      ColumnRef column = new ColumnRef(exp);
      column.setDataType(dataType);
      column.setSQL(sql);
      column.setVisible(visible);
      ColumnSelection columns = table.getColumnSelection(false);
      columns.addAttribute(column);
      table.setColumnSelection(columns, false);
   }

   private AssetQuerySandbox box(Worksheet ws) {
      // the data keys are content based: without this a run could read another's rows
      AssetDataCache.getCache().clearCache();
      AssetQuerySandbox box = new AssetQuerySandbox(ws);
      boxes.add(box);
      return box;
   }

   private static List<Object> column(TableLens t, String name) {
      t.moreRows(TableLens.EOT);
      int col = -1;

      for(int c = 0; c < t.getColCount(); c++) {
         if(name.equals(String.valueOf(t.getObject(0, c)))) {
            col = c;
         }
      }

      assertTrue(col >= 0, "no column " + name);
      List<Object> values = new ArrayList<>();

      for(int r = 1; t.moreRows(r); r++) {
         values.add(t.getObject(r, col));
      }

      return values;
   }


   private static final int RUNTIME = AssetQuerySandbox.RUNTIME_MODE;
   private final List<AssetQuerySandbox> boxes = new ArrayList<>();
}
