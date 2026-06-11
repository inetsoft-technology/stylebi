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

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("core")
class WizAutoBindingServiceSelectBindColumnsTest {
   private static ColumnRef col(String name) {
      return new ColumnRef(new AttributeRef(null, name));
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
   void emptyConfigsBindAllColumns() {
      List<ColumnRef> columns = List.of(col("actor_name"), col("amount"));

      List<ColumnRef> result =
         WizAutoBindingService.selectBindColumns(columns, Map.of());

      assertEquals(2, result.size());
   }

}
