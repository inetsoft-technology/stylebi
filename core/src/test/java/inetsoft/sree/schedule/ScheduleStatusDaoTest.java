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

package inetsoft.sree.schedule;

/*
 * Intent vs implementation suspects
 *
 * Issue #74697
 * [Suspect 1] task-name encoding should avoid reserved-keyword conflicts without aliasing
 *             actual: "__foo__" and "TASK^^foo" map to the same internal key and overwrite each other
 * [Suspect 2] "____" (four underscores) satisfies ^__.*__$ and encodes to "TASK^^" (empty suffix via substring(2,2)),
 *             colliding with any literal task named "TASK^^"
 * [Suspect 3] null task name bypasses encodeTaskName and reaches storage with a null key; no null-guard exists in the DAO
 *
 * ScheduleStatusDao state transitions
 *  [Op: put→get]           setStatus(task, ...) on empty storage                  -> getStatus(task) returns stored fields
 *  [Op: remove]            setStatus(task, ...) + clearStatus(task)               -> getStatus(task) returns null
 *  [Op: update]            setStatus(task, ..., runNow=false) with existing entry  -> lastScheduledStartTime advances to new startTime
 *  [Key: reserved]         taskName matches reserved pattern                      -> DAO uses encoded key consistently
 *  [Failure: put-timeout]  storage.put(...).get(...) fails                        -> method returns computed status without persisting
 *  [Failure: remove-timeout] storage.remove(...).get(...) fails                   -> method swallows failure and keeps existing entry
 *  [Carry: runNow+history] runNow=true and previous status exists                 -> lastScheduledStartTime carries over
 *  [Carry: no-history]     runNow=true and previous status absent                 -> lastScheduledStartTime uses current start time
 *  [Lifecycle: close]      close() invoked                                        -> storage.close() invoked
 *  [Lifecycle: close-error] storage.close() throws                                -> DAO propagates the exception
 */

import inetsoft.sree.internal.cluster.Cluster;
import inetsoft.storage.KeyValuePair;
import inetsoft.storage.KeyValueStorage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.slf4j.LoggerFactory;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;
import java.util.SortedMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

@Tag("core")
public class ScheduleStatusDaoTest {
   private final Logger scheduleStatusDaoLogger =
      (Logger) LoggerFactory.getLogger(ScheduleStatusDao.class);
   private final Level originalLogLevel = scheduleStatusDaoLogger.getLevel();

   @AfterEach
   void restoreLoggerLevel() {
      scheduleStatusDaoLogger.setLevel(originalLogLevel);
   }

   // [Op: put→get] setStatus on empty storage persists all status fields
   // Pre: storage has no entry for task; Post: getStatus(task) returns the new status
   @Test
   void setStatus_roundTripPersistsStatus() {
      InMemoryKeyValueStorage<ScheduleStatusDao.Status> storage = new InMemoryKeyValueStorage<>();
      ScheduleStatusDao dao = newDao(storage);

      ScheduleStatusDao.Status saved =
         dao.setStatus("nightly-task", Scheduler.Status.FINISHED, 100L, 200L, "ok", false);

      assertStatus(saved, Scheduler.Status.FINISHED, 100L, 200L, "ok", 100L);
      assertStatus(dao.getStatus("nightly-task"), Scheduler.Status.FINISHED, 100L, 200L, "ok", 100L);
   }

   // [Op: remove] clearStatus removes the stored entry for the task
   // Pre: status exists; Op: clearStatus(task); Post: getStatus(task) == null
   @Test
   void clearStatus_existingTaskRemovesEntry() {
      InMemoryKeyValueStorage<ScheduleStatusDao.Status> storage = new InMemoryKeyValueStorage<>();
      ScheduleStatusDao dao = newDao(storage);
      dao.setStatus("nightly-task", Scheduler.Status.STARTED, 100L, 0L, null, false);

      dao.clearStatus("nightly-task");

      assertNull(dao.getStatus("nightly-task"));
   }

   // [Key: reserved] reserved-looking task names are encoded consistently for put/get/remove
   // Pre: task name matches reserved pattern; Post: only the encoded key is used internally
   @Test
   void reservedTaskName_isEncodedConsistentlyAcrossOperations() {
      InMemoryKeyValueStorage<ScheduleStatusDao.Status> storage = new InMemoryKeyValueStorage<>();
      ScheduleStatusDao dao = newDao(storage);

      dao.setStatus("__nightly__", Scheduler.Status.FAILED, 10L, 20L, "boom", false);

      assertFalse(storage.contains("__nightly__"));
      assertTrue(storage.contains("TASK^^nightly"));
      assertStatus(dao.getStatus("__nightly__"), Scheduler.Status.FAILED, 10L, 20L, "boom", 10L);

      dao.clearStatus("__nightly__");

      assertFalse(storage.contains("TASK^^nightly"));
      assertNull(dao.getStatus("__nightly__"));
   }

