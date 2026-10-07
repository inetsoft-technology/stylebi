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
import inetsoft.test.*;
import inetsoft.uql.XPrincipal;
import inetsoft.uql.XRepository;
import inetsoft.uql.asset.DependencyHandler;
import inetsoft.uql.asset.EmbeddedTableStorage;
import inetsoft.uql.service.DataSourceRegistry;
import inetsoft.uql.util.Identity;
import inetsoft.uql.util.XSessionService;
import inetsoft.uql.viewsheet.FileFormatInfo;
import inetsoft.util.*;
import inetsoft.util.dep.ScheduleTaskAsset;
import inetsoft.util.dep.XAsset;
import inetsoft.util.dep.XAssetConfig;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

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
 * Bug #77936, a deploy import by an importer that isn't a site admin applies the stored password
 * rule of the task editor (#77192): a local password of a save-to-server path in the file is
 * only kept when the task that the import replaces already stores the same user name and
 * password for the same server. Otherwise it's cleared and the import reports a warning. The
 * file is written and read the way a deploy export and import do (force master encryption and
 * force local encryption/decryption). Both the viewsheet action save-to-server path and the
 * backup action server path are covered.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = BaseTestConfiguration.class,
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class DeployManagerServiceScheduleStoredPasswordImportTest {
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

      SecurityProvider provider = mock(SecurityProvider.class);
      Set<IdentityID> users = Set.of(CALLER, ALICE);
      when(provider.getUser(any(IdentityID.class))).thenAnswer(
         inv -> users.contains(inv.<IdentityID>getArgument(0)) ?
            new User(inv.<IdentityID>getArgument(0)) : null);
      when(provider.getUsers()).thenReturn(users.toArray(new IdentityID[0]));
      when(provider.getGroups()).thenReturn(new IdentityID[0]);
      // the org admin administers the users of its org
      when(provider.checkPermission(any(), any(ResourceType.class), anyString(),
                                    eq(ResourceAction.ADMIN)))
         .thenAnswer(inv -> ORG_A.equals(
            IdentityID.getIdentityIDFromKey(inv.<String>getArgument(2)).getOrgID()));

      SecurityEngine securityEngine = mock(SecurityEngine.class);
      when(securityEngine.isSecurityEnabled()).thenReturn(true);
      when(securityEngine.getSecurityProvider()).thenReturn(provider);
      when(securityEngine.checkPermission(any(), eq(ResourceType.SCHEDULER), eq("*"),
                                          eq(ResourceAction.ACCESS))).thenReturn(true);
      securityStatic = mockStatic(SecurityEngine.class);
      securityStatic.when(SecurityEngine::getSecurity).thenReturn(securityEngine);
      storageStatic = mockStatic(IndexedStorage.class);
      storageStatic.when(IndexedStorage::getIndexedStorage).thenReturn(mock(IndexedStorage.class));
      // the owner principal parseContent stores the task with needs a session id
      sessionStatic = mockStatic(XSessionService.class);
      sessionStatic.when(XSessionService::getService).thenReturn(mock(XSessionService.class));

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
      sessionStatic.close();
      storageStatic.close();
      securityStatic.close();
      scheduleManagerStatic.close();
      orgManagerStatic.close();
      sutil.close();
   }

   @Test
   void overwrite_sameServerAndPassword_keepsPassword() throws Exception {
      when(scheduleManager.getScheduleTask(TASK_ID)).thenReturn(storedTask());

      ScheduleTask imported = importTask(STORED_HOST, true);

      assertPasswords(imported, STORED_HOST, STORED);
      assertTrue(warnings.isEmpty(), warnings.toString());
   }

   @Test
   void overwrite_changedServer_clearsPasswordWithWarning() throws Exception {
      when(scheduleManager.getScheduleTask(TASK_ID)).thenReturn(storedTask());

      ScheduleTask imported = importTask(OTHER_HOST, true);

      assertPasswords(imported, OTHER_HOST, "");
      assertEquals(List.of(Catalog.getCatalog().getString(
         "em.import.schedulePasswordsCleared", "imported")), warnings);
   }

   // a new task replaces no stored task, so there is nothing that already holds the password
   @Test
   void newTask_changedServer_clearsPassword() throws Exception {
      ScheduleTask imported = importTask(OTHER_HOST, false);

      assertPasswords(imported, OTHER_HOST, "");
      assertEquals(1, warnings.size(), warnings.toString());
   }

   @Test
   void newTask_unchangedServer_clearsPassword() throws Exception {
      ScheduleTask imported = importTask(STORED_HOST, false);

      assertPasswords(imported, STORED_HOST, "");
      assertEquals(1, warnings.size(), warnings.toString());
   }

   // site admins aren't restricted, the same as the other deploy import checks
   @Test
   void siteAdmin_changedServer_keepsPassword() throws Exception {
      when(orgManager.isSiteAdmin(any(Principal.class))).thenReturn(true);
      when(scheduleManager.getScheduleTask(TASK_ID)).thenReturn(storedTask());

      ScheduleTask imported = importTask(OTHER_HOST, true);

      assertPasswords(imported, OTHER_HOST, STORED);
      assertTrue(warnings.isEmpty(), warnings.toString());
   }

   /**
    * Imports the deploy export of the stored task, with the server of its paths set to host,
    * through the private per-asset import step, inside the import's force local decryption.
    */
   private ScheduleTask importTask(String host, boolean overwriting) throws Exception {
      File file = tempDir.resolve("task.xml").toFile();
      Files.writeString(file.toPath(), deployExport().replace(STORED_HOST, host),
                        StandardCharsets.UTF_8);
      Map<String, String> names = new HashMap<>();
      names.put(file.getName(), ScheduleTaskAsset.SCHEDULETASK + "_" +
         ScheduleTaskAsset.class.getName() + "^imported^" + XAsset.NULL);
      DeploymentInfo info = mock(DeploymentInfo.class);
      when(info.getNames()).thenReturn(names);
      when(info.getImportWarnings()).thenReturn(warnings);
      XAssetConfig config = new XAssetConfig();
      config.setOverwriting(overwriting);
      ScheduleTaskAsset asset = spy(new ScheduleTaskAsset());
      doReturn("imported").when(asset).getPath();
      // a new task has no parent security resource, so no deploy permission check runs
      doReturn(false).when(asset).exists();
      List<String> failed = new ArrayList<>();

      Method method = Arrays.stream(DeployManagerService.class.getDeclaredMethods())
         .filter(m -> m.getName().equals("importAsset") && m.getParameterCount() == 14)
         .findFirst().orElseThrow();
      method.setAccessible(true);
      PasswordEncryption.setDecryptForceLocal(true);

      try {
         method.invoke(service, file, asset, new ArrayList<>(), failed, null, new ArrayList<>(),
                       new ArrayList<>(), overwriting, null, info, false, config, null,
                       principal);
      }
      finally {
         PasswordEncryption.setDecryptForceLocal(false);
      }

      assertEquals(List.of(), failed);
      ArgumentCaptor<ScheduleTask> stored = ArgumentCaptor.forClass(ScheduleTask.class);
      verify(scheduleManager).setScheduleTask(eq(TASK_ID), stored.capture(), isNull(),
                                              any(Principal.class));
      return stored.getValue();
   }

   private static void assertPasswords(ScheduleTask task, String host, String password) {
      ServerPathInfo vsPath = ((ViewsheetAction) task.getAction(0)).getFilePathInfo(PDF);
      ServerPathInfo backupPath = ((IndividualAssetBackupAction) task.getAction(1)).getServerPath();
      assertEquals("ftp://" + host + "/out", vsPath.getPath());
      assertEquals("ftp://" + host + "/backup", backupPath.getPath());
      assertEquals(password, vsPath.getPassword(), "save-to-server password");
      assertEquals(password, backupPath.getPassword(), "backup password");
   }

   /**
    * The stored task as a deploy export writes it (DeployUtil.createExport).
    */
   private static String deployExport() {
      StringWriter out = new StringWriter();
      PasswordEncryption.setForceMaster(true);
      PasswordEncryption.setEncryptForceLocal(true);

      try(PrintWriter writer = new PrintWriter(out)) {
         writer.print("<?xml version=\"1.0\" encoding=\"UTF-8\" ?><scheduleTask>");
         storedTask().writeXML(writer);
         writer.print("</scheduleTask>");
      }
      finally {
         PasswordEncryption.setForceMaster(false);
         PasswordEncryption.setEncryptForceLocal(false);
      }

      return out.toString();
   }

   private static ScheduleTask storedTask() {
      ScheduleTask task = new ScheduleTask("Nightly");
      task.setOwner(ALICE);
      task.addCondition(new NeverRunCondition());
      ViewsheetAction vsAction = new ViewsheetAction();
      vsAction.setViewsheet("1^128^__NULL__^vs1^" + ORG_A);
      vsAction.setFilePath(PDF, new ServerPathInfo("ftp://" + STORED_HOST + "/out", "bob", STORED));
      task.addAction(vsAction);
      IndividualAssetBackupAction backupAction = new IndividualAssetBackupAction();
      backupAction.setServerPaths(
         new ServerPathInfo("ftp://" + STORED_HOST + "/backup", "bob", STORED));
      task.addAction(backupAction);
      return task;
   }

   private static final String ORG_A = "orga";
   private static final IdentityID CALLER = new IdentityID("oa", ORG_A);
   private static final IdentityID ALICE = new IdentityID("alice", ORG_A);
   private static final String TASK_ID = "alice~;~" + ORG_A + ":Nightly";
   private static final int PDF = FileFormatInfo.EXPORT_TYPE_PDF;
   private static final String STORED_HOST = "files.corp.example";
   private static final String OTHER_HOST = "collector.invalid";
   private static final String STORED = "stored-password";

   @TempDir
   Path tempDir;
   private final List<String> warnings = new ArrayList<>();
   private MockedStatic<SUtil> sutil;
   private MockedStatic<OrganizationManager> orgManagerStatic;
   private MockedStatic<ScheduleManager> scheduleManagerStatic;
   private MockedStatic<SecurityEngine> securityStatic;
   private MockedStatic<IndexedStorage> storageStatic;
   private MockedStatic<XSessionService> sessionStatic;
   private OrganizationManager orgManager;
   private ScheduleManager scheduleManager;
   private DeployManagerService service;
   private XPrincipal principal;
}
