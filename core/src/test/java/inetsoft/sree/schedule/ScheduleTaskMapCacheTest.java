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
package inetsoft.sree.schedule;

import inetsoft.sree.security.IdentityID;
import inetsoft.sree.security.SRPrincipal;
import inetsoft.storage.BlobStorageManager;
import inetsoft.test.*;
import inetsoft.util.BlobIndexedStorage;
import inetsoft.util.ThreadContext;
import inetsoft.util.Tool;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.security.Principal;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #78027: ScheduleTaskMap trusts its cached task while the last-modified time of the stored
 * task (epoch milliseconds of the blob commit) is unchanged. A put of an existing task reads, and
 * so caches, the old task before it writes the new one. When the new write commits in the same
 * millisecond as the old one, the stored time doesn't change and the old task kept being returned
 * after the new one was saved. The storage here reports the commit time of the first write for
 * the second write too, which is what two commits in the same millisecond look like.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, IntegrationTestConfiguration.class,
                                  ScheduleTaskMapCacheTest.Config.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class ScheduleTaskMapCacheTest {
   private static final String ORG = "stmcorg";
   private static final IdentityID ALICE = new IdentityID("alice", ORG);
   private static final String TARGET = "alice~;~" + ORG + ":StmcTarget";

   @Autowired
   ScheduleManager scheduleManager;

   @Autowired
   SameMillisecondStorage storage;

   private Principal savedPrincipal;

   @BeforeEach
   void setUp() {
      savedPrincipal = ThreadContext.getContextPrincipal();
   }

   @AfterEach
   void tearDown() {
      storage.unfreeze();
      ThreadContext.setContextPrincipal(savedPrincipal);
      taskMap().clear();
      taskMap().clearCache();
   }

   // the reported failure (BatchActionTargetPermissionTest stores a task, then replaces it)
   @Test
   void replacingPut_inTheSameMillisecond_getReturnsTheNewTask() {
      ScheduleTask first = newTask("StmcReplace", null);
      String key = key(first.getTaskId());
      taskMap().put(key, first);
      storage.freeze(key);

      taskMap().put(key, newTask("StmcReplace", TARGET));

      ScheduleTask stored = (ScheduleTask) assertDoesNotThrow(
         () -> storage.getXMLSerializable(key, null, ORG));
      assertEquals(List.of(TARGET), targets(stored), "the new task is in the storage");
      assertEquals(List.of(TARGET), targets(taskMap().get(key)), "the task read back");
   }

   // two saves of one task, each one a whole ScheduleManager save
   @Test
   void secondSave_inTheSameMillisecond_isReadBack() throws Exception {
      ScheduleTask first = newTask("StmcResave", null);
      String taskId = first.getTaskId();
      scheduleManager.setScheduleTask(taskId, first, principal());
      storage.freeze(key(taskId));

      scheduleManager.setScheduleTask(taskId, newTask("StmcResave", TARGET), principal());

      assertEquals(List.of(TARGET), targets(scheduleManager.getScheduleTask(taskId, ORG)),
                   "the task read back");
   }

   private ScheduleTaskMap taskMap() {
      return scheduleManager.getOrgTaskMap(ORG);
   }

   private String key(String taskId) {
      return ReflectionTestUtils.invokeMethod(scheduleManager, "getTaskIdentifier", taskId, ORG);
   }

   private static ScheduleTask newTask(String name, String target) {
      ScheduleTask task = new ScheduleTask(name);
      task.setOwner(ALICE);
      // saved without the schedule permission check, as ScheduleTaskOwnerOrgIdTest does
      task.setRemovable(false);
      // a task without a condition isn't loaded (ScheduleTask.parseXML)
      task.addCondition(TimeCondition.at(1, 30, 0));

      if(target != null) {
         BatchAction batch = new BatchAction();
         batch.setTaskId(target);
         task.addAction(batch);
      }

      return task;
   }

   private static List<String> targets(ScheduleTask task) {
      assertNotNull(task, "the task must not be lost");
      List<String> targets = new ArrayList<>();

      for(int i = 0; i < task.getActionCount(); i++) {
         if(task.getAction(i) instanceof BatchAction batch) {
            targets.add(batch.getTaskId());
         }
      }

      return targets;
   }

   private static SRPrincipal principal() {
      SRPrincipal principal = new SRPrincipal(ALICE, new IdentityID[0], new String[0], ORG,
                                              Tool.getSecureRandom().nextLong());
      principal.setIgnoreLogin(true);
      return principal;
   }

   /**
    * Indexed storage that can keep reporting the last-modified time a key had when it was
    * frozen, as if every later write of the key committed in the same millisecond.
    */
   static class SameMillisecondStorage extends BlobIndexedStorage {
      SameMillisecondStorage(BlobStorageManager blobStorageManager) {
         super(blobStorageManager);
      }

      void freeze(String key) {
         frozenTs = super.lastModified(key, ORG);
         frozenKey = key;
      }

      void unfreeze() {
         frozenKey = null;
      }

      @Override
      public long lastModified(String key, String orgID) {
         return key != null && key.equals(frozenKey) ? frozenTs : super.lastModified(key, orgID);
      }

      private volatile String frozenKey;
      private volatile long frozenTs;
   }

   @Configuration
   static class Config {
      // replaces the IntegrationTestConfiguration bean of the same name
      @Bean
      public SameMillisecondStorage indexedStorage(BlobStorageManager blobStorageManager) {
         return new SameMillisecondStorage(blobStorageManager);
      }
   }
}
