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

import inetsoft.graph.VGraph;
import inetsoft.graph.coord.Coordinate;
import inetsoft.graph.guide.axis.Axis;
import inetsoft.graph.guide.axis.DefaultAxis;
import inetsoft.graph.guide.legend.LegendGroup;
import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.report.composition.execution.ViewsheetSandbox;
import inetsoft.report.composition.graph.VGraphPair;
import inetsoft.uql.viewsheet.ChartVSAssembly;
import inetsoft.uql.viewsheet.VSAssembly;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.uql.viewsheet.graph.ChartAggregateRef;
import inetsoft.uql.viewsheet.graph.ChartRef;
import inetsoft.uql.viewsheet.graph.VSChartInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * Which axes and how many legends a chart actually has.
 *
 * <p><b>Why this exists.</b> Nothing else in the agent surface could answer that question, and
 * three tools needed it: the element vocabulary offered {@code y2} on every chart, the region
 * property read described that axis with a full, plausible property list, and the region property
 * write stored a value against it which the read then confirmed. An agent could enumerate, read,
 * write and verify, and be told yes at all four steps about an axis that is not there.
 *
 * <p><b>Why {@code ChartArea} cannot answer it.</b> The obvious place to look is where the
 * property dialogs look — {@code ChartRegionHandler.getAxisArea} maps {@code y2} onto
 * {@code ChartArea.getRightYAxisArea()} — but {@code ChartArea} builds all four axis areas
 * <em>unconditionally</em>, so the area exists whether or not a y2 does and a phantom axis reads
 * as real. (Its <em>title</em> areas are built conditionally, which is why a missing y2 title is
 * detectable and a missing y2 axis is not.) So the answer has to come from the axes themselves —
 * and, as the next paragraph but one records, not merely from whether one is present.
 *
 * <p><b>The binding is the base; the graph only adds to it.</b> The secondary axis is a property
 * of a <em>measure</em> ({@link ChartAggregateRef#isSecondaryY()}), and it lands opposite
 * whichever shelf holds the measures — so an inverted graph ({@link VSChartInfo#isInvertedGraph()})
 * puts it on the top axis rather than the right one. On top of that, a laid-out graph can reveal a
 * secondary axis the binding does not show, because one can be created by the engine rather than
 * by the user (date comparison and the percent scale both call {@code RectCoord.setYScale2}), so
 * the graph is consulted to <em>add</em> x2/y2 and never to remove anything.
 *
 * <p><b>Why the graph cannot simply be believed — this was a live bug in this class.</b> The first
 * version took {@code getAxesAt(RIGHT_AXIS).length > 0} as proof of a y2, on the reasoning that
 * the laid-out graph is what actually rendered. It reported y2 on an ordinary single-measure bar
 * chart, so the phantom sailed straight through the guard. {@code RectCoord.createAxis} builds
 * {@code yaxis2} <em>unconditionally</em> and only discards it when
 * {@code zIndex < 0 && getGridLineCount() == 0} — so with y grid lines enabled the axis survives
 * as a <b>grid-line carrier</b> and is added to the graph with no secondary scale behind it. That
 * is the real shape of "the right y axis always exists": not an empty area, an actual axis object.
 *
 * <p>The discriminator is two lines further down the same method:
 * {@code if(yscale2 == null) yaxis2.setPrimaryAxis(yaxis1);}. So an axis at the top or right whose
 * {@link Axis#getPrimaryAxis()} is {@code null} has its own scale and is a genuine secondary axis,
 * while one that points back at the primary is a mirror of it — a grid carrier, or the primary's
 * labels moved to the opposite side. Neither mirror has an independent descriptor to edit, which
 * is why neither counts.
 *
 * <p><b>And the graph is never allowed to subtract.</b> A hidden axis is dropped from the
 * coordinate entirely, so a graph-only answer would report no x axis for a chart whose x axis was
 * hidden — and then refuse to format or unhide it. Refusing a real axis is a worse failure than
 * the phantom this class was written to stop, so the binding sets the floor.
 */
final class ChartRegionResolver {
   /** The canonical axis names, in the order a reader expects them. */
   static final List<String> AXES = List.of("x", "x2", "y", "y2");

   /**
    * @param count    how many legends the chart renders
    * @param measured false when there was no laid-out graph to count from
    */
   record Legends(int count, boolean measured) {}

   /**
    * @param present  the canonical names of the axes this chart has
    * @param measured true when read from a laid-out graph, false when inferred from the binding
    * @param basis    where the answer came from, for a message the caller can act on
    */
   record Axes(Set<String> present, boolean measured, String basis) {
      boolean has(String axis) {
         return present.contains(canonical(axis));
      }

      /** In {@link #AXES} order rather than discovery order, so output is stable. */
      List<String> ordered() {
         return AXES.stream().filter(present::contains).toList();
      }
   }

   private ChartRegionResolver() {
   }

   static Axes resolve(RuntimeViewsheet rvs, ChartVSAssembly chart) {
      VGraphPair pair = laidOutPair(rvs, chart);

      // ChartArea reads its axis areas from the EXPANDED graph, so resolve from the same one.
      return resolve(chart, pair == null ? null : pair.getExpandedVGraph());
   }

   /**
    * How many legends the chart has, counted where {@code ChartArea} counts them.
    *
    * <p>{@code LegendsArea} builds one area per {@code vgraph.getLegendGroup().getLegendCount()}
    * and is not built at all when the group is null, so that count is exactly the valid index
    * range for a legend target. It comes off the <b>real-size</b> graph, which is the one
    * {@code ChartArea} passes to {@code LegendsArea} - unlike the axes, which come from the
    * expanded graph.
    *
    * <p>An index outside it used to reach StyleBI and return a raw HTTP 500 page; a chart with no
    * laid-out graph has no legends either, so {@code count 0} with the basis stated is a truthful
    * answer rather than a guess.
    */
   static Legends legendCount(RuntimeViewsheet rvs, ChartVSAssembly chart) {
      VGraphPair pair = laidOutPair(rvs, chart);
      VGraph graph = pair == null ? null : pair.getRealSizeVGraph();

      if(graph == null) {
         return new Legends(0, false);
      }

      LegendGroup legends = graph.getLegendGroup();
      return new Legends(legends == null ? 0 : legends.getLegendCount(), true);
   }

   /**
    * Refuses a legend index outside the chart's range, naming the range.
    *
    * <p>The index is the one target in this vocabulary with a knowable bound, so it is also the
    * easiest to check - and it was the one returning a bare 500.
    *
    * <p><b>Only when the count was actually measured.</b> An unmeasured count is zero because
    * there was no graph to count, not because the chart has no legends, and refusing on that
    * would block a legitimate write whenever the layout was unavailable - the same
    * "never subtract on missing evidence" rule the axis side follows. Unmeasured lets the call
    * through to whatever the server says, which is no worse than before this guard existed.
    */
   static void requireLegend(Legends legends, int index) {
      if(!legends.measured() || index >= 0 && index < legends.count()) {
         return;
      }

      throw new IllegalArgumentException(
         "This chart has " + (legends.count() == 0 ? "no legends"
            : legends.count() + (legends.count() == 1 ? " legend" : " legends") +
              ", so the valid indexes are 0" +
              (legends.count() > 1 ? " to " + (legends.count() - 1) : "")) +
         " - '" + index + "' is out of range. A legend exists per aesthetic field bound to the " +
         "chart; get_chart_aesthetics shows which.");
   }

   /**
    * The decision rule, separated from fetching the graph so it can be exercised without a
    * sandbox. The rule is where the live bug was; the plumbing is the live case's business.
    */
   static Axes resolve(ChartVSAssembly chart, VGraph graph) {
      Set<String> present = new LinkedHashSet<>(fromBinding(chart.getVSChartInfo()));

      if(graph == null || graph.getCoordinate() == null) {
         return new Axes(present, false,
                         "the binding alone, because this chart has no laid-out graph yet - it " +
                         "is unbound, empty, or still computing");
      }

      // Additive only, and only for the secondary axes. The graph can reveal a y2 the binding
      // does not (date comparison and the percent scale create one), but it cannot be used to
      // rule an axis out: a hidden axis leaves the coordinate altogether.
      if(hasOwnScaleAxis(graph, Coordinate.TOP_AXIS)) {
         present.add("x2");
      }

      if(hasOwnScaleAxis(graph, Coordinate.RIGHT_AXIS)) {
         present.add("y2");
      }

      return new Axes(present, true, "the binding plus the chart's laid-out graph");
   }

   /**
    * Refuses an axis the chart does not have, naming the ones it does.
    *
    * <p>Refusing on an <em>inferred</em> answer is deliberate rather than an oversight: a chart
    * with no laid-out graph has no rendered axis either, so accepting the write would store a
    * value against something that does not exist — which is the defect this class was added for.
    * The message says which basis was used so the caller can tell the two cases apart.
    */
   static void requireAxis(Axes axes, String region, String target) {
      if(axes.has(target)) {
         return;
      }

      List<String> present = axes.ordered();

      throw new IllegalArgumentException(
         "This chart has no '" + target + "' " + region + ". " +
         (present.isEmpty()
            ? "It has no axes at all"
            : "Its axes are: " + String.join(", ", present)) +
         " (from " + axes.basis() + "). A y2 or x2 axis exists only when a measure uses the " +
         "secondary axis, or the engine created one (date comparison, percent scale) - the axis " +
         "object at the right or top of an ordinary chart is a grid-line carrier, not a second " +
         "axis. Asking for one that is not there used to return a full, plausible property list " +
         "and accept a write against it.");
   }

   /**
    * The long area forms {@code ChartRegionHandler.getAxisArea} accepts, folded onto the short
    * names. Both reach the same axis there, so both have to reach the same answer here -
    * {@code right_y_axis} is exactly the alias a caller reaching for a phantom y2 would use.
    */
   static String canonical(String target) {
      if(target == null) {
         return "";
      }

      return switch(target.trim().toLowerCase()) {
         case "bottom_x_axis" -> "x";
         case "top_x_axis" -> "x2";
         case "left_y_axis" -> "y";
         case "right_y_axis" -> "y2";
         default -> target.trim().toLowerCase();
      };
   }

   static ChartVSAssembly requireChart(RuntimeViewsheet rvs, String assemblyName) {
      Viewsheet vs = rvs == null ? null : rvs.getViewsheet();
      VSAssembly assembly = vs == null ? null : vs.getAssembly(assemblyName);

      if(assembly == null) {
         throw new IllegalArgumentException("Unknown assembly '" + assemblyName + "'.");
      }

      if(!(assembly instanceof ChartVSAssembly chart)) {
         throw new IllegalArgumentException(
            "'" + assemblyName + "' is a " + assembly.getClass().getSimpleName() +
            ", not a chart. Axes, legends and titles only exist on charts.");
      }

      return chart;
   }

   /**
    * Whether a genuine secondary axis sits at this position — one with its own scale rather than
    * one mirroring the primary. See the class comment: presence alone is not evidence, because
    * the secondary axis object is built unconditionally and survives as a grid-line carrier.
    */
   private static boolean hasOwnScaleAxis(VGraph graph, int position) {
      DefaultAxis[] axes = graph.getAxesAt(position);

      if(axes == null) {
         return false;
      }

      for(DefaultAxis axis : axes) {
         if(axis != null && axis.getPrimaryAxis() == null) {
            return true;
         }
      }

      return false;
   }

   private static Set<String> fromBinding(VSChartInfo info) {
      Set<String> present = new LinkedHashSet<>();

      if(info == null) {
         return present;
      }

      ChartRef[] x = info.getXFields();
      ChartRef[] y = info.getYFields();

      if(x != null && x.length > 0) {
         present.add("x");
      }

      if(y != null && y.length > 0) {
         present.add("y");
      }

      // The secondary axis belongs to a measure, and it lands opposite whichever shelf holds the
      // measures: the right axis normally, the top one on an inverted graph, where x and y swap
      // roles. Reading isSecondaryY off the wrong shelf reports x2 as y2 and vice versa.
      boolean inverted = info.isInvertedGraph();

      if(hasSecondaryMeasure(inverted ? x : y)) {
         present.add(inverted ? "x2" : "y2");
      }

      return present;
   }

   private static boolean hasSecondaryMeasure(ChartRef[] refs) {
      if(refs == null) {
         return false;
      }

      for(ChartRef ref : refs) {
         if(ref instanceof ChartAggregateRef aggregate && aggregate.isSecondaryY()) {
            return true;
         }
      }

      return false;
   }

   private static VGraphPair laidOutPair(RuntimeViewsheet rvs, ChartVSAssembly chart) {
      ViewsheetSandbox box = rvs == null ? null : rvs.getViewsheetSandbox().orElse(null);

      if(box == null) {
         return null;
      }

      try {
         box.lockRead();

         try {
            return box.getVGraphPair(chart.getAbsoluteName());
         }
         finally {
            box.unlockRead();
         }
      }
      catch(Exception ex) {
         // A graph that will not lay out is a reason to fall back to the binding and say so, not
         // to fail a read the caller asked for.
         LOG.debug("Failed to lay out {} while resolving its regions; falling back to the binding",
                   chart.getAbsoluteName(), ex);
         return null;
      }
   }

   private static final Logger LOG = LoggerFactory.getLogger(ChartRegionResolver.class);
}
