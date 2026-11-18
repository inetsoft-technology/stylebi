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
package inetsoft.sree.internal.cluster.ignite;

import inetsoft.test.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import javax.net.ssl.*;
import java.io.*;
import java.lang.reflect.Field;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class ClusterFileTransferTest {
   @TempDir
   static Path dir;

   static SSLContext trusted;
   static SSLContext noKey;
   static InetAddress loopback;

   @BeforeAll
   static void setup() throws Exception {
      loopback = InetAddress.getByName("127.0.0.1");
      Path jks = dir.resolve("node.p12");
      String keytool = Path.of(System.getProperty("java.home"), "bin", "keytool").toString();
      Process p = new ProcessBuilder(
         keytool, "-genkeypair", "-alias", "node", "-keyalg", "RSA", "-keysize", "2048",
         "-dname", "CN=127.0.0.1", "-validity", "2", "-storetype", "PKCS12",
         "-keystore", jks.toString(), "-storepass", "changeit", "-keypass", "changeit")
         .redirectErrorStream(true).start();
      p.getInputStream().readAllBytes();
      assertEquals(0, p.waitFor());

      KeyStore ks = KeyStore.getInstance("PKCS12");

      try(InputStream in = Files.newInputStream(jks)) {
         ks.load(in, "changeit".toCharArray());
      }

      KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
      kmf.init(ks, "changeit".toCharArray());
      TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
      tmf.init(ks);
      trusted = SSLContext.getInstance("TLS");
      trusted.init(kmf.getKeyManagers(), tmf.getTrustManagers(), null);
      noKey = SSLContext.getInstance("TLS");
      noKey.init(null, tmf.getTrustManagers(), null);
   }

   private static int freePort() throws IOException {
      try(ServerSocket s = new ServerSocket(0)) {
         return s.getLocalPort();
      }
   }

   private File newFile(String content) throws IOException {
      File f = Files.createTempFile(dir, "src", ".dat").toFile();
      Files.writeString(f.toPath(), content);
      return f;
   }

   private static Object serverSocket(ClusterFileTransfer t) throws Exception {
      Field f = ClusterFileTransfer.class.getDeclaredField("fileTransferSocket");
      f.setAccessible(true);
      return f.get(t);
   }

   @Test
   void tlsRoundTrip() throws Exception {
      try(ClusterFileTransfer server = new ClusterFileTransfer(freePort(), loopback, trusted);
          ClusterFileTransfer client = new ClusterFileTransfer(freePort(), loopback, trusted))
      {
         assertTrue(serverSocket(server) instanceof SSLServerSocket);
         String link = server.addTransferFile(newFile("secret-payload"));
         File received = client.getTransferFile(link);
         assertEquals("secret-payload", Files.readString(received.toPath()));
      }
   }

   @Test
   void clientWithoutKeyFailsAndServerKeepsServing() throws Exception {
      try(ClusterFileTransfer server = new ClusterFileTransfer(freePort(), loopback, trusted);
          ClusterFileTransfer badClient = new ClusterFileTransfer(freePort(), loopback, noKey);
          ClusterFileTransfer goodClient = new ClusterFileTransfer(freePort(), loopback, trusted))
      {
         String link1 = server.addTransferFile(newFile("one"));
         assertThrows(IOException.class, () -> badClient.getTransferFile(link1));

         String link2 = server.addTransferFile(newFile("two"));
         File received = goodClient.getTransferFile(link2);
         assertEquals("two", Files.readString(received.toPath()));
      }
   }

   @Test
   void plainClientGetsNothingFromTlsServer() throws Exception {
      try(ClusterFileTransfer server = new ClusterFileTransfer(freePort(), loopback, trusted)) {
         String link = server.addTransferFile(newFile("secret-payload"));
         int port = Integer.parseInt(link.substring(link.indexOf(':') + 1, link.indexOf('/')));
         String id = link.substring(link.indexOf('/') + 1);

         try(Socket socket = new Socket(loopback, port)) {
            socket.setSoTimeout(2000);
            java.nio.ByteBuffer b = java.nio.ByteBuffer.allocate(16);
            java.util.UUID u = java.util.UUID.fromString(id);
            b.putLong(u.getMostSignificantBits()).putLong(u.getLeastSignificantBits());
            socket.getOutputStream().write(b.array());
            socket.getOutputStream().flush();
            byte[] data = new byte[0];

            try {
               data = socket.getInputStream().readAllBytes();
            }
            catch(IOException ignore) {
            }

            assertFalse(new String(data, StandardCharsets.ISO_8859_1).contains("secret-payload"));
         }
      }
   }

   @Test
   void plainRoundTripUnchanged() throws Exception {
      try(ClusterFileTransfer server = new ClusterFileTransfer(freePort(), loopback);
          ClusterFileTransfer client = new ClusterFileTransfer(freePort(), loopback))
      {
         assertFalse(serverSocket(server) instanceof SSLServerSocket);
         String link = server.addTransferFile(newFile("plain"));
         File received = client.getTransferFile(link);
         assertEquals("plain", Files.readString(received.toPath()));
      }
   }
}
