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

import inetsoft.web.wiz.pairing.*;
import inetsoft.web.wiz.viewsheet.model.ViewsheetModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.security.Principal;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * REST surface for agent-driven viewsheet layout editing.
 *
 * <p>Validation and delegation only — every mutation goes through the Composer's own services
 * via {@link ViewsheetSessionService}, which supplies the capturing dispatcher, the checkpoint,
 * and the browser broadcast.
 */
@RestController
public class ViewsheetAgentController {
   @Autowired
   public ViewsheetAgentController(SheetAgentFeature feature,
                                   SheetJoinService joinService,
                                   SheetSessionService sessionService,
                                   ViewsheetSessionService sessions,
                                   ViewsheetReadService readService)
   {
      this.feature = feature;
      this.joinService = joinService;
      this.sessionService = sessionService;
      this.sessions = sessions;
      this.readService = readService;
   }

   public record JoinRequest(String code) {}
   public record JoinResponse(String sessionToken, String runtimeId, String ownerIdentity) {}

   @PostMapping("/api/wiz/v1/agent/viewsheet/join")
   public JoinResponse join(@RequestBody JoinRequest body, Principal user) throws PairingException {
      requireEnabled();
      JoinSession session = joinService.join(body.code(), user);
      return new JoinResponse(session.sessionToken(), session.runtimeId(), session.ownerIdentity());
   }

   @GetMapping("/api/wiz/v1/agent/viewsheet/{sessionToken}/model")
   public ViewsheetModel model(@PathVariable String sessionToken, Principal user)
      throws PairingException
   {
      requireEnabled();
      return readService.read(sessions.resolve(sessionToken, user));
   }

   @PostMapping("/api/wiz/v1/agent/viewsheet/{sessionToken}/detach")
   public void detach(@PathVariable String sessionToken, Principal user) {
      sessionService.close(sessionToken);
   }

   private void requireEnabled() {
      if(!feature.isEnabled()) {
         throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                                           "Sheet agent pairing is disabled");
      }
   }

   @ExceptionHandler(PairingException.class)
   public ResponseEntity<Map<String, String>> handlePairingException(PairingException e) {
      HttpStatus status = switch(e.getKind()) {
         case SESSION_EXPIRED -> HttpStatus.NOT_FOUND;
         case USER_MISMATCH, FEATURE_DISABLED -> HttpStatus.FORBIDDEN;
         case RATE_LIMITED -> HttpStatus.TOO_MANY_REQUESTS;
         case INTERNAL -> HttpStatus.INTERNAL_SERVER_ERROR;
         default -> HttpStatus.BAD_REQUEST;
      };

      Map<String, String> body = new LinkedHashMap<>();
      body.put("error", e.getMessage());
      body.put("errorCode", e.getKind().name());
      return ResponseEntity.status(status).body(body);
   }

   private final SheetAgentFeature feature;
   private final SheetJoinService joinService;
   private final SheetSessionService sessionService;
   private final ViewsheetSessionService sessions;
   private final ViewsheetReadService readService;
}
