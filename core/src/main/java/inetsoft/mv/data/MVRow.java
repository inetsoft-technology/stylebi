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

import com.esotericsoftware.kryo.kryo5.Kryo;
import com.esotericsoftware.kryo.kryo5.io.Input;
import com.esotericsoftware.kryo.kryo5.io.Output;
import inetsoft.mv.formula.MergeableFormula;
import inetsoft.report.filter.Formula;
import inetsoft.util.Tool;

import java.io.Serializable;
import java.util.Comparator;

/**
 * Row, contains a row of data in one XTableBlock.
 *
 * @author InetSoft Technology
 * @since  10.2
 */
public class MVRow implements Cloneable, Serializable {
   /**
    * Create an instance of row.
    */
   public MVRow() {
      super();
   }

   /**
    * Create an instance of row.
    */
   public MVRow(long[] groups, double[] aggregates) {
      this.groups = groups;
      this.aggregates = aggregates;
   }

   /**
    * Check if equals another object.
    */
   public boolean equals(Object obj) {
      if(obj == null) {
         return false;
      }

      MVRow row = (MVRow) obj;

      for(int i = groups.length - 1; i >= 0; i--) {
         if(groups[i] != row.groups[i]) {
            return false;
         }
      }

      return true;
   }

   /**
    * Get the hash code value.
    */
   public int hashCode() {
      if(hash == 0) {
         int h = 0;

         for(long elem : groups) {
            h = 31 * h + (int) (elem + (elem >> 32));
         }

         hash = h;
      }

      return hash;
   }

   /**
    * Add measure values.
    */
   public void add(double[] marr) {
      if(infos != null) {
         for(FormulaInfo info : infos) {
            info.addValue(marr);
         }
      }
   }

   /**
    * Clone this row.
    */
   @Override
   public Object clone() {
      MVRow row = new MVRow();

      if(groups != null) {
         row.groups = (long[]) groups.clone();
      }

      if(aggregates != null && aggregates.length > 0) {
         row.aggregates = (double[]) aggregates.clone();

         if(infos != null) {
            getDouble(row.aggregates, aggregates.length);
         }
      }

      return row;
   }

   /**
    * Get the aggregate result if any.
    */
   public double[] getDouble(final double[] res, int len) {
      if(infos == null) {
         return aggregates;
      }

      for(int i = 0; i < len; i++) {
         if(infos[i].formula.isNull()) {
            res[i] = Tool.NULL_DOUBLE;
         }
         else {
            res[i] = infos[i].formula.getDoubleResult();
         }
      }

      return res;
   }

   /**
    * Get the aggregate result if any.
    */
   public Object getObject(int idx) {
      if(infos == null) {
         return aggregates[idx];
      }

      return infos[idx].formula.getResult();
   }

   /**
    * Get the aggregate result if any.
    */
   public Object[] getObject(final Object[] res, final int len) {
      if(infos == null) {
         for(int i = 0; i < len; i++) {
            res[i] = aggregates[i];
         }

         return res;
      }

      for(int i = 0; i < len; i++) {
         res[i] = infos[i].formula.getResult();
      }

      return res;
   }

   /**
    * Get the string representation.
    */
   public String toString() {
      StringBuilder sb = new StringBuilder("Row[ g:");

      for(int i = 0; i < groups.length; i++) {
         if(i > 0) {
            sb.append(',');
         }

         sb.append(groups[i]);
      }

      sb.append(" a: ");

      if(aggregates != null) {
         for(int i = 0; i < aggregates.length; i++) {
            if(i > 0) {
               sb.append(',');
            }

            sb.append(aggregates[i]);
         }
      }

      if(infos != null) {
         sb.append(" i: ");

         for(int i = 0; i < infos.length; i++) {
            if(i > 0) {
               sb.append(',');
            }

            sb.append(infos[i] + "=" + getObject(i));
         }
      }

      sb.append(']');

      if(mcnt != -1) {
         sb.append(" mcnt:"+mcnt);
      }

      return sb.toString();
   }

   public final long[] getGroups() {
      return groups;
   }

   public final void setGroups(long[] groups) {
      this.groups = groups;
   }

   public final double[] getAggregates() {
      return aggregates;
   }

   public final void setAggregates(double[] aggregates) {
      this.aggregates = aggregates;
   }

   public final FormulaInfo[] getFormulas() {
      return infos;
   }

