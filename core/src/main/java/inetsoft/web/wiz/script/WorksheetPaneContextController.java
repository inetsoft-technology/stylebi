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
package inetsoft.web.wiz.script;

import inetsoft.report.composition.RuntimeWorksheet;
import inetsoft.web.wiz.pairing.JoinSession;
import inetsoft.web.wiz.pairing.PairingException;
import inetsoft.web.wiz.pairing.SheetAgentFeature;
import inetsoft.web.wiz.pairing.SheetSessionService;
import inetsoft.web.wiz.script.model.PaneContext;
import inetsoft.web.wiz.worksheet.WorksheetEditService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.security.Principal;
import java.util.Map;

/**
 * The read-only pane-context query for a worksheet session (Bug #81557), the worksheet twin of
 * {@link PaneContextController}. Like it, deliberately not routed through {@link PaneScopeService}:
 * the answer names no script location and acts on none, and returns names and types only.
 */
@RestController
public class WorksheetPaneContextController {
   @Autowired
   public WorksheetPaneContextController(SheetAgentFeature feature,
                                         SheetSessionService sessionService,
                                         WorksheetEditService editService,
                                         WorksheetPaneContextService contextService)
   {
      this.feature = feature;
      this.sessionService = sessionService;
      this.editService = editService;
      this.contextService = contextService;
   }

   /** What the worksheet dialog's tree shows for the location this session is paired at. */
   @GetMapping("/api/wiz/v1/agent/worksheet/{sessionToken}/pane-context")
   public PaneContext paneContext(@PathVariable String sessionToken, Principal user)
      throws PairingException
   {
      if(!feature.isEnabled()) {
         throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                                           "Sheet agent pairing is disabled");
      }

      RuntimeWorksheet rws = editService.resolve(sessionToken, user);
      JoinSession session =
         sessionService.resolve(sessionToken, ViewsheetAgentController.agentKey(user));

      if(session == null) {
         throw new PairingException(
            PairingException.Kind.SESSION_EXPIRED, "Invalid or expired session: " + sessionToken);
      }

      if(rws == null) {
         throw new PairingException(PairingException.Kind.SESSION_EXPIRED,
                                    "Runtime not found or expired: " + session.runtimeId());
      }

      return contextService.paneContext(rws, session, sessionService.paneStateOf(session));
   }

   @ExceptionHandler(PairingException.class)
   public ResponseEntity<Map<String, String>> handlePairingException(PairingException e) {
      return ViewsheetAgentController.pairingResponse(e);
   }

   private final SheetAgentFeature feature;
   private final SheetSessionService sessionService;
   private final WorksheetEditService editService;
   private final WorksheetPaneContextService contextService;
}
