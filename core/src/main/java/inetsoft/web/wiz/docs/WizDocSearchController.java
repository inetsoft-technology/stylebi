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
package inetsoft.web.wiz.docs;

import inetsoft.web.admin.ai.AdminAiCallerGuard;
import inetsoft.web.assistant.AIAssistantController;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Proxies the plugins' documentation search to the configured AI assistant server.
 *
 * <p>StyleBI sits in this path so the plugin never needs the assistant's URL and so the request
 * is authenticated: {@code /api/wiz/**} is the only prefix where
 * {@code WizServiceAuthenticationFilter} validates a bearer JWT and {@code CSRFFilter} grants an
 * exemption. The Pinecone and Voyage credentials stay in the assistant server; StyleBI knows
 * only where the assistant is.</p>
 *
 * <p>Deliberately a thin passthrough. Request validation lives in the assistant, which owns the
 * module vocabulary; duplicating it here would produce two validators that drift apart.</p>
 */
@RestController
public class WizDocSearchController {
   @Autowired
   public WizDocSearchController(AssistantDocSearchGateway gateway) {
      this.gateway = gateway;
   }

   @PostMapping(
      value = "/api/wiz/v1/docs/search",
      consumes = MediaType.APPLICATION_JSON_VALUE,
      produces = MediaType.APPLICATION_JSON_VALUE)
   public ResponseEntity<String> search(HttpServletRequest request) throws IOException {
      // Defence in depth rather than load-bearing here: the endpoint is read-only over public
      // product documentation. Kept for consistency with the other plugin-facing AI endpoints,
      // which rely on it as a CSRF backstop under the /api/wiz/** exemption.
      AdminAiCallerGuard.requireBearerAuthenticatedRequest();

      // Read the raw body from the request stream instead of via @RequestBody. WebConfig
      // replaces Spring's default converters, and none of the registered ones can bind a JSON
      // request body onto a String target: MappingJackson2HttpMessageConverter tries to map
      // the JSON object onto String and fails, and StringHttpMessageConverter is restricted to
      // text/plain. A typed DTO isn't the fix either — this controller is a deliberate thin
      // passthrough (see class javadoc) and must not duplicate the assistant's validation.

      // Reject a body that is already oversized by its declared Content-Length without ever
      // opening the input stream.
      if(request.getContentLengthLong() > MAX_REQUEST_BODY_CHARS) {
         return error(HttpStatus.PAYLOAD_TOO_LARGE, "Request body too large.");
      }

      // A doc-search body is a short question. Anything larger is a caller bug or an attack;
      // relaying it upstream to find that out only spends the assistant's resources too.
      // readNBytes(cap + 1) never buffers more than cap + 1 bytes, so a chunked body (no
      // Content-Length) or a dishonest header is still bounded, without a duplicate of
      // AssistantProxyController's LimitedInputStream.
      byte[] bytes = request.getInputStream().readNBytes(MAX_REQUEST_BODY_CHARS + 1);

      if(bytes.length > MAX_REQUEST_BODY_CHARS) {
         return error(HttpStatus.PAYLOAD_TOO_LARGE, "Request body too large.");
      }

      String body = new String(bytes, StandardCharsets.UTF_8);

      String baseUrl = AIAssistantController.resolveAssistantBaseUrl();

      if(baseUrl == null) {
         return error(HttpStatus.SERVICE_UNAVAILABLE,
            "AI assistant server is not configured on this StyleBI server " +
            "(chat.app.internal.url or chat.app.server.url).");
      }

      try {
         AssistantDocSearchGateway.Response response =
            gateway.post(baseUrl, ASSISTANT_PATH, body, request.getHeader("Authorization"));

         // A bare 404 reads as "no such StyleBI route" and sends the operator to debug the wrong
         // layer. It actually means the assistant predates this endpoint.
         if(response.status() == HttpStatus.NOT_FOUND.value()) {
            LOG.warn("AI assistant at {} has no {} endpoint", baseUrl, ASSISTANT_PATH);

            return error(HttpStatus.BAD_GATEWAY,
               "The AI assistant server does not support document search — upgrade required.");
         }

         // Everything else passes through untouched so the assistant's field-named validation
         // errors reach the agent intact.
         return ResponseEntity.status(response.status())
            .contentType(MediaType.APPLICATION_JSON)
            .body(response.body());
      }
      catch(InterruptedException e) {
         Thread.currentThread().interrupt();

         return unreachable(baseUrl, e);
      }
      catch(Exception e) {
         return unreachable(baseUrl, e);
      }
   }

   private ResponseEntity<String> unreachable(String baseUrl, Exception e) {
      LOG.warn("AI assistant doc search failed for {}: {}", baseUrl, e.getMessage());

      return error(HttpStatus.BAD_GATEWAY, "The AI assistant server did not respond.");
   }

   private ResponseEntity<String> error(HttpStatus status, String message) {
      return ResponseEntity.status(status)
         .contentType(MediaType.APPLICATION_JSON)
         .body("{\"error\":\"" + message.replace("\"", "\\\"") + "\"}");
   }

   private static final String ASSISTANT_PATH = "/api/doc-search";
   /**
    * Generous next to the assistant's own 2000-character query limit, but bounded. Enforced
    * against the raw UTF-8 byte count read from the request, which is never smaller than the
    * decoded character count, so this is at least as strict as a character-based cap.
    */
   private static final int MAX_REQUEST_BODY_CHARS = 64 * 1024;
   private static final Logger LOG = LoggerFactory.getLogger(WizDocSearchController.class);

   private final AssistantDocSearchGateway gateway;
}
