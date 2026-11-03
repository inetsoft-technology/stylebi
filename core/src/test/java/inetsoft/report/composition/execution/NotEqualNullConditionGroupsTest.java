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

import inetsoft.report.filter.ConditionGroup;
import inetsoft.test.*;
import inetsoft.uql.ConditionItem;
import inetsoft.uql.ConditionList;
import inetsoft.uql.XCondition;
import inetsoft.uql.asset.AssetCondition;
import inetsoft.uql.asset.ColumnRef;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.schema.XSchema;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Bug #79870: every group that filters data (pre conditions, post/aggregate conditions, crosstab
 * post conditions) evaluates 'not equal to' as false for a null or empty value, as SQL does.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class NotEqualNullConditionGroupsTest {
   @Test
   void preConditionGroup() {
      check(new AssetConditionGroup(list(notEqual(XSchema.STRING, "v03")), 0, null));
   }

   @Test
   void postConditionGroup() throws Exception {
      Class<?> cls = Class.forName(AssetQuery.class.getName() + "$AssetConditionGroup2");
      Constructor<?> ctor = cls.getDeclaredConstructor(
         inetsoft.report.TableLens.class, ConditionList.class, int.class, AssetQuerySandbox.class,
         List.class, List.class, long.class, boolean.class);
      ctor.setAccessible(true);
      ConditionGroup group = (ConditionGroup) ctor.newInstance(
         null, list(notEqual(XSchema.STRING, "v03")), 0, null, new ArrayList<>(),
         new ArrayList<>(), -1L, false);
      check(group);
   }

   @Test
   void crosstabPostConditionGroup() {
      // the crosstab post condition groups (AssetQuery, AbstractCrosstabVSAQuery) are built with
      // the column lookup constructor and then marked as data conditions
      Function<String, Integer> find = name -> 0;
      ConditionGroup group = new ConditionGroup(list(notEqual(XSchema.STRING, "v03")), find);
      // not marked: highlight behaviour
      assertTrue(group.evaluate(new Object[] { (Object) null }));
      group.setNullNotEqualDropped(true);
      check(group);
   }

   @Test
   void numericColumn() {
      AssetCondition cond = notEqual(XSchema.INTEGER, 5);
      ConditionGroup group = new AssetConditionGroup(list(cond), 0, null);
      assertFalse(group.evaluate(new Object[] { (Object) null }));
      assertFalse(group.evaluate(new Object[] { 5 }));
      assertTrue(group.evaluate(new Object[] { 0 }));
      assertTrue(group.evaluate(new Object[] { 7 }));
   }

   @Test
   void notEqualToNullValueUnchanged() {
      // '<> null' (null comparison value is ignored by default): unchanged, nothing is dropped
      AssetCondition cond = new AssetCondition(XSchema.STRING);
      cond.setOperation(XCondition.EQUAL_TO);
      cond.setNegated(true);
      cond.addValue(null);
      ConditionGroup group = new AssetConditionGroup(list(cond), 0, null);
      assertTrue(group.evaluate(new Object[] { (Object) null }));
      assertTrue(group.evaluate(new Object[] { "v01" }));
   }

   private static void check(ConditionGroup group) {
      assertFalse(group.evaluate(new Object[] { (Object) null }));
      assertFalse(group.evaluate(new Object[] { "" }));
      assertFalse(group.evaluate(new Object[] { "v03" }));
      assertTrue(group.evaluate(new Object[] { "v01" }));
   }

   private static AssetCondition notEqual(String type, Object val) {
      AssetCondition cond = new AssetCondition(type);
      cond.setOperation(XCondition.EQUAL_TO);
      cond.setNegated(true);
      cond.addValue(val);
      return cond;
   }

   private static ConditionList list(AssetCondition cond) {
      ConditionList list = new ConditionList();
      list.append(new ConditionItem(new ColumnRef(new AttributeRef(null, "DIM")), cond, 0));
      return list;
   }
}
