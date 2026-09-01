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
import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.report.composition.WorksheetService;
import inetsoft.sree.security.IdentityID;
import inetsoft.sree.security.ResourceAction;
import inetsoft.sree.security.ResourceType;
import inetsoft.sree.security.SecurityProvider;
import inetsoft.uql.XPrincipal;
import inetsoft.uql.asset.AssetEntry;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.web.composer.command.OpenComposerAssetCommand;
import inetsoft.web.wiz.pairing.JoinSession;
import inetsoft.web.wiz.pairing.SheetAgentBroadcastService;
import inetsoft.web.wiz.pairing.SheetRuntimeAccess;
import inetsoft.web.wiz.pairing.SheetSessionService;
import inetsoft.web.wiz.pairing.SheetType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.security.Principal;

/**
 * Opens the base worksheet of the viewsheet already paired to a session, for the
 * {@code open_base_worksheet} agent tool.
 *
 * <p>This is the first tool that reaches a <b>second</b> sheet from a paired session — every
 * other property/binding tool operates on the sheet a human paired directly. Because of that, the
 * refusal guards carry most of this feature's value: each one must name the specific thing that
 * is wrong (the actual base type, the held runtime id, the tool to call next) so that an agent
 * that hits one can act on it rather than retry the same call.
 *
 * <p>The happy path opens the worksheet runtime server-side, mints a paired session for it that
 * carries the <b>viewsheet</b> session's socket identifiers (not the agent's), and pushes an
 * {@link OpenComposerAssetCommand} carrying the new runtime id so the browser attaches to the
 * runtime the server just created instead of opening a second one of its own.
 */
@Service
public class SheetOpenService {
   @Autowired
   public SheetOpenService(ViewsheetSessionService viewsheetSessions,
                            SheetSessionService sheetSessions,
                            WorksheetService worksheetService,
                            SecurityProvider securityProvider,
                            SheetAgentBroadcastService broadcast,
                            ViewsheetService viewsheetService,
                            SheetRuntimeAccess runtimeAccess)
   {
      this.viewsheetSessions = viewsheetSessions;
      this.sheetSessions = sheetSessions;
      this.worksheetService = worksheetService;
      this.securityProvider = securityProvider;
      this.broadcast = broadcast;
      this.viewsheetService = viewsheetService;
      this.runtimeAccess = runtimeAccess;
   }

   /**
    * Open the base worksheet of the viewsheet paired to {@code sessionToken}, for {@code user}.
    *
    * @throws IllegalArgumentException on every refusal, with a message naming the specific
    *                                  problem and, where there is one, the next tool to call.
    */
   public JoinSession openBaseWorksheet(String sessionToken, Principal user) throws Exception {
      return openBaseWorksheet(sessionToken, user, false);
   }

