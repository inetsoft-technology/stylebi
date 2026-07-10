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
package inetsoft.web.admin.schedule;
import inetsoft.sree.AnalyticRepository;
import inetsoft.sree.RepletEngine;
import inetsoft.sree.internal.SUtil;
import inetsoft.sree.schedule.*;
import inetsoft.sree.security.OrganizationManager;
import inetsoft.sree.security.ResourceAction;
import inetsoft.sree.security.ResourceType;
import inetsoft.uql.viewsheet.FileFormatInfo;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.security.Principal;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@Tag("core")
class ScheduleTaskServiceSanitizeTest {

   @Mock
   private AnalyticRepository analyticRepository;
   @Mock
   private RepletEngine repletEngine;
   @Mock
   private ScheduleService scheduleService;
   @Mock
   private Principal principal;

   private ScheduleTaskService service;

   @BeforeEach
   void setUp() {
      service = new ScheduleTaskService(analyticRepository, null, scheduleService, null, null, null, null);
   }

   /**
    * sanitizeConditions() now consults SUtil.isMultiTenant() unconditionally to gate time
    * ranges for non-site-admins in multi-tenant mode. Tests exercising the single-tenant
    * default (the common case) stub it here rather than each hitting the real, Spring-context-
    * dependent implementation.
    */
   private void invokeSanitizeConditions(ScheduleTask task, ScheduleTask original) {
      try(MockedStatic<SUtil> sutil = mockStatic(SUtil.class)) {
         sutil.when(SUtil::isMultiTenant).thenReturn(false);
         service.sanitizeConditions(task, original, principal);
      }
   }

   // ── sanitizeConditions ────────────────────────────────────────────────────

   @Test
   void sanitizeConditions_hasAllPermissions_conditionsUnchanged() {
      allowStartTime();
      allowTimeRange();

      ScheduleTask task = taskWithEveryDay(10, 0, 0);
      ScheduleTask original = taskWithEveryDay(9, 30, 0);

      invokeSanitizeConditions(task, original);

      TimeCondition tc = (TimeCondition) task.getCondition(0);
      assertEquals(10, tc.getHour());
   }

   @Test
   void sanitizeConditions_noStartTime_everyDaySameIndex_restoresTime() {
      denyStartTime();
      allowTimeRange();

      ScheduleTask task = taskWithEveryDay(10, 0, 0);
      ScheduleTask original = taskWithEveryDay(9, 30, 0);

      invokeSanitizeConditions(task, original);

      assertEquals(1, task.getConditionCount());
      TimeCondition tc = (TimeCondition) task.getCondition(0);
      assertEquals(TimeCondition.EVERY_DAY, tc.getType());
      assertEquals(9, tc.getHour());
      assertEquals(30, tc.getMinute());
      assertEquals(0, tc.getSecond());
   }

   @Test
   void sanitizeConditions_noStartTime_everyDayNoOriginalAtIndex_appliesDefaults() {
      denyStartTime();
      allowTimeRange();

      ScheduleTask task = taskWithEveryDay(10, 15, 0);
      ScheduleTask original = new ScheduleTask();   // no conditions

      invokeSanitizeConditions(task, original);

      assertEquals(1, task.getConditionCount());
      TimeCondition tc = (TimeCondition) task.getCondition(0);
      assertEquals(TimeCondition.EVERY_DAY, tc.getType());
      assertEquals(1, tc.getHour());
      assertEquals(30, tc.getMinute());
      assertEquals(0, tc.getSecond());
   }

   @Test
   void sanitizeConditions_noStartTime_everyDayTypeMismatch_appliesDefaults() {
      denyStartTime();
      allowTimeRange();

      // client sends EVERY_DAY but original at same index is EVERY_WEEK
      ScheduleTask task = taskWithEveryDay(10, 0, 0);
      ScheduleTask original = new ScheduleTask();
      TimeCondition origTc = new TimeCondition();
      origTc.setType(TimeCondition.EVERY_WEEK);
      origTc.setHour(8);
      origTc.setMinute(0);
      original.addCondition(origTc);

      invokeSanitizeConditions(task, original);

      // EVERY_DAY condition is kept (not removed), but time is defaulted
      assertEquals(1, task.getConditionCount());
      TimeCondition tc = (TimeCondition) task.getCondition(0);
      assertEquals(TimeCondition.EVERY_DAY, tc.getType());
      assertEquals(1, tc.getHour());
      assertEquals(30, tc.getMinute());
   }

