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
import inetsoft.web.viewsheet.command.UpdateUndoStateCommand;
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

      // One RefreshWorksheetCommand plus one UpdateUndoStateCommand (undo-state sync).
      ArgumentCaptor<MessageHeaders> headersCap = ArgumentCaptor.forClass(MessageHeaders.class);
      verify(dispatcher, times(2)).convertAndSendToUser(
         eq("alice~;~host-org"), eq(CommandDispatcher.COMMANDS_TOPIC), any(), headersCap.capture());

      MessageHeaders headers = headersCap.getAllValues().get(0);
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

   /*
    * The destination string and the destination user are the entire contract of this method, and
    * each is one word away from a silent failure: this class's own javadoc records that a wrong
    * topic is delivered to a destination with no handler and dropped while every call up the stack
    * still reports success, and the adjacent sendToComposer genuinely does pass socketSessionId as
    * the user. Asserting the LITERAL strings is what makes either swap fail here — asserting
    * PAIRING_JOINED_TOPIC against itself would test nothing at all.
    */
   @Test
   void pairingJoinedNoticeAddressesTheMintingBrowser() {
      CommandDispatcherService dispatcher = mock(CommandDispatcherService.class);
      SheetAgentBroadcastService svc = new SheetAgentBroadcastService(dispatcher, noopModelFactory());
      EditorContext context = new EditorContext("assemblyMain", "Chart1", null, null);
      JoinSession session = new JoinSession("tok-1", "vs-9", "alice~;~host-org",
                                            SheetType.VIEWSHEET, 0L, 0L,
                                            JoinSession.ConnectionMode.PAIRED,
                                            "stomp-1", "alice-dest", context);

      svc.sendPairingJoined(session);

      ArgumentCaptor<MessageHeaders> headersCap = ArgumentCaptor.forClass(MessageHeaders.class);
      ArgumentCaptor<Object> payloadCap = ArgumentCaptor.forClass(Object.class);
      verify(dispatcher).convertAndSendToUser(
         eq("alice-dest"), eq("/commands/wiz/pairing/joined"), payloadCap.capture(),
         headersCap.capture());

      assertEquals("stomp-1", SimpMessageHeaderAccessor.getSessionId(headersCap.getValue()));
      assertEquals(new PairingJoinedNotice("vs-9", SheetType.VIEWSHEET, context),
                   payloadCap.getValue());
   }

   /*
    * sendFocusChanged reuses sendPairingJoined's exact topic/addressing (Follow Focus's design
    * plan: "reusing the same broadcast channel #4669 already wired rather than inventing a second
    * one"), distinguished only by PairingJoinedNotice.focusChanged() -- the client-side signal
    * that this is an already-connected session's target moving, not a fresh join.
    */
   @Test
   void focusChangedNoticeReusesTheJoinedTopicWithTheFlagSet() {
      CommandDispatcherService dispatcher = mock(CommandDispatcherService.class);
      SheetAgentBroadcastService svc = new SheetAgentBroadcastService(dispatcher, noopModelFactory());
      EditorContext context = new EditorContext("viewsheetOnInit", null, null, null);
      JoinSession session = new JoinSession("tok-1", "vs-9", "alice~;~host-org",
                                            SheetType.VIEWSHEET, 0L, 0L,
                                            JoinSession.ConnectionMode.PAIRED,
                                            "stomp-1", "alice-dest", context);

      svc.sendFocusChanged(session);

      ArgumentCaptor<Object> payloadCap = ArgumentCaptor.forClass(Object.class);
      verify(dispatcher).convertAndSendToUser(
         eq("alice-dest"), eq("/commands/wiz/pairing/joined"), payloadCap.capture(), any());

      assertEquals(new PairingJoinedNotice("vs-9", SheetType.VIEWSHEET, context, true),
                   payloadCap.getValue());
   }

   /*
    * A null destination user reaches Spring's Assert.notNull, which the caller logs as "notifying
    * the browser failed" — misleading, since nothing failed to send and there was nobody to send
    * to. Mirrors nullSocketSessionSkipsBroadcast above.
    */
   @Test
   void pairingJoinedNoticeSkippedWithoutADestinationUser() {
      CommandDispatcherService dispatcher = mock(CommandDispatcherService.class);
      SheetAgentBroadcastService svc = new SheetAgentBroadcastService(dispatcher, noopModelFactory());
      JoinSession session = new JoinSession("tok-1", "vs-9", "alice~;~host-org",
                                            SheetType.VIEWSHEET, 0L, 0L,
                                            JoinSession.ConnectionMode.PAIRED,
                                            "stomp-1", null, null);

      svc.sendPairingJoined(session);

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
      // One RefreshVSObjectCommand plus one UpdateUndoStateCommand (undo-state sync).
      verify(dispatcher, times(2)).convertAndSendToUser(
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

      // falls back to owner.getName(); one RefreshWorksheetCommand plus one UpdateUndoStateCommand.
      verify(dispatcher, times(2)).convertAndSendToUser(
         eq(owner.getName()), eq(CommandDispatcher.COMMANDS_TOPIC), any(), any());
   }

   @Test
   void broadcastRefreshSendsUpdateUndoStateCommandWithRuntimeUndoPosition() {
      CommandDispatcherService dispatcher = mock(CommandDispatcherService.class);
      SheetAgentBroadcastService svc = new SheetAgentBroadcastService(dispatcher, noopModelFactory());

      RuntimeWorksheet rs = mock(RuntimeWorksheet.class);
      when(rs.getSocketSessionId()).thenReturn("stomp-1");
      when(rs.getSocketUserName()).thenReturn("alice~;~host-org");
      when(rs.size()).thenReturn(4);
      when(rs.getCurrent()).thenReturn(3);
      when(rs.getSavePoint()).thenReturn(1);
      when(rs.getID()).thenReturn("Worksheet/foo-7");
      Principal owner = TestPrincipals.user("alice", "host-org");

      svc.broadcastRefresh(rs, SheetType.WORKSHEET, "Worksheet/foo-7", owner);

      ArgumentCaptor<Object> commandCap = ArgumentCaptor.forClass(Object.class);
      verify(dispatcher, times(2)).convertAndSendToUser(
         eq("alice~;~host-org"), eq(CommandDispatcher.COMMANDS_TOPIC), commandCap.capture(), any());

      Object undoCommand = commandCap.getAllValues().stream()
         .filter(c -> c instanceof UpdateUndoStateCommand)
         .findFirst()
         .orElseThrow(() -> new AssertionError("No UpdateUndoStateCommand was sent"));

      UpdateUndoStateCommand command = (UpdateUndoStateCommand) undoCommand;
      assertEquals(4, command.getPoints());
      assertEquals(3, command.getCurrent());
      assertEquals(1, command.getSavePoint());
      assertEquals("Worksheet/foo-7", command.getId());
   }

   @Test
   void broadcastSaveSendsUpdateUndoStateCommandSoIndicatorClears() {
      CommandDispatcherService dispatcher = mock(CommandDispatcherService.class);
      SheetAgentBroadcastService svc = new SheetAgentBroadcastService(dispatcher, noopModelFactory());

      RuntimeWorksheet rs = mock(RuntimeWorksheet.class);
      when(rs.getSocketSessionId()).thenReturn("stomp-1");
      when(rs.getSocketUserName()).thenReturn("alice~;~host-org");
      // All-distinct values so the assertions below can catch a swapped/mis-mapped field —
      // e.g. current/savePoint would be indistinguishable if stubbed equal.
      when(rs.size()).thenReturn(6);
      when(rs.getCurrent()).thenReturn(5);
      when(rs.getSavePoint()).thenReturn(2);
      when(rs.getID()).thenReturn("Worksheet/foo-7");
      inetsoft.uql.asset.AssetEntry entry = mock(inetsoft.uql.asset.AssetEntry.class);
      when(entry.toView()).thenReturn("foo-7");
      when(entry.toIdentifier()).thenReturn("1^128^__NULL__^foo-7");
      when(rs.getEntry()).thenReturn(entry);
      Principal owner = TestPrincipals.user("alice", "host-org");

      svc.broadcastSave(rs, "Worksheet/foo-7", owner);

      // SetWorksheetInfoCommand + SaveSheetCommand + UpdateUndoStateCommand.
      ArgumentCaptor<Object> commandCap = ArgumentCaptor.forClass(Object.class);
      verify(dispatcher, times(3)).convertAndSendToUser(
         eq("alice~;~host-org"), eq(CommandDispatcher.COMMANDS_TOPIC), commandCap.capture(), any());

      Object undoCommand = commandCap.getAllValues().stream()
         .filter(c -> c instanceof UpdateUndoStateCommand)
         .findFirst()
         .orElseThrow(() -> new AssertionError("No UpdateUndoStateCommand was sent"));

      // Verify each field is forwarded from the runtime as-is (not swapped/dropped). In real
      // usage the caller sets savePoint == current just before this broadcast, which is what
      // makes the Composer's isModified() (current !== savePoint) false right after a save —
      // this test only verifies broadcastSave's forwarding of whatever the runtime reports.
      UpdateUndoStateCommand command = (UpdateUndoStateCommand) undoCommand;
      assertEquals(6, command.getPoints());
      assertEquals(5, command.getCurrent());
      assertEquals(2, command.getSavePoint());
      assertEquals("Worksheet/foo-7", command.getId());
   }
}
