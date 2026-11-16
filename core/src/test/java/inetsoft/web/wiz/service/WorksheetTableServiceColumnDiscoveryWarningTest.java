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
package inetsoft.web.wiz.service;

import inetsoft.uql.tabular.TabularQuery;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * A table whose columns were found in only the leading rows of a longer source says so; a table
 * whose discovery read everything, or whose connector reports nothing, carries no warning.
 */
@Tag("core")
class WorksheetTableServiceColumnDiscoveryWarningTest {
   @Test
   void cappedDiscoveryNamesTheRowsReadAndHowToWidenThem() {
      List<String> warnings = WorksheetTableService.columnDiscoveryWarnings(queryCappedAt(100));

      assertEquals(1, warnings.size());
      assertTrue(warnings.get(0).contains("first 100 rows"), warnings.get(0));
      assertTrue(warnings.get(0).contains("after row 100"), warnings.get(0));
      assertTrue(warnings.get(0).contains("Set maxRows"), warnings.get(0));
   }

   @Test
   void discoveryAlreadyAtTheCeilingDoesNotSuggestAWiderRead() {
      List<String> warnings = WorksheetTableService.columnDiscoveryWarnings(
         queryCappedAt(TabularQuery.MAX_DISCOVERY_ROWS));

      assertEquals(1, warnings.size());
      assertFalse(warnings.get(0).contains("Set maxRows"), warnings.get(0));
   }

   @Test
   void discoveryThatReadEverythingHasNoWarning() {
      assertNull(WorksheetTableService.columnDiscoveryWarnings(queryCappedAt(0)));
   }

   @Test
   void noQueryHasNoWarning() {
      assertNull(WorksheetTableService.columnDiscoveryWarnings(null));
   }

   private static TabularQuery queryCappedAt(int rows) {
      return new TabularQuery("ColumnDiscoveryWarning") {
         @Override
         public int getColumnDiscoveryCappedAt() {
            return rows;
         }
      };
   }
}
