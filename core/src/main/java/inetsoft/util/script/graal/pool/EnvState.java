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

import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

/**
 * The variables of a pooled worksheet script env (bug #76960, spec §4.1): the single source of
 * truth for put/get/remove, as a change log compacted to one entry per name (the latest value
 * or a tombstone) with a monotonic version. Lock-free: an immutable snapshot published by CAS,
 * so a context replaying it always sees the log and its version together. Writes never touch a
 * context.
 */
final class EnvState {
   /**
    * The latest change of one name.
    */
   record Change(String name, Object value, boolean removed, long version) {
   }

   /**
    * An immutable state of the log.
    */
   record Snapshot(long version, Map<String, Change> changes) {
      /**
       * @return the live variables, in the order they were last set.
       */
      Map<String, Object> vars() {
         Map<String, Object> vars = new LinkedHashMap<>();
         changes.values().stream()
            .filter(c -> !c.removed())
            .sorted(Comparator.comparingLong(Change::version))
            .forEach(c -> vars.put(c.name(), c.value()));
         return vars;
      }

      /**
       * @return the changes newer than {@code version}, oldest first.
       */
      List<Change> after(long version) {
         List<Change> list = new ArrayList<>();

         for(Change change : changes.values()) {
            if(change.version() > version) {
               list.add(change);
            }
         }

         list.sort(Comparator.comparingLong(Change::version));
         return list;
      }
   }

   /**
    * Set a variable. Null names and values are rejected, as the Hashtable of the plain env did.
    *
    * @return the new version.
    */
   long put(String name, Object value) {
      Objects.requireNonNull(name);
      Objects.requireNonNull(value);
      return apply(name, value, false);
   }

   /**
    * Remove a variable, leaving a tombstone so contexts that already have it drop it.
    *
    * @return the new version.
    */
   long remove(String name) {
      Objects.requireNonNull(name);
      return apply(name, null, true);
   }

   Object get(String name) {
      Change change = ref.get().changes().get(name);
      return change == null || change.removed() ? null : change.value();
   }

   Snapshot snapshot() {
      return ref.get();
   }

   private long apply(String name, Object value, boolean removed) {
      while(true) {
         Snapshot current = ref.get();
         long version = current.version() + 1;
         Map<String, Change> changes = new HashMap<>(current.changes());
         changes.put(name, new Change(name, value, removed, version));

         if(ref.compareAndSet(current, new Snapshot(version, Collections.unmodifiableMap(changes)))) {
            return version;
         }
      }
   }

   private final AtomicReference<Snapshot> ref =
      new AtomicReference<>(new Snapshot(0L, Map.of()));
}
