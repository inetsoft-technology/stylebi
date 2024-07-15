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
package inetsoft.report.composition.graph;

import inetsoft.graph.data.*;
import inetsoft.report.filter.ConditionGroup;
import inetsoft.report.internal.SubColumns;
import inetsoft.uql.*;
import inetsoft.uql.asset.ColumnRef;
import inetsoft.uql.asset.internal.AssetUtil;
import inetsoft.uql.asset.internal.ConditionUtil;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.erm.DataRef;
import inetsoft.uql.viewsheet.VSDataRef;
import inetsoft.uql.viewsheet.graph.GeoRef;
import inetsoft.util.Tool;

import java.util.*;

/**
 * A GraphConditionGroup stores a list of conditions to be applied to
 * the chart.
 *
 * @version 10.0
 * @author InetSoft Technology Corp
 */
public class GraphConditionGroup extends ConditionGroup {
   /**
    * Create a GraphConditionGroup.
    * @param conds the specified condition.
    */
   public GraphConditionGroup(ConditionList conds) {
      super();
      this.conds = conds;
   }

   /**
    * Test if use the original data.
    */
   public boolean isOriginal() {
      return original;
   }

   /**
    * Set whether use the original data.
    */
   public void setOriginal(boolean original) {
      this.original = original;
   }

   /**
    * Evaluate the condition group with the specified row in chart lens.
    * @param lens the chart lens used for evaluation.
    * @param row the row number of the chart lens.
    */
   public boolean evaluate(DataSet lens, int row) {
      SubColumns sub = getSubColumns(lens, row);
      GraphConditionGroupImpl impl = groups.get(sub);

      if(impl == null) {
         impl = new GraphConditionGroupImpl(lens, this.conds, sub);
         groups.put(sub, impl);
      }

      return impl.evaluate(lens, row);
   }

   /**
    * Get range column.
    */
   private SubColumns getSubColumns(DataSet set, int row) {
      if(set instanceof DataSetFilter) {
         row = ((DataSetFilter) set).getRootRow(row);
         set = ((DataSetFilter) set).getRootDataSet();
      }

      if(row < 0 || !(set instanceof VSDataSet)) {
         return null;
      }

      return ((VSDataSet) set).getSubColumns(row);
   }

   private class GraphConditionGroupImpl extends ConditionGroup {
      public GraphConditionGroupImpl(DataSet chart, ConditionList conds, SubColumns sub) {
         super();

         // after filter, the conds may be empty, this can be indicated by
         // "condition" property, when empty, evaluate result will be always
         // false, this will match Tableau
         conds = filter(chart, conds, sub);
         setNotFoundResult(false);
         conds.validate(false);

         ColumnSelection columns = new ColumnSelection();

         for(int i = 0; i < conds.getSize(); i++) {
            HierarchyItem item = conds.getItem(i);

            if(item instanceof ConditionItem) {
               ConditionItem cond = (ConditionItem) item;
               DataRef attr = cond.getAttribute();
               int col = findColumn(chart, attr);

               if(col < 0) {
                  // in chart highlight, we use base name of Geo(col), but the column name
                  // in dataset is Geo(col). (44768)
                  DataRef geoRef = new AttributeRef(GeoRef.wrapGeoName(attr.getName()));
                  col = findColumn(chart, geoRef);

                  if(col >= 0) {
                     attr = geoRef;
                     cond.setAttribute(attr);
                  }
               }

               if(col >= 0) {
                  columns.addAttribute(attr);
               }
            }
         }

         // remove missing columns
         conds.validate(columns);

         for(int i = 0; i < conds.getSize(); i++) {
            HierarchyItem item = conds.getItem(i);

            if(item instanceof ConditionItem) {
               ConditionItem cond = (ConditionItem) item;
               DataRef attr = cond.getAttribute();
               int col = findColumn(chart, attr);

               XCondition condition = (XCondition) cond.getXCondition().clone();
               conditions.add(condition);
               addCondition(col, condition, cond.getLevel());
               execExpressionValues(attr, condition, getQuerySandbox());
            }

            if(item instanceof JunctionOperator) {
               JunctionOperator op = (JunctionOperator) item;
               addOperator(op.getJunction(), op.getLevel());
            }
         }
      }

