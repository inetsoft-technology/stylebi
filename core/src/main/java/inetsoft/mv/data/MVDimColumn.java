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

import inetsoft.mv.util.SeekableInputStream;
import inetsoft.util.Tool;
import inetsoft.util.swap.XSwapUtil;
import inetsoft.util.swap.XSwapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.WritableByteChannel;

/**
 * The dimension column values and also serves as an index.
 *
 * @author InetSoft Technology
 * @since  10.2
 */
public final class MVDimColumn extends DictDimIndex implements XMVColumn {
   public MVDimColumn(int cardinality, int len, boolean create) {
      super((int) Math.ceil(Math.log(cardinality) / Math.log(2)) + 1);
      this.cardinality = cardinality;

      dimbuf.setSize(len, create);
   }

   /**
    * Set the number of items in the column.
    */
   public void setRowCount(int rcnt) {
      int nkeys = dimbuf.getSize();

      if(nkeys == rcnt) {
         return;
      }
      else if(nkeys < rcnt) {
         throw new RuntimeException("Rows can only be reduced in MVDimColumn");
      }

      dimbuf.setSize(rcnt, true);
   }

   /**
    * Get the column value as a dimention.
    */
   @Override
   public final long getDimValue(int idx) {
      return getValue(idx);
   }

   /**
    * Get the column value as a measure.
    */
   @Override
   public final double getMeasureValue(int idx) {
      int val = getValue(idx);
      return cnull && val == 0 ? Tool.NULL_DOUBLE : val + rangeMin;
   }

   /**
    * Get the value at the specified index.
    */
   public final int getValue(int idx) {
      accessed = XSwapper.cur;
      return dimbuf.getValue(idx);
   }

   /**
    * Set the value at the specified index.
    */
   public void setValue(int idx, int value) {
      dimbuf.setValue(idx, value);
   }

   /**
    * Get the number of items in the column.
    */
   public int getCardinality() {
      return cardinality;
   }

   /**
    * Set the minimum of the value range.
    */
   public void setRangeMin(int rangeMin) {
      this.rangeMin = rangeMin;
   }

   /**
    * Get the minimum of the value range.
    */
   public int getRangeMin() {
      return rangeMin;
   }

   /**
    * Set contains null value.
    */
   public void setContainsNull(boolean cnull) {
      this.cnull = cnull;
   }

   /**
    * Is contains null or not.
    */
   public boolean containsNull() {
      return cnull;
   }

   /**
    * Read from channel.
    */
   @Override
   protected void read0(SeekableInputStream channel) throws Exception {
      super.read0(channel);

      ByteBuffer buf = ByteBuffer.allocate(5);
      channel.readFully(buf);
      XSwapUtil.flip(buf);
      rangeMin = buf.getInt();
      byte cnulltag = buf.get();
      cnull = cnulltag == 0;
   }

   /**
    * Write to channel.
    */
   @Override
   public ByteBuffer write(WritableByteChannel channel, ByteBuffer sbuf) throws IOException {
      sbuf = super.write(channel, sbuf);

      ByteBuffer buf = ByteBuffer.allocate(5);
      buf.putInt(rangeMin);
      buf.put((byte) (cnull ? 0 : 1));
      XSwapUtil.flip(buf);

      while(buf.hasRemaining()) {
         channel.write(buf);
      }

      return sbuf;
   }

   /**
    * Get the data length of this dimension index.
    */
   @Override
   public int getLength() {
      return super.getLength() + 5;
   }

   /**
    * Get header length.
    */
   @Override
   public int getHeaderLength() {
      return super.getHeaderLength() + 5;
   }

   private int cardinality;
   private int rangeMin = 0;
   private boolean cnull;

   private static final Logger LOG =
      LoggerFactory.getLogger(MVDimColumn.class);
}
