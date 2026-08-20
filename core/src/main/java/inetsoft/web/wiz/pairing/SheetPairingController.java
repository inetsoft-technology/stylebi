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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.handler.annotation.*;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.annotation.SendToUser;
import org.springframework.messaging.simp.user.DestinationUserNameProvider;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.security.Principal;
import java.util.LinkedHashMap;
import java.util.Map;

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
   public SheetPairingController(SheetPairingService pairing, SheetSessionService sessions,
                                 SheetAgentFeature feature,
                                 @Value("${wiz.agent.rest-mint.enabled:false}") boolean restMintEnabled)
   {
      this.pairing = pairing;
      this.sessions = sessions;
      this.feature = feature;
      this.restMintEnabled = restMintEnabled;
   }

   /** Response DTO returned by both mint entry points. {@code error} is non-null on failure. */
   public record MintResponse(String code, String error) {
      public static MintResponse ok(String code) { return new MintResponse(code, null); }
      public static MintResponse err(String msg)  { return new MintResponse(null, msg); }
   }

   /**
    * Payload for the STOMP mint.
    *
    * @param editorContext the script/formula location this session should be scoped to, or
    *                      {@code null} for a whole-sheet ("Connect to Claude" toolbar) mint
    */
   public record MintRequest(String runtimeId, SheetType sheetType, EditorContext editorContext) {}

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
    * <p>Disabled by default ({@code wiz.agent.rest-mint.enabled=false}). Must be explicitly
    * enabled in test environments. Production deployments should leave this off and use the
    * STOMP variant instead.</p>
    */
   @PostMapping("/api/wiz/pairing/mint")
   public MintResponse mint(@RequestParam String runtimeId,
                            @RequestParam String socketSessionId,
                            @RequestParam SheetType sheetType,
                            Principal owner)
      throws PairingException
   {
      if(!restMintEnabled) {
         throw new ResponseStatusException(HttpStatus.NOT_FOUND);
      }

      requireFeature();
      LOG.warn("REST mint used (socketSessionId not server-verified) — user={}, runtimeId={}",
               owner != null ? owner.getName() : "null", runtimeId);
      // REST mint is test-only/back-compat and carries no editorContext of its own; the STOMP
      // path below is the production entry point that actually forwards one.
      return MintResponse.ok(pairing.mint(runtimeId, ownerKey(owner), socketSessionId,
                                          destinationUserName(owner), sheetType, null));
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
                                             destinationUserName(owner), req.sheetType(),
                                             req.editorContext()));
      }
      catch(Exception e) {
         LOG.error("STOMP mint failed (runtimeId={}, sheetType={})",
                   req != null ? req.runtimeId() : "null",
                   req != null ? req.sheetType() : "null", e);
         return MintResponse.err(e.getMessage() != null ? e.getMessage() : "Failed to generate pairing code");
      }
   }

   /** Payload for the STOMP detach. {@code editorContext} is required -- a whole-sheet
    *  ("Connect to Claude" toolbar) mint never sends one, and there is nothing for a detach
    *  without one to correctly target: the toolbar session's lifetime is TTL-only by design and
    *  must not be reachable from this endpoint. */
   public record DetachRequest(EditorContext editorContext) {}

   /**
    * STOMP detach — sent by {@code ConnectToClaudeComponent.detach()} when the script pane or
    * formula editor that minted a pane-scoped pairing code is destroyed (its dialog closed or
    * cancelled), ending any live session paired from that exact location. This is the
    * deliberate-close half of Task 9; {@link SheetSessionSocketCleanup} is the other half,
    * covering the socket simply going away (crash/network drop/killed tab).
    *
    * <p>No reply is sent -- the browser never held the session token to begin with (only the
    * agent that joined does), so there is nothing meaningful to report back.
    *
    * <p>Send to: {@code /app/wiz/pairing/detach}
    */
   @MessageMapping("/wiz/pairing/detach")
   public void detachViaSocket(@Payload DetachRequest req, SimpMessageHeaderAccessor accessor) {
      if(req == null || req.editorContext() == null) {
         return;
      }

      sessions.detach(accessor.getSessionId(), req.editorContext());
   }

   /**
    * Payload for the STOMP Follow Focus toggle.
    *
    * @param runtimeId the sheet the toggle applies to -- matched against the caller's own
    *                  socket-bound session, same as {@link #retargetViaSocket}
    * @param enabled   the new opt-in state
    */
   public record FollowFocusRequest(String runtimeId, boolean enabled) {}

   /**
    * STOMP Follow Focus toggle -- turns the opt-in on or off for the caller's own session on
    * {@code runtimeId}. Mirrors {@link #detachViaSocket}'s fire-and-forget shape: no reply is
    * sent, since there is nothing to report back for a toggle (unlike {@link #retargetViaSocket},
    * which can be refused for reasons the browser needs to show a human). {@code socketSessionId}
    * is derived from the accessor, never trusted from the client, same as every other STOMP
    * endpoint in this controller.
    *
    * <p>Send to: {@code /app/wiz/pairing/follow-focus}
    */
   @MessageMapping("/wiz/pairing/follow-focus")
   public void followFocusViaSocket(@Payload FollowFocusRequest req, SimpMessageHeaderAccessor accessor) {
      if(req == null || req.runtimeId() == null) {
         return;
      }

      sessions.setFollowFocus(accessor.getSessionId(), req.runtimeId(), req.enabled());
   }

   /**
    * Payload for the STOMP retarget push -- mirrors {@link MintRequest}'s shape.
    *
    * @param runtimeId    the sheet whose session should be retargeted
    * @param editorContext the script/formula location to push the session's target to
    */
   public record RetargetRequest(String runtimeId, EditorContext editorContext) {}

   /** Response DTO for the STOMP retarget push. {@code error} is non-null on failure. */
   public record RetargetResponse(boolean ok, String error) {
      // Named success()/failure(), not ok()/err(), because ok() would collide with this
      // record's own generated accessor for the `ok` component (same clash MintResponse avoids
      // by not naming a component `code` the same as its factory).
      public static RetargetResponse success()      { return new RetargetResponse(true, null); }
      public static RetargetResponse failure(String msg) { return new RetargetResponse(false, msg); }
   }

   /**
    * STOMP retarget -- the client-asserted "a pane-eligible editor opened" signal Follow Focus
    * sends on the caller's behalf once they have opted in ({@link #followFocusViaSocket}), in
    * place of the human clicking "Connect Agent" inside that pane. Unlike {@link #detachViaSocket}
    * this DOES reply: a retarget can be refused (not opted in, no matching session, or
    * {@code editorContext} names a location the runtime does not have) for reasons the browser
    * needs to surface to a human, not just log.
    *
    * <p>{@code socketSessionId} is derived from the accessor, never trusted from the client.
    *
    * <p>Send to: {@code /app/wiz/pairing/retarget}<br>
    * Reply arrives on: {@code /user/queue/wiz/pairing/retarget}
    */
   @MessageMapping("/wiz/pairing/retarget")
   @SendToUser("/commands/wiz/pairing/retarget")
   public RetargetResponse retargetViaSocket(@Payload RetargetRequest req, SimpMessageHeaderAccessor accessor) {
      if(req == null || req.runtimeId() == null || req.editorContext() == null) {
         return RetargetResponse.failure("runtimeId and editorContext are required");
      }

      try {
         sessions.retarget(accessor.getSessionId(), req.runtimeId(), req.editorContext());
         return RetargetResponse.success();
      }
      catch(PairingException e) {
         return RetargetResponse.failure(e.getMessage());
      }
   }

   /**
    * Payload for the STOMP pop-focus. The {@code editorContext} being popped TO is
    * server-determined, not client-asserted -- the browser doesn't need to know it, only that
    * the operation is "leave the pane I'm currently in", so only {@code runtimeId} is sent.
    */
   public record PopFocusRequest(String runtimeId) {}

   /**
    * STOMP pop-focus -- the client-asserted "a pane-eligible editor closed" signal, popping the
    * caller's session back to whatever preceded its most recent {@link #retargetViaSocket}. No
    * reply is sent -- like {@link #detachViaSocket}, popping past an empty stack is defined as a
    * harmless no-op, not a failure mode the browser needs reported.
    *
    * <p>Send to: {@code /app/wiz/pairing/pop-focus}
    */
   @MessageMapping("/wiz/pairing/pop-focus")
   public void popFocusViaSocket(@Payload PopFocusRequest req, SimpMessageHeaderAccessor accessor) {
      if(req == null || req.runtimeId() == null) {
         return;
      }

      sessions.popFocus(accessor.getSessionId(), req.runtimeId());
   }

   @ExceptionHandler(PairingException.class)
   public ResponseEntity<Map<String, String>> handlePairingException(PairingException e) {
      HttpStatus status = switch(e.getKind()) {
         case SESSION_EXPIRED  -> HttpStatus.NOT_FOUND;
         case USER_MISMATCH,
              FEATURE_DISABLED -> HttpStatus.FORBIDDEN;
         case RATE_LIMITED     -> HttpStatus.TOO_MANY_REQUESTS;
         case INTERNAL         -> HttpStatus.INTERNAL_SERVER_ERROR;
         default               -> HttpStatus.BAD_REQUEST;
      };
      Map<String, String> body = new LinkedHashMap<>();
      body.put("error", e.getMessage());
      body.put("errorCode", e.getKind().name());
      return ResponseEntity.status(status).body(body);
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
   private final SheetSessionService sessions;
   private final SheetAgentFeature feature;
   private final boolean restMintEnabled;

   private static final Logger LOG = LoggerFactory.getLogger(SheetPairingController.class);
}
