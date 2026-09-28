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
 * Bug #77203. Deterministic interleavings of DashboardRegistry reloads, renames and removes
 * against DashboardManager (D), the DashboardRegistryManager lock (M), user registries (R_u) and
 * global registries (R_g). The lock order is D -> M -> R_u -> R_g.
 *
 * The interleavings are driven with a Mockito spy on the DataSpace bean, which parks a reload in
 * getInputStream() (between reset() and parseXML(), holding the registry lock), and by invoking
 * the registry's private change listener directly on a named thread instead of waiting for the
 * asynchronous BlobStorageEvent delivery. A thread is only let go once the other thread is seen
 * blocked on the expected lock owner (ThreadMXBean), so no step depends on timing. The racing
 * actions run on daemon threads with a bounded wait, and a deadlock fails the test instead of
 * hanging the suite. Each test gets a fresh context, so a deadlocked thread can't leave a monitor
 * of a shared singleton held for the next test.
 */

import inetsoft.sree.SreeEnv;
import inetsoft.sree.ViewsheetEntry;
import inetsoft.sree.security.*;
import inetsoft.sree.security.support.SecurityTestDataBuilder;
import inetsoft.storage.KeyValueStorageManager;
import inetsoft.test.*;
import inetsoft.uql.asset.AssetEntry;
import inetsoft.uql.asset.AssetRepository;
import inetsoft.uql.asset.DependencyHandler;
import inetsoft.util.DataChangeListener;
import inetsoft.util.DataChangeListenerManager;
import inetsoft.util.DataSpace;
import inetsoft.util.FileVersions;
import inetsoft.util.Tool;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;

import java.io.*;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.Lock;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.*;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class,
                                  DashboardRegistryConcurrencyTest.Config.class },
                      initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@SreeHome
@Tag("core")
class DashboardRegistryConcurrencyTest {
   @Autowired
   private DashboardRegistryManager registryManager;

   @Autowired
   private DashboardManager dashboardManager;

   @Autowired
   private DataSpace dataSpace;

   @Autowired
   private ApplicationEventPublisher eventPublisher;

   @Autowired
   private SecurityEngine securityEngine;

   private SecurityTestDataBuilder builder;
   private final List<Thread> threads = new ArrayList<>();

   @BeforeEach
   void setUp() {
      assertTrue(mockingDetails(dataSpace).isSpy(), "the DataSpace bean must be the spy");
      OrganizationContextHolder.clear();
   }

   @AfterEach
   void tearDown() {
      threads.forEach(Thread::interrupt);
      threads.clear();
      OrganizationContextHolder.clear();

      if(builder != null) {
         builder.teardown();
         builder = null;
      }
   }

   // ── titled race: a reader never sees a registry emptied by an in-progress reload ──

   @Test
   void reload_concurrentGetDashboard_waitsForReloadAndSeesTheDashboard() throws Exception {
      DashboardRegistry global = registryManager.getRegistry();
      global.addDashboard("A__GLOBAL", newVsDashboard(currentOrg(), null));
      global.save();

      ParkedLoad parked = parkLoad(global.getPath(), null);
      Thread reloader = start("reloader", () -> changeListener(global).dataChanged(null));
      parked.awaitParked();

      AtomicReference<Dashboard> result = new AtomicReference<>();
      Thread reader = start("reader", () -> result.set(global.getDashboard("A__GLOBAL")));
      awaitBlockedBy(reader, reloader);

      parked.release();
      assertCompletes(reloader, reader);
      assertNotNull(result.get(), "a reader must never see the registry emptied by a reload");
   }

   // ── M1-a: global rename (R_g -> D before the fix) vs getDashboards (D -> R_g) ──

   @Test
   void globalRename_whileDashboardManagerReadsTheRegistry_doesNotDeadlock() throws Exception {
      DashboardRegistry global = registryManager.getRegistry();
      global.addDashboard("d1__GLOBAL", newVsDashboard(currentOrg(), null));

      CountDownLatch holdingD = new CountDownLatch(1);
      CountDownLatch renameBlocked = new CountDownLatch(1);

      // models DashboardManager.getDashboards(), which holds D and then reads the registries
      Thread lister = start("D.getDashboards", () -> {
         synchronized(dashboardManager) {
            holdingD.countDown();
            await(renameBlocked);
            global.getDashboard("d2__GLOBAL");
         }
      });
      await(holdingD);

      Thread renamer = start("R_g.renameDashboard",
                             () -> global.renameDashboard("d1__GLOBAL", "d2__GLOBAL"));
      awaitBlockedBy(renamer, lister);
      renameBlocked.countDown();

      assertCompletes(lister, renamer);
      assertNotNull(global.getDashboard("d2__GLOBAL"));
      assertNull(global.getDashboard("d1__GLOBAL"));
   }

