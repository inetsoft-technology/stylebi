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
import inetsoft.util.script.graal.pool.SlotTenant;
import inetsoft.util.script.graal.pool.WsExecContext;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Value;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.locks.Lock;
import java.util.stream.Collectors;

/**
 * This is used to execute script in a TableRow scope. It makes the builtin
 * names override the column names, so if a builtin name is accessed
 * (e.g. new Date()) it would work and the column would be accessible using
 * regular TableRow like field['Date'].
 */
public class TableRowScope implements DynamicScope, ScriptArrayScope, OwnedVarScope,
   SlotTenant
{
   public TableRowScope(TableRow base, String basename) {
      this.base = base;
      this.basename = basename;
   }

   public String getClassName() {
      return "TableRowScope";
   }

   @Override
   public boolean hasMember(String id) {
      if(valmap.containsKey(id) || owned.contains(id)) {
         return true;
      }

      // a builtin this scope defers to (see getMember) is absent here, so the name resolves
      // to the global builtin, not to a same-named column (Bug #77999)
      return !isDeferredBuiltin(id) && base.hasMember(id);
   }

   /**
    * Whether a column named {@code id} must not hide the global builtin of that name: Array
    * and Math always, Date when {@link #setBuiltinDate} is set.
    */
   private boolean isDeferredBuiltin(String id) {
      return "Array".equals(id) || "Math".equals(id) || builtinDate && "Date".equals(id);
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
    * The lock of the formula table this scope belongs to, which confines the owned vars. The
    * pool takes it without waiting before it hands off the objects of the vars (Testing
    * #77123, B1 residual part 2); without it, arrays and objects are not kept across contexts.
    */
   public void setLensLock(Lock lock) {
      this.lensLock = lock;
   }

   /**
    * At the end of a pooled batch, on the slot {@code span} claimed (Testing #77123, B1
    * residual). Never throws a RuntimeException: it runs in the batch's finally. Nothing with
    * the pool off.
    * <ul>
    *    <li>While the owned vars hold only Dates, save the context-free form of each Date of
    *    that slot: a later batch that runs on another slot rebuilds it from its time value.
    *    It runs at every batch end, however the batch ended, as it runs no script code
    *    ({@link OwnedValueCodec}). A value that cannot be read is lost (read as undefined,
    *    with a warning, on another slot), never left with an older snapshot.</li>
    *    <li>Once a var holds another script object (an array, an object), this table is
    *    <i>resident</i> (part 2): nothing is copied here, whatever the claim depth (amendment
    *    A1). The slot becomes the home of the objects of its context: once idle, it is kept
    *    for this table, and this table's next batch prefers it. The objects are saved as one
    *    tree, Dates included (A8), only at a hand-off: a batch on another context pulls them
    *    from the idle home, or the pool closes, expires or takes over the home.</li>
    *    <li>A batch that shares a span which stays open after it ({@code shared}, and {@link
    *    OwnedValueCodec#outlives}: a condition filter's population, another table's batch, a
    *    span nested in a query build's script) does not make its slot a home: the thread holds that slot until the
    *    outer span ends, while another thread's batch of this table may need the objects
    *    (Testing #77123, cond-home, A1). The objects are saved as one tree there instead, as
    *    at a hand-off, and the next batch rebuilds them on its context. Only a table's batch
    *    that is not resident yet at its start shares a span (its first one, or the one that
    *    makes a table of primitives or Dates resident): the batches of a resident table take
    *    a context of their own, which is given back, as the home, at their end. If that tree
    *    would lose a value to the hand-off budget (the entry cap or the time bound), the slot
    *    is made the home instead, as before cond-home, and the table's later batches share
    *    the span they run in (round 3): the value stays live on the thread's context, and a
    *    batch on another thread loses it at a hand-off, as such a value always did (A6).</li>
    * </ul>
    *
    * @param shared {@code true} if the batch ran on the span of its thread, not on a claim of
    *               its own, nor nested in a batch of this table that has one.
    */
   public void snapshotOwnedObjects(ScriptSpan span, boolean shared) {
      if(!hasObjects) {
         return;
      }

      if(!resident) {
         snapshotDates(span);
      }

      if(resident) {
         try {
            if(shared && !spanHome && OwnedValueCodec.outlives(span)) {
               parkShared(span);
            }
            else {
               enrollHome(span);
            }
         }
         catch(RuntimeException ex) {
            LOG.debug("Failed to keep the script objects of formula variables", ex);
         }
      }
   }

   /**
    * @return {@code true} once an owned var held an array or an object that is not a Date, so
    * that this table keeps its objects on a home (B1 residual part 2): its batches then take a
    * context of their own (Testing #77123, cond-home), unless its objects are too big to save
    * as a tree at a batch end (round 3, {@link #snapshotOwnedObjects}).
    */
   public boolean takesOwnSpan() {
      return resident && !spanHome;
   }

   // a batch end on a span that outlives the batch: save the objects of its context as a tree
   private void parkShared(ScriptSpan span) {
      OwnedValueCodec codec = OwnedValueCodec.forSpan(span);

      if(codec == null) {
         // no slot claimed (no formula ran), or a closed one, whose objects are lost
         Context context = OwnedValueCodec.claimedContext(span);

         if(context != null) {
            for(Object o : valmap.entrySet()) {
               @SuppressWarnings("unchecked")
               Map.Entry<Object, Object> e = (Map.Entry<Object, Object>) o;

               if(e.getValue() instanceof Value v && owned.contains(e.getKey()) &&
                  isOf(v, context))
               {
                  e.setValue(OwnedValueCodec.UNREADABLE);
                  snapshots.remove(e.getKey());
               }
            }
         }

         return;
      }

      // a value too big for the budget stays live on this context, which becomes the home as
      // on a span that does not outlive the batch; so do the table's later batches (round 3)
      if(!park(codec, true)) {
         spanHome = true;
         enrollHome(span);
         return;
      }

      // this context is not a home of this table any more, if it was
      OwnedValueCodec.Home home = homes.remove(codec.context());

      if(home != null) {
         OwnedValueCodec.leave(home, this);
      }
   }

   private void snapshotDates(ScriptSpan span) {
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
         boolean object = false;

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

            // a script object that is not a Date: the table keeps them all on a home
            object |= node instanceof OwnedValueCodec.Lost && node != OwnedValueCodec.UNREADABLE;
            snapshots.put(name, node);
            saved.add(name);
         }

         snapshots.keySet().removeIf(k -> !(valmap.get(k) instanceof Value));
         resident = object && lensLock != null;
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

   // a resident table's batch end: the claimed slot is the home of the objects of its context
   private void enrollHome(ScriptSpan span) {
      OwnedValueCodec.Home home = OwnedValueCodec.homeOf(span);
      Context context = home != null ? home.context() : OwnedValueCodec.claimedContext(span);

      if(context == null) {
         return; // no slot claimed: no formula ran in this batch
      }

      boolean any = false;

      for(Object o : valmap.entrySet()) {
         @SuppressWarnings("unchecked")
         Map.Entry<Object, Object> e = (Map.Entry<Object, Object>) o;
         String name = String.valueOf(e.getKey());

         if(e.getValue() instanceof Value v && owned.contains(name) && isOf(v, context)) {
            // the live object is current: an older Date snapshot must never be read
            snapshots.remove(name);

            if(home == null) {
               // a closed slot: its objects cannot be saved, they are lost
               e.setValue(OwnedValueCodec.UNREADABLE);
            }

            any = true;
         }
      }

      if(any && home != null) {
         homes.put(context, home);
         OwnedValueCodec.enroll(home, this);
      }
   }

   /** What {@link #preferHome} returns when it changed nothing. */
   public static final Object NO_HINT = new Object();

   /**
    * Make this thread's next checkout prefer the home of this table's objects, around one
    * batch; call {@link #restoreHome} with the result in the batch's finally. Costs nothing
    * for a table that is not resident.
    */
   public Object preferHome() {
      return resident && !homes.isEmpty() ? OwnedValueCodec.preferHomeOf(this) : NO_HINT;
   }

   /** Restore the preference {@link #preferHome} returned. */
   public static void restoreHome(Object previous) {
      if(previous != NO_HINT) {
         OwnedValueCodec.restoreHomeHint(previous);
      }
   }

   /** The lens lock: the pool tryLocks it before it takes this table's idle home. */
   @Override
   public Lock handOffLock() {
      return lensLock;
   }

   /**
    * Hand off (the pool, holding the idle slot of {@code codec}): save the objects that live
    * there as one tree, under this table's lock taken without waiting.
    */
   @Override
   public boolean handOff(OwnedValueCodec codec) {
      Lock lock = lensLock;

      if(lock == null || !lock.tryLock()) {
         return false;
      }

      try {
         // re-entered by this table's own thread while it moves its objects
         if(busy) {
            return false;
         }

         busy = true;

         try {
            park(codec);
            homes.remove(codec.context());
         }
         finally {
            busy = false;
         }

         return true;
      }
      finally {
         lock.unlock();
      }
   }

   // save the objects of codec's context as one tree: each var then holds its tree root, or
   // the Lost of what it held that is not kept (A3: only that var)
   private void park(OwnedValueCodec codec) {
      park(codec, false);
   }

   // keepOnBudget: if a value would be lost to the hand-off budget, change nothing and
   // return false; the objects stay live on codec's context
   private boolean park(OwnedValueCodec codec, boolean keepOnBudget) {
      Context context = codec.context();
      List<String> names = new ArrayList<>();
      List<Value> values = new ArrayList<>();

      for(Object o : valmap.entrySet()) {
         Map.Entry<?, ?> e = (Map.Entry<?, ?>) o;
         String name = String.valueOf(e.getKey());

         if(e.getValue() instanceof Value v && owned.contains(name) && isOf(v, context)) {
            names.add(name);
            values.add(v);
         }
      }

      Object[] nodes = codec.snapshotTree(values);

      if(keepOnBudget && Arrays.stream(nodes).anyMatch(OwnedValueCodec::overBudget)) {
         return false;
      }

      // the vars this hand-off keeps as copies of arrays or objects: a copy of an object that
      // a lost var reached through a closure, a getter or a WeakMap no longer shares it
      // (Testing #77123, B1 residual). A bigint is immutable; a Date var already loses its
      // identity at the batch-end Date snapshot, so neither is named
      List<String> kept = new ArrayList<>();

      for(int i = 0; i < nodes.length; i++) {
         valmap.put(names.get(i), nodes[i]);
         snapshots.remove(names.get(i));

         if(nodes[i] instanceof OwnedValueCodec.TreeRef ref && ref.holdsCopies()) {
            kept.add(names.get(i));
         }
      }

      for(int i = 0; i < nodes.length; i++) {
         if(nodes[i] instanceof OwnedValueCodec.Lost lost && lost.hidesReferences() &&
            !kept.isEmpty())
         {
            hiddenFrom.put(names.get(i), kept);
         }
         else {
            hiddenFrom.remove(names.get(i));
         }
      }

      return true;
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
    * object keeps its whole context alive, which in a pool may be a retired one, and a home
    * is no longer kept for the table. Primitives are kept. A table that is read only in part
    * (a first page) and then kept in a cache keeps its objects until it is completed,
    * invalidated (the scope is replaced and becomes garbage, and the pool drops a home of a
    * collected scope) or disposed; with the pool off this pins nothing, as the table's script
    * environment keeps its context anyway.
    */
   public void releaseOwnedObjects() {
      ((HashMap<?, ?>) valmap).entrySet().removeIf(
         e -> owned.contains(e.getKey()) && isObjectSlot(e.getValue()));
      // no formula reads them again in this scope
      snapshots.clear();
      hiddenFrom.clear();
      hasObjects = false;
      resident = false;
      spanHome = false;

      for(OwnedValueCodec.Home home : homes.values()) {
         try {
            OwnedValueCodec.leave(home, this);
         }
         catch(RuntimeException ex) {
            LOG.debug("Failed to release the home of formula variables", ex);
         }
      }

      homes.clear();
   }

   private static boolean isObjectSlot(Object value) {
      return value instanceof Value || value instanceof OwnedValueCodec.TreeRef ||
         value instanceof OwnedValueCodec.Lost;
   }

   @Override
   public Object getMember(String id) {
      if(valmap.containsKey(id)) {
         Object v = valmap.get(id);

         if(owned.contains(id)) {
            if(v instanceof Value gv) {
               return ownedObject(id, gv);
            }

            if(v instanceof OwnedValueCodec.TreeRef || v instanceof OwnedValueCodec.Lost) {
               return foreign(id, WsExecContext.currentContext());
            }
         }

         return v;
      }
      else if(owned.contains(id) && !base.hasMember(id)) {
         // declared and not assigned yet: undefined (not null), not a same-named name up
         // the chain
         return UNDEFINED;
      }
      else if(basename != null && basename.equals(id)) {
         return base;
      }

      // avoid overriding builtin from column: hasMember reports these names absent, so the
      // engine resolves the builtin (Array/Math/Date) from the global scope
      if(isDeferredBuiltin(id)) {
         return null;
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
    * There it is rebuilt: a Date from its batch-end snapshot, the arrays and objects of a
    * resident table from a tree taken at a hand-off (Testing #77123, B1 residual); a value that
    * is not kept (a function, a class instance) reads as undefined, with one warning per var
    * naming what it holds.
    */
   private Object ownedObject(String id, Value v) {
      Context current = WsExecContext.currentContext();

      if(current == null || isOf(v, current)) {
         return v;
      }

      return foreign(id, current);
   }

   private Object foreign(String id, Context current) {
      OwnedValueCodec codec = current == null ? null : OwnedValueCodec.current();

      if(codec != null) {
         codec.crossRead();
         rebuildForeign(codec, current);

         if(valmap.get(id) instanceof Value nv && isOf(nv, current)) {
            return nv;
         }
      }

      Object held = valmap.remove(id);
      String copies = copiesOf(id, held);

      if(warned.add(id)) {
         if(held == OwnedValueCodec.HOME_BUSY) {
            LOG.warn("The formula variable \"{}\" holds an array or object that stays on a " +
                     "script context of the worksheet context pool that another thread is " +
                     "using; a batch of rows on another context reads it as undefined.", id);
         }
         else {
            String kind = held instanceof OwnedValueCodec.Lost lost ? lost.kind()
               : snapshots.get(id) instanceof OwnedValueCodec.Lost lost ? lost.kind()
               : "a script object";
            LOG.warn("The formula variable \"{}\" holds {} created on another " +
                     "script context of the worksheet context pool; it cannot be used there " +
                     "and reads as undefined. Keep a number, string, boolean, Date, or an " +
                     "array or plain object of them, in a variable that must last for the " +
                     "whole table.{}", id, kind, copies == null ? "" : copies);
         }
      }
      else if(copies != null) {
         // a later hand-off kept new copies: named on a line of their own, the var's own
         // warning is logged once
         LOG.warn("The formula variable \"{}\" was lost again at a hand-off of the worksheet " +
                  "context pool.{}", id, copies);
      }

      return UNDEFINED;
   }

   /**
    * The warning text naming the vars that the hand-off which lost {@code id} kept as
    * copies, if {@code held} hid references (a function's closure, a getter, a WeakMap, a
    * class instance): such a copy no longer shares an object that {@code id} reached, so it
    * may be stale once {@code id} is created again (Testing #77123, B1 residual). Each var is
    * named once, so these warnings are at most one per var; {@code null} if there is none
    * left to name.
    */
   private String copiesOf(String id, Object held) {
      List<String> kept = hiddenFrom.remove(id);

      if(kept == null || !(held instanceof OwnedValueCodec.Lost lost) ||
         !lost.hidesReferences())
      {
         return null;
      }

      List<String> fresh = kept.stream().filter(copied::add).toList();

      if(fresh.isEmpty()) {
         return null;
      }

      String names = fresh.stream().map(n -> "\"" + n + "\"").collect(Collectors.joining(", "));
      return " The same hand-off kept the variable" + (fresh.size() > 1 ? "s " : " ") + names +
         " of this table as a copy: an object of " + (fresh.size() > 1 ? "theirs" : "its") +
         " that \"" + id + "\" reached through a closure, a getter or setter, a WeakMap or " +
         "WeakSet, or a class instance, if any, keeps its values from before the hand-off and " +
         "no longer changes with \"" + id + "\".";
   }

   /**
    * Rebuild in {@code current} every owned var that holds an object of another context, all
    * at once, so two vars holding one object still hold one object. A resident table first
    * pulls the objects of each other home (a tree snapshot there, taken without waiting). A
    * Date with own properties or of a subclass kept by its batch-end snapshot is rebuilt as a
    * plain Date from its time value, with one warning per var. A var whose object is not kept
    * is left for its read.
    */
   private void rebuildForeign(OwnedValueCodec codec, Context current) {
      busy = true;

      try {
         if(resident) {
            pullHomes(current);
         }

         IdentityHashMap<Object, Value> built = new IdentityHashMap<>();

         for(Object o : valmap.entrySet()) {
            @SuppressWarnings("unchecked")
            Map.Entry<Object, Object> e = (Map.Entry<Object, Object>) o;
            String name = String.valueOf(e.getKey());

            if(!owned.contains(name)) {
               continue;
            }

            Object node;

            if(e.getValue() instanceof Value v) {
               if(isOf(v, current)) {
                  continue;
               }

               node = snapshots.get(name);
            }
            else if(e.getValue() instanceof OwnedValueCodec.TreeRef) {
               node = e.getValue();
            }
            else {
               continue;
            }

            Value nv;

            try {
               nv = codec.rebuild(node, built);
            }
            catch(RuntimeException ex) {
               // e.g. the batch was interrupted: the var is lost (undefined + warning on its
               // read), never a half-rebuilt alias
               LOG.debug("Failed to rebuild the object of the formula variable {}", name, ex);

               if(node instanceof OwnedValueCodec.TreeRef) {
                  e.setValue(OwnedValueCodec.UNREADABLE);
               }
               else {
                  snapshots.put(name, OwnedValueCodec.UNREADABLE);
               }

               continue;
            }

            if(nv == null) {
               continue;
            }

            e.setValue(nv);

            String dropped = node instanceof OwnedValueCodec.DateNode d ? d.dropped()
               : node instanceof OwnedValueCodec.TreeRef ref ? ref.dropped() : null;

            if(dropped != null && warned.add(name)) {
               LOG.warn("The formula variable \"{}\" holds a Date {}; a batch of rows on " +
                        "another script context of the worksheet context pool rebuilds it as " +
                        "a plain Date from its time value only.", name, dropped);
            }
         }
      }
      finally {
         busy = false;
      }
   }

   // pull the objects of every home but current into trees; a home that is in use by another
   // thread (e.g. its claim is still held, amendment A1) or closed loses them, with a warning
   private void pullHomes(Context current) {
      for(Iterator<Map.Entry<Context, OwnedValueCodec.Home>> it = homes.entrySet().iterator();
          it.hasNext(); )
      {
         Map.Entry<Context, OwnedValueCodec.Home> e = it.next();

         if(e.getKey().equals(current)) {
            continue;
         }

         OwnedValueCodec.Home home = e.getValue();
         it.remove();
         boolean pulled;

         try {
            pulled = OwnedValueCodec.pull(home, this, this::park);
         }
         catch(RuntimeException ex) {
            LOG.debug("Failed to pull the objects of formula variables", ex);
            pulled = false;
         }

         if(!pulled) {
            OwnedValueCodec.leave(home, this);
            Object lost = home.isClosed() ? OwnedValueCodec.UNREADABLE
               : OwnedValueCodec.HOME_BUSY;

            for(Object o : valmap.entrySet()) {
               @SuppressWarnings("unchecked")
               Map.Entry<Object, Object> v = (Map.Entry<Object, Object>) o;

               if(v.getValue() instanceof Value gv && owned.contains(v.getKey()) &&
                  isOf(gv, e.getKey()))
               {
                  v.setValue(lost);
                  snapshots.remove(v.getKey());
               }
            }
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
   // the one table this scope belongs to, under that table's lock. An owned var holding an
   // object whose home handed off holds its tree root (OwnedValueCodec.TreeRef) or a Lost
   private HashMap valmap = new HashMap();
   private Set<String> owned = Set.of();
   private final Set<String> warned = new HashSet<>();
   // a lost var whose value hid references at its hand-off -> the vars that hand-off kept
   // (copies that no longer share what it reached); confined like valmap
   private final HashMap<String, List<String>> hiddenFrom = new HashMap<>();
   // the vars named in such a warning, each once
   private final Set<String> copied = new HashSet<>();
   // the batch-end snapshots (OwnedValueCodec nodes) of the owned vars that hold Dates, by
   // var name; confined like valmap (written in the batch's finally, before the lens lock is
   // released)
   private final HashMap<String, Object> snapshots = new HashMap<>();
   // set once an owned var held a script object: a table of primitives takes no snapshot
   private boolean hasObjects;
   // set once an owned var held a script object that is not a Date (B1 residual part 2):
   // its objects live on homes, one per context they are on; confined like valmap
   private boolean resident;
   // set when a batch end on a span that outlives the batch could not save a value within the
   // hand-off budget: the table's objects stay on the span's context, its batches share the
   // span (Testing #77123, cond-home round 3). Under the lens lock
   private boolean spanHome;
   private final HashMap<Context, OwnedValueCodec.Home> homes = new HashMap<>();
   // set while this scope moves its own objects, so a hand-off re-entered on its thread
   // refuses (returns false) instead of saving them under the move
   private boolean busy;
   private volatile Lock lensLock;
   private static final Logger LOG = LoggerFactory.getLogger(TableRowScope.class);
}
