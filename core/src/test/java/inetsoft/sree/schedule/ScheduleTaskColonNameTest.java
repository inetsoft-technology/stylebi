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

import inetsoft.report.internal.Util;
import inetsoft.sree.internal.SUtil;
import inetsoft.sree.security.*;
import inetsoft.test.*;
import inetsoft.uql.asset.AssetEntry;
import inetsoft.uql.asset.AssetRepository;
import inetsoft.uql.asset.internal.AssetFolder;
import inetsoft.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.w3c.dom.Document;

import java.io.StringReader;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #77883: a task whose name contains ':' must load with the id it's stored under, so it's
 * listed in the schedule task lists and can be deleted. Only the name of a legacy task xml (an
 * owner without an organization) has a "user:task" owner prefix.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, IntegrationTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class ScheduleTaskColonNameTest {
   private static final String HOST = Organization.getDefaultOrganizationID();
   private static final IdentityID ADMIN = new IdentityID("admin", HOST);

   @Autowired
   ScheduleManager scheduleManager;

   private final Set<String> keys = new HashSet<>();

   @AfterEach
   void tearDown() {
      ScheduleTaskMap map = scheduleManager.getOrgTaskMap(HOST);

      for(String key : keys) {
         if(IndexedStorage.getIndexedStorage().contains(key, HOST)) {
            map.remove(key);
         }
      }

      keys.clear();
   }

   @ParameterizedTest
   @ValueSource(strings = { "task:name", "admin:report", "admin: nightly", "a:b:c", "plainName" })
   void colonName_roundTripsListsAndDeletes(String name) throws Exception {
      ScheduleTask task = new ScheduleTask(name);
      task.setOwner(ADMIN);
      String id = task.getTaskId();
      assertEquals(ADMIN.convertToKey() + ":" + name, id);
      String key = save(task);

      ScheduleTask loaded = load(key);
      assertEquals(name, loaded.getName());
      assertEquals(id, loaded.getTaskId());
      assertNotNull(scheduleManager.getScheduleTask(id, HOST));
      assertTrue(listedIds().contains(id), "portal/EM schedule list");

      scheduleManager.removeScheduleTask(id, null);
      assertFalse(IndexedStorage.getIndexedStorage().contains(key, HOST), "removed");
   }

   @Test
   void mvTaskName_roundTrips() throws Exception {
      String name = Util.MV_TASK_PREFIX + UUID.randomUUID();
      ScheduleTask task = new ScheduleTask(name, ScheduleTask.Type.MV_TASK);
      task.setOwner(ADMIN);
      String key = save(task);

      ScheduleTask loaded = load(key);
      assertEquals(name, loaded.getName());
      assertEquals(task.getTaskId(), loaded.getTaskId());
      assertTrue(listedIds().contains(task.getTaskId()));
   }

   // the current version writes the owner key and the bare name
   @Test
   void currentXml_colonName_isNotRewritten() throws Exception {
      ScheduleTask task = parse("<Task name=\"admin:report\" owner=\"" + ADMIN.convertToKey() +
                                "\" enabled=\"true\"/>");
      assertEquals("admin:report", task.getName());
      assertEquals(ADMIN.convertToKey() + ":admin:report", task.getTaskId());
   }

   // Bug #73029, a task xml of an old version has a user name owner and a "user:task" name
   @Test
   void legacyXml_userPrefixedName_isRewritten() throws Exception {
      String org = OrganizationManager.getInstance().getCurrentOrgID();
      String owner = new IdentityID("admin", org).convertToKey();

      ScheduleTask task = parse("<Task name=\"admin:legacy\" owner=\"admin\" enabled=\"true\"/>");
      assertEquals(owner + ":legacy", task.getTaskId());

      task = parse("<Task name=\"admin:legacy:x\" owner=\"admin\" enabled=\"true\"/>");
      assertEquals(owner + ":legacy:x", task.getTaskId());
   }

   @Test
   void taskMetaData_keepsColonInName() {
      ScheduleTaskMetaData data = ScheduleManager.getTaskMetaData(ADMIN.convertToKey() + ":a:b:c");
      assertEquals("a:b:c", data.getTaskName());
      assertEquals(ADMIN.convertToKey(), data.getTaskOwnerId());
      assertEquals(ADMIN.convertToKey() + ":a:b:c", data.getTaskId());

      data = ScheduleManager.getTaskMetaData("plain");
      assertEquals("plain", data.getTaskName());
      assertNull(data.getTaskOwnerId());
   }

   @Test
   void taskIdSplitters_keepColonInName() {
      String id = new IdentityID("bob", HOST).convertToKey() + ":a: b";
      assertEquals("bob:a: b", SUtil.getTaskName(id));
      assertEquals("a: b", SUtil.getTaskNameWithoutUser(id));
      assertEquals("bob", SUtil.getTaskUser(id));
      assertEquals(new IdentityID("rob", HOST).convertToKey() + ":a: b",
                   MigrateUtil.getNewUserTaskName(id, "bob", "rob"));

      AssetEntry entry = new AssetEntry(AssetRepository.GLOBAL_SCOPE,
                                        AssetEntry.Type.SCHEDULE_TASK, "/" + id, null, HOST);
      AssetEntry renamed = entry.cloneAssetEntry(new IdentityID("bob", HOST),
                                                 new IdentityID("rob", HOST));
      assertEquals("/" + new IdentityID("rob", HOST).convertToKey() + ":a: b", renamed.getPath());
   }

   // a task stored under a key its parsed id doesn't match (e.g. a name rewritten on load) is
   // removed by the id it's stored under
   @Test
   void orphanStoredUnderOtherId_isRemoved() throws Exception {
      String storedId = ADMIN.convertToKey() + ":orphan:x";
      String key = key(storedId);
      keys.add(key);
      ScheduleTask task = new ScheduleTask("orphanOther");
      task.setOwner(ADMIN);
      scheduleManager.getOrgTaskMap(HOST).put(key, task, HOST);

      ScheduleTask loaded = scheduleManager.getScheduleTask(storedId, HOST);
      assertNotNull(loaded);
      assertNotEquals(storedId, loaded.getTaskId());

      scheduleManager.removeScheduleTask(storedId, null);
      assertFalse(IndexedStorage.getIndexedStorage().contains(key, HOST), "orphan removed");
   }

   private String save(ScheduleTask task) throws Exception {
      String key = key(task.getTaskId());
      keys.add(key);
      scheduleManager.setScheduleTask(task.getTaskId(), task, root(), HOST, null);
      assertTrue(IndexedStorage.getIndexedStorage().contains(key, HOST), key);
      return key;
   }

   private ScheduleTask load(String key) {
      ScheduleTaskMap map = scheduleManager.getOrgTaskMap(HOST);
      map.clearCache();
      ScheduleTask task = map.get(key);
      assertNotNull(task, key);
      return task;
   }

   private Set<String> listedIds() throws Exception {
      AssetFolder folder = (AssetFolder) IndexedStorage.getIndexedStorage()
         .getXMLSerializable(root().toIdentifier(), null, HOST);
      assertNotNull(folder);
      Set<String> ids = new HashSet<>();

      for(ScheduleTask task :
         scheduleManager.getScheduleTasks(folder.getEntries(), false, false, HOST))
      {
         ids.add(task.getTaskId());
      }

      return ids;
   }

   private static ScheduleTask parse(String xml) throws Exception {
      Document doc = Tool.parseXML(new StringReader(xml));
      ScheduleTask task = new ScheduleTask();
      task.parseXML(doc.getDocumentElement());
      return task;
   }

   private static AssetEntry root() {
      return new AssetEntry(AssetRepository.GLOBAL_SCOPE, AssetEntry.Type.SCHEDULE_TASK_FOLDER,
                            "/", null, HOST);
   }

   private static String key(String taskId) {
      return new AssetEntry(AssetRepository.GLOBAL_SCOPE, AssetEntry.Type.SCHEDULE_TASK,
                            "/" + taskId, SUtil.getTaskOwner(taskId), HOST).toIdentifier();
   }
}
