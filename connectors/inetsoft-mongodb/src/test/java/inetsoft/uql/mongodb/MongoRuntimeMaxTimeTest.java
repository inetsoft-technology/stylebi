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

import com.mongodb.client.AggregateIterable;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import org.bson.Document;
import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeUnit;

import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Verifies that the aggregate <tt>maxTimeMS</tt> option is accepted whatever numeric type
 * <tt>Document.parse</tt> produces for it. Only the MongoDB driver objects are mocked.
 */
class MongoRuntimeMaxTimeTest {
   @Test
   void plainIntegerMaxTime() {
      assertMaxTime("{aggregate:'t', pipeline:[], maxTimeMS: 5000}", 5000L);
   }

   @Test
   void doubleMaxTime() {
      assertMaxTime("{aggregate:'t', pipeline:[], maxTimeMS: 5000.0}", 5000L);
   }

   @Test
   void numberLongMaxTime() {
      assertMaxTime("{aggregate:'t', pipeline:[], maxTimeMS: NumberLong(5000)}", 5000L);
   }

   @Test
   void beyondIntRangeMaxTime() {
      assertMaxTime("{aggregate:'t', pipeline:[], maxTimeMS: 5000000000}", 5000000000L);
   }

   @Test
   void absentMaxTimeIsNotApplied() {
      AggregateIterable<Document> it = run("{aggregate:'t', pipeline:[]}");
      verify(it, never()).maxTime(anyLong(), any());
   }

   private void assertMaxTime(String query, long expected) {
      AggregateIterable<Document> it = run(query);
      verify(it).maxTime(expected, TimeUnit.MILLISECONDS);
   }

   @SuppressWarnings("unchecked")
   private AggregateIterable<Document> run(String query) {
      MongoDatabase db = mock(MongoDatabase.class);
      MongoCollection<Document> collection = mock(MongoCollection.class);
      AggregateIterable<Document> it = mock(AggregateIterable.class);
      when(db.getCollection(anyString())).thenReturn(collection);
      when(collection.aggregate(anyList())).thenReturn(it);
      new MongoRuntime().getQueryResults(Document.parse(query), db);
      return it;
   }
}
