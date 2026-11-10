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
package inetsoft.uql.asset;

import inetsoft.test.*;
import inetsoft.uql.VariableTable;
import inetsoft.uql.XCondition;
import inetsoft.uql.schema.UserVariable;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The N of a top N / bottom N condition taken from a variable. The basic cases are in
 * RankingConditionTest.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = BaseTestConfiguration.class,
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class RankingConditionVariableTest {
   private static RankingCondition ranking(int op, Object n) {
      RankingCondition cond = new RankingCondition();
      cond.setOperation(op);
      assertTrue(cond.setN(n));
      return cond;
   }

   private static VariableTable vars(Object value) {
      VariableTable vars = new VariableTable();
      vars.put("n", value);
      return vars;
   }

   @Test
   void numberVariable_becomesN() {
      RankingCondition cond = ranking(XCondition.TOP_N, "$(n)");
      cond.replaceVariable(vars(5));

      assertEquals(5, cond.getN());
   }

   @Test
   void bottomN_isResolvedTheSameWay() {
      RankingCondition cond = ranking(XCondition.BOTTOM_N, "$(n)");
      cond.replaceVariable(vars(3));

      assertEquals(3, cond.getN());
      assertEquals(XCondition.BOTTOM_N, cond.getOperation());
   }

   @Test
   void userVariable_becomesN() {
      RankingCondition cond = ranking(XCondition.TOP_N, new UserVariable("n"));
      cond.replaceVariable(vars(7));

      assertEquals(7, cond.getN());
   }

   @Test
   void textNumber_isParsed() {
      RankingCondition cond = ranking(XCondition.TOP_N, "$(n)");
      cond.replaceVariable(vars("10"));

      assertEquals(10, cond.getN());
   }

   @Test
   void decimalNumber_isTruncated() {
      RankingCondition cond = ranking(XCondition.TOP_N, "$(n)");
      cond.replaceVariable(vars(2.9));

      assertEquals(2, cond.getN());
   }

   @Test
   void longNumber_isNarrowedToInt() {
      RankingCondition cond = ranking(XCondition.TOP_N, "$(n)");
      cond.replaceVariable(vars(3000000000L));

      assertEquals((int) 3000000000L, cond.getN());
   }

   @Test
   void multiValueVariable_usesTheFirstValue() {
      RankingCondition cond = ranking(XCondition.TOP_N, "$(n)");
      cond.replaceVariable(vars(new Object[] { 4, 9 }));

      assertEquals(4, cond.getN());
   }

   @Test
   void missingVariable_keepsTheReference() {
      // the condition is not ignored: N stays the unresolved reference
      RankingCondition cond = ranking(XCondition.TOP_N, "$(n)");
      cond.replaceVariable(new VariableTable());

      assertEquals("$(n)", cond.getN());
   }

   @Test
   void nonNumericText_keepsTheReference() {
      RankingCondition cond = ranking(XCondition.TOP_N, "$(n)");
      cond.replaceVariable(vars("abc"));

      assertEquals("$(n)", cond.getN());
   }

   @Test
   void emptyArray_keepsTheReference() {
      RankingCondition cond = ranking(XCondition.TOP_N, "$(n)");
      cond.replaceVariable(new VariableTable() {
         @Override
         public Object get(String name) {
            return new Object[0];
         }
      });

      assertEquals("$(n)", cond.getN());
   }

   @Test
   void fixedN_isNotChanged() {
      RankingCondition cond = ranking(XCondition.TOP_N, 5);
      cond.replaceVariable(vars(9));

      assertEquals(5, cond.getN());
   }

   @Test
   void getAllVariables_listsTheReferencedVariable() {
      RankingCondition cond = ranking(XCondition.TOP_N, "$(n)");

      UserVariable[] variables = cond.getAllVariables();

      assertEquals(1, variables.length);
      assertEquals("n", variables[0].getName());
   }

   @Test
   void getAllVariables_skipsBuiltinVariables() {
      RankingCondition cond = ranking(XCondition.TOP_N, "$(_USER_)");

      assertEquals(0, cond.getAllVariables().length);
   }
}
