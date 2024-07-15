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

import inetsoft.graph.internal.ManualOrderComparer;
import inetsoft.uql.schema.XSchema;

import java.util.*;

/**
 * Create pairs of measures for scatter plot matrix.
 *
 * @hidden
 * @version 12.2
 * @author InetSoft Technology Corp
 */
public class PairsDataSet extends AbstractDataSetFilter {
   /**
    * Column name for the x measure name of the pair value.
    */
   public static final String XMEASURE_NAME = "_XMeasureName_";
   /**
    * Column name for the x measure value of the pair value.
    */
   public static final String XMEASURE_VALUE = "_XMeasureValue_";
   /**
    * Column name for the y measure name of the pair value.
    */
   public static final String YMEASURE_NAME = "_YMeasureName_";
   /**
    * Column name for the y measure value of the pair value.
    */
   public static final String YMEASURE_VALUE = "_YMeasureValue_";

   public PairsDataSet(DataSet data, String ...measures) {
      this(data, measures, measures);
   }

   public PairsDataSet(DataSet data, String[] xmeasures, String[] ymeasures) {
      super(data);

      this.data = data;
      this.xmeasures = Arrays.asList(xmeasures);
      this.ymeasures = Arrays.asList(ymeasures);
      // y label should stop from top instead of bottom
      Collections.reverse(this.ymeasures);

      this.measures = new HashSet<>(this.xmeasures);
      this.measures.addAll(this.ymeasures);
      createPairs(xmeasures, ymeasures);
      headers = new ArrayList<>();

      for(int i = 0; i < data.getColCount(); i++) {
         headers.add(data.getHeader(i));
      }

      headers.add(XMEASURE_NAME);
      headers.add(YMEASURE_NAME);
      headers.add(XMEASURE_VALUE);
      headers.add(YMEASURE_VALUE);
   }

   /**
    * Get the measures used to create the scatter matrix (defined on both x and y).
    */
   public Collection<String> getMeasures() {
      return measures;
   }

   @Override
   protected Object getData0(int col, int row) {
      int row0 = row / pairs.size();
      int col0 = getBaseCol(col);

      if(col0 >= 0) {
         return data.getData(col0, row0);
      }

      int col2 = col - data.getColCount();
      int pidx = row % pairs.size();

      switch(col2) {
      case 0:
      case 1:
         return pairs.get(pidx)[col2];
      default:
         return data.getData(pairIdxs.get(pidx)[col2 - 2], row0);
      }
   }

   @Override
   protected int getColCount0() {
      return data.getColCount() + 4;
   }

   @Override
   protected int getRowCount0() {
      return data.getRowCount() * pairs.size();
   }

   @Override
   protected int getRowCountUnprojected0() {
      return getRowCount0();
   }

   @Override
   protected int indexOfHeader0(String col, boolean all) {
      return headers.indexOf(col);
   }

   @Override
   protected String getHeader0(int col) {
      return headers.get(col);
   }

   @Override
   protected Class getType0(String col) {
      switch(col) {
      case XMEASURE_NAME:
      case YMEASURE_NAME:
         return String.class;
      case XMEASURE_VALUE:
      case YMEASURE_VALUE:
         return Double.class;
      default:
         return data.getType(col);
      }
   }

   @Override
   protected boolean isMeasure0(String col) {
      switch(col) {
      case XMEASURE_NAME:
      case YMEASURE_NAME:
         return false;
      case XMEASURE_VALUE:
      case YMEASURE_VALUE:
         return true;
      default:
         return data.isMeasure(col);
      }
   }

   @Override
   public int getBaseRow(int r) {
      return r / pairs.size();
   }

   /**
    * PairsDataSet is used as root data set so should not map row index to base.
    */
   @Override
   public int getRootRow(int r) {
     return r;
   }

   @Override
   public DataSet getRootDataSet() {
      return this;
   }

   @Override
   public int getBaseCol(int c) {
      return (c < 0 || c >= data.getColCount()) ? -1: c;
   }

   @Override
   public Comparator getComparator0(String col) {
      switch(col) {
      case XMEASURE_NAME:
         return new ManualOrderComparer(XSchema.STRING, xmeasures);
      case YMEASURE_NAME:
         return new ManualOrderComparer(XSchema.STRING, ymeasures);
      }

      return super.getComparator0(col);
   }

   /**
    * Create pairs of all combinations of measures.
    */
   private void createPairs(String[] xmeasures, String[] ymeasures) {
      pairs = new ArrayList<>();
      pairIdxs = new ArrayList<>();

      for(String m1 : xmeasures) {
         for(String m2 : ymeasures) {
            if(!m1.equals(m2)) {
               pairs.add(new String[] {m1, m2});
               pairIdxs.add(new int[] {data.indexOfHeader(m1), data.indexOfHeader(m2)});
            }
         }
      }
   }

   private Set<String> measures;
   private List<String> xmeasures, ymeasures;
   private List<String[]> pairs;
   private List<int[]> pairIdxs; // optimization, avoid indexOfHeader
   private DataSet data;
   private List<String> headers;
}