   @Test
   void sanitizeConditions_noStartTime_atConditionSameIndex_restoresOriginal() {
      denyStartTime();
      allowTimeRange();

      Calendar cal = Calendar.getInstance();
      cal.set(2025, Calendar.JANUARY, 1, 9, 0, 0);
      Date date = cal.getTime();

      ScheduleTask task = taskWithAt(date);
      ScheduleTask original = taskWithAt(date);
      TimeCondition origTc = (TimeCondition) original.getCondition(0);

      invokeSanitizeConditions(task, original);

      assertEquals(1, task.getConditionCount());
      assertSame(origTc, task.getCondition(0));
   }

   @Test
   void sanitizeConditions_noStartTime_atConditionTypeMismatch_removesCondition() {
      denyStartTime();
      allowTimeRange();

      // client sends AT but original at index 0 is EVERY_DAY
      ScheduleTask task = taskWithAt(new Date());
      ScheduleTask original = taskWithEveryDay(9, 0, 0);

      invokeSanitizeConditions(task, original);

      assertEquals(0, task.getConditionCount());
   }

   @Test
   void sanitizeConditions_noStartTime_everyHourSameIndex_restoresOriginal() {
      denyStartTime();
      allowTimeRange();

      ScheduleTask task = taskWithEveryHour(10, 0);
      ScheduleTask original = taskWithEveryHour(9, 15);
      TimeCondition origTc = (TimeCondition) original.getCondition(0);

      invokeSanitizeConditions(task, original);

      assertEquals(1, task.getConditionCount());
      assertSame(origTc, task.getCondition(0));
   }

   @Test
   void sanitizeConditions_noStartTime_everyHourTypeMismatch_removesCondition() {
      denyStartTime();
      allowTimeRange();

      ScheduleTask task = taskWithEveryHour(10, 0);
      ScheduleTask original = taskWithEveryDay(9, 0, 0);

      invokeSanitizeConditions(task, original);

      assertEquals(0, task.getConditionCount());
   }

   @Test
   void sanitizeConditions_noStartTime_atConditionBeyondOriginal_removesCondition() {
      denyStartTime();
      allowTimeRange();

      ScheduleTask task = taskWithAt(new Date());
      ScheduleTask original = new ScheduleTask();   // empty

      invokeSanitizeConditions(task, original);

      assertEquals(0, task.getConditionCount());
   }

   @Test
   void sanitizeConditions_noTimeRange_sameTypeSameIndex_restoresTimeRange() {
      allowStartTime();
      denyTimeRange();

      ScheduleTask task = taskWithEveryDay(9, 0, 0);
      TimeCondition tc = (TimeCondition) task.getCondition(0);
      tc.setTimeRange(new TimeRange("client-range", "08:00", "09:00", false));

      ScheduleTask original = taskWithEveryDay(9, 0, 0);
      TimeRange origRange = new TimeRange("server-range", "10:00", "11:00", true);
      ((TimeCondition) original.getCondition(0)).setTimeRange(origRange);

      invokeSanitizeConditions(task, original);

      assertEquals(1, task.getConditionCount());
      TimeCondition result = (TimeCondition) task.getCondition(0);
      assertEquals(TimeCondition.EVERY_DAY, result.getType());
      assertEquals(9, result.getHour());   // hour unchanged since startTime is allowed
      assertEquals(origRange, result.getTimeRange());
   }

