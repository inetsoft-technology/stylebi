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
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #82374: a column that is null on every row takes the type the source declares, instead of
 * string. Columns with data and explicitly typed columns are unaffected.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
class DeclaredColumnTypesTest {
   @ParameterizedTest
   @ValueSource(booleans = { true, false })
   void allNullDeclaredObjectLeafTakesTheDeclaredType(boolean expanded) {
      BaseJsonTable table = load(expanded, Map.of("oo.active", "boolean", "oo.n", "integer"),
                                 Map.of("oo", List.of("oo.active", "oo.n")),
                                 row("id", "1", "oo", null), row("id", "2", "oo", null));

      assertEquals("boolean", table.getColumnType("oo.active"));
      assertEquals("integer", table.getColumnType("oo.n"));
   }

   @ParameterizedTest
   @ValueSource(booleans = { true, false })
   void allNullScalarTakesTheDeclaredType(boolean expanded) {
      BaseJsonTable table = load(expanded, Map.of("flag", "boolean"), null,
                                 row("id", "1", "flag", null));

      assertEquals("boolean", table.getColumnType("flag"));
   }

   @ParameterizedTest
   @ValueSource(booleans = { true, false })
   void columnWithDataKeepsItsInferredTypeAndSkipsTheSupplier(boolean expanded) {
      AtomicInteger calls = new AtomicInteger();
      BaseJsonTable table = expanded ? new ExpandedJsonTable((String) null) : new JsonTable();
      table.setDeclaredColumnTypes(() -> {
         calls.incrementAndGet();
         return Map.of("name", "boolean");
      });
      table.load(new ArrayList<>(List.of(row("name", "a"), row("name", null))));

      assertEquals("string", table.getColumnType("name"));
      assertEquals(0, calls.get());
   }

   @ParameterizedTest
   @ValueSource(booleans = { true, false })
   void explicitUserTypeWins(boolean expanded) {
      BaseJsonTable table = expanded ? new ExpandedJsonTable((String) null) : new JsonTable();
      table.setColumnType("flag", "string");
      table.setDeclaredColumnTypes(() -> Map.of("flag", "boolean"));
      table.load(new ArrayList<>(List.of(row("flag", null))));

      assertEquals("string", table.getColumnType("flag"));
   }

   @ParameterizedTest
   @ValueSource(booleans = { true, false })
   void supplierFailureLeavesString(boolean expanded) {
      BaseJsonTable table = expanded ? new ExpandedJsonTable((String) null) : new JsonTable();
      table.setDeclaredColumnTypes(() -> {
         throw new IllegalStateException("introspection disabled");
      });
      table.load(new ArrayList<>(List.of(row("flag", null))));

      assertEquals("string", table.getColumnType("flag"));
   }

   @ParameterizedTest
   @ValueSource(booleans = { true, false })
   void withoutASupplierAllNullStaysString(boolean expanded) {
      BaseJsonTable table = load(expanded, null, null, row("flag", null));

      assertEquals("string", table.getColumnType("flag"));
   }

   @Test
   void unknownColumnNameStaysString() {
      BaseJsonTable table = load(true, Map.of("other", "boolean"), null, row("flag", null));

      assertEquals("string", table.getColumnType("flag"));
   }

   private static Map<String, Object> row(Object... kv) {
      Map<String, Object> map = new LinkedHashMap<>();

      for(int i = 0; i < kv.length; i += 2) {
         map.put((String) kv[i], kv[i + 1]);
      }

      return map;
   }

   @SafeVarargs
   private static BaseJsonTable load(boolean expanded, Map<String, String> types,
                                     Map<String, List<String>> declared, Object... rows)
   {
      BaseJsonTable table = expanded ? new ExpandedJsonTable((String) null) : new JsonTable();
      table.setDeclaredObjectColumns(declared);
      table.setDeclaredColumnTypes(types == null ? null : () -> types);
      table.load(new ArrayList<>(Arrays.asList(rows)));
      return table;
   }
}
