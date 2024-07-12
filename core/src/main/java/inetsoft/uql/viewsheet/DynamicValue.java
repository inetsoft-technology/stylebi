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
package inetsoft.uql.viewsheet;

import inetsoft.uql.schema.XSchema;
import inetsoft.uql.viewsheet.internal.VSUtil;
import inetsoft.util.Tool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Serializable;

/**
 * Dynamic value, comtains a design time string value and a runtime value.
 *
 * @version 8.5
 * @author InetSoft Technology Corp
 */
public class DynamicValue implements Serializable, Cloneable {
   /**
    * Constructor.
    */
   public DynamicValue() {
      super();
   }

   /**
    * Constructor.
    * @param dvalue the specified design time value.
    */
   public DynamicValue(String dvalue) {
      this(dvalue, XSchema.STRING);
   }

   /**
    * Constructor.
    * @param dvalue the specified design time value.
    * @param dtype the specified data type.
    */
   public DynamicValue(String dvalue, String dtype) {
      this.dvalue = dvalue;
      this.dtype = dtype;
   }

   /**
    * Constructor.
    * @param dvalue the specified design time value.
    * @param dtype the specified data type.
    * @param restriction the specified restriction.
    */
   public DynamicValue(String dvalue, String dtype, Object[] restriction) {
      this(dvalue, dtype);

      setRestriction(restriction);
   }

   /**
    * Constructor.
    * @param dvalue the specified design time value.
    * @param dtype the specified data type.
    * @param restriction the specified restriction.
    * @param rnames the names of the values. The name can be returned by
    * variable or script, and it is used to lookup the value.
    */
   public DynamicValue(String dvalue, String dtype, int[] restriction, String[] rnames) {
      this(dvalue, dtype);
      setRestriction(restriction, rnames);
   }

   /**
    * Get the normalized runtime value.
    * @param scalar true to return the first value if the value is an array.
    * @return the normalized runtime value.
    */
   public Object getRuntimeValue(boolean scalar) {
      Object robj = this.robj;

      if(robj == null) {
         synchronized(this) {
            robj = this.robj;

            if(robj == null) {
               updateRObject(scalar);
               robj = this.robj;
            }
         }
      }

      if(scalar && robj instanceof Object[]) {
         Object[] arr = (Object[]) robj;

         // when no item is selected, we should check whether default value
         // could be returned for data types like Boolean, or restriction is
         // defined
         if(arr.length == 0) {
            return findValue(null);
         }

         return arr[0];
      }

      return robj;
   }

   /**
    * Clone the object.
    * @return the cloned object.
    */
   @Override
   public DynamicValue clone() {
      try {
         return (DynamicValue) super.clone();
      }
      catch(Exception ex) {
         LOG.error("Failed to clone DynamicValue", ex);
      }

      return null;
   }

   /**
    * Check if equals another object.
    * @param obj the specified object to compare.
    * @return <tt>true</tt> if equals, <tt>false</tt> otherwise.
    */
   public boolean equals(Object obj) {
      if(!(obj instanceof DynamicValue)) {
         return false;
      }

      DynamicValue dval2 = (DynamicValue) obj;

      return Tool.equals(dvalue, dval2.dvalue);
   }

   /**
    * Calculate the hashcode of the dynamic value.
    */
   public int hashCode() {
      int hash = 0;

      if(dvalue != null) {
         hash += dvalue.hashCode();
      }

      return hash;
   }

   /**
    * Get the data type of this dynamic value.
    * @return the data type.
    */
   public String getDataType() {
      return dtype;
   }

   /**
    * Set the data type to this dynamic value.
    * @param dtype the specified data type.
    */
   public void setDataType(String dtype) {
      this.dtype = dtype;
   }

   /**
    * Get the string representation.
    * @return the string representation.
    */
   public String toString() {
      return dvalue;
   }

   /**
    * Check if is case sensitive when comparing two string values.
    * @return <tt>true</tt> if case sensitive, <tt>false</tt> otherwise.
    */
   public boolean isCaseSensitive() {
      return sensitive;
   }

   /**
    * Set whether case sensitive when comparing two string values.
    * @param sensitive <tt>true</tt> if case sensitive, <tt>false</tt>
    * otherwise.
    */
   public void setCaseSensitive(boolean sensitive) {
      this.sensitive = sensitive;
   }

   /**
    * Set the restriction, sorted in priority order. If not match data, the
    * first element in this array is returned by default.
    * @param restriction the specified restriction.
    */
   public void setRestriction(Object[] restriction) {
      if(restriction != null && restriction.length == 0) {
         throw new RuntimeException("Invalid restriction found: " + restriction);
      }

      this.restriction = restriction;
   }

   /**
    * Set the int restriction.
    * @param restriction the specified int array as the restriction.
    * @param rnames the names of the values. The name can be returned by
    * variable or script, and it is used to lookup the value.
    */
   public void setRestriction(int[] restriction, String[] rnames) {
      if(restriction.length != rnames.length) {
         throw new RuntimeException("Restriction names must match values.");
      }

      Integer[] arr = new Integer[restriction.length];

      for(int i = 0; i < arr.length; i++) {
         arr[i] = restriction[i];
      }

      this.rnames = rnames;
      setRestriction(arr);
   }

