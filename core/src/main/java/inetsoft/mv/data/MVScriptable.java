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

import inetsoft.mv.MVColumn;
import inetsoft.mv.MVDef;
import inetsoft.uql.schema.XSchema;
import inetsoft.util.Tool;
import org.mozilla.javascript.Scriptable;
import org.mozilla.javascript.ScriptableObject;

import java.sql.*;

/**
 * The viewsheet mv scriptable.
 *
 * @version 11.2
 * @author InetSoft Technology Corp
 */
public class MVScriptable extends ScriptableObject {
   public MVScriptable(MVDef mvdef, MVColumn mvcol) {
      this.mvdef = mvdef;
      this.mvcol = mvcol;

      init();
   }

   /**
    * Init mv.
    */
   private void init() {
      try {
         String file = MVStorage.getFile(mvdef.getName());
         this.mv = MVStorage.getInstance().get(file);
      }
      catch(Exception ignore) {
      }
   }

   /**
    * Get the name of the set of objects implemented by this Java class.
    */
   @Override
   public String getClassName() {
      return "MVScriptable";
   }

   /**
    * Get a named property from the object.
    */
   @Override
   public Object get(String name, Scriptable start) {
      if("LastUpdateTime".equals(name)) {
         return new Timestamp(mvdef.getLastUpdateTime());
      }
      else if("MaxValue".equals(name)) {
         return max();
      }
      else if("MinValue".equals(name)) {
         return min();
      }

      return super.get(name, start);
   }

   /**
    * Get an array of property ids.
    */
   @Override
   public Object[] getIds() {
      return props;
   }

   /**
    * The specify mv column's max value.
    */
   private Object max() {
      Object max = null;

      if(mv.getBlockSize() > 0) {
         int c = mv.indexOfHeader(mvcol.getName(), 0);

         if(c < 0 || mv.getDictionary(c, 0) == null) {
            max = mvcol.getOriginalMax();

            if(max == null) {
               for(int i = 0; i < mv.getBlockSize(); i++) {
                  MVBlockInfo binfo = mv.getBlockInfo(i);
                  Object max0 = binfo.getColumnInfo(c).getMax();

                  if(max == null) {
                     max = max0;
                  }
                  else {
                     max = Tool.compare(max0, max) < 0 ? max0 : max;
                  }
               }
            }
         }
         else {
            for(int i = 0; i < mv.getBlockSize(); i++) {
               Object max0 = mv.getDictionary(c, i).max();
               max = Tool.compare(max0, max) > 0 ? max0 : max;
            }
         }
      }

      return format(max);
   }

   /**
    * The pecify mv column's min value.
    */
   private Object min() {
      Object min = null;

      if(mv.getBlockSize() > 0) {
         int c = mv.indexOfHeader(mvcol.getName(), 0);

         if(c < 0 || mv.getDictionary(c, 0) == null) {
            min = mvcol.getOriginalMin();

            if(min == null) {
               for(int i = 0; i < mv.getBlockSize(); i++) {
                  MVBlockInfo binfo = mv.getBlockInfo(i);
                  Object min0 = binfo.getColumnInfo(c).getMin();

                  if(min == null) {
                     min = min0;
                  }
                  else {
                     min = Tool.compare(min0, min) < 0 ? min0 : min;
                  }
               }
            }
         }
         else {
            min = mv.getDictionary(c, 0).min();

            for(int i = 1; i < mv.getBlockSize(); i++) {
               Object min0 = mv.getDictionary(c, i).min();
               min = Tool.compare(min0, min) < 0 ? min0 : min;
            }
         }
      }

      return format(min);
   }

   /**
    * Format the max/min value.
    */
   private Object format(Object val) {
      if(val == null || !mvcol.isDateTime()) {
         return val;
      }

      if(val instanceof java.util.Date) {
         val = ((java.util.Date) val).getTime();
      }

      String dtype = mvcol.getColumn().getDataType();
      long date = val instanceof Long ? (Long) val : ((Double) val).longValue();

      if(XSchema.TIME.equals(dtype)) {
         val = new Time(date);
      }
      else if(XSchema.TIME_INSTANT.equals(dtype)) {
         val = new Timestamp(date);
      }
      else if(XSchema.DATE.equals(dtype)) {
         val = new Date(date);
      }

      return val;
   }

   private MVDef mvdef;
   private MVColumn mvcol;
   private MV mv;
   private static final String[] props = {"LastUpdateTime", "MaxValue",
                                                            "MinValue"};
}
