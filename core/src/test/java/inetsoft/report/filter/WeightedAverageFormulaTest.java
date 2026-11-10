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
 * Tests for {@link WeightedAverageFormula}, Bug #80584.
 */
@Tag("core")
class WeightedAverageFormulaTest {
   @Test
   void addValue_numericStrings_computesWeightedAverage() {
      WeightedAverageFormula f = new WeightedAverageFormula();
      f.addValue(new Object[] { "10", "0.5" });
      f.addValue(new Object[] { "20", "0.5" });
      assertEquals(15.0, ((Number) f.getResult()).doubleValue(), 0.0001);
   }

   @Test
   void addValue_blankStringInEitherPosition_pairIsSkipped() {
      WeightedAverageFormula f = new WeightedAverageFormula();
      f.addValue(new Object[] { "", "0.5" });
      f.addValue(new Object[] { "  ", "0.5" });
      f.addValue(new Object[] { "10", "" });
      f.addValue(new Object[] { "20", "0.2" });
      assertEquals(20.0, ((Number) f.getResult()).doubleValue(), 0.0001);
   }

   @Test
   void addValue_nullAndNonNumericText_pairIsSkipped() {
      WeightedAverageFormula f = new WeightedAverageFormula();
      f.addValue(new Object[] { null, "0.5" });
      f.addValue(new Object[] { "abc", "0.5" });
      f.addValue(new Object[] { 4, 2 });
      assertEquals(4.0, ((Number) f.getResult()).doubleValue(), 0.0001);
   }
}