   /**
    * Open the base worksheet of the viewsheet paired to {@code sessionToken}, for {@code user}.
    *
    * @param force when {@code true} and a worksheet session is already held for this identity,
    *              close it and proceed instead of refusing -- the recovery path for a session
    *              orphaned by a client that lost the local pointer needed to name it for
    *              {@code detach_sheet} (e.g. a prior {@code connect_sheet(force:true)} that
    *              predates that client releasing what it replaces server-side).
    *
    * @throws IllegalArgumentException on every refusal, with a message naming the specific
    *                                  problem and, where there is one, the next tool to call.
    */
   public JoinSession openBaseWorksheet(String sessionToken, Principal user, boolean force)
      throws Exception
   {
      JoinSession vsSession =
         viewsheetSessions.requireSessionAllowingPaneScope(sessionToken, user);

      // Whole-branch review finding 1 (CRITICAL). This guard must come FIRST -- before the
      // runtime is touched, before the worksheet is opened, before any grant is minted.
      //
      // A pane-scoped session is a write handle for ONE script location. Opening a second sheet
      // from it used to mint a whole-sheet worksheet session (editorContext = null), which
      // LAUNDERED the narrow grant into an unscoped one: strictly more authority than the code
      // the user minted, on a runtime the pane's grant never named, and one that socketClosed
      // will not even reap because reaping keys on the editorContext this new session no longer
      // has. The reasoning for the null was right about the VALUE -- an editorContext naming a
      // viewsheet location is meaningless on a worksheet runtime -- and blind to the
      // CONSEQUENCE, which is what authority the new session then carries. The answer is that it
      // must not be created at all.
      if(vsSession.editorContext() != null) {
         throw new IllegalArgumentException(
            "This session is scoped to one script location, not to the whole viewsheet, so it " +
            "cannot open the base worksheet: doing so would turn a single-expression grant into " +
            "a whole-sheet write handle. Ask the user to re-pair from the sheet toolbar " +
            "('Connect to Claude') and call open_base_worksheet from that session.");
      }

      RuntimeViewsheet rvs = viewsheetSessions.resolve(sessionToken, user);
      Viewsheet vs = rvs == null ? null : rvs.getViewsheet();
      AssetEntry baseEntry = vs == null ? null : vs.getBaseEntry();

      if(baseEntry == null) {
         throw new IllegalArgumentException(
            "This viewsheet has no base worksheet to open.");
      }

      if(!baseEntry.isWorksheet()) {
         throw new IllegalArgumentException(
            "The connected viewsheet's base is a " + baseEntry.getType() + ", not a worksheet, " +
            "and cannot be opened with open_base_worksheet.");
      }

      boolean canWorksheet = securityProvider.checkPermission(
         user, ResourceType.WORKSHEET, "*", ResourceAction.ACCESS);

      if(!canWorksheet) {
         throw new IllegalArgumentException(
            "You do not have permission to open this Data Worksheet in Visual Composer.");
      }

      JoinSession held = sheetSessions.findOpen(vsSession.ownerIdentity(), SheetType.WORKSHEET);

      if(held != null) {
         if(!force) {
            throw new IllegalArgumentException(
               "A worksheet session is already open (runtimeId=" + held.runtimeId() + "). Call " +
               "detach_sheet to close it, or -- if detach_sheet reports nothing connected because " +
               "this held session is not in this client's local state -- call open_base_worksheet " +
               "again with force:true to close it and open this one instead.");
         }

         // Best-effort: the caller is discarding this session either way, so a session the server
         // has already forgotten (or that close() otherwise cannot fully act on) must not block
         // the open that follows.
         sheetSessions.close(held.sessionToken());
      }

      if(vsSession.socketSessionId() == null) {
         throw new IllegalArgumentException(
            "The connected viewsheet session has no active browser connection to open the " +
            "worksheet in; ask the user to re-pair (run connect_sheet again) before calling " +
            "open_base_worksheet.");
      }

      // Owned by the BROWSER's principal, not the agent's. WorksheetEngine.getSheet refuses a
      // principal that does not match the runtime's owner unless it carries pairedAgent or
      // supportLogin: the agent has pairedAgent, the browser has neither. Opening as the agent
      // makes the user's own browser the outsider and its attach dies on "Invalid user found",
      // two principals for the same user differing only by session. A paired viewsheet is already
      // browser-owned with the agent reaching in through the flag; this matches it.
      String runtimeId = worksheetService.openWorksheet(baseEntry, rvs.getUser());

      // The base worksheet is a distinct runtime from the viewsheet script pane that opened
      // it -- any editorContext on vsSession names a location on the VIEWSHEET, not this new
      // worksheet, so the new session is opened whole-sheet (null), matching how a base
      // worksheet has always been opened.
      JoinSession wsSession = sheetSessions.open(runtimeId, vsSession.ownerIdentity(),
                                                  SheetType.WORKSHEET,
                                                  vsSession.socketSessionId(),
                                                  vsSession.socketUserName(), null);

      OpenComposerAssetCommand command = OpenComposerAssetCommand.builder()
         .assetId(baseEntry.toIdentifier())
         .viewsheet(false)
         .runtimeId(runtimeId)
         .build();

      // The Composer's own channel, not the paired sheet's: this command is handled by
      // composer-main, which subscribes to /user/composer-client. See sendToComposer.
      broadcast.sendToComposer(vsSession.socketSessionId(), command);

      return wsSession;
   }

