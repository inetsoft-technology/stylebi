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
package inetsoft.uql.rest.datasource.influxdb;

import inetsoft.test.*;
import inetsoft.uql.XTableNode;
import inetsoft.uql.rest.RestJsonQueryRunner;
import inetsoft.uql.rest.json.JsonRestDataIteratorStrategyFactory;
import inetsoft.uql.rest.json.JsonTransformer;
import inetsoft.uql.rest.json.lookup.LookupService;
import inetsoft.uql.rest.pagination.HttpResponseParameterParser;
import inetsoft.uql.rest.pagination.PaginationType;
import inetsoft.uql.tabular.RestParameter;
import inetsoft.uql.tabular.RestParameters;
import inetsoft.util.CoreTool;
import inetsoft.util.Tool;
import inetsoft.util.UserMessage;
import inetsoft.util.credential.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.w3c.dom.Document;

import java.io.PrintWriter;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.*;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Runs the real connector (query, strategy, runner, table) over HTTP against {@link FakeInfluxDB},
 * which answers the way InfluxDB 2.7 does. Covers the three things that went wrong when a list
 * endpoint or an InfluxQL query was read: only the first page of a list was returned, an InfluxQL
 * result had no column names, and an InfluxQL statement that failed looked like an empty table.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class,
                                  InfluxDBConnectorTest.CredentialConfig.class },
                      initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Timeout(60)
class InfluxDBConnectorTest {
   @BeforeEach
   void startServer() throws Exception {
      server = new FakeInfluxDB(130, 45, 1200);
   }

   @AfterEach
   void stopServer() {
      server.close();
   }

   @Test
   void bucketsReadsEveryPageNotOnlyTheFirst() {
      Result result = run(query("Buckets", 0));

      assertEquals(130, result.rows.size());
      assertEquals("bucket0", result.rows.get(0).get(result.col("name")));
      assertEquals("bucket129", result.rows.get(129).get(result.col("name")));
   }

   @Test
   void bucketsAsksForPagesByItemOffsetAtTheLargestPageSize() {
      run(query("Buckets", 0));

      // 100, then 30, then an empty page tells it there are no more
      assertEquals(List.of("limit=100&offset=0", "limit=100&offset=100", "limit=100&offset=130"),
                   pagingParams("/api/v2/buckets"));
   }

   @Test
   void usersAreReadToTheEndEvenThoughTheServerNeverSaysThereIsAnotherPage() {
      Result result = run(query("Users", 0));

      assertEquals(45, result.rows.size());
      assertEquals(List.of("limit=100&offset=0", "limit=100&offset=45"),
                   pagingParams("/api/v2/users"));
   }

   @Test
   void tasksAreReadByFollowingTheNextLinkTheServerSends() {
      Result result = run(query("Tasks", 0));

      assertEquals(1200, result.rows.size());
      // the server ignores an offset on tasks; it must not be sent one
      assertTrue(server.requests().stream().noneMatch(r -> r.contains("offset")), server.requests().toString());
      assertEquals(3, server.requests().stream().filter(r -> r.startsWith("/api/v2/tasks")).count());
   }

   @Test
   void tasksNeedNoUserId() {
      InfluxDBQuery query = query("Tasks", 0);
      Result result = run(query);

      assertEquals(1200, result.rows.size());
      assertFalse(server.requests().get(0).contains("user="), server.requests().get(0));
   }

   @Test
   void rowLimitStopsThePagingAndSaysItStoppedWithRowsLeft() {
      InfluxDBQuery query = query("Buckets", 25);
      query.setRowLimitProbe(true);

      Result result = run(query);

      assertEquals(25, result.rows.size());
      assertTrue(query.isRowLimitReached());
      assertEquals(1, pagingParams("/api/v2/buckets").size());
   }

   @Test
   void rowLimitOnAPageBoundaryStillSaysRowsAreLeft() {
      InfluxDBQuery query = query("Buckets", 100);
      query.setRowLimitProbe(true);

      Result result = run(query);

      assertEquals(100, result.rows.size());
      assertTrue(query.isRowLimitReached());
   }

   @Test
   void rowLimitAboveTheRowsThereAreIsNotReportedAsCut() {
      InfluxDBQuery query = query("Buckets", 500);
      query.setRowLimitProbe(true);

      Result result = run(query);

      assertEquals(130, result.rows.size());
      assertFalse(query.isRowLimitReached());
   }

