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
package inetsoft.uql.viewsheet;

import inetsoft.report.composition.graph.calc.PercentCalc;
import inetsoft.test.*;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A percentageBy script/variable result such as "row" or "col" must resolve to the same
 * option the literal "rows"/"columns" names do, instead of falling back to restriction[0].
 */
@Tag("core")
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class, LibManagerTestConfiguration.class, PluginsTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class PercentageByExpressionTest {
   @ParameterizedTest
   @CsvSource({
      "row,2", "rows,2", "ROW,2", "2,2",
      "col,1", "column,1", "columns,1", "1,1"
   })
   void crosstabResolvesRuntimeValue(String runtime, int expected) throws Exception {
      VSCrosstabInfo info = new VSCrosstabInfo();
      info.setPercentageByValue("=expr");
      rvalue(info, "percentageByValue").setRValue(runtime);
      assertEquals(expected, info.getPercentageByOption());
   }

   @ParameterizedTest
   @CsvSource({
      "row,2", "rows,2", "2,2",
      "col,1", "column,1", "columns,1", "1,1",
      "none,0", "0,0"
   })
   void percentCalcResolvesRuntimeValue(String runtime, int expected) throws Exception {
      PercentCalc calc = new PercentCalc();
      calc.setPercentageByValue("=expr");
      rvalue(calc, "percentageByValue").setRValue(runtime);
      assertEquals(expected, calc.getPercentageByOption());
   }

   private static DynamicValue rvalue(Object owner, String field) throws Exception {
      Field f = owner.getClass().getDeclaredField(field);
      f.setAccessible(true);
      return (DynamicValue) f.get(owner);
   }
}
