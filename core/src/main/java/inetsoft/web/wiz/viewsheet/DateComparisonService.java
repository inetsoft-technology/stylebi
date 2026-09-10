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
package inetsoft.web.wiz.viewsheet;

import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.report.composition.graph.GraphTypeUtil;
import inetsoft.uql.XConstants;
import inetsoft.uql.erm.DataRef;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.graph.Calculator;
import inetsoft.uql.viewsheet.graph.ChartAggregateRef;
import inetsoft.uql.viewsheet.graph.GraphTypes;
import inetsoft.uql.viewsheet.graph.VSChartInfo;
import inetsoft.uql.viewsheet.internal.DateCompareAbleAssemblyInfo;
import inetsoft.uql.viewsheet.internal.DateComparisonInfo;
import inetsoft.uql.viewsheet.internal.DateComparisonUtil;
import inetsoft.uql.viewsheet.internal.StandardPeriods;
import inetsoft.uql.viewsheet.internal.VSAssemblyInfo;
import inetsoft.web.composer.model.vs.*;
import inetsoft.web.composer.vs.dialog.DateComparisonDialogService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.security.Principal;
import java.util.*;

/**
 * Date comparison — period-over-period analysis on an assembly.
 *
 * <p><b>The central guard is the end date.</b> A standard period carries both
 * {@code toDayAsEndDay} and an explicit {@code endDay}, and when the flag is set the range
 * anchors on <i>today</i> and the supplied end date is <b>silently discarded</b>. That is a
 * recorded defect: a caller comparing on a due-date or any forward-looking field asked for a
 * range ending at a specific date, got one ending today, and nothing said so. So supplying an
 * end date here clears the flag, and supplying neither is refused rather than defaulted.
 *
 * <p>The read side deliberately does <b>not</b> echo the raw cell format. A date-comparison cell
 * once serialized a 67 KB timezone table into the response; the normalized shape here carries
 * only what a caller can act on.
 *
 * <p>{@code DateComparisonPaneModel} also carries a {@code VisualFrameModel}, and this service
 * deliberately does <b>not</b> expose it. The date-comparison palette is disabled product-wide:
 * every point that would apply it is commented out behind an {@code @dcColorRemove} marker — the
 * dialog row in {@code date-comparison-pane.component.html}, three blocks plus
 * {@code refreshDcColorFrame()} in {@code ChartDcProcessor}, and the crosstab-to-chart handoff in
 * {@code DateComparisonUtil}. What replaced it is {@code ChartDcProcessor}'s live path, where the
 * trend point/line simply takes its bar's colour. The model still round-trips a frame, so a tool
 * that accepted one would store it, report it back, and change nothing on the chart — a silent
 * success is worse than no knob at all. If the marker is ever lifted, this is the place to
 * re-expose it.
 */
@Service
public class DateComparisonService {
   @Autowired
   public DateComparisonService(ViewsheetSessionService sessions,
                                DateComparisonDialogService comparisonService)
   {
      this.sessions = sessions;
      this.comparisonService = comparisonService;
   }

   /**
    * A date-comparison request in the agent vocabulary.
    *
    * @param periods          how many periods back to compare
    * @param level            the period level — the date level token, e.g. year, quarter, month
    * @param endDate          the range end. Required unless {@code endToday} is set.
    * @param endToday         anchor the range on today instead of an explicit end
    * @param comparisonOption what the numbers mean — value, change, percentChange,
    *                        changeAndValue, or percentChangeAndValue
    * @param shareAssembly   another DateCompareAble assembly to share this assembly's
    *                        date-comparison config from, instead of setting its own
    * @param toDate    whether the period's range runs only up to the same point-in-time as
    *                  today, within its level (e.g. Jan 1 - Mar 15 for a quarter, not the whole
    *                  quarter) — the standard period pane's own "to date" checkbox
    * @param inclusive whether the period's end date is included in its range
    * @param customPeriods an arbitrary, caller-defined list of start/end date-range pairs (e.g.
    *                     "March 1-15 this year" vs. "March 1-15 last year") — StyleBI's
    *                     {@code CustomPeriods}, a peer to the standard period shape above, not a
    *                     variant of it. Mutually exclusive with {@code periods}/{@code level}/
    *                     {@code endDate}/{@code endToday}/{@code toDate}/{@code inclusive} and
    *                     with {@code interval} (StyleBI does not expose interval sub-windows for
    *                     a custom period — see {@code DateComparisonInfo.getIntervalConditions()},
    *                     ticket 64217).
    */
   public record Comparison(Integer periods, String level, String endDate, boolean endToday,
                            String interval, Boolean useFacet, Boolean onlyShowMostRecentDate,
                            String comparisonOption, String shareAssembly, Boolean toDate,
                            Boolean inclusive, List<CustomPeriod> customPeriods) {
      /** Backward-compatible with every call site that predates {@code customPeriods}. */
      public Comparison(Integer periods, String level, String endDate, boolean endToday,
                        String interval, Boolean useFacet, Boolean onlyShowMostRecentDate,
                        String comparisonOption, String shareAssembly, Boolean toDate,
                        Boolean inclusive)
      {
         this(periods, level, endDate, endToday, interval, useFacet, onlyShowMostRecentDate,
              comparisonOption, shareAssembly, toDate, inclusive, null);
      }
   }

   /** One caller-defined date-range pair, e.g. {@code {start: "2026-03-01", end: "2026-03-15"}}. */
   public record CustomPeriod(String start, String end) {}

