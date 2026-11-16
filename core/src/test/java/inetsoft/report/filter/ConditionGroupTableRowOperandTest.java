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
package inetsoft.report.filter;

import inetsoft.report.lens.DefaultTableLens;
import inetsoft.report.script.TableRow;
import inetsoft.test.*;
import inetsoft.uql.ConditionItem;
import inetsoft.uql.ConditionList;
import inetsoft.uql.XCondition;
import inetsoft.uql.asset.AssetCondition;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.schema.XSchema;
import inetsoft.util.script.ScriptException;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #81453: a condition expression that evaluates to a TableRow must be unwrapped (single
 * cell) or rejected loudly, never stringified into "TableRow{...}".
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class, LibManagerTestConfiguration.class, PluginsTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class ConditionGroupTableRowOperandTest {
   private ConditionGroup group(DefaultTableLens table) {
      AssetCondition condition = new AssetCondition();
      condition.setOperation(XCondition.EQUAL_TO);
      condition.setType(XSchema.DOUBLE);
      ConditionList list = new ConditionList();
      list.append(new ConditionItem(new AttributeRef(null, "value"), condition, 0));
      return new ConditionGroup(table, list, null);
   }

   @Test
   void singleCellRowIsUnwrapped() {
      DefaultTableLens table = new DefaultTableLens(new Object[][] {{"v"}, {8725057.775}});
      Object val = group(table).getScriptValue(new TableRow(table, 1), XSchema.DOUBLE);

      assertEquals(8725057.775, ((Number) val).doubleValue(), 1e-6);
   }

   @Test
   void multiCellRowFailsLoudly() {
      DefaultTableLens table = new DefaultTableLens(new Object[][] {{"a", "b"}, {1, 2}});

      ScriptException ex = assertThrows(ScriptException.class,
         () -> group(table).getScriptValue(new TableRow(table, 1), XSchema.DOUBLE));
      assertTrue(ex.getMessage().contains("2 columns"), ex.getMessage());
   }
}
