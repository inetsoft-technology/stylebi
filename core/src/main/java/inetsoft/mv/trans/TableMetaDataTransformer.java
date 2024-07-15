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
package inetsoft.mv.trans;

import inetsoft.mv.MVTool;
import inetsoft.sree.SreeEnv;
import inetsoft.uql.*;
import inetsoft.uql.asset.*;
import inetsoft.uql.erm.DataRef;
import inetsoft.uql.erm.ExpressionRef;
import inetsoft.uql.viewsheet.CalculateRef;
import inetsoft.uql.viewsheet.internal.VSUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * Create a mirror for table with associated selections to be used to
 * create MV for association queries.
 *
 * @version 10.2
 * @author InetSoft Technology Corp
 */
public class TableMetaDataTransformer extends AbstractTransformer {
   /**
    * Transform the table assembly.
    *
    * @return true if successful, false otherwise.
    */
   @Override
   public boolean transform(TransformationDescriptor desc) {
      if("false".equals(SreeEnv.getProperty("mv.cache.association"))) {
         return false;
      }

      if(!desc.getViewsheet().getViewsheetInfo().isAssociationEnabled()) {
         return false;
      }

      TableAssembly mvtbl = desc.getMVTable();

      if(mvtbl == null) {
         return false;
      }

      List<WSColumn> wscols = getAssociatedSelectionColumns(mvtbl, desc);
      boolean needAssociation = false;

      for(WSColumn wscol : wscols) {
         // range (slider & calendar) data is fetched from MVDef and
         // doesn't need association by themselves
         if(wscol.getRangeInfo() == null) {
            needAssociation = true;
         }

         String text = wscol.getDataRef().getAttribute();

         // if dynamic column binding, column is not known and we don't
         // create association MV
         if(VSUtil.isVariableValue(text) || VSUtil.isScriptValue(text)) {
            return false;
         }

         CalculateRef calc = desc.getViewsheet().getCalcField(mvtbl.getName(), text);

         // if calc field reference a parameter, don't support MV. (63682)
         if(calc != null && calc.getDataRef() instanceof ExpressionRef &&
            !MVTool.isExpressionMVCompatible(((ExpressionRef) calc.getDataRef()).getExpression()))
         {
            return false;
         }
      }

      if(!needAssociation) {
         return false;
      }

      // variable condition on tbl, don't create association mv or the
      // condition won't be applied to association query
      /* changed MVTableMetaData to transform the tables to push up variable
         condition so this should not be necessary anymore
      if(hasVariableCondition(mvtbl)) {
         return false;
      }
      */
      if(hasSubQueryCondition(mvtbl, desc)) {
         return false;
      }

      mirror = createMirror(mvtbl, desc.getWorksheet(), true, wscols);

      return mirror != null;
   }

   // get association columns from this table and children. since the tables have
   // not been transformed, variable condition columns on children will need to be
   // accounted for by adding them to the association columns. otherwise the
   // association MV will fail when the variable condition is transformed up
   // at runtime. (42072)
   private static List<WSColumn> getAssociatedSelectionColumns(
      TableAssembly mvtbl, TransformationDescriptor desc)
   {
      List<WSColumn> wscols = desc.getAssociatedSelectionColumns(mvtbl.getName());

      if(mvtbl instanceof ComposedTableAssembly) {
         TableAssembly[] children = ((ComposedTableAssembly) mvtbl).getTableAssemblies(true);
         List<WSColumn> all = new ArrayList<>(wscols);

         for(TableAssembly child : children) {
            ColumnSelection parentCols = mvtbl.getColumnSelection(false);
            List<WSColumn> wscols2 = getAssociatedSelectionColumns(child, desc);

            for(WSColumn wscol2 : wscols2) {
               // make sure the child column is in parent. may mismatch if column is renamed.
               // we may want to handle the mapping for renamed column in the future. (42084)
               if(parentCols.getAttribute(wscol2.getDataRef().getName()) != null) {
                  if(!all.contains(wscol2)) {
                     all.add(wscol2);
                  }
               }
            }
         }

         return all;
      }

      return wscols;
   }