   // ── M1-a': user remove (R_u -> D before the fix) vs getDashboards for that user (D -> R_u) ──

   @Test
   void userRemove_whileDashboardManagerReadsTheRegistry_doesNotDeadlock() throws Exception {
      IdentityID user = new IdentityID("dashcc_remove", currentOrg());
      DashboardRegistry registry = registryManager.getRegistry(user);
      registry.addDashboard("mine", newVsDashboard(currentOrg(), user.name));

      CountDownLatch holdingD = new CountDownLatch(1);
      CountDownLatch removeBlocked = new CountDownLatch(1);

      Thread lister = start("D.getDashboards", () -> {
         synchronized(dashboardManager) {
            holdingD.countDown();
            await(removeBlocked);
            registry.getDashboard("mine");
         }
      });
      await(holdingD);

      Thread remover = start("R_u.removeDashboard", () -> registry.removeDashboard("mine"));
      awaitBlockedBy(remover, lister);
      removeBlocked.countDown();

      assertCompletes(lister, remover);
      assertNull(registry.getDashboard("mine"));
   }

   // ── M1-b: global rename (R_g -> M before the fix) vs migrateRegistry/copyRegistry (M -> R_g) ──

   @Test
   void globalRename_whileRegistryManagerReadsTheGlobalRegistry_doesNotDeadlock()
      throws Exception
   {
      DashboardRegistry global = registryManager.getRegistry();
      global.addDashboard("d1__GLOBAL", newVsDashboard(currentOrg(), null));
      Lock lock = managerLock();

      CountDownLatch holdingM = new CountDownLatch(1);
      CountDownLatch renameBlocked = new CountDownLatch(1);

      // models migrateRegistry(null, ...), which holds M and then reads the global registry
      Thread migrator = start("M.migrateRegistry", () -> {
         lock.lock();

         try {
            holdingM.countDown();
            await(renameBlocked);
            global.getDashboardNames();
         }
         finally {
            lock.unlock();
         }
      });
      await(holdingM);

      Thread renamer = start("R_g.renameDashboard",
                             () -> global.renameDashboard("d1__GLOBAL", "d2__GLOBAL"));
      awaitBlockedBy(renamer, migrator);
      renameBlocked.countDown();

      assertCompletes(migrator, renamer);
      assertNotNull(global.getDashboard("d2__GLOBAL"));
   }

   // ── M1-c: global rename (R_g -> M -> R_u before the fix) vs a user reload (R_u -> R_g) ──

   @Test
   void globalRename_whileUserRegistryReloadsAnOldFile_doesNotDeadlock() throws Exception {
      String org = currentOrg();
      IdentityID user = new IdentityID("dashcc_port", org);
      DashboardRegistry global = registryManager.getRegistry();
      global.addDashboard("d1__GLOBAL", newVsDashboard(org, null));
      DashboardRegistry userRegistry = registryManager.getRegistry(user);
      userRegistry.addDashboard("d1__GLOBAL", newVsDashboard(org, null));

      // the reload reads an old-version file, which makes it look up the global registry
      ParkedLoad parked = parkLoad(userRegistry.getPath(), registryXml("9.0", org, "d1"));
      Thread reloader = start("R_u.reload", () -> changeListener(userRegistry).dataChanged(null));
      parked.awaitParked();

      Thread renamer = start("R_g.renameDashboard",
                             () -> global.renameDashboard("d1__GLOBAL", "d2__GLOBAL"));
      // the rename reaches the user registry, which the parked reload holds
      awaitBlockedBy(renamer, reloader);
      parked.release();

      assertCompletes(reloader, renamer);
      assertNotNull(global.getDashboard("d2__GLOBAL"));
   }

   // ── M1-d: DashboardRegistryManager.renameDashboard (M -> R_u -> D before the fix) vs
   //    getDashboards (D -> M) ──

