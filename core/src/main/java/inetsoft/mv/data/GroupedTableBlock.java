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
package inetsoft.mv.data;

import inetsoft.mv.MVDef;
import inetsoft.mv.comm.XReadBuffer;
import inetsoft.mv.comm.XWriteBuffer;
import inetsoft.mv.formula.CompositeVarianceFormula;
import inetsoft.report.filter.Formula;
import inetsoft.report.filter.MaxFormula;
import inetsoft.uql.asset.*;
import inetsoft.uql.erm.DataRef;
import inetsoft.uql.jdbc.SQLHelper;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.viewsheet.internal.VSUtil;
import inetsoft.util.swap.XSwapUtil;
import inetsoft.util.swap.XSwappableObjectList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.*;

/**
 * GroupedTableBlock, the grouped XTableBlock as query result.
 *
 * @author InetSoft Technology
 * @since  10.2
 */
public final class GroupedTableBlock extends SubTableBlock {
   /**
    * Get the data ref for expression.
    */
   public static DataRef getDataRef(DataRef ref) {
      if(ref instanceof ColumnRef) {
         ColumnRef column = (ColumnRef) ref;
         DataRef iref = column.getDataRef();

         if(iref instanceof AliasDataRef) {
            String dtype = iref.getDataType();
            iref = ((AliasDataRef) iref).getDataRef();

            if(iref instanceof AliasDataRef) {
               iref = ((AliasDataRef) iref).getDataRef();
            }

            column = (ColumnRef) column.clone();
            column.setDataRef(iref);

            // use the base data type if it's set. (49781)
            if(!XSchema.STRING.equals(dtype)) {
               column.setDataType(dtype);
            }

            ref = column;
         }
      }

      return ref;
   }

   /**
    * Get the filter formula for the specified aggregate formua.
    */
   public static Formula getFormula(AggregateFormula formula, boolean composite, SQLHelper helper) {
      String name = null;

      try {
         // @by gregm defensive copy, fixes rare race condition where the wrong
         // formula name is returned by AggregateFormula.getFormulaName() due
         // to the setComposite logic below.
         formula = formula != null ? (AggregateFormula) formula.clone() : null;

         boolean comp = formula != null && formula.isComposite();

         if(formula != null) {
            formula.setComposite(composite);
         }

         name = formula == null ? null : formula.getFormulaName();

         if(name == null || name.equals("none") || name.equals("null")) {
            return new MaxFormula();
         }

         // never null, name would be set to null and return in if block above
         formula.setComposite(comp);

         name = getMVFormula(name);
         Formula form = (Formula) Class.forName(name).newInstance();

         if(form instanceof CompositeVarianceFormula && helper != null) {
            String dbType = helper.getSQLHelperType();
            ((CompositeVarianceFormula) form).setDBType(dbType);
         }

         return form;
      }
      catch(Exception ex) {
         LOG.warn("Failed to get formula: " + name, ex);
      }

      return null;
   }

   /**
    * Use formula class in mv.formula package.
    */
   private static final String getMVFormula(String name) {
      if(name.endsWith("Formula")) {
         return "inetsoft.mv.formula." + name;
      }

      if(name.equals("DistinctCount") ||
         name.equals("Median") ||
         name.equals("Mode"))
      {
         return "inetsoft.mv.formula." + name + "Formula";
      }

      return "inetsoft.report.filter." + name + "Formula";
   }

   /**
    * Create an instance of GroupedTableBlock.
    */
   public GroupedTableBlock() {
      super();

      headers = new String[0];
   }

   /**
    * @orders sorting order of groups.
    */
   public GroupedTableBlock(boolean[] orders) {
      this();
      this.orders = orders;
   }

   /**
    * Extract the "SubTableBlock" headers from the query, for use by init() below
    */
   public static String[] getHeaders(SubMVQuery query) {
      int dcnt = query.groups.length;
      int mcnt = query.aggregates.length;
      String[] headers = new String[dcnt + mcnt];

      for(int i = 0; i < headers.length; i++) {
         if(i < dcnt) {
            headers[i] = VSUtil.getAttribute(query.groups[i].getDataRef());
         }
         else {
            int idx = i - dcnt;
            AggregateRef aref = query.aggregates[idx];
            AggregateFormula formula = aref.getFormula();
            DataRef col1 = getDataRef(aref.getDataRef());
            DataRef col2 = formula == null ? null : (formula.isTwoColumns() ?
               getDataRef(aref.getSecondaryColumn()) : null);

            if(aref.isComposite()) {
               headers[i] = col1.getName();
            }
            else if(col2 == null) {
               headers[i] = formula == null ? col1.getAttribute() :
                  formula.getUID(col1.getName(), null);
            }
            else {
               headers[i] = formula.getUID(col1.getName(), col2.getName());
            }
         }
      }
      return headers;
   }

