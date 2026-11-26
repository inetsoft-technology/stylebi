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
package inetsoft.uql.datagov;

import inetsoft.test.*;
import inetsoft.uql.VariableTable;
import inetsoft.uql.XTableNode;
import inetsoft.util.ConfigurationContext;
import inetsoft.util.CoreTool;
import inetsoft.util.UserMessage;
import inetsoft.util.credential.*;
import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import javax.json.*;
import java.io.InputStream;
import java.io.StringReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Bug #82392: the connector read rows through {@code /api/views/<id>/rows.json}, which Socrata
 * retires on 2026-12-22 (HTTP 410 during brownouts). Rows now come from SODA
 * {@code /resource/<id>.json}; these cases pin the request shape (paging, row cap, search, one
 * metadata read, no rows.json call), that legacy suffixes keep working, and that the rows come out
 * of {@link DatagovTable} exactly as the rows.json export produced them.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, DatagovSodaTest.TestConfig.class },
                      initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
class DatagovSodaTest {
   @AfterAll
   static void resetContext() {
      ConfigurationContext.getContext().setApplicationContext(null);
   }

   // ----- request parsing -----

   @Test
   void parseLegacyRowsJson_mapsMaxRowsAndSearchAndIgnoresTheRest() throws Exception {
      DatagovSoda.Request r = DatagovSoda.parse("https://data.example.org/api/views/erm2-nwe9/" +
         "rows.json?max_rows=100&search=zz%20qq&accessType=DOWNLOAD&foo=bar");

      assertTrue(r.legacy());
      assertEquals("https://data.example.org", r.origin());
      assertEquals("erm2-nwe9", r.datasetId());
      assertEquals(100, r.limit());
      assertEquals("zz%20qq", r.search(), "kept encoded, as it is sent on");
      assertEquals(List.of(), r.filters(),
         "rows.json ignored unknown parameters, so saved assets that carry them must keep working");
   }

   @Test
   void parseLegacyRowsJson_noQueryMeansNoLimit() throws Exception {
      DatagovSoda.Request r = DatagovSoda.parse("http://h:8080/api/views/erm2-nwe9/rows.json");

      assertEquals("http://h:8080", r.origin());
      assertEquals(DatagovSoda.NO_LIMIT, r.limit());
      assertNull(r.search());
   }

   @Test
   void parseNativeResource_keepsFiltersAndTakesLimitAndOffset() throws Exception {
      DatagovSoda.Request r = DatagovSoda.parse("https://h/resource/erm2-nwe9.json" +
         "?$limit=5&$offset=10&$where=a%3D1&$order=b");

      assertFalse(r.legacy());
      assertEquals(5, r.limit());
      assertEquals(10, r.offset());
      assertEquals(List.of("$where=a%3D1", "$order=b"), r.filters());
   }

   @Test
   void parseNativeResource_selectAndGroupAreRefusedByName() {
      Exception select = assertThrows(Exception.class,
         () -> DatagovSoda.parse("https://h/resource/erm2-nwe9.json?$select=a"));
      assertTrue(select.getMessage().contains("$select"), select.getMessage());

      Exception group = assertThrows(Exception.class,
         () -> DatagovSoda.parse("https://h/resource/erm2-nwe9.json?$group=a"));
      assertTrue(group.getMessage().contains("$group"), group.getMessage());
   }

   @Test
   void parseNativeResource_nonNumericLimitIsRefusedByName() {
      Exception e = assertThrows(Exception.class,
         () -> DatagovSoda.parse("https://h/resource/erm2-nwe9.json?$limit=abc"));
      assertTrue(e.getMessage().contains("$limit"), e.getMessage());
   }

   @Test
   void parseAnythingElse_isNotASocrataDatasetRequest() throws Exception {
      assertNull(DatagovSoda.parse("file:/tmp/x/rows.json"));
      assertNull(DatagovSoda.parse("https://h/some/other.json"));
      assertNull(DatagovSoda.parse("https://h/api/views/erm2-nwe9.json"),
         "the metadata URL is not a rows request");
      assertNull(DatagovSoda.parse("not a url"));
   }

