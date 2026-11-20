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
package inetsoft.uql.rest.datasource.mailchimp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.io.InputStream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Mailchimp endpoint corrections (Bug #81908, ported from style-intelligence Bug #80224).
 */
class MailchimpEndpointsTest {
   @Test
   void memberTagsAndEventsUseTheirOwnPaths() throws Exception {
      assertTrue(endpoint("List Member Tags").path("suffix").asText()
                    .contains("/members/{Subscriber Hash}/tags?"));
      assertTrue(endpoint("List Member Events").path("suffix").asText()
                    .contains("/members/{Subscriber Hash}/events?"));
   }

   @Test
   void templatesUseDateCreatedFilters() throws Exception {
      String suffix = endpoint("Templates").path("suffix").asText();
      assertTrue(suffix.contains("&since_date_created={Since Date Created?}"));
      assertTrue(suffix.contains("&before_date_created={Before Date Created?}"));
      assertFalse(suffix.contains("created_at"));
   }

   @Test
   void automationsUseStartTimeFilters() throws Exception {
      String suffix = endpoint("Automations").path("suffix").asText();
      assertTrue(suffix.contains("&before_start_time={Before Start Time?}"));
      assertTrue(suffix.contains("&since_start_time={Since Start Time?}"));
      assertFalse(suffix.contains("send_time"));
   }

   @Test
   void campaignEcommerceActivityHasNoSinceFilter() throws Exception {
      String suffix = endpoint("Campaign E-commerce Activity").path("suffix").asText();
      assertFalse(suffix.contains("since="));
   }

   @Test
   void listActivityIsPaged() throws Exception {
      assertEquals("true", endpoint("List Activity").path("paged").asText());
   }

   private JsonNode endpoint(String name) throws Exception {
      try(InputStream in = getClass().getResourceAsStream("endpoints.json")) {
         assertNotNull(in);

         for(JsonNode endpoint : new ObjectMapper().readTree(in).path("endpoints")) {
            if(name.equals(endpoint.path("name").asText())) {
               return endpoint;
            }
         }
      }

      fail("Missing endpoint " + name);
      return null;
   }
}
