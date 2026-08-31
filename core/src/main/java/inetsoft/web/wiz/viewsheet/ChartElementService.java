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

import inetsoft.uql.viewsheet.ChartVSAssembly;
import inetsoft.uql.viewsheet.VSAssembly;
import inetsoft.uql.viewsheet.Viewsheet;
import com.fasterxml.jackson.databind.ObjectMapper;
import inetsoft.graph.VGraph;
import inetsoft.graph.coord.Coordinate;
import inetsoft.graph.coord.RelationCoord;
import inetsoft.graph.internal.GTool;
import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.report.composition.execution.ViewsheetSandbox;
import inetsoft.report.composition.graph.*;
import inetsoft.report.composition.region.ChartArea;
import inetsoft.uql.viewsheet.graph.GraphTypes;
import inetsoft.uql.viewsheet.graph.VSChartInfo;
import inetsoft.web.viewsheet.controller.chart.*;
import inetsoft.web.viewsheet.event.chart.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.awt.geom.Rectangle2D;
import java.security.Principal;
import java.util.*;

/**
 * A chart's sub-elements: axis, legend and title visibility, and plot sizing.
 *
 * <p>These are authoring operations rather than runtime exploration — they clone
 * {@code ChartVSAssemblyInfo} and mutate its descriptors, the same shape as every other
 * property write — so they belong with the viewsheet plugin rather than being excluded as
 * interactive analysis.
 *
 * <p><b>The underlying events have a footgun this class hides.</b> {@code isHide()} does not
 * mean "hide it": on the titles event, {@code hide=true} with {@code titleType="chart-title"}
 * hides the chart title, {@code hide=true} with {@code titleType="chart-title-true"}
 * <i>shows</i> it, and {@code hide=false} shows <i>all</i> titles regardless of which one you
 * named. An agent asked to "show the y axis title" that set {@code hide=false} would silently
 * un-hide every title the user had deliberately hidden.
 *
 * <p>So the agent-facing surface is {@code (element, target, visible)} and this class maps it
 * onto whatever combination the event actually needs.
 *
 * <p><b>The events are built with Jackson, not setters.</b> They expose getters over private
 * fields and declare no setters at all — Jackson reaches the field once the getter reveals the
 * property, which is exactly how the STOMP layer populates them in production. Building them
 * any other way would either need reflection or a parallel constructor this package does not
 * own. {@code ChartElementServiceTest} asserts the conversion actually lands its values,
 * because an all-default titles event means "show every title", which is the destructive case.
 */
@Service
public class ChartElementService {
   /** Titles addressable by name, mapped to the descriptor token the event expects. */
   private static final Map<String, String> AXIS_TITLES = Map.of(
      "x", "x_title",
      "x2", "x2_title",
      "y", "y_title",
      "y2", "y2_title");

   @Autowired
   public ChartElementService(ViewsheetSessionService sessions,
                              ObjectMapper objectMapper,
                              VSChartAxesVisibilityService axesService,
                              VSChartLegendsVisibilityService legendsService,
                              VSChartTitlesVisibilityService titlesService,
                              VSChartPlotResizeService plotResizeService)
   {
      this.sessions = sessions;
      this.objectMapper = objectMapper;
      this.axesService = axesService;
      this.legendsService = legendsService;
      this.titlesService = titlesService;
      this.plotResizeService = plotResizeService;
   }

