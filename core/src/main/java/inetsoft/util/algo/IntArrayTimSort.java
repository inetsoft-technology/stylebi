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
package inetsoft.util.algo;

/**
 * This is a modified tim sort that sorts an integer array using a comparator instead of
 * comparing integer values.
 */
public class IntArrayTimSort extends IntArraySort {
   private static final int MIN_MERGE = 32;
   private int[] a;
   private IntComparator c;
   private static final int  MIN_GALLOP = 7;
   private int minGallop = MIN_GALLOP;
   private static final int INITIAL_TMP_STORAGE_LENGTH = 256;
   private int[] tmp;
   private int tmpBase; // base of tmp array slice
   private int tmpLen;  // length of tmp array slice
   private int stackSize = 0;  // Number of pending runs on stack
   private int[] runBase;
   private int[] runLen;

   private void init(int[] a, IntComparator c) {
      this.a = a;
      this.c = c;

      int len = a.length;
      int tlen = (len < 2 * INITIAL_TMP_STORAGE_LENGTH) ?
         len >>> 1 : INITIAL_TMP_STORAGE_LENGTH;
      int[] newArray = new int[tlen];
      tmp = newArray;
      tmpBase = 0;
      tmpLen = tlen;

      int stackLen = (len < 120  ?  5 :
                      len < 1542  ? 10 :
                      len < 119151  ? 24 : 49);
      runBase = new int[stackLen];
      runLen = new int[stackLen];
   }

   public int[] sort(int[] a, int lo, int hi, IntComparator c) {
      int nRemaining  = hi - lo;
      if(nRemaining < 2) return a;

      // If array is small, do a "mini-TimSort" with no merges
      if(nRemaining < MIN_MERGE) {
         int initRunLen = countRunAndMakeAscending(a, lo, hi, c);
         binarySort(a, lo, hi, lo + initRunLen, c);
         return a;
      }

      init(a, c);
      int minRun = minRunLength(nRemaining);
      do {
         int runLen = countRunAndMakeAscending(a, lo, hi, c);

         if(cancelled) {
            return a;
         }

         if(runLen < minRun) {
            int force = nRemaining <= minRun ? nRemaining : minRun;
            binarySort(a, lo, lo + force, lo + runLen, c);
            runLen = force;
         }

         pushRun(lo, runLen);
         mergeCollapse();

         lo += runLen;
         nRemaining -= runLen;
      } while(nRemaining != 0);

      mergeForceCollapse();
      return a;
   }

   @SuppressWarnings("fallthrough")
   private static void binarySort(int[] a, int lo, int hi, int start, IntComparator c) {
      if(start == lo) start++;
      for ( ; start < hi; start++) {
         int pivot = a[start];

         int left = lo;
         int right = start;
         while(left < right) {
            int mid = (left + right) >>> 1;
            if(c.compare(pivot, a[mid]) < 0) {
               right = mid;
            }
            else {
               left = mid + 1;
            }
         }

         int n = start - left;
         switch (n) {
         case 2:  a[left + 2] = a[left + 1];
         case 1:  a[left + 1] = a[left];
            break;
         default: System.arraycopy(a, left, a, left + 1, n);
         }
         a[left] = pivot;
      }
   }

   private static int countRunAndMakeAscending(int[] a, int lo, int hi,
                                               IntComparator c) {
      int runHi = lo + 1;
      if(runHi == hi)
      return 1;

      if(c.compare(a[runHi++], a[lo]) < 0) {
         while(runHi < hi && c.compare(a[runHi], a[runHi - 1]) < 0)
         runHi++;
         reverseRange(a, lo, runHi);
      }
      else {
         while(runHi < hi && c.compare(a[runHi], a[runHi - 1]) >= 0)
         runHi++;
      }

      return runHi - lo;
   }

   private static void reverseRange(int[] a, int lo, int hi) {
      hi--;
      while(lo < hi) {
         int t = a[lo];
         a[lo++] = a[hi];
         a[hi--] = t;
      }
   }

   private static int minRunLength(int n) {
      int r = 0;
      while(n >= MIN_MERGE) {
         r |= (n & 1);
         n >>= 1;
      }
      return n + r;
   }

   private void pushRun(int runBase, int runLen) {
      this.runBase[stackSize] = runBase;
      this.runLen[stackSize] = runLen;
      stackSize++;
   }

   private void mergeCollapse() {
      while(stackSize > 1) {
         int n = stackSize - 2;
         if(n > 0 && runLen[n-1] <= runLen[n] + runLen[n+1]) {
            if(runLen[n - 1] < runLen[n + 1])
            n--;
            mergeAt(n);
         } else if(runLen[n] <= runLen[n + 1]) {
            mergeAt(n);
         } else {
            break;
         }
      }
   }

   private void mergeForceCollapse() {
      while(stackSize > 1) {
         int n = stackSize - 2;
         if(n > 0 && runLen[n - 1] < runLen[n + 1])
         n--;
         mergeAt(n);
      }
   }

