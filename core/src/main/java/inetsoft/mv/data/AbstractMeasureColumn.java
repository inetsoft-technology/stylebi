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
import inetsoft.util.Tool;

import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * The base class for swappable measure columns.
 *
 * @author InetSoft Technology
 * @version 11.1
 */
public abstract class AbstractMeasureColumn extends XDimIndex implements MVMeasureColumn {
   /**
    * Measure column for loading the data on demand.
    */
   public AbstractMeasureColumn(SeekableInputStream channel, long fpos,
                                BlockFile file, int size)
   {
      rcnt = size;

      try {
         init(channel, fpos, file, false);
      }
      catch(IOException ex) {
         throw new RuntimeException(ex);
      }
   }

   /**
    * Associate a row with an index key.
    */
   @Override
   public void addKey(int key, int row) {
      throw new RuntimeException("addKey is no supported!");
   }

   /**
    * Sort keys.
    */
   @Override
   public void complete() {
   }

   /**
    * Get the bit set for the specified operation and values.
    */
   @Override
   public BitSet getRows(String op, long[] val, boolean not, boolean cnull) {
      if("IN".equals(op)) {
         return getRows(val, not, cnull);
      }

      return super.getRows(op, val, not, cnull);
   }

   /**
    * Get the bit set for the specified operation and value.
    */
   @Override
   public BitSet getRows(String op, long val, boolean not, boolean cnull) {
      if("null".equals(op)) {
         return getRows("IN", new long[] {val}, not, cnull);
      }

      return super.getRows(op, val, not, cnull);
   }

   /**
    * Get the bit set at the specified index.
    */
   @Override
   public BitSet getRows(long idx, boolean cnull) {
      return getRows(new long[] {idx}, false, cnull);
   }

   /**
    * Get rows matching one of the value ('in' condition).
    * @param vals values to match.
    * @param not negate the condition.
    */
   private BitSet getRows(long[] vals, boolean not, boolean cnull) {
      double[] vals2 = new double[vals.length];

      for(int i = 0; i < vals.length; i++) {
         vals2[i] = getComparisonValue(Double.longBitsToDouble(vals[i]));
      }

      return getRows(vals2, not, cnull);
   }

   /**
    * Get rows matching one of the value ('in' condition).
    * @param vals values to match.
    * @param not negate the condition.
    */
   protected BitSet getRows(double[] vals, boolean not, boolean cnull) {
      BitSet rows = new BitSet();

      for(int fidx = 0, i = 0; i < rcnt; fidx++) {
         double[] fragment = getFragment(fidx);
         int cnt = fragment.length;

         for(int n = 0; n < cnt; n++, i++) {
            double dval = fragment[n];
            boolean found = false;

            for(int k = 0; k < vals.length; k++) {
               if(dval == vals[k]) {
                  found = true;
                  break;
               }
            }

            if(not) {
               found = !found;

               // if null is used as comparison, it should always be false
               // as in sql
               if(found && dval == Tool.NULL_DOUBLE) {
                  found = false;
               }
            }

            if(found) {
               rows.add(i);
            }
         }
      }

      rows.complete();

      return rows;
   }

   /**
    * Get the bit set for the specified range.
    */
   @Override
   public final BitSet getRows(long from0, boolean fincluded, long to0,
                               boolean tincluded, boolean cnull)
   {
      String path = null;
      String key = null;
      BitSet rows = null;
      long modTime = -1;
      BitSetCache rowscache = null;

      if(file != null || channel != null) {
         path = (file != null ? file.toString() : channel.getFilePath().toString());

         try {
            modTime = (file != null ? file.lastModified() : channel.getModificationTime());
         }
         catch (IOException ex) {} // ignore

         rowscache = getRowCache(path, modTime);
         key = prefix + "_" + from0 + fincluded + to0 + tincluded;
         rows = rowscache.get(key);
      }

      if(rows != null) {
         return rows;
      }

      rows = getRows0(from0, fincluded, to0, tincluded, cnull);

      if(key != null) {
         rowscache.put(key, rows);
      }

      return rows;
   }

   /**
    * Access method to return the rowsCache for a given path
    * And if the modTime has changed, then the rowsCache is reset
    */
   private BitSetCache getRowCache(String path, long modTime) {
      CacheHolder holder = rowCaches.get(path);

      if(holder == null) {
         holder = new CacheHolder(new BitSetCache(), modTime);
         rowCaches.put(path, holder);
         return holder.rowCache;
      }

      synchronized(holder) {
         if(holder.modTime != modTime) {
            holder.modTime = modTime;
            holder.rowCache.clear();
         }

         return holder.rowCache;
      }
   }

   /**
    * Clear the rowcache for this column.
    */
   public static final void clearRowCache() {
      rowCaches.clear();
   }