   /** The current settings, normalized. Never echoes the raw cell format. */
   public Map<String, Object> read(String sessionToken, Principal user, String assemblyName)
      throws Exception
   {
      RuntimeViewsheet rvs = sessions.resolve(sessionToken, user);
      String runtimeId = rvs.getID();
      DateComparisonPaneModel model =
         comparisonService.getDateComparison(runtimeId, assemblyName, user);

      Map<String, Object> out = new LinkedHashMap<>();
      out.put("assembly", assemblyName);

      // getDateComparison() always returns a default-populated model for a DateCompareAble
      // assembly, even when no comparison is actually set (e.g. after clear()), so "enabled"
      // cannot be read from model == null.
      boolean enabled = model != null &&
         comparisonService.isDateComparisonEnabled(runtimeId, assemblyName, user);

      // The share-from assembly is worth reporting even when this assembly's own comparison
      // reads as disabled — sharing is exactly the case where this assembly has no comparison
      // of its own and relies entirely on another assembly's.
      String shareFrom = model == null ? null :
         comparisonService.getShare(runtimeId, assemblyName, user).getShareFromAssembly();
      boolean hasShareFrom = shareFrom != null && !shareFrom.isBlank();

      if(!enabled) {
         out.put("enabled", false);

         if(hasShareFrom) {
            out.put("shareFrom", shareFrom);
         }

         return out;
      }

      // model fetched above is this assembly's OWN DateComparisonPaneModel — a default,
      // unset one whenever comparisonShareFrom is set, since a sharing assembly never carries
      // its own DateComparisonInfo (see setDateComparison()). "enabled:true" alone would then
      // report Chart2's own defaults (comparisonOption=VALUE, useFacet=false, ...) as if they
      // were real settings. When sharing, resolve the populated fields through to the share
      // source's actual config instead — DateComparisonUtil.getDateComparison() already
      // performs this resolution (including one level of nested sharing) for every
      // rendering-facing caller.
      if(hasShareFrom) {
         Viewsheet vs = rvs.getViewsheet();
         VSAssembly assembly = vs == null ? null : vs.getAssembly(assemblyName);
         VSAssemblyInfo info = assembly == null ? null : assembly.getVSAssemblyInfo();

         if(info instanceof DateCompareAbleAssemblyInfo) {
            DateComparisonInfo resolved =
               DateComparisonUtil.getDateComparison((DateCompareAbleAssemblyInfo) info, vs);

            if(resolved != null) {
               model = new DateComparisonPaneModel(resolved);
            }
         }
      }

      out.put("enabled", true);
      out.put("comparisonOption", describeComparisonOption(model.getComparisonOption()));
      out.put("useFacet", model.isUseFacet());
      out.put("onlyShowMostRecentDate", model.isOnlyShowMostRecentDate());
      out.put("period", describePeriod(model.getPeriodPaneModel()));
      out.put("interval", describeInterval(model.getIntervalPaneModel()));

      if(hasShareFrom) {
         out.put("shareFrom", shareFrom);
      }

      return out;
   }

   /**
    * Applies a comparison. One {@code sessions.mutate}, so one undo checkpoint.
    *
    * @return a map that, when the comparison forced a bound date dimension's rendering level
    * to change (e.g. a row bound at "month" retargeted to "year" so periods line up), reports
    * the dimension name and the before/after level under {@code retargetedDimension} /
    * {@code retargetedFromLevel} / {@code retargetedToLevel}. Empty when nothing was retargeted.
    * For a chart, also reports {@code chartTypeOverridden}/{@code chartTypeBefore}/
    * {@code chartTypeAfter} when applying the comparison forced the chart's runtime style to
    * change (see {@link #describeChartTypeOverride}). For a chart where the comparison had no
    * effect at all — its date field isn't on x/y, or its chart type doesn't support date
    * comparison to begin with — reports {@code dateComparisonInactive:true} and a {@code reason}
    * (see {@link #describeDateComparisonInactive}) instead of silently returning as if it
    * applied. For a chart where the comparison did apply but a requested {@code useFacet:true}
    * has no rendering effect on it (the comparison's period level already matches its interval
    * granularity, with no value-plus rendering to fall back on either), reports
    * {@code useFacetInapplicable:true} and a {@code reason}
    * (see {@link #describeUseFacetInapplicable}).
    */
   public Map<String, Object> set(String sessionToken, Principal user, String assemblyName,
                                  Comparison comparison, String linkUri) throws Exception
   {
      requireEndAnchor(comparison);
      Map<String, Object> result = new LinkedHashMap<>();

      sessions.mutate(sessionToken, user, (rvs, runtimeId, dispatcher) -> {
         DateComparisonPaneModel model =
            comparisonService.getDateComparison(runtimeId, assemblyName, user);

         if(model == null) {
            throw new IllegalArgumentException(
               "'" + assemblyName + "' does not support date comparison. It needs a date " +
               "dimension in its binding.");
         }

         int beforeChartType = chartRTChartType(rvs, assemblyName);

         apply(model, comparison);
         comparisonService.setDateComparison(runtimeId, assemblyName,
                                            model.toDateComparisonInfo(),
                                            comparison.shareAssembly(), linkUri, user,
                                            dispatcher);
         result.putAll(describeRetargetedDimension(rvs, assemblyName));
         result.putAll(describeChartTypeOverride(rvs, assemblyName, beforeChartType));
         result.putAll(describeDateComparisonInactive(rvs, assemblyName));
         result.putAll(describeUseFacetInapplicable(rvs, assemblyName, comparison));
      });

      return result;
   }