   private void mergeAt(int i) {
      int base1 = runBase[i];
      int len1 = runLen[i];
      int base2 = runBase[i + 1];
      int len2 = runLen[i + 1];

      runLen[i] = len1 + len2;
      if(i == stackSize - 3) {
         runBase[i + 1] = runBase[i + 2];
         runLen[i + 1] = runLen[i + 2];
      }
      stackSize--;

      int k = gallopRight(a[base2], a, base1, len1, 0, c);
      base1 += k;
      len1 -= k;
      if(len1 == 0) return;

      len2 = gallopLeft(a[base1 + len1 - 1], a, base2, len2, len2 - 1, c);
      if(len2 == 0) return;

      if(len1 <= len2)
      mergeLo(base1, len1, base2, len2);
      else
      mergeHi(base1, len1, base2, len2);
   }

   private static int gallopLeft(int key, int[] a, int base, int len, int hint,
                                 IntComparator c) {
      int lastOfs = 0;
      int ofs = 1;
      if(c.compare(key, a[base + hint]) > 0) {
         int maxOfs = len - hint;
         while(ofs < maxOfs && c.compare(key, a[base + hint + ofs]) > 0) {
            lastOfs = ofs;
            ofs = (ofs << 1) + 1;
            if(ofs <= 0) ofs = maxOfs;
         }
         if(ofs > maxOfs) ofs = maxOfs;

         lastOfs += hint;
         ofs += hint;
      } else {
         final int maxOfs = hint + 1;
         while(ofs < maxOfs && c.compare(key, a[base + hint - ofs]) <= 0) {
            lastOfs = ofs;
            ofs = (ofs << 1) + 1;
            if(ofs <= 0) ofs = maxOfs;
         }
         if(ofs > maxOfs) ofs = maxOfs;

         int tmp = lastOfs;
         lastOfs = hint - ofs;
         ofs = hint - tmp;
      }

      lastOfs++;
      while(lastOfs < ofs) {
         int m = lastOfs + ((ofs - lastOfs) >>> 1);

         if(c.compare(key, a[base + m]) > 0)
         lastOfs = m + 1;
         else
         ofs = m;
      }
      return ofs;
   }

   private static int gallopRight(int key, int[] a, int base, int len,
                                     int hint, IntComparator c) {
      int ofs = 1;
      int lastOfs = 0;
      if(c.compare(key, a[base + hint]) < 0) {
         int maxOfs = hint + 1;
         while(ofs < maxOfs && c.compare(key, a[base + hint - ofs]) < 0) {
            lastOfs = ofs;
            ofs = (ofs << 1) + 1;
            if(ofs <= 0)
            ofs = maxOfs;
         }
         if(ofs > maxOfs) ofs = maxOfs;

         int tmp = lastOfs;
         lastOfs = hint - ofs;
         ofs = hint - tmp;
      } else {
         int maxOfs = len - hint;
         while(ofs < maxOfs && c.compare(key, a[base + hint + ofs]) >= 0) {
            lastOfs = ofs;
            ofs = (ofs << 1) + 1;
            if(ofs <= 0) ofs = maxOfs;
         }
         if(ofs > maxOfs)
         ofs = maxOfs;

         lastOfs += hint;
         ofs += hint;
      }

      lastOfs++;
      while(lastOfs < ofs) {
         int m = lastOfs + ((ofs - lastOfs) >>> 1);

         if(c.compare(key, a[base + m]) < 0)
         ofs = m;
         else
         lastOfs = m + 1;
      }
      return ofs;
   }

   private void mergeLo(int base1, int len1, int base2, int len2) {
      int[] a = this.a;
      int[] tmp = ensureCapacity(len1);
      int cursor1 = tmpBase;
      int cursor2 = base2;
      int dest = base1;
      System.arraycopy(a, base1, tmp, cursor1, len1);

      a[dest++] = a[cursor2++];
      if(--len2 == 0) {
         System.arraycopy(tmp, cursor1, a, dest, len1);
         return;
      }
      if(len1 == 1) {
         System.arraycopy(a, cursor2, a, dest, len2);
         a[dest + len2] = tmp[cursor1];
         return;
      }

      IntComparator c = this.c;
      int minGallop = this.minGallop;
      outer:
      while(true) {
         int count1 = 0;
         int count2 = 0;

         do {
            if(c.compare(a[cursor2], tmp[cursor1]) < 0) {
               a[dest++] = a[cursor2++];
               count2++;
               count1 = 0;
               if(--len2 == 0)
               break outer;
            } else {
               a[dest++] = tmp[cursor1++];
               count1++;
               count2 = 0;
               if(--len1 == 1)
               break outer;
            }
         } while((count1 | count2) < minGallop);

         do {
            count1 = gallopRight(a[cursor2], tmp, cursor1, len1, 0, c);
            if(count1 != 0) {
               System.arraycopy(tmp, cursor1, a, dest, count1);
               dest += count1;
               cursor1 += count1;
               len1 -= count1;
               if(len1 <= 1) break outer;
            }
            a[dest++] = a[cursor2++];
            if(--len2 == 0)
            break outer;

            count2 = gallopLeft(tmp[cursor1], a, cursor2, len2, 0, c);
            if(count2 != 0) {
               System.arraycopy(a, cursor2, a, dest, count2);
               dest += count2;
               cursor2 += count2;
               len2 -= count2;
               if(len2 == 0)
               break outer;
            }
            a[dest++] = tmp[cursor1++];
            if(--len1 == 1)
            break outer;
            minGallop--;
         } while(count1 >= MIN_GALLOP | count2 >= MIN_GALLOP);
         if(minGallop < 0)
         minGallop = 0;
         minGallop += 2;
      }
      this.minGallop = minGallop < 1 ? 1 : minGallop;

      if(len1 == 1) {
         System.arraycopy(a, cursor2, a, dest, len2);
         a[dest + len2] = tmp[cursor1];
      } else if(len1 == 0) {
         throw new IllegalArgumentException(
            "Comparison method violates its general contract!");
      } else {
         System.arraycopy(tmp, cursor1, a, dest, len1);
      }
   }

