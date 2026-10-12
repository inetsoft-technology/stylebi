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

import inetsoft.sree.AnalyticRepository;
import inetsoft.sree.internal.cluster.Cluster;
import inetsoft.sree.schedule.*;
import inetsoft.sree.security.*;
import inetsoft.test.*;
import inetsoft.uql.XPrincipal;
import inetsoft.uql.asset.AssetEntry;
import inetsoft.uql.asset.AssetRepository;
import inetsoft.uql.asset.DependencyHandler;
import inetsoft.util.IndexedStorage;
import inetsoft.util.Tool;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.w3c.dom.Element;
import org.xml.sax.InputSource;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.PrintWriter;
import java.io.StringReader;
import java.io.StringWriter;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/*
 * Tier: [integration] - real ScheduleManager, real DataCycleManager and real IndexedStorage
 * (the DataCycleManagerOrgLifecycleTest wiring), plus one case with a mock ScheduleExt and a
 * mock ScheduleClient to observe the scheduler push.
 *
 * Regression: Bug #77213. Toggling the enabled state of a data cycle (schedule extension) task
 * stored it as an ordinary schedule task (a "ghost" that reads back with a mangled id) and
 * never changed the data cycle.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, ScheduleTestConfiguration.class,
                                  DataCycleManagerOrgLifecycleTest.DataCycleManagerConfig.class,
                                  DataCycleManagerOrgLifecycleTest.PortalThemesManagerConfig.class,
                                  ScheduleManagerExtensionTaskTest.RepositoryConfig.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class ScheduleManagerExtensionTaskTest {
   @Configuration
   static class RepositoryConfig {
      @Bean
      public AnalyticRepository analyticRepository() {
         return mock(AnalyticRepository.class);
      }
   }

   private static final String ORG = Organization.getDefaultOrganizationID();
   private static final String CYCLE = "Cycle77213";
   private static final String OWNER_KEY = new IdentityID(XPrincipal.SYSTEM, ORG).convertToKey();
   private static final String TASK_ID = OWNER_KEY + "__" + DataCycleManager.TASK_PREFIX + CYCLE;
   private static final String STAGE2_ID = TASK_ID + " Stage 2";

   @Autowired
   private DataCycleManager dataCycleManager;

   @Autowired
   private ScheduleManager scheduleManager;

   @Autowired
   private IndexedStorage indexedStorage;

   @Autowired
   private SecurityEngine securityEngine;

   @Autowired
   private Cluster cluster;

   private SRPrincipal admin;

   @BeforeEach
   void setUp() throws Exception {
      admin = new SRPrincipal(new IdentityID("admin", ORG),
                              new IdentityID[] { new IdentityID("Administrator", null) },
                              new String[0], ORG, Tool.getSecureRandom().nextLong());
      admin.setIgnoreLogin(true);

      // creates the data cycle asset, enabled by default
      dataCycleManager.addCondition(CYCLE, ORG, TimeCondition.at(1, 0, 0));
      dataCycleManager.setCycleInfo(CYCLE, ORG, new DataCycleManager.CycleInfo(CYCLE, ORG));
   }

   @AfterEach
   void tearDown() throws Exception {
      dataCycleManager.removeDataCycle(CYCLE, ORG);
   }

   @Test
   void toggleOfDataCycleTaskChangesDataCycleAndIsNotStoredAsScheduleTask() throws Exception {
      assertTrue(readAsset().isEnabled(), "precondition: data cycle enabled");
      installGeneratedTask(createCycleTask(true));

      // what ScheduleTaskService.setTaskEnabled() -> ScheduleService.saveTask() does
      ScheduleTask task = scheduleManager.getScheduleTask(TASK_ID, ORG).clone();
      task.setEnabled(false);
      scheduleManager.setScheduleTask(TASK_ID, task, null, admin);

      assertFalse(readAsset().isEnabled(), "the toggle must disable the data cycle");
      assertEquals(List.of(), getStoredCycleTasks(),
                   "a data cycle task must never be stored as an ordinary schedule task");
   }

   @Test
   void roundTrippedDataCycleTaskIsNotStoredAsScheduleTask() throws Exception {
      // e.g. a data cycle task imported through asset deploy (ScheduleTaskAsset.parseContent):
      // parseXML() drops the cycle info, and the data cycle has no generated task, so no
      // extension owns it. Bug #77883, the ':' in the name is kept, the id is not mangled
      ScheduleTask task = roundTrip(createCycleTask(true));
      assertEquals(ScheduleTask.Type.CYCLE_TASK, task.getType());
      assertEquals(TASK_ID, task.getTaskId(), "precondition: the id round-trips");
      assertNull(task.getCycleInfo(), "precondition: the cycle info is dropped");
      assertFalse(dataCycleManager.containsTask(TASK_ID, ORG),
                  "precondition: no extension owns the task");

      scheduleManager.setScheduleTask(task.getTaskId(), task, null, admin);

      assertEquals(List.of(), getStoredCycleTasks(),
                   "an unowned data cycle task must not be stored as a schedule task");
      assertTrue(readAsset().isEnabled(), "the data cycle must be left unchanged");
   }

   @Test
   void enableByTaskIdResolvesTheDataCycle() {
      // no generated task (no cycle info to look up), the name is parsed from the task id
      dataCycleManager.setEnable(STAGE2_ID, ORG, false);
      assertFalse(dataCycleManager.isEnable(CYCLE, ORG), "stage 2 task id maps to the cycle");
      assertFalse(dataCycleManager.isEnable(TASK_ID, ORG));

      dataCycleManager.setEnable(TASK_ID, ORG, true);
      assertTrue(dataCycleManager.isEnable(CYCLE, ORG), "stage 1 task id maps to the cycle");
      assertTrue(dataCycleManager.isEnable(STAGE2_ID, ORG));

      // data cycle names are still accepted (MVService, ScheduleCycleService)
      dataCycleManager.setEnable(CYCLE, ORG, false);
      assertFalse(dataCycleManager.isEnable(CYCLE, ORG));
   }

   @Test
   void toggleOfExtensionTaskReloadsAndPushesRegeneratedTask() throws Exception {
      ScheduleClient scheduleClient = mock(ScheduleClient.class);
      DependencyHandler dependencyHandler = mock(DependencyHandler.class);
      ScheduleExt ext = mock(ScheduleExt.class);
      ScheduleManager manager =
         new ScheduleManager(securityEngine, cluster, scheduleClient, dependencyHandler);
      manager.addScheduleExt(ext);

      ScheduleTask live = createCycleTask(true);
      ScheduleTask regenerated = createCycleTask(false);
      when(ext.getTasks(ORG)).thenReturn(List.of(live), List.of(regenerated));
      when(ext.containsTask(TASK_ID, ORG)).thenReturn(true);
      when(ext.isEnable(TASK_ID, ORG)).thenReturn(true);
      manager.reloadExtensions(ORG);

      ScheduleTask task = manager.getScheduleTask(TASK_ID, ORG).clone();
      task.setEnabled(false);
      manager.setScheduleTask(TASK_ID, task, null, admin);

      verify(ext).setEnable(TASK_ID, ORG, false);
      // ScheduleTask.equals() would match any disabled copy, verify by identity
      verify(scheduleClient).taskAdded(argThat((ScheduleTask t) -> t == regenerated));
      verify(scheduleClient, never()).taskAdded(argThat((ScheduleTask t) -> t == task));
      verifyNoInteractions(dependencyHandler);
      assertSame(regenerated, manager.getScheduleTask(TASK_ID, ORG));
      assertTrue(live.isEnabled(), "the cached extension task must not be mutated");
   }

   @Test
   void saveOfOrdinaryTaskDoesNotCallIntoExtensions() throws Exception {
      ScheduleExt ext = mock(ScheduleExt.class);
      ScheduleManager manager = new ScheduleManager(
         securityEngine, cluster, mock(ScheduleClient.class), mock(DependencyHandler.class));
      manager.addScheduleExt(ext);
      when(ext.getTasks(ORG)).thenReturn(List.of());
      manager.reloadExtensions(ORG);
      clearInvocations(ext);

      ScheduleTask task = new ScheduleTask("Task77213");
      task.setOwner(admin.getIdentityID());
      task.addCondition(TimeCondition.at(1, 0, 0));
      String taskId = task.getTaskId();

      try {
         // internal: skip the schedule permission check, not under test here
         manager.setScheduleTask(taskId, task, null, true, admin);

         // the extension task lists are not thread safe, ordinary saves must not iterate them
         verifyNoInteractions(ext);
      }
      finally {
         manager.removeScheduleTask(taskId, admin);
      }
   }

   @Test
   void toggleOfLoadedExtensionTaskUsesLoadedOwnership() throws Exception {
      ScheduleExt ext = mock(ScheduleExt.class);
      ScheduleManager manager = new ScheduleManager(
         securityEngine, cluster, mock(ScheduleClient.class), mock(DependencyHandler.class));
      manager.addScheduleExt(ext);
      when(ext.getTasks(ORG)).thenReturn(List.of(createCycleTask(true)));
      // e.g. the extension is regenerating its tasks, its own list is empty for now
      when(ext.containsTask(TASK_ID, ORG)).thenReturn(false);
      when(ext.isEnable(TASK_ID, ORG)).thenReturn(true);
      manager.reloadExtensions(ORG);

      ScheduleTask task = manager.getScheduleTask(TASK_ID, ORG).clone();
      task.setEnabled(false);
      manager.setScheduleTask(TASK_ID, task, null, admin);

      verify(ext).setEnable(TASK_ID, ORG, false);
      verify(ext, never()).containsTask(anyString(), anyString());
   }

   // Direct storage checks: the DataCycle asset is re-read (parsed) from IndexedStorage and the
   // storage keys are scanned, so neither depends on ScheduleManager/ScheduleTaskMap caches.

   @Test
   void toggleOfStage2TaskDisablesDataCycleAndWritesNoStorageKey() throws Exception {
      ScheduleTask stage1 = createCycleTask(true);
      ScheduleTask stage2 = createCycleTask(DataCycleManager.TASK_PREFIX + CYCLE + " Stage 2", true);
      assertEquals(STAGE2_ID, stage2.getTaskId(), "precondition: stage 2 task id");
      installGeneratedTask(stage1, stage2);
      Set<String> keysBefore = getCycleStorageKeys();

      ScheduleTask task = scheduleManager.getScheduleTask(STAGE2_ID, ORG).clone();
      task.setEnabled(false);
      scheduleManager.setScheduleTask(STAGE2_ID, task, null, admin);

      assertEquals(keysBefore, getCycleStorageKeys(), "no schedule task may be written to storage");
      assertNoStoredCycleTaskKey();
      assertFalse(readAsset().isEnabled(), "the stage 2 toggle must disable the data cycle");
   }

   @Test
   void toggleOfStage1TaskWritesNoStorageKey() throws Exception {
      installGeneratedTask(createCycleTask(true));
      Set<String> keysBefore = getCycleStorageKeys();

      ScheduleTask task = scheduleManager.getScheduleTask(TASK_ID, ORG).clone();
      task.setEnabled(false);
      scheduleManager.setScheduleTask(TASK_ID, task, null, admin);

      assertEquals(keysBefore, getCycleStorageKeys(), "no schedule task may be written to storage");
      assertNoStoredCycleTaskKey();
      assertFalse(readAsset().isEnabled(), "the toggle must disable the data cycle");
   }

   @Test
   void importOfDataCycleTaskDoesNotCreateGhost() throws Exception {
      // the asset deploy import path, content as written by ScheduleTaskAsset.writeContent()
      StringWriter buffer = new StringWriter();
      PrintWriter writer = new PrintWriter(buffer);
      writer.write("<ScheduleTask>");
      createCycleTask(true).writeXML(writer);
      writer.write("</ScheduleTask>");
      writer.flush();
      Set<String> keysBefore = getCycleStorageKeys();

      new inetsoft.util.dep.ScheduleTaskAsset().parseContent(
         new java.io.ByteArrayInputStream(
            buffer.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8)),
         null, true, false);

      assertEquals(keysBefore, getCycleStorageKeys(), "import must not store the cycle task");
      assertNoStoredCycleTaskKey();
      assertEquals(List.of(), getStoredCycleTasks(), "no ghost may be listed");
      assertTrue(readAsset().isEnabled(), "the data cycle must be left unchanged");
   }

   // Bug #77883, the imported cycle task id now equals the id of the generated task of the same
   // data cycle, the import must still leave the data cycle and its generated task alone
   @Test
   void overwritingImportOfGeneratedDataCycleTaskLeavesDataCycleUnchanged() throws Exception {
      installGeneratedTask(createCycleTask(true));
      StringWriter buffer = new StringWriter();
      PrintWriter writer = new PrintWriter(buffer);
      writer.write("<ScheduleTask>");
      createCycleTask(false).writeXML(writer);
      writer.write("</ScheduleTask>");
      writer.flush();
      Set<String> keysBefore = getCycleStorageKeys();
      inetsoft.util.dep.XAssetConfig config = new inetsoft.util.dep.XAssetConfig();
      config.setOverwriting(true);

      new inetsoft.util.dep.ScheduleTaskAsset().parseContent(
         new java.io.ByteArrayInputStream(
            buffer.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8)),
         config, true, false);

      assertEquals(keysBefore, getCycleStorageKeys(), "import must not store the cycle task");
      assertNoStoredCycleTaskKey();
      assertEquals(List.of(), getStoredCycleTasks(), "no ghost may be listed");
      assertTrue(readAsset().isEnabled(), "the data cycle must be left unchanged");
      assertNotNull(scheduleManager.getScheduleTask(TASK_ID, ORG),
                    "the generated task must be kept");
   }

   private Set<String> getCycleStorageKeys() {
      return new TreeSet<>(indexedStorage.getKeys(key -> key.contains(CYCLE), ORG));
   }

   private void assertNoStoredCycleTaskKey() {
      Set<String> keys = indexedStorage.getKeys(
         key -> key.contains(DataCycleManager.TASK_PREFIX.trim()) ||
            key.contains("DataCycle Task" + IdentityID.KEY_DELIMITER), ORG);
      assertEquals(Set.of(), keys, "no data cycle task key may exist in storage");
   }

   // cycle tasks stored in the ordinary task map (they read back without a cycle info)
   private List<String> getStoredCycleTasks() {
      List<String> result = new ArrayList<>();

      for(ScheduleTask task : scheduleManager.getScheduleTasks(ORG)) {
         if(task.getName() != null && task.getName().contains(CYCLE) && task.getCycleInfo() == null) {
            result.add(task.getTaskId());
         }
      }

      return result;
   }

   // mirrors DataCycleManager.generateTasks()
   private ScheduleTask createCycleTask(boolean enabled) {
      return createCycleTask(DataCycleManager.TASK_PREFIX + CYCLE, enabled);
   }

   private ScheduleTask createCycleTask(String name, boolean enabled) {
      ScheduleTask task = new ScheduleTask(name, ScheduleTask.Type.CYCLE_TASK);
      task.setEditable(false);
      task.setRemovable(false);
      task.setEnabled(enabled);
      task.setOwner(new IdentityID(XPrincipal.SYSTEM, ORG));
      task.setCycleInfo(new DataCycleManager.CycleInfo(CYCLE, ORG));
      task.addCondition(TimeCondition.at(1, 0, 0));
      return task;
   }

   private static ScheduleTask roundTrip(ScheduleTask task) throws Exception {
      StringWriter buffer = new StringWriter();
      PrintWriter writer = new PrintWriter(buffer);
      task.writeXML(writer);
      writer.flush();
      Element elem = DocumentBuilderFactory.newInstance().newDocumentBuilder()
         .parse(new InputSource(new StringReader(buffer.toString())))
         .getDocumentElement();
      ScheduleTask result = new ScheduleTask();
      result.parseXML(elem);
      return result;
   }

   // real generation needs an MV bound to the cycle, install a generated task directly
   @SuppressWarnings({ "unchecked", "rawtypes" })
   private void installGeneratedTask(ScheduleTask... tasks) throws Exception {
      Field tasksField = DataCycleManager.class.getDeclaredField("pregeneratedTasksMap");
      tasksField.setAccessible(true);
      Field statusField = DataCycleManager.class.getDeclaredField("orgPregeneratedTaskLoadedStatus");
      statusField.setAccessible(true);

      synchronized(dataCycleManager) {
         ((Map<String, Vector<ScheduleTask>>) tasksField.get(dataCycleManager))
            .put(ORG, new Vector<>(List.of(tasks)));
         ((Map<String, Boolean>) statusField.get(dataCycleManager)).put(ORG, true);
      }

      Field extField = ScheduleManager.class.getDeclaredField("extensionTasks");
      extField.setAccessible(true);
      // register the owner like reloadExtensions0() does. Without it the toggle falls back to
      // DataCycleManager.containsTask(), which reads pregeneratedTasksMap, and the async storage
      // refresh fired by the setUp() writes regenerates that map without these tasks
      Field ownersField = ScheduleManager.class.getDeclaredField("extensionTaskOwners");
      ownersField.setAccessible(true);
      Class<?> keyClass = Class.forName("inetsoft.sree.schedule.ScheduleManager$ExtTaskKey");
      Constructor<?> keyConstructor = keyClass.getDeclaredConstructor(String.class, String.class);
      keyConstructor.setAccessible(true);
      for(ScheduleTask task : tasks) {
         Object key = keyConstructor.newInstance(task.getTaskId(), ORG);
         ((Map) extField.get(scheduleManager)).put(key, task);
         ((Map) ownersField.get(scheduleManager)).put(key, dataCycleManager);
      }

      Field loadedField = ScheduleManager.class.getDeclaredField("extensionTasksLoadedOrgs");
      loadedField.setAccessible(true);
      ((Set<String>) loadedField.get(scheduleManager)).add(ORG);
   }

   private DataCycleManager.DataCycleAsset readAsset() throws Exception {
      String key = new AssetEntry(AssetRepository.GLOBAL_SCOPE, AssetEntry.Type.DATA_CYCLE,
                                  "/__DATA_CYCLE__" + CYCLE, null, ORG).toIdentifier();
      return (DataCycleManager.DataCycleAsset) indexedStorage.getXMLSerializable(key, null, ORG);
   }
}
