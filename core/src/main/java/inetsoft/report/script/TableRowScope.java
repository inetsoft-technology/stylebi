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
import inetsoft.util.script.graal.ScriptArrayScope;
import inetsoft.util.script.graal.ScriptScope;

import java.util.HashMap;

/**
 * This is used to execute script in a TableRow scope. It makes the builtin
 * names override the column names, so if a builtin name is accessed
 * (e.g. new Date()) it would work and the column would be accessible using
 * regular TableRow like field['Date'].
 */
public class TableRowScope implements DynamicScope, ScriptArrayScope {
   public TableRowScope(TableRow base, String basename) {
      this.base = base;
      this.basename = basename;
   }

   public String getClassName() {
      return "TableRowScope";
   }

   @Override
   public boolean hasMember(String id) {
      return valmap.containsKey(id) || base.hasMember(id);
   }

   @Override
   public Object getMember(String id) {
      if(valmap.containsKey(id)) {
         return valmap.get(id);
      }
      else if(basename != null && basename.equals(id)) {
         return base;
      }

      // avoid overriding builtin from column; returning null lets the
      // engine resolve the builtin (Array/Math/Date) from the global scope
      switch(id) {
      case "Array":
      case "Math":
         return null;
      case "Date":
         if(builtinDate) {
            return null;
         }
      }

      return base.getMember(id);
   }

   @Override
   public Object getArrayElement(long index) {
      return base.getArrayElement(index);
   }

   @Override
   public long getArraySize() {
      return base.getArraySize();
   }

   @Override
   public void putMember(String id, Object value) {
      if(!base.putLocal(id, value)) {
         valmap.put(id, value);
      }
   }

   @Override
   public Object[] getMemberKeys() {
      return base.getMemberKeys();
   }

   @Override
   public ScriptScope getParentScope() {
      return parent;
   }

   public void setParentScope(ScriptScope scope) {
      this.parent = scope;
   }

   /**
    * Set if treating 'Date' as builtin date or find it in scope first.
    */
   public void setBuiltinDate(boolean builtin) {
      this.builtinDate = builtin;
   }

   private TableRow base;
   private String basename;
   private ScriptScope parent;
   private boolean builtinDate = true;
   private HashMap valmap = new HashMap(); // from put()
}
