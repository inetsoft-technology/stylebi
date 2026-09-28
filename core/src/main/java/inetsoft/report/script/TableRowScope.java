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
import inetsoft.util.script.graal.OwnedVarScope;
import inetsoft.util.script.graal.ScriptArrayScope;
import inetsoft.util.script.graal.ScriptScope;
import inetsoft.util.script.graal.ScriptValueConverter;
import inetsoft.util.script.graal.pool.WsExecContext;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Value;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

/**
 * This is used to execute script in a TableRow scope. It makes the builtin
 * names override the column names, so if a builtin name is accessed
 * (e.g. new Date()) it would work and the column would be accessible using
 * regular TableRow like field['Date'].
 */
public class TableRowScope implements DynamicScope, ScriptArrayScope, OwnedVarScope {
   public TableRowScope(TableRow base, String basename) {
      this.base = base;
      this.basename = basename;
   }

   public String getClassName() {
      return "TableRowScope";
   }

   @Override
   public boolean hasMember(String id) {
      return valmap.containsKey(id) || owned.contains(id) ||
         (basename != null && basename.equals(id)) || base.hasMember(id);
   }

   /**
    * Set the top-level var names of the formulas run in this scope, which this scope owns
    * (Testing #77123): their values are kept here, for the rows of this scope's row table
    * only, as Rhino kept them on the row scope. A formula table sets them once, when it
    * creates the scope for a new row table, so each computation of the table starts with
    * none of the values.
    */
   public void setOwnedVars(Set<String> names) {
      this.owned = names == null || names.isEmpty() ? Set.of() : Set.copyOf(names);
   }

   @Override
   public boolean ownsVar(String name) {
      return owned.contains(name);
   }

   @Override
   public void putOwnedVar(String id, Value value) {
      // a var named like a column is the row's cell, as a write of the name always was.
      // checked before converting: toHostStored rejects a function in a pooled context
      if(base.hasMember(id) && base.putLocal(id, ScriptValueConverter.toHostStored(value))) {
         return;
      }

      valmap.put(id, ScriptValueConverter.toOwnedVar(value));
   }

   /**
    * Drop the script objects the owned vars hold, once the table has computed all its rows
    * and runs no formula until it is computed again, in a new scope, or is disposed: a script
    * object keeps its whole context alive, which in a pool may be a retired one. Primitives
    * are kept. A table that is read only in part (a first page) and then kept in a cache keeps
    * its objects until it is completed, invalidated (the scope is replaced and becomes
    * garbage) or disposed; with the pool off this pins nothing, as the table's script
    * environment keeps its context anyway.
    */
   public void releaseOwnedObjects() {
      ((HashMap<?, ?>) valmap).entrySet().removeIf(
         e -> e.getValue() instanceof Value && owned.contains(e.getKey()));
   }

   @Override
   public Object getMember(String id) {
      if(valmap.containsKey(id)) {
         Object v = valmap.get(id);
         return v instanceof Value gv && owned.contains(id) ? ownedObject(id, gv) : v;
      }
      else if(owned.contains(id) && !base.hasMember(id)) {
         // declared and not assigned yet: undefined (not null), not a same-named name up
         // the chain
         return UNDEFINED;
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

   /**
    * The script object an owned var holds, if it belongs to the context executing now. With
    * the context pool, a batch of rows can run on another context than the batch that created
    * it, where it cannot be used (a live reference fails or reads another thread's context):
    * it reads as undefined, as the pool's per-batch reset gave, with one warning per var.
    */
   private Object ownedObject(String id, Value v) {
      Context current = WsExecContext.currentContext();

      if(current == null || isOf(v, current)) {
         return v;
      }

      valmap.remove(id);

      if(warned.add(id)) {
         LOG.warn("The formula variable \"{}\" holds a script object created on another " +
                  "script context of the worksheet context pool; it cannot be used there and " +
                  "reads as undefined. Keep a number, string or boolean in a variable that " +
                  "must last for the whole table.", id);
      }

      return UNDEFINED;
   }

   private static boolean isOf(Value v, Context context) {
      try {
         return context.equals(v.getContext());
      }
      catch(RuntimeException ex) {
         return false;
      }
   }

   private TableRow base;
   private String basename;
   private ScriptScope parent;
   private boolean builtinDate = true;
   // from put(), and the values of the owned vars; written and read only by the formulas of
   // the one table this scope belongs to, under that table's lock
   private HashMap valmap = new HashMap();
   private Set<String> owned = Set.of();
   private final Set<String> warned = new HashSet<>();
   private static final Logger LOG = LoggerFactory.getLogger(TableRowScope.class);
}