   // [Carry: runNow+history] runNow executions preserve the last scheduled start time from history
   // Pre: previous status exists; Op: setStatus(..., runNow=true); Post: scheduled time stays unchanged
   @Test
   void setStatus_runNowWithExistingStatus_preservesLastScheduledStartTime() {
      InMemoryKeyValueStorage<ScheduleStatusDao.Status> storage = new InMemoryKeyValueStorage<>();
      ScheduleStatusDao dao = newDao(storage);
      dao.setStatus("nightly-task", Scheduler.Status.STARTED, 111L, 0L, null, false);

      ScheduleStatusDao.Status updated =
         dao.setStatus("nightly-task", Scheduler.Status.FINISHED, 222L, 333L, null, true);

      assertStatus(updated, Scheduler.Status.FINISHED, 222L, 333L, null, 111L);
      assertStatus(dao.getStatus("nightly-task"), Scheduler.Status.FINISHED, 222L, 333L, null, 111L);
   }

   // [Carry: no-history] runNow falls back to current start time when no prior status exists
   // Pre: previous status missing; Op: setStatus(..., runNow=true); Post: scheduled time == current start time
   @Test
   void setStatus_runNowWithoutExistingStatus_usesCurrentStartTime() {
      InMemoryKeyValueStorage<ScheduleStatusDao.Status> storage = new InMemoryKeyValueStorage<>();
      ScheduleStatusDao dao = newDao(storage);

      ScheduleStatusDao.Status saved =
         dao.setStatus("adhoc-task", Scheduler.Status.STARTED, 456L, 0L, null, true);

      assertStatus(saved, Scheduler.Status.STARTED, 456L, 0L, null, 456L);
      assertStatus(dao.getStatus("adhoc-task"), Scheduler.Status.STARTED, 456L, 0L, null, 456L);
   }

   // [Failure: put-timeout] storage write failures are swallowed and the computed status is still returned
   // Pre: put future fails; Post: returned status is populated but nothing is persisted
   @Test
   void setStatus_whenPutTimesOut_returnsComputedStatusWithoutPersisting() {
      scheduleStatusDaoLogger.setLevel(Level.OFF);
      InMemoryKeyValueStorage<ScheduleStatusDao.Status> storage = new InMemoryKeyValueStorage<>();
      storage.failNextPut(new TimeoutException("simulated timeout"));
      ScheduleStatusDao dao = newDao(storage);

      ScheduleStatusDao.Status returned =
         dao.setStatus("nightly-task", Scheduler.Status.FAILED, 10L, 20L, "boom", false);

      assertStatus(returned, Scheduler.Status.FAILED, 10L, 20L, "boom", 10L);
      assertNull(dao.getStatus("nightly-task"));
   }

   // [Failure: remove-timeout] storage remove failures are swallowed and existing data remains
   // Pre: entry exists and remove future fails; Post: DAO does not throw and entry is still readable
   @Test
   void clearStatus_whenRemoveFails_swallowsFailureAndKeepsExistingEntry() {
      scheduleStatusDaoLogger.setLevel(Level.OFF);
      InMemoryKeyValueStorage<ScheduleStatusDao.Status> storage = new InMemoryKeyValueStorage<>();
      ScheduleStatusDao dao = newDao(storage);
      dao.setStatus("nightly-task", Scheduler.Status.STARTED, 100L, 0L, null, false);
      storage.failNextRemove(new TimeoutException("simulated timeout"));

      assertDoesNotThrow(() -> dao.clearStatus("nightly-task"));
      assertStatus(dao.getStatus("nightly-task"), Scheduler.Status.STARTED, 100L, 0L, null, 100L);
   }

   // [Lifecycle: close] close delegates to the underlying storage
   // Pre: open DAO; Op: close(); Post: storage reports closed
   @Test
   void close_closesUnderlyingStorage() throws Exception {
      InMemoryKeyValueStorage<ScheduleStatusDao.Status> storage = new InMemoryKeyValueStorage<>();
      ScheduleStatusDao dao = newDao(storage);

      dao.close();

      assertTrue(storage.isClosed());
   }

   // [Op: update] normal re-schedule (runNow=false, existing entry) advances lastScheduledStartTime to new startTime
   // Pre: status exists from a prior run; Op: setStatus(..., runNow=false); Post: lastScheduledStartTime == new startTime
   @Test
   void setStatus_normalScheduledRun_withExistingStatus_updatesLastScheduledStartTime() {
      InMemoryKeyValueStorage<ScheduleStatusDao.Status> storage = new InMemoryKeyValueStorage<>();
      ScheduleStatusDao dao = newDao(storage);
      dao.setStatus("nightly-task", Scheduler.Status.FINISHED, 100L, 150L, null, false);

      ScheduleStatusDao.Status updated =
         dao.setStatus("nightly-task", Scheduler.Status.FINISHED, 200L, 300L, null, false);

      assertStatus(updated, Scheduler.Status.FINISHED, 200L, 300L, null, 200L);
      assertStatus(dao.getStatus("nightly-task"), Scheduler.Status.FINISHED, 200L, 300L, null, 200L);
   }

