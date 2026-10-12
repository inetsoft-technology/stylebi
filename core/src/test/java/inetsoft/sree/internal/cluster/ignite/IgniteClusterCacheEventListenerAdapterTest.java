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
package inetsoft.sree.internal.cluster.ignite;

import inetsoft.sree.internal.cluster.EntryEvent;
import inetsoft.sree.internal.cluster.MapChangeListener;
import org.apache.ignite.binary.BinaryObject;
import org.apache.ignite.events.CacheEvent;
import org.apache.ignite.events.EventType;
import org.junit.jupiter.api.*;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Bug #77886: Ignite records EVT_CACHE_OBJECT_EXPIRED with keepBinary=true
 * (GridCacheMapEntry.onExpired in Ignite 2.18), so the old value of an expired entry arrives as a
 * {@link BinaryObject}. The adapter passed it on as is, and IgniteSessionRepository.entryExpired()
 * failed its MapSession cast inside the listener executor, where the exception was lost. A session
 * that ended by its Ignite TTL was therefore never logged out.
 */
@Tag("core")
class IgniteClusterCacheEventListenerAdapterTest {
   @BeforeEach
   void setUp() {
      executor = Executors.newSingleThreadExecutor();
      adapter = new IgniteCluster.CacheEventListenerAdapter<>(CACHE, executor);
   }

   @AfterEach
   void tearDown() {
      executor.shutdownNow();
   }

   @Test
   void expiredEvent_passesDeserializedOldValueToListener() throws Exception {
      Object session = new Object();
      BinaryObject binary = mock(BinaryObject.class);
      when(binary.deserialize()).thenReturn(session);
      AtomicReference<Object> received = new AtomicReference<>();
      adapter.addListener(new Recorder() {
         @Override
         public void entryExpired(EntryEvent<String, Object> event) {
            received.set(event.getOldValue());
         }
      });

      adapter.apply(event(EventType.EVT_CACHE_OBJECT_EXPIRED, binary, null));
      drain();

      assertSame(session, received.get(),
                 "an expired entry's listener must get the cached object, not its binary form");
   }

   @Test
   void removedEvent_passesOldValueUnchanged() throws Exception {
      Object session = new Object();
      AtomicReference<Object> received = new AtomicReference<>();
      adapter.addListener(new Recorder() {
         @Override
         public void entryRemoved(EntryEvent<String, Object> event) {
            received.set(event.getOldValue());
         }
      });

      adapter.apply(event(EventType.EVT_CACHE_OBJECT_REMOVED, session, null));
      drain();

      assertSame(session, received.get());
   }

   private static CacheEvent event(int type, Object oldValue, Object newValue) {
      CacheEvent event = mock(CacheEvent.class);
      when(event.cacheName()).thenReturn(CACHE);
      when(event.type()).thenReturn(type);
      when(event.key()).thenReturn("key");
      when(event.oldValue()).thenReturn(oldValue);
      when(event.newValue()).thenReturn(newValue);
      return event;
   }

   /** Waits until every notification submitted so far has run. */
   private void drain() throws Exception {
      executor.submit(() -> { }).get(10, TimeUnit.SECONDS);
   }

   private abstract static class Recorder implements MapChangeListener<String, Object> {
      @Override
      public void entryAdded(EntryEvent<String, Object> event) {
      }

      @Override
      public void entryUpdated(EntryEvent<String, Object> event) {
      }

      @Override
      public void entryRemoved(EntryEvent<String, Object> event) {
      }
   }

   private static final String CACHE = "spring.session.sessions";
   private ExecutorService executor;
   private IgniteCluster.CacheEventListenerAdapter<String, Object> adapter;
}
