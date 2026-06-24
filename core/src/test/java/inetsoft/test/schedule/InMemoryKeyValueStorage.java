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
package inetsoft.test.schedule;

import inetsoft.storage.KeyValuePair;
import inetsoft.storage.KeyValueStorage;

import java.io.Serializable;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Stream;

/**
 * In-memory KeyValueStorage implementation for use in tests.
 * Extracted from ScheduleStatusDaoTest for reuse across scheduler test infrastructure.
 */
public final class InMemoryKeyValueStorage<T extends Serializable> implements KeyValueStorage<T> {

   @Override
   public boolean contains(String key) {
      return values.containsKey(key);
   }

   @Override
   public T get(String key) {
      return values.get(key);
   }

   @Override
   public Future<T> put(String key, T value) {
      if(nextPutFailure != null) {
         Exception failure = nextPutFailure;
         nextPutFailure = null;
         return new FailedFuture<>(failure);
      }

      values.put(key, value);
      return CompletableFuture.completedFuture(value);
   }

   @Override
   public Future<?> putAll(SortedMap<String, T> values) {
      this.values.putAll(values);
      return CompletableFuture.completedFuture(null);
   }

   @Override
   public Future<T> remove(String key) {
      if(nextRemoveFailure != null) {
         Exception failure = nextRemoveFailure;
         nextRemoveFailure = null;
         return new FailedFuture<>(failure);
      }

      T removed = values.remove(key);
      return CompletableFuture.completedFuture(removed);
   }

   @Override
   public Future<?> removeAll(Set<String> keys) {
      keys.forEach(values::remove);
      return CompletableFuture.completedFuture(null);
   }

   @Override
   public Future<T> rename(String oldKey, String newKey, T value) {
      T renamedValue = value != null ? value : values.get(oldKey);
      values.remove(oldKey);
      T previous = values.put(newKey, renamedValue);
      return CompletableFuture.completedFuture(previous);
   }

   @Override
   public Future<?> replaceAll(SortedMap<String, T> values) {
      this.values.clear();
      this.values.putAll(values);
      return CompletableFuture.completedFuture(null);
   }

   @Override
   public Future<?> deleteStore() {
      values.clear();
      closed = true;
      return CompletableFuture.completedFuture(null);
   }

   @Override
   public Stream<KeyValuePair<T>> stream() {
      return values.entrySet().stream()
         .map(entry -> new KeyValuePair<>(entry.getKey(), entry.getValue()));
   }

   @Override
   public Stream<String> keys() {
      return values.keySet().stream();
   }

   @Override
   public int size() {
      return values.size();
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
      return closed;
   }

   @Override
   public void close() throws Exception {
      if(nextCloseFailure != null) {
         Exception failure = nextCloseFailure;
         nextCloseFailure = null;
         throw failure;
      }

      closed = true;
   }

   public void failNextPut(Exception failure) {
      nextPutFailure = failure;
   }

   public void failNextRemove(Exception failure) {
      nextRemoveFailure = failure;
   }

   public void failOnClose(Exception failure) {
      nextCloseFailure = failure;
   }

   private final ConcurrentHashMap<String, T> values = new ConcurrentHashMap<>();
   private final List<Listener<T>> listeners = new CopyOnWriteArrayList<>();
   private volatile Exception nextPutFailure;
   private volatile Exception nextRemoveFailure;
   private volatile Exception nextCloseFailure;
   private volatile boolean closed;

   private static final class FailedFuture<T> implements Future<T> {
      private FailedFuture(Exception failure) {
         this.failure = failure;
      }

      @Override
      public boolean cancel(boolean mayInterruptIfRunning) {
         return false;
      }

      @Override
      public boolean isCancelled() {
         return false;
      }

      @Override
      public boolean isDone() {
         return true;
      }

      @Override
      public T get() throws InterruptedException, ExecutionException {
         if(failure instanceof InterruptedException interrupted) {
            throw interrupted;
         }

         if(failure instanceof ExecutionException execution) {
            throw execution;
         }

         throw new ExecutionException(failure);
      }

      @Override
      public T get(long timeout, TimeUnit unit)
         throws InterruptedException, ExecutionException, TimeoutException
      {
         if(failure instanceof InterruptedException interrupted) {
            throw interrupted;
         }

         if(failure instanceof TimeoutException timeoutFailure) {
            throw timeoutFailure;
         }

         if(failure instanceof ExecutionException execution) {
            throw execution;
         }

         throw new ExecutionException(failure);
      }

      private final Exception failure;
   }
}
