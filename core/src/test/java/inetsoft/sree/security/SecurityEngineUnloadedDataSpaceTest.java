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

import inetsoft.sree.SreeEnv;
import inetsoft.sree.internal.cluster.Cluster;
import inetsoft.storage.UnloadedDataSpaceTestConfiguration;
import inetsoft.test.*;
import inetsoft.util.ConfigurationContext;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.*;

import static inetsoft.storage.UnloadedDataSpaceTestConfiguration.ENGINE;
import static inetsoft.storage.UnloadedDataSpaceTestConfiguration.STORE;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #77198: the security provider chains and the virtual admin are stored in the data space.
 * When the startup load of {@code dataSpace} timed out, the store is empty, so
 * {@code SecurityChain} and {@code VirtualAuthenticationProvider} found no files and wrote empty
 * or default ones over the stored ones, and {@code SecurityEngine} served the virtual provider
 * while {@code security.enabled=true}. The stored chains were lost, so the next start came up
 * with security off too.
 *
 * <p>The tests run in order, each one in a new context that restarts the node on the key-value
 * engine and the blob files the previous one left: see
 * {@link UnloadedDataSpaceTestConfiguration}. The first one configures security on a loaded
 * store, and each one sets the {@code dataSpace} loads that time out in the next start.</p>
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(
   classes = { BaseTestConfiguration.class, UnloadedDataSpaceTestConfiguration.class },
   initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@SreeHome
@Tag("core")
class SecurityEngineUnloadedDataSpaceTest {
   @AfterAll
   static void clearEngine() throws Exception {
      UnloadedDataSpaceTestConfiguration.timeOutLoads(0);
      ENGINE.close();
   }

   @Test
   @Order(1)
   void securityIsConfiguredOnALoadedStore() throws Exception {
      assertEquals(0, UnloadedDataSpaceTestConfiguration.getTimedOutLoads(cluster));

      SreeEnv.setProperty("security.enabled", "true");
      SreeEnv.save();
      engine.newChain();
      engine.init();
      assertFalse(isVirtual(), "security is off on a loaded store");

      for(String file : FILES) {
         Object blob = ENGINE.get(STORE, file);
         assertNotNull(blob, file + " was not stored");
         stored.put(file, blob);
      }

      // the next start times out the first load of the data space
      UnloadedDataSpaceTestConfiguration.timeOutLoads(1);
   }

   @Test
   @Order(2)
   void startRetriesTheTimedOutLoad() {
      UnloadedDataSpaceTestConfiguration.timeOutLoads(0);
      assertFalse(stored.isEmpty(), "security was not configured before the restart");

      assertEquals(1, UnloadedDataSpaceTestConfiguration.getTimedOutLoads(cluster),
                   "the startup load of " + STORE + " did not time out");
      assertAll(
         () -> assertEquals("true", SreeEnv.getProperty("security.enabled")),
         () -> assertFalse(isVirtual(), "the node started with security off"),
         () -> assertStoredFilesUnchanged(),
         () -> assertEquals(2, UnloadedDataSpaceTestConfiguration.getLoads(cluster),
                            "the timed-out load of " + STORE + " was not retried"));
   }

   @Test
   @Order(3)
   void startFailsWhenTheLoadDoesNotComplete() {
      assertFalse(stored.isEmpty(), "security was not configured before the restart");
      UnloadedDataSpaceTestConfiguration.timeOutLoads(Integer.MAX_VALUE);
      // the context of this test started on the loaded store, start another one that cannot
      // load it
      AnnotationConfigApplicationContext node = new AnnotationConfigApplicationContext();
      Exception thrown = null;
      // Bug #77656: the chains of this test's context still get the change events of its startup
      // load, and they get the data space from the current context. While the node is the
      // current context, each event created the node's data space again and added loads. Remove
      // the chains before the node becomes the current context.
      engine.getSecurityProvider().tearDown();

      try {
         new ConfigurationContextInitializer().initialize(node);
         node.register(BaseTestConfiguration.class, UnloadedDataSpaceTestConfiguration.class);
         node.refresh();
      }
      catch(Exception e) {
         thrown = e;
      }
      finally {
         UnloadedDataSpaceTestConfiguration.timeOutLoads(0);
         node.close();
         ConfigurationContext.getContext().setApplicationContext(applicationContext);
      }

      Cluster nodeCluster = UnloadedDataSpaceTestConfiguration.getLastCluster();
      Exception startFailure = thrown;
      assertAll(
         () -> assertTrue(hasCause(startFailure, IllegalStateException.class,
                                   "Failed to load the data space storage " + STORE),
                          "the start did not fail on the unloaded " + STORE + ": " + startFailure),
         () -> assertEquals(2, UnloadedDataSpaceTestConfiguration.getLoads(nodeCluster),
                            "the load of " + STORE + " was not retried exactly once"),
         () -> assertStoredFilesUnchanged());
   }

   private void assertStoredFilesUnchanged() {
      for(String file : FILES) {
         assertEquals(stored.get(file), ENGINE.get(STORE, file),
                      "the stored " + file + " was replaced");
      }
   }

   private boolean isVirtual() {
      return engine.getSecurityProvider().getAuthenticationProvider().isVirtual();
   }

   private static boolean hasCause(Throwable thrown, Class<? extends Throwable> type,
                                   String message)
   {
      for(Throwable e = thrown; e != null; e = e.getCause()) {
         if(type.isInstance(e) && e.getMessage() != null && e.getMessage().contains(message)) {
            return true;
         }
      }

      return false;
   }

   @Autowired
   private SecurityEngine engine;
   @Autowired
   private Cluster cluster;
   @Autowired
   private ApplicationContext applicationContext;

   // the stored files, kept across the restarts
   private static final Map<String, Object> stored = new HashMap<>();
   private static final List<String> FILES =
      List.of("authc-chain.json", "authz-chain.json", "virtual_security.xml");
}
