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

import inetsoft.test.*;
import inetsoft.uql.XTableNode;
import inetsoft.uql.rest.EndpointJsonQueryRunner;
import inetsoft.uql.rest.RestDataIteratorStrategy;
import inetsoft.uql.rest.datasource.toggl.TogglQuery;
import inetsoft.uql.rest.json.EndpointJsonQuery;
import inetsoft.uql.rest.json.JsonTransformer;
import inetsoft.uql.rest.json.RestJsonQuery;
import inetsoft.uql.rest.json.lookup.LookupService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Mailchimp responses carry hypermedia {@code _links} on the envelope and on every record. They
 * must not become rows or columns, and an envelope whose record array is empty must not become a
 * row of envelope scalars. Uses the real endpoints.json and the real endpoint runner.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
class MailchimpEnvelopeLinksTest {
   private static final String LINKS =
      "[{\"rel\":\"parent\",\"href\":\"h1\"},{\"rel\":\"self\",\"href\":\"h2\"},{\"rel\":\"create\",\"href\":\"h3\"}]";

   private static String feedback(int n) {
      StringBuilder items = new StringBuilder();

      for(int i = 0; i < n; i++) {
         items.append(i > 0 ? "," : "")
            .append("{\"feedback_id\":").append(i + 1).append(",\"message\":\"m").append(i)
            .append("\",\"_links\":").append(LINKS).append("}");
      }

      return "{\"feedback\":[" + items + "],\"campaign_id\":\"c\",\"total_items\":" + n +
         ",\"_links\":" + LINKS + "}";
   }

   @Test
   void emptyEnvelopeYieldsNoColumnsAndNoRows() throws Exception {
      Result r = run(mailchimp("Campaign Feedback List"), q -> feedback(0));
      assertTrue(r.columns.isEmpty(), "columns: " + r.columns);
      assertEquals(0, r.rows);
   }

   @Test
   void oneRecordGivesOneRowWithPrefixedColumns() throws Exception {
      Result r = run(mailchimp("Campaign Feedback List"), q -> feedback(1));
      assertEquals(1, r.rows);
      assertTrue(r.columns.contains("feedback.feedback_id"), r.columns.toString());
      assertNoLinks(r);
   }

   @Test
   void threeRecordsGiveThreeRows() throws Exception {
      Result r = run(mailchimp("Campaign Feedback List"), q -> feedback(3));
      assertEquals(3, r.rows);
      assertTrue(r.columns.contains("feedback.feedback_id"), r.columns.toString());
      assertNoLinks(r);
   }

   @Test
   void singleEntityWithLinksIsOneRow() throws Exception {
      Result r = run(mailchimp("Campaign Feedback List"),
                     q -> "{\"id\":\"x\",\"status\":\"ok\",\"_links\":" + LINKS + "}");
      assertEquals(1, r.rows);
      assertTrue(r.columns.contains("id"), r.columns.toString());
      assertNoLinks(r);
   }

   @Test
   void explicitJsonPathStillWorks() throws Exception {
      MailchimpQuery query = mailchimp("Campaign Feedback List");
      query.setJsonPath("$.feedback[*]");
      Result r = run(query, q -> feedback(2));
      assertEquals(2, r.rows);
      assertNoLinks(r);
   }

   @Test
   void expandedPathStillExpands() throws Exception {
      MailchimpQuery query = mailchimp("Campaign Feedback List");
      query.setExpanded(true);
      query.setExpandTop(false);
      query.setExpandedPath("carts.lines");
      Result r = run(query, q ->
         "{\"carts\":[{\"id\":\"a\",\"lines\":[{\"sku\":1},{\"sku\":2}]}],\"total_items\":1,\"_links\":" +
         LINKS + "}");
      assertEquals(2, r.rows, r.columns.toString());
      assertNoLinks(r);
   }

   @Test
   void mailchimpLookupResponseHasNoLinks() throws Exception {
      MailchimpQuery query = mailchimp("Automations");
      query.setLookupEndpoint0("Automation Emails");
      Result r = run(query, q -> {
         if("Automation Emails".equals(endpointOf(q))) {
            return "{\"emails\":[{\"id\":\"e1\",\"_links\":" + LINKS +
               "}],\"total_items\":1,\"_links\":" + LINKS + "}";
         }

         return "{\"automations\":[{\"id\":\"a1\",\"_links\":" + LINKS +
            "}],\"total_items\":1,\"_links\":" + LINKS + "}";
      });
      assertEquals(1, r.rows);
      assertTrue(r.columns.stream().anyMatch(c -> c.contains("Automation Emails")),
                 r.columns.toString());
      assertNoLinks(r);
   }

   @Test
   void togglLookupIsNotStrippedByParentExcludes() throws Exception {
      TogglQuery query = new TogglQuery();
      query.setEndpoint("Workspaces");
      query.setLookupEndpoint0("All Users");
      Result r = run(query, q -> {
         if("All Users".equals(endpointOf(q))) {
            return "[{\"id\":1,\"email\":\"u@x\",\"api_token\":\"user-tok\"}]";
         }

         return "[{\"id\":9,\"name\":\"w\",\"api_token\":\"ws-tok\"}]";
      });
      assertTrue(r.cells.contains("user-tok"), "lookup's own field kept: " + r.cells + r.columns);
      assertFalse(r.cells.contains("ws-tok"), "the parent's token is still removed");
   }

   @Test
   void mailchimpDeclaresRecursiveLinksExclude() {
      assertTrue(mailchimp("Campaign Feedback List").resolveExcludedJsonPaths()
                    .contains("$..['_links']"));
   }

   private static void assertNoLinks(Result r) {
      assertFalse(r.columns.stream().anyMatch(c -> c.contains("_links")), r.columns.toString());
   }

   private static String endpointOf(RestJsonQuery q) {
      return q instanceof EndpointJsonQuery<?> ? ((EndpointJsonQuery<?>) q).getEndpoint() : null;
   }

   private static MailchimpQuery mailchimp(String endpoint) {
      MailchimpQuery query = new MailchimpQuery();
      query.setEndpoint(endpoint);
      return query;
   }

   private static Result run(EndpointJsonQuery<?> query, Function<RestJsonQuery, String> pages)
      throws Exception
   {
      JsonTransformer transformer = new JsonTransformer();
      XTableNode table = new EndpointJsonQueryRunner(
         query, q -> new OnePage(transformer, pages.apply(q)), new LookupService(), transformer)
         .run();
      Result r = new Result();

      if(table == null) {
         return r;
      }

      while(table.next()) {
         r.rows++;

         if(r.columns.isEmpty()) {
            for(int i = 0; i < table.getColCount(); i++) {
               r.columns.add(table.getName(i));
            }
         }

         for(int i = 0; i < table.getColCount(); i++) {
            r.cells.add(table.getObject(i));
         }
      }

      return r;
   }

   private static final class Result {
      final List<String> columns = new ArrayList<>();
      final List<Object> cells = new ArrayList<>();
      int rows;
   }

   private static final class OnePage implements RestDataIteratorStrategy<Object> {
      OnePage(JsonTransformer transformer, String body) {
         try {
            this.json = transformer.transform(
               new ByteArrayInputStream(body.getBytes(StandardCharsets.UTF_8)));
         }
         catch(Exception ex) {
            throw new RuntimeException(ex);
         }
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
