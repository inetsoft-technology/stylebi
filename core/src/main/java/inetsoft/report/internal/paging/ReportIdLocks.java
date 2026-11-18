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
package inetsoft.report.internal.paging;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Per-report-id, ref-counted locks. An entry exists only while a thread holds or waits for it.
 */
final class ReportIdLocks {
   /**
    * Acquire the lock of an id, waiting at most the given time.
    *
    * @return true if the lock is held (and must be released), false on timeout or interrupt.
    */
   boolean acquire(Object id, long seconds) {
      Entry entry = locks.compute(id, (k, e) -> {
         if(e == null) {
            e = new Entry();
         }

         e.refs++;
         return e;
      });

      boolean acquired = false;

      try {
         acquired = entry.lock.tryLock(seconds, TimeUnit.SECONDS);
      }
      catch(InterruptedException ex) {
         Thread.currentThread().interrupt();
      }
      finally {
         if(!acquired) {
            deref(id);
         }
      }

      return acquired;
   }

   void release(Object id) {
      Entry entry = locks.get(id);

      if(entry != null) {
         entry.lock.unlock();
         deref(id);
      }
   }

   int size() {
      return locks.size();
   }

   private void deref(Object id) {
      locks.computeIfPresent(id, (k, e) -> --e.refs <= 0 ? null : e);
   }

   private static final class Entry {
      final ReentrantLock lock = new ReentrantLock();
      int refs;
   }

   private final ConcurrentHashMap<Object, Entry> locks = new ConcurrentHashMap<>();
}
