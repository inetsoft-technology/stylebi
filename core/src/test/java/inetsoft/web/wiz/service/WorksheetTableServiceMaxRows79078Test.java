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
import inetsoft.uql.asset.TableAssembly;
import inetsoft.uql.asset.Worksheet;
import inetsoft.uql.tabular.TabularDataSource;
import inetsoft.uql.tabular.TabularUtil;
import inetsoft.uql.util.Config;
import inetsoft.web.composer.ws.LayoutGraphService;
import inetsoft.web.composer.ws.joins.InnerJoinService;
import inetsoft.web.portal.controller.database.DataSourceService;
import inetsoft.web.portal.controller.database.QueryManagerService;
import inetsoft.web.wiz.model.WorksheetTable;
import inetsoft.web.wiz.model.WorksheetTableRequest;
import inetsoft.web.wiz.pairing.PairingException;
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

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Bug #79078: a caller-chosen {@code maxRows} must land on the table assembly (the field the model
 * read and every render honour -- TabularBoundQuery.merge() overwrites the query's own limit from
 * it on each execution), not only on the query. Absent or non-positive leaves the assembly at its
 * default (-1).
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(
   classes = { BaseTestConfiguration.class, WorksheetTableServiceMaxRows79078Test.TestConfig.class },
   initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class WorksheetTableServiceMaxRows79078Test {
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

   private static int assemblyMaxRows(String tabularSourceJson) throws Exception {
      XRepository xrepository = mock(XRepository.class);
      when(xrepository.getDataSource(eq("myds"))).thenReturn(mock(TabularDataSource.class));

      WorksheetTableService service = new WorksheetTableService(mock(ViewsheetService.class),
         mock(MetadataApiService.class), mock(InnerJoinService.class),
         mock(LayoutGraphService.class), mock(QueryManagerService.class), xrepository,
         new ObjectMapper(), mock(DataSourceService.class), mock(SecurityEngine.class));

      WorksheetTableRequest request = MAPPER.readValue("""
         { "tables": [ { "tableName": "t1", "tableType": "tabular table", "tabularSource": %s } ] }
         """.formatted(tabularSourceJson), WorksheetTableRequest.class);

      FakeColumnProducingNamedConnectorQuery query = new FakeColumnProducingNamedConnectorQuery();
      query.setColumnsByEndpoint(Map.of("Repos", List.of("repoId")));

      Method build = WorksheetTableService.class.getDeclaredMethod(
         "buildTabularTable", Worksheet.class, WorksheetTable.class);
      build.setAccessible(true);

      try(MockedStatic<TabularUtil> tabularUtil =
             mockStatic(TabularUtil.class, CALLS_REAL_METHODS))
      {
         tabularUtil.when(() -> TabularUtil.createQuery(eq("myds"))).thenReturn(query);
         TableAssembly table = (TableAssembly) build.invoke(
            service, new Worksheet(), request.getTables().get(0));
         return table.getMaxRows();
      }
   }

   @Test
   void suppliedMaxRowsLandsOnTheAssembly() throws Exception {
      assertEquals(100, assemblyMaxRows("""
         { "datasourcePath": "myds", "queryParams": { "endpoint": "Repos" }, "maxRows": 100 }
         """));
   }

   @Test
   void absentMaxRowsLeavesTheAssemblyUncapped() throws Exception {
      assertEquals(-1, assemblyMaxRows("""
         { "datasourcePath": "myds", "queryParams": { "endpoint": "Repos" } }
         """));
   }

   @Test
   void zeroMaxRowsLeavesTheAssemblyUncapped() throws Exception {
      assertEquals(-1, assemblyMaxRows("""
         { "datasourcePath": "myds", "queryParams": { "endpoint": "Repos" }, "maxRows": 0 }
         """));
   }

   @Test
   void negativeMaxRowsIsRefusedInsteadOfSilentlyDropped() {
      // Every other maxRows entry point refuses a negative value; /ws/table must not quietly
      // build an uncapped table instead.
      InvocationTargetException thrown = assertThrows(InvocationTargetException.class,
         () -> assemblyMaxRows("""
            { "datasourcePath": "myds", "queryParams": { "endpoint": "Repos" }, "maxRows": -5 }
            """));
      PairingException cause = assertInstanceOf(PairingException.class, thrown.getCause());
      assertTrue(cause.getMessage().contains("maxRows cannot be negative (got -5)"),
         cause.getMessage());
   }
}
