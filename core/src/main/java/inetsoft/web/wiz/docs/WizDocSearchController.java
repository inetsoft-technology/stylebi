/*
 * This file is part of StyleBI.
 * Copyright (C) 2025  InetSoft Technology
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

import com.fasterxml.jackson.databind.ObjectMapper;
import inetsoft.web.admin.ai.AdminAiCallerGuard;
import inetsoft.web.assistant.AIAssistantController;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;

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
   public void search(HttpServletRequest request, HttpServletResponse response) throws IOException {
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
         writeJson(response, HttpStatus.PAYLOAD_TOO_LARGE.value(),
            errorBody("Request body too large."));
         return;
      }

      // A doc-search body is a short question. Anything larger is a caller bug or an attack;
      // relaying it upstream to find that out only spends the assistant's resources too.
      // readNBytes(cap + 1) never buffers more than cap + 1 bytes, so a chunked body (no
      // Content-Length) or a dishonest header is still bounded, without a duplicate of
      // AssistantProxyController's LimitedInputStream.
      byte[] bytes = request.getInputStream().readNBytes(MAX_REQUEST_BODY_CHARS + 1);

      if(bytes.length > MAX_REQUEST_BODY_CHARS) {
         writeJson(response, HttpStatus.PAYLOAD_TOO_LARGE.value(),
            errorBody("Request body too large."));
         return;
      }

      String body = new String(bytes, StandardCharsets.UTF_8);

      String baseUrl = AIAssistantController.resolveAssistantBaseUrl();

      if(baseUrl == null) {
         writeJson(response, HttpStatus.SERVICE_UNAVAILABLE.value(), errorBody(
            "AI assistant server is not configured on this StyleBI server " +
            "(chat.app.internal.url or chat.app.server.url)."));
         return;
      }

      // Only the network call to the assistant is guarded here. Writing the response is done
      // after the try returns, so a client disconnect mid-write (an IOException from the servlet
      // output stream) propagates as-is instead of being caught below, logged as an assistant
      // failure it was not, and followed by a second, doomed write to an already-committed
      // response.
      AssistantDocSearchGateway.Response gatewayResponse;

      try {
         gatewayResponse = gateway.post(baseUrl, ASSISTANT_PATH, body, request.getHeader("Authorization"));
      }
      catch(InterruptedException e) {
         Thread.currentThread().interrupt();

         unreachable(response, baseUrl, e);
         return;
      }
      catch(Exception e) {
         unreachable(response, baseUrl, e);
         return;
      }

      // A bare 404 reads as "no such StyleBI route" and sends the operator to debug the wrong
      // layer. It actually means the assistant predates this endpoint.
      if(gatewayResponse.status() == HttpStatus.NOT_FOUND.value()) {
         LOG.warn("AI assistant at {} has no {} endpoint", baseUrl, ASSISTANT_PATH);

         writeJson(response, HttpStatus.BAD_GATEWAY.value(), errorBody(
            "The AI assistant server does not support document search — upgrade required."));
         return;
      }

      // Everything else passes through untouched so the assistant's field-named validation
      // errors reach the agent intact. Written straight to the servlet response (not returned
      // as a ResponseEntity<String>) because WebConfig's only application/json-capable
      // converter is Jackson, which would re-serialize this already-serialized JSON string as
      // a JSON string literal — the mirror image of the request-binding defect this endpoint
      // was previously fixed for.
      writeJson(response, gatewayResponse.status(), gatewayResponse.body());
   }

   private void unreachable(HttpServletResponse response, String baseUrl, Exception e)
      throws IOException
   {
      LOG.warn("AI assistant doc search failed for {}: {}", baseUrl, e.getMessage());

      writeJson(response, HttpStatus.BAD_GATEWAY.value(),
         errorBody("The AI assistant server did not respond."));
   }

   /**
    * Serializes {@code message} through Jackson rather than hand-building the JSON literal, so
    * this stays correct for any message text without relying on callers passing only string
    * literals (the previous implementation escaped only {@code "} and had no such guarantee).
    */
   private String errorBody(String message) throws IOException {
      return MAPPER.writeValueAsString(Collections.singletonMap("error", message));
   }

   /**
    * Writes {@code json} to {@code response} directly, bypassing {@code HttpMessageConverter}
    * selection entirely — the same pattern {@link inetsoft.web.assistant.AssistantProxyController}
    * uses for its own proxied responses. {@code json} is always already a complete, valid JSON
    * document (either relayed verbatim from the assistant or built by {@link #errorBody}), so no
    * converter is needed or wanted: routing it through one, as {@code ResponseEntity<String>}
    * did, hands it to {@code MappingJackson2HttpMessageConverter} (the only registered converter
    * that supports {@code application/json}), which re-serializes the {@code String} as a JSON
    * string literal instead of writing it as-is.
    */
   private void writeJson(HttpServletResponse response, int status, String json)
      throws IOException
   {
      byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
      response.setStatus(status);
      response.setContentType(MediaType.APPLICATION_JSON_VALUE);
      response.setCharacterEncoding(StandardCharsets.UTF_8.name());
      response.setContentLength(bytes.length);
      response.getOutputStream().write(bytes);
   }

   private static final String ASSISTANT_PATH = "/api/doc-search";
   /**
    * Generous next to the assistant's own 2000-character query limit, but bounded. Enforced
    * against the raw UTF-8 byte count read from the request, which is never smaller than the
    * decoded character count, so this is at least as strict as a character-based cap.
    */
   private static final int MAX_REQUEST_BODY_CHARS = 64 * 1024;
   private static final Logger LOG = LoggerFactory.getLogger(WizDocSearchController.class);
   private static final ObjectMapper MAPPER = new ObjectMapper();

   private final AssistantDocSearchGateway gateway;
}
