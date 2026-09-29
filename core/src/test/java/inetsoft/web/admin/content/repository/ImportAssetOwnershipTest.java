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

import inetsoft.sree.internal.DeployManagerService;
import inetsoft.sree.internal.DeploymentInfo;
import inetsoft.sree.internal.cluster.Cluster;
import inetsoft.sree.internal.cluster.DistributedMap;
import inetsoft.sree.security.OrganizationManager;
import inetsoft.util.FileSystemService;
import inetsoft.util.ThreadPool;
import inetsoft.web.admin.content.repository.ImportAssetService.ImportAssetContext;
import inetsoft.web.admin.content.repository.model.BookmarkConflict;
import inetsoft.web.admin.content.repository.model.ExportedAssetsModel;
import inetsoft.web.admin.content.repository.model.ImportAssetResponse;
import inetsoft.web.admin.deploy.*;
import inetsoft.web.admin.model.FileData;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.AdditionalAnswers;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.Principal;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Bug #77308: the import contexts and background import results are shared by all
 * organizations and keyed only by an import id, so they may only be read, imported, cancelled
 * or polled by the user and org that uploaded the jar.
 */
@Tag("core")
class ImportAssetOwnershipTest {
   private static final String ORG_A = "orgA";
   private static final String ORG_B = "orgB";
   private static final String ID = "import-1";

   @TempDir
   Path tempDir;

   private Map<String, ImportAssetContext> backing;
   private DeployService deployService;
   private FileSystemService fileSystemService;
   private ImportAssetService service;
   private List<Runnable> jobs;
   private MockedStatic<OrganizationManager> orgManagerStatic;
   private MockedStatic<ThreadPool> threadPoolStatic;
   private MockedStatic<DeployManagerService> deployManagerStatic;
   private MockedConstruction<DeploymentInfo> deploymentInfoConstruction;

   private Principal owner;
   private Principal ownerUpperCaseOrg;
   private Principal foreignOrgAdmin;
   private Principal ownerInOtherOrg;

   private File unzipFolder;
   private ImportJarProperties properties;
   private PartialDeploymentJarInfo ownerInfo;
   private ImportAssetResponse ownerResponse;

   @BeforeEach
   @SuppressWarnings("unchecked")
   void setUp() throws Exception {
      backing = new HashMap<>();
      DistributedMap<String, ImportAssetContext> contexts =
         mock(DistributedMap.class, AdditionalAnswers.delegatesTo(backing));
      Cluster cluster = mock(Cluster.class);
      when(cluster.getMap(ImportAssetService.CACHE_NAME)).thenAnswer(inv -> contexts);

      owner = principal("owner~;~" + ORG_A);
      ownerUpperCaseOrg = principal("owner~;~" + ORG_A);
      foreignOrgAdmin = principal("admin~;~" + ORG_B);
      // same principal name, but currently managing another org (site admin switched org)
      ownerInOtherOrg = principal("owner~;~" + ORG_A);

      OrganizationManager orgManager = mock(OrganizationManager.class);
      when(orgManager.getCurrentOrgID(any())).thenAnswer(inv -> {
         Principal p = inv.getArgument(0);

         if(p == owner) {
            return ORG_A;
         }

         if(p == ownerUpperCaseOrg) {
            return ORG_A.toUpperCase();
         }

         return ORG_B;
      });
      orgManagerStatic = mockStatic(OrganizationManager.class);
      orgManagerStatic.when(OrganizationManager::getInstance).thenReturn(orgManager);

      // capture background jobs so that the test decides when they run
      jobs = new ArrayList<>();
      threadPoolStatic = mockStatic(ThreadPool.class);
      threadPoolStatic.when(() -> ThreadPool.addOnDemand(any(Runnable.class)))
         .thenAnswer(inv -> jobs.add(inv.getArgument(0)));

      ownerInfo = mock(PartialDeploymentJarInfo.class);
      deployManagerStatic = mockStatic(DeployManagerService.class);
      deployManagerStatic.when(() -> DeployManagerService.getInfo(anyString(), anyBoolean()))
         .thenAnswer(inv -> mock(PartialDeploymentJarInfo.class));
      deploymentInfoConstruction = mockConstruction(DeploymentInfo.class);

      unzipFolder = Files.createDirectory(tempDir.resolve("unzip")).toFile();
      properties = ImportJarProperties.builder()
         .unzipFolderPath(unzipFolder.getAbsolutePath())
         .build();

      ownerResponse = ImportAssetResponse.builder().addFailedAssets("orgA secret asset").build();
      deployService = mock(DeployService.class);
      when(deployService.importAsset(any(), any(), anyBoolean(), any(), same(owner), any()))
         .thenReturn(ownerResponse);
      when(deployService.getJarFileInfo(anyString(), any(), any(), any()))
         .thenReturn(mock(ExportedAssetsModel.class));
      when(deployService.getBookmarkConflicts(any(), any(), any()))
         .thenReturn(List.of(mock(BookmarkConflict.class)));

      fileSystemService = mock(FileSystemService.class);
      service = new ImportAssetService(deployService, cluster, fileSystemService);
   }