   // ----- fetch: request shape -----

   @Test
   void fetchPagesPastSodasDefaultThousandRowsWithoutReordering() throws Exception {
      try(SodaStub stub = stub()) {
         DatagovDataSource ds = ds(stub.baseUrl());
         JsonObject json = DatagovSoda.fetch(ds,
            DatagovSoda.parse(stub.baseUrl() + "resource/jb7j-dtam.json"), 0, 500);

         assertEquals(1094, json.getJsonArray("data").size(),
            "the dataset holds 1094 rows; SODA alone would stop at 1000");
         List<String> data = dataRequests(stub);
         assertEquals(3, data.size(), data.toString());
         assertTrue(data.get(0).endsWith("&$limit=500&$offset=0"), data.get(0));
         assertTrue(data.get(1).endsWith("&$limit=500&$offset=500"), data.get(1));
         assertTrue(data.get(2).endsWith("&$limit=500&$offset=1000"), data.get(2));

         for(String request : data) {
            assertTrue(request.contains("$select=:*,*"), request);
            assertFalse(request.contains("$order"),
               "an added $order changes which rows come first; SODA's default order is the " +
               "rows.json order: " + request);
         }
      }
   }

   @Test
   void fetchReadsMetadataOnceAndNeverCallsRowsJson() throws Exception {
      try(SodaStub stub = stub()) {
         DatagovSoda.fetch(ds(stub.baseUrl()),
            DatagovSoda.parse(stub.baseUrl() + "api/views/jb7j-dtam/rows.json"), 0,
            DatagovSoda.PAGE_SIZE);

         assertEquals(1, stub.requests().stream()
                         .filter(r -> r.equals("/api/views/jb7j-dtam.json")).count());
         assertTrue(stub.requests().stream().noneMatch(r -> r.contains("rows.json")),
            stub.requests().toString());
         assertEquals(1, dataRequests(stub).size(), "one page of 50000 covers the 1094 rows");
      }
   }

   @Test
   void fetchMapsMaxRowsToTheRowCap() throws Exception {
      try(SodaStub stub = stub()) {
         DatagovDataSource ds = ds(stub.baseUrl());
         JsonObject json = DatagovSoda.fetch(ds, DatagovSoda.parse(
            stub.baseUrl() + "api/views/jb7j-dtam/rows.json?max_rows=5"), 0, 500);

         assertEquals(5, json.getJsonArray("data").size());
         assertEquals(1, dataRequests(stub).size());
         assertTrue(dataRequests(stub).get(0).contains("&$limit=5&"), dataRequests(stub).get(0));
      }
   }

   @Test
   void fetchTakesTheSmallerOfTheUrlCapAndTheQueryCap() throws Exception {
      try(SodaStub stub = stub()) {
         DatagovDataSource ds = ds(stub.baseUrl());
         JsonObject json = DatagovSoda.fetch(ds, DatagovSoda.parse(
            stub.baseUrl() + "resource/jb7j-dtam.json?$limit=50"), 3, 500);

         assertEquals(3, json.getJsonArray("data").size());
         assertTrue(dataRequests(stub).get(0).contains("&$limit=3&"), dataRequests(stub).get(0));
      }
   }

   @Test
   void fetchMaxRowsZeroReturnsTheColumnsAndNoRows() throws Exception {
      try(SodaStub stub = stub()) {
         JsonObject json = DatagovSoda.fetch(ds(stub.baseUrl()), DatagovSoda.parse(
            stub.baseUrl() + "api/views/jb7j-dtam/rows.json?max_rows=0"), 0, 500);

         assertEquals(0, json.getJsonArray("data").size());
         assertEquals(15, json.getJsonObject("meta").getJsonObject("view")
                         .getJsonArray("columns").size());
         assertEquals(List.of(), dataRequests(stub));
      }
   }

