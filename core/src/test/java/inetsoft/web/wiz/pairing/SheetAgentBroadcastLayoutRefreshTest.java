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
import inetsoft.web.binding.service.VSBindingTreeService;
import inetsoft.web.composer.vs.controller.VSLayoutControllerServiceProxy;
import inetsoft.web.composer.vs.command.ChangeCurrentLayoutCommand;
import inetsoft.web.viewsheet.command.UpdateLayoutCommand;
import inetsoft.web.viewsheet.model.VSObjectModelFactoryService;
import inetsoft.web.viewsheet.service.CommandDispatcher;
import inetsoft.web.viewsheet.service.CommandDispatcherService;
import inetsoft.web.viewsheet.service.CoreLifecycleService;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.messaging.MessageHeaders;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;

import java.security.Principal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Bug #81189: {@link SheetAgentBroadcastService#broadcastLayoutRefresh}. */
@Tag("core")
class SheetAgentBroadcastLayoutRefreshTest {
   private static final Principal AGENT = TestPrincipals.user("alice", "host-org");

   private final CommandDispatcherService dispatcher = mock(CommandDispatcherService.class);
   private final VSLayoutControllerServiceProxy layoutProxy =
      mock(VSLayoutControllerServiceProxy.class);
   private final SheetAgentBroadcastService svc = new SheetAgentBroadcastService(
      dispatcher, mock(VSObjectModelFactoryService.class), mock(VSBindingTreeService.class),
      mock(CoreLifecycleService.class), layoutProxy);

   private static RuntimeViewsheet master(String sessionId) {
      RuntimeViewsheet rvs = mock(RuntimeViewsheet.class);
      when(rvs.getID()).thenReturn("master-1");
      when(rvs.getSocketSessionId()).thenReturn(sessionId);
      when(rvs.getSocketUserName()).thenReturn("alice~;~host-org");
      when(rvs.getLayoutPoint()).thenReturn(2);
      when(rvs.getLayoutPointsSize()).thenReturn(4);
      return rvs;
   }

   @Test
   void forwardsCapturedCommandsUnderTheMasterIdThenTheUndoState() throws Exception {
      doAnswer(inv -> {
         CommandDispatcher d = inv.getArgument(5);
         d.sendCommand(new ChangeCurrentLayoutCommand(null));
         d.sendCommand(UpdateLayoutCommand.builder().layoutName("Phone").build());
         return null;
      }).when(layoutProxy).changeViewsheetLayout(any(), any(), any(), any(), any(), any());

      svc.broadcastLayoutRefresh(master("stomp-1"), "Phone", AGENT);

      // Current layout == target layout, so the native resend leaves the layout undo stack alone.
      verify(layoutProxy).changeViewsheetLayout(
         eq("master-1"), eq("Phone"), eq("Phone"), eq(AGENT), any(), any());

      ArgumentCaptor<Object> payloads = ArgumentCaptor.forClass(Object.class);
      ArgumentCaptor<MessageHeaders> headers = ArgumentCaptor.forClass(MessageHeaders.class);
      verify(dispatcher, times(3)).convertAndSendToUser(
         eq("alice~;~host-org"), eq(CommandDispatcher.COMMANDS_TOPIC), payloads.capture(),
         headers.capture());

      for(MessageHeaders h : headers.getAllValues()) {
         assertEquals("stomp-1", SimpMessageHeaderAccessor.getSessionId(h));
         SimpMessageHeaderAccessor acc = SimpMessageHeaderAccessor.wrap(
            new org.springframework.messaging.support.GenericMessage<>("", h));
         assertEquals("master-1", acc.getNativeHeader(CommandDispatcher.RUNTIME_ID_ATTR).get(0));
      }

      assertInstanceOf(ChangeCurrentLayoutCommand.class, payloads.getAllValues().get(0));
      assertEquals("UpdateLayoutUndoStateCommand",
                   payloads.getAllValues().get(2).getClass().getSimpleName());
   }

   @Test
   void noSocketSessionPushesNothing() throws Exception {
      svc.broadcastLayoutRefresh(master(null), "Phone", AGENT);

      verifyNoInteractions(layoutProxy);
      verifyNoInteractions(dispatcher);
   }

   @Test
   void aFailureIsSwallowed() throws Exception {
      doThrow(new IllegalStateException("boom")).when(layoutProxy)
         .changeViewsheetLayout(any(), any(), any(), any(), any(), any());

      assertDoesNotThrow(() -> svc.broadcastLayoutRefresh(master("stomp-1"), "Phone", AGENT));
      verifyNoInteractions(dispatcher);
   }
}
