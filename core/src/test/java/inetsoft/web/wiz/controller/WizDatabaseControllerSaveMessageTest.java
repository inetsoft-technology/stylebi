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

import com.fasterxml.jackson.databind.ObjectMapper;
import inetsoft.report.internal.Util;
import inetsoft.sree.security.ResourceAction;
import inetsoft.sree.security.ResourceType;
import inetsoft.sree.security.SecurityEngine;
import inetsoft.uql.XRepository;
import inetsoft.uql.util.Config;
import inetsoft.util.MessageException;
import inetsoft.util.audit.ActionRecord;
import inetsoft.web.admin.content.database.AuthenticationDetails;
import inetsoft.web.admin.content.database.DatabaseDefinition;
import inetsoft.web.admin.security.ConnectionStatus;
import inetsoft.web.admin.content.database.DatabaseTypeService;
import inetsoft.web.admin.content.database.NetworkLocation;
import inetsoft.web.admin.content.database.types.CustomDatabaseType;
import inetsoft.web.admin.content.database.types.MySQLDatabaseType;
import inetsoft.web.admin.content.database.types.PostgreSQLDatabaseType;
import inetsoft.uql.XDataSource;
import org.springframework.web.server.ResponseStatusException;
import inetsoft.web.admin.content.repository.DataSourceSettingsModel;
import inetsoft.web.admin.content.repository.DatabaseDatasourcesService;
import inetsoft.web.portal.data.DataSourceBrowserService;
import inetsoft.web.portal.data.DatasourcesService;
import inetsoft.web.portal.service.datasource.DataSourceStatusService;
import inetsoft.web.wiz.model.*;
import inetsoft.web.wiz.request.WizDatabaseCreateRequest;
import inetsoft.web.wiz.request.WizDatabaseTestRequest;
import inetsoft.web.wiz.request.WizFolderCreateRequest;
import inetsoft.web.wiz.request.WizFolderRenameRequest;
import inetsoft.web.wiz.service.EndpointCatalogReader;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.security.Principal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * G5 and G6 of the data source bridge: the JDBC credential reference on the wire, and the server's
 * own refusal text on the save results.
 *
 * <p>Before G6 a {@code MessageException} from the JDBC and folder paths (a cloud-secret id the
 * caller may not use, a path clash) escaped as the generic 500 "StyleBI-side bug", so a plugin could
 * not tell the caller what to fix. Before G5 the JDBC wire had no credential field at all, so a
 * credential id could not be sent, and an update overwrote the stored reference with the stored one
 * even when the caller sent another.</p>
 */
@Tag("core")
class WizDatabaseControllerSaveMessageTest {
   private static final String REFUSAL = "The credential id is not allowed for the caller";

   // ---- G6 ---------------------------------------------------------------------------------

   @Test
   void createReturnsTheRefusalTextNotAGeneric500() throws Exception {
      Fixture f = new Fixture();
      when(f.service.saveDatabase(any(), any(), any(), any())).thenThrow(new MessageException(REFUSAL));

      WizDatabaseSaveResult result = f.controller.createDatabase(
         new WizDatabaseCreateRequest("folder", wiz("orders", "secret-1")), f.principal);

      assertFalse(result.ok());
      assertEquals("UNKNOWN", result.reason(), "the code must stay UNKNOWN for existing clients");
      assertEquals(REFUSAL, result.message());
      assertNull(result.path());
   }

   @Test
   void updateReturnsTheRefusalText() throws Exception {
      Fixture f = new Fixture();
      f.existing("db/orders");
      when(f.service.saveDatabase(any(), any(), any(), any())).thenThrow(new MessageException(REFUSAL));

      WizDatabaseSaveResult result =
         f.controller.updateDatabase("db/orders", wiz("orders", "secret-1"), f.principal);

      assertEquals("UNKNOWN", result.reason());
      assertEquals(REFUSAL, result.message());
   }

