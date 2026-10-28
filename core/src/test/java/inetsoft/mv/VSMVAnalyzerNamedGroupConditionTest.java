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
package inetsoft.mv;

import inetsoft.test.*;
import inetsoft.uql.*;
import inetsoft.uql.asset.*;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.schema.UserVariable;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #79482: a variable pre-condition on a named group column must make the table refuse MV
 * analysis, since the MV holds the group labels and not the values the condition compares.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = BaseTestConfiguration.class,
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class VSMVAnalyzerNamedGroupConditionTest {
   @Test
   void variableNotEqualOnNamedGroupColumnIsRefused() throws Exception {
      assertTrue(refused(table(true, "ORDER_ID", "ORDER_ID", "$(aaa)", true)));
   }

   @Test
   void variableEqualOnNamedGroupColumnIsRefused() throws Exception {
      assertTrue(refused(table(true, "ORDER_ID", "ORDER_ID", "$(aaa)", false)));
   }

   @Test
   void userVariableObjectOnNamedGroupColumnIsRefused() throws Exception {
      // the worksheet XML of the reporter stores the value as a UserVariable (isvariable)
      UserVariable var = new UserVariable("aaa");
      assertTrue(refused(table(true, "ORDER_ID", "ORDER_ID", var, true)));
   }

   @Test
   void constantConditionIsNotRefused() throws Exception {
      assertFalse(refused(table(true, "ORDER_ID", "ORDER_ID", "12001", true)));
   }

   @Test
   void plainGroupIsNotRefused() throws Exception {
      assertFalse(refused(table(false, "ORDER_ID", "ORDER_ID", "$(aaa)", true)));
   }

   @Test
   void variableOnDifferentColumnIsNotRefused() throws Exception {
      assertFalse(refused(table(true, "ORDER_ID", "CUSTOMER_ID", "$(aaa)", true)));
   }

   private PhysicalBoundTableAssembly table(boolean named, String groupCol, String condCol,
                                            Object value, boolean negated)
   {
      Worksheet ws = new Worksheet();
      PhysicalBoundTableAssembly table = new PhysicalBoundTableAssembly(ws, "T1");
      table.setSourceInfo(new SourceInfo(SourceInfo.PHYSICAL_TABLE, "ds1", "ORDERS"));

      GroupRef group = new GroupRef(new ColumnRef(new AttributeRef(null, groupCol)));

      if(named) {
         group.setNamedGroupAssembly("ttt");
      }

      AggregateInfo ainfo = new AggregateInfo();
      ainfo.addGroup(group);
      table.setAggregateInfo(ainfo);

      AssetCondition cond = new AssetCondition();
      cond.setOperation(XCondition.EQUAL_TO);
      cond.setNegated(negated);
      cond.addValue(value);
      ConditionList list = new ConditionList();
      list.append(new ConditionItem(new ColumnRef(new AttributeRef(null, condCol)), cond, 0));
      table.setPreConditionList(list);
      return table;
   }

   private static boolean refused(TableAssembly table) throws Exception {
      VSMVAnalyzer analyzer = new VSMVAnalyzer("1^128^__NULL__^vs", null, null, null, false, null);
      Method method = VSMVAnalyzer.class.getDeclaredMethod(
         "containsDynamicConditionOnNamedGroup", TableAssembly.class);
      method.setAccessible(true);
      return (Boolean) method.invoke(analyzer, table);
   }
}
