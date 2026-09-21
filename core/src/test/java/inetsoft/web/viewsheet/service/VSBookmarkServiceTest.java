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
import inetsoft.sree.internal.cluster.Cluster;
import inetsoft.sree.schedule.ScheduleManager;
import inetsoft.sree.schedule.ScheduleTask;
import inetsoft.sree.schedule.ViewsheetAction;
import inetsoft.sree.security.IdentityID;
import inetsoft.sree.security.SecurityEngine;
import inetsoft.uql.asset.AssetEntry;
import inetsoft.uql.util.XSessionService;
import inetsoft.web.viewsheet.command.MessageCommand;
import inetsoft.web.viewsheet.event.ImmutableVSEditBookmarkEvent;
import inetsoft.web.viewsheet.event.VSEditBookmarkEvent;
import inetsoft.web.viewsheet.model.ImmutableVSBookmarkInfoModel;
import inetsoft.web.viewsheet.model.VSBookmarkInfoModel;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.security.Principal;
import java.util.Vector;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
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
}
