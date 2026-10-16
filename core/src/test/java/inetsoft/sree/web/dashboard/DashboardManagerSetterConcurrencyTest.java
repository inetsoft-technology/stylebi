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
 * filters out names that are not in the global registry. GROUP identities are used so the
 * USER-only registry prune and sync don't take part.
 *
 * Bug #77299. Each cluster node has its own DashboardManager on the same replicated store, so the
 * read-modify-writes above must also exclude a second manager instance, which shares no monitor
 * with the first. They hold the per-org store lock (Cluster.getLock()) for that. The P1 shape and
 * the selection sync of a dashboard-tab load are repeated across two manager instances below. A
 * read that leaves out names the registry does not know yet must not store the shortened list
 * while the registry can't be loaded from its file, and an admin's added global dashboards are
 * stored on top of the stored record, not on top of the filtered list.
 */

import inetsoft.sree.security.IdentityID;
import inetsoft.sree.security.OrganizationContextHolder;
import inetsoft.sree.security.SecurityEngine;
import inetsoft.sree.security.SecurityProvider;
import inetsoft.storage.KeyValueStorage;
import inetsoft.storage.KeyValueStorageManager;
import inetsoft.storage.LoadKeyValueTask;
import inetsoft.test.*;
import inetsoft.uql.util.DefaultIdentity;
import inetsoft.uql.util.Identity;
import inetsoft.util.Tool;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedStatic;
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
import static org.mockito.Mockito.mockStatic;
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
   private final Identity user = new DefaultIdentity("dash77299_user", Identity.USER);

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
         rawStorage.remove(key(user)).get(10L, TimeUnit.SECONDS);
      }

      OrganizationContextHolder.clear();
   }

   // ── Bug #78101: a user rename moves the stored names as they are stored ──

   @Test
   void getStoredDashboards_returnsTheStoredNamesUnfiltered() throws Exception {
      // names that are in no registry, which getDashboards() leaves out
      seed(user, List.of("B", "A", "C__GLOBAL"), List.of("D__GLOBAL"));

      assertArrayEquals(new String[] { "B", "A", "C__GLOBAL" },
                        manager.getStoredDashboards(user));
      assertArrayEquals(new String[0],
                        manager.getStoredDashboards(new DefaultIdentity("none", Identity.USER)));
      assertArrayEquals(new String[0], manager.getStoredDashboards(null));
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

   // ── Bug #77299: a rename on one node keeps a setter's write on another node ──

   /*
    * P1 with the setter and the rename on two manager instances on the same store, the way two
    * cluster nodes run them. The instances share no monitor, so only the store lock keeps the
    * rename from reading the record before the setter's put lands.
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

      awaitLatchOrWaitingOn(renameRead, node1, node2);

      park.release();
      assertCompletes(node2);
      await(renameRead);
      renameGo.countDown();
      assertCompletes(node1);

      DashboardManager.DashboardData data = rawStorage.get(key(group));
      assertEquals(List.of("X", "B", "Y"), data.getDashboards(),
                   "the rename on one node must apply to the selection another node set");
   }

   // ── Bug #77299: a name the registry does not know yet is not removed on read ──

   @Test
   void getDeselectedDashboards_staleRegistryMiss_doesNotPersistThePrune() throws Exception {
      DashboardManager.DashboardData data = new DashboardManager.DashboardData();
      data.setDashboards(new ArrayList<>());
      data.setDeselected(new ArrayList<>(List.of("Z")));
      rawStorage.put(key(group), data).get(10L, TimeUnit.SECONDS);

      DashboardRegistryManager registryManager = mock(DashboardRegistryManager.class);
      DashboardRegistry registry = mock(DashboardRegistry.class);
      // "Z" is not in this node's cached registry yet, e.g. before the notification of a remote
      // rename has reloaded it, and the registry can't be loaded from its file
      when(registry.getDashboard("Z")).thenReturn(null);
      when(registryManager.getRegistry()).thenReturn(registry);

      DashboardManager manager3 = newManager(registryManager);

      String[] deselected = manager3.getDeselectedDashboards(group);

      assertEquals(0, deselected.length,
                   "a name that is not in the registry is not listed");

      DashboardManager.DashboardData stored = rawStorage.get(key(group));
      assertEquals(List.of("Z"), stored.getDeselected(),
                   "a name that is not known to be gone must not be removed from the record");
   }

   // ── Bug #77299: an admin's added global dashboard does not drop an unknown name ──

   /*
    * The same unknown "Z" for an org admin, whose deselected list also gets every global dashboard
    * the admin has neither selected nor deselected ("NewGlobal"). Adding it stores the record, and
    * that must be the stored names plus "NewGlobal", not the listed names, which leave out "Z".
    * isOrgAdmin() resolves the provider through the static SecurityEngine.getSecurity(), hence the
    * static mock.
    */
   @Test
   void getDeselectedDashboards_orgAdminStaleRegistryMissWithNewGlobalDashboard_doesNotPersistThePrune()
      throws Exception
   {
      DashboardRegistryManager registryManager = mock(DashboardRegistryManager.class);
      DashboardRegistry registry = mock(DashboardRegistry.class);
      when(registry.getDashboard("Z")).thenReturn(null);
      when(registry.getDashboardNames()).thenReturn(new String[] { "NewGlobal" });
      when(registryManager.getRegistry()).thenReturn(registry);

      // the first use of the manager syncs every stored record, so the record is seeded after it
      DashboardManager manager3 = newManager(registryManager);
      seed(user, List.of(), List.of("Z"));

      IdentityID userId = user.getIdentityID();
      SecurityProvider provider = mock(SecurityProvider.class);
      IdentityID adminRole = new IdentityID("Organization Administrator", user.getOrganizationID());
      when(provider.getRoles(userId)).thenReturn(new IdentityID[] { adminRole });
      when(provider.getAllRoles(any(IdentityID[].class)))
         .thenReturn(new IdentityID[] { adminRole });
      when(provider.isOrgAdministratorRole(adminRole)).thenReturn(true);

      SecurityEngine orgManagerSecurityEngine = mock(SecurityEngine.class);
      when(orgManagerSecurityEngine.getSecurityProvider()).thenReturn(provider);

      String[] deselected;

      try(MockedStatic<SecurityEngine> securityEngineMock = mockStatic(SecurityEngine.class)) {
         securityEngineMock.when(SecurityEngine::getSecurity).thenReturn(orgManagerSecurityEngine);
         deselected = manager3.getDeselectedDashboards(user);
      }

      assertEquals(List.of("NewGlobal"), Arrays.asList(deselected),
                   "the unknown name is left out and the global dashboard is added");

      DashboardManager.DashboardData stored = rawStorage.get(key(user));
      assertEquals(List.of("Z", "NewGlobal"), stored.getDeselected(),
                   "only the added global dashboard may be stored on top of the record");
   }

   // ── Bug #77299: the selection sync of a tab load on one node keeps a setter's write ──

   /*
    * A dashboard-tab load calls getDashboards(identity, true), which syncs the user's record with
    * syncUserDashboards(identity), a read-modify-write of its own. Here the sync removes a global
    * dashboard that is gone from the loaded global registry file. Run on a second node while a
    * setter is between its get() and put(), it must wait for the setter, so that both the new
    * selection and the removal are kept. syncUserDashboards only runs for USER identities.
    */
   @Test
   void getDashboards_syncRacingSetDashboardsFromAnotherManagerInstance_keepsBothChanges()
      throws Exception
   {
      SecurityEngine securityEngine = mock(SecurityEngine.class);
      when(securityEngine.getSecurityProvider()).thenReturn(mock(SecurityProvider.class));
      DashboardRegistryManager registryManager = mock(DashboardRegistryManager.class);
      DashboardRegistry registry = mock(DashboardRegistry.class);
      when(registry.getDashboard(anyString())).thenReturn(mock(Dashboard.class));
      when(registry.getDashboard("gone__GLOBAL")).thenReturn(null);
      when(registry.syncWithFile()).thenReturn(true);
      when(registry.isFileLoaded()).thenReturn(true);
      when(registryManager.getRegistry()).thenReturn(registry);

      DashboardManager managerA = newManager(securityEngine, registryManager);
      DashboardManager managerB = newManager(securityEngine, registryManager);
      seed(user, List.of("A", "X", "gone__GLOBAL"), List.of());

      park = new Park("node2-set", Step.BEFORE_PUT);
      Thread node2 = start("node2-set", () -> managerB.setDashboards(
         user, new String[] { "X", "A", "Y", "gone__GLOBAL" }));
      park.awaitParked();

      CountDownLatch syncDone = new CountDownLatch(1);
      Thread node1 = start("node1-sync", () -> {
         managerA.getDashboards(user, true);
         syncDone.countDown();
      });

      awaitLatchOrWaitingOn(syncDone, node1, node2);

      park.release();
      assertCompletes(node2);
      assertCompletes(node1);

      DashboardManager.DashboardData data = rawStorage.get(key(user));
      assertEquals(List.of("X", "A", "Y"), data.getDashboards(),
                   "the sync of a tab load on one node and the selection another node set must " +
                   "both be kept");
   }

   /**
    * A DashboardManager instance on the same store as {@link #manager}, like the manager of
    * another cluster node.
    */
   private DashboardManager newManagerSharingStorage() {
      return newManager(mock(DashboardRegistryManager.class));
   }

   /**
    * A DashboardManager instance on the same store as {@link #manager}, with the given registry
    * manager.
    */
   private DashboardManager newManager(DashboardRegistryManager registryManager) {
      return newManager(mock(SecurityEngine.class), registryManager);
   }

   /**
    * A DashboardManager instance on the same store as {@link #manager}, with the given security
    * engine and registry manager.
    */
   private DashboardManager newManager(SecurityEngine securityEngine,
                                       DashboardRegistryManager registryManager)
   {
      KeyValueStorageManager storages = mock(KeyValueStorageManager.class);
      when(storages.getStorage(anyString(), any(LoadKeyValueTask.class))).thenAnswer(inv -> {
         KeyValueStorage<DashboardManager.DashboardData> real =
            keyValueStorageManager.getStorage(inv.getArgument(0), inv.getArgument(1));
         rawStorage = real;
         return copyOnRead(real);
      });

      DashboardManager m = new DashboardManager(securityEngine, registryManager, storages);
      // first use switches the manager to the current org, outside the interleavings
      m.getDashboards(group);
      return m;
   }

   // ── fixture ──

   private void seed(List<String> selected, List<String> deselected) throws Exception {
      seed(group, selected, deselected);
   }

   private void seed(Identity identity, List<String> selected, List<String> deselected)
      throws Exception
   {
      DashboardManager.DashboardData data = new DashboardManager.DashboardData();
      data.setDashboards(new ArrayList<>(selected));
      data.setDeselected(new ArrayList<>(deselected));
      rawStorage.put(key(identity), data).get(10L, TimeUnit.SECONDS);
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
    * Waits until a thread has either passed its step (the latch is counted down) or waits on a
    * lock owned by another thread, the store lock being a java.util.concurrent lock and not a
    * monitor. Without the store lock the thread passes its step; with it it waits for the owner.
    */
   private static void awaitLatchOrWaitingOn(CountDownLatch latch, Thread thread, Thread owner)
      throws InterruptedException
   {
      ThreadMXBean bean = ManagementFactory.getThreadMXBean();
      long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(TIMEOUT_SECONDS);

      while(latch.getCount() > 0) {
         ThreadInfo info = bean.getThreadInfo(thread.getId());

         if(info != null && info.getLockOwnerId() == owner.getId()) {
            return;
         }

         assertTrue(thread.isAlive(), thread.getName() + " ended before its step");
         assertTrue(System.nanoTime() < deadline,
                    thread.getName() + " neither passed its step nor waited on " +
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
