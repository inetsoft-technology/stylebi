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

import inetsoft.report.internal.Util;
import inetsoft.sree.security.ResourceAction;
import inetsoft.sree.security.ResourceType;
import inetsoft.sree.security.SecurityEngine;
import inetsoft.uql.XRepository;
import inetsoft.uql.jdbc.JDBCDataSource;
import inetsoft.util.MessageException;
import inetsoft.util.audit.ActionRecord;
import inetsoft.web.admin.content.database.AuthenticationDetails;
import inetsoft.web.admin.content.database.DatabaseDefinition;
import inetsoft.web.admin.content.database.DatabaseTypeService;
import inetsoft.web.admin.content.database.NetworkLocation;
import inetsoft.web.admin.content.database.types.MySQLDatabaseType;
import inetsoft.web.admin.content.repository.DataSourceSettingsModel;
import inetsoft.web.admin.content.repository.DatabaseDatasourcesService;
import inetsoft.web.admin.security.ConnectionStatus;
import inetsoft.web.wiz.model.*;
import inetsoft.web.wiz.request.WizAdditionalDefinitionRequest;
import inetsoft.web.wiz.request.WizAdditionalRemoveRequest;
import inetsoft.web.wiz.request.WizAdditionalTestRequest;
import inetsoft.web.wiz.service.UnsupportedDatasourceException;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.lang.reflect.Method;
import java.security.Principal;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * The additional-connection bridge endpoints. They are read-modify-write over the Portal's
 * replace-the-whole-list save, so the cases that matter are the ones where that could lose
 * something: an entry the caller did not touch, an entry the caller cannot read, a stored password.
 * The service is mocked and its {@code saveDatabase} argument is inspected.
 */
@Tag("core")
class WizAdditionalConnectionControllerTest {
   @Test
   void mappingsMatchThePublishedContract() {
      Set<String> paths = new HashSet<>();
      String prefix =
         WizAdditionalConnectionController.class.getAnnotation(RequestMapping.class).value()[0];

      for(Method method : WizAdditionalConnectionController.class.getDeclaredMethods()) {
         GetMapping get = method.getAnnotation(GetMapping.class);
         PostMapping post = method.getAnnotation(PostMapping.class);

         if(get != null) {
            paths.add(prefix + get.value()[0]);
         }

         if(post != null) {
            paths.add(prefix + post.value()[0]);
         }
      }

      assertEquals(Set.of("/api/wiz/databases/additional", "/api/wiz/databases/additional/add",
                          "/api/wiz/databases/additional/update",
                          "/api/wiz/databases/additional/remove",
                          "/api/wiz/databases/additional/test"),
                   paths);
   }

   // ---- gates ------------------------------------------------------------------------------

   @Test
   void everyEndpointNeedsWriteOnTheBaseAndTouchesNothingWithout() throws Exception {
      Fixture f = new Fixture();
      f.denyAll();
      WizAdditionalDefinitionRequest request = new WizAdditionalDefinitionRequest(wiz("reports"));

      assertThrows(SecurityException.class, () -> f.controller.listAdditionalConnections(BASE, f.principal));
      assertThrows(SecurityException.class,
                   () -> f.controller.addAdditionalConnection(BASE, request, f.principal));
      assertThrows(SecurityException.class,
                   () -> f.controller.updateAdditionalConnection(BASE, "a", request, f.principal));
      assertThrows(SecurityException.class, () -> f.controller.removeAdditionalConnections(
         BASE, new WizAdditionalRemoveRequest(List.of("a"), false), f.principal));
      assertThrows(SecurityException.class, () -> f.controller.testAdditionalConnection(
         BASE, new WizAdditionalTestRequest(wiz("a"), "a"), f.principal));

      verifyNoInteractions(f.service);
      verify(f.repository, never()).getDataSource(anyString());
   }

   @Test
   void aNonJdbcBaseIsRefusedWith422() throws Exception {
      Fixture f = new Fixture();
      f.allowAll();
      when(f.repository.getDataSource(BASE)).thenReturn(mock(inetsoft.uql.tabular.TabularDataSource.class));

      assertThrows(UnsupportedDatasourceException.class,
                   () -> f.controller.listAdditionalConnections(BASE, f.principal));
   }

   // ---- list -------------------------------------------------------------------------------

