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

import inetsoft.graph.EGraph;
import inetsoft.graph.VGraph;
import inetsoft.graph.aesthetic.VisualFrame;
import inetsoft.graph.coord.Coordinate;
import inetsoft.graph.guide.axis.Axis;
import inetsoft.graph.guide.axis.DefaultAxis;
import inetsoft.graph.guide.legend.Legend;
import inetsoft.graph.guide.legend.LegendGroup;
import inetsoft.graph.internal.GTool;
import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.report.composition.execution.ViewsheetSandbox;
import inetsoft.report.composition.graph.GraphUtil;
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
import java.util.stream.Collectors;

/**
 * Which axes a chart actually has, and which legends it actually renders.
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
    * One legend the chart actually renders, in the vocabulary its visibility event needs.
    *
    * @param field         the aesthetic field it is bound to, as the frame itself reports it;
    *                      null for a legend that carries no field (a measure or static frame)
    * @param aestheticType the event's own discriminator, and the value whose absence hid every
    *                      legend. {@code Legend.createLegend} builds a legend for five frame
    *                      families only, so this is {@code Color}, {@code Size}, {@code Shape},
    *                      {@code Texture} or {@code Line} — the last three being one target,
    *                      see {@link #channelFamily}
    * @param targetFields  the measures this legend applies to, which is what tells two legends
    *                      of the same channel apart on a multi-aesthetic chart
    * @param nodeAesthetic true when this is a relation chart's node aesthetic rather than its edge
    */
   record LegendTarget(String field, String aestheticType, List<String> targetFields,
                       boolean nodeAesthetic)
   {
      /**
       * Lower-cased, to match the channel names {@code get_chart_aesthetics} reports — which is
       * not to say the two always agree on <em>which</em> channel: a line chart renders the field
       * on its shape channel as a {@code line} legend. Empty for a legend with no describable
       * frame, which is also a legend with no field, so neither can be named.
       */
      String channel() {
         return aestheticType == null ? "" : aestheticType.toLowerCase();
      }
   }

   /**
    * @param legends  the chart's legends in the graph's own order, so a position here is the
    *                 0-based legend index the region tools address
    * @param measured false when there was no laid-out graph to read them from
    */
   record Legends(List<LegendTarget> legends, boolean measured) {
      int count() {
         return legends.size();
      }
   }

   /**
    * A chart's axes and legends read from one laid-out graph.
    *
    * <p>They come from different halves of the same {@link VGraphPair} - the axes from the
    * expanded graph, the legends from the real-size one, each where {@code ChartArea} reads them -
    * so fetching the pair once is what makes the two answers describe the same layout rather than
    * two layouts either side of a relayout.
    */
   record Regions(Axes axes, Legends legends) {}

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
      return regions(rvs, chart).axes();
   }

   static Legends legends(RuntimeViewsheet rvs, ChartVSAssembly chart) {
      return regions(rvs, chart).legends();
   }

   /**
    * Both answers off one laid-out pair, for a caller that reports them together.
    *
    * <p>The two halves are read from different graphs on purpose - see {@link Regions}.
    */
   static Regions regions(RuntimeViewsheet rvs, ChartVSAssembly chart) {
      VGraphPair pair = laidOutPair(rvs, chart);

      // ChartArea reads its axis areas from the EXPANDED graph and its legend areas from the
      // REAL-SIZE one, so each half is resolved from the same graph its areas come from.
      return new Regions(resolve(chart, pair == null ? null : pair.getExpandedVGraph()),
                         legends(pair == null ? null : pair.getRealSizeVGraph()));
   }

   /**
    * The legends the chart has, read where {@code ChartArea} reads them.
    *
    * <p>{@code LegendsArea} builds one area per {@code vgraph.getLegendGroup().getLegendCount()}
    * and is not built at all when the group is null, so that count is exactly the valid index
    * range for a legend target. It comes off the <b>real-size</b> graph, which is the one
    * {@code ChartArea} passes to {@code LegendsArea} - unlike the axes, which come from the
    * expanded graph.
    *
    * <p>An index outside it used to reach StyleBI and return a raw HTTP 500 page; a chart with no
    * laid-out graph has no legends either, so an empty list with the basis stated is a truthful
    * answer rather than a guess.
    *
    * <p><b>Each legend is described, not just counted</b>, because the count alone cannot answer
    * the question the visibility event actually asks: which aesthetic channel is this field's
    * legend on. Every fact here is taken from the same place {@link
    * inetsoft.report.composition.region.LegendArea} takes it, so the answer matches what the
    * Composer sends for the same legend rather than being a second opinion about it.
    *
    * <p>A legend whose frame cannot be described still occupies its slot in the list. Dropping it
    * would renumber every legend after it, and the index is a target the region tools address.
    */
   static Legends legends(VGraph graph) {
      if(graph == null) {
         return new Legends(List.of(), false);
      }

      LegendGroup group = graph.getLegendGroup();

      if(group == null) {
         return new Legends(List.of(), true);
      }

      EGraph egraph = graph.getEGraph();
      List<LegendTarget> targets = new ArrayList<>();

      for(int i = 0; i < group.getLegendCount(); i++) {
         Legend legend = group.getLegend(i);
         VisualFrame frame = legend == null ? null : legend.getVisualFrame();

         if(frame == null) {
            targets.add(new LegendTarget(null, null, List.of(), false));
            continue;
         }

         targets.add(new LegendTarget(
            frame.getField(),
            GTool.getFrameType(frame.getClass()),
            egraph == null ? List.of() : GraphUtil.getTargetFields(frame, egraph),
            GraphUtil.isNodeAestheticFrame(frame, legend.getGraphElement())));
      }

      return new Legends(targets, true);
   }

   /**
    * The legend a caller named, or a refusal naming the ones the chart has.
    *
    * <p><b>Why this has to refuse rather than fall through.</b> The event reads a hide with no
    * {@code aestheticType} as "hide every legend" — see {@code VSChartLegendsVisibilityService},
    * which routes it to {@code showAllLegends(false)}. So an unresolved target is not an inert
    * no-op the way an unknown axis column is: it hides the chart's real legends and reports
    * success naming the one the caller asked for. That is what made this the one part of the
    * "a bogus target is an accepted no-op" ruling that could not be closed with it.
    *
    * <p>Forgiving where the intent is unambiguous, per this repo's own rule: the field is matched
    * ignoring case and surrounding space, and a caller who names the <em>channel</em> instead
    * ({@code color}, {@code shape}, {@code size}) is taken to mean that legend when the chart has
    * exactly one of them — with {@code shape}, {@code line} and {@code texture} folded together,
    * because the event does not tell them apart either and the two names for the same legend are
    * both put in front of the caller (see {@link #channelFamily}). Two legends in the same family
    * make it ambiguous, so that is refused with both fields named rather than resolved to the
    * first.
    */
   static LegendTarget requireLegendField(Legends legends, String target) {
      String wanted = target == null ? "" : target.trim();

      if(!legends.measured()) {
         throw new IllegalArgumentException(
            "This chart has no laid-out graph yet - it is unbound, empty, or still computing - so " +
            "its legends cannot be resolved, and hiding one by name would hide them all. Call " +
            "this without 'target' to hide every legend deliberately, or retry once the chart " +
            "has rendered.");
      }

      for(LegendTarget legend : legends.legends()) {
         if(legend.field() != null && legend.field().equals(wanted)) {
            return legend;
         }
      }

      for(LegendTarget legend : legends.legends()) {
         if(legend.field() != null && legend.field().equalsIgnoreCase(wanted)) {
            return legend;
         }
      }

      String family = channelFamily(wanted);
      List<LegendTarget> byChannel = family.isEmpty() ? List.of() : legends.legends().stream()
         .filter(legend -> channelFamily(legend.aestheticType()).equals(family))
         .toList();

      if(byChannel.size() == 1) {
         return byChannel.get(0);
      }

      throw new IllegalArgumentException(describeLegends(legends, target, byChannel.size() > 1));
   }

   /**
    * The channel names that address the same legend, folded onto one.
    *
    * <p>{@code GraphUtil.getLegendDescriptor} maps {@code Shape}, {@code Line} and {@code Texture}
    * onto the same shape legend descriptor, so for hiding they are one target rather than three.
    * Folding them matters because the caller meets both names: {@code get_chart_aesthetics}
    * reports the field on the <b>shape</b> channel, while a line chart renders that legend as
    * <b>Line</b> — so a caller who read the aesthetics and said "shape" was naming this legend
    * correctly and being refused for it.
    */
   private static String channelFamily(String channel) {
      String name = channel == null ? "" : channel.trim().toLowerCase();

      return switch(name) {
         case "shape", "line", "texture" -> "shape";
         default -> name;
      };
   }

   private static String describeLegends(Legends legends, String target, boolean ambiguousChannel) {
      String have = legends.legends().isEmpty()
         ? "This chart renders no legends"
         : "Its legends are: " + legends.legends().stream()
            .map(legend -> (legend.field() == null ? "(unnamed)" : legend.field()) +
               " (" + (legend.aestheticType() == null ? "unknown channel" : legend.channel()) + ")")
            .collect(Collectors.joining(", "));

      return (ambiguousChannel
         ? "This chart has more than one '" + target + "' legend, so naming the channel does not " +
           "say which. Name the field instead. "
         : "This chart has no '" + target + "' legend. ") +
         have + ". A legend target is the aesthetic FIELD name - list_chart_elements on this " +
         "chart, or get_chart_aesthetics, reports them - or the channel above when only one " +
         "legend is on it. Omit 'target' to hide every legend, which is what a target the chart " +
         "does not have used to do while reporting that it had hidden the one you named.";
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

      String range = legends.count() == 0
         ? "This chart has no legends"
         : legends.count() == 1
            ? "This chart has 1 legend, so the only valid index is 0"
            : "This chart has " + legends.count() + " legends, so the valid indexes are 0 to " +
              (legends.count() - 1);

      // No dash before the offending index: "valid indexes are 0 - '7'" reads as a range, which
      // is the opposite of what it says. Seen in live output.
      throw new IllegalArgumentException(
         range + "; '" + index + "' is out of range. A legend exists per aesthetic field bound " +
         "to the chart; get_chart_aesthetics shows which.");
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
