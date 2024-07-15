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
package inetsoft.graph.data;

import inetsoft.test.SreeHome;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SreeHome()
class PairsDataSetTest {
   @Test
   void test1() {
      DefaultDataSet tbl1 = new DefaultDataSet(new Object[][]{
         { "col1", "col2", "col3" },
         { "a", 1, 5.0 },
         { "b", 3, 10.0 },
         { "b", 1, 2.5 },
         { "c", 1, 3.0 }
      });
      PairsDataSet tbl2 = new PairsDataSet(tbl1, "col2", "col3");
      System.err.println("8:");
      //inetsoft.graph.internal.GDebug.printDataSet(tbl2);
      /*
     col1	MeasureName	MeasureValue
     a	col1	1
     a	col2	5.0
     b	col1	3
     b	col2	10.0
     b	col1	1
     b	col2	2.5
     c	col1	1
     c	col2	3.0
      */

      assertEquals(tbl2.getColCount(), 7);
   }

   @Test
   void test2() {
      DefaultDataSet tbl1 = new DefaultDataSet(new Object[][]{
         { "col1", "col2", "col3" },
         { 100, 1, 5.0 },
         { 200, 3, 10.0 },
         { 300, 1, 2.5 },
         { 400, 1, 3.0 }
      });
      PairsDataSet tbl2 = new PairsDataSet(tbl1, new String[]{ "col1" },
                                           new String[]{ "col2", "col3" });
      //inetsoft.graph.internal.GDebug.printDataSet(tbl2);
      /*
    XMeasureName	YMeasureName	XMeasureValue	YMeasureValue
    col1	col2	100	1
    col1	col3	100	5.0
    col1	col2	200	3
    col1	col3	200	10.0
    col1	col2	300	1
    col1	col3	300	2.5
    col1	col2	400	1
    col1	col3	400	3.0
      */

      assertEquals(tbl2.getColCount(), 7);
   }
}