   @Test
   void listOmitsAConnectionTheCallerCannotReadAndNeverReturnsAPassword() throws Exception {
      Fixture f = new Fixture();
      f.allowAll();
      f.stored("reports", "analytics");
      when(f.service.checkAdditionalPermission(BASE, "analytics", ResourceAction.READ, f.principal))
         .thenReturn(true);
      when(f.service.checkAdditionalPermission(BASE, "reports", ResourceAction.READ, f.principal))
         .thenReturn(false);

      WizAdditionalConnections result = f.controller.listAdditionalConnections(BASE, f.principal);

      assertEquals(1, result.connections().size());
      WizAdditionalConnection only = result.connections().get(0);
      assertEquals("analytics", only.name());
      assertNull(only.definition().authentication().password());
      assertTrue(only.deletable());
   }

   // ---- add --------------------------------------------------------------------------------

   @Test
   void addSavesTheCompleteListThroughTheAuditedSaveWithUntouchedEntriesIntact() throws Exception {
      Fixture f = new Fixture();
      f.allowAll();
      f.stored("reports", "analytics");

      WizDatabaseSaveResult result = f.controller.addAdditionalConnection(
         BASE, new WizAdditionalDefinitionRequest(wiz("warehouse")), f.principal);

      assertTrue(result.ok());
      assertEquals("db/orders/warehouse", result.path());
      DataSourceSettingsModel saved = f.savedModel(ActionRecord.ACTION_NAME_EDIT);

      assertEquals("orders", saved.dataSource().getName());
      assertEquals("orders", saved.dataSource().getOldName(),
                   "the base's own name is its key to the stored password");
      assertNull(saved.permissions(), "a connection edit must never write permissions");

      List<String> names = names(saved);
      assertEquals(List.of("reports", "analytics", "warehouse"), names,
                   "the save REPLACES the list, so every stored entry must be in it");

      DatabaseDefinition untouched = saved.additionalDataSources()[0];
      assertEquals("reports", untouched.getOldName());
      assertEquals(Util.PLACEHOLDER_PASSWORD, untouched.getAuthentication().getPassword(),
                   "an untouched entry keeps the placeholder the service recovers the password from");

      DatabaseDefinition added = saved.additionalDataSources()[2];
      assertNull(added.getOldName(), "a new connection has no stored password to recover");
   }

   @Test
   void addRefusesAnOccupiedNameWithoutSaving() throws Exception {
      Fixture f = new Fixture();
      f.allowAll();
      f.stored("reports");

      WizDatabaseSaveResult result = f.controller.addAdditionalConnection(
         BASE, new WizAdditionalDefinitionRequest(wiz("reports")), f.principal);

      assertFalse(result.ok());
      assertEquals("DUPLICATE_NAME", result.reason());
      verify(f.service, never()).saveDatabase(any(), any(), any(), any());
   }

   @Test
   void addRefusesASlashedNameWithAFieldNamedError() throws Exception {
      Fixture f = new Fixture();
      f.allowAll();
      f.stored("reports");

      assertThrows(IllegalArgumentException.class, () -> f.controller.addAdditionalConnection(
         BASE, new WizAdditionalDefinitionRequest(wiz("a.b")), f.principal));
      verify(f.service, never()).saveDatabase(any(), any(), any(), any());
   }

   @Test
   void addCarriesAServerRefusalAndAReturnedStatusBackToTheCaller() throws Exception {
      Fixture f = new Fixture();
      f.allowAll();
      f.stored("reports");
      when(f.service.saveDatabase(any(), any(), any(), any()))
         .thenThrow(new MessageException("the credential id is not allowed for the caller"));

      WizDatabaseSaveResult refused = f.controller.addAdditionalConnection(
         BASE, new WizAdditionalDefinitionRequest(wiz("warehouse")), f.principal);

      assertFalse(refused.ok());
      assertEquals("UNKNOWN", refused.reason());
      assertEquals("the credential id is not allowed for the caller", refused.message());

      doReturn(new ConnectionStatus("Duplicate")).when(f.service)
         .saveDatabase(any(), any(), any(), any());

      assertEquals("DUPLICATE_NAME", f.controller.addAdditionalConnection(
         BASE, new WizAdditionalDefinitionRequest(wiz("warehouse")), f.principal).reason());
   }

   @Test
   void addPassesACredentialIdThroughForTheServiceToAuthorize() throws Exception {
      Fixture f = new Fixture();
      f.allowAll();
      f.stored("reports");
      WizDatabaseDefinition withCredential = new WizDatabaseDefinition(
         "warehouse", null, MySQLDatabaseType.TYPE, new WizNetworkLocation("h", 3306),
         new WizAuthentication(true, null, null, true, "cloud-secret-7"), null, false, -1, 3, null,
         false, false, null);

      f.controller.addAdditionalConnection(
         BASE, new WizAdditionalDefinitionRequest(withCredential), f.principal);

      AuthenticationDetails auth = f.savedModel(ActionRecord.ACTION_NAME_EDIT)
         .additionalDataSources()[1].getAuthentication();
      assertTrue(auth.isUseCredentialId());
      assertEquals("cloud-secret-7", auth.getCredentialId());
   }

