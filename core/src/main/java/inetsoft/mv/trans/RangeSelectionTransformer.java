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
package inetsoft.mv.trans;

import inetsoft.mv.*;
import inetsoft.uql.ColumnSelection;
import inetsoft.uql.ConditionListWrapper;
import inetsoft.uql.asset.*;
import inetsoft.uql.erm.DataRef;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * RangeSelectionTransformer transforms one table assembly by transforming its
 * range selections if any, so that the table assembly could hit mv.
 *
 * @version 10.2
 * @author InetSoft Technology Corp
 */
public final class RangeSelectionTransformer extends AbstractTransformer {
   /**
    * Create an instance of RangeSelectionTransformer.
    */
   public RangeSelectionTransformer() {
      super();
   }

   /**
    * Transform the table assembly.
    * @return true if successful, false otherwise.
    */
   @Override
   public boolean transform(TransformationDescriptor desc) {
      TableAssembly table = desc.getTable(false);
      TableAssembly mvtable = desc.getMVTable();

      if(mvtable == null) {
         return true;
      }

      return transform0(mvtable, desc, true);
   }

   /**
    * Transform range selection internally.
    */
   private boolean transform0(TableAssembly table, TransformationDescriptor desc,
                              boolean root) {
      RuntimeMV rmv = table.getRuntimeMV();
      def = rmv == null || !rmv.isPhysical() ?
         null : MVManager.getManager().get(rmv.getMV());

      if(def == null) {
         if(root && rmv == null) {
            LOG.debug("Materialized view not found in range selection");
            return false;
         }

         if(!(table instanceof ComposedTableAssembly)) {
            return true;
         }

         ComposedTableAssembly ctable = (ComposedTableAssembly) table;
         TableAssembly[] tables = ctable.getTableAssemblies(false);
         boolean result = true;

         for(int i = 0; i < tables.length; i++) {
            result = transform0(tables[i], desc, false) && result;
         }

         return result;
      }

      // it's physical mv, let's try transforming range info
      List columns = desc.getSelectionColumns(table.getName(), false);

      for(int i = 0; i < columns.size(); i++) {
         WSColumn column = (WSColumn) columns.get(i);
         RangeInfo rinfo = column.getRangeInfo();

         if(rinfo == null) {
            continue;
         }

         try {
            transformRange(table, column, rinfo, desc);
         }
         catch(Exception ex) {
            LOG.error("Failed to transform range: " +
               table.getAssemblyEntry() + ", " + column, ex);
            return false;
         }
      }

      return true;
   }

   /**
    * Transform range.
    */
   private void transformRange(TableAssembly table, WSColumn column,
                               RangeInfo info, TransformationDescriptor desc)
      throws Exception
   {
      ColumnSelection columns = table.getColumnSelection();
      DataRef ref = column.getDataRef();
      ref = normalizeColumn(ref, columns);

      if(ref == null) {
         LOG.warn("Failed to transform range, column not found: " + table + ", " +
                  column + " in " + columns);
         return;
      }

      int rtype = info.getRangeType();
      int doption = RangeInfo.getDateRangeOption(rtype);
      String rattr = ref.getAttribute();
      String header = doption < 0 ? RangeMVColumn.getRangeName(rattr) :
         DateMVColumn.getRangeName(rattr, doption);
      MVColumn mvcol = def == null ? null : def.getColumn(header, true, true);

      // if there is no range column, we should be able to use the raw
      // column since measure column can be used for selection condition too
      if(mvcol == null) {
         return;
      }

      // make sure the mv data matches the log setting of the current vs
      if(mvcol instanceof RangeMVColumn &&
         ((RangeMVColumn) mvcol).isLogScale() != info.isLogScale())
      {
         return;
      }

      ConditionListWrapper wrapper = table.getPreRuntimeConditionList();
      column.getRangeInfo().valueToLogic(wrapper, ref, rtype, mvcol);
      wrapper = table.getPostRuntimeConditionList();
      column.getRangeInfo().valueToLogic(wrapper, ref, rtype, mvcol);
      ColumnRef ecol = doption < 0 ?
         RangeMVColumn.createRangeColumn((ColumnRef) ref) :
         DateMVColumn.createDateColumn(doption, (ColumnRef) ref);

      // rename column in this table
      if(!columns.containsAttribute(ecol)) {
         ecol.setVisible(false);
         columns.addAttribute(ecol);
      }

      renameColumn(table, ref, ecol);
      table.resetColumnSelection();
      desc.addInfo(getInfo(table.getName()));
   }

   /**
    * Rename a column.
    */
   private void renameColumn(TableAssembly table, DataRef from, DataRef to) {
      ConditionListWrapper conds = table.getPreRuntimeConditionList();
      RangeInfo.renameColumn(conds, from, to);
      conds = table.getPostRuntimeConditionList();
      RangeInfo.renameColumn(conds, from, to);
   }

   /**
    * Get the transformation message for this transformer.
    * @param block the data block.
    */
   @Override
   protected TransformationInfo getInfo(String block) {
      return TransformationInfo.rangeSelection(block);
   }

   private static final Logger LOG = LoggerFactory.getLogger(RangeSelectionTransformer.class);
   private MVDef def;
}