   @Test
   void sanitizeConditions_noTimeRange_typeMismatch_clearsTimeRange() {
      allowStartTime();
      denyTimeRange();

      ScheduleTask task = taskWithEveryDay(9, 0, 0);
      ((TimeCondition) task.getCondition(0))
         .setTimeRange(new TimeRange("client-range", "08:00", "09:00", false));

      ScheduleTask original = new ScheduleTask();
      TimeCondition origTc = new TimeCondition();
      origTc.setType(TimeCondition.EVERY_WEEK);
      original.addCondition(origTc);

      invokeSanitizeConditions(task, original);

      // EVERY_DAY condition is kept (not removed), but time range is cleared
      assertEquals(1, task.getConditionCount());
      TimeCondition result = (TimeCondition) task.getCondition(0);
      assertEquals(TimeCondition.EVERY_DAY, result.getType());
      assertNull(result.getTimeRange());
   }

   @Test
   void sanitizeConditions_orgAdminMultiTenant_stripsTimeRangeDespiteDefaultAllow() {
      allowStartTime();
      allowTimeRange();   // SCHEDULE_OPTION defaults to allow when unconfigured -- true even
                           // for an org admin who was never explicitly granted this action

      try(MockedStatic<SUtil> sutil = mockStatic(SUtil.class);
          MockedStatic<OrganizationManager> orgManagerStatic = mockStatic(OrganizationManager.class))
      {
         sutil.when(SUtil::isMultiTenant).thenReturn(true);
         OrganizationManager orgManager = mock(OrganizationManager.class);
         orgManagerStatic.when(OrganizationManager::getInstance).thenReturn(orgManager);
         when(orgManager.isSiteAdmin(principal)).thenReturn(false);   // org admin, not site admin

         ScheduleTask task = taskWithEveryDay(9, 0, 0);
         ((TimeCondition) task.getCondition(0))
            .setTimeRange(new TimeRange("client-range", "08:00", "09:00", false));

         ScheduleTask original = taskWithEveryDay(9, 0, 0);   // server had no time range

         service.sanitizeConditions(task, original, principal);

         TimeCondition result = (TimeCondition) task.getCondition(0);
         assertNull(result.getTimeRange(),
            "org admin in multi-tenant mode must not be able to smuggle a time range onto a " +
            "task even though SCHEDULE_OPTION:timeRange defaults to allow when unconfigured");
      }
   }

   // ── sanitizeAction ────────────────────────────────────────────────────────

   @Test
   void sanitizeAction_notViewsheetAction_noOp() {
      // BatchAction is an AbstractAction but not a ViewsheetAction
      BatchAction action = mock(BatchAction.class);
      service.sanitizeAction(action, null, principal);
      verifyNoInteractions(scheduleService);
   }

   @Test
   void sanitizeAction_hasAllPermissions_actionUnchanged() {
      allowNotificationEmail();
      allowSaveToDisk();
      allowEmailDelivery();

      ViewsheetAction action = vsActionWithSheet("vs1");
      action.setNotifications("user@example.com");
      action.setEmails("a@b.com");

      service.sanitizeAction(action, vsActionWithSheet("vs1"), principal);

      assertEquals("user@example.com", action.getNotifications());
      assertEquals("a@b.com", action.getEmails());
   }

   @Test
   void sanitizeAction_noNotificationEmail_sameSheet_origHadNotifications_restores() {
      denyNotificationEmail();
      allowSaveToDisk();
      allowEmailDelivery();

      ViewsheetAction original = vsActionWithSheet("vs1");
      original.setNotifications("admin@example.com");
      original.setNotifyError(true);
      original.setLink(true);

      ViewsheetAction action = vsActionWithSheet("vs1");
      action.setNotifications("hacker@evil.com");
      action.setNotifyError(false);
      action.setLink(false);

      service.sanitizeAction(action, original, principal);

      assertEquals("admin@example.com", action.getNotifications());
      assertTrue(action.isNotifyError());
      assertTrue(action.isLink());
   }

   @Test
   void sanitizeAction_noNotificationEmail_sameSheet_origHadNoNotifications_clears() {
      denyNotificationEmail();
      allowSaveToDisk();
      allowEmailDelivery();

      ViewsheetAction action = vsActionWithSheet("vs1");
      action.setNotifications("hacker@evil.com");
      action.setNotifyError(true);
      action.setLink(true);

      service.sanitizeAction(action, vsActionWithSheet("vs1"), principal);

      assertNull(action.getNotifications());
      assertFalse(action.isNotifyError());
      assertFalse(action.isLink());
   }