   @Test
   void switchingFromAPagedEndpointToAnUnpagedOneDropsThePaging() {
      InfluxDBQuery query = query("Buckets", 0);
      assertEquals(PaginationType.OFFSET, query.getPaginationSpec().getType());

      query.setEndpoint("V1 Query");

      assertEquals(PaginationType.NONE, query.getPaginationSpec().getType());
   }

   @Test
   void influxQLResultHasItsColumnNamesAndRows() {
      Result result = run(v1Query("ok", null));

      assertEquals(List.of("time", "usage_user"), result.columns);
      assertEquals(24, result.rows.size());
      assertTrue(result.rows.get(0).get(0).toString().startsWith("2023-11-14 22:00"),
                 String.valueOf(result.rows.get(0).get(0)));
      assertEquals(0.5, ((Number) result.rows.get(0).get(1)).doubleValue());
   }

   @Test
   void influxQLResultIsTheSameWhenTheJsonPathPicksTheRowsOut() {
      Result result = run(v1Query("ok", "$.results[0].series[0].values[*]"));

      assertEquals(List.of("time", "usage_user"), result.columns);
      assertEquals(24, result.rows.size());
   }

   @Test
   void influxQLStatementThatMatchesNothingIsAnEmptyTable() {
      Result result = run(v1Query("empty", null));

      assertEquals(0, result.rows.size());
   }

   @Test
   void influxQLStatementThatFailsIsNotAnEmptyTable() {
      InfluxDBQuery query = v1Query("error", null);
      CoreTool.clearUserMessage();

      run(query);

      String message = userMessage();
      assertTrue(message.contains("database not found: nodb"), message);
   }

   @Test
   void influxQLGroupByReadsEverySeriesWithItsTagsAsColumns() {
      Result result = run(v1Query("group", null));

      assertEquals(List.of("time", "usage_user", "host"), result.columns);
      assertEquals(4, result.rows.size());
      assertEquals("web-1", result.rows.get(0).get(2));
      assertEquals("web-2", result.rows.get(3).get(2));
   }

   @Test
   void influxQLSeriesWithDifferentColumnsKeepTheirValuesUnderTheirOwnColumns() {
      Result result = run(v1Query("measurements", null));

      assertEquals(List.of("time", "usage", "used"), result.columns);
      assertEquals(2, result.rows.size());
      assertEquals(1.5, ((Number) result.rows.get(0).get(1)).doubleValue());
      assertNull(result.rows.get(0).get(2));
      assertNull(result.rows.get(1).get(1));
      assertEquals(7.5, ((Number) result.rows.get(1).get(2)).doubleValue());
   }

   @Test
   void influxQLTagNamedLikeAFieldDoesNotTakeTheFieldsColumn() {
      Result result = run(v1Query("tagclash", null));

      assertEquals(List.of("time", "host", "tag.host"), result.columns);
      assertEquals("field-a", result.rows.get(0).get(1));
      assertEquals("web-1", result.rows.get(0).get(2));
   }

   @Test
   void influxQLSeveralStatementsAreRefusedNotSilentlyCutToTheFirst() {
      CoreTool.clearUserMessage();

      run(v1Query("multi", null));

      String message = userMessage();
      assertTrue(message.contains("one statement"), message);
   }

   @Test
   void influxQLChunkedResponseIsRefusedNotSilentlyCutShort() {
      CoreTool.clearUserMessage();

      run(v1Query("chunked", null));

      String message = userMessage();
      assertTrue(message.contains("chunk"), message);
   }

   @Test
   void otherEndpointsHaveNoColumnPathsAndTheFluxQueryEndpointNeitherDoes() {
      for(String name : List.of("Buckets", "Health", "Query", "V1 Statistics")) {
         InfluxDBQuery query = query(name, 0);
         assertNull(query.getHeadersPath(), name);
         assertNull(query.getRowsPath(), name);
      }

      assertEquals("results[0].series[0].columns", v1Query("ok", null).getHeadersPath());
      assertEquals("results[0].series[0].values", v1Query("ok", null).getRowsPath());
   }

   @Test
   void aQuerySavedBeforeTheFixIsReadWithTheRightPagingAndColumnsWhenLoaded() throws Exception {
      InfluxDBQuery buckets = query("Buckets", 0);
      buckets.getPaginationSpec().setType(PaginationType.NONE);
      InfluxDBQuery v1 = v1Query("ok", null);
      v1.setHeadersPath(null);
      v1.setRowsPath(null);

      InfluxDBQuery loadedBuckets = reload(buckets);
      InfluxDBQuery loadedV1 = reload(v1);

      assertEquals(PaginationType.OFFSET, loadedBuckets.getPaginationSpec().getType());
      assertEquals("results[0].series[0].columns", loadedV1.getHeadersPath());
      assertEquals("results[0].series[0].values", loadedV1.getRowsPath());
   }

