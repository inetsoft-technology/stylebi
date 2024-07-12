/*
 * inetsoft-web - StyleBI is a business intelligence web application.
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
/**
 * Utility class for performing search operations on sorted arrays.
 */
export class BinarySearch<T> {
   constructor(private readonly sortedArray: T[],
               private readonly compareFn?: (a: T, b: T) => number)
   {
   }

   /**
    * @param sortedArray the sorted array to create a BinarySearch instance with.
    *
    * @return a BinarySearch of a number array with a default number compareFn.
    */
   public static numbers(sortedArray: number[]): BinarySearch<number> {
      const compareFn = (a: number, b: number) => a - b;
      return new BinarySearch(sortedArray, compareFn);
   }

   /**
    * @param element the value to match
    *
    * @return an index of the least element in this array greater than or equal to the given
    * element, or null if there is no such element.
    */
   ceiling(element: T): number | null {
      let lo = 0;
      let hi = this.sortedArray.length - 1;
      let ceilingCandidateIndex: number | null = null;

      while(lo <= hi) {
         const mid = Math.floor((hi + lo) / 2);
         const midElement = this.sortedArray[mid];
         const comparison = this.compareFn(midElement, element);

         if(comparison === 0) {
            ceilingCandidateIndex = mid;
            break;
         }
         else if(comparison < 0) {
            lo = mid + 1;
         }
         else { // comparison > 0
            ceilingCandidateIndex = mid;
            hi = mid - 1;
         }
      }

      return ceilingCandidateIndex;
   }
}