   /**
    * Get the bit set for the specified range.
    */
   protected BitSet getRows0(long from0, boolean fincluded, long to0,
                             boolean tincluded, boolean cnull)
   {
      BitSet rows = new BitSet();
      // make sure NULL is not in the range
      double from = (from0 == Integer.MIN_VALUE) ? -Double.MAX_VALUE + 0.1
         : getComparisonValue(Double.longBitsToDouble(from0));
      double to = (to0 == Integer.MIN_VALUE) ? Double.MAX_VALUE
         : getComparisonValue(Double.longBitsToDouble(to0));
      boolean includeNullCompare = getIncludeNullCompare();

      for(int fidx = 0, i = 0; i < rcnt; fidx++) {
         double[] fragment = getFragment(fidx);
         int cnt = fragment.length;

         for(int n = 0; n < cnt; n++, i++) {
            double val = fragment[n];

            if(val == -Double.MAX_VALUE) {
               if(includeNullCompare) {
                  rows.add(i);
               }

               continue;
            }

            // !(val > from)
            if(val <= from) {
               if(fincluded) {
                  // !(val >= from)
                  if(val < from) {
                     continue;
                  }
               }
               else {
                  continue;
               }
            }

            // !(val < to)
            if(val >= to) {
               if(tincluded) {
                  // !(val <= to)
                  if(val > to) {
                     continue;
                  }
               }
               else {
                  continue;
               }
            }

            rows.add(i);
         }
      }

      rows.complete();

      return rows;
   }

   /**
    * Get bit set for the like op.
    */
   @Override
   public BitSet getLikeOpRows(String op, long val, boolean not) {
      double dval = Double.longBitsToDouble(val);
      BitSet set = new BitSet();

      if(dval == Tool.NULL_DOUBLE) {
         return set;
      }

      String sdval = Tool.toString(dval);
      boolean found = false;

      for(int fidx = 0, i = 0; i < rcnt; fidx++) {
         double[] fragment = getFragment(fidx);
         int cnt = fragment.length;

         for(int n = 0; n < cnt; n++, i++) {
            double rval = fragment[n];

            if(rval == Tool.NULL_DOUBLE) {
               continue;
            }

            String srval = Tool.toString(rval);
            found = not;

            switch(op) {
            case "STARTSWITH":
               if(srval.startsWith(sdval)) {
                  found = !found;
               }
               break;
            case "CONTAINS":
            case "LIKE": // number contains no wildcard so is same as contains
               if(srval.contains(sdval)) {
                  found = !found;
               }
               break;
            }

            if(found) {
               set.add(i);
            }
         }
      }

      set.complete();

      return set;
   }

   /**
    * Get a bit set for all the rows in the index.
    */
   @Override
   public BitSet getAllRows(boolean cnull) {
      BitSet bitset = new BitSet();
      bitset.set(0, rcnt);
      return bitset;
   }

   /**
    * Get the value to use to compare with the column array values.
    * @param val the condition value.
    */
   protected double getComparisonValue(double val) {
      return val;
   }

   /**
    * Get the column value as a measure.
    */
   @Override
   public final double getMeasureValue(int idx) {
      return getValue(idx);
   }

   @Override
   public abstract long getDimValue(int idx);

   /**
    * Get the value at the specified index.
    */
   @Override
   public abstract double getValue(int r);

   /**
    * Set the value at the specified index.
    */
   @Override
   public abstract void setValue(int idx, double value);

   /**
    * Get a fragment of the column containing the row.
    * @index fragment index.
    * @return the fragment array value.
    */
   public abstract double[] getFragment(int index);

   /**
    * Number of bytes per value.
    */
   protected abstract int bytesPer();

   /**
    * Get the number of rows in the column.
    */
   protected final int getRowCount() {
      return rcnt;
   }

   /**
    * Set the number of rows (items) in the column.
    */
   @Override
   public void setRowCount(int rcnt) {
      if(this.rcnt < rcnt) {
         throw new RuntimeException("Rows can only be reduced in MVMeasureColumn");
      }

      this.rcnt = rcnt;
   }

   /**
    * Get the data length of this dimension index.
    */
   @Override
   public int getLength() {
      return rcnt * bytesPer() + 4;
   }

   /**
    * Get the number of bits per value.
    */
   @Override
   public int getBits() {
      return bytesPer() * 8;
   }

   /**
    * Called after this index is swapped to file. Clear the in-memory data now.
    */
   @Override
   protected void swapped() {
   }

   private static class CacheHolder {
      public CacheHolder(BitSetCache rowCache, long modTime) {
         this.rowCache = rowCache;
         this.modTime = modTime;
      }

      public BitSetCache rowCache;
      public long modTime;
   }

   static final int BLOCK_SIZE = 0x20000;
   static final int BLOCK_MASK = 0x1FFFF;
   static final int BLOCK_BITS = 17;

   private static ConcurrentMap<String,CacheHolder> rowCaches = new ConcurrentHashMap<>();

   private int rcnt;
}
