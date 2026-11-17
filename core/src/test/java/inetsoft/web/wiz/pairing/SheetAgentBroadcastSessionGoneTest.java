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
package inetsoft.web.wiz.pairing;

import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.report.composition.RuntimeWorksheet;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.web.binding.service.VSBindingTreeService;
import inetsoft.web.viewsheet.model.VSObjectModelFactoryService;
import inetsoft.web.viewsheet.service.CommandDispatcherService;
import inetsoft.web.viewsheet.service.CoreLifecycleService;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.security.Principal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * An agent write is pushed to the browser session the runtime was stamped with. When that session
 * no longer exists (the browser reconnected under a new id) the push can only be dropped, so the
 * refresh must say so instead of reporting a delivery.
 *
 * [worksheetSessionGone]   a gone session sends nothing and returns the notice
 * [viewsheetSessionGone]   same for a viewsheet
 * [liveSessionIsDelivered] a live session is pushed to and no notice returned
 * [noSessionRecorded]      a sheet no browser ever paired has nothing to refresh and no notice
 */
@Tag("core")
class SheetAgentBroadcastSessionGoneTest {
   private final Principal owner = TestPrincipals.user("alice", "host-org");
   private final CommandDispatcherService dispatcher = mock(CommandDispatcherService.class);
   private final SheetAgentBroadcastService svc = new SheetAgentBroadcastService(
      dispatcher, mock(VSObjectModelFactoryService.class), mock(VSBindingTreeService.class),
      mock(CoreLifecycleService.class));

   @Test
   void worksheetSessionGone() {
      RuntimeWorksheet rs = mock(RuntimeWorksheet.class);
      when(rs.getSocketSessionId()).thenReturn("sock-old");
      when(dispatcher.isSessionLive("sock-old")).thenReturn(false);

      String notice = svc.broadcastRefresh(rs, SheetType.WORKSHEET, "Worksheet/foo-7", owner);

      assertEquals(SheetAgentBroadcastService.SESSION_GONE_NOTICE, notice);
      verify(dispatcher, never()).convertAndSendToUser(any(), any(), any(), any());
   }

   @Test
   void viewsheetSessionGone() {
      RuntimeViewsheet rs = mock(RuntimeViewsheet.class);
      when(rs.getSocketSessionId()).thenReturn("sock-old");
      when(rs.getViewsheet()).thenReturn(mock(Viewsheet.class));
      when(dispatcher.isSessionLive("sock-old")).thenReturn(false);

      String notice = svc.broadcastRefresh(rs, SheetType.VIEWSHEET, "Viewsheet/foo-7", owner);

      assertEquals(SheetAgentBroadcastService.SESSION_GONE_NOTICE, notice);
      verify(dispatcher, never()).convertAndSendToUser(any(), any(), any(), any());
   }

   @Test
   void liveSessionIsDelivered() {
      RuntimeWorksheet rs = mock(RuntimeWorksheet.class);
      when(rs.getSocketSessionId()).thenReturn("sock-1");
      when(rs.getSocketUserName()).thenReturn("alice~;~host-org");
      when(dispatcher.isSessionLive("sock-1")).thenReturn(true);

      String notice = svc.broadcastRefresh(rs, SheetType.WORKSHEET, "Worksheet/foo-7", owner);

      assertNull(notice);
      verify(dispatcher, atLeastOnce()).convertAndSendToUser(any(), any(), any(), any());
   }

   @Test
   void noSessionRecorded() {
      RuntimeWorksheet rs = mock(RuntimeWorksheet.class);

      String notice = svc.broadcastRefresh(rs, SheetType.WORKSHEET, "Worksheet/foo-7", owner);

      assertNull(notice);
      verify(dispatcher, never()).isSessionLive(any());
      verify(dispatcher, never()).convertAndSendToUser(any(), any(), any(), any());
   }
}
