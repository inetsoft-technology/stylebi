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
import inetsoft.report.lib.physical.PhysicalLibrary;
import inetsoft.report.lib.logical.NoopLibrarySecurity;
import inetsoft.sree.internal.cluster.Cluster;
import inetsoft.sree.internal.cluster.MessageEvent;
import inetsoft.sree.internal.cluster.MessageListener;
import inetsoft.sree.security.ResourceAction;
import inetsoft.sree.security.ResourceType;
import inetsoft.sree.security.SecurityEngine;
import inetsoft.storage.BlobStorageManager;
import inetsoft.test.*;
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

import java.io.IOException;
import java.io.Serializable;
import java.security.Principal;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Bug #80832: a plan with two creates intermittently ended rollback-failed. The node receives its
 * own cluster message after every {@code save()} (asynchronously, as Ignite delivers it) and
 * reloads the stored library, which clears any {@code setScript}/{@code removeScript} not yet
 * saved. These tests use the real apply path over a real {@link LibManager}, with the self-message
 * delivered on another thread. The race is forced deterministically: the reload is held inside
 * {@code loadLibrary} (holding the manager's monitor, as every reload does) until the mutation
 * under test has been made.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class,
                                  ScriptLibraryChangesetApplyClusterReloadTest.Config.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class ScriptLibraryChangesetApplyClusterReloadTest {
   @Configuration
   static class Config {
      @Bean
      RenameTransformHandler renameTransformHandler() {
         return mock(RenameTransformHandler.class);
      }
   }

   @Autowired private BlobStorageManager blobStorageManager;

   private final ExecutorService delivery = Executors.newSingleThreadExecutor();
   private final AtomicReference<MessageListener> listener = new AtomicReference<>();
   private final Principal user = mock(Principal.class);
   private final DependencyHandler dependencyHandler = mock(DependencyHandler.class);
   private MockedStatic<DependencyHandler> dependencyStatic;
   private LibManager lib;
   private ScriptLibraryChangePlanService planService;
   private ScriptLibraryChangesetApplyService service;

   // race hooks; a message delivery and the reload only wait once hooksOn is set
   private volatile String hookSetName;
   private volatile String hookRemoveName;
   private final CountDownLatch trigger = new CountDownLatch(1);
   private final CountDownLatch reloadEntered = new CountDownLatch(1);
   private final CountDownLatch mutated = new CountDownLatch(1);
   private volatile boolean hooksOn;

   @BeforeEach
   void setUp() throws Exception {
      Cluster cluster = mock(Cluster.class);
      doAnswer(i -> {
         listener.set(i.getArgument(0));
         return null;
      }).when(cluster).addMessageListener(any(MessageListener.class));
      doAnswer(i -> {
         Serializable msg = i.getArgument(0);
         delivery.submit(() -> {
            try {
               if(hooksOn) {
                  trigger.await(10, TimeUnit.SECONDS);
               }

               listener.get().messageReceived(new MessageEvent(this, "local", true, msg));
            }
            catch(InterruptedException ignore) {
            }
         });
         return null;
      }).when(cluster).sendMessage(any(Serializable.class));

      lib = new LibManager(new NoopLibrarySecurity(), "r80832-org", cluster, blobStorageManager) {
         @Override
         public void setScript(String name, String func) {
            if(hooksOn && name.equals(hookSetName)) {
               fireRace();
            }

            super.setScript(name, func);

            if(hooksOn && name.equals(hookSetName)) {
               mutated.countDown();
            }
         }

         @Override
         public void removeScript(String name) {
            if(hooksOn && name.equals(hookRemoveName)) {
               fireRace();
            }

            super.removeScript(name);

            if(hooksOn && name.equals(hookRemoveName)) {
               mutated.countDown();
            }
         }

         @Override
         protected synchronized void loadLibrary(PhysicalLibrary library, boolean overwrite,
                                                 boolean audit, boolean init)
            throws IOException
         {
            if(hooksOn && trigger.getCount() == 0 && !holdDone) {
               holdDone = true;
               reloadEntered.countDown();

               try {
                  mutated.await(3, TimeUnit.SECONDS);
               }
               catch(InterruptedException ignore) {
               }
            }

            super.loadLibrary(library, overwrite, audit, init);
         }

         private void fireRace() {
            trigger.countDown();

            try {
               // with the fix the reload cannot start while this thread holds the monitor
               reloadEntered.await(1, TimeUnit.SECONDS);
            }
            catch(InterruptedException ignore) {
            }
         }

         private volatile boolean holdDone;
      };

      LibManagerProvider provider = mock(LibManagerProvider.class);
      when(provider.getManager(any(Principal.class))).thenReturn(lib);
      SecurityEngine securityEngine = mock(SecurityEngine.class);
      when(securityEngine.checkPermission(
         eq(user), eq(ResourceType.SCRIPT), anyString(), eq(ResourceAction.ADMIN))).thenReturn(true);
      ScriptLibraryService libraryService = new ScriptLibraryService(provider, securityEngine);
      planService = new ScriptLibraryChangePlanService(libraryService);
      planService.setSyntaxChecker(text -> { });
      service = new ScriptLibraryChangesetApplyService(planService, libraryService, provider,
                                                       mock(AdminBackupService.class));
      dependencyStatic = mockStatic(DependencyHandler.class);
      dependencyStatic.when(DependencyHandler::getInstance).thenReturn(dependencyHandler);
   }

   @AfterEach
   void tearDown() throws Exception {
      hooksOn = false;
      trigger.countDown();
      mutated.countDown();
      dependencyStatic.close();

      for(String n : List.of("fn80832A", "fn80832B")) {
         lib.removeScript(n);
      }

      lib.save();
      Thread.sleep(1500L);
      delivery.shutdownNow();
      lib.close();
   }

   @Test
   void secondCreateSurvivesAReloadStartedByTheFirstSave() throws Exception {
      hookSetName = "fn80832B";
      hooksOn = true;

      ScriptLibraryApplyResult result = apply(create("fn80832A"), create("fn80832B"));

      assertEquals(AdminChangesetApplyService.STATUS_APPLIED, result.status(),
                   String.valueOf(result));
      Thread.sleep(500L);
      assertNotNull(lib.getScript("fn80832A"));
      assertNotNull(lib.getScript("fn80832B"));
   }

   @Test
   void rollbackOfACreateSurvivesAReloadStartedBeforeIt() throws Exception {
      hookRemoveName = "fn80832A";
      hooksOn = true;
      // create B then fails before mutating: B appears behind the plan's back after A is created
      doAnswer(i -> {
         if(i.getArgument(1) != null && i.getArgument(1).toString().contains("fn80832A")) {
            lib.setScript("fn80832B", "function fn80832B() { return 2; }");
            lib.save();
         }

         return null;
      }).when(dependencyHandler).updateScriptDependencies(any(), any(), any());

      ScriptLibraryApplyResult result = apply(create("fn80832A"), create("fn80832B"));

      assertEquals(AdminChangesetApplyService.STATUS_ROLLED_BACK, result.status(),
                   String.valueOf(result));
      Thread.sleep(500L);
      assertNull(lib.getScript("fn80832A"));
   }

   @Test
   void twentyTwoPlanRunsAllApply() throws Exception {
      for(int i = 0; i < 20; i++) {
         ScriptLibraryApplyResult result = apply(create("fn80832A"), create("fn80832B"));
         assertEquals(AdminChangesetApplyService.STATUS_APPLIED, result.status(),
                      "run " + i + ": " + result);
         lib.removeScript("fn80832A");
         lib.removeScript("fn80832B");
         lib.save();
         Thread.sleep(50L);
      }
   }

   private ScriptLibraryChangeRequest create(String name) {
      ScriptLibraryChangeRequest c = new ScriptLibraryChangeRequest();
      c.setVerb(ScriptLibraryChangeRequest.VERB_CREATE);
      c.setName(name);
      c.setText("function " + name + "() { return 1; }");
      return c;
   }

   private ScriptLibraryApplyResult apply(ScriptLibraryChangeRequest... changes) throws Exception {
      ScriptLibraryChangePlanRequest plan = new ScriptLibraryChangePlanRequest();
      plan.setTask("test");
      plan.setChanges(List.of(changes));
      ScriptLibraryApplyRequest req = new ScriptLibraryApplyRequest();
      req.setTask("test");
      req.setChanges(List.of(changes));
      req.setPlanHash(planService.resolve(plan, user).planHash());
      req.setReviewOutcome("ok");
      return service.apply(req, user);
   }
}
