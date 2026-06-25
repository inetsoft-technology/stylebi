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

import inetsoft.sree.security.IdentityID;
import inetsoft.uql.XPrincipal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.messaging.handler.annotation.*;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.annotation.SendToUser;
import org.springframework.messaging.simp.user.DestinationUserNameProvider;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.security.Principal;

/**
 * Secure mint endpoint for Wiz Sheet-Agent pairing.
 *
 * <p>Provides two entry points for minting a single-use pairing code that the browser
 * shows to the user ("Connect to Claude"):
 * <ul>
 *   <li>REST ({@code POST /api/wiz/pairing/mint}) — for testing / back-compat; caller
 *       supplies {@code socketSessionId} as a request parameter.</li>
 *   <li>STOMP ({@code /app/wiz/pairing/mint}) — production path; {@code socketSessionId}
 *       is derived from the STOMP session and cannot be spoofed by the client.</li>
 * </ul>
 */
@RestController
public class SheetPairingController {
   @Autowired
   public SheetPairingController(SheetPairingService pairing, SheetAgentFeature feature) {
      this.pairing = pairing;
      this.feature = feature;
   }

   /** Response DTO returned by both mint entry points. {@code error} is non-null on failure. */
   public record MintResponse(String code, String error) {
      public static MintResponse ok(String code) { return new MintResponse(code, null); }
      public static MintResponse err(String msg)  { return new MintResponse(null, msg); }
   }

   /** Payload for the STOMP mint. */
   public record MintRequest(String runtimeId, SheetType sheetType) {}

   /** Returns whether the sheet-agent pairing feature is enabled. */
   @GetMapping("/api/wiz/pairing/feature")
   public java.util.Map<String, Boolean> featureStatus() {
      return java.util.Map.of("enabled", feature.isEnabled());
   }

   /**
    * REST mint — for testing only. {@code socketSessionId} is supplied by the caller and is
    * not verified server-side; any authenticated user could supply an arbitrary session ID and
    * bind a pairing code to a browser session they do not own.
    *
    * <p><strong>WARNING:</strong> this endpoint must NOT be reachable in production. It is
    * retained solely for integration tests that cannot drive a live STOMP connection. Secure
    * deployments should firewall or remove this endpoint and use the STOMP variant instead.
    */
   @PostMapping("/api/wiz/pairing/mint")
   public MintResponse mint(@RequestParam String runtimeId,
                            @RequestParam String socketSessionId,
                            @RequestParam SheetType sheetType,
                            Principal owner)
   {
      requireFeature();
      LOG.warn("REST mint used (socketSessionId not server-verified) — user={}, runtimeId={}",
               owner != null ? owner.getName() : "null", runtimeId);
      return MintResponse.ok(pairing.mint(runtimeId, ownerKey(owner), socketSessionId,
                                          destinationUserName(owner), sheetType));
   }

   /**
    * STOMP mint — production path. The {@code socketSessionId} is derived from the STOMP
    * session (the browser cannot spoof it). The client sends a {@link MintRequest} payload.
    *
    * <p>Send to: {@code /app/wiz/pairing/mint}<br>
    * Reply arrives on: {@code /user/queue/wiz/pairing/mint}
    */
   @MessageMapping("/wiz/pairing/mint")
   @SendToUser("/commands/wiz/pairing/mint")
   public MintResponse mintViaSocket(@Payload MintRequest req,
                                     Principal owner,
                                     SimpMessageHeaderAccessor accessor)
   {
      if(!feature.isEnabled()) {
         return MintResponse.err("Sheet agent pairing is disabled (set wiz.agent.pairing.enabled=true in sree.properties)");
      }
      try {
         String sessionId = accessor.getSessionId();
         return MintResponse.ok(pairing.mint(req.runtimeId(), ownerKey(owner), sessionId,
                                             destinationUserName(owner), req.sheetType()));
      }
      catch(Exception e) {
         return MintResponse.err(e.getMessage() != null ? e.getMessage() : "Failed to generate pairing code");
      }
   }

   private void requireFeature() {
      if(!feature.isEnabled()) {
         throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Sheet agent pairing is disabled");
      }
   }

   private static String destinationUserName(Principal owner) {
      if(owner instanceof DestinationUserNameProvider provider) {
         return provider.getDestinationUserName();
      }

      return owner == null ? null : owner.getName();
   }

   private static String ownerKey(Principal owner) {
      if(owner == null) {
         throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
      }
      if(owner instanceof XPrincipal xp) {
         IdentityID id = IdentityID.getIdentityIDFromKey(xp.getName());
         if(id != null) return id.convertToKey();
      }
      return owner.getName();
   }

   private final SheetPairingService pairing;
   private final SheetAgentFeature feature;

   private static final Logger LOG = LoggerFactory.getLogger(SheetPairingController.class);
}
