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

import inetsoft.sree.SreeEnv;
import inetsoft.sree.internal.DataCycleManager;
import inetsoft.sree.security.*;
import inetsoft.test.*;
import inetsoft.uql.asset.AssetEntry;
import inetsoft.uql.asset.AssetObject;
import inetsoft.uql.viewsheet.VSBookmark;
import inetsoft.uql.viewsheet.VSBookmarkInfo;
import inetsoft.util.Tool;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.io.IOException;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 *  For some rename or remove functions that are not or only used in ReplitEngine, there is no need to add unit cases, such as:
 *  repletRemoved, assetRemoved,  assetRenamed, only RepletEngine used, ignore
 *  archiveRenamed: useless, ignore
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, IntegrationTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@SreeHome
@Tag("core")
public class ScheduleManagerTest {
   @Autowired
   ScheduleManager scheduleManager;

   private IdentityID identityID_admin;
   private IdentityID identityID_tuser0;
   private SRPrincipal admin;
   private SRPrincipal tuser0;

   @BeforeEach
   void before() {
      identityID_admin = new IdentityID("admin", "host-org");
      identityID_tuser0 = new IdentityID("tuser0", "host-org");
      admin = new SRPrincipal(new IdentityID("admin", Organization.getDefaultOrganizationID()),
                              new IdentityID[] { new IdentityID("Administrator", null)},
                              new String[] {"g0"}, "host-org",
                              Tool.getSecureRandom().nextLong());
      admin.setIgnoreLogin(true);
      tuser0 = new SRPrincipal(new IdentityID("tuser0", Organization.getDefaultOrganizationID()),
                               new IdentityID[]{ new IdentityID("Everyone", null) },
                               new String[]{ "g0" }, "host-org",
                               Tool.getSecureRandom().nextLong());
      tuser0.setIgnoreLogin(true);
   }

   @AfterEach
   void clearEnv() {
      clearAllTask("host-org");
   }

   @Test
   void testStaticFunctions() {
      // check isInternalTask
      assertFalse(ScheduleManager.isInternalTask("tk1"));
      assertTrue(ScheduleManager.isInternalTask("__balance tasks__"));
      assertTrue(ScheduleManager.isInternalTask("__asset file backup__"));
      assertTrue(ScheduleManager.isInternalTask("__update assets dependencies__"));

      //check getTaskId
      assertEquals("admin~;~org1:tk1", ScheduleManager.getTaskId("admin", "tk1", "org1"));
      assertEquals("admin~;~host-org:tk2", ScheduleManager.getTaskId("admin", "tk2", null));
      assertEquals("test~;~user:tk3", ScheduleManager.getTaskId("test~;~user", "test~;~user:tk3", null));

      //check getOwner
      assertEquals("admin~;~org1", ScheduleManager.getOwner("admin~;~org1:tk1").convertToKey());

      assertArrayEquals(
         new String[]{"__asset file backup__"},
         ScheduleManager.getWriteableInternalTaskNames().toArray(new String[0])
      );

      SreeEnv.setProperty("security.enabled", "true");
      assertFalse(ScheduleManager.isShareInGroup());
      assertFalse( ScheduleManager.isDeleteByOwner());

      ScheduleTask tk3 = new ScheduleTask("tk3");
      tk3.setOwner(new IdentityID("test1", "host-org"));

      assertFalse(ScheduleManager.hasShareGroupPermission(tk3, admin));

      assertTrue(
         ScheduleManager.hasTaskPermission(
            new IdentityID("test", "host-org"), admin, ResourceAction.WRITE));

      assertFalse(
         ScheduleManager.hasTaskPermission(
            new IdentityID("test", "host-org"), tuser0, ResourceAction.DELETE));

      SreeEnv.setProperty("schedule.options.shareTaskInGroup", "true");
      SreeEnv.setProperty("schedule.options.deleteTaskOnlyByOwner", "true");

      assertFalse(
         ScheduleManager.hasTaskPermission(
            new IdentityID("test", "host-org"), tuser0, ResourceAction.DELETE));

      assertFalse(ScheduleManager.isSameGroup(new IdentityID("test", "host-org"),
                                              new IdentityID("test1", "host-org")));
   }

