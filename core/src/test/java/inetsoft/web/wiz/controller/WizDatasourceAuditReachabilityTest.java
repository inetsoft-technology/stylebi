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

package inetsoft.web.wiz.controller;

import inetsoft.sree.security.ResourceAction;
import inetsoft.sree.security.ResourceType;
import inetsoft.sree.security.SecurityEngine;
import inetsoft.uql.XRepository;
import inetsoft.uql.tabular.TabularDataSource;
import inetsoft.uql.xmla.XMLADataSource;
import inetsoft.uql.util.Config;
import inetsoft.util.audit.ActionRecord;
import inetsoft.web.admin.content.database.AuthenticationDetails;
import inetsoft.web.admin.content.database.DatabaseDefinition;
import inetsoft.web.admin.content.database.DatabaseTypeService;
import inetsoft.web.admin.content.database.NetworkLocation;
import inetsoft.web.admin.content.database.types.MySQLDatabaseType;
import inetsoft.web.admin.content.repository.DatabaseDatasourcesService;
import inetsoft.web.admin.content.repository.FolderDeleteResult;
import inetsoft.web.portal.controller.database.DataSourceService;
import inetsoft.web.portal.data.*;
import inetsoft.web.portal.service.datasource.DataSourceStatusService;
import inetsoft.web.portal.service.datasource.XmlaDatasourceService;
import inetsoft.web.viewsheet.Audited;
import inetsoft.web.wiz.model.*;
import inetsoft.web.wiz.request.*;
import inetsoft.web.wiz.service.EndpointCatalogReader;
import inetsoft.web.wiz.service.WorksheetTableService;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.security.Principal;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Charter A16: every bridge write path reaches an audited service method, so the Portal's standard
 * audit records are written, and no write path goes around one.
 *
 * <p>Two halves, because an audited method can be audited two ways. Methods annotated with
 * {@code @Audited} are audited by Spring's aspect, which a unit test cannot run, so the annotation
 * itself is pinned by reflection (action and object type) next to the spy that proves the
 * controller calls the method. Methods that write their own {@code ActionRecord} in the body
 * ({@code saveDatabase}, {@code deleteDataSourceFolderResult}, {@code moveDataSource}) are audited
 * by their own code, which these tests do not execute; for those the spy proves the controller
 * reaches them and nothing else. A path that reached neither kind of method would be a defect to fix
 * in the bridge, and would fail here because the spy would see no call.</p>
 */
@Tag("core")
class WizDatasourceAuditReachabilityTest {
   // ---- the audited service methods really are audited -------------------------------------

   @Test
   void theAnnotatedServiceMethodsCarryTheRightAction() throws Exception {
      assertAudited(DatasourcesBaseService.class, "createNewDataSource",
                    ActionRecord.ACTION_NAME_CREATE, ActionRecord.OBJECT_TYPE_DATASOURCE);
      assertAudited(DatasourcesBaseService.class, "updateDataSource",
                    ActionRecord.ACTION_NAME_EDIT, ActionRecord.OBJECT_TYPE_DATASOURCE);
      assertAudited(DatasourcesBaseService.class, "deleteDataSource",
                    ActionRecord.ACTION_NAME_DELETE, ActionRecord.OBJECT_TYPE_DATASOURCE);
      assertAudited(DataSourceBrowserService.class, "addDatasourceFolder",
                    ActionRecord.ACTION_NAME_CREATE, ActionRecord.OBJECT_TYPE_FOLDER);
      assertAudited(DataSourceBrowserService.class, "renameFolder",
                    ActionRecord.ACTION_NAME_RENAME, ActionRecord.OBJECT_TYPE_FOLDER);
   }

   @Test
   void theXmlaServiceInheritsTheAuditedCreateAndUpdate() throws Exception {
      // The XMLA service does not override them, so the bridge's calls on it are the audited ones.
      for(String name : List.of("createNewDataSource", "updateDataSource")) {
         Method method = Arrays.stream(XmlaDatasourceService.class.getMethods())
            .filter(m -> m.getName().equals(name)).findFirst().orElseThrow();

         assertEquals(DatasourcesBaseService.class, method.getDeclaringClass(),
                      name + " is overridden on XmlaDatasourceService; check it is still audited");
      }
   }

   // ---- JDBC -------------------------------------------------------------------------------

   @Test
   void jdbcCreateAndUpdateReachSaveDatabaseWithTheMatchingAction() throws Exception {
      Fixture f = new Fixture();
      f.jdbc();

      f.database.createDatabase(new WizDatabaseCreateRequest("folder", wiz("orders")), f.principal);
      verify(f.databaseService).saveDatabase(
         eq("folder"), any(), eq(ActionRecord.ACTION_NAME_CREATE), eq(f.principal));

      f.database.updateDatabase("folder/orders", wiz("orders"), f.principal);
      verify(f.databaseService).saveDatabase(
         eq("folder/orders"), any(), eq(ActionRecord.ACTION_NAME_EDIT), eq(f.principal));
   }

