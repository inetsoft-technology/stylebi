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
package inetsoft.web.admin.ai.plugins;

import inetsoft.sree.security.SecurityException;
import inetsoft.sree.security.*;
import inetsoft.util.Tool;
import inetsoft.util.audit.AdminChangeRecord;
import inetsoft.util.audit.Audit;
import inetsoft.web.admin.content.plugins.PluginsService;
import inetsoft.web.admin.content.plugins.model.*;
import inetsoft.web.admin.upload.UploadFilesResponse;
import inetsoft.web.admin.upload.UploadService;
import inetsoft.web.admin.upload.UploadedFile;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.security.Principal;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@Tag("core")
@ExtendWith(MockitoExtension.class)
class AdminPluginServiceTest {
   @Mock private PluginsService pluginsService;
   @Mock private UploadService uploadService;
   @Mock private SecurityEngine securityEngine;
   @Mock private Principal principal;

   private AdminPluginService service;

   @BeforeEach
   void setup() {
      service = new AdminPluginService(pluginsService, uploadService, securityEngine);
   }

   // -------------------------------------------------------------------------
   // list
   // -------------------------------------------------------------------------

   @Test
   void listDelegatesToPluginsServiceGetModel() throws Exception {
      PluginsModel model = PluginsModel.builder().plugins(List.of()).build();
      when(pluginsService.getModel(principal)).thenReturn(model);

      assertSame(model, service.list(principal));
   }

   // -------------------------------------------------------------------------
   // upload
   // -------------------------------------------------------------------------

   @Test
   void uploadChecksUploadDriversPermissionAndDelegatesToUploadService() throws Exception {
      when(securityEngine.checkPermission(principal, ResourceType.UPLOAD_DRIVERS, "*",
                                           ResourceAction.ACCESS)).thenReturn(true);
      when(uploadService.add(anyList())).thenReturn("upload-123");
      MockMultipartFile file =
         new MockMultipartFile("file", "postgresql-driver.jar", "application/java-archive", new byte[]{1});

      Map<String, String> result = service.upload(file, principal);

      assertEquals("upload-123", result.get("uploadId"));
      assertEquals("postgresql-driver.jar", result.get("fileName"));

      ArgumentCaptor<List<UploadedFile>> captor = ArgumentCaptor.forClass(List.class);
      verify(uploadService).add(captor.capture());
      assertEquals(1, captor.getValue().size());
      assertEquals("postgresql-driver.jar", captor.getValue().get(0).fileName());
   }

   @Test
   void uploadRefusesWithoutUploadDriversPermission() throws Exception {
      when(securityEngine.checkPermission(principal, ResourceType.UPLOAD_DRIVERS, "*",
                                           ResourceAction.ACCESS)).thenReturn(false);
      MockMultipartFile file =
         new MockMultipartFile("file", "driver.jar", "application/java-archive", new byte[]{1});

      assertThrows(SecurityException.class, () -> service.upload(file, principal));
      verifyNoInteractions(uploadService);
   }

   @Test
   void uploadRejectsAWrongFileType() throws Exception {
      when(securityEngine.checkPermission(principal, ResourceType.UPLOAD_DRIVERS, "*",
                                           ResourceAction.ACCESS)).thenReturn(true);
      MockMultipartFile file =
         new MockMultipartFile("file", "notes.txt", "text/plain", new byte[]{1});

      IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
         () -> service.upload(file, principal));