   /**
    * Shows or hides one of a chart's sub-elements.
    *
    * @param element {@code axis}, {@code legend} or {@code title}
    * @param target  which one — an axis column name, a legend field, or an axis title
    *                ({@code x}, {@code x2}, {@code y}, {@code y2}) or {@code chart} for the
    *                chart title. Null, or blank, means all of that element.
    */
   public void setVisibility(String sessionToken, Principal user, String assemblyName,
                             String element, String target, boolean visible, String linkUri)
      throws Exception
   {
      String kind = requireElement(element);
      // A blank target is no target, and the two layers have to agree on that: the tool's own
      // show-with-a-target guard already treats "" as absent, so a server that read it as present
      // refused the very call the tool had let through, and on the legend path the refusal named
      // an empty legend. Not trimmed otherwise - only the blank case is unambiguous, and a column
      // name's own spacing is its own business.
      String target0 = target != null && target.isBlank() ? null : target;

      // Resolved before the runtime is touched: "show the y title" and "show every title" are
      // different requests, and the event cannot express the first one, so a caller naming a
      // target it cannot honour has to be told rather than quietly given the second.
      if(visible && target0 != null && !"title".equals(kind)) {
         throw new IllegalArgumentException(
            "Showing a single " + kind + " is not supported by the Composer — showing restores " +
            "all of them at once. Call this without 'target' to show every " + kind + ", or " +
            "hide the ones you do not want.");
      }

      // Resolved before the runtime is mutated, for the same reason as the guard above and by the
      // same route ChartRegionPropertyService takes for its own refusals: sessions.mutate
      // checkpoints and broadcasts in a finally, deliberately, because a composer service can
      // partially apply before it ERRORs. Nothing is applied when the resolution itself refuses,
      // so refusing inside the mutation would spend an undo step and a Composer refresh on a call
      // that changed nothing.
      VSChartLegendsVisibilityEvent legendEvent = "legend".equals(kind)
         ? legendEvent(sessionToken, user, assemblyName, target0, visible) : null;

      sessions.mutate(sessionToken, user, (rvs, runtimeId, dispatcher) -> {
         requireChart(rvs, assemblyName);

         switch(kind) {
            case "axis" -> axesService.eventHandler(
               runtimeId,
               event(Map.of("chartName", assemblyName, "hide", !visible), "columnName", target0,
                     VSChartAxesVisibilityEvent.class),
               linkUri, user, dispatcher);
            case "legend" -> legendsService.eventHandler(
               runtimeId, legendEvent, linkUri, user, dispatcher);
            default -> titlesService.eventHandler(
               runtimeId,
               convert(titleFields(assemblyName, target0, visible),
                       VSChartTitlesVisibilityEvent.class),
               linkUri, user, dispatcher);
         }
      });
   }

   /**
    * Resizes the plot area, or resets it.
    *
    * <p>The ratio <em>scales the plot's minimum size</em> — {@code VGraphPair} applies it as
    * {@code minPlotHeight *= heightRatio} — so a large enough ratio enlarges the plot and makes
    * the assembly scroll. <b>The threshold is the chart's own {@code initialRatio}, not 1</b>:
    * {@code VGraphPair} re-derives the ratio only while {@code percent = ratio / initialRatio} is
    * at least 1, so on a chart whose baseline is 2.75 a ratio of 2 is stored and inert.
    *
    * <p><b>The range is now enforced.</b> See {@link #requireRatioInRange} — it was enforced
    * only by the Composer's resize slider, which this tool does not go through.
    *
    * <p>This was originally documented and validated as "the plot's share of the assembly, 0 to
    * 1", which is not what the underlying event does. Because the validation enforced that range,
    * the only values the tool accepted were the ones that do nothing — the tool returned success
    * and never changed a pixel.
    *
    * @param ratio    scale for the plot's minimum size; must be within the chart's own
    *                 {@code initialRatio}..{@code max*Ratio} range
    * @param vertical true to resize the height, false the width
    */
   public void resizePlot(String sessionToken, Principal user, String assemblyName, Double ratio,
                          boolean vertical, boolean reset, String linkUri) throws Exception
   {
      if(!reset && (ratio == null || ratio <= 0 || ratio > MAX_PLOT_RATIO)) {
         throw new IllegalArgumentException(
            "resize_plot needs a 'ratio' greater than 0 and at most " + (int) MAX_PLOT_RATIO +
            " — it scales the plot's minimum size, so a value above 1 enlarges the plot and makes " +
            "it scroll, while 1 or less usually changes nothing visible. Got " + ratio +
            ". Pass reset:true to restore the default instead.");
      }

      // Resolved before the runtime is mutated, like legendEvent in setVisibility and for the
      // same reason: the mutation checkpoints and broadcasts in a finally, so a refusal raised
      // inside it would spend an undo step and a Composer refresh on a call that changed nothing.
      if(!reset) {
         requireRatioInRange(sessionToken, user, assemblyName, ratio, vertical);
      }

      sessions.mutate(sessionToken, user, (rvs, runtimeId, dispatcher) -> {
         requireChart(rvs, assemblyName);

         Map<String, Object> fields = new LinkedHashMap<>();
         fields.put("chartName", assemblyName);
         fields.put("reset", reset);
         fields.put("heightResized", vertical);
         fields.put("sizeRatio", reset || ratio == null ? 0d : ratio);
         plotResizeService.eventHandler(runtimeId, convert(fields, VSChartPlotResizeEvent.class),
                                        linkUri, user, dispatcher);
      });
   }