   // ---- tabular ----------------------------------------------------------------------------

   @Test
   void tabularCreateUpdateAndDeleteReachTheAuditedMethods() throws Exception {
      Fixture f = new Fixture();
      f.allowAll();
      when(f.datasources.checkDuplicate("mongo")).thenReturn(false, true);
      inetsoft.uql.XDataSource tabular = mock(TabularDataSource.class);
      when(f.repository.getDataSource(anyString())).thenReturn(tabular);
      DataSourceDefinition definition = new DataSourceDefinition();
      definition.setName("mongo");
      definition.setType("MongoDB");

      f.tabular.createTabularDataSource(new WizTabularCreateRequest("", definition), f.principal);
      verify(f.datasources).createNewDataSource(any(), eq(false), eq(f.principal));

      when(f.datasources.checkDuplicate("mongo")).thenReturn(true);
      f.tabular.updateTabularDataSource("mongo", definition, f.principal);
      verify(f.datasources).updateDataSource(eq("mongo"), any(), eq(f.principal));

      f.database.deleteDatasources(new WizDatasourceDeleteRequest(
         List.of(new WizDatasourceRef("mongo", "mongo", false)), false), f.principal);
      verify(f.datasources).deleteDataSource(eq("mongo"), any(), eq(false));
   }

   // ---- folders and move -------------------------------------------------------------------

   @Test
   void folderCreateRenameDeleteAndMoveReachTheirServiceMethods() throws Exception {
      Fixture f = new Fixture();
      f.allowAll();
      when(f.browser.deleteDataSourceFolderResult(any(), any(), anyBoolean(), any()))
         .thenReturn(mock(FolderDeleteResult.class));

      f.database.createFolder(new WizFolderCreateRequest(null, "sales"), f.principal);
      verify(f.browser).addDatasourceFolder(eq("sales"), any(), eq(f.principal), anyBoolean());

      f.database.renameFolder(new WizFolderRenameRequest("sales", "retail"), f.principal);
      verify(f.browser).renameFolder(eq("sales"), eq("retail"), any(), any(), eq(f.principal));

      f.database.deleteDatasources(new WizDatasourceDeleteRequest(
         List.of(new WizDatasourceRef("retail", "retail", true)), false), f.principal);
      verify(f.browser).deleteDataSourceFolderResult(eq("retail"), any(), eq(false), eq(f.principal));

      f.database.moveDatasources(new WizMoveRequest(
         List.of(new WizDatasourceRef("a", "a", false)), "retail"), f.principal);
      verify(f.browser).moveDataSource(any(MoveCommand[].class), eq(f.principal));
   }

   // ---- the new endpoints ------------------------------------------------------------------

   @Test
   void xmlaCreateAndUpdateReachTheAuditedMethods() throws Exception {
      Fixture f = new Fixture();
      f.allowAll();
      XMLADataSource stored = mock(XMLADataSource.class);
      when(f.repository.getDataSource("olap/sales")).thenReturn(stored);
      when(f.xmlaService.checkDuplicate("olap/new")).thenReturn(false, true);
      when(f.xmlaService.checkDuplicate("olap/sales")).thenReturn(true);
      WizXmlaDefinition definition = new WizXmlaDefinition(
         "new", null, "d", "n", "i", "c", "http://x", false, new WizXmlaAuthentication(null, null),
         false, null, false);

      f.xmla.createXmla(new WizXmlaCreateRequest("olap", definition), f.principal);
      verify(f.xmlaService).createNewDataSource(any(), eq(true), eq(f.principal));

      f.xmla.updateXmla("olap/sales", new WizXmlaDefinition(
         "sales", null, "d", "n", "i", "c", "http://x", false, new WizXmlaAuthentication(null, null),
         false, null, false), f.principal);
      verify(f.xmlaService).updateDataSource(eq("sales"), any(), eq(f.principal));
   }

   @Test
   void additionalConnectionAddUpdateAndRemoveAllReachSaveDatabase() throws Exception {
      Fixture f = new Fixture();
      f.allowAll();
      f.jdbc();
      f.additionalStored("reports", "analytics");
      when(f.databaseService.checkAdditionalPermission(anyString(), anyString(), any(), any()))
         .thenReturn(true);

      f.additional.addAdditionalConnection(
         "db/orders", new WizAdditionalDefinitionRequest(wiz("warehouse")), f.principal);
      f.additional.updateAdditionalConnection(
         "db/orders", "reports", new WizAdditionalDefinitionRequest(wiz("reporting")), f.principal);
      f.additional.removeAdditionalConnections(
         "db/orders", new WizAdditionalRemoveRequest(List.of("analytics"), false), f.principal);

      verify(f.databaseService, times(3)).saveDatabase(
         eq("db/orders"), any(), eq(ActionRecord.ACTION_NAME_EDIT), eq(f.principal));
   }

