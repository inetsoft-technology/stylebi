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

import inetsoft.mv.trans.TransformationDescriptor;
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
 * Bug #79482: a pre-condition list that moves to the runtime list (a variable, session variable
 * or expression item) with any item on a named group column must make the table refuse MV
 * analysis, since the MV holds the group labels and not the values the condition compares.
 * This tests the analyzer decision only; reading rows from an MV is not covered here.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = BaseTestConfiguration.class,
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class VSMVAnalyzerNamedGroupConditionTest {
   @Test
   void variableNotEqualOnNamedGroupColumnIsRefused() throws Exception {
      assertTrue(refused(table(named(), item("ORDER_ID", "$(aaa)", true))));
   }

   @Test
   void variableEqualOnNamedGroupColumnIsRefused() throws Exception {
      assertTrue(refused(table(named(), item("ORDER_ID", "$(aaa)", false))));
   }

   @Test
   void userVariableObjectOnNamedGroupColumnIsRefused() throws Exception {
      // the worksheet XML of the reporter stores the value as a UserVariable (isvariable)
      assertTrue(refused(table(named(), item("ORDER_ID", new UserVariable("aaa"), true))));
   }

   @Test
   void sessionVariableOnNamedGroupColumnIsRefused() throws Exception {
      assertTrue(refused(table(named(), item("ORDER_ID", "$(_USER_)", true))));
   }

   @Test
   void sessionVariableOnOtherColumnWithConstantOnNamedGroupIsRefused() throws Exception {
      assertTrue(refused(table(named(), item("EMPLOYEE_ID", "$(_USER_)", true),
                               item("ORDER_ID", "12002", true))));
   }

   @Test
   void mixedVariableOnOtherColumnAndConstantOnNamedGroupIsRefused() throws Exception {
      // #79482 reopened: the whole list moves to runtime, so the constant is compared with
      // the group labels too
      assertTrue(refused(table(named(), item("EMPLOYEE_ID", "$(aaa)", true),
                               item("ORDER_ID", "12002", true))));
   }

   @Test
   void inlineNamedGroupMixedIsRefused() throws Exception {
      GroupRef group = plainGroup("ORDER_ID");
      group.setNamedGroupInfo(new NamedGroupInfo());
      assertTrue(refused(table(new GroupRef[] { plainGroup("EMPLOYEE_ID"), group },
                               item("EMPLOYEE_ID", "$(aaa)", true),
                               item("ORDER_ID", "12002", true))));
   }

   @Test
   void constantOnlyListIsNotRefused() throws Exception {
      assertFalse(refused(table(named(), item("ORDER_ID", "12001", true))));
      assertFalse(refused(table(named(), item("EMPLOYEE_ID", "1", true),
                                item("ORDER_ID", "12002", true))));
   }

   @Test
   void variableOnOtherColumnIsNotRefused() throws Exception {
      assertFalse(refused(table(named(), item("CUSTOMER_ID", "$(aaa)", true))));
   }

   @Test
   void plainGroupIsNotRefused() throws Exception {
      assertFalse(refused(table(new GroupRef[] { plainGroup("EMPLOYEE_ID"),
                                                 plainGroup("ORDER_ID") },
                                item("ORDER_ID", "$(aaa)", true))));
   }

   @Test
   void plainGroupMixedIsNotRefused() throws Exception {
      assertFalse(refused(table(new GroupRef[] { plainGroup("EMPLOYEE_ID"),
                                                 plainGroup("ORDER_ID") },
                                item("EMPLOYEE_ID", "$(aaa)", true),
                                item("ORDER_ID", "12002", true))));
   }

   private static GroupRef plainGroup(String col) {
      return new GroupRef(new ColumnRef(new AttributeRef(null, col)));
   }

   private static GroupRef[] named() {
      GroupRef group = plainGroup("ORDER_ID");
      group.setNamedGroupAssembly("ttt");
      return new GroupRef[] { plainGroup("EMPLOYEE_ID"), group };
   }

   private static ConditionItem item(String col, Object value, boolean negated) {
      AssetCondition cond = new AssetCondition();
      cond.setOperation(XCondition.EQUAL_TO);
      cond.setNegated(negated);
      cond.addValue(value);
      return new ConditionItem(new ColumnRef(new AttributeRef(null, col)), cond, 0);
   }

   private PhysicalBoundTableAssembly table(GroupRef[] groups, ConditionItem... items) {
      Worksheet ws = new Worksheet();
      PhysicalBoundTableAssembly table = new PhysicalBoundTableAssembly(ws, "T1");
      table.setSourceInfo(new SourceInfo(SourceInfo.PHYSICAL_TABLE, "ds1", "ORDERS"));

      AggregateInfo ainfo = new AggregateInfo();

      for(GroupRef group : groups) {
         ainfo.addGroup(group);
      }

      table.setAggregateInfo(ainfo);

      ConditionList list = new ConditionList();

      for(ConditionItem item : items) {
         if(list.getSize() > 0) {
            list.append(new JunctionOperator(JunctionOperator.AND, 0));
         }

         list.append(item);
      }

      table.setPreConditionList(list);
      return table;
   }

   private static boolean refused(TableAssembly table) throws Exception {
      VSMVAnalyzer analyzer = new VSMVAnalyzer("1^128^__NULL__^vs", null, null, null, false, null);
      Method method = VSMVAnalyzer.class.getDeclaredMethod(
         "containsDynamicConditionOnNamedGroup", TableAssembly.class,
         TransformationDescriptor.class, VariableTable.class);
      method.setAccessible(true);
      // a descriptor without a worksheet: variable and session variable items are still seen
      return (Boolean) method.invoke(
         analyzer, table, new TransformationDescriptor(), new VariableTable());
   }
}
