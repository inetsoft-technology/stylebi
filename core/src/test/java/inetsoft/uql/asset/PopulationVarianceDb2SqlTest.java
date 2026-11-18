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
package inetsoft.uql.asset;

import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import inetsoft.uql.erm.DataRef;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #79479: DB2 has no varp/stddevp mapping, so the fallback SQL uses an exact DECIMAL
 * numerator (0.0), a DOUBLE denominator (0.0E0, no scale truncation) and clamps the numerator
 * at 0 so SQRT never sees a negative. Other dialects and a null helper are unchanged.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = BaseTestConfiguration.class,
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class PopulationVarianceDb2SqlTest {
   private static AggregateHelper helper(String type) {
      return new AggregateHelper() {
         @Override
         public String getColumnString(DataRef column) {
            return null;
         }

         @Override
         public String getDBType() {
            return type;
         }

         @Override
         public String getAggregateFunction(String func) {
            return null;
         }
      };
   }

   // DB2 shape: exact DECIMAL numerator (0.0), DOUBLE denominator (0.0E0), CASE clamp
   private static final String NUM_T =
      "((0.0+SUM((0.0+T.C) * (0.0+T.C))) * COUNT(T.C) - ((0.0 + SUM(T.C)) * SUM(T.C) ))";
   private static final String VAR_T = "(CASE WHEN " + NUM_T + " < 0 THEN 0.0E0 ELSE " + NUM_T
      + " END)/((0.0E0 + COUNT(T.C)) * COUNT(T.C))";

   @Test
   void db2PopulationVarianceUsesExactNumeratorDoubleDenominatorAndClamp() {
      assertEquals(VAR_T,
                   AggregateFormula.POPULATION_VARIANCE.getExpression("T.C", null, helper("db2")));
   }

   @Test
   void db2PopulationStdDevWrapsClampedVariance() {
      assertEquals("SQRT(" + VAR_T + ")", AggregateFormula.POPULATION_STANDARD_DEVIATION
         .getExpression("T.C", null, helper("db2")));
   }

   @Test
   void db2SubExpressionsUseClampAndDoubleDenominator() {
      String v = AggregateFormula.POPULATION_VARIANCE.getExpressionSub("c", null, helper("db2"));
      String s = AggregateFormula.POPULATION_STANDARD_DEVIATION
         .getExpressionSub("c", null, helper("db2"));
      assertTrue(v.startsWith("(CASE WHEN ((0.0+"), v);
      assertTrue(v.contains(" < 0 THEN 0.0E0 ELSE "), v);
      assertTrue(v.contains("/((0.0E0 + "), v);
      assertEquals(1, v.split("0[.]0E0 [+]", -1).length - 1, v);
      assertEquals("SQRT(" + v + ")", s);
   }

   @Test
   void otherDialectsAreUnchanged() {
      for(String type : new String[] { "derby", "postgresql", "sqlserver" }) {
         String sql = AggregateFormula.POPULATION_VARIANCE.getExpression("T.C", null, helper(type));
         assertFalse(sql.contains("0.0E0"), sql);
         assertTrue(sql.contains("0.0"), sql);
      }

      // exact original shape, byte for byte
      String orig = "((0.0+SUM((0.0+T.C) * (0.0+T.C))) * COUNT(T.C) - ((0.0 + SUM(T.C)) * "
         + "SUM(T.C) ))/((0.0 + COUNT(T.C)) * COUNT(T.C))";
      assertEquals(orig, AggregateFormula.POPULATION_VARIANCE.getExpression("T.C", null, null));
      assertEquals("SQRT(" + orig + ")",
                   AggregateFormula.POPULATION_STANDARD_DEVIATION.getExpression("T.C", null, null));
      assertEquals(orig, AggregateFormula.POPULATION_VARIANCE
         .getExpression("T.C", null, helper("derby")));

      String nullHelper = AggregateFormula.POPULATION_VARIANCE.getExpression("T.C", null, null);
      assertFalse(nullHelper.contains("0.0E0"), nullHelper);
   }
}
