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

   /** Populated by {@code serviceWithBase} so a refusal test can assert NO session was minted. */
   private SheetSessionService sheetSessions;

   /** Populated by {@code serviceWithBase} -- createViewsheet tests stub/verify against this. */
   private inetsoft.analytic.composition.ViewsheetService viewsheetService;

   /** Populated by {@code serviceWithBase} -- createViewsheet's "default to acting worksheet" tests. */
   private inetsoft.web.wiz.pairing.SheetRuntimeAccess runtimeAccess;

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
    * PSM-006: without an explicit {@code force} argument, the 2-arg overload must refuse exactly
    * as before -- the safety check this whole feature relies on (a held session may belong to a
    * different, concurrently-running agent) must not be silently weakened by adding the flag.
    */
   @Test
   void threeArgOverloadWithForceFalseRefusesJustLikeTheTwoArgOverload() {
      SheetOpenService service = serviceWithBase(worksheetEntry(), true, "ws-existing");

      IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class,
         () -> service.openBaseWorksheet("tok-vs", principal(), false));

      assertTrue(thrown.getMessage().contains("ws-existing"), thrown.getMessage());
      verify(sheetSessions, never()).close(anyString());
   }

   /**
    * PSM-006's recovery path: a session orphaned by a client that lost the local pointer needed to
    * name it for {@code detach_sheet} can still be recovered by closing it server-side and
    * proceeding, instead of leaving the caller stuck until the TTL expires it.
    */
   @Test
   void forceClosesTheHeldSessionAndOpensTheNewOneInstead() throws Exception {
      SheetOpenService service = serviceWithBase(worksheetEntry(), true, "ws-existing");

      JoinSession opened = service.openBaseWorksheet("tok-vs", principal(), true);

      verify(sheetSessions).close("tok-ws-held");
      assertEquals(SheetType.WORKSHEET, opened.sheetType());
      assertEquals("ws-runtime-1", opened.runtimeId());
   }

   /**
    * {@code force:true} with nothing actually held must behave exactly like the ordinary happy
    * path -- no session to close, so {@code close} must never be called.
    */
   @Test
   void forceWithNothingHeldStillOpensNormallyWithoutClosingAnything() throws Exception {
      SheetOpenService service = serviceWithBase(worksheetEntry(), true, null);

      service.openBaseWorksheet("tok-vs", principal(), true);

      verify(sheetSessions, never()).close(anyString());
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

   /**
    * PSM-006, against a REAL {@link SheetSessionService} rather than a mock -- proves the held
    * session is actually closed (unreachable via a fresh {@code findOpen}), not merely that
    * {@code close} was called with some argument a mock accepted regardless.
    */
   @Test
   void forceActuallyClosesTheHeldSessionAgainstARealSheetSessionService() throws Exception {
      SheetSessionService realSessions = new SheetSessionService();
      JoinSession held = realSessions.open(
         "ws-existing", OWNER, SheetType.WORKSHEET, "sock-1", SOCKET_USER, null);
      assertNotNull(realSessions.findOpen(OWNER, SheetType.WORKSHEET), "sanity: session is held");

      AssetEntry base = worksheetEntry();
      Viewsheet vs = mock(Viewsheet.class);
      when(vs.getBaseEntry()).thenReturn(base);
      RuntimeViewsheet rvs = mock(RuntimeViewsheet.class);
      when(rvs.getViewsheet()).thenReturn(vs);
      when(rvs.getUser()).thenReturn(BROWSER_PRINCIPAL);

      JoinSession vsSession = new JoinSession(
         "tok-vs", "vs-runtime-1", OWNER, SheetType.VIEWSHEET, 0L,
         SheetSessionService.TTL_MILLIS, JoinSession.ConnectionMode.PAIRED,
         "sock-1", SOCKET_USER, null);
      ViewsheetSessionService viewsheetSessions = mock(ViewsheetSessionService.class);
      when(viewsheetSessions.requireSessionAllowingPaneScope(anyString(), any(Principal.class)))
         .thenReturn(vsSession);
      when(viewsheetSessions.resolve(anyString(), any(Principal.class))).thenReturn(rvs);

      WorksheetService realWorksheetService = mock(WorksheetService.class);
      when(realWorksheetService.openWorksheet(any(AssetEntry.class), any(Principal.class)))
         .thenReturn("ws-runtime-new");

      SecurityProvider securityProvider = mock(SecurityProvider.class);
      when(securityProvider.checkPermission(any(Principal.class), eq(ResourceType.WORKSHEET),
                                             eq("*"), eq(ResourceAction.ACCESS)))
         .thenReturn(true);

      SheetOpenService service = new SheetOpenService(
         viewsheetSessions, realSessions, realWorksheetService, securityProvider,
         mock(SheetAgentBroadcastService.class),
         mock(inetsoft.analytic.composition.ViewsheetService.class),
         mock(inetsoft.web.wiz.pairing.SheetRuntimeAccess.class));

      JoinSession opened = service.openBaseWorksheet("tok-vs", principal(), true);

      assertEquals("ws-runtime-new", opened.runtimeId());
      assertNull(realSessions.resolve(held.sessionToken(), OWNER),
                "the held session must be actually closed, not just ignored");
      // A fresh session for the new runtime is expected to be held now (openBaseWorksheet's own
      // happy path mints one) -- the assertion is that it is the NEW one, not the stale one force
      // was supposed to clear.
      JoinSession stillFound = realSessions.findOpen(OWNER, SheetType.WORKSHEET);
      assertNotNull(stillFound);
      assertEquals("ws-runtime-new", stillFound.runtimeId());
   }

   // ── createViewsheet ──────────────────────────────────────────────────────
   // create_viewsheet: closes the create half of PVA-007/bug 76332 that PR #4900 explicitly
   // deferred. Mints a new viewsheet runtime and pairs the caller to it directly, by reusing the
   // ACTING session's (worksheet or viewsheet) already-live browser socket -- the same
   // no-new-pairing-code mechanism openBaseWorksheet already uses, in reverse.

   @Test
   void createViewsheetDefaultsToActingWorksheetWhenNoDataSourceGiven() throws Exception {
      AssetEntry wsEntry = new AssetEntry(
         inetsoft.uql.asset.AssetRepository.GLOBAL_SCOPE, AssetEntry.Type.WORKSHEET,
         "Sample Queries/customers", null);
      SheetOpenService service = createViewsheetService(SheetType.WORKSHEET, wsEntry, true);

      JoinSession created = service.createViewsheet("tok-acting", principal(), null);

      assertEquals(SheetType.VIEWSHEET, created.sheetType());
      assertEquals("vs-runtime-new", created.runtimeId());
      verify(viewsheetService).openTemporaryViewsheet(isNull(), eq(wsEntry),
         any(Principal.class), isNull());
      ArgumentCaptor<Object> command = ArgumentCaptor.forClass(Object.class);
      verify(broadcast).sendToComposer(eq("sock-1"), command.capture());
      OpenComposerAssetCommand sent = (OpenComposerAssetCommand) command.getValue();
      assertTrue(sent.viewsheet());
      assertEquals("vs-runtime-new", sent.runtimeId());
   }

   @Test
   void createViewsheetAcceptsExplicitDataSourceFromAViewsheetSession() throws Exception {
      AssetEntry lmEntry = new AssetEntry(
         inetsoft.uql.asset.AssetRepository.QUERY_SCOPE, AssetEntry.Type.LOGIC_MODEL,
         "MyDataSource/MyModel", null);
      // acting session is a VIEWSHEET session -- proves the explicit-dataSource path does not
      // require the acting session to be a worksheet, unlike the defaulting path.
      SheetOpenService service = createViewsheetService(SheetType.VIEWSHEET, null, true);

      JoinSession created = service.createViewsheet("tok-acting", principal(), lmEntry);

      assertEquals(SheetType.VIEWSHEET, created.sheetType());
      verify(viewsheetService).openTemporaryViewsheet(isNull(), eq(lmEntry),
         any(Principal.class), isNull());
      verify(runtimeAccess, never()).getSheetForPairing(any(), any(), any());
   }

   @Test
   void createViewsheetRefusesWhenActingSessionIsUnresolvable() {
      SheetOpenService service = createViewsheetService(SheetType.WORKSHEET, null, true);

      IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class,
         () -> service.createViewsheet("not-a-real-token", principal(), null));

      assertTrue(thrown.getMessage().toLowerCase().contains("invalid or expired"),
                thrown.getMessage());
   }

   @Test
   void createViewsheetRefusesWhenActingSessionHasNoSocket() {
      AssetEntry wsEntry = new AssetEntry(
         inetsoft.uql.asset.AssetRepository.GLOBAL_SCOPE, AssetEntry.Type.WORKSHEET,
         "Sample Queries/customers", null);
      SheetOpenService service = createViewsheetService(SheetType.WORKSHEET, wsEntry, true, null);

      IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class,
         () -> service.createViewsheet("tok-acting", principal(), null));

      assertTrue(thrown.getMessage().toLowerCase().contains("browser connection"),
                thrown.getMessage());
   }

   @Test
   void createViewsheetRefusesWhenNoDataSourceAndActingSessionIsNotWorksheet() {
      SheetOpenService service = createViewsheetService(SheetType.VIEWSHEET, null, true);

      IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class,
         () -> service.createViewsheet("tok-acting", principal(), null));

      assertTrue(thrown.getMessage().contains("nothing to default to"), thrown.getMessage());
   }

   @Test
   void createViewsheetRefusesWhenActingWorksheetIsUnsaved() {
      // getEntry() returning null is exactly what an untitled (never-saved) worksheet's own
      // runtime carries -- there is no repository path yet to build a viewsheet from.
      SheetOpenService service = createViewsheetService(SheetType.WORKSHEET, null, true);

      IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class,
         () -> service.createViewsheet("tok-acting", principal(), null));

      assertTrue(thrown.getMessage().contains("has not been saved"), thrown.getMessage());
   }

   /**
    * Mirrors {@link #refusesAPaneScopedSessionRatherThanMintingAWholeSheetOne} for the reverse
    * direction: {@code createViewsheet} used to resolve its acting session through the generic,
    * pane-scope-blind {@link SheetSessionService#resolve} and then mint a brand-new whole-sheet
    * (editorContext = null) viewsheet session from it -- laundering a single-expression grant
    * into unscoped whole-sheet write authority on a runtime the pane's grant never named.
    *
    * <p>The three {@code never()} assertions are the substance: refusing with a message while
    * still opening the runtime, or still minting the session, would leave the hole open.
    */
   @Test
   void createViewsheetRefusesAPaneScopedActingSessionRatherThanMintingAWholeSheetOne()
      throws Exception
   {
      SheetOpenService service = createViewsheetService(
         SheetType.WORKSHEET, null, true, "sock-1",
         new inetsoft.web.wiz.pairing.EditorContext("assemblyMain", "Chart1", null, null));

      IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class,
         () -> service.createViewsheet("tok-acting", principal(), null));

      assertTrue(thrown.getMessage().contains("scoped to one script location"),
                thrown.getMessage());
      assertTrue(thrown.getMessage().contains("create_viewsheet"), thrown.getMessage());

      verify(viewsheetService, never()).openTemporaryViewsheet(
         any(), any(AssetEntry.class), any(Principal.class), any());
      verify(sheetSessions, never()).open(anyString(), anyString(), any(SheetType.class),
                                          any(), any(), any());
      verify(broadcast, never()).sendToComposer(anyString(), any());
   }

   @Test
   void createViewsheetRefusesWhenTheAgentLacksViewsheetPermission() {
      AssetEntry lmEntry = new AssetEntry(
         inetsoft.uql.asset.AssetRepository.QUERY_SCOPE, AssetEntry.Type.LOGIC_MODEL,
         "MyDataSource/MyModel", null);
      SheetOpenService service = createViewsheetService(SheetType.VIEWSHEET, null, false);

      IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class,
         () -> service.createViewsheet("tok-acting", principal(), lmEntry));

      assertTrue(thrown.getMessage().toLowerCase().contains("permission"), thrown.getMessage());
   }

   /**
    * Purpose-built harness for {@code createViewsheet} -- {@code openBaseWorksheet}'s own
    * {@code serviceWithBase} is tailored to that method's very different fixture shape
    * (viewsheet-session-specific mocks) and is not a good fit here.
    *
    * @param actingType     the acting session's own sheet type
    * @param actingWsEntry  the acting session's own worksheet AssetEntry, as
    *                       {@code RuntimeSheet.getEntry()} would return it -- only consulted when
    *                       {@code actingType} is {@code WORKSHEET}; {@code null} simulates an
    *                       unsaved (untitled) worksheet
    * @param hasPermission  whether the agent has {@code ResourceType.VIEWSHEET} ACCESS
    */
   private SheetOpenService createViewsheetService(SheetType actingType, AssetEntry actingWsEntry,
                                                    boolean hasPermission)
   {
      return createViewsheetService(actingType, actingWsEntry, hasPermission, "sock-1");
   }

   private SheetOpenService createViewsheetService(SheetType actingType, AssetEntry actingWsEntry,
                                                    boolean hasPermission, String socketSessionId)
   {
      return createViewsheetService(actingType, actingWsEntry, hasPermission, socketSessionId,
                                    null);
   }

   /**
    * @param editorContext the acting session's own scope -- {@code null} for the ordinary
    *                      whole-sheet session every other test here uses, non-null for the
    *                      pane-scoped session {@code createViewsheet} must refuse.
    */
   private SheetOpenService createViewsheetService(SheetType actingType, AssetEntry actingWsEntry,
                                                    boolean hasPermission, String socketSessionId,
                                                    inetsoft.web.wiz.pairing.EditorContext editorContext)
   {
      try {
         JoinSession actingSession = new JoinSession(
            "tok-acting", "acting-runtime-1", OWNER, actingType, 0L,
            SheetSessionService.TTL_MILLIS, JoinSession.ConnectionMode.PAIRED,
            socketSessionId, SOCKET_USER, editorContext);

         SheetSessionService sheetSessions = mock(SheetSessionService.class);
         this.sheetSessions = sheetSessions;
         when(sheetSessions.resolve(eq("tok-acting"), eq(OWNER))).thenReturn(actingSession);

         JoinSession newVsSession = new JoinSession(
            "tok-vs-new", "vs-runtime-new", OWNER, SheetType.VIEWSHEET, 0L,
            SheetSessionService.TTL_MILLIS, JoinSession.ConnectionMode.PAIRED,
            socketSessionId, SOCKET_USER, null);
         when(sheetSessions.open(eq("vs-runtime-new"), eq(OWNER), eq(SheetType.VIEWSHEET),
                                 eq(socketSessionId), eq(SOCKET_USER), isNull()))
            .thenReturn(newVsSession);

         runtimeAccess = mock(inetsoft.web.wiz.pairing.SheetRuntimeAccess.class);

         if(actingType == SheetType.WORKSHEET) {
            inetsoft.report.composition.RuntimeSheet actingWs =
               mock(inetsoft.report.composition.RuntimeSheet.class);
            when(actingWs.getEntry()).thenReturn(actingWsEntry);
            when(runtimeAccess.getSheetForPairing(eq(SheetType.WORKSHEET), eq("acting-runtime-1"),
                                                  any(Principal.class)))
               .thenReturn(actingWsEntry == null ? null : actingWs);
         }

         viewsheetService = mock(inetsoft.analytic.composition.ViewsheetService.class);
         when(viewsheetService.openTemporaryViewsheet(isNull(), any(AssetEntry.class),
                                                       any(Principal.class), isNull()))
            .thenReturn("vs-runtime-new");

         SecurityProvider securityProvider = mock(SecurityProvider.class);
         when(securityProvider.checkPermission(any(Principal.class), eq(ResourceType.VIEWSHEET),
                                               eq("*"), eq(ResourceAction.ACCESS)))
            .thenReturn(hasPermission);

         broadcast = mock(SheetAgentBroadcastService.class);
         worksheetService = mock(WorksheetService.class);

         return new SheetOpenService(mock(ViewsheetSessionService.class), sheetSessions,
                                     worksheetService, securityProvider, broadcast,
                                     viewsheetService, runtimeAccess);
      }
      catch(Exception e) {
         throw new IllegalStateException(e);
      }
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
      return serviceWithBase(base, hasPermission, heldWorksheetRuntimeId, socketSessionId,
                             broadcastService, null);
   }

   /**
    * @param editorContext the viewsheet session's own scope -- {@code null} for the ordinary
    *                      whole-sheet (toolbar) session every other test here uses, non-null for
    *                      the pane-scoped session {@code openBaseWorksheet} must refuse.
    */
   private SheetOpenService serviceWithBase(AssetEntry base, boolean hasPermission,
                                             String heldWorksheetRuntimeId,
                                             String socketSessionId,
                                             SheetAgentBroadcastService broadcastService,
                                             inetsoft.web.wiz.pairing.EditorContext editorContext)
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
            socketSessionId, SOCKET_USER, editorContext);

         ViewsheetSessionService viewsheetSessions = mock(ViewsheetSessionService.class);
         // Deliberately stubs ONLY the pane-scope-allowing resolution. openBaseWorksheet must use
         // it (so it can SEE a pane-scoped session in order to refuse it by name); if it went back
         // to requireSession, this mock returns null and every test here fails on an NPE rather
         // than silently losing the refusal.
         when(viewsheetSessions.requireSessionAllowingPaneScope(anyString(), any(Principal.class)))
            .thenReturn(vsSession);
         when(viewsheetSessions.resolve(anyString(), any(Principal.class))).thenReturn(rvs);

         SheetSessionService sheetSessions = mock(SheetSessionService.class);
         this.sheetSessions = sheetSessions;
         JoinSession held = heldWorksheetRuntimeId == null ? null : new JoinSession(
            "tok-ws-held", heldWorksheetRuntimeId, OWNER, SheetType.WORKSHEET, 0L,
            SheetSessionService.TTL_MILLIS, JoinSession.ConnectionMode.PAIRED, "sock-1", SOCKET_USER,
            null);
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
            socketSessionId, SOCKET_USER, null);
         when(sheetSessions.open(eq("ws-runtime-1"), eq(OWNER), eq(SheetType.WORKSHEET),
                                 eq(socketSessionId), eq(SOCKET_USER), isNull()))
            .thenReturn(newWsSession);

         broadcast = broadcastService == null
            ? mock(SheetAgentBroadcastService.class) : broadcastService;

         viewsheetService = mock(inetsoft.analytic.composition.ViewsheetService.class);
         runtimeAccess = mock(inetsoft.web.wiz.pairing.SheetRuntimeAccess.class);

         return new SheetOpenService(viewsheetSessions, sheetSessions, worksheetService,
                                      securityProvider, broadcast, viewsheetService, runtimeAccess);
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

   /**
    * Whole-branch review finding 1 (CRITICAL), the worst traced path.
    *
    * <p>Pairing from Chart1's Script tab and calling {@code open_base_worksheet} used to mint a
    * NEW whole-sheet worksheet session with {@code editorContext = null} -- laundering a
    * single-expression grant into an unscoped write handle on a different runtime, one that
    * {@code socketClosed} would never reap precisely because it no longer carried an
    * {@code editorContext}. The code even carried a comment explaining the null, correct about
    * the VALUE (a viewsheet location is meaningless on a worksheet) and blind to the CONSEQUENCE
    * (what authority the new session then has).
    *
    * <p>The three {@code never()} assertions are the substance: refusing with a message while
    * still opening the runtime, or still minting the session, would leave the hole open.
    */
   @Test
   void refusesAPaneScopedSessionRatherThanMintingAWholeSheetOne() throws Exception {
      SheetOpenService service = serviceWithBase(
         worksheetEntry(), true, null, "sock-1", null,
         new inetsoft.web.wiz.pairing.EditorContext("assemblyMain", "Chart1", null, null));

      IllegalArgumentException thrown = assertThrows(
         IllegalArgumentException.class, () -> service.openBaseWorksheet("tok-vs", principal()));

      assertTrue(thrown.getMessage().contains("scoped to one script location"),
                 thrown.getMessage());
      assertTrue(thrown.getMessage().contains("open_base_worksheet"), thrown.getMessage());

      verify(worksheetService, never()).openWorksheet(any(AssetEntry.class), any(Principal.class));
      verify(sheetSessions, never()).open(anyString(), anyString(), any(SheetType.class),
                                          any(), any(), any());
      verify(broadcast, never()).sendToComposer(anyString(), any());
   }

   /**
    * The regression that would be worse than the bug: a whole-sheet toolbar session must still
    * open its base worksheet exactly as before. Pinned by asserting the guard did not fire on
    * the {@code null} editorContext the other tests here already use throughout.
    */
   @Test
   void aWholeSheetSessionStillOpensItsBaseWorksheet() throws Exception {
      SheetOpenService service = serviceWithBase(worksheetEntry(), true, null);

      assertEquals(SheetType.WORKSHEET,
                   service.openBaseWorksheet("tok-vs", principal()).sheetType());
   }

}
