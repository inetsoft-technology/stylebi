/*
 * inetsoft-core - StyleBI is a business intelligence web application.
 * Copyright © 2024 InetSoft Technology (info@inetsoft.com)
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
 * You should have received a copy of the GNU Affrero General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package inetsoft.mv.data;

import inetsoft.util.swap.XSwapper;
import org.roaringbitmap.IntIterator;
import org.roaringbitmap.RoaringBitmap;

import java.io.*;
import java.nio.ByteBuffer;

/**
 * A bit set that represents a selected set of rows. This class uses
 * RoaringBitmap for the actual implementation.
 *
 * @author InetSoft Technology
 * @since  10.2
 */
public final class BitSet {
   /**
    * Creates a new instance of RowSet.
    */
   public BitSet() {
      this(new RoaringBitmap());
   }

   private BitSet(RoaringBitmap bitmap) {
      this.bitmap = bitmap;
   }

   /**
    * Add a row as being selected. This should only be called to add a row to
    * the end of all selected rows. The complete() must be called to commit the
    * changes.
    * @param rowIdx the zero-based index of the row.
    */
   public final void add(int rowIdx) {
      if(rowIdx == endIdx) {
         endIdx++;
      }
      else {
         commit();

         beginIdx = rowIdx;
         endIdx = rowIdx + 1;
      }
   }

   /**
    * Commit pending changes.
    */
   private void commit() {
      if(endIdx > beginIdx) {
         if(endIdx == beginIdx + 1) {
            bitmap.add((int) beginIdx);
         }
         else {
            bitmap.add(beginIdx, endIdx);
         }
      }
   }

   /**
    * Set the bits in the range (no-inclusize at end).
    */
   public final void set(int startrow, int endrow) {
      RoaringBitmap bits = new RoaringBitmap();
      bits.flip(startrow, endrow);
      bitmap.or(bits);
   }

   public void complete() {
      commit();
      beginIdx = endIdx;

      bitmap.trim();
      optimized = false;
   }

   /**
    * Sets a row as being selected.
    * @param rowIndex the zero-based index of the row.
    */
   public final void set(int rowIndex) {
      bitmap.add(rowIndex);
   }

   /**
    * Determines if a row is selected.
    * @param rowIndex the zero-based index of the row.
    * @return <tt>true</tt> if the row is selected; <tt>false</tt> otherwise.
    */
   public final boolean get(int rowIndex) {
      return bitmap.contains(rowIndex);
   }

   /**
    * Compact this bit set.
    */
   public final void compact() {
      bitmap.trim();
   }

   /**
    * Gets the row set that contains the rows in both this row set and another.
    * @param set the row set with which to compare.
    * @return the intersection of the row sets.
    */
   public final BitSet and(BitSet set) {
      return new BitSet(RoaringBitmap.and(bitmap, set.bitmap));
   }

   /**
    * Gets the row set that contains the rows contained in this row set but not
    * another.
    * @param set the row set with which to compare.
    * @return <tt>this</tt> subtract <tt>set</tt>.
    */
   public final BitSet andNot(BitSet set) {
      return new BitSet(RoaringBitmap.andNot(bitmap, set.bitmap));
   }

   /**
    * Gets the rows that are contained in this row set, another row set, or both
    * row sets.
    * @param set the row set with which to compare.
    * @return the union of <tt>this</tt> and <tt>set</tt>.
    */
   public final BitSet or(BitSet set) {
      return new BitSet(RoaringBitmap.or(bitmap, set.bitmap));
   }

   /**
    * Gets the rows that are contained in this row set or another row set, but
    * not both.
    * @param set the row set with which to compare.
    * @return the difference between <tt>this</tt> and <tt>set</tt>.
    */
   public final BitSet xor(BitSet set) {
      return new BitSet(RoaringBitmap.xor(bitmap, set.bitmap));
   }

   /**
    * Gets the number of rows selected in this set.
    * @return the row count.
    */
   public final int rowCount() {
      return bitmap.getCardinality();
   }

   /**
    * Check if any bit is set to true.
    */
   public final boolean isEmpty() {
      return bitmap.isEmpty();
   }

   /**
    * Get an iterator to go through the bit set to true.
    */
   public final IntIterator intIterator() {
      return bitmap.getIntIterator();
   }

   /**
    * Loads the contents of this row set from binary storage.
    */
   public void load(ByteBuffer buffer) throws IOException {
      int len = buffer.getInt();
      byte[] arr = new byte[len];
      buffer.get(arr);
      ByteArrayInputStream bin = new ByteArrayInputStream(arr);
      DataInputStream din = new DataInputStream(bin);
      deserialize0(din);
   }

   /**
    * Save this bit set.
    */
   public void save(ByteBuffer buffer) {
      ByteArrayOutputStream bout = new ByteArrayOutputStream();
      DataOutputStream dout = new DataOutputStream(bout);

      try {
         serialize0(dout);
      }
      catch(Exception ex) {
         // should never happen
         throw new RuntimeException(ex);
      }

      byte[] arr = bout.toByteArray();
      buffer.putInt(arr.length);
      buffer.put(arr);
   }

   /**
    * Read and restore serialized data.
    */
   public final void deserialize(InputStream input) throws IOException {
      deserialize0(new DataInputStream(input));
   }

   /**
    * Write serialized data.
    */
   public final void serialize(OutputStream output) throws IOException {
      serialize0(new DataOutputStream(output));
   }

   /**
    * Read and restore serialized data.
    */
   private void deserialize0(DataInputStream input) throws IOException {
      XSwapper.getSwapper().waitForMemory();
      bitmap.deserialize(input);
   }

   /**
    * Write serialized data.
    */
   private void serialize0(DataOutputStream output) throws IOException {
      runOptimize();
      bitmap.serialize(output);
   }

   /**
    * Get the length of this bit set.
    */
   public final int getLength() {
      runOptimize();
      return bitmap.serializedSizeInBytes() + 4;
   }

   private void runOptimize() {
      if(!optimized) {
         optimized = true;
         bitmap.runOptimize();
      }
   }

   public boolean equals(Object obj) {
      if(!(obj instanceof BitSet)) {
         return false;
      }

      return bitmap.equals(((BitSet) obj).bitmap);
   }

   public String toString() {
      return bitmap.toString();
   }

   private final RoaringBitmap bitmap;
   private transient boolean optimized = false;
   private transient long beginIdx = 0; // beginning of newly added rows
   private transient long endIdx = 0; // end of newly added rows (non-inclusive)
}
