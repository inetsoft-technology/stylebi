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
package inetsoft.web.admin.security;

/*
 * Bug #77136: setIdentity is the shared rename path of the EM, the REST API, the shell and
 * admin-chat, so it must reject a rename to a reserved or unsafe organization id before any
 * change. updateTaskSaveFiles must not move a system folder of external storage when the old or
 * new id is reserved (a legacy organization named "backup").
 */

import inetsoft.sree.internal.SUtil;
import inetsoft.sree.internal.cluster.Cluster;
import inetsoft.sree.security.*;
import inetsoft.storage.ExternalStorageService;
import inetsoft.storage.fs.FilesystemExternalStorageService;
import inetsoft.util.*;
import inetsoft.util.audit.ActionRecord;
import inetsoft.util.audit.Audit;
import inetsoft.util.config.InetsoftConfig;
import inetsoft.web.admin.schedule.model.ServerLocation;
import inetsoft.web.admin.schedule.model.ServerPathInfoModel;
import inetsoft.web.admin.security.user.EditOrganizationPaneModel;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.MockedStatic;
import org.mockito.quality.Strictness;
import org.slf4j.LoggerFactory;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.File;
import java.nio.file.*;
import java.security.Principal;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@Tag("core")
class IdentityServiceOrganizationIdTest {
   @BeforeEach
   void setUp() {
      sUtilStatic = mockStatic(SUtil.class, withSettings().strictness(Strictness.LENIENT));
      sUtilStatic.when(() -> SUtil.getActionRecord(any(Principal.class), anyString(), any(), anyString()))
         .thenReturn(mock(ActionRecord.class));
      sUtilStatic.when(SUtil::isMultiTenant).thenReturn(true);
      auditStatic = mockStatic(Audit.class, withSettings().strictness(Strictness.LENIENT));
      auditStatic.when(Audit::getInstance).thenReturn(mock(Audit.class));
      securityEngineStatic = mockStatic(SecurityEngine.class, withSettings().strictness(Strictness.LENIENT));
      InetsoftConfig config = mock(InetsoftConfig.class, withSettings().lenient());
      configStatic = mockStatic(InetsoftConfig.class, withSettings().strictness(Strictness.LENIENT));
      configStatic.when(InetsoftConfig::getInstance).thenReturn(config);

      SecurityEngine securityEngine = mock(SecurityEngine.class, withSettings().lenient());
      when(securityEngine.isSecurityEnabled()).thenReturn(false);
      externalStorageService = mock(ExternalStorageService.class);
      cluster = mock(Cluster.class);

      service = mock(IdentityService.class, withSettings().defaultAnswer(CALLS_REAL_METHODS));
      ReflectionTestUtils.setField(service, "securityEngine", securityEngine);
      ReflectionTestUtils.setField(service, "externalStorageService", externalStorageService);
      ReflectionTestUtils.setField(service, "cluster", cluster);
      ReflectionTestUtils.setField(service, "LOG", LoggerFactory.getLogger(IdentityService.class));

      eprovider = mock(EditableAuthenticationProvider.class, withSettings().lenient());
      principal = mock(Principal.class);
   }

   @AfterEach
   void tearDown() {
      sUtilStatic.close();
      auditStatic.close();
      securityEngineStatic.close();
      configStatic.close();
   }

   @ParameterizedTest
   @CsvSource({ "orgA,backup", "orgA,HEAPDUMP", "orgA,..", "orgA,a/b", "backup,status" })
   void setIdentity_renameToReservedOrUnsafeId_rejectedBeforeAnyChange(String oldId, String newId) {
      FSOrganization org = new FSOrganization(new IdentityID("Org A", oldId));
      EditOrganizationPaneModel model = orgModel("Org A", newId);

      MessageException thrown = assertThrows(MessageException.class,
                                             () -> service.setIdentity(org, model, eprovider, principal));

      assertEquals(Catalog.getCatalog().getString("em.security.reservedOrganizationID", newId),
                   thrown.getMessage());
      verify(eprovider, never()).getUsers();
      verify(eprovider, never()).setOrganization(any(), any());
      verify(eprovider, never()).copyOrganization(any(), any(), any(), any(), any(), any(), any(),
                                                  anyBoolean(), any());
      verifyNoInteractions(cluster);
   }