   @Test
   void fetchMapsSearchToTheFullTextParameter() throws Exception {
      try(SodaStub stub = stub()) {
         JsonObject json = DatagovSoda.fetch(ds(stub.baseUrl()), DatagovSoda.parse(
            stub.baseUrl() + "api/views/jb7j-dtam/rows.json?search=Septicemia"), 0, 500);

         int rows = json.getJsonArray("data").size();
         assertTrue(rows > 0 && rows < 1094, "rows: " + rows);
         assertTrue(dataRequests(stub).get(0).contains("&$q=Septicemia&"),
            dataRequests(stub).get(0));

         for(JsonValue row : json.getJsonArray("data")) {
            assertTrue(row.toString().contains("Septicemia"), row.toString());
         }
      }
   }

   @Test
   void fetchPassesNativeFiltersThrough() throws Exception {
      try(SodaStub stub = stub()) {
         DatagovSoda.fetch(ds(stub.baseUrl()), DatagovSoda.parse(
            stub.baseUrl() + "resource/jb7j-dtam.json?$where=sex%3D%27F%27&$order=year"), 0, 500);

         String request = dataRequests(stub).get(0);
         assertTrue(request.contains("&$where=sex%3D%27F%27&$order=year&"), request);
      }
   }

   // ----- fetch: rows.json shape -----

   @Test
   void aKeyThatSodaOmitsForNullIsANullCellAndTheColumnStillComesFromMetadata()
      throws Exception
   {
      String view = "{\"columns\":[" +
         "{\"name\":\"A\",\"dataTypeName\":\"text\",\"fieldName\":\"a\"}," +
         "{\"name\":\"B\",\"dataTypeName\":\"number\",\"fieldName\":\"b\"}]}";
      JsonArray rows = SodaStub.array("[{\":id\":\"row-1\",\"a\":\"x\"},{\":id\":\"row-2\"}]");

      try(SodaStub stub = new SodaStub("aaaa-bbbb", view, rows)) {
         JsonObject json = DatagovSoda.fetch(ds(stub.baseUrl()),
            DatagovSoda.parse(stub.baseUrl() + "resource/aaaa-bbbb.json"), 0, 500);

         assertEquals(10, json.getJsonObject("meta").getJsonObject("view")
                         .getJsonArray("columns").size(), "8 system columns plus A and B");
         JsonArray first = json.getJsonArray("data").getJsonArray(0);
         JsonArray second = json.getJsonArray("data").getJsonArray(1);
         assertEquals("x", first.getString(8));
         assertTrue(first.isNull(9), "B has no key in any row, so it is null in every row");
         assertTrue(second.isNull(8));
         assertTrue(second.isNull(9));

         DatagovDataSource ds = ds(stub.baseUrl());
         XTableNode table = new DatagovTable(json, 0, queryFor(ds));
         assertEquals("B", table.getName(9));
         assertEquals(Double.class, table.getType(9),
            "an all-null number column keeps its metadata type");
         assertTrue(table.next());
         assertNull(table.getObject(9));
      }
   }

   @Test
   void systemColumnsAreRebuiltFromSodaSystemFields() throws Exception {
      String view = "{\"columns\":[{\"name\":\"A\",\"dataTypeName\":\"text\",\"fieldName\":\"a\"}]}";
      JsonArray rows = SodaStub.array("[{\":id\":\"row-97wy-rme2_7sk6\"," +
         "\":created_at\":\"2021-06-15T23:34:15.000Z\",\":updated_at\":\"2021-06-16T00:00:00.000Z\"," +
         "\"a\":\"x\"}]");

      try(SodaStub stub = new SodaStub("aaaa-bbbb", view, rows)) {
         JsonObject json = DatagovSoda.fetch(ds(stub.baseUrl()),
            DatagovSoda.parse(stub.baseUrl() + "resource/aaaa-bbbb.json"), 0, 500);
         JsonArray columns = json.getJsonObject("meta").getJsonObject("view")
            .getJsonArray("columns");
         List<String> names = columns.stream()
            .map(c -> c.asJsonObject().getString("name")).toList();
         assertEquals(List.of("sid", "id", "position", "created_at", "created_meta", "updated_at",
                              "updated_meta", "meta", "A"), names);

         JsonArray row = json.getJsonArray("data").getJsonArray(0);
         assertEquals("row-97wy-rme2_7sk6", row.getString(0));
         assertEquals("row-97wy-rme2_7sk6", row.getString(1), "id is filled with the sid");
         assertEquals(0, row.getInt(2));
         assertEquals(1623800055L, row.getJsonNumber(3).longValue());
         assertTrue(row.isNull(4));
         assertEquals(1623801600L, row.getJsonNumber(5).longValue());
         assertTrue(row.isNull(6));
         assertEquals("{ }", row.getString(7));
      }
   }

