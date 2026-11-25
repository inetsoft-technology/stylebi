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

package inetsoft.report.lens;

import inetsoft.report.filter.CrossTabFilter;
import inetsoft.report.filter.Formula;
import inetsoft.report.filter.SumFormula;
import inetsoft.test.*;
import inetsoft.uql.XTable;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Tag;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
public class MaxRowsTableLens2Test {
   @Test
   public void testSerialize() throws Exception {
      MaxRowsTableLens2 originalTable = new MaxRowsTableLens2(XTableUtil.getDefaultTableLens(), 3);
      XTable deserializedTable = TestSerializeUtils.serializeAndDeserialize(originalTable);
      Assertions.assertEquals(MaxRowsTableLens2.class, deserializedTable.getClass());
   }

   /**
    * A crosstab of two groups of three categories each, spans on the group header column.
    */
   private static CrossTabFilter groupedCrosstab() {
      List<Object[]> rows = new ArrayList<>();
      rows.add(new Object[] { "Group", "Cat", "Col", "V" });

      for(String group : new String[] { "g1", "g2" }) {
         for(int cat = 1; cat <= 3; cat++) {
            rows.add(new Object[] { group, group + "c" + cat, "x", (double) cat });
         }
      }

      CrossTabFilter f = new CrossTabFilter(
         new DefaultTableLens(rows.toArray(new Object[0][])), new int[] { 0, 1 }, new int[] { 2 },
         new int[] { 3 }, new Formula[] { new SumFormula() });
      f.setRowHeaders(List.of("Group", "Cat"));
      f.setColHeaders(List.of("Col"));
      f.setSuppressRowGroupTotal(true, 0);
      f.setSuppressRowGrandTotal(true);
      f.moreRows(Integer.MAX_VALUE);
      return f;
   }

   @Test
   public void aSpanCrossingTheLimitIsShortenedToTheRowsThatAreKept() {
      CrossTabFilter base = groupedCrosstab();
      int header = base.getHeaderRowCount();
      // the limit counts body rows; the group g2 starts after three body rows and is cut to two
      MaxRowsTableLens2 limited = new MaxRowsTableLens2(base, 5);
      limited.moreRows(Integer.MAX_VALUE);
      int r = header + 3;

      Assertions.assertEquals(3, base.getSpan(r, 0).height, "the group spans its three categories");
      Assertions.assertEquals(2, limited.getSpan(r, 0).height, "only two of them are kept");
      Assertions.assertEquals(header + 5, limited.getRowCount());
   }

   @Test
   public void aSpanInsideTheLimitIsUnchangedAndASpanOnTheLastKeptRowHasHeightOne() {
      CrossTabFilter base = groupedCrosstab();
      int header = base.getHeaderRowCount();
      MaxRowsTableLens2 inside = new MaxRowsTableLens2(base, 5);
      inside.moreRows(Integer.MAX_VALUE);
      Assertions.assertEquals(3, inside.getSpan(header, 0).height, "the first group is fully inside");

      MaxRowsTableLens2 oneRow = new MaxRowsTableLens2(base, 4);
      oneRow.moreRows(Integer.MAX_VALUE);
      Assertions.assertEquals(1, oneRow.getSpan(header + 3, 0).height, "one kept row of the group");
   }
}