   @Test
   void setIdentity_legacyReservedIdUnchanged_nameEditStillSaved() throws Exception {
      FSOrganization org = new FSOrganization(new IdentityID("Backup Org", "backup"));
      when(eprovider.getOrganizationNames()).thenReturn(new String[]{ "Backup Org" });
      when(eprovider.getOrganization("backup")).thenReturn(org);

      service.setIdentity(org, orgModel("Backup Renamed", "backup"), eprovider, principal);

      verify(eprovider).setOrganization(eq("backup"), argThat(o -> "Backup Renamed".equals(o.getName())));
   }

   @Test
   void setIdentity_nullNewId_isNoChange() throws Exception {
      FSOrganization org = new FSOrganization(new IdentityID("Org A", "orgA"));
      EditOrganizationPaneModel model = EditOrganizationPaneModel.builder()
         .name("Org A").oldName("Org A").id(null).theme(null).build();

      try {
         service.setIdentity(org, model, eprovider, principal);
      }
      catch(MessageException e) {
         fail("a null id must not be checked as a rename: " + e.getMessage());
      }
      catch(Exception ignore) {
         // later steps of the rename are out of scope, only the id gate matters here
      }

      // the save went past the id gate
      verify(eprovider, atLeastOnce()).getUsers();
   }

   @ParameterizedTest
   @CsvSource({ "backup,orgB", "orgA,backup", "status,orgB", "orgA,HeapDump" })
   void updateTaskSaveFiles_reservedOldOrNewId_doesNotMoveFolder(String oldId, String newId)
      throws Exception
   {
      service.updateTaskSaveFiles(new FSOrganization(oldId), new FSOrganization(newId));

      verify(externalStorageService, never()).renameFolder(any(), any());
   }

   @Test
   void updateTaskSaveFiles_normalRename_movesFolder() throws Exception {
      service.updateTaskSaveFiles(new FSOrganization("orgA"), new FSOrganization("orgB"));

      verify(externalStorageService).renameFolder("orgA", "orgB");
   }

   // Bug #77137: with server.save.locations, the task save files of an organization are also in
   // location/orgId/user/..., which the rename left under the old id

   @Test
   void updateTaskSaveFiles_saveLocations_movesEachLocationFolder() throws Exception {
      useSaveLocations("reports", "archive/x/", "/var/reports", "reports", "ftp://host/dir|ftp");

      service.updateTaskSaveFiles(new FSOrganization("orgA"), new FSOrganization("orgB"));

      verify(externalStorageService).renameFolder("orgA", "orgB");
      verify(externalStorageService).renameFolder("reports/orgA", "reports/orgB");
      verify(externalStorageService).renameFolder("archive/x/orgA", "archive/x/orgB");
      verify(externalStorageService).renameFolder("/var/reports/orgA", "/var/reports/orgB");
      verifyNoMoreInteractions(externalStorageService);
   }

   @ParameterizedTest
   @CsvSource({
      // old or new id names the location folder, only the top folder is skipped
      "reports, orgB, reports, orgB, reports/reports, reports/orgB",
      "orgA, reports, orgA, reports, reports/orgA, reports/reports",
      "Reports, orgB, Reports, orgB, reports/Reports, reports/orgB",
      "var, orgB, var, orgB, /var/reports/var, /var/reports/orgB",
   })
   void updateTaskSaveFiles_idIsLocationFolder_skipsOnlyThatFolder(
      String oldId, String newId, String skippedFrom, String skippedTo, String movedFrom, String movedTo)
      throws Exception
   {
      useSaveLocations(oldId.equals("var") ? "/var/reports" : "reports");

      service.updateTaskSaveFiles(new FSOrganization(oldId), new FSOrganization(newId));

      verify(externalStorageService, never()).renameFolder(skippedFrom, skippedTo);
      verify(externalStorageService).renameFolder(movedFrom, movedTo);
      verifyNoMoreInteractions(externalStorageService);
   }

   @Test
   void updateTaskSaveFiles_nestedLocations_doesNotMoveTheInnerLocation() throws Exception {
      useSaveLocations("reports", "reports/archive");

      service.updateTaskSaveFiles(new FSOrganization("archive"), new FSOrganization("orgB"));

      verify(externalStorageService).renameFolder("archive", "orgB");
      verify(externalStorageService, never()).renameFolder("reports/archive", "reports/orgB");
      verify(externalStorageService).renameFolder("reports/archive/archive", "reports/archive/orgB");
      verifyNoMoreInteractions(externalStorageService);
   }

