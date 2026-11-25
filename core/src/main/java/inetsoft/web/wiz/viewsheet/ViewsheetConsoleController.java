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

import inetsoft.web.wiz.pairing.PairingException;
import inetsoft.web.wiz.pairing.SheetAgentFeature;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.security.Principal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * REST surface for the paired viewsheet's Console (Bug #82316): read the messages with the
 * per-level counters, clear them, and set the level filter. Deliberately not in
 * {@code ViewerModePolicy.ALLOWED}: a message can carry script text.
 */
@RestController
public class ViewsheetConsoleController {
   @Autowired
   public ViewsheetConsoleController(SheetAgentFeature feature, ViewsheetConsoleService service) {
      this.feature = feature;
      this.service = service;
   }

   @GetMapping("/api/wiz/v1/agent/viewsheet/{sessionToken}/console")
   public Map<String, Object> read(@PathVariable String sessionToken,
                                   @RequestParam(required = false) List<String> level,
                                   @RequestParam(required = false) Integer limit,
                                   @RequestParam(required = false) Long sinceSeq,
                                   Principal user)
      throws Exception
   {
      requireEnabled();
      return service.read(sessionToken, user, level, limit, sinceSeq);
   }

   @PostMapping("/api/wiz/v1/agent/viewsheet/{sessionToken}/console/clear")
   public Map<String, Object> clear(@PathVariable String sessionToken, Principal user)
      throws Exception
   {
      requireEnabled();
      return service.clear(sessionToken, user);
   }

   @PostMapping("/api/wiz/v1/agent/viewsheet/{sessionToken}/console/levels")
   public Map<String, Object> setLevels(@PathVariable String sessionToken,
                                        @RequestBody(required = false) Map<String, Object> body,
                                        Principal user)
      throws Exception
   {
      requireEnabled();
      return service.setLevels(sessionToken, user, body == null ? null : body.get("levels"));
   }

   private void requireEnabled() throws PairingException {
      if(!feature.isEnabled()) {
         throw new PairingException(PairingException.Kind.FEATURE_DISABLED,
                                    "Sheet agent pairing is disabled");
      }
   }

   /** A value Spring cannot bind (limit=abc, a body that is not JSON) gets the same error shape. */
   @ExceptionHandler({ MethodArgumentTypeMismatchException.class,
                       HttpMessageNotReadableException.class })
   public ResponseEntity<Map<String, String>> handleBadInput(Exception e) {
      String text = e instanceof MethodArgumentTypeMismatchException mismatch ?
         "The value of the \"" + mismatch.getName() + "\" parameter is not valid" :
         "The request body must be a JSON object such as {\"levels\": [\"error\"]}";
      return handlePairingException(new PairingException(text));
   }

   @ExceptionHandler(PairingException.class)
   public ResponseEntity<Map<String, String>> handlePairingException(PairingException e) {
      HttpStatus status = switch(e.getKind()) {
         case SESSION_EXPIRED -> HttpStatus.NOT_FOUND;
         case USER_MISMATCH, FEATURE_DISABLED, VIEWER_MODE -> HttpStatus.FORBIDDEN;
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
   private final ViewsheetConsoleService service;
}
