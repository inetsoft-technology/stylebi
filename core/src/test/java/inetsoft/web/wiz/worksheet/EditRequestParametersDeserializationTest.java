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
 * Verifies {@code add_table}'s {@code endpoint}+{@code parameters} shape round-trips through the
 * real app {@link ObjectMapper} ({@link WebConfig#objectMapper()}, the same one Spring uses for
 * every {@code @RequestBody EditRequest} controller method) -- {@code EditRequest} has no
 * {@code @JsonIgnoreProperties(ignoreUnknown = true)}, so before {@code parameters} was added to
 * the record, a real caller's {@code add_table} request supplying it (the common case: most
 * real endpoints have required parameters) would 400 at deserialization before
 * {@code addTabularTable} ever ran. See {@link GroupSpecDeserializationTest} for the sibling
 * pattern this mirrors.
 */
@Tag("core")
class EditRequestParametersDeserializationTest {

   private final ObjectMapper mapper = new WebConfig().objectMapper();

   @Test
   void deserializesEndpointWithParameters() throws Exception {
      EditRequest req = mapper.readValue("""
         {
           "op": "add_table",
           "table": "issues",
           "datasource": "SaaS/GitHub Prod",
           "endpoint": "Repository Issue Events",
           "parameters": {"owner": "inetsoft-technology", "repo": "stylebi"}
         }
         """, EditRequest.class);

      assertEquals("Repository Issue Events", req.endpoint());
      assertNotNull(req.parameters());
      assertEquals(2, req.parameters().size());
      assertEquals("inetsoft-technology", req.parameters().get("owner"));
      assertEquals("stylebi", req.parameters().get("repo"));
   }

   @Test
   void deserializesEndpointWithoutParameters() throws Exception {
      EditRequest req = mapper.readValue("""
         {
           "op": "add_table",
           "table": "issues",
           "datasource": "SaaS/GitHub Prod",
           "endpoint": "Repository Issue Events"
         }
         """, EditRequest.class);

      assertEquals("Repository Issue Events", req.endpoint());
      assertNull(req.parameters());
   }
}
