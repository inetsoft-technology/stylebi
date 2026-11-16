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

import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.web.wiz.pairing.JoinSession;
import inetsoft.web.wiz.pairing.PairingException;
import inetsoft.web.wiz.pairing.SheetAgentFeature;
import inetsoft.web.wiz.pairing.SheetSessionService;
import inetsoft.web.wiz.script.model.PaneContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.security.Principal;
import java.util.Map;

/**
 * The one read-only context query every script session kind is served, pane-scoped or whole-sheet.
 *
 * <p>Its own controller, and deliberately NOT routed through {@link PaneScopeService}: that class
 * decides whether a session may ACT on a named script location, and this answer names none and acts
 * on none. It still resolves the session through the same pairing checks as every other endpoint
 * (an expired or foreign token is refused exactly alike) and returns names and types only, so a
 * pane token gains no way to read or write anything beyond the one expression it was minted for.
 * {@code ViewsheetSessionService#requireSession} is left strict for the same reason: that rule
 * ("read-only is not a narrower grant") stays true for everything that returns real data.</p>
 */
@RestController
public class PaneContextController {
   @Autowired
   public PaneContextController(SheetAgentFeature feature, SheetSessionService sessionService,
                                ScriptEditService editService,
                                PaneContextService contextService)
   {
      this.feature = feature;
      this.sessionService = sessionService;
      this.editService = editService;
      this.contextService = contextService;
   }

   /** What the dialog's tree shows for the location this session is paired at. */
   @GetMapping("/api/wiz/v1/agent/script/{sessionToken}/pane-context")
   public PaneContext paneContext(@PathVariable String sessionToken, Principal user)
      throws PairingException
   {
      if(!feature.isEnabled()) {
         throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                                           "Sheet agent pairing is disabled");
      }

      RuntimeViewsheet rvs = editService.resolve(sessionToken, user);
      JoinSession session =
         sessionService.resolve(sessionToken, ViewsheetAgentController.agentKey(user));

      if(session == null) {
         throw new PairingException(
            PairingException.Kind.SESSION_EXPIRED, "Invalid or expired session: " + sessionToken);
      }

      return contextService.paneContext(rvs, session, user);
   }

   @ExceptionHandler(PairingException.class)
   public ResponseEntity<Map<String, String>> handlePairingException(PairingException e) {
      return ViewsheetAgentController.pairingResponse(e);
   }

   private final SheetAgentFeature feature;
   private final SheetSessionService sessionService;
   private final ScriptEditService editService;
   private final PaneContextService contextService;
}