   @Test
   void registryManagerRename_whileDashboardManagerGetsARegistry_doesNotDeadlock()
      throws Exception
   {
      IdentityID user = new IdentityID("dashcc_rename", currentOrg());
      DashboardRegistry userRegistry = registryManager.getRegistry(user);
      userRegistry.addDashboard("d1__GLOBAL", newVsDashboard(currentOrg(), null));

      CountDownLatch holdingD = new CountDownLatch(1);
      CountDownLatch renameBlocked = new CountDownLatch(1);

      // models DashboardManager.getDashboards(), which holds D and then calls getRegistry() (M)
      Thread lister = start("D.getDashboards", () -> {
         synchronized(dashboardManager) {
            holdingD.countDown();
            await(renameBlocked);
            registryManager.getRegistry();
         }
      });
      await(holdingD);

      Thread renamer = start("M.renameDashboard",
                             () -> registryManager.renameDashboard("d1__GLOBAL", "d2__GLOBAL"));
      awaitBlockedBy(renamer, lister);
      renameBlocked.countDown();

      assertCompletes(lister, renamer);
      assertNotNull(userRegistry.getDashboard("d2__GLOBAL"));
   }

   // ── R-a: the user registry is re-loaded between the rename snapshot and the rename ──

   @Test
   void registryManagerRename_userRegistryReloadedAfterSnapshot_noNullDashboard()
      throws Exception
   {
      String org = currentOrg();
      IdentityID user = new IdentityID("dashcc_snapshot", org);
      DashboardRegistry userRegistry = registryManager.getRegistry(user);
      userRegistry.addDashboard("d1__GLOBAL", newVsDashboard(org, null));
      userRegistry.save();
      Thread renamer;

      synchronized(dashboardManager) {
         // the snapshot contains the user registry, the rename then waits for D
         renamer = start("M.renameDashboard",
                         () -> registryManager.renameDashboard("d1__GLOBAL", "d2__GLOBAL"));
         awaitBlockedBy(renamer, Thread.currentThread());

         // the file no longer has the old name, and the registry is re-loaded from it
         writeFile(userRegistry.getPath(), registryXml(FileVersions.DASHBOARD_REGISTRY, org, "other"));
         Thread reloader =
            start("R_u.reload", () -> changeListener(userRegistry).dataChanged(null));
         assertCompletes(reloader);
         assertNull(userRegistry.getDashboard("d1__GLOBAL"));
      }

      assertCompletes(renamer);
      Map<String, Dashboard> map = userRegistry.getDashboardsMapSnapshot();
      assertFalse(map.containsValue(null), "a rename must never put a null dashboard: " + map);
      assertFalse(map.containsKey("d2__GLOBAL"));
      assertDoesNotThrow(userRegistry::save);
   }

   // ── M4b: the listener ports an old user file against the user's own org ──

   @Test
   void listenerReload_oldUserFile_portedAgainstTheUsersOwnOrg() throws Exception {
      String orgB = "dashcc_lsn_b";
      IdentityID bob = new IdentityID("bob", orgB);
      setupOrgs(orgB);
      seedPortFiles(orgB, bob);

      DashboardRegistry registry = new DashboardRegistry.UserDashboardRegistry(
         bob, orgB, eventPublisher, securityEngine);

      try {
         // a plain thread has no org, like the BlobStorageEvent thread, it resolves the host org
         Thread reloader =
            start("BlobStorageEvent-like", () -> changeListener(registry).dataChanged(null));
         assertCompletes(reloader);

         assertArrayEquals(new String[] { "X__GLOBAL" }, registry.getDashboardNames());
      }
      finally {
         registry.clear();
      }
   }

   // ── M4b': getRegistry() ports a new user registry against the org it was given ──

   @Test
   void copyRegistry_fromHostOrgCaller_portsSourceAgainstTheSourceOrg() throws Exception {
      String orgB = "dashcc_copy_b";
      String orgC = "dashcc_copy_c";
      IdentityID bob = new IdentityID("bob", orgB);
      setupOrgs(orgB, orgC);
      seedPortFiles(orgB, bob);
      assertEquals(Organization.getDefaultOrganizationID(), currentOrg(),
                   "precondition: the caller is in the host org");

      registryManager.copyRegistry(bob, new Organization(orgB), new Organization(orgC));

      assertArrayEquals(new String[] { "X__GLOBAL" },
                        registryManager.getRegistry(bob).getDashboardNames());
      assertEquals(List.of("X__GLOBAL"), namesInFile(userPath(orgB, "bob")),
                   "the source org's file must be ported against the source org");
      assertEquals(List.of("X__GLOBAL"), namesInFile(userPath(orgC, "bob")));
   }