   @Test
   void valuesSodaEncodesDifferentlyComeBackInTheRowsJsonEncoding() throws Exception {
      String view = "{\"columns\":[" +
         "{\"name\":\"When\",\"dataTypeName\":\"date\",\"fieldName\":\"when\"}," +
         "{\"name\":\"Day\",\"dataTypeName\":\"calendar_date\",\"fieldName\":\"day\"}," +
         "{\"name\":\"Pt\",\"dataTypeName\":\"point\",\"fieldName\":\"pt\"}," +
         "{\"name\":\"Loc\",\"dataTypeName\":\"location\",\"fieldName\":\"loc\"}," +
         "{\"name\":\"Link\",\"dataTypeName\":\"url\",\"fieldName\":\"link\"}," +
         "{\"name\":\"Tel\",\"dataTypeName\":\"phone\",\"fieldName\":\"tel\"}," +
         "{\"name\":\"Ok\",\"dataTypeName\":\"checkbox\",\"fieldName\":\"ok\"}," +
         "{\"name\":\"Amount\",\"dataTypeName\":\"number\",\"fieldName\":\"amount\"}," +
         "{\"name\":\"Area\",\"dataTypeName\":\"multipolygon\",\"fieldName\":\"area\"}]}";
      JsonArray rows = SodaStub.array("[{\":id\":\"row-1\"," +
         "\"when\":\"2020-03-01T00:00:00.000Z\",\"day\":\"2017-06-26T00:00:00.000\"," +
         "\"pt\":{\"type\":\"Point\",\"coordinates\":[-111.73,33.42]}," +
         "\"loc\":{\"latitude\":\"41.85\",\"longitude\":\"-87.69\"}," +
         "\"link\":{\"url\":\"http://a.example/x\",\"description\":\"d\"}," +
         "\"tel\":{\"phone_number\":\"555-0100\",\"phone_type\":\"Home\"}," +
         "\"ok\":true,\"amount\":\"12.50\"," +
         "\"area\":{\"type\":\"MultiPolygon\",\"coordinates\":" +
         "[[[[0,0],[1,0],[1,1],[0,0]]],[[[5,5],[6,5],[6,6],[5,5]],[[5.5,5.6],[5.7,5.6],[5.7,5.8]]]]}}]");

      try(SodaStub stub = new SodaStub("aaaa-bbbb", view, rows)) {
         DatagovDataSource ds = ds(stub.baseUrl());
         JsonObject json = DatagovSoda.fetch(ds,
            DatagovSoda.parse(stub.baseUrl() + "resource/aaaa-bbbb.json"), 0, 500);
         JsonArray row = json.getJsonArray("data").getJsonArray(0);

         assertEquals(1583020800L, row.getJsonNumber(8).longValue(), "date: epoch seconds");
         assertEquals("2017-06-26T00:00:00", row.getString(9), "calendar_date: no fraction");
         assertEquals("POINT (-111.73 33.42)", row.getString(10));
         assertEquals(JsonValue.ValueType.ARRAY, row.get(11).getValueType());
         assertEquals(JsonValue.ValueType.ARRAY, row.get(12).getValueType());
         assertEquals(JsonValue.ValueType.ARRAY, row.get(13).getValueType());
         assertEquals(JsonValue.TRUE, row.get(14));
         assertEquals("12.50", row.getString(15));
         assertEquals("MULTIPOLYGON (((0 0, 1 0, 1 1, 0 0)), ((5 5, 6 5, 6 6, 5 5), " +
                      "(5.5 5.6, 5.7 5.6, 5.7 5.8)))", row.getString(16));

         XTableNode table = new DatagovTable(json, 0, queryFor(ds));
         assertTrue(table.next());
         assertEquals(new BigDecimal("1583020800"), table.getObject(8));
         assertEquals("(41.85, -87.69)", table.getObject(11));
         assertEquals("http://a.example/x", table.getObject(12));
         assertEquals("555-0100", table.getObject(13));
         assertEquals(Boolean.TRUE, table.getObject(14));
         assertEquals(12.5, (Double) table.getObject(15), 0.0);
      }
   }