   // ---- update -----------------------------------------------------------------------------

   @Test
   void updateReplacesOnlyTheNamedEntryAndUsesTheStoredNameAsOldName() throws Exception {
      Fixture f = new Fixture();
      f.allowAll();
      f.stored("reports", "analytics");

      WizDatabaseSaveResult result = f.controller.updateAdditionalConnection(
         BASE, "reports", new WizAdditionalDefinitionRequest(wiz("reporting")), f.principal);

      assertTrue(result.ok());
      assertEquals("db/orders/reporting", result.path());
      DataSourceSettingsModel saved = f.savedModel(ActionRecord.ACTION_NAME_EDIT);
      assertEquals(List.of("reporting", "analytics"), names(saved));

      DatabaseDefinition changed = saved.additionalDataSources()[0];
      assertEquals("reports", changed.getOldName(), "the stored name, never taken from the client");
      assertEquals(Util.PLACEHOLDER_PASSWORD, changed.getAuthentication().getPassword(),
                   "a null password is the placeholder the service recovers the stored one from");

      assertEquals("analytics", saved.additionalDataSources()[1].getOldName());
   }

   @Test
   void updateKeepsAnEntryTheCallerCannotReadInTheSavedList() throws Exception {
      Fixture f = new Fixture();
      f.allowAll();
      f.stored("reports", "hidden");
      when(f.service.checkAdditionalPermission(eq(BASE), eq("hidden"), any(), any()))
         .thenReturn(false);

      f.controller.updateAdditionalConnection(
         BASE, "reports", new WizAdditionalDefinitionRequest(wiz("reports")), f.principal);

      assertEquals(List.of("reports", "hidden"), names(f.savedModel(ActionRecord.ACTION_NAME_EDIT)),
                   "an unreadable entry must be carried through or the save would delete it");
   }

   @Test
   void updateRefusesAnEntryTheCallerCannotRead() throws Exception {
      Fixture f = new Fixture();
      f.allowAll();
      f.stored("hidden");
      when(f.service.checkAdditionalPermission(eq(BASE), eq("hidden"), any(), any()))
         .thenReturn(false);

      assertThrows(SecurityException.class, () -> f.controller.updateAdditionalConnection(
         BASE, "hidden", new WizAdditionalDefinitionRequest(wiz("hidden")), f.principal));
      verify(f.service, never()).saveDatabase(any(), any(), any(), any());
   }

   @Test
   void updateReportsAMissingConnectionAndARenameOntoAnotherOne() throws Exception {
      Fixture f = new Fixture();
      f.allowAll();
      f.stored("reports", "analytics");

      assertEquals("DATASOURCE_LOST", f.controller.updateAdditionalConnection(
         BASE, "nope", new WizAdditionalDefinitionRequest(wiz("nope")), f.principal).reason());
      assertEquals("DUPLICATE_NAME", f.controller.updateAdditionalConnection(
         BASE, "reports", new WizAdditionalDefinitionRequest(wiz("analytics")), f.principal).reason());
      verify(f.service, never()).saveDatabase(any(), any(), any(), any());
   }

   @Test
   void updateKeepsTheStoredCredentialReferenceWhenTheCallerSaysNothingAboutAuthentication()
      throws Exception
   {
      Fixture f = new Fixture();
      f.allowAll();
      f.stored("reports");
      f.additionals.get(0).getAuthentication().setUseCredentialId(true);
      f.additionals.get(0).getAuthentication().setCredentialId("stored-id");

      f.controller.updateAdditionalConnection(
         BASE, "reports", new WizAdditionalDefinitionRequest(wiz("reports")), f.principal);

      AuthenticationDetails auth = f.savedModel(ActionRecord.ACTION_NAME_EDIT)
         .additionalDataSources()[0].getAuthentication();
      assertTrue(auth.isUseCredentialId());
      assertEquals("stored-id", auth.getCredentialId());
   }

   @Test
   void updateKeepsTheStoredUrlPropertiesWhenTheCallerDoesNotEditThem() throws Exception {
      Fixture f = new Fixture();
      f.allowAll();
      f.stored("reports");
      f.additionals.get(0).getInfo().setProperties("useSSL=true");

      f.controller.updateAdditionalConnection(
         BASE, "reports", new WizAdditionalDefinitionRequest(wiz("reports")), f.principal);

      assertEquals("useSSL=true", f.savedModel(ActionRecord.ACTION_NAME_EDIT)
         .additionalDataSources()[0].getInfo().getProperties());
   }

