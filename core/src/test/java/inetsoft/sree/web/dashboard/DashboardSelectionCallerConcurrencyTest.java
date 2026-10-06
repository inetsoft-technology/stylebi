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
 * Bug #77872. Each DashboardManager setter is atomic, but a caller that reads a whole list with
 * getDashboards() or getDeselectedDashboards() and writes it back with setDashboards() or
 * setDeselectedDashboards() releases the manager's monitor and the store lock in between, and
 * overwrites a change another request commits in that gap: the EM dashboard order save, the
 * grant and un-grant loops of the dashboard permissions and the deselected list of a dashboard
 * import. The callers now change the stored names in one locked read-modify-write
 * (DashboardManager.updateDashboards() / updateDashboardLists()).
 *
 * The interleave is deterministic. A HookedManager runs the concurrent writer on another thread,
 * once, on the caller's thread:
 * - right after a getter returns and the caller holds no lock (the gap of a split read-modify-
 *   write). The writer then runs to its end, as nothing stops it;
 * - or right after the caller's read of the stored record inside a locked read-modify-write. The
 *   writer is then let go once it waits on a lock the caller holds, and ends after the caller.
 * Either way the writer's change lands after the caller's read, and both changes must be kept.
 *
 * As in DashboardManagerSetterConcurrencyTest, the store is wrapped in a proxy whose get()
 * returns a serialized copy, as the Ignite replicated cache does, so that the in-place changes of
 * the setters can't hide a lost update. GROUP identities and the anonymous user are used, so that
 * the user selection sync doesn't take part. The class is in this package for the registry's
 * package-private syncWithFile() and isFileLoaded().
 */