   /**
    * Initialize this table block with SubMVQuery.
    */
   @Override
   public void init(SubMVQuery query) {
      blockIndex = query.getBlockIndex();
      dcnt = query.groups.length;
      mcnt = query.aggregates.length;
      int size = (int) Math.min(1024 * 1024, Math.pow(16, dcnt));
      map = new RowMap(Math.max(1024, size));
      donly = mcnt == 0;
      infos = donly ? null : new FormulaInfo[mcnt];
      order = query.order;
      headers = getHeaders(query);
      String[] aggregates = query.getAggregateColumns();

      for(int i = 0; i < headers.length; i++) {
         if(i >= dcnt) {
            int idx = i - dcnt;
            AggregateRef aref = query.aggregates[idx];
            AggregateFormula formula = aref.getFormula();
            DataRef col1 = getDataRef(aref.getDataRef());
            DataRef col2 = formula == null ? null : (formula.isTwoColumns() ?
               getDataRef(aref.getSecondaryColumn()) : null);
            int[] cols;

            if(col2 != null) {
               String mvcol1 = MVDef.getMVHeader(col1);
               String mvcol2 = MVDef.getMVHeader(col2);
               cols = new int[] {indexOfCol(aggregates, mvcol1),
                                 indexOfCol(aggregates, mvcol2)};
            }
            else {
               String mvcol1 = MVDef.getMVHeader(col1);
               cols = new int[] {indexOfCol(aggregates, mvcol1)};
            }

            FormulaInfo info = FormulaInfo.create(getFormula(formula, false, null), cols);
            info.cols = cols;
            infos[idx] = info;
         }
      }
   }

   /**
    * Add a row to grouping.
    */
   @Override
   public void addRow(MVRow row) {
      row.hash = 0;
      RowMap.Entry node = map.put(row);
      MVRow group = node.row;

      if(group == row) {
         MVRow orow = row;
         row = (MVRow) row.clone();
         orow.setGroups(new long[orow.getGroups().length]);
         initRow(row);
         group = node.row = row;
      }

      group.add(row.getAggregates());
   }

   /**
    * Add a row (dimension only) to grouping.
    */
   @Override
   public void addDRow(MVRow row) {
      row.hash = 0;
      RowMap.Entry node = map.put(row);
      MVRow group = node.row;

      if(group == row) {
         MVRow orow = row;
         row = (MVRow) row.clone();
         orow.setGroups(new long[orow.getGroups().length]);
         node.row = row;
      }
   }

   /**
    * Initialize the row.
    */
   private void initRow(MVRow row) {
      if(infos != null) {
         int fsize = infos.length;
         row.infos = new FormulaInfo[fsize];

         for(int i = 0; i < fsize; i++) {
            row.infos[i] = (FormulaInfo) infos[i].clone();
         }
      }
      else {
         row.infos = null;
      }
   }

   /**
    * Complete this table block.
    */
   @Override
   public void complete() {
      List<MVRow> list = map.getRows();
      Class cls = MVRow.class;
      map.clear();

      // spark would perform the distinct/reduce with RDD and is not
      // dependent on the sub-result being sorted
      if(list.size() > 0) {
         if(list.get(0) instanceof MVRow2) {
            cls = MVRow2.class;
            Collections.sort(list, new MVRow2.RowComparator(orders));
         }
         else {
            Collections.sort(list, new MVRow.RowComparator(orders));
         }
      }

      this.rows = new XSwappableObjectList<>(cls);
      list.forEach(r -> this.rows.add(r));
      this.rows.complete();
   }

   @Override
   public boolean isCompleted() {
      return rows != null;
   }

   /**
    * Get the row count of this XTableBlock.
    */
   @Override
   public int getRowCount() {
      if(rows == null) {
         return map.size();
      }

      return rows.size();
   }

   /**
    * Get the measure count of this XTableBlock.
    */
   @Override
   public int getMeasureCount() {
      return mcnt;
   }

   /**
    * Get the row at the specified column.
    */
   @Override
   public MVRow getRow(int r) {
      return rows.get(r);
   }

