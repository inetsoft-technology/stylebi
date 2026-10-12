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
import inetsoft.uql.viewsheet.VSBookmarkInfo;
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
 * Regression coverage for Bug #78122: an EM Options-pane save that never touches Execute As
 * (e.g. only the description changes) must not drop the task's execute-as identity's own
 * viewsheet bookmarks, when security is disabled.
 *
 * <p>Companion to {@link ScheduleTaskServiceEmOptionsSaveTest} (Bug #77168), which covers the
 * same "security disabled always posts a null idName" quirk of
 * {@code task-options-pane.component.ts} for {@code task.getIdentity()} itself. This class covers
 * a second, independent symptom of the identical root cause: {@code setTaskOptions} used to
 * re-derive "the new execute-as identity" a second time, via
 * {@code getIdentityId(model.idName(), principal)} falling back to {@code task.getOwner()}, to
 * decide whether to filter a {@link ViewsheetAction}'s bookmarks down to what the new identity can
 * use. That second computation never got the #77168 fix (the early-return in
 * {@code getNewIdentity} that keeps a null {@code idName} with security disabled as a no-op), so
 * it always resolved to the task's owner and wrongly concluded the execute-as identity had
 * changed from the real identity to the owner -- dropping that real identity's own
 * {@code PRIVATE} (and any {@code GROUPSHARE}) bookmarks on every unrelated Options-pane save.</p>
 */
@ExtendWith(MockitoExtension.class)
@Tag("core")
class ScheduleTaskServiceOptionsSaveBookmarkSurvivalTest {

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
   void setTaskOptions_securityDisabledNullIdName_keepsExecuteAsIdentityBookmarks() {
      // Arrange: task owner "admin", execute-as identity "u1", one ViewsheetAction with a
      // PRIVATE bookmark owned by "u1" -- exactly the scenario diagnosed and reproduced for
      // Bug #78122.
      IdentityID ownerId = new IdentityID("admin", "host-org");
      IdentityID u1Id = new IdentityID("u1", "host-org");
      User u1 = new User(u1Id);

      ScheduleTask task = new ScheduleTask("task1");
      task.setOwner(ownerId);
      task.setIdentity(u1);

      ViewsheetAction action = new ViewsheetAction();
      action.setBookmarks(new String[] { "bm1" });
      action.setBookmarkUsers(new IdentityID[] { u1Id });
      action.setBookmarkTypes(new int[] { VSBookmarkInfo.PRIVATE });
      task.addAction(action);

      when(securityProvider.isVirtual()).thenReturn(true);

      OrganizationManager orgManager = mock(OrganizationManager.class);
      when(orgManager.getCurrentOrgID(principal)).thenReturn("host-org");

      // What task-options-pane.component.ts actually posts for an Options-pane save with
      // security disabled: idName = null (component.ts nulls _executeAs, then
      // fireModelChanged() sets model.idName = this._executeAs), even though the user only
      // changed the description and never touched Execute As.
      TaskOptionsPaneModel model = TaskOptionsPaneModel.builder()
         .enabled(true)
         .deleteIfNotScheduledToRun(false)
         .securityEnabled(false)
         .owner("admin")
         .idName(null)
         .idType(Identity.USER)
         .description("changed description only")
         .build();

      try(MockedStatic<SUtil> sutil = mockStatic(SUtil.class);
          MockedStatic<OrganizationManager> orgs = mockStatic(OrganizationManager.class);
          MockedStatic<ScheduleManager> scheduleMgr = mockStatic(ScheduleManager.class))
      {
         orgs.when(OrganizationManager::getInstance).thenReturn(orgManager);
         sutil.when(SUtil::loadLocaleProperties).thenReturn(new Properties());
         // Real-world value for disabled security (ScheduleManager.isSameGroup returns false
         // unconditionally whenever security is disabled) -- stubbed only to avoid this static
         // method's own reach into ConfigurationContext/SecurityEngine if the (now dead, for
         // this scenario) filter loop were ever reached.
         scheduleMgr.when(() -> ScheduleManager.isSameGroup(any(), any())).thenReturn(false);

         service.setTaskOptions(model, task, principal);
      }

      assertSame(u1, task.getIdentity(),
         "the execute-as identity itself did not change -- an unrelated Options-pane save with " +
         "security disabled must not clear or replace it (Bug #77168).");
      assertArrayEquals(new String[] { "bm1" }, action.getBookmarks(),
         "Bug #78122: the execute-as identity's own PRIVATE bookmark must survive an unrelated " +
         "Options-pane save with security disabled -- the bookmark filter must not mistake the " +
         "always-null idName for an actual identity change to the task owner.");
      assertArrayEquals(new IdentityID[] { u1Id }, action.getBookmarkUsers());
      assertArrayEquals(new int[] { VSBookmarkInfo.PRIVATE }, action.getBookmarkTypes());
   }
}
