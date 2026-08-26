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
import inetsoft.analytic.composition.ViewsheetService;
import inetsoft.sree.security.ResourceAction;
import inetsoft.sree.security.ResourceType;
import inetsoft.sree.security.SecurityEngine;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import inetsoft.uql.XRepository;
import inetsoft.uql.tabular.TabularDataSource;
import inetsoft.uql.tabular.TabularUtil;
import inetsoft.uql.util.Config;
import inetsoft.web.composer.ws.LayoutGraphService;
import inetsoft.web.composer.ws.joins.InnerJoinService;
import inetsoft.web.portal.controller.database.DataSourceService;
import inetsoft.web.portal.controller.database.QueryManagerService;
import inetsoft.web.wiz.model.WorksheetTableRequest;
import inetsoft.web.wiz.model.WorksheetTableResponse;
import inetsoft.web.wiz.model.WorksheetTablesResponse;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.MockedStatic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.security.Principal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * The revised §4.1 row-cap rule: {@code maxRows} is OPTIONAL. Absent, the column-discovery probe
 * runs under a small {@code HINT_MAX_ROWS} hint and NOTHING is persisted on the query; supplied,
 * it is persisted exactly as before. The load-bearing assertion in every "absent" case here is
 * that {@code query.getMaxRows()} stays at its default -- the only automated guard that a
 * server-invented probe default never becomes a persisted truncation, which silently understates
 * every aggregate in every chart bound to the table (worse than a metered API walked to
 * exhaustion on a render, which is at least visible as cost).
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(
   classes = { BaseTestConfiguration.class, WorksheetTableServiceProbeHintTest.TestConfig.class },
   initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class WorksheetTableServiceProbeHintTest {
   @Configuration
   static class TestConfig {
      @Bean
      public Config config() {
         Config config = mock(Config.class);
         when(config.getResourceBundle(ArgumentMatchers.any())).thenReturn(null);
         return config;
      }
   }

   private static final ObjectMapper MAPPER = new ObjectMapper();
   private static final Principal USER = mock(Principal.class);

   private static WorksheetTableRequest batchOf(String tableJson) throws Exception {
      return MAPPER.readValue("{ \"tables\": [ " + tableJson + " ] }", WorksheetTableRequest.class);
   }

   private static WorksheetTableResponse only(WorksheetTablesResponse response) {
      assertEquals(1, response.getTables().size(), "expected exactly one table result");
      return response.getTables().get(0);
   }

   private WorksheetTableService service(XRepository xrepository,
                                         DataSourceService dataSourceService,
                                         SecurityEngine securityEngine)
   {
      return new WorksheetTableService(mock(ViewsheetService.class), mock(MetadataApiService.class),
         mock(InnerJoinService.class), mock(LayoutGraphService.class),
         mock(QueryManagerService.class), xrepository, new ObjectMapper(), dataSourceService,
         securityEngine);
   }

   /**
    * Build one tabular table from {@code tabularSourceJson} against a fresh
    * {@link FakeNamedConnectorQuery}, and return that same instance for inspection.
    *
    * <p>The build always ends in failure here (the fixture's {@code loadOutputColumns}
    * deliberately produces no columns, per its own doc) -- that failure is expected and
    * ignored; what these tests inspect is the query's state AFTER the attempt, which is set
    * before the empty-column check runs.
    */
   private FakeNamedConnectorQuery build(String tabularSourceJson) throws Exception {
      SecurityEngine securityEngine = mock(SecurityEngine.class);
      DataSourceService dataSourceService = mock(DataSourceService.class);
      XRepository xrepository = mock(XRepository.class);

      when(securityEngine.checkPermission(eq(USER), eq(ResourceType.WORKSHEET), eq("*"),
                                          eq(ResourceAction.ACCESS)))
         .thenReturn(true);
      when(dataSourceService.checkPermission(eq("myds"), eq(ResourceAction.READ), eq(USER)))
         .thenReturn(true);

      TabularDataSource<?> ds = mock(TabularDataSource.class);
      when(xrepository.getDataSource(eq("myds"))).thenReturn(ds);

      WorksheetTableRequest request = batchOf("""
         {
           "tableName": "t1",
           "tableType": "tabular table",
           "tabularSource": %s
         }
         """.formatted(tabularSourceJson));

      FakeNamedConnectorQuery query = new FakeNamedConnectorQuery();

      try(MockedStatic<TabularUtil> tabularUtil =
             mockStatic(TabularUtil.class, CALLS_REAL_METHODS))
      {
         tabularUtil.when(() -> TabularUtil.createQuery(eq("myds"))).thenReturn(query);

         WorksheetTableResponse table =
            only(service(xrepository, dataSourceService, securityEngine).createTables(request, USER));

         assertFalse(table.isSuccess(),
            "the fixture produces no columns by design; a success here means this test's own " +
               "setup is broken, not that the row-cap behavior passed");
      }

      return query;
   }

   @Test
   void noMaxRowsHintsTwentyAndLeavesTheQueryUncapped() throws Exception {
      FakeNamedConnectorQuery query = build("""
         { "datasourcePath": "myds", "queryParams": { "endpoint": "Repos" } }
         """);

      assertEquals("20", query.getCapturedHintMaxRows());
      assertEquals(0, query.getMaxRows(), "absence of maxRows must never become a persisted cap");
   }

   @Test
   void noMaxRowsWithSampleRowsHintsTheLargerOfTheTwo() throws Exception {
      FakeNamedConnectorQuery query = build("""
         { "datasourcePath": "myds", "queryParams": { "endpoint": "Repos" }, "sampleRows": 50 }
         """);

      assertEquals("50", query.getCapturedHintMaxRows());
      assertEquals(0, query.getMaxRows());
   }

   @Test
   void suppliedMaxRowsIsPersistedAndTheProbeHintStaysSmall() throws Exception {
      FakeNamedConnectorQuery query = build("""
         { "datasourcePath": "myds", "queryParams": { "endpoint": "Repos" }, "maxRows": 5000 }
         """);

      assertEquals(5000, query.getMaxRows(), "a caller-supplied cap must be persisted");
      assertEquals("20", query.getCapturedHintMaxRows(),
         "the probe must not pull 5000 rows through a metered API just to resolve columns");
   }

   @Test
   void requestWithoutMaxRowsIsAcceptedOnAnUnpaginatedEndpoint() throws Exception {
      // "Repos" is not "Paged" -- FakeNamedConnectorQuery.isPaged() is false. build() itself
      // already asserts the request reaches the empty-column failure (not an earlier,
      // maxRows-related rejection); this test exists to say so explicitly, pinning the reversal
      // that absence of a cap is legal now.
      build("""
         { "datasourcePath": "myds", "queryParams": { "endpoint": "Repos" } }
         """);
   }

   @Test
   void requestWithoutMaxRowsIsAcceptedOnAPaginatedEndpoint() throws Exception {
      FakeNamedConnectorQuery query = build("""
         { "datasourcePath": "myds", "queryParams": { "endpoint": "Paged" } }
         """);

      assertEquals(0, query.getMaxRows(), "a paginated endpoint with no cap is accepted, not rejected");
   }
}