   /**
    * Reads the plot's current sizing, so a {@link #resizePlot} call can be verified at all.
    *
    * <p>This exists because the write had <em>no</em> observable. The ratio scales the plot's
    * <em>minimum</em> size, which a browser expresses as scrollbars inside a fixed-size assembly,
    * while the agent-facing render asks for a graph fitted to the requested box
    * ({@code ScriptImageService} passes the target box as both size and max size) - so an
    * enlarged plot and a default one produce byte-identical images. Plot size is also absent from
    * the chart's property dialog model and from its scriptable API, so before this method a plot
    * resize could not be confirmed through any channel the agent has.
    *
    * <p>Two groups come back and they answer different questions.
    *
    * <p>The <b>ratios</b> are the stored intent: {@code ratio} is what {@link #resizePlot} wrote,
    * {@code resized} is the flag that gates it in {@code VGraphPair} (a ratio stored with this
    * false is inert), and {@code percent} is what {@code VGraphPair} re-applies the ratio from -
    * it only does so while that value is {@code >= 1}, which is what decides whether a resize
    * survives the next layout.
    *
    * <p>The <b>geometry</b> is what the last layout produced, and it is weaker evidence than the
    * first version of this javadoc claimed. Verified live: a resize does not itself relayout, so
    * {@code getVGraphPair} hands back a cached pair and {@code plot}/{@code expandedPlot}/
    * {@code minPlot} did not move even with {@code percent} at 1.48. An expanded plot larger than
    * the real-size one does prove an enlargement is live; equal sizes prove only that this graph
    * has not been rebuilt with it.
    *
    * <p>Also verified live, and the reason {@code percent} is the field to compare:
    * {@code ratio} and {@code initialRatio} are recomputed from whatever box was laid out last
    * ({@code VGraphPair} does {@code setUnitHeightRatio(initialHeightRatio * percent)}), so a
    * written 8 read back as 26.86 after one full-size render while {@code percent} stayed 1.48.
    * And {@code resizePlot}'s reset clears the ratios and flags but <b>not</b> the percents, so a
    * reset chart still reports the old ratio and re-derives it on the next layout - judge a reset
    * by {@code resized}/{@code default}.
    *
    * <p>{@code scrollable}/{@code vScrollable}/{@code hScrollable} are the same
    * {@code GraphUtil} calls the Composer uses to decide whether to draw scrollbars. They are
    * reported because they are the user-visible effect, but they are <b>not</b> an independent
    * check: for an ordinary chart {@code isVScrollable} falls through to
    * {@code info.isHeightResized()}, so it largely restates the flag. The geometry is the
    * stronger oracle.
    *
    * <p>Geometry needs a laid-out graph, which a chart with no data or no sandbox does not have.
    * That case returns the ratios plus {@code geometryUnavailable} naming why, rather than nulls
    * that would read as zero-sized.
    */
   public Map<String, Object> readPlotSize(String sessionToken, Principal user,
                                           String assemblyName)
      throws Exception
   {
      return sessions.read(sessionToken, user, (rvs, runtimeId, dispatcher) -> {
         ChartVSAssembly chart = requireChart(rvs, assemblyName);
         VSChartInfo info = chart.getVSChartInfo();
         Map<String, Object> result = new LinkedHashMap<>();

         result.put("assembly", assemblyName);
         result.put("default", !info.isWidthResized() && !info.isHeightResized());
         result.put("width", ratios(info.getUnitWidthRatio(), info.getUnitWidthRatioPercent(),
                                    info.getInitialWidthRatio(), info.getEffectiveWidthRatio(),
                                    info.isWidthResized()));
         result.put("height", ratios(info.getUnitHeightRatio(), info.getUnitHeightRatioPercent(),
                                     info.getInitialHeightRatio(),
                                     info.getEffectiveHeightRatio(), info.isHeightResized()));
         result.put("toolMaxRatio", MAX_PLOT_RATIO);

         ViewsheetSandbox box = rvs.getViewsheetSandbox().orElse(null);

         if(box == null) {
            result.put("geometryUnavailable",
                       "the viewsheet has no sandbox, so no graph has been laid out");
            return result;
         }

         VGraphPair pair = vgraphPair(box, chart);
         VGraph real = pair == null ? null : pair.getRealSizeVGraph();

         if(real == null) {
            result.put("geometryUnavailable", pair == null
               ? "no graph pair exists for this chart yet"
               : "the chart has no laid-out graph - it is unbound, empty, or still computing");
            return result;
         }

         VGraph expanded = pair.getExpandedVGraph();

         result.put("plot", size(real.getPlotBounds()));
         result.put("graph", size(real.getBounds()));
         result.put("expandedPlot", size(expanded.getPlotBounds()));
         result.put("expanded", expanded != real);
         result.put("minPlot", Map.of("width", round(real.getMinPlotWidth()),
                                      "height", round(real.getMinPlotHeight())));
         result.put("scrollable", GraphUtil.isScrollable(real, info));
         result.put("vScrollable", GraphUtil.isVScrollable(real, info));
         result.put("hScrollable", GraphUtil.isHScrollable(real, info));
         result.putAll(maxRatios(real, info));

         return result;
      });
   }

