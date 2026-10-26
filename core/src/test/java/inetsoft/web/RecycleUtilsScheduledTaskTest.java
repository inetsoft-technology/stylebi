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
package inetsoft.web;

import inetsoft.sree.RepletRegistry;
import inetsoft.sree.RepletRegistryManager;
import inetsoft.sree.schedule.*;
import inetsoft.sree.security.*;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.IntegrationTestConfiguration;
import inetsoft.test.SecurityEngineDispatchConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import inetsoft.storage.KeyValueStorageManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import inetsoft.uql.asset.*;
import inetsoft.uql.asset.internal.AssetUtil;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.util.MessageException;
import inetsoft.util.Tool;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.*;

/**
 * A dashboard that a scheduled task's action uses can't be removed (Redmine #77599). Real
 * repository, registry and {@link ScheduleManager}, no mocks.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, IntegrationTestConfiguration.class,
                                  SecurityEngineDispatchConfiguration.class,
                                  RecycleUtilsScheduledTaskTest.RecycleBinConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class RecycleUtilsScheduledTaskTest {
   @BeforeEach
   void setUp() throws Exception {
      admin = new SRPrincipal(new IdentityID("admin", Organization.getDefaultOrganizationID()),
                              new IdentityID[] { new IdentityID("Administrator", null) },
                              new String[0], Organization.getDefaultOrganizationID(),
                              Tool.getSecureRandom().nextLong());
      admin.setIgnoreLogin(true);
      repository = AssetUtil.getAssetRepository(false);
      repository.setSheet(vs, new Viewsheet(), admin, true);

      task = new ScheduleTask(TASK);
      task.setOwner(owner);
      task.addCondition(TimeCondition.at(1, 30, 0));
      task.addAction(new ViewsheetAction(vs.toIdentifier(), null));
      ScheduleManager.getScheduleManager().setScheduleTask(task.getTaskId(), task, admin);
   }

   @AfterEach
   void tearDown() throws Exception {
      ScheduleManager manager = ScheduleManager.getScheduleManager();

      if(manager.getScheduleTask(task.getTaskId()) != null) {
         manager.removeScheduleTask(task.getTaskId(), admin);
      }

      if(repository.containsEntry(vs)) {
         repository.removeSheet(vs, admin, true);
      }

      // the fork's storage outlives this class, so don't leave the moved sheet in the bin
      RecycleBin bin = RecycleBin.getRecycleBin();

      for(RecycleBin.Entry entry : new ArrayList<>(bin.getEntries())) {
         if(vs.getPath().equals(entry.getOriginalPath())) {
            repository.removeSheet(new AssetEntry(
               AssetRepository.GLOBAL_SCOPE, AssetEntry.Type.VIEWSHEET, entry.getPath(), null),
               admin, true);
            bin.removeEntry(entry.getPath());
         }
      }
   }

   @Test
   void forcedMoveToRecycleBinIsRefusedAndKeepsTheAction() throws Exception {
      MessageException ex = assertThrows(MessageException.class, () ->
         RecycleUtils.moveSheetToRecycleBin(vs, admin, RecycleBin.getRecycleBin(), true));

      assertTrue(ex.getMessage().contains(TASK), ex.getMessage());
      assertTrue(repository.containsEntry(vs));
      assertEquals(1, storedTask().getActionCount());
   }

   @Test
   void folderHoldingTheDashboardIsRefused() throws Exception {
      // the check reads only the tasks, so the dashboard in the folder needn't exist
      useDashboard(new AssetEntry(
         AssetRepository.GLOBAL_SCOPE, AssetEntry.Type.VIEWSHEET, "Bug77599/Dash", null));

      MessageException ex = assertThrows(MessageException.class, () ->
         RecycleUtils.checkScheduledTasks("Bug77599", null));
      assertTrue(ex.getMessage().contains(TASK), ex.getMessage());

      // a sibling folder whose name is a prefix of the dashboard's folder is not affected
      assertDoesNotThrow(() -> RecycleUtils.checkScheduledTasks("Bug7759", null));
      assertDoesNotThrow(() -> RecycleUtils.checkScheduledTasks("Other", null));
   }

   @Test
   void userFolderHoldingTheDashboardIsRefused() throws Exception {
      // a folder under My Dashboards maps to the owner's USER_SCOPE repository folder
      useDashboard(new AssetEntry(
         AssetRepository.USER_SCOPE, AssetEntry.Type.VIEWSHEET, "Bug77599/Dash", owner));

      MessageException ex = assertThrows(MessageException.class, () ->
         RecycleUtils.checkScheduledTasks(Tool.MY_DASHBOARD + "/Bug77599", owner));
      assertTrue(ex.getMessage().contains(TASK), ex.getMessage());

      // the same path in the global scope, or another user's, is a different folder
      assertDoesNotThrow(() -> RecycleUtils.checkScheduledTasks("Bug77599", null));
      assertDoesNotThrow(() -> RecycleUtils.checkScheduledTasks(
         Tool.MY_DASHBOARD + "/Bug77599", new IdentityID("other", owner.orgID)));
   }

   @Test
   void folderMoveToRecycleBinIsRefusedAndKeepsTheFolder() throws Exception {
      useDashboard(new AssetEntry(
         AssetRepository.GLOBAL_SCOPE, AssetEntry.Type.VIEWSHEET, "Bug77599/Dash", null));
      RepletRegistry registry = RepletRegistryManager.getInstance().getRegistry();
      registry.addFolder("Bug77599");
      registry.save();

      try {
         // refused with or without the dependency confirmation
         for(boolean force : new boolean[] { false, true }) {
            MessageException ex = assertThrows(MessageException.class, () ->
               RecycleUtils.moveRepositoryFolderToRecycleBin(
                  "Bug77599", "Bug77599", null, admin, RecycleBin.getRecycleBin(), force));

            assertTrue(ex.getMessage().contains(TASK), ex.getMessage());
            assertTrue(registry.isFolder("Bug77599"));
            assertEquals(1, storedTask().getActionCount());
         }
      }
      finally {
         registry.removeFolder("Bug77599");
         registry.save();
      }
   }

   @Test
   void dashboardNoTaskUsesCanBeMoved() throws Exception {
      ScheduleTask stored = storedTask();
      stored.removeAction(0);
      ScheduleManager.getScheduleManager().setScheduleTask(stored.getTaskId(), stored, admin);

      RecycleUtils.moveSheetToRecycleBin(vs, admin, RecycleBin.getRecycleBin(), false);
      assertFalse(repository.containsEntry(vs));
   }

   private ScheduleTask storedTask() {
      return ScheduleManager.getScheduleManager().getScheduleTask(task.getTaskId());
   }

   private void useDashboard(AssetEntry entry) throws Exception {
      ScheduleTask stored = storedTask();
      stored.removeAction(0);
      stored.addAction(new ViewsheetAction(entry.toIdentifier(), null));
      ScheduleManager.getScheduleManager().setScheduleTask(stored.getTaskId(), stored, admin);
   }

   // the recycle bin is a Spring bean that BaseTestConfiguration does not provide
   @Configuration
   static class RecycleBinConfiguration {
      @Bean
      RecycleBin recycleBin(KeyValueStorageManager keyValueStorageManager) {
         return new RecycleBin(keyValueStorageManager);
      }
   }

   private static final String TASK = "Bug77599Task";
   private final IdentityID owner = new IdentityID("admin", Organization.getDefaultOrganizationID());
   private final AssetEntry vs = new AssetEntry(
      AssetRepository.GLOBAL_SCOPE, AssetEntry.Type.VIEWSHEET, "Bug77599Dash", null);
   private SRPrincipal admin;
   private AssetRepository repository;
   private ScheduleTask task;
}
