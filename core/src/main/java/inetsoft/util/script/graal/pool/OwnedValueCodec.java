/*
 * This file is part of StyleBI.
 * Copyright (C) 2026  InetSoft Technology
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
package inetsoft.util.script.graal.pool;

import inetsoft.util.script.ScriptSpan;
import inetsoft.util.script.graal.ScriptTimeoutGuard;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.PolyglotException;
import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.proxy.ProxyArray;

import java.time.Duration;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * The context-free form of a script object that a formula table's var holds (Testing #77123,
 * B1 residual). It is taken at the end of a pooled batch, on the slot the batch claimed, and
 * rebuilt in the context of a later batch that runs on another slot. Only a Date is kept: its
 * time value (NaN for an Invalid Date). Any other script object is {@link Lost}, and reads as
 * undefined on another context.
 *
 * <p><b>No script code runs in a snapshot</b>, so it may run after the batch's timeout guard
 * fired. The rule the code below keeps: a value is first tested with {@code isDate() &&
 * isInstant()} (the Date internal-slot test, false for every Proxy, so no trap can run), or,
 * for a value whose meta object is the intrinsic Date (an Invalid Date, or an object that only
 * inherits {@code Date.prototype}), with the intrinsic {@code Date.prototype.getTime} captured
 * when the context was created (a builtin that reads the internal slot and throws a TypeError
 * for any other value); only a Date is then read with {@code getMemberKeys()}, which on a
 * Proxy runs its {@code ownKeys} trap. Every other value is only classified by interop messages that never
 * dispatch to script code ({@code getMetaObject}, {@code getMetaSimpleName},
 * {@code canExecute}, {@code hasArrayElements}, {@code equals}/{@code hashCode} identity).
 * A future member or element read of another kind must first rule out a Proxy
 * ({@code getMetaObject()} is the constant {@code Proxy}); {@code hasMembers()} is no guard.
 *
 * <p>A rebuild calls the Date constructor of the executing context captured when that
 * context was created, so a script that replaced the global {@code Date} does not change it.
 */
public final class OwnedValueCodec {
   private OwnedValueCodec(Slot slot) {
      this.slot = slot;
      this.context = slot.engine().context();
      this.dateConstructor = slot.engine().dateConstructor();
      this.getTime = slot.engine().dateGetTime();
   }

   /**
    * @return the codec of the slot {@code span} claimed, or {@code null} if the span is not a
    *         pooled claim (pool off) or no slot was checked out under it (no script ran).
    */
   public static OwnedValueCodec forSpan(ScriptSpan span) {
      if(span instanceof SlotClaim claim) {
         return of(claim.peekSlot());
      }

      return null;
   }

   /**
    * @return {@code true} if {@code span} is a pooled claim that stays open on its thread after
    *         {@code span} closes: it re-entered an outer span of the thread (a condition
    *         filter's population, another table's batch) or a query build holds it (Testing
    *         #77123, cond-home). Its slot is then not given back at the end of the batch that
    *         opened {@code span}. A span at a query build's top level does not outlive its
    *         batch: the build gives the home back at the batch end (round 3).
    */
   public static boolean outlives(ScriptSpan span) {
      return span instanceof SlotClaim claim && claim.depth() > 1 && !claim.buildTop();
   }

   /**
    * @return the codec of the pooled context executing on this thread, or {@code null}.
    */
   public static OwnedValueCodec current() {
      return of(WsExecContext.currentSlot());
   }

   static OwnedValueCodec of(Slot slot) {
      return slot != null && !slot.isClosed() && slot.engine().dateConstructor() != null &&
         slot.engine().dateGetTime() != null ? new OwnedValueCodec(slot) : null;
   }

   /**
    * @return the context of the slot {@code span} claimed, or {@code null} if the span is not
    *         a pooled claim or no slot was checked out under it. Values of this context that
    *         {@link #forSpan} cannot save (a closed slot) are to be lost, never left with an
    *         older snapshot.
    */
   public static Context claimedContext(ScriptSpan span) {
      Slot slot = span instanceof SlotClaim claim ? claim.peekSlot() : null;
      return slot == null ? null : slot.engine().context();
   }

