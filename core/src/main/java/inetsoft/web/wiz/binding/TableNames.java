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
package inetsoft.web.wiz.binding;

import inetsoft.web.binding.model.BindingModel;
import inetsoft.web.wiz.binding.model.BindableTable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Function;

/**
 * Resolves a caller-typed worksheet table name against the tables an assembly can bind.
 *
 * <p>Worksheet table names are case-sensitive identifiers, so a worksheet may hold two tables that
 * differ only by case ({@code Customers} and {@code CUSTOMERS}). A first-match
 * {@code equalsIgnoreCase} loop silently bound the request to whichever sorted first (bug #79192),
 * so the policy lives here once, for every lookup:
 * <ol>
 *   <li>an exact (case-sensitive) match always wins, wherever it sits in the listing;</li>
 *   <li>otherwise a single case-insensitive match is accepted and returned in its real spelling,
 *       which keeps a mis-cased request working;</li>
 *   <li>otherwise several case-insensitive matches are refused, naming every candidate, because
 *       picking one would bind the wrong table without a word;</li>
 *   <li>otherwise there is no match, and the caller reports its own "Available: ..." error.</li>
 * </ol>
 *
 * <p>The result is a real, listed name. Every "is this the table the assembly is already bound to"
 * comparison must resolve first and then compare exactly: a case-insensitive comparison treats the
 * two case-variant tables as one.
 */
final class TableNames {
   private TableNames() {
   }

   /**
    * @return the listed name {@code requested} selects, or {@code null} when none matches
    * @throws IllegalArgumentException when several names match only by case and none exactly
    */
   static String resolve(Collection<String> names, String requested) {
      return resolve(names, Function.identity(), requested);
   }

   /** As {@link #resolve(Collection, String)}, over a binding model's source tables. */
   static String resolveSourceTable(List<BindingModel.SourceTable> tables, String requested) {
      BindingModel.SourceTable table =
         resolve(tables == null ? List.<BindingModel.SourceTable>of() : tables,
                 BindingModel.SourceTable::getName, requested);
      return table == null ? null : table.getName();
   }

   /** As {@link #resolve(Collection, String)}, over listed bindable tables. */
   static BindableTable resolveTable(Collection<BindableTable> tables, String requested) {
      return resolve(tables, BindableTable::name, requested);
   }

   /**
    * The item whose name {@code requested} selects, by the policy in the class comment.
    *
    * @return the item, or {@code null} when none matches
    * @throws IllegalArgumentException when several names match only by case and none exactly
    */
   static <T> T resolve(Collection<T> items, Function<T, String> name, String requested) {
      if(items == null || requested == null) {
         return null;
      }

      T single = null;
      List<String> matches = new ArrayList<>();

      for(T item : items) {
         String candidate = item == null ? null : name.apply(item);

         if(candidate == null) {
            continue;
         }

         if(candidate.equals(requested)) {
            return item;
         }

         if(candidate.equalsIgnoreCase(requested)) {
            if(!matches.contains(candidate)) {
               matches.add(candidate);
            }

            if(single == null) {
               single = item;
            }
         }
      }

      if(matches.size() > 1) {
         throw new IllegalArgumentException(
            "'" + requested + "' is ambiguous: it matches the tables " + String.join(", ", matches) +
            " only by case. Use the exact spelling of one of them.");
      }

      return single;
   }

   /**
    * The listed name that a stored source string denotes, for a "which table is current" decision.
    * The stored spelling normally is a listed name, so that wins; a stored spelling that drifted in
    * case still identifies its table when exactly one table matches it ignoring case, and
    * identifies none when several do (nothing is guessed).
    *
    * @return the listed name, or {@code null} when the source names no single listed table
    */
   static String resolveStored(Collection<String> names, String source) {
      try {
         return resolve(names, source);
      }
      catch(IllegalArgumentException e) {
         return null;
      }
   }
}
