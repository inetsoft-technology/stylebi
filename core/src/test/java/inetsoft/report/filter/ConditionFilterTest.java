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

package inetsoft.report.filter;

import inetsoft.report.internal.Util;
import inetsoft.test.*;
import inetsoft.uql.Condition;
import inetsoft.uql.XTable;
import inetsoft.uql.schema.XSchema;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

@SreeHome
public class ConditionFilterTest {
   @Test
   public void testSerialize() throws Exception {
      final Condition condition = new Condition();
      condition.setOperation(Condition.GREATER_THAN);
      condition.addValue(2);
      condition.setType(XSchema.INTEGER);
      final ConditionGroup conditionGroup = new ConditionGroup();
      conditionGroup.addCondition(1, condition, 0);

      ConditionFilter originalTable = new ConditionFilter(XTableUtil.getDefaultTableLens(),
                                                          conditionGroup);
      Util.printTable(originalTable);
      XTable deserializedTable = TestSerializeUtils.serializeAndDeserialize(originalTable);
      Assertions.assertEquals(ConditionFilter.class, deserializedTable.getClass());
   }
}
