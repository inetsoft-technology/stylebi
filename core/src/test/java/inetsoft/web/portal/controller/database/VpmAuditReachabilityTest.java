/*
 * This file is part of StyleBI.
 * Copyright (C) 2024  InetSoft Technology
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
package inetsoft.web.portal.controller.database;

import inetsoft.analytic.composition.ViewsheetService;
import inetsoft.sree.security.*;
import inetsoft.sree.security.SecurityException;
import inetsoft.test.*;
import inetsoft.uql.XPrincipal;
import inetsoft.uql.XRepository;
import inetsoft.uql.asset.*;
import inetsoft.uql.asset.sync.*;
import inetsoft.uql.erm.*;
import inetsoft.uql.service.DataSourceRegistry;
import inetsoft.util.audit.ActionRecord;
import inetsoft.util.audit.Audit;
import inetsoft.web.binding.service.DataRefModelFactoryService;
import inetsoft.web.composer.vs.controller.VSLayoutServiceProxy;
import inetsoft.web.portal.model.database.*;
import inetsoft.web.portal.model.database.events.*;
import inetsoft.web.viewsheet.Audited;
import inetsoft.web.viewsheet.EventAspect;
import inetsoft.web.viewsheet.EventAspectServiceProxy;
import inetsoft.web.viewsheet.model.RuntimeViewsheetRef;
import inetsoft.web.viewsheet.service.CoreLifecycleService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedStatic;
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.io.FileNotFoundException;
import java.lang.reflect.Method;
import java.security.Principal;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Charter A16 for the VPM routes (design 1.5): each Portal VPM write writes exactly ONE audit
 * record, with the object name and action it wrote before the {@code VpmService} extraction, and a
 * failed call is audited as a failure. The {@code @Audited} annotations moved from the
 * controller to the service, so the service runs behind the real {@code EventAspect} here, and
 * the controller must not be audited a second time.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, IntegrationTestConfiguration.class,
                                  VpmAuditReachabilityTest.DependencyStorageConfig.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
@Tag("integration")
class VpmAuditReachabilityTest {
   @Configuration
   static class DependencyStorageConfig {
      @Bean
      public DependencyStorageService dependencyStorageService() {
         return mock(DependencyStorageService.class);
      }
   }

   private static int count = 0;
   private final Principal bob = new XPrincipal(new IdentityID("bob", "host-org"));
   private final List<ActionRecord> records = new ArrayList<>();
   private MockedStatic<Audit> auditStatic;
   private String ds;
   private VPMController controller;
   private VpmService service;

   @BeforeEach
   void setUp() throws Exception {
      DataSourceRegistry.getRegistry().init();
      ds = "vpmaudit_" + (++count);
      DataSourceRegistry.getRegistry().clearCache();
      records.clear();

      Audit audit = mock(Audit.class);
      doAnswer(inv -> records.add(inv.getArgument(0)))
         .when(audit).auditAction(any(ActionRecord.class), any());
      auditStatic = mockStatic(Audit.class);
      auditStatic.when(Audit::getInstance).thenReturn(audit);

      XRepository repository = mock(XRepository.class);
      when(repository.getDataModel(anyString())).thenAnswer(i -> new XDataModel(ds));
      DataSourceService dataSourceService = mock(DataSourceService.class);
      when(dataSourceService.checkPermission(anyString(), any(ResourceAction.class),
                                             any(Principal.class)))
         .thenReturn(true);
      when(dataSourceService.getModelAssetEntry(any())).thenAnswer(i -> i.getArgument(0));
      EventAspect aspect = new EventAspect(
         mock(RuntimeViewsheetRef.class), mock(CoreLifecycleService.class),
         mock(ViewsheetService.class), mock(VSLayoutServiceProxy.class),
         mock(EventAspectServiceProxy.class));
      VpmService target = new VpmService(
         mock(DataRefModelFactoryService.class), dataSourceService, repository,
         mock(SecurityEngine.class), mock(DependencyHandler.class),
         mock(RenameTransformHandler.class));
      AspectJProxyFactory factory = new AspectJProxyFactory(target);
      factory.setProxyTargetClass(true);
      factory.addAspect(aspect);
      service = (VpmService) factory.getProxy();
      controller = new VPMController(
         null, null, dataSourceService, repository, mock(SecurityEngine.class),
         mock(DependencyHandler.class), mock(RenameTransformHandler.class), null, null, service);
   }

   @AfterEach
   void tearDown() {
      auditStatic.close();
   }

   @Test
   void theServiceMethodsCarryTheActionsTheControllerHadAndTheControllerCarriesNone() {
      assertAudited("create", ActionRecord.ACTION_NAME_CREATE);
      assertAudited("update", ActionRecord.ACTION_NAME_EDIT);
      assertAudited("rename", ActionRecord.ACTION_NAME_RENAME);

      for(Method method : VPMController.class.getDeclaredMethods()) {
         assertNull(method.getAnnotation(Audited.class),
                    method.getName() + " is audited on the controller as well as the service");
      }
   }

   @Test
   void everyPortalWriteWritesOneRecordNamedAsBefore() throws Exception {
      SaveVpmEvent create = new SaveVpmEvent();
      create.setDatabase(ds);
      create.setModel(definition("A"));
      controller.addModel(create, bob);
      assertRecords(record(ActionRecord.ACTION_NAME_CREATE, ds + "/A", true));

      records.clear();
      SaveVpmEvent update = new SaveVpmEvent();
      update.setDatabase(ds);
      update.setVpmName("A");
      update.setModel(definition("A"));
      controller.updateModel(update, bob);
      assertRecords(record(ActionRecord.ACTION_NAME_EDIT, ds + "/A", true));

      records.clear();
      RenameModelEvent rename = new RenameModelEvent();
      rename.setDatabase(ds);
      rename.setOldName("A");
      rename.setNewName("B");
      controller.renameModel(rename, bob);
      assertRecords(record(ActionRecord.ACTION_NAME_RENAME, ds + "/A", true));

      records.clear();
      RemoveDataModelEvent remove = new RemoveDataModelEvent();
      remove.setDatabase(ds);
      Vpm vpm = new Vpm();
      vpm.setName("B");
      remove.setItems(new ArrayList<>(List.of(vpm)));
      controller.removeModel(remove, bob);
      assertRecords(record(ActionRecord.ACTION_NAME_DELETE, ds + "/B", true));
   }

   @Test
   void aRefusedWriteIsAuditedAsAFailureOnce() {
      SaveVpmEvent update = new SaveVpmEvent();
      update.setDatabase(ds);
      update.setVpmName("missing");
      update.setModel(definition("missing"));
      assertThrows(FileNotFoundException.class, () -> controller.updateModel(update, bob));
      assertRecords(record(ActionRecord.ACTION_NAME_EDIT, ds + "/missing", false));
   }

   @Test
   void aBatchDeleteWritesOneRecordPerVpm() throws Exception {
      for(String name : List.of("X", "Y")) {
         SaveVpmEvent create = new SaveVpmEvent();
         create.setDatabase(ds);
         create.setModel(definition(name));
         controller.addModel(create, bob);
      }

      records.clear();
      service.delete(ds, List.of("X", "Y"), bob);
      assertEquals(2, records.size());
      assertEquals(Set.of(ds + "/X", ds + "/Y"),
                   new HashSet<>(records.stream().map(ActionRecord::getObjectName).toList()));
   }

   private void assertAudited(String name, String action) {
      Method method = Arrays.stream(VpmService.class.getDeclaredMethods())
         .filter(m -> m.getName().equals(name)).findFirst().orElseThrow();
      Audited audited = method.getAnnotation(Audited.class);
      assertNotNull(audited, name + " is not audited");
      assertEquals(action, audited.actionName());
      assertEquals(ActionRecord.OBJECT_TYPE_VIRTUAL_PRIVATE_MODEL, audited.objectType());
   }

   private void assertRecords(String[]... expectedRows) {
      List<String[]> expected = List.of(expectedRows);
      List<String[]> actual = records.stream()
         .map(r -> new String[] { r.getActionName(), r.getObjectName(), r.getActionStatus() })
         .toList();
      assertEquals(expected.size(), actual.size(), "records: " + describe(actual));
      assertEquals(describe(expected), describe(actual));

      for(ActionRecord r : records) {
         assertEquals(ActionRecord.OBJECT_TYPE_VIRTUAL_PRIVATE_MODEL, r.getObjectType());
      }
   }

   private static String[] record(String action, String object, boolean success) {
      return new String[] { action, object, success ? ActionRecord.ACTION_STATUS_SUCCESS :
         ActionRecord.ACTION_STATUS_FAILURE };
   }

   private static String describe(List<String[]> rows) {
      return rows.stream().map(Arrays::toString).toList().toString();
   }

   private static VPMDefinition definition(String name) {
      VPMDefinition model = new VPMDefinition();
      model.setName(name);
      model.setConditions(new ArrayList<>());
      return model;
   }
}
