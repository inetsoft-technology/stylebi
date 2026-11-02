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
import inetsoft.util.swap.XSwapUtil;
import it.unimi.dsi.fastutil.ints.*;
import org.roaringbitmap.IntIterator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.WritableByteChannel;
import java.util.*;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Bit map index. The bit vector encoding is ideal for dimensions with low
 * cardinality.
 *
 * @author InetSoft Technology
 * @version 10.2
 */
public final class BitDimIndex extends XDimIndex {
   /**
    * Create an instance of XDimIndex.
    */
   public BitDimIndex() {
      super();
      cache = new Int2ObjectRBTreeMap<>();
   }

   /**
    * Associate a row with an index key.
    */
   @Override
   public void addKey(int key, int row) {
      BitSet rows = cache.get(key);

      if(rows == null) {
         rows = new BitSet();
         cache.put(key, rows);
      }

      rows.set(row);
   }

   /**
    * Sort keys.
    */
   @Override
   public void complete() {
      int size = cache.size();
      keys = new int[size];
      rows = new BitSet[size];
      Iterator<Int2ObjectMap.Entry<BitSet>> iter =
         cache.int2ObjectEntrySet().iterator();

      for(int i = 0; iter.hasNext(); i++) {
         Int2ObjectMap.Entry<BitSet> entry = iter.next();
         keys[i] = entry.getKey();
         rows[i] = entry.getValue();
         rows[i].compact();
      }

      // clear cache data to release memory in time
      cache.clear();
      super.complete();
   }

   /**
    * Get the bit set at the specified index.
    */
   @Override
   public BitSet getRows(long idx, boolean cnull) {
      KeyRowEntry entry = accessKeyRow();
      int[] keys = entry.keys;
      BitSet[] rows = entry.rows;
      int aidx = Arrays.binarySearch(keys, (int) idx);
      BitSet set = aidx < 0 ? new BitSet() : rows[aidx];
      return set;
   }

   /**
    * Get the bit set for the specified range.
    */
   @Override
   public BitSet getRows(long from, boolean fincluded, long to,
                         boolean tincluded, boolean cnull)
   {
      RangeInfo tinfo = new RangeInfo(from, fincluded, to, tincluded);
      RangeInfo cinfo = find(tinfo);

      if(cinfo != null) {
         return cinfo.result;
      }

      KeyRowEntry entry = accessKeyRow();
      int[] keys = entry.keys;
      BitSet[] rows = entry.rows;
      int start, end;

      if(from == Integer.MIN_VALUE) {
         // keys are dictionary indices, not positions. cnull only says the dictionary has a
         // null slot (key 0), the block may have no row for it, so skip key 0 rather than
         // the first position
         start = 0;

         if(cnull) {
            start = Arrays.binarySearch(keys, 0);
            start = start >= 0 ? start + 1 : -start - 1;
         }
      }
      else {
         start = Arrays.binarySearch(keys, (int) from);

         if(start >= 0 && !fincluded) {
            start++;
         }
         else if(start < 0) {
            start = -start - 1;
         }
      }

      if(to == Integer.MIN_VALUE) {
         end = keys.length - 1;
      }
      else {
         end = Arrays.binarySearch(keys, (int) to);

         if(end >= 0 && !tincluded) {
            end--;
         }
         else if(end < 0) {
            end = -end - 2;
         }
      }

      BitSet res = null;

      while(start >= 0 && start <= end && start < keys.length) {
         res = res == null ? rows[start] : res.or(rows[start]);
         start++;
      }

      res = res == null ? new BitSet() : res;
      tinfo.result = res;
      // publish only after the result is computed, so a concurrent identical range never
      // sees an entry without a result and a failed computation leaves nothing behind
      publish(tinfo);
      return res;
   }

   /**
    * Get the bit set for the specified operation and values. A NOT IN list of several values is
    * evaluated as (all rows) minus (rows of the listed keys) instead of one prefix/suffix OR
    * chain per listed value.
    */
   @Override
   public BitSet getRows(String op, long[] val, boolean not, boolean cnull) {
      if(not && "IN".equals(op) && val != null && val.length > 1) {
         BitSet res = getNotInRows(val, cnull);

         if(res != null) {
            return res;
         }
      }

      return super.getRows(op, val, not, cnull);
   }

