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
package inetsoft.web.viewsheet.service;

import inetsoft.analytic.composition.ViewsheetService;
import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.sree.internal.SUtil;
import inetsoft.sree.internal.cluster.Cluster;
import inetsoft.sree.schedule.ScheduleManager;
import inetsoft.sree.schedule.ScheduleTask;
import inetsoft.sree.schedule.ViewsheetAction;
import inetsoft.sree.security.IdentityID;
import inetsoft.sree.security.ResourceAction;
import inetsoft.sree.security.ResourceType;
import inetsoft.sree.security.SecurityEngine;
import inetsoft.uql.XPrincipal;
import inetsoft.uql.asset.AssetEntry;
import inetsoft.uql.asset.sync.ViewsheetBookmarkChangedEvent;
import inetsoft.uql.util.XSessionService;
import inetsoft.uql.viewsheet.VSBookmarkInfo;
import inetsoft.util.Catalog;
import inetsoft.util.audit.AuditRecordUtils;
import inetsoft.web.viewsheet.command.MessageCommand;
import inetsoft.web.viewsheet.event.ImmutableVSEditBookmarkEvent;
import inetsoft.web.viewsheet.event.VSEditBookmarkEvent;
import inetsoft.web.viewsheet.model.ImmutableVSBookmarkInfoModel;
import inetsoft.web.viewsheet.model.VSBookmarkInfoModel;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import java.security.Principal;
import java.util.Vector;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Bug #76845 (part 2 -- delete_bookmark's schedule-task-usage safety check).
 *
 * <p>{@link VSBookmarkService#deleteBookmark} independently re-derived an identity from the
 * CALLING principal (rather than the event's own {@code owner} field, already correctly used for
 * the actual removal) to look up whether a live schedule task depends on the bookmark being
 * deleted. Once {@link inetsoft.web.wiz.viewsheet.ViewsheetAssemblyAgentController} started
 * threading the RESOLVED TARGET OWNER through for a non-owner override (the bug #76845 fix), this
 * mismatch meant a non-owner deleting someone else's shared, schedule-task-dependent bookmark
 * would no longer be protected by that check -- the exact check that protects the bookmark's
 * actual owner today when they delete it themselves.
 */
@Tag("core")
class VSBookmarkServiceTest {
   @Test
   void deleteBookmark_nonOwnerSharedReadOnlyFalse_referencedByScheduleTaskUnderOriginalOwner_stillBlocked()
      throws Exception
   {
      IdentityID admin = IdentityID.getIdentityIDFromKey("admin");

      RuntimeViewsheet rvs = mock(RuntimeViewsheet.class);
      AssetEntry entry = mock(AssetEntry.class);
      when(entry.toIdentifier()).thenReturn("1^1^__NULL__^myVS");
      when(rvs.getEntry()).thenReturn(entry);
      when(rvs.getID()).thenReturn("runtime-1");

      VSObjectService vsObjectService = mock(VSObjectService.class);
      Principal caller = () -> "user0";
      when(vsObjectService.getRuntimeViewsheet(eq("runtime-1"), eq(caller))).thenReturn(rvs);

      // A live schedule task whose ViewsheetAction references "admin_all" owned by the ORIGINAL
      // owner ("admin"), not the caller ("user0"), on this exact viewsheet.
      ViewsheetAction action = new ViewsheetAction();
      action.setViewsheet("1^1^__NULL__^myVS");
      action.setBookmarks(new String[] { "admin_all" });
      action.setBookmarkUsers(new IdentityID[] { admin });
      ScheduleTask task = new ScheduleTask("dependent-task");
      task.addAction(action);
      Vector<ScheduleTask> tasks = new Vector<>();
      tasks.add(task);

      ScheduleManager scheduleManager = mock(ScheduleManager.class);
      when(scheduleManager.getScheduleTasks()).thenReturn(tasks);

      VSBookmarkService service = new VSBookmarkService(vsObjectService,
         mock(ViewsheetService.class), mock(SecurityEngine.class), scheduleManager,
         mock(Cluster.class), mock(XSessionService.class));

      // The controller-side #76845 fix has already threaded the RESOLVED TARGET OWNER ("admin")
      // into the event -- simulating that here, since this test targets the service-side gap,
      // not the controller.
      VSBookmarkInfoModel model = ImmutableVSBookmarkInfoModel.builder()
         .name("admin_all")
         .owner(admin)
         .readOnly(false)
         .build();
      VSEditBookmarkEvent event = ImmutableVSEditBookmarkEvent.builder()
         .vsBookmarkInfoModel(model)
         .confirmed(false)
         .build();

      CommandDispatcher dispatcher = mock(CommandDispatcher.class);
      service.deleteBookmark("runtime-1", event, caller, dispatcher, "");

      ArgumentCaptor<MessageCommand> captor = ArgumentCaptor.forClass(MessageCommand.class);
      verify(dispatcher).sendCommand(captor.capture());
      assertEquals(MessageCommand.Type.ERROR, captor.getValue().getType());
      verify(rvs, never()).removeBookmark(any(), any());
   }

   /**
    * Bug #76827 -- {@link VSBookmarkService#renameBookmarkInViewSheet} happy path: the saved
    * bookmark payload is moved via {@link RuntimeViewsheet#editBookmark} (mocked here, so this
    * test only confirms the call and its exact arguments -- {@code RuntimeViewsheet.editBookmark}'s
    * own state-preserving behavior is exercised by the real, unmocked data layer, not by this
    * service-level test), the change is broadcast, and the returned command is OK.
    */
   @Test
   void renameBookmarkInViewSheet_happyPath_preservesStateAndBroadcasts() throws Exception {
      RuntimeViewsheet rvs = mock(RuntimeViewsheet.class);
      AssetEntry entry = mock(AssetEntry.class);
      when(entry.toIdentifier()).thenReturn("1^1^__NULL__^myVS");
      when(rvs.getEntry()).thenReturn(entry);

      ScheduleManager scheduleManager = mock(ScheduleManager.class);
      when(scheduleManager.getScheduleTasks()).thenReturn(new Vector<>());

      Cluster cluster = mock(Cluster.class);

      VSBookmarkService service = new VSBookmarkService(mock(VSObjectService.class),
         mock(ViewsheetService.class), mock(SecurityEngine.class), scheduleManager,
         cluster, mock(XSessionService.class));

      Principal caller = () -> "user0";
      MessageCommand result = service.renameBookmarkInViewSheet(
         rvs, "user0_g1", "user0_g", VSBookmarkInfo.GROUPSHARE, false, false, caller);

      assertEquals(MessageCommand.Type.OK, result.getType());
      verify(rvs).editBookmark("user0_g1", "user0_g", VSBookmarkInfo.GROUPSHARE, false);
      verify(cluster).sendMessage(any(ViewsheetBookmarkChangedEvent.class));
   }

   /**
    * Bug #76827 -- a rename onto an already-existing name is always refused, with no
    * {@code confirmed} bypass, mirroring {@code create_bookmark}'s own hard collision refusal.
    */
   @Test
   void renameBookmarkInViewSheet_duplicateName_refused() throws Exception {
      RuntimeViewsheet rvs = mock(RuntimeViewsheet.class);
      AssetEntry entry = mock(AssetEntry.class);
      when(entry.toIdentifier()).thenReturn("1^1^__NULL__^myVS");
      when(rvs.getEntry()).thenReturn(entry);
      when(rvs.containsBookmark(eq("user0_g1"), any())).thenReturn(true);

      VSBookmarkService service = new VSBookmarkService(mock(VSObjectService.class),
         mock(ViewsheetService.class), mock(SecurityEngine.class), mock(ScheduleManager.class),
         mock(Cluster.class), mock(XSessionService.class));

      Principal caller = () -> "user0";
      MessageCommand result = service.renameBookmarkInViewSheet(
         rvs, "user0_g1", "user0_g", VSBookmarkInfo.PRIVATE, true, false, caller);

      assertEquals(MessageCommand.Type.ERROR, result.getType());
      verify(rvs, never()).editBookmark(any(), any(), anyInt(), anyBoolean());
   }

   /**
    * Bug #76827 -- renaming a bookmark referenced by a live schedule task is refused, naming the
    * problem, when {@code confirmed} is false. This is the case the diagnosis/refute identified
    * as load-bearing: {@link VSBookmarkService#editBookmark} (the existing, interactive-UI-facing
    * method) signals this same underlying concern via a dispatched {@code Type.CONFIRM} command,
    * which is inert under this bridge's {@code CapturingCommandDispatcher} -- this new method
    * instead returns a non-OK {@code MessageCommand} directly, so a caller that only inspects the
    * return value (like {@code requireOk}) cannot silently miss the refusal.
    */
   @Test
   void renameBookmarkInViewSheet_scheduleReferenced_notConfirmed_refused() throws Exception {
      IdentityID user0 = IdentityID.getIdentityIDFromKey("user0");

      RuntimeViewsheet rvs = mock(RuntimeViewsheet.class);
      AssetEntry entry = mock(AssetEntry.class);
      when(entry.toIdentifier()).thenReturn("1^1^__NULL__^myVS");
      when(rvs.getEntry()).thenReturn(entry);

      ViewsheetAction action = new ViewsheetAction();
      action.setViewsheet("1^1^__NULL__^myVS");
      action.setBookmarks(new String[] { "user0_g" });
      action.setBookmarkUsers(new IdentityID[] { user0 });
      ScheduleTask task = new ScheduleTask("dependent-task");
      task.addAction(action);
      Vector<ScheduleTask> tasks = new Vector<>();
      tasks.add(task);

      ScheduleManager scheduleManager = mock(ScheduleManager.class);
      when(scheduleManager.getScheduleTasks()).thenReturn(tasks);

      VSBookmarkService service = new VSBookmarkService(mock(VSObjectService.class),
         mock(ViewsheetService.class), mock(SecurityEngine.class), scheduleManager,
         mock(Cluster.class), mock(XSessionService.class));

      Principal caller = () -> "user0";
      MessageCommand result = service.renameBookmarkInViewSheet(
         rvs, "user0_g1", "user0_g", VSBookmarkInfo.GROUPSHARE, false, false, caller);

      assertEquals(MessageCommand.Type.ERROR, result.getType());
      verify(rvs, never()).editBookmark(any(), any(), anyInt(), anyBoolean());
      verify(scheduleManager, never()).bookmarkRenamed(any(), any(), any(), any());
   }

   /**
    * Bug #76827 -- renaming a bookmark referenced by a live schedule task succeeds once
    * {@code confirmed} is true, and the schedule task's own bookmark reference is updated
    * ({@link ScheduleManager#bookmarkRenamed}) to the new name, the same call the existing
    * {@link VSBookmarkService#editBookmark} makes for its own confirmed branch.
    */
   @Test
   void renameBookmarkInViewSheet_scheduleReferenced_confirmed_succeedsAndUpdatesSchedule()
      throws Exception
   {
      IdentityID user0 = IdentityID.getIdentityIDFromKey("user0");

      RuntimeViewsheet rvs = mock(RuntimeViewsheet.class);
      AssetEntry entry = mock(AssetEntry.class);
      when(entry.toIdentifier()).thenReturn("1^1^__NULL__^myVS");
      when(rvs.getEntry()).thenReturn(entry);

      ViewsheetAction action = new ViewsheetAction();
      action.setViewsheet("1^1^__NULL__^myVS");
      action.setBookmarks(new String[] { "user0_g" });
      action.setBookmarkUsers(new IdentityID[] { user0 });
      ScheduleTask task = new ScheduleTask("dependent-task");
      task.addAction(action);
      Vector<ScheduleTask> tasks = new Vector<>();
      tasks.add(task);

      ScheduleManager scheduleManager = mock(ScheduleManager.class);
      when(scheduleManager.getScheduleTasks()).thenReturn(tasks);

      Cluster cluster = mock(Cluster.class);

      VSBookmarkService service = new VSBookmarkService(mock(VSObjectService.class),
         mock(ViewsheetService.class), mock(SecurityEngine.class), scheduleManager,
         cluster, mock(XSessionService.class));

      Principal caller = () -> "user0";
      MessageCommand result = service.renameBookmarkInViewSheet(
         rvs, "user0_g1", "user0_g", VSBookmarkInfo.GROUPSHARE, false, true, caller);

      assertEquals(MessageCommand.Type.OK, result.getType());
      verify(rvs).editBookmark("user0_g1", "user0_g", VSBookmarkInfo.GROUPSHARE, false);
      verify(scheduleManager).bookmarkRenamed("user0_g", "user0_g1", "1^1^__NULL__^myVS", user0);
      verify(cluster).sendMessage(any(ViewsheetBookmarkChangedEvent.class));
   }

   /**
    * Bug #76950 -- a rename through {@link VSBookmarkService#renameBookmarkInViewSheet} writes
    * the same audit record as the native {@link VSBookmarkService#editBookmark}, from a snapshot
    * of the bookmark taken before the rename.
    */
   @Test
   void renameBookmarkInViewSheet_writesEditAuditRecordFromPreRenameSnapshot() throws Exception {
      IdentityID user0 = IdentityID.getIdentityIDFromKey("user0");

      RuntimeViewsheet rvs = mock(RuntimeViewsheet.class);
      AssetEntry entry = mock(AssetEntry.class);
      when(entry.toIdentifier()).thenReturn("1^1^__NULL__^myVS");
      when(rvs.getEntry()).thenReturn(entry);

      VSBookmarkInfo current = new VSBookmarkInfo();
      current.setName("user0_g");
      current.setType(VSBookmarkInfo.PRIVATE);
      when(rvs.getBookmarkInfo("user0_g", user0)).thenReturn(current);

      ScheduleManager scheduleManager = mock(ScheduleManager.class);
      when(scheduleManager.getScheduleTasks()).thenReturn(new Vector<>());

      VSBookmarkService service = new VSBookmarkService(mock(VSObjectService.class),
         mock(ViewsheetService.class), mock(SecurityEngine.class), scheduleManager,
         mock(Cluster.class), mock(XSessionService.class));

      Principal caller = () -> "user0";

      try(MockedStatic<AuditRecordUtils> audit = mockStatic(AuditRecordUtils.class)) {
         MessageCommand result = service.renameBookmarkInViewSheet(
            rvs, "user0_g1", "user0_g", VSBookmarkInfo.GROUPSHARE, false, false, caller);

         assertEquals(MessageCommand.Type.OK, result.getType());
         ArgumentCaptor<VSBookmarkInfo> orig = ArgumentCaptor.forClass(VSBookmarkInfo.class);
         audit.verify(() -> AuditRecordUtils.executeEditBookmarkRecord(
            eq(rvs), orig.capture(), eq("user0_g1"), eq(user0)));
         assertEquals("user0_g", orig.getValue().getName());
         assertEquals(VSBookmarkInfo.PRIVATE, orig.getValue().getType());
         assertNotSame(current, orig.getValue());
      }
   }

   /**
    * Bug #80169 -- the refusal for a user of another organization on a default-organization
    * dashboard must come from a real catalog entry that describes the actual reason, not the
    * old literal "Can't create simple scheduler for default organization".
    */
   @Test
   void checkAddBookmark_otherOrgOnDefaultOrgDashboard_refusesWithCatalogMessage() throws Exception {
      RuntimeViewsheet rvs = mock(RuntimeViewsheet.class);
      AssetEntry entry = mock(AssetEntry.class);
      when(entry.getOrgID()).thenReturn("host-org");
      when(rvs.getEntry()).thenReturn(entry);

      XPrincipal principal = mock(XPrincipal.class);
      when(principal.getOrgId()).thenReturn("orgA");

      SecurityEngine engine = mock(SecurityEngine.class);
      when(engine.checkPermission(any(Principal.class), any(ResourceType.class),
         any(String.class), any(ResourceAction.class))).thenReturn(true);

      VSBookmarkService service = new VSBookmarkService(mock(VSObjectService.class),
         mock(ViewsheetService.class), engine, mock(ScheduleManager.class),
         mock(Cluster.class), mock(XSessionService.class));

      try(MockedStatic<SUtil> sutil = mockStatic(SUtil.class)) {
         sutil.when(() -> SUtil.isDefaultVSGloballyVisible(principal)).thenReturn(true);

         MessageCommand result = service.checkAddBookmark(rvs, "b1", false, principal);

         String expected =
            Catalog.getCatalog().getString(VSBookmarkService.DEFAULT_ORG_DENIED_KEY);
         assertEquals(MessageCommand.Type.WARNING, result.getType());
         assertNotEquals(VSBookmarkService.DEFAULT_ORG_DENIED_KEY, expected);
         assertEquals(expected, result.getMessage());
         assertTrue(result.getMessage().contains("bookmark"));
         assertNotEquals("Can't create simple scheduler for default organization",
                         result.getMessage());
      }
   }

   /** Runs setDefaultBookmark for a user of {@code principalOrg} on an entry of {@code entryOrg}. */
   private RuntimeViewsheet runSetDefault(String entryOrg, String principalOrg,
                                          boolean globallyVisible,
                                          CommandDispatcher dispatcher) throws Exception
   {
      RuntimeViewsheet rvs = mock(RuntimeViewsheet.class);
      AssetEntry entry = mock(AssetEntry.class);
      when(entry.getOrgID()).thenReturn(entryOrg);
      when(entry.getScope()).thenReturn(inetsoft.uql.asset.AssetRepository.GLOBAL_SCOPE);
      when(rvs.getEntry()).thenReturn(entry);
      inetsoft.uql.viewsheet.Viewsheet vs = mock(inetsoft.uql.viewsheet.Viewsheet.class);
      when(vs.getRuntimeEntry()).thenReturn(entry);
      when(rvs.getViewsheet()).thenReturn(vs);

      XPrincipal principal = mock(XPrincipal.class);
      when(principal.getOrgId()).thenReturn(principalOrg);

      VSObjectService objectService = mock(VSObjectService.class);
      when(objectService.getRuntimeViewsheet("rt", principal)).thenReturn(rvs);
      VSBookmarkService service = new VSBookmarkService(objectService,
         mock(ViewsheetService.class), mock(SecurityEngine.class), mock(ScheduleManager.class),
         mock(Cluster.class), mock(XSessionService.class));
      VSEditBookmarkEvent event = ImmutableVSEditBookmarkEvent.builder()
         .vsBookmarkInfoModel(VSBookmarkInfoModel.builder().name("b1")
            .owner(new IdentityID("u", "orgA")).build())
         .confirmed(false).build();

      try(MockedStatic<SUtil> sutil = mockStatic(SUtil.class)) {
         sutil.when(() -> SUtil.isDefaultVSGloballyVisible(principal)).thenReturn(globallyVisible);
         service.setDefaultBookmark("rt", event, principal, dispatcher, "");
      }

      return rvs;
   }

   /** Bug #80168: another org's user cannot set a default bookmark on a default-org dashboard. */
   @Test
   void setDefaultBookmark_otherOrgOnDefaultOrgDashboard_isRefused() throws Exception {
      CommandDispatcher dispatcher = mock(CommandDispatcher.class);
      RuntimeViewsheet rvs = runSetDefault(
         inetsoft.sree.security.Organization.getDefaultOrganizationID(), "orgA", true, dispatcher);

      ArgumentCaptor<MessageCommand> captor = ArgumentCaptor.forClass(MessageCommand.class);
      verify(dispatcher).sendCommand(captor.capture());
      assertEquals(MessageCommand.Type.WARNING, captor.getValue().getType());
      assertEquals(Catalog.getCatalog().getString(VSBookmarkService.DEFAULT_ORG_DENIED_KEY),
                   captor.getValue().getMessage());
      verify(rvs, never()).setDefaultBookmark(any());
   }

   @Test
   void setDefaultBookmark_sameOrgUser_isAllowed() throws Exception {
      String org = inetsoft.sree.security.Organization.getDefaultOrganizationID();
      CommandDispatcher dispatcher = mock(CommandDispatcher.class);
      RuntimeViewsheet rvs = runSetDefault(org, org, true, dispatcher);
      verify(rvs).setDefaultBookmark(any());
      verify(dispatcher, never()).sendCommand(any());
   }

   @Test
   void setDefaultBookmark_nonDefaultOrgEntryFromOtherOrg_isAllowed() throws Exception {
      CommandDispatcher dispatcher = mock(CommandDispatcher.class);
      RuntimeViewsheet rvs = runSetDefault("some-other-org-entry", "orgA", true, dispatcher);
      verify(rvs).setDefaultBookmark(any());
      verify(dispatcher, never()).sendCommand(any());
   }

   /** Site admins (and a disabled exposure flag) make isDefaultVSGloballyVisible false. */
   @Test
   void setDefaultBookmark_siteAdminOrFlagOff_isAllowed() throws Exception {
      CommandDispatcher dispatcher = mock(CommandDispatcher.class);
      RuntimeViewsheet rvs = runSetDefault(
         inetsoft.sree.security.Organization.getDefaultOrganizationID(), "orgA", false, dispatcher);
      verify(rvs).setDefaultBookmark(any());
      verify(dispatcher, never()).sendCommand(any());
   }
}