   public Context context() {
      return context;
   }

   /**
    * The snapshot of {@code v}, a value of {@link #context()}; aliases share one node through
    * {@code ids}. Never throws a RuntimeException: a value that cannot be read is
    * {@link Lost}. A caller's cancel is kept (see {@link #snapshotTree}).
    */
   public Object snapshot(Value v, Map<Value, Object> ids) {
      Object node;
      boolean cancelled = Thread.interrupted();

      try {
         Supplier<? extends Throwable> fault = readFault;

         if(fault != null) {
            Throwable ex = fault.get();

            if(ex instanceof Error error) {
               throw error;
            }

            throw (RuntimeException) ex;
         }

         // interop identity (no script code), inside the catch like every read
         Object seen = ids.get(v);

         if(seen != null) {
            return seen;
         }

         // the ordering rule (class comment): isDate() && isInstant() before any member read
         if(v.isDate() && v.isInstant()) {
            node = dateNode(v, v.asInstant().toEpochMilli());
         }
         else {
            // an Invalid Date is no interop date. Its meta object is the intrinsic Date, but
            // so is that of any object that only inherits Date.prototype
            // (Object.create(Date.prototype), an ES5 "subclass"): only the Date internal
            // slot, read by the intrinsic getTime, tells them apart
            boolean dateMeta = dateConstructor.equals(v.getMetaObject());
            Double time = dateMeta ? timeValue(v) : null;
            node = time != null ? dateNode(v, time) : dateMeta ? NOT_A_DATE : new Lost(kind(v));
         }

         ids.put(v, node);
      }
      catch(RuntimeException ex) {
         ScriptTimeoutGuard.keepCancel(ex, null);
         node = UNREADABLE;
      }
      finally {
         if(cancelled) {
            Thread.currentThread().interrupt();
         }
      }

      return node;
   }

   /**
    * Rebuild {@code node} in {@link #context()}, which executes on this thread; aliases
    * share one value through {@code built}.
    *
    * @return the value, or {@code null} if the node is not rebuilt (a {@link Lost} value).
    *         A caller's cancel is kept (see {@link #snapshotTree}).
    */
   public Value rebuild(Object node, IdentityHashMap<Object, Value> built) {
      boolean cancelled = Thread.interrupted();

      try {
         return rebuild0(node, built);
      }
      catch(RuntimeException ex) {
         ScriptTimeoutGuard.keepCancel(ex, null);
         throw ex;
      }
      finally {
         if(cancelled) {
            Thread.currentThread().interrupt();
         }
      }
   }

   private Value rebuild0(Object node, IdentityHashMap<Object, Value> built) {
      Value seen = built.get(node);

      if(seen != null) {
         return seen;
      }

      if(node instanceof TreeRef ref) {
         Value roots;

         if(built.containsKey(ref.tree)) {
            roots = built.get(ref.tree);

            if(roots == null) {
               throw new IllegalStateException("The tree of a formula variable failed to build");
            }
         }
         else {
            try {
               roots = buildTree(ref.tree);
            }
            catch(RuntimeException ex) {
               built.put(ref.tree, null); // its other roots fail at once
               throw ex;
            }

            built.put(ref.tree, roots);
         }

         Value v = roots.getArrayElement(ref.index);
         built.put(node, v);
         return v;
      }

      if(!(node instanceof DateNode d)) {
         return null;
      }

      Value v = dateConstructor.newInstance(d.time);
      built.put(node, v);
      return v;
   }

   /**
    * The node of {@code v}, a value with the Date internal slot (so no Proxy: reading its
    * member keys runs no trap, and own enumerable string keys run no getter).
    */
   private DateNode dateNode(Value v, double time) {
      Value meta = v.getMetaObject();
      String dropped = null;

      if(meta == null || !meta.equals(dateConstructor)) {
         String cls = meta == null ? null : meta.getMetaSimpleName();
         dropped = cls == null || cls.isEmpty() ? "of a subclass" : "of a subclass (" + cls + ")";
      }

      Set<String> keys = v.getMemberKeys();

      if(!keys.isEmpty()) {
         String props = "with the properties " + list(keys);
         dropped = dropped == null ? props : dropped + " and " + props;
      }

      return new DateNode(time, dropped);
   }

