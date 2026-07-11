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
package inetsoft.report.composition.execution;

import inetsoft.report.lens.WindowTableLens;
import inetsoft.test.*;
import inetsoft.uql.ColumnSelection;
import inetsoft.uql.XConstants;
import inetsoft.uql.asset.ColumnRef;
import inetsoft.uql.asset.SortRef;
import inetsoft.uql.asset.WindowExpressionRef;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.erm.DataRef;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link AssetQuery#buildWindowSpecs(ColumnSelection)} — the resolver that
 * translates {@link WindowExpressionRef} columns in a column selection into
 * {@link WindowTableLens.Spec} instances (base-column indices) for the in-memory window
 * routing stage ({@code AssetQuery.getWindowTableLens}).
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class WindowRoutingTest {
   @Test
   void buildsSpecsFromWindowColumnsResolvingIndices() {
      // output columns: [stage(0), amount(1), rn(2 = ROW_NUMBER OVER(PARTITION BY stage ORDER BY amount DESC))]
      ColumnRef stage = new ColumnRef(new AttributeRef(null, "stage"));
      ColumnRef amount = new ColumnRef(new AttributeRef(null, "amount"));
      WindowExpressionRef win = new WindowExpressionRef(
         "ROW_NUMBER", null, 0,
         List.of(new ColumnRef(new AttributeRef(null, "stage"))),
         List.of(desc(new ColumnRef(new AttributeRef(null, "amount")))));
      win.setName("rn");
      ColumnRef rn = new ColumnRef(win);
      rn.setSQL(true);

      ColumnSelection cols = new ColumnSelection();
      cols.addAttribute(stage);
      cols.addAttribute(amount);
      cols.addAttribute(rn);

      WindowTableLens.Spec[] specs = AssetQuery.buildWindowSpecs(cols);

      assertEquals(1, specs.length);
      assertEquals("rn", specs[0].header);
      assertEquals("ROW_NUMBER", specs[0].fn);
      assertArrayEquals(new int[]{0}, specs[0].partCols);   // stage -> col 0
      assertArrayEquals(new int[]{1}, specs[0].orderCols);  // amount -> col 1
      assertArrayEquals(new boolean[]{false}, specs[0].orderAsc);
      assertEquals(-1, specs[0].argCol);                    // ROW_NUMBER has none
   }

   @Test
   void noWindowColumnsYieldsEmptySpecs() {
      ColumnSelection cols = new ColumnSelection();
      cols.addAttribute(new ColumnRef(new AttributeRef(null, "stage")));
      cols.addAttribute(new ColumnRef(new AttributeRef(null, "amount")));

      WindowTableLens.Spec[] specs = AssetQuery.buildWindowSpecs(cols);

      assertEquals(0, specs.length);
   }

   @Test
   void divergentOrderByAcrossSpecsThrows() {
      // Two window columns on the same table with different, non-empty ORDER BY grammars —
      // WindowTableLens shares a single partition/order across all specs in a table (Phase 2),
      // so this must fail loud rather than silently mis-sort the second column.
      ColumnRef stage = new ColumnRef(new AttributeRef(null, "stage"));
      ColumnRef amount = new ColumnRef(new AttributeRef(null, "amount"));
      ColumnRef qty = new ColumnRef(new AttributeRef(null, "qty"));

      WindowExpressionRef win1 = new WindowExpressionRef(
         "ROW_NUMBER", null, 0, List.of(),
         List.of(desc(new ColumnRef(new AttributeRef(null, "amount")))));
      win1.setName("rn1");
      ColumnRef rn1 = new ColumnRef(win1);
      rn1.setSQL(true);

      WindowExpressionRef win2 = new WindowExpressionRef(
         "RANK", null, 0, List.of(),
         List.of(asc(new ColumnRef(new AttributeRef(null, "qty")))));
      win2.setName("rn2");
      ColumnRef rn2 = new ColumnRef(win2);
      rn2.setSQL(true);

      ColumnSelection cols = new ColumnSelection();
      cols.addAttribute(stage);
      cols.addAttribute(amount);
      cols.addAttribute(qty);
      cols.addAttribute(rn1);
      cols.addAttribute(rn2);

      assertThrows(RuntimeException.class, () -> AssetQuery.buildWindowSpecs(cols));
   }

   private static SortRef desc(DataRef ref) {
      SortRef s = new SortRef(ref);
      s.setOrder(XConstants.SORT_DESC);
      return s;
   }

   private static SortRef asc(DataRef ref) {
      SortRef s = new SortRef(ref);
      s.setOrder(XConstants.SORT_ASC);
      return s;
   }
}