      /**
       * Evaluate the condition group with the specified row in chart lens.
       * @param lens the chart lens used for evaluation.
       * @param row the row number of the chart lens.
       */
      public boolean evaluate(DataSet lens, int row) {
         if(conditions.isEmpty()) {
            return false;
         }

         Object[] values = new Object[lens.getColCount()];
         VSDataSet root = null;
         int ridx = -1;

         if(original) {
            DataSet root0 = null;

            if(lens instanceof DataSetFilter) {
               root0 = ((DataSetFilter) lens).getRootDataSet();
               ridx = ((DataSetFilter) lens).getRootRow(row);
            }
            else {
               root0 = lens;
               ridx = row;
            }

            if(root0 instanceof PairsDataSet) {
               ridx = ((PairsDataSet) root0).getBaseRow(ridx);
               root0 = ((PairsDataSet) root0).getDataSet();
            }

            if(root0 instanceof VSDataSet) {
               root = (VSDataSet) root0;
            }
         }

         // for date, user may like to define highlight condition using date,
         // so for this case we should call getData to fetch value; meanwhile
         // user may want to define brushing condition using number, then for
         // this case we should call getOriginalData to fetch value
         for(int i = 0; i < values.length; i++) {
            int cidx = ridx < 0 ? -1 : (!(lens instanceof DataSetFilter) ?
               i : ((DataSetFilter) lens).getRootCol(i));
            values[i] = original && root != null && ridx >= 0 && ridx < root.getRowCount() &&
               cidx >= 0 &&  cidx < root.getColCount() ? root.getOriginalData(cidx, ridx) :
               lens.getData(i, row);
         }

         if(hasField) {
            if(colmap == null) {
               colmap = new int[fieldmap.size()][];

               for(int i = 0; i < fieldmap.size(); i++) {
                  DataRef[] refs = fieldmap.get(i);
                  colmap[i] = (refs == null) ? new int[0] : new int[refs.length];

                  for(int j = 0; refs != null && j < refs.length; j++) {
                     colmap[i][j] = findColumn(lens, refs[j]);
                  }
               }
            }

            // replace condition values with field value
            for(int i = 0, cidx = 0; i < fieldmap.size(); i++) {
               int[] cols = colmap[i];

               if(cols.length == 0) {
                  continue;
               }

               XCondition condition = conditions.get(cidx++);

               if(condition instanceof Condition) {
                  Condition cond = (Condition) condition;

                  for(int k = 0; k < cols.length; k++) {
                     int index = cols[k];

                     if(index == THIS_FIELD) {
                        index = colIdx;
                     }

                     if(index >= 0 && index < values.length) {
                        cond.setValue(k, values[index]);
                     }
                  }
               }
            }
         }

         return evaluate0(values);
      }

      /**
       * Find the column index of the specified DataRef.
       */
      private int findColumn(DataSet chart, DataRef ref) {
         if(ref == null || chart == null) {
            return -1;
         }

         String name = ref.getName();
         String attr = ref.getAttribute();
         String fullname = ref instanceof VSDataRef ? ((VSDataRef) ref).getFullName() : "";
         String cap = ref instanceof ColumnRef ?
            ((ColumnRef) ref).getCaption() : null;
         cap = cap == null ? name : cap;

         for(int i = 0; i < chart.getColCount(); i++) {
            String header = chart.getHeader(i);
            String baseHeader = getBaseName(header);

            if(Tool.equals(header, name) || Tool.equals(header, attr) ||
               Tool.equals(header, cap) || Tool.equals(header, fullname) ||
               Tool.equals(baseHeader, name) || Tool.equals(baseHeader, attr) ||
               Tool.equals(baseHeader, cap) || Tool.equals(baseHeader, fullname))
            {
               return i;
            }

            // @by alam, for date type which transformed by VS10_2Transformer.
            if((ref.getRefType() & DataRef.CUBE_TIME_DIMENSION) ==
               DataRef.CUBE_TIME_DIMENSION)
            {
               if(header.endsWith(cap)) {
                  return i;
               }
            }
         }

         return -1;
      }

      /**
       * Get the field's name without the "Group" tag.
       */
      private String getBaseName(String name) {
         if(name == null) {
            return null;
         }

         String originalName = name;

         if(!name.startsWith("DataGroup") && !name.startsWith("ColorGroup") &&
            !name.startsWith("ShapeGroup") && !name.startsWith("SizeGroup") &&
            !name.startsWith("TextGroup"))
         {
            return originalName;
         }

         int idx = name.indexOf('(');
         int last = name.lastIndexOf(')');

         if(idx > 0 && last > idx) {
            return name.substring(idx + 1, last);
         }

         return originalName;
      }

      private ConditionList filter(final DataSet chart, ConditionList conds, final SubColumns sub) {
         AssetUtil.Filter filter = sub == null ? null : new AssetUtil.Filter() {
            @Override
            public boolean keep(DataRef attr) {
               int col = findColumn(chart, attr);
               String header = col < 0 ? null : chart.getHeader(col);
               return header == null || sub.lazyContains(header);
            }
         };
         return ConditionUtil.filter(conds, filter);
      }

      private int colIdx = -1;
      private List<XCondition> conditions = new ArrayList<>();
   }

   public String toString() {
      return "GraphConditionGroup" + super.hashCode() + "[" + conds + "]";
   }

   /**
    * Getter of asset query sandbox.
    */
   public Object getQuerySandbox() {
      return querySandbox;
   }

   /**
    * Setter of asset query sandbox.
    */
   public void setQuerySandbox(Object box) {
      this.querySandbox = box;
   }

   private ConditionList conds;
   private boolean original;
   private Map<SubColumns, GraphConditionGroupImpl> groups = new HashMap();
   private Object querySandbox;
}