   /**
    * @return the time value of {@code v} (NaN for an Invalid Date) if it has the Date
    *         internal slot, else {@code null}. The intrinsic getTime reads only that slot and
    *         runs no script code. An interrupt or cancel propagates (the value is unreadable).
    */
   private Double timeValue(Value v) {
      try {
         return getTime.execute(v).asDouble();
      }
      catch(PolyglotException ex) {
         if(ex.isGuestException() && !ex.isCancelled() && !ex.isInterrupted() &&
            !ex.isResourceExhausted())
         {
            return null; // the TypeError of a value that is not a Date
         }

         throw ex;
      }
   }

   // at most MAX_KEYS names, "a, b, c and 17 more"
   private static String list(Set<String> keys) {
      if(keys.size() <= MAX_KEYS) {
         return String.join(", ", keys);
      }

      List<String> first = new ArrayList<>(keys).subList(0, MAX_KEYS);
      return String.join(", ", first) + " and " + (keys.size() - MAX_KEYS) + " more";
   }

   // what a value that is not kept is, for the warning: "holds <kind> created on another..."
   private static String kind(Value v) {
      try {
         if(v.canExecute()) {
            return "a function";
         }

         if(v.hasArrayElements()) {
            return "an array";
         }

         Value meta = v.getMetaObject();
         String name = meta == null ? null : meta.getMetaSimpleName();

         if(name == null || name.isEmpty()) {
            return "a script object";
         }

         if("Object".equals(name)) {
            return "an object";
         }

         return ("AEIOaeio".indexOf(name.charAt(0)) >= 0 ? "an " : "a ") + name + " object";
      }
      catch(RuntimeException ex) {
         return "a script object";
      }
   }

   // ---- residency (Testing #77123, B1 residual part 2) -------------------------------------
   //
   // A lens whose owned vars hold arrays or objects (anything but Dates only) keeps them as
   // live values on one pooled context, its home, which is reserved for it while the lens is
   // idle. A structured snapshot (owned-cloner.js, evaluated when the context was created) is
   // taken only at a hand-off: another context's batch pulls the values from the idle home,
   // or the pool closes, expires or takes over the home.

   /** The pooled context a lens's object vars live on, and its pool. */
   public static final class Home {
      Home(SlotPool pool, Slot slot) {
         this.pool = pool;
         this.slot = slot;
      }

      /** @return whether the context of this home was closed. */
      public boolean isClosed() {
         return slot.isClosed();
      }

      /** @return the Context of this home. */
      public Context context() {
         return slot.engine().context();
      }

      final SlotPool pool;
      final Slot slot;
   }

   /**
    * @return the home of the slot {@code span} claimed, or {@code null} if the span is not a
    *         pooled claim, no slot was checked out under it, or the slot is closed.
    */
   public static Home homeOf(ScriptSpan span) {
      if(span instanceof SlotClaim claim) {
         Slot slot = claim.peekSlot();

         if(slot != null && !slot.isClosed()) {
            return new Home(claim.pool(), slot);
         }
      }

      return null;
   }

   /**
    * Record {@code tenant} as living on {@code home}, whose slot the calling thread holds (a
    * batch end, at any claim depth, amendment A1): once the slot is idle, other claims skip it
    * while it is an exclusive home of the pool, or take it over after a hand-off (A4).
    */
   public static void enroll(Home home, SlotTenant tenant) {
      home.pool.enroll(home.slot, tenant);
   }

   /** Stop reserving {@code home} for {@code tenant}. Never waits. */
   public static void leave(Home home, SlotTenant tenant) {
      home.pool.leave(home.slot, tenant);
   }

   /**
    * Make this thread's next checkout prefer a home of {@code tenant}; {@code null} for none.
    *
    * @return the previous preference, for {@link #restoreHomeHint}.
    */
   public static Object preferHomeOf(SlotTenant tenant) {
      return SlotClaim.setHomeHint(tenant);
   }