   /** One axis' worth of ratio state, in the order a reader needs it. */
   private static Map<String, Object> ratios(double ratio, double percent, double initial,
                                             double effective, boolean resized)
   {
      Map<String, Object> map = new LinkedHashMap<>();
      map.put("ratio", round(ratio));
      map.put("resized", resized);
      map.put("percent", round(percent));
      map.put("initialRatio", round(initial));
      map.put("effectiveRatio", round(effective));
      return map;
   }

   /**
    * How far the plot can legitimately be enlarged, by the same formula the Composer's own
    * chart-areas command sends to the browser - so a caller is not left guessing against this
    * class' blunt {@link #MAX_PLOT_RATIO} cap, which is a typo guard rather than a real limit.
    */
   private static Map<String, Object> maxRatios(VGraph graph, VSChartInfo info) {
      Coordinate coord = graph.getCoordinate();
      double maxWidth;
      double maxHeight;

      if(coord instanceof RelationCoord || GraphTypeUtil.isWordCloud(info) ||
         GraphTypeUtil.isDotPlot(info))
      {
         // these scale both directions together, and the Composer caps them at 5
         maxWidth = maxHeight = 5;
      }
      else if(coord != null) {
         maxWidth = info.getInitialWidthRatio() *
            GTool.getUnitCount(coord, Coordinate.BOTTOM_AXIS, false);
         maxHeight = info.getInitialHeightRatio() *
            GTool.getUnitCount(coord, Coordinate.LEFT_AXIS, false);
      }
      else {
         return Map.of();
      }

      return Map.of("maxWidthRatio", round(maxWidth), "maxHeightRatio", round(maxHeight));
   }

