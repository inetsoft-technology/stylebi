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
 * Bug #82412: a detail calc field that only the right-hand side of a condition reads
 * (id < calc1) must not be pruned, or the condition cannot be evaluated and the table
 * comes out empty.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class,
                                  LibManagerTestConfiguration.class,
                                  PluginsTestConfiguration.class,
                                  VSAQueryConditionRhsCalcFieldTest.TestConfig.class },
                      initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class VSAQueryConditionRhsCalcFieldTest {
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
   void calcFieldOnTheRightHandSideIsKeptAndFilters() throws Exception {
      assertEquals(List.of("3", "4"), rows(true));
   }

   @Test
   void literalRightHandValueStillFilters() throws Exception {
      assertEquals(List.of("1", "2"), rows(false));
   }

   /**
    * @param calcRhs true for id < calc1 (calc1 = v - 25), false for id < 3.
    */
   private static List<String> rows(boolean calcRhs) throws Exception {
      Worksheet ws = new Worksheet();
      String[] types = { XSchema.INTEGER, XSchema.INTEGER };
      EmbeddedTableAssembly u = new EmbeddedTableAssembly(ws, "T");
      u.setEmbeddedData(new XEmbeddedTable(types, new Object[][] {
         { "id", "v" }, { 1, 10 }, { 2, 20 }, { 3, 30 }, { 4, 40 } }));
      ws.addAssembly(u);

      Viewsheet vs = new Viewsheet();
      CalculateRef calc = new CalculateRef(true);
      ExpressionRef eref = new ExpressionRef(null, "calc1");
      eref.setExpression("field['v'] - 25");
      calc.setDataRef(eref);
      calc.setDataType(XSchema.INTEGER);
      vs.addCalcField("T", calc);

      ViewsheetSandbox box = mock(ViewsheetSandbox.class, withSettings().stubOnly());
      doReturn(vs).when(box).getViewsheet();
      OutputVSAQuery query = new OutputVSAQuery(box, "Text1");

      TableAssembly vtable = VSAQuery.getVSTableAssembly("T", false, vs, ws);
      TableAssembly table = ViewsheetSandbox.copyBoundTable(vtable, "V_MT_Text1");
      ColumnSelection columns = table.getColumnSelection(false);
      ColumnSelection output = new ColumnSelection();
      output.addAttribute(columns.getAttribute("id"));
      table.setColumnSelection(output, false);
      ws.addAssembly(table);

      AssetCondition cond = new AssetCondition();
      cond.setOperation(XCondition.LESS_THAN);
      cond.setType(XSchema.INTEGER);

      if(calcRhs) {
         cond.addValue(columns.getAttribute("calc1"));
      }
      else {
         cond.addValue(3);
      }

      ConditionList conds = new ConditionList();
      conds.append(new ConditionItem(columns.getAttribute("id"), cond, 0));
      table.setPreRuntimeConditionList(conds);

      Method prune =
         VSAQuery.class.getDeclaredMethod("removeUnusedCalcFields", TableAssembly.class);
      prune.setAccessible(true);
      prune.invoke(query, table);

      AssetQuerySandbox wbox = new AssetQuerySandbox(ws, null, new VariableTable());

      try {
         AssetQuery aquery = AssetQuery.createAssetQuery(
            table, AssetQuerySandbox.RUNTIME_MODE, wbox, false, -1L, true, false);
         TableLens lens = aquery.getTableLens(new VariableTable());
         assertNotNull(lens, "the query failed, see the log");
         List<String> rows = new ArrayList<>();

         for(int r = lens.getHeaderRowCount(); lens.moreRows(r); r++) {
            Object value = lens.getObject(r, 0);
            rows.add(String.valueOf(value instanceof Number ? ((Number) value).intValue() : value));
         }

         return rows;
      }
      finally {
         wbox.dispose();
      }
   }
}
