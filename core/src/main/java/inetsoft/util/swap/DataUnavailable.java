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
}
