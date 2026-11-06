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

import inetsoft.report.Hyperlink;
import inetsoft.report.filter.HighlightGroup;
import inetsoft.uql.viewsheet.VSAggregateRef;
import inetsoft.uql.viewsheet.VSDimensionRef;
import inetsoft.uql.viewsheet.graph.CandleChartInfo;
import inetsoft.uql.viewsheet.graph.ChartInfo;
import inetsoft.uql.viewsheet.graph.ChartRef;
import inetsoft.uql.viewsheet.graph.GanttChartInfo;
import inetsoft.uql.viewsheet.graph.HighlightRef;
import inetsoft.uql.viewsheet.graph.HyperlinkRef;
import inetsoft.uql.viewsheet.graph.RelationChartInfo;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Carries the hyperlink and highlights of a field across a shelf rewrite (bugs #80003, #80210).
 *
 * <p>A shelf write rebuilds every ref on the shelf from the incoming {@code FieldRef}s, and the
 * models those refs are built from have no hyperlink slot, so a field that stays on the shelf
 * lost its link; the series and text highlight groups, which also live on the ref, were lost the
 * same way. They are snapshotted off the stored refs before the write and reapplied to the refs
 * that match afterwards.
 *
 * <p>Matching is by field identity, never by position: a measure is its column plus aggregate
 * formula, a dimension its column plus date level (a date-level change is a different field and
 * drops the link). A field inserted ahead of a linked one therefore cannot take its link.
 *
 * <p>A highlight is dropped, and reported by {@link #reapply}, when its field stays on the shelf
 * under a different formula or date level: its conditions name the old full name (for example
 * {@code Sum(REVENUE)}) and would match nothing. A calculator change keeps the identity but renames
 * the full name, so a carried highlight can go stale the same way (as in the Composer).
 */
final class ChartHyperlinkCarry {
   private ChartHyperlinkCarry() {
   }

   /** A stored ref's link and highlights together with the identity it is matched by. */
   record Saved(ChartRef ref, Hyperlink link, HighlightGroup highlight, HighlightGroup textHighlight)
   {
      boolean hasHighlight() {
         return highlight != null || textHighlight != null;
      }
   }

   /** The links and highlights of the refs currently on {@code shelf}; empty for an unknown shelf. */
   static List<Saved> snapshot(ChartInfo info, String shelf) {
      List<Saved> saved = new ArrayList<>();

      for(ChartRef ref : refs(info, shelf)) {
         Hyperlink link = ref instanceof HyperlinkRef h && h.getHyperlink() != null
            ? (Hyperlink) h.getHyperlink().clone() : null;
         HighlightGroup hl = null;
         HighlightGroup textHl = null;

         if(ref instanceof HighlightRef h) {
            hl = h.getHighlightGroup() == null ? null : h.getHighlightGroup().clone();
            textHl = h.getTextHighlightGroup() == null ? null : h.getTextHighlightGroup().clone();
         }

         if(link != null || hl != null || textHl != null) {
            saved.add(new Saved(ref, link, hl, textHl));
         }
      }

      return saved;
   }

   /**
    * Sets each saved link and highlight on the first unmatched ref now on {@code shelf} with the
    * same identity.
    *
    * @return a note for each highlight dropped because its field is still on the shelf under a
    *         different formula or date level.
    */
   static List<String> reapply(ChartInfo info, String shelf, List<Saved> saved) {
      List<String> notes = new ArrayList<>();

      if(saved.isEmpty()) {
         return notes;
      }

      List<Saved> pool = new ArrayList<>(saved);
      List<ChartRef> now = refs(info, shelf);

      for(ChartRef ref : now) {
         for(Saved candidate : pool) {
            if(!sameField(candidate.ref(), ref)) {
               continue;
            }

            if(candidate.link() != null && ref instanceof HyperlinkRef target &&
               target.getHyperlink() == null)
            {
               target.setHyperlink((Hyperlink) candidate.link().clone());
            }

            if(ref instanceof HighlightRef target) {
               if(candidate.highlight() != null && target.getHighlightGroup() == null) {
                  target.setHighlightGroup(candidate.highlight().clone());
               }

               if(candidate.textHighlight() != null && target.getTextHighlightGroup() == null) {
                  target.setTextHighlightGroup(candidate.textHighlight().clone());
               }
            }

            pool.remove(candidate);
            break;
         }
      }

      for(Saved dropped : pool) {
         if(dropped.hasHighlight() && sameColumn(dropped.ref(), now)) {
            notes.add("The highlight on " + fullName(dropped.ref()) + " was removed: its field " +
               "stays on the " + shelf + " shelf under a different " +
               (dropped.ref() instanceof VSDimensionRef ? "date level" : "aggregate formula") +
               ", and a highlight's conditions name the old field. Re-create it with " +
               "set_highlight on the new field.");
         }
      }

      return notes;
   }

   private static boolean sameColumn(ChartRef old, List<ChartRef> now) {
      for(ChartRef ref : now) {
         boolean sameKind = old instanceof VSAggregateRef && ref instanceof VSAggregateRef ||
            old instanceof VSDimensionRef && ref instanceof VSDimensionRef;

         if(sameKind && old.getName() != null && old.getName().equalsIgnoreCase(ref.getName())) {
            return true;
         }
      }

      return false;
   }

   private static String fullName(ChartRef ref) {
      return ref instanceof VSAggregateRef a ? a.getFullName() :
         ref instanceof VSDimensionRef d ? d.getFullName() : ref.getName();
   }

   private static boolean sameField(ChartRef a, ChartRef b) {
      if(a instanceof VSAggregateRef x && b instanceof VSAggregateRef y) {
         return x.getName() != null && x.getName().equalsIgnoreCase(y.getName()) &&
            Objects.equals(x.getFormula(), y.getFormula());
      }

      if(a instanceof VSDimensionRef x && b instanceof VSDimensionRef y) {
         return x.getName() != null && x.getName().equalsIgnoreCase(y.getName()) &&
            x.getDateLevel() == y.getDateLevel();
      }

      return false;
   }

   private static List<ChartRef> refs(ChartInfo info, String shelf) {
      List<ChartRef> refs = new ArrayList<>();

      if(info == null || shelf == null) {
         return refs;
      }

      ChartRef[] list = switch(shelf.toLowerCase()) {
         case "x" -> info.getXFields();
         case "y" -> info.getYFields();
         case "group" -> info.getGroupFields();
         default -> null;
      };

      if(list != null) {
         refs.addAll(List.of(list));
         return refs;
      }

      ChartRef single = switch(shelf.toLowerCase()) {
         case "path" -> info.getPathField();
         case "open" -> info instanceof CandleChartInfo c ? c.getOpenField() : null;
         case "high" -> info instanceof CandleChartInfo c ? c.getHighField() : null;
         case "low" -> info instanceof CandleChartInfo c ? c.getLowField() : null;
         case "close" -> info instanceof CandleChartInfo c ? c.getCloseField() : null;
         case "source" -> info instanceof RelationChartInfo r ? r.getSourceField() : null;
         case "target" -> info instanceof RelationChartInfo r ? r.getTargetField() : null;
         case "start" -> info instanceof GanttChartInfo g ? g.getStartField() : null;
         case "end" -> info instanceof GanttChartInfo g ? g.getEndField() : null;
         case "milestone" -> info instanceof GanttChartInfo g ? g.getMilestoneField() : null;
         default -> null;
      };

      if(single != null) {
         refs.add(single);
      }

      return refs;
   }
}
