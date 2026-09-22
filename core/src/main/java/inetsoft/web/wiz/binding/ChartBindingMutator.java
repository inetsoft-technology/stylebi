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
import inetsoft.report.internal.Util;
import inetsoft.uql.XConstants;
import inetsoft.uql.asset.SourceInfo;
import inetsoft.uql.viewsheet.graph.GraphTypes;
import inetsoft.uql.viewsheet.graph.VSChartInfo;
import inetsoft.uql.viewsheet.graph.VSMapInfo;
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
import java.util.Objects;

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
         setShelf(model, shelf, fields, null, null, null, null);
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
      setShelf(model, shelf, fields, rvs, source, refModelService, null);
   }

   /**
    * @param chartInfo      the chart's live {@code VSChartInfo}, so this write can be checked
    *                       against the org's column-count limit before it lands — the same
    *                       check {@code VSChartDndService.addColumns} makes for a drag-drop add.
    *                       {@code null} skips the check (matching the no-chartInfo overloads,
    *                       used where no live chart/session is available, e.g. unit tests).
    */
   public static void setShelf(ChartBindingModel model, String shelf, List<FieldRef> fields,
                               RuntimeViewsheet rvs, SourceInfo source,
                               DataRefModelFactoryService refModelService, VSChartInfo chartInfo)
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

      requireColumnLimit(chartInfo, readShelf(model, name).size(), fields == null ? 0 : fields.size());
      requireNoMapDimensionOnXY(chartInfo, name, fields);

      // Captured before the overwrite below, for three independent, unrelated "restore state
      // across a shelf rewrite" mechanisms below: preserveChartTypes (aggregate chartType on x/y
      // only), the dimension sort/ranking preservation loop (all three shelves -- bug #76881,
      // porting VTB-004/TableBindingMutator.dimensions()'s own previous-state matching), and
      // preserveAggregateState (aggregate calculateInfo/secondaryY, all three shelves -- bug
      // #76896).
      List<ChartRefModel> oldRefs = new ArrayList<>(readShelf(model, name));

      List<ChartRefModel> refs = new ArrayList<>();
      List<FieldRef> fieldList = fields == null ? List.<FieldRef>of() : fields;

      // Unconsumed-search pool for preserveAggregateState -- unlike the dimension branch below
      // (which still matches by position, a pre-existing #76881 weakness left as-is here), this
      // must survive an ordinary field *insertion* ahead of an existing measure. Mirrors
      // preserveChartTypes's own consume-based search (minus its secondaryY tiebreak, which would
      // be circular here -- secondaryY is one of the fields this fix itself restores).
      List<ChartAggregateRefModel> unconsumedAggregates = new ArrayList<>();

      for(ChartRefModel old : oldRefs) {
         if(old instanceof ChartAggregateRefModel aggregate) {
            unconsumedAggregates.add(aggregate);
         }
      }

      for(int i = 0; i < fieldList.size(); i++) {
         FieldRef field = fieldList.get(i);
         ChartRefModel ref = FieldRefFactory.toChartRef(field, rvs, source, refModelService);

         if(ref instanceof ChartDimensionRefModel dimension && i < oldRefs.size() &&
            oldRefs.get(i) instanceof ChartDimensionRefModel previous && matches(previous, field))
         {
            preserveDimensionState(previous, dimension, field);
         }

         if(ref instanceof ChartAggregateRefModel aggregate) {
            ChartAggregateRefModel previousAgg = null;

            for(ChartAggregateRefModel candidate : unconsumedAggregates) {
               if(sameMeasure(candidate, aggregate)) {
                  previousAgg = candidate;
                  break;
               }
            }

            if(previousAgg != null) {
               unconsumedAggregates.remove(previousAgg);
               preserveAggregateState(previousAgg, aggregate, field);
            }
         }

         refs.add(ref);
      }

      // Only x/y ever carry a per-measure chartType (multi-style) -- group's aggregates have
      // none to lose, so this is skipped there rather than harmlessly doing nothing every call.
      if(("x".equals(name) || "y".equals(name)) && !oldRefs.isEmpty()) {
         preserveChartTypes(oldRefs, refs);
      }

      switch(name) {
      case "x" -> model.setXFields(refs);
      case "y" -> model.setYFields(refs);
      default -> model.setGroupFields(refs);
      }
   }

   /**
    * Carries each surviving measure's {@code chartType} across a shelf rewrite.
    *
    * <p>{@code toChartRef} never sets a {@code chartType} on the refs it builds --
    * {@code requireNoInboundChartType} refuses one arriving on the incoming {@code FieldRef} by
    * design, since {@code set_chart_type}'s own {@code field} argument is the only accepted way
    * to write one. But that left every {@code set_chart_shelf} call silently resetting whatever a
    * prior {@code set_chart_type} had stored -- including the ordinary case of adding one more
    * field to an already-typed shelf, confirmed live to NOT happen via drag-and-drop in the native
    * Composer UI, only through this write path. Bug #76689, VCS-005.
    *
    * <p>Matches by (column, aggregate) identity, and further by {@code secondaryY} when more than
    * one surviving ref shares that identity (two measures can legitimately share a column and
    * aggregate, differing only by which Y axis they render on -- {@code requireNoInboundChartType}'s
    * own sibling ambiguity, VCS-014) -- a candidate consumed by one match is removed from
    * consideration so it is never reused for a second match, meaning two old refs sharing an
    * identity restore onto two different new refs (in bind order) rather than both restoring onto
    * whichever is found first.
    */
   private static void preserveChartTypes(List<ChartRefModel> oldRefs, List<ChartRefModel> newRefs) {
      List<ChartAggregateRefModel> survivors = new ArrayList<>();

      for(ChartRefModel ref : oldRefs) {
         if(ref instanceof ChartAggregateRefModel aggregate &&
            aggregate.getChartType() != GraphTypes.CHART_AUTO)
         {
            survivors.add(aggregate);
         }
      }

      if(survivors.isEmpty()) {
         return;
      }

      for(ChartRefModel ref : newRefs) {
         if(!(ref instanceof ChartAggregateRefModel newAggregate)) {
            continue;
         }

         ChartAggregateRefModel matched = null;

         // Prefer column+aggregate+secondaryY, so a same-identity pair (differing only by
         // secondaryY) each restore onto their own match rather than either onto both.
         for(ChartAggregateRefModel old : survivors) {
            if(sameMeasure(old, newAggregate) && old.isSecondaryY() == newAggregate.isSecondaryY()) {
               matched = old;
               break;
            }
         }

         if(matched == null) {
            for(ChartAggregateRefModel old : survivors) {
               if(sameMeasure(old, newAggregate)) {
                  matched = old;
                  break;
               }
            }
         }

         if(matched != null) {
            newAggregate.setChartType(matched.getChartType());
            survivors.remove(matched);
         }
      }
   }

   private static boolean sameMeasure(ChartAggregateRefModel a, ChartAggregateRefModel b) {
      return equalsIgnoreCaseOrBothNull(a.getColumnValue(), b.getColumnValue()) &&
             equalsIgnoreCaseOrBothNull(a.getFormula(), b.getFormula());
   }

   /**
    * Carries a matched measure's {@code calculateInfo} (Trend/Calculator) and {@code secondaryY}
    * across a shelf rewrite -- the aggregate-ref sibling of {@link #preserveDimensionState},
    * closing the gap left after bug #76881 fixed only the dimension case in this file (bug
    * #76896). {@code toChartRef} already coerces an incoming {@code null} {@code secondaryY} to
    * {@code false} before this runs, so this reads {@code field.secondaryY()} directly (the raw
    * incoming value), not {@code aggregate.isSecondaryY()} -- the only way to tell "the caller
    * said nothing" apart from "the caller explicitly said false".
    *
    * <p>{@code secondaryY} preservation runs unconditionally on every shelf, with no {@code x}/
    * {@code group} gate: the plugin layer already refuses {@code secondaryY} outright on any
    * shelf but {@code y} (bug #76608), so {@code previous.isSecondaryY()} can never be
    * {@code true} on {@code x}/{@code group} -- this branch is a permanent no-op there, not a
    * case needing its own shelf guard.
    */
   private static void preserveAggregateState(ChartAggregateRefModel previous,
                                               ChartAggregateRefModel aggregate, FieldRef field)
   {
      if(field.calculateInfo() == null) {
         aggregate.setCalculateInfo(previous.getCalculateInfo());
      }

      if(field.secondaryY() == null) {
         aggregate.setSecondaryY(previous.isSecondaryY());
      }
   }

   /**
    * Carries a matched dimension's sort/ranking state across a shelf rewrite -- porting VTB-004
    * (Redmine #76574, {@code TableBindingMutator.dimensions()}'s own {@code matches()}/{@code
    * copyOf()}) to the chart x/y/group path, which never had it (bug #76881). {@code
    * FieldRefFactory.toChartRef} already applied {@code dimension}'s own {@code columnValue}/
    * {@code dateLevel}/{@code namedGroupInfo} (+ forced {@code order = SORT_SPECIFIC} when a
    * named group is present) before this runs, so this only ever touches the fields {@code
    * toChartRef} does not set from {@code field} at all: {@code sortByCol}, {@code manualOrder},
    * {@code rankingOption}/{@code rankingN}/{@code rankingCol}, {@code groupOthers}, {@code
    * others}, {@code timeSeries}.
    *
    * <p>{@code order} is copied forward too, but only when the incoming field does not itself
    * carry a named group -- an incoming {@code namedGroupValues}/{@code namedGroup} already won
    * that field via {@code toChartRef}'s own forced {@code SORT_SPECIFIC}, and copying over it
    * here would silently discard what the caller just asked for. When {@code order} IS copied
    * forward, it can carry a stray {@code SORT_SPECIFIC} bit from a matched previous ref whose
    * named group this incoming field no longer supplies -- stripped by the same self-heal
    * {@code TableBindingMutator.dimensions()} applies, mirroring {@code
    * BDimensionRefModel.createDataRef()}'s own conversion-time self-heal.
    */
   private static void preserveDimensionState(ChartDimensionRefModel previous,
                                               ChartDimensionRefModel dimension, FieldRef field)
   {
      dimension.setSortByCol(previous.getSortByCol());
      dimension.setManualOrder(previous.getManualOrder() == null
         ? null : new ArrayList<>(previous.getManualOrder()));
      dimension.setRankingOption(previous.getRankingOption());
      dimension.setRankingN(previous.getRankingN());
      dimension.setRankingCol(previous.getRankingCol());
      dimension.setGroupOthers(previous.isGroupOthers());
      dimension.setOthers(previous.isOthers());
      dimension.setTimeSeries(previous.isTimeSeries());

      boolean incomingSuppliesGroup =
         field.namedGroupValues() != null || field.namedGroup() != null;

      if(!incomingSuppliesGroup) {
         dimension.setOrder(previous.getOrder());

         if((dimension.getOrder() & XConstants.SORT_SPECIFIC) != 0 &&
            (dimension.getNamedGroupInfo() == null || dimension.getNamedGroupInfo().getType() == 0) &&
            (dimension.getManualOrder() == null || dimension.getManualOrder().isEmpty()))
         {
            dimension.setOrder(dimension.getOrder() & ~XConstants.SORT_SPECIFIC);
         }
      }
   }

   /** Whether {@code previous} is the same occurrence of the same column as {@code field} --
    *  same identity {@code TableBindingMutator.dimensions()}'s own {@code matches()} uses. */
   private static boolean matches(BDimensionRefModel previous, FieldRef field) {
      if(previous == null || field.column() == null) {
         return false;
      }

      String previousColumn = previous.getColumnValue() == null
         ? previous.getName() : previous.getColumnValue();

      if(previousColumn == null || !previousColumn.equalsIgnoreCase(field.column())) {
         return false;
      }

      String previousLevel = previous.getDateLevel();
      String incomingLevel = DateLevels.normalize(field.dateLevel());
      boolean previousUnset = previousLevel == null || previousLevel.isBlank() ||
         "-1".equals(previousLevel);
      boolean incomingUnset = incomingLevel == null || "-1".equals(incomingLevel);

      return previousUnset && incomingUnset || Objects.equals(previousLevel, incomingLevel);
   }

   /** Case-insensitive like the rest of this class's column/measure-name matching (e.g.
    *  {@code requireDimension}, {@code requireUnambiguousMeasure}) -- {@code Objects.equals}
    *  alone would compare case-sensitively, an inconsistent convention within the same class. */
   private static boolean equalsIgnoreCaseOrBothNull(String a, String b) {
      return a == null ? b == null : a.equalsIgnoreCase(b);
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
         setSingleShelf(model, shelf, field, null, null, null, null);
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
      setSingleShelf(model, shelf, field, rvs, source, refModelService, null);
   }

   /**
    * @param chartInfo see {@link #setShelf(ChartBindingModel, String, List, RuntimeViewsheet,
    *                  SourceInfo, DataRefModelFactoryService, VSChartInfo)}'s {@code chartInfo}.
    */
   public static void setSingleShelf(ChartBindingModel model, String shelf, FieldRef field,
                                     RuntimeViewsheet rvs, SourceInfo source,
                                     DataRefModelFactoryService refModelService,
                                     VSChartInfo chartInfo)
      throws Exception
   {
      String name = requireSingleShelf(shelf);
      int oldCount = readSingleShelf(model, name) == null ? 0 : 1;
      int newCount = field == null ? 0 : 1;
      requireColumnLimit(chartInfo, oldCount, newCount);

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

   /**
    * Refuses a shelf write that would push the chart's total bound-field count (every shelf plus
    * every aesthetic channel, mirroring {@code VSChartInfo.getFields()} — the same total
    * {@code VSChartDndService.addColumns} checks for a drag-drop add) past
    * {@code Util.getOrganizationMaxColumn()}. {@code chartInfo == null} skips the check: there is
    * no live chart to total against (the no-chartInfo overloads used by unit tests and any other
    * caller that only has a bare {@code ChartBindingModel}).
    *
    * <p>The check only fires on <b>net growth</b> of this shelf ({@code newShelfCount >
    * oldShelfCount}). A net-neutral or net-decreasing edit is always allowed, regardless of the
    * chart's pre-existing total — mirroring how native's own add/remove split behaves:
    * {@code VSChartDndService.addColumns} caps a drag-drop add, but
    * {@code VSChartDndService.removeColumns} has no limit check at all. Without this guard, a
    * chart that is already over budget (grandfathered, or the org limit lowered by an admin after
    * the chart was created) would become permanently unable to have any shelf edited through this
    * path — even a strict shrink — because the absolute post-edit total would still read over
    * limit.
    */
   private static void requireColumnLimit(VSChartInfo chartInfo, int oldShelfCount, int newShelfCount) {
      if(chartInfo == null || newShelfCount <= oldShelfCount) {
         return;
      }

      int geoSize = chartInfo instanceof VSMapInfo ? ((VSMapInfo) chartInfo).getGeoFieldCount() : 0;
      int total = chartInfo.getFields().length + geoSize - oldShelfCount + newShelfCount;

      if(total > Util.getOrganizationMaxColumn()) {
         throw new IllegalArgumentException(Util.getColumnLimitMessage());
      }
   }

   /**
    * On a map, {@code x}/{@code y} hold lat/lon measures ({@code MapInfo.isLat}/{@code isLon}),
    * not a dimension shelf — a map's geo dimension lives on {@code geoFields}, a 4th list this
    * class does not write, and {@code set_chart_type(type: "map")} already populates it
    * automatically on retype. A dimension bound here has no valid interpretation: it silently
    * flips {@code VSMapInfo.isFacet()} via {@code MapInfo.hasXYDimension()}, splitting the map
    * into one tiny facet panel per value instead of rendering it as a single map.
    */
   private static void requireNoMapDimensionOnXY(VSChartInfo chartInfo, String shelf,
                                                  List<FieldRef> fields)
   {
      if(!(chartInfo instanceof VSMapInfo) || fields == null ||
         !("x".equals(shelf) || "y".equals(shelf)))
      {
         return;
      }

      for(FieldRef field : fields) {
         if("dimension".equalsIgnoreCase(field.type())) {
            throw new IllegalArgumentException(
               "'" + shelf + "' holds lat/lon measures on a map, not dimensions. The geo " +
               "dimension is set automatically by set_chart_type and reads back on the 'geo' " +
               "shelf; bind any other dimension to 'group' instead.");
         }
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
      if(sort != null && sort.sortByField() != null && !sort.sortByField().isBlank()) {
         requireUnambiguousMeasure(model, sort.sortByField(), "sortByField");
      }

      DimensionSortRanking.applySort(requireDimension(model, shelf, column, index), sort);
   }

   public static void setRanking(ChartBindingModel model, String shelf, String column,
                                 Integer index, DimensionSortRanking.Ranking ranking)
   {
      if(ranking != null && ranking.measure() != null && !ranking.measure().isBlank()) {
         requireUnambiguousMeasure(model, ranking.measure(), "measure");
      }

      DimensionSortRanking.applyRanking(requireDimension(model, shelf, column, index), ranking);
   }

   /**
    * Refuses a {@code sortByField}/{@code measure} that names a bare column bound as a measure
    * more than once across the chart's shelves under different aggregates -- e.g. both
    * {@code Sum(Total)} and {@code Average(Total)} on {@code y}. Bug #76689, VCS-014: a bare name
    * that matches 2+ bound measures used to silently resolve to whichever was bound first (via
    * {@code BDimensionRefModel.setSortByCol}/{@code setRankingCol}, which stores the raw string
    * with no resolution logic of its own downstream), with no error and no signal a different
    * aggregate could have been meant. An already-qualified form (e.g. {@code "Sum(Total)"}) is
    * ordinarily unambiguous and passes straight through, matching {@code get_binding}'s own
    * {@code highlightField} vocabulary for a measure -- mirrors {@code requireDimension}'s
    * same-shelf {@code index}-ambiguity discipline, extended across shelves and by aggregate
    * identity instead of shelf position, since a measure (unlike a dimension) is never
    * disambiguated by position. "Ordinarily", not always: the same column+aggregate can also be
    * bound twice differing only by {@code secondaryY} (the collision {@code preserveChartTypes}
    * already handles for VCS-005), in which case even the qualified form is genuinely ambiguous
    * and is refused rather than silently accepted as if it named one binding.
    */
   private static void requireUnambiguousMeasure(ChartBindingModel model, String measure,
                                                  String param)
   {
      List<String> qualifiedMatches = new ArrayList<>();
      List<String> bareMatches = new ArrayList<>();

      for(String shelf : SHELVES) {
         for(ChartRefModel ref : readShelf(model, shelf)) {
            if(!(ref instanceof ChartAggregateRefModel aggregate)) {
               continue;
            }

            String column = aggregate.getColumnValue();
            String formula = aggregate.getFormula();

            if(column == null) {
               continue;
            }

            String qualified = formula == null ? column : formula + "(" + column + ")";

            if(qualified.equalsIgnoreCase(measure)) {
               qualifiedMatches.add(qualified);
            }

            if(column.equalsIgnoreCase(measure)) {
               bareMatches.add(qualified);
            }
         }
      }

      // An already-qualified form is only unambiguous when exactly one binding produces it.
      // Two bindings can legitimately stringify identically -- e.g. Sum(Total) bound twice,
      // once on the primary Y axis and once on secondary (the same collision setShelf's own
      // chartType restoration -- see preserveChartTypes/sameMeasure above -- already has to
      // handle) -- since this qualified vocabulary (matching get_binding's own
      // highlightField) carries no secondaryY/shelf qualifier at all. There is no further
      // string this call could accept to tell them apart, so it is refused outright rather
      // than silently resolving to whichever bound first -- the same failure shape this
      // whole method exists to close, just one level up from the bare-column case below.
      if(qualifiedMatches.size() > 1) {
         throw new IllegalArgumentException(
            "'" + param + "' \"" + measure + "\" names " + qualifiedMatches.size() +
            " separate measure bindings that all stringify identically (the same column and " +
            "aggregate bound more than once, most likely differing only by which Y axis they " +
            "render on) -- there is currently no qualified form that tells them apart. Remove " +
            "the duplicate binding, or " + param + " by a different, unambiguous measure " +
            "instead.");
      }

      if(qualifiedMatches.size() == 1) {
         return;
      }

      if(bareMatches.size() > 1) {
         throw new IllegalArgumentException(
            "'" + param + "' \"" + measure + "\" is ambiguous -- " + bareMatches.size() +
            " measures share this column with different aggregates: " +
            String.join(", ", bareMatches) + ". Pass the qualified form (e.g. " + param + ":\"" +
            bareMatches.get(0) + "\") to disambiguate.");
      }

      // Zero matches is deliberately NOT refused here, unlike the >1 cases above: this check's
      // scope is narrowly the silent-first-match ambiguity (Bug #76689, VCS-014), not whether
      // the name resolves to a real binding at all -- an unresolvable sortByField/measure is
      // pre-existing, documented behavior this fix does not change.
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