   @Test
   void sanitizeAction_noNotificationEmail_differentSheet_clears() {
      denyNotificationEmail();
      allowSaveToDisk();
      allowEmailDelivery();

      ViewsheetAction original = vsActionWithSheet("vs1");
      original.setNotifications("admin@example.com");
      original.setNotifyError(true);
      original.setLink(true);

      ViewsheetAction action = vsActionWithSheet("vs2");
      action.setNotifications("hacker@evil.com");
      action.setNotifyError(true);
      action.setLink(true);

      service.sanitizeAction(action, original, principal);

      assertNull(action.getNotifications());
      assertFalse(action.isNotifyError());
      assertFalse(action.isLink());
   }

   @Test
   void sanitizeAction_noEmailDelivery_sameSheet_origHadEmails_restoresAllFields() {
      allowNotificationEmail();
      allowSaveToDisk();
      denyEmailDelivery();

      ViewsheetAction original = vsActionWithSheet("vs1");
      original.setEmails("admin@example.com");
      original.setCCAddresses("cc@example.com");
      original.setSubject("Server subject");
      original.setMessage("Server body");

      ViewsheetAction action = vsActionWithSheet("vs1");
      action.setEmails("hacker@evil.com");
      action.setCCAddresses("hacker2@evil.com");
      action.setSubject("Hacker subject");
      action.setMessage("Hacker body");

      service.sanitizeAction(action, original, principal);

      assertEquals("admin@example.com", action.getEmails());
      assertEquals("cc@example.com", action.getCCAddresses());
      assertEquals("Server subject", action.getSubject());
      assertEquals("Server body", action.getMessage());
   }

   @Test
   void sanitizeAction_noEmailDelivery_sameSheet_origHadNoEmails_clearsAllFields() {
      allowNotificationEmail();
      allowSaveToDisk();
      denyEmailDelivery();

      ViewsheetAction action = vsActionWithSheet("vs1");
      action.setEmails("hacker@evil.com");
      action.setCCAddresses("cc@evil.com");
      action.setBCCAddresses("bcc@evil.com");
      action.setSubject("Subject");
      action.setMessage("Body");

      service.sanitizeAction(action, vsActionWithSheet("vs1"), principal);

      assertNull(action.getEmails());
      assertNull(action.getCCAddresses());
      assertNull(action.getBCCAddresses());
      assertNull(action.getSubject());
      assertNull(action.getMessage());
   }

   @Test
   void sanitizeAction_noEmailDelivery_differentSheet_clears() {
      allowNotificationEmail();
      allowSaveToDisk();
      denyEmailDelivery();

      ViewsheetAction original = vsActionWithSheet("vs1");
      original.setEmails("admin@example.com");
      original.setCCAddresses("cc@example.com");
      original.setSubject("Server subject");
      original.setAttachmentName("report.pdf");
      original.setCompressFile(true);

      ViewsheetAction action = vsActionWithSheet("vs2");
      action.setEmails("hacker@evil.com");
      action.setCCAddresses("hacker2@evil.com");
      action.setSubject("Hacker subject");
      action.setAttachmentName("evil.pdf");
      action.setCompressFile(true);

      service.sanitizeAction(action, original, principal);

      assertNull(action.getEmails());
      assertNull(action.getCCAddresses());
      assertNull(action.getSubject());
      assertNull(action.getAttachmentName());
      assertFalse(action.isCompressFile());
   }

