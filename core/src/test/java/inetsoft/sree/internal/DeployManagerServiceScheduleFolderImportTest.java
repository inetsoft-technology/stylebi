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
 * Bug #77454, the schedule folders of a deploy import of a schedule task:
 * <ul>
 *    <li>an importer that isn't a site admin (oa@orga) needs the schedule task folder WRITE
 *    permission on the parent of each folder that doesn't exist yet, an existing folder needs
 *    none;</li>
 *    <li>no folder is written before the task is accepted, and a file without a task writes
 *    nothing;</li>
 *    <li>a site admin import moves a folder owner to the current organization.</li>
 * </ul>
 * The deploy path runs through the real per-asset import step with a real ScheduleTaskAsset,
 * a mocked asset would hide the folder writes.
 */
@Tag("core")
class DeployManagerServiceScheduleFolderImportTest {
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
      OrganizationManager orgManager = mock(OrganizationManager.class);
      when(orgManager.getCurrentOrgID()).thenReturn(ORG_A);
      when(orgManager.getCurrentOrgID(any(Principal.class))).thenReturn(ORG_A);
      orgManagerStatic = mockStatic(OrganizationManager.class);
      orgManagerStatic.when(OrganizationManager::getInstance).thenReturn(orgManager);
      scheduleManager = mock(ScheduleManager.class);
      scheduleManagerStatic = mockStatic(ScheduleManager.class, CALLS_REAL_METHODS);
      scheduleManagerStatic.when(ScheduleManager::getScheduleManager).thenReturn(scheduleManager);

      SecurityProvider provider = mock(SecurityProvider.class);
      Set<IdentityID> users = Set.of(CALLER, BOB, new IdentityID("admin", HOST_ORG));
      when(provider.getUser(any(IdentityID.class))).thenAnswer(
         inv -> users.contains(inv.<IdentityID>getArgument(0)) ?
            new User(inv.<IdentityID>getArgument(0)) : null);
      when(provider.getUsers()).thenReturn(users.toArray(new IdentityID[0]));
      when(provider.getGroups()).thenReturn(new IdentityID[0]);
      when(provider.checkPermission(any(), any(ResourceType.class), anyString(),
                                    eq(ResourceAction.ADMIN)))
         .thenAnswer(inv -> ORG_A.equals(
            IdentityID.getIdentityIDFromKey(inv.<String>getArgument(2)).getOrgID()));

      securityEngine = mock(SecurityEngine.class);
      when(securityEngine.isSecurityEnabled()).thenReturn(true);
      when(securityEngine.getSecurityProvider()).thenReturn(provider);
      when(securityEngine.checkPermission(any(), eq(ResourceType.SCHEDULER), eq("*"),
                                          eq(ResourceAction.ACCESS))).thenReturn(true);
      // the caller has the folder WRITE permission only on the paths in writableFolders
      when(securityEngine.checkPermission(any(), eq(ResourceType.SCHEDULE_TASK_FOLDER),
                                          anyString(), eq(ResourceAction.WRITE)))
         .thenAnswer(inv -> writableFolders.contains(inv.<String>getArgument(2)));
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

