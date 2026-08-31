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
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Proves {@code WorksheetTableService.buildTabularTable} actually calls
 * {@code TabularQueryContractSupport.applyQueryContract} with {@code tabularSource.queryParams}
 * -- not just that the helper itself is correct in isolation (already covered by
 * {@link TabularQueryContractSupportTest}). A passing unit test that nothing production calls
 * is worse than no test (see {@code 2026-08-19-tabular-table-creation.md} §5.2).
 *
 * <p>{@link FakeNamedConnectorQuery} stands in for a real connector's query class --
 * {@code TabularUtil.createQuery} is mocked statically to return it (its normal path resolves a
 * REGISTERED connector's query via a global {@code Config}/{@code XRepository.getRepository()}
 * lookup, which no fake datasource can satisfy); every other static method on {@code TabularUtil}
 * (notably {@code getPropertyMap}, the actual reflection under test) calls through to the real
 * implementation.</p>
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(
   classes = { BaseTestConfiguration.class, WorksheetTableServiceLookupWiringTest.TestConfig.class },
   initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class WorksheetTableServiceLookupWiringTest {
   /**
    * {@code TabularSchemaExtractor.extract} (now unconditional in {@code buildTabularTable} --
    * its params list order is the topological sort's tie-break) builds a layout via
    * {@code LayoutCreator}, which resolves labels through {@code Config.getConfig()}, a Spring
    * bean {@code BaseTestConfiguration} does not provide. A stub answering no bundle is enough --
    * same as {@code TabularSchemaExtractorTest}'s manual {@code ConfigurationContext} install,
    * done here as a Spring bean instead since this test already runs under a real context.
    */
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

   @Test
   void buildTabularTableRefusesAnUnknownLookupNameViaTheSharedHelper() throws Exception {
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
           "tabularSource": {
             "datasourcePath": "myds",
             "queryParams": { "endpoint": "Repos", "lookupEndpoint0": "Bogus" },
             "maxRows": 100
           }
         }
         """);

      try(MockedStatic<TabularUtil> tabularUtil =
             mockStatic(TabularUtil.class, CALLS_REAL_METHODS))
      {
         tabularUtil.when(() -> TabularUtil.createQuery(eq("myds")))
            .thenReturn(new FakeNamedConnectorQuery());

         WorksheetTableResponse table =
            only(service(xrepository, dataSourceService, securityEngine).createTables(request, USER));

         assertFalse(table.isSuccess());
         // "Issues" is Repos' one real lookup choice -- proves the request actually reached
         // TabularQueryContractSupport's tagsMethod validation for lookupEndpoint0 (only that
         // code path can name it), not just some other, unrelated failure.
         assertTrue(table.getErrorMessage().contains("Issues"),
            "expected the lookup-chain rejection naming Repos' real choices, got: " +
               table.getErrorMessage());
      }
   }

   @Test
   void buildTabularTableAcceptsAKnownLookupNameViaTheSharedHelper() throws Exception {
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
           "tabularSource": {
             "datasourcePath": "myds",
             "queryParams": { "endpoint": "Repos", "lookupEndpoint0": "Issues" },
             "maxRows": 100
           }
         }
         """);

      try(MockedStatic<TabularUtil> tabularUtil =
             mockStatic(TabularUtil.class, CALLS_REAL_METHODS))
      {
         tabularUtil.when(() -> TabularUtil.createQuery(eq("myds")))
            .thenReturn(new FakeNamedConnectorQuery());

         // A valid lookup name passes tagsMethod validation and reaches loadColumnSelection,
         // which fails for the unrelated reason that FakeNamedConnectorQuery has no real
         // HTTP/data execution behind it -- proof the lookup validation itself did NOT reject
         // this request.
         WorksheetTableResponse table =
            only(service(xrepository, dataSourceService, securityEngine).createTables(request, USER));

         assertFalse(table.isSuccess());
         assertFalse(table.getErrorMessage().contains("has no value"),
            "a known lookup name must not be rejected by tagsMethod validation, got: " +
               table.getErrorMessage());
      }
   }
}