   public static void restoreHomeHint(Object previous) {
      SlotClaim.restoreHomeHint(previous);
   }

   /**
    * Pull the values of {@code tenant} from its idle {@code home}: take the slot without
    * waiting, run {@code save} with its codec (a tree snapshot), stop reserving it for the
    * tenant, and give it back.
    *
    * A pool thread that holds the home for a moment without the tenant's lock (a probe: a
    * take-over, the expiry, a retire) is waited out, for at most {@link #PULL_SPIN_NANOS} and
    * never by an interrupted thread (Testing #77123, finding G1).
    *
    * @return {@code false} if the slot is held or closed: nothing was saved.
    */
   public static boolean pull(Home home, SlotTenant tenant, Consumer<OwnedValueCodec> save) {
      Slot slot = home.slot;

      if(!takeForPull(slot)) {
         return false;
      }

      try {
         OwnedValueCodec codec = of(slot);

         if(codec == null) {
            return false;
         }

         slot.metrics().pulled();
         save.accept(codec);
         return true;
      }
      finally {
         try {
            home.pool.leave(slot, tenant);
         }
         finally {
            home.pool.returnPulled(slot);
         }
      }
   }

   /**
    * Take {@code slot} for a pull without waiting for a claim: only a probe of a pool thread
    * (which never waits for anything this thread holds: it only tryLocks, and takes the
    * pool's leaf homes lock) is waited out, spinning, for at most {@link #PULL_SPIN_NANOS}.
    * An interrupted thread stops at once and keeps its interrupt flag.
    *
    * @return whether the calling thread holds the slot now.
    */
   private static boolean takeForPull(Slot slot) {
      if(slot.tryAcquire()) {
         return true;
      }

      if(slot.isHeldByCurrentThread()) {
         return false;
      }

      Thread thread = Thread.currentThread();
      long start = System.nanoTime();

      while(slot.isProbed() && !slot.isClosed() && !thread.isInterrupted()) {
         Consumer<Slot> hook = pullSpinHook;

         if(hook != null) {
            hook.accept(slot);
         }

         Thread.onSpinWait();

         if(slot.tryAcquire()) {
            return true;
         }

         if(System.nanoTime() - start >= PULL_SPIN_NANOS) {
            return false;
         }
      }

      // a prober gives the slot back before its probe ends: once it ended, one more try
      return !thread.isInterrupted() && slot.tryAcquire();
   }

   /** The longest a pull waits out a probe of its home (finding G1), about 50 ms. */
   static final long PULL_SPIN_NANOS = java.util.concurrent.TimeUnit.MILLISECONDS.toNanos(50);

