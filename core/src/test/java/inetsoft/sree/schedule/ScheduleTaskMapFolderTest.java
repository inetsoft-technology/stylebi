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
import inetsoft.test.*;
import inetsoft.uql.asset.AssetEntry;
import inetsoft.uql.asset.AssetRepository;
import inetsoft.uql.asset.internal.AssetFolder;
import inetsoft.util.IndexedStorage;
import inetsoft.util.ThreadContext;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.security.Principal;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #78740, a put of a task never leaves it listed in more than one folder. The current folder
 * is found by folder membership, not by the path of the (cached) task, and a null parent keeps
 * the stored folder of an existing task.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, IntegrationTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class ScheduleTaskMapFolderTest {
   private static final String ORG = "stmforg";
   private static final IdentityID ALICE = new IdentityID("alice", ORG);

   @Autowired
   ScheduleManager scheduleManager;

   private Principal savedPrincipal;

   @BeforeEach
   void setUp() throws Exception {
      savedPrincipal = ThreadContext.getContextPrincipal();
      addFolder("FolderA");
      addFolder("FolderB");
   }

   @AfterEach
   void tearDown() {
      ThreadContext.setContextPrincipal(savedPrincipal);
      taskMap().clear();
      taskMap().clearCache();
   }

   // the put of the same task into another folder moves it
   @Test
   void putIntoAnotherFolder_removesTheOldFolderEntry() throws Exception {
      String key = store("StmfMove", folder("FolderA"));

      taskMap().put(key, loaded(key), folder("FolderB"), ORG);

      assertListedOnlyIn(key, "FolderB");
   }

   // putting into the root folder (xml path "/") removes the entry of the folder
   @Test
   void putIntoRoot_removesTheOldFolderEntry() throws Exception {
      String key = store("StmfRoot", folder("FolderA"));

      taskMap().put(key, loaded(key), folder("/"), ORG);

      assertListedOnlyIn(key, "/");
   }

   // a 4-arg put with a null parent keeps the folder of a stored task
   @Test
   void nullParent_keepsTheStoredFolder() throws Exception {
      String key = store("StmfKeep", folder("FolderA"));

      taskMap().put(key, loaded(key), null, ORG);

      assertListedOnlyIn(key, "FolderA");
      assertEquals("FolderA", loaded(key).getPath());
   }

   // a null parent for a new task is the root folder
   @Test
   void nullParent_newTaskGoesToRoot() throws Exception {
      ScheduleTask task = newTask("StmfNew");
      String key = key(task.getTaskId());

      taskMap().put(key, task, null, ORG);

      assertListedOnlyIn(key, "/");
   }

   // the cached instance may have its path changed before the put, the old folder is still found
   @Test
   void mutatedCachedInstance_oldFolderEntryIsStillRemoved() throws Exception {
      String key = store("StmfMutated", folder("FolderA"));
      ScheduleTask cached = loaded(key);
      cached.setPath("FolderB");

      taskMap().put(key, cached, folder("FolderB"), ORG);

      assertListedOnlyIn(key, "FolderB");
   }

   // a stored path of a folder that doesn't exist doesn't break the put
   @Test
   void staleTaskPath_isTolerated() throws Exception {
      String key = store("StmfStale", folder("FolderA"));
      ScheduleTask cached = loaded(key);
      cached.setPath("Gone");

      taskMap().put(key, cached, folder("FolderB"), ORG);

      assertListedOnlyIn(key, "FolderB");
   }

   private String store(String name, AssetEntry parent) throws Exception {
      ScheduleTask task = newTask(name);
      String key = key(task.getTaskId());
      taskMap().put(key, task, parent, ORG);
      assertListedOnlyIn(key, parent.getPath());
      return key;
   }

   private void assertListedOnlyIn(String key, String path) throws Exception {
      AssetEntry entry = AssetEntry.createAssetEntry(key, ORG);

      for(String p : new String[] { "/", "FolderA", "FolderB" }) {
         AssetFolder f = getFolder(p);
         assertEquals(p.equals(path), f != null && f.containsEntry(entry),
                      "listed in " + p + ", expected only in " + path);
      }
   }

   private ScheduleTask loaded(String key) {
      ScheduleTask task = taskMap().get(key);
      assertNotNull(task);
      return task;
   }

   private ScheduleTaskMap taskMap() {
      return scheduleManager.getOrgTaskMap(ORG);
   }

   private String key(String taskId) {
      return ReflectionTestUtils.invokeMethod(scheduleManager, "getTaskIdentifier", taskId, ORG);
   }

   private void addFolder(String path) throws Exception {
      IndexedStorage storage = IndexedStorage.getIndexedStorage();
      AssetEntry entry = folder(path);
      storage.putXMLSerializable(entry.toIdentifier(), new AssetFolder());
      AssetFolder root = getFolder("/");

      if(root == null) {
         root = new AssetFolder();
      }

      root.addEntry(entry);
      storage.putXMLSerializable(folder("/").toIdentifier(), root);
   }

   private static AssetFolder getFolder(String path) throws Exception {
      return (AssetFolder) IndexedStorage.getIndexedStorage()
         .getXMLSerializable(folder(path).toIdentifier(), null, ORG);
   }

   private static AssetEntry folder(String path) {
      return new AssetEntry(AssetRepository.GLOBAL_SCOPE, AssetEntry.Type.SCHEDULE_TASK_FOLDER,
                            path, null, ORG);
   }

   private static ScheduleTask newTask(String name) {
      ScheduleTask task = new ScheduleTask(name);
      task.setOwner(ALICE);
      task.setRemovable(false);
      task.addCondition(TimeCondition.at(1, 30, 0));
      return task;
   }
}