   @Test
   void toWktWritesEachGeoJsonGeometryAsText() {
      assertEquals("POINT (1 2)", wkt("{\"type\":\"Point\",\"coordinates\":[1,2]}"));
      assertEquals("MULTIPOINT ((1 2), (3 4))",
         wkt("{\"type\":\"MultiPoint\",\"coordinates\":[[1,2],[3,4]]}"));
      assertEquals("LINESTRING (1 2, 3 4)",
         wkt("{\"type\":\"LineString\",\"coordinates\":[[1,2],[3,4]]}"));
      assertEquals("MULTILINESTRING ((1 2, 3 4), (5 6, 7 8))",
         wkt("{\"type\":\"MultiLineString\",\"coordinates\":[[[1,2],[3,4]],[[5,6],[7,8]]]}"));
      assertEquals("POLYGON ((0 0, 1 0, 1 1, 0 0))",
         wkt("{\"type\":\"Polygon\",\"coordinates\":[[[0,0],[1,0],[1,1],[0,0]]]}"));
   }

   @Test
   void toWktKeepsTheJsonTextOfAGeometryItCannotRead() {
      String odd = "{\"type\":\"Mystery\",\"coordinates\":[1]}";
      assertEquals(Json.createReader(new StringReader(odd)).readObject().toString(), wkt(odd));
   }

   // ----- runQuery -----

   @Test
   void rowsReadFromSodaEqualTheRowsJsonExportCellForCell() throws Exception {
      DatagovDataSource fileDs = new DatagovDataSource();
      fileDs.setURL(getClass().getResource("rows-string-sid.json").toExternalForm());
      DatagovQuery legacyQuery = new DatagovQuery();
      legacyQuery.setDataSource(fileDs);
      XTableNode legacy = new DatagovRuntime().runQuery(legacyQuery, new VariableTable());

      try(SodaStub stub = stub()) {
         DatagovQuery query = new DatagovQuery();
         query.setDataSource(ds(stub.baseUrl()));
         query.setSuffix("/resource/jb7j-dtam.json");
         XTableNode soda = new DatagovRuntime().runQuery(query, new VariableTable());

         assertNotNull(soda);
         assertEquals(legacy.getColCount(), soda.getColCount());

         for(int c = 0; c < legacy.getColCount(); c++) {
            assertEquals(legacy.getName(c), soda.getName(c), "column " + c);
         }

         // sid, created_at, updated_at and the dataset's own columns are the same cells. The
         // fixture's id (a UUID), position (the row ordinal), created_meta/updated_meta and meta
         // are values SODA does not have; they are rebuilt as documented in DatagovSoda.
         int[] compared = { 0, 3, 5, 8, 9, 10, 11, 12, 13, 14 };
         int rows = 0;

         while(legacy.next()) {
            assertTrue(soda.next(), "row " + rows);

            for(int c : compared) {
               assertEquals(legacy.getObject(c), soda.getObject(c),
                  "row " + rows + " column " + legacy.getName(c));
            }

            assertEquals(soda.getObject(0), soda.getObject(1), "id is filled with the sid");
            rows++;
         }

         assertFalse(soda.next());
         assertEquals(1094, rows);

         for(int c : compared) {
            assertEquals(legacy.getType(c), soda.getType(c), legacy.getName(c));
         }
      }
   }