   /**
    * Refuses a ratio outside the range the Composer's own resize slider can produce.
    *
    * <p><b>The range was enforced by a widget, not by the server.</b> The slider is drawn with
    * {@code min=initialRatio} and {@code max=maxHorizontalResize/maxVerticalResize}
    * (vs-chart.component.html), while {@link VSChartPlotResizeService} validates nothing at all -
    * it writes whatever {@code sizeRatio} arrives. A person dragging that slider cannot leave the
    * range, so this tool, which posts the event directly, was the first caller able to.
    *
    * <p>Both ends were reachable and neither was reported. <b>Below {@code initialRatio}</b> the
    * write is stored and inert: {@code VGraphPair} re-derives the ratio only while
    * {@code percent = ratio / initialRatio} is at least 1, so the call returned success and
    * changed nothing - the silent-degradation shape {@link #readPlotSize} exists to make visible.
    * <b>Above the maximum</b> it does take effect, but it lands the sheet in a state no Composer
    * user can reach: past that point every axis unit already has a full unit of space, so a
    * larger ratio only adds empty space.
    *
    * <p><b>Read through its own session read</b>, like {@link #legendEvent}, so a refusal costs no
    * undo step - see the note at that method.
    *
    * <p>The maximum needs the laid-out graph, so a chart that has none - unbound, empty, still
    * computing - is checked against the lower bound alone rather than being refused for a bound
    * that cannot be computed. The lower bound never needs the graph: {@code initialRatio} is on
    * the chart info.
    *
    * <p><b>This guards the agent path only.</b> {@code VSChartPlotResizeService} is still
    * unvalidated. It is reachable today from here and from the slider, and both now bound it, so
    * nothing out of range can be written - but any new caller added to that service must carry
    * the same check or the gap reopens.
    */
   private void requireRatioInRange(String sessionToken, Principal user, String assemblyName,
                                    double ratio, boolean vertical)
      throws Exception
   {
      sessions.read(sessionToken, user, (rvs, runtimeId, dispatcher) -> {
         ChartVSAssembly chart = requireChart(rvs, assemblyName);
         VSChartInfo info = chart.getVSChartInfo();

         // No chart info means neither bound exists to check against - no baseline and no
         // maximum - so the ratio falls through to the blunt 0 < ratio <= MAX_PLOT_RATIO guard
         // rather than being refused for bounds that cannot be computed, the same stance this
         // method takes for a chart with no laid-out graph.
         if(info == null) {
            return null;
         }

         // VSChartPlotResizeService applies one ratio to BOTH directions for these, dividing it by
         // each direction's own initial ratio - so both bounds have to hold. Checking only the
         // direction the caller named would let half of such a write through inert.
         boolean square = GraphTypeUtil.isWordCloud(info) ||
            info.getChartType() == GraphTypes.CHART_CIRCULAR;
         boolean width = square || !vertical;
         boolean height = square || vertical;

         if(width) {
            requireAtLeastInitial(ratio, info.getInitialWidthRatio(), "width");
         }

         if(height) {
            requireAtLeastInitial(ratio, info.getInitialHeightRatio(), "height");
         }

         VGraph real = laidOutGraph(rvs, chart);

         if(real != null) {
            Map<String, Object> max = maxRatios(real, info);

            if(width) {
               requireAtMost(ratio, max.get("maxWidthRatio"), "width");
            }

            if(height) {
               requireAtMost(ratio, max.get("maxHeightRatio"), "height");
            }
         }

         return null;
      });
   }

   /** The chart's laid-out graph, or null when there is none to measure. */
   private static VGraph laidOutGraph(RuntimeViewsheet rvs, ChartVSAssembly chart)
      throws Exception
   {
      ViewsheetSandbox box = rvs.getViewsheetSandbox().orElse(null);

      if(box == null) {
         return null;
      }

      VGraphPair pair = vgraphPair(box, chart);

      return pair == null ? null : pair.getRealSizeVGraph();
   }

   /**
    * The chart's graph pair, read under the sandbox's read lock.
    *
    * <p>One copy of the lock/unlock dance for the two callers that need it: {@link #readPlotSize},
    * which turns each of the three ways this can come back empty into its own
    * {@code geometryUnavailable} message, and {@link #laidOutGraph}, which only needs to know
    * whether there is a graph to measure.
    */
   private static VGraphPair vgraphPair(ViewsheetSandbox box, ChartVSAssembly chart)
      throws Exception
   {
      box.lockRead();

      try {
         return box.getVGraphPair(chart.getAbsoluteName());
      }
      finally {
         box.unlockRead();
      }
   }

   /** Below the baseline the write is accepted and inert, which is worse than a refusal. */
   private static void requireAtLeastInitial(double ratio, double initial, String direction) {
      if(ratio < initial) {
         throw new IllegalArgumentException(
            "resize_plot's 'ratio' of " + round(ratio) + " is below this chart's " + direction +
            " baseline of " + round(initial) + ", so it would be stored and change nothing - the " +
            "layout re-applies a resize only while ratio / baseline is at least 1. Pass at least " +
            round(initial) + " to enlarge the plot, or reset:true to restore the default size.");
      }
   }

