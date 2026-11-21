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
package inetsoft.web.wiz.controller;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Per-database serialization of the check-then-write sequences of the data model routes (create,
 * rename, move, folder create/rename, delete) on this node. A second node or the Portal can still
 * race; a cluster lock is a follow-up, the same limitation the data source routes record.
 */
final class WizDataModelLocks {
   private WizDataModelLocks() {
   }

   /** Runs the body holding the lock of the database. */
   static <T> T withDatabase(String database, WizDataModelErrors.Body<T> body) throws Exception {
      ReentrantLock lock = LOCKS.computeIfAbsent(database, k -> new ReentrantLock());
      lock.lock();

      try {
         return body.call();
      }
      finally {
         lock.unlock();
      }
   }

   private static final ConcurrentMap<String, ReentrantLock> LOCKS = new ConcurrentHashMap<>();
}
