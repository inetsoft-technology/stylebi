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

import inetsoft.mv.util.SeekableInputStream;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.WritableByteChannel;

/**
 * The column in the MV.
 *
 * @author InetSoft Technology
 * @since  11.1
 */
public interface XMVColumn {
   /**
    * Get the column value as a dimension.
    */
   public long getDimValue(int idx);

   /**
    * Get the column value as a measure.
    */
   public double getMeasureValue(int idx);

   /**
    * Get the data length (in bytes) of this column.
    */
   public int getLength();

   /**
    * Read from channel.
    */
   public void read(SeekableInputStream channel) throws IOException;

   /**
    * Write to channel.
    */
   public ByteBuffer write(WritableByteChannel channel, ByteBuffer sbuf)
         throws IOException;
}