   /**
    * check save and remove  task
    */
   @Test
   void testSaveTask() throws Exception {
      ScheduleTask tk1 = new ScheduleTask("tk1");
      tk1.setOwner(identityID_tuser0);
      ScheduleTask mvtk2  = new ScheduleTask("mvtk2");
      mvtk2.setOwner(identityID_tuser0);
      mvtk2.setRemovable(false);

      Collection<ScheduleTask> tasks = Arrays.asList(tk1, mvtk2);
      assertFalse(scheduleManager.getScheduleTasks().contains(tk1));

      scheduleManager.save(tasks, "host-org" );
      assertTrue(scheduleManager.getScheduleTasks().contains(tk1));
      assertTrue(scheduleManager.getScheduleTasks().contains(mvtk2));

      //check didn't remove task without D permission
      Throwable exception = assertThrows(
         IOException.class,
         () ->  scheduleManager.removeScheduleTask("tuser0~;~host-org:tk1", tuser0)
      );
      assertTrue(exception.getMessage().contains("doesn't have delete permission for"));

      // check normal remove task
      scheduleManager.removeScheduleTask("tuser0~;~host-org:tk1", admin);

      // check didn't remove task, such as mv task
      assertFalse(mvtk2.isRemovable());
      exception = assertThrows(
         IOException.class,
         () -> scheduleManager.removeScheduleTask("tuser0~;~host-org:mvtk2", admin)
      );
      assertTrue(exception.getMessage().contains("Task is not removable:"));
   }

   /**
    * check save and remove external task
    */
   @Test
   void testSaveTaskWithExtTask() throws Exception {
      //mock CycleInfo
      DataCycleManager.CycleInfo mockCycleInfo = mock(DataCycleManager.CycleInfo.class);
      when(mockCycleInfo.getOrgId()).thenReturn("host-org");
      when(mockCycleInfo.getName()).thenReturn("cycle1");

      ScheduleTask tk1 = new ScheduleTask("tk1");
      tk1.setOwner(identityID_tuser0);
      tk1.setCycleInfo(mockCycleInfo);

      // mock ScheduleExt
      ScheduleExt mockScheduleExt = mock(ScheduleExt.class);
      when(mockScheduleExt.containsTask("tuser0~;~host-org:tk1", "host-org")).thenReturn(true);
      when(mockScheduleExt.getTasks()).thenReturn(List.of(tk1));
      when(mockScheduleExt.getTasks("host-org")).thenReturn(List.of(tk1));
      when(mockScheduleExt.isEnable("tuser0~;~host-org:tk1", "host-org")).thenReturn(false);
      when(mockScheduleExt.deleteTask("tuser0~;~host-org:tk1")).thenReturn(true);

      scheduleManager.addScheduleExt(mockScheduleExt);
      scheduleManager.save(List.of(tk1), "host-org");

     // assertFalse(scheduleManager.getAllScheduleTasks().contains(tk1));  //check ext task in all tasks
      assertTrue(scheduleManager.getScheduleTasks().contains(tk1));  //check 1 ext task
      assertTrue(scheduleManager.getScheduleTasks("host-org").contains(tk1));  //check task in org

      assertTrue(scheduleManager.getExtensionTasks().contains(tk1));
      assertEquals(1, scheduleManager.getExtensions().size());
   }

   /**
    * check other actions
    * getScheduleTasks, getScheduleActivities,getScheduleTasks
    */
   @Test
   void testSetScheduleTask() throws Exception {
      ScheduleTask tk1 = new ScheduleTask("tk1");
      tk1.setOwner(identityID_tuser0);
      ScheduleTask tk2 = new ScheduleTask("tk2");

      //check tuser0 no permission to set task
      Throwable exception = assertThrows(
         IOException.class,
         () -> scheduleManager.setScheduleTask("tk1", tk1, tuser0)
      );
      assertTrue(exception.getMessage().contains("User 'tuser0' doesn't have schedule permission"));

      // set task with tuser0 owner
      scheduleManager.setScheduleTask("tk1", tk1, admin);
      assertTrue(scheduleManager.getScheduleTasks().contains(tk1));

      // set task no owner, use admin as owner
      scheduleManager.setScheduleTask("tk2", tk2, admin);
      assertEquals("admin~;~host-org", scheduleManager.getScheduleTask("admin~;~host-org:tk2").getOwner().convertToKey());

      //check get ScheduleTasks by taskList
      Vector<ScheduleTask> taskList= scheduleManager.getScheduleTasks(admin, List.of(tk1, tk2), "host-org");
      assertEquals(2, taskList.size());
      assertEquals("admin~;~host-org", taskList.getFirst().getOwner().convertToKey());
      assertEquals("tuser0~;~host-org", taskList.getLast().getOwner().convertToKey());
   }

   /**
    * check some other get and set functions.
    */
   @Test
   void testDependentTasks() {
      assertEquals("__balance tasks__", scheduleManager.getBalanceTask().getName());
      assertEquals("__asset file backup__", scheduleManager.getAssetBackupTask().getName());
      assertFalse(scheduleManager.isArchiveTaskEnabled());
      assertEquals(0, scheduleManager.getScheduleActivities().size());

      List<AssetObject>  assetObjects = scheduleManager.getDependentTasks("1^5^__NULL__^admin:tk1", "host-org");
      assertEquals(0, assetObjects.size());
   }

