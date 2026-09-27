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
import inetsoft.uql.asset.DependencyHandler;
import inetsoft.uql.asset.EmbeddedTableStorage;
import inetsoft.uql.service.DataSourceRegistry;
import inetsoft.util.*;
import inetsoft.util.dep.ScheduleTaskAsset;
import inetsoft.util.dep.XAssetConfig;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedStatic;

import java.io.File;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Bug #77192: importing a schedule task from a JAR must not accept a cloud secret id that the
 * importer does not already manage. The same rule as saving a task applies, and a rejected task
 * is reported as failed and not imported.
 */
@Tag("core")
class DeployManagerServiceScheduleSecretIdImportTest {
   @BeforeEach
   void setUp() {
      tool = mockStatic(Tool.class, CALLS_REAL_METHODS);
      tool.when(Tool::isCloudSecrets).thenReturn(true);
      sutil = mockStatic(SUtil.class, CALLS_REAL_METHODS);
      sutil.when(SUtil::getServerLocations).thenReturn(List.of());
      sutil.when(SUtil::isMultiTenant).thenReturn(true);
      sutil.when(() -> SUtil.getIdentity(any(), anyInt())).thenReturn(null);
      orgManager = mock(OrganizationManager.class);
      when(orgManager.getCurrentOrgID()).thenReturn("orga");
      orgManagerStatic = mockStatic(OrganizationManager.class);
      orgManagerStatic.when(OrganizationManager::getInstance).thenReturn(orgManager);
      scheduleManager = mock(ScheduleManager.class);
      scheduleManagerStatic = mockStatic(ScheduleManager.class);
      scheduleManagerStatic.when(ScheduleManager::getScheduleManager).thenReturn(scheduleManager);
      scheduleManagerStatic.when(() -> ScheduleManager.getTaskId(anyString(), anyString()))
         .thenAnswer(inv -> inv.getArgument(0) + ":" + inv.getArgument(1));

      securityEngine = mock(SecurityEngine.class);
      when(securityEngine.isSecurityEnabled()).thenReturn(true);
      service = new DeployManagerService(
         securityEngine, mock(DependencyHandler.class), mock(DataSourceRegistry.class),
         mock(DashboardRegistryManager.class), mock(LibManagerProvider.class),
         mock(DashboardManager.class), mock(XRepository.class), mock(FileSystemService.class),
         mock(DataSpace.class), mock(EmbeddedTableStorage.class),
         mock(RepletRegistryManager.class));

      principal = mock(XPrincipal.class);
      when(principal.getName()).thenReturn(OWNER.convertToKey());
   }

   @AfterEach
   void tearDown() {
      scheduleManagerStatic.close();
      orgManagerStatic.close();
      sutil.close();
      tool.close();
   }

   @Test
   void rejectsTaskWithForeignSecretIdWithoutImportingIt() throws Exception {
      ScheduleTaskAsset asset = taskAsset();

      List<String> failed = importEntry(taskXml("ftp://collector.invalid/backup", FOREIGN_ID),
                                        asset);

      String expected = Catalog.getCatalog().getString(
         "em.import.file.failed.secretIdNotAllowed", ScheduleTaskAsset.SCHEDULETASK + " imported");
      assertEquals(List.of(expected), failed);
      verify(asset, never()).parseContent(any(InputStream.class), any(), anyBoolean(),
                                          anyBoolean());
      tool.verify(() -> Tool.loadCredentials(anyString()), never());
      tool.verify(() -> Tool.decryptPassword(eq(FOREIGN_ID), anyBoolean()), never());
   }

   @Test
   void acceptsReimportWithUnchangedSecretIdAndServer() throws Exception {
      File file = write(taskXml("ftp://files.corp.example/backup", OWN_ID));
      ServerPathInfo storedPath = new ServerPathInfo("ftp://files.corp.example/old");
      storedPath.setUseCredential(true);
      storedPath.setSecretId(OWN_ID);
      IndividualAssetBackupAction storedAction = new IndividualAssetBackupAction();
      storedAction.setServerPaths(storedPath);
      ScheduleTask stored = new ScheduleTask("imported");
      stored.addAction(storedAction);
      when(scheduleManager.getScheduleTask(OWNER.convertToKey() + ":imported")).thenReturn(stored);

      assertTrue(service.isImportedScheduleSecretIdsAllowed(file, principal));
      assertFalse(service.isImportedScheduleSecretIdsAllowed(
         write(taskXml("ftp://files.corp.example/x@collector.invalid/backup", OWN_ID)),
         principal));
   }

   @Test
   void siteAdminMayImportNewSecretId() throws Exception {
      when(orgManager.isSiteAdmin(principal)).thenReturn(true);

      assertTrue(service.isImportedScheduleSecretIdsAllowed(
         write(taskXml("ftp://collector.invalid/backup", FOREIGN_ID)), principal));
   }

   @Test
   void localSecretsModeIsUnaffected() throws Exception {
      tool.when(Tool::isCloudSecrets).thenReturn(false);

      assertTrue(service.isImportedScheduleSecretIdsAllowed(
         write(taskXml("ftp://collector.invalid/backup", FOREIGN_ID)), principal));
   }

   private ScheduleTaskAsset taskAsset() throws Exception {
      ScheduleTaskAsset asset = mock(ScheduleTaskAsset.class);
      when(asset.getType()).thenReturn(ScheduleTaskAsset.SCHEDULETASK);
      when(asset.getPath()).thenReturn("imported");
      // an existing asset without a security resource skips the deploy permission check
      when(asset.exists()).thenReturn(true);
      when(asset.getSecurityResource()).thenReturn(null);
      return asset;
   }

   /**
    * Imports one SCHEDULETASK entry through the private per-asset import step.
    */
   private List<String> importEntry(String content, ScheduleTaskAsset asset) throws Exception {
      File file = write(content);
      Map<String, String> names = new HashMap<>();
      names.put(file.getName(), ScheduleTaskAsset.SCHEDULETASK + "_" +
         ScheduleTaskAsset.class.getName() + "^imported^" + OWNER.convertToKey());
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

   private static String taskXml(String path, String secretId) {
      return "<?xml version=\"1.0\" encoding=\"UTF-8\" ?><scheduleTask><Task name=\"imported\"" +
         " owner=\"" + OWNER.convertToKey() + "\" enabled=\"true\" path=\"/\">" +
         "<Condition type=\"NeverRun\"/><Action type=\"Backup\"><ServerPath path=\"" + path +
         "\" useCredential=\"true\" secretId=\"" + secretId + "\"/></Action></Task>" +
         "</scheduleTask>";
   }

   private static final IdentityID OWNER = new IdentityID("alice", "orga");
   private static final String FOREIGN_ID = "org-b-secret";
   private static final String OWN_ID = "own-secret";

   @TempDir
   Path tempDir;
   private int fileCount;
   private MockedStatic<Tool> tool;
   private MockedStatic<SUtil> sutil;
   private MockedStatic<OrganizationManager> orgManagerStatic;
   private MockedStatic<ScheduleManager> scheduleManagerStatic;
   private OrganizationManager orgManager;
   private ScheduleManager scheduleManager;
   private SecurityEngine securityEngine;
   private DeployManagerService service;
   private XPrincipal principal;
}
