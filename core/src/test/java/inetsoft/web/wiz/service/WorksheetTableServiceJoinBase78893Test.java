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
import inetsoft.test.*;
import inetsoft.uql.ColumnSelection;
import inetsoft.uql.XNode;
import inetsoft.uql.XRepository;
import inetsoft.uql.asset.*;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.jdbc.JDBCDataSource;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.schema.XTypeNode;
import inetsoft.uql.service.DataSourceRegistry;
import inetsoft.uql.tabular.TabularDataSource;
import inetsoft.uql.tabular.TabularUtil;
import inetsoft.uql.util.Config;
import inetsoft.web.composer.ws.LayoutGraphService;
import inetsoft.web.composer.ws.joins.InnerJoinService;
import inetsoft.web.portal.controller.database.DataSourceService;
import inetsoft.web.portal.controller.database.QueryManagerService;
import inetsoft.web.wiz.model.WorksheetTableRequest;
import inetsoft.web.wiz.model.WorksheetTableResponse;
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

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Bug #78893: a /ws/table relational join whose tableName names one of its own source tables, or a
 * table a source is built on, replaced that table and then resolved to itself, recursing until
 * StackOverflowError. The join is refused naming the table, and the original table stays.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(
   classes = { BaseTestConfiguration.class, WorksheetTableServiceJoinBase78893Test.TestConfig.class },
   initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class WorksheetTableServiceJoinBase78893Test {
   @Configuration
   static class TestConfig {
      @Bean
      public Config config() {
         Config config = mock(Config.class);
         when(config.getResourceBundle(ArgumentMatchers.any())).thenReturn(null);
         return config;
      }
   }

   private static final Principal USER = mock(Principal.class);
   private QueryManagerService queryManagerService;
   private boolean tabularQueryCreated;

   private static final String SAME_NAME = "Relational join table '%s' cannot have the same name " +
      "as its source table. Choose a different tableName.";

   @Test
   void aJoinNamedLikeADirectBaseIsRefusedAndKeepsTheBase() throws Exception {
      Worksheet ws = worksheet();
      Assembly t1 = ws.getAssembly("T1");

      WorksheetTableResponse result = create(ws, join("T1", "[ \"T1\", \"T2\" ]",
                                                      path("T1", "T2")));

      assertRefused(result, SAME_NAME.formatted("T1"));
      assertSame(t1, ws.getAssembly("T1"));
   }

   @Test
   void aJoinNamedLikeATableOnlyInItsJoinPathsIsRefusedAndKeepsTheTable() throws Exception {
      Worksheet ws = worksheet();
      Assembly t2 = ws.getAssembly("T2");

      WorksheetTableResponse result = create(ws, join("T2", "[ \"T1\", \"T3\" ]",
                                                      path("T1", "T2"), path("T1", "T3")));

      assertRefused(result, SAME_NAME.formatted("T2"));
      assertSame(t2, ws.getAssembly("T2"));
   }

   @Test
   void aJoinNamedLikeATableItsBaseIsBuiltOnIsRefusedAndKeepsTheTable() throws Exception {
      Worksheet ws = worksheet();
      Assembly t1 = ws.getAssembly("T1");
      MirrorTableAssembly mirror = new MirrorTableAssembly(ws, "M", (TableAssembly) t1);
      mirror.setColumnSelection(((TableAssembly) t1).getColumnSelection().clone());
      ws.addAssembly(mirror);

      WorksheetTableResponse result = create(ws, join("T1", "[ \"M\", \"T2\" ]",
                                                      path("M", "T2")));

      assertRefused(result, "Relational join table 'T1' cannot replace table 'T1': its source " +
                            "table 'M' is built on it. Choose a different tableName.");
      assertSame(t1, ws.getAssembly("T1"));
      assertSame(mirror, ws.getAssembly("M"));
   }

   @Test
   void aJoinNamedLikeAnUnrelatedTableStillReplacesIt() throws Exception {
      Worksheet ws = worksheet();
      Assembly t3 = ws.getAssembly("T3");

      WorksheetTableResponse result = create(ws, join("T3", "[ \"T1\", \"T2\" ]",
                                                      path("T1", "T2")));

      assertTrue(result.isSuccess(), result.getErrorMessage());
      assertInstanceOf(RelationalJoinTableAssembly.class, ws.getAssembly("T3"));
      assertNotSame(t3, ws.getAssembly("T3"));
   }

   @Test
   void aJoinWithAFreeNameIsAdded() throws Exception {
      Worksheet ws = worksheet();

      WorksheetTableResponse result = create(ws, join("J1", "[ \"T1\", \"T2\" ]",
                                                      path("T1", "T2")));

      assertTrue(result.isSuccess(), result.getErrorMessage());
      assertInstanceOf(RelationalJoinTableAssembly.class, ws.getAssembly("J1"));
   }

   private static void assertRefused(WorksheetTableResponse result, String message) {
      assertFalse(result.isSuccess(), "a join over its own source was reported added");
      assertEquals(message, result.getErrorMessage());
   }

   private static String path(String left, String right) {
      return """
         { "leftTable": "%s", "leftKey": "ID", "rightTable": "%s", "rightKey": "ID",
           "joinType": "inner" }
         """.formatted(left, right);
   }

   private static String join(String name, String bases, String... paths) {
      return """
         { "tableName": "%s", "tableType": "relational join table",
           "baseTables": %s, "joinPaths": [ %s ] }
         """.formatted(name, bases, String.join(",", paths));
   }

   /** A worksheet holding tables T1, T2 and T3, each with an ID column. */
   private static Worksheet worksheet() {
      Worksheet ws = new Worksheet();
      ws.addAssembly(table(ws, "T1", "ID", "NAME"));
      ws.addAssembly(table(ws, "T2", "ID", "AMOUNT"));
      ws.addAssembly(table(ws, "T3", "ID", "REGION"));
      return ws;
   }

   private static PhysicalBoundTableAssembly table(Worksheet ws, String name, String... columns) {
      PhysicalBoundTableAssembly table = new PhysicalBoundTableAssembly(ws, name);
      table.setSourceInfo(new SourceInfo(SourceInfo.PHYSICAL_TABLE, "ds78893", name));
      ColumnSelection cs = new ColumnSelection();

      for(String column : columns) {
         cs.addAttribute(new ColumnRef(new AttributeRef(name, column)));
      }

      table.setColumnSelection(cs);
      return table;
   }

   private WorksheetTableResponse create(Worksheet ws, String tableJson) throws Exception {
      ViewsheetService viewsheetService = mock(ViewsheetService.class);
      AssetRepository repository = mock(AssetRepository.class);
      when(viewsheetService.getAssetRepository()).thenReturn(repository);
      when(repository.getSheet(any(), any(), anyBoolean(), any())).thenReturn(ws);

      SecurityEngine securityEngine = mock(SecurityEngine.class);
      when(securityEngine.checkPermission(eq(USER), any(ResourceType.class), anyString(),
                                          any(ResourceAction.class))).thenReturn(true);
      DataSourceService dataSourceService = mock(DataSourceService.class);
      when(dataSourceService.checkPermission(anyString(), eq(ResourceAction.READ), eq(USER)))
         .thenReturn(true);

      // physical table: the source table ORDERS exists
      JDBCDataSource ds = mock(JDBCDataSource.class);
      when(ds.getName()).thenReturn("ds78893");
      when(ds.getDatabaseTypeString()).thenReturn("h2");
      MetadataApiService metadataApiService = mock(MetadataApiService.class);
      when(metadataApiService.getJDBCDatasource("ds78893")).thenReturn(ds);
      XNode ordersMeta = new XNode("ORDERS");
      ordersMeta.setAttribute("type", "TABLE");
      when(metadataApiService.getTableMetaData(eq(ds), any(), any(), eq("ORDERS")))
         .thenReturn(ordersMeta);

      // sql query table: the SQL resolves to one column
      queryManagerService = mock(QueryManagerService.class);
      ColumnSelection sqlColumns = new ColumnSelection();
      sqlColumns.addAttribute(new ColumnRef(new AttributeRef(null, "ID")));
      when(queryManagerService.getColumnSelection(any(), any(), any(), any(), any()))
         .thenReturn(sqlColumns);

      // tabular table: the endpoint returns one column
      XRepository xrepository = mock(XRepository.class);
      TabularDataSource<?> tabularSource = mock(TabularDataSource.class);
      when(xrepository.getDataSource(eq("tab78893"))).thenReturn(tabularSource);
      FakeNamedConnectorQuery query = new FakeNamedConnectorQuery();
      XTypeNode idColumn = XSchema.createPrimitiveType(XSchema.STRING);
      idColumn.setName("id");
      query.setOutputColumns(new XTypeNode[] { idColumn });

      WorksheetTableService service = new WorksheetTableService(
         viewsheetService, metadataApiService,
         new InnerJoinService(viewsheetService, mock(DataSourceRegistry.class)),
         mock(LayoutGraphService.class), queryManagerService, xrepository, new ObjectMapper(),
         dataSourceService, securityEngine);

      String worksheetId = new AssetEntry(AssetRepository.GLOBAL_SCOPE,
         AssetEntry.Type.WORKSHEET, "ws78893", null).toIdentifier();
      WorksheetTableRequest request = new ObjectMapper().readValue(
         "{ \"worksheetId\": \"" + worksheetId + "\", \"tables\": [ " + tableJson + " ] }",
         WorksheetTableRequest.class);

      try(MockedStatic<TabularUtil> tabularUtil =
             mockStatic(TabularUtil.class, CALLS_REAL_METHODS))
      {
         tabularUtil.when(() -> TabularUtil.createQuery(eq("tab78893"))).thenAnswer(invocation -> {
            tabularQueryCreated = true;
            return query;
         });
         var tables = service.createTables(request, USER).getTables();
         assertEquals(1, tables.size());
         return tables.get(0);
      }
   }
}