   /**
    * Date comparison forces a bound date dimension's runtime grouping level up (or down) to
    * match the comparison period, so periods actually line up — a deliberate rendering decision,
    * not a bug. But it only ever mutates the runtime clone (design binding is untouched), and
    * nothing told the caller it happened. {@code CrosstabDcProcessor.process()} already stashes
    * the pre-retarget clone on {@code VSCrosstabInfo.getDateComparisonRef()} before mutating the
    * live runtime ref in place; comparing that snapshot's level against the same-named dimension
    * in the (already-refreshed, by the time {@code sessions.mutate} returns) runtime headers is
    * the cheapest way to observe what actually changed, without re-deriving the DC math here.
    *
    * <p>Matches by {@code getName()} (the plain column name), not {@code getFullName()} — the
    * full name embeds the date level itself (e.g. "Month(ORDER_DATE)" vs. "Year(ORDER_DATE)"),
    * so it never matches once the level has actually changed.
    *
    * <p>Searches only the shelf {@code VSCrosstabInfo.isDateComparisonOnRow()} names, not "row,
    * then column" — that flag is set by the very same {@code updateRuntimeHeaders()} call that
    * produces {@code getDateComparisonRef()}, so it is a definitive answer, not a guess. Trying
    * row first and falling back to column would misreport an unrelated, untouched dimension's
    * level if a crosstab happened to bind a same-named date dimension on both shelves.
    *
    * <p>A chart needs the opposite before/after pairing. {@code CrosstabDcProcessor} stashes the
    * pre-retarget clone on {@code getDateComparisonRef()} <i>before</i> mutating the live ref
    * (stash-then-mutate), but {@code ChartDcProcessor.process()} mutates the dimension it found
    * in place first and only clones it into {@code VSChartInfo.getDateComparisonRef()}
    * afterward (mutate-then-stash — see {@code ChartDcProcessor.java} lines 186 then 203..204).
    * So for a chart, {@code getDateComparisonRef()} already holds the <i>after</i> level, not a
    * "before" snapshot; reusing it as "before" the way the crosstab branch does would compare an
    * already-retargeted value against itself and always report nothing. Instead, "before" comes
    * from the untouched design binding — {@code VSChartInfo.getXFields()}/{@code getYFields()},
    * which {@code ChartInfoModelBuilder} never RT-substitutes for a dimension (only for
    * {@code VSChartAggregateRef} measures) — matched by name against the axis
    * {@code VSChartInfo.isDcBaseDateOnX()} says the date dimension actually came from (the same
    * flag {@code ChartDcProcessor.process()} sets before it starts mutating, so — like the
    * crosstab's row/column flag — it is a definitive answer, not a guess).
    */
   private static Map<String, Object> describeRetargetedDimension(RuntimeViewsheet rvs,
                                                                   String assemblyName)
   {
      Map<String, Object> out = new LinkedHashMap<>();
      Viewsheet vs = rvs.getViewsheet();
      VSAssembly assembly = vs == null ? null : vs.getAssembly(assemblyName);

      if(assembly instanceof CrosstabVSAssembly) {
         VSCrosstabInfo crosstabInfo = ((CrosstabVSAssembly) assembly).getVSCrosstabInfo();
         VSDataRef before = crosstabInfo == null ? null : crosstabInfo.getDateComparisonRef();

         if(!(before instanceof VSDimensionRef)) {
            return out;
         }

         VSDimensionRef beforeDim = (VSDimensionRef) before;
         DataRef[] shelf = crosstabInfo.isDateComparisonOnRow() ?
            crosstabInfo.getRuntimeRowHeaders() : crosstabInfo.getRuntimeColHeaders();
         VSDimensionRef afterDim = findDimensionByName(shelf, beforeDim.getName());

         if(afterDim == null || afterDim.getDateLevel() == beforeDim.getDateLevel()) {
            return out;
         }

         out.put("retargetedDimension", beforeDim.getName());
         out.put("retargetedFromLevel", levelWord(beforeDim.getDateLevel()));
         out.put("retargetedToLevel", levelWord(afterDim.getDateLevel()));
         return out;
      }

      if(assembly instanceof ChartVSAssembly) {
         VSChartInfo cinfo = ((ChartVSAssembly) assembly).getVSChartInfo();
         VSDataRef after = cinfo == null ? null : cinfo.getDateComparisonRef();

         if(!(after instanceof VSDimensionRef)) {
            return out;
         }

         VSDimensionRef afterDim = (VSDimensionRef) after;
         DataRef[] designShelf = cinfo.isDcBaseDateOnX() ? cinfo.getXFields() : cinfo.getYFields();
         VSDimensionRef beforeDim = findDimensionByName(designShelf, afterDim.getName());

         if(beforeDim == null || beforeDim.getDateLevel() == afterDim.getDateLevel()) {
            return out;
         }

         out.put("retargetedDimension", afterDim.getName());
         out.put("retargetedFromLevel", levelWord(beforeDim.getDateLevel()));
         out.put("retargetedToLevel", levelWord(afterDim.getDateLevel()));
         return out;
      }

      return out;
   }

   private static VSDimensionRef findDimensionByName(DataRef[] refs, String name) {
      if(refs == null || name == null) {
         return null;
      }

      for(DataRef ref : refs) {
         if(ref instanceof VSDimensionRef && name.equals(((VSDimensionRef) ref).getName())) {
            return (VSDimensionRef) ref;
         }
      }

      return null;
   }

