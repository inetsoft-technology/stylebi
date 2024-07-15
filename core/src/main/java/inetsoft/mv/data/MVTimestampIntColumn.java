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
package inetsoft.mv.data;

import inetsoft.mv.fs.BlockFile;
import inetsoft.mv.util.SeekableInputStream;

/**
 * A measure column for storing timestamp as seconds from epoc.
 *
 * @author InetSoft Technology
 * @version 12.1
 */
public class MVTimestampIntColumn extends MVUIntColumn {
   /**
    * Measure column for loading the data on demand.
    * @param newbuf true if the buffer is new (empty).
    */
   public MVTimestampIntColumn(SeekableInputStream channel, long fpos,
                               BlockFile file, int size, boolean newbuf)
   {
      super(channel, fpos, file, size, newbuf, FACTOR);
   }

   /**
    * Returns fpos
    */
   public long getFpos() {
      return fpos;
   }

   /**
    * Returns file
    */
   public BlockFile getFile() {
      return file;
   }

   private static double FACTOR = 1000;
}
