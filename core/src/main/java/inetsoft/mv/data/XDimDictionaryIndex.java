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

import inetsoft.uql.jdbc.XBinaryCondition;
import inetsoft.util.swap.XSwapUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.WritableByteChannel;

/**
 * BlockDictionary, stores all dictionary in a table block.
 *
 * @author InetSoft Technology
 * @since  11.4
 */
public class XDimDictionaryIndex extends XDimDictionary {
   /**
    * Default constructor.
    */
   public XDimDictionaryIndex() {
      super();
   }

   /**
    * Constructor.
    * @param column the column for current dictionary.
    * @param index the dictionary index in palette.
    */
   public XDimDictionaryIndex(int column, int index) {
      this.column = column;
      this.index = index;
   }

   /**
    * Get the column index.
    */
   public int getColumn() {
      return column;
   }

   /**
    * Get index in palette.
    */
   public int getIndex() {
      return index;
   }

   /**
    * Set the original dict.
    */
   public void setDictionary(XDimDictionary dict) {
      this.dict = dict;
   }

   //--------------------XDimDictionary implementation--------------------------

   /**
    * Get dimension value.
    */
   @Override
   public final Object getValue(int index) {
      return dict.getValue(index);
   }

   /**
    * Get dimension index.
    */
   @Override
   public int indexOf(Object value, int row) {
      return dict.indexOf(value, row);
   }

   /**
    * Get the minimum of the value range.
    */
   @Override
   public int getRangeMin() {
      return dict.getRangeMin();
   }

   /**
    * Check if contains null value.
    */
   @Override
   public boolean containsNull() {
      return dict.containsNull();
   }

   /**
    * Map dimension value in condition to index.
    */
   @Override
   public void fixFilter(XBinaryCondition cond) {
      if(dict != null) {
         dict.fixFilter(cond);
      }
   }

   /**
    * Get the max value.
    */
   @Override
   public Object max() {
      return dict.max();
   }

   /**
    * Get the min value.
    */
   @Override
   public Object min() {
      return dict.min();
   }

   /**
    * Merge from dictionary.
    */
   @Override
   public void mergeFrom(XDimDictionary mdim) {
      // @temp by hunk, to do.
   }

   /**
    * Clear the XDimDictionary.
    */
   @Override
   public void clear() {
      dict = null;
   }

   /**
    * Get the size.
    */
   @Override
   public int size() {
      return dict.size();
   }

   /**
    * Load from binary storage.
    */
   @Override
   public void read(ChannelProvider channelProvider, ReadableByteChannel channel) throws IOException
   {
      ByteBuffer buf = ByteBuffer.allocate(8);
      channel.read(buf);
      XSwapUtil.flip(buf);
      column = buf.getInt();
      index = buf.getInt();
   }

   /**
    * Save to binary storage.
    */
   @Override
   public void write(WritableByteChannel channel) throws IOException {
      ByteBuffer buf = ByteBuffer.allocate(8);
      buf.putInt(column);
      buf.putInt(index);
      XSwapUtil.flip(buf);

      while(buf.hasRemaining()) {
         channel.write(buf);
      }
   }

   /**
    * Check if the swappable is swappable.
    * @return <tt>true</tt> if swappable, <tt>false</tt> otherwise.
    */
   @Override
   public boolean isSwappable() {
      return false;
   }

   /**
    * Dispose the swappable.
    */
   @Override
   public synchronized void dispose() {
      super.dispose();
      dict = null;
   }

   @Override
   public boolean isOverflow() {
      return dict.isOverflow();
   }

   /**
    * Reset overflow.
    */
   @Override
   public boolean resetOverflow() {
      // do nothing
      return false;
   }

   /**
    * Add one more value to this dimension dictionary.
    */
   @Override
   public void addValue(Object obj) {
      // do nothing
   }

   /**
    * Complete this dimension dictionary.
    */
   @Override
   public void complete() {
      // do nothing
   }

   /**
    * Set data type.
    */
   @Override
   public void setDataType(Class cls) {
      // do nothing
   }

   /**
    * Clone XDimDictionaryIndex.
    */
   @Override
   public Object clone() {
      XDimDictionaryIndex dictIndex = (XDimDictionaryIndex) super.clone();
      return dictIndex;
   }

   // for test
   public String toString() {
      return column + " | " + index + " | " +
         (dict != null ? dict.toString() : "null");
   }

   private int column;
   private int index;
   private transient XDimDictionary dict;

   private static final Logger LOG =
      LoggerFactory.getLogger(XDimDictionaryIndex.class);
}
