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

import java.util.List;

/**
 * The decisions of the Task 0 host-boundary audit (docs audit-task0.md, spec §14.12) that
 * worksheet scripts running on pooled contexts are built on. The audit found no corpus
 * script that needs an allow-list exception: 454 worksheet scripts and 12 library bodies,
 * 0 affected uses.
 */
final class WsBoundaryPolicy {
   private WsBoundaryPolicy() {
   }

   /**
    * A1: ScriptValueConverter.toHost keeps main's shapes (Object[], Double, java.util.Date).
    * Only its plain-object fallthrough becomes a copy; the HostAccess mappings apply only to
    * direct calls on Java objects.
    */
   static final boolean TO_HOST_KEEPS_MAIN_SHAPES = true;

   /**
    * A2: a function or non-plain object is rejected only where the value is kept (scope and
    * array writes, HostAccess parameters), never when passed to a transient global function.
    */
   static final boolean REJECT_AT_STORE_ONLY = true;

   /**
    * A3: copies are LinkedHashMap/ArrayList subclasses that are also ProxyObject/ProxyArray.
    */
   static final boolean PROXY_BACKED_COPIES = true;

   /**
    * A4: an exec result that is a function or non-plain object is an error.
    */
   static final boolean REJECT_UNSTORABLE_EXEC_RESULT = true;

   /**
    * A5: a JS array passed to a Map-typed parameter is rejected, not index-mapped.
    */
   static final boolean REJECT_ARRAY_FOR_MAP_PARAMETER = true;