   @Test
   void getRegistry_unknownOrg_doesNotCacheAGlobalRegistryWithANullPath() throws Exception {
      String org = "dashcc_unknown";
      IdentityID bob = new IdentityID("bob", org);
      writeFile(userPath(org, "bob"), registryXml("9.0", org, "X"));

      DashboardRegistry registry = registryManager.getRegistry(bob);

      assertArrayEquals(new String[] { "X" }, registry.getDashboardNames());
      assertFalse(registryCache().containsKey(org + "__ADMIN__"),
                  "no global registry may be created for an org the provider doesn't know");
      assertEquals(List.of("X"), namesInFile(userPath(org, "bob")));
      assertTrue(versionInFile(userPath(org, "bob")).startsWith("9.0"),
                 "a file that could not be ported must not be saved as ported");
   }

   // ── M4c: a registry evicted by clear() does not watch its file again ──

   @Test
   void renameUserDelete_evictedRegistry_doesNotReattachItsListener() throws Exception {
      IdentityID user = new IdentityID("dashcc_deleted", currentOrg());
      DashboardRegistry registry = registryManager.getRegistry(user);
      registry.addDashboard("mine", newVsDashboard(currentOrg(), user.name));
      registry.save();
      assertEquals(1, listenerCount(registry.getPath()));

      registryManager.renameUser(null, user);

      assertEquals(0, listenerCount(registry.getPath()),
                   "the evicted registry must not watch the file again after its save()");
      assertEquals(0, listenerManager(registry).size());

      registryManager.getRegistry(user);
      assertEquals(1, listenerCount(registry.getPath()));
   }

   @Test
   void renameUser_evictedGlobalRegistry_doesNotReattachItsListener() throws Exception {
      String org = currentOrg();
      DashboardRegistry global = registryManager.getRegistry();
      global.addDashboard("g__GLOBAL", newVsDashboard(org, null));
      global.save();
      assertEquals(1, listenerCount(global.getPath()));

      registryManager.renameUser(new IdentityID("dashcc_old", org),
                                 new IdentityID("dashcc_new", org));

      assertFalse(registryCache().containsValue(global), "precondition: the global is evicted");
      assertEquals(0, listenerManager(global).size(),
                   "the evicted global registry must not watch the file again");
      assertEquals(List.of("g__GLOBAL"), namesInFile(global.getPath()),
                   "the evicted registry must still write the file");

      // only the global registry the manager has cached now (loaded again for the new user's
      // registry) watches the file
      registryManager.getRegistry();
      assertEquals(1, listenerCount(global.getPath()));
   }

   @Test
   void evictedRegistry_changeEvent_doesNotReload() throws Exception {
      IdentityID user = new IdentityID("dashcc_evicted", currentOrg());
      DashboardRegistry registry = registryManager.getRegistry(user);
      registry.addDashboard("mine", newVsDashboard(currentOrg(), user.name));
      registryManager.clear(user);

      // an event that was already on its way when the registry was evicted
      Thread reloader = start("late event", () -> changeListener(registry).dataChanged(null));
      assertCompletes(reloader);

      assertArrayEquals(new String[] { "mine" }, registry.getDashboardNames());
      assertEquals(0, listenerManager(registry).size());
   }

   @Test
   void migrateRegistry_newOrg_registryWatchesItsNewFile() throws Exception {
      String orgFrom = "dashcc_mig_from";
      String orgTo = "dashcc_mig_to";
      setupOrgs(orgFrom, orgTo);
      DashboardRegistry registry = registryManager.getRegistry(orgFrom);
      registry.addDashboard("m__GLOBAL", newVsDashboard(orgFrom, null));
      registry.save();
      String oldPath = registry.getPath();

      registryManager.migrateRegistry(null, new Organization(orgFrom), new Organization(orgTo));

      assertSame(registry, registryCache().get(orgTo + "__ADMIN__"));
      assertEquals(1, listenerCount(registry.getPath()),
                   "a migrated registry is used again and must watch its new file");
      assertEquals(0, listenerCount(oldPath));
      assertEquals(List.of("m__GLOBAL"), namesInFile(registry.getPath()));
   }

