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
package inetsoft.util.script;

/**
 * Implementation of all Logical functions for JavaScript
 *
 * @version 8.0, 7/26/2005
 * @author InetSoft Technology Corp
 */
public class CalcLogic {
   /**
    * Returns TRUE if all its arguments are TRUE; returns FALSE if one or more 
    * argument is FALSE.
    * @return TRUE if all its arguments are TRUE; returns FALSE if one or more
    * argument is FALSE.
    */
   public static boolean and(Object logical1, Object logical2, Object logical3, 
                             Object logical4, Object logical5, Object logical6, 
                             Object logical7, Object logical8, Object logical9, 
                             Object logical10) {
      boolean val = true;
      
      logical1 = JavaScriptEngine.unwrap(logical1);
      logical2 = JavaScriptEngine.unwrap(logical2);
      logical3 = JavaScriptEngine.unwrap(logical3);
      logical4 = JavaScriptEngine.unwrap(logical4);
      logical5 = JavaScriptEngine.unwrap(logical5);
      logical6 = JavaScriptEngine.unwrap(logical6);
      logical7 = JavaScriptEngine.unwrap(logical7);
      logical8 = JavaScriptEngine.unwrap(logical8);
      logical9 = JavaScriptEngine.unwrap(logical9);
      logical10 = JavaScriptEngine.unwrap(logical10);
            
      if(logical1 != null) {
         val = val && Boolean.parseBoolean(logical1.toString());
      }
      else {
         return val;
      }
      
      if(logical2 != null) {
         val = val && Boolean.parseBoolean(logical2.toString());
      }
      else {
         return val;
      }
      
      if(logical3 != null) {
         val = val && Boolean.parseBoolean(logical3.toString());
      }
      else {
         return val;
      }
      
      if(logical4 != null) {
         val = val && Boolean.parseBoolean(logical4.toString());
      }
      else {
         return val;
      }
      
      if(logical5 != null) {
         val = val && Boolean.parseBoolean(logical5.toString());
      }
      else {
         return val;
      }
      
      if(logical6 != null) {
         val = val && Boolean.parseBoolean(logical6.toString());
      }
      else {
         return val;
      }
      
      if(logical7 != null) {
         val = val && Boolean.parseBoolean(logical7.toString());
      }
      else {
         return val;
      }
      
      if(logical8 != null) {
         val = val && Boolean.parseBoolean(logical8.toString());
      }
      else {
         return val;
      }
      
      if(logical9 != null) {
         val = val && Boolean.parseBoolean(logical9.toString());
      }
      else {
         return val;
      }
      
      if(logical10 != null) {
         val = val && Boolean.parseBoolean(logical10.toString());
      }
      
      return val;
   }
   
   /**
    * Returns TRUE at least one of it's arguments is TRUE; returns FALSE if 
    * none of the arguments are FALSE.
    * @return TRUE at least one of it's arguments is TRUE; returns FALSE if
    * none of the arguments are FALSE.
    */
   public static boolean or(Object logical1, Object logical2, Object logical3, 
                            Object logical4, Object logical5, Object logical6, 
                            Object logical7, Object logical8, Object logical9, 
                            Object logical10) {
      boolean val = false;
      
      logical1 = JavaScriptEngine.unwrap(logical1);
      logical2 = JavaScriptEngine.unwrap(logical2);
      logical3 = JavaScriptEngine.unwrap(logical3);
      logical4 = JavaScriptEngine.unwrap(logical4);
      logical5 = JavaScriptEngine.unwrap(logical5);
      logical6 = JavaScriptEngine.unwrap(logical6);
      logical7 = JavaScriptEngine.unwrap(logical7);
      logical8 = JavaScriptEngine.unwrap(logical8);
      logical9 = JavaScriptEngine.unwrap(logical9);
      logical10 = JavaScriptEngine.unwrap(logical10);
      
      if(logical1 != null) {
         val = val || Boolean.parseBoolean(logical1.toString());
      }
      else {
         return val;
      }
      
      if(logical2 != null) {
         val = val || Boolean.parseBoolean(logical2.toString());
      }
      else {
         return val;
      }
      
      if(logical3 != null) {
         val = val || Boolean.parseBoolean(logical3.toString());
      }
      else {
         return val;
      }
      
      if(logical4 != null) {
         val = val || Boolean.parseBoolean(logical4.toString());
      }
      else {
         return val;
      }
      
      if(logical5 != null) {
         val = val || Boolean.parseBoolean(logical5.toString());
      }
      else {
         return val;
      }
      
      if(logical6 != null) {
         val = val || Boolean.parseBoolean(logical6.toString());
      }
      else {
         return val;
      }
      
      if(logical7 != null) {
         val = val || Boolean.parseBoolean(logical7.toString());
      }
      else {
         return val;
      }
      
      if(logical8 != null) {
         val = val || Boolean.parseBoolean(logical8.toString());
      }
      else {
         return val;
      }
      
      if(logical9 != null) {
         val = val || Boolean.parseBoolean(logical9.toString());
      }
      else {
         return val;
      }
      
      if(logical10 != null) {
         val = val || Boolean.parseBoolean(logical10.toString());
      }
      
      return val;
   }
   
   /**
    * Returns negation of it's argument
    * @param logical evalualed logical values
    * @return TRUE if argument is FALSE and vice versa
    */
   public static boolean not(Object logical) {
      logical = JavaScriptEngine.unwrap(logical);
      return !Boolean.parseBoolean(logical.toString());
   }
   
   /**
    * Returns one value if a condition you specify evaluates to TRUE and 
    * another value if it evaluates to FALSE
    * @param logical_test logical value
    * @param value_if_true value returned if the logical value is TRUE
    * @param value_if_false value returned if the logical value is FALSE
    * @return returned value
    */
   public static Object iif(Object logical_test, Object value_if_true,
                            Object value_if_false) {
      logical_test = JavaScriptEngine.unwrap(logical_test);
      value_if_true = JavaScriptEngine.unwrap(value_if_true);
      value_if_false = JavaScriptEngine.unwrap(value_if_false);
      
      if(Boolean.parseBoolean(logical_test.toString())) {
         if(value_if_true != null && !"".equals(value_if_true)) {
            return value_if_true;
         }
         
         return 0;
      }
      else {
         if(value_if_false == null) {
            return Boolean.FALSE;
         }
         
         return value_if_false;
      }
   }
}
