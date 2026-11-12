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
import inetsoft.uql.ConditionItem;
import inetsoft.uql.ConditionList;
import inetsoft.uql.XCondition;
import inetsoft.uql.asset.*;
import inetsoft.util.Tool;
import inetsoft.util.UserMessage;
import inetsoft.uql.erm.ExpressionRef;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.service.DataSourceRegistry;
import inetsoft.uql.util.XEmbeddedTable;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
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
      Tool.clearUserMessage();
      TableLens t = box(concat).getTableLens("T", RUNTIME);

      // one sample row of X placeholders instead of the two real rows
      for(String name : new String[] { "cityA", "city" }) {
         List<Object> values = column(t, name);
         assertEquals(1, values.size(), name);
         assertTrue(String.valueOf(values.get(0)).matches("X+"), name + ": " + values);
      }

      // W11: the sample row is 999 / XXXX and the user is told which expression failed
      assertEquals(Arrays.asList(999), column(t, "customer_id"));
      assertEquals(Arrays.asList("XXXX"), column(t, "city"));
      UserMessage message = Tool.getUserMessage();
      assertNotNull(message);
      assertTrue(message.getMessage().contains("expression failed"), message.getMessage());
      assertTrue(message.getMessage().contains("concat is not defined"), message.getMessage());
   }

   @Test
   @DisplayName("E1: a plain field expression of the same type is a copy of the column")
   void e1_plainFieldExpression() throws Exception {
      Worksheet ws = ws();
      addExpression(ws, "cityCopy", "field['city']", XSchema.STRING, false, true);
      assertEquals(Arrays.asList("Albany", "Fresno"),
                   column(box(ws).getTableLens("T", RUNTIME), "cityCopy"));
   }

   @Test
   @DisplayName("E2: a visible expression over a hidden one over a hidden one")
   void e2_chainOfThreeExpressions() throws Exception {
      Worksheet ws = ws();
      // the visible field is listed first, so the hidden ones it needs are added by recursion
      addExpression(ws, "v", "field['h2'] + field['customer_id']", XSchema.INTEGER, false, true);
      addExpression(ws, "h2", "field['h1'] * 2", XSchema.INTEGER, false, false);
      addExpression(ws, "h1", "CALC.quarter(field['orderdate'])", XSchema.INTEGER, false, false);
      // quarter 3 and 1: 3 * 2 + 3 and 1 * 2 + 10
      assertEquals(Arrays.asList(9, 12), column(box(ws).getTableLens("T", RUNTIME), "v"));
   }

   @Disabled("Bug #81206, mismatch E3")
   @Test
   @DisplayName("E3: two expressions that refer to each other fail with a handled error")
   void e3_circularReference_isAHandledFailure() throws Exception {
      Worksheet ws = ws();
      addExpression(ws, "a", "field['b'] + 1", XSchema.INTEGER, false, true);
      addExpression(ws, "b", "field['a'] + 1", XSchema.INTEGER, false, true);
      // an exception or a sample table, never a StackOverflowError
      try {
         box(ws).getTableLens("T", RUNTIME);
      }
      catch(Exception expected) {
         // handled failure
      }
   }

   @Test
   @DisplayName("PIN E3 (current behavior, pending dev #80953): an expression that refers to " +
      "itself reads its own column as empty")
   void e3_pin_selfReference() throws Exception {
      Worksheet ws = ws();
      addExpression(ws, "a1", "field['a1'] + 1", XSchema.INTEGER, false, true);
      Tool.clearUserMessage();
      assertEquals(Arrays.asList(1, 1), column(box(ws).getTableLens("T", RUNTIME), "a1"));
      assertNull(Tool.getUserMessage());
   }

   @Test
   @DisplayName("PIN E4 (current behavior, pending dev #80953): a missing column gives null " +
      "values without a message")
   void e4_pin_missingColumn() throws Exception {
      Worksheet ws = ws();
      addExpression(ws, "bad", "field['nope'] + 1", XSchema.INTEGER, false, true);
      Tool.clearUserMessage();
      assertEquals(Arrays.asList(null, null),
                   column(box(ws).getTableLens("T", RUNTIME), "bad"));
      assertNull(Tool.getUserMessage());
   }

   @Test
   @DisplayName("PIN E5 (current behavior, pending dev #80953): an expression named like an " +
      "existing column is dropped")
   void e5_pin_expressionNamedLikeAColumn() throws Exception {
      Worksheet ws = ws();
      addExpression(ws, "city", "'X'", XSchema.STRING, false, true);
      TableLens t = box(ws).getTableLens("T", RUNTIME);
      assertEquals(Arrays.asList("Albany", "Fresno"), column(t, "city"));
      assertEquals(3, t.getColCount());
   }

   @Test
   @DisplayName("E6: names with a space, a quote, non-ASCII letters and double quotes resolve")
   void e6_specialNames() throws Exception {
      Worksheet ws = ws();
      addExpression(ws, "Total Price", "field['customer_id'] * 2", XSchema.INTEGER, false, true);
      addExpression(ws, "O'Brien", "field['Total Price'] + 1", XSchema.INTEGER, false, true);
      addExpression(ws, "ümlautß", "field[\"O'Brien\"] + 1", XSchema.INTEGER, false,
                    true);
      addExpression(ws, "say \"hi\"", "field['customer_id'] * 2", XSchema.INTEGER, false, true);
      addExpression(ws, "next", "field['say \"hi\"'] + 1", XSchema.INTEGER, false, true);
      TableLens t = box(ws).getTableLens("T", RUNTIME);
      assertEquals(Arrays.asList(6, 20), column(t, "Total Price"));
      assertEquals(Arrays.asList(7, 21), column(t, "O'Brien"));
      assertEquals(Arrays.asList(8, 22), column(t, "ümlautß"));
      assertEquals(Arrays.asList(6, 20), column(t, "say \"hi\""));
      assertEquals(Arrays.asList(7, 21), column(t, "next"));
   }

   @Test
   @DisplayName("E7: an empty table has the expression column and no rows")
   void e7_emptyTable() throws Exception {
      Worksheet ws = ws(new Object[][] { { "customer_id", "city", "orderdate" } });
      addExpression(ws, "twice", "field['customer_id'] * 2", XSchema.INTEGER, false, true);
      TableLens t = box(ws).getTableLens("T", RUNTIME);
      assertEquals(Arrays.asList(), column(t, "twice"));
      assertEquals(4, t.getColCount());
   }

   @Test
   @DisplayName("E8: five thousand rows read in two batches keep their order")
   void e8_fiveThousandRows_twoBatches() throws Exception {
      Object[][] data = new Object[5001][];
      data[0] = new Object[] { "customer_id", "city", "orderdate" };

      for(int i = 1; i <= 5000; i++) {
         data[i] = new Object[] { i, "c" + i, Timestamp.valueOf("2001-08-15 00:00:00") };
      }

      Worksheet ws = ws(data);
      addExpression(ws, "twice", "field['customer_id'] * 2", XSchema.INTEGER, false, true);
      TableLens t = box(ws).getTableLens("T", RUNTIME);
      int col = t.getColCount() - 1;
      assertTrue(t.moreRows(100));
      assertEquals(2, t.getObject(1, col));
      assertEquals(200, t.getObject(100, col));
      assertFalse(t.moreRows(5001));

      for(int r = 1; r <= 5000; r++) {
         assertEquals(2 * r, t.getObject(r, col), "row " + r);
      }
   }

   @Test
   @DisplayName("E9: an unset or null parameter reads as undefined, an empty array index fails " +
      "the table")
   void e9_missingNullAndEmptyArrayParameter() throws Exception {
      Worksheet unset = ws();
      addExpression(unset, "p1", "field['customer_id'] + parameter.offset", XSchema.INTEGER,
                    false, true);
      addExpression(unset, "p2", "field['city'] + ' ' + parameter.offset", XSchema.STRING, false,
                    true);
      Tool.clearUserMessage();
      TableLens t = box(unset).getTableLens("T", RUNTIME);
      assertEquals(Arrays.asList(null, null), column(t, "p1"));
      assertEquals(Arrays.asList("Albany undefined", "Fresno undefined"), column(t, "p2"));
      assertNull(Tool.getUserMessage());

      // PIN (class B): an index into an empty array fails the table, the sample row is shown
      Worksheet empty = ws();
      addExpression(empty, "p3", "field['city'] + ' ' + parameter.region[0]", XSchema.STRING,
                    false, true);
      AssetQuerySandbox box = box(empty);
      box.getVariableTable().put("region", new Object[0]);
      Tool.clearUserMessage();
      TableLens failed = box.getTableLens("T", RUNTIME);
      assertEquals(Arrays.asList(999), column(failed, "customer_id"));
      UserMessage message = Tool.getUserMessage();
      assertNotNull(message);
      assertTrue(message.getMessage().contains("expression failed"), message.getMessage());
   }

   @Test
   @DisplayName("E10: the script part of a SQL expression runs on a table that cannot merge")
   void e10_scriptPartOfASqlExpression() throws Exception {
      Worksheet ws = ws();
      addExpression(ws, "idPlusTen",
                    "customer_id + 10 /*script field['customer_id'] + 10 script*/",
                    XSchema.INTEGER, true, true);
      assertEquals(Arrays.asList(13, 20),
                   column(box(ws).getTableLens("T", RUNTIME), "idPlusTen"));
   }

   @Test
   @DisplayName("E11: a pre and a post condition on an expression column")
   void e11_conditionOnAnExpressionColumn() throws Exception {
      for(boolean pre : new boolean[] { true, false }) {
         Worksheet ws = ws();
         addExpression(ws, "idPlusTen", "field['customer_id'] + 10", XSchema.INTEGER, false, true);
         EmbeddedTableAssembly table = (EmbeddedTableAssembly) ws.getAssembly("T");
         AssetCondition condition = new AssetCondition();
         condition.setOperation(XCondition.GREATER_THAN);
         condition.setType(XSchema.INTEGER);
         condition.addValue(15);
         ConditionList list = new ConditionList();
         list.append(new ConditionItem(table.getColumnSelection(false).getAttribute("idPlusTen"),
                                       condition, 0));

         if(pre) {
            table.setPreConditionList(list);
         }
         else {
            table.setPostConditionList(list);
         }

         // only customer 10: 10 + 10 = 20 passes
         assertEquals(Arrays.asList(20), column(box(ws).getTableLens("T", RUNTIME), "idPlusTen"),
                      pre ? "pre" : "post");
      }
   }

   @Test
   @DisplayName("T7: the declared type of an expression column is the type of the result column")
   void t7_declaredTypeEndToEnd() throws Exception {
      Worksheet ws = ws();
      addExpression(ws, "d", "field['customer_id'] * 1", XSchema.DOUBLE, false, true);
      addExpression(ws, "i", "CALC.quarter(field['orderdate'])", XSchema.INTEGER, false, true);
      addExpression(ws, "s", "field['customer_id'] + ''", XSchema.STRING, false, true);
      TableLens t = box(ws).getTableLens("T", RUNTIME);
      assertEquals(Arrays.asList(3.0, 10.0), column(t, "d"));
      assertEquals(Arrays.asList(3, 1), column(t, "i"));
      assertEquals(Arrays.asList("3", "10"), column(t, "s"));

      for(int c = 0; c < t.getColCount(); c++) {
         String name = String.valueOf(t.getObject(0, c));

         if("d".equals(name)) {
            assertEquals(Double.class, t.getColType(c));
         }
         else if("i".equals(name)) {
            assertEquals(Integer.class, t.getColType(c));
         }
         else if("s".equals(name)) {
            assertEquals(String.class, t.getColType(c));
         }
      }
   }

   /** An embedded table T: customer_id, city, orderdate. */
   private static Worksheet ws() {
      return ws(new Object[][] {
         { "customer_id", "city", "orderdate" },
         { 3, "Albany", Timestamp.valueOf("2001-08-15 00:00:00") },
         { 10, "Fresno", Timestamp.valueOf("2001-02-01 00:00:00") }
      });
   }

   private static Worksheet ws(Object[][] data) {
      Worksheet ws = new Worksheet();
      EmbeddedTableAssembly table = new EmbeddedTableAssembly(ws, "T");
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
