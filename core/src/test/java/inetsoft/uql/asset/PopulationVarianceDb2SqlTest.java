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
 * Bug #79479: DB2 has no varp/stddevp mapping, so the fallback SQL must not use the
 * DECIMAL(2,1) literal 0.0, which truncates the quotient to one decimal digit.
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

   // the exact (scale-preserving) SUMSQ term keeps its DECIMAL 0.0; only the division matters
   private static String stripSumSq(String sql) {
      return sql.replace("0.0E0", "").replace("(0.0+T.C)", "").replace("(0.0+c)", "");
   }

   @Test
   void db2PopulationVarianceUsesDoubleLiteral() {
      String sql = AggregateFormula.POPULATION_VARIANCE.getExpression("T.C", null, helper("db2"));
      assertTrue(sql.contains("0.0E0"), sql);
      assertFalse(stripSumSq(sql).contains("0.0"), sql);
   }

   @Test
   void db2PopulationStdDevUsesDoubleLiteral() {
      String sql = AggregateFormula.POPULATION_STANDARD_DEVIATION
         .getExpression("T.C", null, helper("db2"));
      assertTrue(sql.startsWith("SQRT("), sql);
      assertFalse(stripSumSq(sql).contains("0.0"), sql);
   }

   @Test
   void db2SubExpressionsUseDoubleLiteral() {
      String v = AggregateFormula.POPULATION_VARIANCE.getExpressionSub("c", null, helper("db2"));
      String s = AggregateFormula.POPULATION_STANDARD_DEVIATION
         .getExpressionSub("c", null, helper("db2"));
      assertTrue(v.contains("0.0E0"), v);
      assertFalse(stripSumSq(v).contains("0.0"), v);
      assertTrue(s.contains("0.0E0"), s);
   }

   @Test
   void otherDialectsAreUnchanged() {
      for(String type : new String[] { "derby", "postgresql", "sqlserver" }) {
         String sql = AggregateFormula.POPULATION_VARIANCE.getExpression("T.C", null, helper(type));
         assertFalse(sql.contains("0.0E0"), sql);
         assertTrue(sql.contains("0.0"), sql);
      }

      String nullHelper = AggregateFormula.POPULATION_VARIANCE.getExpression("T.C", null, null);
      assertFalse(nullHelper.contains("0.0E0"), nullHelper);
   }
}
