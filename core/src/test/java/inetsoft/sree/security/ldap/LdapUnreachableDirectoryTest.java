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

package inetsoft.sree.security.ldap;

import inetsoft.sree.SreeEnv;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import javax.naming.CommunicationException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.*;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #79220: a directory that accepts the TCP connection but never answers must not block a
 * login (or the other logins queued behind it) forever.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = BaseTestConfiguration.class,
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("slow")
class LdapUnreachableDirectoryTest {
   private static final long TIMEOUT = 1500L;

   private ServerSocket blackHole;
   private final List<Socket> accepted = Collections.synchronizedList(new ArrayList<>());

   @BeforeEach
   void setUp() throws Exception {
      blackHole = new ServerSocket(0);
      Thread t = new Thread(() -> {
         while(!blackHole.isClosed()) {
            try {
               accepted.add(blackHole.accept()); // never read, never answer
            }
            catch(Exception e) {
               // closed
            }
         }
      }, "black-hole-ldap");
      t.setDaemon(true);
      t.start();

      SreeEnv.setProperty(LdapAuthenticationProvider.CONNECT_TIMEOUT_PROPERTY, "" + TIMEOUT);
      SreeEnv.setProperty(LdapAuthenticationProvider.READ_TIMEOUT_PROPERTY, "" + TIMEOUT);
      SreeEnv.setProperty(LdapAuthenticationProvider.FAILURE_RETRY_INTERVAL_PROPERTY, "30000");
   }

   @AfterEach
   void tearDown() throws Exception {
      blackHole.close();

      for(Socket s : new ArrayList<>(accepted)) {
         s.close();
      }

      SreeEnv.remove(LdapAuthenticationProvider.CONNECT_TIMEOUT_PROPERTY);
      SreeEnv.remove(LdapAuthenticationProvider.READ_TIMEOUT_PROPERTY);
      SreeEnv.remove(LdapAuthenticationProvider.FAILURE_RETRY_INTERVAL_PROPERTY);
   }

   private GenericLdapAuthenticationProvider generic() {
      GenericLdapAuthenticationProvider g = new GenericLdapAuthenticationProvider();
      g.setProtocol("ldap");
      g.setHost("localhost");
      g.setPort(blackHole.getLocalPort());
      g.setRootDn("dc=example,dc=com");
      g.setLdapAdministrator("uid=admin,ou=system");
      g.setPassword("secret");
      return g;
   }

   private ADAuthenticationProvider ad() {
      ADAuthenticationProvider ad = new ADAuthenticationProvider();
      ad.setProtocol("ldap");
      ad.setHost("localhost");
      ad.setPort(blackHole.getLocalPort());
      ad.setRootDn("dc=example,dc=com");
      ad.setLdapAdministrator("a@example.com");
      ad.setPassword("secret");
      return ad;
   }

   private static void assertBounded(ThrowingRunnable create) {
      long start = System.nanoTime();
      CompletableFuture<Throwable> result = CompletableFuture.supplyAsync(() -> {
         try {
            create.run();
            return null;
         }
         catch(Throwable e) {
            return e;
         }
      });

      Throwable error = assertDoesNotThrow(() -> result.get(TIMEOUT * 4, TimeUnit.MILLISECONDS),
                                           "context creation did not return in bounded time");
      long ms = (System.nanoTime() - start) / 1_000_000L;
      assertInstanceOf(CommunicationException.class, error);
      assertTrue(ms < TIMEOUT * 3, "took " + ms + " ms");
   }

   @Test
   void genericCreateContextIsBounded() {
      GenericLdapAuthenticationProvider g = generic();
      assertBounded(g::createContext);
   }

   @Test
   void genericStartTlsCreateContextIsBounded() {
      GenericLdapAuthenticationProvider g = generic();
      g.setStartTls(true);
      assertBounded(g::createContext);
   }

   @Test
   void adCreateContextIsBounded() {
      ADAuthenticationProvider ad = ad();
      assertBounded(ad::createContext);
   }

   @Test
   void invalidTimeoutPropertyFallsBackToDefault() {
      SreeEnv.setProperty(LdapAuthenticationProvider.CONNECT_TIMEOUT_PROPERTY, "abc");
      SreeEnv.setProperty(LdapAuthenticationProvider.READ_TIMEOUT_PROPERTY, "0");
      Map<Object, Object> env = new HashMap<>();
      LdapAuthenticationProvider.applyTimeouts(env);
      assertEquals("5000", env.get("com.sun.jndi.ldap.connect.timeout"));
      assertEquals("15000", env.get("com.sun.jndi.ldap.read.timeout"));
   }

   @Test
   void queuedBorrowersFailFastAfterFirstFailure() throws Exception {
      GenericLdapAuthenticationProvider g = generic();
      int borrowers = 3;
      ExecutorService exec = Executors.newFixedThreadPool(borrowers);

      try {
         long start = System.nanoTime();
         List<Future<Throwable>> futures = new ArrayList<>();

         for(int i = 0; i < borrowers; i++) {
            futures.add(exec.submit(() -> {
               try {
                  g.borrowContext();
                  return null;
               }
               catch(Throwable e) {
                  return e;
               }
            }));
         }

         for(Future<Throwable> f : futures) {
            Throwable e = f.get(TIMEOUT * 6, TimeUnit.MILLISECONDS);
            assertInstanceOf(CommunicationException.class, e);
         }

         long ms = (System.nanoTime() - start) / 1_000_000L;
         // without the memory the borrowers serialize behind the pool lock: ~3 x TIMEOUT
         assertTrue(ms < TIMEOUT * 2, "borrowers took " + ms + " ms");

         // a later borrower returns at once, and a flush lifts the memory
         long t0 = System.nanoTime();
         assertThrows(CommunicationException.class, g::borrowContext);
         assertTrue((System.nanoTime() - t0) / 1_000_000L < TIMEOUT / 2);

         g.flushContextPool();
         g.checkRecentCommunicationFailure();
      }
      finally {
         exec.shutdownNow();
      }
   }

   private interface ThrowingRunnable {
      void run() throws Exception;
   }
}
