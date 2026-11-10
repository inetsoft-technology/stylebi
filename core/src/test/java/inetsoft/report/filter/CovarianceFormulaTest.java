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
package inetsoft.report.filter;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for CovarianceFormula, Bug #80974: a blank string is no value; the double[] pair is members 0 and 1.
 */
@Tag("core")
class CovarianceFormulaTest {
   private static final Object[][] PAIRS = {
      { 1, 1 }, { 2, 2 }, { "", 3 }, { 4, 4 }, { 5, 5.5 }
   };

   private static void feed(Formula f, Object[][] pairs) {
      for(Object[] p : pairs) {
         f.addValue((Object) p);
      }
   }

   @Test
   void blankInPair_skipsWholePair() {
      CovarianceFormula f = new CovarianceFormula();
      feed(f, PAIRS);
      assertEquals(2.75, ((Number) f.getResult()).doubleValue(), 1e-4);
   }

   @Test
   void blankOnEitherSideOrWhitespace_skipsPair() {
      CovarianceFormula a = new CovarianceFormula();
      feed(a, new Object[][] { { 1, 1 }, { 2, 2 }, { 3, "" }, { 4, 4 }, { 5, 5.5 }, { " ", 9 } });
      CovarianceFormula b = new CovarianceFormula();
      feed(b, new Object[][] { { 1, 1 }, { 2, 2 }, { 4, 4 }, { 5, 5.5 } });
      assertEquals(((Number) b.getResult()).doubleValue(), ((Number) a.getResult()).doubleValue(), 1e-12);
   }

   @Test
   void allBlank_sameAsAllNull() {
      CovarianceFormula blank = new CovarianceFormula();
      CovarianceFormula nulls = new CovarianceFormula();
      blank.setDefaultResult(false);
      nulls.setDefaultResult(false);
      feed(blank, new Object[][] { { "", "" }, { "", 3 } });
      feed(nulls, new Object[][] { { null, null }, { null, 3 } });
      assertEquals(nulls.getResult(), blank.getResult());
      assertEquals(nulls.isNull(), blank.isNull());
   }

   @Test
   void doubleArrayPair_readsMembersZeroAndOne() {
      CovarianceFormula f = new CovarianceFormula();
      f.addValue(new double[] { 1, 1 });
      f.addValue(new double[] { 2, 2 });
      f.addValue(new double[] { 4, 4 });
      f.addValue(new double[] { 5, 5.5 });
      CovarianceFormula g = new CovarianceFormula();
      feed(g, new Object[][] { { 1, 1 }, { 2, 2 }, { 4, 4 }, { 5, 5.5 } });
      assertEquals(((Number) g.getResult()).doubleValue(), ((Number) f.getResult()).doubleValue(), 1e-12);
      // too short / null sentinel are skipped, not an exception
      f.addValue(new double[] { 9 });
      f.addValue(new double[] { 9, inetsoft.util.Tool.NULL_DOUBLE });
      assertEquals(((Number) g.getResult()).doubleValue(), ((Number) f.getResult()).doubleValue(), 1e-12);
   }
}
