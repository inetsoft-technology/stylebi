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
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.web.composer.command.FocusSheetCommand;
import inetsoft.web.binding.service.VSBindingTreeService;
import inetsoft.web.viewsheet.model.VSObjectModelFactoryService;
import inetsoft.web.viewsheet.service.CommandDispatcher;
import inetsoft.web.viewsheet.service.CommandDispatcherService;
import inetsoft.web.viewsheet.service.ComposerClientService;
import inetsoft.web.viewsheet.service.CoreLifecycleService;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.messaging.MessageHeaders;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.support.GenericMessage;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Bug #80379: {@link SheetAgentBroadcastService#focusSheet} is the hint that lets a Composer with
 * "Follow Focus" ticked switch to the tab an agent just edited. These pin its addressing (the
 * composer channel, the owning socket session only, no runtime header) and that no refresh
 * broadcast sends it by itself -- a worksheet save repaints dependent viewsheets through
 * {@code broadcastRefresh}, and none of them was edited.
 *
 * <p>Plain Mockito like {@link SheetAgentBroadcastServiceTest}: nothing here reaches
 * {@code ConfigurationContext}.
 */
@Tag("core")
class SheetAgentBroadcastFocusSheetTest {
   private static SheetAgentBroadcastService service(CommandDispatcherService dispatcher) {
      return new SheetAgentBroadcastService(dispatcher, mock(VSObjectModelFactoryService.class),
                                            mock(VSBindingTreeService.class),
                                            mock(CoreLifecycleService.class));
   }

   @Test
   void focusSheetSendsTheCommandToTheOwningBrowsersComposerChannel() {
      CommandDispatcherService dispatcher = TestDispatchers.live();
      RuntimeWorksheet rws = mock(RuntimeWorksheet.class);
      when(rws.getSocketSessionId()).thenReturn("stomp-1");

      service(dispatcher).focusSheet(rws, "Worksheet/foo-7");

      ArgumentCaptor<Object> command = ArgumentCaptor.forClass(Object.class);
      ArgumentCaptor<MessageHeaders> headers = ArgumentCaptor.forClass(MessageHeaders.class);
      // the literal destination strings: the sheet channel has the same constant name and is
      // dropped silently by the composer client
      verify(dispatcher).convertAndSendToUser(
         eq("stomp-1"), eq(ComposerClientService.COMMANDS_TOPIC), command.capture(),
         headers.capture());
      assertEquals("/composer-client", ComposerClientService.COMMANDS_TOPIC);

      FocusSheetCommand sent = assertInstanceOf(FocusSheetCommand.class, command.getValue());
      assertEquals("Worksheet/foo-7", sent.runtimeId());
      assertEquals("focusSheet", sent.type());

      assertEquals("stomp-1", SimpMessageHeaderAccessor.getSessionId(headers.getValue()));
      SimpMessageHeaderAccessor acc =
         SimpMessageHeaderAccessor.wrap(new GenericMessage<>("", headers.getValue()));
      // a runtime header would route it to a sheet's own channel filter
      assertNull(acc.getNativeHeader(CommandDispatcher.RUNTIME_ID_ATTR));
      verifyNoMoreInteractions(ignoreStubs(dispatcher));
   }

   @Test
   void focusSheetSerializesTheTypeTheBrowserDiscriminatesOn() throws Exception {
      String json = new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(
         FocusSheetCommand.builder().runtimeId("Viewsheet/bar-9").build());

      assertTrue(json.contains("\"type\":\"focusSheet\""), json);
      assertTrue(json.contains("\"runtimeId\":\"Viewsheet/bar-9\""), json);
   }

   @Test
   void focusSheetSendsNothingWithoutABrowserSession() {
      CommandDispatcherService dispatcher = TestDispatchers.live();
      RuntimeSheet rs = mock(RuntimeSheet.class);
      when(rs.getSocketSessionId()).thenReturn(null);

      service(dispatcher).focusSheet(rs, "Worksheet/foo-7");

      verify(dispatcher, never()).convertAndSendToUser(any(), any(), any(), any());
   }

   @Test
   void focusSheetSendsNothingWhenTheBrowserSessionIsGone() {
      // a bare mock reports every session gone
      CommandDispatcherService dispatcher = mock(CommandDispatcherService.class);
      RuntimeSheet rs = mock(RuntimeSheet.class);
      when(rs.getSocketSessionId()).thenReturn("stomp-1");

      service(dispatcher).focusSheet(rs, "Worksheet/foo-7");

      verify(dispatcher, never()).convertAndSendToUser(any(), any(), any(), any());
   }

   @Test
   void focusSheetNeverFailsTheCallerAfterASuccessfulEdit() {
      CommandDispatcherService dispatcher = TestDispatchers.live();
      doThrow(new IllegalStateException("broker down")).when(dispatcher)
         .convertAndSendToUser(any(), any(), any(), any());
      RuntimeSheet rs = mock(RuntimeSheet.class);
      when(rs.getSocketSessionId()).thenReturn("stomp-1");

      assertDoesNotThrow(() -> service(dispatcher).focusSheet(rs, "Worksheet/foo-7"));
   }

   /**
    * A worksheet save reloads each dependent viewsheet runtime and repaints it with
    * {@code broadcastRefresh}; that, and every other refresh, must stay a repaint only.
    */
   @Test
   void aRefreshBroadcastNeverSendsTheFocusHint() {
      CommandDispatcherService dispatcher = TestDispatchers.live();
      SheetAgentBroadcastService svc = service(dispatcher);

      RuntimeViewsheet rvs = mock(RuntimeViewsheet.class);
      when(rvs.getSocketSessionId()).thenReturn("stomp-vs-1");
      when(rvs.getSocketUserName()).thenReturn("alice~;~host-org");
      Viewsheet vs = mock(Viewsheet.class);
      when(vs.getAssemblies()).thenReturn(new inetsoft.uql.asset.Assembly[0]);
      when(rvs.getViewsheet()).thenReturn(vs);
      RuntimeWorksheet rws = mock(RuntimeWorksheet.class);
      when(rws.getSocketSessionId()).thenReturn("stomp-ws-1");
      when(rws.getSocketUserName()).thenReturn("alice~;~host-org");

      svc.broadcastRefresh(rvs, SheetType.VIEWSHEET, "Viewsheet/bar-9",
                           TestPrincipals.user("alice", "host-org"));
      svc.broadcastRefresh(rws, SheetType.WORKSHEET, "Worksheet/foo-7",
                           TestPrincipals.user("alice", "host-org"));

      verify(dispatcher, never()).convertAndSendToUser(
         any(), eq(ComposerClientService.COMMANDS_TOPIC), any(), any());
   }
}
