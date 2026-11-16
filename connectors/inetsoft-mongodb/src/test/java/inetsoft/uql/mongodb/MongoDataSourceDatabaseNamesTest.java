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

import com.mongodb.MongoClient;
import com.mongodb.client.MongoIterable;
import inetsoft.util.ConfigurationContext;
import inetsoft.util.credential.*;
import org.junit.jupiter.api.*;
import org.springframework.context.ApplicationContext;

import java.util.Iterator;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.mockito.Mockito.*;

/**
 * Bug #80411: getDatabaseNames creates its own, unpooled MongoClient and must close it
 * on both the success and the failure path.
 */
class MongoDataSourceDatabaseNamesTest {
   @BeforeAll
   static void setUpContext() {
      CredentialService credentialService = mock(CredentialService.class);
      when(credentialService.createCredential(CredentialType.PASSWORD))
         .thenReturn(mock(LocalPasswordCredential.class));
      when(credentialService.createCredential(CredentialType.PASSWORD, false))
         .thenReturn(mock(LocalPasswordCredential.class));
      ApplicationContext context = mock(ApplicationContext.class);
      when(context.getBean(CredentialService.class)).thenReturn(credentialService);
      ConfigurationContext.getContext().setApplicationContext(context);
   }

   @AfterAll
   static void resetContext() {
      ConfigurationContext.getContext().setApplicationContext(null);
   }

   @SuppressWarnings("unchecked")
   @Test
   void closesClientAfterListingNames() {
      MongoClient client = mock(MongoClient.class);
      MongoIterable<String> iterable = mock(MongoIterable.class);
      Iterator<String> iterator = List.of("a", "b").iterator();
      when(iterable.iterator()).thenAnswer(inv -> mockCursor(iterator));
      when(client.listDatabaseNames()).thenReturn(iterable);

      assertArrayEquals(new String[] { "a", "b" }, source(client).getDatabaseNames());
      verify(client).close();
   }

   @Test
   void closesClientWhenListingFails() {
      MongoClient client = mock(MongoClient.class);
      when(client.listDatabaseNames()).thenThrow(new IllegalStateException("boom"));

      assertArrayEquals(new String[0], source(client).getDatabaseNames());
      verify(client).close();
   }

   @SuppressWarnings("unchecked")
   private static com.mongodb.client.MongoCursor<String> mockCursor(Iterator<String> it) {
      com.mongodb.client.MongoCursor<String> cursor = mock(com.mongodb.client.MongoCursor.class);
      when(cursor.hasNext()).thenAnswer(inv -> it.hasNext());
      when(cursor.next()).thenAnswer(inv -> it.next());
      return cursor;
   }

   private static MongoDataSource source(MongoClient client) {
      MongoDataSource ds = new MongoDataSource() {
         @Override
         public MongoClient getMongoClient() {
            return client;
         }
      };
      ds.setHost("localhost");
      return ds;
   }
}
