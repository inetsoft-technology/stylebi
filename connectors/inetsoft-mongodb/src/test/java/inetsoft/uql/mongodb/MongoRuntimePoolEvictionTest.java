/*
 * This file is part of StyleBI.
 * Copyright (C) 2024  InetSoft Technology
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
package inetsoft.uql.mongodb;

import inetsoft.util.ConfigurationContext;
import inetsoft.util.ResourceCache;
import inetsoft.util.credential.*;
import org.junit.jupiter.api.*;
import org.mockito.ArgumentMatchers;
import org.springframework.context.ApplicationContext;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Verifies that clients evicted from the <tt>MongoRuntime</tt> pool are closed.
 */
class MongoRuntimePoolEvictionTest {
   @BeforeAll
   static void setupContext() {
      CredentialService credentialService = mock(CredentialService.class);
      when(credentialService.createCredential(ArgumentMatchers.any(CredentialType.class)))
         .thenReturn(mock(LocalPasswordCredential.class));
      when(credentialService.createCredential(
         ArgumentMatchers.any(CredentialType.class), ArgumentMatchers.anyBoolean()))
         .thenReturn(mock(LocalPasswordCredential.class));
      ApplicationContext context = mock(ApplicationContext.class);
      when(context.getBean(CredentialService.class)).thenReturn(credentialService);
      ConfigurationContext.getContext().setApplicationContext(context);
   }

   @AfterAll
   static void resetContext() {
      ConfigurationContext.getContext().setApplicationContext(null);
   }

   @Test
   @SuppressWarnings("unchecked")
   void evictedClientIsClosed() throws Exception {
      Field field = MongoRuntime.class.getDeclaredField("pool");
      field.setAccessible(true);
      ResourceCache<MongoDataSource, com.mongodb.MongoClient> pool =
         (ResourceCache<MongoDataSource, com.mongodb.MongoClient>) field.get(null);
      pool.clear();
      List<com.mongodb.MongoClient> clients = new ArrayList<>();

      try {
         // the pool holds at most 100 clients; fetching more evicts the least recently used
         for(int i = 0; i < 101; i++) {
            MongoDataSource ds = new MongoDataSource();
            ds.setName("evict-ds" + i);
            ds.setHost("localhost");
            ds.setPort(27000 + i);
            clients.add(pool.get(ds));
            Thread.sleep(2);
         }

         assertTrue(pool.size() < 101, "pool should have evicted at least one client");
         assertTrue(isClosed(clients.get(0)), "evicted client should be closed");
         assertFalse(isClosed(clients.get(100)), "client still in the pool must stay open");
      }
      finally {
         pool.clear();
         clients.forEach(com.mongodb.MongoClient::close);
      }
   }

   private static boolean isClosed(com.mongodb.MongoClient client) throws Exception {
      Method getCluster = null;

      for(Class<?> c = client.getClass(); c != null && getCluster == null; c = c.getSuperclass()) {
         try {
            getCluster = c.getDeclaredMethod("getCluster");
         }
         catch(NoSuchMethodException ignore) {
         }
      }

      assertNotNull(getCluster, "driver has no getCluster()");
      getCluster.setAccessible(true);
      Object cluster = getCluster.invoke(client);
      Method isClosed = cluster.getClass().getMethod("isClosed");
      isClosed.setAccessible(true);
      return (Boolean) isClosed.invoke(cluster);
   }
}
