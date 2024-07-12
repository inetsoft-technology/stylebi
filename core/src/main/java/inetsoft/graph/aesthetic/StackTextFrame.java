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
package inetsoft.graph.aesthetic;

import com.inetsoft.build.tern.*;
import inetsoft.graph.data.*;
import inetsoft.graph.element.*;

import java.util.*;

/**
 * This class returns a value for each stack instead of every data point.
 *
 * @version 10.3
 * @author InetSoft Technology
 */
@TernClass(url = "#cshid=StackTextFrame")
public class StackTextFrame extends TextFrame implements CategoricalFrame {
   /**
    * Create a text frame. The field needs to be set by calling setField.
    */
   public StackTextFrame() {
   }

   /**
    * Create a text frame.
    * @param elem the corresponding element to extract dimensions.
    * @param vars the field(s) for calculating stack value.
    */
   @TernConstructor
   public StackTextFrame(GraphElement elem, String ...vars) {
      this();
      this.elem = elem;

      String[] dims = new String[elem.getDimCount()];

      for(int i = 0; i < dims.length; i++) {
         dims[i] = elem.getDim(i);
      }

      setDimensions(dims);
      this.vars = vars;
      // field set in CategoricalFrame will impact grouping (GraphElement.getAllGroupFields).
      // measures should not be treated as a dimension. (53706)
      //setField(vars != null && vars.length > 0 ? vars[0] : null);

      if(elem instanceof StackableElement) {
         setStackNegative(((StackableElement) elem).isStackNegative());
      }
   }

   /**
    * Set the dimensions to group data.
    */
   @TernMethod
   public void setDimensions(String[] dims) {
      this.dims = dims;
   }

   /**
    * Get the dimensions to group data.
    */
   @TernMethod
   public String[] getDimensions() {
      return dims;
   }

   /**
    * Check if negative values are stacked separately.
    */
   @TernMethod
   public boolean isStackNegative() {
      return negGrp;
   }

   /**
    * Set whether negative values are stacked separately. If true (default), the
    * negative values are stacked downward and positive values are stacked
    * upward. Otherwise, all values are accumulated together.
    */
   @TernMethod
   public void setStackNegative(boolean negGrp) {
      this.negGrp = negGrp;
   }

   /**
    * Check if this frame has been initialized and is ready to be used.
    */
   @Override
   @TernMethod
   public boolean isValid() {
      return true;
   }

   /**
    * Initialize the legend frame with values from the dataset.
    */
   @Override
   public void init(DataSet data) {
      // each dataset is initialized once (could be more than one datasets
      // in facet chart)
      if(!datasets.contains(data.hashCode())) {
         if(elem != null) {
            SortedDataSet sorted = elem.sortData(data, null);

            if(sorted != null) {
               data = sorted;
            }
         }

         initValues(data, null);
      }

      super.init(data);
   }

   /**
    * Get the text for the specified cell.
    * @param data the specified dataset.
    * @param col the specified column name.
    * @param row the specified row index.
    */
   @Override
   public Object getText(DataSet data, String col, int row) {
      if(!isTextVisible(data, row)) {
         return null;
      }

      String fld = getField();

      if(fld == null) {
         fld = col;
      }

      if(!datasets.contains(data.hashCode())) {
         initValues(data, fld);
      }

      String key = getKey(data, row, fld);
      String lastVar = lastVars.get(key);
      Integer topRow = rowmap.get(key);

      if(lastVar != null && col != null && !lastVar.equals(col)) {
         return null;
      }

      if(topRow == null || row != topRow) {
         return null;
      }

      return getText(vmap.get(key));
   }

   /**
    * Get the row index on the root dataset.
    * @param data the chartLens to plot using this element.
    * @param row the row index for the dataset.
    * @see GraphElement
    */
   private int getRootRowIndex(DataSet data, int row) {
      if(data instanceof DataSetFilter) {
         DataSetFilter filter = (DataSetFilter) data;
         return filter.getRootRow(row);
      }

      return row;
   }

