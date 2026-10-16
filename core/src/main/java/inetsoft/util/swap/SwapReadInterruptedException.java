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

import java.io.File;
import java.io.InterruptedIOException;
import java.nio.channels.AsynchronousCloseException;
import java.nio.channels.ClosedByInterruptException;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

/**
 * Thrown when the read of a swap file was interrupted, e.g. by a script timeout or a cancel,
 * which closes the {@link java.nio.channels.FileChannel} of the read. The swap file is not
 * lost, a later read tries it again, but the data was not read by this one, so it is still a
 * {@link SwapFileReadException}: every reader that must not go on without the data treats it
 * the same (bug #77916). The interrupt flag of the thread is left set.
 */
public class SwapReadInterruptedException extends SwapFileReadException {
   public SwapReadInterruptedException(File file, Throwable cause) {
      super(file, "The read of swap file " + file + " was interrupted, the swapped data was " +
            "not read", cause);
   }

   private SwapReadInterruptedException(File file, String message, Throwable cause) {
      super(file, message, cause);
   }

   /**
    * Copy this failure, keeping its class: a reader that must not go on without the data
    * tells an interrupted read by it (bug #77916, #78084).
    */
   @Override
   public SwapReadInterruptedException copy() {
      SwapReadInterruptedException copy =
         new SwapReadInterruptedException(getFile(), getMessage(), getCause());
      copy.setStackTrace(getStackTrace());
      return copy;
   }

   /**
    * Check if a swap file read failed because the reading thread was interrupted: a channel
    * closed by the interrupt, or an interrupted read or close while the interrupt flag of the
    * thread is still set. Call it on the thread that read the file.
    */
   public static boolean isInterrupt(Throwable failure) {
      boolean interrupted = Thread.currentThread().isInterrupted();
      Set<Throwable> seen = Collections.newSetFromMap(new IdentityHashMap<>());

      for(Throwable t = failure; t != null && seen.add(t); t = t.getCause()) {
         if(t instanceof ClosedByInterruptException ||
            interrupted && (t instanceof AsynchronousCloseException ||
                            t instanceof InterruptedIOException ||
                            t instanceof InterruptedException))
         {
            return true;
         }
      }

      return false;
   }
}
