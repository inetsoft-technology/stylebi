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
package inetsoft.web.binding.model;

import inetsoft.test.*;
import inetsoft.uql.Condition;
import inetsoft.uql.ConditionItem;
import inetsoft.uql.ConditionList;
import inetsoft.uql.JunctionOperator;
import inetsoft.uql.XCondition;
import inetsoft.uql.asset.DateCondition;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.erm.DataRef;
import inetsoft.uql.schema.XSchema;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.sql.Timestamp;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Regression tests for PC-007: a date_in-resolved named-group condition (a raw DateCondition
 * clone, per ConditionUtil.fromModelToConditionList()'s DATE_IN branch) must be normalized into
 * the plain GREATER_THAN(>=)/AND/LESS_THAN(<=) range shape NamedRangeRef already knows how to
 * render, since a DateCondition is a sibling of Condition (both extend AbstractCondition) and
 * would otherwise fall through ConditionItem's deprecated getCondition() accessor as a blank,
 * always-empty condition.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class, PluginsTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
public class NamedGroupInfoModelTest {

   @Test
   void normalizeDateInGroupCondition_expandsDateConditionIntoBetweenRange() {
      DataRef attribute = dateAttribute(XSchema.DATE);
      ConditionList conditionList = singleItemList(attribute, new DateCondition.YearCondition(0));

      NamedGroupInfoModel.normalizeDateInGroupCondition(conditionList);

      assertEquals(3, conditionList.getConditionSize());

      ConditionItem startItem = conditionList.getConditionItem(0);
      Condition start = startItem.getCondition();
      assertEquals(XCondition.GREATER_THAN, start.getOperation());
      assertTrue(start.isEqual());
      assertEquals(1, start.getValueCount());
      assertFalse(start.getValue(0) instanceof Timestamp, "non-timestamp field must use java.sql.Date");
      assertEquals(startOfThisYear().getTimeInMillis(), ((Date) start.getValue(0)).getTime());
      assertSame(attribute, startItem.getAttribute());

      assertEquals(JunctionOperator.AND, conditionList.getJunction(1));

      ConditionItem endItem = conditionList.getConditionItem(2);
      Condition end = endItem.getCondition();
      assertEquals(XCondition.LESS_THAN, end.getOperation());
      assertTrue(end.isEqual());
      assertEquals(1, end.getValueCount());
      assertFalse(end.getValue(0) instanceof Timestamp);
      assertEquals(endOfThisYear().getTimeInMillis(), ((Date) end.getValue(0)).getTime());
      assertSame(attribute, endItem.getAttribute());
   }

   @Test
   void normalizeDateInGroupCondition_timestampField_usesTimestampValues() {
      DataRef attribute = dateAttribute(XSchema.TIME_INSTANT);
      ConditionList conditionList = singleItemList(attribute, new DateCondition.YearCondition(0));

      NamedGroupInfoModel.normalizeDateInGroupCondition(conditionList);

      Condition start = conditionList.getConditionItem(0).getCondition();
      Condition end = conditionList.getConditionItem(2).getCondition();
      assertTrue(start.getValue(0) instanceof Timestamp, "timestamp field must use java.sql.Timestamp");
      assertTrue(end.getValue(0) instanceof Timestamp, "timestamp field must use java.sql.Timestamp");
   }

   @Test
   void normalizeDateInGroupCondition_plainCondition_isNoOp() {
      DataRef attribute = dateAttribute(XSchema.DATE);
      Condition plain = new Condition();
      plain.setOperation(XCondition.EQUAL_TO);
      plain.addValue("g1");
      ConditionList conditionList = singleItemList(attribute, plain);

      NamedGroupInfoModel.normalizeDateInGroupCondition(conditionList);

      assertEquals(1, conditionList.getConditionSize());
      assertSame(plain, conditionList.getConditionItem(0).getCondition());
   }

   @Test
   void normalizeDateInGroupCondition_negatedCondition_isNoOp() {
      DataRef attribute = dateAttribute(XSchema.DATE);
      DateCondition dateCondition = new DateCondition.YearCondition(0);
      dateCondition.setNegated(true);
      ConditionList conditionList = singleItemList(attribute, dateCondition);

      NamedGroupInfoModel.normalizeDateInGroupCondition(conditionList);

      assertEquals(1, conditionList.getConditionSize());
      assertSame(dateCondition, conditionList.getConditionItem(0).getXCondition());
   }

   @Test
   void normalizeDateInGroupCondition_nullList_doesNotThrow() {
      assertDoesNotThrow(() -> NamedGroupInfoModel.normalizeDateInGroupCondition(null));
   }

   @Test
   void normalizeDateInGroupCondition_alreadyMultiItemList_isUntouched() {
      DataRef attribute = dateAttribute(XSchema.DATE);
      Condition start = new Condition();
      start.setOperation(XCondition.GREATER_THAN);
      start.setEqual(true);
      start.addValue(new java.sql.Date(0));
      Condition end = new Condition();
      end.setOperation(XCondition.LESS_THAN);
      end.setEqual(true);
      end.addValue(new java.sql.Date(1000));

      ConditionList conditionList = new ConditionList();
      conditionList.append(new ConditionItem(attribute, start, 0));
      conditionList.append(new JunctionOperator(JunctionOperator.AND, 0));
      conditionList.append(new ConditionItem(attribute, end, 0));

      NamedGroupInfoModel.normalizeDateInGroupCondition(conditionList);

      assertEquals(3, conditionList.getConditionSize());
      assertSame(start, conditionList.getConditionItem(0).getCondition());
      assertSame(end, conditionList.getConditionItem(2).getCondition());
   }

   private static ConditionList singleItemList(DataRef attribute, XCondition condition) {
      ConditionList conditionList = new ConditionList();
      conditionList.append(new ConditionItem(attribute, condition, 0));
      return conditionList;
   }

   private static AttributeRef dateAttribute(String dataType) {
      AttributeRef attribute = new AttributeRef("date_col");
      attribute.setDataType(dataType);
      return attribute;
   }

   private static Calendar startOfThisYear() {
      Calendar cal = new GregorianCalendar();
      cal.set(currentYear(), Calendar.JANUARY, 1, 0, 0, 0);
      cal.set(Calendar.MILLISECOND, 0);
      return cal;
   }

   private static Calendar endOfThisYear() {
      Calendar cal = new GregorianCalendar();
      cal.set(currentYear(), Calendar.DECEMBER, 31, 23, 59, 59);
      cal.set(Calendar.MILLISECOND, 0);
      return cal;
   }

   private static int currentYear() {
      return Calendar.getInstance().get(Calendar.YEAR);
   }
}
