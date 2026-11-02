package inetsoft.sree.security.ldap;

import inetsoft.sree.security.*;
import inetsoft.test.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import javax.net.ssl.*;
import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.security.KeyStore;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #79079: runs checkParameters() against a minimal in-process LDAP server (bind and search
 * answered in raw BER, optional TLS with a throwaway self-signed certificate) to show a valid
 * configuration still passes, bad credentials keep their message, and an untrusted certificate
 * (ldaps and StartTLS) is reported instead of passing the connection test.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = BaseTestConfiguration.class,
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class LdapCheckParametersFakeServerTest {
   static class Fake implements Closeable {
      ServerSocket ss;
      volatile String mode; // plain-ok, tls, starttls
      volatile int bindRc = 0;

      Fake(String mode) throws Exception {
         this.mode = mode;

         if(mode.equals("tls")) {
            ss = ctx().getServerSocketFactory().createServerSocket(0);
         }
         else {
            ss = new ServerSocket(0);
         }

         Thread t = new Thread(this::run, "fake-ldap");
         t.setDaemon(true);
         t.start();
      }

      int port() {
         return ss.getLocalPort();
      }

      void run() {
         while(!ss.isClosed()) {
            try {
               Socket s = ss.accept();
               Thread h = new Thread(() -> handle(s));
               h.setDaemon(true);
               h.start();
            }
            catch(IOException e) {
            }
         }
      }

      void handle(Socket s) {
         try {
            serveLdap(s);
         }
         catch(Exception e) {
            System.out.println("FAKE handler: " + e);
         }
      }

      void serveLdap(Socket s) throws Exception {
         DataInputStream in = new DataInputStream(s.getInputStream());
         OutputStream out = s.getOutputStream();

         while(true) {
            int tag = in.read();

            if(tag < 0) {
               return;
            }

            if(tag == 0x16) {
               s.close();
               return;
            }

            int len = in.read();

            if((len & 0x80) != 0) {
               int n = len & 0x7f;
               len = 0;

               for(int i = 0; i < n; i++) {
                  len = (len << 8) | in.read();
               }
            }

            byte[] body = new byte[len];
            in.readFully(body);
            int idLen = body[1];
            int id = body[2];
            int opTag = body[2 + idLen] & 0xff;
            byte[] resp = null;

            if(opTag == 0x60) {
               resp = msg(id, new byte[] { 0x61, 7, 0x0a, 1, (byte) bindRc, 4, 0, 4, 0 });
            }
            else if(opTag == 0x63) {
               byte[] dn = "dc=example,dc=com".getBytes();
               byte[] entry = new byte[2 + 2 + dn.length + 2];
               int p = 0;
               entry[p++] = 0x64;
               entry[p++] = (byte) (2 + dn.length + 2);
               entry[p++] = 4;
               entry[p++] = (byte) dn.length;
               System.arraycopy(dn, 0, entry, p, dn.length);
               p += dn.length;
               entry[p++] = 0x30;
               entry[p++] = 0;
               ByteArrayOutputStream bo = new ByteArrayOutputStream();
               bo.write(msg(id, entry));
               bo.write(msg(id, new byte[] { 0x65, 7, 0x0a, 1, 0, 4, 0, 4, 0 }));
               resp = bo.toByteArray();
            }
            else if(opTag == 0x77 && mode.equals("starttls")) {
               out.write(msg(id, new byte[] { 0x78, 7, 0x0a, 1, 0, 4, 0, 4, 0 }));
               out.flush();
               SSLSocket ssl = (SSLSocket) ctx().getSocketFactory()
                  .createSocket(s, null, s.getPort(), true);
               ssl.setUseClientMode(false);
               ssl.startHandshake();
               s = ssl;
               in = new DataInputStream(s.getInputStream());
               out = s.getOutputStream();
               continue;
            }
            else if(opTag == 0x42) {
               s.close();
               return;
            }

            if(resp != null) {
               out.write(resp);
               out.flush();
            }
         }
      }

      static byte[] msg(int id, byte[] op) {
         byte[] m = new byte[2 + 3 + op.length];
         m[0] = 0x30;
         m[1] = (byte) (3 + op.length);
         m[2] = 2;
         m[3] = 1;
         m[4] = (byte) id;
         System.arraycopy(op, 0, m, 5, op.length);
         return m;
      }

      static synchronized SSLContext ctx() throws Exception {
         Path ksf = Path.of(System.getProperty("java.io.tmpdir"),
                            "fake-ldap-" + ProcessHandle.current().pid() + ".p12");

         if(!Files.exists(ksf)) {
            String keytool = Path.of(System.getProperty("java.home"), "bin", "keytool").toString();
            Process p = new ProcessBuilder(keytool, "-genkeypair", "-alias", "k", "-keyalg", "RSA",
               "-keysize", "2048", "-dname", "CN=localhost", "-validity", "2", "-storetype",
               "PKCS12", "-keystore", ksf.toString(), "-storepass", "changeit", "-ext",
               "san=dns:localhost").redirectErrorStream(true).start();
            p.getInputStream().readAllBytes();
            p.waitFor();
            ksf.toFile().deleteOnExit();
         }

         KeyStore ks = KeyStore.getInstance("PKCS12");

         try(InputStream i = Files.newInputStream(ksf)) {
            ks.load(i, "changeit".toCharArray());
         }

         KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
         kmf.init(ks, "changeit".toCharArray());
         SSLContext c = SSLContext.getInstance("TLS");
         c.init(kmf.getKeyManagers(), null, null);
         return c;
      }

      public void close() throws IOException {
         ss.close();
      }
   }

   private static GenericLdapAuthenticationProvider generic(String proto, int port) {
      GenericLdapAuthenticationProvider g = new GenericLdapAuthenticationProvider();
      g.setProtocol(proto);
      g.setHost("localhost");
      g.setPort(port);
      g.setRootDn("dc=example,dc=com");
      g.setLdapAdministrator("uid=admin,ou=system");
      g.setPassword("secret");
      return g;
   }

   private static ADAuthenticationProvider ad(String proto, int port) {
      ADAuthenticationProvider ad = new ADAuthenticationProvider();
      ad.setProtocol(proto);
      ad.setHost("localhost");
      ad.setPort(port);
      ad.setRootDn("dc=example,dc=com");
      ad.setLdapAdministrator("a@example.com");
      ad.setPassword("secret");
      return ad;
   }

   @Test
   void validConfigurationStillPasses() throws Exception {
      try(Fake f = new Fake("plain-ok")) {
         GenericLdapAuthenticationProvider g = generic("ldap", f.port());
         ADAuthenticationProvider a = ad("ldap", f.port());

         try {
            assertDoesNotThrow(g::checkParameters);
            assertDoesNotThrow(a::checkParameters);
         }
         finally {
            g.tearDown();
            a.tearDown();
         }
      }
   }

   @Test
   void badCredentialsKeepTheirMessage() throws Exception {
      try(Fake f = new Fake("plain-ok")) {
         f.bindRc = 49;
         GenericLdapAuthenticationProvider g = generic("ldap", f.port());

         try {
            SRSecurityException ex = assertThrows(SRSecurityException.class, g::checkParameters);
            assertEquals("Invalid Administrator Credentials.", ex.getMessage());
         }
         finally {
            g.tearDown();
         }
      }
   }

   @Test
   void ldapsWithUntrustedCertificateIsReported() throws Exception {
      try(Fake f = new Fake("tls")) {
         GenericLdapAuthenticationProvider g = generic("ldaps", f.port());
         ADAuthenticationProvider a = ad("ldaps", f.port());

         try {
            assertThrows(SRSecurityException.class, g::checkParameters);
            assertThrows(SRSecurityException.class, a::checkParameters);
         }
         finally {
            g.tearDown();
            a.tearDown();
         }
      }
   }

   @Test
   void startTlsWithUntrustedCertificateIsReported() throws Exception {
      try(Fake f = new Fake("starttls")) {
         GenericLdapAuthenticationProvider g = generic("ldap", f.port());
         g.setStartTls(true);

         try {
            SRSecurityException ex = assertThrows(SRSecurityException.class, g::checkParameters);
            assertTrue(ex.getMessage().contains("TLS"), ex.getMessage());
         }
         finally {
            g.tearDown();
         }
      }
   }
}
