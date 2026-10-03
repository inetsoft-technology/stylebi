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

/**
 * Thrown when the data of a swapped fragment cannot be read back from its swap file and no
 * copy of it is left in memory.
 */
public class SwapFileReadException extends RuntimeException {
   public SwapFileReadException(File file, Throwable cause) {
      super("Could not read swap file " + file + ", the swapped data is not available", cause);
      this.file = file;
   }

   /**
    * Get the swap file that could not be read.
    */
   public File getFile() {
      return file;
   }

   private final File file;
}
