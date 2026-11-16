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
import inetsoft.uql.XNode;
import inetsoft.uql.XRepository;
import inetsoft.uql.asset.AssetRepository;
import inetsoft.uql.erm.XDataModel;
import inetsoft.uql.jdbc.JDBCDataSource;
import inetsoft.uql.util.DefaultMetaDataProvider;
import inetsoft.util.ThreadContext;
import inetsoft.web.composer.AssetTreeService;
import inetsoft.web.portal.controller.database.DataSourceService;
import inetsoft.web.wiz.model.DatabaseTableInfo;
import inetsoft.web.wiz.model.DatabaseTableMeta;
import inetsoft.web.wiz.model.DatasourceTablesResponse;
import inetsoft.web.wiz.model.SchemaSearchResponse;
import inetsoft.web.wiz.request.SchemaSearchRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.lang.reflect.Constructor;
import java.security.Principal;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * A JDBC data source whose connection blocks or fails must not take the metadata calls down with
 * it: search_schema returns the reachable sources plus a warning naming the skipped one, and
 * get_table_details names the unreachable data source instead of reporting a missing table.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class MetadataApiServiceUnreachableSourceTest {
   private static final String GOOD = "Examples/Orders";
   private static final String DEAD = "b81417-blackhole";

   @BeforeEach
   void shortBudget() {
      System.setProperty(MetadataCallGuard.BUDGET_PROPERTY, "600");
      MetadataCallGuard.reset();
   }

   @AfterEach
   void restoreBudget() {
      System.clearProperty(MetadataCallGuard.BUDGET_PROPERTY);
      MetadataCallGuard.reset();
   }

   private DataSourceService dataSourceService() throws Exception {
      DataSourceService service = mock(DataSourceService.class);
      when(service.checkPermission(anyString(), any(ResourceAction.class), any()))
         .thenReturn(true);
      return service;
   }

   private DatasourceTablesResponse oneTable(String name) {
      DatabaseTableInfo table = new DatabaseTableInfo();
      table.setTable(name);
      table.setType("TABLE");
      DatasourceTablesResponse response = new DatasourceTablesResponse();
      response.setTables(List.of(table));
      return response;
   }

   @Test
   void searchSchemaReturnsReachableSourcesAndWarnsAboutTheBlockedOne() throws Exception {
      XRepository xrepository = mock(XRepository.class);
      // the blocked source comes first: it must not use up the time of the one after it
      when(xrepository.getDataSourceFullNames()).thenReturn(new String[] { DEAD, GOOD });
      when(xrepository.getDataSource(anyString())).thenReturn(mock(JDBCDataSource.class));

      MetadataApiService service = spy(new MetadataApiService(
         xrepository, dataSourceService(), mock(AssetRepository.class),
         mock(AssetTreeService.class), new ObjectMapper()));

      doAnswer(inv -> {
         if(DEAD.equals(inv.getArgument(0))) {
            Thread.sleep(10_000);
         }

         return oneTable("orders");
      }).when(service).getDatabaseTables(anyString(), any(), any(), any(), any());

      SchemaSearchRequest request = new SchemaSearchRequest();
      request.setQuery("order");

      long start = System.nanoTime();
      SchemaSearchResponse response = service.searchSchema(request, mock(Principal.class));
      long elapsedMs = (System.nanoTime() - start) / 1_000_000;

      assertTrue(elapsedMs < 5_000, "returned within the budget, took " + elapsedMs + " ms");
      assertEquals(1, response.getResults().size());
      assertEquals(GOOD, response.getResults().get(0).getDatasource());
      assertNotNull(response.getWarnings());
      assertEquals(1, response.getWarnings().size());
      assertTrue(response.getWarnings().get(0).contains(DEAD), response.getWarnings().get(0));
   }

   @Test
   void searchSchemaHasNoWarningsWhenEverySourceAnswers() throws Exception {
      XRepository xrepository = mock(XRepository.class);
      when(xrepository.getDataSourceFullNames()).thenReturn(new String[] { GOOD });
      when(xrepository.getDataSource(anyString())).thenReturn(mock(JDBCDataSource.class));

      MetadataApiService service = spy(new MetadataApiService(
         xrepository, dataSourceService(), mock(AssetRepository.class),
         mock(AssetTreeService.class), new ObjectMapper()));
      doReturn(oneTable("orders")).when(service)
         .getDatabaseTables(anyString(), any(), any(), any(), any());

      SchemaSearchRequest request = new SchemaSearchRequest();
      request.setQuery("order");
      SchemaSearchResponse response = service.searchSchema(request, mock(Principal.class));

      assertEquals(1, response.getResults().size());
      assertNull(response.getWarnings());
   }

   private MetadataApiService serviceWith(DefaultMetaDataProvider provider) throws Exception {
      XRepository xrepository = mock(XRepository.class);
      DataSourceService dataSourceService = dataSourceService();
      JDBCDataSource dataSource = mock(JDBCDataSource.class);

      when(xrepository.getDataSource(DEAD)).thenReturn(dataSource);
      when(dataSourceService.getDataSource(DEAD)).thenReturn(dataSource);
      when(dataSource.getFullName()).thenReturn(DEAD);
      when(dataSource.getDatabaseTypeString()).thenReturn("");
      when(dataSource.getRuntimeProductName()).thenReturn("UnitTestDatabase");
      when(dataSourceService.getDataModel(DEAD)).thenReturn(mock(XDataModel.class));
      when(dataSourceService.getDefaultMetaDataProvider(eq(dataSource), any()))
         .thenReturn(provider);

      return new MetadataApiService(
         xrepository, dataSourceService, mock(AssetRepository.class),
         mock(AssetTreeService.class), new ObjectMapper());
   }

   @Test
   void getTableDetailsNamesTheDataSourceWhenItsConnectionBlocks() throws Exception {
      DefaultMetaDataProvider provider = mock(DefaultMetaDataProvider.class);
      when(provider.getRootMetaData(anyString())).thenAnswer(inv -> {
         Thread.sleep(10_000);
         return new XNode();
      });

      MetadataApiService service = serviceWith(provider);

      DatasourceUnreachableException ex = assertThrows(DatasourceUnreachableException.class,
         () -> service.getTableDetails(DEAD, "T1", null, null, mock(Principal.class)));

      assertEquals(DEAD, ex.getDatasource());
      assertTrue(ex.getMessage().contains(DEAD));
      assertFalse(ex.getMessage().contains("not found"));
   }

   @Test
   void getTableDetailsNamesTheDataSourceWhenItsMetadataFailed() throws Exception {
      DefaultMetaDataProvider provider = mock(DefaultMetaDataProvider.class);
      when(provider.getRootMetaData(anyString())).thenReturn(metaDataFailureNode());

      MetadataApiService service = serviceWith(provider);

      DatasourceUnreachableException ex = assertThrows(DatasourceUnreachableException.class,
         () -> service.getTableDetails(DEAD, "T1", null, null, mock(Principal.class)));

      assertEquals(DEAD, ex.getDatasource());
   }

   @Test
   void blockedDataSourceIsAnsweredAtOnceOnTheNextCall() throws Exception {
      DefaultMetaDataProvider provider = mock(DefaultMetaDataProvider.class);
      when(provider.getRootMetaData(anyString())).thenAnswer(inv -> {
         Thread.sleep(10_000);
         return new XNode();
      });

      MetadataApiService service = serviceWith(provider);
      assertThrows(DatasourceUnreachableException.class,
         () -> service.getTableDetails(DEAD, "T1", null, null, mock(Principal.class)));

      long start = System.nanoTime();
      DatasourceUnreachableException ex = assertThrows(DatasourceUnreachableException.class,
         () -> service.getTableDetails(DEAD, "T1", null, null, mock(Principal.class)));
      long elapsedMs = (System.nanoTime() - start) / 1_000_000;

      assertTrue(elapsedMs < 300, "second call answered at once, took " + elapsedMs + " ms");
      assertTrue(ex.getMessage().contains(DEAD));
   }

   @Test
   void slowButConnectedDataSourceIsNotMarkedDownAndLaterCallsAreNotRejected() throws Exception {
      AtomicBoolean slow = new AtomicBoolean(true);
      DefaultMetaDataProvider provider = mock(DefaultMetaDataProvider.class);
      when(provider.getRootMetaData(anyString())).thenReturn(new XNode());
      when(provider.getMetaData(any(XNode.class), eq(true))).thenAnswer(inv -> {
         if(slow.get()) {
            // the connection is fine, reading the table's columns takes longer than the budget
            Thread.sleep(10_000);
         }

         XNode root = new XNode();
         XNode result = new XNode("Result");
         result.addChild(new XNode("ORDER_ID"));
         root.addChild(result);
         return root;
      });
      when(provider.getTable(any(), any(), eq("Orders"), eq(false))).thenReturn(new XNode("Orders"));

      MetadataApiService service = serviceWith(provider);

      DatasourceBudgetExceededException slowEx = assertThrows(
         DatasourceBudgetExceededException.class,
         () -> service.getTableDetails(DEAD, "Orders", null, null, mock(Principal.class)));
      assertTrue(slowEx.getMessage().contains(DEAD));
      assertFalse(slowEx.getMessage().contains("unreachable"));

      // the source answers normally again: it was never recorded as down
      slow.set(false);
      long start = System.nanoTime();
      DatabaseTableMeta meta =
         service.getTableDetails(DEAD, "Orders", null, null, mock(Principal.class));
      long elapsedMs = (System.nanoTime() - start) / 1_000_000;

      assertEquals(1, meta.getColumns().size());
      assertTrue(elapsedMs < 5_000, "later call not rejected, took " + elapsedMs + " ms");
   }

   @Test
   void searchSchemaWarnsAboutASlowButConnectedSourceWithoutCallingItUnreachable()
      throws Exception
   {
      XRepository xrepository = mock(XRepository.class);
      when(xrepository.getDataSourceFullNames()).thenReturn(new String[] { DEAD });
      when(xrepository.getDataSource(anyString())).thenReturn(mock(JDBCDataSource.class));

      MetadataApiService service = spy(new MetadataApiService(
         xrepository, dataSourceService(), mock(AssetRepository.class),
         mock(AssetTreeService.class), new ObjectMapper()));

      doAnswer(inv -> {
         MetadataCallGuard.markConnected();
         Thread.sleep(10_000);
         return oneTable("orders");
      }).when(service).getDatabaseTables(anyString(), any(), any(), any(), any());

      SchemaSearchRequest request = new SchemaSearchRequest();
      request.setQuery("order");
      SchemaSearchResponse response = service.searchSchema(request, mock(Principal.class));

      assertEquals(1, response.getWarnings().size());
      assertTrue(response.getWarnings().get(0).contains("time budget"));
      assertFalse(response.getWarnings().get(0).contains("unreachable"));
      // not recorded as down
      MetadataCallGuard.checkNotDown(DEAD);
   }

   @Test
   void guardRunsTheTaskWithTheCallersPrincipal() throws Exception {
      Principal caller = mock(Principal.class);
      ThreadContext.setContextPrincipal(caller);

      try {
         Principal seen = MetadataCallGuard.call("ds", ThreadContext::getContextPrincipal);
         assertSame(caller, seen);
      }
      finally {
         ThreadContext.setContextPrincipal(null);
      }
   }

   @Test
   void guardRethrowsTheTasksOwnException() {
      IllegalStateException ex = assertThrows(IllegalStateException.class,
         () -> MetadataCallGuard.call("ds", () -> {
            throw new IllegalStateException("boom");
         }));
      assertEquals("boom", ex.getMessage());
   }

   /** The placeholder XEngine caches for a failed metadata retrieval (a private class). */
   private static XNode metaDataFailureNode() throws Exception {
      Constructor<?> ctor =
         Class.forName("inetsoft.uql.service.XEngine$MetaDataFailureNode").getDeclaredConstructor();
      ctor.setAccessible(true);
      return (XNode) ctor.newInstance();
   }
}
