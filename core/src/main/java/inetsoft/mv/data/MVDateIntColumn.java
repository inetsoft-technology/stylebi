/*
 * inetsoft-core - StyleBI is a business intelligence web application.
 * Copyright © 2024 InetSoft Technology (info@inetsoft.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package inetsoft.mv.data;

import inetsoft.mv.fs.BlockFile;
import inetsoft.mv.util.SeekableInputStream;

/**
 * A measure column for storing date as hours from epoc.
 *
 * @author InetSoft Technology
 * @version 12.1
 */
public class MVDateIntColumn extends MVRangeIntColumn {
   /**
    * Measure column for loading the data on demand.
    * @param newbuf true if the buffer is new (empty).
    */
   public MVDateIntColumn(SeekableInputStream channel, long fpos,
                          BlockFile file, int size, boolean newbuf)
   {
      super(channel, fpos, file, size, newbuf, FACTOR);
   }

   // some timezone may not be on the hour, e.t. UTC+5:30, so we only round
   // the time to each 30mins instead of 60mins
   private static double FACTOR = 30 * 60 * 1000;
}