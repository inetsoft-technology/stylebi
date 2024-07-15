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
package inetsoft.uql.table;

import java.io.File;
import java.io.Serializable;
import java.nio.channels.FileChannel;

/**
 * XSerializable, it performs as a container, which could swap out/in data
 * by invalidating or validating.
 *
 * @version 9.1
 * @author InetSoft Technology Corp
 */
public interface XSerializable extends Serializable {
   /**
    * Unknown state.
    */
   public static final byte UNKNOWN = 0;
   /**
    * True state.
    */
   public static final byte TRUE = 1;
   /**
    * False state.
    */
   public static final byte FALSE = 2;

   /**
    * Check if the table column is in valid state.
    * @return <tt>true</tt> if in valid state, <tt>false</tt> otherwise.
    */
   public boolean isValid();

   /**
    * Check if is serializable.
    * @return <tt>true</tt> if serializable, <tt>false</tt> otherwise.
    */
   public boolean isSerializable();

   /**
    * Swap data to file.
    */
   public void swap(File file, FileChannel fc) throws Exception;

   /**
    * Clear out the internal array (after swapping).
    */
   public void invalidate();

   /**
    * Dispose the serializable.
    */
   public void dispose();
}
