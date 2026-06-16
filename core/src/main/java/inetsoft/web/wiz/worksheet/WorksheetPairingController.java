/*
 * This file is part of StyleBI.
 * Copyright (C) 2024  InetSoft Technology
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
package inetsoft.web.wiz.worksheet;

import inetsoft.sree.security.IdentityID;
import inetsoft.uql.XPrincipal;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.security.Principal;

@RestController
public class WorksheetPairingController {
   private final WorksheetPairingService pairing;
   private final WorksheetAgentFeature feature;

   public WorksheetPairingController(WorksheetPairingService pairing,
                                     WorksheetAgentFeature feature)
   {
      this.pairing = pairing;
      this.feature = feature;
   }

   /** Browser mints a code for its open runtime. socketSessionId comes from its STOMP session. */
   public record MintResponse(String code) {}

   @PostMapping("/api/wiz/worksheet/pairing/mint")
   public MintResponse mint(@RequestParam String runtimeId,
                            @RequestParam String socketSessionId,
                            Principal owner)
   {
      if(!feature.isEnabled()) {
         throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Worksheet agent pairing is disabled");
      }
      String ownerId = owner instanceof XPrincipal
         ? IdentityID.getIdentityIDFromKey(owner.getName()).convertToKey()
         : owner.getName();
      return new MintResponse(pairing.mint(runtimeId, ownerId, socketSessionId));
   }
}
