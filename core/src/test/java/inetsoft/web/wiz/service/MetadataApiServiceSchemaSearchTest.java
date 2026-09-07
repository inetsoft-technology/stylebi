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
package inetsoft.web.wiz.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import inetsoft.sree.security.ResourceAction;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import inetsoft.uql.XDataSource;
import inetsoft.uql.XRepository;
import inetsoft.uql.asset.AssetRepository;
import inetsoft.uql.jdbc.JDBCDataSource;
import inetsoft.web.composer.AssetTreeService;
import inetsoft.web.portal.controller.database.DataSourceService;
import inetsoft.web.wiz.model.DatabaseTableInfo;
import inetsoft.web.wiz.model.DatabaseTableMeta;
import inetsoft.web.wiz.model.DatasourceTablesResponse;
import inetsoft.web.wiz.model.SchemaSearchResponse;
import inetsoft.web.wiz.request.SchemaSearchRequest;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.security.Principal;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

/**
 * Bug #76350 PSD-001: search_schema("category") missed a table literally named "categories" —
 * "categories".contains("category") is false (they diverge at the plural -y -&gt; -ies spelling
 * change), not a datasource-scoping bug. These drive {@code tableNameMatches}/{@code stem}
 * directly, the way {@link MetadataApiServiceStructureTest} drives other package-private
 * extraction helpers, rather than standing up a full {@code XRepository}-backed searchSchema()
 * round trip.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class MetadataApiServiceSchemaSearchTest {
   @Test
   void singularQueryMatchesPluralTableName() {
      assertTrue(MetadataApiService.tableNameMatches("categories", "category"));
   }

   @Test
   void pluralQueryStillMatchesPluralTableName() {
      // The opposite direction was already correct before the fix (plain substring), kept as a
      // regression guard that the new stemmed fallback doesn't disturb it.
      assertTrue(MetadataApiService.tableNameMatches("categories", "categories"));
   }

   @Test
   void esSuffixRequiresASibilantBaseToAvoidOvercorrecting() {
      // A prior candidate for this guard used "wines"/"win" as a plain tableNameMatches() check
      // (a bad choice caught in review: "win" is a literal prefix of "wines", so that pair is
      // already true via plain substring regardless of stemming and proves nothing about the
      // stemmer). The genuine risk is in stem()'s own output: a naive rule that always strips a
      // trailing "es" would reduce "wines" to "win" -- a completely unrelated word (victory,
      // not the plural of wine) -- and that wrong stem, unlike "wine"/"wines" themselves, is not
      // just a truncation of the input, so it is not automatically caught by the plain substring
      // check either time it is reused elsewhere. Requiring the "es" to follow a sibilant sound
      // (box/glass/church-style plurals) keeps "wines" reducing to the correct "wine", not "win".
      assertEquals("wine", MetadataApiService.stem("wines"));
      assertFalse(MetadataApiService.stem("wines").equals(MetadataApiService.stem("win")));
   }

   @Test
   void sibilantEsPluralsStillStem() {
      assertTrue(MetadataApiService.tableNameMatches("boxes", "box"));
      assertTrue(MetadataApiService.tableNameMatches("glasses", "glass"));
   }

   @Test
   void stemHandlesIesEsAndSSuffixes() {
      assertEquals("category", MetadataApiService.stem("categories"));
      assertEquals("box", MetadataApiService.stem("boxes"));
      assertEquals("wine", MetadataApiService.stem("wines"));
      assertEquals("order", MetadataApiService.stem("orders"));
      assertEquals("glass", MetadataApiService.stem("glass"));
   }

   /**
    * Bug #76449 wbs006: searchSchema("", fields=[...]) short-circuited to an empty result
    * before request.getFields() was ever read, so a fields-only search could never find a
    * column that genuinely exists. These drive {@code searchSchema} itself (not just the
    * static helpers above) with {@code getDatabaseTables}/{@code getTableDetails} stubbed out
    * via a spy, since the JDBC metadata-provider plumbing they walk is unrelated to the
    * short-circuit-ordering bug under test.
    */
   private MetadataApiService createSpiedService(DatasourceTablesResponse tablesResponse,
                                                   DatabaseTableMeta tableMeta)
      throws Exception
   {
      XRepository xrepository = mock(XRepository.class);
      when(xrepository.getDataSourceFullNames()).thenReturn(new String[] { "Examples/Orders" });
      when(xrepository.getDataSource("Examples/Orders")).thenReturn(mock(JDBCDataSource.class));

      DataSourceService dataSourceService = mock(DataSourceService.class);
      when(dataSourceService.checkPermission(anyString(), any(ResourceAction.class), any()))
         .thenReturn(true);

      MetadataApiService service = spy(new MetadataApiService(
         xrepository, dataSourceService, mock(AssetRepository.class),
         mock(AssetTreeService.class), new ObjectMapper()));

      doReturn(tablesResponse).when(service).getDatabaseTables(anyString(), any());

      if(tableMeta != null) {
         doReturn(tableMeta).when(service)
            .getTableDetails(anyString(), anyString(), any(), any(), any());
      }

      return service;
   }

   private DatasourceTablesResponse oneTableFixture(String tableName) {
      DatabaseTableInfo table = new DatabaseTableInfo();
      table.setTable(tableName);
      table.setType("TABLE");

      DatasourceTablesResponse response = new DatasourceTablesResponse();
      response.setTables(List.of(table));
      return response;
   }

   private DatabaseTableMeta columnFixture(String columnName) {
      DatabaseTableMeta.ColumnMeta column = new DatabaseTableMeta.ColumnMeta();
      column.setName(columnName);
      column.setType("INTEGER");

      DatabaseTableMeta meta = new DatabaseTableMeta();
      meta.setColumns(List.of(column));
      return meta;
   }

   @Test
   void emptyQueryWithFieldsStillFindsMatchingColumns() throws Exception {
      MetadataApiService service = createSpiedService(
         oneTableFixture("orders"), columnFixture("PRODUCT_ID"));

      SchemaSearchRequest request = new SchemaSearchRequest();
      request.setQuery("");
      request.setFields(List.of("PRODUCT_ID"));

      SchemaSearchResponse response = service.searchSchema(request, mock(Principal.class));

      assertEquals(1, response.getResults().size(),
         "an empty query with a real fields match must not be short-circuited away");
      assertEquals("orders", response.getResults().get(0).getTable());
   }

   @Test
   void nonsenseQueryWithFieldsMatchesTheSameAsEmptyQuery() throws Exception {
      MetadataApiService service = createSpiedService(
         oneTableFixture("orders"), columnFixture("PRODUCT_ID"));

      SchemaSearchRequest request = new SchemaSearchRequest();
      request.setQuery("zzzznonsense");
      request.setFields(List.of("PRODUCT_ID"));

      SchemaSearchResponse response = service.searchSchema(request, mock(Principal.class));

      assertEquals(1, response.getResults().size());
      assertEquals("orders", response.getResults().get(0).getTable());
   }

   @Test
   void emptyQueryAndEmptyFieldsStillShortCircuitsToNoResults() throws Exception {
      MetadataApiService service = createSpiedService(oneTableFixture("orders"), null);

      SchemaSearchRequest request = new SchemaSearchRequest();
      request.setQuery("");
      request.setFields(Collections.emptyList());

      SchemaSearchResponse response = service.searchSchema(request, mock(Principal.class));

      assertTrue(response.getResults().isEmpty(),
         "genuinely empty search criteria (no query, no fields) must still return no results");
   }

   @Test
   void queryAndFieldsTogetherStillMatchOnTableNameAloneWhenNoColumnMatches() throws Exception {
      MetadataApiService service = createSpiedService(
         oneTableFixture("orders"), columnFixture("UNRELATED_COLUMN"));

      SchemaSearchRequest request = new SchemaSearchRequest();
      request.setQuery("order");
      request.setFields(List.of("NOT_A_REAL_COLUMN"));

      SchemaSearchResponse response = service.searchSchema(request, mock(Principal.class));

      assertEquals(1, response.getResults().size(),
         "a table-name match alone (query 'order' vs table 'orders') must still be included " +
         "when a fields list is also supplied but contributes no column match -- the fix's " +
         "queryLower-computation move must not have broken the non-empty-query path");
      assertEquals("orders", response.getResults().get(0).getTable());
   }
}
