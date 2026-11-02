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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import inetsoft.test.*;
import inetsoft.util.DataChangeListener;
import inetsoft.util.DataSpace;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.lang.management.ManagementFactory;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #79127: {@link SecurityChain#setProviders(List)} set the list outside the chain's reload
 * lock and {@link SecurityChain#saveConfiguration()} read it outside the lock, so an EM edit that
 * overlapped the reload of a change saved by another node left authc-chain.json and the runtime
 * chain with different providers. Every interleaving here is driven by latches and thread states,
 * not sleeps, and every wait is bounded. The latches are instance fields of the chains and
 * providers of one test, so no state outlives it.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = BaseTestConfiguration.class,
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class SecurityChainSetProvidersLockTest {
   private static final String AUTHC_FILE = "authc-chain.json";
   private static final String AUTHZ_FILE = "authz-chain.json";
   private static final long WAIT_SECONDS = 20L;
   private static final long WAIT_MILLIS = WAIT_SECONDS * 1000L;

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

   // (a) an EM edit lands while a reload has published its list and not yet disposed the old

   @Test
   void emEditInsideReloadWindow() throws Exception {
      assertConverges(List.of("A"), List.of("A", "E"), List.of("A", "R"));
   }

   // (c) add, remove and reorder go through the same setProviders() path

   @Test
   void addInsideReloadWindow() throws Exception {
      assertConverges(List.of("A", "B"), List.of("A", "B", "E"), List.of("A", "B", "R"));
   }

   @Test
   void removeInsideReloadWindow() throws Exception {
      assertConverges(List.of("A", "B", "C"), List.of("A", "C"), List.of("A", "B", "C", "R"));
   }

   @Test
   void reorderInsideReloadWindow() throws Exception {
      assertConverges(List.of("A", "B", "C"), List.of("C", "A", "B"), List.of("A", "B", "R"));
   }

   private void assertConverges(List<String> initial, List<String> emNames,
                                List<String> reloadNames) throws Exception
   {
      HookedAuthcChain host = newHost(initial);
      AuthenticationChain other = newAuthc();
      // the EM request read its list before the other node saved
      List<AuthenticationProvider> emList = named(emNames);
      save(other, reloadNames);

      Thread reloader = start("reloader", host::loadConfiguration);
      assertTrue(host.inWindow.await(WAIT_SECONDS, TimeUnit.SECONDS), "reload never started");

      // the reload has published the list and holds its lock until the old providers are
      // disposed: the EM save lands now
      Thread em = start("em", () -> host.setProviders(emList));
      awaitState(em, Thread.State.WAITING);
      host.release.countDown();
      reloader.join(WAIT_MILLIS);
      em.join(WAIT_MILLIS);

      assertEquals(emNames, fileNames(AUTHC_FILE), "file");
      assertEquals(emNames, names(host.getProviders()), "runtime");
   }

   // the authorization chain shares the base class path

   @Test
   void authorizationChainEditInsideReloadWindow() throws Exception {
      HookedAuthzChain host = new HookedAuthzChain();
      authzChains.add(host);
      removeListener(AUTHZ_FILE, host);
      host.setProviders(authz("A"));
      tick();
      AuthorizationChain other = new AuthorizationChain();
      authzChains.add(other);
      removeListener(AUTHZ_FILE, other);
      List<AuthorizationProvider> emList = authz("A", "E");
      tick();
      other.setProviders(authz("A", "R"));
      tick();

      Thread reloader = start("reloader", host::loadConfiguration);
      assertTrue(host.inWindow.await(WAIT_SECONDS, TimeUnit.SECONDS), "reload never started");
      Thread em = start("em", () -> host.setProviders(emList));
      awaitState(em, Thread.State.WAITING);
      host.release.countDown();
      reloader.join(WAIT_MILLIS);
      em.join(WAIT_MILLIS);

      assertEquals(List.of("A", "E"), fileNames(AUTHZ_FILE), "file");
      assertEquals(List.of("A", "E"), names(host.getProviders()), "runtime");
   }

   // (b) a reload tries to complete between the save's read of the list and its write

   @Test
   void reloadBetweenSaveReadAndWrite() throws Exception {
      HookedAuthcChain host = newHost(List.of("A"));
      AuthenticationChain other = newAuthc();
      GatedProvider gated = new GatedProvider("E");
      List<AuthenticationProvider> emList = new ArrayList<>(List.of(named("A").get(0), gated));
      save(other, List.of("A", "R"));

      Thread em = start("em", () -> host.setProviders(emList));
      assertTrue(gated.inWrite.await(WAIT_SECONDS, TimeUnit.SECONDS), "save never started");

      // the save is serializing the list: the reload must wait for it, not run in between
      Thread reloader = start("reloader", host::loadConfiguration);
      awaitState(reloader, Thread.State.WAITING);
      gated.releaseWrite.countDown();
      em.join(WAIT_MILLIS);
      reloader.join(WAIT_MILLIS);

      assertEquals(List.of("A", "E"), fileNames(AUTHC_FILE), "file");
      assertEquals(List.of("A", "E"), names(host.getProviders()), "runtime");
   }

   // a bare save inside the window must write the list the reload published, not the old one

   @Test
   void bareSaveInsideReloadWindowDoesNotWriteEmptyList() throws Exception {
      HookedAuthcChain host = newHost(List.of("A"));
      AuthenticationChain other = newAuthc();
      save(other, List.of("A", "R"));

      Thread reloader = start("reloader", host::loadConfiguration);
      assertTrue(host.inWindow.await(WAIT_SECONDS, TimeUnit.SECONDS), "reload never started");
      Thread saver = start("saver", host::saveConfiguration);
      awaitState(saver, Thread.State.WAITING);
      host.release.countDown();
      reloader.join(WAIT_MILLIS);
      saver.join(WAIT_MILLIS);

      assertEquals(List.of("A", "R"), fileNames(AUTHC_FILE), "file");
      assertEquals(List.of("A", "R"), names(host.getProviders()), "runtime");
   }

   // an EM edit as AuthenticationProviderService does it reads the list and adds to it: inside
   // the window it must read the published list, or it would save a chain of only its own
   // provider and wipe the file for the cluster (Bug #79355)

   @Test
   void emReadModifyWriteInsideReloadWindowKeepsTheProviders() throws Exception {
      HookedAuthcChain host = newHost(List.of("A"));
      AuthenticationChain other = newAuthc();
      save(other, List.of("A", "R"));

      Thread reloader = start("reloader", host::loadConfiguration);
      assertTrue(host.inWindow.await(WAIT_SECONDS, TimeUnit.SECONDS), "reload never started");

      Thread em = start("em", () -> {
         host.writeLock();

         try {
            List<AuthenticationProvider> list = host.getProviders();
            assertEquals(List.of("A", "R"), names(list), "the list the edit read");
            list.add(named("E").get(0));
            host.setProviders(list);
         }
         finally {
            host.writeUnlock();
         }
      });
      awaitState(em, Thread.State.WAITING);
      host.release.countDown();
      reloader.join(WAIT_MILLIS);
      em.join(WAIT_MILLIS);

      assertEquals(List.of("A", "R", "E"), fileNames(AUTHC_FILE), "file");
      assertEquals(List.of("A", "R", "E"), names(host.getProviders()), "runtime");
   }

   // (d) a reload, an EM edit as AuthenticationProviderService does it (write lock, then save),
   // and readers of every kind finish in the documented lock order

   @Test
   void reloadEditAndReadersDoNotDeadlock() throws Exception {
      HookedAuthcChain host = newHost(List.of("A"));
      AuthenticationChain other = newAuthc();
      save(other, List.of("A", "R"));

      Thread reloader = start("reloader", host::loadConfiguration);
      assertTrue(host.inWindow.await(WAIT_SECONDS, TimeUnit.SECONDS), "reload never started");

      ExecutorService pool = Executors.newFixedThreadPool(3, r -> {
         Thread t = new Thread(r, "reader");
         t.setDaemon(true);
         return t;
      });

      try {
         // lock free: must answer while the reload holds its lock inside the window, with the
         // list the reload published and never an empty one (Bug #79355)
         Future<List<AuthenticationProvider>> peek = pool.submit(host::peekProviders);
         assertEquals(List.of("A", "R"), names(peek.get(WAIT_SECONDS, TimeUnit.SECONDS)));

         Future<?> edit = pool.submit(() -> {
            host.writeLock();

            try {
               List<AuthenticationProvider> list = host.getProviders();
               list.add(named("E").get(0));
               host.setProviders(list);
            }
            finally {
               host.writeUnlock();
            }
         });
         Future<List<AuthenticationProvider>> after =
            pool.submit(() -> host.getProvidersAfterReload("test"));
         Future<List<AuthenticationProvider>> read = pool.submit(host::getProviders);

         host.release.countDown();

         try {
            reloader.join(WAIT_MILLIS);
            assertFalse(reloader.isAlive(), "the reload did not finish");
            edit.get(WAIT_SECONDS, TimeUnit.SECONDS);
            after.get(WAIT_SECONDS, TimeUnit.SECONDS);
            read.get(WAIT_SECONDS, TimeUnit.SECONDS);
         }
         catch(TimeoutException e) {
            long[] ids = ManagementFactory.getThreadMXBean().findDeadlockedThreads();
            fail("timed out, deadlocked threads: " + (ids == null ? "none found" :
               Arrays.stream(ManagementFactory.getThreadMXBean().getThreadInfo(ids))
                  .map(Object::toString).collect(Collectors.joining("\n"))));
         }
      }
      finally {
         host.release.countDown();
         pool.shutdownNow();
         assertTrue(pool.awaitTermination(WAIT_SECONDS, TimeUnit.SECONDS));
      }

      assertEquals(fileNames(AUTHC_FILE), names(host.getProviders()));
   }

   // helpers

   private HookedAuthcChain newHost(List<String> initial) {
      HookedAuthcChain host = new HookedAuthcChain();
      authcChains.add(host);
      removeListener(AUTHC_FILE, host);
      host.setProviders(named(initial));
      tick();
      return host;
   }

   private AuthenticationChain newAuthc() {
      AuthenticationChain chain = new AuthenticationChain();
      authcChains.add(chain);
      removeListener(AUTHC_FILE, chain);
      return chain;
   }

   private static void save(AuthenticationChain chain, List<String> names) {
      tick();
      chain.setProviders(named(names));
      tick();
   }

   /** The listener would reload on a file event; the tests call loadConfiguration() themselves. */
   private static void removeListener(String file, DataChangeListener listener) {
      DataSpace.getDataSpace().removeChangeListener(null, file, listener);
   }

   private static List<AuthenticationProvider> named(List<String> names) {
      List<AuthenticationProvider> list = new ArrayList<>();

      for(String name : names) {
         FileAuthenticationProvider provider = new FileAuthenticationProvider();
         provider.setProviderName(name);
         list.add(provider);
      }

      return list;
   }

   private static List<AuthenticationProvider> named(String name) {
      return named(List.of(name));
   }

   private static List<AuthorizationProvider> authz(String... names) {
      List<AuthorizationProvider> list = new ArrayList<>();

      for(String name : names) {
         FileAuthorizationProvider provider = new FileAuthorizationProvider();
         provider.setProviderName(name);
         list.add(provider);
      }

      return list;
   }

   private static List<String> names(List<? extends JsonConfigurableProvider> list) {
      return list.stream().map(JsonConfigurableProvider::getProviderName)
         .collect(Collectors.toList());
   }

   /** What another node, or this one after a restart, would load from the file. */
   private List<String> fileNames(String file) {
      if(AUTHC_FILE.equals(file)) {
         AuthenticationChain fresh = new AuthenticationChain();
         authcChains.add(fresh);
         removeListener(file, fresh);
         return names(fresh.getProviders());
      }

      AuthorizationChain fresh = new AuthorizationChain();
      authzChains.add(fresh);
      removeListener(file, fresh);
      return names(fresh.getProviders());
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

   private static void awaitState(Thread thread, Thread.State state) {
      long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(WAIT_SECONDS);

      while(thread.getState() != state && System.nanoTime() < deadline) {
         Thread.onSpinWait();
      }

      assertEquals(state, thread.getState(), "thread " + thread.getName());
   }

   private static void park(CountDownLatch in, CountDownLatch release) {
      // null while the superclass constructor runs, before the fields are assigned
      if(in != null && Thread.currentThread().getName().equals("reloader")) {
         in.countDown();

         try {
            release.await(WAIT_SECONDS, TimeUnit.SECONDS);
         }
         catch(InterruptedException e) {
            Thread.currentThread().interrupt();
         }
      }
   }

   @FunctionalInterface
   private interface ThrowingRunnable {
      void run() throws Exception;
   }

   /**
    * Parks the thread named "reloader" after setProviderList() and before the old providers are
    * disposed, with the reload lock held.
    */
   private static final class HookedAuthcChain extends AuthenticationChain {
      final CountDownLatch inWindow;
      final CountDownLatch release;

      HookedAuthcChain() {
         super();
         inWindow = new CountDownLatch(1);
         release = new CountDownLatch(1);
      }

      @Override
      void disposeAll(List<AuthenticationProvider> list) {
         park(inWindow, release);
         super.disposeAll(list);
      }
   }

   private static final class HookedAuthzChain extends AuthorizationChain {
      final CountDownLatch inWindow;
      final CountDownLatch release;

      HookedAuthzChain() {
         super();
         inWindow = new CountDownLatch(1);
         release = new CountDownLatch(1);
      }

      @Override
      void disposeAll(List<AuthorizationProvider> list) {
         park(inWindow, release);
         super.disposeAll(list);
      }
   }

   /** Parks the thread named "em" while it serializes this provider. */
   public static class GatedProvider extends FileAuthenticationProvider {
      final CountDownLatch inWrite = new CountDownLatch(1);
      final CountDownLatch releaseWrite = new CountDownLatch(1);

      public GatedProvider() {
      }

      GatedProvider(String name) {
         setProviderName(name);
      }

      @Override
      public JsonNode writeConfiguration(ObjectMapper mapper) {
         if(Thread.currentThread().getName().equals("em")) {
            inWrite.countDown();

            try {
               releaseWrite.await(WAIT_SECONDS, TimeUnit.SECONDS);
            }
            catch(InterruptedException e) {
               Thread.currentThread().interrupt();
            }
         }

         return super.writeConfiguration(mapper);
      }
   }
}