   // ---- remove -----------------------------------------------------------------------------

   @Test
   void removeJudgesEachNameAloneAndSavesTheRemovableOnesTogether() throws Exception {
      Fixture f = new Fixture();
      f.allowAll();
      f.stored("reports", "analytics", "warehouse");
      when(f.service.additionalDeletable(BASE, "analytics")).thenReturn(true);

      WizAdditionalRemoveResult result = f.controller.removeAdditionalConnections(
         BASE, new WizAdditionalRemoveRequest(List.of("reports", "analytics", "nope"), false),
         f.principal);

      assertEquals(3, result.results().size());
      assertTrue(result.results().get(0).ok());
      assertFalse(result.results().get(1).ok());
      assertEquals(WizAdditionalRemoveResult.HAS_DEPENDENCIES, result.results().get(1).reason());
      assertNotNull(result.results().get(1).dependencyMessage());
      assertEquals(WizAdditionalRemoveResult.DATASOURCE_LOST, result.results().get(2).reason());

      verify(f.service, times(1)).saveDatabase(any(), any(), any(), any());
      assertEquals(List.of("analytics", "warehouse"),
                   names(f.savedModel(ActionRecord.ACTION_NAME_EDIT)));
   }

   @Test
   void removeWithForceIgnoresDependenciesAndWithNothingRemovableSavesNothing() throws Exception {
      Fixture f = new Fixture();
      f.allowAll();
      f.stored("reports");
      when(f.service.additionalDeletable(BASE, "reports")).thenReturn(true);

      WizAdditionalRemoveResult refused = f.controller.removeAdditionalConnections(
         BASE, new WizAdditionalRemoveRequest(List.of("reports"), false), f.principal);

      assertEquals(WizAdditionalRemoveResult.HAS_DEPENDENCIES, refused.results().get(0).reason());
      verify(f.service, never()).saveDatabase(any(), any(), any(), any());

      WizAdditionalRemoveResult forced = f.controller.removeAdditionalConnections(
         BASE, new WizAdditionalRemoveRequest(List.of("reports"), true), f.principal);

      assertTrue(forced.results().get(0).ok());
      assertEquals(List.of(), names(f.savedModel(ActionRecord.ACTION_NAME_EDIT)));
   }

   @Test
   void aFailedSharedSaveIsReportedOnEveryRemovedNameNeverAsSuccess() throws Exception {
      Fixture f = new Fixture();
      f.allowAll();
      f.stored("reports", "analytics");
      when(f.service.saveDatabase(any(), any(), any(), any()))
         .thenThrow(new MessageException("refused"));

      WizAdditionalRemoveResult result = f.controller.removeAdditionalConnections(
         BASE, new WizAdditionalRemoveRequest(List.of("reports", "analytics"), false), f.principal);

      for(WizAdditionalRemoveResult.Item item : result.results()) {
         assertFalse(item.ok());
         assertEquals("UNKNOWN", item.reason());
         assertEquals("refused", item.message());
      }
   }

   // ---- test -------------------------------------------------------------------------------

   @Test
   void testRecoversTheStoredPasswordThroughTheNamedConnectionAsAnAdditionalSource()
      throws Exception
   {
      Fixture f = new Fixture();
      f.allowAll();
      f.stored("reports");
      when(f.service.testDataSourceConnection(anyString(), any(DatabaseDefinition.class), any(),
                                              anyBoolean()))
         .thenReturn(new ConnectionStatus("ok", true));

      WizConnectionTestResult result = f.controller.testAdditionalConnection(
         BASE, new WizAdditionalTestRequest(wiz("reports"), "reports"), f.principal);

      assertTrue(result.connected());
      ArgumentCaptor<DatabaseDefinition> definition = ArgumentCaptor.forClass(DatabaseDefinition.class);
      verify(f.service).testDataSourceConnection(
         eq(BASE), definition.capture(), eq(f.principal), eq(true));
      assertEquals("reports", definition.getValue().getOldName());
   }