   @Test
   void testViewSheetRenamed() throws Exception {
      ScheduleTask vstk1 = createScheduleTask("vstk1");

      scheduleManager.setScheduleTask("admin~;~host-org:vstk1", vstk1, admin);

      // check viewsheetRenamed, change to new vs name:vs1_1
      scheduleManager.viewSheetRenamed("1^128^__NULL__^f1/vs1^host-org",
                                       "1^128^__NULL__^f1/vs1_1^host-org", "admin", "host-org");
      ViewsheetAction vsaction =  (ViewsheetAction)scheduleManager.getScheduleTask("admin~;~host-org:vstk1").getAction(0);
      assertEquals("1^128^__NULL__^f1/vs1_1^host-org", vsaction.getViewsheetName());

      //check bookmarkRenamed, change to new bookmark name:abk1_1
      scheduleManager.bookmarkRenamed("abk1", "abk1_1", "1^128^__NULL__^f1/vs1_1^host-org", identityID_admin);
      vsaction =  (ViewsheetAction)scheduleManager.getScheduleTask("admin~;~host-org:vstk1").getAction(0);

      assertTrue(Arrays.toString(vsaction.getBookmarks()).contains("abk1_1"));

      // check vs folderRenamed, change to new folder name: f1_1
      scheduleManager.folderRenamed("f1", "f1_1", null, "host-org");
      vsaction =  (ViewsheetAction)scheduleManager.getScheduleTask("admin~;~host-org:vstk1").getAction(0);
      assertEquals("1^128^__NULL__^f1_1/vs1_1^host-org", vsaction.getViewsheetName());

      // check viewsheetRemoved,  action be remove
      AssetEntry vs1Entry = AssetEntry.createAssetEntry("1^128^__NULL__^f1_1/vs1_1^host-org");
      scheduleManager.viewsheetRemoved(vs1Entry, "host-org");
      ScheduleTask task1 =  scheduleManager.getScheduleTask("admin~;~host-org:vstk1");
      assertEquals(0, task1.getActionCount());
   }

   /**
    * check rename vs, folder and ws assetEntry, task info change rightly
    */
   @Test
   void checkRenameSheetInSchedule() throws Exception {
      ScheduleTask vstk1 = createScheduleTask("vstk1");

      scheduleManager.setScheduleTask("admin~;~host-org:vstk1", vstk1, admin);

      //check assetentry is vs
      AssetEntry oentry = AssetEntry.createAssetEntry("1^128^__NULL__^f1/vs1^host-org");
      AssetEntry nentry = AssetEntry.createAssetEntry("1^128^__NULL__^f1/vs1_1^host-org");
      scheduleManager.renameSheetInSchedule(oentry, nentry);
      ViewsheetAction vsaction =  (ViewsheetAction)scheduleManager.getScheduleTask("admin~;~host-org:vstk1").getAction(0);
      assertEquals("1^128^__NULL__^f1/vs1_1^host-org", vsaction.getViewsheetName());

      //check assetEntry is folder
      AssetEntry folderOEntry = AssetEntry.createAssetEntry("0^65605^__NULL__^f1");
      AssetEntry folderNEntry = AssetEntry.createAssetEntry("0^65605^__NULL__^f1_1");
      scheduleManager.renameSheetInSchedule(folderOEntry, folderNEntry);
      vsaction =  (ViewsheetAction)scheduleManager.getScheduleTask("admin~;~host-org:vstk1").getAction(0);
      assertEquals("1^128^__NULL__^f1_1/vs1_1^host-org", vsaction.getViewsheetName());

      //check batch action
      BatchAction batchAction = spy(BatchAction.class);
      AssetEntry wsOEntry = AssetEntry.createAssetEntry("1^2^__NULL__^ws1^host-org");
      AssetEntry wsNEntry = AssetEntry.createAssetEntry("1^2^__NULL__^ws1_1^host-org");
      batchAction.setQueryEntry(wsOEntry);

      vstk1.addAction(batchAction);
      vstk1.setAction(1, batchAction);
      scheduleManager.setScheduleTask("admin~;~host-org:vstk1", vstk1, admin);
      scheduleManager.renameSheetInSchedule(wsOEntry, wsNEntry);
      BatchAction batchAction1 =  (BatchAction)scheduleManager.getScheduleTask("admin~;~host-org:vstk1").getAction(1);
      assertEquals("ws1_1", batchAction1.getQueryEntry().toString());
   }

