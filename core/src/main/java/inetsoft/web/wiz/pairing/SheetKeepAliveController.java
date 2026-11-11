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
package inetsoft.web.wiz.pairing;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.security.Principal;
import java.util.LinkedHashMap;
import java.util.Map;

/** Keep-alive for a paired worksheet or viewsheet runtime. See {@link SheetKeepAliveService}. */
@RestController
public class SheetKeepAliveController {
   private final SheetAgentFeature feature;
   private final SheetKeepAliveService service;

   @Autowired
   public SheetKeepAliveController(SheetAgentFeature feature, SheetKeepAliveService service) {
      this.feature = feature;
      this.service = service;
   }

   @PostMapping("/api/wiz/v1/agent/sheet/{sessionToken}/keep-alive")
   public Map<String, Boolean> keepAlive(@PathVariable String sessionToken, Principal user)
      throws PairingException
   {
      if(!feature.isEnabled()) {
         throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                                           "Sheet agent pairing is disabled");
      }

      service.keepAlive(sessionToken, user);
      return Map.of("alive", true);
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
}