   @Test
   void metadataRefreshIsReadOnlyAndTheTestEndpointsPersistNothing() throws Exception {
      Fixture f = new Fixture();
      f.allowAll();
      f.jdbc();

      f.database.testDatabaseConnection(
         new WizDatabaseTestRequest("db/orders", wiz("orders")), f.principal);
      f.metadata.refreshMetadata(new WizMetadataRefreshRequest("db/orders"), f.principal);

      verify(f.databaseService, never()).saveDatabase(any(), any(), any(), any());
      verify(f.datasources, never()).createNewDataSource(any(), anyBoolean(), any());
      verify(f.datasources, never()).updateDataSource(any(), any(), any());
      verify(f.datasources, never()).deleteDataSource(any(), any(), anyBoolean());
   }

   // ---- support ----------------------------------------------------------------------------

   private static void assertAudited(Class<?> type, String method, String action, String objectType) {
      Method found = Arrays.stream(type.getMethods())
         .filter(m -> m.getName().equals(method) && m.isAnnotationPresent(Audited.class))
         .findFirst()
         .orElseThrow(() -> new AssertionError(
            type.getSimpleName() + "." + method + " is no longer @Audited, so the bridge write " +
               "that calls it would write no Portal audit record"));

      Audited audited = found.getAnnotation(Audited.class);
      assertEquals(action, audited.actionName(), type.getSimpleName() + "." + method);
      assertEquals(objectType, audited.objectType(), type.getSimpleName() + "." + method);
   }

   private static WizDatabaseDefinition wiz(String name) {
      return new WizDatabaseDefinition(
         name, null, MySQLDatabaseType.TYPE, new WizNetworkLocation("h", 3306),
         new WizAuthentication(true, "u", null), null, false, -1, 3, null, false, false, null);
   }

   private static DatabaseDefinition storedDefinition(String name) {
      DatabaseDefinition definition = new DatabaseDefinition();
      definition.setName(name);
      definition.setOldName(name);
      definition.setType(MySQLDatabaseType.TYPE);
      AuthenticationDetails auth = new AuthenticationDetails();
      auth.setRequired(true);
      auth.setPassword("placeholder");
      definition.setAuthentication(auth);
      definition.setInfo(new MySQLDatabaseType().createDatabaseInfo());
      definition.setNetwork(new NetworkLocation());
      return definition;
   }

   private static final class Fixture {
      Fixture() throws Exception {
         doReturn(new MySQLDatabaseType()).when(types).getDatabaseType(MySQLDatabaseType.TYPE);
         database = new WizDatabaseController(
            browser, mock(DataSourceStatusService.class), databaseService, types, security,
            mock(Config.class), repository, mock(EndpointCatalogReader.class), datasources);
         tabular = new WizTabularController(
            datasources, security, repository, mock(WorksheetTableService.class));
         xmla = new WizXmlaController(xmlaService, security, repository);
         additional = new WizAdditionalConnectionController(databaseService, types, security, repository);
         metadata = new WizDatasourceMetadataController(
            repository, mock(DataSourceService.class), security);
      }

      void allowAll() throws Exception {
         when(security.checkPermission(any(), any(ResourceType.class), anyString(),
                                       any(ResourceAction.class))).thenReturn(true);
      }

      /** A JDBC database at {@code db/orders} that the service can read back and save. */
      void jdbc() throws Exception {
         allowAll();
         when(repository.getDataSource(anyString()))
            .thenReturn(mock(inetsoft.uql.jdbc.JDBCDataSource.class));
         when(databaseService.getActionName(anyString(), anyString()))
            .thenReturn(ActionRecord.ACTION_NAME_EDIT);
         when(databaseService.getDatabaseDefinition(anyString(), any()))
            .thenAnswer(inv -> storedDefinition("orders"));
      }

      void additionalStored(String... names) throws Exception {
         DatabaseDefinition[] stored = new DatabaseDefinition[names.length];

         for(int i = 0; i < names.length; i++) {
            stored[i] = storedDefinition(names[i]);
         }

         when(databaseService.getAdditionalDatabaseDefinition(anyString(), any())).thenReturn(stored);
      }

      final DataSourceBrowserService browser = mock(DataSourceBrowserService.class);
      final DatabaseDatasourcesService databaseService = mock(DatabaseDatasourcesService.class);
      final DatasourcesService datasources = mock(DatasourcesService.class);
      final XmlaDatasourceService xmlaService = mock(XmlaDatasourceService.class);
      final DatabaseTypeService types = mock(DatabaseTypeService.class);
      final SecurityEngine security = mock(SecurityEngine.class);
      final XRepository repository = mock(XRepository.class);
      final Principal principal = mock(Principal.class);
      final WizDatabaseController database;
      final WizTabularController tabular;
      final WizXmlaController xmla;
      final WizAdditionalConnectionController additional;
      final WizDatasourceMetadataController metadata;
   }
}