   @Test
   void sanitizeAction_noSaveToDisk_differentSheet_clears() {
      allowNotificationEmail();
      denySaveToDisk();
      allowEmailDelivery();

      ViewsheetAction original = vsActionWithSheet("vs1");
      original.setFilePath(FileFormatInfo.EXPORT_TYPE_PDF, new ServerPathInfo("/reports/report.pdf"));
      original.setSaveToServerMatch(true);

      ViewsheetAction action = vsActionWithSheet("vs2");
      action.setFilePath(FileFormatInfo.EXPORT_TYPE_PDF, new ServerPathInfo("/evil/path.pdf"));
      action.setSaveToServerMatch(true);

      service.sanitizeAction(action, original, principal);

      assertEquals(0, action.getSaveFormats().length);
      assertFalse(action.isSaveToServerMatch());
   }

   @Test
   void sanitizeAction_noSaveToDisk_sameSheet_origHadSave_restoresSaveSettings() {
      allowNotificationEmail();
      denySaveToDisk();
      allowEmailDelivery();

      ViewsheetAction original = vsActionWithSheet("vs1");
      original.setFilePath(FileFormatInfo.EXPORT_TYPE_PDF, new ServerPathInfo("/reports/report.pdf"));
      original.setSaveToServerMatch(true);

      ViewsheetAction action = vsActionWithSheet("vs1");
      action.setFilePath(FileFormatInfo.EXPORT_TYPE_PDF, new ServerPathInfo("/evil/path.pdf"));

      service.sanitizeAction(action, original, principal);

      assertEquals("/reports/report.pdf", action.getFilePath(FileFormatInfo.EXPORT_TYPE_PDF));
      assertTrue(action.isSaveToServerMatch());
   }

   @Test
   void sanitizeAction_noSaveToDisk_sameSheet_origHadNoSave_clearsSaveSettings() {
      allowNotificationEmail();
      denySaveToDisk();
      allowEmailDelivery();

      ViewsheetAction action = vsActionWithSheet("vs1");
      action.setFilePath(FileFormatInfo.EXPORT_TYPE_PDF, new ServerPathInfo("/evil/path.pdf"));
      action.setSaveToServerMatch(true);
      action.setSaveToServerExpandSelections(true);

      service.sanitizeAction(action, vsActionWithSheet("vs1"), principal);

      assertEquals(0, action.getSaveFormats().length);
      assertFalse(action.isSaveToServerMatch());
      assertFalse(action.isSaveToServerExpandSelections());
   }

   // ── canDeleteInternalTask ────────────────────────────────────────────────
   //
   // Regression coverage: canDeleteInternalTask() must always defer to
   // RepletEngine.checkPermission() for SCHEDULE_TASK WRITE rather than granting any
   // Organization Administrator unconditional access. Bypassing checkPermission() let an org
   // admin save changes to the three internal system tasks
   // (__asset file backup__/__balance tasks__/__update assets dependencies__) that
   // ActionPermissionService.orgAdminActionExclusions specifically excludes them from.

   @Test
   void canDeleteInternalTask_nullTask_returnsFalse() {
      assertFalse(service.canDeleteInternalTask(null, principal));
      verifyNoInteractions(repletEngine);
   }

   @Test
   void canDeleteInternalTask_nullPrincipal_returnsFalse() {
      assertFalse(service.canDeleteInternalTask(internalTask("__asset file backup__"), null));
      verifyNoInteractions(repletEngine);
   }

   @Test
   void canDeleteInternalTask_noRepletEngine_returnsFalse() {
      when(analyticRepository.isWrapperFor(RepletEngine.class)).thenReturn(false);

      assertFalse(service.canDeleteInternalTask(internalTask("__asset file backup__"), principal));
   }

   @Test
   void canDeleteInternalTask_engineDenies_returnsFalseRegardlessOfRole() {
      wireRepletEngine();
      ScheduleTask task = internalTask("__asset file backup__");
      when(repletEngine.checkPermission(principal, ResourceType.SCHEDULE_TASK,
         "__asset file backup__", ResourceAction.WRITE)).thenReturn(false);

      assertFalse(service.canDeleteInternalTask(task, principal),
         "must defer to checkPermission() instead of granting access by role");

      verify(repletEngine).checkPermission(principal, ResourceType.SCHEDULE_TASK,
         "__asset file backup__", ResourceAction.WRITE);
   }

