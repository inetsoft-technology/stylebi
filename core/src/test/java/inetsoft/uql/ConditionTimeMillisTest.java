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

import java.sql.Time;
import java.util.Calendar;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #82477: an in-memory TIME condition ignored milliseconds, so "= 00:00:00" matched a row
 * of 00:00:00.500. Rows with and without ms, for =, one of, their negations, and range operators.
 */
// Condition's constructor reads a property through PropertiesEngine, a Spring bean
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = BaseTestConfiguration.class,
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class ConditionTimeMillisTest {
   private static Time time(int h, int m, int s, int ms) {
      Calendar c = Calendar.getInstance();
      c.clear();
      c.set(1970, Calendar.JANUARY, 1, h, m, s);
      c.set(Calendar.MILLISECOND, ms);
      return new Time(c.getTimeInMillis());
   }

   private static Condition cond(int op, boolean negated, Object... values) {
      Condition c = new Condition(XSchema.TIME);
      c.setOperation(op);
      c.setNegated(negated);

      for(Object v : values) {
         c.addValue(v);
      }

      return c;
   }

   @Test
   void equalTo() {
      Condition c = cond(XCondition.EQUAL_TO, false, time(0, 0, 0, 0));
      assertTrue(c.evaluate(time(0, 0, 0, 0)));
      assertFalse(c.evaluate(time(0, 0, 0, 500)));
      assertFalse(c.evaluate(time(0, 0, 1, 0)));
   }

   @Test
   void notEqualTo() {
      Condition c = cond(XCondition.EQUAL_TO, true, time(0, 0, 0, 0));
      assertFalse(c.evaluate(time(0, 0, 0, 0)));
      assertTrue(c.evaluate(time(0, 0, 0, 500)));
   }

   @Test
   void oneOf() {
      Condition c = cond(XCondition.ONE_OF, false, time(0, 0, 0, 0), time(13, 45, 30, 0));
      assertTrue(c.evaluate(time(0, 0, 0, 0)));
      assertTrue(c.evaluate(time(13, 45, 30, 0)));
      assertFalse(c.evaluate(time(0, 0, 0, 500)));
      assertFalse(c.evaluate(time(13, 45, 30, 250)));
   }

   @Test
   void notOneOf() {
      Condition c = cond(XCondition.ONE_OF, true, time(0, 0, 0, 0), time(13, 45, 30, 0));
      assertFalse(c.evaluate(time(0, 0, 0, 0)));
      assertTrue(c.evaluate(time(0, 0, 0, 500)));
      assertTrue(c.evaluate(time(13, 45, 30, 250)));
   }

   @Test
   void rangeOperators() {
      Condition gt = cond(XCondition.GREATER_THAN, false, time(0, 0, 0, 0));
      assertTrue(gt.evaluate(time(0, 0, 0, 500)));
      assertFalse(gt.evaluate(time(0, 0, 0, 0)));

      Condition lt = cond(XCondition.LESS_THAN, false, time(0, 0, 0, 500));
      assertTrue(lt.evaluate(time(0, 0, 0, 0)));
      assertFalse(lt.evaluate(time(0, 0, 0, 500)));

      Condition between = cond(XCondition.BETWEEN, false, time(0, 0, 0, 100), time(0, 0, 0, 600));
      assertTrue(between.evaluate(time(0, 0, 0, 500)));
      assertFalse(between.evaluate(time(0, 0, 0, 0)));
      assertFalse(between.evaluate(time(0, 0, 0, 700)));
   }
}
