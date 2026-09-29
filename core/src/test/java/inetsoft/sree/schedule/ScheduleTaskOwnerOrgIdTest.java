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

import inetsoft.sree.internal.SUtil;
import inetsoft.sree.security.*;
import inetsoft.test.*;
import inetsoft.uql.XPrincipal;
import inetsoft.uql.asset.AssetEntry;
import inetsoft.uql.asset.AssetRepository;
import inetsoft.util.ThreadContext;
import inetsoft.util.Tool;
import inetsoft.util.migrate.MigrateScheduleTask;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #77359: a task id embeds the owner and it's the scheduler (quartz) job key of the task in
 * all organizations. A stored task's owner must be in the organization it's stored in, otherwise
 * the tasks of two organizations get the same id and running, stopping or saving one affects the
 * other.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, IntegrationTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class ScheduleTaskOwnerOrgIdTest {
   private static final String HOST = Organization.getDefaultOrganizationID();
   private static final String ORG_A = "ooorga";
   private static final String ORG_B = "ooorgb";
   private static final IdentityID ALICE = new IdentityID("alice", ORG_A);
   private static final IdentityID BOB = new IdentityID("bob", ORG_B);

   @Autowired
   ScheduleManager scheduleManager;

   private final List<String> hostKeys = new ArrayList<>();

   @AfterEach
   void tearDown() {
      ThreadContext.setContextPrincipal(null);
      ThreadContext.setPrincipal(null);

      for(String key : hostKeys) {
         scheduleManager.getOrgTaskMap(HOST).remove(key);
      }

      hostKeys.clear();

      for(String org : new String[] { ORG_A, ORG_B }) {
         @SuppressWarnings("unchecked")
         Map<String, ScheduleTask> map =
            (Map<String, ScheduleTask>) (Object) scheduleManager.getOrgTaskMap(org);
         map.clear();
      }
   }

   // ---- parseXML ----

   @Test
   void parseXML_nullOwnerWithOrg_isSystemOfThatOrg() throws Exception {
      ScheduleTask task = parse("<Task name=\"ooNightly\" owner=\"null~;~" + ORG_B + "\"/>", false);

      assertEquals(new IdentityID(XPrincipal.SYSTEM, ORG_B), task.getOwner());
      assertEquals(system(ORG_B) + ":ooNightly", task.getTaskId());
   }

   @Test
   void parseXML_siteAdminImportOfNullOwner_isSystemOfTargetOrg() throws Exception {
      setContextOrg(BOB, ORG_B);
      ScheduleTask nullOwner = parse("<Task name=\"ooNightly\" owner=\"null\"/>", true);
      ScheduleTask noOwner = parse("<Task name=\"ooNightly\"/>", true);

      assertEquals(system(ORG_B) + ":ooNightly", nullOwner.getTaskId());
      assertEquals(system(ORG_B) + ":ooNightly", noOwner.getTaskId());
   }

   @Test
   void parseXML_explicitOwnerKeys_areUnchanged() throws Exception {
      assertEquals(system(HOST) + ":ooNightly",
                   parse("<Task name=\"ooNightly\" owner=\"" + system(HOST) + "\"/>", false)
                      .getTaskId());
      assertEquals(ALICE.convertToKey() + ":ooNightly",
                   parse("<Task name=\"ooNightly\" owner=\"" + ALICE.convertToKey() + "\"/>",
                         false).getTaskId());
   }

   // ---- org copy ----

   @Test
   void orgCopyOfLegacyNullOwner_isSystemOfNewOrg() throws Exception {
      Document doc = document("<Task name=\"ooNightly\" owner=\"null\"/>");
      MigrateScheduleTask migrate =
         new MigrateScheduleTask(null, new Organization(HOST), new Organization(ORG_B));
      Method process = MigrateScheduleTask.class.getDeclaredMethod("processAssemblies",
                                                                     Element.class);
      process.setAccessible(true);
      process.invoke(migrate, doc.getDocumentElement());

      ScheduleTask copied = new ScheduleTask();
      copied.parseXML(doc.getDocumentElement());

      assertEquals(system(ORG_B) + ":ooNightly", copied.getTaskId());
   }

   // ---- load from the task map ----

   @Test
   void legacyOwnerWithoutOrg_loadedFromTaskMap_isSystemOfStorageOrg() {
      String keyHost = putLegacyNullOwner("ooNightly", HOST);
      String keyA = putLegacyNullOwner("ooNightly", ORG_A);
      String keyB = putLegacyNullOwner("ooNightly", ORG_B);

      ScheduleTask host = load(HOST, keyHost);
      ScheduleTask a = load(ORG_A, keyA);
      ScheduleTask b = load(ORG_B, keyB);

      assertEquals(system(HOST) + ":ooNightly", host.getTaskId());
      assertEquals(system(ORG_A) + ":ooNightly", a.getTaskId());
      assertEquals(system(ORG_B) + ":ooNightly", b.getTaskId());
   }

   // ---- setScheduleTask guard ----

   @Test
   void setScheduleTask_ownerOfOtherOrg_isRefused() {
      ScheduleTask task = newTask("ooNightly", ALICE);

      IOException ex = assertThrows(IOException.class,
         () -> scheduleManager.setScheduleTask(task.getTaskId(), task, principal(BOB, ORG_B)));

      assertTrue(ex.getMessage().contains(ORG_B), ex.getMessage());
      assertNull(scheduleManager.getScheduleTask(task.getTaskId(), ORG_B));
   }

   @Test
   void setScheduleTask_ownerOfStorageOrg_isSaved() throws Exception {
      ScheduleTask task = newTask("ooNightly", BOB);

      scheduleManager.setScheduleTask(task.getTaskId(), task, principal(BOB, ORG_B));

      assertNotNull(scheduleManager.getScheduleTask(task.getTaskId(), ORG_B));
   }

   @Test
   void setScheduleTask_storedTaskAlreadyOwnedInOtherOrg_canBeModified() throws Exception {
      ScheduleTask stored = newTask("ooWeekly", new IdentityID(XPrincipal.SYSTEM, HOST));
      String key = new AssetEntry(AssetRepository.GLOBAL_SCOPE, AssetEntry.Type.SCHEDULE_TASK,
                                  "/" + stored.getTaskId(),
                                  SUtil.getTaskOwner(stored.getTaskId()), ORG_B).toIdentifier();
      scheduleManager.getOrgTaskMap(ORG_B).put(key, stored, ORG_B);

      ScheduleTask modified = (ScheduleTask) stored.clone();
      modified.setEnabled(false);

      assertDoesNotThrow(() -> scheduleManager.setScheduleTask(
         modified.getTaskId(), modified, principal(BOB, ORG_B)));
   }

   @Test
   void setScheduleTask_ownerlessTask_getsOwnerOfStorageOrg() throws Exception {
      // a principal of org A working in org B
      ScheduleTask task = newTask("ooNightly", null);

      scheduleManager.setScheduleTask("ooNightly", task, principal(ALICE, ORG_B));

      assertEquals(ORG_B, task.getOwner().getOrgID());
      assertNotNull(scheduleManager.getScheduleTask(task.getTaskId(), ORG_B));
   }

   // ---- duplicate ids ----

   @Test
   void logDuplicateTaskIds_findsSameIdInTwoOrgs() throws Exception {
      IdentityID hostSystem = new IdentityID(XPrincipal.SYSTEM, HOST);
      ScheduleTask a = new ScheduleTask("ooShared");
      a.setOwner(hostSystem);
      ScheduleTask b = new ScheduleTask("ooShared");
      b.setOwner(hostSystem);
      b.setDescription("org b copy");
      ScheduleTask other = new ScheduleTask("ooOwn");
      other.setOwner(BOB);
      scheduleManager.save(List.of(a), ORG_A);
      scheduleManager.save(List.of(b, other), ORG_B);

      assertEquals(List.of(a.getTaskId()), List.copyOf(scheduleManager.logDuplicateTaskIds()));
   }

   // ---- helpers ----

   // not removable, like the tasks the server creates, which skips the scheduler permission check
   private static ScheduleTask newTask(String name, IdentityID owner) {
      ScheduleTask task = new ScheduleTask(name);
      task.setOwner(owner);
      task.setRemovable(false);
      return task;
   }

   private static String system(String orgID) {
      return new IdentityID(XPrincipal.SYSTEM, orgID).convertToKey();
   }

   private static SRPrincipal principal(IdentityID user, String orgID) {
      SRPrincipal principal = new SRPrincipal(user, new IdentityID[0], new String[0], orgID,
                                              Tool.getSecureRandom().nextLong());
      principal.setIgnoreLogin(true);
      return principal;
   }

   private static void setContextOrg(IdentityID user, String orgID) {
      ThreadContext.setContextPrincipal(principal(user, orgID));
   }

   private static Document document(String xml) throws Exception {
      return DocumentBuilderFactory.newInstance().newDocumentBuilder()
         .parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
   }

   private static ScheduleTask parse(String xml, boolean siteAdminImport) throws Exception {
      ScheduleTask task = new ScheduleTask();
      task.parseXML(document(xml).getDocumentElement(), siteAdminImport);
      return task;
   }

   // a legacy task written without an owner organization, stored under its owner-less id like a
   // task saved before 13.1
   private String putLegacyNullOwner(String name, String orgID) {
      ScheduleTask task = new ScheduleTask(name);
      task.setOwner(new IdentityID("null", null));
      String key = new AssetEntry(AssetRepository.GLOBAL_SCOPE, AssetEntry.Type.SCHEDULE_TASK,
                                  "/" + name, null, orgID).toIdentifier();
      scheduleManager.getOrgTaskMap(orgID).put(key, task, orgID);

      if(HOST.equals(orgID)) {
         hostKeys.add(key);
      }

      return key;
   }

   private ScheduleTask load(String orgID, String key) {
      ScheduleTaskMap map = scheduleManager.getOrgTaskMap(orgID);
      map.clearCache();
      ScheduleTask task = map.get(key);
      assertNotNull(task, key);
      return task;
   }
}
