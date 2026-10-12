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
import inetsoft.test.*;
import inetsoft.uql.*;
import inetsoft.uql.asset.*;
import inetsoft.uql.erm.ExpressionRef;
import inetsoft.uql.schema.UserVariable;
import inetsoft.uql.service.DataSourceRegistry;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.schema.XValueNode;
import inetsoft.uql.util.XEmbeddedTable;
import inetsoft.uql.viewsheet.CalculateRef;
import inetsoft.uql.viewsheet.Viewsheet;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.lang.reflect.Method;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Bug #77867: a query prunes the unused calc fields from its own copy of the worksheet base
 * table, in a worksheet wrapper, instead of from the base table every query shares. The query
 * must return the rows it returned when it pruned the shared table, with a detail calc field
 * on the base table, a selection on it and a variable condition on the table below it.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class,
                                  LibManagerTestConfiguration.class,
                                  PluginsTestConfiguration.class,
                                  VSAQueryPrunedBaseTableRowsTest.TestConfig.class },
                      initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class VSAQueryPrunedBaseTableRowsTest {
   @Configuration
   static class TestConfig {
      @Bean
      @SuppressWarnings("unchecked")
      public AssetDataCache assetDataCache() {
         ObjectProvider<DistributedTableCacheStore> provider = mock(ObjectProvider.class);
         when(provider.getObject()).thenReturn(mock(DistributedTableCacheStore.class));
         return new AssetDataCache(mock(DataSourceRegistry.class), provider);
      }
   }

   @Test
   void prunedCopyReturnsTheRowsOfThePrunedSharedTable() throws Exception {
      List<List<String>> shared = rows(false);
      List<List<String>> copy = rows(true);

      assertEquals(List.of(List.of("2", "200"), List.of("4", "400")), shared);
      assertEquals(shared, copy);
   }

   /**
    * Bind a text query to T, prune its calc fields and run it.
    * @param copy true to prune as the query does, on its own copy of T, false to prune T in
    * place as the query did before 77867.
    */
   private static List<List<String>> rows(boolean copy) throws Exception {
      Worksheet ws = worksheet();
      Viewsheet vs = new Viewsheet();

      for(String name : new String[] { "calc1", "calc2", "calc3" }) {
         CalculateRef calc = new CalculateRef(true);
         ExpressionRef eref = new ExpressionRef(null, name);
         eref.setExpression("field['v'] * 10");
         calc.setDataRef(eref);
         calc.setDataType(XSchema.INTEGER);
         vs.addCalcField("T", calc);
      }

      ViewsheetSandbox box = mock(ViewsheetSandbox.class, withSettings().stubOnly());
      doReturn(vs).when(box).getViewsheet();
      OutputVSAQuery query = new OutputVSAQuery(box, "Text1");

      TableAssembly vtable = VSAQuery.getVSTableAssembly("T", false, vs, ws);
      TableAssembly table = ViewsheetSandbox.copyBoundTable(vtable, "V_MT_Text1");
      ColumnSelection columns = table.getColumnSelection(false);
      ColumnSelection output = new ColumnSelection();
      output.addAttribute(columns.getAttribute("id"));
      output.addAttribute(columns.getAttribute("calc1"));
      table.setColumnSelection(output, false);
      ws.addAssembly(table);

      if(copy) {
         Method prune =
            VSAQuery.class.getDeclaredMethod("removeUnusedCalcFields", TableAssembly.class);
         prune.setAccessible(true);
         prune.invoke(query, table);
         assertNotSame(ws, table.getWorksheet(), "the query did not prune its own copy");
      }
      else {
         Method prune =
            VSAQuery.class.getDeclaredMethod("removeUnusedCalcField", TableAssembly.class);
         prune.setAccessible(true);

         while((Boolean) prune.invoke(query, table)) {
            // prune the shared T in place
         }
      }

      ColumnSelection child =
         ((MirrorTableAssembly) table).getTableAssembly().getColumnSelection(false);
      assertNotNull(child.getAttribute("calc1"));
      assertNull(child.getAttribute("calc2"), "the unused calc2 was not pruned");

      VariableTable vars = new VariableTable();
      vars.put("minid", 2);
      AssetQuerySandbox wbox = new AssetQuerySandbox(ws, null, vars);

      try {
         AssetQuery aquery = AssetQuery.createAssetQuery(
            table, AssetQuerySandbox.RUNTIME_MODE, wbox, false, -1L, true, false);
         TableLens lens = aquery.getTableLens(vars);
         assertNotNull(lens, "the query failed, see the log");
         List<List<String>> rows = new ArrayList<>();

         for(int r = lens.getHeaderRowCount(); lens.moreRows(r); r++) {
            List<String> row = new ArrayList<>();

            for(int c = 0; c < lens.getColCount(); c++) {
               Object value = lens.getObject(r, c);
               row.add(String.valueOf(value instanceof Number ? ((Number) value).intValue() : value));
            }

            rows.add(row);
         }

         return rows;
      }
      finally {
         wbox.dispose();
      }
   }

   /**
    * U (id, grp, v) with the condition id >= $(minid), the variable minid (default 2), and T,
    * a mirror of U with the selection grp = 1.
    */
   private static Worksheet worksheet() {
      Worksheet ws = new Worksheet();
      String[] types = { XSchema.INTEGER, XSchema.INTEGER, XSchema.INTEGER };
      EmbeddedTableAssembly u = new EmbeddedTableAssembly(ws, "U");
      u.setEmbeddedData(new XEmbeddedTable(types, new Object[][] {
         { "id", "grp", "v" }, { 1, 1, 10 }, { 2, 1, 20 }, { 3, 2, 30 }, { 4, 1, 40 } }));
      ws.addAssembly(u);

      AssetCondition idcond = new AssetCondition();
      idcond.setOperation(XCondition.GREATER_THAN);
      idcond.setEqual(true);
      idcond.setType(XSchema.INTEGER);
      idcond.addValue(new UserVariable("minid"));
      ConditionList uconds = new ConditionList();
      uconds.append(new ConditionItem(u.getColumnSelection(false).getAttribute("id"), idcond, 0));
      u.setPreConditionList(uconds);

      DefaultVariableAssembly variable = new DefaultVariableAssembly(ws, "minid");
      AssetVariable var = new AssetVariable("minid");
      var.setValueNode(XValueNode.createValueNode(2, "minid"));
      variable.setVariable(var);
      ws.addAssembly(variable);

      MirrorTableAssembly t = new MirrorTableAssembly(ws, "T", u);
      ws.addAssembly(t);

      AssetCondition grpcond = new AssetCondition();
      grpcond.setOperation(XCondition.EQUAL_TO);
      grpcond.setType(XSchema.INTEGER);
      grpcond.addValue(1);
      ConditionList tconds = new ConditionList();
      tconds.append(new ConditionItem(t.getColumnSelection(false).getAttribute("grp"), grpcond, 0));
      t.setPreRuntimeConditionList(tconds);
      return ws;
   }
}