   @Test
   void testConnectionReportsARefusalAsAFailedTestWithItsText() throws Exception {
      Fixture f = new Fixture();
      when(f.service.testDataSourceConnection(any(), any(DatabaseDefinition.class), any(),
                                              anyBoolean()))
         .thenThrow(new MessageException(REFUSAL));

      WizConnectionTestResult result = f.controller.testDatabaseConnection(
         new WizDatabaseTestRequest(null, wiz("orders", "secret-1")), f.principal);

      assertFalse(result.connected());
      assertEquals(REFUSAL, result.message());
   }

   @Test
   void testRunsWithTheStoredUrlPropertiesOfAnExistingDatabase() throws Exception {
      Fixture f = new Fixture();
      f.existing("db/orders").getInfo().setProperties("useSSL=true");
      when(f.service.testDataSourceConnection(any(), any(DatabaseDefinition.class), any(),
                                              anyBoolean()))
         .thenReturn(new ConnectionStatus("ok", true));

      f.controller.testDatabaseConnection(
         new WizDatabaseTestRequest("db/orders", wiz("orders", null)), f.principal);

      ArgumentCaptor<DatabaseDefinition> definition = ArgumentCaptor.forClass(DatabaseDefinition.class);
      verify(f.service).testDataSourceConnection(
         eq("db/orders"), definition.capture(), eq(f.principal), eq(false));
      assertEquals("useSSL=true", definition.getValue().getInfo().getProperties());
   }

   @Test
   void folderCreateAndRenameReturnTheRefusalText() throws Exception {
      Fixture f = new Fixture();
      doThrow(new MessageException("path clash")).when(f.browser)
         .addDatasourceFolder(any(), any(), any(), anyBoolean());
      when(f.browser.renameFolder(any(), any(), any(), any(), any()))
         .thenThrow(new MessageException("path clash on rename"));

      WizFolderSaveResult created =
         f.controller.createFolder(new WizFolderCreateRequest(null, "sales"), f.principal);
      WizFolderSaveResult renamed =
         f.controller.renameFolder(new WizFolderRenameRequest("sales", "retail"), f.principal);

      assertFalse(created.ok());
      assertEquals("UNKNOWN", created.reason());
      assertEquals("path clash", created.message());
      assertEquals("path clash on rename", renamed.message());
   }

   @Test
   void theSaveResultsKeepTheirThreeArgumentShapeAndCarryAnOptionalMessageOnTheWire()
      throws Exception
   {
      assertNull(new WizDatabaseSaveResult(true, null, "p").message());
      assertNull(WizFolderSaveResult.ok("p").message());
      assertNull(WizFolderSaveResult.failed(WizFolderSaveResult.DUPLICATE_NAME).message());

      String json = new ObjectMapper().writeValueAsString(
         new WizDatabaseSaveResult(false, "UNKNOWN", null, REFUSAL));

      assertTrue(json.contains("\"message\":\"" + REFUSAL + "\""), json);
   }

   // ---- G5 ---------------------------------------------------------------------------------

   @Test
   void createPassesTheCredentialReferenceToTheServiceThatAuthorizesIt() throws Exception {
      Fixture f = new Fixture();

      f.controller.createDatabase(
         new WizDatabaseCreateRequest("folder", wiz("orders", "secret-1")), f.principal);

      AuthenticationDetails auth = f.savedDefinition(ActionRecord.ACTION_NAME_CREATE, "folder");
      assertTrue(auth.isUseCredentialId());
      assertEquals("secret-1", auth.getCredentialId());
   }

