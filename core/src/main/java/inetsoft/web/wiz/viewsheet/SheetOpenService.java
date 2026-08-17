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
                            SheetAgentBroadcastService broadcast)
   {
      this.viewsheetSessions = viewsheetSessions;
      this.sheetSessions = sheetSessions;
      this.worksheetService = worksheetService;
      this.securityProvider = securityProvider;
      this.broadcast = broadcast;
   }

   /**
    * Open the base worksheet of the viewsheet paired to {@code sessionToken}, for {@code user}.
    *
    * @throws IllegalArgumentException on every refusal, with a message naming the specific
    *                                  problem and, where there is one, the next tool to call.
    */
   public JoinSession openBaseWorksheet(String sessionToken, Principal user) throws Exception {
      JoinSession vsSession = viewsheetSessions.requireSession(sessionToken, user);
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
         throw new IllegalArgumentException(
            "A worksheet session is already open (runtimeId=" + held.runtimeId() + "). Call " +
            "detach_sheet to close it before opening another base worksheet.");
      }

      if(vsSession.socketSessionId() == null) {
         throw new IllegalArgumentException(
            "The connected viewsheet session has no active browser connection to open the " +
            "worksheet in; ask the user to re-pair (run connect_viewsheet again) before calling " +
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

   private final ViewsheetSessionService viewsheetSessions;
   private final SheetSessionService sheetSessions;
   private final WorksheetService worksheetService;
   private final SecurityProvider securityProvider;
   private final SheetAgentBroadcastService broadcast;
}