   @Test
   void aSavedRowsJsonSuffixIsServedFromSodaAndIgnoresItsOldParameters() throws Exception {
      try(SodaStub stub = stub()) {
         DatagovQuery query = new DatagovQuery();
         query.setDataSource(ds(stub.baseUrl()));
         query.setSuffix("/api/views/jb7j-dtam/rows.json?max_rows=7&accessType=DOWNLOAD");

         XTableNode table = new DatagovRuntime().runQuery(query, new VariableTable());

         assertEquals(7, countRows(table));
         assertTrue(stub.requests().stream().noneMatch(r -> r.contains("rows.json")),
            stub.requests().toString());
      }
   }

   @Test
   void aDataSourceUrlThatIsItselfARowsJsonLinkIsTranslatedToo() throws Exception {
      try(SodaStub stub = stub()) {
         DatagovQuery query = new DatagovQuery();
         query.setDataSource(ds(stub.baseUrl() + "api/views/jb7j-dtam/rows.json?max_rows=4"));

         XTableNode table = new DatagovRuntime().runQuery(query, new VariableTable());

         assertEquals(4, countRows(table), "an empty suffix leaves the resolved URL the rows.json");
         assertTrue(stub.requests().stream().noneMatch(r -> r.contains("rows.json")));
      }
   }

   @Test
   void anAbsoluteSuffixIsTranslatedToo() throws Exception {
      try(SodaStub stub = stub()) {
         DatagovQuery query = new DatagovQuery();
         query.setDataSource(ds("https://unused.example/"));
         query.setSuffix(stub.baseUrl() + "api/views/jb7j-dtam/rows.json?max_rows=2");

         assertEquals(2, countRows(new DatagovRuntime().runQuery(query, new VariableTable())));
      }
   }

   @Test
   void theQueryRowLimitCapsWhatIsRequested() throws Exception {
      try(SodaStub stub = stub()) {
         DatagovQuery query = new DatagovQuery();
         query.setDataSource(ds(stub.baseUrl()));
         query.setSuffix("/resource/jb7j-dtam.json");
         query.setMaxRows(9);

         assertEquals(9, countRows(new DatagovRuntime().runQuery(query, new VariableTable())));
         assertTrue(dataRequests(stub).get(0).contains("&$limit=9&"), dataRequests(stub).get(0));
      }
   }

   // ----- failures -----

   @Test
   void aRetiredEndpointIsReportedAsGoneNotAsMissing() throws Exception {
      try(SodaStub stub = stub()) {
         stub.failWith("/resource", 410, "{\"code\":\"feature_deprecated\"}");
         String message = failureMessage(stub, "/resource/jb7j-dtam.json");

         assertTrue(message.contains("410") && message.contains("gone"), message);
         assertFalse(message.contains("404"), message);
         assertFalse(message.contains("does not exist"), message);
         assertFalse(message.contains("/resource/<id>.json"),
            "a 410 from /resource/ must not suggest /resource/: " + message);
      }
   }

   @Test
   void aGoneRowsJsonSuggestsTheResourceEndpointAndOtherGoneEndpointsDoNot() {
      String retired = DatagovRuntime.describeFailure("u", new DatagovHttpException(410,
         "https://h/api/views/erm2-nwe9/rows.json?max_rows=3", ""));
      assertTrue(retired.contains("rows.json") && retired.contains("/resource/<id>.json"), retired);

      for(String url : List.of("https://h/resource/erm2-nwe9.json?$limit=3",
                               "https://h/api/views/erm2-nwe9.json", "https://h/other"))
      {
         String message = DatagovRuntime.describeFailure("u",
            new DatagovHttpException(410, url, ""));
         assertTrue(message.contains("gone (HTTP 410)") && message.contains(url), message);
         assertFalse(message.contains("/resource/<id>.json"), message);
      }
   }

