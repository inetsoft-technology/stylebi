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

import inetsoft.mv.MVTool;
import inetsoft.util.swap.XSwapUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.WritableByteChannel;

/**
 * MVColumnInfo, store data for column.
 *
 * @author InetSoft Technology
 * @since  11.4
 */
public class MVColumnInfo implements Cloneable {
   /**
    * Default Constructor.
    */
   public MVColumnInfo() {
      super();
   }

   /**
    * Create an instance of MVColumnInfo.
    *
    * @param dict the dictionary of current column.
    * @param min the min value of current column.
    * @param max the max value of current column.
    */
   public MVColumnInfo(XDimDictionaryIndex dict, Object min, Object max) {
      this.dict = dict;
      this.min = min;
      this.max = max;
   }

   /**
    * Load from binary storage.
    */
   public void read(ChannelProvider channelProvider, ReadableByteChannel channel) throws IOException
   {
      Object[] mm = MVTool.readObjects(channel, false);
      min = mm[0];
      max = mm[1];
      ByteBuffer buf = ByteBuffer.allocate(1);
      channel.read(buf);
      XSwapUtil.flip(buf);

      if(buf.get() != 0) {
         dict = new XDimDictionaryIndex();
         dict.read(channelProvider, channel);
      }

      XSwapUtil.flip(buf);
      channel.read(buf);
      XSwapUtil.flip(buf);
      number = buf.get() == 1;
   }

   /**
    * Save to binary storage.
    */
   public void write(WritableByteChannel channel) throws IOException {
      Object[] mm = {min, max};
      ByteBuffer buf = MVTool.getObjectsByteBuffer(mm, false);

      while(buf.hasRemaining()) {
         channel.write(buf);
      }

      buf = ByteBuffer.allocate(1);
      buf.put(dict != null ? (byte) 1 : (byte) 0);
      XSwapUtil.flip(buf);

      while(buf.hasRemaining()) {
         channel.write(buf);
      }

      if(dict != null) {
         dict.write(channel);
      }

      XSwapUtil.flip(buf);
      buf.put(number ? (byte) 1 : (byte) 0);
      XSwapUtil.flip(buf);

      while(buf.hasRemaining()) {
         channel.write(buf);
      }
   }

   /**
    * Set the column min value.
    *
    * @param min the min value of the column.
    */
   public void setMin(Object min) {
      this.min = min;
   }

   /**
    * Get column min value.
    */
   public Object getMin() {
      return min;
   }

   /**
    * Set the column max value.
    *
    * @param max the max value of the column.
    */
   public void setMax(Object max) {
      this.max = max;
   }

   /**
    * Get column max value.
    */
   public Object getMax() {
      return max;
   }

   /**
    * Get column dict.
    */
   public XDimDictionaryIndex getDictionary() {
      return dict;
   }

   /**
    * Set column dict.
    */
   public void setDictionary(XDimDictionaryIndex dict) {
      this.dict = dict;
   }

   /**
    * Clone MVColumnInfo.
    */
   @Override
   public Object clone() {
      try {
         MVColumnInfo cinfo = (MVColumnInfo) super.clone();

         if(dict != null) {
            cinfo.dict = (XDimDictionaryIndex) dict.clone();
         }

         return cinfo;
      }
      catch(CloneNotSupportedException e) {
         LOG.error("Clone failed: " + e);
         return null;
      }
   }

   /**
    * Get number flag.
    */
   public boolean isNumber() {
      return number;
   }

   /**
    * Set number flag.
    */
   public void setNumber(boolean number) {
      this.number = number;
   }

   private boolean number;
   private Object min;
   private Object max;
   private XDimDictionaryIndex dict;

   private static final Logger LOG =
      LoggerFactory.getLogger(MVColumnInfo.class);
}
