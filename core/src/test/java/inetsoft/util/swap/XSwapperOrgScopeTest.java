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
package inetsoft.util.swap;

import inetsoft.sree.SreeEnv;
import inetsoft.sree.security.IdentityID;
import inetsoft.sree.security.OrganizationContextHolder;
import inetsoft.test.*;
import inetsoft.uql.XPrincipal;
import inetsoft.util.ThreadContext;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #77623: the swapper settings are JVM-wide, so an <code>inetsoft.org.&lt;org&gt;.</code>
 * override must not change what a thread in that organization reads.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class XSwapperOrgScopeTest {
   @BeforeEach
   void setUp() {
      clearThread();

      for(Map.Entry<String, String> e : GLOBAL.entrySet()) {
         SreeEnv.setProperty(e.getKey(), e.getValue());
         SreeEnv.setProperty(ORG_PREFIX + e.getKey(), ORG_VALUE);
      }

      SreeEnv.setProperty(CONTROL, "global");
      SreeEnv.setProperty(ORG_PREFIX + CONTROL, ORG_VALUE);
   }

   @AfterEach
   void tearDown() {
      clearThread();

      for(String name : GLOBAL.keySet()) {
         SreeEnv.remove(name);
         SreeEnv.remove(ORG_PREFIX + name);
      }

      SreeEnv.remove(CONTROL);
      SreeEnv.remove(ORG_PREFIX + CONTROL);
   }

   @Test
   void orgThread_readsGlobalSwapperSettings() {
      inOrg("orga");
      // the override is in effect for a setting that is not JVM-wide
      assertEquals(ORG_VALUE, SreeEnv.getProperty(CONTROL));

      for(Map.Entry<String, String> e : GLOBAL.entrySet()) {
         assertEquals(e.getValue(), SreeEnv.getProperty(e.getKey()), e.getKey());
         assertEquals(e.getValue(), SreeEnv.getProperty(e.getKey(), "def"), e.getKey());
      }
   }

   @Test
   void orgThread_swapperAccessorsUseGlobalSettings() {
      XSwapper swapper = XSwapper.getSwapper();
      inOrg("orga");

      assertEquals(25000L, swapper.getMaxCriticalWait());
      assertEquals(15000L, swapper.getGCMinInterval());
      assertEquals(600000L, XSwapper.getPeriodicGCInterval());
   }

   private static void inOrg(String orgID) {
      clearThread();
      ThreadContext.setContextPrincipal(new XPrincipal(new IdentityID("user", orgID)));
   }

   private static void clearThread() {
      ThreadContext.setContextPrincipal(null);
      ThreadContext.setPrincipal(null);
      OrganizationContextHolder.clear();
   }

   private static final String ORG_PREFIX = "inetsoft.org.orga.";
   private static final String ORG_VALUE = "1";
   private static final String CONTROL = "test77623.key";
   private static final Map<String, String> GLOBAL = Map.of(
      "swapper.critical.max.wait", "25000",
      "swapper.gc.min.interval", "15000",
      "swapper.idle.gc.interval", "600000",
      "swapper.count", "2",
      "swapper.free.ratio", "0.5,0.3",
      "swappable.alive.period", "1500",
      "swapper.scalingMetric.excludeEden", "true",
      "ignore.swapper.memory.state", "false");
}