   @Test
   void updateKeepsTheStoredReferenceWhenTheCallerSaysNothingAboutAuthentication() throws Exception {
      Fixture f = new Fixture();
      f.existing("db/orders").getAuthentication().setUseCredentialId(true);
      f.stored.getAuthentication().setCredentialId("stored-id");

      f.controller.updateDatabase("db/orders", wiz("orders", null), f.principal);

      AuthenticationDetails auth = f.savedDefinition(ActionRecord.ACTION_NAME_EDIT, "db/orders");
      assertTrue(auth.isUseCredentialId(), "the stored reference must survive an unrelated edit");
      assertEquals("stored-id", auth.getCredentialId());
   }

   @Test
   void updateKeepsTheStoredUrlPropertiesWhenTheCallerDoesNotEditThem() throws Exception {
      Fixture f = new Fixture();
      f.existing("db/orders").getInfo().setProperties("useSSL=true");

      f.controller.updateDatabase("db/orders", wiz("orders", null), f.principal);

      ArgumentCaptor<DataSourceSettingsModel> model =
         ArgumentCaptor.forClass(DataSourceSettingsModel.class);
      verify(f.service).saveDatabase(eq("db/orders"), model.capture(),
                                     eq(ActionRecord.ACTION_NAME_EDIT), eq(f.principal));
      assertEquals("useSSL=true", model.getValue().dataSource().getInfo().getProperties());
   }

   @Test
   void updateKeepsAReferenceTheCallerDidSet() throws Exception {
      Fixture f = new Fixture();
      f.existing("db/orders").getAuthentication().setUseCredentialId(true);
      f.stored.getAuthentication().setCredentialId("stored-id");

      f.controller.updateDatabase("db/orders", wiz("orders", "new-id"), f.principal);

      AuthenticationDetails auth = f.savedDefinition(ActionRecord.ACTION_NAME_EDIT, "db/orders");
      assertEquals("new-id", auth.getCredentialId(), "the caller's value must not be overwritten");
   }

   @Test
   void updateWithAPasswordDoesNotResurrectTheStoredCredentialReference() throws Exception {
      Fixture f = new Fixture();
      f.existing("db/orders").getAuthentication().setUseCredentialId(true);
      f.stored.getAuthentication().setCredentialId("stored-id");
      WizDatabaseDefinition withPassword = new WizDatabaseDefinition(
         "orders", null, MySQLDatabaseType.TYPE, new WizNetworkLocation("h", 3306),
         new WizAuthentication(true, "svc", "new-password"), null, false, -1, 3, null, false, false, null);

      f.controller.updateDatabase("db/orders", withPassword, f.principal);

      AuthenticationDetails auth = f.savedDefinition(ActionRecord.ACTION_NAME_EDIT, "db/orders");
      assertFalse(auth.isUseCredentialId(),
                  "a caller who supplied a password chose password authentication");
      assertEquals("new-password", auth.getPassword());
   }

   // ---- D-8: standard-type source created from the template ----------------------------------

   private static WizDatabaseDefinition overlay(String type, boolean customEditMode, String customUrl,
                                                String jdbcUrl)
   {
      // What a client gets by overlaying a standard type on the blank template: the template is a
      // CUSTOM definition with customEditMode set, and that flag survives the overlay.
      return new WizDatabaseDefinition(
         "pg", null, type, new WizNetworkLocation("db.example", 5432),
         new WizAuthentication(true, "u", "p"),
         new WizDatabaseInfo("d", null, null, null, null, null, jdbcUrl, null, customEditMode,
                             customUrl, null, null),
         false, -1, 3, null, false, false, null);
   }