   @AfterEach
   void tearDown() {
      deploymentInfoConstruction.close();
      deployManagerStatic.close();
      threadPoolStatic.close();
      orgManagerStatic.close();
   }

   // ---- upload ----

   @Test
   void uploadStampsContextWithUploaderAndOrg() throws Exception {
      File temp = tempDir.resolve("import.zip").toFile();
      when(fileSystemService.getCacheTempFile("import", "zip")).thenReturn(temp);
      when(deployService.setJarFile(temp.getAbsolutePath(), false)).thenReturn(properties);
      FileData file = FileData.builder().name("import.jar")
         .content(Base64.getEncoder().encodeToString(new byte[] { 1, 2, 3 })).build();

      assertNotNull(service.setJarFile(ID, file, owner));

      ImportAssetContext context = backing.get(ID);
      assertTrue(context.isOwnedBy(owner));
      assertTrue(context.isOwnedBy(ownerUpperCaseOrg));
      assertFalse(context.isOwnedBy(foreignOrgAdmin));
      assertFalse(context.isOwnedBy(ownerInOtherOrg));
      verify(deployService).getJarFileInfo(eq(ID), any(), isNull(), same(owner));
   }

   @Test
   void unstampedContextIsNeverOwned() {
      ImportAssetContext context = new ImportAssetContext(ID, null, null);

      assertFalse(context.isOwnedBy(owner));
      assertFalse(context.isOwnedBy(null));
   }

   // ---- jar info ----

   @Test
   void foreignOrgCallerCannotReadJarInfo() {
      assertForeignJarInfoRefused(foreignOrgAdmin);
   }

   @Test
   void sameUserManagingAnotherOrgCannotReadJarInfo() {
      assertForeignJarInfoRefused(ownerInOtherOrg);
   }

   @Test
   void ownerOrgMatchIgnoresCase() throws Exception {
      putOwnerContext();

      assertNotNull(service.getJarFileInfo(ID, ownerUpperCaseOrg));
      verify(deployService).getJarFileInfo(eq(ID), any(), isNull(), same(ownerUpperCaseOrg));
   }

   // ---- bookmark conflicts ----

   @Test
   void foreignCallerGetsNoBookmarkConflicts() throws Exception {
      putOwnerContext();

      assertTrue(service.getBookmarkConflicts(ID, null, null, null, true, null, foreignOrgAdmin)
                    .isEmpty());
      assertTrue(service.getBookmarkConflicts(ID, null, null, null, true, null, ownerInOtherOrg)
                    .isEmpty());
      verify(deployService, never()).getBookmarkConflicts(any(), any(), any());

      assertEquals(1, service.getBookmarkConflicts(ID, null, null, null, true, null, owner).size());
   }

   // ---- import ----

