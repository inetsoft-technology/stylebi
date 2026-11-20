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
package inetsoft.uql.rest.datasource.toggl;

import inetsoft.test.*;
import inetsoft.uql.XTableNode;
import inetsoft.uql.rest.EndpointJsonQueryRunner;
import inetsoft.uql.rest.RestDataIteratorStrategy;
import inetsoft.uql.rest.json.JsonTransformer;
import inetsoft.uql.rest.json.lookup.LookupService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The Toggl endpoints that echo a credential back ("Current User" returns the account's own API
 * token, the one the data source authenticates with; "Workspaces"/"Workspace" a workspace-level
 * token) must not turn it into a column, a cell, a sample row or a shape entry.
 *
 * <p>Goes through the REAL endpoints.json and the real endpoint runner: a hand-built endpoint would
 * pass even if the declaration never reached the runtime.</p>
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
class TogglExcludedFieldsTest {
   private static final String SECRET = "tok-top-level-secret";
   private static final String WS_SECRET = "tok-workspace-secret";

   private static final String ME_WITHOUT_RELATED =
      "{\"id\":7,\"email\":\"a@b.c\",\"api_token\":\"" + SECRET + "\",\"default_workspace_id\":9}";
   private static final String ME_WITH_RELATED =
      "{\"id\":7,\"email\":\"a@b.c\",\"api_token\":\"" + SECRET + "\",\"workspaces\":[" +
      "{\"id\":9,\"name\":\"w\",\"api_token\":\"" + WS_SECRET + "\"}]}";
   private static final String WORKSPACES =
      "[{\"id\":9,\"name\":\"w\",\"api_token\":\"" + WS_SECRET + "\"}," +
      "{\"id\":10,\"name\":\"x\",\"api_token\":\"" + WS_SECRET + "\"}]";
   private static final String WORKSPACE =
      "{\"id\":9,\"name\":\"w\",\"api_token\":\"" + WS_SECRET + "\"}";

   @Test
   void currentUserWithoutRelatedDataKeepsOtherFields() throws Exception {
      Result r = run("Current User", ME_WITHOUT_RELATED);
      assertNoSecret(r);
      assertTrue(r.columns.contains("email"));
      assertFalse(r.columns.isEmpty());
   }

   @Test
   void currentUserWithRelatedDataDropsNestedWorkspaceToken() throws Exception {
      Result r = run("Current User", ME_WITH_RELATED);
      assertNoSecret(r);
      assertTrue(r.columns.stream().anyMatch(c -> c.contains("name")),
                 "nested workspace fields other than the token survive: " + r.columns);
   }

   @Test
   void workspacesArrayDropsEveryRowsToken() throws Exception {
      Result r = run("Workspaces", WORKSPACES);
      assertNoSecret(r);
      assertEquals(2, r.rows);
   }

   @Test
   void workspaceObjectDropsToken() throws Exception {
      Result r = run("Workspace", WORKSPACE);
      assertNoSecret(r);
      assertEquals(1, r.rows);
   }

   @Test
   void currentUserMetadataCarriesNoToken() throws Exception {
      for(String body : List.of(ME_WITHOUT_RELATED, ME_WITH_RELATED)) {
         String metadata = metadata("Current User", body);
         assertFalse(metadata.contains("api_token"), "api_token in metadata: " + metadata);
         assertFalse(metadata.contains(SECRET) || metadata.contains(WS_SECRET));
         assertTrue(metadata.contains("email"), "other fields survive: " + metadata);
      }
   }

   @Test
   void workspacesMetadataCarriesNoToken() throws Exception {
      String metadata = metadata("Workspaces", WORKSPACES);
      assertFalse(metadata.contains("api_token") || metadata.contains(WS_SECRET), metadata);
      assertTrue(metadata.contains("\"name\""), metadata);
   }

   @Test
   void declarationsAreLoadedFromTheRealEndpointsFile() {
      Map<String, TogglEndpoint> map = new TogglQuery().getEndpointMap();
      assertEquals(List.of("$.api_token", "$.workspaces[*].api_token"),
                   map.get("Current User").getExcludeJsonPaths());
      assertEquals(List.of("$[*].api_token"), map.get("Workspaces").getExcludeJsonPaths());
      assertEquals(List.of("$.api_token"), map.get("Workspace").getExcludeJsonPaths());
      // an endpoint that declares nothing is untouched
      assertTrue(map.get("Clients").getExcludeJsonPaths().isEmpty());
   }

   private static void assertNoSecret(Result r) {
      assertFalse(r.columns.stream().anyMatch(c -> c.contains("api_token")),
                  "api_token column present: " + r.columns);
      assertFalse(r.cells.stream().anyMatch(v -> v != null &&
                                            (v.toString().contains(SECRET) ||
                                             v.toString().contains(WS_SECRET))),
                  "a token value reached a cell");
      assertFalse(String.valueOf(r.sample).contains("tok-"), "a token value reached the sample");
      assertFalse(String.valueOf(r.shape).contains("api_token"), "api_token reached the shape");
   }

   private static String metadata(String endpoint, String body) throws Exception {
      TogglQuery query = new TogglQuery();
      query.setEndpoint(endpoint);
      JsonTransformer transformer = new JsonTransformer();
      Object json = transformer.transform(
         new ByteArrayInputStream(body.getBytes(StandardCharsets.UTF_8)));
      return new EndpointJsonQueryRunner(
         query, q -> new OnePage(json), new LookupService(), transformer).generateMetadata();
   }

   private static Result run(String endpoint, String body) throws Exception {
      TogglQuery query = new TogglQuery();
      query.setEndpoint(endpoint);
      query.setSampleRowLimit(10);
      JsonTransformer transformer = new JsonTransformer();
      Object json = transformer.transform(
         new ByteArrayInputStream(body.getBytes(StandardCharsets.UTF_8)));
      // run(), not runStream(): the streamed table is only readable once it has been finished
      XTableNode table = new EndpointJsonQueryRunner(
         query, q -> new OnePage(json), new LookupService(), transformer).run();
      assertNotNull(table, "the query returned no table for " + endpoint);
      Result r = new Result();
      r.sample = query.getSampleRows();
      r.shape = query.getResponseShape();

      while(table.next()) {
         r.rows++;

         // Column names are only known once a row has been read (the table is streamed), so a
         // check taken before the first next() would see none and pass on an empty column list.
         if(r.columns.isEmpty()) {
            for(int i = 0; i < table.getColCount(); i++) {
               r.columns.add(table.getName(i));
            }
         }

         for(int i = 0; i < table.getColCount(); i++) {
            r.cells.add(table.getObject(i));
         }
      }

      assertFalse(r.columns.isEmpty(), "the table must have produced columns for " + endpoint);

      return r;
   }

   private static final class Result {
      final List<String> columns = new ArrayList<>();
      final List<Object> cells = new ArrayList<>();
      int rows;
      Object sample;
      Object shape;
   }

   private static final class OnePage implements RestDataIteratorStrategy<Object> {
      OnePage(Object json) {
         this.json = json;
      }

      @Override
      public boolean hasNext() {
         return !served;
      }

      @Override
      public Object next() {
         served = true;
         return json;
      }

      @Override
      public void close() {
      }

      private final Object json;
      private boolean served;
   }
}