   /**
    * Snapshot {@code roots}, values of {@link #context()}, into one context-free tree with the
    * context's pristine cloner, so aliases and cycles among them are kept. Runs no script code.
    * Bounded by the pool's hand-off budget (an entry cap and a time bound, not the script
    * timeout, amendment A6), and by an interrupt past it. Never throws a RuntimeException.
    * <p>
    * A caller's cancel (an interrupt of the thread) is kept (Testing #77123): a flag set
    * before the snapshot is cleared while it runs, so Graal does not stop the snapshot on it,
    * and set again after; a cancel that lands during the snapshot loses every root (it is
    * {@link #UNREADABLE}, never kept from an older batch) and is re-asserted, since Graal
    * cleared it. Only an interrupt of the hand-off's own time bound, or of a timeout guard
    * still open on the thread, is a time-out.
    *
    * @return one node per root: a {@link TreeRef}, or a {@link Lost} naming what that root
    *         holds that is not kept (a function, a class instance, a Proxy, an accessor...);
    *         a refusal loses the root it was found in and every root that shares an object
    *         with that root, each with its own kind (A3), and no other root. Past the time
    *         bound every root is lost, and so it is past the marking budget
    *         ({@link #MARK_FACTOR} times the entry cap) while the lost roots are checked for
    *         shared objects.
    */
   public Object[] snapshotTree(List<Value> roots) {
      Object[] nodes = new Object[roots.size()];

      if(nodes.length == 0) {
         return nodes;
      }

      PoolConfig config = slot.config();
      Value snap = slot.engine().clonerSnap();
      List<Object> kept = new ArrayList<>();
      Map<Integer, String> failed = new HashMap<>();
      Map<Integer, String> drops = new HashMap<>();
      Set<Integer> hides = new HashSet<>();
      long start = System.nanoTime();
      ScriptTimeoutGuard.Guard budget = null;
      boolean cancelled = Thread.interrupted();

      try {
         Supplier<? extends Throwable> fault = readFault;

         if(fault != null) {
            Throwable ex = fault.get();

            if(ex instanceof Error error) {
               throw error;
            }

            throw (RuntimeException) ex;
         }

         if(snap == null) {
            Arrays.fill(nodes, UNREADABLE);
            return nodes;
         }

         String text;
         KEEP_OUT.set(kept);
         FAILS.set(failed);
         DROPS.set(drops);
         HIDES.set(hides);

         // a backstop past the cloner's own time checks
         try(ScriptTimeoutGuard.Guard guard =
                slot.engine().guard(Duration.ofMillis(config.handOffMillis() + 1000)))
         {
            budget = guard;
            text = snap.execute(ProxyArray.fromList(new ArrayList<Object>(roots)),
                                config.handOffEntries(), config.handOffMillis(),
                                markBudget(config.handOffEntries())).asString();
         }

         Tree tree = new Tree(text, kept);

         for(int i = 0; i < nodes.length; i++) {
            String kind = failed.get(i);
            nodes[i] = kind != null ? new Lost(kind, isBudgetKind(kind), hides.contains(i))
               : new TreeRef(tree, i, drops.get(i));
         }
      }
      catch(PolyglotException ex) {
         // a caller's cancel, not this hand-off's time bound: kept, the roots unreadable
         boolean cancel = ScriptTimeoutGuard.keepCancel(ex, budget);
         Arrays.fill(nodes, !cancel && (ex.isInterrupted() || ex.isCancelled())
            ? new Lost("a value that took longer than " + config.handOffMillis() +
                       " ms to save", true) : UNREADABLE);
      }
      catch(RuntimeException ex) {
         ScriptTimeoutGuard.keepCancel(ex, budget);
         Arrays.fill(nodes, UNREADABLE);
      }
      finally {
         KEEP_OUT.remove();
         FAILS.remove();
         DROPS.remove();
         HIDES.remove();
         slot.metrics().handedOff(System.nanoTime() - start);

         if(cancelled) {
            Thread.currentThread().interrupt();
         }
      }

      return nodes;
   }

   /**
    * @return the marking budget of a hand-off whose entry cap is {@code entries}.
    */
   public static int markBudget(int entries) {
      return (int) Math.min(Integer.MAX_VALUE, (long) MARK_FACTOR * entries);
   }

   // the roots of a tree, built once in this (executing) context; bounded by the cloner's time
   // checks and by the snapshot's entry cap
   private Value buildTree(Tree tree) {
      Value build = slot.engine().clonerBuild();

      if(build == null) {
         throw new IllegalStateException("No cloner on this worksheet script context");
      }

      KEEP_IN.set(tree.kept);

      try {
         return build.execute(tree.text, slot.config().handOffMillis());
      }
      finally {
         KEEP_IN.remove();
         slot.metrics().rebuilt();
      }
   }

   /** Count a read of an owned var's object that was made on another context. */
   public void crossRead() {
      slot.metrics().crossRead();
   }

   /** A tree snapshot: the cloner's text and the host objects it keeps by reference. */
   public static final class Tree {
      Tree(String text, List<Object> kept) {
         this.text = text;
         this.kept = kept;
      }

      private final String text;
      private final List<Object> kept;
   }

   /** One root of a tree snapshot. */
   public static final class TreeRef {
      TreeRef(Tree tree, int index, String dropped) {
         this.tree = tree;
         this.index = index;
         this.dropped = dropped;
      }

      /**
       * @return what the rebuild of a Date in this root drops ("with the properties a, b",
       *         "of a subclass"): it is rebuilt as a plain Date from its time value; or
       *         {@code null}.
       */
      public String dropped() {
         return dropped;
      }

