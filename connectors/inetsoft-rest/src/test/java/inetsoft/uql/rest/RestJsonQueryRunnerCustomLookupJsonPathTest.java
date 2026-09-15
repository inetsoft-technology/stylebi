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
package inetsoft.uql.rest;

import inetsoft.uql.rest.json.JsonTransformer;
import inetsoft.uql.rest.json.RestJsonQuery;
import inetsoft.uql.rest.json.lookup.LookupService;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Regression test for #76690 WBT-004: a customLookups level whose jsonPath resolves to a bare
 * scalar (e.g. "$.id") must fail loud instead of silently leaving the row untouched.
 */
public class RestJsonQueryRunnerCustomLookupJsonPathTest {
   @Test
   void scalarJsonPathThrowsInsteadOfSilentlyNoOpping() {
      final RestJsonQuery query = new RestJsonQuery();
      query.setLookupUrl0("comments?postId={param1}");
      query.setLookupJsonPath0("$.id");
      query.setLookupKey0("id");

      final RestJsonQueryRunner runner = new RestJsonQueryRunner(
         query, null, new LookupService(), new JsonTransformer());
      final Map<String, Object> post = post();

      final IllegalStateException ex = assertThrows(IllegalStateException.class,
         () -> runner.doLookups(query, post));
      assertTrue(ex.getMessage().contains("customLookups[0].jsonPath"));
      assertTrue(ex.getMessage().contains("$.id"));
      assertFalse(post.containsKey("lookup1"));
   }

   @Test
   void rowJsonPathStillAddsLookupProperty() throws Exception {
      final RestJsonQuery query = new RestJsonQuery();
      query.setLookupUrl0("comments?postId={param1}");
      query.setLookupJsonPath0("$");
      query.setLookupKey0("id");

      final RestJsonQueryRunner runner = new RestJsonQueryRunner(
         query, null, new LookupService(), new JsonTransformer());
      final Map<String, Object> post = post();

      runner.doLookups(query, post);

      assertTrue(post.containsKey("lookup1"));
   }

   private static Map<String, Object> post() {
      final Map<String, Object> post = new LinkedHashMap<>();
      post.put("userId", 1);
      post.put("id", 1);
      post.put("title", "title");
      post.put("body", "body");
      return post;
   }
}
