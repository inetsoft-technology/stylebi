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

import com.fasterxml.jackson.databind.ObjectMapper;
import inetsoft.test.*;
import inetsoft.util.DataSpace;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.io.InputStream;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #79127: an EM edit that starts while this node is reloading another node's commit (the
 * cluster lock makes that likely: the edit starts when the other node's edit releases it, which is
 * when the event of its save arrives) must start from the new list. The reload used to record the
 * digest of the new content before it swapped the list, and the check that decides whether to
 * reload runs outside the reload lock, so the edit's forced reload saw "already loaded", did not
 * wait, read the empty or the old list and overwrote the file (provider list lost). The reload is
 * held inside its window with latches, no sleeps; every wait is bounded.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = BaseTestConfiguration.class,
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class SecurityChainReloadEditRaceTest {
   private static final String AUTHC_FILE = "authc-chain.json";
   private static final long WAIT_SECONDS = 20L;

   private final List<AuthenticationChain> chains = new ArrayList<>();
   private final List<Thread> threads = new ArrayList<>();

   @AfterEach
   void tearDown() throws Exception {
      for(Thread thread : threads) {
         thread.join(WAIT_SECONDS * 1000L);
         assertFalse(thread.isAlive(), "thread " + thread.getName() + " is still running");
      }

      chains.forEach(AuthenticationChain::tearDown);
   }

   /**
    * The reload holds after the old list was cleared (the list is empty) while the old providers
    * are disposed.
    */
   @Test
   void editDuringTheEmptyListWindowStartsFromTheNewList() throws Exception {
      assertEditSeesTheNewList(true);
   }

   /**
    * The reload holds after it read the new content and before it published the list (the list is
    * still the old one).
    */
   @Test
   void editDuringTheOldListWindowStartsFromTheNewList() throws Exception {
      assertEditSeesTheNewList(false);
   }

   private void assertEditSeesTheNewList(boolean emptyWindow) throws Exception {
      Gated node1 = node(), node2 = node();
      node1.setProviders(named("P"));
      node2.loadConfiguration();
      assertEquals(List.of("P"), names(node2.getProviders()));

      // node 1's edit is saved, node 2's event starts reloading it
      node1.setProviders(named("P", "A"));
      node2.emptyWindow = emptyWindow;
      node2.gated = true;
      Thread event = start("reloader", node2::loadConfiguration);
      assertTrue(node2.inWindow.await(WAIT_SECONDS, TimeUnit.SECONDS), "reload never started");

      // node 2's admin edit starts now
      List<List<String>> seen = new CopyOnWriteArrayList<>();
      Thread edit = start("edit", () -> node2.editProviders(() -> {
         List<AuthenticationProvider> list = node2.getProviders();
         seen.add(names(list));
         list.addAll(named("B"));
         node2.setProviders(list);
      }));
      awaitBlocked(edit);
      node2.release.countDown();
      event.join(WAIT_SECONDS * 1000L);
      edit.join(WAIT_SECONDS * 1000L);

      assertEquals(List.of(List.of("P", "A")), seen, "the edit started from a stale list");
      assertEquals(List.of("P", "A", "B"), fileNames(), "the edit overwrote the other change");
      assertEquals(List.of("P", "A", "B"), names(node2.getProviders()));
   }

   // helpers

   private Gated node() {
      Gated chain = new Gated();
      chains.add(chain);
      DataSpace.getDataSpace().removeChangeListener(null, AUTHC_FILE, chain);
      return chain;
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

   /**
    * Waits until the thread waits for a lock, or has finished (a broken lock order, which the
    * assertions then report).
    */
   private static void awaitBlocked(Thread thread) {
      long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);

      while(System.nanoTime() < deadline && thread.isAlive() &&
            thread.getState() != Thread.State.WAITING && thread.getState() != Thread.State.BLOCKED)
      {
         Thread.onSpinWait();
      }
   }

   private static List<AuthenticationProvider> named(String... names) {
      List<AuthenticationProvider> list = new ArrayList<>();

      for(String name : names) {
         FileAuthenticationProvider provider = new FileAuthenticationProvider();
         provider.setProviderName(name);
         list.add(provider);
      }

      return list;
   }

   private static List<String> names(List<? extends JsonConfigurableProvider> list) {
      return list.stream().map(JsonConfigurableProvider::getProviderName)
         .collect(Collectors.toList());
   }

   private static List<String> fileNames() throws Exception {
      try(InputStream in = DataSpace.getDataSpace().getInputStream(null, AUTHC_FILE)) {
         List<String> names = new ArrayList<>();
         new ObjectMapper().readTree(in).get("providers")
            .forEach(node -> names.add(node.get("name").asText()));
         return names;
      }
   }

   @FunctionalInterface
   private interface ThrowingRunnable {
      void run() throws Exception;
   }

   /**
    * Holds the thread named "reloader" inside a reload: when the old providers are disposed
    * (after the list was cleared), or when the old providers are described (after the new content
    * was read, before the list is published).
    */
   private static final class Gated extends AuthenticationChain {
      final CountDownLatch inWindow = new CountDownLatch(1);
      final CountDownLatch release = new CountDownLatch(1);
      volatile boolean gated;
      volatile boolean emptyWindow;

      @Override
      Map<String, String> describeProviders(List<AuthenticationProvider> list) {
         if(!emptyWindow) {
            park();
         }

         return super.describeProviders(list);
      }

      @Override
      void dispose(AuthenticationProvider provider) {
         if(emptyWindow) {
            park();
         }

         super.dispose(provider);
      }

      private void park() {
         if(gated && "reloader".equals(Thread.currentThread().getName())) {
            gated = false;
            inWindow.countDown();

            try {
               release.await(WAIT_SECONDS, TimeUnit.SECONDS);
            }
            catch(InterruptedException e) {
               Thread.currentThread().interrupt();
            }
         }
      }
   }
}