   // [Lifecycle: close-error] close() propagates exceptions thrown by the underlying storage
   // Pre: storage.close() throws; Post: DAO.close() re-throws the same exception
   @Test
   void close_whenStorageThrows_propagatesException() {
      InMemoryKeyValueStorage<ScheduleStatusDao.Status> storage = new InMemoryKeyValueStorage<>();
      storage.failOnClose(new RuntimeException("simulated close failure"));
      ScheduleStatusDao dao = newDao(storage);

      assertThrows(Exception.class, dao::close);
   }

   // Issue #74697
   // [Suspect 1] reserved-name encoding aliases a valid literal task name to the same key
   // Pre: "__foo__" and "TASK^^foo" are both valid names; Post: they should not overwrite each other
   @Disabled("Bug: reserved-name encoding aliases different valid task names to the same storage key")
   @Test
   void setStatus_reservedAndLiteralEncodedNames_shouldNotCollide() {
      InMemoryKeyValueStorage<ScheduleStatusDao.Status> storage = new InMemoryKeyValueStorage<>();
      ScheduleStatusDao dao = newDao(storage);

      dao.setStatus("__foo__", Scheduler.Status.STARTED, 10L, 0L, null, false);
      dao.setStatus("TASK^^foo", Scheduler.Status.FINISHED, 20L, 30L, null, false);

      assertStatus(dao.getStatus("__foo__"), Scheduler.Status.STARTED, 10L, 0L, null, 10L);
      assertStatus(dao.getStatus("TASK^^foo"), Scheduler.Status.FINISHED, 20L, 30L, null, 20L);
   }

   // Issue #74697
   // [Suspect 2] "____" satisfies ^__.*__$ and encodes to "TASK^^" (empty suffix via substring(2,2)),
   // colliding with a literal task named "TASK^^"; the two entries silently overwrite each other
   @Disabled("Bug: '____' encodes to 'TASK^^' (empty suffix) because substring(2, length-2) == \"\"; " +
             "storing '____' and 'TASK^^' targets the same key and the second write silently overwrites the first")
   @Test
   void setStatus_fourUnderscoreTaskName_shouldNotCollideWithEmptySuffixKey() {
      InMemoryKeyValueStorage<ScheduleStatusDao.Status> storage = new InMemoryKeyValueStorage<>();
      ScheduleStatusDao dao = newDao(storage);

      dao.setStatus("____", Scheduler.Status.STARTED, 10L, 0L, null, false);
      dao.setStatus("TASK^^", Scheduler.Status.FINISHED, 20L, 30L, null, false);

      assertStatus(dao.getStatus("____"), Scheduler.Status.STARTED, 10L, 0L, null, 10L);
      assertStatus(dao.getStatus("TASK^^"), Scheduler.Status.FINISHED, 20L, 30L, null, 20L);
   }

   // Issue #74697
   // [Suspect 3] null task name bypasses encodeTaskName unchanged and reaches storage with a null key
   // Pre: null taskName; Op: any DAO operation; Post: SHOULD throw, but currently stores under null key
   @Disabled("Bug: encodeTaskName(null) returns null; storage operations then use a null key whose behaviour " +
             "is backend-dependent (MapDB/Ignite may reject it at runtime). The DAO should guard null names explicitly.")
   @Test
   void setStatus_nullTaskName_shouldRejectWithException() {
      InMemoryKeyValueStorage<ScheduleStatusDao.Status> storage = new InMemoryKeyValueStorage<>();
      ScheduleStatusDao dao = newDao(storage);

      assertThrows(IllegalArgumentException.class,
         () -> dao.setStatus(null, Scheduler.Status.STARTED, 1L, 0L, null, false));
   }

   private static ScheduleStatusDao newDao(InMemoryKeyValueStorage<ScheduleStatusDao.Status> storage) {
      Cluster cluster = Mockito.mock(Cluster.class);

      try(MockedStatic<KeyValueStorage> mocked = Mockito.mockStatic(KeyValueStorage.class)) {
         mocked.when(() -> KeyValueStorage.newInstance("scheduleStatus", cluster))
            .thenAnswer(invocation -> storage);
         return new ScheduleStatusDao(cluster);
      }
   }

   private static void assertStatus(ScheduleStatusDao.Status actual, Scheduler.Status status,
                                    long startTime, long endTime, String error,
                                    long scheduledStartTime)
   {
      assertNotNull(actual);
      assertEquals(status, actual.getStatus());
      assertEquals(startTime, actual.getStartTime());
      assertEquals(endTime, actual.getEndTime());
      assertEquals(error, actual.getError());
      assertEquals(scheduledStartTime, actual.getLastScheduledStartTime());
   }

   private static final class InMemoryKeyValueStorage<T extends Serializable>
      implements KeyValueStorage<T>
   {
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
         T renamedValue = value != null ? value : values.remove(oldKey);
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

      void failNextPut(Exception failure) {
         nextPutFailure = failure;
      }

      void failNextRemove(Exception failure) {
         nextRemoveFailure = failure;
      }

      void failOnClose(Exception failure) {
         nextCloseFailure = failure;
      }

      private final LinkedHashMap<String, T> values = new LinkedHashMap<>();
      private final List<Listener<T>> listeners = new ArrayList<>();
      private Exception nextPutFailure;
      private Exception nextRemoveFailure;
      private Exception nextCloseFailure;
      private boolean closed;
   }

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
