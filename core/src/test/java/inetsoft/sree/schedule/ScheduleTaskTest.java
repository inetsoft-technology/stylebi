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

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import inetsoft.sree.SreeEnv;
import inetsoft.sree.internal.DataCycleManager;
import inetsoft.sree.security.*;
import inetsoft.test.*;
import inetsoft.uql.XPrincipal;
import inetsoft.uql.util.Identity;
import inetsoft.util.Tool;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.MockedStatic;
import org.slf4j.LoggerFactory;
import org.junit.jupiter.api.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.w3c.dom.Element;
import org.xml.sax.InputSource;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.PrintWriter;
import java.io.StringReader;
import java.io.StringWriter;
import java.lang.reflect.Field;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;
import java.util.Enumeration;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/*
 * Cases deferred - require integration context:
 *
 * [ScheduleTask] run(Principal) / doRun(Principal) - parallel action execution with timeout
 *             -> needs a real thread pool and live ScheduleAction implementations; NOT yet covered
 * [ScheduleTask] cancel() - runtimeTask delegation path
 *             -> needs run() executing concurrently in a separate thread; NOT yet covered
 * [ScheduleTask] writeXML / parseXML - XML round-trip serialization
 *             -> needs full DOM/Spring context; NOT yet covered
 * [ScheduleTask] equals(Object) - multi-field comparison across conditions and actions
 *             -> deferred: requires constructing fully equal tasks with real condition/action equals()
 * [ScheduleTask] getRetryTime(long) - lastRun > 0 branch uses TimeCondition.getRetryTime(time, lastRun)
 *             -> needs the shared mock ScheduleStatusDao to return a status with lastScheduledStartTime > 0;
 *                requires reset(scheduleStatusDao) to avoid polluting testCheckRetryTime; NOT yet covered
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, ScheduleTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
public class ScheduleTaskTest {
   private ScheduleTask scheduleTask;
   @Autowired SecurityEngine securityEngine;

   @Test
   void getTaskTimeoutReturnsConfiguredValue() {
      try(MockedStatic<SreeEnv> sreeEnv = mockStatic(SreeEnv.class)) {
         sreeEnv.when(() -> SreeEnv.getProperty("schedule.task.timeout")).thenReturn("1500");

         assertEquals(1500L, ScheduleTask.getTaskTimeout(),
                      "A numeric schedule.task.timeout must be used as-is");
      }
   }

   @ParameterizedTest(name = "schedule.task.timeout [{0}] falls back to the default")
   @NullSource
   @ValueSource(strings = { "abc", "", " ", "600000ms", "600,000", "1e5" })
   void getTaskTimeoutFallsBackWhenValueIsNotANumber(String propertyValue) {
      try(MockedStatic<SreeEnv> sreeEnv = mockStatic(SreeEnv.class)) {
         sreeEnv.when(() -> SreeEnv.getProperty("schedule.task.timeout"))
            .thenReturn(propertyValue);

         assertEquals(ScheduleTask.DEFAULT_TASK_TIMEOUT, ScheduleTask.getTaskTimeout(),
                      "A missing or non-numeric schedule.task.timeout must fall back to " +
                      "DEFAULT_TASK_TIMEOUT instead of throwing");
      }
   }

   @Test
   void getTaskTimeoutWarnsNamingThePropertyAndTheOffendingValue() {
      // the warning is half the fix: without it the fallback is silent and an admin has no way
      // to attribute a task running on the default timeout to the value they mistyped
      Logger logger = (Logger) LoggerFactory.getLogger(ScheduleTask.class);
      ListAppender<ILoggingEvent> appender = new ListAppender<>();
      appender.start();
      logger.addAppender(appender);

      try {
         try(MockedStatic<SreeEnv> sreeEnv = mockStatic(SreeEnv.class)) {
            sreeEnv.when(() -> SreeEnv.getProperty("schedule.task.timeout")).thenReturn("abc");

            ScheduleTask.getTaskTimeout();
         }

         assertEquals(1, appender.list.size(),
                      "the fallback must report itself exactly once");

         ILoggingEvent event = appender.list.get(0);
         assertEquals(Level.WARN, event.getLevel(), "the fallback must be reported at WARN");

         String message = event.getFormattedMessage();
         assertTrue(message.contains("schedule.task.timeout"),
                    "the warning must name the property so the cause is discoverable: " + message);
         assertTrue(message.contains("abc"),
                    "the warning must quote the offending value: " + message);
      }
      finally {
         logger.detachAppender(appender);
      }
   }

   @Test
   void getTaskTimeoutDefaultTracksShippedProperty() {
      // read the shipped default rather than a literal, so that editing defaults.properties
      // without editing DEFAULT_TASK_TIMEOUT fails here instead of silently changing the
      // timeout that applies whenever the property is unreadable
      String shipped = SreeEnv.getDefaultProperties().getProperty("schedule.task.timeout");

      assertNotNull(shipped, "defaults.properties must ship a schedule.task.timeout value");
      assertEquals(Long.parseLong(shipped), ScheduleTask.DEFAULT_TASK_TIMEOUT,
                   "DEFAULT_TASK_TIMEOUT must track schedule.task.timeout in " +
                   "defaults.properties so the fallback does not silently change the timeout");
   }

   @Test
   void testCheckRetryTime() {
      scheduleTask = spy(ScheduleTask.class);
      TimeCondition mockTimeCondition = mock(TimeCondition.class);
      when(mockTimeCondition.check(anyLong())).thenReturn(true);
      when(mockTimeCondition.getRetryTime(anyLong())).thenReturn(1L);

      scheduleTask.addCondition(mockTimeCondition);
      scheduleTask.setCondition(0, mockTimeCondition);

      ViewsheetAction spyVSAction = spy(ViewsheetAction.class);
      spyVSAction.setViewsheet("1^128^__NULL__^f1/vs1^host-org");
      scheduleTask.addAction(spyVSAction);
      scheduleTask.setAction(0, spyVSAction);

      scheduleTask.setName("task1");
      assertEquals("task1", scheduleTask.getName());

      //1. check
      assertTrue(scheduleTask.check(123658));

      //2. check retry time
      scheduleTask.setEnabled(false);
      assertFalse(scheduleTask.isEnabled());
      assertEquals(-1, scheduleTask.getRetryTime(1234568));

      scheduleTask.setEnabled(true);
      //no last run time
      assertEquals(1, scheduleTask.getRetryTime(1234568));
      // have last runtime  ??

      //3. check hasNextRuntime
      assertTrue(scheduleTask.hasNextRuntime(1234568));
      //completionCondition
      scheduleTask.removeCondition(0);
      CompletionCondition mockCompletionCondition = mock(CompletionCondition.class);
      scheduleTask.addCondition(mockCompletionCondition);
      scheduleTask.setCondition(0, mockCompletionCondition);

      assertFalse(scheduleTask.hasNextRuntime(1234568));
      when(scheduleTask.hasNextRuntime(anyLong())).thenReturn(true);
   }

   @Test
   void testRemoveActionContiditon() {
      scheduleTask = createBasicScheduleTask("task1");

      CompletionCondition mockCompletionCondition = mock(CompletionCondition.class);
      when(mockCompletionCondition.getTaskName()).thenReturn("task1");
      scheduleTask.addCondition(mockCompletionCondition);
      scheduleTask.setCondition(1, mockCompletionCondition);

      BatchAction batchAction = mock(BatchAction.class);
      scheduleTask.addAction(batchAction);
      scheduleTask.setAction(1, batchAction);

      scheduleTask.setName("task1");

      //0. check setComplete
      scheduleTask.setComplete("task1", true);
      CompletionCondition completionCondition = (CompletionCondition)scheduleTask.getCondition(1);
      assertFalse(completionCondition.check(anyLong()));

      //1. check remove condition
      scheduleTask.removeCondition(-1);
      assertEquals(2, scheduleTask.getConditionCount());

      scheduleTask.removeCondition(1);
      assertEquals(1, scheduleTask.getConditionCount());

      //2. check remove action
      scheduleTask.removeAction(0);
      assertEquals(1, scheduleTask.getActionCount());
   }

   @Test
   void checkEqual() {
      scheduleTask = createBasicScheduleTask("task1");

      ScheduleTask scheduleTask1 = scheduleTask.clone();
      assertEquals(scheduleTask.getConditionCount(), scheduleTask1.getConditionCount());

      ScheduleTask scheduleTask2 = new ScheduleTask();
      scheduleTask.copyTo(scheduleTask2);
      assertEquals(scheduleTask.getActionCount(), scheduleTask2.getActionCount());
   }

   @Test
   void testOtherSetGetMethod() {
      IdentityID testUser = new IdentityID("testUser", "testOrg");
      FSUser testFSUser = new FSUser(testUser);

      SecurityProvider securityProvider = mock(SecurityProvider.class);
      when(securityProvider.getUser(eq(testUser))).thenReturn(testFSUser);
      when(securityProvider.getOrganizationIDs()).thenReturn(new String[] { "host-org", "testOrg" });
      when(securityEngine.getSecurityProvider()).thenReturn(securityProvider);

      scheduleTask = createBasicScheduleTask("task1");

      scheduleTask.setDeleteIfNoMoreRun(true);
      assertTrue(scheduleTask.isDeleteIfNoMoreRun());

      scheduleTask.setDurable(true);
      assertTrue(scheduleTask.isDurable());

      scheduleTask.setStartDate(toDate("2025-01-01T00:00:00"));
      assertEquals(toDate("2025-01-01T00:00:00"), scheduleTask.getStartDate());

      scheduleTask.setEndDate(toDate("2025-12-31T23:59:59"));
      assertEquals(toDate("2025-12-31T23:59:59"), scheduleTask.getEndDate());

      scheduleTask.setLocale("en_US");
      assertEquals("en_US", scheduleTask.getLocale());

      scheduleTask.setUser(testUser);
      assertEquals(testUser.getName(), scheduleTask.getUser());

      scheduleTask.setDescription("Test Schedule Task");
      assertEquals("Test Schedule Task", scheduleTask.getDescription());

      assertEquals("task1", scheduleTask.toView(false));
      scheduleTask.setOwner(testUser);
      assertEquals("testUser:task1", scheduleTask.toView(true));
      assertEquals("testUser:task1", scheduleTask.toView(true, true));
   }

   // --- getTaskId ---

   @Test
   void getTaskId_normalTask_noOwner_returnsName() {
      ScheduleTask task = new ScheduleTask("my-task");
      assertEquals("my-task", task.getTaskId());
   }

   @Test
   void getTaskId_normalTask_withOwner_returnsOwnerKeyColonName() {
      ScheduleTask task = new ScheduleTask("my-task");
      IdentityID owner = new IdentityID("alice", "org1");
      task.setOwner(owner);
      assertEquals(owner.convertToKey() + ":my-task", task.getTaskId());
   }

   @Test
   void getTaskId_cycleTask_returnsOwnerKeyDoubleUnderscoreName() {
      IdentityID owner = new IdentityID("alice", "org1");
      ScheduleTask task = new ScheduleTask("my-task", ScheduleTask.Type.CYCLE_TASK);
      task.setOwner(owner);
      assertEquals(owner.convertToKey() + "__my-task", task.getTaskId());
   }

   @Test
   void getTaskId_internalTask_returnsName() {
      ScheduleTask task = new ScheduleTask("internal-task", ScheduleTask.Type.INTERNAL_TASK);
      assertEquals("internal-task", task.getTaskId());
   }

   @Test
   void getTaskId_invalidatedBySetName() {
      IdentityID owner = new IdentityID("alice", "org1");
      ScheduleTask task = new ScheduleTask("original");
      task.setOwner(owner);
      assertEquals(owner.convertToKey() + ":original", task.getTaskId());
      task.setName("renamed");
      assertEquals(owner.convertToKey() + ":renamed", task.getTaskId());
   }

   // --- check ---

   @Test
   void check_allConditionsFalse_returnsFalse() {
      ScheduleTask task = new ScheduleTask("task");
      TimeCondition c1 = mock(TimeCondition.class);
      TimeCondition c2 = mock(TimeCondition.class);
      when(c1.check(anyLong())).thenReturn(false);
      when(c2.check(anyLong())).thenReturn(false);
      task.addCondition(c1);
      task.addCondition(c2);
      assertFalse(task.check(1000L));
   }

   @Test
   void check_allConditionsAlwaysEvaluated_evenAfterFirstTrue() {
      // The loop does not short-circuit so CompletionConditions get their state reset on every check call.
      ScheduleTask task = new ScheduleTask("task");
      TimeCondition c1 = mock(TimeCondition.class);
      TimeCondition c2 = mock(TimeCondition.class);
      when(c1.check(anyLong())).thenReturn(true);
      when(c2.check(anyLong())).thenReturn(false);
      task.addCondition(c1);
      task.addCondition(c2);
      assertTrue(task.check(1000L));
      verify(c1, times(1)).check(anyLong());
      verify(c2, times(1)).check(anyLong()); // must be called even though c1 already returned true
   }

   // --- getRetryTime ---

   @Test
   void getRetryTime_allConditionsReturnNegative_returnsNegative() {
      ScheduleTask task = new ScheduleTask("task");
      TimeCondition cond = mock(TimeCondition.class);
      when(cond.getRetryTime(anyLong())).thenReturn(-1L);
      task.addCondition(cond);
      assertEquals(-1L, task.getRetryTime(1000L));
   }

   // --- setComplete ---

   @Test
   void setComplete_whenTaskIsRunning_doesNotUpdateCondition() throws Exception {
      ScheduleTask task = new ScheduleTask("task");
      CompletionCondition cc = new CompletionCondition("parent-task");
      task.addCondition(cc);

      // simulate task in-progress without calling run()
      Field runningField = ScheduleTask.class.getDeclaredField("running");
      runningField.setAccessible(true);
      runningField.setBoolean(task, true);

      task.setComplete("parent-task", true);

      // cc.setComplete(true) was not called because of the early return; check() returns default false
      assertFalse(cc.check(0L));
   }

   // --- renameDependency ---

   @Test
   void renameDependency_existingDependency_isUpdated() {
      ScheduleTask task = new ScheduleTask("task");
      task.addCondition(new CompletionCondition("old-parent"));

      task.renameDependency("old-parent", "new-parent");

      Enumeration<String> deps = task.getDependency();
      assertEquals("new-parent", deps.nextElement());
      assertFalse(deps.hasMoreElements());
   }

   @Test
   void renameDependency_nonExistentName_doesNotChangeDependency() {
      ScheduleTask task = new ScheduleTask("task");
      task.addCondition(new CompletionCondition("parent"));

      task.renameDependency("no-such-task", "new-name");

      Enumeration<String> deps = task.getDependency();
      assertEquals("parent", deps.nextElement());
      assertFalse(deps.hasMoreElements());
   }

   // --- addCondition / removeCondition dependency tracking ---

   @Test
   void addAndRemoveCompletionCondition_dependencyTracking() {
      ScheduleTask task = new ScheduleTask("task");
      CompletionCondition cc = new CompletionCondition("parent-task");
      task.addCondition(cc);

      Enumeration<String> depsAfterAdd = task.getDependency();
      assertTrue(depsAfterAdd.hasMoreElements());
      assertEquals("parent-task", depsAfterAdd.nextElement());

      task.removeCondition(0);
      assertFalse(task.getDependency().hasMoreElements());
   }

   // --- parseXML(elem, isSiteAdminImport) : org-copy rewrite of CompletionCondition ---

   /*
    * Bug: after copying an organization, a task's "Run After" completion condition pointing
    * at a Data Cycle task showed up blank. Root cause: updateConditionTaskPath() located the
    * owner/task-name split with path.indexOf(":"), but cycle-task ids use "<owner>__<name>"
    * (ScheduleTask.getTaskId(), Type.CYCLE_TASK branch) and the name itself is
    * "DataCycle Task: Cycle1", which contains its own colon. The first colon found was the one
    * inside the task name, so the rewrite dropped "__DataCycle Task" entirely, producing a
    * taskName that could never match a real task in the new org.
    */
   @Test
   void parseXML_siteAdminImport_cycleTaskCompletionCondition_rewritesOwnerOrgPreservingTaskName()
      throws Exception
   {
      String sourceOrg = "source-org";
      String targetOrg = "target-org";
      String cycleTaskId = new IdentityID(XPrincipal.SYSTEM, sourceOrg).convertToKey() +
         "__" + DataCycleManager.TASK_PREFIX + "Cycle1";

      String xml = "<Task name=\"task1\" owner=\"" +
         Tool.escape(new IdentityID("admin", sourceOrg).convertToKey()) + "\" enabled=\"true\">" +
         "<Condition type=\"Completion\" task=\"" + Tool.escape(cycleTaskId) + "\"/>" +
         "</Task>";

      Element elem = parseTaskXml(xml);
      ScheduleTask task = new ScheduleTask();

      OrganizationManager.runInOrgScope(targetOrg, () -> {
         task.parseXML(elem, true);
         return null;
      });

      CompletionCondition cc = (CompletionCondition) task.getCondition(0);
      String expected = new IdentityID(XPrincipal.SYSTEM, targetOrg).convertToKey() +
         "__" + DataCycleManager.TASK_PREFIX + "Cycle1";
      assertEquals(expected, cc.getTaskName());
   }

   @Test
   void parseXML_siteAdminImport_normalTaskCompletionCondition_rewritesOwnerOrg() throws Exception {
      String sourceOrg = "source-org";
      String targetOrg = "target-org";
      String parentTaskId = new IdentityID("admin", sourceOrg).convertToKey() + ":parent task";

      String xml = "<Task name=\"task1\" owner=\"" +
         Tool.escape(new IdentityID("admin", sourceOrg).convertToKey()) + "\" enabled=\"true\">" +
         "<Condition type=\"Completion\" task=\"" + Tool.escape(parentTaskId) + "\"/>" +
         "</Task>";

      Element elem = parseTaskXml(xml);
      ScheduleTask task = new ScheduleTask();

      OrganizationManager.runInOrgScope(targetOrg, () -> {
         task.parseXML(elem, true);
         return null;
      });

      CompletionCondition cc = (CompletionCondition) task.getCondition(0);
      String expected = new IdentityID("admin", targetOrg).convertToKey() + ":parent task";
      assertEquals(expected, cc.getTaskName());
   }

   // --- Bug #77120, unresolvable execute-as identities ---

   private static final IdentityID EXEC_AS = new IdentityID("g1", "host-org");

   @ParameterizedTest(name = "unresolvable execute-as of type {0} is kept as a placeholder")
   @ValueSource(ints = { Identity.USER, Identity.GROUP, Identity.ROLE })
   void parseXML_unresolvableIdentity_keptAsPlaceholderAndWarned(int type) throws Exception {
      Logger logger = (Logger) LoggerFactory.getLogger(ScheduleTask.class);
      ListAppender<ILoggingEvent> appender = new ListAppender<>();
      appender.start();
      logger.addAppender(appender);

      try {
         ScheduleTask task = parseWithProvider(executeAsXml(type), mock(SecurityProvider.class), true);

         Identity identity = task.getIdentity();
         assertNotNull(identity, "a lookup miss must not drop the execute-as identity");
         assertEquals(type, identity.getType());
         assertEquals(EXEC_AS, identity.getIdentityID());
         assertTrue(appender.list.stream().anyMatch(e -> e.getLevel() == Level.WARN &&
            e.getFormattedMessage().contains(EXEC_AS.toString()) &&
            e.getFormattedMessage().contains("t1")),
            "the WARN must name the task and the unresolved identity");
      }
      finally {
         logger.detachAppender(appender);
      }
   }

   @ParameterizedTest(name = "unresolvable execute-as of type {0} survives a save")
   @ValueSource(ints = { Identity.USER, Identity.GROUP, Identity.ROLE })
   void writeXML_unresolvableIdentity_roundTripsAndResolvesOnceAvailable(int type)
      throws Exception
   {
      ScheduleTask task = parseWithProvider(executeAsXml(type), mock(SecurityProvider.class), true);
      StringWriter out = new StringWriter();
      task.writeXML(new PrintWriter(out));
      String saved = out.toString();

      assertTrue(saved.contains("idname=\"" + Tool.escape(EXEC_AS.convertToKey()) + "\""), saved);
      assertTrue(saved.contains("idtype=\"" + type + "\""), saved);

      Identity resolved = type == Identity.GROUP ? new Group(EXEC_AS) :
         type == Identity.ROLE ? new Role(EXEC_AS) : new User(EXEC_AS);
      ScheduleTask reparsed = parseWithProvider(saved, resolvingProvider(resolved), true);

      assertSame(resolved, reparsed.getIdentity(),
                 "the saved reference must bind to the identity once the provider knows it again");
   }

   @Test
   void parseXML_resolvableIdentity_keepsProviderIdentity() throws Exception {
      Group group = new Group(EXEC_AS);
      ScheduleTask task = parseWithProvider(executeAsXml(Identity.GROUP), resolvingProvider(group), true);

      assertSame(group, task.getIdentity());
   }

   @Test
   void parseXML_noIdentity_staysNull() throws Exception {
      ScheduleTask task = parseWithProvider(
         "<Task name=\"t1\" owner=\"admin~;~host-org\" enabled=\"true\"/>",
         mock(SecurityProvider.class), true);

      assertNull(task.getIdentity());
   }

   @Test
   void parseXML_securityDisabled_unresolvableIdentity_keepsPlaceholderIdentity() throws Exception {
      // Bug #77168: dropping the placeholder here (identity == null) is what silently and
      // permanently erased execute-as on the very next writeXML(), since writeXML's guard is
      // bare "if(identity != null)". The placeholder must now be kept regardless of security
      // state so it round-trips; the task still effectively runs as its owner, but that must
      // now come from the principal-builder callers (ScheduleTaskJob/ClusterJobStore/
      // JobCompletionListener) falling back to the owner while security is disabled, not from
      // parseXML silently dropping the reference.
      ScheduleTask task = parseWithProvider(executeAsXml(Identity.GROUP), mock(SecurityProvider.class), false);

      Identity identity = task.getIdentity();
      assertNotNull(identity,
                    "with security disabled, an unresolvable execute-as must still be kept as " +
                    "a placeholder so it is not lost on the next save");
      assertEquals(Identity.GROUP, identity.getType());
      assertEquals(EXEC_AS, identity.getIdentityID());
   }

   @Test
   void run_securityEnabled_unresolvableIdentity_failsClosed() throws Throwable {
      ScheduleTask task = new ScheduleTask("t1");
      ScheduleAction action = mock(ScheduleAction.class);
      task.addAction(action);
      task.setIdentity(new Group(EXEC_AS));

      withSecurity(mock(SecurityProvider.class), true, () -> {
         IllegalStateException ex = assertThrows(IllegalStateException.class, () -> task.run(null));
         assertTrue(ex.getMessage().contains(EXEC_AS.toString()), ex.getMessage());
         return null;
      });

      verify(action, never()).run(any());
      assertFalse(task.isRunning(), "a refused run must not leave the task marked as running");
   }

   @Test
   void run_securityEnabled_resolvableIdentity_runs() throws Throwable {
      Group group = new Group(EXEC_AS);
      ScheduleTask task = new ScheduleTask("t1");
      task.setIdentity(group);

      withSecurity(resolvingProvider(group), true, () -> {
         assertDoesNotThrow(() -> task.run(null));
         return null;
      });
   }

   @Test
   void run_securityDisabled_unresolvableIdentity_runs() throws Throwable {
      ScheduleTask task = new ScheduleTask("t1");
      task.setIdentity(new Group(EXEC_AS));

      withSecurity(mock(SecurityProvider.class), false, () -> {
         assertDoesNotThrow(() -> task.run(null));
         return null;
      });
   }

   // --- Bug #77167, a site-admin import moves the execute-as identity to the importing org ---

   private static final IdentityID SOURCE_EXEC_AS = new IdentityID("x", "source-org");
   private static final IdentityID TARGET_EXEC_AS = new IdentityID("x", "target-org");

   @ParameterizedTest(name = "site-admin import moves execute-as of type {0} to the target org")
   @ValueSource(ints = { Identity.USER, Identity.GROUP, Identity.ROLE })
   void parseXML_siteAdminImport_executeAsMovesToTargetOrg(int type) throws Exception {
      ScheduleTask task = importTask(importXml(SOURCE_EXEC_AS, type),
                                     knownProvider(SOURCE_EXEC_AS, TARGET_EXEC_AS), true);

      assertEquals(new IdentityID("admin", "target-org"), task.getOwner());
      assertNotNull(task.getIdentity());
      assertEquals(type, task.getIdentity().getType());
      assertEquals(TARGET_EXEC_AS, task.getIdentity().getIdentityID(),
                   "the execute-as identity must follow the owner into the importing org");
   }

   @ParameterizedTest(name = "site-admin import keeps a target-org placeholder of type {0}")
   @ValueSource(ints = { Identity.USER, Identity.GROUP, Identity.ROLE })
   void parseXML_siteAdminImport_missingInTargetOrg_placeholderInTargetOrg(int type)
      throws Exception
   {
      // only the source org has the identity, it must not bind to it
      ScheduleTask task = importTask(importXml(SOURCE_EXEC_AS, type),
                                     knownProvider(SOURCE_EXEC_AS), true);

      assertNotNull(task.getIdentity());
      assertEquals(type, task.getIdentity().getType());
      assertEquals(TARGET_EXEC_AS, task.getIdentity().getIdentityID(),
                   "the unresolved placeholder must be scoped to the importing org");
   }

   @Test
   void parseXML_siteAdminImport_globalRoleStaysGlobal() throws Exception {
      IdentityID global = new IdentityID("x", null);
      ScheduleTask task = importTask(importXml(global, Identity.ROLE), knownProvider(global), true);

      assertNotNull(task.getIdentity());
      assertEquals(global, task.getIdentity().getIdentityID());
   }

   @ParameterizedTest(name = "non-site-admin import keeps execute-as of type {0} unchanged")
   @ValueSource(ints = { Identity.USER, Identity.GROUP, Identity.ROLE })
   void parseXML_nonSiteAdminImport_executeAsUnchanged(int type) throws Exception {
      ScheduleTask task = importTask(importXml(SOURCE_EXEC_AS, type),
                                     knownProvider(SOURCE_EXEC_AS, TARGET_EXEC_AS), false);

      assertNotNull(task.getIdentity());
      assertEquals(SOURCE_EXEC_AS, task.getIdentity().getIdentityID());
   }

   private static String importXml(IdentityID executeAs, int type) {
      return "<Task name=\"t1\" owner=\"" +
         Tool.escape(new IdentityID("admin", "source-org").convertToKey()) +
         "\" enabled=\"true\" idname=\"" + Tool.escape(executeAs.convertToKey()) +
         "\" idtype=\"" + type + "\"/>";
   }

   private static SecurityProvider knownProvider(IdentityID... ids) {
      SecurityProvider provider = mock(SecurityProvider.class);

      for(IdentityID id : ids) {
         when(provider.getUser(id)).thenReturn(new User(id));
         when(provider.getGroup(id)).thenReturn(new Group(id));
         when(provider.getRole(id)).thenReturn(new Role(id));
      }

      return provider;
   }

   private ScheduleTask importTask(String xml, SecurityProvider provider, boolean siteAdmin)
      throws Exception
   {
      Element elem = parseTaskXml(xml);
      ScheduleTask task = new ScheduleTask();
      withSecurity(provider, true, () -> OrganizationManager.runInOrgScope("target-org", () -> {
         task.parseXML(elem, siteAdmin);
         return null;
      }));
      return task;
   }

   private static String executeAsXml(int type) {
      return "<Task name=\"t1\" owner=\"admin~;~host-org\" enabled=\"true\" idname=\"" +
         Tool.escape(EXEC_AS.convertToKey()) + "\" idtype=\"" + type + "\"/>";
   }

   private static SecurityProvider resolvingProvider(Identity identity) {
      SecurityProvider provider = mock(SecurityProvider.class);
      when(provider.getUser(EXEC_AS)).thenReturn(identity instanceof User ? (User) identity : null);
      when(provider.getGroup(EXEC_AS)).thenReturn(identity instanceof Group ? (Group) identity : null);
      when(provider.getRole(EXEC_AS)).thenReturn(identity instanceof Role ? (Role) identity : null);
      return provider;
   }

   private ScheduleTask parseWithProvider(String xml, SecurityProvider provider, boolean enabled)
      throws Exception
   {
      Element elem = parseTaskXml(xml);
      ScheduleTask task = new ScheduleTask();
      withSecurity(provider, enabled, () -> {
         task.parseXML(elem);
         return null;
      });
      return task;
   }

   private void withSecurity(SecurityProvider provider, boolean enabled,
                             java.util.concurrent.Callable<Void> body) throws Exception
   {
      doReturn(provider).when(securityEngine).getSecurityProvider();
      doReturn(enabled).when(securityEngine).isSecurityEnabled();

      try {
         body.call();
      }
      finally {
         // back to a plain spy; restoring with doCallRealMethod() leaves isSecurityEnabled()
         // stubbed, which breaks a later when(securityEngine.getSecurityProvider())
         reset(securityEngine);
      }
   }

   private static Element parseTaskXml(String xml) throws Exception {
      DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
      return factory.newDocumentBuilder()
         .parse(new InputSource(new StringReader(xml)))
         .getDocumentElement();
   }

   private ScheduleTask createBasicScheduleTask(String taskName) {
      ScheduleTask scheduleTask = new ScheduleTask(taskName);
      TimeCondition mockTimeCondition = mock(TimeCondition.class);
      scheduleTask.addCondition(mockTimeCondition);
      scheduleTask.setCondition(0, mockTimeCondition);

      ViewsheetAction spyVSAction = spy(ViewsheetAction.class);
      spyVSAction.setViewsheet("1^128^__NULL__^f1/vs1^host-org");
      scheduleTask.addAction(spyVSAction);
      scheduleTask.setAction(0, spyVSAction);

      return scheduleTask;
   }

   private Date toDate(String localDateTime) {
      return Date.from(LocalDateTime.parse(localDateTime)
                          .atZone(ZoneId.systemDefault())  //  ZoneId.systemDefault()
                          .toInstant());
   }
}
