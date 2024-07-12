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
package inetsoft.mv.data;

import inetsoft.mv.MVDef;
import inetsoft.report.TableLens;
import inetsoft.report.internal.table.TableFormat;
import inetsoft.uql.*;
import inetsoft.uql.asset.*;
import inetsoft.uql.erm.DataRef;
import inetsoft.uql.jdbc.SQLHelper;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.util.XUtil;
import inetsoft.uql.viewsheet.internal.VSUtil;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Collection;
import java.util.List;

/**
 * MergedTableBlock, the grouped XTableBlock as query result on server side.
 *
 * @author InetSoft Technology
 * @since  10.2
 */
public abstract class MergedTableBlock implements XTableBlock {
   /**
    * Constructor.
    */
   public MergedTableBlock(MVQuery query, SubTableBlock block) {
      super();

      this.query = query;
      dcnt = query.groups.length;
      mcnt = query.aggregates.length;
      dimAggregate = query.dimAggregate;
      detail = query.isDetail();
      donly = mcnt == 0;
      infos = new FormulaInfo[mcnt];
      headers = new String[dcnt + mcnt];
      meta = new XMetaInfo[dcnt + mcnt];
      identifiers = new String[dcnt + mcnt];

      for(int i = 0; i < headers.length; i++) {
         if(i < dcnt) {
            DataRef ref = query.groups[i].getDataRef();
            headers[i] = VSUtil.getAttribute(ref);

            if(ref instanceof ColumnRef &&
               XSchema.areDataTypesCompatible(ref.getDataType(), ((ColumnRef) ref).getDataRef().getDataType()))
            {
               meta[i] = query.getXMetaInfo(MVDef.getMVHeader(ref));
            }

            identifiers[i] = query.getColumnIdentifier(MVDef.getMVHeader(ref));

            // add default format for date
            // if(meta[i] == null || meta[i].isEmpty()) {
            if(ref instanceof ColumnRef) {
               ref = ((ColumnRef) ref).getDataRef();

               if(ref instanceof DateRangeRef) {
                  int level = ((DateRangeRef) ref).getDateOption();
                  SimpleDateFormat dfmt = XUtil.getDefaultDateFormat(level,
                     ((DateRangeRef) ref).getOriginalType());

                  if(dfmt != null) {
                     meta[i] = meta[i] == null ? new XMetaInfo() : meta[i].clone();
                     String fmt = dfmt.toPattern();
                     meta[i].setXFormatInfo(new XFormatInfo(TableFormat.DATE_FORMAT, fmt));
                  }
                  // @by yanie: bug1416295134182
                  // Don't apply format to number date part
                  else if(level != DateRangeRef.NONE_INTERVAL && meta[i] != null) {
                     meta[i] = meta[i].clone();
                     meta[i].setXFormatInfo(null);
                  }

                  // @by yanie: bug1416295134182
                  // don't apply drill to PART_DATE_GROUP
                  if(meta[i] != null && (level & XConstants.PART_DATE_GROUP) != 0) {
                     meta[i].setXDrillInfo(null);
                  }
               }
            }
         }
         else {
            int idx = i - dcnt;
            AggregateRef aref = query.aggregates[idx];
            AggregateFormula formula = aref.getFormula();
            DataRef mref = GroupedTableBlock.getDataRef(aref.getDataRef());
            meta[i] = query.getXMetaInfo(MVDef.getMVHeader(mref));
            identifiers[i] = query.getColumnIdentifier(MVDef.getMVHeader(mref));
            boolean composite = false;

            // aggregated? do not maintain drill
            if(!detail && meta[i] != null) {
               meta[i] = meta[i].clone();
               meta[i].setXDrillInfo(null);
               XFormatInfo finfo = meta[i].getXFormatInfo();
               String fmt = finfo == null ? null : finfo.getFormat();

               // date format is meaningless
               if(TableFormat.DATE_FORMAT.equals(fmt)) {
                  meta[i].setXFormatInfo(null);
               }
            }

            if(formula == null || !formula.isCombinable()) {
               if(formula == AggregateFormula.COUNT_DISTINCT) {
                  formula = AggregateFormula.SUM;
               }
               else {
                  formula = AggregateFormula.MAX;
               }
            }

            int[] cols;

            if(aref instanceof CompositeAggregateRef) {
               CompositeAggregateRef cref = (CompositeAggregateRef) aref;
               List caggrs = cref.getChildAggregates();
               cols = new int[caggrs.size()];
               composite = true;

               for(int j = 0; j < cols.length; j++) {
                  AggregateRef ref = (AggregateRef) caggrs.get(j);
                  String cname = MVDef.getMVHeader(ref.getDataRef());
                  cols[j] = block.indexOfHeader(cname) - dcnt;
               }
            }
            else {
               Collection<AggregateRef> collection = aref.getSubAggregates();
               cols = new int[collection.size()];
               int colidx = 0;

               for(AggregateRef ref : collection) {
                  String cname = MVDef.getMVHeader(ref.getDataRef());
                  cols[colidx] = block.indexOfHeader(cname) - dcnt;
                  colidx++;
               }

               composite = true;
            }

            headers[i] = VSUtil.getAttribute(aref);
            SQLHelper helper = query.getSQLHelper();
            FormulaInfo info = FormulaInfo.create(
               GroupedTableBlock.getFormula(formula, composite, helper), cols);
            infos[idx] = info;
         }
      }
   }