   /**
    * Replace the rows in the block.
    */
   @Override
   public void setRows(XSwappableObjectList<MVRow> rows) {
      this.rows = rows;
   }

   /**
    * Read this transferable.
    */
   @Override
   public void read(XReadBuffer buf) throws IOException {
      blockIndex = buf.readInt();
      int rcnt = buf.readInt();
      dcnt = buf.readInt();
      mcnt = buf.readInt();
      donly = mcnt == 0;
      int cnt = dcnt + mcnt;
      headers = new String[cnt];

      for(int i = 0; i < cnt; i++) {
         headers[i] = buf.readString();
      }

      rows = new XSwappableObjectList<>(MVRow.class);
      int dlen = dcnt << 3;
      int mlen = mcnt << 3;
      ByteBuffer tbuf = null;
      long[] dmatrix = new long[BLOCK_SIZE * dcnt];
      double[] mmatrix = new double[BLOCK_SIZE * mcnt];

      for(int i = 0; i < rcnt; i += BLOCK_SIZE) {
         int block = Math.min(rcnt - i, BLOCK_SIZE);
         int length = block * (dlen + mlen);
         // @by jasons, direct byte buffers are only efficient when used with
         //             an nio channel
         ByteBuffer dbuf = ByteBuffer.allocate(length);

         tbuf = readBytes(buf, tbuf, dbuf, length);
         XSwapUtil.flip(dbuf);
         dbuf.asLongBuffer().get(dmatrix, 0, block * dcnt);

         if(!donly) {
            XSwapUtil.position(dbuf, block * dlen);
            dbuf.asDoubleBuffer().get(mmatrix, 0, block * mcnt);
         }

         if(!donly) {
            // add created row to rows
            for(int j = 0; j < block; j++) {
               long[] darr = new long[dcnt];
               System.arraycopy(dmatrix, j * dcnt, darr, 0, dcnt);
               double[] marr = new double[mcnt];
               System.arraycopy(mmatrix, j * mcnt, marr, 0, mcnt);
               rows.add(new MVRow(darr, marr));
            }
         }
         else {
            // add created row to rows
            for(int j = 0; j < block; j++) {
               long[] darr = new long[dcnt];
               System.arraycopy(dmatrix, j * dcnt, darr, 0, dcnt);
               rows.add(new MVRow(darr, null));
            }
         }
      }

      rows.complete();
   }

   /**
    * Write this transferable.
    */
   @Override
   public void write(XWriteBuffer buf) throws IOException {
      int rcnt = rows.size();
      buf.writeInt(blockIndex);
      buf.writeInt(rcnt);
      buf.writeInt(dcnt);
      buf.writeInt(mcnt);

      for(String header : headers) {
         buf.writeString(header);
      }

      int written = 0;
      double[] res = new double[mcnt];
      int dlen = dcnt << 3;
      int mlen = mcnt << 3;
      long[] dmatrix = new long[BLOCK_SIZE * dcnt];
      double[] mmatrix = new double[BLOCK_SIZE * mcnt];

      while(written < rcnt) {
         int block = Math.min(BLOCK_SIZE, rcnt - written);
         ByteBuffer dbuf = ByteBuffer.allocate(block * (dlen + mlen));
         int end = written + block;
         int count = 0;

         for(int i = written; i < end; i++) {
            System.arraycopy(rows.get(i).getGroups(), 0, dmatrix, count, dcnt);
            count += dcnt;
         }

         dbuf.asLongBuffer().put(dmatrix, 0, count);
         int pos = count << 3;
         XSwapUtil.position(dbuf, pos);

         if(!donly) {
            count = 0;

            for(int i = written; i < end; i++) {
               rows.get(i).getDouble(res, mcnt);
               System.arraycopy(res, 0, mmatrix, count, mcnt);
               count += mcnt;
            }

            dbuf.asDoubleBuffer().put(mmatrix, 0, count);
            pos += count << 3;
            XSwapUtil.position(dbuf, pos);
         }

         written += block;
         XSwapUtil.flip(dbuf);
         buf.write(dbuf);
         // XWriteBuffer) keeps a reference to it and uses it later
      }
   }

   /**
    * Get the string representation.
    */
   public String toString() {
      return super.toString() + '<' + Arrays.asList(headers) + '>';
   }

   private static final Logger LOG =
      LoggerFactory.getLogger(GroupedTableBlock.class);

   int mcnt;
   private XSwappableObjectList<MVRow> rows;
   private RowMap map;
   private FormulaInfo[] infos;
   private boolean[] orders;
   private boolean donly;
}
