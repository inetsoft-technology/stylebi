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
package inetsoft.uql;

import inetsoft.test.*;
import inetsoft.uql.schema.XSchema;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Bug #82411: only a comparison with a sub query matches any of its values. A plain condition
 * with several values still compares with the first one, as the pushed-down sql does.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class ConditionMultiValueComparisonTest {
   private static Condition cond(int op, boolean equal, Object... values) {
      Condition cond = new Condition(XSchema.INTEGER);
      cond.setOperation(op);
      cond.setEqual(equal);

      for(Object value : values) {
         cond.addValue(value);
      }

      return cond;
   }

   @Test
   void greaterThanUsesTheFirstValueOnly() {
      assertEquals(false, cond(XCondition.GREATER_THAN, false, 5, 1).evaluate(3));
      assertEquals(true, cond(XCondition.GREATER_THAN, false, 1, 5).evaluate(3));
      assertEquals(true, cond(XCondition.GREATER_THAN, true, 3, 9).evaluate(3));
   }

   @Test
   void lessThanUsesTheFirstValueOnly() {
      assertEquals(false, cond(XCondition.LESS_THAN, false, 1, 5).evaluate(3));
      assertEquals(true, cond(XCondition.LESS_THAN, false, 5, 1).evaluate(3));
   }
}
