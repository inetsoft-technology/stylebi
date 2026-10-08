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
package inetsoft.util.swap;

import inetsoft.uql.util.TableLoadException;
import inetsoft.util.stall.LockStallException;

/**
 * The failures of a data read that mean the data is not available, as opposed to a read that
 * failed on bad data: a lock stall ({@link LockStallException}) or a lost swap file
 * ({@link SwapFileReadException}). A reader that turns other failures into a null or empty
 * value must let these reach its caller, or a script computes a wrong result from data it
 * never read (bug #77910).
 */
public final class DataUnavailable {
   private DataUnavailable() {
   }

   /**
    * Find the failure in the cause chain of {@code failure} that means the data is not
    * available. A lock stall is found before a lost swap file.
    *
    * @return the lock stall or swap file read failure of the chain, or {@code null} if there
    * is none.
    */
   public static RuntimeException find(Throwable failure) {
      LockStallException stall = LockStallException.find(failure);

      if(stall != null) {
         return stall;
      }

      return SwapFileReadException.find(failure);
   }

   /**
    * Rethrow the lock stall or swap file read failure in the cause chain of {@code failure}
    * itself, if there is one, and return otherwise.
    */
   public static void rethrow(Throwable failure) {
      RuntimeException unavailable = find(failure);

      if(unavailable != null) {
         throw unavailable;
      }
   }

   /**
    * Copy a lock stall, a swap file read failure or a table load failure: a new instance of
    * the same class with the same message, fields, cause and stack trace, and none of its
    * suppressed exceptions. The copy does not keep {@code failure} in its cause chain.
    * <p>
    * A failure that Java code called by a script throws gets a suppressed Truffle stack trace
    * element added to it, which cannot be serialized, e.g. in the response of a cluster call.
    * So the script engine rethrows a copy of such a failure, and a table that keeps a failure
    * for its later readers keeps a copy that is never thrown itself, and throws a new instance
    * to each reader (bug #78084).
    *
    * @return the copy, {@code failure} itself if it is none of these failures, or
    * {@code null} if it is {@code null}.
    */
   public static RuntimeException copy(RuntimeException failure) {
      if(failure instanceof LockStallException stall) {
         return stall.copy();
      }
      else if(failure instanceof SwapFileReadException swap) {
         return swap.copy();
      }
      else if(failure instanceof TableLoadException load) {
         return copy(load);
      }

      return failure;
   }

   /**
    * The cause for a new exception that a table throws to a reader for a failure it keeps,
    * e.g. {@code new SetOperationException(copyInChain(kept))}. A reader finds a lock stall,
    * a swap file read failure or a load failure in the cause chain by its class and may throw
    * it on through a script, so the chain must not hold the kept instance of one (bug #78084).
    *
    * @return a copy of {@code failure} if it is such a failure, a copy of the one in its cause
    * chain otherwise, the copy taking the place of {@code failure}, or {@code failure} itself
    * if its chain has none.
    */
   public static Throwable copyInChain(Throwable failure) {
      if(failure instanceof RuntimeException ex) {
         RuntimeException copy = copy(ex);

         if(copy != ex) {
            return copy;
         }
      }

      RuntimeException found = find(failure);

      if(found == null) {
         found = TableLoadException.find(failure);
      }

      return found != null ? copy(found) : failure;
   }

   /**
    * Copy a table load failure, see {@link #copy(RuntimeException)}.
    */
   public static TableLoadException copy(TableLoadException failure) {
      if(failure == null) {
         return null;
      }

      TableLoadException copy = new TableLoadException(failure.getMessage(), failure.getCause());
      copy.setStackTrace(failure.getStackTrace());
      return copy;
   }
}
