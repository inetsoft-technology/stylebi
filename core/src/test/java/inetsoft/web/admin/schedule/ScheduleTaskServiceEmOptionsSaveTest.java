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

import inetsoft.sree.AnalyticRepository;
import inetsoft.sree.internal.SUtil;
import inetsoft.sree.schedule.*;
import inetsoft.sree.security.*;
import inetsoft.uql.util.Identity;
import inetsoft.web.admin.schedule.model.TaskOptionsPaneModel;
import org.junit.jupiter.api.*;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.junit.jupiter.api.extension.ExtendWith;

import java.security.Principal;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Regression coverage for the EM Options-pane save-path gap in Bug #77168 (Redmine journals
 * 317687 / 317894 / 318676 / 321657 / 322054).
 *
 * <ul>
 *    <li>{@code task-options-pane.component.ts} (EM) nulls {@code _executeAs} whenever
 *    {@code model.securityEnabled} is false, so an Options-pane save posts {@code idName = null}
 *    ({@code fireModelChanged()}) regardless of whether the user touched execute-as at all.</li>
 *    <li>{@code ScheduleTaskService.setTaskOptions} / {@code getNewIdentity} used to store that
 *    {@code null} unconditionally: {@code getIdentityId(null, principal)} returns {@code null},
 *    so the "keep old identity" guard added for Bug #77120
 *    ({@code Tool.equals(oldIdentity.getIdentityID(), newIdentityID)}) could never fire against a
 *    non-null placeholder id, and the placeholder was dropped to {@code null}.</li>
 * </ul>
 *
 * The fix makes {@code getNewIdentity} keep the old identity unchanged whenever the incoming
 * {@code idName} is {@code null} AND the server's own security state
 * ({@code ScheduleTaskService.isSecurityEnabled()}, backed by {@code SecurityProvider.isVirtual()}
 * -- the same mechanism the class already uses to produce {@code model.securityEnabled()} for the
 * client) says security is disabled, instead of trusting the client-supplied
 * {@code model.securityEnabled()} DTO field.
 */
@ExtendWith(MockitoExtension.class)
@Tag("core")
class ScheduleTaskServiceEmOptionsSaveTest {

   @Mock
   private AnalyticRepository analyticRepository;
   @Mock
   private ScheduleManager scheduleManager;
   @Mock
   private ScheduleService scheduleService;
   @Mock
   private SecurityProvider securityProvider;
   @Mock
   private Principal principal;

   private ScheduleTaskService service;

   @BeforeEach
   void setUp() {
      service = new ScheduleTaskService(
         analyticRepository, scheduleManager, scheduleService, null, securityProvider, null, null);
   }

   @Test
   void setTaskOptions_securityDisabledNullIdName_keepsUnresolvablePlaceholder() {
      // Arrange: a task whose execute-as is a kept, unresolved placeholder -- what
      // ScheduleTask.parseXML produces today for an unresolvable "g1" with security disabled,
      // per the #77120/#77168 fix (ScheduleTask.java's placeholder-creation branch, no longer
      // gated on isSecurityEnabled()).
      IdentityID id = new IdentityID("g1", "host-org");
      Group placeholder = new Group(id);
      ScheduleTask task = new ScheduleTask("task1");
      task.setIdentity(placeholder);

      when(securityProvider.isVirtual()).thenReturn(true);

      // What task-options-pane.component.ts actually posts for an Options-pane save with
      // security disabled: idName = null (component.ts nulls _executeAs, then
      // fireModelChanged() sets model.idName = this._executeAs), even though the user never
      // touched execute-as.
      Identity result = applyOptions(task, false, null, Identity.GROUP);

      assertSame(placeholder, result,
         "FIXED #77168: an EM Options-pane save with security disabled and a null idName must " +
         "not be treated as an explicit clear -- the stored execute-as placeholder has to " +
         "survive an unrelated edit, the same as the security-enabled 'unchanged idName' case.");
   }

