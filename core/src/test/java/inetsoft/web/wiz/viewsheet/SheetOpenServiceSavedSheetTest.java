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

import inetsoft.analytic.composition.ViewsheetService;
import inetsoft.report.composition.RuntimeSheet;
import inetsoft.report.composition.WorksheetService;
import inetsoft.sree.security.IdentityID;
import inetsoft.sree.security.ResourceAction;
import inetsoft.sree.security.ResourceType;
import inetsoft.sree.security.SecurityProvider;
import inetsoft.uql.asset.AssetEntry;
import inetsoft.uql.asset.AssetRepository;
import inetsoft.web.composer.command.OpenComposerAssetCommand;
import inetsoft.web.wiz.pairing.EditorContext;
import inetsoft.web.wiz.pairing.JoinSession;
import inetsoft.web.wiz.pairing.PairingException;
import inetsoft.web.wiz.pairing.SheetAgentBroadcastService;
import inetsoft.web.wiz.pairing.SheetRuntimeAccess;
import inetsoft.web.wiz.pairing.SheetSessionService;
import inetsoft.web.wiz.pairing.SheetType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.security.Principal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * {@code SheetOpenService.openSavedSheet} (Redmine #81016): open a saved viewsheet/worksheet by
 * path from an already-paired session. The tests pin the security model -- the entry is built
 * server-side from (type, path, scope) only, a user scope is always the caller's own, and every
 * refusal happens before a runtime is opened or a session minted.
 */
@Tag("core")
class SheetOpenServiceSavedSheetTest {
   private static final String OWNER = "alice~;~host-org";
   private static final String SOCKET_USER = "alice-browser";

   private SheetSessionService sheetSessions;
   private WorksheetService worksheetService;
   private ViewsheetService viewsheetService;
   private SheetRuntimeAccess runtimeAccess;
   private SheetAgentBroadcastService broadcast;
   private SecurityProvider securityProvider;
   private SheetOpenService service;
   private final Principal browser = () -> "browser-" + OWNER;

   @BeforeEach
   void setUp() throws Exception {
      sheetSessions = mock(SheetSessionService.class);
      worksheetService = mock(WorksheetService.class);
      viewsheetService = mock(ViewsheetService.class);
      runtimeAccess = mock(SheetRuntimeAccess.class);
      broadcast = mock(SheetAgentBroadcastService.class);
      securityProvider = mock(SecurityProvider.class);
      when(securityProvider.checkPermission(any(Principal.class), any(ResourceType.class),
                                            eq("*"), eq(ResourceAction.ACCESS)))
         .thenReturn(true);

      acting(SheetType.VIEWSHEET, "sock-1", null);

      RuntimeSheet actingSheet = mock(RuntimeSheet.class);
      when(actingSheet.getUser()).thenReturn(browser);
      when(runtimeAccess.getSheetForPairing(any(SheetType.class), eq("acting-rt"),
                                            any(Principal.class)))
         .thenReturn(actingSheet);

      when(viewsheetService.openViewsheet(any(AssetEntry.class), any(Principal.class),
                                          anyBoolean()))
         .thenReturn("vs-new");
      when(worksheetService.openWorksheet(any(AssetEntry.class), any(Principal.class)))
         .thenReturn("ws-new");
      when(sheetSessions.open(eq("vs-new"), eq(OWNER), eq(SheetType.VIEWSHEET), any(), any(),
                              isNull()))
         .thenReturn(session("tok-vs-new", "vs-new", SheetType.VIEWSHEET));
      when(sheetSessions.open(eq("ws-new"), eq(OWNER), eq(SheetType.WORKSHEET), any(), any(),
                              isNull()))
         .thenReturn(session("tok-ws-new", "ws-new", SheetType.WORKSHEET));

      service = new SheetOpenService(mock(ViewsheetSessionService.class), sheetSessions,
                                     worksheetService, securityProvider, broadcast,
                                     viewsheetService, runtimeAccess);
   }

   private void acting(SheetType type, String socket, EditorContext context) {
      when(sheetSessions.resolve(eq("tok-acting"), eq(OWNER)))
         .thenReturn(new JoinSession("tok-acting", "acting-rt", OWNER, type, 0L,
                                     SheetSessionService.TTL_MILLIS,
                                     JoinSession.ConnectionMode.PAIRED, socket, SOCKET_USER,
                                     context));
   }

   private static JoinSession session(String token, String runtime, SheetType type) {
      return new JoinSession(token, runtime, OWNER, type, 0L, SheetSessionService.TTL_MILLIS,
                             JoinSession.ConnectionMode.PAIRED, "sock-1", SOCKET_USER, null);
   }

   private static Principal agent() {
      return () -> OWNER;
   }

   private AssetEntry openedViewsheetEntry() throws Exception {
      ArgumentCaptor<AssetEntry> entry = ArgumentCaptor.forClass(AssetEntry.class);
      verify(viewsheetService).openViewsheet(entry.capture(), any(Principal.class), eq(false));
      return entry.getValue();
   }

   private void assertNothingOpened() throws Exception {
      verify(viewsheetService, never()).openViewsheet(any(AssetEntry.class), any(Principal.class),
                                                      anyBoolean());
      verify(worksheetService, never()).openWorksheet(any(AssetEntry.class), any(Principal.class));
      verify(sheetSessions, never()).open(anyString(), anyString(), any(SheetType.class), any(),
                                          any(), any());
      verify(broadcast, never()).sendToComposer(anyString(), any());
   }

   private IllegalArgumentException refused(SheetType type, String path, String scope) {
      return assertThrows(IllegalArgumentException.class,
         () -> service.openSavedSheet("tok-acting", agent(), type, path, scope, false));
   }

   @Test
   void opensASavedViewsheetAsTheBrowserPrincipalAndPairsIt() throws Exception {
      JoinSession opened = service.openSavedSheet(
         "tok-acting", agent(), SheetType.VIEWSHEET, "Sales/Dashboard", null, false);

      assertEquals("tok-vs-new", opened.sessionToken());
      assertEquals(SheetType.VIEWSHEET, opened.sheetType());

      ArgumentCaptor<AssetEntry> entry = ArgumentCaptor.forClass(AssetEntry.class);
      verify(viewsheetService).openViewsheet(entry.capture(), same(browser), eq(false));
      assertEquals(AssetEntry.Type.VIEWSHEET, entry.getValue().getType());
      assertEquals(AssetRepository.GLOBAL_SCOPE, entry.getValue().getScope());
      assertEquals("Sales/Dashboard", entry.getValue().getPath());
      assertEquals("host-org", entry.getValue().getOrgID());
      assertNull(entry.getValue().getUser());

      // The new session reuses the acting session's socket, like create_viewsheet.
      verify(sheetSessions).open("vs-new", OWNER, SheetType.VIEWSHEET, "sock-1", SOCKET_USER,
                                 null);

      ArgumentCaptor<OpenComposerAssetCommand> command =
         ArgumentCaptor.forClass(OpenComposerAssetCommand.class);
      verify(broadcast).sendToComposer(eq("sock-1"), command.capture());
      assertEquals(entry.getValue().toIdentifier(), command.getValue().assetId());
      assertTrue(command.getValue().viewsheet());
      assertEquals("vs-new", command.getValue().runtimeId());
      assertTrue(command.getValue().agentActive());
      assertTrue(command.getValue().closeRuntimeOnTabClose());
   }

   @Test
   void opensASavedWorksheetAsTheBrowserPrincipalAndPairsIt() throws Exception {
      JoinSession opened = service.openSavedSheet(
         "tok-acting", agent(), SheetType.WORKSHEET, "Sales/Orders", "global", false);

      assertEquals("tok-ws-new", opened.sessionToken());
      assertEquals(SheetType.WORKSHEET, opened.sheetType());

      ArgumentCaptor<AssetEntry> entry = ArgumentCaptor.forClass(AssetEntry.class);
      verify(worksheetService).openWorksheet(entry.capture(), same(browser));
      assertEquals(AssetEntry.Type.WORKSHEET, entry.getValue().getType());

      ArgumentCaptor<OpenComposerAssetCommand> command =
         ArgumentCaptor.forClass(OpenComposerAssetCommand.class);
      verify(broadcast).sendToComposer(eq("sock-1"), command.capture());
      assertFalse(command.getValue().viewsheet());
      assertEquals("ws-new", command.getValue().runtimeId());
   }

   @Test
   void userScopeAlwaysResolvesToTheCallersOwnPrivateTreeAndOrganization() throws Exception {
      service.openSavedSheet("tok-acting", agent(), SheetType.VIEWSHEET, "Mine/VS", "User", false);

      AssetEntry entry = openedViewsheetEntry();
      assertEquals(AssetRepository.USER_SCOPE, entry.getScope());
      assertEquals(new IdentityID("alice", "host-org"), entry.getUser());
      assertEquals("host-org", entry.getOrgID());
   }

   /**
    * isDashboard switches the per-asset READ check off in WorksheetEngine.openSheet, and
    * openAutoSaved does the same in AbstractAssetEngine.getSheet. Both are entry properties, so
    * the entry must be built with none and no request field may be able to carry them in.
    */
   @Test
   void theEntryNeverCarriesThePropertiesThatSkipThePerAssetReadCheck() throws Exception {
      service.openSavedSheet("tok-acting", agent(), SheetType.VIEWSHEET,
                             "Sales/Dashboard?isDashboard=true&openAutoSaved=true", null, false);

      AssetEntry entry = openedViewsheetEntry();
      assertNull(entry.getProperty("isDashboard"));
      assertNull(entry.getProperty("openAutoSaved"));
      assertEquals("Sales/Dashboard?isDashboard=true&openAutoSaved=true", entry.getPath());
   }

   @Test
   void refusesAnIdentifierStringInPlaceOfAPathBecauseTheCaretIsTheFieldSeparator()
      throws Exception
   {
      AssetEntry other = new AssetEntry(AssetRepository.USER_SCOPE, AssetEntry.Type.VIEWSHEET,
                                        "Secret", new IdentityID("bob", "host-org"));
      other.setProperty("isDashboard", "true");

      assertThrows(PairingException.class, () -> service.openSavedSheet(
         "tok-acting", agent(), SheetType.VIEWSHEET, other.toIdentifier(), null, false));
      assertNothingOpened();
   }

   @Test
   void refusesParentDirectorySegmentsBothSeparators() throws Exception {
      for(String path : new String[] {"../Other/VS", "Sales/../../x", "Sales\\..\\x", ".."}) {
         IllegalArgumentException e = refused(SheetType.VIEWSHEET, path, null);
         assertTrue(e.getMessage().contains(".."), e.getMessage());
      }

      assertNothingOpened();
   }

   @Test
   void aLeadingSeparatorIsTrimmedNotTreatedAsTheRepositoryRoot() throws Exception {
      service.openSavedSheet("tok-acting", agent(), SheetType.VIEWSHEET, "  /Sales/Dashboard ",
                             null, false);

      assertEquals("Sales/Dashboard", openedViewsheetEntry().getPath());
   }

   @Test
   void refusesABlankOrRootPath() throws Exception {
      assertTrue(refused(SheetType.VIEWSHEET, null, null).getMessage().contains("path"));
      assertTrue(refused(SheetType.VIEWSHEET, "  ", null).getMessage().contains("path"));
      assertTrue(refused(SheetType.VIEWSHEET, "/", null).getMessage().contains("path"));
      assertNothingOpened();
   }

   @Test
   void refusesAScopeThatIsNotGlobalOrUser() throws Exception {
      assertThrows(PairingException.class, () -> service.openSavedSheet(
         "tok-acting", agent(), SheetType.VIEWSHEET, "Sales/VS", "bob", false));
      assertNothingOpened();
   }

   @Test
   void refusesWithoutTypeLevelAccessBeforeTouchingTheRepository() throws Exception {
      when(securityProvider.checkPermission(any(Principal.class), eq(ResourceType.VIEWSHEET),
                                            eq("*"), eq(ResourceAction.ACCESS)))
         .thenReturn(false);

      IllegalArgumentException e = refused(SheetType.VIEWSHEET, "Sales/VS", null);

      assertTrue(e.getMessage().contains("permission"), e.getMessage());
      assertNothingOpened();
   }

   @Test
   void aWorksheetChecksWorksheetAccessNotViewsheetAccess() throws Exception {
      when(securityProvider.checkPermission(any(Principal.class), eq(ResourceType.WORKSHEET),
                                            eq("*"), eq(ResourceAction.ACCESS)))
         .thenReturn(false);

      assertTrue(refused(SheetType.WORKSHEET, "Sales/WS", null).getMessage()
                    .contains("permission"));
      assertNothingOpened();
   }

   /**
    * The repository's own per-asset READ check is what rejects an asset the caller cannot read; it
    * surfaces as an exception from the open. That, a missing asset and an asset of the other type
    * must be indistinguishable to the caller, and must leave no runtime session behind.
    */
   @Test
   void anAssetTheCallerCannotOpenIsRefusedWithoutMintingASession() throws Exception {
      when(viewsheetService.openViewsheet(any(AssetEntry.class), any(Principal.class),
                                          anyBoolean()))
         .thenThrow(new SecurityException("no READ"));

      IllegalArgumentException e = refused(SheetType.VIEWSHEET, "Bob/Private", "user");

      assertTrue(e.getMessage().contains("no viewsheet named 'Bob/Private'"), e.getMessage());
      assertTrue(e.getMessage().contains("list_viewsheets"), e.getMessage());
      assertFalse(e.getMessage().contains("no READ"), "must not leak why: " + e.getMessage());
      verify(sheetSessions, never()).open(anyString(), anyString(), any(SheetType.class), any(),
                                          any(), any());
      verify(broadcast, never()).sendToComposer(anyString(), any());
   }

   @Test
   void aNullRuntimeFromTheOpenIsRefusedNotPaired() throws Exception {
      when(worksheetService.openWorksheet(any(AssetEntry.class), any(Principal.class)))
         .thenReturn(null);

      refused(SheetType.WORKSHEET, "Sales/WS", null);

      verify(sheetSessions, never()).open(anyString(), anyString(), any(SheetType.class), any(),
                                          any(), any());
   }

   @Test
   void refusesAnUnresolvableActingSession() throws Exception {
      IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
         () -> service.openSavedSheet("stale", agent(), SheetType.VIEWSHEET, "Sales/VS", null,
                                      false));

      assertTrue(e.getMessage().contains("connect_sheet"), e.getMessage());
      assertNothingOpened();
   }

   @Test
   void refusesAPaneScopedActingSession() throws Exception {
      acting(SheetType.VIEWSHEET, "sock-1", new EditorContext("assemblyMain", "Chart1", null, null));

      IllegalArgumentException e = refused(SheetType.VIEWSHEET, "Sales/VS", null);

      assertTrue(e.getMessage().contains("scoped to one script location"), e.getMessage());
      assertNothingOpened();
   }

   @Test
   void refusesAnActingSessionWithNoBrowserConnection() throws Exception {
      acting(SheetType.VIEWSHEET, null, null);

      IllegalArgumentException e = refused(SheetType.WORKSHEET, "Sales/WS", null);

      assertTrue(e.getMessage().contains("no active browser connection"), e.getMessage());
      assertTrue(e.getMessage().contains("open_worksheet"), e.getMessage());
      assertNothingOpened();
   }

   @Test
   void aHeldWorksheetSessionIsRefusedNamingItsRuntimeUnlessForced() throws Exception {
      when(sheetSessions.findOpen(OWNER, SheetType.WORKSHEET))
         .thenReturn(session("tok-ws-held", "ws-held", SheetType.WORKSHEET));

      IllegalArgumentException e = refused(SheetType.WORKSHEET, "Sales/WS", null);

      assertTrue(e.getMessage().contains("ws-held"), e.getMessage());
      assertTrue(e.getMessage().contains("open_worksheet"), e.getMessage());
      verify(sheetSessions, never()).close(anyString());
      assertNothingOpened();

      service.openSavedSheet("tok-acting", agent(), SheetType.WORKSHEET, "Sales/WS", null, true);

      verify(sheetSessions).close("tok-ws-held");
      verify(broadcast).sendAgentInactive(argThat(s -> "tok-ws-held".equals(s.sessionToken())));
      verify(worksheetService).openWorksheet(any(AssetEntry.class), any(Principal.class));
   }

   /** Viewsheets may be several at once, unlike worksheets: a held worksheet never blocks one. */
   @Test
   void aHeldWorksheetSessionDoesNotBlockOpeningAViewsheet() throws Exception {
      when(sheetSessions.findOpen(OWNER, SheetType.WORKSHEET))
         .thenReturn(session("tok-ws-held", "ws-held", SheetType.WORKSHEET));

      service.openSavedSheet("tok-acting", agent(), SheetType.VIEWSHEET, "Sales/VS", null, false);

      verify(sheetSessions, never()).close(anyString());
      verify(viewsheetService).openViewsheet(any(AssetEntry.class), any(Principal.class),
                                             eq(false));
   }

   @Test
   void anExpiredActingRuntimeFallsBackToTheAgentPrincipal() throws Exception {
      when(runtimeAccess.getSheetForPairing(any(SheetType.class), eq("acting-rt"),
                                            any(Principal.class)))
         .thenThrow(new PairingException(PairingException.Kind.SESSION_EXPIRED, "gone"));
      Principal agent = agent();

      service.openSavedSheet("tok-acting", agent, SheetType.VIEWSHEET, "Sales/VS", null, false);

      verify(viewsheetService).openViewsheet(any(AssetEntry.class), same(agent), eq(false));
   }
}