   /** Past the maximum a resize only adds empty space, and no Composer user can get there. */
   private static void requireAtMost(double ratio, Object max, String direction) {
      if(max instanceof Number bound && ratio > bound.doubleValue()) {
         throw new IllegalArgumentException(
            "resize_plot's 'ratio' of " + round(ratio) + " is above this chart's " + direction +
            " maximum of " + bound + ", which is as far as the Composer's own resize slider goes " +
            "- every axis unit already has a full unit of space there, so a larger ratio only " +
            "adds empty space. Pass " + bound + " or less.");
      }
   }

   private static Map<String, Object> size(Rectangle2D bounds) {
      if(bounds == null) {
         return Map.of();
      }

      return Map.of("width", round(bounds.getWidth()), "height", round(bounds.getHeight()));
   }

   /** Pixel and ratio values carry no meaning past 2 decimals, and full doubles read as noise. */
   private static double round(double value) {
      return Math.round(value * 100d) / 100d;
   }

   /**
    * The vocabulary, narrowed to one chart's real axes when an assembly is named.
    *
    * <p>Without an assembly this is the flat vocabulary it always was — every element and every
    * title target, the same for every chart in the product. That is kept because it is a valid
    * question ("what can this tool address at all") and because it needs no runtime.
    *
    * <p>With an assembly, the title targets are filtered to the axes the chart actually has and
    * {@code axes} names them, so the caller is no longer offered a {@code y2} on a chart with one
    * measure. {@code axesBasis} says whether that came from the laid-out graph or was inferred
    * from the binding, because the two are not equally trustworthy and hiding the difference is
    * how a plausible-but-wrong answer gets believed.
    *
    * <p>{@code legends} names the chart's legends for the same reason, and it is the answer to a
    * different question than {@code get_chart_aesthetics}: that reports which channels are
    * <em>bound</em>, while this reports which legends are <em>rendered</em>, which is what the
    * visibility and region tools can address.
    */
   public Map<String, Object> vocabulary(String sessionToken, Principal user, String assembly)
      throws Exception
   {
      if(assembly == null || assembly.isBlank()) {
         return vocabulary();
      }

      // One laid-out pair for both answers, via regions() rather than the two single-answer
      // entry points: those fetch the pair each, so the axes and the legends could describe two
      // layouts either side of a relayout while being reported as one chart's.
      ChartRegionResolver.Regions regions = sessions.read(
         sessionToken, user,
         (rvs, runtimeId, dispatcher) ->
            ChartRegionResolver.regions(rvs, ChartRegionResolver.requireChart(rvs, assembly)));

      ChartRegionResolver.Axes axes = regions.axes();
      Map<String, Object> out = new LinkedHashMap<>(vocabulary());
      List<String> titleTargets = new ArrayList<>(axes.ordered());
      // The chart title is not an axis title and does not depend on any axis existing.
      titleTargets.add("chart");

      out.put("assembly", assembly);
      out.put("axes", axes.ordered());
      out.put("titleTargets", titleTargets);
      out.put("axesBasis", axes.basis());
      out.put("axesMeasured", axes.measured());
      out.put("legends", describe(regions.legends()));
      out.put("legendsMeasured", regions.legends().measured());
      return out;
   }

   /**
    * The legends as targets a caller can name.
    *
    * <p>Reported for the same reason the axes are: the write now refuses a legend the chart does
    * not have, and a refusal with nowhere to look it up would just move the guessing. The
    * {@code index} is the legend's own position, which is what the region property tools address,
    * so the two vocabularies line up here rather than in the caller's head.
    */
   private static List<Map<String, Object>> describe(ChartRegionResolver.Legends legends) {
      List<Map<String, Object>> out = new ArrayList<>();

      for(int i = 0; i < legends.count(); i++) {
         ChartRegionResolver.LegendTarget legend = legends.legends().get(i);
         Map<String, Object> one = new LinkedHashMap<>();
         one.put("index", i);

         // Omitted rather than reported as empty, both of them and on the same rule: a legend
         // with no describable frame has no field and no channel, so neither can be named as a
         // target, and an empty string reads like one that can. The entry stays, because its
         // index is addressable even when nothing else about it is.
         if(legend.aestheticType() != null) {
            one.put("channel", legend.channel());
         }

         if(legend.field() != null) {
            one.put("field", legend.field());
         }

         out.add(one);
      }

      return out;
   }

