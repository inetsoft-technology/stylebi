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
package inetsoft.uql;

import inetsoft.test.*;
import inetsoft.uql.schema.UserVariable;
import inetsoft.uql.schema.XSchema;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static org.junit.jupiter.api.Assertions.*;

/**
 * In-memory condition evaluation of parameter values: the sentinel values NULL_VALUE,
 * EMPTY_STRING and NULL_STRING, and variables that resolve to arrays. The SQL generation side
 * of the sentinels is covered by the XUtilSentinel*Test classes.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = BaseTestConfiguration.class,
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class ConditionVariableValueTest {
   private static Condition condition(String type, int op, Object... values) {
      Condition cond = new Condition(type);
      cond.setOperation(op);

      for(Object value : values) {
         cond.addValue(value);
      }

      return cond;
   }

   private static VariableTable vars(String name, Object value) {
      VariableTable vart = new VariableTable();
      vart.put(name, value);
      return vart;
   }

   @Nested
   class SentinelValues {
      @Test
      void nullValue_turnsConditionIntoNullTest() {
         Condition cond = condition(XSchema.STRING, XCondition.EQUAL_TO,
                                    XConstants.CONDITION_NULL_VALUE);

         assertTrue(cond.evaluate(null));
         assertEquals(XCondition.NULL, cond.getOperation());
         assertFalse(cond.evaluate("a"));
      }

      @Test
      void emptyString_matchesOnlyEmptyString() {
         Condition cond = condition(XSchema.STRING, XCondition.EQUAL_TO,
                                    XConstants.CONDITION_EMPTY_STRING);

         assertTrue(cond.evaluate(""));
         assertEquals("", cond.getValue(0));
         assertFalse(cond.evaluate("a"));
      }

      @Test
      void nullString_matchesTheTextNull() {
         Condition cond = condition(XSchema.STRING, XCondition.EQUAL_TO,
                                    XConstants.CONDITION_NULL_STRING);

         assertTrue(cond.evaluate("null"));
         assertEquals("null", cond.getValue(0));
         assertFalse(cond.evaluate(""));
      }

      @Test
      void oneOf_singleNullValueElement_turnsConditionIntoNullTest() {
         Condition cond = condition(XSchema.STRING, XCondition.ONE_OF,
                                    new Object[] { XConstants.CONDITION_NULL_VALUE });

         assertTrue(cond.evaluate(null));
         assertEquals(XCondition.NULL, cond.getOperation());
      }

      @Test
      void oneOf_singleEmptyStringElement_isReplacedByEmptyString() {
         Object[] values = { XConstants.CONDITION_EMPTY_STRING };
         Condition cond = condition(XSchema.STRING, XCondition.ONE_OF, (Object) values);

         cond.evaluate("");
         assertEquals("", values[0]);
         assertEquals(XCondition.ONE_OF, cond.getOperation());
      }

      @Test
      void oneOf_singleNullStringElement_isReplacedByTextNull() {
         Object[] values = { XConstants.CONDITION_NULL_STRING };
         Condition cond = condition(XSchema.STRING, XCondition.ONE_OF, (Object) values);

         cond.evaluate("null");
         assertEquals("null", values[0]);
      }

      @Test
      void oneOf_sentinelAmongOtherElements_isNotRewritten() {
         Object[] values = { XConstants.CONDITION_NULL_VALUE, "a" };
         Condition cond = condition(XSchema.STRING, XCondition.ONE_OF, (Object) values);

         assertTrue(cond.evaluate("a"));
         assertEquals(XCondition.ONE_OF, cond.getOperation());
         assertEquals(XConstants.CONDITION_NULL_VALUE, values[0]);
      }
   }

   @Nested
   class VariableResolvedToArray {
      @Test
      void oneOf_multipleElements_matchesAnyElement() throws Exception {
         Condition cond = condition(XSchema.STRING, XCondition.ONE_OF, new UserVariable("v"));
         cond.replaceVariable(vars("v", new Object[] { "a", "b" }));

         assertFalse(cond.isIgnored());
         assertTrue(cond.evaluate("a"));
         assertTrue(cond.evaluate("b"));
         assertFalse(cond.evaluate("c"));
      }

      @Test
      void oneOf_singleElement_matchesThatElement() throws Exception {
         Condition cond = condition(XSchema.STRING, XCondition.ONE_OF, new UserVariable("v"));
         cond.replaceVariable(vars("v", new Object[] { "a" }));

         assertFalse(cond.isIgnored());
         assertTrue(cond.evaluate("a"));
         assertFalse(cond.evaluate("b"));
      }

      @Test
      void oneOf_emptyArrayFromVariableTable_isIgnored() throws Exception {
         // VariableTable.put stores an array without a non-null element, an empty one included,
         // as null, so the condition is ignored and every row passes
         Condition cond = condition(XSchema.STRING, XCondition.ONE_OF, new UserVariable("v"));
         cond.replaceVariable(vars("v", new Object[0]));

         assertTrue(cond.isIgnored());
      }

      @Test
      void oneOf_emptyArrayBypassingPut_isNotIgnoredAndMatchesNothing() throws Exception {
         Condition cond = condition(XSchema.STRING, XCondition.ONE_OF, new UserVariable("v"));
         cond.replaceVariable(new VariableTable() {
            @Override
            public Object get(UserVariable var) {
               return new Object[0];
            }
         });

         assertFalse(cond.isIgnored());
         assertFalse(cond.evaluate("a"));
         assertFalse(cond.evaluate(null));
      }

      @Test
      void oneOf_singleNullElementArray_isIgnored() throws Exception {
         Condition cond = condition(XSchema.STRING, XCondition.ONE_OF, new UserVariable("v"));
         cond.replaceVariable(vars("v", new Object[] { null }));

         assertTrue(cond.isIgnored());
      }

      @Test
      void oneOf_allNullArray_isIgnored() throws Exception {
         Condition cond = condition(XSchema.STRING, XCondition.ONE_OF, new UserVariable("v"));
         cond.replaceVariable(vars("v", new Object[] { null, null }));

         assertTrue(cond.isIgnored());
      }

      @Test
      void oneOf_nullElementAmongOthers_isNotIgnored() throws Exception {
         Condition cond = condition(XSchema.STRING, XCondition.ONE_OF, new UserVariable("v"));
         cond.replaceVariable(vars("v", new Object[] { "a", null }));

         assertFalse(cond.isIgnored());
         assertTrue(cond.evaluate("a"));
         assertFalse(cond.evaluate("b"));
      }

      @Test
      void equalTo_singleNullElementArray_isIgnored() throws Exception {
         // VariableTable.put turns it into null, so this holds for every operation
         Condition cond = condition(XSchema.STRING, XCondition.EQUAL_TO, new UserVariable("v"));
         cond.replaceVariable(vars("v", new Object[] { null }));

         assertTrue(cond.isIgnored());
      }

      @Test
      void equalTo_singleNullElementArrayBypassingPut_isNotIgnored() throws Exception {
         // Condition itself only special-cases {null} for ONE_OF
         Condition cond = condition(XSchema.STRING, XCondition.EQUAL_TO, new UserVariable("v"));
         cond.replaceVariable(new VariableTable() {
            @Override
            public Object get(UserVariable var) {
               return new Object[] { null };
            }
         });

         assertFalse(cond.isIgnored());
      }

      @Test
      void between_variableBounds_matchesInclusiveRange() throws Exception {
         Condition cond = condition(XSchema.INTEGER, XCondition.BETWEEN,
                                    new UserVariable("low"), new UserVariable("high"));
         VariableTable vart = new VariableTable();
         vart.put("low", 1);
         vart.put("high", 10);
         cond.replaceVariable(vart);

         assertFalse(cond.isIgnored());
         assertTrue(cond.evaluate(1));
         assertTrue(cond.evaluate(10));
         assertFalse(cond.evaluate(11));
      }

      @Test
      void between_oneBoundMissing_isIgnored() throws Exception {
         Condition cond = condition(XSchema.INTEGER, XCondition.BETWEEN,
                                    new UserVariable("low"), new UserVariable("high"));
         cond.replaceVariable(vars("low", 1));

         assertTrue(cond.isIgnored());
      }
   }
}
