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
package inetsoft.sree.web.dashboard;

/*
 * Bug #77232. The DashboardManager setters read the whole DashboardData record, change one field
 * and put the record back. Unless that read-modify-write holds the manager's monitor, it loses a
 * concurrent write to the same record:
 *
 * - P1: a setDashboards() racing a locked rename RMW (UserDashboardRegistry.renameDashboard, which
 *   does getDashboards() -> setDashboards() under runLocked()) is overwritten by the rename's list
 *   that was read before the setter's put landed.
 * - P2: a setDashboards() writes back the stale deselected list it read, over a concurrent
 *   setDeselectedDashboards().
 *
 * In production a KeyValueStorage get() returns a fresh copy (the Ignite replicated cache
 * deserializes on every read), but MockCluster returns the stored instance, and the in-place
 * mutation in the setters would then hide the lost update. The dashboards store is therefore
 * wrapped in a proxy whose get() returns a serialized copy. The proxy also parks a named thread
 * after its get() or before its put(), and a thread is only let go once the other thread is seen
 * either past its step or blocked on the parked thread (ThreadMXBean), so no step depends on
 * timing. The stored records are read back from the raw store, because getDeselectedDashboards()
 * still filters out names that are not in the global registry. GROUP identities are used so the
 * USER-only registry prune and sync don't take part.
 *
 * Bug #77299 extends the above to cluster scope: the KeyValueStorage is actually replicated
 * cluster-wide (confirmed by tracing LocalKeyValueStorage -> the singleton PutKeyValueTask
 * executor), so P1/P2/P2' above, which the #77232 fix solved with a plain per-JVM `synchronized`,
 * still lose an update when the two racing calls are made on two *different* DashboardManager
 * instances (simulating two cluster nodes), since they share no JVM monitor at all. See
 * renameDashboard_racingSetDashboardsFromAnotherManagerInstance_keepsBothChanges() below, which
 * repeats P1's shape across two manager instances sharing one real KeyValueStorage, to prove the
 * new cluster-wide lock (getDashboardsLockName()/Cluster.lockKey()) closes that gap.
 *
 * Bug #77299 also identified a second, independent mechanism: getDashboards()/
 * getDeselectedDashboards() used to persist the filtered (shortened) list whenever a name was not
 * recognized by the local registry cache, but that cache is only *eventually* consistent with a
 * remote rename/delete, so a read landing mid-reload could permanently discard a still-valid name.
 * The fix stops persisting that filtered result; see
 * getDeselectedDashboards_staleRegistryMiss_doesNotPersistThePrune() below.
 */

