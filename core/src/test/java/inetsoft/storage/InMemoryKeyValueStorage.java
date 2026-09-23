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
package inetsoft.storage;

import java.io.Serializable;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Stream;

/**
 * Simple in-memory {@link KeyValueStorage} for unit tests that need to simulate writes made by
 * other cluster nodes. Writes made through the {@code KeyValueStorage} API (i.e. by the code under
 * test) do not fire events. Writes made through the {@code remote*} methods simulate another node
 * and, when asked to, fire the event synchronously to the listeners registered at that moment,
 * like the real storage does with the listener set it reads at dispatch time.
 *
 * <p>It lives in the {@code inetsoft.storage} package because the {@link KeyValueStorage.Event}
 * constructor is package-private.</p>
 */
public class InMemoryKeyValueStorage<T extends Serializable> implements KeyValueStorage<T> {
   /**
    * Sets an action that runs once, in the middle of the next {@link #putAll} or
    * {@link #removeAll} call, after the values have been written.
    */
   public void runDuringNextWrite(Runnable action) {
      duringNextWrite = action;
   }

   /**
    * When enabled, writes made through the {@code KeyValueStorage} API also fire events, like the
    * real storage does for a node's own writes. The events are delivered asynchronously, on
    * another thread and after a short delay, to the listeners registered when they are
    * dispatched, which is how {@code LocalKeyValueStorage} delivers the Ignite cache events.
    */
   public void setAsyncLocalEvents(boolean asyncLocalEvents) {
      this.asyncLocalEvents = asyncLocalEvents;
   }

   public void remotePut(String key, T value, boolean fireEvent) {
      T oldValue = map.put(key, value);

      if(fireEvent) {
         Event<T> event = new Event<>(this, key, "test", oldValue, value);

         for(Listener<T> listener : new ArrayList<>(listeners)) {
            if(oldValue == null) {
               listener.entryAdded(event);
            }
            else {
               listener.entryUpdated(event);
            }
         }
      }
   }

   public void remoteRemove(String key, boolean fireEvent) {
      T oldValue = map.remove(key);

      if(fireEvent && oldValue != null) {
         Event<T> event = new Event<>(this, key, "test", oldValue, null);

         for(Listener<T> listener : new ArrayList<>(listeners)) {
            listener.entryRemoved(event);
         }
      }
   }

   public boolean hasListener(Listener<T> listener) {
      return listeners.contains(listener);
   }

   @Override
   public boolean contains(String key) {
      return map.containsKey(key);
   }

   @Override
   public T get(String key) {
      return map.get(key);
   }

   @Override
   public Future<T> put(String key, T value) {
      return CompletableFuture.completedFuture(map.put(key, value));
   }

   @Override
   public Future<?> putAll(SortedMap<String, T> values) {
      for(Map.Entry<String, T> e : values.entrySet()) {
         T oldValue = map.put(e.getKey(), e.getValue());
         fireLocalEvent(e.getKey(), oldValue, e.getValue());
      }

      afterWrite();
      return CompletableFuture.completedFuture(null);
   }

   @Override
   public Future<T> remove(String key) {
      return CompletableFuture.completedFuture(map.remove(key));
   }

   @Override
   public Future<?> removeAll(Set<String> keys) {
      for(String key : keys) {
         T oldValue = map.remove(key);

         if(oldValue != null) {
            fireLocalEvent(key, oldValue, null);
         }
      }

      afterWrite();
      return CompletableFuture.completedFuture(null);
   }

   @Override
   public Future<T> rename(String oldKey, String newKey, T value) {
      T old = map.remove(oldKey);
      map.put(newKey, value == null ? old : value);
      return CompletableFuture.completedFuture(old);
   }

   @Override
   public Future<?> replaceAll(SortedMap<String, T> values) {
      map.clear();
      map.putAll(values);
      return CompletableFuture.completedFuture(null);
   }

   @Override
   public Future<?> deleteStore() {
      map.clear();
      return CompletableFuture.completedFuture(null);
   }

   @Override
   public Stream<KeyValuePair<T>> stream() {
      return new ArrayList<>(map.entrySet()).stream()
         .map(e -> new KeyValuePair<>(e.getKey(), e.getValue()));
   }

   @Override
   public Stream<String> keys() {
      return new ArrayList<>(map.keySet()).stream();
   }

   @Override
   public int size() {
      return map.size();
   }

   @Override
   public void addListener(Listener<T> listener) {
      listeners.add(listener);
   }

   @Override
   public void removeListener(Listener<T> listener) {
      listeners.remove(listener);
   }

   @Override
   public boolean isClosed() {
      return false;
   }

   @Override
   public void close() {
      eventExecutor.shutdownNow();
   }

   private void fireLocalEvent(String key, T oldValue, T newValue) {
      if(!asyncLocalEvents) {
         return;
      }

      Event<T> event = new Event<>(this, key, "test", oldValue, newValue);
      eventExecutor.schedule(() -> {
         for(Listener<T> listener : new ArrayList<>(listeners)) {
            if(newValue == null) {
               listener.entryRemoved(event);
            }
            else if(oldValue == null) {
               listener.entryAdded(event);
            }
            else {
               listener.entryUpdated(event);
            }
         }
      }, 50L, TimeUnit.MILLISECONDS);
   }

   private void afterWrite() {
      Runnable action = duringNextWrite;
      duringNextWrite = null;

      if(action != null) {
         action.run();
      }
   }

   private final Map<String, T> map = new ConcurrentSkipListMap<>();
   private final Set<Listener<T>> listeners = new CopyOnWriteArraySet<>();
   private volatile Runnable duringNextWrite;
   private volatile boolean asyncLocalEvents;
   private final ScheduledExecutorService eventExecutor =
      Executors.newSingleThreadScheduledExecutor(r -> {
         Thread thread = new Thread(r, "InMemoryKeyValueStorage-events");
         thread.setDaemon(true);
         return thread;
      });
}