   /**
    * The release note for script.ws.contextPool (spec §3.2, §14.5, §14.12 A6,
    * §14.13, §14.14), one entry per line. The PR description's "Release note" is
    * String.join("\n", RELEASE_NOTE), word for word.
    */
   static final List<String> RELEASE_NOTE = List.of(
      "Worksheet script context pool (script.ws.contextPool, default false in this release)",
      "",
      "When enabled, worksheet scripts (expression columns, script conditions, calc fields,",
      "variable defaults, MV and R pre/post scripts) run on pooled script contexts, so a script",
      "never waits for another thread's script and the worksheet script-lock deadlocks",
      "(#76960, #76961, #76964, #76965, #76972) cannot occur. Behaviour changes when enabled:",
      "- Worksheet script globals (an assignment to an undeclared name, a function, or a",
      "  top-level var of a script that is not an expression column's formula) are",
      "  guaranteed only within one claimed span (one formula or filter batch, one summary or",
      "  crosstab aggregation, one condition build, one variable default); they no longer",
      "  carry over between batches, tables or queries. An expression column's top-level",
      "  var is kept for its whole table (see below); if it holds a script object (array,",
      "  object, Date, function), a batch that runs on another pooled context reads it as",
      "  undefined and logs one warning. A number, string or boolean is always kept.",
      "  A first formula batch is only about 10 rows and later ones double, so an accumulator",
      "  kept in an undeclared global (acc = ...) restarts at each batch, the first time after",
      "  about 10 rows; one kept in an object-valued var restarts at each batch that runs on",
      "  another pooled context. Keep accumulators in a top-level var holding a number, string",
      "  or boolean.",
      "- Scripts can run for rows nobody asked for. A first or random read of a table (a",
      "  page, a count) evaluates what pool off would; while a table is read row by row,",
      "  formula batches double from there, up to .maxBatchRows rows past the one requested,",
      "  so a reader that stops after N rows can have evaluated up to about 2N + 10 rows, and",
      "  at most about .maxBatchRows rows past N. Side effects (log calls, counters, host",
      "  writes) run for those rows, their script errors surface earlier, and they count",
      "  toward script.max.errors.",
      "- script.max.errors is counted per worksheet script environment (one per sandbox), not",
      "  per shared script engine.",
      "- A patch of a builtin prototype or static (Array.prototype.x = ..., JSON.stringify =",
      "  ...) stays only on the pooled context that ran it, so a later script may or may not",
      "  see it, depending on which context it runs on.",
      "- Known limitation: a JS array or object passed to a Java method parameter typed",
      "  Object, Map or List is a copy, so Java changes to it (out parameters,",
      "  java.util.Collections sort/reverse/swap/fill on a JS array) are not seen by the",
      "  script. Such a change made while the script runs is logged at WARN once per script",
      "  and counted (copyMutations in the pool summary). Return the changed value from the",
      "  Java method and assign it in the script instead. A Java object that keeps the copy",
      "  sees the value as passed, on any thread.",
      "- Object identity is lost across the Java boundary: a JS object stored in a Java",
      "  collection comes back as a copy.",
      "- Java toString() of a copied JS object now reads {a=1} instead of {a: 1}.",
      "- A JS function, Map, Set, RegExp, Promise or class instance cannot be stored in a",
      "  Java object or scope or in a viewsheet object (for example vsObj.f = function(){}),",
      "  passed to a Java method parameter typed Object, Map or List, or returned as a",
      "  worksheet expression result; this raises a clear script error. A function passed",
      "  to a callback parameter (java.util.Comparator and similar) still works.",
      "- A worksheet plain object or Date written into a viewsheet object arrives there as a",
      "  host copy (a Java map copy, a java.util.Date), and a worksheet array as a Java",
      "  Object[], not a JS array.",
      "- A JS callback (comparator and similar) that a Java object keeps and calls after the",
      "  worksheet script run that created it runs outside that run, on cleaned globals: a",
      "  global it reads can be quietly undefined (typeof g is 'undefined', no error), and it",
      "  fails when its context is busy or retired. Chart and viewsheet callbacks are not",
      "  affected.",
      "- javaDate.equals(jsDate) can now be true, because a JS Date passed to a Java method",
      "  arrives as java.util.Date.",
      "- A parameter added to a query's variable table mid-query (by VPM or a worksheet",
      "  script) is still seen as parameter.x by that query's formula, calc-field and",
      "  embedded-table scripts; a script that reads it without parameter. (for example a",
      "  post-condition) sees the sandbox's variable table instead, so the value can differ.",
      "- Binding and event code (AssetEventUtil, VSBindingService) still sets parameters on",
      "  the sandbox's shared scope for a moment, as before; a pooled query of the same",
      "  sandbox that runs at that moment can see them.",
      "- A formula table holds its lock for up to .maxBatchRows rows of script evaluation, so",
      "  a concurrent reader of already computed rows can wait that long.",
      "Enabling: set script.ws.contextPool=true in sree.properties (any case), or as a JVM",
      "option in lowercase only, -Dscript.ws.contextpool=true: SREE lowercases property names",
      "before it reads system properties, so -Dscript.ws.contextPool=true is ignored. The flag",
      "is read when a worksheet sandbox is created, so it applies to new sessions.",
      "Tuning: script.ws.contextPool.idleMillis (60000), .cleanThreshold (256),",
      ".warnSlotsPerSandbox (16), .warnSlotsPerNode (2000), .batchRows (256),",
      ".maxBatchRows (8192). .batchRows 0 turns script batching off; with any other value a",
      "first or random batch is what pool off computes, and batches double while a table is",
      "read sequentially, up to .maxBatchRows. The node's pool totals are logged at INFO",
      "every 5 minutes while the pool is in use (slots, cleansPerExec and more).",
      "Changes that apply with the pool on or off (§6.1-6.3):",
      "- The AssetQueryScope maps are concurrent, so worksheet scope keys (for...in,",
      "  Object.keys, autocomplete) no longer keep insertion order.",
      "- The condition-filter row map is a snapshot (#76972): a read past the last row of a",
      "  condition-filtered table raises IndexOutOfBoundsException instead of returning a",
      "  wrong row, and a crosstab condition's last-row span is no longer over-counted.",
      "- A top-level var of an expression column's formula belongs to its table (#77123): it",
      "  keeps its value from row to row for the whole table, on every script path (formulas",
      "  using this or several statements reset it on every row before) and however the table",
      "  is read, and starts over when the table is computed again. Other tables and scripts",
      "  no longer see it. Two patterns change: a var without initializer that is assigned",
      "  only under a condition (var r; if(c) { r = x; } r), or only inside try/catch, now",
      "  keeps the previous row's value on the rows where it is not assigned. Use let for a",
      "  variable that must start empty on every row. With the pool on, only a number,",
      "  string or boolean in it is kept across batches of rows; an array, object, Date or",
      "  function is kept only within one batch (see above). A read retried after a",
      "  lock-stall error (stall watchdog in FAIL mode) runs that row's formulas again, so",
      "  an accumulator can count the row twice.",
      "- Script timeouts use per-exec tokens (pool on or off, when script.execution.timeout",
      "  is set): after a real timeout an exec can take up to 3 s longer to return, and it",
      "  holds its script engine lock meanwhile, so other scripts waiting on that engine",
      "  wait with it. Its interrupt no longer reaches a later script, except",
      "  in the rare case where the interrupt itself cannot finish within its bound: then",
      "  the Context is flagged unknown, and with the pool on the slot is closed instead of",
      "  reused."
   );
}
