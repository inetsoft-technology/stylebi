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

package inetsoft.uql.rest.datasource;

import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.EndpointsTestConfig;
import inetsoft.test.ScriptedHttpHandler;
import inetsoft.test.SreeHome;
import inetsoft.test.SwapperTestConfiguration;
import inetsoft.uql.VariableTable;
import inetsoft.uql.XTableNode;
import inetsoft.uql.rest.json.RestJsonDataSource;
import inetsoft.uql.rest.json.RestJsonQuery;
import inetsoft.uql.rest.pagination.PaginationParamType;
import inetsoft.uql.rest.pagination.PaginationType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Runs the custom REST JSON data source (a URL and settings typed in by the user, rather than a
 * predefined endpoint) with the HTTP responses scripted, and a JSON file as the non-HTTP source.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(
   classes = { BaseTestConfiguration.class, EndpointsTestConfig.class,
               SwapperTestConfiguration.class },
   initializers = ConfigurationContextInitializer.class)
@SreeHome
class CustomRestReplayTest {
   @Test
   void anUnpagedQueryReadsTheSelectedPath() throws Exception {
      ScriptedHttpHandler http = new ScriptedHttpHandler().on("rest.test/items",
         "{\"data\":[{\"id\":1,\"n\":\"a\"},{\"id\":2,\"n\":\"b\"}]}");
      RestJsonQuery query = query("https://rest.test", "/items", "$.data");

      List<Map<String, Object>> rows = run(query, http);

      assertEquals(List.of(1, 2), column(rows, "id"), rows.toString());
      assertEquals(List.of("a", "b"), column(rows, "n"), rows.toString());
      assertEquals(1, http.requests().size(), http.requests().toString());
      assertEquals("https://rest.test/items", http.requests().get(0));
   }

   @Test
   void pageCountPaginationReadsEveryAnnouncedPage() throws Exception {
      ScriptedHttpHandler http = new ScriptedHttpHandler().on("rest.test/items",
         "{\"pages\":3,\"data\":[{\"id\":1}]}", "{\"pages\":3,\"data\":[{\"id\":2}]}",
         "{\"pages\":3,\"data\":[{\"id\":3}]}");
      RestJsonQuery query = query("https://rest.test", "/items", "$.data");
      query.setPaginationType(PaginationType.PAGE_COUNT);
      query.setTotalPagesParamType(PaginationParamType.JSON_PATH);
      query.setTotalPagesParamValue("$.pages");
      query.setPageNumberParamToWriteType(PaginationParamType.QUERY);
      query.setPageNumberParamToWriteValue("page");

      List<Map<String, Object>> rows = run(query, http);

      assertEquals(List.of(1, 2, 3), column(rows, "id"), rows.toString());
      assertEquals(3, http.requests().size(), http.requests().toString());
      assertTrue(http.requests().get(1).contains("page="), http.requests().get(1));
      assertNotEquals(http.requests().get(1), http.requests().get(2));
   }

   @Test
   void aPostQuerySendsItsBody() throws Exception {
      ScriptedHttpHandler http = new ScriptedHttpHandler()
         .on("rest.test/search", "[{\"id\":7}]");
      RestJsonQuery query = query("https://rest.test", "/search", "$");
      query.setRequestType("POST");
      query.setContentType("application/json");
      query.setRequestBody("{\"q\":\"x\"}");

      List<Map<String, Object>> rows = run(query, http);

      assertEquals(List.of(7), column(rows, "id"), rows.toString());
      assertEquals(List.of("{\"q\":\"x\"}"), http.bodies());
   }

   @Test
   void headersAndRowsPathsBuildATableFromAMatrix() throws Exception {
      ScriptedHttpHandler http = new ScriptedHttpHandler().on("rest.test/matrix",
         "{\"cols\":[\"a\",\"b\"],\"rows\":[[1,2],[3,4]]}");
      RestJsonQuery query = query("https://rest.test", "/matrix", "$");
      query.setHeadersPath("cols");
      query.setRowsPath("rows");

      List<Map<String, Object>> rows = run(query, http);

      assertEquals(List.of(1, 3), column(rows, "a"), rows.toString());
      assertEquals(List.of(2, 4), column(rows, "b"), rows.toString());
   }

   @Test
   void aJsonFileIsReadWithoutAnyRequest(@TempDir Path dir) throws Exception {
      Path file = dir.resolve("data.json");
      Files.writeString(file, "{\"data\":[{\"id\":1},{\"id\":2},{\"id\":3}]}",
                        StandardCharsets.UTF_8);
      ScriptedHttpHandler http = new ScriptedHttpHandler();
      RestJsonQuery query = query(file.toUri().toString(), "", "$.data");

      List<Map<String, Object>> rows = run(query, http);

      assertEquals(List.of(1, 2, 3), column(rows, "id"), rows.toString());
      assertTrue(http.requests().isEmpty(), http.requests().toString());
   }

   @Test
   void aMissingJsonFileIsReportedAsAFailedQuery(@TempDir Path dir) throws Exception {
      // Bug #82010, a failed read is thrown instead of returning no rows
      ScriptedHttpHandler http = new ScriptedHttpHandler();
      RestJsonQuery query = query(dir.resolve("missing.json").toUri().toString(), "", "$");

      RuntimeException error = assertThrows(RuntimeException.class,
         () -> new ScriptedHttpHandler.RestJsonReplayRuntime(http)
            .runQuery(query, new VariableTable()));

      assertTrue(String.valueOf(error.getMessage()).contains("missing.json"), error.toString());
   }

   private static RestJsonQuery query(String url, String suffix, String jsonPath) {
      RestJsonDataSource dataSource = new RestJsonDataSource();
      dataSource.setURL(url);
      RestJsonQuery query = new RestJsonQuery();
      query.setDataSource(dataSource);
      query.setSuffix(suffix);
      query.setJsonPath(jsonPath);
      return query;
   }

   private static List<Map<String, Object>> run(RestJsonQuery query, ScriptedHttpHandler http) {
      XTableNode table = new ScriptedHttpHandler.RestJsonReplayRuntime(http)
         .runQuery(query, new VariableTable());
      http.assertNoFailures();
      assertNotNull(table, "the query returned no table; see the log for the error");
      List<Map<String, Object>> rows = new ArrayList<>();

      while(table.next()) {
         Map<String, Object> row = new LinkedHashMap<>();

         for(int i = 0; i < table.getColCount(); i++) {
            row.put(table.getName(i), table.getObject(i));
         }

         rows.add(row);
      }

      return rows;
   }

   private static List<Object> column(List<Map<String, Object>> rows, String name) {
      List<Object> values = new ArrayList<>();
      rows.forEach(r -> values.add(r.get(name)));
      return values;
   }
}
