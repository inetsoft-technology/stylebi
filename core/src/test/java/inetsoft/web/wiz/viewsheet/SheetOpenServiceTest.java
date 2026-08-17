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
package inetsoft.web.wiz.viewsheet;

import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.report.composition.WorksheetService;
import inetsoft.sree.security.ResourceAction;
import inetsoft.sree.security.ResourceType;
import inetsoft.sree.security.SecurityProvider;
import inetsoft.uql.asset.AssetEntry;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.web.composer.command.OpenComposerAssetCommand;
import inetsoft.web.wiz.pairing.JoinSession;
import inetsoft.web.wiz.pairing.SheetAgentBroadcastService;
import inetsoft.web.wiz.pairing.SheetSessionService;
import inetsoft.web.wiz.pairing.SheetType;
import inetsoft.web.viewsheet.model.VSObjectModelFactoryService;
import inetsoft.web.viewsheet.service.ComposerClientService;
import inetsoft.web.viewsheet.service.CommandDispatcherService;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.messaging.MessageHeaders;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;

import java.security.Principal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@Tag("core")
class SheetOpenServiceTest {
   /**
    * Three identities, deliberately different.
    *
    * <p>The session's {@code ownerIdentity}, the browser's socket user name, and the agent's own
    * principal name are separate concepts that happen to coincide in practice. Holding them apart
    * here is what lets these tests prove <em>which</em> one each guard keys on: with all three set
    * to the same string, a lookup on the wrong one still passes.
    */
   private static final String OWNER = "alice~;~host-org";
   private static final String SOCKET_USER = "alice-browser";

   /**
    * Populated by {@code serviceWithBase} so tests can verify the broadcast the happy path sends
    * to the browser, without threading a mock through every call site.
    */
   private SheetAgentBroadcastService broadcast;

   /** Populated by {@code serviceWithBase} so tests can assert which principal opened the runtime. */
   private WorksheetService worksheetService;

   /**
    * The principal that owns the paired viewsheet runtime — i.e. the user's browser session.
    * Deliberately a different object from {@link #principal()}, the agent: the two are the same
    * logical user but different sessions, and the ownership check compares sessions.
    */
   private static final Principal BROWSER_PRINCIPAL = () -> OWNER;

   /**
    * Necessarily equal to {@link #OWNER}: {@code SheetSessionService.resolve} refuses a session
    * whose {@code ownerIdentity} differs from the agent's identity, so a session where these two
    * differ cannot exist. A review asked for distinct values here to prove which one the
    * already-held guard keys on; that is not constructible, so the argument-specific stub in
    * {@code serviceWithBase} carries that proof instead.
    */
   private static final String PRINCIPAL_NAME = OWNER;

   @Test
   void refusesWhenTheViewsheetHasNoBaseWorksheet() {
      SheetOpenService service = serviceWithBase(null, true, null);

      IllegalArgumentException thrown = assertThrows(
         IllegalArgumentException.class, () -> service.openBaseWorksheet("tok-vs", principal()));

      assertTrue(thrown.getMessage().contains("no base worksheet"), thrown.getMessage());
   }

   /**
    * A logical model or data source is a legitimate base and is NOT a worksheet. The message must
    * name what it actually is, or the caller retries the same call expecting a different answer.
    */
   @Test
   void refusesWhenTheBaseIsNotAWorksheetAndNamesTheActualType() {
      AssetEntry logicModel = mock(AssetEntry.class);
      when(logicModel.isWorksheet()).thenReturn(false);
      when(logicModel.getType()).thenReturn(AssetEntry.Type.LOGIC_MODEL);
      SheetOpenService service = serviceWithBase(logicModel, true, null);

      IllegalArgumentException thrown = assertThrows(
         IllegalArgumentException.class, () -> service.openBaseWorksheet("tok-vs", principal()));

      assertTrue(thrown.getMessage().contains("LOGIC_MODEL"), thrown.getMessage());
   }

   @Test
   void refusesWhenTheAgentLacksWorksheetPermission() {
      SheetOpenService service = serviceWithBase(worksheetEntry(), false, null);

      IllegalArgumentException thrown = assertThrows(
         IllegalArgumentException.class, () -> service.openBaseWorksheet("tok-vs", principal()));

      assertTrue(thrown.getMessage().toLowerCase().contains("permission"), thrown.getMessage());
   }

   /**
    * Silently replacing a held session is the defect that shipped once already: it ends a session
    * the user may be editing, with a success response.
    */
   @Test
   void refusesWhenAWorksheetSessionIsAlreadyHeldAndNamesItsRuntimeId() {
      SheetOpenService service = serviceWithBase(worksheetEntry(), true, "ws-existing");

      IllegalArgumentException thrown = assertThrows(
         IllegalArgumentException.class, () -> service.openBaseWorksheet("tok-vs", principal()));

      assertTrue(thrown.getMessage().contains("ws-existing"), thrown.getMessage());
      assertTrue(thrown.getMessage().contains("detach_sheet"), thrown.getMessage());
      // WHICH identity the lookup keys on is pinned by the helper's stub, which is argument-
      // specific: findOpen is stubbed for OWNER alone, so a call with any other value returns
      // null, the guard does not fire, and this test fails with no exception thrown.
   }

