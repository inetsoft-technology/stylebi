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
package inetsoft.web.admin.deploy;

import inetsoft.sree.RepositoryEntry;
import inetsoft.sree.internal.SUtil;
import inetsoft.sree.internal.cluster.Cluster;
import inetsoft.sree.portal.PortalThemesManager;
import inetsoft.sree.schedule.*;
import inetsoft.sree.security.*;
import inetsoft.sree.security.support.SecurityTestDataBuilder;
import inetsoft.test.*;
import inetsoft.uql.XPrincipal;
import inetsoft.uql.asset.AssetEntry;
import inetsoft.uql.util.Identity;
import inetsoft.util.*;
import inetsoft.util.dep.*;
import inetsoft.web.AutoSaveUtils;
import inetsoft.web.admin.content.repository.*;
import inetsoft.web.admin.content.repository.model.*;
import inetsoft.web.service.BinaryTransferService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedStatic;
import org.springframework.context.annotation.*;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.io.File;
import java.lang.reflect.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.Principal;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Bug #77923, #77924: the writer of a schedule task asset reads the task stored under its id and
 * the writer of an auto-save asset reads the file of the user named in its file name, both ignore
 * the client-supplied owner. The repository export checked that owner (or nothing, for an
 * auto-save or an owner-less dependent), so a delegated EM user could export another user's task
 * or unsaved sheet, and the host organization's internal task, by naming themself as the owner.
 * The export must check the stored task / the owner in the file name, for selected entities,
 * get-dependent-assets, the check-permission preflight and dependent assets, while the owner, an
 * organization admin of the owner and a site admin still get the content.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, IntegrationTestConfiguration.class,
                                  DeployServiceExportStoredOwnerTest.Config.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class DeployServiceExportStoredOwnerTest {
   private static final String ORG_A = "dxsorga";
   private static final String ORG_B = "dxsorgb";
   private static final IdentityID DAVE = new IdentityID("dave", ORG_A);
   private static final IdentityID BOB = new IdentityID("bob", ORG_B);
   private static final String DAVE_TASK = DAVE.convertToKey() + ":daveSecret";
   private static final String BOB_TASK = BOB.convertToKey() + ":bobSecret";
   private static final String MISSING_TASK = DAVE.convertToKey() + ":noSuchTask";
   private static final String BACKUP = "__asset file backup__";
   private static final String VS_SECRET = "DAVE-SECRET-UNSAVED-VS";
   private static final String WS_SECRET = "DAVE-SECRET-UNSAVED-WS";
   private static final String NULL_SECRET = "NULL-USER-UNSAVED-VS";

   @Configuration
   static class Config {
      @Bean
      @Primary
      PortalThemesManager portalThemesManager() {
         PortalThemesManager manager = mock(PortalThemesManager.class);
         when(manager.getCssEntries()).thenReturn(new HashMap<>());
         return manager;
      }
   }

   @TempDir
   Path tempDir;

   private SecurityTestDataBuilder builder;
   private MockedStatic<SUtil> sutilStatic;
   private DeployService deployService;
   private ExportAssetService exportService;
   private SRPrincipal alice;   // org admin of org A
   private SRPrincipal carol;   // plain user of org A, the delegated EM repository user
   private SRPrincipal dave;    // plain user of org A, owner of the task and the auto-saves
   private SRPrincipal sadm;    // site admin (org A)
   private SRPrincipal erin;    // plain user of org A with ADMIN permission on dave
   private String cycleTask;    // data cycle task of org A, owned by the system user
   private String vsFile;       // repository tree paths of the recycled auto-saves
   private String wsFile;
   private String nullFile;
   private int exportCount;

   @BeforeAll
   void setupAll() throws Exception {
      builder = SecurityTestDataBuilder.create()
         .addOrg("dxsOrgA", ORG_A)
         .addOrg("dxsOrgB", ORG_B)
         .addOrgAdminRole("dxsOrgAdminA", ORG_A)
         .addSysAdminRole("dxsSiteAdmin", ORG_A)
         .addUser("alice", ORG_A, "password")
         .addUser("carol", ORG_A, "password")
         .addUser("dave", ORG_A, "password")
         .addUser("sadm", ORG_A, "password")
         .addUser("erin", ORG_A, "password")
         .addUser("bob", ORG_B, "password")
         .addUserToRole("alice", "dxsOrgAdminA", ORG_A)
         .addUserToRole("sadm", "dxsSiteAdmin", ORG_A)
         .grantPermission(ResourceType.SECURITY_USER, DAVE.convertToKey(), ResourceAction.ADMIN,
                          "erin", Identity.USER, ORG_A);
      builder.setup();

      ScheduleManager manager = ScheduleManager.getScheduleManager();
      storeTask(manager, userTask("daveSecret", DAVE), ORG_A, false);
      storeTask(manager, userTask("bobSecret", BOB), ORG_B, false);

      ScheduleTask cycle = new ScheduleTask(
         "DataCycle Task: dxsCycle", ScheduleTask.Type.CYCLE_TASK);
      cycle.setOwner(new IdentityID(XPrincipal.SYSTEM, ORG_A));
      cycle.addAction(new BatchAction());
      cycleTask = cycle.getTaskId();
      storeExtensionTask(manager, cycle, ORG_A);

      String host = Organization.getDefaultOrganizationID();

      if(manager.getScheduleTask(BACKUP, host) == null) {
         ScheduleTask backup = new ScheduleTask(BACKUP, ScheduleTask.Type.INTERNAL_TASK);
         backup.setOwner(new IdentityID(XPrincipal.SYSTEM, host));
         backup.addAction(new AssetFileBackupAction());
         storeTask(manager, backup, host, true);
      }

      assertNotNull(manager.getScheduleTask(DAVE_TASK, ORG_A), "setup: dave's task");
      assertNotNull(manager.getScheduleTask(BOB_TASK, ORG_B), "setup: bob's task");
      assertNotNull(manager.getScheduleTask(cycleTask, ORG_A), "setup: data cycle task");
      assertNotNull(manager.getScheduleTask(BACKUP, host), "setup: host-org internal task");

      // the names AutoSaveUtils.recycleUserAutoSave gives recycled auto-saves
      dave = loginPrincipalOf("dave", ORG_A);
      vsFile = "8^VIEWSHEET^" + dave.getName() + "^Untitled-1^127_0_0_1~";
      wsFile = "8^WORKSHEET^" + dave.getName() + "^Untitled-2^127_0_0_1~";
      nullFile = "8^VIEWSHEET^_NULL_^Untitled-3^127_0_0_1~";
      writeAutoSave(vsFile, VS_SECRET);
      writeAutoSave(wsFile, WS_SECRET);
      writeAutoSave(nullFile, NULL_SECRET);
   }

   @AfterAll
   void teardownAll() {
      if(builder != null) {
         builder.teardown();
      }
   }

   @BeforeEach
   void setUp() {
      sutilStatic = mockStatic(SUtil.class, CALLS_REAL_METHODS);
      sutilStatic.when(SUtil::isMultiTenant).thenReturn(true);

      alice = loginPrincipalOf("alice", ORG_A);
      carol = loginPrincipalOf("carol", ORG_A);
      dave = loginPrincipalOf("dave", ORG_A);
      sadm = loginPrincipalOf("sadm", ORG_A);
      erin = loginPrincipalOf("erin", ORG_A);

      ContentRepositoryTreeService treeService = mock(ContentRepositoryTreeService.class);
      when(treeService.getUnscopedPath(anyString()))
         .thenAnswer(inv -> SUtil.getUnscopedPath(inv.getArgument(0)));
      RepletRegistryService registryService = mock(RepletRegistryService.class);
      FileSystemService fileSystemService = mock(FileSystemService.class);
      when(fileSystemService.getCacheFile(anyString()))
         .thenAnswer(inv -> tempDir.resolve((exportCount++) + "-" + inv.getArgument(0)).toFile());
      deployService = new DeployService(treeService, SecurityEngine.getSecurity(), null, null,
                                        null, null, null, fileSystemService, registryService);
      exportService = new ExportAssetService(deployService, mock(BinaryTransferService.class),
                                             mock(Cluster.class), fileSystemService);
   }

   @AfterEach
   void tearDown() {
      ThreadContext.setContextPrincipal(null);
      OrganizationContextHolder.setCurrentOrgId(null);
      sutilStatic.close();
   }

   // ── schedule tasks (#77923) ─────────────────────────────────────────────

   @Test
   void task_selectedWithAnyOwner_isRefusedForPlainUser() {
      for(IdentityID owner : Arrays.asList(carol.getIdentityID(), DAVE, null)) {
         assertRefused(carol, List.of(task(DAVE_TASK, owner)), List.of());
      }
   }

   @Test
   void task_dependentWithAnyOwner_isRefusedForPlainUser() {
      for(IdentityID owner : Arrays.asList(null, new IdentityID(XAsset.NULL, ORG_A),
                                           carol.getIdentityID()))
      {
         assertRefused(carol, List.of(), List.of(required(DAVE_TASK, "SCHEDULETASK", owner)));
      }
   }

   @Test
   void task_forgedOwner_isRefusedByDependencyListAndPreflight() throws Exception {
      SelectedAssetModel forged = task(DAVE_TASK, carol.getIdentityID());

      assertThrows(MessageException.class, () -> as(carol, () ->
         deployService.getDependentAssetsList(List.of(forged), carol)));
      // the schedule backup action save checks added assets with the same method
      assertThrows(MessageException.class, () -> as(carol, () ->
         deployService.getEntryAssets(List.of(forged), Set.of(), carol)));
      assertTrue(preflight(carol, forged).isEmpty());
   }

   @Test
   void task_missing_isRefused() {
      assertRefused(carol, List.of(task(MISSING_TASK, carol.getIdentityID())), List.of());
      assertRefused(alice, List.of(), List.of(required(MISSING_TASK, "SCHEDULETASK", null)));
   }

   @Test
   void task_owner_orgAdmin_andSiteAdmin_exportTheTask() throws Exception {
      for(SRPrincipal caller : List.of(dave, alice, sadm)) {
         Map<String, String> entries = export(
            caller, List.of(task(DAVE_TASK, DAVE)), List.of(required(DAVE_TASK, "SCHEDULETASK", null)));

         assertTrue(entries.entrySet().stream().anyMatch(
            e -> e.getKey().startsWith("SCHEDULETASK_") && e.getValue().contains("daveSecret")),
                    caller.getName() + " exported " + entries.keySet());
         assertTrue(preflight(caller, task(DAVE_TASK, DAVE)).size() == 1, caller.getName());
      }
   }

   @Test
   void task_plainUserWithAdminOnOwner_exportsTheTask() throws Exception {
      // the stored owner decides, not the role: a plain user that may administer dave still
      // gets dave's task, whatever owner is sent, while carol (no permission) is refused above
      for(IdentityID owner : Arrays.asList(DAVE, erin.getIdentityID())) {
         Map<String, String> entries = export(
            erin, List.of(task(DAVE_TASK, owner)), List.of(required(DAVE_TASK, "SCHEDULETASK", null)));

         assertTrue(entries.values().stream().anyMatch(v -> v.contains("daveSecret")),
                    entries.keySet().toString());
         assertEquals(1, preflight(erin, task(DAVE_TASK, owner)).size());
      }
   }

   @Test
   void task_siteAdmin_exportsOtherOrgTask() throws Exception {
      OrganizationContextHolder.setCurrentOrgId(ORG_B);
      Map<String, String> entries = export(sadm, List.of(task(BOB_TASK, BOB)), List.of());

      assertTrue(entries.values().stream().anyMatch(v -> v.contains("bobSecret")),
                 entries.keySet().toString());
   }

   @Test
   void task_dataCycleTask_isExportedByOrgAdminOnly() throws Exception {
      Map<String, String> entries = export(alice, List.of(task(cycleTask, null)), List.of());

      assertTrue(entries.values().stream().anyMatch(v -> v.contains("dxsCycle")),
                 entries.keySet().toString());
      assertRefused(carol, List.of(task(cycleTask, carol.getIdentityID())), List.of());
   }

   @Test
   void task_hostOrgInternalTask_isRefusedForOrgUsers() {
      for(SRPrincipal caller : List.of(carol, alice)) {
         assertRefused(caller, List.of(task(BACKUP, caller.getIdentityID())), List.of());
         assertRefused(caller, List.of(), List.of(required(BACKUP, "SCHEDULETASK", null)));
      }
   }

   @Test
   void task_hostOrgInternalTask_isExportedBySiteAdmin() throws Exception {
      Map<String, String> entries = export(sadm, List.of(task(BACKUP, null)), List.of());

      assertTrue(entries.values().stream().anyMatch(v -> v.contains(BACKUP)),
                 entries.keySet().toString());
   }

   @Test
   void task_withoutOwner_isSiteAdminOnly() throws Exception {
      ScheduleTask stored = ScheduleManager.getScheduleManager().getScheduleTask(DAVE_TASK, ORG_A);
      IdentityID owner = stored.getOwner();
      stored.setOwner(null);

      try {
         XAsset asset = SUtil.getXAsset(ScheduleTaskAsset.SCHEDULETASK, DAVE_TASK, null);

         for(SRPrincipal caller : List.of(carol, alice, dave)) {
            assertFalse(as(caller, () -> XAssetExportPermission.isPermitted(
               asset, DAVE_TASK, true, caller)), caller.getName());
         }

         assertTrue(as(sadm, () -> XAssetExportPermission.isPermitted(
            asset, DAVE_TASK, true, sadm)));
      }
      finally {
         stored.setOwner(owner);
      }
   }

   // ── auto-saves (#77924) ─────────────────────────────────────────────────

   @Test
   void autoSave_selected_isRefusedForPlainUser() {
      assertRefused(carol, List.of(autoSave(RepositoryEntry.AUTO_SAVE_VS, vsFile, carol.getIdentityID())),
                    List.of());
      assertRefused(carol, List.of(autoSave(RepositoryEntry.AUTO_SAVE_VS, vsFile, null)), List.of());
      assertRefused(carol, List.of(autoSave(RepositoryEntry.AUTO_SAVE_WS, wsFile, null)), List.of());
   }

   @Test
   void autoSave_dependentWithAnyOwner_isRefusedForPlainUser() {
      for(IdentityID owner : Arrays.asList(null, new IdentityID(XAsset.NULL, ORG_A),
                                           carol.getIdentityID()))
      {
         assertRefused(carol, List.of(), List.of(required(vsFile, "AUTOSAVEVS", owner)));
         assertRefused(carol, List.of(), List.of(required(wsFile, "AUTOSAVEWS", owner)));
      }
   }

   @Test
   void autoSave_preflight_dropsOtherUsersFile() throws Exception {
      assertTrue(preflight(carol, autoSave(RepositoryEntry.AUTO_SAVE_VS, vsFile,
                                           carol.getIdentityID())).isEmpty());
      assertEquals(1, preflight(alice, autoSave(RepositoryEntry.AUTO_SAVE_VS, vsFile, null)).size());
   }

   @Test
   void autoSave_owner_orgAdmin_andSiteAdmin_exportTheFile() throws Exception {
      for(SRPrincipal caller : List.of(dave, alice, sadm)) {
         assertContent(caller, List.of(autoSave(RepositoryEntry.AUTO_SAVE_VS, vsFile, null)),
                       List.of(), VS_SECRET);
         assertContent(caller, List.of(), List.of(required(wsFile, "AUTOSAVEWS", null)), WS_SECRET);
      }
   }

   @Test
   void autoSave_withoutUser_isAdminOnly() throws Exception {
      assertRefused(carol, List.of(autoSave(RepositoryEntry.AUTO_SAVE_VS, nullFile, null)), List.of());
      assertRefused(carol, List.of(), List.of(required(nullFile, "AUTOSAVEVS", null)));
      assertContent(alice, List.of(autoSave(RepositoryEntry.AUTO_SAVE_VS, nullFile, null)),
                    List.of(), NULL_SECRET);
   }

   @Test
   void autoSave_malformedName_isRefused() throws Exception {
      // SUtil.getXAsset() can't build it, check the helper on its own
      VSAutoSaveAsset asset = mock(VSAutoSaveAsset.class);
      when(asset.getPath()).thenReturn("8^VIEWSHEET^" + DAVE.convertToKey());

      assertFalse(as(sadm, () -> XAssetExportPermission.isPermitted(asset, "x", true, sadm)));
   }

   // ── helpers ─────────────────────────────────────────────────────────────

   private void assertRefused(SRPrincipal caller, List<SelectedAssetModel> selected,
                              List<RequiredAssetModel> dependents)
   {
      Map<String, String> entries;

      try {
         entries = export(caller, selected, dependents);
      }
      catch(Exception e) {
         assertInstanceOf(MessageException.class, e, caller.getName());
         return;
      }

      fail(caller.getName() + " exported " + entries.keySet());
   }

   private void assertContent(SRPrincipal caller, List<SelectedAssetModel> selected,
                              List<RequiredAssetModel> dependents, String content)
      throws Exception
   {
      Map<String, String> entries = export(caller, selected, dependents);
      assertTrue(entries.values().stream().anyMatch(v -> v.contains(content)),
                 caller.getName() + " exported " + entries.keySet());
   }

   private List<SelectedAssetModel> preflight(SRPrincipal caller, SelectedAssetModel entity)
      throws Exception
   {
      return as(caller, () -> deployService.filterEntities(
         SelectedAssetModelList.builder().selectedAssets(List.of(entity)).build(), caller))
         .selectedAssets();
   }

   private Map<String, String> export(SRPrincipal caller, List<SelectedAssetModel> selected,
                                      List<RequiredAssetModel> dependents) throws Exception
   {
      ExportedAssetsModel model = ExportedAssetsModel.builder()
         .name("export77923")
         .selectedEntities(selected)
         .dependentAssets(dependents)
         .build();
      ExportJarProperties properties =
         as(caller, () -> exportService.createExport("id", model, caller));
      Map<String, String> entries = new LinkedHashMap<>();

      try(ZipFile zip = new ZipFile(new File(properties.zipFilePath()))) {
         for(ZipEntry entry : Collections.list(zip.entries())) {
            if(entry.getSize() != 0 && (entry.getName().startsWith("SCHEDULETASK") ||
               entry.getName().startsWith("AUTOSAVE")))
            {
               entries.put(entry.getName(), new String(
                  zip.getInputStream(entry).readAllBytes(), StandardCharsets.UTF_8));
            }
         }
      }

      return entries;
   }

   private static ScheduleTask userTask(String name, IdentityID owner) {
      ScheduleTask task = new ScheduleTask(name);
      task.setOwner(owner);
      task.addAction(new BatchAction());
      return task;
   }

   private static void storeTask(ScheduleManager manager, ScheduleTask task, String orgID,
                                 boolean internal) throws Exception
   {
      Method m = ScheduleManager.class.getDeclaredMethod(
         "setScheduleTask", String.class, ScheduleTask.class, AssetEntry.class, String.class,
         boolean.class, boolean.class, Principal.class, boolean.class);
      m.setAccessible(true);
      m.invoke(manager, task.getTaskId(), task, null, orgID, internal, true, null, true);
   }

   // a data cycle task is an extension task generated by DataCycleManager, it is not stored
   @SuppressWarnings("unchecked")
   private static void storeExtensionTask(ScheduleManager manager, ScheduleTask task,
                                          String orgID) throws Exception
   {
      Class<?> keyClass = Class.forName(ScheduleManager.class.getName() + "$ExtTaskKey");
      Constructor<?> key = keyClass.getDeclaredConstructor(String.class, String.class);
      key.setAccessible(true);
      Field field = ScheduleManager.class.getDeclaredField("extensionTasks");
      field.setAccessible(true);
      ((Map<Object, ScheduleTask>) field.get(manager))
         .put(key.newInstance(task.getTaskId(), orgID), task);
   }

   private void writeAutoSave(String file, String content) throws Exception {
      as(dave, () -> {
         AutoSaveUtils.writeAutoSaveFile(content.getBytes(StandardCharsets.UTF_8),
                                         AutoSaveUtils.RECYCLE_PREFIX + file, dave);
         assertTrue(AutoSaveUtils.exists(AutoSaveUtils.RECYCLE_PREFIX + file, dave), file);
         return null;
      });
   }

   private static SelectedAssetModel task(String path, IdentityID owner) {
      return SelectedAssetModel.builder()
         .path(path)
         .type(RepositoryEntry.SCHEDULE_TASK)
         .typeName(ScheduleTaskAsset.SCHEDULETASK)
         .typeLabel("")
         .user(owner)
         .build();
   }

   private static SelectedAssetModel autoSave(int type, String path, IdentityID owner) {
      return SelectedAssetModel.builder()
         .path(path)
         .type(type)
         .typeName(type == RepositoryEntry.AUTO_SAVE_VS ?
                      VSAutoSaveAsset.AUTOSAVEVS : WSAutoSaveAsset.AUTOSAVEWS)
         .typeLabel("")
         .user(owner)
         .build();
   }

   private static RequiredAssetModel required(String name, String type, IdentityID owner) {
      return RequiredAssetModel.builder()
         .name(name)
         .type(type)
         .user(owner)
         .lastModifiedTime(0)
         .build();
   }

   private static <T> T as(Principal principal, Callable<T> call) throws Exception {
      Principal old = ThreadContext.getContextPrincipal();
      ThreadContext.setContextPrincipal(principal);

      try {
         return call.call();
      }
      finally {
         ThreadContext.setContextPrincipal(old);
      }
   }

   private SRPrincipal loginPrincipalOf(String name, String orgID) {
      SRPrincipal principal = builder.principalOf(name, orgID);
      principal.setProperty("__internal__", "true");
      return principal;
   }
}
