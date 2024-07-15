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
package inetsoft.graph.data;

import java.util.Comparator;

/**
 * Combines two comparators. The second comparator has higher precedence. If it returns
 * equal, then the first comparator is used to resolve the comparison.
 */
public class CombinedDataSetComparator implements Comparator, DataSetComparator {
   public CombinedDataSetComparator(String col, Comparator c1, Comparator c2) {
      this.col = col;
      this.c1 = c1;
      this.c2 = c2;
   }

   @Override
   public Comparator getComparator(int row) {
      return this;
   }

   @Override
   public int compare(Object v1, Object v2) {
      int rc = c2.compare(v1, v2);

      if(rc == 0) {
         rc = c1.compare(v1, v2);
      }

      return rc;
   }

   @Override
   public int compare(DataSet data, int row1, int row2) {
      int rc = compare(c2, data, row1, row2);

      if(rc == 0) {
         rc = compare(c1, data, row1, row2);
      }

      return rc;
   }

   private int compare(Comparator c1, DataSet data, int row1, int row2) {
      return (c1 instanceof DataSetComparator)
         ? ((DataSetComparator) c1).compare(data, row1, row2)
         : c1.compare(data.getData(col, row1), data.getData(col, row2));
   }

   @Override
   public DataSetComparator getComparator(DataSet data) {
      return new CombinedDataSetComparator(col, DataSetComparator.getComparator(c1, data),
                                           DataSetComparator.getComparator(c2, data));
   }

   @Override
   public String toString() {
      return super.toString() + "[" + c1 + "," + c2 + "]";
   }

   private Comparator c1, c2;
   private String col;
}