   @Test
   void testRunsWithTheStoredUrlPropertiesOfTheNamedConnection() throws Exception {
      Fixture f = new Fixture();
      f.allowAll();
      f.stored("reports");
      f.additionals.get(0).getInfo().setProperties("useSSL=true");
      when(f.service.testDataSourceConnection(anyString(), any(DatabaseDefinition.class), any(),
                                              anyBoolean()))
         .thenReturn(new ConnectionStatus("ok", true));

      f.controller.testAdditionalConnection(
         BASE, new WizAdditionalTestRequest(wiz("reports"), "reports"), f.principal);

      ArgumentCaptor<DatabaseDefinition> definition = ArgumentCaptor.forClass(DatabaseDefinition.class);
      verify(f.service).testDataSourceConnection(
         eq(BASE), definition.capture(), eq(f.principal), eq(true));
      assertEquals("useSSL=true", definition.getValue().getInfo().getProperties());
   }

   @Test
   void testReportsAServerRefusalAsAFailedTestWithItsText() throws Exception {
      Fixture f = new Fixture();
      f.allowAll();
      f.stored("reports");
      when(f.service.testDataSourceConnection(anyString(), any(DatabaseDefinition.class), any(),
                                              anyBoolean()))
         .thenThrow(new MessageException("the credential id is not allowed for the caller"));

      WizConnectionTestResult result = f.controller.testAdditionalConnection(
         BASE, new WizAdditionalTestRequest(wiz("reports"), null), f.principal);

      assertFalse(result.connected());
      assertEquals("the credential id is not allowed for the caller", result.message());
   }

   // ---- support ----------------------------------------------------------------------------

   private static List<String> names(DataSourceSettingsModel model) {
      List<String> names = new ArrayList<>();

      for(DatabaseDefinition definition : model.additionalDataSources()) {
         names.add(definition.getName());
      }

      return names;
   }

   private static WizDatabaseDefinition wiz(String name) {
      return new WizDatabaseDefinition(
         name, null, MySQLDatabaseType.TYPE, new WizNetworkLocation("db.example", 3306),
         new WizAuthentication(true, "svc", null), null, false, -1, 3, null, false, false, null);
   }

   private static DatabaseDefinition stored(String name) {
      DatabaseDefinition definition = new DatabaseDefinition();
      definition.setName(name);
      definition.setOldName(name);
      definition.setType(MySQLDatabaseType.TYPE);
      AuthenticationDetails auth = new AuthenticationDetails();
      auth.setRequired(true);
      auth.setUserName("svc");
      auth.setPassword(Util.PLACEHOLDER_PASSWORD);
      definition.setAuthentication(auth);
      definition.setInfo(new MySQLDatabaseType().createDatabaseInfo());
      definition.setNetwork(new NetworkLocation());
      return definition;
   }

   private static final String BASE = "db/orders";

   private static final class Fixture {
      Fixture() throws Exception {
         doReturn(new MySQLDatabaseType()).when(types).getDatabaseType(MySQLDatabaseType.TYPE);
         when(repository.getDataSource(BASE)).thenReturn(mock(JDBCDataSource.class));
         when(service.checkAdditionalPermission(anyString(), anyString(), any(), any()))
            .thenReturn(true);
         controller = new WizAdditionalConnectionController(service, types, security, repository);
      }

      void allowAll() throws Exception {
         when(security.checkPermission(any(), any(ResourceType.class), anyString(),
                                       any(ResourceAction.class))).thenReturn(true);
      }

      void denyAll() throws Exception {
         when(security.checkPermission(any(), any(ResourceType.class), anyString(),
                                       any(ResourceAction.class))).thenReturn(false);
      }

      void stored(String... names) throws Exception {
         for(String name : names) {
            additionals.add(WizAdditionalConnectionControllerTest.stored(name));
         }

         when(service.getAdditionalDatabaseDefinition(BASE, principal))
            .thenAnswer(inv -> additionals.toArray(new DatabaseDefinition[0]));
         DatabaseDefinition base = WizAdditionalConnectionControllerTest.stored("orders");
         base.setName("db/orders");
         when(service.getDatabaseDefinition(BASE, principal)).thenReturn(base);
      }

      DataSourceSettingsModel savedModel(String action) throws Exception {
         ArgumentCaptor<DataSourceSettingsModel> model =
            ArgumentCaptor.forClass(DataSourceSettingsModel.class);
         verify(service).saveDatabase(eq(BASE), model.capture(), eq(action), eq(principal));
         return model.getValue();
      }

      final DatabaseDatasourcesService service = mock(DatabaseDatasourcesService.class);
      final DatabaseTypeService types = mock(DatabaseTypeService.class);
      final SecurityEngine security = mock(SecurityEngine.class);
      final XRepository repository = mock(XRepository.class);
      final Principal principal = mock(Principal.class);
      final List<DatabaseDefinition> additionals = new ArrayList<>();
      final WizAdditionalConnectionController controller;
   }
}
