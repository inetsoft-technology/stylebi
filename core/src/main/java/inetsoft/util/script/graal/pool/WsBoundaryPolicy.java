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
    * The release-note lines for script.ws.contextPool (spec §3.2, §14.5, §14.12 A6).
    */
   static final List<String> RELEASE_NOTE = List.of(
      "Worksheet script globals (a top-level var or an assignment to an undeclared name) are " +
         "guaranteed only within one claimed span (one formula or filter batch, one condition " +
         "build, one variable default); they no longer carry over between batches, tables or " +
         "queries, and a var accumulator can depend on how the table is read.",
      "A JS array or object passed to a Java method is a copy: host mutation of an out " +
         "parameter is no longer seen by the script, and java.util.Collections " +
         "sort/reverse/shuffle on a JS array no longer reorder it.",
      "Object identity is lost across the Java boundary: a JS object stored in a Java " +
         "collection comes back as a copy.",
      "Java toString() of a copied JS object now reads {a=1} instead of {a: 1}.",
      "A JS function, Map, Set, RegExp, Promise or class instance cannot be stored in a " +
         "Java object or scope, passed to a Java method parameter, or returned as a " +
         "worksheet expression result; this raises a clear script error.",
      "A JS callback (comparator and similar) stored in a Java object is valid only during " +
         "the worksheet script run that created it; chart and viewsheet callbacks are not " +
         "affected.",
      "javaDate.equals(jsDate) can now be true, because a JS Date passed to a Java method " +
         "arrives as java.util.Date."
   );
}