   private void mergeHi(int base1, int len1, int base2, int len2) {
      int[] a = this.a;
      int[] tmp = ensureCapacity(len2);
      int tmpBase = this.tmpBase;
      System.arraycopy(a, base2, tmp, tmpBase, len2);

      int cursor1 = base1 + len1 - 1;
      int cursor2 = tmpBase + len2 - 1;
      int dest = base2 + len2 - 1;

      a[dest--] = a[cursor1--];
      if(--len1 == 0) {
         System.arraycopy(tmp, tmpBase, a, dest - (len2 - 1), len2);
         return;
      }
      if(len2 == 1) {
         dest -= len1;
         cursor1 -= len1;
         System.arraycopy(a, cursor1 + 1, a, dest + 1, len1);
         a[dest] = tmp[cursor2];
         return;
      }

      IntComparator c = this.c;
      int minGallop = this.minGallop;
      outer:
      while(true) {
         int count1 = 0;
         int count2 = 0;

         do {
            if(c.compare(tmp[cursor2], a[cursor1]) < 0) {
               a[dest--] = a[cursor1--];
               count1++;
               count2 = 0;
               if(--len1 == 0)
               break outer;
            } else {
               a[dest--] = tmp[cursor2--];
               count2++;
               count1 = 0;
               if(--len2 == 1)
               break outer;
            }
         } while((count1 | count2) < minGallop);

         do {
            count1 = len1 - gallopRight(tmp[cursor2], a, base1, len1, len1 - 1, c);
            if(count1 != 0) {
               dest -= count1;
               cursor1 -= count1;
               len1 -= count1;
               System.arraycopy(a, cursor1 + 1, a, dest + 1, count1);
               if(len1 == 0)
               break outer;
            }
            a[dest--] = tmp[cursor2--];
            if(--len2 == 1)
            break outer;

            count2 = len2 - gallopLeft(a[cursor1], tmp, tmpBase, len2, len2 - 1, c);
            if(count2 != 0) {
               dest -= count2;
               cursor2 -= count2;
               len2 -= count2;
               System.arraycopy(tmp, cursor2 + 1, a, dest + 1, count2);
               if(len2 <= 1)
               break outer;
            }
            a[dest--] = a[cursor1--];
            if(--len1 == 0)
            break outer;
            minGallop--;
         } while(count1 >= MIN_GALLOP | count2 >= MIN_GALLOP);
         if(minGallop < 0)
         minGallop = 0;
         minGallop += 2;
      }
      this.minGallop = minGallop < 1 ? 1 : minGallop;

      if(len2 == 1) {
         dest -= len1;
         cursor1 -= len1;
         System.arraycopy(a, cursor1 + 1, a, dest + 1, len1);
         a[dest] = tmp[cursor2];
      } else if(len2 == 0) {
         throw new IllegalArgumentException(
            "Comparison method violates its general contract!");
      } else {
         System.arraycopy(tmp, tmpBase, a, dest - (len2 - 1), len2);
      }
   }

   private int[] ensureCapacity(int minCapacity) {
      if(tmpLen < minCapacity) {
         int newSize = minCapacity;
         newSize |= newSize >> 1;
         newSize |= newSize >> 2;
         newSize |= newSize >> 4;
         newSize |= newSize >> 8;
         newSize |= newSize >> 16;
         newSize++;

         if(newSize < 0) {
            newSize = minCapacity;
         }
         else {
            newSize = Math.min(newSize, a.length >>> 1);
         }

         int[] newArray = new int[newSize];
         tmp = newArray;
         tmpLen = newSize;
         tmpBase = 0;
      }
      return tmp;
   }
}
