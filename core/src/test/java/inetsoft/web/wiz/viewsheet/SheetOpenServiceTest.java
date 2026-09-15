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
    * Populated by {@code createViewsheetService} -- the freshly-minted runtime's own real,
    * TEMPORARY_SCOPE entry, so tests can assert the browser gets told to open THIS, not
    * dataSource's own identifier.
    */
   private AssetEntry newVsTempEntry;

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
    * I-1 (agent-sheet-visibility review round 1): {@code open_base_worksheet} mints a genuine new
    * {@link JoinSession} an agent then holds, exactly the situation the Composer tab-bar "agent
    * connected" indicator exists to surface -- but the happy path used to return without ever
    * calling {@code sendAgentActive}, so the icon never appeared for a worksheet opened this way.
    * Mirrors {@code SheetJoinServiceTest.notifiesTabBarOnJoin}.
    */
   @Test
   void notifiesTheTabBarThatAnAgentIsNowAttachedToTheNewWorksheet() throws Exception {
      SheetOpenService service = serviceWithBase(worksheetEntry(), true, null);

      JoinSession opened = service.openBaseWorksheet("tok-vs", principal());

      ArgumentCaptor<JoinSession> sent = ArgumentCaptor.forClass(JoinSession.class);
      verify(broadcast).sendAgentActive(sent.capture());
      assertEquals(opened.runtimeId(), sent.getValue().runtimeId());
   }

   /**
    * A broken tab-bar notification must not fail the open -- best-effort, independent try/catch,
    * mirroring {@code SheetJoinServiceTest.tabBarNotifyFailureDoesNotFailTheJoin}.
    */
   @Test
   void tabBarNotifyFailureDoesNotFailTheOpen() throws Exception {
      SheetAgentBroadcastService flaky = mock(SheetAgentBroadcastService.class);
      doThrow(new RuntimeException("socket gone")).when(flaky).sendAgentActive(any());
      SheetOpenService service = serviceWithBase(worksheetEntry(), true, null, "sock-1", flaky);

      JoinSession opened = service.openBaseWorksheet("tok-vs", principal());

      assertNotNull(opened);
      assertEquals("ws-runtime-1", opened.runtimeId());
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
      // Opens as the acting session's own BROWSER principal (stubbed distinct from the agent's
      // own principal() in this fixture) -- not the agent's -- or the browser's own later
      // attach to this runtime dies on "Invalid user found". See SheetOpenService's own
      // comment on why.
      ArgumentCaptor<Principal> openedAs = ArgumentCaptor.forClass(Principal.class);
      verify(viewsheetService).openTemporaryViewsheet(isNull(), eq(wsEntry),
         openedAs.capture(), isNull());
      assertEquals("browser-" + PRINCIPAL_NAME, openedAs.getValue().getName());
      ArgumentCaptor<Object> command = ArgumentCaptor.forClass(Object.class);
      verify(broadcast).sendToComposer(eq("sock-1"), command.capture());
      OpenComposerAssetCommand sent = (OpenComposerAssetCommand) command.getValue();
      assertTrue(sent.viewsheet());
      assertEquals("vs-runtime-new", sent.runtimeId());
      // The browser is told to open the new runtime's OWN real (TEMPORARY_SCOPE, VIEWSHEET)
      // entry, not dataSource's -- dataSource is a WORKSHEET/LOGIC_MODEL/DATA_SOURCE entry,
      // never Type.VIEWSHEET, so VSLifecycleService.openViewsheet's entry.isViewsheet() check
      // would otherwise always reject the browser's own re-open of it (the root cause this
      // fixes). See SheetOpenService's own comment.
      assertEquals(newVsTempEntry.toIdentifier(), sent.assetId());
      assertNotEquals(wsEntry.toIdentifier(), sent.assetId());
   }

   /**
    * agent-sheet-visibility: create_viewsheet is a third real entry point that attaches a
    * session (alongside SheetJoinService.join and openBaseWorksheet), so it needs the same
    * sendAgentActive notification for the Composer tab-bar "agent connected" indicator to be
    * consistent across all three. Mirrors notifiesTheTabBarThatAnAgentIsNowAttachedToTheNewWorksheet.
    */
   @Test
   void notifiesTheTabBarThatAnAgentIsNowAttachedToTheNewViewsheet() throws Exception {
      AssetEntry wsEntry = new AssetEntry(
         inetsoft.uql.asset.AssetRepository.GLOBAL_SCOPE, AssetEntry.Type.WORKSHEET,
         "Sample Queries/customers", null);
      SheetOpenService service = createViewsheetService(SheetType.WORKSHEET, wsEntry, true);

      JoinSession created = service.createViewsheet("tok-acting", principal(), null);

      ArgumentCaptor<JoinSession> sent = ArgumentCaptor.forClass(JoinSession.class);
      verify(broadcast).sendAgentActive(sent.capture());
      assertEquals(created.runtimeId(), sent.getValue().runtimeId());
   }

   /** A broken tab-bar notification must not fail the create -- best-effort, independent try/catch. */
   @Test
   void tabBarNotifyFailureDoesNotFailTheCreate() throws Exception {
      AssetEntry wsEntry = new AssetEntry(
         inetsoft.uql.asset.AssetRepository.GLOBAL_SCOPE, AssetEntry.Type.WORKSHEET,
         "Sample Queries/customers", null);
      SheetOpenService service = createViewsheetService(SheetType.WORKSHEET, wsEntry, true);
      doThrow(new RuntimeException("socket gone")).when(broadcast).sendAgentActive(any());

      JoinSession created = service.createViewsheet("tok-acting", principal(), null);

      assertNotNull(created);
      assertEquals("vs-runtime-new", created.runtimeId());
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
      // dataSource is passed through unchanged -- explicit dataSource is never defaulted.
      verify(viewsheetService).openTemporaryViewsheet(isNull(), eq(lmEntry),
         any(Principal.class), isNull());
      // getSheetForPairing IS called now (unlike before) -- it's how createViewsheet gets the
      // acting session's own browser principal to open the new runtime as, not the agent's own.
      // See SheetOpenService's own comment on why this must happen for every acting session
      // type, not just a WORKSHEET session defaulting its dataSource.
      verify(runtimeAccess).getSheetForPairing(eq(SheetType.VIEWSHEET), eq("acting-runtime-1"),
         any(Principal.class));
   }

   /**
    * The acting session (a JoinSession, its own 30-minute-TTL store) can outlive its own
    * underlying runtime, which has an independent cache lifecycle -- so getSheetForPairing can
    * throw SESSION_EXPIRED even though nothing is wrong with the request itself. An explicit
    * dataSource never depended on the acting runtime being alive before createViewsheet started
    * fetching it (only for the browser principal); this must not newly fail the call.
    */
   @Test
   void createViewsheetToleratesExpiredActingRuntimeWhenDataSourceIsExplicit() throws Exception {
      AssetEntry lmEntry = new AssetEntry(
         inetsoft.uql.asset.AssetRepository.QUERY_SCOPE, AssetEntry.Type.LOGIC_MODEL,
         "MyDataSource/MyModel", null);
      SheetOpenService service = createViewsheetService(SheetType.VIEWSHEET, null, true);

      when(runtimeAccess.getSheetForPairing(eq(SheetType.VIEWSHEET), eq("acting-runtime-1"),
                                            any(Principal.class)))
         .thenThrow(new inetsoft.web.wiz.pairing.PairingException(
            inetsoft.web.wiz.pairing.PairingException.Kind.SESSION_EXPIRED,
            "Viewsheet runtime not found or expired: acting-runtime-1"));

      // No browser principal is resolvable now -- falls back to the agent's own, the pre-fix
      // behavior for this path.
      Principal agent = principal();
      when(viewsheetService.openTemporaryViewsheet(isNull(), eq(lmEntry), eq(agent), isNull()))
         .thenReturn("vs-runtime-new");
      RuntimeViewsheet newRvs = mock(RuntimeViewsheet.class);
      when(newRvs.getEntry()).thenReturn(newVsTempEntry);
      when(viewsheetService.getViewsheet(eq("vs-runtime-new"), eq(agent))).thenReturn(newRvs);

      JoinSession created = service.createViewsheet("tok-acting", agent, lmEntry);

      assertEquals(SheetType.VIEWSHEET, created.sheetType());
      verify(viewsheetService).openTemporaryViewsheet(isNull(), eq(lmEntry), eq(agent), isNull());
   }

   /**
    * The sibling of the above: defaulting to the acting worksheet's own entry genuinely needs
    * the acting runtime, unlike an explicit dataSource, so an expired acting runtime must still
    * fail loud here rather than silently falling back.
    */
   @Test
   void createViewsheetStillFailsWhenDefaultingAndActingRuntimeIsExpired() throws Exception {
      SheetOpenService service = createViewsheetService(SheetType.WORKSHEET, null, true);
      when(runtimeAccess.getSheetForPairing(eq(SheetType.WORKSHEET), eq("acting-runtime-1"),
                                            any(Principal.class)))
         .thenThrow(new inetsoft.web.wiz.pairing.PairingException(
            inetsoft.web.wiz.pairing.PairingException.Kind.SESSION_EXPIRED,
            "Worksheet runtime not found or expired: acting-runtime-1"));

      assertThrows(inetsoft.web.wiz.pairing.PairingException.class,
         () -> service.createViewsheet("tok-acting", principal(), null));
   }

   @Test
   void createViewsheetRefusesWhenActingSessionIsUnresolvable() {
      SheetOpenService service = createViewsheetService(SheetType.WORKSHEET, null, true);

      IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class,
         () -> service.createViewsheet("not-a-real-token", principal(), null));

      assertTrue(thrown.getMessage().toLowerCase().contains("invalid or expired"),
                thrown.getMessage());
   }

   /**
    * PSP-027: a session established directly at login (no Composer pane ever paired) has
    * {@code socketSessionId() == null} from the moment it is created, and there is no expectation
    * it ever gets one -- but {@code PortalAgentNoticeService}'s {@code /user/composer-client}
    * subscription is exactly what this case needs to reach: an identity-addressed broadcast, not a
    * socket-addressed one. The tab-bar "agent attached" indicator genuinely has no meaning without
    * a specific browser tab to highlight, so {@code sendAgentActive} alone stays skipped; the
    * {@code OpenComposerAssetCommand} must still go out, via
    * {@code sendToComposerByIdentity(ownerIdentity, ...)}, not be silently dropped.
    */
   @Test
   void createViewsheetFallsBackToIdentityAddressedBroadcastWhenActingSessionHasNoSocket()
      throws Exception
   {
      AssetEntry wsEntry = new AssetEntry(
         inetsoft.uql.asset.AssetRepository.GLOBAL_SCOPE, AssetEntry.Type.WORKSHEET,
         "Sample Queries/customers", null);
      SheetOpenService service = createViewsheetService(SheetType.WORKSHEET, wsEntry, true, null);

      JoinSession created = service.createViewsheet("tok-acting", principal(), null);

      assertEquals("vs-runtime-new", created.runtimeId());
      assertNull(created.socketSessionId());
      verify(broadcast, never()).sendAgentActive(any());
      verify(broadcast, never()).sendToComposer(anyString(), any());

      ArgumentCaptor<Object> command = ArgumentCaptor.forClass(Object.class);
      verify(broadcast).sendToComposerByIdentity(eq(OWNER), command.capture());
      OpenComposerAssetCommand sent = (OpenComposerAssetCommand) command.getValue();
      assertEquals("vs-runtime-new", sent.runtimeId());
      assertTrue(sent.viewsheet());
   }

   /**
    * Portal-session-pairing Lane B / D10: the acting session itself may be the still-unattached
    * (runtimeId == null, sheetType == null) session a directly-established login produces, not
    * just an attached-but-socketless one. {@code SheetRuntimeAccess.getSheetForPairing}'s
    * {@code switch(sheetType)} has no default arm and NPEs on a null selector -- createViewsheet
    * must recognize "no runtimeId yet" and skip that call entirely (falling back to the
    * agent-owned-principal path, exactly like an expired attached runtime already does), not rely
    * on catching a PairingException a null selector never throws. Counter-assertion: must not
    * throw an unrelated NullPointerException.
    */
   @Test
   void createViewsheetToleratesAnUnattachedActingSessionWithNoNpe() throws Exception {
      JoinSession unattached = new JoinSession(
         "tok-portal", null, OWNER, null, 0L,
         SheetSessionService.TTL_MILLIS, JoinSession.ConnectionMode.PAIRED,
         null, null, null);

      SheetSessionService sheetSessions = mock(SheetSessionService.class);
      this.sheetSessions = sheetSessions;
      when(sheetSessions.resolve(eq("tok-portal"), eq(OWNER))).thenReturn(unattached);

      AssetEntry lmEntry = new AssetEntry(
         inetsoft.uql.asset.AssetRepository.QUERY_SCOPE, AssetEntry.Type.LOGIC_MODEL,
         "MyDataSource/MyModel", null);

      Principal agent = () -> OWNER;
      viewsheetService = mock(inetsoft.analytic.composition.ViewsheetService.class);
      when(viewsheetService.openTemporaryViewsheet(isNull(), eq(lmEntry), eq(agent), isNull()))
         .thenReturn("vs-runtime-new");
      inetsoft.report.composition.RuntimeViewsheet newRvs =
         mock(inetsoft.report.composition.RuntimeViewsheet.class);
      AssetEntry tempEntry = new AssetEntry(
         inetsoft.uql.asset.AssetRepository.TEMPORARY_SCOPE, AssetEntry.Type.VIEWSHEET,
         "Untitled-1", null);
      when(newRvs.getEntry()).thenReturn(tempEntry);
      when(viewsheetService.getViewsheet(eq("vs-runtime-new"), eq(agent))).thenReturn(newRvs);

      JoinSession newVsSession = new JoinSession(
         "tok-vs-new", "vs-runtime-new", OWNER, SheetType.VIEWSHEET, 0L,
         SheetSessionService.TTL_MILLIS, JoinSession.ConnectionMode.PAIRED,
         null, null, null);
      when(sheetSessions.open(eq("vs-runtime-new"), eq(OWNER), eq(SheetType.VIEWSHEET),
                              isNull(), isNull(), isNull()))
         .thenReturn(newVsSession);

      SecurityProvider securityProvider = mock(SecurityProvider.class);
      when(securityProvider.checkPermission(any(Principal.class), eq(ResourceType.VIEWSHEET),
                                            eq("*"), eq(ResourceAction.ACCESS)))
         .thenReturn(true);

      runtimeAccess = mock(inetsoft.web.wiz.pairing.SheetRuntimeAccess.class);
      broadcast = mock(SheetAgentBroadcastService.class);
      worksheetService = mock(WorksheetService.class);

      SheetOpenService service = new SheetOpenService(
         mock(ViewsheetSessionService.class), sheetSessions, worksheetService, securityProvider,
         broadcast, viewsheetService, runtimeAccess);

      JoinSession created = service.createViewsheet("tok-portal", agent, lmEntry);

      assertEquals("vs-runtime-new", created.runtimeId());
      assertNull(created.socketSessionId());
      verify(runtimeAccess, never()).getSheetForPairing(any(), any(), any());
      verify(viewsheetService).openTemporaryViewsheet(isNull(), eq(lmEntry), eq(agent), isNull());
      verify(broadcast, never()).sendAgentActive(any());
      verify(broadcast, never()).sendToComposer(anyString(), any());
      // PSP-027: still notified, just via the identity-addressed fallback -- see
      // createViewsheetFallsBackToIdentityAddressedBroadcastWhenActingSessionHasNoSocket.
      verify(broadcast).sendToComposerByIdentity(eq(OWNER), any());
   }

   /**
    * Attach-by-path (section 2.5): when {@code dataSource} is itself a {@code Type.VIEWSHEET}
    * entry (resolved by {@code ViewsheetAssemblyAgentController#resolveDataSourceEntry}'s new
    * "viewsheet" branch), createViewsheet must open THAT saved asset directly via
    * {@code viewsheetService.openViewsheet}, never build a new, blank one from it via
    * {@code openTemporaryViewsheet} -- and tell the browser to open the SAME existing asset id,
    * not a freshly-minted temporary entry.
    */
   @Test
   void createViewsheetOpensAnExistingViewsheetDirectlyWhenDataSourceIsAlreadyAViewsheet()
      throws Exception
   {
      AssetEntry vsEntry = new AssetEntry(
         inetsoft.uql.asset.AssetRepository.GLOBAL_SCOPE, AssetEntry.Type.VIEWSHEET,
         "Sample Reports/sales", null);
      SheetOpenService service = createViewsheetService(SheetType.WORKSHEET, null, true);

      when(viewsheetService.openViewsheet(eq(vsEntry), any(Principal.class), eq(true)))
         .thenReturn("vs-runtime-existing");
      JoinSession newVsSession = new JoinSession(
         "tok-vs-existing", "vs-runtime-existing", OWNER, SheetType.VIEWSHEET, 0L,
         SheetSessionService.TTL_MILLIS, JoinSession.ConnectionMode.PAIRED,
         "sock-1", SOCKET_USER, null);
      when(sheetSessions.open(eq("vs-runtime-existing"), eq(OWNER), eq(SheetType.VIEWSHEET),
                              eq("sock-1"), eq(SOCKET_USER), isNull()))
         .thenReturn(newVsSession);

      JoinSession created = service.createViewsheet("tok-acting", principal(), vsEntry);

      assertEquals("vs-runtime-existing", created.runtimeId());
      verify(viewsheetService).openViewsheet(eq(vsEntry), any(Principal.class), eq(true));
      verify(viewsheetService, never())
         .openTemporaryViewsheet(any(), any(AssetEntry.class), any(Principal.class), any());

      ArgumentCaptor<Object> command = ArgumentCaptor.forClass(Object.class);
      verify(broadcast).sendToComposer(eq("sock-1"), command.capture());
      OpenComposerAssetCommand sent = (OpenComposerAssetCommand) command.getValue();
      assertEquals(vsEntry.toIdentifier(), sent.assetId(),
         "the browser must be told to open THIS existing asset, not a freshly-minted temp entry");
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
      verify(broadcast, never()).sendAgentActive(any());
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

   // ── createWorksheet ──────────────────────────────────────────────────────
   // create_worksheet: mints a brand-new, BLANK worksheet runtime and pairs the caller to it
   // directly, by reusing the ACTING session's (worksheet or viewsheet) already-live browser
   // socket -- the exact mechanism createViewsheet already uses in the reverse direction
   // (worksheet/viewsheet -> new viewsheet), applied here to worksheet/viewsheet -> new worksheet.

   @Test
   void createWorksheetMintsABlankWorksheetRuntimeFromAWorksheetActingSession() throws Exception {
      SheetOpenService service = createWorksheetService(SheetType.WORKSHEET, true);

      JoinSession created = service.createWorksheet("tok-acting", principal());

      assertEquals(SheetType.WORKSHEET, created.sheetType());
      assertEquals("ws-runtime-new", created.runtimeId());
   }

   /** Proves the acting session may be EITHER sheet type, exactly like createViewsheet. */
   @Test
   void createWorksheetMintsABlankWorksheetRuntimeFromAViewsheetActingSession() throws Exception {
      SheetOpenService service = createWorksheetService(SheetType.VIEWSHEET, true);

      JoinSession created = service.createWorksheet("tok-acting", principal());

      assertEquals(SheetType.WORKSHEET, created.sheetType());
      assertEquals("ws-runtime-new", created.runtimeId());
   }

   /** The whole point of create_worksheet vs. createViewsheet(dataSource): no asset to point at. */
   @Test
   void createWorksheetOpensATemporaryWorksheetWithNoAssetEntry() throws Exception {
      SheetOpenService service = createWorksheetService(SheetType.WORKSHEET, true);

      service.createWorksheet("tok-acting", principal());

      verify(viewsheetService).openTemporaryWorksheet(any(Principal.class), isNull());
   }

   @Test
   void theNewWorksheetSessionCarriesTheActingSessionsSocketIdentifiers() throws Exception {
      SheetOpenService service = createWorksheetService(SheetType.WORKSHEET, true);

      JoinSession created = service.createWorksheet("tok-acting", principal());

      assertEquals("sock-1", created.socketSessionId());
      assertEquals(SOCKET_USER, created.socketUserName());
   }

   @Test
   void pushesAnOpenCommandMarkedAsAWorksheetCarryingTheServerCreatedRuntimeId() throws Exception {
      SheetOpenService service = createWorksheetService(SheetType.WORKSHEET, true);

      service.createWorksheet("tok-acting", principal());

      ArgumentCaptor<Object> command = ArgumentCaptor.forClass(Object.class);
      verify(broadcast).sendToComposer(eq("sock-1"), command.capture());

      OpenComposerAssetCommand sent = (OpenComposerAssetCommand) command.getValue();
      assertEquals("ws-runtime-new", sent.runtimeId());
      assertFalse(sent.viewsheet());
   }

   /**
    * agent-sheet-visibility: create_worksheet is another real entry point that attaches a
    * session, alongside SheetJoinService.join, openBaseWorksheet, and createViewsheet, so it
    * needs the same sendAgentActive notification for the Composer tab-bar "agent connected"
    * indicator to be consistent across all of them.
    */
   @Test
   void notifiesTheTabBarThatAnAgentIsNowAttachedToTheNewWorksheetFromCreateWorksheet()
      throws Exception
   {
      SheetOpenService service = createWorksheetService(SheetType.WORKSHEET, true);

      JoinSession created = service.createWorksheet("tok-acting", principal());

      ArgumentCaptor<JoinSession> sent = ArgumentCaptor.forClass(JoinSession.class);
      verify(broadcast).sendAgentActive(sent.capture());
      assertEquals(created.runtimeId(), sent.getValue().runtimeId());
   }

   /** A broken tab-bar notification must not fail the create -- best-effort, independent try/catch. */
   @Test
   void tabBarNotifyFailureDoesNotFailTheCreateWorksheet() throws Exception {
      SheetOpenService service = createWorksheetService(SheetType.WORKSHEET, true);
      doThrow(new RuntimeException("socket gone")).when(broadcast).sendAgentActive(any());

      JoinSession created = service.createWorksheet("tok-acting", principal());

      assertNotNull(created);
      assertEquals("ws-runtime-new", created.runtimeId());
   }

   @Test
   void createWorksheetRefusesWhenActingSessionIsUnresolvable() {
      SheetOpenService service = createWorksheetService(SheetType.WORKSHEET, true);

      IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class,
         () -> service.createWorksheet("not-a-real-token", principal()));

      assertTrue(thrown.getMessage().toLowerCase().contains("invalid or expired"),
                thrown.getMessage());
   }

   /**
    * PSP-027 -- mirrors
    * createViewsheetFallsBackToIdentityAddressedBroadcastWhenActingSessionHasNoSocket: a
    * socketless acting session must still notify {@code PortalAgentNoticeService}, via the
    * identity-addressed fallback, rather than silently dropping the notice.
    */
   @Test
   void createWorksheetFallsBackToIdentityAddressedBroadcastWhenActingSessionHasNoSocket()
      throws Exception
   {
      SheetOpenService service = createWorksheetService(SheetType.WORKSHEET, true, null, null);

      JoinSession created = service.createWorksheet("tok-acting", principal());

      assertEquals("ws-runtime-new", created.runtimeId());
      assertNull(created.socketSessionId());
      verify(broadcast, never()).sendAgentActive(any());
      verify(broadcast, never()).sendToComposer(anyString(), any());

      ArgumentCaptor<Object> command = ArgumentCaptor.forClass(Object.class);
      verify(broadcast).sendToComposerByIdentity(eq(OWNER), command.capture());
      OpenComposerAssetCommand sent = (OpenComposerAssetCommand) command.getValue();
      assertEquals("ws-runtime-new", sent.runtimeId());
      assertFalse(sent.viewsheet());
   }

   /**
    * Attach-by-path (section 2.5): when {@code existingEntry} is given, createWorksheet must open
    * THAT saved asset directly via {@code worksheetService.openWorksheet}, never mint a new,
    * blank one via {@code openTemporaryWorksheet} -- and tell the browser to open the SAME
    * existing asset id.
    */
   @Test
   void createWorksheetOpensAnExistingWorksheetDirectlyWhenGivenAnExistingEntry() throws Exception {
      AssetEntry wsEntry = new AssetEntry(
         inetsoft.uql.asset.AssetRepository.GLOBAL_SCOPE, AssetEntry.Type.WORKSHEET,
         "Sample Queries/customers", null);
      SheetOpenService service = createWorksheetService(SheetType.WORKSHEET, true);

      when(worksheetService.openWorksheet(eq(wsEntry), any(Principal.class)))
         .thenReturn("ws-runtime-existing");
      JoinSession newWsSession = new JoinSession(
         "tok-ws-existing", "ws-runtime-existing", OWNER, SheetType.WORKSHEET, 0L,
         SheetSessionService.TTL_MILLIS, JoinSession.ConnectionMode.PAIRED,
         "sock-1", SOCKET_USER, null);
      when(sheetSessions.open(eq("ws-runtime-existing"), eq(OWNER), eq(SheetType.WORKSHEET),
                              eq("sock-1"), eq(SOCKET_USER), isNull()))
         .thenReturn(newWsSession);

      JoinSession created = service.createWorksheet("tok-acting", principal(), wsEntry);

      assertEquals("ws-runtime-existing", created.runtimeId());
      verify(worksheetService).openWorksheet(eq(wsEntry), any(Principal.class));
      verify(viewsheetService, never()).openTemporaryWorksheet(any(Principal.class), any());

      ArgumentCaptor<Object> command = ArgumentCaptor.forClass(Object.class);
      verify(broadcast).sendToComposer(eq("sock-1"), command.capture());
      OpenComposerAssetCommand sent = (OpenComposerAssetCommand) command.getValue();
      assertEquals(wsEntry.toIdentifier(), sent.assetId());
   }

   /** The existing 2-arg overload must still produce byte-for-byte the same blank-worksheet
    *  behavior as before this lane's change -- it delegates to the 3-arg overload with a null
    *  existingEntry, never accidentally attaching by path. */
   @Test
   void twoArgOverloadStillMintsABlankWorksheetNotAttachedByPath() throws Exception {
      SheetOpenService service = createWorksheetService(SheetType.WORKSHEET, true);

      JoinSession created = service.createWorksheet("tok-acting", principal());

      assertEquals("ws-runtime-new", created.runtimeId());
      verify(viewsheetService).openTemporaryWorksheet(any(Principal.class), isNull());
      verify(worksheetService, never()).openWorksheet(any(AssetEntry.class), any(Principal.class));
   }

   /**
    * Mirrors createViewsheetRefusesAPaneScopedActingSessionRatherThanMintingAWholeSheetOne: a
    * pane-scoped session is a write handle for ONE script location, and minting a new whole-sheet
    * (editorContext = null) session from it would launder that narrow grant into unscoped
    * whole-sheet authority on a runtime the pane's grant never named.
    *
    * <p>The four never() assertions are the substance: refusing with a message while still
    * opening the runtime, or still minting the session, would leave the hole open.
    */
   @Test
   void createWorksheetRefusesAPaneScopedActingSessionRatherThanMintingAWholeSheetOne()
      throws Exception
   {
      SheetOpenService service = createWorksheetService(
         SheetType.WORKSHEET, true, "sock-1",
         new inetsoft.web.wiz.pairing.EditorContext("assemblyMain", "Chart1", null, null));

      IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class,
         () -> service.createWorksheet("tok-acting", principal()));

      assertTrue(thrown.getMessage().contains("scoped to one script location"),
                thrown.getMessage());
      assertTrue(thrown.getMessage().contains("create_worksheet"), thrown.getMessage());

      verify(viewsheetService, never()).openTemporaryWorksheet(any(Principal.class), any());
      verify(sheetSessions, never()).open(anyString(), anyString(), any(SheetType.class),
                                          any(), any(), any());
      verify(broadcast, never()).sendAgentActive(any());
      verify(broadcast, never()).sendToComposer(anyString(), any());
   }

   @Test
   void createWorksheetRefusesWhenTheAgentLacksWorksheetPermission() {
      SheetOpenService service = createWorksheetService(SheetType.WORKSHEET, false);

      IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class,
         () -> service.createWorksheet("tok-acting", principal()));

      assertTrue(thrown.getMessage().toLowerCase().contains("permission"), thrown.getMessage());
   }

   /**
    * Purpose-built harness for {@code createWorksheet} -- mirrors {@code createViewsheetService}'s
    * shape but with no data-source concept at all: create_worksheet always mints a BLANK
    * worksheet, so there is no "defaults to acting worksheet" / "explicit data source" split to
    * parameterize.
    *
    * @param actingType    the acting session's own sheet type (worksheet or viewsheet -- either
    *                      is valid, unlike openBaseWorksheet which only ever follows a viewsheet)
    * @param hasPermission whether the agent has {@code ResourceType.WORKSHEET} ACCESS
    */
   private SheetOpenService createWorksheetService(SheetType actingType, boolean hasPermission) {
      return createWorksheetService(actingType, hasPermission, "sock-1", null);
   }

   /**
    * @param editorContext the acting session's own scope -- {@code null} for the ordinary
    *                      whole-sheet session every other test here uses, non-null for the
    *                      pane-scoped session createWorksheet must refuse.
    */
   private SheetOpenService createWorksheetService(SheetType actingType, boolean hasPermission,
                                                    String socketSessionId,
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

         JoinSession newWsSession = new JoinSession(
            "tok-ws-new", "ws-runtime-new", OWNER, SheetType.WORKSHEET, 0L,
            SheetSessionService.TTL_MILLIS, JoinSession.ConnectionMode.PAIRED,
            socketSessionId, SOCKET_USER, null);
         when(sheetSessions.open(eq("ws-runtime-new"), eq(OWNER), eq(SheetType.WORKSHEET),
                                 eq(socketSessionId), eq(SOCKET_USER), isNull()))
            .thenReturn(newWsSession);

         runtimeAccess = mock(inetsoft.web.wiz.pairing.SheetRuntimeAccess.class);

         viewsheetService = mock(inetsoft.analytic.composition.ViewsheetService.class);
         when(viewsheetService.openTemporaryWorksheet(any(Principal.class), isNull()))
            .thenReturn("ws-runtime-new");

         SecurityProvider securityProvider = mock(SecurityProvider.class);
         when(securityProvider.checkPermission(any(Principal.class), eq(ResourceType.WORKSHEET),
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

         // createViewsheet now fetches the acting session's own RuntimeSheet unconditionally
         // (not just for a WORKSHEET acting session defaulting dataSource) -- it needs
         // actingSheet.getUser() for every call, to open the new runtime as the BROWSER's
         // principal rather than the agent's (see SheetOpenService's own comment). Stub it for
         // every actingType; getEntry() stays null except where a WORKSHEET test actually wants
         // a defaulted dataSource.
         Principal browserPrincipal = () -> "browser-" + PRINCIPAL_NAME;
         inetsoft.report.composition.RuntimeSheet actingSheet =
            actingType == SheetType.WORKSHEET && actingWsEntry == null ? null
            : mock(inetsoft.report.composition.RuntimeSheet.class);

         if(actingSheet != null) {
            when(actingSheet.getUser()).thenReturn(browserPrincipal);
            when(actingSheet.getEntry()).thenReturn(actingWsEntry);
         }

         when(runtimeAccess.getSheetForPairing(eq(actingType), eq("acting-runtime-1"),
                                               any(Principal.class)))
            .thenReturn(actingSheet);

         viewsheetService = mock(inetsoft.analytic.composition.ViewsheetService.class);
         when(viewsheetService.openTemporaryViewsheet(isNull(), any(AssetEntry.class),
                                                       eq(browserPrincipal), isNull()))
            .thenReturn("vs-runtime-new");

         // createViewsheet also fetches the freshly-minted runtime's own (real, TEMPORARY_SCOPE)
         // entry to tell the browser to open THAT, not dataSource's identifier -- see its own
         // comment. Stub a distinct temporary entry so tests can tell it apart from dataSource.
         newVsTempEntry = new AssetEntry(
            inetsoft.uql.asset.AssetRepository.TEMPORARY_SCOPE, AssetEntry.Type.VIEWSHEET,
            "Untitled-1", null);
         inetsoft.report.composition.RuntimeViewsheet newRvs =
            mock(inetsoft.report.composition.RuntimeViewsheet.class);
         when(newRvs.getEntry()).thenReturn(newVsTempEntry);
         when(viewsheetService.getViewsheet(eq("vs-runtime-new"), eq(browserPrincipal)))
            .thenReturn(newRvs);

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