      store.put(folderId("/"), new AssetFolder());
      IndexedStorage indexedStorage = mock(IndexedStorage.class);
      when(indexedStorage.getXMLSerializable(anyString(), any()))
         .thenAnswer(inv -> store.get(inv.<String>getArgument(0)));
      when(indexedStorage.contains(anyString()))
         .thenAnswer(inv -> store.containsKey(inv.<String>getArgument(0)));
      doAnswer(inv -> store.put(inv.getArgument(0), inv.getArgument(1)))
         .when(indexedStorage).putXMLSerializable(anyString(), any());
      storageStatic = mockStatic(IndexedStorage.class);
      storageStatic.when(IndexedStorage::getIndexedStorage).thenReturn(indexedStorage);
      sessionsStatic = mockStatic(XSessionService.class);
      sessionsStatic.when(XSessionService::getService).thenReturn(mock(XSessionService.class));
   }

   @AfterEach
   void tearDown() {
      sessionsStatic.close();
      storageStatic.close();
      securityStatic.close();
      scheduleManagerStatic.close();
      orgManagerStatic.close();
      sutil.close();
   }

   // 2a, a valid task in a new folder F, the caller has no WRITE on the root
   @Test
   void restrictedImport_newFolderWithoutWrite_isRefused() throws Exception {
      List<String> failed = importEntry(taskInFolders("F", "F"));

      assertEquals(1, failed.size(), "the refusal is reported: " + failed);
      assertFalse(store.containsKey(folderId("F")), "F was created without WRITE on the root");
      assertEquals(0, root().getEntries().length);
      verify(scheduleManager, never()).setScheduleTask(anyString(), any(ScheduleTask.class),
                                                       any(), any(Principal.class));
   }

   @Test
   void restrictedImport_newFolderWithWrite_isCreated() throws Exception {
      writableFolders.add("/");

      List<String> failed = importEntry(taskInFolders("F", "F"));

      assertEquals(List.of(), failed);
      assertTrue(store.containsKey(folderId("F")));
      assertTrue(root().containsEntry(folder("F")));
      verify(scheduleManager, atLeastOnce()).setScheduleTask(anyString(), any(ScheduleTask.class),
                                                             any(), any(Principal.class));
   }

   // each missing folder of a chain needs WRITE on its own parent, nothing is written when one
   // of them is refused
   @Test
   void restrictedImport_missingChain_needsWriteOnEachParent() throws Exception {
      writableFolders.add("/");

      List<String> failed = importEntry(taskInFolders("X/Y", "X", "X/Y"));

      assertEquals(1, failed.size(), "the refusal is reported: " + failed);
      assertFalse(store.containsKey(folderId("X")), "X was written before X/Y was refused");
      assertFalse(store.containsKey(folderId("X/Y")));

      writableFolders.add("X");
      failed = importEntry(taskInFolders("X/Y", "X", "X/Y"));

      assertEquals(List.of(), failed);
      assertTrue(store.containsKey(folderId("X")));
      assertTrue(store.containsKey(folderId("X/Y")));
   }

   // a new folder under an existing folder needs WRITE on that folder, WRITE on the root isn't
   // enough, and the existing folder isn't changed when the import is refused
   @Test
   void restrictedImport_newFolderUnderUnwritableExistingFolder_isRefused() throws Exception {
      AssetFolder x = new AssetFolder();
      store.put(folderId("X"), x);
      root().addEntry(folder("X"));
      writableFolders.add("/");

      List<String> failed = importEntry(taskInFolders("X/Y", "X", "X/Y"));

      assertEquals(1, failed.size(), "the refusal is reported: " + failed);
      assertFalse(store.containsKey(folderId("X/Y")));
      assertSame(x, store.get(folderId("X")));
      assertEquals(0, x.getEntries().length);
   }

   // amendments 2-i and 2-ii, a round trip into an org where the folder already exists needs no
   // WRITE on its parent and none on the existing destination folder
   @Test
   void restrictedImport_existingFolderWithoutWrite_isImported() throws Exception {
      AssetFolder existing = new AssetFolder();
      store.put(folderId("F"), existing);
      root().addEntry(folder("F"));

      List<String> failed = importEntry(taskInFolders("F", "F"));

      assertEquals(List.of(), failed);
      assertSame(existing, store.get(folderId("F")));
      verify(scheduleManager, atLeastOnce()).setScheduleTask(anyString(), any(ScheduleTask.class),
                                                             any(), any(Principal.class));
   }

   // 2b, a file with folders and no task skipped every task check, the scheduler permission too
   @Test
   void restrictedImport_folderOnlyFile_isRefusedAndWritesNothing() throws Exception {
      when(securityEngine.checkPermission(any(), eq(ResourceType.SCHEDULER), eq("*"),
                                          eq(ResourceAction.ACCESS))).thenReturn(false);
      writableFolders.add("/");

      List<String> failed = importEntry(FOLDER_ONLY);

      assertEquals(1, failed.size(), "the refusal is reported: " + failed);
      assertFalse(store.containsKey(folderId("X")));
      assertFalse(store.containsKey(folderId("X/Y")));
   }

   // a file without a task writes no folders for a site admin either
   @Test
   void siteAdminParseContent_folderOnlyFile_writesNothing() {
      ScheduleTaskAsset asset = new ScheduleTaskAsset();

      assertThrows(IOException.class, () -> asset.parseContent(
         new ByteArrayInputStream(FOLDER_ONLY.getBytes(StandardCharsets.UTF_8)), null, true,
         true));
      assertFalse(store.containsKey(folderId("X")));
      assertFalse(store.containsKey(folderId("X/Y")));
   }

   // 2c, inside parseContent the internal task is refused before any folder is written
   @Test
   void restrictedParseContent_internalTask_writesNoFolder() {
      writableFolders.add("/");
      ScheduleTaskAsset asset = new ScheduleTaskAsset();
      asset.setRestrictedImporter(principal);
      String xml = taskXml(BOB.convertToKey(), "type=\"INTERNAL_TASK\"", "I", "I");

      assertThrows(inetsoft.sree.security.SecurityException.class, () -> asset.parseContent(
         new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)), null, true, false));
      assertFalse(store.containsKey(folderId("I")),
                  "folder I was written before the internal task was refused");
   }

   // 2d, through the import step the internal task is refused by the pre-check
   @Test
   void restrictedImport_internalTask_isRefusedBeforeFolders() throws Exception {
      writableFolders.add("/");
      String xml = taskXml(BOB.convertToKey(), "type=\"INTERNAL_TASK\"", "I", "I");

      List<String> failed = importEntry(xml);

      assertEquals(1, failed.size());
      assertFalse(store.containsKey(folderId("I")));
   }

   // 3, a site admin imports into orga a file exported from orgb, the folder owner is moved to
   // orga like the task owner
   @Test
   void siteAdminParseContent_foreignFolderOwner_isRemappedToCurrentOrg() throws Exception {
      String foreign = new IdentityID("bob", "orgb").convertToKey();
      String xml = taskXml(foreign, "", "F", "F")
         .replace("<Folder path=\"F\"/>", "<Folder path=\"F\" owner=\"" + foreign + "\"/>");

      new ScheduleTaskAsset().parseContent(
         new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)), null, true, true);

      ArgumentCaptor<ScheduleTask> stored = ArgumentCaptor.forClass(ScheduleTask.class);
      verify(scheduleManager, atLeastOnce()).setScheduleTask(anyString(), stored.capture(), any(),
                                                             any(Principal.class));
      assertEquals(ORG_A, stored.getValue().getOwner().getOrgID());
      assertEquals(new IdentityID("bob", ORG_A),
                   ((AssetFolder) store.get(folderId("F"))).getOwner());
   }

   @Test
   void siteAdminParseContent_sameOrgFolderOwner_isKept() throws Exception {
      String xml = taskXml(BOB.convertToKey(), "", "F", "F")
         .replace("<Folder path=\"F\"/>",
                  "<Folder path=\"F\" owner=\"" + BOB.convertToKey() + "\"/>");

      new ScheduleTaskAsset().parseContent(
         new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)), null, true, true);

      assertEquals(BOB, ((AssetFolder) store.get(folderId("F"))).getOwner());
   }

   @Test
   void siteAdminParseContent_folderWithoutOwner_staysWithoutOwner() throws Exception {
      new ScheduleTaskAsset().parseContent(
         new ByteArrayInputStream(taskInFolders("F", "F").getBytes(StandardCharsets.UTF_8)),
         null, true, true);

      assertNull(((AssetFolder) store.get(folderId("F"))).getOwner());
   }

   private List<String> importEntry(String content) throws Exception {
      ScheduleTaskAsset asset = new ScheduleTaskAsset();
      // exists() needs a task path
      asset.parseIdentifier("imported", null);
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

   private AssetFolder root() {
      return (AssetFolder) store.get(folderId("/"));
   }

   private static AssetEntry folder(String path) {
      return new AssetEntry(AssetRepository.GLOBAL_SCOPE, AssetEntry.Type.SCHEDULE_TASK_FOLDER,
                            path, null);
   }

   private static String folderId(String path) {
      return folder(path).toIdentifier();
   }

   private static String taskInFolders(String taskPath, String... folders) {
      return taskXml(BOB.convertToKey(), "", taskPath, folders);
   }

   private static String taskXml(String owner, String attrs, String taskPath, String... folders) {
      StringBuilder folderXml = new StringBuilder("<folders>");

      for(String folder : folders) {
         folderXml.append("<Folder path=\"").append(folder).append("\"/>");
      }

      folderXml.append("</folders>");
      return "<?xml version=\"1.0\" encoding=\"UTF-8\" ?><scheduleTask>" + folderXml +
         "<Task name=\"imported\" owner=\"" + owner + "\" enabled=\"true\" path=\"" + taskPath +
         "\" " + attrs + "><Condition type=\"NeverRun\"/></Task></scheduleTask>";
   }

   private static final String ORG_A = "orga";
   private static final String HOST_ORG = Organization.getDefaultOrganizationID();
   private static final IdentityID CALLER = new IdentityID("oa", ORG_A);
   private static final IdentityID BOB = new IdentityID("bob", ORG_A);
   private static final String FOLDER_ONLY =
      "<?xml version=\"1.0\" encoding=\"UTF-8\" ?><scheduleTask><folders>" +
      "<Folder path=\"X\"/><Folder path=\"X/Y\"/></folders></scheduleTask>";

   @TempDir
   Path tempDir;
   private int fileCount;
   private final Map<String, XMLSerializable> store = new HashMap<>();
   private final Set<String> writableFolders = new HashSet<>();
   private MockedStatic<SUtil> sutil;
   private MockedStatic<OrganizationManager> orgManagerStatic;
   private MockedStatic<ScheduleManager> scheduleManagerStatic;
   private MockedStatic<SecurityEngine> securityStatic;
   private MockedStatic<IndexedStorage> storageStatic;
   private MockedStatic<XSessionService> sessionsStatic;
   private ScheduleManager scheduleManager;
   private SecurityEngine securityEngine;
   private DeployManagerService service;
   private XPrincipal principal;
}