   /**
    * A session with no recorded socket cannot reach a browser, so the worksheet would open
    * nowhere. Refusing beats opening a runtime the user never sees and cannot close.
    */
   @Test
   void refusesWhenTheViewsheetSessionHasNoSocketSession() {
      SheetOpenService service = serviceWithBase(worksheetEntry(), true, null, null);

      IllegalArgumentException thrown = assertThrows(
         IllegalArgumentException.class, () -> service.openBaseWorksheet("tok-vs", principal()));

      assertTrue(thrown.getMessage().toLowerCase().contains("re-pair"), thrown.getMessage());
   }

   /**
    * The single most important assertion in this feature. Without the viewsheet session's socket
    * identifiers on the new grant, the agent's worksheet edits never broadcast and the user
    * watches a stale sheet -- the divergence failure the consolidation existed to remove.
    */
   @Test
   void theNewSessionCarriesTheViewsheetSessionsSocketIdentifiers() throws Exception {
      SheetOpenService service = serviceWithBase(worksheetEntry(), true, null);

      JoinSession opened = service.openBaseWorksheet("tok-vs", principal());

      assertEquals("sock-1", opened.socketSessionId());
      assertEquals(SOCKET_USER, opened.socketUserName());
      assertEquals(SheetType.WORKSHEET, opened.sheetType());
   }

   @Test
   void pushesAnOpenCommandCarryingTheServerCreatedRuntimeId() throws Exception {
      SheetOpenService service = serviceWithBase(worksheetEntry(), true, null);

      service.openBaseWorksheet("tok-vs", principal());

      ArgumentCaptor<Object> command = ArgumentCaptor.forClass(Object.class);
      verify(broadcast).sendToComposer(eq("sock-1"), command.capture());

      OpenComposerAssetCommand sent = (OpenComposerAssetCommand) command.getValue();
      assertEquals("ws-runtime-1", sent.runtimeId(), "the browser must attach, not open its own");
      assertFalse(sent.viewsheet());
   }

   /**
    * Which STOMP destination the command lands on -- the assertion whose absence let this ship
    * broken.
    *
    * <p>{@code OpenComposerAssetCommand} has exactly one handler in the client: composer-main,
    * fed by a subscription to {@code /user/composer-client}. Published instead to
    * {@link inetsoft.web.viewsheet.service.CommandDispatcher#COMMANDS_TOPIC} ({@code "/commands"})
    * it reaches the per-sheet client, which has no handler for it, and is dropped with no error --
    * the user's Composer opens no tab while every call up the stack still reports success.
    *
    * <p>Both constants are named {@code COMMANDS_TOPIC}, on different classes, which is how the
    * wrong one survived review. This test drives the <b>real</b> broadcast service over a mocked
    * dispatcher: mocking the broadcast service is what made the sibling test above blind, since
    * the destination is chosen inside the very method that was stubbed.
    */
   @Test
   void theOpenCommandGoesToTheComposerClientTopicNotTheSheetRuntimeTopic() throws Exception {
      CommandDispatcherService dispatcher = mock(CommandDispatcherService.class);
      SheetAgentBroadcastService realBroadcast = new SheetAgentBroadcastService(
         dispatcher, mock(VSObjectModelFactoryService.class));
      SheetOpenService service =
         serviceWithBase(worksheetEntry(), true, null, "sock-1", realBroadcast);

      service.openBaseWorksheet("tok-vs", principal());

      ArgumentCaptor<MessageHeaders> headers = ArgumentCaptor.forClass(MessageHeaders.class);
      verify(dispatcher).convertAndSendToUser(
         eq("sock-1"), eq(ComposerClientService.COMMANDS_TOPIC),
         any(OpenComposerAssetCommand.class), headers.capture());

      assertEquals("sock-1", SimpMessageHeaderAccessor.getSessionId(headers.getValue()),
                   "must be delivered to the paired browser's socket session");
   }

   /**
    * Who owns the new runtime. The browser must, not the agent.
    *
    * <p>{@code WorksheetEngine.getSheet} rejects a principal that does not match the runtime's
    * owner unless it carries {@code pairedAgent} or {@code supportLogin}. The agent carries
    * {@code pairedAgent}; the user's browser carries neither. Opening the runtime as the agent
    * therefore makes the <em>browser</em> the outsider, and its attach dies on "Invalid user
    * found" — two principals for the same admin differing only by session id.
    *
    * <p>A paired viewsheet already has this the right way round: the browser opened it and the
    * agent reaches it through the flag. Mirroring that keeps one rule for both sheet types.
    */
   @Test
   void opensTheRuntimeAsTheViewsheetsOwnerSoTheBrowserCanAttach() throws Exception {
      SheetOpenService service = serviceWithBase(worksheetEntry(), true, null);

      service.openBaseWorksheet("tok-vs", principal());

      verify(worksheetService).openWorksheet(any(AssetEntry.class), same(BROWSER_PRINCIPAL));
   }

