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

import inetsoft.test.schedule.InMemoryKeyValueStorage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.util.concurrent.TimeoutException;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;

import static org.junit.jupiter.api.Assertions.*;

/*
 * Intent vs implementation suspects Issue #75501 
 *
 * [Suspect 1] setStatus(task, ...) when storage.put() throws InterruptedException
 *             -> intent: acknowledge failure and log
 *             -> actual: InterruptedException is caught by the broad catch(Exception ex) at line 103;
 *                the JVM clears the thread's interrupted flag when the exception is thrown, and the
 *                catch block never calls Thread.currentThread().interrupt() to restore it.
 *                The method then returns newStatus normally, so the caller has no way to detect
 *                the interruption via isInterrupted().
 *             -> trigger: a thread blocked in Future.get(10, SECONDS) while the storage backend
 *                (e.g. Ignite under cluster node failover) is slow, and an external caller
 *                (Spring executor shutdown or Quartz job-thread interrupt) fires thread.interrupt().
 *             -> impact: low in local/MapDB deployments (futures complete near-instantly);
 *                real in Ignite-backed clusters during graceful shutdown — Quartz or Spring may
 *                fail to stop the thread, delaying shutdown or causing the job to re-enter.
 *             -> fix: split the catch into InterruptedException (restore flag) + Exception (log only).
 *
 * [Suspect 2] clearStatus(task) when storage.remove() throws InterruptedException
 *             -> actual: same interrupt-flag leak as Suspect 1, but the catch at line 122 makes it
 *                more visible because InterruptedException is named explicitly yet the flag is still
 *                not restored.
 *             -> trigger/impact/fix: identical to Suspect 1.
 */
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
      assertFalse(storage.contains("TASK^^nightly"));
      assertTrue(storage.contains("TASK^^11:__nightly__")); // length-prefixed format
      assertEquals(1, storage.size());
      assertStatus(dao.getStatus("__nightly__"), Scheduler.Status.FAILED, 10L, 20L, "boom", 10L);

      dao.clearStatus("__nightly__");

      assertFalse(storage.contains("TASK^^nightly"));
      assertFalse(storage.contains("TASK^^11:__nightly__"));
      assertEquals(0, storage.size());
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

   // reserved-name encoding must not alias a valid literal task name to the same key
   // Pre: "__foo__" and "TASK^^foo" are both valid names; Post: they should not overwrite each other
   @Test
   void setStatus_reservedAndLiteralEncodedNames_shouldNotCollide() {
      InMemoryKeyValueStorage<ScheduleStatusDao.Status> storage = new InMemoryKeyValueStorage<>();
      ScheduleStatusDao dao = newDao(storage);

      dao.setStatus("__foo__", Scheduler.Status.STARTED, 10L, 0L, null, false);
      dao.setStatus("TASK^^foo", Scheduler.Status.FINISHED, 20L, 30L, null, false);

      assertEquals(2, storage.size());
      assertTrue(storage.contains("TASK^^7:__foo__"));   // "__foo__" (len=7) encoded
      assertTrue(storage.contains("TASK^^9:TASK^^foo")); // "TASK^^foo" (len=9) starts with prefix
      assertStatus(dao.getStatus("__foo__"), Scheduler.Status.STARTED, 10L, 0L, null, 10L);
      assertStatus(dao.getStatus("TASK^^foo"), Scheduler.Status.FINISHED, 20L, 30L, null, 20L);
   }

   // "____" satisfies ^__.*__$ and encodes to "TASK^^4:____"; "TASK^^" starts with ENCODE_PREFIX
   // and encodes to "TASK^^6:TASK^^"; the length-prefixed format guarantees no collision
   @Test
   void setStatus_fourUnderscoreTaskName_shouldNotCollideWithEmptySuffixKey() {
      InMemoryKeyValueStorage<ScheduleStatusDao.Status> storage = new InMemoryKeyValueStorage<>();
      ScheduleStatusDao dao = newDao(storage);

      dao.setStatus("____", Scheduler.Status.STARTED, 10L, 0L, null, false);
      dao.setStatus("TASK^^", Scheduler.Status.FINISHED, 20L, 30L, null, false);

      assertEquals(2, storage.size());
      assertStatus(dao.getStatus("____"), Scheduler.Status.STARTED, 10L, 0L, null, 10L);
      assertStatus(dao.getStatus("TASK^^"), Scheduler.Status.FINISHED, 20L, 30L, null, 20L);
   }

   // null task name must be rejected before reaching storage
   // Pre: null taskName; Op: any DAO operation; Post: throws and storage remains unchanged
   @Test
   void nullTaskName_shouldRejectWithException() {
      InMemoryKeyValueStorage<ScheduleStatusDao.Status> storage = new InMemoryKeyValueStorage<>();
      ScheduleStatusDao dao = newDao(storage);

      IllegalArgumentException getException =
         assertThrows(IllegalArgumentException.class, () -> dao.getStatus(null));
      IllegalArgumentException setException = assertThrows(IllegalArgumentException.class,
         () -> dao.setStatus(null, Scheduler.Status.STARTED, 1L, 0L, null, false));
      IllegalArgumentException clearException =
         assertThrows(IllegalArgumentException.class, () -> dao.clearStatus(null));

      assertEquals("taskName must not be null", getException.getMessage());
      assertEquals("taskName must not be null", setException.getMessage());
      assertEquals("taskName must not be null", clearException.getMessage());
      assertEquals(0, storage.size());
   }

   // [Lifecycle: idempotency] clearStatus on a task that was never set does not throw
   // Pre: storage empty; Op: clearStatus("no-such-task"); Post: no exception, storage stays empty
   @Test
   void clearStatus_nonExistentTask_doesNotThrow() {
      InMemoryKeyValueStorage<ScheduleStatusDao.Status> storage = new InMemoryKeyValueStorage<>();
      ScheduleStatusDao dao = newDao(storage);

      assertDoesNotThrow(() -> dao.clearStatus("no-such-task"));
      assertEquals(0, storage.size());
   }

   @Disabled("Suspect 1: setStatus swallows InterruptedException without restoring the thread " +
             "interrupted flag - ScheduleStatusDao:103; Fix: add Thread.currentThread().interrupt() " +
             "in the catch block")
   @Test
   void setStatus_whenPutInterrupted_shouldRestoreInterruptFlag() {
      scheduleStatusDaoLogger.setLevel(Level.OFF);
      InMemoryKeyValueStorage<ScheduleStatusDao.Status> storage = new InMemoryKeyValueStorage<>();
      storage.failNextPut(new InterruptedException("simulated interrupt"));
      ScheduleStatusDao dao = newDao(storage);

      dao.setStatus("task", Scheduler.Status.STARTED, 1L, 0L, null, false);

      assertTrue(Thread.interrupted()); // returns true and clears flag; currently FAILS
   }

   @Disabled("Suspect 2: clearStatus swallows InterruptedException without restoring the thread " +
             "interrupted flag - ScheduleStatusDao:122; Fix: add Thread.currentThread().interrupt() " +
             "in the catch block")
   @Test
   void clearStatus_whenRemoveInterrupted_shouldRestoreInterruptFlag() {
      scheduleStatusDaoLogger.setLevel(Level.OFF);
      InMemoryKeyValueStorage<ScheduleStatusDao.Status> storage = new InMemoryKeyValueStorage<>();
      ScheduleStatusDao dao = newDao(storage);
      dao.setStatus("task", Scheduler.Status.STARTED, 1L, 0L, null, false);
      storage.failNextRemove(new InterruptedException("simulated interrupt"));

      dao.clearStatus("task");

      assertTrue(Thread.interrupted()); // returns true and clears flag; currently FAILS
   }

   private static ScheduleStatusDao newDao(InMemoryKeyValueStorage<ScheduleStatusDao.Status> storage) {
      return new ScheduleStatusDao(storage);
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

}
