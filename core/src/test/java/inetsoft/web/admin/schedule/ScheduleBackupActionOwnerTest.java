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
package inetsoft.web.admin.schedule;

import inetsoft.sree.AnalyticRepository;
import inetsoft.sree.RepositoryEntry;
import inetsoft.sree.internal.SUtil;
import inetsoft.sree.schedule.*;
import inetsoft.sree.security.*;
import inetsoft.sree.security.support.SecurityTestDataBuilder;
import inetsoft.sree.web.dashboard.DashboardRegistryManager;
import inetsoft.test.*;
import inetsoft.util.*;
import inetsoft.util.dep.*;
import inetsoft.web.admin.content.repository.ContentRepositoryTreeService;
import inetsoft.web.admin.content.repository.RepletRegistryService;
import inetsoft.web.admin.content.repository.model.SelectedAssetModel;
import inetsoft.web.admin.deploy.DeployService;
import inetsoft.web.admin.schedule.model.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.security.Principal;
import java.util.*;
import java.util.concurrent.Callable;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Bug #77862: the schedule backup action resolves its assets from the client-supplied owners
 * with DeployService.getEntryAssets(), and IndividualAssetBackupAction.deploy() later exports
 * them with no principal, so the task save is the only place they can be checked. The save is
 * reached from the EM and from the portal (/api/portal/schedule/save). An asset the caller adds
 * must be refused like an export of it. An asset the stored task already holds is kept without a
 * check, so a caller may still rename the task or change its other options (Bug #77405).
 *
 * Uses the real ScheduleService / DeployService model conversion and the real SecurityEngine
 * (SecurityTestDataBuilder).
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, IntegrationTestConfiguration.class,
                                  ScheduleBackupActionOwnerTest.Config.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class ScheduleBackupActionOwnerTest {
   /**
    * DashboardAsset.exists() reads the dashboard registry, the stored test dashboards don't
    * exist.
    */
   @Configuration
   static class Config {
      @Bean
      DashboardRegistryManager dashboardRegistryManager() {
         return mock(DashboardRegistryManager.class, RETURNS_MOCKS);
      }
   }

   private static final String ORG_A = "sbaorga";
   private static final String ORG_B = "sbaorgb";
   private static final IdentityID ALICE = new IdentityID("alice", ORG_A);
   private static final IdentityID CAROL = new IdentityID("carol", ORG_A);
   private static final IdentityID DAN = new IdentityID("dan", ORG_A);
   private static final IdentityID SADM = new IdentityID("sadm", ORG_A);
   private static final IdentityID BOB = new IdentityID("bob", ORG_B);
   private static final String LINK = "http://host/";
   private static final String BACKUP_PATH = "/backup";

   private static SecurityTestDataBuilder builder;

   private MockedStatic<SUtil> sutilStatic;
   private ScheduleManager scheduleManager;
   private ScheduleService scheduleService;
   private ScheduleTaskService service;
   private SRPrincipal alice;   // org admin of org A
   private SRPrincipal carol;   // plain user of org A

   /**
    * The owner-keyed asset types, each with the path the repository tree sends and the path of
    * the asset that is stored.
    */
   enum OwnedType {
      DASHBOARD(RepositoryEntry.DASHBOARD, SUtil.MY_DASHBOARD + "/secretDash", "secretDash",
                "DASHBOARD"),
      VIEWSHEET(RepositoryEntry.VIEWSHEET, "secretVs", "secretVs", "VIEWSHEET"),
      WORKSHEET(RepositoryEntry.WORKSHEET, "secretWs", "secretWs", "WORKSHEET");

      OwnedType(int type, String path, String assetPath, String assetType) {
         this.type = type;
         this.path = path;
         this.assetPath = assetPath;
         this.assetType = assetType;
      }

      final int type;
      final String path;
      final String assetPath;
      final String assetType;
   }

   @BeforeAll
   static void setupAll() throws Exception {
      builder = SecurityTestDataBuilder.create()
         .addOrg("sbaOrgA", ORG_A)
         .addOrg("sbaOrgB", ORG_B)
         .addOrgAdminRole("sbaOrgAdminA", ORG_A)
         .addSysAdminRole("sbaSiteAdmin", ORG_A)
         .addUser("alice", ORG_A, "password")
         .addUser("carol", ORG_A, "password")
         .addUser("dan", ORG_A, "password")
         .addUser("sadm", ORG_A, "password")
         .addUser("bob", ORG_B, "password")
         .addUserToRole("alice", "sbaOrgAdminA", ORG_A)
         .addUserToRole("sadm", "sbaSiteAdmin", ORG_A);
      builder.setup();
   }

   @AfterAll
   static void teardownAll() {
      if(builder != null) {
         builder.teardown();
      }
   }

   @BeforeEach
   void setUp() throws Exception {
      sutilStatic = mockStatic(SUtil.class, CALLS_REAL_METHODS);
      sutilStatic.when(SUtil::isMultiTenant).thenReturn(true);

      alice = loginPrincipalOf("alice", ORG_A);
      carol = loginPrincipalOf("carol", ORG_A);

      SecurityEngine securityEngine = SecurityEngine.getSecurity();
      ContentRepositoryTreeService treeService = mock(ContentRepositoryTreeService.class);
      when(treeService.getUnscopedPath(anyString()))
         .thenAnswer(inv -> SUtil.getUnscopedPath(inv.getArgument(0)));
      // no registry entry, a viewsheet stays a viewsheet (not a snapshot)
      DeployService deployService = new DeployService(
         treeService, securityEngine, null, null, null, null, null,
         mock(FileSystemService.class), mock(RepletRegistryService.class));
      ScheduleConditionService conditionService = new ScheduleConditionService();
      scheduleService = spy(new ScheduleService(
         null, null, null, conditionService, null, deployService, null, null, securityEngine,
         null, null, null, null));
      doReturn(true).when(scheduleService).checkPermission(any(), any(), anyString());
      doAnswer(inv -> inv.getArgument(1))
         .when(scheduleService).updateTaskName(anyString(), anyString(), any(), any());
      doNothing().when(scheduleService).saveTask(anyString(), any(), any());

      scheduleManager = mock(ScheduleManager.class);
      service = spy(new ScheduleTaskService(
         mock(AnalyticRepository.class), scheduleManager, scheduleService, conditionService,
         securityEngine.getSecurityProvider(), null, securityEngine));
      doNothing().when(service).setTaskOptions(any(), any(), any());
      doReturn(null).when(service).getDialogModel(anyString(), any(), anyBoolean());
   }

   @AfterEach
   void tearDown() {
      ThreadContext.setContextPrincipal(null);
      sutilStatic.close();
   }

   // the portal task save (/api/portal/schedule/save) of a backup action
   @ParameterizedTest
   @EnumSource(OwnedType.class)
   void portalSave_addedOtherOrgAsset_isRefused(OwnedType type) throws Exception {
      storeTask("CarolTask", CAROL);
      ScheduleTaskEditorModel model = editorModel("CarolTask", "CarolTask", selected(type, BOB));

      assertThrows(MessageException.class,
                   () -> as(carol, () -> service.saveTask(model, LINK, carol)));
      verify(scheduleService, never()).saveTask(anyString(), any(), any());
   }

   @ParameterizedTest
   @EnumSource(OwnedType.class)
   void emSave_addedSameOrgOtherUserAsset_isRefusedForPlainUser(OwnedType type) throws Exception {
      storeTask("CarolTask", CAROL);
      ScheduleTaskEditorModel model = editorModel("CarolTask", "CarolTask", selected(type, DAN));

      assertThrows(MessageException.class,
                   () -> as(carol, () -> service.saveTask(model, LINK, carol, true)));
      verify(scheduleService, never()).saveTask(anyString(), any(), any());
   }

   @ParameterizedTest
   @EnumSource(OwnedType.class)
   void emSave_orgAdminAddsOwnOrgUserAsset_isAllowed(OwnedType type) throws Exception {
      storeTask("AliceTask", ALICE);
      ScheduleTaskEditorModel model = editorModel("AliceTask", "AliceTask", selected(type, CAROL));

      as(alice, () -> service.saveTask(model, LINK, alice, true));

      assertSavedAssets("AliceTask", List.of(asset(type, CAROL)));
   }

   @ParameterizedTest
   @EnumSource(OwnedType.class)
   void emSave_orgAdminAddsOtherOrgAssetToOwnTask_isRefused(OwnedType type) throws Exception {
      // the task already holds an asset of another organization, only the added one is checked
      storeTask("AliceTask", ALICE, asset(OwnedType.WORKSHEET, BOB));
      ScheduleTaskEditorModel model = editorModel(
         "AliceTask", "AliceTask", selected(OwnedType.WORKSHEET, BOB), selected(type, BOB),
         selected(type, CAROL));

      if(type == OwnedType.WORKSHEET) {
         // the same asset sent twice is the stored asset
         assertDoesNotThrow(() -> as(alice, () -> service.saveTask(model, LINK, alice, true)));
         return;
      }

      assertThrows(MessageException.class,
                   () -> as(alice, () -> service.saveTask(model, LINK, alice, true)));
      verify(scheduleService, never()).saveTask(anyString(), any(), any());
   }

   // Bug #77405, a task a site admin created with other organizations' assets can still be
   // renamed by an org admin of its organization
   @Test
   void emSave_orgAdminRenamesTaskWithStoredOtherOrgAssets_isAllowed() throws Exception {
      List<XAsset> stored = Arrays.stream(OwnedType.values()).map(t -> asset(t, BOB)).toList();
      ScheduleTask task = storeTask("SiteTask", SADM, stored.toArray(new XAsset[0]));
      // updateTaskName renames the stored task
      when(scheduleManager.getScheduleTask("Renamed")).thenReturn(task);
      ScheduleTaskEditorModel model = editorModel(
         "Renamed", "SiteTask", Arrays.stream(OwnedType.values()).map(t -> selected(t, BOB))
            .toArray(SelectedAssetModel[]::new));

      as(alice, () -> service.saveTask(model, LINK, alice, true));

      assertSavedAssets("Renamed", stored);
   }

   @Test
   void emSave_removeStoredOtherOrgAssetFromSecondBackupAction_isAllowed() throws Exception {
      // the first backup action is removed, the second one moves to its index
      ScheduleTask task = new ScheduleTask("AliceTask");
      task.setOwner(ALICE);
      task.addAction(backupAction(asset(OwnedType.WORKSHEET, CAROL)));
      task.addAction(backupAction(asset(OwnedType.VIEWSHEET, BOB)));
      when(scheduleManager.getScheduleTask("AliceTask")).thenReturn(task);
      ScheduleTaskEditorModel model =
         editorModel("AliceTask", "AliceTask", selected(OwnedType.VIEWSHEET, BOB));

      as(alice, () -> service.saveTask(model, LINK, alice, true));

      assertSavedAssets("AliceTask", List.of(asset(OwnedType.VIEWSHEET, BOB)));
   }

   // the comparison of Bug #77405 re-resolves the stored action as the caller, it keeps the
   // stored assets
   @Test
   void resavedActionModel_storedOtherOrgAssets_isNotRefused() throws Exception {
      List<XAsset> assets = new ArrayList<>();

      for(OwnedType type : OwnedType.values()) {
         XAsset asset = spy(asset(type, BOB));
         doReturn(true).when(asset).exists();
         assets.add(asset);
      }

      IndividualAssetBackupAction action = backupAction(assets.toArray(new XAsset[0]));

      assertDoesNotThrow(() -> as(alice, () -> ReflectionTestUtils.invokeMethod(
         service, "getResavedActionModel", action, LINK, alice, true)));
   }

   // an auto-save asset has the owner __NULL__ in the action model, the owner is the user in the
   // file name (Bug #77924)
   @Test
   void ownAutoSaveAsset_nullModelOwner_isAllowed() throws Exception {
      XAsset autoSave = spy(SUtil.getXAsset(
         VSAutoSaveAsset.AUTOSAVEVS, "4^VIEWSHEET^" + CAROL.convertToKey() + "^vs1^127.0.0.1",
         null));
      doReturn(true).when(autoSave).exists();
      IndividualAssetBackupAction stored = backupAction(autoSave);
      BackupActionModel storedModel =
         (BackupActionModel) as(carol, () -> scheduleService.getActionModel(stored, carol, true));
      SelectedAssetModel storedAsset = storedModel.assets().get(0);
      assertEquals(XAsset.NULL, storedAsset.user().name);
      // with the type of the repository tree entry, the model has no type of an auto-save asset
      BackupActionModel model = BackupActionModel.builder().from(storedModel)
         .assets(List.of(SelectedAssetModel.builder().from(storedAsset)
                            .type(RepositoryEntry.AUTO_SAVE_VS).build()))
         .build();

      // not kept, the asset is checked as an added asset
      IndividualAssetBackupAction action = (IndividualAssetBackupAction)
         as(carol, () -> scheduleService.getActionFromModel(model, null, carol, LINK));

      assertEquals(1, action.getAssets().size());
      assertInstanceOf(VSAutoSaveAsset.class, action.getAssets().get(0));
   }

   private ScheduleTask storeTask(String name, IdentityID owner, XAsset... assets) {
      ScheduleTask task = new ScheduleTask(name);
      task.setOwner(owner);

      if(assets.length > 0) {
         task.addAction(backupAction(assets));
      }

      when(scheduleManager.getScheduleTask(name)).thenReturn(task);
      return task;
   }

   private void assertSavedAssets(String name, List<XAsset> expected) throws Exception {
      ArgumentCaptor<ScheduleTask> saved = ArgumentCaptor.forClass(ScheduleTask.class);
      // a task that isn't renamed is saved under its id
      verify(scheduleService).saveTask(endsWith(name), saved.capture(), any());
      IndividualAssetBackupAction action =
         (IndividualAssetBackupAction) saved.getValue().getAction(0);
      assertEquals(expected.stream().map(XAsset::toIdentifier).toList(),
                   action.getAssets().stream().map(XAsset::toIdentifier).toList());
   }

   private static IndividualAssetBackupAction backupAction(XAsset... assets) {
      IndividualAssetBackupAction action = new IndividualAssetBackupAction();
      action.setPaths(BACKUP_PATH);
      action.setAssets(List.of(assets));
      return action;
   }

   private static XAsset asset(OwnedType type, IdentityID owner) {
      return SUtil.getXAsset(type.assetType, type.assetPath, owner);
   }

   private static SelectedAssetModel selected(OwnedType type, IdentityID owner) {
      return SelectedAssetModel.builder()
         .path(type.path)
         .type(type.type)
         .typeName(type.assetType)
         .typeLabel("")
         .user(owner)
         .build();
   }

   private static ScheduleTaskEditorModel editorModel(String taskName, String oldTaskName,
                                                      SelectedAssetModel... assets)
   {
      BackupActionModel action = BackupActionModel.builder()
         .assets(List.of(assets))
         .backupPathsEnabled(true)
         .backupPath(BACKUP_PATH)
         .backupServerPath(ServerPathInfoModel.builder().path(BACKUP_PATH).build())
         .actionType("BackupAction")
         .actionClass("BackupActionModel")
         .build();
      return ScheduleTaskEditorModel.builder()
         .taskName(taskName)
         .oldTaskName(oldTaskName)
         .options(mock(TaskOptionsPaneModel.class))
         .addActions(action)
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

   private static SRPrincipal loginPrincipalOf(String name, String orgID) {
      SRPrincipal principal = builder.principalOf(name, orgID);
      principal.setProperty("__internal__", "true");
      return principal;
   }
}
