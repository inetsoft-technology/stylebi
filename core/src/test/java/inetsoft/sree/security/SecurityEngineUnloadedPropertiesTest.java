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

import inetsoft.sree.EarlyLoadedProperties;
import inetsoft.sree.PropertiesEngine;
import inetsoft.sree.SreeEnv;
import inetsoft.sree.internal.cluster.Cluster;
import inetsoft.storage.KeyValueEngine;
import inetsoft.storage.UnloadedPropertiesTestConfiguration;
import inetsoft.test.*;
import inetsoft.util.ConfigurationContext;
import inetsoft.util.DataSpace;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.function.BooleanSupplier;

import static inetsoft.storage.UnloadedPropertiesTestConfiguration.STORE;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #76975: when the startup load of {@code sreeProperties} times out, the node initializes
 * {@code SecurityEngine} from an empty property store, so security is off. The properties arrive
 * a few seconds later, when the queued load runs, and the change task reloads them, but
 * {@code SecurityEngine} only re-initializes when {@code security.provider} changed. The stored
 * {@code security.enabled=true} is then reported by {@code SreeEnv} while the engine keeps
 * serving the virtual provider, until the node is restarted.
 *
 * <p>The load timeout, the empty store, the reload and the event are the product's own: see
 * {@link UnloadedPropertiesTestConfiguration}. The security provider chain is kept in the data
 * space, not in the property store, so it is written before the startup initialization is
 * repeated.</p>
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(
   classes = { BaseTestConfiguration.class, UnloadedPropertiesTestConfiguration.class },
   initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@SreeHome
@Tag("core")
class SecurityEngineUnloadedPropertiesTest {
   @AfterAll
   static void clearEarlyLoadedProperties() {
      // the early-loaded properties outlive a context in the test JVM and still hold the
      // security.enabled=true that these tests stored. A later test class that sets the same
      // value would not store it, so its next reload would turn security off
      ConfigurationContext.getContext().remove(EarlyLoadedProperties.class.getName());
   }

   @BeforeEach
   void startWithUnloadedProperties() {
      DataSpace space = DataSpace.getDataSpace();
      authcChainExisted = space.exists(null, AUTHC_CHAIN);
      authzChainExisted = space.exists(null, AUTHZ_CHAIN);

      assertTrue(UnloadedPropertiesTestConfiguration.firstLoadTimedOut(cluster),
                 "the startup load of " + STORE + " did not time out");

      // the early-loaded properties outlive a context in the test JVM and can still hold the
      // properties another test loaded, so load the empty store into fresh ones
      PropertiesEngine.getInstance().clear();
      PropertiesEngine.getInstance().init();
      engine.newChain();
      engine.init();
      before = "before the properties arrived: security.enabled=" +
         SreeEnv.getProperty("security.enabled") + ", virtual provider=" + isVirtual();
   }

   @AfterEach
   void removeChain() {
      DataSpace space = DataSpace.getDataSpace();

      if(!authcChainExisted) {
         space.delete(null, AUTHC_CHAIN);
      }

      if(!authzChainExisted) {
         space.delete(null, AUTHZ_CHAIN);
      }
   }

   @Test
   void securityIsEnabledOnceTheStoredPropertiesArrive() throws Exception {
      UnloadedPropertiesTestConfiguration.fill(cluster);
      waitFor(() -> "true".equals(SreeEnv.getProperty("security.enabled")),
              "the reload of the arrived properties");

      awaitNonVirtual();
      assertFalse(isVirtual(),
                  "SreeEnv reports security.enabled=true, but SecurityEngine still serves the " +
                     "virtual provider after the stored properties arrived (" + before + ")");
   }

   @Test
   void securityIsEnabledWhenTheArrivedPropertiesChangeTheProvider() throws Exception {
      // the only change that re-initializes SecurityEngine on a reload
      keyValueEngine.put(STORE, "security.provider", "bug76975");
      UnloadedPropertiesTestConfiguration.fill(cluster);
      waitFor(() -> "true".equals(SreeEnv.getProperty("security.enabled")),
              "the reload of the arrived properties");

      awaitNonVirtual();
      assertFalse(isVirtual(), "SecurityEngine was not re-initialized by a provider change (" +
         before + ")");
   }

   /**
    * The reloaded properties are visible before the reload event reaches SecurityEngine, so give
    * its listener a moment.
    */
   private void awaitNonVirtual() throws InterruptedException {
      long end = System.currentTimeMillis() + 2000L;

      while(isVirtual() && System.currentTimeMillis() < end) {
         Thread.sleep(50L);
      }
   }

   private boolean isVirtual() {
      return engine.getSecurityProvider().getAuthenticationProvider().isVirtual();
   }

   private static void waitFor(BooleanSupplier condition, String what)
      throws InterruptedException
   {
      long end = System.currentTimeMillis() + 10000L;

      while(!condition.getAsBoolean()) {
         if(System.currentTimeMillis() > end) {
            fail("Timed out waiting for " + what);
         }

         Thread.sleep(50L);
      }
   }

   @Autowired
   private SecurityEngine engine;
   @Autowired
   private Cluster cluster;
   @Autowired
   private KeyValueEngine keyValueEngine;
   private String before;
   private boolean authcChainExisted;
   private boolean authzChainExisted;

   private static final String AUTHC_CHAIN = "authc-chain.json";
   private static final String AUTHZ_CHAIN = "authz-chain.json";
}
