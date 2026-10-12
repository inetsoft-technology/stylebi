/*
 * This file is part of StyleBI.
 * Copyright (C) 2025  InetSoft Technology
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

import inetsoft.sree.security.*;
import inetsoft.test.*;
import inetsoft.uql.asset.AssetEntry;
import inetsoft.uql.viewsheet.VSBookmarkInfo;
import inetsoft.util.Tool;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/*
 * Tier: [integration] - real ScheduleManager bean and task storage.
 *
 * Bug #78028: a ViewsheetAction whose viewsheet identifier does not resolve to an entry (an
 * empty value kept by task import, or a legacy identifier without '^') made every rename
 * handler that resolves the action's entry throw: folderRenamed, identityRenamed (through
 * updateViewsheets) and the folder branch of renameSheetInSchedule. The throw skipped the
 * trailing save, so the other tasks were not updated in storage, and a user rename lost the
 * renamed user's tasks, which had already been removed from storage.
 *
 * Every assertion reads storage after removeTaskCacheOfOrg, because the handlers mutate the
 * cached tasks before they save. The task iteration order is the order of the org's root
 * folder, not insertion order, so each case seeds several valid tasks and checks that at least
 * one is visited before and one after an unresolved task.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, IntegrationTestConfiguration.class,
                                  SecurityEngineDispatchConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class ScheduleManagerUnresolvedViewsheetTest {
   private static final String ORG = "host-org";
   private static final String VS = "1^128^__NULL__^f1/vs1^" + ORG;
   private static final String RENAMED_VS = "1^128^__NULL__^f1_1/vs1^" + ORG;

   @Autowired
   ScheduleManager scheduleManager;

   @Autowired
   SecurityEngine securityEngine;

   private SRPrincipal admin;
   private final IdentityID adminId = new IdentityID("admin", ORG);
   private final List<String> seededIds = new ArrayList<>();

   @BeforeEach
   void before() {
      SecurityEngineOverrides.assertInstalled(securityEngine);
      admin = new SRPrincipal(new IdentityID("admin", Organization.getDefaultOrganizationID()),
                              new IdentityID[] { new IdentityID("Administrator", null) },
                              new String[] { "g0" }, ORG, Tool.getSecureRandom().nextLong());
      admin.setIgnoreLogin(true);
   }

   @AfterEach
   void after() throws Exception {
      for(String taskId : seededIds) {
         if(scheduleManager.getScheduleTask(taskId, ORG) != null) {
            scheduleManager.removeScheduleTask(taskId, admin, false);
         }
      }

      seededIds.clear();
      scheduleManager.removeTaskCacheOfOrg(ORG);
   }

   @ParameterizedTest
   @ValueSource(strings = { "", "f1/vs1" })
   void folderRenamed_unresolvedViewsheet_skipsItAndRenamesOthers(String badVS)
      throws Exception
   {
      List<String> good = seedGoodTasks("frgood", adminId, 4);
      List<String> bad = seedTasks("frbad", adminId, 4, badVS);
      // actions are visited from the last index down, so the unresolved one is reached first
      String mixed = seed("frmixed", adminId, VS, badVS);
      scheduleManager.removeTaskCacheOfOrg(ORG);
      assertVisitedOnBothSides(good, bad);

      assertDoesNotThrow(() -> scheduleManager.folderRenamed("f1", "f1_1", null, ORG));

      scheduleManager.removeTaskCacheOfOrg(ORG);

      for(String taskId : good) {
         assertEquals(RENAMED_VS, viewsheet(taskId, 0), taskId);
      }

      for(String taskId : bad) {
         assertEquals(badVS, viewsheet(taskId, 0), taskId);
      }

      assertEquals(RENAMED_VS, viewsheet(mixed, 0));
      assertEquals(badVS, viewsheet(mixed, 1));
   }

   @Test
   void identityRenamed_unresolvedViewsheetOfOtherOwner_keepsAndRenamesUserTasks()
      throws Exception
   {
      IdentityID user = new IdentityID("tuser78028", ORG);
      IdentityID renamed = new IdentityID("tuser78028_1", ORG);
      String privateVS = "4^128^" + user.convertToKey() + "^vs1^" + ORG;

      List<String> good = seedGoodTasks("irgood", user, 4);
      String privateTask = seed("irprivate", user, privateVS);
      List<String> bad = seedTasks("irbad", adminId, 4, "");
      scheduleManager.removeTaskCacheOfOrg(ORG);
      assertVisitedOnBothSides(good, bad);

      assertDoesNotThrow(() -> scheduleManager.identityRenamed(user, new User(renamed)));

      scheduleManager.removeTaskCacheOfOrg(ORG);

      for(String taskId : good) {
         String newId = renamedTaskId(taskId, renamed);
         assertNull(scheduleManager.getScheduleTask(taskId, ORG), taskId);
         ScheduleTask task = scheduleManager.getScheduleTask(newId, ORG);
         assertNotNull(task, newId);
         assertEquals(renamed, task.getOwner());
         assertArrayEquals(new IdentityID[] { renamed },
                           ((ViewsheetAction) task.getAction(0)).getBookmarkUsers());
      }

      assertEquals("4^128^" + renamed.convertToKey() + "^vs1^" + ORG,
                   viewsheet(renamedTaskId(privateTask, renamed), 0));

      for(String taskId : bad) {
         assertEquals("", viewsheet(taskId, 0), taskId);
      }
   }

   @ParameterizedTest
   @ValueSource(strings = { "", "f1/vs1" })
   void identityRenamed_userOwnsUnresolvedViewsheetTask_keepsAndRenamesIt(String badVS)
      throws Exception
   {
      IdentityID user = new IdentityID("tuser78028b", ORG);
      IdentityID renamed = new IdentityID("tuser78028b_1", ORG);
      List<String> good = seedGoodTasks("iogood", user, 2);
      String bad = seed("iobad", user, badVS);

      assertDoesNotThrow(() -> scheduleManager.identityRenamed(user, new User(renamed)));

      scheduleManager.removeTaskCacheOfOrg(ORG);

      for(String taskId : good) {
         assertNotNull(scheduleManager.getScheduleTask(renamedTaskId(taskId, renamed), ORG),
                       taskId);
      }

      String newBad = renamedTaskId(bad, renamed);
      assertNull(scheduleManager.getScheduleTask(bad, ORG));
      ScheduleTask task = scheduleManager.getScheduleTask(newBad, ORG);
      assertNotNull(task, newBad);
      ViewsheetAction action = (ViewsheetAction) task.getAction(0);
      assertEquals(badVS, action.getViewsheet());
      // the bookmark users do not depend on the viewsheet entry and are still renamed
      assertArrayEquals(new IdentityID[] { renamed }, action.getBookmarkUsers());
   }

   @ParameterizedTest
   @ValueSource(strings = { "", "f1/vs1" })
   void renameSheetInSchedule_folderWithUnresolvedViewsheet_skipsItAndRenamesOthers(String badVS)
      throws Exception
   {
      List<String> good = seedGoodTasks("rsgood", adminId, 4);
      List<String> bad = seedTasks("rsbad", adminId, 4, badVS);
      scheduleManager.removeTaskCacheOfOrg(ORG);
      assertVisitedOnBothSides(good, bad);

      AssetEntry folderOEntry = AssetEntry.createAssetEntry("0^65605^__NULL__^f1^" + ORG);
      AssetEntry folderNEntry = AssetEntry.createAssetEntry("0^65605^__NULL__^f1_1^" + ORG);
      assertDoesNotThrow(() -> scheduleManager.renameSheetInSchedule(folderOEntry, folderNEntry));

      scheduleManager.removeTaskCacheOfOrg(ORG);

      for(String taskId : good) {
         assertEquals(RENAMED_VS, viewsheet(taskId, 0), taskId);
      }

      for(String taskId : bad) {
         assertEquals(badVS, viewsheet(taskId, 0), taskId);
      }
   }

   private List<String> seedGoodTasks(String prefix, IdentityID owner, int count)
      throws Exception
   {
      return seedTasks(prefix, owner, count, VS);
   }

   private List<String> seedTasks(String prefix, IdentityID owner, int count, String viewsheet)
      throws Exception
   {
      List<String> ids = new ArrayList<>();

      for(int i = 0; i < count; i++) {
         ids.add(seed(prefix + i, owner, viewsheet));
      }

      return ids;
   }

   private String seed(String name, IdentityID owner, String... viewsheets) throws Exception {
      ScheduleTask task = new ScheduleTask(name);
      task.setOwner(owner);

      for(String viewsheet : viewsheets) {
         ViewsheetAction action = new ViewsheetAction();
         action.setViewsheet(viewsheet);
         action.setBookmarks(new String[] { "bk1" });
         action.setBookmarkTypes(new int[] { VSBookmarkInfo.ALLSHARE });
         action.setBookmarkUsers(new IdentityID[] { owner });
         task.addAction(action);
      }

      task.addCondition(TimeCondition.at(10, 35, 59));
      String taskId = ScheduleManager.getTaskId(owner.getName(), name, ORG);
      scheduleManager.setScheduleTask(taskId, task, admin);
      seededIds.add(taskId);
      return taskId;
   }

   private String renamedTaskId(String taskId, IdentityID renamed) {
      String newId = ScheduleManager.getTaskId(
         renamed.getName(), taskId.substring(taskId.indexOf(':') + 1), ORG);
      seededIds.add(newId);
      return newId;
   }

   private String viewsheet(String taskId, int index) {
      ScheduleTask task = scheduleManager.getScheduleTask(taskId, ORG);
      assertNotNull(task, taskId);
      return ((ViewsheetAction) task.getAction(index)).getViewsheet();
   }

   /**
    * Checks that the handlers visit at least one valid task before an unresolved one and one
    * after an unresolved one, so a throw would both skip the save of the earlier tasks and the
    * update of the later ones.
    */
   private void assertVisitedOnBothSides(List<String> good, List<String> bad) {
      List<String> order = new ArrayList<>();

      for(ScheduleTask task : scheduleManager.getOrgTaskMap(ORG).values()) {
         if(task != null) {
            order.add(task.getTaskId());
         }
      }

      int firstBad = order.stream().filter(bad::contains).mapToInt(order::indexOf).min()
         .orElseThrow();
      int lastBad = order.stream().filter(bad::contains).mapToInt(order::indexOf).max()
         .orElseThrow();
      assertTrue(good.stream().anyMatch(id -> order.indexOf(id) >= 0 &&
                    order.indexOf(id) < lastBad), "no valid task before " + order);
      assertTrue(good.stream().anyMatch(id -> order.indexOf(id) > firstBad),
                 "no valid task after " + order);
   }
}