   public Map<String, Object> vocabulary() {
      return Map.of(
         "elements", List.of("axis", "legend", "title"),
         "titleTargets", List.of("x", "x2", "y", "y2", "chart"),
         // Spelled out because the same word means two things across two tools: here a target
         // names the column whose axis is hidden, while set_chart_region_properties takes the
         // axis TYPE (y, y2, x, x2). Following this note over there addressed no axis at all.
         "note", "For hiding and showing, an axis target is a column name (call get_binding) and " +
            "a legend target is an aesthetic field name, or its channel when the chart has only " +
            "one legend on it — name an assembly here for this chart's own axes and rendered " +
            "legends, or call get_chart_aesthetics. Showing a single axis or legend is not " +
            "supported — showing restores all of them. Note that " +
            "set_chart_region_properties addresses an axis by TYPE (y, y2, x, x2) instead, not " +
            "by column — it takes the column separately, as 'field'.");
   }

   // ── the legend footgun, contained ─────────────────────────────────────────

   /**
    * Builds the legends event, resolving a named legend to the channel the event needs.
    *
    * <p><b>Why the resolution is not optional.</b> {@code aestheticType} is the event's
    * discriminator, and {@code VSChartLegendsVisibilityService} reads a hide that omits it as
    * "hide every legend" — it routes straight to {@code showAllLegends(false)}. This class used
    * to send {@code field} alone, so the field was dead on arrival: naming one legend of two hid
    * <em>both</em>, and the summary reported having hidden the one that was named. It looked like
    * a no-op on a single-legend chart, which is why reading the source was the only way to tell
    * the two behaviours apart.
    *
    * <p>So a named legend is resolved against the ones the chart actually renders, and an
    * unresolvable name is refused — the one case where "a bogus target is an accepted no-op"
    * does not hold, because here it is not a no-op.
    *
    * <p>The remaining fields are the same ones the Composer's own {@code hideLegend} sends, taken
    * from the same graph it reads them from: {@code targetFields} to tell two same-channel
    * legends apart, {@code nodeAesthetic} for a relation chart's node aesthetic, and
    * {@code colorMerged} for the case below.
    *
    * <p><b>It reads through its own session read rather than the caller's mutation</b>, so that a
    * refusal costs nothing. See the note at the call site: the mutation checkpoints and
    * broadcasts even when it throws, on purpose, and a resolution that refuses has applied
    * nothing for that undo step to cover.
    */
   private VSChartLegendsVisibilityEvent legendEvent(String sessionToken, Principal user,
                                                     String assemblyName, String target,
                                                     boolean visible)
      throws Exception
   {
      // No target is the one case where hide-all is what was asked for, and showing is always all
      // of them - the event has no way to express showing one, which setVisibility refuses above.
      // Neither needs the graph, so neither reads the runtime here.
      if(target == null) {
         Map<String, Object> fields = new LinkedHashMap<>();
         fields.put("chartName", assemblyName);
         fields.put("hide", !visible);
         return convert(fields, VSChartLegendsVisibilityEvent.class);
      }

      return sessions.read(sessionToken, user, (rvs, runtimeId, dispatcher) -> {
         ChartVSAssembly chart = requireChart(rvs, assemblyName);
         ChartRegionResolver.Legends legends = ChartRegionResolver.legends(rvs, chart);

         return convert(
            legendFields(assemblyName, visible,
                         ChartRegionResolver.requireLegendField(legends, target), legends),
            VSChartLegendsVisibilityEvent.class);
      });
   }

   /**
    * The event's fields for hiding one named legend, separated from fetching the graph so the
    * values can be pinned in a test. This map is where the defect lived: it is one missing key
    * away from meaning "hide them all".
    */
   static Map<String, Object> legendFields(String chartName, boolean visible,
                                           ChartRegionResolver.LegendTarget legend,
                                           ChartRegionResolver.Legends legends)
   {
      Map<String, Object> fields = new LinkedHashMap<>();
      fields.put("chartName", chartName);
      fields.put("hide", !visible);
      fields.put("field", legend.field());
      fields.put("targetFields", legend.targetFields());
      fields.put("aestheticType", legend.aestheticType());
      fields.put("nodeAesthetic", legend.nodeAesthetic());
      fields.put("colorMerged", colorMerged(legends, legend.aestheticType()));
      return fields;
   }

