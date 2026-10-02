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
package inetsoft.util;

/**
 * Thrown when a thread lost an {@link UpgradableReadWriteLock} it believed it held: a bounded
 * {@link UpgradableReadWriteLock#restoreLocks()} could not take the lock back, or the thread
 * tried to take the lock again while the entries of that failed restore are still on its
 * lock-state stack.
 *
 * <p>This is a lock-integrity failure, not a data or query error. The frames that asked for
 * the lock ran, or would run, without it, so a caller that catches exceptions generically
 * (e.g. {@code catch(Exception)} around an assembly update) must rethrow it rather than log
 * it and continue as if it held the lock (bug #77153). It extends
 * {@link IllegalStateException}, the type the bounded restore threw before.</p>
 */
public class LockRestoreException extends IllegalStateException {
   public LockRestoreException(String message) {
      super(message);
   }

   public LockRestoreException(String message, Throwable cause) {
      super(message, cause);
   }
}