   private static InfluxDBQuery reload(InfluxDBQuery query) throws Exception {
      StringWriter xml = new StringWriter();
      PrintWriter writer = new PrintWriter(xml);
      query.writeXML(writer);
      writer.flush();
      Document doc = Tool.parseXML(new StringReader(xml.toString()));
      InfluxDBQuery loaded = new InfluxDBQuery();
      loaded.parseXML(doc.getDocumentElement());
      return loaded;
   }

   private List<String> pagingParams(String path) {
      return server.requests().stream()
         .filter(r -> r.startsWith(path))
         .map(r -> r.substring(r.indexOf('?') + 1))
         .map(q -> Arrays.stream(q.split("&"))
            .filter(p -> p.startsWith("limit=") || p.startsWith("offset="))
            .sorted()
            .collect(Collectors.joining("&")))
         .collect(Collectors.toList());
   }

   /** What the run told the user, which also clears it. */
   private static String userMessage() {
      UserMessage message = CoreTool.getUserMessage();
      return message == null ? "" : message.getMessage();
   }

   private InfluxDBQuery v1Query(String statement, String jsonPath) {
      InfluxDBQuery query = query("V1 Query", 0);
      setParameter(query, "Database Name", "db");
      setParameter(query, "Query Statement", statement);

      if(jsonPath != null) {
         query.setJsonPath(jsonPath);
      }

      return query;
   }

   private InfluxDBQuery query(String endpoint, int maxRows) {
      InfluxDBDataSource dataSource = new InfluxDBDataSource();
      dataSource.setAuthScheme("TOKEN");
      dataSource.setURL(server.url());
      InfluxDBQuery query = new InfluxDBQuery();
      query.setDataSource(dataSource);
      query.setEndpoint(endpoint);
      query.setMaxRows(maxRows);

      switch(endpoint) {
      case "Buckets":
         query.setJsonPath("$.buckets[*]");
         break;
      case "Users":
         query.setJsonPath("$.users[*]");
         break;
      case "Tasks":
         query.setJsonPath("$.tasks[*]");
         break;
      default:
         break;
      }

      return query;
   }

   private static void setParameter(InfluxDBQuery query, String name, String value) {
      RestParameters parameters = query.getParameters();
      RestParameter parameter = parameters.findParameter(name);
      assertNotNull(parameter, name);
      parameter.setValue(value);
      query.setParameters(parameters);
   }

   /**
    * Runs the query on this thread, so what the run tells the user can be read back: the runtime
    * would run it on a thread of its own.
    */
   private static Result run(InfluxDBQuery query) {
      JsonTransformer transformer = new JsonTransformer();
      XTableNode table = new RestJsonQueryRunner(
         query, new JsonRestDataIteratorStrategyFactory(
            transformer, new HttpResponseParameterParser(transformer)),
         new LookupService(), transformer).run();
      return new Result(table);
   }

   private static final class Result {
      /** @param table the result, or null, which is what a run that found no rows returns. */
      Result(XTableNode table) {
         if(table == null) {
            return;
         }

         readColumns(table);

         while(table.next()) {
            // a table of objects only knows its columns once it has a row
            readColumns(table);
            List<Object> row = new ArrayList<>();

            for(int c = 0; c < table.getColCount(); c++) {
               row.add(table.getObject(c));
            }

            rows.add(row);
         }
      }

      private void readColumns(XTableNode table) {
         if(columns.isEmpty()) {
            for(int c = 0; c < table.getColCount(); c++) {
               columns.add(table.getName(c));
            }
         }
      }

      int col(String name) {
         assertTrue(columns.contains(name), name + " in " + columns);
         return columns.indexOf(name);
      }

      final List<String> columns = new ArrayList<>();
      final List<List<Object>> rows = new ArrayList<>();
   }

   @Configuration
   static class CredentialConfig {
      @Bean
      CredentialService credentialService() {
         CredentialService service = mock(CredentialService.class);
         ApiTokenCredential credential = mock(ApiTokenCredential.class);
         when(credential.getApiToken()).thenReturn("test-token");
         when(service.createCredential(any(CredentialType.class))).thenReturn(credential);
         when(service.createCredential(any(CredentialType.class), anyBoolean()))
            .thenReturn(credential);
         return service;
      }
   }

   private FakeInfluxDB server;
}
