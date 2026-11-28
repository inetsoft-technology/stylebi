/*
 * This file is part of StyleBI.
 * Copyright (C) 2026 InetSoft Technology
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
package inetsoft.util;

import inetsoft.uql.Condition;
import inetsoft.uql.XCondition;
import inetsoft.uql.schema.XSchema;
import inetsoft.test.*;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #82378: BigDecimal/BigInteger values above 2^53 must compare exactly through
 * CoreTool.compare / CoreTool.equals (used by group by, distinct and conditions), not through
 * double.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = BaseTestConfiguration.class,
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class CoreToolCompareBigDecimalTest {
   private static final BigDecimal A = new BigDecimal("9007199254740995");
   private static final BigDecimal B = new BigDecimal("9007199254740996");

   private static int cmp(Object a, Object b) {
      return CoreTool.compare(a, b, true, true);
   }

   @Test
   void bigDecimalsAbove2Pow53AreDistinct() {
      assertTrue(cmp(A, B) < 0);
      assertTrue(cmp(B, A) > 0);
      assertEquals(0, cmp(A, new BigDecimal("9007199254740995.0")));
      assertFalse(CoreTool.equals(A, B));
      assertTrue(CoreTool.equals(A, new BigDecimal("9007199254740995")));
   }

   @Test
   void bigIntegerAndLongMixAreExact() {
      assertTrue(cmp(A.toBigInteger(), B) < 0);
      assertTrue(cmp(9007199254740993L, new BigDecimal("9007199254740994")) < 0);
      assertEquals(0, cmp(9007199254740993L, new BigDecimal("9007199254740993")));
      assertFalse(CoreTool.equals(9007199254740993L, new BigDecimal("9007199254740994")));
   }

   @Test
   void smallAndFloatingKeepOldBehavior() {
      assertEquals(0, cmp(new BigDecimal("5"), 5));
      assertEquals(0, cmp(new BigDecimal("5.5"), 5.5));
      assertTrue(cmp(new BigDecimal("1.25"), new BigDecimal("1.5")) < 0);
   }

   @Test
   void equalToConditionOnBigDecimal() {
      for(String type : new String[] { XSchema.LONG, XSchema.DOUBLE, XSchema.DECIMAL }) {
         Condition cond = new Condition(type);
         cond.setOperation(XCondition.EQUAL_TO);
         cond.addValue(A);
         assertTrue(cond.evaluate(A), type);
         assertFalse(cond.evaluate(B), type);
      }
   }
}
