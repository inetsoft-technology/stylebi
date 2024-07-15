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
package inetsoft.util;

import org.roaringbitmap.RoaringBitmap;

import java.util.BitSet;

/**
 * This class implements a vector of bits that grows as needed.
 * Each component of the bit set has a boolean value.
 *
 * @version 10.3
 * @author InetSoft Technology Corp
 */
public class SparseBitSet extends BitSet {
   /**
    * Get the set of bitset.
    */
   protected RoaringBitmap getBitSet() {
      return bitset;
   }

   /**
    * Constructor.
    * Constructs a new set.
    */
   public SparseBitSet() {
      bitset = new RoaringBitmap();
   }

   /**
    * Performs a logical AND of this target bit set with the argument bit set.
    */
   public void and(SparseBitSet set) {
      bitset.and(set.getBitSet());
      max = -1;
   }

   /**
    * Clears all of the bits in this bit set whose corresponding bit is set
    * in the specified bit set.
    */
   public void andNot(SparseBitSet set) {
      bitset.andNot(set.getBitSet());
      max = -1;
   }

   /**
    * Removes the bit specified from the set.
    */
   @Override
   public void clear(int bit) {
      bitset.remove(bit);
      max = -1;
   }

   /**
    * Clone set and return this new set.
    */
   @Override
   public Object clone() {
      SparseBitSet set = new SparseBitSet();
      set.or(this);
      return set;
   }

   /**
    * Equality check.
    */
   public boolean equals(Object obj) {
      if(obj == this) {
         return true;
      }

      if(!(obj instanceof SparseBitSet)) {
         return false;
      }
      else {
         SparseBitSet other = (SparseBitSet) obj;
         return bitset.equals(other.getBitSet());
      }
   }

   /**
    * Checks if specific bit contained in set.
    */
   @Override
   public boolean get(int bit) {
      return bitset.contains(Integer.valueOf(bit));
   }

   /**
    * Return internal set hashcode.
    */
   public int hashCode() {
      return bitset.hashCode();
   }

   /**
    * Returns the maximum element in set + 1.
    */
   @Override
   public int length() {
      if(max != -1) {
         return max;
      }

      this.max = 0;

      // make sure set not empty
      // get maximum value +1
      if(bitset.getCardinality() > 0) {
         this.max = bitset.last() + 1;
      }

      return this.max;
   }

   /**
    * Performs a logical OR of this bit set with the bit set argument.
    */
   public void or(SparseBitSet set) {
      bitset.or(set.getBitSet());
      max = -1;
   }

   /**
    * Adds bit specified to set.
    */
   @Override
   public void set(int bit) {
      bitset.add(bit);
      max = -1;
   }

   @Override
   public int nextSetBit(int fromIndex) {
      return (int) bitset.nextValue(fromIndex);
   }

   @Override
   public int nextClearBit(int fromIndex) {
      return (int) bitset.nextAbsentValue(fromIndex);
   }

   /**
    * Return size of internal set.
    */
   @Override
   public int size() {
      return bitset.getCardinality();
   }

   /**
    * Return string representation of internal set.
    */
   public String toString() {
      return bitset.toString();
   }

   /**
    * Performs a logical XOR of this bit set with the bit set argument.
    */
   public void xor(SparseBitSet set) {
      bitset.xor(set.getBitSet());
      max = -1;
   }

   private RoaringBitmap bitset;
   // optimize, calculate max is expensive, cache it
   private int max = -1;
}