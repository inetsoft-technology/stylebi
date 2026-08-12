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

import com.fasterxml.jackson.databind.JsonMappingException;
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

   @Test
   void deserializesDateOptionAsDateLevelAlias() throws Exception {
      // "dateOption" is add_date_range_column's spelling of this same concept — a highly
      // plausible agent near-miss that must not silently deserialize to an ungrouped date
      // (it would otherwise fall through to the unmapped "dateLevel" key and be dropped).
      EditRequest req = mapper.readValue(
         "{\"op\": \"set_group_aggregate\", \"table\": \"T\", " +
         "\"groups\": [{\"field\": \"Order Date\", \"dateOption\": \"QUARTER\"}]}",
         EditRequest.class);

      assertEquals(1, req.groups().size());
      assertEquals("Order Date", req.groups().get(0).field());
      assertEquals("QUARTER", req.groups().get(0).dateLevel());
   }

   @Test
   void rejectsNonStringNonObjectGroupEntry() {
      // A group entry that is neither a string nor an object (e.g. a bare number or null
      // in the array) must fail loud at the deserializer, not silently become
      // GroupSpec(null, null) and surface later as a confusing "Column not found for
      // group: 'null'".
      assertThrows(JsonMappingException.class, () -> mapper.readValue(
         "{\"op\": \"set_group_aggregate\", \"table\": \"T\", \"groups\": [5]}",
         EditRequest.class));
   }
}
