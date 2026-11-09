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
package inetsoft.web.admin.ai.scriptlibrary;

import inetsoft.report.LibManager;
import inetsoft.report.LibManagerProvider;
import inetsoft.report.lib.logical.NoopLibrarySecurity;
import inetsoft.sree.internal.cluster.Cluster;
import inetsoft.sree.security.IdentityID;
import inetsoft.sree.security.ResourceAction;
import inetsoft.sree.security.ResourceType;
import inetsoft.sree.security.SecurityEngine;
import inetsoft.storage.BlobStorageManager;
import inetsoft.test.*;
import inetsoft.uql.XPrincipal;
import inetsoft.uql.asset.DependencyHandler;
import inetsoft.uql.asset.sync.RenameTransformHandler;
import inetsoft.web.admin.ai.AdminBackupService;
import inetsoft.web.admin.ai.AdminChangesetApplyService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedStatic;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.lang.management.ManagementFactory;
import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Bug #80832 review: the apply steps hold the LibManager monitor (M) while they save.
 * {@code LibManagerProvider.getManager} takes the provider lock (L) and then M, so nothing running
 * under M may reach the provider (for example through {@code ScriptLibraryService.exists}), or a
 * concurrent {@code getManager} for the same organization deadlocks against it and freezes every
 * script-library access. This test uses a REAL provider and service and makes {@code save()} slow
 * so a concurrent {@code getManager} always lands inside the step.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class,
                                  ScriptLibraryChangesetApplyLockOrderTest.Config.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class ScriptLibraryChangesetApplyLockOrderTest {
   @Configuration
   static class Config {
      @Bean
      RenameTransformHandler renameTransformHandler() {
         return mock(RenameTransformHandler.class);
      }
   }

   @Autowired private BlobStorageManager blobStorageManager;

   private final CountDownLatch saveEntered = new CountDownLatch(1);
   private MockedStatic<DependencyHandler> dependencyStatic;
   private LibManager lib;
   private volatile boolean stuck;

   @BeforeEach
   void setUp() {
      dependencyStatic = mockStatic(DependencyHandler.class);
      dependencyStatic.when(DependencyHandler::getInstance)
         .thenReturn(mock(DependencyHandler.class));
   }

   @AfterEach
   void tearDown() throws Exception {
      dependencyStatic.close();

      // a deadlocked manager can never be cleaned up; do not hang the build on it
      if(lib != null && !stuck) {
         lib.runAtomically(() -> {
            lib.removeScript("fn80832L");
            lib.save();
            return null;
         });
         Thread.sleep(1500L);
         lib.close();
      }
   }

   @Test
   @SuppressWarnings("unchecked")
   void applyDoesNotDeadlockWithAConcurrentGetManager() throws Exception {
      Cluster cluster = mock(Cluster.class);
      SecurityEngine securityEngine = mock(SecurityEngine.class);
      LibManagerProvider provider =
         new LibManagerProvider(cluster, blobStorageManager, securityEngine);
      XPrincipal user = new XPrincipal(new IdentityID("tester", "r80832-lock-org"),
                                       new IdentityID[0], new String[0], "r80832-lock-org");
      when(securityEngine.checkPermission(
         eq(user), eq(ResourceType.SCRIPT), anyString(), eq(ResourceAction.ADMIN))).thenReturn(true);

      // let the provider resolve the organization, then swap in a manager with a slow save()
      provider.getManager(user);
      Field f = LibManagerProvider.class.getDeclaredField("managers");
      f.setAccessible(true);
      Map<String, LibManager> managers = (Map<String, LibManager>) f.get(provider);
      String orgID = managers.keySet().iterator().next();
      managers.get(orgID).close();
      lib = new LibManager(new NoopLibrarySecurity(), orgID, cluster, blobStorageManager) {
         @Override
         public synchronized void save() throws Exception {
            saveEntered.countDown();
            Thread.sleep(500L);
            super.save();
         }
      };
      managers.put(orgID, lib);

      ScriptLibraryService libraryService = new ScriptLibraryService(provider, securityEngine);
      ScriptLibraryChangePlanService planService = new ScriptLibraryChangePlanService(libraryService);
      planService.setSyntaxChecker(text -> { });
      ScriptLibraryChangesetApplyService service = new ScriptLibraryChangesetApplyService(
         planService, libraryService, provider, mock(AdminBackupService.class));

      ScriptLibraryChangeRequest c = new ScriptLibraryChangeRequest();
      c.setVerb(ScriptLibraryChangeRequest.VERB_CREATE);
      c.setName("fn80832L");
      c.setText("function fn80832L() { return 1; }");
      ScriptLibraryChangePlanRequest plan = new ScriptLibraryChangePlanRequest();
      plan.setTask("test");
      plan.setChanges(List.of(c));
      ScriptLibraryApplyRequest req = new ScriptLibraryApplyRequest();
      req.setTask("test");
      req.setChanges(List.of(c));
      req.setPlanHash(planService.resolve(plan, user).planHash());
      req.setReviewOutcome("ok");

      AtomicReference<ScriptLibraryApplyResult> result = new AtomicReference<>();
      Thread applier = new Thread(() -> {
         try {
            result.set(service.apply(req, user));
         }
         catch(Exception e) {
            throw new RuntimeException(e);
         }
      }, "apply-thread");
      Thread other = new Thread(() -> {
         try {
            saveEntered.await(10, TimeUnit.SECONDS);
            provider.getManager(orgID);
         }
         catch(InterruptedException ignore) {
         }
      }, "other-request");
      applier.setDaemon(true);
      other.setDaemon(true);
      applier.start();
      other.start();
      applier.join(15000L);
      other.join(5000L);

      long[] deadlocked = ManagementFactory.getThreadMXBean().findDeadlockedThreads();
      stuck = deadlocked != null || applier.isAlive() || other.isAlive();
      assertNull(deadlocked, "deadlocked threads: " + (deadlocked == null ? 0 : deadlocked.length));
      assertFalse(applier.isAlive(), "apply did not finish");
      assertFalse(other.isAlive(), "getManager did not finish");
      assertEquals(AdminChangesetApplyService.STATUS_APPLIED, result.get().status());
   }
}
