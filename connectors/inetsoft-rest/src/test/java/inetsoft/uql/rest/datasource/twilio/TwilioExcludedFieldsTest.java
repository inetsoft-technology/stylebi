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
package inetsoft.uql.rest.datasource.twilio;

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
 * The Twilio endpoints that return an account's auth token ("Account" the datasource's own
 * password, "Accounts" that of every subaccount as well) must not turn it into a column, a cell,
 * a sample row or a shape entry.
 *
 * <p>Goes through the REAL endpoints.json and the real endpoint runner: a hand-built endpoint would
 * pass even if the declaration never reached the runtime.</p>
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
class TwilioExcludedFieldsTest {
   private static final String SECRET = "tok-main-account-secret";
   private static final String SUB_SECRET = "tok-subaccount-secret";

   private static final String ACCOUNT =
      "{\"sid\":\"ACmain\",\"friendly_name\":\"main\",\"status\":\"active\"," +
      "\"auth_token\":\"" + SECRET + "\"}";
   private static final String ACCOUNTS =
      "{\"page\":0,\"accounts\":[" +
      "{\"sid\":\"ACmain\",\"friendly_name\":\"main\",\"auth_token\":\"" + SECRET + "\"}," +
      "{\"sid\":\"ACsub\",\"friendly_name\":\"sub\",\"auth_token\":\"" + SUB_SECRET + "\"}]}";

   @Test
   void accountDropsTokenAndKeepsOtherFields() throws Exception {
      Result r = run("Account", ACCOUNT);
      assertNoSecret(r);
      assertTrue(r.columns.contains("friendly_name"), r.columns.toString());
      assertEquals(1, r.rows);
   }

   @Test
   void accountsDropsEveryAccountsToken() throws Exception {
      Result r = run("Accounts", ACCOUNTS);
      assertNoSecret(r);
      assertTrue(r.columns.stream().anyMatch(c -> c.contains("friendly_name")), r.columns.toString());
      assertEquals(2, r.rows);
   }

   @Test
   void metadataCarriesNoToken() throws Exception {
      for(String[] c : new String[][] { { "Account", ACCOUNT }, { "Accounts", ACCOUNTS } }) {
         String metadata = metadata(c[0], c[1]);
         assertFalse(metadata.contains("auth_token"), "auth_token in metadata: " + metadata);
         assertFalse(metadata.contains(SECRET) || metadata.contains(SUB_SECRET));
         assertTrue(metadata.contains("friendly_name"), "other fields survive: " + metadata);
      }
   }

   @Test
   void declarationsAreLoadedFromTheRealEndpointsFile() {
      Map<String, TwilioEndpoint> map = new TwilioQuery().getEndpointMap();
      assertEquals(List.of("$.auth_token"), map.get("Account").getExcludeJsonPaths());
      assertEquals(List.of("$.accounts[*].auth_token"), map.get("Accounts").getExcludeJsonPaths());
      // an endpoint that returns no credential declares nothing
      assertTrue(map.get("Keys").getExcludeJsonPaths().isEmpty());
   }

   private static void assertNoSecret(Result r) {
      assertFalse(r.columns.stream().anyMatch(c -> c.contains("auth_token")),
                  "auth_token column present: " + r.columns);
      assertFalse(r.cells.stream().anyMatch(v -> v != null &&
                                            (v.toString().contains(SECRET) ||
                                             v.toString().contains(SUB_SECRET))),
                  "a token value reached a cell");
      assertFalse(String.valueOf(r.sample).contains("tok-"), "a token value reached the sample");
      assertFalse(String.valueOf(r.shape).contains("auth_token"), "auth_token reached the shape");
   }

   private static String metadata(String endpoint, String body) throws Exception {
      TwilioQuery query = new TwilioQuery();
      query.setEndpoint(endpoint);
      JsonTransformer transformer = new JsonTransformer();
      Object json = transformer.transform(
         new ByteArrayInputStream(body.getBytes(StandardCharsets.UTF_8)));
      return new EndpointJsonQueryRunner(
         query, q -> new OnePage(json), new LookupService(), transformer).generateMetadata();
   }

   private static Result run(String endpoint, String body) throws Exception {
      TwilioQuery query = new TwilioQuery();
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