   /**
    * Whether the colour legend is merged into the one being hidden, so its descriptor has to go
    * too — the same test the Composer's own {@code VSChartService.hideLegend} makes, over the same
    * legend list. A colour legend that is not rendered separately is drawn inside another
    * legend's box, and hiding that box without it leaves the colour swatches behind.
    */
   private static boolean colorMerged(ChartRegionResolver.Legends legends, String aestheticType) {
      long colors = legends.legends().stream()
         .filter(legend -> ChartArea.COLOR_LEGEND.equals(legend.aestheticType()))
         .count();

      return colors < (ChartArea.COLOR_LEGEND.equals(aestheticType) ? 2 : 1);
   }

   // ── the titles footgun, contained ─────────────────────────────────────────

   /**
    * Translates {@code (target, visible)} into the event's own idiom.
    *
    * <p>{@code hide=false} means "show every title", so it is only ever used when the caller
    * asked for exactly that. Showing one title goes through the event's
    * {@code chart-title-true} special case, and showing a single <i>axis</i> title has no
    * expression at all — which the caller is told rather than silently given all of them.
    */
   static Map<String, Object> titleFields(String assemblyName, String target, boolean visible) {
      Map<String, Object> fields = new LinkedHashMap<>();
      fields.put("chartName", assemblyName);

      if(target == null) {
         fields.put("hide", !visible);

         if(!visible) {
            fields.put("titleType", "chart-title");
         }

         return fields;
      }

      String name = target.trim().toLowerCase();

      if("chart".equals(name)) {
         // The event expresses "show the chart title" as hide=true plus this token, which is
         // why the agent-facing surface never exposes `hide`.
         fields.put("hide", true);
         fields.put("titleType", visible ? "chart-title-true" : "chart-title");
         return fields;
      }

      String axisTitle = AXIS_TITLES.get(name);

      if(axisTitle == null) {
         throw new IllegalArgumentException(
            "Unknown title target '" + target + "'. Valid targets: " +
            new TreeSet<>(AXIS_TITLES.keySet()) + ", or 'chart' for the chart title.");
      }

      if(visible) {
         throw new IllegalArgumentException(
            "Showing a single axis title is not supported by the Composer — showing restores " +
            "all titles at once. Call this without 'target' to show every title, or hide the " +
            "ones you do not want.");
      }

      fields.put("hide", true);
      fields.put("titleType", axisTitle);
      return fields;
   }

   private <T> T event(Map<String, Object> base, String targetField, String target,
                       Class<T> type)
   {
      Map<String, Object> fields = new LinkedHashMap<>(base);

      if(target != null) {
         fields.put(targetField, target);
      }

      return convert(fields, type);
   }

   private <T> T convert(Map<String, Object> fields, Class<T> type) {
      return objectMapper.convertValue(fields, type);
   }

   private static String requireElement(String element) {
      String kind = element == null ? "" : element.trim().toLowerCase();

      if(!List.of("axis", "legend", "title").contains(kind)) {
         throw new IllegalArgumentException(
            "'element' must be axis, legend or title, got '" + element + "'.");
      }

      return kind;
   }

   /** Shared with {@link ChartRegionPropertyService} so both refuse a non-chart identically. */
   private static ChartVSAssembly requireChart(RuntimeViewsheet rvs, String assemblyName) {
      return ChartRegionResolver.requireChart(rvs, assemblyName);
   }

   private final ViewsheetSessionService sessions;
   private final ObjectMapper objectMapper;
   private final VSChartAxesVisibilityService axesService;
   private final VSChartLegendsVisibilityService legendsService;
   private final VSChartTitlesVisibilityService titlesService;
   /**
    * Upper bound on the plot scale. The underlying ratio multiplies the plot's minimum size with
    * no ceiling of its own, so an absurd value would ask for an enormous graph — worth refusing
    * outright in a tool a model drives, where a stray 500 is a plausible typo for 5.
    */
   private static final double MAX_PLOT_RATIO = 10d;

   private final VSChartPlotResizeService plotResizeService;
}
