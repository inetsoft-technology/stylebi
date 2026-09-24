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
      "- Worksheet script globals (a top-level var or an assignment to an undeclared name) are",
      "  guaranteed only within one claimed span (one formula or filter batch, one summary or",
      "  crosstab aggregation, one condition build, one variable default); they no longer carry",
      "  over between batches, tables or queries, and a var accumulator can depend on how the",
      "  table is read.",
      "- Scripts can run for rows nobody asked for: a formula or filter batch evaluates at",
      "  least .batchRows (up to .maxBatchRows) rows past the one requested. Side effects (log",
      "  calls, counters, host writes) run for those rows, their script errors surface earlier,",
      "  and they count toward script.max.errors.",
      "- script.max.errors is counted per worksheet script environment (one per sandbox), not",
      "  per shared script engine.",
      "- A patch of a builtin prototype or static (Array.prototype.x = ..., JSON.stringify =",
      "  ...) stays only on the pooled context that ran it, so a later script may or may not",
      "  see it, depending on which context it runs on.",
      "- A JS array or object passed to a Java method is a copy: host mutation of an out",
      "  parameter is no longer seen by the script, and java.util.Collections",
      "  sort/reverse/shuffle on a JS array no longer reorder it.",
      "- Object identity is lost across the Java boundary: a JS object stored in a Java",
      "  collection comes back as a copy.",
      "- Java toString() of a copied JS object now reads {a=1} instead of {a: 1}.",
      "- A JS function, Map, Set, RegExp, Promise or class instance cannot be stored in a",
      "  Java object or scope or in a viewsheet object (for example vsObj.f = function(){}),",
      "  passed to a Java method parameter, or returned as a worksheet expression result;",
      "  this raises a clear script error.",
      "- A worksheet plain object or Date written into a viewsheet object arrives there as a",
      "  host copy (a Java map copy, a java.util.Date), and a worksheet array as a Java",
      "  Object[], not a JS array.",
      "- A JS callback (comparator and similar) stored in a Java object is valid only during",
      "  the worksheet script run that created it; chart and viewsheet callbacks are not",
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
      ".maxBatchRows (8192). Script batches start at .batchRows rows and double while a table",
      "is read sequentially, up to .maxBatchRows. The node's pool totals are logged at INFO",
      "every 5 minutes while the pool is in use (slots, cleansPerExec and more).",
      "Changes that apply with the pool on or off (§6.1-6.3):",
      "- The AssetQueryScope maps are concurrent, so worksheet scope keys (for...in,",
      "  Object.keys, autocomplete) no longer keep insertion order.",
      "- The condition-filter row map is a snapshot (#76972): a read past the last row of a",
      "  condition-filtered table raises IndexOutOfBoundsException instead of returning a",
      "  wrong row, and a crosstab condition's last-row span is no longer over-counted.",
      "- Script timeouts use per-exec tokens (pool on or off, when script.execution.timeout",
      "  is set): after a real timeout an exec can take up to 3 s longer to return, and it",
      "  holds its script engine lock meanwhile, so other scripts waiting on that engine",
      "  wait with it. Its interrupt no longer reaches a later script, except",
      "  in the rare case where the interrupt itself cannot finish within its bound: then",
      "  the Context is flagged unknown, and with the pool on the slot is closed instead of",
      "  reused."
   );
}