   @Test
   void updateTaskSaveFiles_oneFolderFails_movesTheOthersAndShowsOneMessage() throws Exception {
      useSaveLocations("reports", "archive");
      doThrow(new java.io.IOException("C:\\server\\storage\\orgA locked"))
         .when(externalStorageService).renameFolder("orgA", "orgB");
      doThrow(new java.io.IOException("locked"))
         .when(externalStorageService).renameFolder("reports/orgA", "reports/orgB");
      Tool.clearUserMessage();

      try {
         service.updateTaskSaveFiles(new FSOrganization("orgA"), new FSOrganization("orgB"));

         verify(externalStorageService).renameFolder("archive/orgA", "archive/orgB");
         UserMessage message = Tool.getUserMessage();
         assertNotNull(message);
         assertEquals(Catalog.getCatalog().getString("em.organization.renameIssue"),
                      message.getMessage());
      }
      finally {
         Tool.clearUserMessage();
      }
   }

   @Test
   void updateTaskSaveFiles_filesystem_movesOnlyTheOrganizationFiles() throws Exception {
      useSaveLocations("reports");
      Path base = Files.createDirectories(tempDir.resolve("base"));
      String[] files = {
         "orgA/alice/a.pdf", "reports/orgA/alice/b.pdf", "reports/orgC/carol/c.pdf"
      };

      for(String file : files) {
         Path path = base.resolve(file);
         Files.createDirectories(path.getParent());
         Files.writeString(path, file);
      }

      ReflectionTestUtils.setField(service, "externalStorageService",
                                   new FilesystemExternalStorageService(base));

      try(MockedStatic<FileSystemService> fsStatic = mockStatic(FileSystemService.class)) {
         FileSystemService fs = mock(FileSystemService.class);
         when(fs.getFile(anyString())).thenAnswer(i -> new File((String) i.getArgument(0)));
         when(fs.rename(any(), any()))
            .thenAnswer(i -> ((File) i.getArgument(0)).renameTo(i.getArgument(1)));
         fsStatic.when(FileSystemService::getInstance).thenReturn(fs);

         service.updateTaskSaveFiles(new FSOrganization("orgA"), new FSOrganization("orgB"));

         assertTrue(Files.exists(base.resolve("orgB/alice/a.pdf")));
         assertTrue(Files.exists(base.resolve("reports/orgB/alice/b.pdf")));
         assertTrue(Files.exists(base.resolve("reports/orgC/carol/c.pdf")));
         assertFalse(Files.exists(base.resolve("orgA")));
         assertFalse(Files.exists(base.resolve("reports/orgA")));

         // a rename to the location folder must not delete or move the other organizations
         service.updateTaskSaveFiles(new FSOrganization("orgB"), new FSOrganization("reports"));

         assertTrue(Files.exists(base.resolve("reports/orgC/carol/c.pdf")));
         assertTrue(Files.exists(base.resolve("reports/reports/alice/b.pdf")));
         verify(fs, never()).deleteFile(any(File.class));
      }
   }

   private void useSaveLocations(String... paths) {
      List<ServerLocation> locations = new ArrayList<>();

      for(String path : paths) {
         boolean ftp = path.endsWith("|ftp");
         String lpath = ftp ? path.substring(0, path.length() - 4) : path;
         locations.add(ServerLocation.builder().path(lpath).label(lpath)
                          .pathInfoModel(ServerPathInfoModel.builder().path(lpath).ftp(ftp).build())
                          .build());
      }

      sUtilStatic.when(SUtil::getServerLocations).thenReturn(locations);
   }

   private static EditOrganizationPaneModel orgModel(String name, String id) {
      return EditOrganizationPaneModel.builder()
         .name(name)
         .oldName(name.equals("Backup Renamed") ? "Backup Org" : name)
         .id(id)
         .theme(null)
         .build();
   }

   @TempDir
   Path tempDir;
   private IdentityService service;
   private EditableAuthenticationProvider eprovider;
   private ExternalStorageService externalStorageService;
   private Cluster cluster;
   private Principal principal;
   private MockedStatic<SUtil> sUtilStatic;
   private MockedStatic<Audit> auditStatic;
   private MockedStatic<SecurityEngine> securityEngineStatic;
   private MockedStatic<InetsoftConfig> configStatic;
}
