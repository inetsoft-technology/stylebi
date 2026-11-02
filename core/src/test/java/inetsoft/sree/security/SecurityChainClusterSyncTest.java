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

import inetsoft.sree.internal.cluster.Cluster;
import inetsoft.test.*;
import inetsoft.util.DataChangeListener;
import inetsoft.util.DataSpace;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.io.InputStream;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.locks.Lock;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #79127: nodes of a cluster must end with the providers the chain file holds, and a
 * read-modify-write of the providers must not overwrite another node's edit. Several chain
 * instances over one {@link DataSpace} stand in for the nodes. The listeners are removed and
 * {@code loadConfiguration()} is called where a node would process the file event, so every
 * interleaving is placed by the test, with an explicit mtime where the writes tie or invert.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = BaseTestConfiguration.class,
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class SecurityChainClusterSyncTest {
   private static final String AUTHC_FILE = "authc-chain.json";
   private static final String AUTHZ_FILE = "authz-chain.json";
   private static final String AUTHC_LOCK = "inetsoft.security.chain.editLock." + AUTHC_FILE;

   private final List<AuthenticationChain> authcChains = new ArrayList<>();
   private final List<AuthorizationChain> authzChains = new ArrayList<>();

   @AfterEach
   void tearDown() {
      authcChains.forEach(AuthenticationChain::tearDown);
      authzChains.forEach(AuthorizationChain::tearDown);
   }

   // the later write carries an earlier mtime than a write that landed before it

   @Test
   void laterLandingWriteWithEarlierMtimeIsReloaded() throws Exception {
      DataSpace ds = DataSpace.getDataSpace();
      AuthenticationChain a = node(), b = node(), c = node();
      tick();
      a.setProviders(named("A"));
      long stampA = ds.getLastModified(null, AUTHC_FILE);
      byte[] contentA = readFile(ds);
      tick();
      b.setProviders(named("B"));
      c.loadConfiguration();
      assertEquals(List.of("B"), names(c));

      // A was stamped before B but lands after it
      ds.withOutputStream(null, AUTHC_FILE, stampA, out -> out.write(contentA));
      assertTrue(stampA < ds.getLastModified(null, AUTHC_FILE) + 1);

      for(AuthenticationChain node : List.of(a, b, c)) {
         node.loadConfiguration();
      }

      assertEquals(List.of("A"), names(a));
      assertEquals(List.of("A"), names(b), "node 2 kept its overwritten save");
      assertEquals(List.of("A"), names(c), "node 3 kept the older content");
   }

   @Test
   void laterLandingWriteWithEqualMtimeIsReloaded() throws Exception {
      DataSpace ds = DataSpace.getDataSpace();
      AuthenticationChain a = node(), b = node(), c = node();
      tick();
      a.setProviders(named("A"));
      byte[] contentA = readFile(ds);
      tick();
      b.setProviders(named("B"));
      c.loadConfiguration();
      long stampB = ds.getLastModified(null, AUTHC_FILE);

      ds.withOutputStream(null, AUTHC_FILE, stampB, out -> out.write(contentA));
      assertEquals(stampB, ds.getLastModified(null, AUTHC_FILE));

      for(AuthenticationChain node : List.of(a, b, c)) {
         node.loadConfiguration();
      }

      assertEquals(List.of("A"), names(a));
      assertEquals(List.of("A"), names(b), "node 2 kept its overwritten save");
      assertEquals(List.of("A"), names(c), "node 3 kept the older content");
   }

   @Test
   void authorizationChainFollowsTheSameRule() throws Exception {
      DataSpace ds = DataSpace.getDataSpace();
      AuthorizationChain a = authzNode(), b = authzNode(), c = authzNode();
      tick();
      a.setProviders(authz("A"));
      byte[] contentA = readFile(ds, AUTHZ_FILE);
      tick();
      b.setProviders(authz("B"));
      c.loadConfiguration();
      long stampB = ds.getLastModified(null, AUTHZ_FILE);

      ds.withOutputStream(null, AUTHZ_FILE, stampB, out -> out.write(contentA));

      for(AuthorizationChain node : List.of(a, b, c)) {
         node.loadConfiguration();
      }

      assertEquals(List.of("A"), names(a.getProviders()));
      assertEquals(List.of("A"), names(b.getProviders()), "node 2 kept its overwritten save");
      assertEquals(List.of("A"), names(c.getProviders()), "node 3 kept the older content");
   }

   // A -> B -> A: content a node held before comes back

   @Test
   void abaIsNotSkipped() throws Exception {
      AuthenticationChain a = node(), b = node();
      tick();
      a.setProviders(named("A"));
      b.loadConfiguration();
      assertEquals(List.of("A"), names(b));

      tick();
      a.setProviders(named("B"));
      b.loadConfiguration();
      assertEquals(List.of("B"), names(b));

      tick();
      a.setProviders(named("A"));
      b.loadConfiguration();
      assertEquals(List.of("A"), names(b), "the content that came back was skipped");
   }

   @Test
   void abaOfAnOverwrittenOwnSaveIsNotSkipped() throws Exception {
      AuthenticationChain a = node(), b = node();
      tick();
      a.setProviders(named("A"));
      b.loadConfiguration();
      tick();
      b.setProviders(named("B"));
      tick();
      // another node overwrites B's save with the content B held before it
      a.setProviders(named("A"));

      b.loadConfiguration();

      assertEquals(List.of("A"), names(b), "the overwritten save was kept");
   }

   // reload storms: the writer's own content never reloads it, an unrelated event is a no-op

   @Test
   void ownSaveAndRepeatedEventsDoNotReload() throws Exception {
      AuthenticationChain a = node();
      tick();
      a.setProviders(named("A", "B"));
      List<AuthenticationProvider> held = a.getProviderList();

      for(int i = 0; i < 3; i++) {
         a.loadConfiguration();
      }

      assertSame(held, a.getProviderList(), "own save was reloaded");

      AuthenticationChain b = node();
      List<AuthenticationProvider> loaded = b.getProviderList();
      b.loadConfiguration();
      b.loadConfiguration();
      assertSame(loaded, b.getProviderList(), "an unchanged file was reloaded");
   }

   @Test
   void ownSaveThatWasOverwrittenIsReloaded() throws Exception {
      AuthenticationChain a = node(), b = node();
      DataSpace ds = DataSpace.getDataSpace();
      tick();
      a.setProviders(named("A"));
      tick();
      b.setProviders(named("B"));
      byte[] contentB = readFile(ds);
      tick();
      a.setProviders(named("A", "A2"));
      assertEquals(List.of("A", "A2"), names(a));

      // B's write lands after A's save with an earlier stamp
      ds.withOutputStream(null, AUTHC_FILE, 1L, out -> out.write(contentB));

      a.loadConfiguration();
      assertEquals(List.of("B"), names(a), "the overwritten own save was kept");
   }

   // two threads, two nodes, each add one provider through the cluster-locked edit

   @Test
   void concurrentEditsOnTwoNodesKeepBothAndConverge() throws Exception {
      AuthenticationChain a = node(), b = node(), c = node();
      DataSpace ds = DataSpace.getDataSpace();
      ExecutorService pool = Executors.newFixedThreadPool(2);

      try {
         for(int round = 0; round < 100; round++) {
            tick();
            a.setProviders(named("P"));
            b.loadConfiguration();
            c.loadConfiguration();

            CyclicBarrier barrier = new CyclicBarrier(2);
            Future<?> fa = pool.submit(() -> { add(a, "A", barrier); return null; });
            Future<?> fb = pool.submit(() -> { add(b, "B", barrier); return null; });
            fa.get(30, TimeUnit.SECONDS);
            fb.get(30, TimeUnit.SECONDS);

            // each node processes the events
            for(AuthenticationChain node : List.of(a, b, c)) {
               node.loadConfiguration();
            }

            Set<String> expected = Set.of("P", "A", "B");
            assertEquals(expected, new HashSet<>(names(a)), "node 1, round " + round);
            assertEquals(names(a), names(b), "node 2 diverged, round " + round);
            assertEquals(names(a), names(c), "node 3 diverged, round " + round);
            assertEquals(3, names(a).size(), "round " + round);
         }
      }
      finally {
         pool.shutdownNow();
      }

      assertNotNull(ds.getDigest(null, AUTHC_FILE));
   }

   // the edit takes the cluster lock and reloads the file before it runs

   @Test
   void editStartsFromTheFileNotFromTheRuntimeChain() throws Exception {
      AuthenticationChain a = node(), b = node();
      tick();
      a.setProviders(named("P"));
      b.loadConfiguration();
      tick();
      a.setProviders(named("P", "A"));
      // b has not processed the event of A's save: its runtime chain is stale
      assertEquals(List.of("P"), names(b));

      List<List<String>> seen = new ArrayList<>();
      b.editProviders(() -> {
         List<AuthenticationProvider> list = b.getProviders();
         seen.add(names(list));
         list.add(named("B").get(0));
         b.setProviders(list);
      });

      assertEquals(List.of(List.of("P", "A")), seen, "the edit started from a stale chain");
      assertEquals(List.of("P", "A", "B"), names(b));
      a.loadConfiguration();
      assertEquals(List.of("P", "A", "B"), names(a));
   }

   @Test
   void editWaitsForTheClusterLock() throws Exception {
      AuthenticationChain a = node();
      Lock held = Cluster.getInstance().getLock(AUTHC_LOCK);
      CountDownLatch entered = new CountDownLatch(1);
      held.lock();
      Thread thread = null;

      try {
         thread = new Thread(() -> a.editProviders(entered::countDown), "editor");
         thread.setDaemon(true);
         thread.start();

         long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(20);

         while(thread.getState() != Thread.State.WAITING &&
               thread.getState() != Thread.State.TIMED_WAITING &&
               thread.getState() != Thread.State.BLOCKED && System.nanoTime() < deadline)
         {
            Thread.onSpinWait();
         }

         assertFalse(entered.await(300, TimeUnit.MILLISECONDS),
                     "the edit ran while another node held the cluster lock");
      }
      finally {
         held.unlock();
      }

      assertTrue(entered.await(20, TimeUnit.SECONDS), "the edit never ran");
      thread.join(20000);
      assertFalse(thread.isAlive());
   }

   @Test
   void editReleasesTheLocksWhenItFails() throws Exception {
      AuthenticationChain a = node();

      assertThrows(IllegalStateException.class, () -> a.editProviders(() -> {
         throw new IllegalStateException("rejected");
      }));

      // the cluster lock and the write lock are free again, from another thread
      ExecutorService pool = Executors.newSingleThreadExecutor();

      try {
         Future<Boolean> next = pool.submit(() -> {
            boolean[] ran = new boolean[1];
            a.editProviders(() -> ran[0] = true);
            return ran[0];
         });
         assertTrue(next.get(20, TimeUnit.SECONDS));
      }
      finally {
         pool.shutdownNow();
      }
   }

   // helpers

   private void add(AuthenticationChain chain, String name, CyclicBarrier barrier) throws Exception {
      barrier.await();
      chain.editProviders(() -> {
         List<AuthenticationProvider> list = chain.getProviders();
         list.addAll(named(name));
         chain.setProviders(list);
      });
   }

   private AuthenticationChain node() {
      AuthenticationChain chain = new AuthenticationChain();
      authcChains.add(chain);
      removeListener(AUTHC_FILE, chain);
      return chain;
   }

   private AuthorizationChain authzNode() {
      AuthorizationChain chain = new AuthorizationChain();
      authzChains.add(chain);
      removeListener(AUTHZ_FILE, chain);
      return chain;
   }

   /** The listener would reload on a file event; the tests call loadConfiguration() themselves. */
   private static void removeListener(String file, DataChangeListener listener) {
      DataSpace.getDataSpace().removeChangeListener(null, file, listener);
   }

   private static byte[] readFile(DataSpace ds) throws Exception {
      return readFile(ds, AUTHC_FILE);
   }

   private static byte[] readFile(DataSpace ds, String file) throws Exception {
      try(InputStream in = ds.getInputStream(null, file)) {
         return in.readAllBytes();
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

   private static List<AuthorizationProvider> authz(String... names) {
      List<AuthorizationProvider> list = new ArrayList<>();

      for(String name : names) {
         FileAuthorizationProvider provider = new FileAuthorizationProvider();
         provider.setProviderName(name);
         list.add(provider);
      }

      return list;
   }

   private static List<String> names(AuthenticationChain chain) {
      return names(chain.getProviders());
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
}
