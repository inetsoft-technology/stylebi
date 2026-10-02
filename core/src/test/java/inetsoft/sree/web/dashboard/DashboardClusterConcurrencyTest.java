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

import inetsoft.sree.ViewsheetEntry;
import inetsoft.sree.security.*;
import inetsoft.sree.security.support.SecurityTestDataBuilder;
import inetsoft.storage.*;
import inetsoft.test.*;
import inetsoft.uql.asset.AssetEntry;
import inetsoft.uql.asset.AssetRepository;
import inetsoft.uql.util.DefaultIdentity;
import inetsoft.uql.util.Identity;
import inetsoft.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.io.*;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;
import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Bug #77272: two cluster nodes that create a dashboard for the same user at the same time lose
 * one of them on every node.
 *
 * <p>Each node caches the user's registry file, and a create added the dashboard to the node's
 * cached map and then wrote the whole map over the file. A second node, whose cached copy did
 * not have the first node's dashboard yet, wrote it away (ordering a), and the first node then
 * reloaded the winner's file. A notification of a remote save that arrived between the add and
 * the write replaced the cached map and dropped the unsaved dashboard (ordering b). The
 * DashboardManager then pruned the selections against the node's (possibly stale) cached
 * registry and stored the pruned list, and the per-user selection record was read, changed and
 * put back with a node-local monitor only.
 *
 * <p>A second node's registry is a second registry instance on the same file, and a second
 * node's DashboardManager a second manager on the same store, the way the diagnosis and
 * refutation modelled them. A node's cached copy is kept stale by holding its monitor, which
 * holds off the change notification that would reload it.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class,
                                  DashboardClusterConcurrencyTest.Config.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class DashboardClusterConcurrencyTest {
   @Autowired
   private DashboardRegistryManager dashboardRegistryManager;

   @Autowired
   private DataSpace dataSpace;

   @Autowired
   private ApplicationEventPublisher eventPublisher;

   @Autowired
   private SecurityEngine securityEngine;

   @Autowired
   private KeyValueStorageManager keyValueStorageManager;

   private SecurityTestDataBuilder builder;
   private IdentityID user;
   private DashboardRegistry node1;
   private DashboardRegistry node2;
   private final List<Thread> threads = new ArrayList<>();
   private final List<Runnable> cleanups = new ArrayList<>();

   @BeforeEach
   void setUp() throws Exception {
      String orgId = "dash77272";
      builder = SecurityTestDataBuilder.create()
         .addOrg("Dash77272", orgId)
         .addUser("erin", orgId, "password")
         .setup();
      user = new IdentityID("erin", orgId);
      node1 = dashboardRegistryManager.getRegistry(user);
      node1.putDashboard("seed", newVsDashboard());
      node2 = secondNode();
      assertEquals(Set.of("seed"), names(node2), "precondition: node2 loaded the file");
   }

   @AfterEach
   void tearDown() throws Exception {
      threads.forEach(Thread::interrupt);

      for(Thread thread : threads) {
         thread.join(TimeUnit.SECONDS.toMillis(TIMEOUT_SECONDS));
      }

      threads.clear();
      cleanups.forEach(Runnable::run);
      cleanups.clear();

      if(node2 != null) {
         node2.clear();
      }

      if(node1 != null) {
         dataSpace.delete(null, node1.getPath());
         dashboardRegistryManager.clear(user);
      }

      if(builder != null) {
         builder.teardown();
         builder = null;
      }
   }

   // ── ordering (a): both nodes create from a stale cached copy ──

   @Test
   void concurrentCreates_onTwoNodes_keepBothDashboards() throws Exception {
      // no notification reaches node2 between the two creates, so its cached copy has neither
      synchronized(node2) {
         node1.putDashboard("x1", newVsDashboard());
         node2.putDashboard("y1", newVsDashboard());
      }

      deliverNotifications();

      assertEquals(Set.of("seed", "x1", "y1"), fileNames(), "the file lost a dashboard");
      assertEquals(Set.of("seed", "x1", "y1"), names(node1), "the first creator lost a dashboard");
      assertEquals(Set.of("seed", "x1", "y1"), names(node2), "the second creator lost a dashboard");
   }

   // ── ordering (b): a remote save's notification arrives while a create is in progress ──

   @Test
   void remoteNotification_duringCreate_doesNotDropTheCreatedDashboard() throws Exception {
      BlockingDashboard y1 = new BlockingDashboard();

      synchronized(node2) {
         node1.putDashboard("x1", newVsDashboard());
         // node1's notification is delivered to node2 while node2 is between applying its
         // create and writing it
         y1.onWrite = () -> {
            Thread thread = start("listener", () -> fireListener(node2));
            awaitBlockedOrDone(thread);
            return thread;
         };
      }

      node2.putDashboard("y1", y1);

      // null if the dashboard was dropped before it was written
      if(y1.listener != null) {
         assertCompletes(y1.listener);
      }

      assertNotNull(node2.getDashboard("y1"), "the notification dropped the created dashboard");
      deliverNotifications();

      assertEquals(Set.of("seed", "x1", "y1"), fileNames(), "the file lost a dashboard");
      assertEquals(Set.of("seed", "x1", "y1"), names(node1));
      assertEquals(Set.of("seed", "x1", "y1"), names(node2));
   }

   // ── a rename or a removal on a node with a stale cached copy keeps the other node's create ──

   @Test
   void renameAndRemove_onStaleNode_keepTheOtherNodesCreate() throws Exception {
      node1.putDashboard("old", newVsDashboard());
      fireListener(node2);
      assertEquals(Set.of("seed", "old"), names(node2), "precondition: node2 loaded old");

      synchronized(node2) {
         // node2 renames and removes without having loaded node1's create
         node1.putDashboard("x1", newVsDashboard());
         node2.renameDashboard("old", "new");
         node2.removeDashboard("seed");
      }

      deliverNotifications();

      assertEquals(Set.of("new", "x1"), fileNames(), "the file lost a dashboard");
      assertEquals(Set.of("new", "x1"), names(node1));
      assertEquals(Set.of("new", "x1"), names(node2));
   }

   // ── the selections are not pruned against a stale cached registry ──

   @Test
   void addDashboard_withStaleRegistry_keepsTheOtherNodesSelection() throws Exception {
      DashboardManager manager = newManager(keyValueStorageManager);
      Identity identity = new DefaultIdentity(user, Identity.USER);
      KeyValueStorage<DashboardManager.DashboardData> store = store(identity);

      synchronized(node1) {
         // node1 creates x, then node2 creates and selects y, which node1 has not loaded yet
         node1.putDashboard("x", newVsDashboard());
         node2.putDashboard("y", newVsDashboard());
         manager.addDashboard(identity, "y");
         assertNull(node1.getDashboard("y"), "precondition: node1's cached copy is stale");

         // node1 selects x
         manager.addDashboard(identity, "x");
      }

      assertEquals(List.of("y", "x"), store.get(key(identity)).getDashboards(),
                   "the selection of the dashboard node1 did not know yet must be kept");
   }

   @Test
   void getDashboards_withStaleRegistry_doesNotRemoveTheSelection() throws Exception {
      DashboardManager manager = newManager(keyValueStorageManager);
      Identity identity = new DefaultIdentity(user, Identity.USER);
      KeyValueStorage<DashboardManager.DashboardData> store = store(identity);

      synchronized(node1) {
         node2.putDashboard("y", newVsDashboard());
         manager.addDashboard(identity, "y");

         // the tab model on node1, before node1 has loaded y
         assertFalse(Arrays.asList(manager.getDashboards(identity)).contains("y"),
                     "precondition: node1's cached copy is stale");
      }

      assertTrue(store.get(key(identity)).getDashboards().contains("y"),
                 "a stale cached registry must not remove the stored selection");

      fireListener(node1);
      assertTrue(Arrays.asList(manager.getDashboards(identity)).contains("y"),
                 "the dashboard is listed once node1 has loaded it");
   }

   // ── the selection record read-modify-write is atomic across nodes ──

   @Test
   void addDashboard_onTwoNodes_keepsBothSelections() throws Exception {
      Identity group = new DefaultIdentity("dash77272_group", Identity.GROUP);
      Park park = new Park("n1");
      KeyValueStorageManager storages = mock(KeyValueStorageManager.class);
      when(storages.getStorage(anyString(), any(LoadKeyValueTask.class))).thenAnswer(inv -> {
         KeyValueStorage<DashboardManager.DashboardData> real =
            keyValueStorageManager.getStorage(inv.getArgument(0), inv.getArgument(1));
         return parkAfterGet(real, park);
      });
      DashboardManager manager1 = newManager(storages);
      DashboardManager manager2 = newManager(storages);
      KeyValueStorage<DashboardManager.DashboardData> store = store(group);
      DashboardManager.DashboardData data = new DashboardManager.DashboardData();
      data.setDashboards(new ArrayList<>(List.of("a")));
      store.put(key(group), data).get(10L, TimeUnit.SECONDS);
      // first use switches the managers to the current org, outside the interleaving
      manager1.getDashboards(group);
      manager2.getDashboards(group);
      park.armed = true;

      Thread n1 = start("n1", () -> manager1.addDashboard(group, "x"));
      park.awaitParked();

      CountDownLatch n2Done = new CountDownLatch(1);
      Thread n2 = start("n2", () -> {
         manager2.addDashboard(group, "y");
         n2Done.countDown();
      });
      awaitLatchOrWaitingOn(n2Done, n2, n1);

      park.release();
      assertCompletes(n1, n2);

      List<String> stored = store.get(key(group)).getDashboards();
      assertEquals(Set.of("a", "x", "y"), new HashSet<>(stored),
                   "a concurrent selection on another node was lost");
   }

   // ── fixture ──

   private DashboardRegistry secondNode() {
      DashboardRegistry registry = new DashboardRegistry.UserDashboardRegistry(
         user, user.orgID, eventPublisher, securityEngine);
      registry.loadDashboard(null);
      return registry;
   }

   private DashboardManager newManager(KeyValueStorageManager storages) {
      return new DashboardManager(securityEngine, dashboardRegistryManager, storages);
   }

   private KeyValueStorage<DashboardManager.DashboardData> store(Identity identity) {
      String storeID = OrganizationManager.getInstance().getCurrentOrgID().toLowerCase() +
         "__dashboards";
      KeyValueStorage<DashboardManager.DashboardData> store =
         keyValueStorageManager.getStorage(storeID);
      cleanups.add(() -> {
         try {
            store.remove(key(identity)).get(10L, TimeUnit.SECONDS);
         }
         catch(Exception e) {
            throw new RuntimeException(e);
         }
      });
      return store;
   }

   private static String key(Identity identity) {
      return identity.getType() + ":" + identity.getName();
   }

   /**
    * Lets the natural (asynchronous) notifications arrive, then delivers a late one to both
    * nodes, the way the BlobStorageEvent thread does.
    */
   private void deliverNotifications() throws InterruptedException {
      Thread.sleep(500);
      fireListener(node1);
      fireListener(node2);
   }

   private static void fireListener(DashboardRegistry registry) {
      String path = registry.getPath();
      int idx = path.lastIndexOf('/');
      DataChangeEvent event = new DataChangeEvent(path.substring(0, idx),
                                                  path.substring(idx + 1),
                                                  System.currentTimeMillis());

      try {
         Field field = DashboardRegistry.class.getDeclaredField("changeListener");
         field.setAccessible(true);
         ((DataChangeListener) field.get(registry)).dataChanged(event);
      }
      catch(ReflectiveOperationException e) {
         throw new RuntimeException(e);
      }
   }

   private static Set<String> names(DashboardRegistry registry) {
      return new HashSet<>(Arrays.asList(registry.getDashboardNames()));
   }

   private Set<String> fileNames() throws Exception {
      Set<String> names = new HashSet<>();

      try(InputStream in = dataSpace.getInputStream(null, node1.getPath())) {
         assertNotNull(in, "registry file must exist");
         org.w3c.dom.NodeList list =
            Tool.parseXML(in).getDocumentElement().getElementsByTagName("name");

         for(int i = 0; i < list.getLength(); i++) {
            names.add(list.item(i).getTextContent().trim());
         }
      }

      return names;
   }

   private static VSDashboard newVsDashboard() {
      VSDashboard dashboard = new VSDashboard();
      initVsDashboard(dashboard);
      return dashboard;
   }

   private static void initVsDashboard(VSDashboard dashboard) {
      String path = "myvs";
      AssetEntry entry = new AssetEntry(AssetRepository.GLOBAL_SCOPE, AssetEntry.Type.VIEWSHEET,
                                        path, null, "host-org");
      ViewsheetEntry viewsheetEntry = new ViewsheetEntry(path, null);
      viewsheetEntry.setIdentifier(entry.toIdentifier());
      dashboard.setViewsheet(viewsheetEntry);
   }

   /**
    * A dashboard that runs a hook the first time it is written, i.e. inside the create, after the
    * create was applied and before the file is written. It is written as a plain VSDashboard.
    */
   private static final class BlockingDashboard extends VSDashboard {
      BlockingDashboard() {
         initVsDashboard(this);
      }

      @Override
      public void writeXML(PrintWriter writer) {
         if(onWrite != null && listener == null) {
            listener = onWrite.get();
         }

         StringWriter buffer = new StringWriter();
         PrintWriter out = new PrintWriter(buffer);
         super.writeXML(out);
         out.flush();
         writer.print(buffer.toString().replace(getClass().getName(),
                                                VSDashboard.class.getName()));
      }

      volatile java.util.function.Supplier<Thread> onWrite;
      volatile Thread listener;
   }

   /**
    * Wraps the store so that get() returns a serialized copy, as the Ignite replicated cache does,
    * and parks the named thread once after its get().
    */
   @SuppressWarnings("unchecked")
   private static KeyValueStorage<DashboardManager.DashboardData> parkAfterGet(
      KeyValueStorage<DashboardManager.DashboardData> real, Park park)
   {
      return (KeyValueStorage<DashboardManager.DashboardData>) Proxy.newProxyInstance(
         DashboardClusterConcurrencyTest.class.getClassLoader(),
         new Class<?>[] { KeyValueStorage.class },
         (proxy, method, args) -> {
            Object result;

            try {
               result = method.invoke(real, args);
            }
            catch(InvocationTargetException e) {
               throw e.getCause();
            }

            if("get".equals(method.getName()) && result instanceof Serializable) {
               result = serialCopy((Serializable) result);

               if(park.armed && park.thread.equals(Thread.currentThread().getName())) {
                  park.parkHere();
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

   private static final class Park {
      Park(String thread) {
         this.thread = thread;
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
      volatile boolean armed;
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
    * Waits until a thread is blocked on a monitor or has ended.
    */
   private static void awaitBlockedOrDone(Thread thread) {
      long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(TIMEOUT_SECONDS);

      while(thread.isAlive() && thread.getState() != Thread.State.BLOCKED) {
         assertTrue(System.nanoTime() < deadline, thread.getName() + " neither blocked nor ended");
         Thread.onSpinWait();
      }
   }

   /**
    * Waits until a thread has either passed its step (the latch is counted down) or waits for a
    * lock owned by another thread. Without the fix the thread passes its step; with the fix it
    * waits for the owner's cluster lock.
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

   @Configuration
   static class Config {
      @Bean
      public DashboardRegistryManager dashboardRegistryManager(
         ApplicationEventPublisher eventPublisher, SecurityEngine securityEngine,
         inetsoft.uql.asset.DependencyHandler dependencyHandler, DataSpace dataSpace)
      {
         return new DashboardRegistryManager(eventPublisher, securityEngine, dependencyHandler,
                                             dataSpace);
      }

      @Bean
      public DashboardManager dashboardManager(SecurityEngine securityEngine,
                                               DashboardRegistryManager dashboardRegistryManager,
                                               KeyValueStorageManager keyValueStorageManager)
      {
         return new DashboardManager(securityEngine, dashboardRegistryManager,
                                     keyValueStorageManager);
      }
   }
}
