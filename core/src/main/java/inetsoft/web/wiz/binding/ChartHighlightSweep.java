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

import inetsoft.report.filter.Highlight;
import inetsoft.report.filter.HighlightGroup;
import inetsoft.uql.ConditionList;
import inetsoft.uql.erm.DataRef;
import inetsoft.uql.viewsheet.VSDataRef;
import inetsoft.uql.viewsheet.graph.AestheticRef;
import inetsoft.uql.viewsheet.graph.ChartInfo;
import inetsoft.uql.viewsheet.graph.ChartRef;
import inetsoft.uql.viewsheet.graph.HighlightRef;
import inetsoft.uql.viewsheet.graph.VSChartInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Says what a shelf write did to the chart's highlights (bug #81526).
 *
 * <p>A highlight lives on the field it was set on, so rewriting a shelf takes the highlights of
 * every field that leaves with it, and {@link ChartHyperlinkCarry#reapply} reports only the case
 * where the same column stays on the shelf under a different formula. A highlight whose field
 * left, and one whose condition names a field that left the chart, were both silent. This sweep
 * compares the chart's highlights before and after the write and reports both. It never changes
 * a highlight.
 */
final class ChartHighlightSweep {
   private ChartHighlightSweep() {
   }

   private static final String[] SHELVES = {
      "x", "y", "group", "path", "open", "high", "low", "close", "source", "target", "start", "end",
      "milestone"
   };

   /**
    * A highlight as a value: what it was, who held it and which fields its conditions name.
    *
    * @param owner    the holder's display name, calculator included; what the notes say.
    * @param identity the holder's field identity, calculator excluded ({@link
    *                 ChartHyperlinkCarry#identity}): a write that only adds, removes or changes a
    *                 calculator renames the measure but keeps its highlights, so the same field
    *                 is recognized by this and not by {@code owner}.
    */
   record Held(String owner, String identity, String kind, String name, String shelf,
               List<String> conditionFields)
   {
      /** Pairs a highlight with the one that is exactly it, calculator included. */
      String exactKey() {
         return owner + "|" + identity + "|" + kind + "|" + name;
      }

      /** Pairs a highlight with the one on the same field, whatever its calculator. */
      String key() {
         return identity + "|" + kind + "|" + name;
      }
   }

   /** The chart's highlights and the field names bound on it, taken at one moment. */
   record Snapshot(List<Held> held, Set<String> bound) {
      static final Snapshot EMPTY = new Snapshot(List.of(), Set.of());
   }

   /** Never throws: the sweep only adds disclosure and must not fail the write it reports on. */
   static Snapshot snapshot(ChartInfo info) {
      try {
         return take(info);
      }
      catch(RuntimeException e) {
         LOG.warn("Could not snapshot the chart's highlights", e);
         return Snapshot.EMPTY;
      }
   }

   /**
    * @param reported the identities ({@link ChartHyperlinkCarry#identity}) of the fields whose
    *                 highlights {@link ChartHyperlinkCarry#reapply} already reported.
    *
    * @return a note for each highlight the write removed together with its field, and for each
    *         surviving highlight whose condition names a field that was bound before the write and
    *         is not any more.
    */
   static List<String> sweep(ChartInfo info, Snapshot before, Set<String> reported) {
      List<String> notes = new ArrayList<>();

      if(before == null || before.held().isEmpty()) {
         return notes;
      }

      try {
         Snapshot after = take(info);

         List<Held> matches = pair(before.held(), after.held());

         for(int i = 0; i < before.held().size(); i++) {
            Held was = before.held().get(i);
            Held kept = matches.get(i);

            if(kept != null) {
               for(String field : kept.conditionFields()) {
                  String key = field.toLowerCase(Locale.ROOT);

                  if(before.bound().contains(key) && !after.bound().contains(key)) {
                     // the owner as it is now: a calculator change renames the measure
                     notes.add("The highlight '" + kept.name() + "' on " + kept.owner() + " (" +
                        kept.kind() + ") has a condition on " + field + ", which is no longer " +
                        "bound on the chart, so it can no longer match. Rebind " + field +
                        " or re-create the highlight with set_highlight.");
                  }
               }
            }
            else if(!reported.contains(was.identity())) {
               notes.add("The highlight '" + was.name() + "' on " + was.owner() + " (" +
                  was.kind() + ") was removed together with its field on the " + was.shelf() +
                  " shelf. Re-create it with set_highlight on a field that is bound.");
            }
         }
      }
      catch(RuntimeException e) {
         LOG.warn("Could not sweep the chart's highlights", e);
      }

      return notes;
   }

   /**
    * Pairs each highlight held before the write with the one that survives it, if any.
    *
    * <p>Each surviving highlight answers for at most one earlier one, so two measures on the same
    * column with different calculators, each holding a highlight of the same name, are never
    * collapsed into one. A highlight is paired with its exact self first and only then with one on
    * the same field under another calculator.
    *
    * @return the surviving counterpart of each highlight in {@code before}, in the same order;
    *         {@code null} where there is none.
    */
   private static List<Held> pair(List<Held> before, List<Held> after) {
      List<Held> candidates = new ArrayList<>(after);
      List<Held> matches = new ArrayList<>();

      for(Held was : before) {
         matches.add(claim(candidates, h -> h.exactKey().equals(was.exactKey())));
      }

      for(int i = 0; i < before.size(); i++) {
         if(matches.get(i) == null) {
            Held was = before.get(i);
            matches.set(i, claim(candidates, h -> h.key().equals(was.key())));
         }
      }

      return matches;
   }

   private static Held claim(List<Held> candidates, Predicate<Held> test) {
      for(int i = 0; i < candidates.size(); i++) {
         if(test.test(candidates.get(i))) {
            return candidates.remove(i);
         }
      }

      return null;
   }

   private static Snapshot take(ChartInfo info) {
      List<Held> held = new ArrayList<>();
      Set<String> bound = new LinkedHashSet<>();

      if(info == null) {
         return Snapshot.EMPTY;
      }

      for(String shelf : SHELVES) {
         for(ChartRef ref : ChartHyperlinkCarry.refs(info, shelf)) {
            addNames(bound, ref);

            if(ref instanceof HighlightRef h) {
               String owner = ChartHyperlinkCarry.fullName(ref);
               String identity = ChartHyperlinkCarry.identity(ref);
               add(held, owner, identity, ref.isMeasure() ? "mark" : "axis", shelf,
                   h.getHighlightGroup());
               add(held, owner, identity, "text", shelf, h.getTextHighlightGroup());
            }
         }
      }

      if(info instanceof VSChartInfo vinfo) {
         add(held, "the chart", "the chart", "mark", "chart", vinfo.getHighlightGroup());
         add(held, "the chart", "the chart", "text", "chart", vinfo.getTextHighlightGroup());
      }

      // x, y and the chart-level aesthetic channels, then the aesthetic fields of the aggregates:
      // a field moved to color is still bound. Design fields only: the runtime ones can still
      // describe the chart as it was before the write.
      for(VSDataRef ref : info.getFields()) {
         addNames(bound, ref);
      }

      for(AestheticRef aesthetic : info.getAestheticRefs(false)) {
         if(aesthetic != null) {
            addNames(bound, aesthetic.getDataRef());
         }
      }

      return new Snapshot(held, bound);
   }

   private static void addNames(Set<String> bound, DataRef ref) {
      if(ref == null) {
         return;
      }

      String[] names = {
         ref.getName(), ref.getAttribute(), ref instanceof VSDataRef v ? v.getFullName() : null
      };

      for(String name : names) {
         if(name != null && !name.isEmpty()) {
            bound.add(name.toLowerCase(Locale.ROOT));
         }
      }
   }

   private static void add(List<Held> held, String owner, String identity, String kind,
                           String shelf, HighlightGroup group)
   {
      if(group == null || group.getLevels() == null) {
         return;
      }

      for(String level : group.getLevels()) {
         String[] names = group.getNames(level);

         for(String name : names == null ? new String[0] : names) {
            Highlight highlight = group.getHighlight(level, name);
            held.add(new Held(owner, identity, kind, name, shelf, conditionFields(highlight)));
         }
      }
   }

   private static List<String> conditionFields(Highlight highlight) {
      List<String> fields = new ArrayList<>();
      ConditionList conditions = highlight == null ? null : highlight.getConditionGroup();

      for(int i = 0; conditions != null && i < conditions.getSize(); i++) {
         DataRef attribute = conditions.getAttribute(i);

         if(attribute != null && attribute.getName() != null &&
            !fields.contains(attribute.getName()))
         {
            fields.add(attribute.getName());
         }
      }

      return fields;
   }

   private static final Logger LOG = LoggerFactory.getLogger(ChartHighlightSweep.class);
}