import inetsoft.sree.security.OrganizationContextHolder;
import inetsoft.sree.security.SecurityEngine;
import inetsoft.storage.KeyValueStorage;
import inetsoft.storage.KeyValueStorageManager;
import inetsoft.storage.LoadKeyValueTask;
import inetsoft.test.*;
import inetsoft.uql.util.DefaultIdentity;
import inetsoft.uql.util.Identity;
import inetsoft.util.Tool;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.io.*;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class DashboardManagerSetterConcurrencyTest {
   @Autowired
   private KeyValueStorageManager keyValueStorageManager;

   private DashboardManager manager;
   private volatile KeyValueStorage<DashboardManager.DashboardData> rawStorage;
   private volatile Park park;
   private final List<Thread> threads = new ArrayList<>();
   private final Identity group = new DefaultIdentity("dash77232_group", Identity.GROUP);

   @BeforeEach
   void setUp() {
      OrganizationContextHolder.clear();
      KeyValueStorageManager storages = mock(KeyValueStorageManager.class);
      when(storages.getStorage(anyString(), any(LoadKeyValueTask.class))).thenAnswer(inv -> {
         KeyValueStorage<DashboardManager.DashboardData> real =
            keyValueStorageManager.getStorage(inv.getArgument(0), inv.getArgument(1));
         rawStorage = real;
         return copyOnRead(real);
      });

      manager = new DashboardManager(mock(SecurityEngine.class),
                                     mock(DashboardRegistryManager.class), storages);
      // first use switches the manager to the current org, outside the interleavings
      manager.getDashboards(group);
      assertNotNull(rawStorage);
   }

   @AfterEach
   void tearDown() throws Exception {
      Park current = park;
      park = null;

      if(current != null) {
         current.release();
      }

      threads.forEach(Thread::interrupt);

      for(Thread thread : threads) {
         thread.join(TimeUnit.SECONDS.toMillis(TIMEOUT_SECONDS));
      }

      threads.clear();

      if(rawStorage != null) {
         rawStorage.remove(key(group)).get(10L, TimeUnit.SECONDS);
      }

      OrganizationContextHolder.clear();
   }

   // ── P1: a setter racing a locked rename RMW keeps its write ──

   @Test
   void setDashboards_racingLockedRename_keepsBothTheRenameAndTheNewSelection() throws Exception {
      seed(List.of("A", "X"), List.of());
      park = new Park("arrange", Step.BEFORE_PUT);

      Thread arrange = start("arrange",
                             () -> manager.setDashboards(group, new String[] { "X", "A", "Y" }));
      park.awaitParked();

      // models UserDashboardRegistry.renameDashboard (DashboardRegistry.java runLocked block)
      CountDownLatch renameRead = new CountDownLatch(1);
      CountDownLatch renameGo = new CountDownLatch(1);
      Thread renamer = start("renamer", () -> manager.runLocked(() -> {
         String[] dashboards = manager.getDashboards(group);
         renameRead.countDown();
         await(renameGo);
         manager.setDashboards(group, Tool.replace(dashboards, "A", "B"));
      }));
      awaitLatchOrBlockedBy(renameRead, renamer, arrange);

      park.release();
      assertCompletes(arrange);
      renameGo.countDown();
      assertCompletes(renamer);

      DashboardManager.DashboardData data = rawStorage.get(key(group));
      assertEquals(List.of("X", "B", "Y"), data.getDashboards(),
                   "the rename must apply to the new selection, not overwrite it");
   }

   // ── P2: setDashboards does not write back a stale deselected list ──

   @Test
   void setDashboards_racingSetDeselected_keepsTheDeselectedDashboards() throws Exception {
      seed(List.of("A", "X"), List.of());
      park = new Park("selector", Step.AFTER_GET);

      Thread selector = start("selector",
                              () -> manager.setDashboards(group, new String[] { "B", "X" }));
      park.awaitParked();

      CountDownLatch deselectDone = new CountDownLatch(1);
      Thread deselector = start("deselector", () -> {
         manager.setDeselectedDashboards(group, new String[] { "Z" });
         deselectDone.countDown();
      });
      awaitLatchOrBlockedBy(deselectDone, deselector, selector);

      park.release();
      assertCompletes(selector, deselector);

      DashboardManager.DashboardData data = rawStorage.get(key(group));
      assertEquals(List.of("B", "X"), data.getDashboards());
      assertEquals(List.of("Z"), data.getDeselected(),
                   "setDashboards must not restore the deselected list it read before");
   }

   // ── P2': the same, with the roles swapped ──

   @Test
   void setDeselected_racingSetDashboards_keepsTheSelectedDashboards() throws Exception {
      seed(List.of("A", "X"), List.of());
      park = new Park("deselector", Step.AFTER_GET);

      Thread deselector = start("deselector",
                                () -> manager.setDeselectedDashboards(group, new String[] { "Z" }));
      park.awaitParked();

      CountDownLatch selectDone = new CountDownLatch(1);
      Thread selector = start("selector", () -> {
         manager.setDashboards(group, new String[] { "B", "X" });
         selectDone.countDown();
      });
      awaitLatchOrBlockedBy(selectDone, selector, deselector);

      park.release();
      assertCompletes(selector, deselector);

      DashboardManager.DashboardData data = rawStorage.get(key(group));
      assertEquals(List.of("B", "X"), data.getDashboards(),
                   "setDeselectedDashboards must not restore the selection it read before");
      assertEquals(List.of("Z"), data.getDeselected());
   }

   // ── Bug #77299: cluster-wide atomicity across two DashboardManager instances ──

   /*
    * Same shape as P1 above, but "arrange" and "renamer" now run on two SEPARATE DashboardManager
    * instances sharing one real KeyValueStorage, simulating two cluster nodes. The two instances
    * have no JVM monitor in common (different `synchronized` locks), so this exercises only the
    * cluster-wide lock added for #77299 (getDashboardsLockName()/Cluster.lockKey()), not the
    * #77232 per-JVM synchronized fix that P1 already covers.
    */
   @Test
   void renameDashboard_racingSetDashboardsFromAnotherManagerInstance_keepsBothChanges()
      throws Exception
   {
      seed(List.of("A", "X"), List.of());
      DashboardManager manager2 = newManagerSharingStorage();

      park = new Park("node2-set", Step.BEFORE_PUT);
      Thread node2 = start("node2-set",
                           () -> manager2.setDashboards(group, new String[] { "X", "A", "Y" }));
      park.awaitParked();

      CountDownLatch renameRead = new CountDownLatch(1);
      CountDownLatch renameGo = new CountDownLatch(1);
      Thread node1 = start("node1-rename", () -> manager.runLocked(() -> {
         String[] dashboards = manager.getDashboards(group);
         renameRead.countDown();
         await(renameGo);
         manager.setDashboards(group, Tool.replace(dashboards, "A", "B"));
      }));

      // node1's runLocked() must be excluded by the same cluster-wide lock node2 is holding:
      // without it, these are two independent DashboardManager instances with no JVM monitor in
      // common, so node1's read could freely interleave with node2's still-pending write.
      assertFalse(renameRead.await(300, TimeUnit.MILLISECONDS),
                  "node1 must be blocked by the cluster-wide lock while node2 holds it");
      assertTrue(node1.isAlive(), "node1 ended unexpectedly instead of blocking");

      park.release();
      assertCompletes(node2);
      await(renameRead);
      renameGo.countDown();
      assertCompletes(node1);

      DashboardManager.DashboardData data = rawStorage.get(key(group));
      assertEquals(List.of("X", "B", "Y"), data.getDashboards(),
                   "cluster-wide lock: a rename on one manager instance must observe and " +
                   "preserve a concurrent write already committed by another instance, " +
                   "simulating another cluster node");
   }

   // ── Bug #77299: a stale registry miss on read must not be persisted as a removal ──

   @Test
   void getDeselectedDashboards_staleRegistryMiss_doesNotPersistThePrune() throws Exception {
      DashboardManager.DashboardData data = new DashboardManager.DashboardData();
      data.setDashboards(new ArrayList<>());
      data.setDeselected(new ArrayList<>(List.of("Z")));
      rawStorage.put(key(group), data).get(10L, TimeUnit.SECONDS);

      DashboardRegistryManager registryManager = mock(DashboardRegistryManager.class);
      DashboardRegistry registry = mock(DashboardRegistry.class);
      // "Z" is not (yet) recognized by this node's registry cache, e.g. mid-reload of a remote
      // rename/delete (the registry's reload is deliberately asynchronous), even though it was
      // validly deselected and no mutation ever confirmed it deleted.
      when(registry.getDashboard("Z")).thenReturn(null);
      when(registryManager.getRegistry()).thenReturn(registry);

      DashboardManager manager3 = newManager(registryManager);

      String[] deselected = manager3.getDeselectedDashboards(group);

      assertEquals(0, deselected.length,
                   "an unrecognized name is still filtered out of what this call returns");

      DashboardManager.DashboardData stored = rawStorage.get(key(group));
      assertEquals(List.of("Z"), stored.getDeselected(),
                   "a registry cache that does not yet recognize a name must not have its " +
                   "absence persisted as a removal: the cache may simply be mid-reload of a " +
                   "concurrent remote rename/delete, not a confirmed deletion");
   }

   /**
    * A second DashboardManager instance sharing the same underlying KeyValueStorage as
    * {@link #manager}, and using its own unstubbed DashboardRegistryManager mock, simulating
    * another cluster node.
    */
   private DashboardManager newManagerSharingStorage() {
      return newManager(mock(DashboardRegistryManager.class));
   }

   /**
    * A DashboardManager instance sharing the same underlying KeyValueStorage as {@link #manager},
    * using the given DashboardRegistryManager.
    */
   private DashboardManager newManager(DashboardRegistryManager registryManager) {
      KeyValueStorageManager storages = mock(KeyValueStorageManager.class);
      when(storages.getStorage(anyString(), any(LoadKeyValueTask.class))).thenAnswer(inv -> {
         KeyValueStorage<DashboardManager.DashboardData> real =
            keyValueStorageManager.getStorage(inv.getArgument(0), inv.getArgument(1));
         rawStorage = real;
         return copyOnRead(real);
      });

      DashboardManager m = new DashboardManager(mock(SecurityEngine.class), registryManager, storages);
      // first use switches the manager to the current org, outside the interleavings, as setUp()
      // already does for `manager`
      m.getDashboards(group);
      return m;
   }

   // ── fixture ──

   private void seed(List<String> selected, List<String> deselected) throws Exception {
      DashboardManager.DashboardData data = new DashboardManager.DashboardData();
      data.setDashboards(new ArrayList<>(selected));
      data.setDeselected(new ArrayList<>(deselected));
      rawStorage.put(key(group), data).get(10L, TimeUnit.SECONDS);
   }

   private static String key(Identity identity) {
      return identity.getType() + ":" + identity.getName();
   }

   /**
    * Wraps the store so that get() returns a serialized copy, as the Ignite replicated cache does,
    * and parks the thread named by {@link #park} after its get() or before its put().
    */
   @SuppressWarnings("unchecked")
   private KeyValueStorage<DashboardManager.DashboardData> copyOnRead(
      KeyValueStorage<DashboardManager.DashboardData> real)
   {
      return (KeyValueStorage<DashboardManager.DashboardData>) Proxy.newProxyInstance(
         getClass().getClassLoader(), new Class<?>[] { KeyValueStorage.class },
         (proxy, method, args) -> {
            Park current = park;
            boolean parked = current != null &&
               current.thread.equals(Thread.currentThread().getName());

            if(parked && current.step == Step.BEFORE_PUT && "put".equals(method.getName())) {
               current.parkHere();
            }

            Object result;

            try {
               result = method.invoke(real, args);
            }
            catch(InvocationTargetException e) {
               throw e.getCause();
            }

            if("get".equals(method.getName()) && result instanceof Serializable) {
               result = serialCopy((Serializable) result);

               if(parked && current.step == Step.AFTER_GET) {
                  current.parkHere();
               }
            }

            return result;
         });
   }

   private static Object serialCopy(Serializable value) throws Exception {
      ByteArrayOutputStream bytes = new ByteArrayOutputStream();

      try(ObjectOutputStream out = new ObjectOutputStream(bytes)) {
         out.writeObject(value);
      }

      try(ObjectInputStream in =
             new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray())))
      {
         return in.readObject();
      }
   }

   private enum Step { AFTER_GET, BEFORE_PUT }

   /**
    * Parks the named thread once, at the given step of its storage access.
    */
   private static final class Park {
      Park(String thread, Step step) {
         this.thread = thread;
         this.step = step;
      }

      void parkHere() {
         if(parked.getCount() == 0) {
            return;
         }

         parked.countDown();
         await(release);
      }

      void awaitParked() {
         await(parked);
      }

      void release() {
         release.countDown();
      }

      final String thread;
      final Step step;
      final CountDownLatch parked = new CountDownLatch(1);
      final CountDownLatch release = new CountDownLatch(1);
   }

   // ── thread helpers ──

   private Thread start(String name, Runnable action) {
      Thread thread = new Thread(action, name);
      thread.setDaemon(true);
      threads.add(thread);
      thread.start();
      return thread;
   }

   private static void await(CountDownLatch latch) {
      try {
         if(!latch.await(TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
            throw new AssertionError("timed out waiting for a latch");
         }
      }
      catch(InterruptedException e) {
         Thread.currentThread().interrupt();
         throw new AssertionError(e);
      }
   }

   /**
    * Waits until a thread has either passed its step (the latch is counted down) or is blocked on
    * a lock owned by another thread. Without the fix the thread passes its step; with the fix it
    * waits for the owner's setter to finish.
    */
   private static void awaitLatchOrBlockedBy(CountDownLatch latch, Thread thread, Thread owner)
      throws InterruptedException
   {
      ThreadMXBean bean = ManagementFactory.getThreadMXBean();
      long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(TIMEOUT_SECONDS);

      while(latch.getCount() > 0) {
         ThreadInfo info = bean.getThreadInfo(thread.getId());

         if(info != null && info.getLockOwnerId() == owner.getId() &&
            info.getThreadState() == Thread.State.BLOCKED)
         {
            return;
         }

         assertTrue(thread.isAlive(), thread.getName() + " ended before its step");
         assertTrue(System.nanoTime() < deadline,
                    thread.getName() + " neither passed its step nor blocked on " +
                    owner.getName());
         latch.await(10, TimeUnit.MILLISECONDS);
      }
   }

   /**
    * Waits for the threads to end, failing at once on a deadlock, and at the latest after the
    * timeout.
    */
   private static void assertCompletes(Thread... threads) throws InterruptedException {
      ThreadMXBean bean = ManagementFactory.getThreadMXBean();
      long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(TIMEOUT_SECONDS);
      Set<Long> ids = new HashSet<>();
      Arrays.stream(threads).forEach(t -> ids.add(t.getId()));

      for(Thread thread : threads) {
         while(thread.isAlive()) {
            long[] deadlocked = bean.findDeadlockedThreads();

            if(deadlocked != null && Arrays.stream(deadlocked).anyMatch(ids::contains)) {
               StringBuilder message = new StringBuilder("deadlock:");

               for(ThreadInfo info : bean.getThreadInfo(deadlocked, true, true)) {
                  message.append('\n').append(info);
               }

               fail(message.toString());
            }

            assertTrue(System.nanoTime() < deadline, "threads did not end in time");
            thread.join(10);
         }
      }
   }

   private static final int TIMEOUT_SECONDS = 30;
}
