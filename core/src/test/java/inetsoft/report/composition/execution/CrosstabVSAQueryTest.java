/*
 * This file is part of StyleBI.
 * Copyright (C) 2025  InetSoft Technology
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

package inetsoft.report.composition.execution;

import inetsoft.report.composition.graph.calc.PercentCalc;
import inetsoft.report.filter.*;
import inetsoft.test.*;
import inetsoft.uql.XTable;
import inetsoft.uql.asset.*;
import inetsoft.uql.erm.DataRef;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.internal.*;

import java.lang.reflect.Method;
import java.util.*;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static inetsoft.report.composition.execution.CrosstabVSAQuerySupport.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.mock;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
public class CrosstabVSAQueryTest {
   @Test
   public void testSerializeCrossTabSortFilter() throws Exception {
      int[] rowh = new int[]{ 0 };
      int[] colh = new int[]{ 1 };
      int[] dcol = new int[]{ 1, 2 };
      Formula[] formulas = new Formula[]{ new SumFormula(), new AverageFormula() };
      CrossTabFilter crossTabFilter = new CrossTabFilter(XTableUtil.getDefaultTableLens(), rowh, colh,
                                                         dcol, formulas);
      CrosstabVSAQuery.CrossTabSortFilter originalTable = new CrosstabVSAQuery.CrossTabSortFilter(
         crossTabFilter, new int[]{ 0 }, new boolean[]{ true });
      XTable deserializedTable = TestSerializeUtils.serializeAndDeserialize(originalTable);
      Assertions.assertEquals(CrosstabVSAQuery.CrossTabSortFilter.class, deserializedTable.getClass());
   }

   // ---- findCol: sort a dimension by the value of a measure ----

   private static int findCol(String name, DataRef[] aggregates) throws Exception {
      CrosstabVSAQuery query = mock(CrosstabVSAQuery.class,
                                    org.mockito.Mockito.CALLS_REAL_METHODS);
      Method method = AbstractCrosstabVSAQuery.class.getDeclaredMethod(
         "findCol", String.class, DataRef[].class);
      method.setAccessible(true);
      return (Integer) method.invoke(query, name, aggregates);
   }

   @Test
   void findColLocatesTheAggregateByFullNameFieldNameOrPlainNameAndMissesOtherwise()
      throws Exception
   {
      VSAggregateRef percent = agg("Qty", AggregateFormula.SUM);
      PercentCalc calc = new PercentCalc();
      calc.setLevel(PercentCalc.GRAND_TOTAL);
      percent.setCalculator(calc);
      VSAggregateRef plain = agg("Price", AggregateFormula.SUM);
      VSAggregateRef other = agg("Day", AggregateFormula.MAX);
      DataRef[] aggregates = { percent, plain, other };

      assertEquals(0, findCol(percent.getFullName(), aggregates), "full name of a calculated measure");
      assertEquals(1, findCol(plain.getName(), aggregates), "plain name still found");
      assertEquals(2, findCol(other.getName(), aggregates));
      assertEquals(-1, findCol(null, aggregates));
      assertEquals(-1, findCol("NoSuchMeasure", aggregates));
      assertEquals(0, findCol(VSUtil.getAggregateField(percent.getFullName(), percent), aggregates),
                   "field name of a calculated measure");
   }
}
