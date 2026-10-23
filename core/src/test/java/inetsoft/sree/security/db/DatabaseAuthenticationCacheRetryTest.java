/*
 * This file is part of StyleBI.
 * Copyright (C) 2025  InetSoft Technology
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

package inetsoft.sree.security.db;

import inetsoft.sree.security.AuthenticationChain;
import inetsoft.sree.security.AuthenticationProvider;
import inetsoft.test.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.*;

import static inetsoft.sree.security.db.DatabaseCacheTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #78779: a node whose first cache connect fails because the singleton host's chain does
 * not have the provider yet must retry within about a second, not after 10 s.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = BaseTestConfiguration.class,
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class DatabaseAuthenticationCacheRetryTest {
   @BeforeEach
   void setUp() throws Exception {
      support = new DatabaseCacheTestSupport();
      support.setUp("3600000");
   }

   @AfterEach
   void tearDown() {
      support.tearDown();
   }

   @Test
   void hostChainLagsAtFirstConnect_usersVisibleWithinAboutOneSecond() throws Exception {
      AuthenticationChain chain = support.initEngine(List.of(support.provider("base78779", ALL_USERS)));
      DatabaseAuthenticationProvider added = support.provider("added78779", ALL_USERS);

      // the saving node uses the provider before the host's chain has it: connect() throws on
      // the host and the client arms its backoff
      assertEquals(0, added.getUsers().length);
      assertEquals(2_000L, retryDelay(added), "first failure arms 1 s and doubles to 2 s");

      List<AuthenticationProvider> list = chain.getProviders();
      list.add(added);
      long start = System.currentTimeMillis();
      chain.setProviders(list);

      assertTrue(await(() -> Arrays.asList(added.getUsers()).contains(ALICE), 5_000),
                 "users never became visible");
      long elapsed = System.currentTimeMillis() - start;
      assertTrue(elapsed < 3_000L, "users visible only after " + elapsed + " ms");
      assertEquals(1_000L, retryDelay(added), "a successful connect resets the backoff");
   }

   @Test
   void consecutiveFailuresDoubleUpToFiveMinutes() throws Exception {
      support.initEngine(List.of(support.provider("base78779b", ALL_USERS)));
      DatabaseAuthenticationProvider missing = support.provider("missing78779", ALL_USERS);
      DatabaseAuthenticationCache cache = missing.getCache(false);
      Method initialize = DatabaseAuthenticationCache.class.getDeclaredMethod("initialize");
      initialize.setAccessible(true);
      Field nextRetry = DatabaseAuthenticationCache.class.getDeclaredField("nextRetryTime");
      nextRetry.setAccessible(true);
      List<Long> delays = new ArrayList<>();

      for(int i = 0; i < 12; i++) {
         long before = System.currentTimeMillis();
         assertFalse((Boolean) initialize.invoke(cache));
         long armed = (Long) nextRetry.get(cache) - before;
         delays.add(armed);
         // while armed, no further attempt is made
         assertFalse((Boolean) initialize.invoke(cache));
         nextRetry.set(cache, 0L);
      }

      long[] expected = { 1_000, 2_000, 4_000, 8_000, 16_000, 32_000, 64_000, 128_000, 256_000,
                          300_000, 300_000, 300_000 };

      for(int i = 0; i < expected.length; i++) {
         long d = delays.get(i);
         assertTrue(d >= expected[i] && d <= expected[i] + 500,
                    "attempt " + i + ": expected ~" + expected[i] + " ms, got " + delays);
      }
   }

   private static long retryDelay(DatabaseAuthenticationProvider p) throws Exception {
      Field field = DatabaseAuthenticationCache.class.getDeclaredField("currentRetryDelayMs");
      field.setAccessible(true);
      return (Long) field.get(p.getCache(false));
   }

   private DatabaseCacheTestSupport support;
}
