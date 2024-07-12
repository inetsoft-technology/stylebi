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

import inetsoft.mv.fs.BlockFile;
import inetsoft.mv.util.SeekableInputStream;
import inetsoft.uql.asset.DateRangeRef;
import inetsoft.util.Tool;
import inetsoft.util.swap.XSwapUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;
import java.util.Date;

/**
 * A double measure column that is swapped to/from block files.
 *
 * @author InetSoft Technology
 * @version 11.1
 */
public class MVDateColumnWrapper extends MVDecimalColumn implements MVColumnWrapper {
   /**
    * Measure column for creating the column (in-memory).
    */
   public MVDateColumnWrapper(int dateOpt, int idx,
                              SeekableInputStream channel, long fpos,
                              BlockFile file, int size)
   {
      super(channel, fpos, file, size, true);
      this.originalCol = idx;
      this.dateOpt = dateOpt;
   }

   /**
    * Measure column for loading the data on demand.
    */
   public MVDateColumnWrapper(SeekableInputStream channel, long fpos, BlockFile file, int size) {
      super(channel, fpos, file, size, false);
      access();
   }

   /**
    * Get the value at the specified index.
    */
   @Override
   public double getValue(int r) {
      double oval = fetchOriginalColumn().getValue(r);
      return getRangeValue(oval);
   }

   /**
    * Set the number of rows (items) in the column.
    */
   @Override
   public void setRowCount(int rcnt) {
      super.setRowCount(rcnt);
      fetchOriginalColumn().setRowCount(rcnt);
   }

   private double getRangeValue(double oval) {
      if(oval == Tool.NULL_DOUBLE) {
         return oval;
      }

      // adjust for timezone difference when server and MV (spark) have
      // different timezone
      Date d = new Date((long) oval + timezoneAdj);
      Object result = DateRangeRef.getData(dateOpt, d);

      return result instanceof Date ? ((Date) result).getTime() : (Integer) result;
   }

   /**
    * Set the value at the specified index.
    */
   @Override
   public void setValue(int idx, double value) {
      // do nothing
   }

   @Override
   public double[] getFragment(int index) {
      double[][] dateRangeValues = this.dateRangeValues;

      if(dateRangeValues == null) {
         dateRangeValues = new double[fetchOriginalColumn().fragments.length][];
         this.dateRangeValues = dateRangeValues;
      }

      if(dateRangeValues[index] == null) {
         double[] fragment = fetchOriginalColumn().getFragment(index);
         double[] vals2 = new double[fragment.length];

         for(int i = 0; i < fragment.length; i++) {
            vals2[i] = getRangeValue(fragment[i]);
         }

         dateRangeValues[index] = vals2;
      }

      return dateRangeValues[index];
   }

   @Override
   protected void swapped() {
      dateRangeValues = null;
   }

   @Override
   protected void createFragments(int size, boolean swappable) {
      // do nothing
   }

   @Override
   protected void initHeader(ByteBuffer buf) {
      super.initHeader(buf);
      XSwapUtil.position(buf, buf.capacity() - 8);
      originalCol = buf.getInt();
      dateOpt = buf.getInt();
   }

   @Override
   protected ByteBuffer createHeaderBuf() {
      ByteBuffer buf = super.createHeaderBuf();
      XSwapUtil.position(buf, buf.capacity() - 8);
      buf.putInt(getOriginalColumn());
      buf.putInt(dateOpt);
      return buf;
   }

   /**
    * Get the data length of this dimension index.
    */
   @Override
   public int getLength() {
      return super.getLength() + 8;
   }

   /**
    * Get header length.
    */
   @Override
   public int getHeaderLength() {
      return super.getHeaderLength() + 8;
   }

   /**
    * Number of bytes per value.
    */
   @Override
   protected int bytesPer() {
      return 0;
   }

   protected double[] createValue(int size) {
      return null;
   }

   @Override
   protected Fragment createFragment(int index, int size, boolean newbuf) {
      return null;
   }

   /**
    * Get original column index.
    */
   @Override
   public int getOriginalColumn() {
      if(originalCol < 0) {
         try {
            readHeader();
         }
         catch(Exception ex) {
            LOG.error("Failed to read header", ex);
         }
      }

      return originalCol;
   }

   /**
    * Get level.
    */
   public int getLevel() {
      if(dateOpt < 0) {
         try {
            readHeader();
         }
         catch(Exception ex) {
            LOG.error("Failed to read header", ex);
         }
      }

      return dateOpt;
   }

   /**
    * Set the table containing this column.
    */
   @Override
   public void setDefaultTableBlock(DefaultTableBlock table) {
      this.table = table;
   }

   /**
    * Make sure column is loaded.
    */
   private final MVDecimalColumn fetchOriginalColumn() {
      if(originalColumn == null) {
         XMVColumn bcol = table.mcols[getOriginalColumn() - table.dcols.length];
         originalColumn = (MVDecimalColumn) bcol;
      }

      return originalColumn;
   }

   /**
    * Set server timezone offset to use for calculation of date range.
    */
   public static void setServerTimezoneOffset(int timezoneOffset) {
      int myTZ = (new java.util.Date()).getTimezoneOffset();
      timezoneAdj = (myTZ - timezoneOffset) * 60000;
   }

   private int dateOpt = -1;
   private int originalCol = -1;
   private transient MVDecimalColumn originalColumn;
   private DefaultTableBlock table;

   // timezone offset of server, needs to change to thread local if multiple
   // servers access one spark MV
   private static int timezoneAdj = 0;
   private double[][] dateRangeValues;

   private static final Logger LOG =
      LoggerFactory.getLogger(MVDateColumnWrapper.class);
}