   /**
    * {@code ChartDcProcessor.updateDateComparisonChartType()} unconditionally forces a chart's
    * primary/value series' runtime style to Bar/Bar-Stack whenever a comparison is applied,
    * regardless of the chart's original type — e.g. a Line chart with a group-shelf dimension
    * loses that dimension's only visual-breakdown mechanism (Bar has none) the moment a
    * comparison is set, with nothing reporting it. {@code get_binding} stays "correct"
    * throughout, since the binding itself is never touched — only the runtime chart type is.
    * This compares the runtime chart type read before {@code comparisonService.setDateComparison}
    * ran against the same read afterward, so the disclosure reflects reality regardless of which
    * of {@code ChartDcProcessor}'s branches (plain, multi-style, value-plus) actually fired.
    *
    * <p>Not a chart, or the type did not change: returns an empty map.
    */
   private static Map<String, Object> describeChartTypeOverride(RuntimeViewsheet rvs,
                                                                 String assemblyName,
                                                                 int beforeType)
   {
      Map<String, Object> out = new LinkedHashMap<>();

      if(beforeType == NOT_A_CHART) {
         return out;
      }

      int afterType = chartRTChartType(rvs, assemblyName);

      if(afterType == NOT_A_CHART || afterType == beforeType) {
         return out;
      }

      out.put("chartTypeOverridden", true);
      out.put("chartTypeBefore", GraphTypes.getDisplayName(beforeType));
      out.put("chartTypeAfter", GraphTypes.getDisplayName(afterType));
      return out;
   }

   private static final int NOT_A_CHART = Integer.MIN_VALUE;

   /** The runtime chart type that would be affected by a date-comparison chart-type override. */
   private static int chartRTChartType(RuntimeViewsheet rvs, String assemblyName) {
      Viewsheet vs = rvs.getViewsheet();
      VSAssembly assembly = vs == null ? null : vs.getAssembly(assemblyName);

      if(!(assembly instanceof ChartVSAssembly)) {
         return NOT_A_CHART;
      }

      VSChartInfo cinfo = ((ChartVSAssembly) assembly).getVSChartInfo();
      return cinfo == null ? NOT_A_CHART : effectiveRTChartType(cinfo);
   }

   /**
    * The non-multi-style branch changes {@code VSChartInfo}'s own runtime type; the multi-style
    * branch changes each aggregate's instead (see {@code ChartDcProcessor
    * .updateDateComparisonChartType}) — read whichever one the comparison would actually change.
    */
   private static int effectiveRTChartType(VSChartInfo cinfo) {
      if(!cinfo.isMultiStyles()) {
         return cinfo.getRTChartType();
      }

      for(ChartAggregateRef agg : cinfo.getAestheticAggregateRefs(true)) {
         if(agg != null) {
            return agg.getRTChartType();
         }
      }

      return cinfo.getRTChartType();
   }

   /**
    * {@code ChartDcProcessor} only ever finds "the" date dimension to compare on by searching
    * {@code VSChartInfo.getRTXFields()}/{@code getRTYFields()} — never group/color/shape/text —
    * and {@code DateComparisonUtil.supportDateComparison()} rejects the attempt even earlier for
    * any chart type outside {auto, bar, line, area, interval, point} (a pie's sliced dimension is
    * always forced onto color, so it can never satisfy the x/y search either). Either gate leaves
    * {@code VSChartInfo.getDateComparisonRef()} null — set only inside {@code ChartDcProcessor
    * .process()}'s body, which never runs when a gate fires — with {@link #set} otherwise
    * returning {@code {ok:true}} exactly as if the comparison had applied.
    *
    * <p>The chart-type check here mirrors {@code DateComparisonUtil.supportDateComparison()}'s own
    * inline predicate rather than calling it (that method also runs the x/y field search this
    * helper doesn't need, and short-circuits true once a comparison has ever applied once before,
    * neither of which this disclosure wants) — if that shared engine class's allow-list changes,
    * this predicate needs updating to match.
    *
    * <p>Not a chart, or the comparison actually took effect: returns an empty map.
    */
   private static Map<String, Object> describeDateComparisonInactive(RuntimeViewsheet rvs,
                                                                     String assemblyName)
   {
      Map<String, Object> out = new LinkedHashMap<>();
      Viewsheet vs = rvs.getViewsheet();
      VSAssembly assembly = vs == null ? null : vs.getAssembly(assemblyName);

      if(!(assembly instanceof ChartVSAssembly)) {
         return out;
      }

      VSChartInfo cinfo = ((ChartVSAssembly) assembly).getVSChartInfo();

      if(cinfo == null || cinfo.getDateComparisonRef() != null) {
         return out;
      }

      boolean invalidChartType = GraphTypeUtil.checkType(cinfo, type ->
         !GraphTypes.isAuto(type) && !GraphTypes.isBar(type) && !GraphTypes.isLine(type) &&
         !GraphTypes.isArea(type) && !GraphTypes.isInterval(type) && !GraphTypes.isPoint(type));

      out.put("dateComparisonInactive", true);

      if(invalidChartType) {
         out.put("reason", GraphTypes.getDisplayName(effectiveRTChartType(cinfo)) +
            " charts don't support date comparison — only Bar, Line, Area, Interval, and Point " +
            "(or Auto resolving to one of those) do. Convert the chart type first if a " +
            "comparison is needed.");
      }
      else {
         out.put("reason", "no date-typed field is bound to this chart's x or y axis — date " +
            "comparison (own or shared) only applies to a date dimension on x or y, never " +
            "group, color, shape, or text.");
      }

      return out;
   }

