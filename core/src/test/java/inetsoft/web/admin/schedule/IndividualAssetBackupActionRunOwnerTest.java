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
import inetsoft.sree.SreeEnv;
import inetsoft.sree.internal.DataCycleManager;
import inetsoft.sree.internal.SUtil;
import inetsoft.sree.schedule.*;
import inetsoft.sree.security.*;
import inetsoft.sree.security.support.SecurityTestDataBuilder;
import inetsoft.sree.web.dashboard.DashboardRegistryManager;
import inetsoft.storage.ExternalStorageService;
import inetsoft.test.*;
import inetsoft.uql.XPrincipal;
import inetsoft.uql.asset.*;
import inetsoft.uql.asset.internal.AssetUtil;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.util.ThreadContext;
import inetsoft.util.dep.*;
import inetsoft.web.admin.deploy.XAssetExportPermission;
import inetsoft.web.admin.model.FileData;
import inetsoft.web.admin.schedule.model.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.Principal;
import java.util.*;
import java.util.concurrent.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Bug #77922: a stored schedule backup action is checked against the task owner when the task
 * runs, whatever path stored its assets (the task editor, the EM task import, an older version
 * or the API). The task editor checks only the assets a caller adds.
 *
 * Runs the real ScheduleTask.run() (runtime copy, run principal, ScheduleTask.doRun) with the
 * real IndividualAssetBackupAction, DeployUtil and XAssetExportPermission against the real
 * SecurityEngine (SecurityTestDataBuilder) and sheets saved in the real asset storage. Only the
 * backup destination (ExternalStorageService) is a mock that records the ZIP entries. The task
 * thread pool is replaced with one that runs in the test thread, so the multi-tenant stub of
 * SUtil applies to the action.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, IntegrationTestConfiguration.class,
                                  IndividualAssetBackupActionRunOwnerTest.Config.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class IndividualAssetBackupActionRunOwnerTest {
   @Configuration
   static class Config {
      @Bean
      DashboardRegistryManager dashboardRegistryManager() {
         return mock(DashboardRegistryManager.class, RETURNS_MOCKS);
      }

      @Bean
      ExternalStorageService externalStorageService() {
         return mock(ExternalStorageService.class);
      }

      @Bean
      DataCycleManager dataCycleManager() {
         return mock(DataCycleManager.class);
      }
   }

   private static final String ORG_A = "iabrorga";
   private static final String ORG_B = "iabrorgb";
   private static final IdentityID ALICE = new IdentityID("alice", ORG_A);
   private static final IdentityID CARL = new IdentityID("carl", ORG_A);
   private static final IdentityID DAVE = new IdentityID("dave", ORG_A);
   private static final IdentityID SADM = new IdentityID("sadm", ORG_A);
   private static final IdentityID BOB = new IdentityID("bob", ORG_B);
   private static final IdentityID CARL_GROUP = new IdentityID("iabrCarlGroup", ORG_A);
   // a missing owner named like a site admin of another org (Bug #77452 principal)
   private static final IdentityID SROOT_IN_A = new IdentityID("iabrRoot", ORG_A);
   private static final String FTP = "ftp://attacker.example/loot";
   private static final String BACKUP_PATH = "/backup/iabr";

   private static SecurityTestDataBuilder builder;

   @Autowired
   private ExternalStorageService storage;

   private MockedStatic<SUtil> sutilStatic;
   private ExecutorService originalPool;
   private String oldTaskFailed;
   private final List<String> written = new ArrayList<>();

   @BeforeAll
   static void setupAll() throws Exception {
      builder = SecurityTestDataBuilder.create()
         .addOrg("iabrOrgA", ORG_A)
         .addOrg("iabrOrgB", ORG_B)
         .addOrgAdminRole("iabrOrgAdminA", ORG_A)
         .addSysAdminRole("iabrSiteAdmin", ORG_A)
         .addUser("alice", ORG_A, "password")
         .addUser("carl", ORG_A, "password")
         .addUser("dave", ORG_A, "password")
         .addUser("sadm", ORG_A, "password")
         .addUser("bob", ORG_B, "password")
         .addSysAdminRole("iabrSiteAdminB", ORG_B)
         .addUser("iabrRoot", ORG_B, "password")
         .addUserToRole("iabrRoot", "iabrSiteAdminB", ORG_B)
         .addGroup(CARL_GROUP.name, ORG_A)
         .addUserToGroup("carl", CARL_GROUP.name, ORG_A)
         .addUserToRole("alice", "iabrOrgAdminA", ORG_A)
         .addUserToRole("sadm", "iabrSiteAdmin", ORG_A);
      builder.setup();

      saveWorksheet(new AssetEntry(AssetRepository.USER_SCOPE, AssetEntry.Type.WORKSHEET,
                                   "daveSecretWs", DAVE, ORG_A), new XPrincipal(DAVE));
      saveWorksheet(new AssetEntry(AssetRepository.USER_SCOPE, AssetEntry.Type.WORKSHEET,
                                   "carlWs", CARL, ORG_A), new XPrincipal(CARL));
      saveWorksheet(new AssetEntry(AssetRepository.USER_SCOPE, AssetEntry.Type.WORKSHEET,
                                   "bobSecretWs", BOB, ORG_B), new XPrincipal(BOB));
      AssetEntry globalWs = new AssetEntry(AssetRepository.GLOBAL_SCOPE,
                                           AssetEntry.Type.WORKSHEET, "iabrGlobalWs", null, ORG_A);
      saveWorksheet(globalWs, builder.principalOf("alice", ORG_A));

      // carl's viewsheet on the global worksheet that carl may only read
      Viewsheet vs = new Viewsheet();
      vs.setBaseEntry(globalWs);
      OrganizationContextHolder.setCurrentOrgId(ORG_A);

      try {
         AssetUtil.getAssetRepository(false).setSheet(
            new AssetEntry(AssetRepository.USER_SCOPE, AssetEntry.Type.VIEWSHEET, "carlVs", CARL,
                           ORG_A), vs, new XPrincipal(CARL), true);
      }
      finally {
         OrganizationContextHolder.setCurrentOrgId(null);
      }
   }

   private static void saveWorksheet(AssetEntry entry, Principal user) throws Exception {
      OrganizationContextHolder.setCurrentOrgId(entry.getOrgID());

      try {
         AssetUtil.getAssetRepository(false).setSheet(entry, new Worksheet(), user, true);
      }
      finally {
         OrganizationContextHolder.setCurrentOrgId(null);
      }
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

      originalPool = (ExecutorService) poolField().get(null);
      poolField().set(null, new CallerThreadExecutor());

      // no task-failed mail from the refused runs
      oldTaskFailed = SreeEnv.getProperty("schedule.options.taskFailed");
      SreeEnv.setProperty("schedule.options.taskFailed", "false");

      reset(storage);
      written.clear();
      doAnswer(inv -> {
         Path file = inv.getArgument(1);

         try(ZipFile zip = new ZipFile(file.toFile())) {
            for(ZipEntry e : Collections.list(zip.entries())) {
               written.add(e.getName() + " (" + e.getSize() + ")");
            }
         }

         return null;
      }).when(storage).write(anyString(), any(Path.class), any());
   }

   @AfterEach
   void tearDown() throws Exception {
      poolField().set(null, originalPool);
      SreeEnv.setProperty("schedule.options.taskFailed", oldTaskFailed);
      ThreadContext.setContextPrincipal(null);
      OrganizationContextHolder.setCurrentOrgId(null);
      sutilStatic.close();
   }

   // the import bypass: a plain user imports a task of his own that names another same-org
   // user's private sheet, the import stores it, the run must refuse it
   @Test
   void importedSameOrgForeignSheet_isRefusedAtRun() throws Throwable {
      ScheduleTask imported = importBackup("Loot", "dave~;~" + ORG_A, "daveSecretWs");
      assertNotNull(imported, "precondition: the import stores the task");
      assertEquals(CARL, imported.getOwner());

      Throwable e = assertThrows(Throwable.class, () -> run(imported));
      assertTrue(e.getMessage().contains("daveSecretWs"), e.getMessage());
      verify(storage, never()).write(anyString(), any(Path.class), any());
   }

   // a task stored by an older version that names a sheet of another organization
   @Test
   void legacyCrossOrgSheet_isRefusedAtRun() throws Throwable {
      ScheduleTask task = backupTask("Legacy", ALICE, FTP,
                                     SUtil.getXAsset("WORKSHEET", "bobSecretWs", BOB));

      Throwable e = assertThrows(Throwable.class, () -> run(task));
      assertTrue(e.getMessage().contains("bobSecretWs"), e.getMessage());
      verify(storage, never()).write(anyString(), any(Path.class), any());
   }

   // Behaviour change: a global sheet that a plain owner may not administer, e.g. added by an
   // organization admin, is refused as the task editor refuses it
   @Test
   void plainOwner_globalSheetWithoutAdmin_isRefusedAtRun() throws Throwable {
      ScheduleTask task = backupTask("Global", CARL, BACKUP_PATH,
                                     SUtil.getXAsset("WORKSHEET", "iabrGlobalWs", null));

      Throwable e = assertThrows(Throwable.class, () -> run(task));
      assertTrue(e.getMessage().contains("iabrGlobalWs"), e.getMessage());
      verify(storage, never()).write(anyString(), any(Path.class), any());
   }

   @Test
   void ownersOwnSheet_isBackedUp() throws Throwable {
      run(backupTask("Own", CARL, BACKUP_PATH, SUtil.getXAsset("WORKSHEET", "carlWs", CARL)));

      assertWritten("carl~;~" + ORG_A + "^carlWs");
   }

   @Test
   void orgAdminOwner_sameOrgUsersSheet_isBackedUp() throws Throwable {
      run(backupTask("Admin", ALICE, BACKUP_PATH,
                     SUtil.getXAsset("WORKSHEET", "daveSecretWs", DAVE)));

      assertWritten("dave~;~" + ORG_A + "^daveSecretWs");
   }

   @Test
   void siteAdminOwner_otherOrgSheet_isBackedUp() throws Throwable {
      run(backupTask("Site", SADM, BACKUP_PATH,
                     SUtil.getXAsset("WORKSHEET", "bobSecretWs", BOB)));

      assertWritten("bob~;~" + ORG_B + "^bobSecretWs");
   }

   // the run principal of a group execute-as task is named after the group, the assets are
   // checked against the owner
   @Test
   void groupExecuteAs_ownersOwnSheet_isBackedUp() throws Throwable {
      ScheduleTask task = backupTask("AsGroup", CARL, BACKUP_PATH,
                                     SUtil.getXAsset("WORKSHEET", "carlWs", CARL));
      task.setIdentity(new Group(CARL_GROUP));
      assertEquals(CARL_GROUP.convertToKey(),
                   SUtil.getScheduleTaskRunPrincipal(task, null, false).getName(),
                   "precondition: the run principal is the group");

      run(task);

      assertWritten("carl~;~" + ORG_A + "^carlWs");
   }

   // a global dependency is not checked for ADMIN, the owner only reads the global worksheet
   @Test
   void ownSheet_dependingOnGlobalWorksheet_isBackedUpWithIt() throws Throwable {
      XAsset globalWs = SUtil.getXAsset("WORKSHEET", "iabrGlobalWs", null);
      assertFalse(as(ownerPrincipal(CARL), () -> XAssetExportPermission.isPermitted(
                     globalWs, globalWs.getPath(), true, ownerPrincipal(CARL))),
                  "precondition: carl has no ADMIN permission on the global worksheet");

      run(backupTask("Deps", CARL, BACKUP_PATH, SUtil.getXAsset("VIEWSHEET", "carlVs", CARL)));

      assertWritten("carl~;~" + ORG_A + "^carlVs");
      assertWritten("^iabrGlobalWs");
   }

   // Bug #77922 R2: a global asset without a security resource is checked for ADMIN on the ASSET
   // resource of its unscoped path, as the task editor checks a selected asset
   @Test
   void dataCycleAsset_checkedLikeTheTaskEditor() throws Throwable {
      XAsset cycle = SUtil.getXAsset(DataCycleAsset.DATACYCLE, "iabrCycle", null);
      assertNull(cycle.getSecurityResource());
      assertEquals(cycle.getPath(), SUtil.getUnscopedPath(cycle.getPath()));

      boolean adminAllowed = as(ownerPrincipal(ALICE), () -> XAssetExportPermission.isPermitted(
         cycle, SUtil.getUnscopedPath(cycle.getPath()), true, ownerPrincipal(ALICE)));
      boolean plainAllowed = as(ownerPrincipal(CARL), () -> XAssetExportPermission.isPermitted(
         cycle, SUtil.getUnscopedPath(cycle.getPath()), true, ownerPrincipal(CARL)));
      assertTrue(adminAllowed, "precondition: the org admin administers the data cycle");
      assertFalse(plainAllowed, "precondition: a plain user does not");

      run(backupTask("Cycle", ALICE, BACKUP_PATH, cycle));
      verify(storage).write(anyString(), any(Path.class), any());

      Throwable plainError = assertThrows(Throwable.class, () -> run(
         backupTask("Cycle2", CARL, BACKUP_PATH, cycle)));
      assertTrue(plainError.getMessage().contains("may not back up"), plainError.getMessage());
   }

   // a task whose owner is missing and named like a site admin of another org runs with the
   // organization admin roles of its own org (Bug #77452), it still backs up the sheets of its
   // org and not those of another org
   @Test
   void missingSiteAdminNameOwner_backsUpOwnOrgOnly() throws Throwable {
      assertNotNull(SUtil.getSameNameSiteAdmin(
                       SecurityEngine.getSecurity().getSecurityProvider(), SROOT_IN_A),
                    "precondition: the owner is a missing site admin name");

      run(backupTask("RootOwn", SROOT_IN_A, BACKUP_PATH,
                     SUtil.getXAsset("WORKSHEET", "daveSecretWs", DAVE)));
      assertWritten("dave~;~" + ORG_A + "^daveSecretWs");

      reset(storage);
      Throwable e = assertThrows(Throwable.class, () -> run(
         backupTask("RootOther", SROOT_IN_A, BACKUP_PATH,
                    SUtil.getXAsset("WORKSHEET", "bobSecretWs", BOB))));
      assertTrue(e.getMessage().contains("bobSecretWs"), e.getMessage());
      verify(storage, never()).write(anyString(), any(Path.class), any());
   }

   private void assertWritten(String fragment) throws Exception {
      verify(storage).write(anyString(), any(Path.class), any());
      assertTrue(written.stream().anyMatch(n -> n.contains(fragment) && !n.endsWith("(0)")),
                 written.toString());
   }

   private static ScheduleTask backupTask(String name, IdentityID owner, String path,
                                          XAsset... assets)
   {
      IndividualAssetBackupAction action = new IndividualAssetBackupAction();
      action.setServerPaths(new ServerPathInfo(path));
      action.setAssets(List.of(assets));
      ScheduleTask task = new ScheduleTask(owner.convertToKey() + ":" + name);
      task.setOwner(owner);
      task.addAction(action);
      return task;
   }

   /**
    * Runs the task as the scheduler does, ScheduleTask.run() with the run principal, which
    * copies the task through its XML.
    */
   private static void run(ScheduleTask task) throws Throwable {
      SRPrincipal principal = SUtil.getScheduleTaskRunPrincipal(task, null, false);
      OrganizationContextHolder.setCurrentOrgId(task.getOwner().getOrgID());

      try {
         task.run(principal);
      }
      finally {
         OrganizationContextHolder.setCurrentOrgId(null);
      }
   }

   private static SRPrincipal ownerPrincipal(IdentityID owner) {
      return SUtil.getScheduleTaskOwnerPrincipal(owner, null, false);
   }

   private ScheduleTask importBackup(String name, String user, String path) throws Exception {
      ScheduleManager scheduleManager = mock(ScheduleManager.class);
      AnalyticRepository repository = mock(AnalyticRepository.class);
      when(repository.checkPermission(any(), eq(ResourceType.SCHEDULER), anyString(),
                                      eq(ResourceAction.ACCESS))).thenReturn(true);
      ImportTaskController importController = new ImportTaskController(
         scheduleManager, mock(ScheduleTaskFolderService.class), repository,
         SecurityEngine.getSecurity());
      Map<String, Object> sessionAttrs = new HashMap<>();
      HttpSession session = mock(HttpSession.class);
      doAnswer(inv -> sessionAttrs.put(inv.getArgument(0), inv.getArgument(1)))
         .when(session).setAttribute(anyString(), any());
      doAnswer(inv -> sessionAttrs.remove(inv.getArgument(0)))
         .when(session).removeAttribute(anyString());
      when(session.getAttribute(anyString())).thenAnswer(inv -> sessionAttrs.get(inv.getArgument(0)));
      HttpServletRequest request = mock(HttpServletRequest.class);
      when(request.getSession(true)).thenReturn(session);

      SRPrincipal carl = builder.principalOf("carl", ORG_A);
      carl.setProperty("__internal__", "true");
      String xml = "<schedule><Task name=\"" + name + "\" owner=\"carl~;~" + ORG_A +
         "\" enabled=\"true\"><Condition type=\"NeverRun\"/>" +
         "<Action type=\"Backup\"><ServerPath path=\"" + FTP + "\" useCredential=\"false\"/>" +
         "<XAsset type=\"WORKSHEET\" path=\"" + path + "\" user=\"" + user + "\"></XAsset>" +
         "</Action></Task></schedule>";
      FileData file = FileData.builder().name("tasks.xml")
         .content(Base64.getEncoder().encodeToString(xml.getBytes(StandardCharsets.UTF_8)))
         .build();
      OrganizationContextHolder.setCurrentOrgId(ORG_A);

      try {
         ImportTaskDialogModel dialog = as(carl, () -> importController.setTaskFile(
            file, request, carl));
         List<String> ids = dialog.tasks().stream().map(TaskDependencyModel::taskId).toList();
         ImportTaskResponse response = as(carl, () -> importController.importScheduleTask(
            ids, request, false, "http://h/", carl));
         assertTrue(response.failedTasks().isEmpty(), String.valueOf(response.failedTasks()));
      }
      finally {
         OrganizationContextHolder.setCurrentOrgId(null);
      }

      ArgumentCaptor<ScheduleTask> captor = ArgumentCaptor.forClass(ScheduleTask.class);
      verify(scheduleManager, atMost(1)).setScheduleTask(anyString(), captor.capture(),
                                                         any(Principal.class));
      return captor.getAllValues().isEmpty() ? null : captor.getValue();
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

   private static Field poolField() throws NoSuchFieldException {
      Field field = ScheduleTask.class.getDeclaredField("threadPool");
      field.setAccessible(true);
      return field;
   }

   /**
    * Runs the actions of a task in the calling thread.
    */
   private static final class CallerThreadExecutor extends AbstractExecutorService {
      @Override
      public void execute(Runnable command) {
         command.run();
      }

      @Override
      public void shutdown() {
      }

      @Override
      public List<Runnable> shutdownNow() {
         return List.of();
      }

      @Override
      public boolean isShutdown() {
         return false;
      }

      @Override
      public boolean isTerminated() {
         return false;
      }

      @Override
      public boolean awaitTermination(long timeout, TimeUnit unit) {
         return true;
      }
   }
}
