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

package inetsoft.report.script.formula;

import inetsoft.report.lens.DefaultTableLens;
import inetsoft.test.*;
import inetsoft.uql.XTable;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #82397: the cached group lookup used for {@code data['col@group:value']} on a detail
 * (non-grouped) table must keep a null group apart from an empty-string group.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class CachedRowSelectorTest {
   /**
    * Region/Total = (null, 1.0), ("", 2.0), ("a", 4.0).
    */
   private static XTable nullAndEmptyTable() {
      return new DefaultTableLens(new Object[][] {
         { "Region", "Total" },
         { null, 1.0 },
         { "", 2.0 },
         { "a", 4.0 }
      });
   }

   private static List<Object> cells(String spec, XTable table) throws Exception {
      return new ArrayList<>(new NamedCellRange(spec).getCells(table, false));
   }

   @Test
   void emptyStringLiteralSelectsOnlyTheEmptyStringRows() throws Exception {
      assertEquals(List.of(2.0), cells("Total@Region:''", nullAndEmptyTable()));
   }

   @Test
   void nullExpressionSelectsOnlyTheNullRows() throws Exception {
      assertEquals(List.of(1.0), cells("Total@Region:=null", nullAndEmptyTable()));
   }

   @Test
   void otherGroupValuesAreUnchanged() throws Exception {
      assertEquals(List.of(4.0), cells("Total@Region:a", nullAndEmptyTable()));
   }

   @Test
   void nullAndEmptyGroupKeysSelectDifferentRows() {
      XTable table = nullAndEmptyTable();

      assertEquals(Set.of(1), matchingRows(table, null));
      assertEquals(Set.of(2), matchingRows(table, ""));
      assertEquals(Set.of(3), matchingRows(table, "a"));
   }

   /**
    * Select rows the way a {@code $Region} group reference does: a GroupSpec holding the
    * unwrapped cell value, which is null for the null group.
    */
   private static Set<Integer> matchingRows(XTable table, Object groupValue) {
      Map<String, NamedCellRange.GroupSpec> specs = new HashMap<>();
      specs.put("Region", new NamedCellRange.GroupSpec(groupValue));
      CachedRowSelector selector = CachedRowSelector.getSelector(table, specs);
      Set<Integer> rows = new TreeSet<>();

      try {
         selector.prepare(specs);

         for(int r = table.getHeaderRowCount(); table.moreRows(r); r++) {
            if(selector.match(table, r, 1) == RangeProcessor.YES) {
               rows.add(r);
            }
         }
      }
      finally {
         selector.endProcess();
      }

      return rows;
   }
}
