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
package inetsoft.web.wiz.service;

import inetsoft.sree.schedule.ScheduleManager;
import inetsoft.sree.schedule.ScheduleTask;
import inetsoft.sree.schedule.TimeCondition;
import inetsoft.sree.schedule.ViewsheetAction;
import inetsoft.sree.security.IdentityID;
import inetsoft.sree.security.ResourceAction;
import inetsoft.sree.security.ResourceType;
import inetsoft.sree.security.SRPrincipal;
import inetsoft.sree.security.SecurityEngine;
import inetsoft.uql.asset.AssetEntry;
import inetsoft.uql.asset.AssetRepository;
import inetsoft.uql.viewsheet.VSBookmark;
import inetsoft.uql.viewsheet.VSBookmarkInfo;
import inetsoft.web.admin.schedule.ScheduleTaskService;
import inetsoft.web.wiz.service.WizScheduleService.TaskRequest;
import inetsoft.web.wiz.service.WizScheduleService.TaskView;
import inetsoft.uql.util.XSessionService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import org.mockito.MockedStatic;

import java.security.Principal;
import java.util.List;
import java.util.Vector;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Redmine #82413: the non-admin dashboard schedule task service. Pins that a task is owned by the
 * caller only, that another user's task is never listed, changed or deleted, that the Schedule
 * permissions and schedule options are evaluated for the real user, and the time conditions built
 * from the plugin's request.
 */
@Tag("core")
class WizScheduleServiceTest {
   private MockedStatic<XSessionService> sessionStatic;

   /** A principal registers itself with the session service when it is built. */
   @BeforeEach
   void mockSessionService() {
      sessionStatic = mockStatic(XSessionService.class);
      sessionStatic.when(XSessionService::getService).thenReturn(mock(XSessionService.class));
   }

   @AfterEach
   void closeSessionService() {
      sessionStatic.close();
   }

   private static final String ORG = "org1";
   private static final IdentityID BOB = new IdentityID("bob", ORG);
   private static final IdentityID ALICE = new IdentityID("alice", ORG);

   @Test
   void createStoresATaskOwnedByTheCallerWithTheEmailActionDefaults() throws Exception {
      Fixture f = new Fixture(BOB);

      TaskView view = f.service.create(daily("Weekly sales"), f.wiz, "http://h/sree/");

      ArgumentCaptor<ScheduleTask> stored = ArgumentCaptor.forClass(ScheduleTask.class);
      verify(f.manager).setScheduleTask(eq("bob~;~org1:Weekly sales"), stored.capture(), isNull(),
                                        eq(f.wiz));
      ScheduleTask task = stored.getValue();
      assertEquals(BOB, task.getOwner());
      assertTrue(task.isEnabled());
      assertEquals(1, task.getConditionCount());
      ViewsheetAction action = (ViewsheetAction) task.getAction(0);
      assertEquals("a@x.com,b@x.com", action.getEmails());
      assertEquals("Excel", action.getFileFormat());
      assertArrayEquals(new String[] { "(Home)" }, action.getBookmarks());
      assertEquals(AssetEntry.createAssetEntry(action.getViewsheet()).getPath(), "Sales/Overview");
      assertEquals("Weekly sales", view.name());
      assertEquals("Sales/Overview", view.path());
      assertEquals(List.of("a@x.com", "b@x.com"), view.to());
   }

   @Test
   void theScheduleOptionsAreEvaluatedForTheRealUser() throws Exception {
      Fixture f = new Fixture(BOB);

      f.service.create(daily("t"), f.wiz, "u/");

      ArgumentCaptor<Principal> forConditions = ArgumentCaptor.forClass(Principal.class);
      ArgumentCaptor<Principal> forAction = ArgumentCaptor.forClass(Principal.class);
      verify(f.tasks).sanitizeConditions(any(ScheduleTask.class), any(ScheduleTask.class),
                                         forConditions.capture());
      verify(f.tasks).sanitizeAction(any(), any(), forAction.capture());
      assertFalse(SRPrincipal.isWizPrincipal(forConditions.getValue()));
      assertFalse(SRPrincipal.isWizPrincipal(forAction.getValue()));
   }

