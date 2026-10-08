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
package inetsoft.web.admin.schedule;

import inetsoft.report.internal.Util;
import inetsoft.sree.AnalyticRepository;
import inetsoft.sree.RepositoryEntry;
import inetsoft.sree.schedule.*;
import inetsoft.sree.security.*;
import inetsoft.test.*;
import inetsoft.uql.XPrincipal;
import inetsoft.uql.asset.AssetEntry;
import inetsoft.uql.viewsheet.FileFormatInfo;
import inetsoft.util.ThreadContext;
import inetsoft.web.admin.deploy.DeployService;
import inetsoft.web.admin.schedule.model.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.security.Principal;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Bug #77973, a task save pairs the actions and conditions the editor sends with the stored
 * items they replace by the original index the editor got with them, not by their position.
 * The editor lists are loaded with the real getTaskActions/getTaskConditions and saved with the
 * real saveTask.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, IntegrationTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class ScheduleTaskItemPairingTest {
   @BeforeEach
   void setUp() throws Exception {
      savedPrincipal = ThreadContext.getContextPrincipal();
      savedThreadPrincipal = ThreadContext.getPrincipal();
      OrganizationManager orgManager = mock(OrganizationManager.class);
      orgStatic = mockStatic(OrganizationManager.class);
      orgStatic.when(OrganizationManager::getInstance).thenReturn(orgManager);
      when(orgManager.getCurrentOrgID(any())).thenReturn("orga");
      when(orgManager.getCurrentOrgID()).thenReturn("orga");

      scheduleManager = mock(ScheduleManager.class);
      SecurityEngine securityEngine = mock(SecurityEngine.class);
      when(securityEngine.checkPermission(any(), any(ResourceType.class), anyString(),
                                          any(ResourceAction.class))).thenReturn(true);
      scheduleService = spy(new ScheduleService(null, scheduleManager, null,
                                                new ScheduleConditionService(), null,
                                                mock(DeployService.class), null, null, null,
                                                null, null, null, null));
      doReturn(true).when(scheduleService)
         .checkPermission(any(), eq(ResourceType.SCHEDULE_OPTION), anyString());
      doReturn(TASK).when(scheduleService).updateTaskName(any(), any(), any(), any());
      doNothing().when(scheduleService).saveTask(anyString(), any(), any());
      doReturn(new AssetEntry[0]).when(scheduleService).getViewsheets(any());
      AnalyticRepository repository = mock(AnalyticRepository.class);
      when(repository.getFolders(any(), any())).thenReturn(new RepositoryEntry[0]);
      service = spy(new ScheduleTaskService(repository, scheduleManager, scheduleService,
                                            new ScheduleConditionService(),
                                            mock(SecurityProvider.class), null, securityEngine));
      doReturn(null).when(service).getDialogModel(anyString(), any(), anyBoolean());
      // the options are not under test
      doNothing().when(service).setTaskOptions(any(), any(), any());
      principal = mock(XPrincipal.class);
      when(principal.getName()).thenReturn(new IdentityID("alice", "orga").convertToKey());
   }

   @AfterEach
   void tearDown() {
      orgStatic.close();
      ThreadContext.setContextPrincipal(savedPrincipal);
      ThreadContext.setPrincipal(savedThreadPrincipal);
   }

   @Test
   void deleteFirstActionKeepsZipPasswordOfKeptAction() throws Exception {
      ViewsheetAction kept = deleteFirstAction(action(SHEET, "a@x.com", "pwA"),
                                               action(SHEET, "b@x.com", "pwB"));
      assertEquals("b@x.com", kept.getEmails());
      assertEquals("pwB", kept.getPassword());
   }

   @Test
   void deleteFirstActionWithoutEmailDeliveryKeepsOwnRecipients() throws Exception {
      deny("emailDelivery");
      ViewsheetAction kept = deleteFirstAction(action(SHEET, "a@x.com", "pwA"),
                                               action(SHEET, "b@x.com", "pwB"));
      assertEquals("b@x.com", kept.getEmails());
      assertEquals("pwB", kept.getPassword());
   }

   @Test
   void deleteFirstActionOfOtherSheetWithoutEmailDeliveryKeepsOwnRecipients() throws Exception {
      deny("emailDelivery");
      ViewsheetAction kept = deleteFirstAction(action(SHEET2, "a@x.com", "pwA"),
                                               action(SHEET, "b@x.com", "pwB"));
      assertEquals("b@x.com", kept.getEmails());
      assertEquals("pwB", kept.getPassword());
   }

   @Test
   void deleteFirstActionWithoutNotificationEmailKeepsOwnNotifications() throws Exception {
      deny("notificationEmail");
      ViewsheetAction a = action(SHEET, "a@x.com", "pwA");
      a.setNotifications("na@x.com");
      ViewsheetAction b = action(SHEET, "b@x.com", "pwB");
      b.setNotifications("nb@x.com");
      assertEquals("nb@x.com", deleteFirstAction(a, b).getNotifications());
   }

   @Test
   void deleteFirstActionWithoutSaveToDiskKeepsOwnSavePath() throws Exception {
      deny("saveToDisk");
      ViewsheetAction a = action(SHEET, "a@x.com", "pwA");
      a.setFilePath(PDF, new ServerPathInfo("/out/a.pdf", null, null));
      ViewsheetAction b = action(SHEET, "b@x.com", "pwB");
      b.setFilePath(PDF, new ServerPathInfo("/out/b.pdf", null, null));
      assertEquals("/out/b.pdf", deleteFirstAction(a, b).getFilePath(PDF));
   }

   @Test
   void deleteFirstActionKeepsFtpPasswordOfKeptAction() throws Exception {
      ViewsheetAction a = action(SHEET, "a@x.com", "pwA");
      a.setFilePath(PDF, new ServerPathInfo("ftp://files.example/out/a.pdf", "u", "ftpA"));
      ViewsheetAction b = action(SHEET, "b@x.com", "pwB");
      b.setFilePath(PDF, new ServerPathInfo("ftp://files.example/out/b.pdf", "u", "ftpB"));
      assertEquals("ftpB", deleteFirstAction(a, b).getFilePathInfo(PDF).getPassword());
   }

   @Test
   void unchangedSaveKeepsEachPassword() throws Exception {
      ScheduleTask stored = storedTask(action(SHEET, "a@x.com", "pwA"),
                                       action(SHEET, "b@x.com", "pwB"));
      ScheduleTask saved = save(stored, editorActions(), true);
      assertEquals("pwA", password(saved, 0));
      assertEquals("pwB", password(saved, 1));
   }

   @Test
   void copyTakesPasswordOfItsSourceNotPlaceholder() throws Exception {
      ScheduleTask stored = storedTask(action(SHEET, "a@x.com", "pwA"));
      List<ScheduleActionModel> actions = editorActions();
      // the editors' Copy clones the model, with its original index
      actions.add(actions.get(0));
      ScheduleTask saved = save(stored, actions, true);
      assertEquals(2, saved.getActionCount());
      assertEquals("pwA", password(saved, 0));
      assertEquals("pwA", password(saved, 1));
   }

   @Test
   void copyIsNotTheStoredVersionOfItsSource() throws Exception {
      deny("emailDelivery");
      ScheduleTask stored = storedTask(action(SHEET, "a@x.com", "pwA"));
      List<ScheduleActionModel> actions = editorActions();
      actions.add(actions.get(0));
      ScheduleTask saved = save(stored, actions, true);
      // only the first claimant is the stored action, a copy is a new action
      assertEquals("a@x.com", ((ViewsheetAction) saved.getAction(0)).getEmails());
      assertEquals("pwA", password(saved, 0));
      assertNull(((ViewsheetAction) saved.getAction(1)).getEmails());
   }

   @Test
   void copyOfDeletedActionIsThatAction() throws Exception {
      ScheduleTask stored = storedTask(action(SHEET, "a@x.com", "pwA"),
                                       action(SHEET, "b@x.com", "pwB"));
      List<ScheduleActionModel> actions = editorActions();
      actions.add(actions.get(0));
      actions.remove(0);
      ScheduleTask saved = save(stored, actions, true);
      assertEquals("pwB", password(saved, 0));
      assertEquals("pwA", password(saved, 1));
   }

   @Test
   void unresolvedPlaceholderIsNotStoredAsPassword() throws Exception {
      ScheduleTask stored = storedTask(action(SHEET, "a@x.com", "pwA"));
      List<ScheduleActionModel> actions = editorActions();
      // an index that isn't a stored position is a new action
      actions.add(GeneralActionModel.builder()
                     .from((GeneralActionModel) actions.get(0)).originalIndex(5).build());
      ScheduleTask saved = save(stored, actions, true);
      assertEquals("pwA", password(saved, 0));
      assertNull(password(saved, 1));
   }

   @Test
   void bodyWithoutMarkerPairsByPosition() throws Exception {
      ScheduleTask stored = storedTask(action(SHEET, "a@x.com", "pwA"),
                                       action(SHEET, "b@x.com", "pwB"));
      ScheduleTask saved = save(stored, editorActions(), false);
      assertEquals("pwA", password(saved, 0));
      assertEquals("pwB", password(saved, 1));
   }

   @Test
   void bodyWithoutMarkerDoesNotStorePlaceholderOfNewAction() throws Exception {
      ScheduleTask stored = storedTask(action(SHEET, "a@x.com", "pwA"));
      List<ScheduleActionModel> actions = editorActions();
      actions.add(actions.get(0));
      actions.add(actions.get(0));
      ScheduleTask saved = save(stored, actions, false);
      assertEquals("pwA", password(saved, 0));
      // positional pairing (an older client), the second action has no stored partner
      assertNull(password(saved, 2));
   }

   @Test
   void editorActionsCarryStoredPositionOfActionsWithModel() throws Exception {
      ScheduleTask stored = storedTask(mock(ScheduleAction.class),
                                       action(SHEET, "b@x.com", "pwB"));
      when(scheduleManager.getScheduleTask(TASK)).thenReturn(stored);
      List<ScheduleActionModel> actions = editorActions();
      assertEquals(1, actions.size());
      assertEquals(1, actions.get(0).originalIndex());
   }

   @Test
   void deleteFirstConditionKeepsTimeOfKeptCondition() throws Exception {
      deny("startTime");
      ScheduleTask stored = storedTask(action(SHEET, "b@x.com", "pwB"));
      stored.addCondition(daily(3, 0, null));
      stored.addCondition(daily(9, 15, null));
      TimeCondition kept = deleteFirstCondition(stored);
      assertEquals(9, kept.getHour());
      assertEquals(15, kept.getMinute());
   }

   @Test
   void deleteFirstConditionKeepsBalancedTimeOfKeptCondition() throws Exception {
      TimeRange range = new TimeRange("Morning", "06:00", "12:00", false);
      ScheduleTask stored = storedTask(action(SHEET, "b@x.com", "pwB"));
      stored.addCondition(daily(7, 0, range));
      stored.addCondition(daily(9, 15, range));
      TimeCondition kept = deleteFirstCondition(stored);
      assertEquals(9, kept.getHour());
      assertEquals(15, kept.getMinute());
   }

   @Test
   void deleteFirstBackupActionKeepsFtpPasswordOfKeptAction() throws Exception {
      ScheduleTask stored = storedTask(backup("ftp://files.example/bk/a.zip", "ftpA"),
                                       backup("ftp://files.example/bk/b.zip", "ftpB"));
      List<ScheduleActionModel> actions = editorActions();
      assertEquals(Util.PLACEHOLDER_PASSWORD,
                   ((BackupActionModel) actions.get(1)).backupServerPath().password());
      actions.remove(0);
      ScheduleTask saved = save(stored, actions, true);
      assertEquals(1, saved.getActionCount());
      ServerPathInfo kept = ((IndividualAssetBackupAction) saved.getAction(0)).getServerPath();
      assertEquals("ftp://files.example/bk/b.zip", kept.getPath());
      assertEquals("ftpB", kept.getPassword());
   }

   @Test
   void multiDeleteWithTwoDigitIndexesKeepsEachKeptActionsOwnValues() throws Exception {
      deny("emailDelivery");
      ScheduleAction[] all = new ScheduleAction[12];

      for(int i = 0; i < all.length; i++) {
         all[i] = action(SHEET, "a" + i + "@x.com", "pw" + i);
      }

      ScheduleTask stored = storedTask(all);
      List<ScheduleActionModel> actions = editorActions();
      // the portal removes the selected indexes [2, 10] from the end
      actions.remove(10);
      actions.remove(2);
      ScheduleTask saved = save(stored, actions, true);
      assertEquals(10, saved.getActionCount());
      int[] kept = { 0, 1, 3, 4, 5, 6, 7, 8, 9, 11 };

      for(int i = 0; i < kept.length; i++) {
         ViewsheetAction action = (ViewsheetAction) saved.getAction(i);
         assertEquals("a" + kept[i] + "@x.com", action.getEmails());
         assertEquals("pw" + kept[i], action.getPassword());
      }
   }

   @Test
   void reorderedActionsKeepTheirOwnValues() throws Exception {
      deny("emailDelivery");
      ScheduleTask stored = storedTask(action(SHEET, "a@x.com", "pwA"),
                                       action(SHEET, "b@x.com", "pwB"));
      List<ScheduleActionModel> actions = editorActions();
      Collections.reverse(actions);
      ScheduleTask saved = save(stored, actions, true);
      assertEquals("b@x.com", ((ViewsheetAction) saved.getAction(0)).getEmails());
      assertEquals("pwB", password(saved, 0));
      assertEquals("a@x.com", ((ViewsheetAction) saved.getAction(1)).getEmails());
      assertEquals("pwA", password(saved, 1));
   }

   @Test
   void editedKeptActionKeepsItsPasswordAfterDelete() throws Exception {
      ScheduleTask stored = storedTask(action(SHEET, "a@x.com", "pwA"),
                                       action(SHEET, "b@x.com", "pwB"));
      List<ScheduleActionModel> actions = editorActions();
      actions.remove(0);
      // the editor changes the recipients and sends the password placeholder back
      actions.set(0, GeneralActionModel.builder().from((GeneralActionModel) actions.get(0))
         .to("b2@x.com").build());
      ScheduleTask saved = save(stored, actions, true);
      assertEquals("b2@x.com", ((ViewsheetAction) saved.getAction(0)).getEmails());
      assertEquals("pwB", password(saved, 0));
   }

   @Test
   void portalSaveDeleteFirstActionKeepsZipPasswordOfKeptAction() throws Exception {
      ScheduleTask stored = storedTask(action(SHEET, "a@x.com", "pwA"),
                                       action(SHEET, "b@x.com", "pwB"));
      List<ScheduleActionModel> actions =
         new ArrayList<>(service.getTaskActions(TASK, principal, false).actions());
      actions.remove(0);
      service.saveTask(ScheduleTaskEditorModel.builder()
                          .taskName(TASK).oldTaskName(TASK).options(mock(TaskOptionsPaneModel.class))
                          .addAllActions(actions).itemsIdentified(true).build(),
                       LINK, principal, false);
      ArgumentCaptor<ScheduleTask> captor = ArgumentCaptor.forClass(ScheduleTask.class);
      verify(scheduleService).saveTask(anyString(), captor.capture(), any());
      assertEquals(1, captor.getValue().getActionCount());
      assertEquals("b@x.com", ((ViewsheetAction) captor.getValue().getAction(0)).getEmails());
      assertEquals("pwB", password(captor.getValue(), 0));
   }

   private ViewsheetAction deleteFirstAction(ScheduleAction a, ScheduleAction b)
      throws Exception
   {
      ScheduleTask stored = storedTask(a, b);
      List<ScheduleActionModel> actions = editorActions();
      actions.remove(0);
      ScheduleTask saved = save(stored, actions, true);
      assertEquals(1, saved.getActionCount());
      return (ViewsheetAction) saved.getAction(0);
   }

   private TimeCondition deleteFirstCondition(ScheduleTask stored) throws Exception {
      when(scheduleManager.getScheduleTask(TASK)).thenReturn(stored);
      List<ScheduleConditionModel> conditions =
         new ArrayList<>(service.getTaskConditions(TASK, principal).conditions());
      conditions.remove(0);
      ScheduleTaskEditorModel model = ScheduleTaskEditorModel.builder()
         .taskName(TASK).oldTaskName(TASK).options(mock(TaskOptionsPaneModel.class))
         .addAllConditions(conditions)
         .addAllActions(editorActions())
         .itemsIdentified(true)
         .build();
      ScheduleTask saved = save(model);
      assertEquals(1, saved.getConditionCount());
      return (TimeCondition) saved.getCondition(0);
   }

   private ScheduleTask storedTask(ScheduleAction... actions) {
      ScheduleTask stored = new ScheduleTask(TASK);
      stored.setOwner(new IdentityID("alice", "orga"));
      Arrays.stream(actions).forEach(stored::addAction);
      when(scheduleManager.getScheduleTask(TASK)).thenReturn(stored);
      return stored;
   }

   /**
    * Gets the actions the editor gets for the stored task.
    */
   private List<ScheduleActionModel> editorActions() throws Exception {
      return new ArrayList<>(service.getTaskActions(TASK, principal, true).actions());
   }

   private ScheduleTask save(ScheduleTask stored, List<ScheduleActionModel> actions,
                             boolean identified)
      throws Exception
   {
      ScheduleTaskEditorModel.Builder builder = ScheduleTaskEditorModel.builder();
      builder.taskName(TASK).oldTaskName(TASK).options(mock(TaskOptionsPaneModel.class))
         .addAllActions(actions);

      if(identified) {
         builder.itemsIdentified(true);
      }

      return save(builder.build());
   }

   private ScheduleTask save(ScheduleTaskEditorModel model) throws Exception {
      service.saveTask(model, LINK, principal, true);
      ArgumentCaptor<ScheduleTask> captor = ArgumentCaptor.forClass(ScheduleTask.class);
      verify(scheduleService).saveTask(anyString(), captor.capture(), any());
      return captor.getValue();
   }

   private void deny(String option) {
      doReturn(false).when(scheduleService)
         .checkPermission(any(), eq(ResourceType.SCHEDULE_OPTION), eq(option));
   }

   private static String password(ScheduleTask task, int index) {
      return ((ViewsheetAction) task.getAction(index)).getPassword();
   }

   private static TimeCondition daily(int hour, int minute, TimeRange range) {
      TimeCondition condition = TimeCondition.at(hour, minute, 0);
      condition.setType(TimeCondition.EVERY_DAY);
      condition.setTimeRange(range);
      return condition;
   }

   private static ViewsheetAction action(String sheet, String emails, String password) {
      ViewsheetAction action = new ViewsheetAction();
      action.setViewsheet(sheet);
      action.setEmails(emails);
      action.setFileFormat("PDF");
      action.setCompressFile(true);
      action.setPassword(password);
      action.setMatchLayout(true);
      return action;
   }

   private static IndividualAssetBackupAction backup(String path, String password) {
      IndividualAssetBackupAction action = new IndividualAssetBackupAction();
      action.setServerPaths(new ServerPathInfo(path, "u", password));
      return action;
   }

   private static final int PDF = FileFormatInfo.EXPORT_TYPE_PDF;
   private static final String TASK = "task1";
   private static final String SHEET = "1^128^__NULL__^Examples/Census^orga";
   private static final String SHEET2 = "1^128^__NULL__^Examples/Other^orga";
   private static final String LINK = "http://host/";
   private Principal savedPrincipal;
   private Principal savedThreadPrincipal;
   private MockedStatic<OrganizationManager> orgStatic;
   private ScheduleManager scheduleManager;
   private ScheduleService scheduleService;
   private ScheduleTaskService service;
   private XPrincipal principal;
}
