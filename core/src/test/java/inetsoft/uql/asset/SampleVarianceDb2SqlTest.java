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
import inetsoft.uql.jdbc.SQLHelper;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #79477: DB2 VARIANCE/STDDEV are the population (biased) functions and VARIANCE_SAMP/
 * STDDEV_SAMP don't exist before DB2 11.1, so the sample Variance and Standard Deviation
 * aggregates must use the portable n - 1 formula, not a native function.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = BaseTestConfiguration.class,
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class SampleVarianceDb2SqlTest {
   // shaped like the helper PreAssetQuery builds over the real SQL helper
   private static AggregateHelper helper(String type) {
      SQLHelper sql = SQLHelper.getSQLHelper(type);

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
            return sql.getAggregateFunction(func);
         }
      };
   }

   private static final String NUM =
      "((0.0+SUM((0.0+T.C) * (0.0+T.C))) * COUNT(T.C) - ((0.0 + SUM(T.C)) * SUM(T.C) ))/";

   // DB2 types 0.0 as DECIMAL(2,1), so only the divisor starts from the DOUBLE literal 0.0E0
   private static final String DB2_SAMPLE =
      NUM + "NULLIF(((0.0E0 + COUNT(T.C)) * (COUNT(T.C) - 1)), 0)";

   @Test
   void db2VarianceUsesPortableSampleFormula() {
      String v = AggregateFormula.VARIANCE.getExpression("T.C", null, helper("db2"));
      assertFalse(v.toLowerCase().contains("variance("), v);
      assertFalse(v.toLowerCase().contains("_samp"), v);
      // n - 1 divisor in DOUBLE, guarded by NULLIF for a single-row group
      assertEquals(DB2_SAMPLE, v);
      // the numerator stays exact DECIMAL: identical values give exactly 0, never negative
      assertFalse(v.substring(0, NUM.length()).contains("0.0E0"), v);
   }

   @Test
   void db2StandardDeviationUsesPortableSampleFormula() {
      String s = AggregateFormula.STANDARD_DEVIATION.getExpression("T.C", null, helper("db2"));
      assertFalse(s.toLowerCase().contains("stddev("), s);
      assertFalse(s.toLowerCase().contains("_samp"), s);
      assertEquals("SQRT(" + DB2_SAMPLE + ")", s);
   }

   @Test
   void nonDb2SampleSqlIsUnchanged() {
      String expected = NUM + "NULLIF(((0.0 + COUNT(T.C)) * (COUNT(T.C) - 1)), 0)";

      assertEquals(expected, AggregateFormula.VARIANCE.getExpression("T.C", null, null));
      assertEquals("SQRT(" + expected + ")",
                   AggregateFormula.STANDARD_DEVIATION.getExpression("T.C", null, null));
   }

   @Test
   void db2PopulationVariantsKeepFallback() {
      String v = AggregateFormula.POPULATION_VARIANCE.getExpression("T.C", null, helper("db2"));
      String s = AggregateFormula.POPULATION_STANDARD_DEVIATION
         .getExpression("T.C", null, helper("db2"));
      assertFalse(v.toLowerCase().contains("_samp"), v);
      assertFalse(s.toLowerCase().contains("_samp"), s);
      assertTrue(s.startsWith("SQRT("), s);
   }
}
