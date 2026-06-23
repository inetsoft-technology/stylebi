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
package inetsoft.web.wiz.service;

import inetsoft.uql.asset.ColumnRef;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.web.wiz.model.SimpleFieldInfo;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("core")
class WizAutoBindingServiceSelectBindColumnsTest {
   private static ColumnRef col(String name) {
      return new ColumnRef(new AttributeRef(null, name));
   }

   private static ColumnRef aliasedCol(String attribute, String alias) {
      ColumnRef ref = new ColumnRef(new AttributeRef(null, attribute));
      ref.setAlias(alias);
      return ref;
   }

   private static SimpleFieldInfo config(String field) {
      SimpleFieldInfo info = new SimpleFieldInfo();
      info.setField(field);
      return info;
   }

   @Test
   void fieldConfigsFilterBindColumns() {
      List<ColumnRef> columns = List.of(col("actor_name"), col("amount"), col("rental_id"));

      List<ColumnRef> result = WizAutoBindingService.selectBindColumns(
         columns,
         Map.of("actor_name", config("actor_name"), "amount", config("amount")));

      assertEquals(2, result.size());
      assertTrue(result.stream().anyMatch(c -> "actor_name".equals(c.getAttribute())));
      assertTrue(result.stream().anyMatch(c -> "amount".equals(c.getAttribute())));
   }

   @Test
   void unknownFieldConfigThrowsWithAvailableColumns() {
      List<ColumnRef> columns = List.of(col("actor_name"), col("amount"));

      IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
         () -> WizAutoBindingService.selectBindColumns(
            columns, Map.of("actor_nme", config("actor_nme"))));

      assertTrue(e.getMessage().contains("actor_nme"));
      assertTrue(e.getMessage().contains("actor_name"));
      assertTrue(e.getMessage().contains("amount"));
   }

   @Test
   void aggregateAliasColumnMatchedByDisplayName() {
      // An aggregate-output column: getAttribute() returns the underlying base column
      // (growth_pct), while the alias carries the output name (growth_rate). A fieldConfig
      // keyed by the alias must match (Redmine #75420).
      List<ColumnRef> columns = List.of(col("band"), aliasedCol("growth_pct", "growth_rate"));

      List<ColumnRef> result = WizAutoBindingService.selectBindColumns(
         columns, Map.of("band", config("band"), "growth_rate", config("growth_rate")));

      assertEquals(2, result.size());
      assertTrue(result.stream().anyMatch(c -> "growth_rate".equals(c.getDisplayName())));
   }

   @Test
   void aggregateBaseAttributeReportedUnknownWhenAliased() {
      // When a column is aliased, the base attribute name is no longer a valid binding key;
      // the error must list the display name (growth_rate), guiding the caller to it.
      List<ColumnRef> columns = List.of(col("band"), aliasedCol("growth_pct", "growth_rate"));

      IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
         () -> WizAutoBindingService.selectBindColumns(
            columns, Map.of("growth_pct", config("growth_pct"))));

      assertTrue(e.getMessage().contains("growth_pct"));
      assertTrue(e.getMessage().contains("growth_rate"));
   }

   @Test
   void emptyConfigsBindAllColumns() {
      List<ColumnRef> columns = List.of(col("actor_name"), col("amount"));

      List<ColumnRef> result =
         WizAutoBindingService.selectBindColumns(columns, Map.of());

      assertEquals(2, result.size());
   }

   @Test
   void prefixedFieldConfigNormalisedForSqlQueryTable() {
      // sql-query-table columns are bare; caller passes prefixed form "GapQuery.COMPANY_NAME"
      List<ColumnRef> columns = List.of(col("COMPANY_NAME"), col("REVENUE"));

      Map<String, SimpleFieldInfo> configMap = new HashMap<>();
      configMap.put("GapQuery.COMPANY_NAME", config("GapQuery.COMPANY_NAME"));
      configMap.put("GapQuery.REVENUE",      config("GapQuery.REVENUE"));

      List<ColumnRef> result = WizAutoBindingService.selectBindColumns(columns, configMap);

      assertEquals(2, result.size());
      // after normalisation, map keys must be bare names so downstream lookups resolve
      assertTrue(configMap.containsKey("COMPANY_NAME"));
      assertTrue(configMap.containsKey("REVENUE"));
      assertFalse(configMap.containsKey("GapQuery.COMPANY_NAME"));
      assertFalse(configMap.containsKey("GapQuery.REVENUE"));
      // fi.getField() must also be updated so log messages show the bare name
      assertEquals("COMPANY_NAME", configMap.get("COMPANY_NAME").getField());
      assertEquals("REVENUE",      configMap.get("REVENUE").getField());
   }

   @Test
   void prefixNormalisationCollisionFirstEntryWins() {
      // Two prefixed keys strip to the same bare name — first entry wins, second is dropped.
      List<ColumnRef> columns = List.of(col("FOO"));

      Map<String, SimpleFieldInfo> configMap = new HashMap<>();
      SimpleFieldInfo first  = config("TableA.FOO");
      SimpleFieldInfo second = config("TableB.FOO");
      configMap.put("TableA.FOO", first);
      configMap.put("TableB.FOO", second);

      List<ColumnRef> result = WizAutoBindingService.selectBindColumns(columns, configMap);

      assertEquals(1, result.size());
      assertTrue(configMap.containsKey("FOO"));
      assertEquals(1, configMap.size()); // exactly one entry survived
   }

}
