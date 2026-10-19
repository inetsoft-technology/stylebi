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
 * Bug #78892: a /ws/table physical, sql query, tabular or relational join table into an existing
 * worksheet fails naming the holder when its tableName is blank or already names an assembly of
 * another kind (variable, named group), and that holder is left in place. A join used to delete
 * the holder and take its name. A table of the same name is still replaced.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(
   classes = { BaseTestConfiguration.class, WorksheetTableServiceNameTaken78892Test.TestConfig.class },
   initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class WorksheetTableServiceNameTaken78892Test {
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

   // ─── physical table ───────────────────────────────────────────────────────────────────────

   @Test
   void aPhysicalTableNamedLikeAVariableFailsAndKeepsTheVariable() throws Exception {
      assertRefused(physical("V1"), "Physical table", "V1", "variable",
                    DefaultVariableAssembly.class);
   }

   @Test
   void aPhysicalTableNamedLikeANamedGroupFailsAndKeepsTheNamedGroup() throws Exception {
      assertRefused(physical("NG1"), "Physical table", "NG1", "named group",
                    DefaultNamedGroupAssembly.class);
   }

   @Test
   void aPhysicalTableWithABlankNameFails() throws Exception {
      assertBlankRefused(physical(""), "Physical table");
   }

   @Test
   void aPhysicalTableNamedLikeATableStillReplacesIt() throws Exception {
      Worksheet ws = worksheet();
      Assembly old = ws.getAssembly("T1");

      WorksheetTableResponse result = create(ws, physical("T1"));

      assertTrue(result.isSuccess(), result.getErrorMessage());
      assertInstanceOf(PhysicalBoundTableAssembly.class, ws.getAssembly("T1"));
      assertNotSame(old, ws.getAssembly("T1"));
   }

   // ─── sql query table ──────────────────────────────────────────────────────────────────────

   @Test
   void aSqlQueryTableNamedLikeAVariableFailsAndKeepsTheVariable() throws Exception {
      assertRefused(sql("V1"), "SQL query table", "V1", "variable",
                    DefaultVariableAssembly.class);
   }

   @Test
   void aSqlQueryTableNamedLikeANamedGroupFailsAndKeepsTheNamedGroup() throws Exception {
      assertRefused(sql("NG1"), "SQL query table", "NG1", "named group",
                    DefaultNamedGroupAssembly.class);
   }

   // ─── tabular table ────────────────────────────────────────────────────────────────────────

   @Test
   void aTabularTableNamedLikeAVariableFailsAndKeepsTheVariable() throws Exception {
      assertRefused(tabular("V1"), "Tabular table", "V1", "variable",
                    DefaultVariableAssembly.class);
   }

   @Test
   void aTabularTableNamedLikeANamedGroupFailsAndKeepsTheNamedGroup() throws Exception {
      assertRefused(tabular("NG1"), "Tabular table", "NG1", "named group",
                    DefaultNamedGroupAssembly.class);
   }

   // A name that cannot be used is refused before the SQL runs or the endpoint is requested.

   @Test
   void aSqlQueryTableNamedLikeAVariableRunsNoSql() throws Exception {
      create(worksheet(), sql("V1"));

      verify(queryManagerService, never()).getColumnSelection(any(), any(), any(), any(), any());
   }

   @Test
   void aTabularTableNamedLikeAVariableRequestsNothing() throws Exception {
      create(worksheet(), tabular("V1"));

      assertFalse(tabularQueryCreated, "the endpoint was requested for a name that is refused");
   }

   @Test
   void aTabularTableWithABlankNameFails() throws Exception {
      assertBlankRefused(tabular(""), "Tabular table");
   }

   // ─── relational join table ────────────────────────────────────────────────────────────────

   @Test
   void aJoinNamedLikeAVariableFailsAndKeepsTheVariable() throws Exception {
      assertRefused(join("V1"), "Relational join table", "V1", "variable",
                    DefaultVariableAssembly.class);
   }

   @Test
   void aJoinNamedLikeANamedGroupFailsAndKeepsTheNamedGroup() throws Exception {
      assertRefused(join("NG1"), "Relational join table", "NG1", "named group",
                    DefaultNamedGroupAssembly.class);
   }

   @Test
   void aJoinWithABlankNameFails() throws Exception {
      assertBlankRefused(join(""), "Relational join table");
   }

   @Test
   void aJoinWithAFreeNameIsAdded() throws Exception {
      Worksheet ws = worksheet();

      WorksheetTableResponse result = create(ws, join("J1"));

      assertTrue(result.isSuccess(), result.getErrorMessage());
      assertInstanceOf(RelationalJoinTableAssembly.class, ws.getAssembly("J1"));
   }

   // ─── helpers ──────────────────────────────────────────────────────────────────────────────

   private void assertRefused(String tableJson, String kind, String name, String holderKind,
                                     Class<? extends Assembly> holderClass)
      throws Exception
   {
      Worksheet ws = worksheet();
      Assembly holder = ws.getAssembly(name);

      WorksheetTableResponse result = create(ws, tableJson);

      assertInstanceOf(holderClass, ws.getAssembly(name), "the " + holderKind + " was replaced");
      assertSame(holder, ws.getAssembly(name), "the " + holderKind + " was replaced");
      assertFalse(result.isSuccess(), "a " + kind + " over a " + holderKind + " was reported added");
      assertEquals(kind + " '" + name + "' cannot be added: '" + name + "' already names a " +
                   holderKind + " in this worksheet. Choose a different tableName.",
                   result.getErrorMessage());
   }

   private void assertBlankRefused(String tableJson, String kind) throws Exception {
      Worksheet ws = worksheet();
      int count = ws.getAssemblies().length;

      WorksheetTableResponse result = create(ws, tableJson);

      assertFalse(result.isSuccess(), "a " + kind + " with a blank name was reported added");
      assertEquals(kind + " '' could not be added to the worksheet.", result.getErrorMessage());
      assertEquals(count, ws.getAssemblies().length);
   }

   private static String physical(String name) {
      return """
         { "tableName": "%s", "tableType": "physical table",
           "physicalSource": { "datasourcePath": "ds78892", "tableName": "ORDERS" } }
         """.formatted(name);
   }

   private static String sql(String name) {
      return """
         { "tableName": "%s", "tableType": "sql query table",
           "sqlExpression": "SELECT ID FROM ORDERS",
           "physicalSource": { "datasourcePath": "ds78892" } }
         """.formatted(name);
   }

   private static String tabular(String name) {
      return """
         { "tableName": "%s", "tableType": "tabular table",
           "tabularSource": { "datasourcePath": "tab78892", "queryParams": { "endpoint": "Repos" } } }
         """.formatted(name);
   }

   private static String join(String name) {
      return """
         { "tableName": "%s", "tableType": "relational join table",
           "baseTables": [ "T1", "T2" ],
           "joinPaths": [ { "leftTable": "T1", "leftKey": "ID", "rightTable": "T2",
                            "rightKey": "ID", "joinType": "inner" } ] }
         """.formatted(name);
   }

   /** A worksheet holding tables T1 and T2, a variable V1 and a named group NG1. */
   private static Worksheet worksheet() {
      Worksheet ws = new Worksheet();
      ws.addAssembly(table(ws, "T1", "ID", "NAME"));
      ws.addAssembly(table(ws, "T2", "ID", "AMOUNT"));
      DefaultVariableAssembly variable = new DefaultVariableAssembly(ws, "V1");
      variable.setVariable(new AssetVariable("V1"));
      ws.addAssembly(variable);
      ws.addAssembly(new DefaultNamedGroupAssembly(ws, "NG1"));
      return ws;
   }

   private static PhysicalBoundTableAssembly table(Worksheet ws, String name, String... columns) {
      PhysicalBoundTableAssembly table = new PhysicalBoundTableAssembly(ws, name);
      table.setSourceInfo(new SourceInfo(SourceInfo.PHYSICAL_TABLE, "ds78892", name));
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
      when(ds.getName()).thenReturn("ds78892");
      when(ds.getDatabaseTypeString()).thenReturn("h2");
      MetadataApiService metadataApiService = mock(MetadataApiService.class);
      when(metadataApiService.getJDBCDatasource("ds78892")).thenReturn(ds);
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
      when(xrepository.getDataSource(eq("tab78892"))).thenReturn(tabularSource);
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
         AssetEntry.Type.WORKSHEET, "ws78892", null).toIdentifier();
      WorksheetTableRequest request = new ObjectMapper().readValue(
         "{ \"worksheetId\": \"" + worksheetId + "\", \"tables\": [ " + tableJson + " ] }",
         WorksheetTableRequest.class);

      try(MockedStatic<TabularUtil> tabularUtil =
             mockStatic(TabularUtil.class, CALLS_REAL_METHODS))
      {
         tabularUtil.when(() -> TabularUtil.createQuery(eq("tab78892"))).thenAnswer(invocation -> {
            tabularQueryCreated = true;
            return query;
         });
         var tables = service.createTables(request, USER).getTables();
         assertEquals(1, tables.size());
         return tables.get(0);
      }
   }
}