   /**
    * {@code useFacet} is genuinely read by {@code ChartDcProcessor} (unlike a dead flag), but its
    * one axis-placement consumer (the {@code periodRef != null} block, {@code ChartDcProcessor
    * .process():110-165}) only ever creates a {@code periodRef} for a {@code StandardPeriods}
    * comparison when {@code DateComparisonInfo.periodLevelSameAsGranularityLevel()} is false — the
    * common case of a plain N-over-N comparison with no distinct interval breakdown (e.g. a
    * year-over-year comparison with no quarterly/monthly granularity override) makes that method
    * return true, so {@code periodRef} stays null and this whole block — every line that reads
    * {@code useFacet} — never runs. The flag's only other consumer ({@code
    * ChartDcProcessor.updateDateComparisonChartType():806}, choosing Line vs. Point for the
    * secondary "change" series) is gated behind {@code DateComparisonInfo.isValuePlus()}
    * separately, so {@code useFacet} still has an effect there even when the axis-placement
    * consumer is dead — this only reports {@code useFacetInapplicable} when both are inactive.
    *
    * <p>Only meaningful for a chart ({@code useFacet} has no crosstab consumer at all), and only
    * when the comparison itself actually applied to it — {@link #describeDateComparisonInactive}
    * already reports the case where it didn't (wrong chart type, or no date field on x/y), and
    * that message already covers {@code useFacet} having no effect too, for an unrelated reason;
    * reporting both here would be redundant and would misname the cause.
    *
    * <p>Not a chart, {@code useFacet:true} was not requested, or the comparison had (or would
    * still have, via the value-plus consumer) some rendering effect: returns an empty map.
    */
   private static Map<String, Object> describeUseFacetInapplicable(RuntimeViewsheet rvs,
                                                                    String assemblyName,
                                                                    Comparison comparison)
   {
      Map<String, Object> out = new LinkedHashMap<>();

      if(comparison.useFacet() == null || !comparison.useFacet()) {
         return out;
      }

      Viewsheet vs = rvs.getViewsheet();
      VSAssembly assembly = vs == null ? null : vs.getAssembly(assemblyName);

      if(!(assembly instanceof ChartVSAssembly)) {
         return out;
      }

      VSChartInfo cinfo = ((ChartVSAssembly) assembly).getVSChartInfo();

      if(cinfo == null || cinfo.getDateComparisonRef() == null) {
         return out;
      }

      DateComparisonInfo dcInfo = ((ChartVSAssembly) assembly).getChartInfo().getDateComparisonInfo();

      if(dcInfo == null) {
         return out;
      }

      boolean periodAxisPlacementDead = dcInfo.getPeriods() instanceof StandardPeriods &&
         dcInfo.periodLevelSameAsGranularityLevel();

      if(!periodAxisPlacementDead || dcInfo.isValuePlus()) {
         return out;
      }

      out.put("useFacetInapplicable", true);
      out.put("reason", "faceting has no effect when the comparison's period level already " +
         "matches its interval granularity — there is no separate breakdown left to place on " +
         "the opposite axis. A finer interval granularity than the period level (e.g. a " +
         "quarterly breakdown within a yearly comparison) is needed for useFacet to take effect.");
      return out;
   }

   public void clear(String sessionToken, Principal user, String assemblyName, String linkUri)
      throws Exception
   {
      sessions.mutate(sessionToken, user, (rvs, runtimeId, dispatcher) ->
         comparisonService.clearDateComparison(runtimeId, assemblyName, linkUri, user,
                                               dispatcher));
   }

   // ── the end-date guard ────────────────────────────────────────────────────

   /**
    * The recorded defect, refused at the boundary.
    *
    * <p>An explicit end date and "anchor on today" are mutually exclusive, and asking for both
    * is how the end date came to be discarded. Asking for neither is refused too: defaulting to
    * today is exactly the behaviour that produced a wrong range for a forward-looking field.
    */
   static void requireEndAnchor(Comparison comparison) {
      if(comparison == null) {
         throw new IllegalArgumentException("set_date_comparison needs a comparison.");
      }

      if(hasCustomPeriods(comparison)) {
         requireNoStandardPeriodFields(comparison);
         return;
      }

      boolean hasEnd = comparison.endDate() != null && !comparison.endDate().isBlank();

      // The anchor is only required when the period is actually being set. Demanding it on every
      // call meant a caller who wanted nothing but useFacet:true had to invent a period, and the
      // call then rewrote the one already there.
      if(!setsPeriod(comparison)) {
         if(hasEnd || comparison.endToday()) {
            throw new IllegalArgumentException(
               "An end anchor was given without any period to anchor. Pass 'periods' and 'level' " +
               "to set the period, or drop 'endDate'/'endToday' to leave it alone.");
         }

         return;
      }

      if(hasEnd && comparison.endToday()) {
         throw new IllegalArgumentException(
            "'endDate' and 'endToday' cannot both be set. When the range anchors on today the " +
            "end date is discarded, which is how a comparison silently ended today instead of " +
            "where you asked. Pick one.");
      }

      if(!hasEnd && !comparison.endToday()) {
         throw new IllegalArgumentException(
            "set_date_comparison needs either an 'endDate' or endToday:true. It is not " +
            "defaulted, because defaulting to today gives a forward-looking field — a due date, " +
            "say — a range that ends before the data does, and nothing reports it.");
      }

      if(comparison.periods() != null && comparison.periods() < 1) {
         throw new IllegalArgumentException(
            "'periods' must be at least 1, got " + comparison.periods() + ".");
      }
   }

