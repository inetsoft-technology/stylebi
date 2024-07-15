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
package inetsoft.util.script;


import org.mozilla.javascript.Scriptable;

import java.awt.*;
import java.util.Stack;

/**
 * This class holds the context of a formula execution. A context is set and
 * accessible in the same thread of the execution.
 * 
 * @version 8.0, 7/27/2005
 * @author InetSoft Technology Corp
 */
public class FormulaContext {
   /**
    * Get the containing table. 
    * The table is a subclass of XTable.
    */
   public static Object getTable() {
      return get(table);
   }
   
   /**
    * Set the the containing table.
    * The table should be a subclass of XTable.
    */
   public static void pushTable(Object lens) {
      push(table, lens);
   }
    
   /**
    * Remove the containing table.
    */
   public static void popTable() {
      pop(table);
   }

   /**
    * Get the location of the formula in the table lens.
    * @return the cell location where x is column index and y is row index.
    */
   public static Point getCellLocation() {
      return (Point) get(cell);
   }
   
   /**
    * Set the location of the formula in the table lens.
    */
   public static void pushCellLocation(Point loc) {
      push(cell, loc);
   }
    
   /**
    * Remove the location of the formula in the table lens.
    */
   public static void popCellLocation() {
      pop(cell);
   }
  
   /**
    * Get the containing scope of the formula execution.
    */
   public static Scriptable getScope() {
      return (Scriptable) get(scope);
   }
   
   /**
    * Set the containing scope of the formula execution.
    */
   public static void pushScope(Scriptable scriptable) {
      push(scope, scriptable);
   }
    
   /**
    * Remove the containing scope of the formula execution.
    */
   public static void popScope() {
      pop(scope);
   }

   /**
    * Set whether to run the script in a restricted environment.
    */
   public static void setRestricted(boolean restricted) {
      sandbox.set(restricted);
   }
   
   /**
    * Check whether to run the script in a restricted environment.
    */
   public static boolean isRestricted() {
      Boolean bobj = (Boolean) sandbox.get();
      return bobj != null && bobj.booleanValue();
   }

   /**
    * Push a new context to the stack.
    */
   private static void push(ThreadLocal local, Object val) {
      Stack stack = (Stack) local.get();

      if(stack == null) {
         stack = new Stack();
         local.set(stack);
      }
      
      stack.push(val);
   }
    
   /**
    * Pop a context from the stack.
    */
   private static void pop(ThreadLocal local) {
      Stack stack = (Stack) local.get();

      if(stack == null || stack.size() == 0) {
         throw new RuntimeException("No formula context is created.");
      }

      stack.pop();
   }

   /**
    * Get the value from the top of the stack in the thread local.
    */
   private static Object get(ThreadLocal local) {
      Stack stack = (Stack) local.get();

      if(stack != null && stack.size() > 0) {
         return stack.peek();
      }

      return null;
   }

   private static ThreadLocal table = new ThreadLocal();
   private static ThreadLocal cell = new ThreadLocal();
   private static ThreadLocal scope = new ThreadLocal();
   private static ThreadLocal sandbox = new ThreadLocal();
}
