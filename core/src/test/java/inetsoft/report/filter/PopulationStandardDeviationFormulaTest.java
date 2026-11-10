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
 * Tests for PopulationStandardDeviationFormula, Bug #80974: a blank string is no value; the double[] pair is members 0 and 1.
 */
@Tag("core")
class PopulationStandardDeviationFormulaTest {
   @Test
   void blankString_isSkipped() {
      PopulationStandardDeviationFormula f = new PopulationStandardDeviationFormula();
      for(Object o : new Object[] { 2, "", 4, " ", 6 }) {
         f.addValue(o);
      }
      assertEquals(Math.sqrt(8.0 / 3), ((Number) f.getResult()).doubleValue(), 1e-9);
   }
}