   @Test
   void setTaskOptions_securityEnabledNullIdName_behavesAsBefore() {
      // The security-enabled path (the "Clear" action in the EM/portal UI, which is only
      // reachable when security is enabled) must keep falling through to null/owner -- this
      // fix must not change that already-correct behavior.
      ScheduleTask task = new ScheduleTask("task1");
      task.setIdentity(new Group(new IdentityID("g1", "host-org")));

      when(securityProvider.isVirtual()).thenReturn(false);

      Identity result = applyOptions(task, true, null, Identity.GROUP);

      assertNull(result,
         "security-enabled save: a null idName is an explicit clear and must still drop the " +
         "execute-as identity, as before this fix.");
   }

   @Test
   void setTaskOptions_securityDisabledNullIdName_noIdentityToKeep_staysNull() {
      // Security disabled, idName null, and the task never had an execute-as identity to begin
      // with -- must stay null, not error.
      ScheduleTask task = new ScheduleTask("task1");
      assertNull(task.getIdentity());

      when(securityProvider.isVirtual()).thenReturn(true);

      Identity result = applyOptions(task, false, null, Identity.GROUP);

      assertNull(result, "no identity to keep: stays null without error.");
   }

   @Test
   void setTaskOptions_securityDisabledRealIdName_changesIdentity() {
      // Security disabled but the user (or an automated caller) explicitly set execute-as to a
      // different, resolvable identity -- that intentional change must still take effect, not
      // get stuck on the old placeholder forever.
      ScheduleTask task = new ScheduleTask("task1");
      task.setIdentity(new Group(new IdentityID("g1", "host-org")));

      IdentityID newId = new IdentityID("g2", "host-org");
      Group newGroup = new Group(newId);

      OrganizationManager orgManager = mock(OrganizationManager.class);
      when(orgManager.getCurrentOrgID(principal)).thenReturn("host-org");

      TaskOptionsPaneModel model = TaskOptionsPaneModel.builder()
         .enabled(true)
         .deleteIfNotScheduledToRun(false)
         .securityEnabled(false)
         .owner("admin")
         .idName("g2")
         .idType(Identity.GROUP)
         .build();

      try(MockedStatic<SUtil> sutil = mockStatic(SUtil.class);
          MockedStatic<OrganizationManager> orgs = mockStatic(OrganizationManager.class))
      {
         orgs.when(OrganizationManager::getInstance).thenReturn(orgManager);
         sutil.when(() -> SUtil.getIdentity(eq(newId), eq(Identity.GROUP))).thenReturn(newGroup);
         sutil.when(SUtil::loadLocaleProperties).thenReturn(new Properties());

         service.setTaskOptions(model, task, principal);
      }

      assertSame(newGroup, task.getIdentity(),
         "an explicit, resolvable execute-as change must still be applied even when security " +
         "is disabled -- the keep-old-identity path only applies to a null idName.");
   }

   /**
    * @param modelSecurityEnabled the client-supplied {@code model.securityEnabled()} DTO value
    *                             -- cosmetic only, the fix deliberately does not trust it; the
    *                             server-side behavior is driven solely by the
    *                             {@code securityProvider.isVirtual()} stub each test sets up.
    */
   private Identity applyOptions(ScheduleTask task, boolean modelSecurityEnabled, String idName,
                                  int idType)
   {
      OrganizationManager orgManager = mock(OrganizationManager.class);
      when(orgManager.getCurrentOrgID(principal)).thenReturn("host-org");
      TaskOptionsPaneModel model = TaskOptionsPaneModel.builder()
         .enabled(true)
         .deleteIfNotScheduledToRun(false)
         .securityEnabled(modelSecurityEnabled)
         .owner("admin")
         .idName(idName)
         .idType(idType)
         .build();

      try(MockedStatic<SUtil> sutil = mockStatic(SUtil.class);
          MockedStatic<OrganizationManager> orgs = mockStatic(OrganizationManager.class))
      {
         orgs.when(OrganizationManager::getInstance).thenReturn(orgManager);
         sutil.when(() -> SUtil.getIdentity(any(), anyInt())).thenReturn(null);
         sutil.when(SUtil::loadLocaleProperties).thenReturn(new Properties());

         service.setTaskOptions(model, task, principal);
      }

      return task.getIdentity();
   }
}