   /** Whether the call asks for any period change at all. */
   private static boolean setsPeriod(Comparison comparison) {
      return comparison.periods() != null
         || comparison.level() != null
         || (comparison.endDate() != null && !comparison.endDate().isBlank())
         || comparison.endToday()
         || comparison.interval() != null
         || comparison.toDate() != null
         || comparison.inclusive() != null
         || hasCustomPeriods(comparison);
   }

   private static boolean hasCustomPeriods(Comparison comparison) {
      return comparison.customPeriods() != null && !comparison.customPeriods().isEmpty();
   }

   /**
    * {@code customPeriods} (StyleBI's {@code CustomPeriods}) and a standard period (StyleBI's
    * {@code StandardPeriods}) are peers behind {@code DateComparisonPeriods}, not one a variant
    * of the other — {@code PeriodPaneModel} can hold only one at a time. {@code interval} is
    * refused too, mirroring the engine's own restriction: {@code
    * DateComparisonInfo.getIntervalConditions()} does not expose interval sub-windows (e.g.
    * month-to-date) for a custom period (ticket 64217), so accepting one here would silently do
    * nothing once applied.
    */
   private static void requireNoStandardPeriodFields(Comparison comparison) {
      if(comparison.periods() != null || comparison.level() != null
         || (comparison.endDate() != null && !comparison.endDate().isBlank())
         || comparison.endToday() || comparison.toDate() != null
         || comparison.inclusive() != null)
      {
         throw new IllegalArgumentException(
            "'customPeriods' cannot be combined with 'periods'/'level'/'endDate'/'endToday'/" +
            "'toDate'/'inclusive' — a custom date-comparison period is a peer to a standard " +
            "period, not composable with one. Pass only 'customPeriods' to set a custom " +
            "period, or drop it to set a standard one.");
      }

      if(comparison.interval() != null) {
         throw new IllegalArgumentException(
            "'interval' cannot be combined with 'customPeriods'. StyleBI's date-comparison " +
            "engine does not expose an interval sub-window (e.g. monthToDate) for a custom " +
            "period. Drop 'interval', or use 'periods'/'level' instead of 'customPeriods'.");
      }
   }

   private static void apply(DateComparisonPaneModel model, Comparison comparison) {
      if(comparison.useFacet() != null) {
         model.setUseFacet(comparison.useFacet());
      }

      if(comparison.onlyShowMostRecentDate() != null) {
         model.setOnlyShowMostRecentDate(comparison.onlyShowMostRecentDate());
      }

      if(comparison.comparisonOption() != null) {
         model.setComparisonOption(normalizeComparisonOption(comparison.comparisonOption()));
      }

      PeriodPaneModel periods = model.getPeriodPaneModel();

      if(periods == null) {
         throw new IllegalArgumentException(
            "This assembly's date comparison has no period pane, so the period cannot be set.");
      }

      // Nothing period-related was asked for, so the period is left exactly as it is. This block
      // used to run unconditionally, which is how a call setting only useFacet converted a custom
      // period to standard and discarded it.
      if(!setsPeriod(comparison)) {
         return;
      }

      // A caller who explicitly lists start/end pairs is deliberately asking for a custom
      // period, unlike the accidental "just set useFacet" case the guard below exists to
      // prevent — so switching an existing standard period to custom here is allowed outright,
      // symmetric with allowing a deliberate switch the other way (see below).
      if(hasCustomPeriods(comparison)) {
         periods.setCustom(true);
         List<DatePeriodModel> datePeriods = new ArrayList<>();

         for(CustomPeriod period : comparison.customPeriods()) {
            DatePeriodModel periodModel = new DatePeriodModel();
            periodModel.setStart(new DynamicValueModel());
            periodModel.setEnd(new DynamicValueModel());
            setDynamic(periodModel.getStart(), period.start());
            setDynamic(periodModel.getEnd(), period.end());
            datePeriods.add(periodModel);
         }

         periods.getCustomPeriodPaneModel().setDatePeriods(datePeriods);
         return;
      }

      if(periods.isCustom()) {
         throw new IllegalArgumentException(
            "This assembly uses a custom date-comparison period, and setting a standard period " +
            "here would discard it with no way back. Clear the comparison first if that is what " +
            "you want, or pass 'customPeriods' instead of 'periods'/'level' to set a new custom " +
            "period.");
      }

      periods.setCustom(false);
      StandardPeriodPaneModel standard = periods.getStandardPeriodPaneModel();

      if(standard == null) {
         throw new IllegalArgumentException(
            "This assembly's date comparison has no standard period pane.");
      }

      if(comparison.periods() != null) {
         setDynamic(standard.getPreCount(), String.valueOf(comparison.periods()));
      }

      if(comparison.level() != null) {
         setDynamic(standard.getDateLevel(), normalizeLevel(comparison.level()));
      }

      if(comparison.toDate() != null) {
         standard.setToDate(comparison.toDate());
      }

      if(comparison.inclusive() != null) {
         standard.setInclusive(comparison.inclusive());
      }

      // Setting the end date clears the today anchor, because leaving it set is what discarded
      // the date.
      if(comparison.endToday()) {
         standard.setToDayAsEndDay(true);
      }
      else {
         standard.setToDayAsEndDay(false);
         setDynamic(standard.getEndDay(), comparison.endDate());
      }

      IntervalPaneModel interval = model.getIntervalPaneModel();

      if(comparison.interval() != null && interval != null) {
         setDynamic(interval.getLevel(), normalizeInterval(comparison.interval()));
      }
   }