   @Test
   void aStandardTypeWithTheTemplateCustomEditModeAndNoCustomUrlIsBuiltFromTheFields()
      throws Exception
   {
      DatabaseTypeService real = mock(DatabaseTypeService.class);
      PostgreSQLDatabaseType pgType = new PostgreSQLDatabaseType();
      doReturn(pgType).when(real).getDatabaseType("POSTGRESQL");
      doReturn(new MySQLDatabaseType()).when(real).getDatabaseType(MySQLDatabaseType.TYPE);
      WizDatabaseMapper mapper = new WizDatabaseMapper(real);

      DatabaseDefinition pg = mapper.toDatabaseDefinition(overlay("POSTGRESQL", true, null, null), null);
      DatabaseDefinition my = mapper.toDatabaseDefinition(overlay("MYSQL", true, "", null), null);

      // custom-edit mode with no custom URL is what stored a source with a null URL, which then
      // failed every read with a NullPointerException in the type URL parser
      assertFalse(pg.getInfo().isCustomEditMode());
      assertFalse(my.getInfo().isCustomEditMode());
      assertEquals("jdbc:postgresql://db.example:5432/d",
                   pgType.formatUrl(pg.getNetwork(),
                                    (PostgreSQLDatabaseType.PostgreSQLDatabaseInfo) pg.getInfo()));
   }

   @Test
   void anExplicitCustomUrlOnAStandardTypeIsStillHonoured() throws Exception {
      DatabaseTypeService real = mock(DatabaseTypeService.class);
      doReturn(new MySQLDatabaseType()).when(real).getDatabaseType(MySQLDatabaseType.TYPE);

      DatabaseDefinition my = new WizDatabaseMapper(real).toDatabaseDefinition(
         overlay("MYSQL", true, "jdbc:mysql://x:3306/y?a=b", null), null);

      assertTrue(my.getInfo().isCustomEditMode());
      assertEquals("jdbc:mysql://x:3306/y?a=b", my.getInfo().getCustomUrl());
   }

   @Test
   void aCustomTypeWithNoUrlAtAllIsRefusedWithAFieldNamedError() throws Exception {
      DatabaseTypeService real = mock(DatabaseTypeService.class);
      doReturn(new CustomDatabaseType()).when(real).getDatabaseType("CUSTOM");
      WizDatabaseMapper mapper = new WizDatabaseMapper(real);

      ResponseStatusException ex = assertThrows(
         ResponseStatusException.class,
         () -> mapper.toDatabaseDefinition(overlay("CUSTOM", true, null, null), null));

      assertEquals(400, ex.getStatusCode().value());
      assertTrue(ex.getReason().contains("jdbcUrl"));
      // an explicit jdbcUrl still works, as before
      assertEquals("jdbc:h2:mem:x", mapper.toDatabaseDefinition(
         overlay("CUSTOM", true, null, "jdbc:h2:mem:x"), null).getInfo().getCustomUrl());
   }

   @Test
   void aMissingSourceIs404NotTheGeneric500ButARealFaultStillPropagates() throws Exception {
      Fixture f = new Fixture();
      when(f.security.checkPermission(any(), any(ResourceType.class), anyString(),
                                      any(ResourceAction.class))).thenReturn(true);
      when(f.service.getDatabaseDefinition("db/none", f.principal))
         .thenThrow(new RuntimeException("Datasource does not exist!"));

      ResponseStatusException ex = assertThrows(
         ResponseStatusException.class,
         () -> f.controller.getDatabaseDefinition("db/none", f.principal));
      assertEquals(404, ex.getStatusCode().value());

      when(f.xrepository.getDataSource("db/broken")).thenReturn(mock(XDataSource.class));
      when(f.service.getDatabaseDefinition("db/broken", f.principal))
         .thenThrow(new NullPointerException("real bug"));

      assertThrows(NullPointerException.class,
                   () -> f.controller.getDatabaseDefinition("db/broken", f.principal));
   }

   @Test
   void readReturnsTheCredentialIdButNeverAPassword() throws Exception {
      Fixture f = new Fixture();
      f.existing("db/orders").getAuthentication().setUseCredentialId(true);
      f.stored.getAuthentication().setCredentialId("stored-id");
      f.stored.getAuthentication().setPassword("must-never-leak");
      when(f.security.checkPermission(any(), any(ResourceType.class), anyString(),
                                      any(ResourceAction.class))).thenReturn(true);

      WizAuthentication auth = f.controller.getDatabaseDefinition("db/orders", f.principal)
         .authentication();

      assertTrue(auth.useCredentialId());
      assertEquals("stored-id", auth.credentialId());
      assertNull(auth.password());
   }