   /**
    * {@code create_viewsheet}. Mints a brand-new viewsheet runtime bound to {@code dataSource}
    * (or, when {@code null}, defaults to the acting session's own worksheet), and pairs the
    * caller to it directly by reusing the ACTING session's already-live browser socket -- the
    * exact mechanism {@link #openBaseWorksheet} already uses to avoid a second pairing code,
    * applied in the reverse (worksheet/either sheet type -> new viewsheet) direction.
    *
    * <p>Unlike {@link #openBaseWorksheet}, the acting session may be EITHER sheet type -- a
    * worksheet or a viewsheet -- so it is resolved through the generic, type-agnostic
    * {@link SheetSessionService#resolve}, not {@link ViewsheetSessionService}.
    *
    * @param fromSessionToken the already-paired session (worksheet or viewsheet) whose browser
    *                         connection the new session reuses
    * @param dataSource       the worksheet/logical-model/physical-table entry to build from, or
    *                         {@code null} to default to the acting session's own worksheet (only
    *                         valid when the acting session IS a worksheet session)
    * @throws IllegalArgumentException on every refusal, with a message naming the specific
    *                                  problem and, where there is one, the next tool to call.
    */
   public JoinSession createViewsheet(String fromSessionToken, Principal user,
                                      AssetEntry dataSource) throws Exception
   {
      JoinSession actingSession = sheetSessions.resolve(fromSessionToken, agentKey(user));

      if(actingSession == null) {
         throw new IllegalArgumentException(
            "Invalid or expired session: " + fromSessionToken + ". Ask the user for a fresh " +
            "pairing code and run connect_sheet again.");
      }

      if(actingSession.socketSessionId() == null) {
         throw new IllegalArgumentException(
            "The connected session has no active browser connection to create a viewsheet in; " +
            "ask the user to re-pair (run connect_sheet again) before calling create_viewsheet.");
      }

      if(dataSource == null) {
         if(actingSession.sheetType() != SheetType.WORKSHEET) {
            throw new IllegalArgumentException(
               "No data source was given and the connected session is not a worksheet, so " +
               "there is nothing to default to. Pass type/path (and datasource/table for a " +
               "physical table) naming the source to build the new viewsheet from.");
         }

         RuntimeSheet actingWs = runtimeAccess.getSheetForPairing(
            SheetType.WORKSHEET, actingSession.runtimeId(), user);
         dataSource = actingWs == null ? null : actingWs.getEntry();

         if(dataSource == null || dataSource.getPath() == null) {
            throw new IllegalArgumentException(
               "The connected worksheet has not been saved yet, so it has no path to build a " +
               "viewsheet from. Save it first (save_worksheet), or pass type/path explicitly.");
         }
      }

      boolean canCreate = securityProvider.checkPermission(
         user, ResourceType.VIEWSHEET, "*", ResourceAction.ACCESS);

      if(!canCreate) {
         throw new IllegalArgumentException(
            "You do not have permission to create a viewsheet in the Visual Composer.");
      }

      String runtimeId = viewsheetService.openTemporaryViewsheet(null, dataSource, user, null);

      // The acting session's own socket/owner, exactly like openBaseWorksheet mints the reverse
      // direction -- no new pairing code, and the new session is opened whole-sheet (null
      // editorContext), matching how a freshly-created viewsheet has always been opened.
      JoinSession vsSession = sheetSessions.open(runtimeId, actingSession.ownerIdentity(),
                                                  SheetType.VIEWSHEET,
                                                  actingSession.socketSessionId(),
                                                  actingSession.socketUserName(), null);

      OpenComposerAssetCommand command = OpenComposerAssetCommand.builder()
         .assetId(dataSource.toIdentifier())
         .viewsheet(true)
         .runtimeId(runtimeId)
         .build();

      broadcast.sendToComposer(actingSession.socketSessionId(), command);

      return vsSession;
   }

   private static String agentKey(Principal agent) {
      if(agent instanceof XPrincipal p) {
         IdentityID id = IdentityID.getIdentityIDFromKey(p.getName());
         return id != null ? id.convertToKey() : p.getName();
      }

      return agent != null ? agent.getName() : null;
   }

   private final ViewsheetSessionService viewsheetSessions;
   private final SheetSessionService sheetSessions;
   private final WorksheetService worksheetService;
   private final SecurityProvider securityProvider;
   private final SheetAgentBroadcastService broadcast;
   private final ViewsheetService viewsheetService;
   private final SheetRuntimeAccess runtimeAccess;
}
