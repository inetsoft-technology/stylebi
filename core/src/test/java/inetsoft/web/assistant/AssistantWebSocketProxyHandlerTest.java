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

/*
 * Test strategy
 *
 * chat.app.server.ssl.verify=false is documented as relaxing TLS for the assistant's three
 * server-to-server transports. Two of them relaxed both halves -- AssistantProxyController via
 * NoopHostnameVerifier, AIAssistantController's health client via
 * SSLParameters.setEndpointIdentificationAlgorithm("") -- but this WebSocket handler built its
 * trust-all context with Apache httpcore5's SSLContextBuilder, whose TrustManagerDelegate
 * implements the PLAIN X509TrustManager. JSSE wraps a plain manager in one that still performs
 * the identity check, and Tomcat's WsWebSocketContainer.createSSLEngine unconditionally sets the
 * endpoint identification algorithm to "HTTPS" with no user property to override it. So a
 * self-signed cert was accepted while a CN/SAN mismatch was still refused, even though the
 * operator had disabled verification for exactly that case.
 *
 * The fix supplies an X509ExtendedTrustManager instead. Because JSSE performs hostname
 * verification inside the trust manager, an extended manager whose SSLEngine/Socket overloads
 * no-op is what disables the check; there is no separate flag to assert on.
 *
 * These tests deliberately do not call buildWsClient(). Constructing a StandardWebSocketClient
 * needs a jakarta.websocket container implementation, which core has only as a "provided" API --
 * the implementation arrives from the server module at runtime. Rather than add a Tomcat test
 * dependency to core's pom, [G2] reproduces the one thing Tomcat does that matters here: it
 * requests "HTTPS" endpoint identification on the engine. Asserting our manager passes anyway
 * tests the mechanism more directly than asserting a user-property key would.
 *
 * Behavioral guarantees covered:
 *
 * [G1] TRUST_ALL is an X509ExtendedTrustManager, not merely an X509TrustManager. This is the
 *      property the whole fix rests on -- reverting to httpcore5's SSLContextBuilder fails here.
 * [G2] It still accepts an untrusted chain when the engine requests "HTTPS" endpoint
 *      identification, which is what Tomcat's WebSocket client always does.
 * [G3] Every check* overload accepts an untrusted chain rather than throwing, including the
 *      SSLEngine and Socket overloads that the identity check rides on.
 */

import org.junit.jupiter.api.*;

import javax.net.ssl.*;
import java.lang.reflect.Field;
import java.net.Socket;
import java.security.cert.X509Certificate;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

@Tag("core")
class AssistantWebSocketProxyHandlerTest {
   private X509ExtendedTrustManager trustAll() throws Exception {
      Field field = AssistantWebSocketProxyHandler.class.getDeclaredField("TRUST_ALL");
      field.setAccessible(true);
      return assertInstanceOf(
         X509ExtendedTrustManager.class, field.get(null),
         "must be X509ExtendedTrustManager: JSSE wraps a plain X509TrustManager in one that " +
            "still performs hostname verification, and Tomcat forces the HTTPS endpoint " +
            "identification algorithm with no way to switch it off");
   }

   private static X509Certificate[] untrustedChain() {
      return new X509Certificate[] { mock(X509Certificate.class) };
   }

   // [G1]
   @Test
   void trustManagerIsTheExtendedVariant() throws Exception {
      assertNotNull(trustAll());
   }

   @Test
   void trustAllInitializesAnSslContext() throws Exception {
      // Guards the wiring buildWsClient depends on: an SSLContext will accept this manager.
      SSLContext context = SSLContext.getInstance("TLS");
      assertDoesNotThrow(() -> context.init(null, new TrustManager[] { trustAll() }, null));
   }

   // [G2] -- the case the old plain-manager implementation got wrong
   @Test
   void acceptsUntrustedChainEvenWhenTheEngineRequestsHttpsIdentification() throws Exception {
      SSLContext context = SSLContext.getInstance("TLS");
      context.init(null, new TrustManager[] { trustAll() }, null);

      // Exactly what Tomcat's WsWebSocketContainer.createSSLEngine does, unconditionally.
      SSLEngine engine = context.createSSLEngine("wrong-host.example.com", 443);
      SSLParameters params = engine.getSSLParameters();
      params.setEndpointIdentificationAlgorithm("HTTPS");
      engine.setSSLParameters(params);
      engine.setUseClientMode(true);

      assertEquals("HTTPS", engine.getSSLParameters().getEndpointIdentificationAlgorithm(),
                   "the test is only meaningful if identification really is requested");
      assertDoesNotThrow(() -> trustAll().checkServerTrusted(untrustedChain(), "RSA", engine));
   }

   // [G3]
   @Test
   void everyCheckOverloadAcceptsAnUntrustedChain() throws Exception {
      X509ExtendedTrustManager manager = trustAll();
      X509Certificate[] chain = untrustedChain();

      assertDoesNotThrow(() -> {
         manager.checkServerTrusted(chain, "RSA");
         manager.checkServerTrusted(chain, "RSA", (Socket) null);
         manager.checkServerTrusted(chain, "RSA", (SSLEngine) null);
         manager.checkClientTrusted(chain, "RSA");
         manager.checkClientTrusted(chain, "RSA", (Socket) null);
         manager.checkClientTrusted(chain, "RSA", (SSLEngine) null);
      }, "the SSLEngine/Socket overloads are the ones the identity check rides on");

      assertEquals(0, manager.getAcceptedIssuers().length);
   }
}
