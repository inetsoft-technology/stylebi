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
import inetsoft.uql.asset.AssetEntry;
import inetsoft.uql.asset.AssetRepository;
import inetsoft.uql.asset.internal.AssetFolder;
import inetsoft.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #77379: the folder entry a schedule task is saved in must be written in the organization
 * the task is stored in, not in the organization of the folder entry, which may be sent by the
 * client or be created in another organization (e.g. by a thread without a context principal).
 * A folder entry of the same organization in another case is kept as it is.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, IntegrationTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class ScheduleManagerParentOrgTest {
   private static final String HOST = Organization.getDefaultOrganizationID();
   private static final String ORG_A = "pporga";
   private static final String ORG_B = "pporgb";
   private static final String FOLDER = "PpMine";

   @Autowired
   ScheduleManager scheduleManager;

   private final Set<String> orgs = new HashSet<>();

   @AfterEach
   void tearDown() throws Exception {
      ThreadContext.setContextPrincipal(null);
      ThreadContext.setPrincipal(null);
      IndexedStorage storage = IndexedStorage.getIndexedStorage();

      for(String org : orgs) {
         @SuppressWarnings("unchecked")
         Map<String, ScheduleTask> map =
            (Map<String, ScheduleTask>) (Object) scheduleManager.getOrgTaskMap(org);
         map.clear();
         String folder = folder(org).toIdentifier();

         if(storage.contains(folder, org)) {
            storage.remove(folder);
         }
      }

      orgs.clear();
   }

   // the portal move-items / new task case, a folder entry naming another organization
   @Test
   void parentOfOtherOrg_isWrittenInStoredOrg() throws Exception {
      ScheduleTask task = newTask("ppMoved", new IdentityID("alice", ORG_A));
      String key = key(task.getTaskId(), ORG_A);

      scheduleManager.setScheduleTask(task.getTaskId(), task, folder(ORG_B), ORG_A, null);

      assertTrue(contains(ORG_A, key), "folder of the stored organization");
      assertFalse(contains(ORG_B, key), "folder of the other organization");
      assertEquals(FOLDER, load(ORG_A, key).getPath());
   }

   // the task balancer case, a folder entry created by a thread without a context principal (in
   // the host organization) for a task stored in another organization
   @Test
   void parentOfThreadOrg_withoutContextPrincipal_isWrittenInStoredOrg() throws Exception {
      ThreadContext.setContextPrincipal(null);
      ScheduleTask task = newTask("ppBalanced", new IdentityID("alice", ORG_A));
      String key = key(task.getTaskId(), ORG_A);
      AssetEntry parent = new AssetEntry(AssetRepository.GLOBAL_SCOPE,
                                         AssetEntry.Type.SCHEDULE_TASK_FOLDER, FOLDER, null);
      assertEquals(HOST, parent.getOrgID());

      scheduleManager.setScheduleTask(task.getTaskId(), task, parent, ORG_A, null);

      assertTrue(contains(ORG_A, key));
      assertFalse(contains(HOST, key));
   }

   // a site admin working in another organization, the task is stored in that organization
   @Test
   void siteAdminInOtherOrg_parentIsWrittenInThatOrg() throws Exception {
      SRPrincipal admin = new SRPrincipal(new IdentityID("admin", HOST), new IdentityID[0],
                                          new String[0], ORG_A,
                                          Tool.getSecureRandom().nextLong());
      admin.setIgnoreLogin(true);
      ScheduleTask task = newTask("ppAdmin", new IdentityID("alice", ORG_A));
      String key = key(task.getTaskId(), ORG_A);
      AssetEntry parent = folder(HOST);

      scheduleManager.setScheduleTask(task.getTaskId(), task, parent, admin);

      assertTrue(contains(ORG_A, key));
      assertFalse(contains(HOST, key));
   }

   // a mixed case organization id, the lower case folder entry of the same organization is used
   // as it is (the organization id of a default entry is lower case)
   @Test
   void mixedCaseOrg_parentOfSameOrgIsKept() throws Exception {
      String mixed = "PpOrgC";
      String lower = mixed.toLowerCase();
      orgs.add(lower);
      ScheduleTask task = newTask("ppMixed", new IdentityID("carol", mixed));
      String key = key(task.getTaskId(), mixed);

      scheduleManager.setScheduleTask(task.getTaskId(), task, folder(lower), mixed, null);

      AssetFolder folder = (AssetFolder) IndexedStorage.getIndexedStorage()
         .getXMLSerializable(folder(lower).toIdentifier(), null, lower);
      assertNotNull(folder);
      assertTrue(Arrays.stream(folder.getEntries())
                    .anyMatch(e -> e.toIdentifier().equals(key)), Arrays.toString(folder.getEntries()));
      assertEquals(FOLDER, task.getPath());
   }

   private ScheduleTask newTask(String name, IdentityID owner) {
      ScheduleTask task = new ScheduleTask(name);
      task.setOwner(owner);
      orgs.add(owner.orgID);
      orgs.add(ORG_A);
      orgs.add(ORG_B);
      orgs.add(HOST);
      return task;
   }

   private static AssetEntry folder(String orgID) {
      return new AssetEntry(AssetRepository.GLOBAL_SCOPE, AssetEntry.Type.SCHEDULE_TASK_FOLDER,
                            FOLDER, null, orgID);
   }

   private static String key(String taskId, String orgID) {
      return new AssetEntry(AssetRepository.GLOBAL_SCOPE, AssetEntry.Type.SCHEDULE_TASK,
                            "/" + taskId, SUtil.getTaskOwner(taskId), orgID).toIdentifier();
   }

   private static boolean contains(String orgID, String key) throws Exception {
      AssetFolder folder = (AssetFolder) IndexedStorage.getIndexedStorage()
         .getXMLSerializable(folder(orgID).toIdentifier(), null, orgID);
      return folder != null && folder.containsEntry(AssetEntry.createAssetEntry(key, orgID));
   }

   private ScheduleTask load(String orgID, String key) {
      ScheduleTaskMap map = scheduleManager.getOrgTaskMap(orgID);
      map.clearCache();
      ScheduleTask task = map.get(key);
      assertNotNull(task, key);
      return task;
   }
}