   /**
    * Get the table to use for querying table meta data (association).
    */
   public TableAssembly getMetaDataTable() {
      return mirror;
   }

   /**
    * Create mirror table for the subset of columns.
    */
   public static TableAssembly createMirror(TableAssembly mvtable) {
      int mode = TransformationDescriptor.RUN_MODE;
      TransformationDescriptor desc = new TransformationDescriptor(mvtable, mode);

      String tblname = mvtable.getName();

      if(tblname.startsWith(Assembly.SELECTION)) {
         tblname = tblname.substring(Assembly.SELECTION.length());
      }

      return createMirror(mvtable, mvtable.getWorksheet(), false,
                          desc.getAssociatedSelectionColumns(tblname));
   }

   /**
    * Create mirror table for the subset of columns.
    */
   private static TableAssembly createMirror(
      TableAssembly mvtable, Worksheet ws, boolean creation,
      List<WSColumn> wscols)
   {
      String name = PREFIX + mvtable.getName();
      MirrorTableAssembly mirror = new MirrorTableAssembly(ws, name, mvtable);
      mirror.setVisible(false);
      ws.addAssembly(mirror);

      ColumnSelection mvsel = mvtable.getColumnSelection(true);
      ColumnSelection sel = mirror.getColumnSelection(true);
      ColumnSelection nsel = new ColumnSelection();
      AggregateInfo ainfo = new AggregateInfo();
      boolean error = false;

      for(int i = 0; i < sel.getAttributeCount(); i++) {
         ((ColumnRef) sel.getAttribute(i)).setVisible(false);
      }

      for(WSColumn wscol : wscols) {
         ColumnRef col = (ColumnRef) normalizeColumn(wscol.getDataRef(), sel);

         if(col != null) {
            nsel.addAttribute(col);
            col.setVisible(true);

            if(creation) {
               ainfo.addGroup(new GroupRef(col));
            }
         }
         else {
            // the wscol may contain cols from child, if it's not found, don't treat it as
            // an error.
            if(Objects.equals(wscol.getTableName(), mvtable.getName())) {
               LOG.info(
                  "Cannot materialize selection, column {} is missing from the " +
                  "materialized table. This is probably caused by VPM hidden columns " +
                  "being applied.", wscol.getDataRef().getName());
               error = true;
            }
         }
      }

      for(int i = 0; i < mvsel.getAttributeCount(); i++) {
         DataRef ref = mvsel.getAttribute(i);

         if(ref instanceof CalculateRef && !VSUtil.isAggregateCalc(ref) &&
            nsel.getAttribute(ref.getName()) == null)
         {
            nsel.addAttribute(ref);
         }
      }

      // get distinct rows using grouping so it can be applied in MV
      if(creation) {
         mirror.setAggregateInfo(ainfo);
      }

      mirror.setColumnSelection(nsel, false);
      mirror.setColumnSelection(nsel, true);

      return error ? null : mirror;
   }

   @Override
   protected TransformationInfo getInfo(String block) {
      return null;
   }

   // check if the table contains condition with subquery.
   private boolean hasSubQueryCondition(TableAssembly tbl, TransformationDescriptor desc) {
      ConditionListWrapper conds = tbl.getPreRuntimeConditionList();

      if(conds == null) {
         return false;
      }

      for(int i = 0; i < conds.getConditionSize(); i++) {
         HierarchyItem item = conds.getItem(i);

         if(item instanceof ConditionItem) {
            XCondition cond = ((ConditionItem) item).getXCondition();

            if(cond instanceof Condition) {
               List values = ((Condition) cond).getValues();
               boolean subFilter = values.stream()
                  .filter(v -> v instanceof SubQueryValue)
                  .filter(v -> desc.isSelectionOnTable(((SubQueryValue) v).getQuery()))
                  .count() > 0;

               if(subFilter) {
                  return true;
               }
            }
         }
      }

      return false;
   }

   public static final String PREFIX = "ASSOCIATION_";
   private static final Logger LOG = LoggerFactory.getLogger(TableMetaDataTransformer.class);

   private TableAssembly mirror;
}