   /**
    * Rows whose key is none of vals, with exactly the result of AND-ing, per value, the rows
    * before it (from the first position that cnull does not skip) and the rows after it.
    * Positions skipped by cnull (key 0 and anything below it) are only reachable through the
    * "after" side, so they are kept only if their key is above every listed value.
    *
    * @return the rows, or null if the values are not plain int keys and the generic path
    * must be used.
    */
   private BitSet getNotInRows(long[] vals, boolean cnull) {
      long max = Long.MIN_VALUE;

      for(long v : vals) {
         // MIN_VALUE is the open-end marker of the range api
         if(v != (int) v || v == Integer.MIN_VALUE) {
            return null;
         }

         max = Math.max(max, v);
      }

      KeyRowEntry entry = accessKeyRow();
      int[] keys = entry.keys;
      BitSet[] rows = entry.rows;

      if(keys.length == 0) {
         return new BitSet();
      }

      int start = 0;

      if(cnull) {
         start = Arrays.binarySearch(keys, 0);
         start = start >= 0 ? start + 1 : -start - 1;
      }

      BitSet excluded = null;

      for(long v : vals) {
         int idx = Arrays.binarySearch(keys, (int) v);

         if(idx >= start) {
            excluded = excluded == null ? rows[idx] : excluded.or(rows[idx]);
         }
      }

      BitSet res = getAllRowsFrom(keys, rows, start, cnull);
      res = excluded == null ? res : res.andNot(excluded);

      for(int i = 0; i < start; i++) {
         if(keys[i] > max) {
            res = res.or(rows[i]);
         }
      }

      return res;
   }

   /**
    * Union of the rows of all keys from position start, cached per cnull since it only depends
    * on the keys and rows.
    */
   private BitSet getAllRowsFrom(int[] keys, BitSet[] rows, int start, boolean cnull) {
      AllRows cached = allRows[cnull ? 1 : 0];

      if(cached != null && cached.keys == keys && cached.start == start) {
         return cached.rows;
      }

      BitSet res = null;

      for(int i = start; i < keys.length; i++) {
         res = res == null ? rows[i] : res.or(rows[i]);
      }

      res = res == null ? new BitSet() : res;
      allRows[cnull ? 1 : 0] = new AllRows(keys, start, res);
      return res;
   }

   /**
    * Find cached range info. Only completed entries (with a result) are in the cache.
    */
   private RangeInfo find(RangeInfo info) {
      lock.lock();

      try {
         for(int i = 0; i < ranges.length; i++) {
            if(info.equals(ranges[i])) {
               return ranges[i];
            }
         }

         return null;
      }
      finally {
         lock.unlock();
      }
   }

   /**
    * Add a completed range info to the cache, unless an identical one was added meanwhile.
    */
   private void publish(RangeInfo info) {
      lock.lock();

      try {
         for(int i = 0; i < ranges.length; i++) {
            if(info.equals(ranges[i])) {
               return;
            }
         }

         rangepos = rangepos == ranges.length - 1 ? 0 : rangepos + 1;
         ranges[rangepos] = info;
      }
      finally {
         lock.unlock();
      }
   }

   /**
    * Read from channel.
    */
   @Override
   protected void read0(SeekableInputStream channel) throws Exception {
      ByteBuffer buf = ByteBuffer.allocate(5);
      channel.readFully(buf);
      XSwapUtil.flip(buf);
      int len = buf.getInt();
      setCompressed(buf.get() == 1);

      ByteBuffer mapped = channel.map(channel.position(), len);

      try {
         ByteBuffer buf2 = mapped;

         if(isCompressed()) {
            buf2 = XSwapUtil.uncompressByteBuffer(mapped);
         }

         int size = buf2.getInt();
         keys = new int[size];
         rows = new BitSet[size];

         for(int i = 0; i < size; i++) {
            keys[i] = buf2.getInt();
            rows[i] = new BitSet();
            rows[i].load(buf2);
         }
      }
      finally {
         channel.unmap(mapped);
      }
   }

   /**
    * Write to channel.
    */
   @Override
   public synchronized ByteBuffer write(WritableByteChannel channel, ByteBuffer sbuf)
      throws IOException
   {
      access(); // make sure data is in memory

      int len = getLength();
      ByteBuffer buf = null;

      if(sbuf == null || sbuf.capacity() < len - 4) {
         buf = ByteBuffer.allocate(len - 4);
      }
      else {
         buf = sbuf;
         XSwapUtil.clear(buf);
      }

      int size = keys.length;
      buf.putInt(size);

      for(int i = 0; i < size; i++) {
         buf.putInt(keys[i]);
         rows[i].save(buf);
      }

      XSwapUtil.flip(buf);
      ByteBuffer buf2 = buf;

      if(isCompressed()) {
         buf2 = XSwapUtil.compressByteBuffer(buf);
      }

      ByteBuffer headBuf = ByteBuffer.allocate(5);
      headBuf.putInt(buf2.remaining());
      headBuf.put((byte) (isCompressed() ? 1 : 0));
      XSwapUtil.flip(headBuf);

      while(headBuf.hasRemaining()) {
         channel.write(headBuf);
      }

      while(buf2.hasRemaining()) {
         channel.write(buf2);
      }

      return buf;
   }

