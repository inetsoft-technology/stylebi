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
import inetsoft.uql.viewsheet.VSAggregateRef;
import inetsoft.uql.viewsheet.VSDimensionRef;
import inetsoft.uql.viewsheet.graph.CandleChartInfo;
import inetsoft.uql.viewsheet.graph.ChartInfo;
import inetsoft.uql.viewsheet.graph.ChartRef;
import inetsoft.uql.viewsheet.graph.GanttChartInfo;
import inetsoft.uql.viewsheet.graph.HyperlinkRef;
import inetsoft.uql.viewsheet.graph.RelationChartInfo;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Carries the hyperlink of a field across a shelf rewrite (bug #80003).
 *
 * <p>A shelf write rebuilds every ref on the shelf from the incoming {@code FieldRef}s, and the
 * models those refs are built from have no hyperlink slot, so a field that stays on the shelf
 * lost its link. The links are snapshotted off the stored refs before the write and reapplied to
 * the refs that match afterwards.
 *
 * <p>Matching is by field identity, never by position: a measure is its column plus aggregate
 * formula, a dimension its column plus date level (a date-level change is a different field and
 * drops the link). A field inserted ahead of a linked one therefore cannot take its link.
 */
final class ChartHyperlinkCarry {
   private ChartHyperlinkCarry() {
   }

   /** A stored ref's link together with the identity it is matched by. */
   record Saved(ChartRef ref, Hyperlink link) {
   }

   /** The links of the refs currently on {@code shelf}; empty for an unknown shelf. */
   static List<Saved> snapshot(ChartInfo info, String shelf) {
      List<Saved> saved = new ArrayList<>();

      for(ChartRef ref : refs(info, shelf)) {
         if(ref instanceof HyperlinkRef link && link.getHyperlink() != null) {
            saved.add(new Saved(ref, (Hyperlink) link.getHyperlink().clone()));
         }
      }

      return saved;
   }

   /** Sets each saved link on the first unmatched ref now on {@code shelf} with the same identity. */
   static void reapply(ChartInfo info, String shelf, List<Saved> saved) {
      if(saved.isEmpty()) {
         return;
      }

      List<Saved> pool = new ArrayList<>(saved);

      for(ChartRef ref : refs(info, shelf)) {
         if(!(ref instanceof HyperlinkRef target) || target.getHyperlink() != null) {
            continue;
         }

         for(Saved candidate : pool) {
            if(sameField(candidate.ref(), ref)) {
               target.setHyperlink((Hyperlink) candidate.link().clone());
               pool.remove(candidate);
               break;
            }
         }
      }
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
