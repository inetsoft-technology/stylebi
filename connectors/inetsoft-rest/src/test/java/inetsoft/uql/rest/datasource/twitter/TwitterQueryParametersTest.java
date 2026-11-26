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
package inetsoft.uql.rest.datasource.twitter;

import inetsoft.test.*;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The query parameters of the Twitter endpoints come from their suffix template: a variable that
 * the user left empty is left out, the others are sent URL-encoded.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(
   classes = { BaseTestConfiguration.class, EndpointsTestConfig.class,
               SwapperTestConfiguration.class },
   initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class TwitterQueryParametersTest {
   private static TwitterQuery counts(Map<String, String> parameters) {
      TwitterDataSource dataSource = new TwitterDataSource();
      dataSource.setAccessToken("tw-token");
      dataSource.setRefreshToken("tw-refresh");
      dataSource.setTokenExpiration(System.currentTimeMillis() + Duration.ofHours(1).toMillis());
      return ReplaySupport.query(dataSource, new TwitterQuery(), "Tweet Counts All", "$.data",
                                 parameters);
   }

   private static Map<String, String> queryOf(String url) {
      Map<String, String> map = new LinkedHashMap<>();
      String raw = URI.create(url).getRawQuery();

      if(raw != null) {
         for(String pair : raw.split("&")) {
            int i = pair.indexOf('=');
            map.put(URLDecoder.decode(pair.substring(0, i), StandardCharsets.UTF_8),
                    URLDecoder.decode(pair.substring(i + 1), StandardCharsets.UTF_8));
         }
      }

      return map;
   }

   @Test
   void emptyOptionalParametersAreLeftOut() throws Exception {
      ScriptedHttpHandler http = new ScriptedHttpHandler().on(url -> true, "{\"data\":[]}");
      ReplaySupport.runTable(counts(Map.of("Query", "hello")), http);

      assertEquals("/2/tweets/counts/all", URI.create(http.requests().get(0)).getPath());
      assertEquals(Map.of("query", "hello"), queryOf(http.requests().get(0)));
   }

   @Test
   void valuesWithSpecialCharactersArriveUnchanged() throws Exception {
      String query = "from:a&b c+d=e%20 #tag";
      ScriptedHttpHandler http = new ScriptedHttpHandler().on(url -> true, "{\"data\":[]}");
      ReplaySupport.runTable(counts(Map.of("Query", query, "Granularity", "day",
                                          "Start Time", "2024-01-01")), http);

      Map<String, String> sent = queryOf(http.requests().get(0));
      assertEquals(query, sent.get("query"));
      assertEquals("day", sent.get("granularity"));
      assertEquals("2024-01-01", sent.get("start_time"));
      assertEquals(3, sent.size(), sent.toString());
   }

   @Test
   void everyRequestCarriesTheBearerToken() throws Exception {
      TwitterQuery query = counts(Map.of("Query", "x"));
      assertEquals("Bearer tw-token", ((TwitterDataSource) query.getDataSource())
         .getQueryHttpParameters()[0].getValue());
   }
}
