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
import inetsoft.uql.asset.AssetRepository;
import inetsoft.uql.util.DefaultIdentity;
import inetsoft.uql.util.Identity;
import inetsoft.uql.viewsheet.VSBookmark;
import inetsoft.uql.viewsheet.VSBookmarkInfo;
import inetsoft.util.Tool;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.w3c.dom.Document;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import javax.xml.parsers.DocumentBuilderFactory;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/*
 * Tier: [integration] — real ScheduleManager bean, SecurityEngine, and in-memory task maps.
 *
 * Intent vs implementation suspects: none confirmed at this time.
 * Regression: checkIdentityRenamedWithSystemTaskCondition (Bug #74651),
 *             assetRenamed_mvActionWithNullEntry_skipsItAndRenamesOthers (Bug #77214).
 *
 * Intentionally out of scope (RepletEngine-only or unused):
 * repletRemoved, assetRemoved, assetRenamed, archiveRenamed.
 */

/*
 * Cases deferred - require broader integration context:
 *
 * [ScheduleManager] initialize() / reloadExtensions() / cluster message broadcast
 *             -> extension reload and cross-node sync; NOT duplicated here
 * [ScheduleManager] identityRemoved() user/group/org teardown
 *             -> needs EditableAuthenticationProvider fixture; NOT yet covered
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, IntegrationTestConfiguration.class,
                                  SecurityEngineDispatchConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@SreeHome
@Tag("core")
public class ScheduleManagerTest {
   @Autowired
   ScheduleManager scheduleManager;

   @Autowired
   SecurityEngine securityEngine;

   @Autowired
   SecurityEngineOverrides securityEngineOverrides;

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
      SreeEnv.setProperty("schedule.options.shareTaskInGroup", "false");
      SreeEnv.setProperty("schedule.options.deleteTaskOnlyByOwner", "false");
      clearAllTask("host-org");
   }

   @Test
   void staticHelpers_internalTaskIdsAndPermissions() {
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

   @Test
   void getTaskMetaData_qualifiedAndBareIds_splitOwnerAndName() {
      ScheduleTaskMetaData qualified =
         ScheduleManager.getTaskMetaData("admin~;~host-org:sales-report");
      assertEquals("sales-report", qualified.getTaskName());
      assertEquals("admin~;~host-org", qualified.getTaskOwnerId());

      ScheduleTaskMetaData bare = ScheduleManager.getTaskMetaData("__balance tasks__");
      assertEquals("__balance tasks__", bare.getTaskName());
      assertNull(bare.getTaskOwnerId());
   }

   @Test
   void isDeleteOnlyByOwner_shareDisabled_returnsTrue() {
      SreeEnv.setProperty("security.enabled", "true");
      SreeEnv.setProperty("schedule.options.shareTaskInGroup", "false");

      ScheduleTask task = new ScheduleTask("owner-only");
      task.setOwner(identityID_tuser0);

      assertTrue(scheduleManager.isDeleteOnlyByOwner(task, tuser0));
   }

   /*
    * The Schedule settings page displays these two options with
    * SreeEnv.getBooleanProperty(property, "true", "CHECKED"), and defaults.properties ships
    * four of the eight schedule.options.* keys as CHECKED, so CHECKED is the established
    * spelling for this family. The enforcement paths here passed no true-value list, which
    * falls back to "true".equals -- a stored CHECKED showed both options ticked in Enterprise
    * Manager while every enforcement path read them as off.
    */
   @Test
   void schedulePermissionOptions_checkedValue_matchesDisplayedState() {
      SreeEnv.setProperty("security.enabled", "true");
      SreeEnv.setProperty("schedule.options.shareTaskInGroup", "CHECKED");
      SreeEnv.setProperty("schedule.options.deleteTaskOnlyByOwner", "CHECKED");

      assertTrue(ScheduleManager.isShareInGroup());
      assertTrue(ScheduleManager.isDeleteByOwner());

      // hasShareGroupPermission() now delegates to isShareInGroup() for the flag. Its outcome
      // is not asserted here: past the flag it defers to isSameGroup(), which resolves groups
      // through the security provider, and this fixture defines no users for tuser0/admin.

      // an unrecognized value stays off
      SreeEnv.setProperty("schedule.options.shareTaskInGroup", "yes");
      assertFalse(ScheduleManager.isShareInGroup());
      SreeEnv.setProperty("schedule.options.deleteTaskOnlyByOwner", "1");
      assertFalse(ScheduleManager.isDeleteByOwner());
   }

   /**
    * check save and remove  task
    */
   @Test
   void saveAndRemoveTask_enforcesDeletePermissionAndRemovableFlag() throws Exception {
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

   @Test
   void removeScheduleTask_dependencyCheck_blocksDeleteWhenReferenced() throws Exception {
      ScheduleTask parent = new ScheduleTask("parent-dep");
      parent.setOwner(identityID_admin);
      parent.addCondition(TimeCondition.at(10, 0, 0));

      ScheduleTask child = new ScheduleTask("child-dep");
      child.setOwner(identityID_admin);
      child.addCondition(new CompletionCondition(parent.getTaskId()));

      scheduleManager.setScheduleTask(parent.getTaskId(), parent, admin);
      scheduleManager.setScheduleTask(child.getTaskId(), child, admin);

      Vector<ScheduleTask> allTasks = new Vector<>(scheduleManager.getScheduleTasks());
      assertTrue(scheduleManager.hasDependency(allTasks, parent.getTaskId()));

      Exception exception = assertThrows(Exception.class,
         () -> scheduleManager.removeScheduleTask(parent.getTaskId(), admin, true));
      assertNotNull(exception.getMessage());
      assertNotNull(scheduleManager.getScheduleTask(parent.getTaskId()),
         "Parent task must still exist after blocked delete");
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

      // only data cycle tasks are extension tasks (Bug #77213)
      ScheduleTask tk1 = new ScheduleTask("tk1", ScheduleTask.Type.CYCLE_TASK);
      tk1.setOwner(identityID_tuser0);
      tk1.setCycleInfo(mockCycleInfo);

      // mock ScheduleExt
      ScheduleExt mockScheduleExt = mock(ScheduleExt.class);
      when(mockScheduleExt.containsTask(tk1.getTaskId(), "host-org")).thenReturn(true);
      when(mockScheduleExt.getTasks()).thenReturn(List.of(tk1));
      when(mockScheduleExt.getTasks("host-org")).thenReturn(List.of(tk1));
      when(mockScheduleExt.isEnable(tk1.getTaskId(), "host-org")).thenReturn(false);
      when(mockScheduleExt.deleteTask(tk1.getTaskId())).thenReturn(true);

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
    * Bug #77214: an MV action parsed from task XML without an MVDef (e.g. an imported or legacy
    * task file) has a null entry. assetRenamed must skip it instead of throwing an NPE, and must
    * still rename and save the other matching actions.
    */
   @Test
   void assetRenamed_mvActionWithNullEntry_skipsItAndRenamesOthers() throws Exception {
      Document doc = DocumentBuilderFactory.newInstance().newDocumentBuilder()
         .parse(new ByteArrayInputStream("<Action type=\"MV\"/>".getBytes(StandardCharsets.UTF_8)));
      MVAction mvAction = new MVAction();
      mvAction.parseXML(doc.getDocumentElement());
      assertNull(mvAction.getEntry());

      AssetEntry wsOEntry = AssetEntry.createAssetEntry("1^2^__NULL__^ws1^host-org");
      AssetEntry wsNEntry = AssetEntry.createAssetEntry("1^2^__NULL__^ws1_1^host-org");
      BatchAction batchAction = new BatchAction();
      batchAction.setQueryEntry(wsOEntry);

      // actions are visited from the last index down, so the MV action is reached first
      ScheduleTask task = createScheduleTask("mvtk1");
      task.addAction(batchAction);
      task.addAction(mvAction);
      String taskId = "admin~;~host-org:mvtk1";
      scheduleManager.setScheduleTask(taskId, task, admin);

      try {
         assertDoesNotThrow(() -> scheduleManager.assetRenamed(wsOEntry, wsNEntry, "host-org"));

         scheduleManager.removeTaskCacheOfOrg("host-org");
         ScheduleTask reloaded = scheduleManager.getScheduleTask(taskId);
         assertEquals(3, reloaded.getActionCount());
         assertEquals(wsNEntry, ((BatchAction) reloaded.getAction(1)).getQueryEntry());
         assertNull(((MVAction) reloaded.getAction(2)).getEntry());
      }
      finally {
         // clearAllTask() was observed not to remove this task; remove it explicitly so its
         // BatchAction (null task id) does not leak into checkIdentityRenamed
         scheduleManager.removeScheduleTask(taskId, admin, false);
      }
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

      // Bug #78129, a task stored before the sheet READ check, its run principal can't read
      // the sheet, the rename is what's tested
      setScheduleTaskUnchecked("tuser0~;~host-org:user_tk1", user_tk1);
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
    * Regression test for Bug #77097: a group rename must not rewrite the task chain, the private
    * viewsheet or the bookmarks of a same-named user, which the task ids and viewsheet paths only
    * identify by name. Only the "execute as" group is renamed.
    */
   @Test
   void checkGroupRenamedLeavesSameNamedUserTask() throws Exception {
      IdentityID userSales = new IdentityID("sales", "host-org");
      String privateVS = "4^128^sales~;~host-org^vs1^host-org";

      ViewsheetAction vsAction = new ViewsheetAction();
      vsAction.setViewsheet(privateVS);
      vsAction.setBookmarks(new String[] { "bk1" });
      vsAction.setBookmarkTypes(new int[] { VSBookmarkInfo.PRIVATE });
      vsAction.setBookmarkUsers(new IdentityID[] { userSales });

      CompletionCondition condition = new CompletionCondition();
      condition.setTaskName("sales~;~host-org:group_tk2");

      ScheduleTask task = new ScheduleTask("group_tk1");
      task.setOwner(userSales);
      task.addAction(vsAction);
      task.addCondition(condition);
      task.setIdentity(new Group(new IdentityID("sales", "host-org")));
      // Bug #78129, a task stored before the sheet READ check, a group can't read the owner's
      // private sheet, the rename is what's tested
      setScheduleTaskUnchecked("sales~;~host-org:group_tk1", task);

      Group sales2 = new Group(new IdentityID("sales2", "host-org"));
      scheduleManager.identityRenamed(new IdentityID("sales", "host-org"), sales2);

      ScheduleTask renamed = scheduleManager.getScheduleTask("sales~;~host-org:group_tk1");
      assertNotNull(renamed, "the user's task must keep its id");
      assertEquals(userSales, renamed.getOwner());
      assertEquals("sales~;~host-org:group_tk2",
                   ((CompletionCondition) renamed.getCondition(0)).getTaskName());

      Enumeration<String> dependencies = renamed.getDependency();

      while(dependencies.hasMoreElements()) {
         assertFalse(dependencies.nextElement().startsWith("sales2~;~"));
      }

      ViewsheetAction action = (ViewsheetAction) renamed.getAction(0);
      assertEquals(privateVS, action.getViewsheet());
      assertArrayEquals(new IdentityID[] { userSales }, action.getBookmarkUsers());
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

   /**
    * Bug #77111: a group removal removes the name(Group) token and keeps a same-named bare token,
    * which denotes a user.
    */
   @Test
   void identityRemoved_groupRemovesOnlyGroupNotifications() throws Exception {
      IdentityID gX = new IdentityID("gX", "host-org");

      withNotificationTasks(() -> {
         ScheduleTask task = seedNotificationTask("n77111_grp", "gX,gX(Group),e@f.com", "host-org");
         scheduleManager.identityRemoved(new Group(gX), mockProvider(new Group(gX)));
         assertEquals("gX,e@f.com", readNotifications(task, "host-org"));
      });
   }

   /**
    * Bug #77111: a user removal removes the bare and the name(User) tokens, and keeps the
    * same-named group.
    */
   @Test
   void identityRemoved_userRemovesBareAndTypedNotifications() throws Exception {
      IdentityID u9 = new IdentityID("u9", "host-org");

      withNotificationTasks(() -> {
         ScheduleTask task =
            seedNotificationTask("n77111_usr", "u9,u9(User),u9(Group),e@f.com", "host-org");
         scheduleManager.identityRemoved(new User(u9), mockProvider(new User(u9)));
         assertEquals("u9(Group),e@f.com", readNotifications(task, "host-org"));
      });
   }

   /**
    * Bug #77111: a group rename renames only the old name(Group) token. The same-named users of
    * the old and the new name keep their tokens.
    */
   @Test
   void identityRenamed_groupRenamesOnlyGroupNotifications() throws Exception {
      withNotificationTasks(() -> {
         ScheduleTask task = seedNotificationTask(
            "n77111_grpren", "g1,g1(Group),sales,sales(User),e@f.com", "host-org");
         scheduleManager.identityRenamed(new IdentityID("g1", "host-org"),
                                         new Group(new IdentityID("sales", "host-org")));
         assertEquals("g1,sales(Group),sales,sales(User),e@f.com",
                      readNotifications(task, "host-org"));
      });
   }

   /**
    * Bug #77111: a user rename renames the bare and the name(User) tokens in the same form, and
    * keeps the same-named group.
    */
   @Test
   void identityRenamed_userRenamesBareAndTypedNotifications() throws Exception {
      withNotificationTasks(() -> {
         ScheduleTask task =
            seedNotificationTask("n77111_usrren", "a,a(User),a(Group),e@f.com", "host-org");
         scheduleManager.identityRenamed(new IdentityID("a", "host-org"),
                                         new User(new IdentityID("b", "host-org")));
         assertEquals("b,b(User),a(Group),e@f.com", readNotifications(task, "host-org"));
      });
   }

   /**
    * Bug #77111: semicolon and comma lists are both matched, and the delimiters and the spacing
    * of the kept tokens are preserved.
    */
   @Test
   void identityRenamedAndRemoved_keepDelimitersAndSpacing() throws Exception {
      IdentityID u9 = new IdentityID("u9", "host-org");

      withNotificationTasks(() -> {
         ScheduleTask removed =
            seedNotificationTask("n77111_semi", "e@f.com; u9(User) ;gX", "host-org");
         ScheduleTask renamed =
            seedNotificationTask("n77111_mixed", "a;x@y.com , a(User)", "host-org");

         scheduleManager.identityRemoved(new User(u9), mockProvider(new User(u9)));
         assertEquals("e@f.com;gX", readNotifications(removed, "host-org"));

         scheduleManager.identityRenamed(new IdentityID("a", "host-org"),
                                         new User(new IdentityID("b", "host-org")));
         assertEquals("b;x@y.com , b(User)", readNotifications(renamed, "host-org"));
      });
   }

   /**
    * Bug #77111: a bare email address is an address, not a user, even if a user has that name.
    */
   @Test
   void identityRemoved_emailTokenIsNotAUser() throws Exception {
      IdentityID alice = new IdentityID("alice@x.com", "host-org");

      withNotificationTasks(() -> {
         ScheduleTask task =
            seedNotificationTask("n77111_email", "alice@x.com,alice@x.com(User)", "host-org");
         scheduleManager.identityRemoved(new User(alice), mockProvider(new User(alice)));
         assertEquals("alice@x.com", readNotifications(task, "host-org"));
      });
   }

   /**
    * Bug #77111: a task whose notifications do not denote the identity is left byte-identical and
    * is not saved.
    */
   @Test
   void identityRemoved_unmatchedNotificationsNotSaved() throws Exception {
      IdentityID u9 = new IdentityID("u9", "host-org");

      withNotificationTasks(() -> {
         ScheduleTask matched = seedNotificationTask("n77111_hit", "u9,e@f.com", "host-org");
         ScheduleTask unmatched =
            seedNotificationTask("n77111_miss", "a@b.com; c@d.com,u9(Group)", "host-org");
         ScheduleManager spyManager = spy(scheduleManager);
         spyManager.identityRemoved(new User(u9), mockProvider(new User(u9)));

         @SuppressWarnings("unchecked")
         ArgumentCaptor<Collection<ScheduleTask>> saved = ArgumentCaptor.forClass(Collection.class);
         verify(spyManager).save(saved.capture(), eq("host-org"));
         Set<String> savedNames = new HashSet<>();
         saved.getValue().forEach(task -> savedNames.add(task.getName()));
         assertEquals(Set.of("n77111_hit"), savedNames);
         assertEquals("e@f.com", readNotifications(matched, "host-org"));
         assertEquals("a@b.com; c@d.com,u9(Group)", readNotifications(unmatched, "host-org"));
      });
   }

   /**
    * Bug #77111: a removal only changes the notifications in the identity's own org.
    */
   @Test
   void identityRemoved_notificationsScopedToOrg() throws Exception {
      IdentityID u9 = new IdentityID("u9", "org1");

      withNotificationTasks(() -> {
         ScheduleTask org1Task = seedNotificationTask("n77111_org1", "u9,e@f.com", "org1");
         ScheduleTask org2Task = seedNotificationTask("n77111_org2", "u9,e@f.com", "org2");
         scheduleManager.identityRemoved(new User(u9), mockProvider(new User(u9)));
         assertEquals("e@f.com", readNotifications(org1Task, "org1"));
         assertEquals("u9,e@f.com", readNotifications(org2Task, "org2"));
      });
   }

   /**
    * Bug #77148: a user removal removes the bare and the name(User) tokens from the to, cc and bcc
    * delivery lists, and keeps the same-named group, the other groups and the email addresses.
    */
   @Test
   void identityRemoved_userRemovesDeliveryRecipients() throws Exception {
      IdentityID bob = new IdentityID("bob", "host-org");

      withNotificationTasks(() -> {
         ScheduleTask task = seedDeliveryTask(
            "n77148_usr", "bob(User),bob,g1(Group),bob(Group),e@f.com", "bob", "bob(User)",
            "host-org");
         scheduleManager.identityRemoved(new User(bob), mockProvider(new User(bob)));
         assertDelivery(task, "host-org", "g1(Group),bob(Group),e@f.com", "", "");
      });
   }

   /**
    * Bug #77148: a group removal removes only the name(Group) tokens from the delivery lists. A
    * same-named bare token and a same-named name(User) token denote a user and are kept.
    */
   @Test
   void identityRemoved_groupRemovesOnlyGroupDeliveryRecipients() throws Exception {
      IdentityID g1 = new IdentityID("g1", "host-org");

      withNotificationTasks(() -> {
         ScheduleTask task = seedDeliveryTask(
            "n77148_grp", "g1,g1(User),g1(Group),e@f.com", "g1(Group)", "g1", "host-org");
         scheduleManager.identityRemoved(new Group(g1), mockProvider(new Group(g1)));
         assertDelivery(task, "host-org", "g1,g1(User),e@f.com", "", "g1");
      });
   }

   /**
    * Bug #77148: a user rename renames the bare and the name(User) tokens of the delivery lists in
    * the same form, and keeps the same-named group, the delimiters and the spacing.
    */
   @Test
   void identityRenamed_userRenamesDeliveryRecipients() throws Exception {
      withNotificationTasks(() -> {
         ScheduleTask task = seedDeliveryTask(
            "n77148_usrren", "alice , alice(User);alice(Group),e@f.com", "alice", "alice(User)",
            "host-org");
         scheduleManager.identityRenamed(new IdentityID("alice", "host-org"),
                                         new User(new IdentityID("alice2", "host-org")));
         assertDelivery(task, "host-org", "alice2 , alice2(User);alice(Group),e@f.com",
                        "alice2", "alice2(User)");
      });
   }

   /**
    * Bug #77148: a group rename renames only the name(Group) tokens of the delivery lists. The
    * same-named user tokens are kept.
    */
   @Test
   void identityRenamed_groupRenamesOnlyGroupDeliveryRecipients() throws Exception {
      withNotificationTasks(() -> {
         ScheduleTask task = seedDeliveryTask(
            "n77148_grpren", "g1,g1(User),g1(Group),e@f.com", "g1(Group)", "g1", "host-org");
         scheduleManager.identityRenamed(new IdentityID("g1", "host-org"),
                                         new Group(new IdentityID("g2", "host-org")));
         assertDelivery(task, "host-org", "g1,g1(User),g2(Group),e@f.com", "g2(Group)", "g1");
      });
   }

   /**
    * Bug #77148: a raw email address equal to a removed user's name and a display-name address
    * are addresses, not the user, and are kept. Only the name(User) token is removed.
    */
   @Test
   void identityRemoved_emailShapedUserKeepsDeliveryAddresses() throws Exception {
      IdentityID bob = new IdentityID("bob@x.com", "host-org");

      withNotificationTasks(() -> {
         ScheduleTask task = seedDeliveryTask(
            "n77148_email", "bob@x.com, Bob <bob@x.com>; bob@x.com(User)", "bob@x.com",
            "Bob <bob@x.com>", "host-org");
         scheduleManager.identityRemoved(new User(bob), mockProvider(new User(bob)));
         assertDelivery(task, "host-org", "bob@x.com, Bob <bob@x.com>", "bob@x.com",
                        "Bob <bob@x.com>");
      });
   }

   /**
    * Bug #77148: a non-ASCII user name is renamed in the delivery lists.
    */
   @Test
   void identityRenamed_nonAsciiUserRenamesDeliveryRecipients() throws Exception {
      String zhang = "\u5f20\u4e09";

      withNotificationTasks(() -> {
         ScheduleTask task = seedDeliveryTask(
            "n77148_nonascii", zhang + "," + zhang + "(User)", zhang, "e@f.com", "host-org");
         scheduleManager.identityRenamed(new IdentityID(zhang, "host-org"),
                                         new User(new IdentityID("zs", "host-org")));
         assertDelivery(task, "host-org", "zs,zs(User)", "zs", "e@f.com");
      });
   }

   /**
    * Bug #77148: a task whose delivery lists do not denote the removed identity is left
    * byte-identical and is not saved.
    */
   @Test
   void identityRemoved_unmatchedDeliveryRecipientsNotSaved() throws Exception {
      IdentityID u9 = new IdentityID("u9", "host-org");

      withNotificationTasks(() -> {
         ScheduleTask matched =
            seedDeliveryTask("n77148_hit", "e@f.com", "u9(User)", "x@y.com", "host-org");
         ScheduleTask unmatched = seedDeliveryTask(
            "n77148_miss", "a@b.com; c@d.com", "u9(Group)", "x@y.com , U9", "host-org");
         ScheduleManager spyManager = spy(scheduleManager);
         spyManager.identityRemoved(new User(u9), mockProvider(new User(u9)));

         @SuppressWarnings("unchecked")
         ArgumentCaptor<Collection<ScheduleTask>> saved = ArgumentCaptor.forClass(Collection.class);
         verify(spyManager).save(saved.capture(), eq("host-org"));
         Set<String> savedNames = new HashSet<>();
         saved.getValue().forEach(task -> savedNames.add(task.getName()));
         assertEquals(Set.of("n77148_hit"), savedNames);
         assertDelivery(matched, "host-org", "e@f.com", "", "x@y.com");
         assertDelivery(unmatched, "host-org", "a@b.com; c@d.com", "u9(Group)", "x@y.com , U9");
      });
   }

   /**
    * Bug #77148: when the removed identity was the only "to" recipient, the "to" list becomes
    * empty, so the email step is skipped at run time (and shown as disabled), while the cc list is
    * kept as is.
    */
   @Test
   void identityRemoved_onlyToRecipientRemovedDisablesEmailStep() throws Exception {
      IdentityID bob = new IdentityID("bob", "host-org");

      withNotificationTasks(() -> {
         ScheduleTask task =
            seedDeliveryTask("n77148_onlyto", "bob", "carol@x.com", null, "host-org");
         scheduleManager.identityRemoved(new User(bob), mockProvider(new User(bob)));

         ViewsheetAction loaded = (ViewsheetAction)
            scheduleManager.getScheduleTask(task.getTaskId(), "host-org").getAction(0);
         assertEquals("", loaded.getEmails());
         assertTrue(loaded.getScheduleEmails(null).isEmpty());
         assertEquals("carol@x.com", loaded.getCCAddresses());
      });
   }

   /**
    * Bug #77148: a task whose only match is in the bcc list is saved, and the removal is in the
    * stored task, not only in the cached in-memory copy.
    */
   @Test
   void identityRemoved_bccOnlyMatchSavedToStorage() throws Exception {
      IdentityID bob = new IdentityID("bob", "host-org");

      withNotificationTasks(() -> {
         ScheduleTask task = seedDeliveryTask(
            "n77148_bcconly", "e@f.com", "x@y.com", "bob,z@w.com", "host-org");
         ScheduleManager spyManager = spy(scheduleManager);
         spyManager.identityRemoved(new User(bob), mockProvider(new User(bob)));

         @SuppressWarnings("unchecked")
         ArgumentCaptor<Collection<ScheduleTask>> saved = ArgumentCaptor.forClass(Collection.class);
         verify(spyManager).save(saved.capture(), eq("host-org"));
         Set<String> savedNames = new HashSet<>();
         saved.getValue().forEach(t -> savedNames.add(t.getName()));
         assertEquals(Set.of("n77148_bcconly"), savedNames);

         AbstractAction stored = loadStoredAction(task, "host-org");
         assertEquals("e@f.com", stored.getEmails(), "to");
         assertEquals("x@y.com", stored.getCCAddresses(), "cc");
         assertEquals("z@w.com", stored.getBCCAddresses(), "bcc");
      });
   }

   /**
    * Bug #77148: a user referenced in both the to and the cc list is renamed in both lists of the
    * stored task.
    */
   @Test
   void identityRenamed_userInToAndCcRenamedInStorage() throws Exception {
      withNotificationTasks(() -> {
         ScheduleTask task = seedDeliveryTask(
            "n77148_toccren", "alice,e@f.com", "alice(User)", null, "host-org");
         scheduleManager.identityRenamed(new IdentityID("alice", "host-org"),
                                         new User(new IdentityID("alice2", "host-org")));

         AbstractAction stored = loadStoredAction(task, "host-org");
         assertEquals("alice2,e@f.com", stored.getEmails(), "to");
         assertEquals("alice2(User)", stored.getCCAddresses(), "cc");
      });
   }

   /**
    * Drops the schedule task cache and parses the first action of the task again from storage.
    */
   private AbstractAction loadStoredAction(ScheduleTask task, String orgID) {
      scheduleManager.getOrgTaskMap(orgID).clearCache();
      ScheduleTask loaded = scheduleManager.getScheduleTask(task.getTaskId(), orgID);
      assertNotNull(loaded);
      assertNotSame(task, loaded);
      return (AbstractAction) loaded.getAction(0);
   }

   private static EditableAuthenticationProvider mockProvider(User user) {
      EditableAuthenticationProvider provider = mock(EditableAuthenticationProvider.class);
      when(provider.getUser(user.getIdentityID())).thenReturn(user);
      return provider;
   }

   private static EditableAuthenticationProvider mockProvider(Group group) {
      EditableAuthenticationProvider provider = mock(EditableAuthenticationProvider.class);
      when(provider.getGroup(group.getIdentityID())).thenReturn(group);
      return provider;
   }

   /**
    * Runs the body and then removes the n77111_ and n77148_ tasks it seeded.
    */
   private void withNotificationTasks(NotificationTestBody body) throws Exception {
      try {
         body.run();
      }
      finally {
         for(String org : new String[] { "host-org", "org1", "org2" }) {
            scheduleManager.getOrgTaskMap(org).values()
               .removeIf(task -> task != null && (task.getName().startsWith("n77111_") ||
                                                  task.getName().startsWith("n77148_")));
         }
      }
   }

   private ScheduleTask seedNotificationTask(String name, String notifications, String orgID)
      throws Exception
   {
      ScheduleTask task = createScheduleTask(name);
      ((AbstractAction) task.getAction(0)).setNotifications(notifications);
      scheduleManager.save(List.of(task), orgID);
      return task;
   }

   private String readNotifications(ScheduleTask task, String orgID) {
      ScheduleTask loaded = scheduleManager.getScheduleTask(task.getTaskId(), orgID);
      return ((AbstractAction) loaded.getAction(0)).getNotifications();
   }

   private ScheduleTask seedDeliveryTask(String name, String emails, String ccAddresses,
                                         String bccAddresses, String orgID)
      throws Exception
   {
      ScheduleTask task = createScheduleTask(name);
      AbstractAction action = (AbstractAction) task.getAction(0);
      action.setEmails(emails);
      action.setCCAddresses(ccAddresses);
      action.setBCCAddresses(bccAddresses);
      scheduleManager.save(List.of(task), orgID);
      return task;
   }

   private void assertDelivery(ScheduleTask task, String orgID, String emails,
                               String ccAddresses, String bccAddresses)
   {
      ScheduleTask loaded = scheduleManager.getScheduleTask(task.getTaskId(), orgID);
      AbstractAction action = (AbstractAction) loaded.getAction(0);
      assertEquals(emails, action.getEmails(), "to");
      assertEquals(ccAddresses, action.getCCAddresses(), "cc");
      assertEquals(bccAddresses, action.getBCCAddresses(), "bcc");
   }

   @FunctionalInterface
   private interface NotificationTestBody {
      void run() throws Exception;
   }

   /**
    * getIdentityRemovalImpact() must report tasks owned by the deleted user and resolve the task
    * map from the identity's own org, without modifying any task. (The "execute as" path is
    * re-resolved from the security provider on load and is exercised via the EM/UI and manual
    * multi-tenant tests rather than this provider-less fixture.)
    */
   @Test
   void getIdentityRemovalImpact_reportsOwnedTasksWithoutMutating() throws Exception {
      ScheduleTask owned = new ScheduleTask("impact_owned");
      owned.setOwner(identityID_tuser0);

      ScheduleTask other = new ScheduleTask("impact_other");
      other.setOwner(identityID_admin);

      scheduleManager.setScheduleTask("tuser0~;~host-org:impact_owned", owned, admin);
      scheduleManager.setScheduleTask("admin~;~host-org:impact_other", other, admin);

      EditableAuthenticationProvider provider = mock(EditableAuthenticationProvider.class);

      // org is taken from the identity itself (identityID.orgID), so no provider lookup is needed
      ScheduleManager.IdentityTaskImpact impact =
         scheduleManager.getIdentityRemovalImpact(new User(identityID_tuser0), provider);

      assertTrue(impact.ownedTasks().contains("impact_owned"));
      assertFalse(impact.ownedTasks().contains("impact_other"));

      // the read-only impact check must not delete the owned task
      assertNotNull(scheduleManager.getScheduleTask("tuser0~;~host-org:impact_owned"));
   }

   /**
    * Bug #77100: removing an org role must clear the "execute as" in the role's own org, and
    * must not strip same-named bare notification tokens (they denote users) in any org.
    */
   @Test
   void identityRemoved_orgRoleScansRoleOrgAndKeepsNotifications() throws Exception {
      IdentityID roleX1 = new IdentityID("roleX", "org1");
      IdentityID roleXHost = new IdentityID("roleX", "host-org");

      withRoleFixture(() -> {
         ScheduleTask org1Task = seedTask("r77100_org1", new IdentityID("u1", "org1"),
                                          new Role(roleX1), "roleX,a@b.com", "org1");
         ScheduleTask hostTask = seedTask("r77100_host", identityID_admin,
                                          new Role(roleXHost), "roleX,c@d.com", "host-org");

         scheduleManager.identityRemoved(new DefaultIdentity(roleX1, Identity.ROLE),
                                         mock(EditableAuthenticationProvider.class));

         ScheduleTask org1After = scheduleManager.getScheduleTask(org1Task.getTaskId(), "org1");
         assertNull(org1After.getIdentity());
         assertEquals("roleX,a@b.com", getNotifications(org1After));

         // the same-named role in host-org and the host-org user token are untouched
         ScheduleTask hostAfter = scheduleManager.getScheduleTask(hostTask.getTaskId(), "host-org");
         assertNotNull(hostAfter.getIdentity());
         assertEquals(roleXHost, hostAfter.getIdentity().getIdentityID());
         assertEquals("roleX,c@d.com", getNotifications(hostAfter));
      });
   }

   /**
    * Bug #77100: removing a global role clears exact (name, null) "execute as" references in every
    * org, leaves same-named org roles alone, and never creates a task map for a null org.
    */
   @Test
   void identityRemoved_globalRoleClearsExecuteAsInEveryOrg() throws Exception {
      IdentityID roleG = new IdentityID("roleG", null);
      IdentityID roleG2 = new IdentityID("roleG", "org2");

      withRoleFixture(() -> {
         ScheduleTask hostTask = seedTask("r77100_ghost", identityID_admin,
                                          new Role(roleG), "roleG,c@d.com", "host-org");
         ScheduleTask org2Task = seedTask("r77100_gorg2", new IdentityID("u2", "org2"),
                                          new Role(roleG), "roleG,x@y.com", "org2");
         ScheduleTask org2OrgRoleTask = seedTask("r77100_gorg2r", new IdentityID("u2", "org2"),
                                                 new Role(roleG2), null, "org2");

         scheduleManager.identityRemoved(new DefaultIdentity(roleG, Identity.ROLE),
                                         mock(EditableAuthenticationProvider.class));

         ScheduleTask hostAfter = scheduleManager.getScheduleTask(hostTask.getTaskId(), "host-org");
         assertNull(hostAfter.getIdentity());
         assertEquals("roleG,c@d.com", getNotifications(hostAfter));

         ScheduleTask org2After = scheduleManager.getScheduleTask(org2Task.getTaskId(), "org2");
         assertNull(org2After.getIdentity());
         assertEquals("roleG,x@y.com", getNotifications(org2After));

         ScheduleTask org2RoleAfter =
            scheduleManager.getScheduleTask(org2OrgRoleTask.getTaskId(), "org2");
         assertNotNull(org2RoleAfter.getIdentity());
         assertEquals(roleG2, org2RoleAfter.getIdentity().getIdentityID());

         assertFalse(getTaskMapKeys().contains(null), "no task map for a null org");
      });
   }

   /**
    * Bug #77100: a host-org role is still cleared in host-org, and not in another org.
    */
   @Test
   void identityRemoved_hostOrgRoleScansHostOrg() throws Exception {
      IdentityID roleHHost = new IdentityID("roleH", "host-org");
      IdentityID roleH1 = new IdentityID("roleH", "org1");

      withRoleFixture(() -> {
         ScheduleTask hostTask = seedTask("r77100_hhost", identityID_admin,
                                          new Role(roleHHost), null, "host-org");
         ScheduleTask org1Task = seedTask("r77100_horg1", new IdentityID("u1", "org1"),
                                          new Role(roleH1), null, "org1");

         scheduleManager.identityRemoved(new DefaultIdentity(roleHHost, Identity.ROLE),
                                         mock(EditableAuthenticationProvider.class));

         assertNull(scheduleManager.getScheduleTask(hostTask.getTaskId(), "host-org").getIdentity());
         ScheduleTask org1After = scheduleManager.getScheduleTask(org1Task.getTaskId(), "org1");
         assertNotNull(org1After.getIdentity());
         assertEquals(roleH1, org1After.getIdentity().getIdentityID());
      });
   }

   /**
    * Bug #77100 guard: user and group removal keep their existing behavior (own org scanned,
    * owned tasks deleted, "execute as" cleared). The notification cleanup is type aware since
    * Bug #77111 and is covered by its tests.
    */
   @Test
   void identityRemoved_userAndGroupUnchanged() throws Exception {
      IdentityID gX = new IdentityID("gX", "org1");
      IdentityID u9 = new IdentityID("u9", "org1");

      withRoleFixture(() -> {
         ScheduleTask groupTask = seedTask("r77100_grp", new IdentityID("u1", "org1"),
                                           new Group(gX), "gX,e@f.com", "org1");
         ScheduleTask ownedTask = seedTask("r77100_own", u9, null, null, "org1");

         EditableAuthenticationProvider provider = mock(EditableAuthenticationProvider.class);
         when(provider.getGroup(gX)).thenReturn(new Group(gX));
         when(provider.getUser(u9)).thenReturn(new User(u9));

         scheduleManager.identityRemoved(new DefaultIdentity(gX, Identity.GROUP), provider);
         ScheduleTask groupAfter = scheduleManager.getScheduleTask(groupTask.getTaskId(), "org1");
         assertNull(groupAfter.getIdentity());

         scheduleManager.identityRemoved(new DefaultIdentity(u9, Identity.USER), provider);
         assertNull(scheduleManager.getScheduleTask(ownedTask.getTaskId(), "org1"));
      });
   }

   /**
    * Bug #77100 guard: the role branch returns early, but schedule extensions must still be
    * notified of the removed role.
    */
   @Test
   void identityRemoved_roleStillNotifiesExtensions() throws Exception {
      ScheduleExt ext = mock(ScheduleExt.class);
      Identity role = new DefaultIdentity(new IdentityID("roleE", "org1"), Identity.ROLE);
      scheduleManager.addScheduleExt(ext);

      try {
         scheduleManager.identityRemoved(role, mock(EditableAuthenticationProvider.class));
         verify(ext).identityRemoved(role);
      }
      finally {
         scheduleManager.getExtensions().remove(ext);
      }
   }

   /**
    * Runs the body with orgs host-org/org1/org2 and a provider that still resolves the roles and
    * groups, so a stale "execute as" survives a reload instead of re-resolving to null.
    */
   private void withRoleFixture(ThrowingRunnable body) throws Exception {
      String[] orgs = { "host-org", "org1", "org2" };
      SecurityProvider provider = mock(SecurityProvider.class);
      when(provider.getOrganizationIDs()).thenReturn(orgs);
      when(provider.getRole(any())).thenAnswer(inv -> new Role(inv.<IdentityID>getArgument(0)));
      when(provider.getGroup(any())).thenAnswer(inv -> new Group(inv.<IdentityID>getArgument(0)));
      // the task owners exist, so a removed "execute as" is reset to the owner (Bug #77332)
      when(provider.getUser(any())).thenAnswer(inv -> new User(inv.<IdentityID>getArgument(0)));
      // never stub the shared spy here, background threads use it (Bug #77336)
      securityEngineOverrides.setOrganizations(orgs);
      securityEngineOverrides.setSecurityProvider(provider);

      try {
         body.run();
      }
      finally {
         securityEngineOverrides.clear();

         for(String org : orgs) {
            Iterator<ScheduleTask> i = scheduleManager.getOrgTaskMap(org).values().iterator();

            while(i.hasNext()) {
               ScheduleTask task = i.next();

               if(task != null && task.getName().startsWith("r77100_")) {
                  i.remove();
               }
            }
         }
      }
   }

   /**
    * Fails the class loudly if the dispatcher was not installed on the spy.
    */
   @BeforeAll
   void verifySecurityEngineDispatch() {
      SecurityEngineOverrides.assertInstalled(securityEngine);
   }

   private ScheduleTask seedTask(String name, IdentityID owner, Identity executeAs,
                                 String notifications, String orgID) throws Exception
   {
      ScheduleTask task = createScheduleTask(name);
      task.setOwner(owner);
      task.setIdentity(executeAs);

      if(notifications != null) {
         ((AbstractAction) task.getAction(0)).setNotifications(notifications);
      }

      scheduleManager.save(List.of(task), orgID);
      return task;
   }

   /**
    * Saves a task as admin without the asset permission checks, as a task stored before the
    * sheet READ check of Bug #78129.
    */
   private void setScheduleTaskUnchecked(String taskId, ScheduleTask task) throws Exception {
      AssetRepository.IGNORE_PERM.set(true);

      try {
         scheduleManager.setScheduleTask(taskId, task, admin);
      }
      finally {
         AssetRepository.IGNORE_PERM.remove();
      }
   }

   private static String getNotifications(ScheduleTask task) {
      return ((AbstractAction) task.getAction(0)).getNotifications();
   }

   @SuppressWarnings("unchecked")
   private Set<String> getTaskMapKeys() throws Exception {
      java.lang.reflect.Field field = ScheduleManager.class.getDeclaredField("taskMap");
      field.setAccessible(true);
      return new HashSet<>(((Map<String, ?>) field.get(scheduleManager)).keySet());
   }

   @FunctionalInterface
   private interface ThrowingRunnable {
      void run() throws Exception;
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

