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

import inetsoft.report.TableLens;
import inetsoft.test.*;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Regression test for bug #76972: {@code AbstractConditionFilter.getBaseRowIndex} must never
 * hand back a guessed value once its bounded retries are exhausted with the row still
 * unmapped. It must either return the row it actually maps to or fail with a clear exception --
 * never silently read past the row map's count and come back with 0, the header row.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
public class AbstractConditionFilterGetBaseRowIndexTest {
   /**
    * moreRows() never grows or completes the row map here, so every one of
    * getBaseRowIndex()'s retries is exhausted while the row stays unmapped -- deterministically
    * simulating what a losing race against invalidate() leaves behind. This must fail loudly
    * instead of reading past the map's count and silently coming back as 0, the header row --
    * and the fallback that decides this must do it without calling moreRows() again, so it
    * cannot invert ConditionFilter2's env-lock-then-monitor order.
    */
   @Test
   public void exhaustedRetriesFailLoudlyInsteadOfGuessing() {
      NeverProgressesFilter filter = new NeverProgressesFilter(XTableUtil.getDefaultTableLens());

      IndexOutOfBoundsException ex =
         assertThrows(IndexOutOfBoundsException.class, () -> filter.getBaseRowIndex(3));
      assertTrue(ex.getMessage().contains("3"),
         "exception should mention the unmapped row: " + ex.getMessage());
      assertTrue(ex.getMessage().contains("1"),
         "exception should mention the row map's actual size: " + ex.getMessage());
      // 1 initial call plus the 3 bounded retries; the fallback snapshot must not add a 5th.
      assertEquals(4, filter.moreRowsCalls,
         "the locked fallback must not call moreRows()");
   }

   /**
    * A condition filter whose {@code moreRows} never delegates to the real population logic,
    * so the row map is stuck at header-only size forever: every retry, and the final fallback
    * snapshot, sees the same under-sized map.
    */
   private static final class NeverProgressesFilter extends AbstractConditionFilter {
      NeverProgressesFilter(TableLens table) {
         setTable(table);
      }

      @Override
      protected boolean checkCondition(int r) {
         return true;
      }

      @Override
      public boolean moreRows(int row) {
         // deliberately does not call super.moreRows(): the row map never grows or completes.
         moreRowsCalls++;
         return false;
      }

      volatile int moreRowsCalls;
   }
}
