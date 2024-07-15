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
package inetsoft.graph.aesthetic;

import com.inetsoft.build.tern.TernMethod;
import inetsoft.graph.data.DataSet;
import inetsoft.util.CoreTool;

import java.util.*;

/**
 * This class defines the common API for all text frames.
 *
 * @version 10.0
 * @author InetSoft Technology
 */
public abstract class TextFrame extends VisualFrame {
   /**
    * Check if the legend frame should be shown as a legend. The default
    * implementation will just check whether there are multiple labels.
    */
   @Override
   @TernMethod
   public boolean isVisible() {
      // text frame is always invisible
      return false;
   }

   /**
    * Get the values of the mapped by this frame.
    */
   @Override
   @TernMethod
   public Object[] getValues() {
      // text frame is always invisible
      throw new RuntimeException("Unsupported method called!");
   }

   /**
    * Get the title to show on the legend.
    */
   @Override
   @TernMethod
   public String getTitle() {
      // text frame is always invisible
      throw new RuntimeException("Unsupported method called!");
   }

   /**
    * Get the aliased values.
    */
   @TernMethod
   public Collection getKeys() {
      return new ArrayList();
   }

   /**
    * Get the text for the specified cell.
    * @param data the specified dataset.
    * @param col the specified column name.
    * @param row the specified row index.
    */
   public Object getText(DataSet data, String col, int row) {
      if(!isTextVisible(data, row)) {
         return null;
      }

      String fld = getField();
      Object val = fld == null ? (col == null ? null : data.getData(col, row))
         : data.getData(fld, row);

      return getText(val);
   }

   /**
    * Get the text for the specified value.
    */
   @TernMethod
   public Object getText(Object val) {
      return val;
   }

   /**
    * Set the column to compare against visible values. The setVisibleValues or
    * setVisibleRange must be called to set the visibility condition.
    */
   @TernMethod
   public void setVisibleField(String vfield) {
      this.vfield = vfield;
   }

   /**
    * Get the column to compare against visible values.
    */
   @TernMethod
   public String getVisibleField() {
      return vfield;
   }

   /**
    * Set the values to show label. The values are compared against the values
    * from the column specified by setVisibleField. If the value matches, the
    * label is shown, otherwise the label is ignored.
    */
   @TernMethod
   public void setVisibleValues(Object ...vals) {
      vset.clear();

      for(Object val : vals) {
         vset.add(val);
      }
   }

   /**
    * Get the values to show text label.
    * @see TextFrame#setVisibleValues(Object...)
    */
   @TernMethod
   public Object[] getVisibleValues() {
      return vset.toArray();
   }

   /**
    * Set the range of value to show label. The range is compared against the
    * values from the column specified by setVisibleField. If the value is in
    * the range (inclusive), the label is shown, otherwise the label is ignored.
    * @param lo date or number, the lower bound of the range, or null to ignore.
    * @param hi date or number, the upper bound of the range, or null to ignore.
    */
   public void setVisibleRange(Comparable lo, Comparable hi) {
      vrange = new Comparable[] {lo, hi};
   }

   /**
    * Get the visible range as [lower, upper].
    */
   public Comparable[] getVisibleRange() {
      return vrange;
   }

   /**
    * Check if value matches visible values.
    */
   protected boolean isTextVisible(DataSet data, int row) {
      if(vfield == null) {
         return true;
      }

      Object val = data.getData(vfield, row);

      if(vrange != null) {
         if(!(val instanceof Comparable)) {
            return false;
         }

         Comparable v = (Comparable) val;

         return (vrange[0] == null || compare(v, vrange[0]) >= 0) &&
            (vrange[1] == null || compare(v, vrange[1]) <= 0);
      }
      else {
         return vset.contains(val);
      }
   }

   /**
    * Compare two values.
    */
   private int compare(Comparable v1, Comparable v2) {
      if(v1.getClass().equals(v2.getClass())) {
         return v1.compareTo(v2);
      }

      // Double.compareTo(Integer) causes cast exception
      if(v1 instanceof Number && v2 instanceof Number) {
         double d1 = ((Number) v1).doubleValue();
         double d2 = ((Number) v2).doubleValue();

         return (Double.valueOf(d1)).compareTo(Double.valueOf(d2));
      }

      return v1.compareTo(v2);
   }

   @Override
   public boolean equals(Object obj) {
      if(!super.equals(obj)) {
         return false;
      }

      TextFrame frame = (TextFrame) obj;

      return CoreTool.equals(vfield, frame.vfield) &&
         CoreTool.equals(vset, frame.vset);
   }

   private String vfield; // visible value column
   private Set vset = new HashSet();  // visible values
   private Comparable[] vrange; // numeric range for visible values
}