   // ── fixture helpers ──

   private String currentOrg() {
      return OrganizationManager.getInstance().getCurrentOrgID();
   }

   private void setupOrgs(String... orgIds) throws Exception {
      builder = SecurityTestDataBuilder.create();

      for(String orgId : orgIds) {
         builder.addOrg(orgId + "_name", orgId);
      }

      builder.setup();
      OrganizationContextHolder.clear();
   }

   /**
    * The org's global registry has X__GLOBAL, the host org's has nothing, and bob's user file
    * is an old version with node X, which is ported to X__GLOBAL against bob's own org.
    */
   private void seedPortFiles(String org, IdentityID bob) throws Exception {
      writeFile(SreeEnv.getPath("$(sree.home)/portal/" + org + "/dashboard-registry.xml"),
                registryXml(FileVersions.DASHBOARD_REGISTRY, org, "X__GLOBAL"));
      writeFile(userPath(org, bob.name), registryXml("9.0", org, "X"));
      assertNull(registryManager.getRegistry().getDashboard("X__GLOBAL"),
                 "precondition: the host org's global registry has no X__GLOBAL");
   }

   private static String userPath(String org, String user) {
      return SreeEnv.getPath("$(sree.home)/portal/" + org + "/" + user + "/dashboard-registry.xml");
   }

   private static VSDashboard newVsDashboard(String orgId, String userName) {
      VSDashboard dashboard = new VSDashboard();
      IdentityID owner = userName == null ? null : new IdentityID(userName, orgId);
      int scope = owner == null ? AssetRepository.GLOBAL_SCOPE : AssetRepository.USER_SCOPE;
      String path = (userName == null ? "" : userName + "/") + "myvs";
      AssetEntry entry = new AssetEntry(scope, AssetEntry.Type.VIEWSHEET, path, owner, orgId);
      ViewsheetEntry viewsheetEntry = new ViewsheetEntry(path, owner);
      viewsheetEntry.setIdentifier(entry.toIdentifier());
      dashboard.setViewsheet(viewsheetEntry);
      return dashboard;
   }

   private static byte[] registryXml(String version, String org, String... names) {
      StringWriter buffer = new StringWriter();
      PrintWriter writer = new PrintWriter(buffer);
      writer.println("<?xml version=\"1.0\"?>");
      writer.println("<dashboardRegistry>");
      writer.println("<Version>" + version + "</Version>");

      for(String name : names) {
         writer.println("<node>");
         writer.println("<name><![CDATA[" + name + "]]></name>");
         newVsDashboard(org, null).writeXML(writer);
         writer.println("</node>");
      }

      writer.println("</dashboardRegistry>");
      writer.flush();
      return buffer.toString().getBytes(StandardCharsets.UTF_8);
   }

   private void writeFile(String path, byte[] content) throws IOException {
      dataSpace.withOutputStream(null, path, out -> out.write(content));
   }

   private Document readFile(String path) throws Exception {
      try(InputStream in = dataSpace.getInputStream(null, path)) {
         assertNotNull(in, "file must exist: " + path);
         return Tool.parseXML(in);
      }
   }

   private List<String> namesInFile(String path) throws Exception {
      NodeList nodes = readFile(path).getElementsByTagName("name");
      List<String> names = new ArrayList<>();

      for(int i = 0; i < nodes.getLength(); i++) {
         names.add(Tool.getValue(nodes.item(i)));
      }

      return names;
   }

   private String versionInFile(String path) throws Exception {
      return Tool.getValue(readFile(path).getElementsByTagName("Version").item(0));
   }

   /**
    * Parks the next load of a file by a thread started by this test, between the registry's
    * reset() and parseXML(), with the registry locked. The parked load reads {@code content}
    * if it is not null, otherwise the file.
    */
   private ParkedLoad parkLoad(String path, byte[] content) throws IOException {
      ParkedLoad parked = new ParkedLoad();

      doAnswer(inv -> {
         if(threads.contains(Thread.currentThread()) && parked.parked.getCount() > 0) {
            parked.parked.countDown();
            await(parked.release);

            if(content != null) {
               return new ByteArrayInputStream(content);
            }
         }

         return inv.callRealMethod();
      }).when(dataSpace).getInputStream(isNull(), eq(path));

      return parked;
   }

