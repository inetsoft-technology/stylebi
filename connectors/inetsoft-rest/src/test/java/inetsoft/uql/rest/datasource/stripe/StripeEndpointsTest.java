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
package inetsoft.uql.rest.datasource.stripe;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Stripe removed GET /v1/invoices/upcoming, and the Transfers query string must join every
 * parameter with '&' and keep all of them optional.
 */
class StripeEndpointsTest {
   @Test
   void transfersSuffixJoinsOptionalParametersWithAmpersand() throws Exception {
      JsonNode transfers = null;

      for(JsonNode endpoint : endpoints()) {
         if("Transfers".equals(endpoint.path("name").asText())) {
            transfers = endpoint;
         }
      }

      assertNotNull(transfers);
      assertEquals(
         "/v1/transfers?destination={Destination?}&created={Created?:Unix timestamp}" +
         "&transfer_group={Transfer Group?}", transfers.path("suffix").asText());
   }

   @Test
   void doesNotListRemovedUpcomingInvoiceEndpoints() throws Exception {
      Set<String> names = new HashSet<>();

      for(JsonNode endpoint : endpoints()) {
         names.add(endpoint.path("name").asText());
         assertFalse(endpoint.path("suffix").asText().contains("/invoices/upcoming"),
                     endpoint.path("name").asText());

         for(JsonNode lookup : endpoint.path("lookups")) {
            for(JsonNode target : lookup.path("endpoints")) {
               assertFalse(target.asText().startsWith("Upcoming Invoice"), target.asText());
            }

            assertFalse(lookup.path("endpoint").asText().startsWith("Upcoming Invoice"));
         }
      }

      assertFalse(names.contains("Upcoming Invoice"));
      assertFalse(names.contains("Upcoming Invoice Line Items"));
   }

   private JsonNode endpoints() throws Exception {
      try(InputStream in = getClass().getResourceAsStream("endpoints.json")) {
         assertNotNull(in);
         JsonNode endpoints = new ObjectMapper().readTree(in).path("endpoints");
         assertTrue(endpoints.size() > 0);
         return endpoints;
      }
   }
}
