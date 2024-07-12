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
package inetsoft.util.swap;

import inetsoft.test.SreeHome;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SreeHome()
class XSwappableObjectListTest {
   @Test
   void testList() {
      XSwappableObjectList list = new XSwappableObjectList(null);
      int n = 400000;
      long start = System.currentTimeMillis();
      String prefix = "list.item.test";

      for(int i = 0; i < n; i++) {
         list.add(prefix + i);
      }

      list.complete();

      try {
         Thread.sleep(1000);
      }
      catch(Exception ex) {
      }

      for(int i = 0; i < n; i++) {
         assertEquals(prefix + i, list.get(i));
      }

      long end = System.currentTimeMillis();
      System.err.println("time: " + (end - start));
   }
}
