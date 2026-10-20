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
import inetsoft.uql.service.DataSourceRegistry;
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
 * Bug #78907: a /ws/table request for a table whose name already exists in the worksheet replaces
 * the existing table, and when the new table then failed a later step (a pre- or post-aggregate
 * condition) the rollback removed it, so the failed request deleted the original table and
 * cleared the primary. The original is now put back.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(
   classes = { BaseTestConfiguration.class, WorksheetTableServiceFailedRebuild78907Test.TestConfig.class },
   initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class WorksheetTableServiceFailedRebuild78907Test {
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

   private static final String BAD_CONDITION =
      "[ { \"field\": \"NO_SUCH_COLUMN\", \"operation\": \"=\", \"value\": \"1\" } ]";

   @Test
   void aFailedPreAggregateConditionKeepsTheExistingTable() throws Exception {
      assertKeepsExisting("\"preAggregateCondition\": " + BAD_CONDITION);
   }

   @Test
   void aFailedPostAggregateConditionKeepsTheExistingTable() throws Exception {
      assertKeepsExisting("\"postAggregateCondition\": " + BAD_CONDITION);
   }

   @Test
   void aFailedRebuildKeepsThePrimaryTable() throws Exception {
      Worksheet ws = worksheet();
      ws.setPrimaryAssembly("T1");

      WorksheetTableResponse result =
         create(ws, physical("T1", "\"preAggregateCondition\": " + BAD_CONDITION));

      assertFalse(result.isSuccess());
      assertEquals("T1", ws.getPrimaryAssemblyName());
   }

   @Test
   void anExistingMirrorStillResolvesTheTableAfterAFailedRebuild() throws Exception {
      Worksheet ws = worksheet();
      Assembly old = ws.getAssembly("T1");

      create(ws, physical("T1", "\"preAggregateCondition\": " + BAD_CONDITION));

      MirrorTableAssembly mirror = (MirrorTableAssembly) ws.getAssembly("M1");
      assertNotNull(mirror);
      assertEquals("T1", mirror.getTableNames()[0]);
      assertSame(old, ws.getAssembly(mirror.getTableNames()[0]));
   }

   @Test
   void aSuccessfulRebuildStillReplacesTheTable() throws Exception {
      Worksheet ws = worksheet();
      Assembly old = ws.getAssembly("T1");

      WorksheetTableResponse result = create(ws, physical("T1", null));

      assertTrue(result.isSuccess(), result.getErrorMessage());
      assertNotSame(old, ws.getAssembly("T1"));
   }

   @Test
   void aFailedNewTableLeavesNoAssembly() throws Exception {
      Worksheet ws = worksheet();
      int count = ws.getAssemblies().length;

      WorksheetTableResponse result =
         create(ws, physical("N1", "\"preAggregateCondition\": " + BAD_CONDITION));

      assertFalse(result.isSuccess());
      assertNull(ws.getAssembly("N1"));
      assertEquals(count, ws.getAssemblies().length);
   }

   @Test
   void aTableNamedLikeAVariableStillKeepsTheVariable() throws Exception {
      Worksheet ws = worksheet();
      Assembly variable = ws.getAssembly("V1");

      WorksheetTableResponse result =
         create(ws, physical("V1", "\"preAggregateCondition\": " + BAD_CONDITION));

      assertFalse(result.isSuccess());
      assertSame(variable, ws.getAssembly("V1"));
   }

   private void assertKeepsExisting(String failingField) throws Exception {
      Worksheet ws = worksheet();
      Assembly old = ws.getAssembly("T1");

      WorksheetTableResponse result = create(ws, physical("T1", failingField));

      assertFalse(result.isSuccess(), "a rebuild that fails a later step was reported added");
      assertSame(old, ws.getAssembly("T1"), "the existing table was not put back");
   }

   private static String physical(String name, String extra) {
      return """
         { "tableName": "%s", "tableType": "physical table",
           "physicalSource": { "datasourcePath": "ds78907", "tableName": "ORDERS" }%s }
         """.formatted(name, extra == null ? "" : ", " + extra);
   }

   /** A worksheet holding tables T1 and T2, a mirror M1 of T1 and a variable V1. */
   private static Worksheet worksheet() {
      Worksheet ws = new Worksheet();
      PhysicalBoundTableAssembly t1 = table(ws, "T1", "ID", "NAME");
      ws.addAssembly(t1);
      ws.addAssembly(table(ws, "T2", "ID", "AMOUNT"));
      ws.addAssembly(new MirrorTableAssembly(ws, "M1", t1));
      DefaultVariableAssembly variable = new DefaultVariableAssembly(ws, "V1");
      variable.setVariable(new AssetVariable("V1"));
      ws.addAssembly(variable);
      return ws;
   }

   private static PhysicalBoundTableAssembly table(Worksheet ws, String name, String... columns) {
      PhysicalBoundTableAssembly table = new PhysicalBoundTableAssembly(ws, name);
      table.setSourceInfo(new SourceInfo(SourceInfo.PHYSICAL_TABLE, "ds78907", name));
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

      JDBCDataSource ds = mock(JDBCDataSource.class);
      when(ds.getName()).thenReturn("ds78907");
      when(ds.getDatabaseTypeString()).thenReturn("h2");
      MetadataApiService metadataApiService = mock(MetadataApiService.class);
      when(metadataApiService.getJDBCDatasource("ds78907")).thenReturn(ds);
      XNode ordersMeta = new XNode("ORDERS");
      ordersMeta.setAttribute("type", "TABLE");
      when(metadataApiService.getTableMetaData(eq(ds), any(), any(), eq("ORDERS")))
         .thenReturn(ordersMeta);

      WorksheetTableService service = new WorksheetTableService(
         viewsheetService, metadataApiService,
         new InnerJoinService(viewsheetService, mock(DataSourceRegistry.class)),
         mock(LayoutGraphService.class), mock(QueryManagerService.class),
         mock(XRepository.class), new ObjectMapper(), dataSourceService, securityEngine);

      String worksheetId = new AssetEntry(AssetRepository.GLOBAL_SCOPE,
         AssetEntry.Type.WORKSHEET, "ws78907", null).toIdentifier();
      WorksheetTableRequest request = new ObjectMapper().readValue(
         "{ \"worksheetId\": \"" + worksheetId + "\", \"tables\": [ " + tableJson + " ] }",
         WorksheetTableRequest.class);

      var tables = service.createTables(request, USER).getTables();
      assertEquals(1, tables.size());
      return tables.get(0);
   }
}
