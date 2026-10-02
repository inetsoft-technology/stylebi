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
package inetsoft.sree;

import inetsoft.report.internal.license.LicenseManager;
import inetsoft.sree.internal.SUtil;
import inetsoft.sree.security.SecurityEngine;
import inetsoft.storage.InMemoryKeyValueStorage;
import inetsoft.storage.KeyValueStorage;
import inetsoft.test.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #77323: with {@code -Dsecurity.users.multitenant=true} and no stored value, the value the
 * EM displays ({@code SecurityConfigController.getMultiTenancy()} reads
 * {@code SreeEnv.getProperty("security.users.multiTenant", "false")}) and the value the server
 * enforces ({@link SUtil#isMultiTenant()}) must agree, using the real layered
 * {@link PropertiesEngine} over an in-memory storage rather than stubbed reads.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class MultiTenantSystemPropertyTest {
   @BeforeEach
   void setUp() throws Exception {
      oldSystemProperty = System.getProperty(KEY);
      System.setProperty(KEY, "true");
      engine = PropertiesEngine.getInstance();
      engine.clear();
      originalStorage = getStorage();
      storage = new InMemoryKeyValueStorage<>();
      setStorage(storage);
      engine.init();

      security = Mockito.mockStatic(SecurityEngine.class, Mockito.CALLS_REAL_METHODS);
      SecurityEngine engineMock = Mockito.mock(SecurityEngine.class);
      Mockito.when(engineMock.isSecurityEnabled()).thenReturn(true);
      security.when(SecurityEngine::getSecurity).thenReturn(engineMock);
      license = Mockito.mockStatic(LicenseManager.class, Mockito.CALLS_REAL_METHODS);
      license.when(LicenseManager::isEnterprise).thenReturn(true);
   }

   @AfterEach
   void tearDown() throws Exception {
      license.close();
      security.close();
      engine.clear();
      setStorage(originalStorage);

      if(oldSystemProperty == null) {
         System.clearProperty(KEY);
      }
      else {
         System.setProperty(KEY, oldSystemProperty);
      }

      engine.init();
   }

   @Test
   void systemPropertyNotStored_displayAndEnforcementAgree() {
      assertNull(storage.get(KEY), "precondition: nothing stored");
      assertEquals("true", SreeEnv.getProperty("security.users.multiTenant", "false"),
                   "EM display (get-multi-tenancy)");
      assertTrue(SUtil.isMultiTenant(), "enforcement must agree with the EM display");
   }

   @Test
   void reEnableWithSystemProperty_isCorrectNoOp() throws Exception {
      // what POST /api/em/security/set-multi-tenancy {enable:true} does: the effective value is
      // already "true", so nothing is stored, and that is now correct because enforcement is on
      SUtil.setMultiTenant(true);

      assertNull(storage.get(KEY), "the same-value write is skipped");
      assertTrue(SUtil.isMultiTenant());
      assertEquals("true", SreeEnv.getProperty("security.users.multiTenant", "false"));
   }

   @Test
   void storedFalseWins_thenRemovedByPeer_fallsBackToSystemProperty() throws Exception {
      // disabled earlier through the EM, which stored "false"
      engine.clear();
      storage.remotePut(KEY, "false", false);
      engine.init();

      assertEquals("false", storage.get(KEY));
      assertFalse(SUtil.isMultiTenant(), "a stored false overrides -D true");
      assertEquals("false", SreeEnv.getProperty("security.users.multiTenant", "false"));

      // another node deletes the stored value; this node has not reloaded yet
      storage.remoteRemove(KEY, false);
      assertTrue(SUtil.isMultiTenant(), "a removed key falls back to -D immediately");
   }

   @SuppressWarnings("unchecked")
   private KeyValueStorage<String> getStorage() throws Exception {
      return (KeyValueStorage<String>) storageField().get(engine);
   }

   private void setStorage(KeyValueStorage<String> value) throws Exception {
      storageField().set(engine, value);
   }

   private static Field storageField() throws Exception {
      Field field = PropertiesEngine.class.getDeclaredField("kvStorage");
      field.setAccessible(true);
      return field;
   }

   private static final String KEY = "security.users.multitenant";

   private PropertiesEngine engine;
   private KeyValueStorage<String> originalStorage;
   private InMemoryKeyValueStorage<String> storage;
   private MockedStatic<SecurityEngine> security;
   private MockedStatic<LicenseManager> license;
   private String oldSystemProperty;
}