   // ── harness ───────────────────────────────────────────────────────────────

   /** The four-guard helper. Defaults the viewsheet session's socketSessionId to {@code "sock-1"}. */
   private SheetOpenService serviceWithBase(AssetEntry base, boolean hasPermission,
                                             String heldWorksheetRuntimeId)
   {
      return serviceWithBase(base, hasPermission, heldWorksheetRuntimeId, "sock-1");
   }

   private SheetOpenService serviceWithBase(AssetEntry base, boolean hasPermission,
                                             String heldWorksheetRuntimeId,
                                             String socketSessionId)
   {
      return serviceWithBase(base, hasPermission, heldWorksheetRuntimeId, socketSessionId, null);
   }

   /**
    * @param broadcastService the collaborator to inject, or {@code null} for a mock. Pass a real
    *                         one to assert on what actually reaches the dispatcher.
    */
   private SheetOpenService serviceWithBase(AssetEntry base, boolean hasPermission,
                                             String heldWorksheetRuntimeId,
                                             String socketSessionId,
                                             SheetAgentBroadcastService broadcastService)
   {
      try {
         Viewsheet vs = mock(Viewsheet.class);
         when(vs.getBaseEntry()).thenReturn(base);

         RuntimeViewsheet rvs = mock(RuntimeViewsheet.class);
         when(rvs.getViewsheet()).thenReturn(vs);
         when(rvs.getUser()).thenReturn(BROWSER_PRINCIPAL);

         JoinSession vsSession = new JoinSession(
            "tok-vs", "vs-runtime-1", OWNER, SheetType.VIEWSHEET, 0L,
            SheetSessionService.TTL_MILLIS, JoinSession.ConnectionMode.PAIRED,
            socketSessionId, SOCKET_USER);

         ViewsheetSessionService viewsheetSessions = mock(ViewsheetSessionService.class);
         when(viewsheetSessions.requireSession(anyString(), any(Principal.class)))
            .thenReturn(vsSession);
         when(viewsheetSessions.resolve(anyString(), any(Principal.class))).thenReturn(rvs);

         SheetSessionService sheetSessions = mock(SheetSessionService.class);
         JoinSession held = heldWorksheetRuntimeId == null ? null : new JoinSession(
            "tok-ws-held", heldWorksheetRuntimeId, OWNER, SheetType.WORKSHEET, 0L,
            SheetSessionService.TTL_MILLIS, JoinSession.ConnectionMode.PAIRED, "sock-1", SOCKET_USER);
         // Stubbed for OWNER only. If the guard ever keys the lookup on the agent's principal name
         // instead of the session's ownerIdentity, this returns null and the test fails -- which is
         // the point of keeping the two values distinct.
         when(sheetSessions.findOpen(OWNER, SheetType.WORKSHEET)).thenReturn(held);

         worksheetService = mock(WorksheetService.class);
         when(worksheetService.openWorksheet(any(AssetEntry.class), any(Principal.class)))
            .thenReturn("ws-runtime-1");

         SecurityProvider securityProvider = mock(SecurityProvider.class);
         when(securityProvider.checkPermission(any(Principal.class), eq(ResourceType.WORKSHEET),
                                                eq("*"), eq(ResourceAction.ACCESS)))
            .thenReturn(hasPermission);

         // The new worksheet session must carry the viewsheet session's socket identifiers, not
         // the agent's principal -- stubbed argument-specific so a call with the wrong identity
         // returns null and the happy-path tests fail loudly.
         JoinSession newWsSession = new JoinSession(
            "tok-ws-new", "ws-runtime-1", OWNER, SheetType.WORKSHEET, 0L,
            SheetSessionService.TTL_MILLIS, JoinSession.ConnectionMode.PAIRED,
            socketSessionId, SOCKET_USER);
         when(sheetSessions.open(eq("ws-runtime-1"), eq(OWNER), eq(SheetType.WORKSHEET),
                                 eq(socketSessionId), eq(SOCKET_USER)))
            .thenReturn(newWsSession);

         broadcast = broadcastService == null
            ? mock(SheetAgentBroadcastService.class) : broadcastService;

         return new SheetOpenService(viewsheetSessions, sheetSessions, worksheetService,
                                      securityProvider, broadcast);
      }
      catch(Exception e) {
         throw new IllegalStateException(e);
      }
   }

   private static AssetEntry worksheetEntry() {
      AssetEntry entry = mock(AssetEntry.class);
      when(entry.isWorksheet()).thenReturn(true);
      when(entry.getType()).thenReturn(AssetEntry.Type.WORKSHEET);
      return entry;
   }

   private static Principal principal() {
      return () -> PRINCIPAL_NAME;
   }
}