   @Test
   void aMissingDatasetIsReportedAsNotFound() throws Exception {
      try(SodaStub stub = stub()) {
         stub.failWith("/api/views", 404, "{\"code\":\"not_found\"}");
         String message = failureMessage(stub, "/resource/jb7j-dtam.json");

         assertTrue(message.contains("404") && message.contains("not found"), message);
      }
   }

   @Test
   void otherStatusesNameTheStatus() {
      assertTrue(DatagovRuntime.describeFailure("u", new DatagovHttpException(503, "u", ""))
                    .contains("503"));
      assertTrue(DatagovRuntime.describeFailure("u", new DatagovHttpException(403, "u", ""))
                    .contains("access denied"));
      assertTrue(DatagovRuntime.describeFailure("u", new DatagovHttpException(410, "u", ""))
                    .contains("gone (HTTP 410)"));
      assertTrue(DatagovRuntime.describeFailure("u", new DatagovHttpException(404, "u", ""))
                    .contains("not found (HTTP 404)"));
   }

   @Test
   void theStatusOfARowsPageFailureIsKeptNotFlattenedToFileNotFound() throws Exception {
      try(SodaStub stub = stub()) {
         stub.failWith("/resource", 410, "");

         DatagovHttpException e = assertThrows(DatagovHttpException.class,
            () -> DatagovSoda.fetch(ds(stub.baseUrl()),
               DatagovSoda.parse(stub.baseUrl() + "resource/jb7j-dtam.json"), 0, 500));
         assertEquals(410, e.getStatus());
      }
   }

   @Test
   void aSelectInTheSuffixFailsWithTheParameterNamed() throws Exception {
      try(SodaStub stub = stub()) {
         String message = failureMessage(stub, "/resource/jb7j-dtam.json?$select=year");
         assertTrue(message.contains("$select"), message);
      }
   }

   // ----- helpers -----

   private String failureMessage(SodaStub stub, String suffix) {
      DatagovQuery query = new DatagovQuery();
      query.setDataSource(ds(stub.baseUrl()));
      query.setSuffix(suffix);
      CoreTool.clearUserMessage();

      try {
         assertNull(new DatagovRuntime().runQuery(query, new VariableTable()));
         UserMessage msg = CoreTool.getUserMessage();
         assertNotNull(msg, "the failure cause must be recorded");
         return msg.getMessage();
      }
      finally {
         CoreTool.clearUserMessage();
      }
   }

   private SodaStub stub() throws Exception {
      return new SodaStub("jb7j-dtam", resource("soda-view.json"),
                          SodaStub.array(resource("soda-rows.json")));
   }

   private String resource(String name) throws Exception {
      try(InputStream in = getClass().getResourceAsStream(name)) {
         return new String(IOUtils.toByteArray(in), StandardCharsets.UTF_8);
      }
   }

   private static List<String> dataRequests(SodaStub stub) {
      return stub.requests().stream().filter(r -> r.startsWith("/resource/")).toList();
   }

   private static DatagovDataSource ds(String url) {
      DatagovDataSource ds = new DatagovDataSource();
      ds.setURL(url);
      return ds;
   }

   private static DatagovQuery queryFor(DatagovDataSource ds) {
      DatagovQuery query = new DatagovQuery();
      query.setDataSource(ds);
      return query;
   }

   private static int countRows(XTableNode table) {
      assertNotNull(table);
      int rows = 0;

      while(table.next()) {
         rows++;
      }

      return rows;
   }

   private static String wkt(String geoJson) {
      return DatagovSoda.toWkt(Json.createReader(new StringReader(geoJson)).readObject());
   }

   @Configuration
   static class TestConfig {
      @Bean
      public CredentialService credentialService() {
         CredentialService credentialService = mock(CredentialService.class);
         when(credentialService.createCredential(CredentialType.PASSWORD))
            .thenReturn(mock(LocalPasswordCredential.class));
         when(credentialService.createCredential(CredentialType.PASSWORD, false))
            .thenReturn(mock(LocalPasswordCredential.class));
         return credentialService;
      }
   }
}