   private static final Map<String, Integer> LEVEL_WORDS = Map.of(
      "year", XConstants.YEAR_DATE_GROUP,
      "quarter", XConstants.QUARTER_DATE_GROUP,
      "month", XConstants.MONTH_DATE_GROUP,
      "week", XConstants.WEEK_DATE_GROUP,
      "day", XConstants.DAY_DATE_GROUP
   );

   /**
    * Translates the agent vocabulary's period-level word to the numeric
    * {@code XConstants.*_DATE_GROUP} code every Angular consumer of {@code standardPeriodLevel}
    * expects. Writing the raw word through untranslated is what emptied
    * {@code date-comparison-interval-pane.component.ts}'s granularities list and crashed the
    * Date Comparison editor.
    */
   private static String normalizeLevel(String level) {
      Integer code = LEVEL_WORDS.get(level.trim().toLowerCase());

      if(code == null) {
         throw new IllegalArgumentException(
            "'level' must be one of: year, quarter, month, week, day. Got '" + level + "'.");
      }

      return code.toString();
   }

   private static final Map<Integer, String> LEVEL_NAMES = Map.of(
      XConstants.YEAR_DATE_GROUP, "year",
      XConstants.QUARTER_DATE_GROUP, "quarter",
      XConstants.MONTH_DATE_GROUP, "month",
      XConstants.WEEK_DATE_GROUP, "week",
      XConstants.DAY_DATE_GROUP, "day"
   );

   /** The inverse of {@link #normalizeLevel(String)}, for reporting a level back to the caller. */
   private static String levelWord(int level) {
      return LEVEL_NAMES.getOrDefault(level, String.valueOf(level));
   }

   private static final Map<String, Integer> COMPARISON_OPTION_WORDS = Map.of(
      "value", Calculator.VALUE,
      "change", Calculator.CHANGE,
      "percentchange", Calculator.PERCENT,
      "changeandvalue", DateComparisonInfo.CHANGE_VALUE,
      "percentchangeandvalue", DateComparisonInfo.PERCENT_VALUE
   );

   /**
    * Translates the agent vocabulary's comparison-option word to
    * {@code DateComparisonPaneModel#setComparisonOption(int)}'s expected int. The Angular dialog
    * shows 5 options (Value Only / Change / Change and Value / Percent Change / Percent Change
    * and Value), one flat int each — {@link Calculator}'s VALUE/CHANGE/PERCENT constants for the
    * first three, and {@link DateComparisonInfo}'s CHANGE_VALUE/PERCENT_VALUE constants (101/102)
    * for the combined two. There is no separate "also show value" flag to set alongside a
    * 3-value enum; the combined options are their own int.
    */
   private static int normalizeComparisonOption(String comparisonOption) {
      Integer code = COMPARISON_OPTION_WORDS.get(comparisonOption.trim().toLowerCase());

      if(code == null) {
         throw new IllegalArgumentException(
            "'comparisonOption' must be one of: value, change, percentChange, changeAndValue, " +
            "percentChangeAndValue. Got '" + comparisonOption + "'.");
      }

      return code;
   }

   /**
    * The inverse of {@link #COMPARISON_OPTION_WORDS}. Not reachable through this dialog in
    * practice ({@link Calculator}'s other constants — RUNNINGTOTAL/MOVING/CUSTOM/COMPOUNDGROWTH —
    * never end up on a date-comparison model), but a comparisonOption outside even this wider
    * read-side vocabulary is reported as {@code null} rather than a bare int — this service does
    * not echo raw magic numbers to the caller.
    */
   private static final Map<Integer, String> COMPARISON_OPTION_NAMES = Map.of(
      Calculator.VALUE, "value",
      Calculator.CHANGE, "change",
      Calculator.PERCENT, "percentChange",
      DateComparisonInfo.CHANGE_VALUE, "changeAndValue",
      DateComparisonInfo.PERCENT_VALUE, "percentChangeAndValue"
   );

   /** The inverse of {@link #normalizeComparisonOption(String)}, for reporting it back. */
   private static String describeComparisonOption(int comparisonOption) {
      return COMPARISON_OPTION_NAMES.get(comparisonOption);
   }

   private static final Map<String, Integer> INTERVAL_WORDS = Map.of(
      "all", DateComparisonInfo.ALL,
      "yeartodate", DateComparisonInfo.YEAR_TO_DATE,
      "quartertodate", DateComparisonInfo.QUARTER_TO_DATE,
      "monthtodate", DateComparisonInfo.MONTH_TO_DATE,
      "weektodate", DateComparisonInfo.WEEK_TO_DATE,
      "samequarter", DateComparisonInfo.SAME_QUARTER,
      "samemonth", DateComparisonInfo.SAME_MONTH,
      "sameweek", DateComparisonInfo.SAME_WEEK,
      "sameday", DateComparisonInfo.SAME_DAY
   );

