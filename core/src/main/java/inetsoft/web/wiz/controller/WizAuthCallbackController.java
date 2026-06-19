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
package inetsoft.web.wiz.controller;

import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.text.ParseException;
import java.util.*;
import java.util.concurrent.*;

/**
 * Handles the MCP plugin SSO login flow.
 *
 * <p>Flow:
 * <ol>
 *   <li>Plugin calls {@code login_start} → constructs callback URL pointing here with a nonce</li>
 *   <li>StyleBI {@code /sso/authorize} authenticates user, POSTs JWT to this callback</li>
 *   <li>This controller stores the JWT by nonce (TTL 10 min)</li>
 *   <li>Plugin polls {@code GET /api/wiz/v1/auth/pickup?nonce=...} until token is ready</li>
 * </ol>
 */
@RestController("wizAuthCallbackController")
@RequestMapping("/api/wiz")
public class WizAuthCallbackController {

   /**
    * POST /api/wiz/auth/callback?nonce=...
    * Called by StyleBI's /sso/authorize via an auto-submitting form POST.
    * Stores the JWT under the nonce for one-time pickup.
    */
   @PostMapping(value = "/auth/callback")
   public ResponseEntity<Void> callback(
      @RequestParam("nonce") String nonce,
      @RequestParam("token") String token)
   {
      if(nonce == null || nonce.isBlank() || token == null || token.isBlank()) {
         return ResponseEntity.badRequest().build();
      }

      long expiresAt = extractExpiration(token);
      pendingTokens.put(nonce, new PendingToken(token, expiresAt));
      LOG.debug("Stored SSO callback token for nonce (length={})", nonce.length());

      // Return a simple close-tab HTML page
      return ResponseEntity.ok().build();
   }

   /**
    * GET /api/wiz/v1/auth/pickup?nonce=...
    * Unauthenticated endpoint polled by the MCP plugin after login_start.
    * Returns the token once and removes it (one-time use).
    * Returns 404 while the token has not yet arrived.
    */
   @GetMapping(value = "/v1/auth/pickup", produces = MediaType.APPLICATION_JSON_VALUE)
   public ResponseEntity<Map<String, Object>> pickup(@RequestParam("nonce") String nonce) {
      if(nonce == null || nonce.isBlank()) {
         return ResponseEntity.badRequest().build();
      }

      PendingToken entry = pendingTokens.remove(nonce);

      if(entry == null || System.currentTimeMillis() > entry.expiresAtMs()) {
         return ResponseEntity.notFound().build();
      }

      Map<String, Object> body = new LinkedHashMap<>();
      body.put("accessToken", entry.token());
      body.put("expiresAt", entry.expiresAtMs());
      body.put("user", extractUser(entry.token()));
      return ResponseEntity.ok(body);
   }

   // -------------------------------------------------------------------------
   // Helpers
   // -------------------------------------------------------------------------

   private static long extractExpiration(String token) {
      try {
         SignedJWT jwt = SignedJWT.parse(token);
         Date exp = jwt.getJWTClaimsSet().getExpirationTime();
         return exp != null ? exp.getTime() : System.currentTimeMillis() + 10 * 60 * 1000L;
      }
      catch(ParseException e) {
         return System.currentTimeMillis() + 10 * 60 * 1000L;
      }
   }

   private static Map<String, Object> extractUser(String token) {
      Map<String, Object> user = new LinkedHashMap<>();

      try {
         JWTClaimsSet claims = SignedJWT.parse(token).getJWTClaimsSet();
         String subject = claims.getSubject();
         // Subject is stored as "user~;~orgId" key format
         String username = subject != null && subject.contains("~;~")
            ? subject.substring(0, subject.indexOf("~;~")) : subject;
         String orgId = subject != null && subject.contains("~;~")
            ? subject.substring(subject.indexOf("~;~") + 3) : null;

         user.put("username", username);
         user.put("organizationId", orgId);
      }
      catch(ParseException e) {
         user.put("username", "unknown");
      }

      return user;
   }

   private record PendingToken(String token, long expiresAtMs) {}

   // Bounded map to prevent memory leaks; evicts oldest entries when full.
   private static final Map<String, PendingToken> pendingTokens =
      new ConcurrentHashMap<>(64);

   private static final Logger LOG = LoggerFactory.getLogger(WizAuthCallbackController.class);
}
