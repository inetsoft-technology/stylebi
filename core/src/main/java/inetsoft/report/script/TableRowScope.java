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
package inetsoft.report.script;

import inetsoft.util.script.DynamicScope;
import org.mozilla.javascript.Scriptable;
import org.mozilla.javascript.ScriptableObject;

import java.util.HashMap;

/**
 * This is used to execute script in a TableRow scope. It makes the builtin
 * names override the column names, so if a builtin name is accessed
 * (e.g. new Date()) it would work and the column would be accessible using
 * regular TableRow like field['Date'].
 */
public class TableRowScope extends ScriptableObject implements DynamicScope {
   public TableRowScope(TableRow base, String basename) {
      this.base = base;
      this.basename = basename;
   }

   @Override
   public String getClassName() {
      return "TableRowScope";
   }

   @Override
   public boolean has(String id, Scriptable start) {
      return valmap.containsKey(id) || base.has(id, start);
   }

   @Override
   public boolean has(int index, Scriptable start) {
      return base.has(index, start);
   }

   @Override
   public Object get(String id, Scriptable start) {
      if(valmap.containsKey(id)) {
         return valmap.get(id);
      }
      else if(basename != null && basename.equals(id)) {
         return base;
      }

      // avoid overriding builtin from column
      switch(id) {
      case "Array":
      case "Math":
         return super.get(id, start);
      case "Date":
         if(builtinDate) {
            return super.get(id, start);
         }
      }

      return base.get(id, start);
   }

   @Override
   public Object get(int index, Scriptable start) {
      return base.get(index, start);
   }

   @Override
   public void put(String id, Scriptable start, Object value) {
      if(!base.putLocal(id, start, value)) {
         valmap.put(id, value);
      }
   }

   @Override
   public void put(int index, Scriptable start, Object value) {
      base.put(index, start, value);
   }

   @Override
   public Object getDefaultValue(Class hint) {
      return base.getDefaultValue(hint);
   }

   @Override
   public Object[] getIds() {
      return base.getIds();
   }

   @Override
   public boolean hasInstance(Scriptable value) {
      return base.hasInstance(value);
   }

   @Override
   public Scriptable getPrototype() {
      return base.getPrototype();
   }

   @Override
   public void setPrototype(Scriptable prototype) {
      base.setPrototype(prototype);
   }

   @Override
   public Scriptable getParentScope() {
      return base.getParentScope();
   }

   @Override
   public void setParentScope(Scriptable scope) {
      super.setParentScope(scope);
      base.setParentScope(scope);
   }

   /**
    * Set if treating 'Date' as builtin date or find it in scope first.
    */
   public void setBuiltinDate(boolean builtin) {
      this.builtinDate = builtin;
   }

   private TableRow base;
   private String basename;
   private boolean builtinDate = true;
   private HashMap valmap = new HashMap(); // from put()
}