   /**
    * Return the string representation of the value if it's set as restrictions.
    */
   public String getName() {
      Object rval = getRuntimeValue(true);

      if(restriction != null && rnames != null && rval != null) {
         for(int i = 0; i < restriction.length && i < rnames.length; i++) {
            if(rval.toString().equals(restriction[i].toString())) {
               return i < rnames.length ? rnames[i] : null;
            }
         }
      }

      return null;
   }

   /**
    * Set the boolean restriction.
    * @param restriction the specified boolean array as the restriction.
    */
   public void setRestriction(boolean[] restriction) {
      Boolean[] arr = new Boolean[restriction.length];

      for(int i = 0; i < arr.length; i++) {
         arr[i] = Boolean.valueOf(restriction[i]);
      }

      setRestriction(arr);
   }

   /**
    * Get the restrictions.
    * @return the restrictions if any.
    */
   public Object getRestriction() {
      return restriction;
   }

   /**
    * Set the dynamic value string.
    */
   public void setDValue(String val) {
      if(!Tool.equals(dvalue, val)) {
         this.dvalue = val;
         rvalue = null;
         robj = null;
      }
   }

   /**
    * Get the dynamic value string.
    */
   public String getDValue() {
      return dvalue;
   }

   /**
    * Set the runtime value object.
    */
   public void setRValue(Object val) {
      if(rvalue != val) {
         rvalue = val;
         robj = null;
      }
   }

   /**
    * Get the runtime value object.
    */
   public Object getRValue() {
      if(!VSUtil.isVariableValue(dvalue) && !VSUtil.isScriptValue(dvalue) &&
         rvalue == null && dvalue != null)
      {
         rvalue = dvalue;
      }

      return rvalue;
   }

   /**
    * When rvalue is changed, update the cached object value.
    */
   private void updateRObject(boolean scalar) {
      Object robj = getRValue();

      if(robj instanceof Object[]) {
         if(scalar && XSchema.COLOR.equals(dtype)) {
            robj = findValue(robj);
         }
         else {
            Object[] arr = (Object[]) robj;
            Object[] tmp = new Object[arr.length];

            for(int k = 0; k < arr.length; k++) {
               // do not changed the original data directory
               // see bug1255335904269
               tmp[k] = findValue(arr[k]);
            }

            robj = tmp;
         }
      }
      else {
         robj = findValue(robj);
      }

      this.robj = robj;
   }

   /**
    * Find the value in the restriction list.
    */
   private Object findValue(Object val) {
      Object robj = null;

      if(XSchema.STRING.equals(dtype)) {
         robj = val;
      }
      else if(XSchema.BOOLEAN.equals(dtype)) {
         String txt = val + "";

         // for boolean, 1 -> true, 0 -> false, "str" -> str, null -> false
         if(val instanceof Number) {
            robj = ((Number) val).intValue() != 0;
         }
         else if("true".equalsIgnoreCase(txt)) {
            robj = Boolean.TRUE;
         }
         else if("false".equalsIgnoreCase(txt)) {
            robj = Boolean.FALSE;
         }
         else if("show".equalsIgnoreCase(txt)) {
            robj = Boolean.TRUE;
         }
         else if("hide".equalsIgnoreCase(txt)) {
            robj = Boolean.FALSE;
         }
         else if(val instanceof Boolean) {
            robj = val;
         }
         else {
            robj = val != null;
         }
      }
      else {
         robj = Tool.getData(dtype, val);
      }

      if(restriction == null) {
         return robj;
      }

      // check if the value is on the list
      for(int i = 0; i < restriction.length; i++) {
         if(Tool.equals(restriction[i], robj, sensitive)) {
            return restriction[i];
         }
      }

      // lookup by name
      int idx = findName(val);

      if(idx >= 0 && idx < restriction.length) {
         return restriction[idx];
      }

      return restriction[0];
   }

   /**
    * Find the value in the restriction list.
    */
   private int findName(Object name) {
      if(rnames == null) {
         return -1;
      }

      // null and "" are treated as same value
      if(name == null) {
         name = "";
      }

      for(int i = 0; i < rnames.length; i++) {
         if(Tool.equals(rnames[i], name, sensitive)) {
            return i;
         }
      }

      String txt = name.toString();

      // usability issue, support "none" properly
      if(Tool.equals("none", txt, sensitive)) {
         name = "";
      }

      for(int i = 0; i < rnames.length; i++) {
         if(Tool.equals(rnames[i], name, sensitive)) {
            return i;
         }
      }

      return -1;
   }

   private String dtype = XSchema.STRING;
   private boolean sensitive = false;
   private Object[] restriction = null; // all possible values
   private String[] rnames = null; // names of the values

   private String dvalue;
   private Object rvalue;
   private Object robj; // cached runtime object

   private static final Logger LOG =
      LoggerFactory.getLogger(DynamicValue.class);
}