   @Test
   void canDeleteInternalTask_engineGrants_returnsTrue() {
      wireRepletEngine();
      ScheduleTask task = internalTask("some other internal task");
      when(repletEngine.checkPermission(principal, ResourceType.SCHEDULE_TASK,
         "some other internal task", ResourceAction.WRITE)).thenReturn(true);

      assertTrue(service.canDeleteInternalTask(task, principal));
   }

   private void wireRepletEngine() {
      when(analyticRepository.isWrapperFor(RepletEngine.class)).thenReturn(true);
      when(analyticRepository.unwrap(RepletEngine.class)).thenReturn(repletEngine);
   }

   private ScheduleTask internalTask(String name) {
      ScheduleTask task = new ScheduleTask();
      task.setName(name);
      return task;
   }

   // ── helpers ───────────────────────────────────────────────────────────────

   private ScheduleTask taskWithEveryDay(int hour, int minute, int second) {
      ScheduleTask task = new ScheduleTask();
      TimeCondition tc = new TimeCondition();
      tc.setType(TimeCondition.EVERY_DAY);
      tc.setHour(hour);
      tc.setMinute(minute);
      tc.setSecond(second);
      task.addCondition(tc);
      return task;
   }

   private ScheduleTask taskWithEveryHour(int hour, int minute) {
      ScheduleTask task = new ScheduleTask();
      TimeCondition tc = new TimeCondition();
      tc.setType(TimeCondition.EVERY_HOUR);
      tc.setHour(hour);
      tc.setMinute(minute);
      task.addCondition(tc);
      return task;
   }

   private ScheduleTask taskWithAt(Date date) {
      ScheduleTask task = new ScheduleTask();
      TimeCondition tc = TimeCondition.at(date);
      task.addCondition(tc);
      return task;
   }

   private ViewsheetAction vsActionWithSheet(String sheet) {
      ViewsheetAction action = new ViewsheetAction();
      action.setViewsheet(sheet);
      return action;
   }

   private void allowStartTime() {
      when(scheduleService.checkPermission(eq(principal),
         eq(ResourceType.SCHEDULE_OPTION), eq("startTime")))
         .thenReturn(true);
   }

   private void denyStartTime() {
      when(scheduleService.checkPermission(eq(principal),
         eq(ResourceType.SCHEDULE_OPTION), eq("startTime")))
         .thenReturn(false);
   }

   private void allowTimeRange() {
      when(scheduleService.checkPermission(eq(principal),
         eq(ResourceType.SCHEDULE_OPTION), eq("timeRange")))
         .thenReturn(true);
   }

   private void denyTimeRange() {
      when(scheduleService.checkPermission(eq(principal),
         eq(ResourceType.SCHEDULE_OPTION), eq("timeRange")))
         .thenReturn(false);
   }

   private void allowNotificationEmail() {
      when(scheduleService.checkPermission(eq(principal),
         eq(ResourceType.SCHEDULE_OPTION), eq("notificationEmail")))
         .thenReturn(true);
   }

   private void denyNotificationEmail() {
      when(scheduleService.checkPermission(eq(principal),
         eq(ResourceType.SCHEDULE_OPTION), eq("notificationEmail")))
         .thenReturn(false);
   }

   private void allowSaveToDisk() {
      when(scheduleService.checkPermission(eq(principal),
         eq(ResourceType.SCHEDULE_OPTION), eq("saveToDisk")))
         .thenReturn(true);
   }

   private void denySaveToDisk() {
      when(scheduleService.checkPermission(eq(principal),
         eq(ResourceType.SCHEDULE_OPTION), eq("saveToDisk")))
         .thenReturn(false);
   }

   private void allowEmailDelivery() {
      when(scheduleService.checkPermission(eq(principal),
         eq(ResourceType.SCHEDULE_OPTION), eq("emailDelivery")))
         .thenReturn(true);
   }

   private void denyEmailDelivery() {
      when(scheduleService.checkPermission(eq(principal),
         eq(ResourceType.SCHEDULE_OPTION), eq("emailDelivery")))
         .thenReturn(false);
   }
}
