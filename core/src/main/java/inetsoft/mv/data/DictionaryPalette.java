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

import inetsoft.util.swap.XSwapUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.WritableByteChannel;
import java.util.ArrayList;
import java.util.List;

/**
 * DictionaryPalette, a palette for block dictionary in same column.
 *
 * @author InetSoft Technology
 * @since  11.4
 */
public final class DictionaryPalette implements Cloneable {
   /**
    * Default constructor.
    */
   public DictionaryPalette() {
      super();
   }

   /**
    * Create an instance of DictionaryPalette.
    *
    * @param column the column index.
    */
   public DictionaryPalette(int column) {
      this.column = column;
   }

   /**
    * Get the column index.
    */
   public int getColumn() {
      return column;
   }

   /**
    * Transform the real dictionary to dictionary index.
    */
   public XDimDictionaryIndex getDictionary(XDimDictionary dict) {
      if(dict == null) {
         return null;
      }

      int index = getIndex(dict);

      if(index < 0) {
         dicts.add(dict);
         index = dicts.size() - 1;
      }

      XDimDictionaryIndex dictIndex = new XDimDictionaryIndex(column, index);
      dictIndex.setDictionary(dict);
      return dictIndex;
   }

   /**
    * Get XDimDictionary by index.
    * @param index index of dicts.
    * @return the dict of index.
    */
   public XDimDictionary getDict(int index) {
      return dicts.get(index);
   }

   /**
    * Get dicts.
    * @return dicts.
    */
   public List<XDimDictionary> getDicts() {
      return dicts;
   }

   /**
    * Write dicts to file.
    */
   public void write(WritableByteChannel channel) throws IOException{
      ByteBuffer buf = ByteBuffer.allocate(8);
      buf.putInt(dicts.size());
      buf.putInt(column);
      XSwapUtil.flip(buf);

      while(buf.hasRemaining()) {
         channel.write(buf);
      }

      for(XDimDictionary dict : dicts) {
         buf = ByteBuffer.allocate(1);
         buf.put((dict != null) ? (byte)1 : (byte)0);
         XSwapUtil.flip(buf);

         while(buf.hasRemaining()) {
            channel.write(buf);
         }

         if(dict != null) {
            dict.write(channel);
         }
      }
   }

   /**
    * Read dicts from file.
    */
   public void read(ChannelProvider channelProvider, ReadableByteChannel channel) throws IOException
   {
      ByteBuffer buf = ByteBuffer.allocate(8);
      channel.read(buf);
      XSwapUtil.flip(buf);
      int dictsSize = buf.getInt();
      column = buf.getInt();

      for(int j = 0; j < dictsSize; j++) {
         buf = ByteBuffer.allocate(1);
         channel.read(buf);
         XSwapUtil.flip(buf);

         if(buf.get() != 0) {
            XDimDictionary dict = new XDimDictionary();
            dict.read(channelProvider, channel);
            dicts.add(dict);
         }
         else {
            dicts.add(null);
         }
      }
   }

   /**
    * Clone the original dict.
    */
   public XDimDictionary cloneDict(XDimDictionaryIndex index) {
      XDimDictionary dict = dicts.get(index.getIndex());
      return (XDimDictionary) dict.clone();
   }

   /**
    * Get the dictionary index.
    */
   public int getIndex(XDimDictionary dict) {
      return dicts.indexOf(dict);
   }

   /**
    * Clone DictionaryPalette.
    */
   @Override
   public Object clone() {
      try {
         DictionaryPalette palette = (DictionaryPalette) super.clone();
         palette.column = column;
         palette.dicts = new ArrayList<>();
         palette.dicts.addAll(dicts);

         return palette;
      }
      catch(CloneNotSupportedException e) {
         LOG.error("Clone failed: " + e);
         return null;
      }
   }

   /**
    * Set a dictionary at index.
    */
   public void setDictionary(int index, XDimDictionary dict) {
      dicts.set(index, dict);
   }

   private int column;
   private List<XDimDictionary> dicts = new ArrayList<>();

   private static final Logger LOG = LoggerFactory.getLogger(DictionaryPalette.class);
}
