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
package inetsoft.sree.internal;

import inetsoft.report.LibManagerProvider;
import inetsoft.sree.RepletRegistryManager;
import inetsoft.sree.schedule.*;
import inetsoft.sree.security.*;
import inetsoft.sree.web.dashboard.DashboardManager;
import inetsoft.sree.web.dashboard.DashboardRegistryManager;
import inetsoft.uql.XPrincipal;
import inetsoft.uql.XRepository;
import inetsoft.uql.asset.AssetEntry;
import inetsoft.uql.asset.AssetRepository;
import inetsoft.uql.asset.DependencyHandler;
import inetsoft.uql.asset.EmbeddedTableStorage;
import inetsoft.uql.asset.internal.AssetFolder;
import inetsoft.uql.service.DataSourceRegistry;
import inetsoft.uql.util.Identity;
import inetsoft.uql.util.XSessionService;
import inetsoft.util.*;
import inetsoft.util.dep.ScheduleTaskAsset;
import inetsoft.util.dep.XAsset;
import inetsoft.util.dep.XAssetConfig;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import java.io.*;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.Principal;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Bug #77281: a deploy import (EM content repository import and the public file API) by a
 * caller that isn't a site admin must not trust the owner, execute-as identity, type,
 * removable flag and time ranges of the task xml. The owner and execute-as identity are moved
 * to the caller's org and then get the same check as the schedule task import. The ADMIN check
 * is granted on every user of the caller's org, including one that doesn't exist, like the real
 * org admin check (Bug #66393).
 */
@Tag("core")
class DeployManagerServiceScheduleTaskIdentityImportTest {
   @BeforeEach
   void setUp() throws Exception {
      sutil = mockStatic(SUtil.class, CALLS_REAL_METHODS);
      sutil.when(SUtil::isMultiTenant).thenReturn(true);
      sutil.when(() -> SUtil.getIdentity(any(), anyInt())).thenAnswer(inv -> {
         IdentityID id = inv.getArgument(0);
         int type = inv.getArgument(1);
         return id == null ? null : type == Identity.GROUP ? new Group(id) :
            type == Identity.ROLE ? new Role(id) : new User(id);
      });
      orgManager = mock(OrganizationManager.class);
      when(orgManager.getCurrentOrgID()).thenReturn(ORG_A);
      when(orgManager.getCurrentOrgID(any(Principal.class))).thenReturn(ORG_A);
      orgManagerStatic = mockStatic(OrganizationManager.class);
      orgManagerStatic.when(OrganizationManager::getInstance).thenReturn(orgManager);
      scheduleManager = mock(ScheduleManager.class);
      scheduleManagerStatic = mockStatic(ScheduleManager.class, CALLS_REAL_METHODS);
      scheduleManagerStatic.when(ScheduleManager::getScheduleManager).thenReturn(scheduleManager);

      provider = mock(SecurityProvider.class);
      Set<IdentityID> users = Set.of(CALLER, BOB, new IdentityID("admin", HOST_ORG));
      when(provider.getUser(any(IdentityID.class))).thenAnswer(
         inv -> users.contains(inv.<IdentityID>getArgument(0)) ?
            new User(inv.<IdentityID>getArgument(0)) : null);
      when(provider.getUsers()).thenReturn(users.toArray(new IdentityID[0]));
      when(provider.getGroups()).thenReturn(new IdentityID[] { STAFF });
      when(provider.checkPermission(any(), any(ResourceType.class), anyString(),
                                    eq(ResourceAction.ADMIN)))
         .thenAnswer(inv -> ORG_A.equals(
            IdentityID.getIdentityIDFromKey(inv.<String>getArgument(2)).getOrgID()));

      securityEngine = mock(SecurityEngine.class);
      when(securityEngine.isSecurityEnabled()).thenReturn(true);
      when(securityEngine.getSecurityProvider()).thenReturn(provider);
      when(securityEngine.checkPermission(any(), eq(ResourceType.SCHEDULER), eq("*"),
                                          eq(ResourceAction.ACCESS))).thenReturn(true);
      securityStatic = mockStatic(SecurityEngine.class);
      securityStatic.when(SecurityEngine::getSecurity).thenReturn(securityEngine);
      service = new DeployManagerService(
         securityEngine, mock(DependencyHandler.class), mock(DataSourceRegistry.class),
         mock(DashboardRegistryManager.class), mock(LibManagerProvider.class),
         mock(DashboardManager.class), mock(XRepository.class), mock(FileSystemService.class),
         mock(DataSpace.class), mock(EmbeddedTableStorage.class),
         mock(RepletRegistryManager.class));

      principal = mock(XPrincipal.class);
      when(principal.getName()).thenReturn(CALLER.convertToKey());
   }

   @AfterEach
   void tearDown() {
      securityStatic.close();
      scheduleManagerStatic.close();
      orgManagerStatic.close();
      sutil.close();
   }

   // C4a, a task owned by a missing user runs with the roles of the same-named site admin
   @Test
   void ownerNamingMissingUser_isRefusedAndNotImported() throws Exception {
      ScheduleTaskAsset asset = taskAsset();

      List<String> failed = importEntry(taskXml("admin~;~" + ORG_A, ""), asset);

      String expected = Catalog.getCatalog().getString(
         "em.import.file.failed.noPermission", ScheduleTaskAsset.SCHEDULETASK + " imported");
      assertEquals(List.of(expected), failed);
      verify(asset, never()).parseContent(any(InputStream.class), any(), anyBoolean(),
                                          anyBoolean());
      verify(asset, never()).setRestrictedImporter(any());
   }

   // C4, the real site admin of the host org as the owner is moved to the caller's org, where
   // no such user exists
   @Test
   void ownerIsForeignSiteAdmin_isRefused() throws Exception {
      assertFalse(service.isImportedScheduleTaskAllowed(
         write(taskXml("admin~;~" + HOST_ORG, "")), principal));
   }

   // A4, an owner of another org (e.g. a jar exported from another server) is remapped and then
   // checked, so an existing user of the caller's org of the same name is accepted
   @Test
   void ownerFromOtherOrg_isRemappedThenAllowed() throws Exception {
      assertTrue(service.isImportedScheduleTaskAllowed(write(taskXml("bob~;~orgb", "")),
                                                       principal));
   }

   // A4, a legacy "null" owner is the host org system user
   @Test
   void legacyNullOwner_isRefused() throws Exception {
      assertFalse(service.isImportedScheduleTaskAllowed(write(taskXml("null", "")), principal));
   }

   @Test
   void executeAsGlobalAdministratorRole_isRefused() throws Exception {
      assertFalse(service.isImportedScheduleTaskAllowed(write(taskXml(
         BOB.convertToKey(), "idname=\"Administrator~;~__GLOBAL__\" idtype=\"2\"")), principal));
   }

   @Test
   void executeAsMissingUser_isRefused() throws Exception {
      assertFalse(service.isImportedScheduleTaskAllowed(write(taskXml(
         BOB.convertToKey(), "idname=\"admin~;~" + HOST_ORG + "\" idtype=\"0\"")), principal));
   }

   @Test
   void internalTaskType_isRefused() throws Exception {
      assertFalse(service.isImportedScheduleTaskAllowed(
         write(taskXml(BOB.convertToKey(), "type=\"INTERNAL_TASK\"")), principal));
   }

   @Test
   void internalTaskContent_withoutWritePermission_isRefused() throws Exception {
      String xml = taskXml(BOB.convertToKey(), "")
         .replace("<Condition type=\"NeverRun\"/>",
                  "<Condition type=\"NeverRun\"/><Action type=\"AssetFileBackup\"/>");

      assertFalse(service.isImportedScheduleTaskAllowed(write(xml), principal));
   }

   // A5-style, the scheduler permission is checked against the caller, not the xml owner, and
   // regardless of the removable flag
   @Test
   void withoutSchedulerPermission_isRefused() throws Exception {
      when(securityEngine.checkPermission(any(), eq(ResourceType.SCHEDULER), eq("*"),
                                          eq(ResourceAction.ACCESS))).thenReturn(false);

      assertFalse(service.isImportedScheduleTaskAllowed(
         write(taskXml(BOB.convertToKey(), "removable=\"false\"")), principal));
   }

   @Test
   void administeredOwnerAndGroup_isImportedAsRestricted() throws Exception {
      ScheduleTaskAsset asset = taskAsset();

      List<String> failed = importEntry(
         taskXml(BOB.convertToKey(), "idname=\"" + STAFF.convertToKey() + "\" idtype=\"1\""),
         asset);

      assertEquals(List.of(), failed);
      verify(asset).setRestrictedImporter(principal);
      verify(asset).parseContent(any(InputStream.class), any(), eq(true), eq(false));
   }

   @Test
   void siteAdmin_isNotChecked() throws Exception {
      when(orgManager.isSiteAdmin(principal)).thenReturn(true);
      ScheduleTaskAsset asset = taskAsset();

      List<String> failed = importEntry(taskXml("admin~;~" + ORG_A, ""), asset);

      assertEquals(List.of(), failed);
      verify(asset, never()).setRestrictedImporter(any());
      verify(asset).parseContent(any(InputStream.class), any(), eq(true), eq(true));
   }

   @Test
   void securityDisabled_isNotChecked() throws Exception {
      when(securityEngine.isSecurityEnabled()).thenReturn(false);
      ScheduleTaskAsset asset = taskAsset();

      List<String> failed = importEntry(taskXml("admin~;~" + ORG_A, ""), asset);

      assertEquals(List.of(), failed);
      verify(asset, never()).setRestrictedImporter(any());
   }

   // A5, the check applies whenever security is on, not only in a multi-tenant install: a
   // non-site-admin importer of a single-tenant install can't make a task run as the global
   // system administrator role
   @Test
   void notMultiTenant_nonSiteAdminImporter_isStillChecked() throws Exception {
      sutil.when(SUtil::isMultiTenant).thenReturn(false);
      ScheduleTaskAsset asset = taskAsset();

      List<String> failed = importEntry(taskXml(
         BOB.convertToKey(), "idname=\"Administrator~;~__GLOBAL__\" idtype=\"2\""), asset);

      String expected = Catalog.getCatalog().getString(
         "em.import.file.failed.noPermission", ScheduleTaskAsset.SCHEDULETASK + " imported");
      assertEquals(List.of(expected), failed);
      verify(asset, never()).parseContent(any(InputStream.class), any(), anyBoolean(),
                                          anyBoolean());
   }

   // the content is parsed the same way as it was checked, and the flags only the server sets
   // and the global time ranges are not taken from the file
   @Test
   void restrictedParseContent_remapsOwnerAndIgnoresFlagsAndTimeRanges() throws Exception {
      try(MockedStatic<IndexedStorage> storage = mockStatic(IndexedStorage.class);
          MockedStatic<TimeRange> timeRange = mockStatic(TimeRange.class, CALLS_REAL_METHODS);
          MockedStatic<XSessionService> sessions = mockStatic(XSessionService.class))
      {
         // the owner principal parseContent stores the task with needs a session id
         sessions.when(XSessionService::getService).thenReturn(mock(XSessionService.class));
         storage.when(IndexedStorage::getIndexedStorage).thenReturn(mock(IndexedStorage.class));
         timeRange.when(TimeRange::getTimeRanges).thenReturn(new ArrayList<>());
         ScheduleTaskAsset asset = new ScheduleTaskAsset();
         asset.setRestrictedImporter(principal);
         String xml = taskXml("bob~;~orgb", "removable=\"false\" editable=\"false\"")
            .replace("</scheduleTask>", "<timeRanges><timeRange name=\"Night\" " +
               "startTime=\"00:00:00\" endTime=\"06:00:00\" defaultRange=\"false\"/>" +
               "</timeRanges></scheduleTask>");

         asset.parseContent(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)),
                            null, true, false);

         ArgumentCaptor<ScheduleTask> stored = ArgumentCaptor.forClass(ScheduleTask.class);
         verify(scheduleManager).setScheduleTask(anyString(), stored.capture(), isNull(),
                                                 any(Principal.class),
                                                 // Bug #77972, saved with the replaced task as the baseline
                                                 nullable(ScheduleTask.class));
         assertEquals(BOB, stored.getValue().getOwner());
         assertTrue(stored.getValue().isRemovable());
         assertTrue(stored.getValue().isEditable());
         timeRange.verify(() -> TimeRange.setTimeRanges(any()), never());
      }
   }

   @Test
   void restrictedParseContent_refusesInternalType() throws Exception {
      try(MockedStatic<IndexedStorage> storage = mockStatic(IndexedStorage.class)) {
         storage.when(IndexedStorage::getIndexedStorage).thenReturn(mock(IndexedStorage.class));
         ScheduleTaskAsset asset = new ScheduleTaskAsset();
         asset.setRestrictedImporter(principal);
         String xml = taskXml(BOB.convertToKey(), "type=\"INTERNAL_TASK\"");

         assertThrows(inetsoft.sree.security.SecurityException.class, () -> asset.parseContent(
            new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)), null, true, false));
         verify(scheduleManager, never()).setScheduleTask(anyString(), any(ScheduleTask.class),
                                                          any(), any(Principal.class));
      }
   }

   // Bug #77406, the folder owner of the file isn't checked, a new folder gets no owner, like a
   // folder created in the UI
   @Test
   void restrictedParseContent_createsFoldersWithoutOwner() throws Exception {
      // Bug #77454, the importer may create the folders
      when(securityEngine.checkPermission(any(), eq(ResourceType.SCHEDULE_TASK_FOLDER),
                                          anyString(), eq(ResourceAction.WRITE))).thenReturn(true);
      Map<String, XMLSerializable> store = importFolders(principal, false);

      assertNull(((AssetFolder) store.get(folderId("F"))).getOwner());
      assertNull(((AssetFolder) store.get(folderId("F/G"))).getOwner());
   }

   // Bug #77454, the folder owner is moved to the current organization, like the task owner
   @Test
   void siteAdminParseContent_remapsFolderOwnerToCurrentOrg() throws Exception {
      Map<String, XMLSerializable> store = importFolders(null, true);

      IdentityID owner = new IdentityID("admin", ORG_A);
      assertEquals(owner, ((AssetFolder) store.get(folderId("F"))).getOwner());
      assertEquals(owner, ((AssetFolder) store.get(folderId("F/G"))).getOwner());
   }

   private Map<String, XMLSerializable> importFolders(Principal restrictedImporter,
                                                      boolean isSiteAdmin)
      throws Exception
   {
      Map<String, XMLSerializable> store = new HashMap<>();
      store.put(folderId("/"), new AssetFolder());
      IndexedStorage indexedStorage = mock(IndexedStorage.class);
      when(indexedStorage.getXMLSerializable(anyString(), any()))
         .thenAnswer(inv -> store.get(inv.<String>getArgument(0)));
      when(indexedStorage.contains(anyString()))
         .thenAnswer(inv -> store.containsKey(inv.<String>getArgument(0)));
      doAnswer(inv -> store.put(inv.getArgument(0), inv.getArgument(1)))
         .when(indexedStorage).putXMLSerializable(anyString(), any());

      try(MockedStatic<IndexedStorage> storage = mockStatic(IndexedStorage.class);
          MockedStatic<XSessionService> sessions = mockStatic(XSessionService.class))
      {
         sessions.when(XSessionService::getService).thenReturn(mock(XSessionService.class));
         storage.when(IndexedStorage::getIndexedStorage).thenReturn(indexedStorage);
         ScheduleTaskAsset asset = new ScheduleTaskAsset();
         asset.setRestrictedImporter(restrictedImporter);
         String folderOwner = new IdentityID("admin", HOST_ORG).convertToKey();
         String xml = taskXml(BOB.convertToKey(), "")
            .replace("path=\"/\"", "path=\"F/G\"")
            .replace("<scheduleTask>", "<scheduleTask><folders>" +
               "<Folder path=\"F\" owner=\"" + folderOwner + "\"></Folder>" +
               "<Folder path=\"F/G\" owner=\"" + folderOwner + "\"></Folder></folders>");

         asset.parseContent(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)),
                            null, true, isSiteAdmin);
      }

      assertNotNull(store.get(folderId("F")));
      assertNotNull(store.get(folderId("F/G")));
      return store;
   }

   private static String folderId(String path) {
      return new AssetEntry(AssetRepository.GLOBAL_SCOPE, AssetEntry.Type.SCHEDULE_TASK_FOLDER,
                            path, null).toIdentifier();
   }

   private ScheduleTaskAsset taskAsset() throws Exception {
      ScheduleTaskAsset asset = mock(ScheduleTaskAsset.class);
      when(asset.getType()).thenReturn(ScheduleTaskAsset.SCHEDULETASK);
      when(asset.getPath()).thenReturn("imported");
      // a new task has no parent security resource, so no deploy permission check runs
      when(asset.exists()).thenReturn(false);
      return asset;
   }

   /**
    * Imports one SCHEDULETASK entry through the private per-asset import step.
    */
   private List<String> importEntry(String content, ScheduleTaskAsset asset) throws Exception {
      File file = write(content);
      Map<String, String> names = new HashMap<>();
      names.put(file.getName(), ScheduleTaskAsset.SCHEDULETASK + "_" +
         ScheduleTaskAsset.class.getName() + "^imported^" + XAsset.NULL);
      DeploymentInfo info = mock(DeploymentInfo.class);
      when(info.getNames()).thenReturn(names);
      XAssetConfig config = new XAssetConfig();
      config.setOverwriting(true);
      List<String> failed = new ArrayList<>();

      Method method = Arrays.stream(DeployManagerService.class.getDeclaredMethods())
         .filter(m -> m.getName().equals("importAsset") && m.getParameterCount() == 14)
         .findFirst().orElseThrow();
      method.setAccessible(true);
      method.invoke(service, file, asset, new ArrayList<>(), failed, null, new ArrayList<>(),
                    new ArrayList<>(), true, null, info, false, config, null, principal);
      return failed;
   }

   private File write(String content) throws Exception {
      File file = tempDir.resolve("f" + (++fileCount)).toFile();
      Files.writeString(file.toPath(), content, StandardCharsets.UTF_8);
      return file;
   }

   private static String taskXml(String owner, String attrs) {
      return "<?xml version=\"1.0\" encoding=\"UTF-8\" ?><scheduleTask><Task name=\"imported\"" +
         " owner=\"" + owner + "\" enabled=\"true\" path=\"/\" " + attrs + ">" +
         "<Condition type=\"NeverRun\"/></Task></scheduleTask>";
   }

   private static final String ORG_A = "orga";
   private static final String HOST_ORG = Organization.getDefaultOrganizationID();
   private static final IdentityID CALLER = new IdentityID("oa", ORG_A);
   private static final IdentityID BOB = new IdentityID("bob", ORG_A);
   private static final IdentityID STAFF = new IdentityID("staff", ORG_A);

   @TempDir
   Path tempDir;
   private int fileCount;
   private MockedStatic<SUtil> sutil;
   private MockedStatic<OrganizationManager> orgManagerStatic;
   private MockedStatic<ScheduleManager> scheduleManagerStatic;
   private MockedStatic<SecurityEngine> securityStatic;
   private OrganizationManager orgManager;
   private ScheduleManager scheduleManager;
   private SecurityProvider provider;
   private SecurityEngine securityEngine;
   private DeployManagerService service;
   private XPrincipal principal;
}
