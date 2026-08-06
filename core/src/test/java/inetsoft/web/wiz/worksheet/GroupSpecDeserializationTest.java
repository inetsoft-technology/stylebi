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
package inetsoft.web.wiz.worksheet;

import com.fasterxml.jackson.databind.ObjectMapper;
import inetsoft.web.WebConfig;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifies {@code EditRequest.groups} accepts both the original plain-string wire format
 * and the new {@code {field, dateLevel}} object format (Bug #75952) — old callers of
 * {@code set_group_aggregate} must keep working unchanged.
 */
@Tag("core")
class GroupSpecDeserializationTest {

   private final ObjectMapper mapper = new WebConfig().objectMapper();

   @Test
   void deserializesPlainStringGroup() throws Exception {
      EditRequest req = mapper.readValue(
         "{\"op\": \"set_group_aggregate\", \"table\": \"T\", \"groups\": [\"Department\"]}",
         EditRequest.class);

      assertEquals(1, req.groups().size());
      assertEquals("Department", req.groups().get(0).field());
      assertNull(req.groups().get(0).dateLevel());
   }

   @Test
   void deserializesObjectGroupWithDateLevel() throws Exception {
      EditRequest req = mapper.readValue(
         "{\"op\": \"set_group_aggregate\", \"table\": \"T\", " +
         "\"groups\": [{\"field\": \"Order Date\", \"dateLevel\": \"QUARTER\"}]}",
         EditRequest.class);

      assertEquals(1, req.groups().size());
      assertEquals("Order Date", req.groups().get(0).field());
      assertEquals("QUARTER", req.groups().get(0).dateLevel());
   }

   @Test
   void deserializesMixedStringAndObjectGroups() throws Exception {
      EditRequest req = mapper.readValue(
         "{\"op\": \"set_group_aggregate\", \"table\": \"T\", " +
         "\"groups\": [\"Employee\", {\"field\": \"Order Date\", \"dateLevel\": \"QUARTER\"}]}",
         EditRequest.class);

      assertEquals(2, req.groups().size());
      assertEquals("Employee", req.groups().get(0).field());
      assertNull(req.groups().get(0).dateLevel());
      assertEquals("Order Date", req.groups().get(1).field());
      assertEquals("QUARTER", req.groups().get(1).dateLevel());
   }
}
