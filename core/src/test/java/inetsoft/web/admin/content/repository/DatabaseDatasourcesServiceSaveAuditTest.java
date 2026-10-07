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
package inetsoft.web.admin.content.repository;

import inetsoft.analytic.composition.ViewsheetService;
import inetsoft.sree.security.*;
import inetsoft.test.*;
import inetsoft.uql.DataSourceFolder;
import inetsoft.uql.XRepository;
import inetsoft.uql.asset.sync.RenameTransformHandler;
import inetsoft.uql.jdbc.JDBCDataSource;
import inetsoft.uql.jdbc.util.JDBCUtil;
import inetsoft.uql.service.DataSourceRegistry;
import inetsoft.uql.util.Config;
import inetsoft.util.Tool;
import inetsoft.util.audit.ActionRecord;
import inetsoft.util.audit.Audit;
import inetsoft.web.admin.content.database.DatabaseDefinition;
import inetsoft.web.admin.content.database.DatabaseTypeService;
import inetsoft.web.admin.content.database.types.AccessDatabaseType;
import inetsoft.web.admin.content.database.types.CustomDatabaseType;
import inetsoft.web.admin.general.DatabaseSettingsService;
import inetsoft.web.admin.security.ConnectionStatus;
import inetsoft.web.composer.vs.controller.VSLayoutServiceProxy;
import inetsoft.web.portal.controller.database.DataSourceService;
import inetsoft.web.portal.data.DatabaseDatasourcesController;
import inetsoft.web.portal.service.datasource.DataSourceStatusService;
import inetsoft.web.session.IgniteSessionRepository;
import inetsoft.web.viewsheet.EventAspect;
import inetsoft.web.viewsheet.EventAspectServiceProxy;
import inetsoft.web.viewsheet.model.RuntimeViewsheetRef;
import inetsoft.web.viewsheet.service.CoreLifecycleService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.MockedStatic;
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.security.Principal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Bug #77844, a data source save that is refused ("Duplicate", "Duplicate Folder",
 * "Invalid Folder") is audited as a failure with the refusal, through both overloads of
 * {@link DatabaseDatasourcesService#saveDatabase}, with exactly one record per save. The service
 * is wrapped with a real {@link EventAspect}, as in production, so an {@code @Audited} left on
 * either overload would show up as a second record.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, IntegrationTestConfiguration.class,
                                  DataSourcePathClashTest.Beans.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class DatabaseDatasourcesServiceSaveAuditTest {
   private static final String URL = "jdbc:derby:memory:bug77844;create=true";

   @Autowired
   private DataSourceRegistry registry;
   @Autowired
   private XRepository repository;
   private final List<ActionRecord> records = new ArrayList<>();
   private MockedStatic<Audit> auditStatic;
   private SecurityEngine security;
   private DatabaseDatasourcesService service;
   private Principal principal;

   @BeforeEach
   void setUp() throws Exception {
      registry.init();
      records.clear();
      security = mock(SecurityEngine.class);
      when(security.checkPermission(any(), any(), anyString(), any())).thenReturn(true);
      Audit audit = mock(Audit.class);
      doAnswer(inv -> records.add(inv.getArgument(0)))
         .when(audit).auditAction(any(ActionRecord.class), any());
      auditStatic = mockStatic(Audit.class);
      auditStatic.when(Audit::getInstance).thenReturn(audit);
      EventAspect aspect = new EventAspect(
         mock(RuntimeViewsheetRef.class), mock(CoreLifecycleService.class),
         mock(ViewsheetService.class), mock(VSLayoutServiceProxy.class),
         mock(EventAspectServiceProxy.class));
      DatabaseDatasourcesService target = new DatabaseDatasourcesService(
         new DatabaseTypeService(List.of(new CustomDatabaseType(), new AccessDatabaseType())),
         security, mock(DatabaseSettingsService.class), repository,
         mock(ResourcePermissionService.class), mock(DataSourceStatusService.class),
         mock(IgniteSessionRepository.class), registry, mock(RenameTransformHandler.class));
      AspectJProxyFactory factory = new AspectJProxyFactory(target);
      factory.setProxyTargetClass(true);
      factory.addAspect(aspect);
      service = factory.getProxy();
      principal = new SRPrincipal(new IdentityID("admin", Organization.getDefaultOrganizationID()),
                                  new IdentityID[0], new String[0],
                                  Organization.getDefaultOrganizationID(),
                                  Tool.getSecureRandom().nextLong());

      // prefixed so they don't clash with other classes using the same storage
      jdbc("aDup");
      jdbc("aRen");
      folder("aRenTo");
      folder("aFolder");
      folder("aAddF");
      jdbc("aAddF/aAddP");
      folder("aAddF/aAddP/aAddC");
      folder("aUnder");
      jdbc("aUnder");
      folder("aUnder/aSub");
      jdbc("aEdit");
   }

   @AfterEach
   void tearDown() {
      auditStatic.close();
   }

   static Stream<Arguments> refusals() {
      // label, path, name, action, additional connection (or null), refusal, data source that
      // must not exist after the refusal (or null)
      Object[][] cases = {
         { "duplicate name", "", "aDup", ActionRecord.ACTION_NAME_CREATE, null, "Duplicate",
           null },
         { "duplicate additional connection", "", "aDupNew", ActionRecord.ACTION_NAME_CREATE,
           "aDup", "Duplicate", "aDupNew" },
         { "rename onto a folder", "aRen", "aRenTo", ActionRecord.ACTION_NAME_EDIT, null,
           "Duplicate Folder", null },
         { "additional connection onto a folder", "aAddF/aAddP", "aAddP",
           ActionRecord.ACTION_NAME_EDIT, "aAddC", "Duplicate Folder", null },
         { "create onto a folder", "", "aFolder", ActionRecord.ACTION_NAME_CREATE, null,
           "Duplicate Folder", null },
         { "create in a missing folder", "aNoFolder", "aNew1", ActionRecord.ACTION_NAME_CREATE,
           null, "Invalid Folder", "aNoFolder/aNew1" },
         { "create under a data source", "aUnder/aSub", "aNew2", ActionRecord.ACTION_NAME_CREATE,
           null, "Invalid Folder", "aUnder/aSub/aNew2" },
      };
      List<Arguments> args = new ArrayList<>();

      for(boolean em : new boolean[] { false, true }) {
         for(Object[] c : cases) {
            args.add(Arguments.of(em ? "EM" : "portal", c[0], c[1], c[2], c[3], c[4], c[5],
                                  c[6]));
         }
      }

      return args.stream();
   }

   @ParameterizedTest(name = "{0} {1}")
   @MethodSource("refusals")
   void refusalIsAuditedFailure(String overload, String label, String path, String name,
                                String action, String additional, String refusal,
                                String notCreated)
      throws Exception
   {
      boolean em = "EM".equals(overload);
      DatabaseDefinition def = edit(ActionRecord.ACTION_NAME_EDIT.equals(action) ?
         (JDBCDataSource) repository.getDataSource(path) : source(name));
      def.setName(name);
      DatabaseDefinition[] adds = additional == null ? new DatabaseDefinition[0] :
         new DatabaseDefinition[] { edit(source(additional)) };
      DataSourceSettingsModel model = DataSourceSettingsModel.builder().uploadEnabled(false)
         .dataSource(def).additionalDataSources(adds).build();
      ConnectionStatus status = em ?
         service.saveDatabase(path, model, action, "Data Source/" + name, "new Name:" + name,
                              principal) :
         service.saveDatabase(path, model, action, principal);

      assertNotNull(status, label);
      assertEquals(refusal, status.getStatus(), label);
      assertEquals(1, records.size(), label);
      ActionRecord record = records.get(0);
      assertEquals(ActionRecord.ACTION_STATUS_FAILURE, record.getActionStatus(), label);
      assertEquals(refusal, record.getActionError(), label);
      assertEquals(em ? "Data Source/" + name : name, record.getObjectName(), label);
      assertEquals(action, record.getActionName(), label);
      assertEquals(ActionRecord.OBJECT_TYPE_DATASOURCE, record.getObjectType(), label);

      if(notCreated != null) {
         assertNull(repository.getDataSource(notCreated), label);
      }
   }

   @Test
   void portalSaveIsAuditedSuccess() throws Exception {
      ConnectionStatus status = service.saveDatabase(
         "", settings(edit(source("aOk4"))), ActionRecord.ACTION_NAME_CREATE, principal);

      assertNull(status);
      assertEquals(1, records.size());
      ActionRecord record = records.get(0);
      assertEquals(ActionRecord.ACTION_STATUS_SUCCESS, record.getActionStatus());
      assertEquals("", record.getActionError());
      assertEquals("aOk4", record.getObjectName());
      assertEquals(ActionRecord.ACTION_NAME_CREATE, record.getActionName());
      assertNotNull(repository.getDataSource("aOk4"));
   }

   @Test
   void emSaveIsAuditedSuccessWithCallerError() throws Exception {
      DatabaseDefinition def = edit((JDBCDataSource) repository.getDataSource("aEdit"));
      def.setName("aEdited");
      ConnectionStatus status = service.saveDatabase(
         "aEdit", settings(def), ActionRecord.ACTION_NAME_EDIT, "Data Source/aEdit",
         "new Name:aEdited", principal);

      assertNull(status);
      assertEquals(1, records.size());
      ActionRecord record = records.get(0);
      assertEquals(ActionRecord.ACTION_STATUS_SUCCESS, record.getActionStatus());
      assertEquals("new Name:aEdited", record.getActionError());
      assertEquals("Data Source/aEdit", record.getObjectName());
      assertEquals(ActionRecord.ACTION_NAME_EDIT, record.getActionName());
      assertNotNull(repository.getDataSource("aEdited"));
   }

   @Test
   void thrownFailureIsAuditedFailureOnce() throws Exception {
      // an edit without WRITE on the data source throws a SecurityException
      when(security.checkPermission(any(), eq(ResourceType.DATA_SOURCE), anyString(),
                                    eq(ResourceAction.WRITE))).thenReturn(false);
      DatabaseDefinition def = edit((JDBCDataSource) repository.getDataSource("aEdit"));

      for(boolean em : new boolean[] { false, true }) {
         records.clear();
         Exception thrown = assertThrows(Exception.class, () -> {
            if(em) {
               service.saveDatabase("aEdit", settings(def), ActionRecord.ACTION_NAME_EDIT,
                                    "Data Source/aEdit", null, principal);
            }
            else {
               service.saveDatabase("aEdit", settings(def), ActionRecord.ACTION_NAME_EDIT,
                                    principal);
            }
         });

         assertEquals(1, records.size());
         ActionRecord record = records.get(0);
         assertEquals(ActionRecord.ACTION_STATUS_FAILURE, record.getActionStatus());
         assertEquals(thrown.getMessage(), record.getActionError());
         assertEquals(em ? "Data Source/aEdit" : "aEdit", record.getObjectName());
      }
   }

   @Test
   void recordKeepsUserAndOrganization() throws Exception {
      // the user and organization fields are the ones @Audited wrote before the save was
      // audited by hand
      Principal user = new SRPrincipal(
         new IdentityID("auditor", Organization.getDefaultOrganizationID()), new IdentityID[0],
         new String[0], Organization.getDefaultOrganizationID(),
         Tool.getSecureRandom().nextLong());
      DatabaseDefinition refused = edit(source("aDup"));
      DatabaseDefinition saved4 = edit(source("aUser4"));
      DatabaseDefinition saved6 = edit(source("aUser6"));

      service.saveDatabase("", settings(refused), ActionRecord.ACTION_NAME_CREATE, user);
      service.saveDatabase("", settings(refused), ActionRecord.ACTION_NAME_CREATE,
                           "Data Source/aDup", null, user);
      service.saveDatabase("", settings(saved4), ActionRecord.ACTION_NAME_CREATE, user);
      service.saveDatabase("", settings(saved6), ActionRecord.ACTION_NAME_CREATE,
                           "Data Source/aUser6", null, user);

      assertEquals(4, records.size());

      for(ActionRecord record : records) {
         assertEquals(user.getName(), record.getUserSessionID());
         assertEquals("auditor", record.getUserName());
         assertEquals(Organization.getDefaultOrganizationID(), record.getOrganizationId());
         assertEquals(Organization.getDefaultOrganizationID(), record.getResourceOrganization());
         assertEquals(Organization.getDefaultOrganizationName(),
                      record.getResourceOrganizationName());
      }
   }

   @Test
   void refusalThroughRealControllers() throws Exception {
      DatabaseDatasourcesController portal = new DatabaseDatasourcesController(
         service, null, null, mock(DataSourceService.class), repository);
      RepositoryDataSourcesController em = new RepositoryDataSourcesController(
         service, mock(ResourcePermissionService.class), mock(Config.class), repository);

      DatabaseDefinition def = edit(source("aDup"));
      ConnectionStatus status = portal.setDataSourceModel("", settings(def), principal);

      assertEquals("Duplicate", status.getStatus());
      assertEquals(1, records.size());
      assertEquals(ActionRecord.ACTION_STATUS_FAILURE, records.get(0).getActionStatus());
      assertEquals("Duplicate", records.get(0).getActionError());
      assertEquals("aDup", records.get(0).getObjectName());

      records.clear();
      def = edit((JDBCDataSource) repository.getDataSource("aRen"));
      def.setName("aRenTo");
      status = em.setDataSourceModel("aRen", false, settings(def), principal);

      assertEquals("Duplicate Folder", status.getStatus());
      assertEquals(1, records.size());
      ActionRecord record = records.get(0);
      assertEquals(ActionRecord.ACTION_STATUS_FAILURE, record.getActionStatus());
      assertEquals("Duplicate Folder", record.getActionError());
      assertEquals(ActionRecord.ACTION_NAME_EDIT, record.getActionName());
      assertTrue(record.getObjectName().endsWith("aRen"), record.getObjectName());
      assertNotNull(repository.getDataSource("aRen"));
   }

   private static DataSourceSettingsModel settings(DatabaseDefinition def) {
      return DataSourceSettingsModel.builder().uploadEnabled(false).dataSource(def)
         .additionalDataSources(new DatabaseDefinition[0]).build();
   }

   private void folder(String path) {
      registry.setDataSourceFolder(new DataSourceFolder(path, LocalDateTime.now(), null));
   }

   private void jdbc(String path) throws Exception {
      registry.setDataSource(source(path), false);
   }

   private static JDBCDataSource source(String name) {
      JDBCDataSource dataSource = new JDBCDataSource();
      dataSource.setName(name);
      dataSource.setCustom(true);
      dataSource.setCustomEditMode(true);
      dataSource.setDriver("org.apache.derby.jdbc.EmbeddedDriver");
      dataSource.setURL(URL);
      dataSource.setCustomUrl(URL);
      return dataSource;
   }

   private static DatabaseDefinition edit(JDBCDataSource dataSource) {
      return JDBCUtil.buildDatabaseDefinition(
         dataSource, JDBCUtil.getJDBCDatabaseType(CustomDatabaseType.TYPE));
   }
}