   /**
    * Translates the agent vocabulary's interval-level word to the {@code DateComparisonInfo}
    * bitmask {@code date-comparison-interval-pane.component.ts}'s {@code intervalLevels} dropdown
    * sends. Mirrors {@link #normalizeLevel(String)} — writing the raw word through untranslated
    * is the same class of defect that method exists to prevent.
    */
   private static String normalizeInterval(String interval) {
      Integer code = INTERVAL_WORDS.get(interval.trim().toLowerCase().replace(" ", ""));

      if(code == null) {
         throw new IllegalArgumentException(
            "'interval' must be one of: all, yearToDate, quarterToDate, monthToDate, " +
            "weekToDate, sameQuarter, sameMonth, sameWeek, sameDay. Got '" + interval + "'.");
      }

      return code.toString();
   }

   private static void setDynamic(DynamicValueModel target, String value) {
      if(target == null) {
         throw new IllegalArgumentException(
            "This assembly's date comparison does not expose that setting.");
      }

      target.setValue(value);
   }

   private static final Map<Integer, String> INTERVAL_NAMES = Map.of(
      DateComparisonInfo.ALL, "all",
      DateComparisonInfo.YEAR_TO_DATE, "yearToDate",
      DateComparisonInfo.QUARTER_TO_DATE, "quarterToDate",
      DateComparisonInfo.MONTH_TO_DATE, "monthToDate",
      DateComparisonInfo.WEEK_TO_DATE, "weekToDate",
      DateComparisonInfo.SAME_QUARTER, "sameQuarter",
      DateComparisonInfo.SAME_MONTH, "sameMonth",
      DateComparisonInfo.SAME_WEEK, "sameWeek",
      DateComparisonInfo.SAME_DAY, "sameDay"
   );

   /** The inverse of {@link #normalizeInterval(String)}, for reporting it back. */
   private static String intervalWord(int interval) {
      return INTERVAL_NAMES.getOrDefault(interval, String.valueOf(interval));
   }

   // ── read normalization ────────────────────────────────────────────────────

   private static Map<String, Object> describePeriod(PeriodPaneModel periods) {
      Map<String, Object> out = new LinkedHashMap<>();

      if(periods == null) {
         return out;
      }

      out.put("custom", periods.isCustom());

      if(periods.isCustom()) {
         out.put("customPeriods", describeCustomPeriods(periods.getCustomPeriodPaneModel()));
         return out;
      }

      StandardPeriodPaneModel standard = periods.getStandardPeriodPaneModel();

      if(standard != null) {
         out.put("periods", value(standard.getPreCount()));
         out.put("level", describeCode(standard.getDateLevel(), DateComparisonService::levelWord));
         out.put("endToday", standard.isToDayAsEndDay());
         out.put("endDate", standard.isToDayAsEndDay() ? null : value(standard.getEndDay()));
         out.put("inclusive", standard.isInclusive());
         out.put("toDate", standard.isToDate());
      }

      return out;
   }

   /**
    * The custom-period peer to the standard-period fields above — an assembly whose comparison
    * was built via the manual UI's Custom Periods tab has no {@code preCount}/{@code dateLevel}/
    * etc. at all, so this reads back the actual {@code start}/{@code end} pairs instead.
    */
   private static List<Map<String, Object>> describeCustomPeriods(CustomPeriodPaneModel custom) {
      List<Map<String, Object>> out = new ArrayList<>();

      if(custom == null || custom.getDatePeriods() == null) {
         return out;
      }

      for(DatePeriodModel period : custom.getDatePeriods()) {
         if(period == null) {
            continue;
         }

         Map<String, Object> entry = new LinkedHashMap<>();
         entry.put("start", value(period.getStart()));
         entry.put("end", value(period.getEnd()));
         out.add(entry);
      }

      return out;
   }

   private static Map<String, Object> describeInterval(IntervalPaneModel interval) {
      Map<String, Object> out = new LinkedHashMap<>();

      if(interval == null) {
         return out;
      }

      out.put("level", describeCode(interval.getLevel(), DateComparisonService::intervalWord));
      out.put("granularity", value(interval.getGranularity()));
      out.put("endDayAsToDate", interval.isEndDayAsToDate());
      out.put("inclusive", interval.isInclusive());
      return out;
   }

   private static Object value(DynamicValueModel model) {
      return model == null ? null : model.getValue();
   }

   /**
    * Reads a dynamic value written by {@link #normalizeLevel(String)}/{@link
    * #normalizeInterval(String)} back as the word an agent caller understands, mirroring {@link
    * #describeComparisonOption(int)}. Written but never wired up when {@code level}/{@code
    * interval}'s write-side word-to-code normalization first landed -- {@code get_date_comparison}
    * kept reporting the raw StyleBI numeric/bitmask code, asymmetric with comparisonOption's own
    * two-way translation. Falls back to the raw stored string for a non-numeric dynamic value
    * (a formula/expression) or a code the map does not recognize, rather than throwing --  a
    * DynamicValueModel is not guaranteed to hold a plain int literal.
    */
   private static Object describeCode(DynamicValueModel model, java.util.function.IntFunction<String> word) {
      if(model == null) {
         return null;
      }

      Object rawValue = model.getValue();

      if(rawValue == null) {
         return null;
      }

      String raw = rawValue.toString();

      try {
         return word.apply(Integer.parseInt(raw.trim()));
      }
      catch(NumberFormatException e) {
         return raw;
      }
   }

   private final ViewsheetSessionService sessions;
   private final DateComparisonDialogService comparisonService;
}
