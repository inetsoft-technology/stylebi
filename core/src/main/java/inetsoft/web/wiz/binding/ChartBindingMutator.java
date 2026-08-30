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

import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.uql.asset.SourceInfo;
import inetsoft.web.binding.model.BDimensionRefModel;
import inetsoft.web.binding.model.ChartBindingModel;
import inetsoft.web.binding.model.graph.ChartAggregateRefModel;
import inetsoft.web.binding.model.graph.ChartDimensionRefModel;
import inetsoft.web.binding.model.graph.ChartRefModel;
import inetsoft.web.binding.service.DataRefModelFactoryService;
import inetsoft.web.wiz.binding.model.FieldRef;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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
      try {
         setShelf(model, shelf, fields, null, null, null);
      }
      catch(RuntimeException e) {
         throw e; // preserve e.g. requireType's IllegalArgumentException as-is
      }
      catch(Exception e) {
         throw new RuntimeException(e);
      }
   }

   /**
    * @param rvs            the runtime viewsheet, so a field's {@code namedGroup} can be
    *                       resolved against a worksheet-local named group.
    * @param source         the chart's own {@code SourceInfo}.
    * @param refModelService needed to resolve a worksheet-local named group's conditions.
    */
   public static void setShelf(ChartBindingModel model, String shelf, List<FieldRef> fields,
                               RuntimeViewsheet rvs, SourceInfo source,
                               DataRefModelFactoryService refModelService)
      throws Exception
   {
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
         refs.add(FieldRefFactory.toChartRef(field, rvs, source, refModelService));
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
      try {
         setSingleShelf(model, shelf, field, null, null, null);
      }
      catch(RuntimeException e) {
         throw e; // preserve e.g. requireSingleShelf's IllegalArgumentException as-is
      }
      catch(Exception e) {
         throw new RuntimeException(e);
      }
   }

   /** @see #setShelf(ChartBindingModel, String, List, RuntimeViewsheet, SourceInfo, DataRefModelFactoryService) */
   public static void setSingleShelf(ChartBindingModel model, String shelf, FieldRef field,
                                     RuntimeViewsheet rvs, SourceInfo source,
                                     DataRefModelFactoryService refModelService)
      throws Exception
   {
      String name = requireSingleShelf(shelf);
      ChartRefModel ref = field == null
         ? null : FieldRefFactory.toChartRef(field, rvs, source, refModelService);

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

   /** Reads one of the list shelves; never null, so a caller can count it without a guard. */
   public static List<ChartRefModel> readShelf(ChartBindingModel model, String shelf) {
      String name = shelf == null ? "" : shelf.trim().toLowerCase();
      List<ChartRefModel> refs = switch(name) {
         case "x" -> model.getXFields();
         case "y" -> model.getYFields();
         case "group" -> model.getGroupFields();
         default -> throw new IllegalArgumentException(
            "Unknown chart shelf '" + shelf + "'. Valid shelves: " + String.join(", ", SHELVES));
      };

      return refs == null ? List.of() : refs;
   }

   // ── per-dimension sort/ranking (bug #76350, PCB-001) ──────────────────────────────────────
   //
   // ChartDimensionRefModel extends BDimensionRefModel — the same base class TableBindingMutator
   // already drives with DimensionSortRanking for a crosstab's rows/cols. A chart's x/y/group
   // dimensions carry the identical order/sortByCol/ranking fields; FieldRefFactory.toChartRef
   // simply never set them, which is the whole reason "sort a chart axis by a measure's value"
   // had no tool despite the model underneath already supporting it.

   /**
    * The dimension a call means, on a chart's x/y/group shelf. Mirrors
    * {@code TableBindingMutator.requireDimension} exactly — chart dimensions are addressed by
    * column name for the same reason: a stable index would silently point at the wrong column
    * after any shelf reorder.
    */
   private static BDimensionRefModel requireDimension(ChartBindingModel model, String shelf,
                                                       String column, Integer index)
   {
      List<ChartRefModel> refs = readShelf(model, shelf);
      List<String> present = new ArrayList<>();
      Map<Integer, BDimensionRefModel> matches = new LinkedHashMap<>();

      for(int i = 0; i < refs.size(); i++) {
         ChartRefModel ref = refs.get(i);

         if(!(ref instanceof ChartDimensionRefModel dimension)) {
            continue;
         }

         String value = dimension.getColumnValue() == null
            ? dimension.getName() : dimension.getColumnValue();
         present.add(value);

         if(value != null && value.equalsIgnoreCase(column)) {
            matches.put(i, dimension);
         }
      }

      if(index != null) {
         BDimensionRefModel chosen = matches.get(index);

         if(chosen == null) {
            throw new IllegalArgumentException(
               "index " + index + " is not a position of '" + column + "' on the " + shelf +
               " shelf. It is bound at: " + matches.keySet() + ".");
         }

         return chosen;
      }

      if(matches.size() > 1) {
         throw new IllegalArgumentException(
            "'" + column + "' is bound " + matches.size() + " times on the " + shelf +
            " shelf, so this call is ambiguous. Pass 'index' to say which.");
      }

      if(matches.size() == 1) {
         return matches.values().iterator().next();
      }

      throw new IllegalArgumentException(
         "'" + column + "' is not a dimension on the " + shelf + " shelf. It holds: " +
         (present.isEmpty() ? "(nothing)" : String.join(", ", present)) + ".");
   }

   public static void setSort(ChartBindingModel model, String shelf, String column,
                              Integer index, DimensionSortRanking.Sort sort)
   {
      DimensionSortRanking.applySort(requireDimension(model, shelf, column, index), sort);
   }

   public static void setRanking(ChartBindingModel model, String shelf, String column,
                                 Integer index, DimensionSortRanking.Ranking ranking)
   {
      DimensionSortRanking.applyRanking(requireDimension(model, shelf, column, index), ranking);
   }

   /** The sort and ranking on every dimension of a chart shelf. */
   public static Map<String, Object> describeSorts(ChartBindingModel model, String shelf) {
      Map<String, Object> out = new LinkedHashMap<>();
      List<ChartRefModel> refs = readShelf(model, shelf);
      List<BDimensionRefModel> dimensions = new ArrayList<>();

      for(ChartRefModel ref : refs) {
         if(ref instanceof ChartDimensionRefModel dimension) {
            dimensions.add(dimension);
         }
      }

      for(int i = 0; i < dimensions.size(); i++) {
         BDimensionRefModel dimension = dimensions.get(i);
         String column = dimension.getColumnValue() == null
            ? dimension.getName() : dimension.getColumnValue();
         long occurrences = dimensions.stream()
            .map(d -> d.getColumnValue() == null ? d.getName() : d.getColumnValue())
            .filter(value -> value != null && value.equalsIgnoreCase(column))
            .count();

         out.put(occurrences > 1 ? column + " [" + i + "]" : column,
                 DimensionSortRanking.describe(dimension));
      }

      return out;
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
