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

import inetsoft.web.binding.model.ChartBindingModel;
import inetsoft.web.binding.model.graph.ChartAggregateRefModel;
import inetsoft.web.binding.model.graph.ChartDimensionRefModel;
import inetsoft.web.binding.model.graph.ChartRefModel;
import inetsoft.web.wiz.binding.model.FieldRef;

import java.util.ArrayList;
import java.util.List;

/**
 * Read-modify-write over {@code ChartBindingModel}.
 *
 * <p>Callers must pass the model returned by {@code VSBindingService.createModel} and mutate
 * it in place. Constructing a fresh model would drop every field this class does not set —
 * including the thirteen in {@link ChartBindingFields#AESTHETIC}, which
 * {@code ChangeChartRefEvent} round-trips whether or not a data-binding write means to touch
 * them.
 */
public final class ChartBindingMutator {
   /** Shelves this phase writes. Chart's specialized shelves arrive in 2b Phase 2. */
   public static final List<String> SHELVES = List.of("x", "y", "group");

   private ChartBindingMutator() {
   }

   public static void setShelf(ChartBindingModel model, String shelf, List<FieldRef> fields) {
      String name = shelf == null ? "" : shelf.trim().toLowerCase();

      if(SINGLE_SHELVES.contains(name)) {
         throw new IllegalArgumentException(
            "'" + name + "' holds exactly one field, not a list — use set_chart_single_shelf " +
            "for it. Passing a list here would bind only the first field and drop the rest " +
            "without saying so.");
      }

      if(!SHELVES.contains(name)) {
         throw new IllegalArgumentException(
            "Unknown chart shelf '" + shelf + "'. Valid shelves: " +
            String.join(", ", SHELVES) + ". Single-field shelves (open, high, low, close, path, " +
            "source, target, start, end, milestone) use set_chart_single_shelf.");
      }

      List<ChartRefModel> refs = new ArrayList<>();

      for(FieldRef field : fields == null ? List.<FieldRef>of() : fields) {
         refs.add(FieldRefFactory.toChartRef(field));
      }

      switch(name) {
      case "x" -> model.setXFields(refs);
      case "y" -> model.setYFields(refs);
      default -> model.setGroupFields(refs);
      }
   }

   /**
    * Shelves that hold exactly <b>one</b> field rather than a list.
    *
    * <p>A candlestick has one close, a Gantt bar one start. Keeping them out of {@link #SHELVES}
    * is deliberate: routing them through the list API would silently bind the first element of a
    * list and drop the rest.
    */
   public static final List<String> SINGLE_SHELVES =
      List.of("open", "high", "low", "close", "path", "source", "target",
              "start", "end", "milestone");

   /**
    * Sets one single-field shelf. A null {@code field} clears it.
    *
    * <p>Which shelves a chart actually reads depends on its type — a candlestick uses
    * open/high/low/close and ignores x/y, a Gantt uses start/end/milestone. Binding the wrong
    * family for the current chart type renders an empty chart with no error anywhere, which is
    * why {@code set_chart_type} and these belong in the same conversation.
    */
   public static void setSingleShelf(ChartBindingModel model, String shelf, FieldRef field) {
      String name = requireSingleShelf(shelf);
      ChartRefModel ref = field == null ? null : FieldRefFactory.toChartRef(field);

      switch(name) {
      case "open" -> model.setOpenField(ref);
      case "high" -> model.setHighField(ref);
      case "low" -> model.setLowField(ref);
      case "close" -> model.setCloseField(ref);
      case "path" -> model.setPathField(ref);
      case "source" -> model.setSourceField(ref);
      case "target" -> model.setTargetField(ref);
      case "start" -> model.setStartField(ref);
      case "end" -> model.setEndField(ref);
      default -> model.setMilestoneField(ref);
      }
   }

   /** Reads one single-field shelf, or null when nothing is bound to it. */
   public static ChartRefModel readSingleShelf(ChartBindingModel model, String shelf) {
      return switch(requireSingleShelf(shelf)) {
         case "open" -> model.getOpenField();
         case "high" -> model.getHighField();
         case "low" -> model.getLowField();
         case "close" -> model.getCloseField();
         case "path" -> model.getPathField();
         case "source" -> model.getSourceField();
         case "target" -> model.getTargetField();
         case "start" -> model.getStartField();
         case "end" -> model.getEndField();
         default -> model.getMilestoneField();
      };
   }

   private static String requireSingleShelf(String shelf) {
      String name = shelf == null ? "" : shelf.trim().toLowerCase();

      if(SHELVES.contains(name)) {
         throw new IllegalArgumentException(
            "'" + name + "' holds a list of fields, not one — use set_chart_shelf for it. " +
            "Single-field shelves: " + String.join(", ", SINGLE_SHELVES) + ".");
      }

      if(!SINGLE_SHELVES.contains(name)) {
         throw new IllegalArgumentException(
            "Unknown chart shelf '" + shelf + "'. Single-field shelves: " +
            String.join(", ", SINGLE_SHELVES) + ". List shelves: " +
            String.join(", ", SHELVES) + ".");
      }

      return name;
   }
}
