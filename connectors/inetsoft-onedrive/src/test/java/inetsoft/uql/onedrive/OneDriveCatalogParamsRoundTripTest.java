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
package inetsoft.uql.onedrive;

import inetsoft.uql.tabular.*;
import inetsoft.util.ConfigurationContext;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.spy;

/**
 * B7 (mandatory, per the charter's own "'it did not throw' is not the assertion"): {@code
 * describeDataset}'s {@code params}, handed unchanged to the REAL {@code
 * TabularQueryContractSupport.applyQueryContract}, builds a table for the TARGET THE ID ACTUALLY
 * NAMES -- proven by round-tripping TWO ids with DIFFERENT column sets and asserting the built
 * query's own columns match EACH one's own target, not merely that neither call threw.
 *
 * <p>Deliberately uses two plain-text CSV fixtures rather than an Excel one: {@code
 * applyQueryContract} only writes bean properties and reads them back (it never itself loads
 * columns), but this test's OWN verification step -- calling {@code query.getColumns()} on a FRESH
 * query built the same way {@code create_worksheet_table} would -- lazily triggers
 * {@code loadColumns()}, and only the CSV path avoids the Excel-specific Spring context
 * {@code OneDriveCatalogExcelTest} needs.
 */
@Tag("core")
class OneDriveCatalogParamsRoundTripTest {
   @BeforeAll
   static void installContext() {
      previous = OneDriveTestSupport.installMockContext();
   }

   @AfterAll
   static void clearContext() {
      OneDriveTestSupport.clearContext(previous);
   }

   private static ConfigurationContext previous;

   private static InputStream readFixture(String file) {
      InputStream input = OneDriveCatalogParamsRoundTripTest.class.getResourceAsStream(file);
      assertNotNull(input, "fixture not found: " + file);
      return input;
   }

   private static OneDriveQuery spyReading(String fixture) {
      OneDriveQuery query = spy(new OneDriveQuery());
      doAnswer(invocation -> readFixture(fixture)).when(query).getFile();
      return query;
   }

   private static Map<String, String> describeParams(String path, String fixture) throws Exception {
      OneDriveDataSource ds = OneDriveTestSupport.fakeDataSource("OneDrive Test");
      TabularDatasetSchema schema =
         OneDriveCatalog.describeDataset(ds, path, d -> spyReading(fixture));
      return schema.params();
   }

   /** Drives the REAL contract layer, matching {@code OneDriveTabularContractTest}'s own helper. */
   private static String apply(OneDriveQuery query, Map<String, Object> queryParams) throws Exception {
      Map<String, PropertyMeta> pmap = TabularUtil.getPropertyMap(query.getClass());
      TabularQuerySchema schema = new TabularSchemaExtractor().extract(query, query.getType());
      return inetsoft.web.wiz.service.TabularQueryContractSupport.applyQueryContract(
         query, pmap, schema, queryParams, "myds");
   }

   @Test
   void twoDifferentIdsRoundTripToTheirOwnDistinctColumnSets() throws Exception {
      Map<String, String> paramsA = describeParams("Test/TestCSV.csv", "TestCSV.csv");
      Map<String, String> paramsB = describeParams("Test/TestCSV2.csv", "TestCSV2.csv");

      // The params key comes from the getter, not a literal -- SKILL rule -- so derive it via
      // TabularUtil.getPropertyMap rather than hardcoding "path".
      String pathKey = TabularUtil.getPropertyMap(OneDriveQuery.class).keySet().stream()
         .filter(k -> k.equalsIgnoreCase("path")).findFirst().orElseThrow();
      assertEquals("path", pathKey);

      OneDriveQuery queryA = spyReading("TestCSV.csv");
      OneDriveQuery queryB = spyReading("TestCSV2.csv");

      apply(queryA, Map.copyOf(paramsA));
      apply(queryB, Map.copyOf(paramsB));

      assertEquals("Test/TestCSV.csv", queryA.getPath());
      assertEquals("Test/TestCSV2.csv", queryB.getPath());

      List<String> columnsA = List.of(queryA.getColumns()).stream()
         .map(ColumnDefinition::getName).toList();
      List<String> columnsB = List.of(queryB.getColumns()).stream()
         .map(ColumnDefinition::getName).toList();

      // The discriminating assertion: NOT "it did not throw," but that each query's OWN columns
      // match ITS OWN target and the two differ from each other.
      assertEquals(List.of("#datatype measurement", "tag", "double", "dateTime:RFC3339"), columnsA);
      assertEquals(List.of("id", "name", "amount"), columnsB);
      assertNotEquals(columnsA, columnsB);
   }
}