import inetsoft.sree.security.*;
import inetsoft.storage.KeyValueStorage;
import inetsoft.storage.KeyValueStorageManager;
import inetsoft.storage.LoadKeyValueTask;
import inetsoft.test.*;
import inetsoft.uql.XPrincipal;
import inetsoft.uql.asset.DependencyHandler;
import inetsoft.uql.asset.sync.RenameTransformHandler;
import inetsoft.uql.util.DefaultIdentity;
import inetsoft.uql.util.Identity;
import inetsoft.util.dep.DashboardAsset;
import inetsoft.web.admin.content.repository.*;
import inetsoft.web.admin.content.repository.model.RepositoryFolderDashboardSettingsModel;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedStatic;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.io.*;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadInfo;
import java.lang.reflect.*;
import java.nio.charset.StandardCharsets;
import java.security.Principal;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class DashboardSelectionCallerConcurrencyTest {
   @Autowired
   private KeyValueStorageManager keyValueStorageManager;

   private HookedManager manager;
   private RepositoryDashboardService service;
   private DashboardRegistryManager registryManager;
   private volatile KeyValueStorage<DashboardManager.DashboardData> rawStorage;
   private final Set<String> globalNames = Collections.synchronizedSet(new HashSet<>());
   private final Set<String> userNames = Collections.synchronizedSet(new HashSet<>());
   private final List<Identity> seeded = new ArrayList<>();
   private final Identity anonymous = new DefaultIdentity(XPrincipal.ANONYMOUS, Identity.USER);

   @BeforeEach
   void setUp() {
      OrganizationContextHolder.clear();
      DashboardRegistry gregistry = registry(globalNames, true);
      DashboardRegistry uregistry = registry(userNames, false);
      registryManager = mock(DashboardRegistryManager.class);
      when(registryManager.getRegistry()).thenReturn(gregistry);
      when(registryManager.getRegistry((IdentityID) any())).thenReturn(uregistry);

      KeyValueStorageManager storages = mock(KeyValueStorageManager.class);
      when(storages.getStorage(anyString(), any(LoadKeyValueTask.class))).thenAnswer(inv -> {
         KeyValueStorage<DashboardManager.DashboardData> real =
            keyValueStorageManager.getStorage(inv.getArgument(0), inv.getArgument(1));
         rawStorage = real;
         return copyOnRead(real);
      });

      manager = new HookedManager(registryManager, storages);
      // first use switches the manager to the current org, outside the interleavings
      manager.getDashboards(new DefaultIdentity("dash77872_init", Identity.GROUP));
      assertNotNull(rawStorage);

      SecurityProvider securityProvider = mock(SecurityProvider.class);
      when(securityProvider.checkPermission(any(), any(), anyString(), any())).thenReturn(true);
      SecurityEngine securityEngine = mock(SecurityEngine.class);
      when(securityEngine.isSecurityEnabled()).thenReturn(false);
      service = new RepositoryDashboardService(
         mock(ResourcePermissionService.class), securityProvider,
         mock(ContentRepositoryTreeService.class), manager, securityEngine,
         mock(DependencyHandler.class), registryManager, mock(RenameTransformHandler.class));
   }

   @AfterEach
   void tearDown() throws Exception {
      manager.disarm();

      if(rawStorage != null) {
         for(Identity identity : seeded) {
            rawStorage.remove(key(identity)).get(10L, TimeUnit.SECONDS);
         }
      }

      OrganizationContextHolder.clear();
   }

   // ── EM order save (RepositoryDashboardService.setDashboardFolderSettings) ──

   @Test
   void emOrderSave_racingCreatedDashboard_keepsTheNewDashboard() throws Exception {
      userNames.addAll(List.of("A", "B", "New1"));
      seed(anonymous, List.of("A", "B"), List.of());
      manager.arm(() -> manager.addDashboard(anonymous, "New1"));

      runCaller(() -> service.setDashboardFolderSettings(order("B", "A"), principal()));

      List<String> stored = stored(anonymous).getDashboards();
      assertTrue(stored.contains("New1"),
                 "a dashboard created while the order is saved must stay selected: " + stored);
      assertTrue(stored.indexOf("B") < stored.indexOf("A"), "the order is saved: " + stored);
   }

   @Test
   void emOrderSave_racingGlobalRename_keepsTheRenamedDashboard() throws Exception {
      globalNames.addAll(List.of("G__GLOBAL", "K__GLOBAL"));
      seed(anonymous, List.of("G__GLOBAL", "K__GLOBAL"), List.of());
      manager.arm(() -> {
         manager.renameDashboard("G__GLOBAL", "H__GLOBAL");
         globalNames.remove("G__GLOBAL");
         globalNames.add("H__GLOBAL");
      });

      runCaller(() -> service.setDashboardFolderSettings(order("K__GLOBAL", "G__GLOBAL"),
                                                         principal()));

      assertEquals(List.of("K__GLOBAL", "H__GLOBAL"), stored(anonymous).getDashboards(),
                   "the rename must not be overwritten by the old name");
   }

   /*
    * No concurrency: a stored name that the registries don't list and that is not known to be
    * gone (its registry file is not loaded) is left out by getDashboards(). Reordering must keep
    * it, and must not add the request's names that are not stored.
    */
   @Test
   void emOrderSave_nameNotInRegistry_isKept() throws Exception {
      userNames.addAll(List.of("A", "B"));
      seed(anonymous, List.of("A", "B", "Z"), List.of());

      runCaller(() -> service.setDashboardFolderSettings(order("B", "Injected", "A"),
                                                         principal()));

      assertEquals(List.of("Z", "B", "A"), stored(anonymous).getDashboards(),
                   "the stored names are reordered, none is dropped or added");
   }

   // ── dashboard permissions (RepositoryDashboardService.setIdentityPermission) ──

   @Test
   void grant_racingCreatedDashboard_keepsTheNewDashboard() throws Exception {
      Identity group = new DefaultIdentity("dash77872_g2", Identity.GROUP);
      seed(group, List.of("A"), List.of());
      manager.arm(() -> manager.addDashboard(group, "New2"));

      runCaller(() -> setIdentityPermission(new HashSet<>(Set.of(group.getName())),
                                            new IdentityID[0], "G__GLOBAL"));

      assertEquals(Set.of("A", "G__GLOBAL", "New2"),
                   new HashSet<>(stored(group).getDashboards()));
   }

   @Test
   void ungrant_racingCreatedDashboard_keepsTheNewDashboard() throws Exception {
      Identity group = new DefaultIdentity("dash77872_g3", Identity.GROUP);
      seed(group, List.of("A", "G__GLOBAL"), List.of());
      manager.arm(() -> manager.addDashboard(group, "New3"));

      runCaller(() -> setIdentityPermission(Collections.EMPTY_SET,
                                            new IdentityID[] { group.getIdentityID() },
                                            "G__GLOBAL"));

      assertEquals(List.of("A", "New3"), stored(group).getDashboards());
   }

   @Test
   void ungrant_selectedAndDeselected_removesOnlyTheSelectedName() throws Exception {
      Identity group = new DefaultIdentity("dash77872_g4", Identity.GROUP);
      globalNames.add("G__GLOBAL");
      seed(group, List.of("A", "G__GLOBAL"), List.of("G__GLOBAL"));

      runCaller(() -> setIdentityPermission(Collections.EMPTY_SET,
                                            new IdentityID[] { group.getIdentityID() },
                                            "G__GLOBAL"));

      DashboardManager.DashboardData data = stored(group);
      assertEquals(List.of("A"), data.getDashboards());
      assertEquals(List.of("G__GLOBAL"), data.getDeselected(),
                   "as before, a deselected name is only removed when it is not selected");
   }

   // ── dashboard import (DashboardAsset.parseContent) ──

   @Test
   void importDeselected_racingSetDeselected_keepsTheDeselectedDashboard() throws Exception {
      Identity group = new DefaultIdentity("dash77872_g7", Identity.GROUP);
      globalNames.addAll(List.of("D1", "X", "Imp__GLOBAL"));
      seed(group, List.of(), List.of("D1"));
      manager.arm(() -> manager.setDeselectedDashboards(group, new String[] { "D1", "X" }));

      String xml = "<dashboardAsset><deselected name=\"" + group.getName() + "\" type=\"" +
         Identity.GROUP + "\"/></dashboardAsset>";
      DashboardAsset asset = new DashboardAsset("Imp__GLOBAL", null);

      runCaller(() -> {
         try(MockedStatic<DashboardManager> managers =
                mockStatic(DashboardManager.class, CALLS_REAL_METHODS);
             MockedStatic<DashboardRegistryManager> registries =
                mockStatic(DashboardRegistryManager.class))
         {
            managers.when(DashboardManager::getManager).thenReturn(manager);
            DashboardRegistryManager importRegistries = mock(DashboardRegistryManager.class);
            when(importRegistries.getRegistry((IdentityID) any()))
               .thenReturn(mock(DashboardRegistry.class));
            registries.when(DashboardRegistryManager::getInstance).thenReturn(importRegistries);
            asset.parseContent(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)),
                               null, true, true);
         }

         return null;
      });

      // the writer is a whole-list set, like an arrange dialog save, so when it waits for the
      // import it replaces the import's list. Either way the import must not drop "X".
      List<String> deselected = stored(group).getDeselected();
      assertTrue(deselected.contains("X"),
                 "a dashboard deselected while the import runs must stay deselected: " +
                 deselected);
   }

   // ── fixture ──

   private RepositoryFolderDashboardSettingsModel order(String... dashboards) {
      return RepositoryFolderDashboardSettingsModel.builder()
         .dashboards(Arrays.asList(dashboards))
         .build();
   }

   private static Principal principal() {
      XPrincipal principal = mock(XPrincipal.class);
      when(principal.getName()).thenReturn(
         new IdentityID("admin", Organization.getDefaultOrganizationID()).convertToKey());
      return principal;
   }

   private Object setIdentityPermission(Set<String> grants, IdentityID[] identities,
                                        String dashboard)
      throws Exception
   {
      Method method = RepositoryDashboardService.class.getDeclaredMethod(
         "setIdentityPermission", Set.class, Identity.Type.class, IdentityID[].class,
         String.class, Principal.class);
      method.setAccessible(true);

      try {
         return method.invoke(service, grants, Identity.Type.GROUP, identities, dashboard,
                              principal());
      }
      catch(InvocationTargetException e) {
         throw e.getCause() instanceof Exception ? (Exception) e.getCause() : e;
      }
   }

   /**
    * Runs the caller under test on its own thread, the one the manager's hook watches, with
    * SecurityEngine.getSecurity() mocked there (static mocks are thread-local).
    */
   private void runCaller(Callable<?> caller) throws Exception {
      AtomicReference<Throwable> error = new AtomicReference<>();
      Thread thread = new Thread(() -> {
         SecurityProvider provider = mock(SecurityProvider.class);
         when(provider.getRoles(any(IdentityID.class))).thenReturn(new IdentityID[0]);
         when(provider.getAllRoles(any(IdentityID[].class))).thenReturn(new IdentityID[0]);
         SecurityEngine securityEngine = mock(SecurityEngine.class);
         when(securityEngine.getSecurityProvider()).thenReturn(provider);
         when(securityEngine.getOrgUsers(any())).thenReturn(new IdentityID[0]);

         try(MockedStatic<SecurityEngine> engines = mockStatic(SecurityEngine.class)) {
            engines.when(SecurityEngine::getSecurity).thenReturn(securityEngine);
            caller.call();
         }
         catch(Throwable e) {
            error.set(e);
         }
      }, CALLER);
      thread.setDaemon(true);
      thread.start();
      thread.join(TimeUnit.SECONDS.toMillis(TIMEOUT_SECONDS));
      assertFalse(thread.isAlive(), "the caller did not end in time");
      manager.awaitWriter();

      if(error.get() != null) {
         throw new AssertionError("the caller failed", error.get());
      }
   }

   private static DashboardRegistry registry(Set<String> names, boolean fileLoaded) {
      DashboardRegistry registry = mock(DashboardRegistry.class);
      when(registry.getDashboard(anyString()))
         .thenAnswer(inv -> names.contains(inv.<String>getArgument(0)) ?
            mock(Dashboard.class) : null);
      when(registry.getDashboardNames()).thenAnswer(inv -> names.toArray(new String[0]));
      when(registry.syncWithFile()).thenReturn(true);
      when(registry.isFileLoaded()).thenReturn(fileLoaded);
      return registry;
   }

   private void seed(Identity identity, List<String> selected, List<String> deselected)
      throws Exception
   {
      DashboardManager.DashboardData data = new DashboardManager.DashboardData();
      data.setDashboards(new ArrayList<>(selected));
      data.setDeselected(new ArrayList<>(deselected));
      rawStorage.put(key(identity), data).get(10L, TimeUnit.SECONDS);
      seeded.add(identity);
   }

   private DashboardManager.DashboardData stored(Identity identity) {
      return rawStorage.get(key(identity));
   }

   private static String key(Identity identity) {
      return identity.getType() + ":" + identity.getName();
   }

   /**
    * Wraps the store so that get() returns a serialized copy, as the Ignite replicated cache does,
    * and tells the manager about the reads of the caller's thread.
    */
   @SuppressWarnings("unchecked")
   private KeyValueStorage<DashboardManager.DashboardData> copyOnRead(
      KeyValueStorage<DashboardManager.DashboardData> real)
   {
      return (KeyValueStorage<DashboardManager.DashboardData>) Proxy.newProxyInstance(
         getClass().getClassLoader(), new Class<?>[] { KeyValueStorage.class },
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
            }

            if("get".equals(method.getName()) && manager != null) {
               manager.afterStoreGet();
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

   /**
    * Runs the armed writer once on another thread, after the caller's read: after a getter
    * returns when the caller holds no lock, or after the caller's store read inside a locked
    * read-modify-write (outside a getter).
    */
   private static final class HookedManager extends DashboardManager {
      HookedManager(DashboardRegistryManager registryManager, KeyValueStorageManager storages) {
         super(mock(SecurityEngine.class), registryManager, storages);
      }

      void arm(Runnable writer) {
         this.writer = writer;
         fired.set(false);
      }

      void disarm() {
         writer = null;
      }

      @Override
      public String[] getDashboards(Identity identity) {
         return inGetter(() -> super.getDashboards(identity));
      }

      @Override
      public String[] getDashboards(Identity identity, boolean sync) {
         return inGetter(() -> super.getDashboards(identity, sync));
      }

      @Override
      public String[] getDeselectedDashboards(Identity identity) {
         return inGetter(() -> super.getDeselectedDashboards(identity));
      }

      private String[] inGetter(java.util.function.Supplier<String[]> getter) {
         depth.set(depth.get() + 1);
         String[] result;

         try {
            result = getter.get();
         }
         finally {
            depth.set(depth.get() - 1);
         }

         // the gap of a split read-modify-write: nothing stops the writer
         if(depth.get() == 0 && !Thread.holdsLock(this) && fire()) {
            Thread thread = startWriter();

            try {
               thread.join(TimeUnit.SECONDS.toMillis(TIMEOUT_SECONDS));
            }
            catch(InterruptedException e) {
               Thread.currentThread().interrupt();
               throw new AssertionError(e);
            }

            assertFalse(thread.isAlive(), "the writer blocked, though no lock was held");
         }

         return result;
      }

      void afterStoreGet() {
         // the read of a locked read-modify-write: the writer must wait for the caller
         if(depth.get() == 0 && Thread.holdsLock(this) && fire()) {
            Thread thread = startWriter();
            long caller = Thread.currentThread().getId();
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(TIMEOUT_SECONDS);

            while(thread.isAlive()) {
               ThreadInfo info = ManagementFactory.getThreadMXBean().getThreadInfo(thread.getId());

               if(info != null && info.getLockOwnerId() == caller) {
                  break;
               }

               assertTrue(System.nanoTime() < deadline, "the writer neither ended nor waited");
               Thread.onSpinWait();
            }
         }
      }

      private boolean fire() {
         return writer != null && CALLER.equals(Thread.currentThread().getName()) &&
            fired.compareAndSet(false, true);
      }

      private Thread startWriter() {
         Runnable action = writer;
         Thread thread = new Thread(() -> {
            try {
               action.run();
            }
            catch(Throwable e) {
               writerError.set(e);
            }
         }, "writer");
         thread.setDaemon(true);
         writerThread = thread;
         thread.start();
         return thread;
      }

      void awaitWriter() throws InterruptedException {
         Thread thread = writerThread;

         if(thread != null) {
            thread.join(TimeUnit.SECONDS.toMillis(TIMEOUT_SECONDS));
            assertFalse(thread.isAlive(), "the writer did not end in time");
         }

         if(writerError.get() != null) {
            throw new AssertionError("the writer failed", writerError.get());
         }

         if(writer != null) {
            assertTrue(fired.get(), "the writer was never run");
         }
      }

      private volatile Runnable writer;
      private volatile Thread writerThread;
      private final AtomicBoolean fired = new AtomicBoolean();
      private final AtomicReference<Throwable> writerError = new AtomicReference<>();
      private final ThreadLocal<Integer> depth = ThreadLocal.withInitial(() -> 0);
   }

   private static final String CALLER = "dash77872-caller";
   private static final int TIMEOUT_SECONDS = 30;
}
