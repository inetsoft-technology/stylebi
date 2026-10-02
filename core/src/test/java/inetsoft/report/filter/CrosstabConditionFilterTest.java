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

import inetsoft.report.lens.DefaultTableLens;
import inetsoft.test.*;
import inetsoft.uql.Condition;
import inetsoft.uql.XTable;
import inetsoft.uql.schema.XSchema;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.Tag;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.awt.*;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
public class CrosstabConditionFilterTest {
   @Test
   public void testSerialize() throws Exception {
      final Condition condition = new Condition();
      condition.setOperation(Condition.GREATER_THAN);
      condition.addValue(2);
      condition.setType(XSchema.INTEGER);
      final ConditionGroup conditionGroup = new ConditionGroup();
      conditionGroup.addCondition(1, condition, 0);

      CrosstabConditionFilter originalTable = new CrosstabConditionFilter(XTableUtil.getDefaultTableLens(),
                                                                          conditionGroup);
      XTable deserializedTable = TestSerializeUtils.serializeAndDeserialize(originalTable);
      Assertions.assertEquals(CrosstabConditionFilter.class, deserializedTable.getClass());
   }

   // Bug #76972 follow-up: a vertical span that reaches the last row of the filtered table
   // used to make getSpan() probe one row past the end (getBaseRowIndex(rowCount)), which
   // throws IndexOutOfBoundsException. It must instead report the span without probing past
   // the last row.
   @Test
   public void testGetSpanReachingLastRow() {
      Object[][] data = {
         { "col1", "col2" },
         { "a", 1 },
         { "b", 2 },
         { "c", 3 },
         { "d", 4 }
      };
      DefaultTableLens table = new DefaultTableLens(data);
      // Span starts at row 3 and covers rows 3-4, i.e. it reaches the table's last row (4).
      table.setSpan(3, 0, new Dimension(1, 2));

      // Condition that all data rows satisfy, so the filtered row indices map 1:1 to the
      // base table row indices.
      Condition condition = new Condition();
      condition.setOperation(Condition.GREATER_THAN);
      condition.addValue(0);
      condition.setType(XSchema.INTEGER);
      ConditionGroup conditionGroup = new ConditionGroup();
      conditionGroup.addCondition(1, condition, 0);

      CrosstabConditionFilter filter = new CrosstabConditionFilter(table, conditionGroup);
      // Force the filter to fully populate its row map first (as happens once a caller has
      // asked for the row count), so getRowCount() below returns the real, positive count
      // instead of the "still growing" negative sentinel.
      filter.moreRows(XTable.EOT);

      Dimension span = Assertions.assertDoesNotThrow(() -> filter.getSpan(3, 0));

      Assertions.assertNotNull(span);
      Assertions.assertEquals(1, span.width);
      Assertions.assertEquals(2, span.height);
   }
}
