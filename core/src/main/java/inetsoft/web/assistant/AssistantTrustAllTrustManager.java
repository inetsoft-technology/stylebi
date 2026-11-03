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

package inetsoft.web.assistant;

import javax.net.ssl.SSLEngine;
import javax.net.ssl.X509ExtendedTrustManager;
import java.net.Socket;
import java.security.cert.X509Certificate;

/**
 * Trusts every certificate chain <em>and</em> every hostname, for
 * {@code chat.app.server.ssl.verify=false}. Shared by every outbound assistant connection
 * (health check, doc search, WebSocket proxy).
 *
 * <p>This must be an {@link X509ExtendedTrustManager}, not a plain {@code X509TrustManager}.
 * JSSE performs hostname verification <em>inside</em> the trust manager, so a plain
 * {@code X509TrustManager} gets wrapped in one that still runs the identity check. The JDK
 * {@code HttpClient} ignores an empty {@code SSLParameters.setEndpointIdentificationAlgorithm}
 * and Tomcat's WebSocket client sets "HTTPS" unconditionally, so with a plain manager the chain
 * is trusted but a certificate whose CN/SAN does not match the host is still refused. Overriding
 * the {@code SSLEngine} and {@code Socket} overloads here is what actually disables the check.
 */
final class AssistantTrustAllTrustManager extends X509ExtendedTrustManager {
   static final AssistantTrustAllTrustManager INSTANCE = new AssistantTrustAllTrustManager();

   private AssistantTrustAllTrustManager() {
   }

   @Override
   public void checkClientTrusted(X509Certificate[] chain, String authType) {
   }

   @Override
   public void checkClientTrusted(X509Certificate[] chain, String authType, Socket socket) {
   }

   @Override
   public void checkClientTrusted(X509Certificate[] chain, String authType, SSLEngine engine) {
   }

   @Override
   public void checkServerTrusted(X509Certificate[] chain, String authType) {
   }

   @Override
   public void checkServerTrusted(X509Certificate[] chain, String authType, Socket socket) {
   }

   @Override
   public void checkServerTrusted(X509Certificate[] chain, String authType, SSLEngine engine) {
   }

   @Override
   public X509Certificate[] getAcceptedIssuers() {
      return new X509Certificate[0];
   }
}
