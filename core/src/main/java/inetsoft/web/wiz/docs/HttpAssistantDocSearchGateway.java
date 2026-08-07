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

import inetsoft.web.assistant.AIAssistantController;
import jakarta.annotation.PreDestroy;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * {@link AssistantDocSearchGateway} over {@code java.net.http.HttpClient}, matching the client
 * style {@code AIAssistantController} already uses for its assistant health check.
 *
 * <p>Honours {@code chat.app.server.ssl.verify} for parity with the rest of the assistant
 * integration: private-network deployments commonly run the assistant with a self-signed
 * certificate.</p>
 */
@Component
public class HttpAssistantDocSearchGateway implements AssistantDocSearchGateway {
   @Override
   public Response post(String baseUrl, String path, String body, String authorization)
      throws IOException, InterruptedException
   {
      String url = baseUrl.endsWith("/")
         ? baseUrl.substring(0, baseUrl.length() - 1) + path
         : baseUrl + path;

      HttpRequest.Builder builder = HttpRequest.newBuilder()
         .uri(URI.create(url))
         .timeout(RESPONSE_TIMEOUT)
         .header("Content-Type", "application/json")
         .POST(HttpRequest.BodyPublishers.ofString(body));

      if(authorization != null && !authorization.isEmpty()) {
         builder.header("Authorization", authorization);
      }

      HttpResponse<String> response =
         client().send(builder.build(), HttpResponse.BodyHandlers.ofString());

      return new Response(response.statusCode(),
         response.body() == null ? "" : response.body());
   }

   private synchronized HttpClient client() {
      if(cachedClient == null) {
         HttpClient.Builder builder = HttpClient.newBuilder()
            .connectTimeout(CONNECT_TIMEOUT);
         AIAssistantController.applyAssistantTls(builder, "doc search");
         cachedClient = builder.build();
      }

      return cachedClient;
   }

   @PreDestroy
   public void closeClient() {
      if(cachedClient != null) {
         cachedClient.close();
      }
   }

   private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);
   private static final Duration RESPONSE_TIMEOUT = Duration.ofSeconds(20);

   private volatile HttpClient cachedClient;
}