   public final void setFormulas(FormulaInfo[] infos) {
      // optimization, avoid empty infos.
      this.infos = infos != null && infos.length > 0 ? infos : null;
   }

   // For Spark, need to get the measure count back from the SubTableBlock,
   // which is executed in Worker.
   public final void setMeasureCount(int mcnt) {
      this.mcnt = mcnt;
   }

   // For Spark, need to get the measure count back to the SparkMVExecutor,
   // which is executed in Master.
   public final int getMeasureCount() {
      return mcnt;
   }

   /**
    * Add row2 to this row by adding the values or merge the formulas.
    */
   public void fold(MVRow row2) {
      if(row2.infos == null) {
         add(row2.getAggregates());
      }
      else {
         for(int i = 0; i < infos.length; i++) {
            Formula f = infos[i].getFormula();
            ((MergeableFormula) f).merge(row2.infos[i].getFormula());
         }
      }
   }

   // write object for KryoSerializable
   public void write(Kryo kryo, Output output) {
      output.writeInt(hash);
      output.writeInt(mcnt);

      output.writeInt(groups.length);

      for(long group : groups) {
         output.writeLong(group);
      }

      output.writeInt(aggregates == null ? -1 : aggregates.length);

      if(aggregates != null) {
         for(double aggr : aggregates) {
            output.writeDouble(aggr);
         }
      }

      output.writeInt(infos == null ? -1 : infos.length);

      if(infos != null) {
         for(FormulaInfo info : infos) {
            kryo.writeObjectOrNull(output, info, FormulaInfo.class);
         }
      }
   }

   // read object for KryoSerializable
   public void read(Kryo kryo, Input input) {
      hash = input.readInt();
      mcnt = input.readInt();

      int cnt = input.readInt();
      groups = new long[cnt];

      for(int i = 0; i < groups.length; i++) {
         groups[i] = input.readLong();
      }

      cnt = input.readInt();

      if(cnt >= 0) {
         aggregates = new double[cnt];

         for(int i = 0; i < aggregates.length; i++) {
            aggregates[i] = input.readDouble();
         }
      }

      cnt = input.readInt();

      if(cnt >= 0) {
         infos = new FormulaInfo[cnt];

         for(int i = 0; i < infos.length; i++) {
            infos[i] = kryo.readObjectOrNull(input, FormulaInfo.class);
         }
      }
   }

   public static class Key implements Serializable {
      // for serialization
      public Key() {
      }

      public Key(long[] groups) {
         this.groups = groups;
      }

      @Override
      public boolean equals(Object obj) {
         if(obj == null) {
            return false;
         }

         MVRow.Key row = (MVRow.Key) obj;

         for(int i = groups.length - 1; i >= 0; i--) {
            if(groups[i] != row.groups[i]) {
               return false;
            }
         }

         return true;
      }

      public int hashCode() {
         if(hash == 0) {
            for(long elem : groups) {
               hash ^= (int) elem + (int) (elem >> 32) + hash * 31;
            }
         }

         return hash;
      }

      // write object for KryoSerializable
      public void write(Kryo kryo, Output output) {
         output.writeInt(groups.length);

         for(long group : groups) {
            output.writeLong(group);
         }
      }

      // read object for KryoSerializable
      public void read(Kryo kryo, Input input) {
         int cnt = input.readInt();
         groups = new long[cnt];

         for(int i = 0; i < groups.length; i++) {
            groups[i] = input.readLong();
         }
      }

      private long[] groups;
      private transient int hash;
   }

   /**
    * Compare this row with another row.
    */
   public static class RowComparator implements Comparator<MVRow>, Serializable {
      public RowComparator() {
         // for serialization
      }

      public RowComparator(boolean[] orders) {
         this.orders = orders;
      }

      @Override
      public int compare(MVRow row1, MVRow row2) {
         int gcnt = row1.groups.length;

         for(int i = 0; i < gcnt; i++) {
            if(row1.groups[i] != row2.groups[i]) {
               // make sure the result is not out of integer bounds
               int rc = row1.groups[i] > row2.groups[i] ? 1 : -1;
               return orders[i] ? rc : -rc;
            }
         }

         return 0;
      }

      private boolean[] orders;
   }

   protected long[] groups;
   protected double[] aggregates;
   protected int hash;
   protected FormulaInfo[] infos;
   protected int mcnt = -1;
}
