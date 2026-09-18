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

package inetsoft.uql.viewsheet.internal;

import inetsoft.report.composition.graph.calc.RunningTotalCalc;
import inetsoft.report.composition.graph.calc.RunningTotalColumn;
import inetsoft.test.*;
import inetsoft.uql.viewsheet.graph.AbstractCalc;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;

// Bug #76797
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
public class VSUtilTest {
   // breakBy defaulting from unset to its first-time ROW_INNER sentinel must not wipe an
   // explicitly-set resetLevel.
   @Test
   void resetLevelIsPreservedWhenBreakByDefaultsToRowInner() {
      RunningTotalCalc calc = new RunningTotalCalc();
      calc.setResetLevel(RunningTotalColumn.YEAR);

      VSUtil.updateCalculateInfo(true, false, false, calc, null);

      assertEquals(AbstractCalc.ROW_INNER, calc.getBreakBy());
      assertEquals(RunningTotalColumn.YEAR, calc.getResetLevel());
   }

   // Same defaulting behavior for the column-inner sentinel.
   @Test
   void resetLevelIsPreservedWhenBreakByDefaultsToColumnInner() {
      RunningTotalCalc calc = new RunningTotalCalc();
      calc.setResetLevel(RunningTotalColumn.YEAR);

      VSUtil.updateCalculateInfo(false, true, false, calc, null);

      assertEquals(AbstractCalc.COLUMN_INNER, calc.getBreakBy());
      assertEquals(RunningTotalColumn.YEAR, calc.getResetLevel());
   }

   // breakBy already explicitly set to the sentinel matching the current shelf shape must
   // continue to short-circuit before the reset-clearing check (pre-existing behavior).
   @Test
   void resetLevelIsPreservedWhenBreakByAlreadyMatchesRowInner() {
      RunningTotalCalc calc = new RunningTotalCalc();
      calc.setBreakBy(AbstractCalc.ROW_INNER);
      calc.setResetLevel(RunningTotalColumn.YEAR);

      VSUtil.updateCalculateInfo(true, false, false, calc, null);

      assertEquals(AbstractCalc.ROW_INNER, calc.getBreakBy());
      assertEquals(RunningTotalColumn.YEAR, calc.getResetLevel());
   }

   @Test
   void resetLevelIsPreservedWhenBreakByAlreadyMatchesColumnInner() {
      RunningTotalCalc calc = new RunningTotalCalc();
      calc.setBreakBy(AbstractCalc.COLUMN_INNER);
      calc.setResetLevel(RunningTotalColumn.YEAR);

      VSUtil.updateCalculateInfo(true, true, false, calc, null);

      assertEquals(AbstractCalc.COLUMN_INNER, calc.getBreakBy());
      assertEquals(RunningTotalColumn.YEAR, calc.getResetLevel());
   }
}