   /**
    * check rename user, task info change rightly
    */
   @Test
   void checkIdentityRenamed() throws Exception {
      ViewsheetAction spyVSAction = spy(ViewsheetAction.class);
      spyVSAction.setViewsheet("1^128^__NULL__^f3/vs2^host-org");

      CompletionCondition condition = spy(CompletionCondition.class);
      condition.setTaskName("tuser0~;~host-org:user_tk2");

      ScheduleTask user_tk1 = new ScheduleTask("user_tk1");  //user_tk1
      user_tk1.setOwner(new IdentityID("tuser0", "host-org"));
      user_tk1.addAction(spyVSAction );
      user_tk1.setAction(0, spyVSAction);
      user_tk1.addCondition(condition);
      user_tk1.setCondition(0, condition);
      user_tk1.setIdentity(new User(identityID_tuser0));

      scheduleManager.setScheduleTask("tuser0~;~host-org:user_tk1", user_tk1, admin);
      // check rename user.
      User tuser0_1 = new User(new IdentityID("tuser0_1", "host-org"));
      scheduleManager.identityRenamed(identityID_tuser0, tuser0_1);
      assertEquals("tuser0_1~;~host-org", scheduleManager.getScheduleTask("tuser0_1~;~host-org:user_tk1").getOwner().convertToKey());

      // check composition condition task user name changed.
      CompletionCondition completionCondition =
         (CompletionCondition) scheduleManager.getScheduleTask("tuser0_1~;~host-org:user_tk1").getCondition(0);
      assertEquals("tuser0_1~;~host-org:user_tk2",  completionCondition.getTaskName());
   }

   /**
    * Regression test for Bug #74651: identityRenamed() must not throw
    * StringIndexOutOfBoundsException when a CompletionCondition or dependency
    * references a system/internal task name that has no owner prefix (no colon).
    */
   @Test
   void checkIdentityRenamedWithSystemTaskCondition() throws Exception {
      CompletionCondition systemCondition = spy(CompletionCondition.class);
      systemCondition.setTaskName("__balance tasks__");

      ScheduleTask userTask = new ScheduleTask("user_tk_sys");
      userTask.setOwner(new IdentityID("tuser0", "host-org"));
      userTask.addCondition(systemCondition);
      userTask.setCondition(0, systemCondition);
      userTask.setIdentity(new User(identityID_tuser0));

      scheduleManager.setScheduleTask("tuser0~;~host-org:user_tk_sys", userTask, admin);

      User tuser0_1 = new User(new IdentityID("tuser0_1", "host-org"));
      assertDoesNotThrow(() -> scheduleManager.identityRenamed(identityID_tuser0, tuser0_1));

      CompletionCondition cond = (CompletionCondition)
         scheduleManager.getScheduleTask("tuser0_1~;~host-org:user_tk_sys").getCondition(0);
      assertEquals("__balance tasks__", cond.getTaskName());
   }

   private ScheduleTask createScheduleTask(String taskName) {
      ViewsheetAction spyVSAction = spy(ViewsheetAction.class);
      spyVSAction.setViewsheet("1^128^__NULL__^f1/vs1^host-org");
      spyVSAction.setBookmarkTypes(
         new int[] {VSBookmarkInfo.ALLSHARE, VSBookmarkInfo.ALLSHARE });
      spyVSAction.setBookmarks(
         new String[] { VSBookmark.HOME_BOOKMARK, "abk1"});
      spyVSAction.setBookmarkUsers(new IdentityID[] { identityID_admin, identityID_admin});

      TimeCondition condition = TimeCondition.at(10,35,59);

      ScheduleTask vstk1 = new ScheduleTask(taskName);  //vstk1
      vstk1.setOwner(new IdentityID("admin", "host-org"));
      vstk1.addAction(spyVSAction );
      vstk1.setAction(0, spyVSAction);
      vstk1.addCondition(condition);
      vstk1.setCondition(0, condition);

      return vstk1;
   }

   /**
    * clear all tasks in the org, except internal tasks
    */
   private void clearAllTask(String orgId) {
      scheduleManager = ScheduleManager.getScheduleManager();
      String[] internalTaskNames = new String[] {
         "__balance tasks__",
         "__asset file backup__",
         "__update assets dependencies__"
      };
      scheduleManager.getScheduleTasks().forEach(
         it -> {
            try {
               if(!Arrays.asList(internalTaskNames).contains(it.getName())) {
                  scheduleManager.removeScheduleTask(it.getName(), admin, false);
               }
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
         }
      );
      scheduleManager.removeTaskCacheOfOrg(orgId);
   }
}

