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
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

/**
 * Thrown when the data of a swapped fragment cannot be read back from its swap file and no
 * copy of it is left in memory.
 */
public class SwapFileReadException extends RuntimeException {
   public SwapFileReadException(File file, Throwable cause) {
      this(file, "Could not read swap file " + file + ", the swapped data is not available",
           cause);
   }

   protected SwapFileReadException(File file, String message, Throwable cause) {
      super(message, cause);
      this.file = file;
   }

   /**
    * Copy this failure: a new instance of the same class with the same file, message, cause
    * and stack trace, and none of its suppressed exceptions. The copy does not keep this
    * instance in its cause chain, so it can be kept or thrown where this instance may have
    * been thrown through a script, which adds a suppressed stack trace element that cannot be
    * serialized to the instance (bug #78084). A subclass overrides it to keep its class.
    */
   public SwapFileReadException copy() {
      SwapFileReadException copy = new SwapFileReadException(file, getMessage(), getCause());
      copy.setStackTrace(getStackTrace());
      return copy;
   }

   /**
    * Get the swap file that could not be read.
    */
   public File getFile() {
      return file;
   }

   /**
    * Find the swap file read failure in the cause chain of {@code failure}, e.g. one a base
    * table wrapped (bug #77651).
    *
    * @return the outermost swap file read failure of the chain, or {@code null} if there is
    * none.
    */
   public static SwapFileReadException find(Throwable failure) {
      Set<Throwable> seen = Collections.newSetFromMap(new IdentityHashMap<>());

      for(Throwable t = failure; t != null && seen.add(t); t = t.getCause()) {
         if(t instanceof SwapFileReadException) {
            return (SwapFileReadException) t;
         }
      }

      return null;
   }

   private final File file;
}
