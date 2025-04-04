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

import inetsoft.report.TableLens;
import inetsoft.report.filter.*;
import inetsoft.test.*;
import inetsoft.uql.Condition;
import inetsoft.uql.schema.XSchema;
import org.junit.jupiter.api.Test;

@SreeHome
public class PostProcessorTest {
   @Test
   public void testSerializeConditionFilter2() throws Exception {
      final Condition condition = new Condition();
      condition.setOperation(Condition.GREATER_THAN);
      condition.addValue(2);
      condition.setType(XSchema.INTEGER);
      final ConditionGroup conditionGroup = new ConditionGroup();
      conditionGroup.addCondition(1, condition, 0);

      TableLens originalTable = PostProcessor.filter(XTableUtil.getDefaultTableLens(),
                                                     conditionGroup);
      TestSerializeUtils.serializeAndDeserialize(originalTable);
   }

   @Test
   public void testSerializeTableSummaryFilter2() throws Exception {
      int[] sumCols = new int[]{ 1, 2 };
      Formula[] formulas = new Formula[]{
         new SumFormula(),
         new AverageFormula()
      };
      TableLens originalTable = PostProcessor.tableSummary(XTableUtil.getDefaultTableLens(),
                                                           sumCols, formulas);
      TestSerializeUtils.serializeAndDeserialize(originalTable);
   }
}