   @Test
   void foreignOrgCallerCannotImportOwnersJar() throws Exception {
      assertForeignImportRefused(foreignOrgAdmin);
   }

   @Test
   void sameUserManagingAnotherOrgCannotImportOwnersJar() throws Exception {
      assertForeignImportRefused(ownerInOtherOrg);
   }

   @Test
   void foreignPollNeitherReadsNorConsumesOwnersResult() throws Exception {
      putOwnerContext();
      assertFalse(importAsset(owner).complete());
      assertEquals(1, jobs.size());

      // while the owner's job is still running
      assertForeignPollRefused(1);

      runJobs();

      // after the owner's job has finished
      assertForeignPollRefused(0);

      ImportAssetResponse response = importAsset(owner);
      assertSame(ownerResponse, response);

      // the owner consumed the result, so it is gone for the owner too
      assertTrue(importAsset(owner).failed());
   }

   // ---- cancel ----

   @Test
   void foreignCallerCannotCancelOwnersImport() throws Exception {
      putOwnerContext();

      service.finishImport(ID, foreignOrgAdmin);
      service.finishImport(ID, ownerInOtherOrg);

      assertSame(properties, backing.get(ID).getProperties());
      assertTrue(unzipFolder.exists());

      service.finishImport(ID, owner);

      assertFalse(backing.containsKey(ID));
      assertFalse(unzipFolder.exists());
   }

   private void assertForeignJarInfoRefused(Principal caller) {
      ImportAssetContext context = putOwnerContext();

      assertThrows(IllegalStateException.class, () -> service.getJarFileInfo(ID, caller));

      assertSame(context, backing.get(ID));
      assertSame(ownerInfo, context.getInfo());
      deployManagerStatic.verifyNoInteractions();
      verifyNoInteractions(deployService);
   }

   private void assertForeignImportRefused(Principal caller) throws Exception {
      ImportAssetContext context = putOwnerContext();

      assertTrue(importAsset(caller).failed());
      assertTrue(service.importAsset(ID, null, null, null, true, null, true, false, caller,
                                     Map.of()).failed());

      assertSame(context, backing.get(ID));
      assertTrue(jobs.isEmpty());
      verify(deployService, never()).importAsset(any(), any(), anyBoolean(), any(), any(), any());

      // the owner's import still works after the refusal
      assertFalse(importAsset(owner).complete());
      assertFalse(backing.containsKey(ID));
      runJobs();
      assertSame(ownerResponse, importAsset(owner));
   }

   private void assertForeignPollRefused(int pendingJobs) throws Exception {
      for(Principal caller : List.of(foreignOrgAdmin, ownerInOtherOrg)) {
         ImportAssetResponse response = importAsset(caller);
         assertTrue(response.failed());
         assertNotSame(ownerResponse, response);
         assertTrue(response.failedAssets().isEmpty());
      }

      // no job was started or replaced for the foreign caller
      assertEquals(pendingJobs, jobs.size());
      verify(deployService, never())
         .importAsset(any(), any(), anyBoolean(), any(), same(foreignOrgAdmin), any());
      verify(deployService, never())
         .importAsset(any(), any(), anyBoolean(), any(), same(ownerInOtherOrg), any());
   }

   private ImportAssetResponse importAsset(Principal caller) throws Exception {
      return service.importAsset(ID, null, null, null, true, null, true, true, caller, Map.of());
   }

   private ImportAssetContext putOwnerContext() {
      ImportAssetContext context = new ImportAssetContext(ID, owner.getName(), ORG_A);
      context.setProperties(properties);
      context.setInfo(ownerInfo);
      backing.put(ID, context);
      return context;
   }

   private void runJobs() {
      List<Runnable> pending = new ArrayList<>(jobs);
      jobs.clear();
      pending.forEach(Runnable::run);
   }

   private static Principal principal(String name) {
      Principal principal = mock(Principal.class);
      when(principal.getName()).thenReturn(name);
      return principal;
   }
}
