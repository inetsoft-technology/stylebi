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
package inetsoft.uql.util;

import inetsoft.test.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #82230: an object the query declares, and that is null on every row, builds the declared
 * leaf columns instead of one column named after the object.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
class DeclaredObjectColumnsTest {
   @ParameterizedTest
   @ValueSource(booleans = { true, false })
   void allNullObjectBuildsDeclaredLeaves(boolean expanded) {
      BaseJsonTable table = load(expanded, declared("folder", "folder.id", "folder.name"),
                                 row("id", "1", "folder", null), row("id", "2", "folder", null));

      assertEquals(List.of("id", "folder.id", "folder.name"), names(table));
      assertNull(value(table, 0, "folder.id"));
      assertEquals("2", value(table, 1, "id"));
   }

   @ParameterizedTest
   @ValueSource(booleans = { true, false })
   void mixedRowsDropTheStrayObjectColumn(boolean expanded) {
      BaseJsonTable table = load(expanded, declared("folder", "folder.id"),
                                 row("id", "1", "folder", null),
                                 row("id", "2", "folder", Map.of("id", "f9")));

      assertEquals(List.of("id", "folder.id"), names(table));
      assertNull(value(table, 0, "folder.id"));
      assertEquals("f9", value(table, 1, "folder.id"));
   }

   @ParameterizedTest
   @ValueSource(booleans = { true, false })
   void withoutDeclarationsTheObjectColumnStays(boolean expanded) {
      BaseJsonTable table = load(expanded, null, row("id", "1", "folder", null));

      assertEquals(List.of("id", "folder"), names(table));
   }

   @ParameterizedTest
   @ValueSource(booleans = { true, false })
   void nonNullDataIsUnchanged(boolean expanded) {
      BaseJsonTable table = load(expanded, declared("folder", "folder.id", "folder.name"),
                                 row("id", "1", "folder", row("id", "f1", "name", "A")));

      assertEquals(List.of("id", "folder.id", "folder.name"), names(table));
      assertEquals("f1", value(table, 0, "folder.id"));
   }

   @ParameterizedTest
   @ValueSource(booleans = { true, false })
   void nestedObjectsNullAtEitherLevelBuildTheLeaves(boolean expanded) {
      Map<String, List<String>> declared = new LinkedHashMap<>();
      declared.put("a", List.of("a.b.c"));
      declared.put("a.b", List.of("a.b.c"));

      BaseJsonTable outer = load(expanded, declared, row("id", "1", "a", null));
      assertEquals(List.of("id", "a.b.c"), names(outer));

      BaseJsonTable inner = load(expanded, declared, row("id", "1", "a", row("b", null)));
      assertEquals(List.of("id", "a.b.c"), names(inner));
   }

   @Test
   void expandedTableScanOnlyTheFirstTenThousandRows() {
      Object[] rows = new Object[10001];

      for(int i = 0; i < rows.length; i++) {
         rows[i] = i == 10000 ? row("id", "x", "folder", Map.of("id", "late")) :
            row("id", "x", "folder", null);
      }

      BaseJsonTable table = load(true, declared("folder", "folder.id"), rows);

      // the late non-null object is beyond the scan cap, as header discovery already is
      assertEquals(List.of("id", "folder.id"), names(table));
      assertEquals("late", value(table, 10000, "folder.id"));
   }

   private static Map<String, List<String>> declared(String object, String... leaves) {
      return Map.of(object, List.of(leaves));
   }

   private static Map<String, Object> row(Object... kv) {
      Map<String, Object> map = new LinkedHashMap<>();

      for(int i = 0; i < kv.length; i += 2) {
         map.put((String) kv[i], kv[i + 1]);
      }

      return map;
   }

   @SafeVarargs
   private static BaseJsonTable load(boolean expanded, Map<String, List<String>> declared,
                                     Object... rows)
   {
      BaseJsonTable table = expanded ? new ExpandedJsonTable((String) null) : new JsonTable();
      table.setDeclaredObjectColumns(declared);
      table.load(new ArrayList<>(Arrays.asList(rows)));
      return table;
   }

   private static List<String> names(BaseJsonTable table) {
      List<String> names = new ArrayList<>();

      for(int i = 0; i < table.getColCount(); i++) {
         names.add(table.getName(i));
      }

      return names;
   }

   private static Object value(BaseJsonTable table, int row, String column) {
      int col = names(table).indexOf(column);
      table.rewind();

      for(int r = 0; r <= row; r++) {
         assertTrue(table.next());
      }

      return table.getObject(col);
   }
}