   /**
    * Reset the row from data node, then the row will be transformed to
    * a new row at server node.
    */
   protected final MVRow resetRow(MVRow r, FormulaInfo[] infos, int mcnt,
                                  double[] arr, Object[] arr2, int mcnt2)
   {
      if(dimAggregate) {
         arr2 = ((MVRow2) r).aggregates2;
      }
      else {
         arr = r.getDouble(arr, mcnt2);
      }

      if(mcnt > 0) {
         FormulaInfo[] ninfos = new FormulaInfo[mcnt];

         for(int i = 0; i < mcnt; i++) {
            ninfos[i] = (FormulaInfo) infos[i].clone();
         }

         r.setFormulas(ninfos);

         if(dimAggregate) {
            ((MVRow2) r).add(arr2);
         }
         else {
            r.add(arr);
         }
      }

      return r;
   }

   /**
    * Add a grouped table block.
    */
   public abstract void add(SubTableBlock table) throws IOException;

   /**
    * Called when add() is finished.
    */
   public abstract void complete();

   /**
    * Get the column count of this XTableBlock.
    */
   @Override
   public int getColCount() {
      return headers.length;
   }

   /**
    * Get the dimension count of this XTableBlock.
    */
   @Override
   public int getDimCount() {
      return dcnt;
   }

   /**
    * Get the header at the specified column.
    */
   @Override
   public String getHeader(int c) {
      return headers[c];
   }

   /**
    * Get the XMetaInfo at the specified column.
    */
   public XMetaInfo getXMetaInfo(int c) {
      return meta[c];
   }

   /**
    * Get the identifier.
    */
   public String getColumnIdentifier(int c) {
      return identifiers[c];
   }

   /**
    * Get the measure count of this XTableBlock.
    */
   @Override
   public int getMeasureCount() {
      return mcnt;
   }

   /**
    * Get the index of the specified column header.
    */
   @Override
   public int indexOfHeader(String header) {
      return MVDef.indexOfHeader(header, headers, 0);
   }

   /**
    * Return the contained table lens if there is one.
    */
   public TableLens getTableLens() {
      return null;
   }

   final int dcnt;
   final int mcnt;
   final boolean donly;
   final String[] headers;
   final XMetaInfo[] meta;
   final String[] identifiers;
   final FormulaInfo[] infos;
   private boolean detail;
   boolean dimAggregate;
   protected MVQuery query;
}
