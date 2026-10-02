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

import inetsoft.sree.PropertiesEngine;
import inetsoft.sree.SreeEnv;
import inetsoft.sree.internal.cluster.Cluster;
import inetsoft.storage.KeyValueEngine;
import inetsoft.storage.LoadKeyValueTask;
import inetsoft.test.*;
import inetsoft.util.DataSpace;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #76975: {@code SecurityEngine} re-initializes when reloaded properties enable security
 * while it was initialized with security disabled, and not on a reload that leaves the security
 * state as it is. The stored properties are changed in the key-value engine and reloaded by an
 * external load of the store, so the entry events, the reload and the
 * {@code ApplicationPropertiesChangedEvent} are the product's own.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(
   classes = BaseTestConfiguration.class, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@SreeHome
@Tag("core")
class SecurityEngineReloadTest {
   @BeforeEach
   void startWithSecurityDisabled() throws Exception {
      DataSpace space = DataSpace.getDataSpace();
      authcChainExisted = space.exists(null, AUTHC_CHAIN);
      authzChainExisted = space.exists(null, AUTHZ_CHAIN);

      // the provider chain is kept in the data space, not in the property store. Building it
      // stores security.enabled=true, so build it first and then remove that property
      engine.newChain();
      keyValueEngine.remove(STORE, "security.enabled");
      keyValueEngine.remove(STORE, UNRELATED);
      // the engine removes from the persistent store only, while the properties are loaded from
      // the storage's replicated map, which still holds security.enabled=true in the context that
      // built the chain. Load the map from the store like an external change does
      cluster.submit(STORE, new LoadKeyValueTask<String>(STORE, true)).get(10L, TimeUnit.SECONDS);
      // the early-loaded properties outlive a context in the test JVM, so load the store into
      // fresh ones
      PropertiesEngine.getInstance().clear();
      PropertiesEngine.getInstance().init();
      engine.init();
      assertTrue(isVirtual(), "security is not disabled at the start");
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
   void reloadThatEnablesSecurityInitializesTheProvider() throws Exception {
      reload("security.enabled", "true");

      awaitNonVirtual();
      assertFalse(isVirtual(), "SreeEnv reports security.enabled=true, but SecurityEngine " +
         "still serves the virtual provider after the reload");
   }

   @Test
   void reloadThatKeepsTheSecurityStateKeepsTheProvider() throws Exception {
      reload("security.enabled", "true");
      awaitNonVirtual();
      SecurityProvider provider = engine.getSecurityProvider();
      assertFalse(provider.getAuthenticationProvider().isVirtual(), "security was not enabled");

      reload(UNRELATED, "changed");
      // the listener runs after the reloaded properties are visible, give it a moment
      Thread.sleep(1000L);
      assertSame(provider, engine.getSecurityProvider(),
                 "a reload that did not change the security state re-initialized SecurityEngine");
   }

   /**
    * Security is enabled but no provider can be created (no chain), and the engine was
    * initialized that way: a reload must not re-initialize it every time.
    */
   @Test
   void reloadWithSecurityEnabledButNoProviderDoesNotReinitialize() throws Exception {
      DataSpace space = DataSpace.getDataSpace();

      try {
         space.delete(null, AUTHC_CHAIN);
         space.delete(null, AUTHZ_CHAIN);
         reload("security.enabled", "true");
         // let the listener of that reload finish before the engine is initialized again
         Thread.sleep(1000L);
         engine.init();
         SecurityProvider provider = engine.getSecurityProvider();
         assertTrue(provider.getAuthenticationProvider().isVirtual(),
                    "a provider was created without a chain");

         reload(UNRELATED, "changed");
         Thread.sleep(1000L);
         assertSame(provider, engine.getSecurityProvider(),
                    "a reload re-initialized SecurityEngine although it was already " +
                       "initialized with security enabled");
      }
      finally {
         engine.newChain();
      }
   }

   private void reload(String name, String value) throws Exception {
      keyValueEngine.put(STORE, name, value);
      cluster.submit(STORE, new LoadKeyValueTask<String>(STORE, true)).get(10L, TimeUnit.SECONDS);
      waitFor(() -> value.equals(SreeEnv.getProperty(name)), "the reload of " + name);
   }

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
   private boolean authcChainExisted;
   private boolean authzChainExisted;

   private static final String STORE = "sreeProperties";
   private static final String UNRELATED = "bug76975.unrelated";
   private static final String AUTHC_CHAIN = "authc-chain.json";
   private static final String AUTHZ_CHAIN = "authz-chain.json";
}