   @Test
   void theWireAcceptsTheOldShapeAndTheNewFieldsAreOptional() throws Exception {
      ObjectMapper mapper = new ObjectMapper();

      WizAuthentication old = mapper.readValue(
         "{\"required\":true,\"userName\":\"u\",\"password\":\"p\"}", WizAuthentication.class);
      assertTrue(old.required());
      assertEquals("u", old.userName());
      assertEquals("p", old.password());
      assertFalse(old.useCredentialId());
      assertNull(old.credentialId());

      WizAuthentication withId = mapper.readValue(
         "{\"required\":true,\"useCredentialId\":true,\"credentialId\":\"c1\"}",
         WizAuthentication.class);
      assertTrue(withId.useCredentialId());
      assertEquals("c1", withId.credentialId());
      assertEquals(new WizAuthentication(true, "u", "p"), new WizAuthentication(true, "u", "p", false, null));
   }

   // ---- support ----------------------------------------------------------------------------

   private static WizDatabaseDefinition wiz(String name, String credentialId) {
      WizAuthentication auth = credentialId == null
         ? new WizAuthentication(true, "svc", null)
         : new WizAuthentication(true, null, null, true, credentialId);

      return new WizDatabaseDefinition(
         name, null, MySQLDatabaseType.TYPE, new WizNetworkLocation("db.example", 3306), auth, null,
         false, -1, 3, null, false, false, null);
   }

   private static final class Fixture {
      Fixture() throws Exception {
         doReturn(new MySQLDatabaseType()).when(types).getDatabaseType(MySQLDatabaseType.TYPE);
         when(security.checkPermission(any(), any(ResourceType.class), anyString(),
                                       any(ResourceAction.class))).thenReturn(true);
         controller = new WizDatabaseController(
            browser, mock(DataSourceStatusService.class), service, types, security,
            mock(Config.class), xrepository, mock(EndpointCatalogReader.class),
            mock(DatasourcesService.class));
      }

      /** An existing JDBC database at the path, authenticating with a user and password. */
      DatabaseDefinition existing(String path) throws Exception {
         stored = new DatabaseDefinition();
         stored.setName("orders");
         stored.setOldName("orders");
         stored.setType(MySQLDatabaseType.TYPE);
         AuthenticationDetails auth = new AuthenticationDetails();
         auth.setRequired(true);
         auth.setUserName("svc");
         auth.setPassword(Util.PLACEHOLDER_PASSWORD);
         stored.setAuthentication(auth);
         stored.setInfo(new MySQLDatabaseType().createDatabaseInfo());
         stored.setNetwork(new NetworkLocation());
         when(service.getActionName(path, "orders")).thenReturn(ActionRecord.ACTION_NAME_EDIT);
         when(service.getDatabaseDefinition(path, principal)).thenReturn(stored);
         return stored;
      }

      AuthenticationDetails savedDefinition(String action, String path) throws Exception {
         ArgumentCaptor<DataSourceSettingsModel> model =
            ArgumentCaptor.forClass(DataSourceSettingsModel.class);
         verify(service).saveDatabase(eq(path), model.capture(), eq(action), eq(principal));
         return model.getValue().dataSource().getAuthentication();
      }

      DatabaseDefinition stored;
      final DataSourceBrowserService browser = mock(DataSourceBrowserService.class);
      final DatabaseDatasourcesService service = mock(DatabaseDatasourcesService.class);
      final DatabaseTypeService types = mock(DatabaseTypeService.class);
      final SecurityEngine security = mock(SecurityEngine.class);
      final XRepository xrepository = mock(XRepository.class);
      final Principal principal = mock(Principal.class);
      final WizDatabaseController controller;
   }
}
