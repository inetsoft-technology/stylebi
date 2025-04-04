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

import inetsoft.report.lens.DefaultTableLens;
import inetsoft.test.*;
import inetsoft.uql.XTable;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.text.DateFormat;
import java.text.SimpleDateFormat;

@SreeHome
public class ChartVSAQueryTest {
   @Test
   public void testSerializeShrinkNumberTableLens() throws Exception {
      ChartVSAQuery.ShrinkNumberTableLens originalTable = new ChartVSAQuery.ShrinkNumberTableLens(
         XTableUtil.getDefaultTableLens());
      originalTable.setShrinkColumn(1, true);
      XTable deserializedTable = TestSerializeUtils.serializeAndDeserialize(originalTable);
      Assertions.assertEquals(ChartVSAQuery.ShrinkNumberTableLens.class, deserializedTable.getClass());
   }

   @Test
   public void testSerializePeriodDateTableLens() throws Exception {
      DefaultTableLens defTable = new DefaultTableLens(new Object[][]{
         { "col1", "col2", "col3" },
         { "a", "2021-01-03", 1 },
         { "a", "2021-01-03", 2 },
         { "b", "2021-01-10", 2.5 },
         { "b", "2021-01-24", 10 },
         { "c", "2021-01-24", 1 }
      });
      DateFormat[] fmts = new DateFormat[3];
      fmts[1] = new SimpleDateFormat("yyyy-MM-dd");
      ChartVSAQuery.PeriodDateTableLens originalTable = new ChartVSAQuery.PeriodDateTableLens(
         defTable, fmts);
      XTable deserializedTable = TestSerializeUtils.serializeAndDeserialize(originalTable);
      Assertions.assertEquals(ChartVSAQuery.PeriodDateTableLens.class, deserializedTable.getClass());
   }
}
