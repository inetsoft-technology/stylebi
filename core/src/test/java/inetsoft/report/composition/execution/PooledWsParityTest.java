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

import inetsoft.report.filter.CalcFieldFormula;
import inetsoft.report.filter.ConditionGroup;
import inetsoft.report.filter.Formula;
import inetsoft.report.filter.SumFormula;
import inetsoft.report.lens.DefaultTableLens;
import inetsoft.report.lens.FormulaTableLens;
import inetsoft.sree.SreeEnv;
import inetsoft.test.*;
import inetsoft.uql.ConditionItem;
import inetsoft.uql.ConditionList;
import inetsoft.uql.XCondition;
import inetsoft.uql.asset.AssetCondition;
import inetsoft.uql.asset.ExpressionValue;
import inetsoft.uql.asset.Worksheet;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.schema.XSchema;
import inetsoft.util.script.graal.pool.PoolConfig;
import inetsoft.util.script.graal.pool.WorksheetScriptEnv;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Gate 13 parity (bug #76960): a calc field (VS calc field and crosstab aggregate calc field
 * both run CalcFieldFormula), a JS condition value and a formula column give the same results
 * over pooled worksheet contexts as with the pool off.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class, LibManagerTestConfiguration.class, PluginsTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
public class PooledWsParityTest {
   @AfterEach
   public void tearDown() {
      SreeEnv.remove(PoolConfig.ENABLED);
   }

   @Test
   public void calcFieldMatches() throws Exception {
      assertEquals(calcField(false), calcField(true));
      assertNotNull(calcField(true));
   }

   @Test
   public void jsConditionMatches() throws Exception {
      assertEquals(condition(false), condition(true));
   }

   @Test
   public void formulaColumnMatches() throws Exception {
      assertEquals(formula(false), formula(true));
   }

   private Object calcField(boolean pool) throws Exception {
      AssetQuerySandbox box = sandbox(pool);
      CalcFieldFormula formula = new CalcFieldFormula(
         "SUM * 2", new String[] { "SUM" }, new Formula[] { new SumFormula() },
         new int[] { 0 }, box.getScriptEnv(), box.getScope());
      formula.addValue(new Object[] { null, 5.0 });
      formula.addValue(new Object[] { null, 7.0 });
      return formula.getResult();
   }

   private List<Boolean> condition(boolean pool) throws Exception {
      AssetQuerySandbox box = sandbox(pool);
      AssetCondition condition = new AssetCondition();
      condition.setOperation(XCondition.GREATER_THAN);
      condition.setType(XSchema.INTEGER);
      ExpressionValue value = new ExpressionValue();
      value.setExpression("1 + 1");
      value.setType(ExpressionValue.JAVASCRIPT);
      condition.addValue(value);
      ConditionList list = new ConditionList();
      list.append(new ConditionItem(new AttributeRef(null, "value"), condition, 0));
      DefaultTableLens table = new DefaultTableLens(new Object[][] {{"value"}, {1}, {2}, {3}});
      ConditionGroup group = new ConditionGroup(table, list, box);
      List<Boolean> result = new ArrayList<>();

      for(int r = 1; r < table.getRowCount(); r++) {
         result.add(group.evaluate(table, r));
      }

      return result;
   }

   private List<Object> formula(boolean pool) throws Exception {
      AssetQuerySandbox box = sandbox(pool);
      Object[][] data = new Object[301][];
      data[0] = new Object[] {"value"};

      for(int i = 1; i <= 300; i++) {
         data[i] = new Object[] {i % 30};
      }

      FormulaTableLens lens = new FormulaTableLens(new DefaultTableLens(data), new String[] {"f"},
         new String[] {"field['value'] * 2 + (parameter == null ? 0 : 1)"},
         box.getScriptEnv(), box.getScope());
      List<Object> values = new ArrayList<>();

      for(int r = 1; lens.moreRows(r); r++) {
         values.add(lens.getObject(r, 1));
      }

      return values;
   }

   private static AssetQuerySandbox sandbox(boolean pool) {
      SreeEnv.setProperty(PoolConfig.ENABLED, String.valueOf(pool));
      AssetQuerySandbox box = new AssetQuerySandbox(new Worksheet());
      assertEquals(pool, box.getScriptEnv() instanceof WorksheetScriptEnv);
      return box;
   }
}
