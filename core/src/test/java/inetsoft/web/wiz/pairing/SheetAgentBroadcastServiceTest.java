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

import inetsoft.report.composition.RuntimeSheet;
import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.report.composition.RuntimeWorksheet;
import inetsoft.uql.viewsheet.TextVSAssembly;
import inetsoft.uql.viewsheet.VSAssembly;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.web.viewsheet.model.VSObjectModel;
import inetsoft.web.viewsheet.model.VSObjectModelFactoryService;
import inetsoft.web.viewsheet.service.CommandDispatcher;
import inetsoft.web.viewsheet.service.CommandDispatcherService;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.messaging.MessageHeaders;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;

import java.security.Principal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@Tag("core")
class SheetAgentBroadcastServiceTest {

   private static VSObjectModelFactoryService noopModelFactory() {
      return mock(VSObjectModelFactoryService.class);
   }

   @Test
   void worksheetBroadcastTargetsSocketSessionWithoutClientId() {
      CommandDispatcherService dispatcher = mock(CommandDispatcherService.class);
      SheetAgentBroadcastService svc = new SheetAgentBroadcastService(dispatcher, noopModelFactory());

      RuntimeWorksheet rs = mock(RuntimeWorksheet.class);
      when(rs.getSocketSessionId()).thenReturn("stomp-1");
      when(rs.getSocketUserName()).thenReturn("alice~;~host-org");
      Principal owner = TestPrincipals.user("alice", "host-org");

      svc.broadcastRefresh(rs, SheetType.WORKSHEET, "Worksheet/foo-7", owner);

      ArgumentCaptor<MessageHeaders> headersCap = ArgumentCaptor.forClass(MessageHeaders.class);
      verify(dispatcher).convertAndSendToUser(
         eq("alice~;~host-org"), eq(CommandDispatcher.COMMANDS_TOPIC), any(), headersCap.capture());

      MessageHeaders headers = headersCap.getValue();
      // session id must match
      assertEquals("stomp-1", SimpMessageHeaderAccessor.getSessionId(headers));
      // RUNTIME_ID_ATTR header set
      SimpMessageHeaderAccessor acc = SimpMessageHeaderAccessor.wrap(
         new org.springframework.messaging.support.GenericMessage<>("", headers));
      assertEquals("Worksheet/foo-7", acc.getNativeHeader(CommandDispatcher.RUNTIME_ID_ATTR).get(0));
      // inetsoftClientId must NOT be present
      assertNull(acc.getNativeHeader("inetsoftClientId"));
   }

   @Test
   void nullSocketSessionSkipsBroadcast() {
      CommandDispatcherService dispatcher = mock(CommandDispatcherService.class);
      SheetAgentBroadcastService svc = new SheetAgentBroadcastService(dispatcher, noopModelFactory());

      RuntimeSheet rs = mock(RuntimeSheet.class);
      when(rs.getSocketSessionId()).thenReturn(null);

      svc.broadcastRefresh(rs, SheetType.WORKSHEET, "Worksheet/foo-7",
                           TestPrincipals.user("alice", "host-org"));

      verifyNoInteractions(dispatcher);
   }

   @Test
   void viewsheetBroadcastSendsOneRefreshVSObjectCommandPerVisibleAssembly() {
      CommandDispatcherService dispatcher = mock(CommandDispatcherService.class);
      VSObjectModelFactoryService modelFactory = mock(VSObjectModelFactoryService.class);
      SheetAgentBroadcastService svc = new SheetAgentBroadcastService(dispatcher, modelFactory);

      RuntimeViewsheet rvs = mock(RuntimeViewsheet.class);
      when(rvs.getSocketSessionId()).thenReturn("stomp-vs-1");
      when(rvs.getSocketUserName()).thenReturn("alice~;~host-org");

      Viewsheet vs = mock(Viewsheet.class);
      VSAssembly visible = mock(TextVSAssembly.class);
      when(visible.isVisible()).thenReturn(true);
      when(visible.getName()).thenReturn("Text1");
      VSAssembly hidden = mock(TextVSAssembly.class);
      when(hidden.isVisible()).thenReturn(false);
      when(vs.getAssemblies()).thenReturn(new inetsoft.uql.asset.Assembly[]{ visible, hidden });
      when(rvs.getViewsheet()).thenReturn(vs);

      VSObjectModel<?> model = mock(VSObjectModel.class);
      when(modelFactory.createModel(visible, rvs)).thenReturn(model);

      svc.broadcastRefresh(rvs, SheetType.VIEWSHEET, "ViewsheetRuntime/bar-9",
                           TestPrincipals.user("alice", "host-org"));

      // Only the visible assembly gets a command; the hidden one is skipped.
      verify(modelFactory, times(1)).createModel(any(), eq(rvs));
      verify(dispatcher, times(1)).convertAndSendToUser(
         eq("alice~;~host-org"), eq(CommandDispatcher.COMMANDS_TOPIC), any(), any());
   }

   @Test
   void fallsBackToOwnerNameWhenSocketUserNameIsNull() {
      CommandDispatcherService dispatcher = mock(CommandDispatcherService.class);
      SheetAgentBroadcastService svc = new SheetAgentBroadcastService(dispatcher, noopModelFactory());

      RuntimeWorksheet rs = mock(RuntimeWorksheet.class);
      when(rs.getSocketSessionId()).thenReturn("stomp-2");
      when(rs.getSocketUserName()).thenReturn(null);  // no user recorded
      Principal owner = TestPrincipals.user("alice", "host-org");

      svc.broadcastRefresh(rs, SheetType.WORKSHEET, "Worksheet/foo-7", owner);

      // falls back to owner.getName()
      verify(dispatcher).convertAndSendToUser(
         eq(owner.getName()), eq(CommandDispatcher.COMMANDS_TOPIC), any(), any());
   }
}
