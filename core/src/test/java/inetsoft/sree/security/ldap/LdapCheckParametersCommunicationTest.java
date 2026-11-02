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
package inetsoft.sree.security.ldap;

import inetsoft.sree.security.SRSecurityException;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.io.IOException;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #79079: checkParameters() (the EM "test connection" gate) must report every
 * CommunicationException, not only ConnectException/UnknownHostException. The "LDAP server"
 * is a local socket, so no real directory is required.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = BaseTestConfiguration.class,
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class LdapCheckParametersCommunicationTest {
   private ServerSocket server;
   private Thread acceptor;
   private volatile boolean closeAtOnce;
   private volatile byte[] reply;
   private GenericLdapAuthenticationProvider generic;
   private ADAuthenticationProvider ad;

   @BeforeEach
   void setUp() throws Exception {
      server = new ServerSocket(0);
      acceptor = new Thread(this::serve, "ldap-fake-server");
      acceptor.setDaemon(true);
      acceptor.start();
   }

   @AfterEach
   void tearDown() throws Exception {
      server.close();
      acceptor.join(5000);

      if(generic != null) {
         generic.tearDown();
      }

      if(ad != null) {
         ad.tearDown();
      }
   }

   private void serve() {
      while(!server.isClosed()) {
         try(Socket socket = server.accept()) {
            if(!closeAtOnce) {
               socket.setSoTimeout(2000);
               OutputStream out = socket.getOutputStream();

               if(reply != null) {
                  out.write(reply);
                  out.flush();
               }

               try {
                  socket.getInputStream().read(new byte[4096]);
                  socket.getInputStream().read(new byte[4096]);
               }
               catch(IOException ignore) {
                  // timeout or reset
               }
            }
         }
         catch(IOException ignore) {
            // server closed
         }
      }
   }

   private GenericLdapAuthenticationProvider generic(String protocol, int port) {
      generic = new GenericLdapAuthenticationProvider();
      generic.setProtocol(protocol);
      generic.setHost("localhost");
      generic.setPort(port);
      generic.setRootDn("dc=example,dc=com");
      generic.setLdapAdministrator("uid=admin,ou=system");
      generic.setPassword("secret");
      return generic;
   }

   @Test
   void ldapsToPlainTextServerIsReported() {
      reply = "not tls\r\n".getBytes(StandardCharsets.US_ASCII);
      SRSecurityException ex = assertThrows(SRSecurityException.class,
         () -> generic("ldaps", server.getLocalPort()).checkParameters());
      // The JDK client races between reading the plain-text bytes (SSLException, "TLS"
      // wording) and seeing the server close (SocketException "Connection or outbound has
      // closed", generic wording). Both are a reported connection failure; the wording
      // depends on that race, so assert the failure kind, not the wording (Bug #79135).
      assertInstanceOf(javax.naming.CommunicationException.class, ex.getCause(),
                       ex.getMessage());
      assertTrue(ex.getMessage().startsWith("Failed to"), ex.getMessage());
   }

   @Test
   void adLdapsToPlainTextServerIsReported() {
      reply = "not tls\r\n".getBytes(StandardCharsets.US_ASCII);
      ad = new ADAuthenticationProvider();
      ad.setProtocol("ldaps");
      ad.setHost("localhost");
      ad.setPort(server.getLocalPort());
      ad.setRootDn("dc=example,dc=com");
      ad.setLdapAdministrator("admin@example.com");
      ad.setPassword("secret");
      assertThrows(SRSecurityException.class, ad::checkParameters);
   }

   @Test
   void serverClosingImmediatelyIsReported() {
      closeAtOnce = true;
      assertThrows(SRSecurityException.class,
         () -> generic("ldap", server.getLocalPort()).checkParameters());
   }

   @Test
   void startTlsAgainstGarbageServerIsReported() {
      reply = "garbage garbage garbage\r\n".getBytes(StandardCharsets.US_ASCII);
      GenericLdapAuthenticationProvider p = generic("ldap", server.getLocalPort());
      p.setStartTls(true);
      assertThrows(SRSecurityException.class, p::checkParameters);
   }

   @Test
   void refusedPortStillReportsPortAndHost() throws Exception {
      int port;

      try(ServerSocket s = new ServerSocket(0)) {
         port = s.getLocalPort();
      }

      SRSecurityException ex = assertThrows(SRSecurityException.class,
         () -> generic("ldap", port).checkParameters());
      assertEquals("Invalid Port & Host Name combination", ex.getMessage());
   }
}