      assertTrue(ex.getMessage().contains("file"), ex.getMessage());
      verifyNoInteractions(uploadService);
   }

   @Test
   void uploadRejectsAnEmptyFile() throws Exception {
      when(securityEngine.checkPermission(principal, ResourceType.UPLOAD_DRIVERS, "*",
                                           ResourceAction.ACCESS)).thenReturn(true);
      MockMultipartFile file =
         new MockMultipartFile("file", "driver.jar", "application/java-archive", new byte[0]);

      assertThrows(IllegalArgumentException.class, () -> service.upload(file, principal));
      verifyNoInteractions(uploadService);
   }

   // -------------------------------------------------------------------------
   // uploadMaven
   // -------------------------------------------------------------------------

   @Test
   void uploadMavenChecksUploadDriversPermissionAndDelegatesToUploadServiceAdd() throws Exception {
      when(securityEngine.checkPermission(principal, ResourceType.UPLOAD_DRIVERS, "*",
                                           ResourceAction.ACCESS)).thenReturn(true);
      UploadFilesResponse response = UploadFilesResponse.builder()
         .identifier("upload-456")
         .files(List.of("postgresql-42.7.3.jar", "checker-qual-3.42.0.jar"))
         .build();
      when(uploadService.add("org.postgresql:postgresql:42.7.3")).thenReturn(response);

      Map<String, Object> result = service.uploadMaven("org.postgresql:postgresql:42.7.3", principal);

      assertEquals("upload-456", result.get("uploadId"));
      assertEquals(List.of("postgresql-42.7.3.jar", "checker-qual-3.42.0.jar"),
                   result.get("fileNames"));
   }

   @Test
   void uploadMavenRefusesWithoutUploadDriversPermission() throws Exception {
      when(securityEngine.checkPermission(principal, ResourceType.UPLOAD_DRIVERS, "*",
                                           ResourceAction.ACCESS)).thenReturn(false);

      assertThrows(SecurityException.class,
         () -> service.uploadMaven("org.postgresql:postgresql:42.7.3", principal));
      verifyNoInteractions(uploadService);
   }

   @Test
   void uploadMavenPropagatesFileNotFoundExceptionForAnUnresolvableGav() throws Exception {
      when(securityEngine.checkPermission(principal, ResourceType.UPLOAD_DRIVERS, "*",
                                           ResourceAction.ACCESS)).thenReturn(true);
      when(uploadService.add("does.not:exist:1.0"))
         .thenThrow(new java.io.FileNotFoundException("does.not:exist:1.0"));

      assertThrows(java.io.FileNotFoundException.class,
         () -> service.uploadMaven("does.not:exist:1.0", principal));
   }

   @Test
   void uploadMavenRejectsAMalformedGavWithoutCallingUploadService() throws Exception {
      when(securityEngine.checkPermission(principal, ResourceType.UPLOAD_DRIVERS, "*",
                                           ResourceAction.ACCESS)).thenReturn(true);

      for(String badGav : new String[]{null, "", "  ", "org.postgresql", "org.postgresql:postgresql",
                                        "org.postgresql:postgresql:42.7.3:extra",
                                        "org.postgresql::42.7.3", ":postgresql:42.7.3",
                                        "org.postgresql:postgresql:"})
      {
         IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
            () -> service.uploadMaven(badGav, principal));
         assertTrue(ex.getMessage().contains("gav"), ex.getMessage());
      }

      verifyNoInteractions(uploadService);
   }

   // -------------------------------------------------------------------------
   // scan
   // -------------------------------------------------------------------------

   @Test
   void scanReturnsDriverClassesFound() throws Exception {
      when(uploadService.get("upload-123")).thenReturn(Optional.of(List.of()));
      when(pluginsService.scanDrivers("upload-123", principal))
         .thenReturn(List.of("org.postgresql.Driver"));

      DriverList result = service.scan("upload-123", principal);

      assertEquals(List.of("org.postgresql.Driver"), result.drivers());
   }

   @Test
   void scanRefusesLoudWhenUploadIdIsUnknown() throws Exception {
      when(uploadService.get("missing")).thenReturn(Optional.empty());

      IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
         () -> service.scan("missing", principal));

      assertTrue(ex.getMessage().contains("uploadId"), ex.getMessage());
      verifyNoInteractions(pluginsService);
   }

   @Test
   void scanRefusesLoudWhenNoDriverClassesAreFound() throws Exception {
      when(uploadService.get("upload-zip")).thenReturn(Optional.of(List.of()));
      when(pluginsService.scanDrivers("upload-zip", principal)).thenReturn(List.of());

      IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
         () -> service.scan("upload-zip", principal));

      assertTrue(ex.getMessage().contains("plugin zip"), ex.getMessage());
   }

   // -------------------------------------------------------------------------
   // install
   // -------------------------------------------------------------------------

   @Test
   void installRefusesWithoutAcknowledgeServerCodeExecution() {
      AdminInstallDriverOrPluginRequest request = new AdminInstallDriverOrPluginRequest();
      request.setUploadId("upload-123");
      request.setReviewOutcome("looks fine");
      request.setAcknowledgeServerCodeExecution(false);

      IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
         () -> service.install(request, principal));

      assertTrue(ex.getMessage().startsWith("acknowledgeServerCodeExecution"), ex.getMessage());
      verifyNoInteractions(pluginsService);
   }

   @Test
   void installRefusesWithoutReviewOutcome() {
      AdminInstallDriverOrPluginRequest request = new AdminInstallDriverOrPluginRequest();
      request.setUploadId("upload-123");
      request.setAcknowledgeServerCodeExecution(true);

      IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
         () -> service.install(request, principal));

      assertTrue(ex.getMessage().startsWith("reviewOutcome"), ex.getMessage());
      verifyNoInteractions(pluginsService);
   }

   @Test
   void installPluginZipPathCallsInstallPluginsThenReturnsRefreshedModel() throws Exception {
      AdminInstallDriverOrPluginRequest request = new AdminInstallDriverOrPluginRequest();
      request.setUploadId("upload-123");
      request.setAcknowledgeServerCodeExecution(true);
      request.setReviewOutcome("human approved");

      PluginsModel refreshed = PluginsModel.builder().plugins(List.of()).build();
      when(pluginsService.getModel(principal)).thenReturn(refreshed);

      PluginsModel result = service.install(request, principal);

      assertSame(refreshed, result);
      verify(pluginsService).installPlugins("upload-123", principal);
      verify(pluginsService, never()).createDriverPlugin(any(), any());
   }

   @Test
   void installDriverJarPathCallsCreateDriverPluginThenReturnsRefreshedModel() throws Exception {
      AdminInstallDriverOrPluginRequest request = new AdminInstallDriverOrPluginRequest();
      request.setUploadId("upload-123");
      request.setAcknowledgeServerCodeExecution(true);
      request.setReviewOutcome("human approved");
      AdminDriverPluginSpec spec = new AdminDriverPluginSpec();
      spec.setPluginId("mycompany.mydriver");
      spec.setPluginName("My Driver");
      spec.setPluginVersion("1.0.0");
      spec.setDrivers(List.of("com.mycompany.Driver"));
      request.setAsDriverPlugin(spec);

      PluginsModel refreshed = PluginsModel.builder().plugins(List.of()).build();
      when(pluginsService.getModel(principal)).thenReturn(refreshed);

      PluginsModel result = service.install(request, principal);

      assertSame(refreshed, result);
      ArgumentCaptor<CreateDriverPluginRequest> captor =
         ArgumentCaptor.forClass(CreateDriverPluginRequest.class);
      verify(pluginsService).createDriverPlugin(captor.capture(), eq(principal));
      assertEquals("upload-123", captor.getValue().uploadId());
      assertEquals("mycompany.mydriver", captor.getValue().pluginId());
      assertEquals(List.of("com.mycompany.Driver"), captor.getValue().drivers());
      verify(pluginsService, never()).installPlugins(any(), any());
   }

   @Test
   void installWritesAnAdminChangeAuditRecordCarryingTaskAndReviewOutcome() throws Exception {
      AdminInstallDriverOrPluginRequest request = new AdminInstallDriverOrPluginRequest();
      request.setUploadId("upload-123");
      request.setAcknowledgeServerCodeExecution(true);
      request.setReviewOutcome("human approved");
      request.setTask("install the new postgres driver");

      PluginsModel refreshed = PluginsModel.builder().plugins(List.of()).build();
      when(pluginsService.getModel(principal)).thenReturn(refreshed);
      when(principal.getName()).thenReturn("admin");

      Audit auditInstance = mock(Audit.class);

      try(MockedStatic<Audit> audit = mockStatic(Audit.class);
          MockedStatic<Tool> tool = mockStatic(Tool.class))
      {
         audit.when(Audit::getInstance).thenReturn(auditInstance);
         tool.when(Tool::getHost).thenReturn("test-host");
         service.install(request, principal);
      }

      ArgumentCaptor<AdminChangeRecord> captor = ArgumentCaptor.forClass(AdminChangeRecord.class);
      verify(auditInstance).auditAdminChange(captor.capture(), eq(principal));
      AdminChangeRecord record = captor.getValue();
      assertEquals("install the new postgres driver", record.getTaskDescription());
      assertEquals("human approved", record.getReviewOutcome());
      assertEquals("upload-123", record.getTransactionId());
      assertEquals(AdminChangeRecord.STATUS_VERIFIED, record.getStatus());
      assertEquals("admin", record.getUserName());
   }

   @Test
   void installWritesAFailedAdminChangeAuditRecordWhenTheInstallItselfThrows() throws Exception {
      AdminInstallDriverOrPluginRequest request = new AdminInstallDriverOrPluginRequest();
      request.setUploadId("upload-123");
      request.setAcknowledgeServerCodeExecution(true);
      request.setReviewOutcome("human approved");
      request.setTask("install the new postgres driver");

      doThrow(new RuntimeException("boom"))
         .when(pluginsService).installPlugins("upload-123", principal);

      Audit auditInstance = mock(Audit.class);

      try(MockedStatic<Audit> audit = mockStatic(Audit.class);
          MockedStatic<Tool> tool = mockStatic(Tool.class))
      {
         audit.when(Audit::getInstance).thenReturn(auditInstance);
         tool.when(Tool::getHost).thenReturn("test-host");
         assertThrows(RuntimeException.class, () -> service.install(request, principal));
      }

      ArgumentCaptor<AdminChangeRecord> captor = ArgumentCaptor.forClass(AdminChangeRecord.class);
      verify(auditInstance).auditAdminChange(captor.capture(), eq(principal));
      assertEquals(AdminChangeRecord.STATUS_FAILED, captor.getValue().getStatus());
      assertEquals("install the new postgres driver", captor.getValue().getTaskDescription());
   }

   // -------------------------------------------------------------------------
   // remove
   // -------------------------------------------------------------------------

   @Test
   void removeRefusesWithoutAcknowledgeIrreversibleRemove() {
      AdminRemoveDriverOrPluginRequest request = new AdminRemoveDriverOrPluginRequest();
      request.setPluginIds(List.of("some.plugin"));
      request.setReviewOutcome("looks fine");
      request.setAcknowledgeIrreversibleRemove(false);

      IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
         () -> service.remove(request, principal));

      assertTrue(ex.getMessage().startsWith("acknowledgeIrreversibleRemove"), ex.getMessage());
      verifyNoInteractions(pluginsService);
   }

   @Test
   void removeRefusesWithoutReviewOutcome() {
      AdminRemoveDriverOrPluginRequest request = new AdminRemoveDriverOrPluginRequest();
      request.setPluginIds(List.of("some.plugin"));
      request.setAcknowledgeIrreversibleRemove(true);

      IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
         () -> service.remove(request, principal));

      assertTrue(ex.getMessage().startsWith("reviewOutcome"), ex.getMessage());
      verifyNoInteractions(pluginsService);
   }

   @Test
   void removeResolvesRequestedIdsAgainstTheCurrentListThenUninstallsThem() throws Exception {
      AdminRemoveDriverOrPluginRequest request = new AdminRemoveDriverOrPluginRequest();
      request.setPluginIds(List.of("some.plugin"));
      request.setAcknowledgeIrreversibleRemove(true);
      request.setReviewOutcome("human approved");

      PluginModel installed = PluginModel.builder()
         .id("some.plugin").name("Some Plugin").version("1.0.0").readOnly(false).build();
      PluginsModel current = PluginsModel.builder().plugins(List.of(installed)).build();
      PluginsModel refreshed = PluginsModel.builder().plugins(List.of()).build();
      when(pluginsService.getModel(principal)).thenReturn(current, refreshed);

      PluginsModel result = service.remove(request, principal);

      assertSame(refreshed, result);
      ArgumentCaptor<PluginsModel> captor = ArgumentCaptor.forClass(PluginsModel.class);
      verify(pluginsService).uninstallPlugins(captor.capture(), eq(principal));
      assertEquals(List.of(installed), captor.getValue().plugins());
   }

   @Test
   void removeRefusesLoudWhenAPluginIdIsNotCurrentlyInstalled() throws Exception {
      AdminRemoveDriverOrPluginRequest request = new AdminRemoveDriverOrPluginRequest();
      request.setPluginIds(List.of("does.not.exist"));
      request.setAcknowledgeIrreversibleRemove(true);
      request.setReviewOutcome("human approved");

      when(pluginsService.getModel(principal))
         .thenReturn(PluginsModel.builder().plugins(List.of()).build());

      IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
         () -> service.remove(request, principal));

      assertTrue(ex.getMessage().contains("does.not.exist"), ex.getMessage());
      verify(pluginsService, never()).uninstallPlugins(any(), any());
   }

   @Test
   void removeWritesAnAdminChangeAuditRecordCarryingTaskAndReviewOutcome() throws Exception {
      AdminRemoveDriverOrPluginRequest request = new AdminRemoveDriverOrPluginRequest();
      request.setPluginIds(List.of("some.plugin"));
      request.setAcknowledgeIrreversibleRemove(true);
      request.setReviewOutcome("human approved");
      request.setTask("remove the unused mysql driver");

      PluginModel installed = PluginModel.builder()
         .id("some.plugin").name("Some Plugin").version("1.0.0").readOnly(false).build();
      PluginsModel current = PluginsModel.builder().plugins(List.of(installed)).build();
      PluginsModel refreshed = PluginsModel.builder().plugins(List.of()).build();
      when(pluginsService.getModel(principal)).thenReturn(current, refreshed);
      when(principal.getName()).thenReturn("admin");

      Audit auditInstance = mock(Audit.class);

      try(MockedStatic<Audit> audit = mockStatic(Audit.class);
          MockedStatic<Tool> tool = mockStatic(Tool.class))
      {
         audit.when(Audit::getInstance).thenReturn(auditInstance);
         tool.when(Tool::getHost).thenReturn("test-host");
         service.remove(request, principal);
      }

      ArgumentCaptor<AdminChangeRecord> captor = ArgumentCaptor.forClass(AdminChangeRecord.class);
      verify(auditInstance).auditAdminChange(captor.capture(), eq(principal));
      AdminChangeRecord record = captor.getValue();
      assertEquals("remove the unused mysql driver", record.getTaskDescription());
      assertEquals("human approved", record.getReviewOutcome());
      assertEquals("some.plugin", record.getProperty());
      assertEquals(AdminChangeRecord.STATUS_VERIFIED, record.getStatus());
      assertEquals("admin", record.getUserName());
   }

   @Test
   void removeWritesAFailedAdminChangeAuditRecordWhenUninstallItselfThrows() throws Exception {
      AdminRemoveDriverOrPluginRequest request = new AdminRemoveDriverOrPluginRequest();
      request.setPluginIds(List.of("some.plugin"));
      request.setAcknowledgeIrreversibleRemove(true);
      request.setReviewOutcome("human approved");
      request.setTask("remove the unused mysql driver");

      PluginModel installed = PluginModel.builder()
         .id("some.plugin").name("Some Plugin").version("1.0.0").readOnly(false).build();
      PluginsModel current = PluginsModel.builder().plugins(List.of(installed)).build();
      when(pluginsService.getModel(principal)).thenReturn(current);
      doThrow(new RuntimeException("boom"))
         .when(pluginsService).uninstallPlugins(any(), eq(principal));

      Audit auditInstance = mock(Audit.class);

      try(MockedStatic<Audit> audit = mockStatic(Audit.class);
          MockedStatic<Tool> tool = mockStatic(Tool.class))
      {
         audit.when(Audit::getInstance).thenReturn(auditInstance);
         tool.when(Tool::getHost).thenReturn("test-host");
         assertThrows(RuntimeException.class, () -> service.remove(request, principal));
      }

      ArgumentCaptor<AdminChangeRecord> captor = ArgumentCaptor.forClass(AdminChangeRecord.class);
      verify(auditInstance).auditAdminChange(captor.capture(), eq(principal));
      assertEquals(AdminChangeRecord.STATUS_FAILED, captor.getValue().getStatus());
      assertEquals("remove the unused mysql driver", captor.getValue().getTaskDescription());
   }
}