   private static final class ParkedLoad {
      void awaitParked() {
         await(parked);
      }

      void release() {
         release.countDown();
      }

      final CountDownLatch parked = new CountDownLatch(1);
      final CountDownLatch release = new CountDownLatch(1);
   }

   private static DataChangeListener changeListener(DashboardRegistry registry) {
      return (DataChangeListener) field(DashboardRegistry.class, "changeListener", registry);
   }

   private static DataChangeListenerManager listenerManager(DashboardRegistry registry) {
      return (DataChangeListenerManager) field(DashboardRegistry.class, "dmgr", registry);
   }

   private Lock managerLock() {
      return (Lock) field(DashboardRegistryManager.class, "lock", registryManager);
   }

   @SuppressWarnings("unchecked")
   private Map<String, DashboardRegistry> registryCache() {
      return (Map<String, DashboardRegistry>)
         field(DashboardRegistryManager.class, "registries", registryManager);
   }

   /**
    * Counts the listeners that the data space notifies for exactly this file.
    */
   @SuppressWarnings("unchecked")
   private int listenerCount(String path) {
      Object node = field(DataSpace.class, "listeners", dataSpace);
      node = field(node.getClass(), "root", node);

      for(String part : dataSpace.getPath(null, path).split("/")) {
         node = ((Map<String, Object>) field(node.getClass(), "children", node)).get(part);

         if(node == null) {
            return 0;
         }
      }

      return ((Set<DataChangeListener>) field(node.getClass(), "listeners", node)).size();
   }

   private static Object field(Class<?> type, String name, Object target) {
      try {
         Field field = type.getDeclaredField(name);
         field.setAccessible(true);
         return field.get(target);
      }
      catch(ReflectiveOperationException e) {
         throw new AssertionError(e);
      }
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
    * Waits until a thread is blocked on a lock (monitor or java.util.concurrent lock) owned by
    * another thread.
    */
   private static void awaitBlockedBy(Thread thread, Thread owner) throws InterruptedException {
      ThreadMXBean bean = ManagementFactory.getThreadMXBean();
      long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(TIMEOUT_SECONDS);

      while(System.nanoTime() < deadline) {
         ThreadInfo info = bean.getThreadInfo(thread.getId());

         if(info != null && info.getLockOwnerId() == owner.getId() &&
            (info.getThreadState() == Thread.State.BLOCKED ||
             info.getThreadState() == Thread.State.WAITING))
         {
            return;
         }

         assertTrue(thread.isAlive(), thread.getName() + " ended before it blocked");
         Thread.sleep(10);
      }

      fail(thread.getName() + " never blocked on a lock held by " + owner.getName());
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

      while(Arrays.stream(threads).anyMatch(Thread::isAlive)) {
         long[] deadlocked = bean.findDeadlockedThreads();

         // only this test's threads, a failed earlier test may have left deadlocked threads
         if(deadlocked != null && Arrays.stream(deadlocked).anyMatch(ids::contains)) {
            StringBuilder message = new StringBuilder("deadlock:");

            for(ThreadInfo info : bean.getThreadInfo(deadlocked, true, true)) {
               message.append('\n').append(info);
            }

            fail(message.toString());
         }

         assertTrue(System.nanoTime() < deadline, "threads did not end in time");
         Thread.sleep(10);
      }
   }

   private static final int TIMEOUT_SECONDS = 30;

   @Configuration
   static class Config {
      @Bean
      public static BeanPostProcessor dataSpaceSpy() {
         return new BeanPostProcessor() {
            @Override
            public Object postProcessAfterInitialization(Object bean, String beanName) {
               return bean instanceof DataSpace ? spy(bean) : bean;
            }
         };
      }

      @Bean
      public DashboardRegistryManager dashboardRegistryManager(
         ApplicationEventPublisher eventPublisher, SecurityEngine securityEngine,
         DependencyHandler dependencyHandler, DataSpace dataSpace)
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
                                     keyValueStorageManager)
         {
            // DashboardManager.close() is synchronized. If a regression leaves a deadlocked
            // thread holding the manager, closing the dirtied context would hang the suite
            // instead of letting the failed test report.
            @Override
            public void close() {
            }
         };
      }
   }
}
