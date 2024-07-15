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

import inetsoft.graph.aesthetic.ColorFrame;
import inetsoft.graph.data.DataSet;
import inetsoft.graph.data.DataSetFilter;
import inetsoft.report.filter.Highlight;
import inetsoft.report.filter.HighlightGroup;
import inetsoft.uql.Condition;
import inetsoft.uql.ConditionList;
import inetsoft.uql.asset.ColumnRef;
import inetsoft.uql.erm.DataRef;
import inetsoft.uql.xmla.MemberObject;
import inetsoft.uql.xmla.XMLAUtil;
import inetsoft.util.Tool;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;

/**
 * HLColorFrame, the color frame to apply highlight.
 *
 * @version 10.0
 * @author InetSoft Technology
 */
public class HLColorFrame extends ColorFrame {
   /**
    * Constructor.
    * @param col the specified measure to apply highlight, null to apply to
    * any measure.
    */
   public HLColorFrame(String col, HighlightGroup group, DataSet data) {
      this(col, group, data, false);
   }

   /**
    * Constructor.
    * @param col the specified measure to apply highlight, null to apply to
    * any measure.
    */
   public HLColorFrame(String col, HighlightGroup group, DataSet data, boolean original) {
      super();

      this.col = col;
      this.rconds = new GraphConditionGroup[0];
      this.highlights = new Highlight[0];
      this.original = original;

      if(data != null && group != null) {
         init(data, group);
      }

      setOriginal(original);
   }

   @Override
   public String getVisualField() {
      return col;
   }

   /**
    * Get the default color.
    */
   public Color getDefaultColor() {
      return def;
   }

   /**
    * Set the default color.
    */
   public void setDefaultColor(Color def) {
      this.def = def;
   }

   /**
    * Get the highlights in the frame.
    */
   public Highlight[] getHighlights() {
      return highlights;
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

      for(int i = 0; i < rconds.length; i++) {
         rconds[i].setOriginal(original);
      }
   }

   /**
    * Initialize the frame to create condition groups.
    */
   private void init(DataSet data, HighlightGroup group) {
      String[] names = group.getNames();
      List clist = new ArrayList();
      List hlist = new ArrayList();

      for(int i = 0; i < names.length; i++) {
         Highlight hl = group.getHighlight(names[i]);

         if(hl == null || hl.isEmpty()) {
            continue;
         }

         ConditionList conds = hl.getConditionGroup();

         // ignore empty ones
         if(conds == null || conds.isEmpty()) {
            continue;
         }

         // for cube, condition is always use caption value, so we don't need
         // to replace it to uname, for brush, it seems working correct too
         // because it is already used original values
         // fix bug1288616492092

         if(original) {
            replaceHLConditionValues(data, conds);
         }

         GraphConditionGroup rconds = new GraphConditionGroup(conds);
         clist.add(rconds);
         hlist.add(hl);
      }

      rconds = new GraphConditionGroup[clist.size()];
      clist.toArray(rconds);
      highlights = new Highlight[hlist.size()];
      hlist.toArray(highlights);
   }

   /**
    * Replace highlight condition values to member object unique name.
    */
   private void replaceHLConditionValues(DataSet data, ConditionList conds) {
      VSDataSet vdata = null;

      if(data instanceof DataSetFilter) {
         data = ((DataSetFilter) data).getRootDataSet();
      }

      if(data instanceof VSDataSet) {
         vdata = (VSDataSet) data;
      }

      if(vdata == null) {
         return;
      }

      for(int j = 0; j < conds.getSize(); j++) {
         DataRef ref = conds.getAttribute(j);

         if(ref == null) {
            continue;
         }

         if((ref.getRefType() & DataRef.CUBE) != DataRef.CUBE) {
            return;
         }

         int col = vdata.indexOfHeader(ref.getAttribute());

         if(col < 0) {
            if(ref instanceof ColumnRef) {
               col = vdata.indexOfHeader(((ColumnRef) ref).getHeaderName());
            }

            if(col < 0) {
               continue;
            }
         }

         Condition cond = conds.getCondition(j);

         if(cond == null) {
            continue;
         }

         for(int row = 0; row < vdata.getRowCount(); row++) {
            Object obj = vdata.getMemberObject(col, row);

            if(!(obj instanceof MemberObject)) {
               continue;
            }

            MemberObject mobj = (MemberObject) obj;

            // only do this for old version, attribute of new version is the
            // same with level unique name
            if(Tool.equals(ref.getAttribute(), mobj.getLName())) {
               return;
            }

            for(int k = 0; k < cond.getValueCount(); k++) {
               Object val = cond.getValue(k);

               if(val instanceof String) {
                  String fullCaption = (String) val;

                  if(XMLAUtil.isIdentity(fullCaption, mobj)) {
                     cond.setValue(k, mobj.getUName());
                  }
               }
            }
         }
      }
   }

   /**
    * Check if the highlight color frame is empty.
    */
   public boolean isEmpty() {
      return rconds.length == 0;
   }

   /**
    * Check if the legend frame should be shown as a legend. The default
    * implementation will just check whether there are multiple labels.
    */
   @Override
   public boolean isVisible() {
      return false;
   }

   /**
    * Get the color for the specified cell.
    * @param data the specified chart lens.
    * @param col the specified column name.
    * @param row the specified row index.
    */
   @Override
   public Color getColor(DataSet data, String col, int row) {
      col = GraphUtil.getOriginalCol(col);

      if(this.col != null && !this.col.equals(col)) {
         return def;
      }

      Highlight hl = getHighlight(data, row);
      return hl != null ? hl.getForeground() : def;
   }

   public Highlight getHighlight(DataSet data, int row) {
      for(int i = 0; i < rconds.length; i++) {
         if(rconds[i].evaluate(data, row)) {
            return highlights[i];
         }
      }

      return null;
   }

   /**
    * Check if the highlight condition is met in the dataset.
    */
   public boolean isHighlighted(String hlname, DataSet data) {
      if(data == null) {
         return false;
      }

      for(int r = 0; r < data.getRowCount(); r++) {
         for(int i = 0; i < rconds.length; i++) {
            if(rconds[i].evaluate(data, r)) {
               if(highlights[i].getName().equals(hlname)) {
                  return true;
               }
            }
         }
      }

      return false;
   }

   /**
    * Get the color for the specified value.
    * @param val the specified value.
    */
   @Override
   public Color getColor(Object val) {
      throw new RuntimeException("Unsupported method called!");
   }

   /**
    * Get the title to show on the legend.
    */
   @Override
   public String getTitle() {
      throw new RuntimeException("Unsupported method called!");
   }

   /**
    * Get the values mapped by this frame.
    */
   @Override
   public Object[] getValues() {
      throw new RuntimeException("Unsupported method called!");
   }

   @Override
   public boolean isApplicable(String field) {
      // for brushing, default color is set and should be applied if a vo doesn't match
      // highlight condition regardless of whether it's source or target on a network. (57427)
      return def != null || super.isApplicable(field);
   }

   /**
    * Set the formula condition env to the condition.
    */
   public void setQuerySandbox(Object querySandbox) {
      if(rconds != null) {
         for(GraphConditionGroup rcond : rconds) {
            rcond.setQuerySandbox(querySandbox);
         }
      }
   }

   private String col;
   private Color def;
   private boolean original;
   private GraphConditionGroup[] rconds;
   private Highlight[] highlights;
}
