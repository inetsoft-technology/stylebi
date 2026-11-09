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

package inetsoft.sree.schedule;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Testing #79042: every rule of {@code schedule-task-permission-policy.json} parses and evaluates,
 * also the rules only the enterprise e2e matrix uses, and every operation rule names a rule, so a
 * mistake in the file shows up here instead of in the e2e run.
 */
@Tag("core")
class SchedulePermissionPolicyTest {
   @Test
   void everyRuleEvaluatesForEveryCombination() throws Exception {
      JsonNode root = readPolicy();
      SchedulePermissionPolicy policy = SchedulePermissionPolicy.load();
      List<String> names = new ArrayList<>();
      root.get("attributes").fieldNames().forEachRemaining(names::add);
      names.add("share");
      names.add("deleteOnlyByOwner");
      // an emptied file would otherwise pass with nothing checked
      assertFalse(root.get("rules").isEmpty(), "rules");
      assertTrue(names.size() > 2, "attributes");

      for(JsonNode rule : root.get("rules")) {
         String id = rule.get("id").asText();

         for(int bits = 0; bits < 1 << names.size(); bits++) {
            Map<String, Boolean> values = new HashMap<>();

            for(int i = 0; i < names.size(); i++) {
               values.put(names.get(i), (bits & 1 << i) != 0);
            }

            assertDoesNotThrow(() -> policy.allows(id, values), "rule " + id);
         }
      }
   }

   @Test
   void everyOperationRuleNamesARule() throws Exception {
      JsonNode root = readPolicy();
      Set<String> rules = new HashSet<>();
      root.get("rules").forEach(r -> rules.add(r.get("id").asText()));
      assertEquals(root.get("rules").size(), rules.size(), "rule ids are unique");
      assertFalse(root.get("operationRules").isEmpty(), "operationRules");

      root.get("operationRules").fields().forEachRemaining(e -> {
         // operation, operation/variant or operation@entry
         assertTrue(e.getKey().matches("[A-Za-z]+(/[A-Za-z]+|@[A-Za-z]+)?"), e.getKey());
         assertTrue(rules.contains(e.getValue().asText()),
                    e.getKey() + " names the unknown rule " + e.getValue().asText());
      });
   }

   private static JsonNode readPolicy() throws Exception {
      try(InputStream in = SchedulePermissionPolicy.class
         .getResourceAsStream("schedule-task-permission-policy.json"))
      {
         return new ObjectMapper().readTree(Objects.requireNonNull(in, "policy file"));
      }
   }
}
