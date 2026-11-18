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

import inetsoft.uql.viewsheet.graph.AxisDescriptor;
import inetsoft.uql.viewsheet.graph.ChartInfo;
import inetsoft.uql.viewsheet.graph.ChartRef;
import inetsoft.uql.viewsheet.graph.VSChartInfo;
import inetsoft.uql.viewsheet.graph.VSChartRef;

import java.util.ArrayList;
import java.util.List;

/**
 * Carries the axis descriptor of a field across a shelf rewrite (bug #81636).
 *
 * <p>A shelf write rebuilds every ref on the shelf from the incoming {@code FieldRef}s, and the
 * models those refs are built from have no axis slot, so a field that stays on the shelf got a
 * default descriptor: the rendered axis lost its rotation, aliases, label side, line color, ticks
 * and label format, while the runtime refs the read tools consult still held the old ones. The
 * descriptors are snapshotted off the stored refs (the runtime refs where the design ref still
 * holds the default) before the write and reapplied to the refs that match afterwards, by field
 * identity as in {@link ChartHyperlinkCarry}.
 */
final class ChartAxisCarry {
   private ChartAxisCarry() {
   }

   /** A stored ref with the non-default axis descriptor it held. */
   record Saved(ChartRef ref, AxisDescriptor descriptor) {
   }

   /** The non-default axis descriptors of the refs currently on {@code shelf}. */
   static List<Saved> snapshot(ChartInfo info, String shelf) {
      List<Saved> saved = new ArrayList<>();
      List<ChartRef> runtime = runtimeRefs(info, shelf);

      for(ChartRef ref : ChartHyperlinkCarry.refs(info, shelf)) {
         AxisDescriptor desc = nonDefault(ref);

         if(desc == null) {
            for(ChartRef rt : runtime) {
               if(ChartHyperlinkCarry.sameField(ref, rt) && nonDefault(rt) != null) {
                  desc = nonDefault(rt);
                  break;
               }
            }
         }

         if(desc != null) {
            saved.add(new Saved(ref, desc.clone()));
         }
      }

      return saved;
   }

   /**
    * Sets each saved descriptor on the first unmatched ref now on {@code shelf} with the same
    * identity.
    *
    * @return whether any descriptor was reapplied, so the caller knows the chart must be rebuilt.
    */
   static boolean reapply(ChartInfo info, String shelf, List<Saved> saved) {
      if(saved.isEmpty()) {
         return false;
      }

      List<Saved> pool = new ArrayList<>(saved);
      boolean applied = false;

      for(ChartRef ref : ChartHyperlinkCarry.refs(info, shelf)) {
         if(!(ref instanceof VSChartRef target)) {
            continue;
         }

         for(Saved candidate : pool) {
            if(ChartHyperlinkCarry.sameField(candidate.ref(), ref)) {
               target.setAxisDescriptor(candidate.descriptor().clone());
               pool.remove(candidate);
               applied = true;
               break;
            }
         }
      }

      return applied;
   }

   private static AxisDescriptor nonDefault(ChartRef ref) {
      AxisDescriptor desc = ref instanceof VSChartRef v ? v.getAxisDescriptor() : null;
      return desc == null || desc.equalsContent(new AxisDescriptor()) ? null : desc;
   }

   private static List<ChartRef> runtimeRefs(ChartInfo info, String shelf) {
      List<ChartRef> refs = new ArrayList<>();

      if(!(info instanceof VSChartInfo vs) || shelf == null) {
         return refs;
      }

      ChartRef[] list = switch(shelf.toLowerCase()) {
         case "x" -> vs.getRTXFields();
         case "y" -> vs.getRTYFields();
         case "group" -> vs.getRTGroupFields();
         default -> null;
      };

      if(list != null) {
         refs.addAll(List.of(list));
      }

      return refs;
   }
}