   @Test
   void aUserWhoMayNotDeliverByEmailGetsAnErrorNotAnEmptyTask() throws Exception {
      Fixture f = new Fixture(BOB);
      // what sanitizeAction does for a user without the emailDelivery option
      doAnswer(inv -> {
         ((ViewsheetAction) inv.getArgument(0)).setEmails(null);
         return null;
      }).when(f.tasks).sanitizeAction(any(), any(), any());

      SecurityException e = assertThrows(SecurityException.class,
         () -> f.service.create(daily("t"), f.wiz, "u/"));
      assertTrue(e.getMessage().contains("email"));
      verify(f.manager, never()).setScheduleTask(anyString(), any(), any(AssetEntry.class), any());
   }

   @Test
   void aUserWhoMayNotSetATimeGetsAnErrorWhenTheConditionIsDiscarded() throws Exception {
      Fixture f = new Fixture(BOB);
      // what sanitizeConditions does to a run-once condition without the startTime option
      doAnswer(inv -> {
         ((ScheduleTask) inv.getArgument(0)).removeCondition(0);
         return null;
      }).when(f.tasks).sanitizeConditions(any(ScheduleTask.class), any(ScheduleTask.class), any());

      TaskRequest once = new TaskRequest("t", "Sales/Overview", null, null, null, "once", null,
                                         "2030-01-01T08:30", null, null, null, null, null,
                                         List.of("a@x.com"), null, null, null, null, null, null);
      assertThrows(SecurityException.class, () -> f.service.create(once, f.wiz, "u/"));
      verify(f.manager, never()).setScheduleTask(anyString(), any(), any(AssetEntry.class), any());
   }

   @Test
   void noScheduleAccessMeansNothingIsReadOrWritten() throws Exception {
      Fixture f = new Fixture(BOB);
      when(f.engine.checkPermission(any(), eq(ResourceType.PORTAL_TAB), eq("Schedule"),
                                    any(ResourceAction.class)))
         .thenAnswer(inv -> SRPrincipal.isWizPrincipal(inv.getArgument(0)));

      assertThrows(SecurityException.class, () -> f.service.create(daily("t"), f.wiz, "u/"));
      assertThrows(SecurityException.class, () -> f.service.list(null, null, f.wiz));
      assertThrows(SecurityException.class, () -> f.service.get("t", f.wiz));
      assertThrows(SecurityException.class, () -> f.service.update("t", daily("t"), f.wiz, "u/"));
      assertThrows(SecurityException.class, () -> f.service.delete("t", f.wiz));
      assertThrows(SecurityException.class, () -> f.service.getFormatOptions(f.wiz));
      verify(f.manager, never()).setScheduleTask(anyString(), any(), any(AssetEntry.class), any());
      verify(f.manager, never()).removeScheduleTask(anyString(), any(), anyBoolean());
   }

