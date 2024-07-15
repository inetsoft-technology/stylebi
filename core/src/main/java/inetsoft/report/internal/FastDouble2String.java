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
package inetsoft.report.internal;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.*;

/**
 *
 * @version 5.1, 9/20/2003
 * @author InetSoft Technology Corp
 */
public class FastDouble2String {
   public FastDouble2String(int size) {
      doublestr = Collections.synchronizedMap(new HashMap(size));
      fracstr = Collections.synchronizedMap(new HashMap(size));
      intstr = Collections.synchronizedMap(new HashMap(size));
      this.size = size;
   }

   /*
    * Two caches are because majority of cache will be happening in fracstr. 
    * It has a separate cache
    * therefore doublestr cache will stay rather static and avoid the most 
    * expensive call.
    */
   public String double2String(double val) {
      if(val == 0.0) {
         return pzero;
      }
      else if(val == -0.0) {
         return nzero;
      }

      int intval = (int) val;
      
      // optimization, most numbers are integer so handle it as a special case
      if(intval == val) {
         return Integer.toString(intval);
      }
      
      int intpart = Math.abs(intval);
      double fraction = Math.abs(val) - intpart;

      if(fraction > 0.00000000000001 && fraction < 0.001) {
         Double valObj = Double.valueOf(val);
         Object obj = doublestr.get(valObj);

         if(obj != null) {
            return (String) obj;
         }

         if(doublestr.size() > size) {
            doublestr.clear();
         }

         String str = numFmt.format(val);

         doublestr.put(valObj, str);

         return str;
      }
      else if(fraction > 0.00000000000001) {
         Double valObj = Double.valueOf(val);
         Object obj = fracstr.get(valObj);

         if(obj != null) {
            return (String) obj;
         }

         StringBuilder result = new StringBuilder(30);

         if(val < 0) {
            result.append("-");
         }

         if(intpart > 0) {
            result.append(Integer.toString(intpart));
         }
         else {
            result.append("0");
         }

         String str = Double.toString(fraction);
         int dpoint = intpart > 0 ? 4 : getFirstNonZero(str) + 6;

         result.append(str.substring(1, Math.min(str.length(), dpoint)));
         str = result.toString();
         
         if(fracstr.size() > size) {
            fracstr.clear();
         }

         fracstr.put(valObj, str);

         return str;
      }
      else {
         Double valObj = Double.valueOf(val);
         Object obj = intstr.get(valObj);

         if(obj != null) {
            return (String) obj;
         }

         StringBuilder result = new StringBuilder(30);

         if(val < 0) {
            result.append("-");
         }

         if(intpart > 0) {
            result.append(Integer.toString(intpart));
         }
         else {
            result.append("0");
         }

         String str = result.toString();

         if(intstr.size() > size) {
            intstr.clear();
         }

         intstr.put(valObj, str);
         return str;
      }
   }

   /**
    * Find the position of the first non-zero char.
    */
   private static int getFirstNonZero(String str) {
      for(int i = 0; i < str.length(); i++) {
         if(str.charAt(i) != '0') {
            return i;
         }
      }

      return str.length();
   }
   
   // the cache for values that would need to call DecimalFormat(most expensive)
   private Map doublestr;
   // the cache for values that would need to call Double.toString()
   private Map fracstr;
   // the cache for values that would need to call Long.toString() & strbuffers
   private Map intstr;
   private int size = 20;
   private static String pzero = "0";
   private static String nzero = "-0.0";
   static final DecimalFormat numFmt = new DecimalFormat("#.############",
      new DecimalFormatSymbols(Locale.ENGLISH));
}