   /**
    * Called after this index is swapped to file. Clear the in-memory data now.
    */
   @Override
   protected void swapped() {
      keys = null;
      rows = null;
      allRows[0] = null;
      allRows[1] = null;
   }

   /**
    * Get the data length of this dimension index.
    */
   @Override
   public int getLength() {
      if(len == 0) {
         len = 4 + 4;
         KeyRowEntry entry = accessKeyRow();
         int[] keys = entry.keys;
         BitSet[] rows = entry.rows;
         int size = keys.length;

         for(int i = 0; i < size; i++) {
            len += 4 + rows[i].getLength();
         }
      }

      return len;
   }

   /**
    * Update keys with the key map.
    */
   public synchronized void updateKeys(Map<Integer, Integer> map) {
      KeyRowEntry entry = accessKeyRow();
      // lock this function, so keys and rows will not be swapped, so
      // the local keys and rows is in sync with class member keys, rows
      int[] keys = entry.keys;
      BitSet[] rows = entry.rows;
      int size = keys.length;

      for(int i = 0; i < size; i++) {
         keys[i] = map.containsKey(keys[i]) ? map.get(keys[i]) : keys[i];
      }
   }

   /**
    * Merge keys and values.
    */
   public static BitDimIndex merge(BitDimIndex dim0, BitDimIndex dim1, int rcnt)
   {
      BitDimIndex dIndex = new BitDimIndex();

      // synchronized dim0, so it will not be swapped during touch time
      synchronized(dim0) {
         dim0.accessKeyRow();

         for(int key : dim0.keys) {
            BitSet rows = dim0.getRows(key, true);
            dIndex.cache.put(key, rows);
         }
      }

      synchronized(dim1) {
         dim1.accessKeyRow();

         for(int key : dim1.keys) {
            BitSet rows = dim1.getRows(key, true);
            IntIterator iter = rows.intIterator();

            while(iter.hasNext()) {
               int r = iter.next();
               dIndex.addKey(key, r + rcnt);
            }
         }
      }

      dIndex.complete();
      return dIndex;
   }

   private KeyRowEntry accessKeyRow() {
      touch();
      int[] keys = this.keys;
      BitSet[] rows = this.rows;

      if(keys == null || rows == null) {
         synchronized(this) {
            keys = this.keys;
            rows = this.rows;

            if(keys == null || rows == null) {
               access();
               keys = this.keys;
               rows = this.rows;
            }
         }
      }

      return new KeyRowEntry(keys, rows);
   }

   /**
    * The union of all rows from a start position, valid for one keys array. A swap replaces
    * the keys array, so a mismatch recomputes it.
    */
   private static final class AllRows {
      public AllRows(int[] keys, int start, BitSet rows) {
         this.keys = keys;
         this.start = start;
         this.rows = rows;
      }

      private final int[] keys;
      private final int start;
      private final BitSet rows;
   }

   private static final class KeyRowEntry {
      public KeyRowEntry(int[] keys, BitSet[] rows) {
         this.keys = keys;
         this.rows = rows;
      }

      public int[] keys;
      public BitSet[] rows;
   }

   /**
    * RangeInfo, the range condition caches evaluation result.
    */
   private static final class RangeInfo {
      public RangeInfo(long from, boolean fincluded, long to, boolean tincluded) {
         this.from = from;
         this.fincluded = fincluded;
         this.to = to;
         this.tincluded = tincluded;
      }

      public boolean equals(Object obj) {
         if(obj == null) {
            return false;
         }

         RangeInfo info = (RangeInfo) obj;
         return info.from == from && info.fincluded == fincluded &&
            info.to == to && info.tincluded == tincluded;
      }

      public String toString() {
         return "RangeInfo<" + from + ',' + fincluded + ',' + to + ',' +
            tincluded + '>';
      }

      private final long from;
      private final boolean fincluded;
      private final long to;
      private final boolean tincluded;
      private BitSet result;
   }

   private final RangeInfo[] ranges = new RangeInfo[12];
   private final Lock lock = new ReentrantLock();
   private int rangepos = -1;
   // [cnull false, cnull true]; a slot is replaced whole, so a racing reader sees a full entry
   private final AllRows[] allRows = new AllRows[2];
   private final Int2ObjectSortedMap<BitSet> cache;
   private int len = 0;
   private int[] keys;
   private BitSet[] rows;

   private static final Logger LOG =
      LoggerFactory.getLogger(BitDimIndex.class);
}