   @Test
   void anExistingTaskIsNeverOverwritten() throws Exception {
      Fixture f = new Fixture(BOB);
      f.store(task("t", BOB));

      IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
         () -> f.service.create(daily("t"), f.wiz, "u/"));
      assertTrue(e.getMessage().contains("already exists"));
      verify(f.manager, never()).setScheduleTask(anyString(), any(), any(AssetEntry.class), any());
   }

   @Test
   void listShowsOnlyTheCallersOwnDashboardTasks() throws Exception {
      Fixture f = new Fixture(BOB);
      ScheduleTask mine = task("mine", BOB);
      ScheduleTask theirs = task("theirs", ALICE);
      ScheduleTask noDashboard = new ScheduleTask();
      noDashboard.setName("plain");
      noDashboard.setOwner(BOB);
      when(f.manager.getScheduleTasks(any(Principal.class)))
         .thenReturn(new Vector<>(List.of(mine, theirs, noDashboard)));

      List<TaskView> views = f.service.list(null, null, f.wiz);

      assertEquals(1, views.size());
      assertEquals("mine", views.get(0).name());
   }

   @Test
   void anotherUsersTaskIsReportedAsNotFoundWhateverTheOperation() throws Exception {
      Fixture f = new Fixture(BOB);
      f.store(task("theirs", ALICE));
      // a name cannot reach another owner's id: the id is always built from the caller's key
      f.store(task("alice~;~org1:theirs", ALICE));

      for(String name : new String[] { "theirs", "alice~;~org1:theirs", "missing" }) {
         assertThrows(IllegalArgumentException.class, () -> f.service.get(name, f.wiz));
         assertThrows(IllegalArgumentException.class,
                      () -> f.service.update(name, daily(name), f.wiz, "u/"));
         assertThrows(IllegalArgumentException.class, () -> f.service.delete(name, f.wiz));
      }

      verify(f.manager, never()).setScheduleTask(anyString(), any(), any(AssetEntry.class), any());
      verify(f.manager, never()).removeScheduleTask(anyString(), any(), anyBoolean());
   }

   @Test
   void theCallerCanChangeAndDeleteOwnTask() throws Exception {
      Fixture f = new Fixture(BOB);
      f.store(task("mine", BOB));
      when(f.assets.containsEntry(any(AssetEntry.class))).thenReturn(true);

      TaskRequest change = new TaskRequest(null, null, null, "d", false, null, null, null, null,
                                           null, null, null, null, null, null, null, "new subject",
                                           null, "pdf", null);
      TaskView view = f.service.update("mine", change, f.wiz, "u/");

      assertFalse(view.enabled());
      assertEquals("PDF", view.format());
      assertEquals("new subject", view.subject());
      // the owner and the recipients are not touched by a change that does not carry them
      assertEquals(List.of("a@x.com"), view.to());
      verify(f.manager).setScheduleTask(eq("bob~;~org1:mine"), any(ScheduleTask.class), any(),
                                        eq(f.wiz));

      f.service.delete("mine", f.wiz);
      verify(f.manager).removeScheduleTask("bob~;~org1:mine", f.wiz, true);
   }

   @Test
   void aTaskWithMoreThanOneActionIsNotChangedHere() throws Exception {
      Fixture f = new Fixture(BOB);
      ScheduleTask two = task("two", BOB);
      two.addAction(new ViewsheetAction());
      f.store(two);

      assertThrows(IllegalArgumentException.class, () -> f.service.delete("two", f.wiz));
      assertThrows(IllegalArgumentException.class,
                   () -> f.service.update("two", daily("two"), f.wiz, "u/"));
   }

   @Test
   void taskNamesCannotCarryOwnerOrPathSeparators() {
      Fixture f = new Fixture(BOB);

      for(String name : new String[] { "", " ", "a^b", "a/b", "a\\b" }) {
         assertThrows(IllegalArgumentException.class,
                      () -> f.service.create(daily(name), f.wiz, "u/"), "name=" + name);
      }
   }

   @Test
   void aDailyConditionRunsAtTheGivenTime() {
      TimeCondition c = (TimeCondition) WizScheduleService.buildCondition(request(
         "daily", "08:30", null, null, null, null));

      assertEquals(TimeCondition.EVERY_DAY, c.getType());
      assertEquals(8, c.getHour());
      assertEquals(30, c.getMinute());
      assertEquals(1, c.getInterval());
   }

   @Test
   void aWeeklyConditionNeedsDaysAndKeepsThem() {
      TimeCondition c = (TimeCondition) WizScheduleService.buildCondition(request(
         "weekly", "09:00", List.of(2, 4), null, null, null));

      assertEquals(TimeCondition.EVERY_WEEK, c.getType());
      assertArrayEquals(new int[] { 2, 4 }, c.getDaysOfWeek());
      assertThrows(IllegalArgumentException.class, () -> WizScheduleService.buildCondition(
         request("weekly", "09:00", null, null, null, null)));
      assertThrows(IllegalArgumentException.class, () -> WizScheduleService.buildCondition(
         request("weekly", "09:00", List.of(0), null, null, null)));
      assertThrows(IllegalArgumentException.class, () -> WizScheduleService.buildCondition(
         request("weekly", "09:00", List.of(8), null, null, null)));
   }

   @Test
   void aMonthlyConditionNeedsADayOfMonth() {
      TimeCondition c = (TimeCondition) WizScheduleService.buildCondition(request(
         "monthly", "06:15", null, 15, null, null));
      assertEquals(TimeCondition.EVERY_MONTH, c.getType());
      assertEquals(15, c.getDayOfMonth());

      assertEquals(-1, ((TimeCondition) WizScheduleService.buildCondition(request(
         "monthly", "06:15", null, -1, null, null))).getDayOfMonth());
      assertThrows(IllegalArgumentException.class, () -> WizScheduleService.buildCondition(
         request("monthly", "06:15", null, null, null, null)));
      assertThrows(IllegalArgumentException.class, () -> WizScheduleService.buildCondition(
         request("monthly", "06:15", null, 32, null, null)));
   }

   @Test
   void aOnceConditionRunsAtTheGivenLocalTime() {
      TimeCondition c = (TimeCondition) WizScheduleService.buildCondition(request(
         "once", null, null, null, "2030-01-01T08:30", "UTC"));

      assertEquals(TimeCondition.AT, c.getType());
      assertEquals(java.time.Instant.parse("2030-01-01T08:30:00Z"), c.getDate().toInstant());
      assertThrows(IllegalArgumentException.class, () -> WizScheduleService.buildCondition(
         request("once", null, null, null, null, null)));
      assertThrows(IllegalArgumentException.class, () -> WizScheduleService.buildCondition(
         request("once", null, null, null, "tomorrow", null)));
   }

   @Test
   void badTimesFrequenciesAndZonesAreRefusedWithTheFieldNamed() {
      String[][] cases = {
         { "hourly", "08:30", "frequency" }, { null, "08:30", "frequency" },
         { "daily", null, "time" }, { "daily", "25:00", "time" }, { "daily", "8am", "time" },
         { "daily", "08:61", "time" } };

      for(String[] c : cases) {
         IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
            () -> WizScheduleService.buildCondition(request(c[0], c[1], null, null, null, null)),
            c[0] + "/" + c[1]);
         assertTrue(e.getMessage().contains(c[2]), e.getMessage());
      }

      IllegalArgumentException zone = assertThrows(IllegalArgumentException.class,
         () -> WizScheduleService.buildCondition(request("daily", "08:30", null, null, null,
                                                         "Mars/Olympus")));
      assertTrue(zone.getMessage().contains("timeZone"));
      assertDoesNotThrow(() -> WizScheduleService.buildCondition(
         request("daily", "08:30", null, null, null, "America/New_York")));
   }


   @Test
   void theDefaultHomeBookmarkIsStoredAsTheScheduleTabStoresIt() throws Exception {
      Fixture f = new Fixture(BOB);

      f.service.create(daily("t"), f.wiz, "u/");

      ViewsheetAction action = storedAction(f);
      // all-share, owned by the caller: a private (0) Home bookmark fails at run time with "No
      // valid bookmark" (ViewsheetAction#runViewsheetAction matches name, type and owner)
      assertArrayEquals(new String[] { "(Home)" }, action.getBookmarks());
      assertArrayEquals(new int[] { VSBookmarkInfo.ALLSHARE }, action.getBookmarkTypes());
      assertArrayEquals(new IdentityID[] { BOB }, action.getBookmarkUsers());
      String xml = xml(action);
      assertTrue(xml.contains("type=\"1\""), xml);
      assertFalse(xml.contains("type=\"0\""), xml);
   }

   @Test
   void aHomeBookmarkTheListLacksIsStillAllShare() throws Exception {
      Fixture f = new Fixture(BOB);
      f.offered.clear();

      f.service.create(daily("t"), f.wiz, "u/");

      assertArrayEquals(new int[] { VSBookmarkInfo.ALLSHARE }, storedAction(f).getBookmarkTypes());
   }

   @Test
   void aNamedBookmarkIsStoredWithTheTypeAndOwnerTheListGives() throws Exception {
      Fixture f = new Fixture(BOB);
      f.offered.add(new VSBookmarkInfo("Q1", VSBookmarkInfo.ALLSHARE, ALICE, false, 2L));
      f.offered.add(new VSBookmarkInfo("Mine", VSBookmarkInfo.PRIVATE, BOB, false, 3L));
      f.offered.add(new VSBookmarkInfo("Team", VSBookmarkInfo.GROUPSHARE, ALICE, false, 4L));

      f.service.create(withBookmarks("Q1(alice)", "Mine", "Team"), f.wiz, "u/");

      ViewsheetAction action = storedAction(f);
      assertArrayEquals(new String[] { "Q1", "Mine", "Team" }, action.getBookmarks());
      assertArrayEquals(new int[] { 1, 0, 2 }, action.getBookmarkTypes());
      assertArrayEquals(new IdentityID[] { ALICE, BOB, ALICE }, action.getBookmarkUsers());
   }

   @Test
   void anUnknownOrAmbiguousBookmarkIsRefusedNamingTheChoices() throws Exception {
      Fixture f = new Fixture(BOB);
      f.offered.add(new VSBookmarkInfo("Q1", VSBookmarkInfo.ALLSHARE, ALICE, false, 2L));
      f.offered.add(new VSBookmarkInfo("Q1", VSBookmarkInfo.ALLSHARE, BOB, false, 3L));

      IllegalArgumentException missing = assertThrows(IllegalArgumentException.class,
         () -> f.service.create(withBookmarks("Nope"), f.wiz, "u/"));
      assertTrue(missing.getMessage().contains("(Home)"), missing.getMessage());
      IllegalArgumentException ambiguous = assertThrows(IllegalArgumentException.class,
         () -> f.service.create(withBookmarks("Q1"), f.wiz, "u/"));
      assertTrue(ambiguous.getMessage().contains("Q1(alice)") &&
                    ambiguous.getMessage().contains("Q1(bob)"), ambiguous.getMessage());
      verify(f.manager, never()).setScheduleTask(anyString(), any(), any(AssetEntry.class), any());
   }

   @Test
   void theEmailActionGetsTheScheduleTabDefaults() throws Exception {
      Fixture f = new Fixture(BOB);

      f.service.create(daily("t"), f.wiz, "u/");

      ViewsheetAction action = storedAction(f);
      assertTrue(action.isMatchLayout());
      assertFalse(action.isExpandSelections());
      assertFalse(action.isOnlyDataComponents());
      assertTrue(action.isSaveToServerMatch());
      assertTrue(action.isMessageHtml());
      assertFalse(action.isDeliverLink());
      assertFalse(action.isCompressFile());
      // not ".pdf": an empty attachment name delivers a file with no base name
      assertEquals("Sales_Overview", action.getAttachmentName());
      assertEquals("bob@x.com", action.getFrom());
      String xml = xml(action);
      assertTrue(xml.contains("Sales_Overview"), xml);
      assertTrue(xml.contains("bob@x.com"), xml);
   }

   @Test
   void theAttachmentNameFollowsTheDashboardPathLikeTheScheduleTab() {
      AssetEntry entry = new AssetEntry(AssetRepository.GLOBAL_SCOPE, AssetEntry.Type.VIEWSHEET,
                                        "Examples/Sales Summary", null, ORG);

      assertEquals("Examples_Sales_Summary", WizScheduleService.attachmentName(entry));
   }

   @Test
   void anUpdateOfAnotherFieldKeepsTheStoredEmailAction() throws Exception {
      Fixture f = new Fixture(BOB);
      f.store(task("mine", BOB));

      f.service.update("mine", new TaskRequest(null, null, null, null, false, null, null, null,
                                               null, null, null, null, null, null, null, null,
                                               null, null, null, null), f.wiz, "u/");

      ArgumentCaptor<ScheduleTask> stored = ArgumentCaptor.forClass(ScheduleTask.class);
      verify(f.manager).setScheduleTask(anyString(), stored.capture(), any(), any());
      ViewsheetAction action = (ViewsheetAction) stored.getValue().getAction(0);
      assertEquals("a@x.com", action.getEmails());
      assertEquals("Excel", action.getFileFormat());
   }

   private static TaskRequest withBookmarks(String... names) {
      return new TaskRequest("t", "Sales/Overview", null, null, null, "daily", "08:30", null,
                             null, null, null, null, null, List.of("a@x.com"), null, null, null,
                             null, null, List.of(names));
   }

   private static ViewsheetAction storedAction(Fixture f) throws Exception {
      ArgumentCaptor<ScheduleTask> stored = ArgumentCaptor.forClass(ScheduleTask.class);
      verify(f.manager).setScheduleTask(anyString(), stored.capture(), isNull(), any());
      return (ViewsheetAction) stored.getValue().getAction(0);
   }

   /** The action as it is persisted, which is what the scheduler reads back. */
   private static String xml(ViewsheetAction action) {
      java.io.StringWriter out = new java.io.StringWriter();
      java.io.PrintWriter writer = new java.io.PrintWriter(out);
      action.writeXML(writer);
      writer.flush();
      return out.toString();
   }

   private static TaskRequest request(String frequency, String time, List<Integer> weekdays,
                                      Integer dayOfMonth, String runAt, String zone)
   {
      return new TaskRequest("t", "Sales/Overview", null, null, null, frequency, time, runAt,
                             weekdays, dayOfMonth, null, null, zone, List.of("a@x.com"), null,
                             null, null, null, null, null);
   }

   private static TaskRequest daily(String name) {
      return new TaskRequest(name, "Sales/Overview", null, "desc", null, "daily", "08:30", null,
                             null, null, null, null, null, List.of("a@x.com", "b@x.com"), null,
                             null, "subj", "body", null, null);
   }

   /** A stored task of {@code owner} with one dashboard email action. */
   private static ScheduleTask task(String name, IdentityID owner) {
      ScheduleTask task = new ScheduleTask();
      task.setName(name);
      task.setOwner(owner);
      task.setEnabled(true);
      task.addCondition(TimeCondition.at(8, 30, 0));
      ViewsheetAction action = new ViewsheetAction();
      AssetEntry entry = new AssetEntry(AssetRepository.GLOBAL_SCOPE, AssetEntry.Type.VIEWSHEET,
                                        "Sales/Overview", null, ORG);
      action.setViewsheet(entry.toIdentifier());
      action.setEmails("a@x.com");
      action.setFileFormat("Excel");
      task.addAction(action);
      return task;
   }

   private static final class Fixture {
      final SecurityEngine engine = mock(SecurityEngine.class);
      final AssetRepository assets = mock(AssetRepository.class);
      final ScheduleManager manager = mock(ScheduleManager.class);
      final ScheduleTaskService tasks = mock(ScheduleTaskService.class);
      /** What the Schedule tab offers: Home first (all-share, owned by the caller). */
      final List<VSBookmarkInfo> offered = new java.util.ArrayList<>();
      final WizScheduleService service = new WizScheduleService(
         new WizDashboardAccess(assets, engine), manager, tasks,
         new WizScheduleService.Environment() {
            @Override
            public VSBookmarkInfo[] bookmarks(AssetEntry entry, IdentityID caller) {
               return offered.toArray(new VSBookmarkInfo[0]);
            }

            @Override
            public String sender(IdentityID caller) {
               return "bob@x.com";
            }
         });
      final SRPrincipal wiz;

      Fixture(IdentityID caller) {
         offered.add(new VSBookmarkInfo(VSBookmark.HOME_BOOKMARK, VSBookmarkInfo.ALLSHARE, caller,
                                        false, 1L));
         wiz = new SRPrincipal(caller, new IdentityID[0], new String[0], caller.orgID, 42L);
         wiz.setProperty("wiz", "true");

         try {
            when(engine.checkPermission(any(), any(ResourceType.class), anyString(),
                                        any(ResourceAction.class))).thenReturn(true);
            when(assets.containsEntry(any(AssetEntry.class))).thenReturn(true);
            when(tasks.getVSMailFormatNames()).thenReturn(List.of("Excel", "PDF", "CSV"));
         }
         catch(Exception e) {
            throw new IllegalStateException(e);
         }
      }

      void store(ScheduleTask task) {
         when(manager.getScheduleTask(task.getTaskId())).thenReturn(task);
      }
   }
}
