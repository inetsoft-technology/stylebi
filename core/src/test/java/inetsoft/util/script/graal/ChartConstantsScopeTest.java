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
package inetsoft.util.script.graal;

import inetsoft.graph.GraphConstants;
import inetsoft.report.composition.region.ChartConstants;
import inetsoft.uql.XConstants;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Bug #81534: the constants the script editor lists under Chart must be defined in the Chart
 * constant scope.
 */
@Tag("core")
class ChartConstantsScopeTest {
   @Test
   void firstLastFormulaAndMasksAreDefined() {
      ConstantScope scope = new ConstantScope(ChartConstants.class);

      for(String name : new String[] { "FIRST_FORMULA", "LAST_FORMULA", "DASH_MASK",
                                       "WIDTH_MASK", "SUM_FORMULA", "SOLID_MASK" })
      {
         assertTrue(scope.hasMember(name), name + " should be defined");
      }

      assertEquals(XConstants.FIRST_FORMULA, scope.getMember("FIRST_FORMULA"));
      assertEquals(XConstants.LAST_FORMULA, scope.getMember("LAST_FORMULA"));
      assertEquals(GraphConstants.DASH_MASK, scope.getMember("DASH_MASK"));
      assertEquals(GraphConstants.WIDTH_MASK, scope.getMember("WIDTH_MASK"));
   }
}
