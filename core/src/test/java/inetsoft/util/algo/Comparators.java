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
package inetsoft.util.algo;

import java.util.Collection;
import java.util.Comparator;

/**
 * Test utility class for testing that a comparator follows the transitivity contract:
 * if a > b and b > c then a > c.
 *
 * <p> Taken from <a href="https://stackoverflow.com/a/35000727/10497857">here</a>.
 *
 * @author Gili Tzabari
 */
public final class Comparators {
   private Comparators() {
      // prevent construction
   }

   /**
    * Verify that a comparator is transitive.
    *
    * @param <T>        the type being compared
    * @param comparator the comparator to test
    * @param elements   the elements to test against
    *
    * @throws AssertionError if the comparator is not transitive
    */
   public static <T> void verifyTransitivity(Comparator<T> comparator, Collection<T> elements) {
      for(T first : elements) {
         for(T second : elements) {
            int result1 = comparator.compare(first, second);
            int result2 = comparator.compare(second, first);

            if(result1 != -result2) {
               // Uncomment the following line to step through the failed case
               //comparator.compare(first, second);
               throw new AssertionError("compare(" + first + ", " + second + ") == " + result1 +
                                           " but swapping the parameters returns " + result2);
            }
         }
      }

      for(T first : elements) {
         for(T second : elements) {
            int firstGreaterThanSecond = comparator.compare(first, second);

            if(firstGreaterThanSecond <= 0) {
               continue;
            }

            for(T third : elements) {
               int secondGreaterThanThird = comparator.compare(second, third);

               if(secondGreaterThanThird <= 0) {
                  continue;
               }

               int firstGreaterThanThird = comparator.compare(first, third);

               if(firstGreaterThanThird <= 0) {
                  // Uncomment the following line to step through the failed case
//                  comparator.compare(first, third);
                  throw new AssertionError("compare(" + first + ", " + second + ") > 0, " +
                                              "compare(" + second + ", " + third + ") > 0, but compare(" + first + ", " + third + ") == " +
                                              firstGreaterThanThird);
               }
            }
         }
      }
   }
}