   /**
    * Get the stack row indexes if stackmap contains index.
    * @param index the row index to lookup in the stackmap.
    * @return the row indexes of the same key.
    */
   @TernMethod
   public int[] getStackRowIndexes(int index) {
      Set<Object> keys = stackmap.keySet();

      for(Object key : keys) {
         Set<Integer> stackset = stackmap.get(key);

         if(stackset.contains(index)) {
            int[] indexes = new int[stackset.size()];
            int i = 0;

            for(Integer stackIndex : stackset) {
               indexes[i] = stackIndex;
               i++;
            }

            Arrays.sort(indexes);
            return indexes;
         }
      }

      return new int[] {index};
   }

   /**
    * Calculate group values.
    */
   private synchronized void initValues(DataSet data, String field) {
      String[] vars = this.vars;

      if(vars == null || vars.length == 0) {
         if(field != null) {
            vars = new String[] { field };
         }
      }

      if(vars == null || vars.length == 0) {
         return;
      }

      datasets.add(data.hashCode());
      SubDataSet subDataSet = data instanceof SubDataSet ? (SubDataSet) data : null;
      int endRow = elem.getEndRow(data);
      GraphtDataSelector selector = getGraphDataSelector();

      for(int i = elem.getStartRow(data); i < endRow; i++) {
         final int row = i;
         boolean allNull = Arrays.stream(vars)
            .map(v -> data.getData(v, row))
            .allMatch(v -> v == null);

         // ignore all null (null values are not plotted). (51357)
         if(allNull) {
            continue;
         }

         if(selector != null && !selector.accept(data, i, vars)) {
            continue;
         }

         for(String v : vars) {
            Object obj = data.getData(v, row);

            if(!(obj instanceof Number)) {
               continue;
            }

            double value = ((Number) obj).doubleValue();
            String key = getKey(data, i, v);
            Double gval = vmap.get(key);
            Set<Integer> stackset;

            if(gval == null) {
               gval = 0.0;
               stackset = new HashSet<>();
               stackmap.put(key, stackset);
            }
            else {
               stackset = stackmap.get(key);
            }

            int rootRow = getRootRowIndex(data, i);

            if(rootRow >= 0) {
               stackset.add(getRootRowIndex(data, i));
            }

            gval += ((Number) value).doubleValue();
            vmap.put(key, gval);

            if(subDataSet == null || subDataSet.getBaseRow(i) != -1 || !rowmap.containsKey(key)) {
               rowmap.put(key, i);

               // if there are null, lastVar may not be the last var in elem. check the data
               // and remember for later use. (49871)
               lastVars.put(key, v);
            }
         }
      }
   }

   /**
    * Get an unique key that identifies the dimensions on the table row.
    */
   private String getKey(DataSet data, int row, String var) {
      StringBuilder buf = new StringBuilder(data.hashCode() + "|");

      for(String dim : dims) {
         buf.append(data.getData(dim, row));
         buf.append("|");
      }

      if(negGrp) {
         Object val = data.getData(var, row);

         if(val instanceof Number && ((Number) val).doubleValue() < 0) {
            buf.append("negative");
         }
         // allow null
         else {
            buf.append("positive");
         }
      }

      return buf.toString();
   }

   public boolean equals(Object obj) {
      if(!super.equals(obj)) {
         return false;
      }

      StackTextFrame frame = (StackTextFrame) obj;
      return negGrp == frame.negGrp;
   }

   /**
    * Check if the value is assigned a static aesthetic value.
    */
   @Override
   @TernMethod
   public boolean isStatic(Object val) {
      return false;
   }

   @Override
   @TernMethod
   public Set<Object> getStaticValues() {
      return new HashSet<>();
   }

   @Override
   @TernMethod
   public void clearStatic() {
   }

   private String[] dims; // group dimensions
   private boolean negGrp = true;
   // key -> group value
   private Map<Object,Double> vmap = new HashMap<>();
   // key -> last row of a group
   private Map<Object,Integer> rowmap = new HashMap<>();
   // key -> last var of a group
   private Map<Object, String> lastVars = new HashMap<>();
   private Map<Object,Set<Integer>> stackmap = new HashMap<>();
   private Set<Integer> datasets = new HashSet<>(); // initialized datasets
   private GraphElement elem;
   private String[] vars;
   private static final long serialVersionUID = 1L;
}
