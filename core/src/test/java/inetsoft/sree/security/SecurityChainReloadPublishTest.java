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
package inetsoft.sree.security;

import inetsoft.test.*;
import inetsoft.util.DataSpace;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #79355: {@link SecurityChain#loadConfiguration()} published an empty provider list and only
 * then tore the old providers down, so every reader of the chain (authentication, authorization,
 * the edit that reads the list to save it) saw no provider for as long as the old providers'
 * {@code tearDown()} took. The reload now publishes the new list once and tears the old providers
 * down afterwards. Every interleaving is driven by latches and counters, not sleeps, and every
 * wait is bounded.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = BaseTestConfiguration.class,
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class SecurityChainReloadPublishTest {
   private static final String AUTHC_FILE = "authc-chain.json";
   private static final String AUTHZ_FILE = "authz-chain.json";
   private static final long WAIT_SECONDS = 20L;
   private static final long WAIT_MILLIS = WAIT_SECONDS * 1000L;
   private static final int READS_DURING_TEARDOWN = 300;

   private final List<AuthenticationChain> authcChains = new ArrayList<>();
   private final List<AuthorizationChain> authzChains = new ArrayList<>();
   private final List<Thread> threads = new ArrayList<>();

   @AfterEach
   void tearDown() throws Exception {
      for(Thread thread : threads) {
         thread.join(WAIT_MILLIS);
         assertFalse(thread.isAlive(), "thread " + thread.getName() + " is still running");
      }

      authcChains.forEach(AuthenticationChain::tearDown);
      authzChains.forEach(AuthorizationChain::tearDown);
   }

   // an authentication chain never looks empty, and keeps finding a user, while the old
   // providers are being torn down

   @Test
   void authenticationChainIsNeverEmptyWhileOldProvidersTearDown() throws Exception {
      AuthenticationChain host = newAuthc();
      BlockingAuthn blocking = new BlockingAuthn("P");
      host.setProviders(List.of(blocking));
      IdentityID alice = new IdentityID("alice", Organization.getDefaultOrganizationID());
      blocking.addUser(new FSUser(alice));
      AuthenticationChain other = newAuthc();
      tick();
      other.setProviders(List.of(file("P")));
      tick();
      assertNotNull(host.getUser(alice), "the user must be known before the reload");

      AtomicInteger empty = new AtomicInteger();
      AtomicInteger refused = new AtomicInteger();
      Reader reader = new Reader(() -> {
         if(host.getProviders().isEmpty()) {
            empty.incrementAndGet();
         }

         if(host.findUserOwner(alice) == null) {
            refused.incrementAndGet();
         }
      });

      assertReaderSeesNoGap(blocking, host::loadConfiguration, reader);
      assertEquals(0, empty.get(), "reads that found the chain empty");
      assertEquals(0, refused.get(), "reads that refused a valid user");
      assertEquals(List.of("P"), names(host.getProviders()));
      assertNotSame(blocking, host.getProviders().get(0), "the file's provider replaced the old");
   }

   // the authorization chain reads the raw list in most of its methods

   @Test
   void authorizationChainIsNeverEmptyWhileOldProvidersTearDown() throws Exception {
      AuthorizationChain host = newAuthz();
      BlockingAuthz blocking = new BlockingAuthz("P");
      host.setProviders(List.of(blocking));
      AuthorizationChain other = newAuthz();
      tick();
      other.setProviders(List.of(authz("P")));
      tick();

      AtomicInteger empty = new AtomicInteger();
      Reader reader = new Reader(() -> {
         if(host.getProviderList().isEmpty() || host.getProviders().isEmpty() ||
            host.peekProviders().isEmpty())
         {
            empty.incrementAndGet();
         }
      });

      assertReaderSeesNoGap(blocking, host::loadConfiguration, reader);
      assertEquals(0, empty.get(), "reads that found the chain empty");
      assertEquals(List.of("P"), names(host.getProviders()));
   }

   // a tearDown() that throws neither leaves the chain without providers nor skips the
   // providers after it

   @Test
   void throwingTearDownKeepsNewListAndDisposesTheRest() throws Exception {
      AuthenticationChain host = newAuthc();
      ThrowingAuthn thrower = new ThrowingAuthn("A");
      CountingAuthn counter = new CountingAuthn("B");
      host.setProviders(List.of(thrower, counter));
      AuthenticationChain other = newAuthc();
      tick();
      other.setProviders(List.of(file("X"), file("Y")));
      tick();

      assertDoesNotThrow(host::loadConfiguration);

      assertEquals(List.of("X", "Y"), names(host.getProviders()));
      assertTrue(thrower.tornDown.get(), "the throwing provider was torn down");
      assertTrue(counter.tornDown.get(), "the provider after the throwing one was torn down");
   }

   // the new list is live, and the old providers are not yet disposed, when the first one is
   // disposed; and the listeners moved from the old list to the new one

   @Test
   void oldProvidersAreDisposedAfterTheNewListIsPublished() throws Exception {
      AuthenticationChain host = newAuthc();
      List<List<String>> seen = new CopyOnWriteArrayList<>();
      host.setProviders(List.of(new ObservingAuthn("A", () -> seen.add(names(host.getProviders())))));
      AuthenticationChain other = newAuthc();
      tick();
      other.setProviders(List.of(file("N1"), file("N2")));
      tick();

      host.loadConfiguration();

      assertEquals(List.of(List.of("N1", "N2")), seen);
   }

   // helpers

   /**
    * Starts the reload, waits until the old provider is inside its tearDown() and the reader has
    * read the chain many times, then lets the teardown finish.
    */
   private void assertReaderSeesNoGap(Blocking blocking, ThrowingRunnable reload, Reader reader)
      throws Exception
   {
      Thread reloader = start("reloader", reload);
      Thread readerThread = start("reader", reader);

      try {
         assertTrue(blocking.inTearDown().await(WAIT_SECONDS, TimeUnit.SECONDS),
                    "the old provider was never torn down");
         int from = reader.reads.get();
         long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(WAIT_SECONDS);

         while(reader.reads.get() < from + READS_DURING_TEARDOWN && System.nanoTime() < deadline) {
            Thread.onSpinWait();
         }

         assertTrue(reader.reads.get() >= from + READS_DURING_TEARDOWN,
                    "the reader did not run while the teardown was blocked");
         assertNull(reader.failure.get(), () -> "reader failed: " + reader.failure.get());
      }
      finally {
         blocking.release().countDown();
         reloader.join(WAIT_MILLIS);
         reader.stop.set(true);
         readerThread.join(WAIT_MILLIS);
      }

      assertFalse(reloader.isAlive(), "the reload did not finish");
   }

   private AuthenticationChain newAuthc() {
      AuthenticationChain chain = new AuthenticationChain();
      authcChains.add(chain);
      // the listener would reload on a file event; the tests call loadConfiguration() themselves
      DataSpace.getDataSpace().removeChangeListener(null, AUTHC_FILE, chain);
      return chain;
   }

   private AuthorizationChain newAuthz() {
      AuthorizationChain chain = new AuthorizationChain();
      authzChains.add(chain);
      DataSpace.getDataSpace().removeChangeListener(null, AUTHZ_FILE, chain);
      return chain;
   }

   private static FileAuthenticationProvider file(String name) {
      FileAuthenticationProvider provider = new FileAuthenticationProvider();
      provider.setProviderName(name);
      return provider;
   }

   private static FileAuthorizationProvider authz(String name) {
      FileAuthorizationProvider provider = new FileAuthorizationProvider();
      provider.setProviderName(name);
      return provider;
   }

   private static List<String> names(List<? extends JsonConfigurableProvider> list) {
      return list.stream().map(JsonConfigurableProvider::getProviderName)
         .collect(Collectors.toList());
   }

   /** Makes the next write's modification time later than the last one. */
   private static void tick() {
      long start = System.currentTimeMillis();

      while(System.currentTimeMillis() - start < 3L) {
         Thread.onSpinWait();
      }
   }

   private Thread start(String name, ThrowingRunnable action) {
      Thread thread = new Thread(() -> {
         try {
            action.run();
         }
         catch(Exception e) {
            throw new RuntimeException(e);
         }
      }, name);
      thread.setDaemon(true);
      threads.add(thread);
      thread.start();
      return thread;
   }

   @FunctionalInterface
   private interface ThrowingRunnable {
      void run() throws Exception;
   }

   /** Runs one check in a loop until stopped, counting the rounds. */
   private static final class Reader implements ThrowingRunnable {
      final AtomicInteger reads = new AtomicInteger();
      final AtomicBoolean stop = new AtomicBoolean();
      final AtomicReference<Throwable> failure = new AtomicReference<>();
      private final Runnable check;

      Reader(Runnable check) {
         this.check = check;
      }

      @Override
      public void run() {
         try {
            while(!stop.get()) {
               check.run();
               reads.incrementAndGet();
            }
         }
         catch(Throwable e) {
            failure.set(e);
         }
      }
   }

   private interface Blocking {
      CountDownLatch inTearDown();

      CountDownLatch release();
   }

   /** Blocks the "reloader" thread in tearDown() until released. */
   private static void block(CountDownLatch in, CountDownLatch release) {
      if(Thread.currentThread().getName().equals("reloader")) {
         in.countDown();

         try {
            release.await(WAIT_SECONDS, TimeUnit.SECONDS);
         }
         catch(InterruptedException e) {
            Thread.currentThread().interrupt();
         }
      }
   }

   public static class BlockingAuthn extends FileAuthenticationProvider implements Blocking {
      final CountDownLatch inTearDown = new CountDownLatch(1);
      final CountDownLatch release = new CountDownLatch(1);

      public BlockingAuthn() {
      }

      BlockingAuthn(String name) {
         setProviderName(name);
      }

      @Override
      public CountDownLatch inTearDown() {
         return inTearDown;
      }

      @Override
      public CountDownLatch release() {
         return release;
      }

      @Override
      public void tearDown() {
         block(inTearDown, release);
         super.tearDown();
      }
   }

   public static class BlockingAuthz extends FileAuthorizationProvider implements Blocking {
      final CountDownLatch inTearDown = new CountDownLatch(1);
      final CountDownLatch release = new CountDownLatch(1);

      public BlockingAuthz() {
      }

      BlockingAuthz(String name) {
         setProviderName(name);
      }

      @Override
      public CountDownLatch inTearDown() {
         return inTearDown;
      }

      @Override
      public CountDownLatch release() {
         return release;
      }

      @Override
      public void tearDown() {
         block(inTearDown, release);
         super.tearDown();
      }
   }

   public static class ThrowingAuthn extends FileAuthenticationProvider {
      final AtomicBoolean tornDown = new AtomicBoolean();

      public ThrowingAuthn() {
      }

      ThrowingAuthn(String name) {
         setProviderName(name);
      }

      @Override
      public void tearDown() {
         tornDown.set(true);
         throw new IllegalStateException("simulated tearDown failure");
      }
   }

   public static class CountingAuthn extends FileAuthenticationProvider {
      final AtomicBoolean tornDown = new AtomicBoolean();

      public CountingAuthn() {
      }

      CountingAuthn(String name) {
         setProviderName(name);
      }

      @Override
      public void tearDown() {
         tornDown.set(true);
         super.tearDown();
      }
   }

   public static class ObservingAuthn extends FileAuthenticationProvider {
      private Runnable onTearDown;

      public ObservingAuthn() {
      }

      ObservingAuthn(String name, Runnable onTearDown) {
         setProviderName(name);
         this.onTearDown = onTearDown;
      }

      @Override
      public void tearDown() {
         if(onTearDown != null) {
            onTearDown.run();
         }

         super.tearDown();
      }
   }
}
