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
import inetsoft.util.script.ScriptSpan;
import inetsoft.util.script.graal.OwnedVarScope;
import inetsoft.util.script.graal.ScriptArrayScope;
import inetsoft.util.script.graal.ScriptScope;
import inetsoft.util.script.graal.ScriptValueConverter;
import inetsoft.util.script.graal.pool.OwnedValueCodec;
import inetsoft.util.script.graal.pool.WsExecContext;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Value;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

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
      return valmap.containsKey(id) || owned.contains(id) || base.hasMember(id);
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

      Object stored = ScriptValueConverter.toOwnedVar(value);
      hasObjects |= stored instanceof Value;
      valmap.put(id, stored);
   }

   /**
    * At the end of a pooled batch, on the slot {@code span} claimed, save the context-free
    * form of the script object each owned var of that slot holds (Testing #77123, B1
    * residual): a later batch that runs on another slot rebuilds a Date from it. Runs at
    * every batch end, however the batch ended, as it runs no script code
    * ({@link OwnedValueCodec}). A var holding an object of another slot keeps its snapshot:
    * that object was not used in this batch. A value that cannot be read is lost (read as
    * undefined, with a warning, on another slot), never left with an older snapshot. Nothing
    * with the pool off. Never throws a RuntimeException: it runs in the batch's finally.
    */
   public void snapshotOwnedObjects(ScriptSpan span) {
      if(!hasObjects) {
         return;
      }

      Set<String> saved = null;

      try {
         OwnedValueCodec codec = OwnedValueCodec.forSpan(span);

         if(codec == null) {
            // pool off, or no slot claimed (no formula ran): the snapshots are current. A
            // claimed slot that cannot be saved (closed): its objects are lost
            loseObjectsOf(OwnedValueCodec.claimedContext(span));
            return;
         }

         saved = new HashSet<>();
         Context context = codec.context();
         Map<Value, Object> ids = new HashMap<>();

         for(Object o : valmap.entrySet()) {
            Map.Entry<?, ?> e = (Map.Entry<?, ?>) o;
            String name = String.valueOf(e.getKey());

            if(!(e.getValue() instanceof Value v) || !owned.contains(name)) {
               continue;
            }

            Object node;

            try {
               if(!context.equals(v.getContext())) {
                  continue;
               }

               node = codec.snapshot(v, ids);
            }
            catch(RuntimeException ex) {
               node = OwnedValueCodec.UNREADABLE;
            }

            snapshots.put(name, node);
            saved.add(name);
         }

         snapshots.keySet().removeIf(k -> !(valmap.get(k) instanceof Value));
      }
      catch(RuntimeException ex) {
         LOG.debug("Failed to save the script objects of formula variables", ex);

         for(Object o : valmap.entrySet()) {
            Map.Entry<?, ?> e = (Map.Entry<?, ?>) o;
            String name = String.valueOf(e.getKey());

            if(e.getValue() instanceof Value && owned.contains(name) &&
               (saved == null || !saved.contains(name)))
            {
               snapshots.put(name, OwnedValueCodec.UNREADABLE);
            }
         }
      }
   }

   // mark every owned var holding an object of context (or one whose context cannot be
   // read) lost: never left with an older snapshot
   private void loseObjectsOf(Context context) {
      if(context == null) {
         return;
      }

      for(Object o : valmap.entrySet()) {
         Map.Entry<?, ?> e = (Map.Entry<?, ?>) o;
         String name = String.valueOf(e.getKey());

         if(e.getValue() instanceof Value v && owned.contains(name)) {
            boolean of;

            try {
               of = context.equals(v.getContext());
            }
            catch(RuntimeException ex) {
               of = true;
            }

            if(of) {
               snapshots.put(name, OwnedValueCodec.UNREADABLE);
            }
         }
      }
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
      // no formula reads them again in this scope
      snapshots.clear();
      hasObjects = false;
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
    * it, where it cannot be used (a live reference fails or reads another thread's context).
    * A Date is rebuilt there from its batch-end snapshot (Testing #77123, B1 residual); any
    * other object reads as undefined, as the pool's per-batch reset gave, with one warning
    * per var naming what it holds.
    */
   private Object ownedObject(String id, Value v) {
      Context current = WsExecContext.currentContext();

      if(current == null || isOf(v, current)) {
         return v;
      }

      OwnedValueCodec codec = snapshots.isEmpty() ? null : OwnedValueCodec.current();

      if(codec != null) {
         rebuildForeign(codec, current);

         if(valmap.get(id) instanceof Value nv && isOf(nv, current)) {
            return nv;
         }
      }

      valmap.remove(id);

      if(warned.add(id)) {
         String kind = snapshots.get(id) instanceof OwnedValueCodec.Lost lost
            ? lost.kind() : "a script object";
         LOG.warn("The formula variable \"{}\" holds {} created on another " +
                  "script context of the worksheet context pool; it cannot be used there and " +
                  "reads as undefined. Keep a number, string, boolean or Date in a variable " +
                  "that must last for the whole table.", id, kind);
      }

      return UNDEFINED;
   }

   /**
    * Rebuild in {@code current} every owned var that holds a Date of another context, from
    * its snapshot, all at once so two vars holding one Date still hold one Date. A Date with
    * own properties or of a subclass is rebuilt as a plain Date from its time value, with one
    * warning per var. A var that holds anything else is left for its read.
    */
   private void rebuildForeign(OwnedValueCodec codec, Context current) {
      IdentityHashMap<Object, Value> built = new IdentityHashMap<>();

      for(Object o : valmap.entrySet()) {
         @SuppressWarnings("unchecked")
         Map.Entry<Object, Object> e = (Map.Entry<Object, Object>) o;
         String name = String.valueOf(e.getKey());

         if(!(e.getValue() instanceof Value v) || !owned.contains(name) || isOf(v, current)) {
            continue;
         }

         Object node = snapshots.get(name);
         Value nv;

         try {
            nv = codec.rebuild(node, built);
         }
         catch(RuntimeException ex) {
            // e.g. the batch was interrupted: the var is lost (undefined + warning on its
            // read), never a half-rebuilt alias
            LOG.debug("Failed to rebuild the Date of the formula variable {}", name, ex);
            snapshots.put(name, OwnedValueCodec.UNREADABLE);
            continue;
         }

         if(nv == null) {
            continue;
         }

         e.setValue(nv);

         if(node instanceof OwnedValueCodec.DateNode d && d.dropped() != null &&
            warned.add(name))
         {
            LOG.warn("The formula variable \"{}\" holds a Date {}; a batch of rows on " +
                     "another script context of the worksheet context pool rebuilds it as a " +
                     "plain Date from its time value only.", name, d.dropped());
         }
      }
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
   // the batch-end snapshots (OwnedValueCodec nodes) of the owned vars that hold script
   // objects, by var name; confined like valmap (written in the batch's finally, before the
   // lens lock is released)
   private final HashMap<String, Object> snapshots = new HashMap<>();
   // set once an owned var held a script object: a table of primitives takes no snapshot
   private boolean hasObjects;
   private static final Logger LOG = LoggerFactory.getLogger(TableRowScope.class);
}