      private final String dropped;

      private final Tree tree;
      private final int index;
   }

   // the cloner's host callbacks, set around one snap / build on this thread
   static final ThreadLocal<List<Object>> KEEP_OUT = new ThreadLocal<>();
   static final ThreadLocal<List<Object>> KEEP_IN = new ThreadLocal<>();
   static final ThreadLocal<Map<Integer, String>> FAILS = new ThreadLocal<>();
   static final ThreadLocal<Map<Integer, String>> DROPS = new ThreadLocal<>();
   static final ThreadLocal<Set<Integer>> HIDES = new ThreadLocal<>();

   /**
    * The marking budget of a hand-off (the objects walked to check the lost roots for
    * shared objects), in entry caps: past it every root is lost.
    */
   public static final int MARK_FACTOR = 4;

   /** A Date: its time value (NaN for an Invalid Date) and what a rebuild drops, if any. */
   public static final class DateNode {
      DateNode(double time, String dropped) {
         this.time = time;
         this.dropped = dropped;
      }

      /**
       * @return what a rebuild drops ("with the properties a, b", "of a subclass (Foo)"),
       *         or {@code null}.
       */
      public String dropped() {
         return dropped;
      }

      private final double time;
      private final String dropped;
   }

   /** A value that is not kept across contexts, e.g. an array. */
   public static final class Lost {
      Lost(String kind) {
         this(kind, false);
      }

      Lost(String kind, boolean budget) {
         this(kind, budget, false);
      }

      Lost(String kind, boolean budget, boolean hides) {
         this.kind = kind;
         this.budget = budget;
         this.hides = hides;
      }

      /** @return what the value was, with its article ("an array", "a function"). */
      public String kind() {
         return kind;
      }

      /**
       * @return {@code true} if the value was lost to the hand-off budget (the entry cap, the
       *         marking budget or the time bound), not for what it is: it stays usable on its
       *         own context (Testing #77123, cond-home round 3).
       */
      public boolean overBudget() {
         return budget;
      }

      /**
       * @return {@code true} if the value held, at its hand-off, something whose references
       *         the cloner cannot list (a function's closure, a getter, a WeakMap, a class
       *         instance, a Proxy): a var kept by the same hand-off may be a copy of an object
       *         it reached there, which no longer shares anything with it (Testing #77123, B1
       *         residual).
       */
      public boolean hidesReferences() {
         return hides;
      }

      private final String kind;
      private final boolean budget;
      private final boolean hides;
   }

   /**
    * @return {@code true} if {@code node}, a snapshot node, is a value lost to the hand-off
    *         budget ({@link Lost#overBudget}).
    */
   public static boolean overBudget(Object node) {
      return node instanceof Lost lost && lost.overBudget();
   }

   // the kinds owned-cloner.js reports past its budget (stopHard: TIME, the entry cap, MARKS)
   private static boolean isBudgetKind(String kind) {
      return kind.startsWith("a value that took longer than ") ||
         kind.startsWith("a value with more than ") ||
         kind.startsWith("a value that could not be checked for an object shared with a ");
   }

   /** The node of an object that inherits Date.prototype without being a Date. */
   public static final Lost NOT_A_DATE =
      new Lost("an object that inherits Date.prototype but is no Date");

   /** The objects of a home another thread holds: lost for the batch that pulled them. */
   public static final Lost HOME_BUSY =
      new Lost("an object on a script context in use by another thread");

   /** The node of a value whose snapshot failed: it is lost, never kept from an older batch. */
   public static final Lost UNREADABLE = new Lost("a value that could not be read");

   // tests only: run by a pull at each retry while a pool thread probes the home (Testing
   // #77123, finding G1), on the pulling thread
   static volatile Consumer<Slot> pullSpinHook;

   // tests only (PoolTestSupport.failOwnedValueReads): every snapshot read throws this
   static volatile Supplier<? extends Throwable> readFault;

   private final Slot slot;
   private final Context context;
   private final Value dateConstructor;
   private final Value getTime;
   private static final int MAX_KEYS = 8;
}
