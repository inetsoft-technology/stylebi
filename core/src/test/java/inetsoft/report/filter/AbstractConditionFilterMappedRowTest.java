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
package inetsoft.report.filter;

import inetsoft.report.lens.DefaultTableLens;
import inetsoft.test.*;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.function.IntPredicate;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #76960: the monitor-free {@code isRowMapped} fast path answers only from the count a
 * population published (a volatile write after its row map writes), never from the row map's
 * own non-volatile size; and the population loop without a read-ahead minimum is main's loop,
 * even when a reentrant {@code invalidate()} resets the base row inside it. These pin the
 * ordering; they do not try to reproduce a memory-model race.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
public class AbstractConditionFilterMappedRowTest {
   /**
    * Rows the running population has added to the row map are not reported as mapped until
    * the population ends and publishes its count.
    */
   @Test
   public void rowsAreNotMappedUntilThePopulationPublishes() {
      boolean[] seenDuringLoop = new boolean[1];
      Filter[] self = new Filter[1];
      self[0] = new Filter(table(40), r -> {
         if(r == 5) {
            // rows 1-4 are in the row map already, but not published yet
            seenDuringLoop[0] = self[0].isRowMapped(4);
         }

         return true;
      });

      assertTrue(self[0].moreRows(6));
      assertFalse(seenDuringLoop[0], "row 4 was reported mapped before the loop published it");
      assertTrue(self[0].isRowMapped(4));
      assertTrue(self[0].isRowMapped(6));
      assertFalse(self[0].isRowMapped(7));
   }

   /**
    * invalidate() zeroes the published count before it publishes the new row map, so no row,
    * not even a header row of the new map, is reported mapped until it is populated again.
    */
   @Test
   public void invalidateHidesMappedRowsUntilTheNextPopulation() {
      Filter filter = new Filter(table(40), r -> true);

      assertTrue(filter.moreRows(10));
      assertTrue(filter.isRowMapped(10));

      filter.invalidate();

      assertFalse(filter.isRowMapped(0));
      assertFalse(filter.isRowMapped(10));

      assertTrue(filter.moreRows(10));
      assertTrue(filter.isRowMapped(10));
      assertFalse(filter.isRowMapped(11));
   }

   /**
    * Without a read-ahead minimum (every filter off the pool), a same-thread invalidate() that
    * resets the base row inside the population loop must not keep the loop populating past
    * the requested row: main stops as soon as the row is mapped.
    */
   @Test
   public void reentrantInvalidateKeepsThePoolOffLoop() {
      boolean[] dense = new boolean[1];
      Filter[] self = new Filter[1];
      self[0] = new Filter(table(40), r -> {
         if(!dense[0] && r == 21) {
            // the conditions change and the filter is invalidated from inside its own loop
            dense[0] = true;
            self[0].invalidate();
         }

         return dense[0] || r % 10 == 0;
      });

      // sparse: rows 10 and 20 are mapped, the base row is at 21
      assertTrue(self[0].moreRows(2));
      assertEquals(-3 - 1, self[0].getRowCount());

      // dense after the reset: main maps base rows 1-3 and stops with the header and 3 rows
      assertTrue(self[0].moreRows(3));
      assertEquals(-4 - 1, self[0].getRowCount());
   }

   private static DefaultTableLens table(int rows) {
      Object[][] data = new Object[rows + 1][];
      data[0] = new Object[] {"id"};

      for(int i = 1; i <= rows; i++) {
         data[i] = new Object[] {i};
      }

      return new DefaultTableLens(data);
   }

   private static final class Filter extends AbstractConditionFilter {
      Filter(DefaultTableLens table, IntPredicate condition) {
         this.condition = condition;
         setTable(table);
      }

      @Override
      protected boolean checkCondition(int r) {
         return condition.test(r);
      }

      private final IntPredicate condition;
   }
}
