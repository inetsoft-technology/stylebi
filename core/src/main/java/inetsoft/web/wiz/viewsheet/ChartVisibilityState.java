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
import inetsoft.graph.aesthetic.ColorFrame;
import inetsoft.graph.aesthetic.CompositeVisualFrame;
import inetsoft.graph.aesthetic.VisualFrame;
import inetsoft.graph.element.GraphElement;
import inetsoft.graph.guide.legend.LegendGroup;
import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.report.composition.graph.GraphUtil;
import inetsoft.report.composition.region.ChartArea;
import inetsoft.uql.erm.DataRef;
import inetsoft.uql.viewsheet.ChartVSAssembly;
import inetsoft.uql.viewsheet.internal.ChartVSAssemblyInfo;
import inetsoft.uql.viewsheet.VSDataRef;
import inetsoft.uql.viewsheet.graph.*;
import inetsoft.web.graph.handler.ChartRegionHandler;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Reads whether a chart's legends, axes and axis titles are hidden, from the descriptors the
 * visibility tools write (bug #80331).
 *
 * <p>Nothing else reported this. The legend list is read off the laid-out graph, and a legend
 * whose descriptor is switched off is simply not built into it, so a hidden legend was
 * indistinguishable from a chart that has none. Everything here is therefore <b>descriptor
 * state</b>, not what was drawn.
 *
 * <p><b>Legends are paired with bound aesthetics only.</b> Hiding every legend writes every
 * {@link LegendDescriptor} the chart has, including the ones for channels with nothing bound, so
 * "any descriptor is off" would call a color-only chart's shape legend hidden. And because a
 * legend with a single value is not drawn although its descriptor is on, an empty legend list
 * must never be read as hidden: only an off descriptor (or the NO_LEGEND layout) is.
 */
final class ChartVisibilityState {
   /**
    * @param channel the aesthetic channel: color, shape, size, node-color or node-size
    * @param field   the bound field
    */
   record HiddenLegend(String channel, String field) {
   }

   /** The bound legends that are switched off. Never null; empty when none is hidden. */
   record HiddenLegends(List<HiddenLegend> hidden) {
      static final HiddenLegends NONE = new HiddenLegends(List.of());

      boolean any() {
         return !hidden.isEmpty();
      }

      /**
       * The hidden legend a caller named, matched like {@code ChartRegionResolver.requireLegendField}
       * matches a drawn one: by field (exact, then ignoring case and surrounding space), else by
       * channel when exactly one hidden legend is on it. Null when the name matches none.
       */
      HiddenLegend find(String target) {
         String wanted = target == null ? "" : target.trim();

         for(HiddenLegend legend : hidden) {
            if(legend.field().equals(wanted)) {
               return legend;
            }
         }

         for(HiddenLegend legend : hidden) {
            if(legend.field().equalsIgnoreCase(wanted)) {
               return legend;
            }
         }

         String channel = channelFamily(wanted);
         List<HiddenLegend> byChannel = hidden.stream()
            .filter(legend -> channelFamily(legend.channel()).equals(channel)).toList();

         return byChannel.size() == 1 ? byChannel.get(0) : null;
      }

      private static String channelFamily(String channel) {
         String name = channel == null ? "" : channel.trim().toLowerCase();

         return switch(name) {
            case "line", "texture" -> "shape";
            default -> name;
         };
      }

      /** For a message: {@code color [Customer:Region], size [Total]}. */
      String describe() {
         StringBuilder sb = new StringBuilder();

         for(HiddenLegend legend : hidden) {
            if(sb.length() > 0) {
               sb.append(", ");
            }

            sb.append(legend.channel()).append(" [").append(legend.field()).append(']');
         }

         return sb.toString();
      }
   }

   private ChartVisibilityState() {
   }

   /**
    * The descriptor the visibility tools write: the runtime one when it exists (they write both,
    * and the runtime one is what the renderer prefers), else the design one.
    */
   static ChartDescriptor descriptor(ChartVSAssembly chart) {
      ChartVSAssemblyInfo info = chart.getChartInfo();
      return info != null && info.getRTChartDescriptor() != null ? info.getRTChartDescriptor()
         : chart.getChartDescriptor();
   }

   /** The condition {@code requireDrawnAxis} uses for the max-mode twin flags. */
   static boolean maxMode(RuntimeViewsheet rvs, ChartVSAssembly chart) {
      return rvs.getViewsheet().isMaxMode() && chart.getChartInfo() != null &&
         chart.getChartInfo().getMaxSize() != null;
   }

   /**
    * @param maxMode true when the chart is in max mode with a max size, which reads the max-mode
    *                twin flags the hide tools write alongside the normal ones
    */
   static HiddenLegends hiddenLegends(ChartDescriptor chartDesc, VSChartInfo info,
                                      boolean maxMode)
   {
      LegendsDescriptor legendsDesc = chartDesc == null ? null : chartDesc.getLegendsDescriptor();

      if(legendsDesc == null || info == null) {
         return HiddenLegends.NONE;
      }

      boolean noLegend = legendsDesc.getLayout() == LegendsDescriptor.NO_LEGEND;
      // keyed so that a field bound on several measures is reported once
      Map<String, HiddenLegend> found = new LinkedHashMap<>();

      if(info.isMultiAesthetic()) {
         ChartRef[] refs = info.getBindingRefs(false);

         for(ChartRef ref : refs == null ? new ChartRef[0] : refs) {
            if(ref instanceof ChartAggregateRef agg) {
               collect(found, "color", agg.getColorField(), null, noLegend, maxMode);
               collect(found, "shape", agg.getShapeField(), null, noLegend, maxMode);
               collect(found, "size", agg.getSizeField(), null, noLegend, maxMode);
            }
         }
      }
      else {
         // a relation chart's edge color/size are drawn and hidden through their own descriptor
         boolean relationChart = info instanceof RelationChartInfo;
         collect(found, "color", info.getColorField(),
                 relationChart ? null : legendsDesc.getColorLegendDescriptor(), noLegend,
                 maxMode);
         collect(found, "shape", info.getShapeField(),
                 legendsDesc.getShapeLegendDescriptor(), noLegend, maxMode);
         collect(found, "size", info.getSizeField(),
                 relationChart ? null : legendsDesc.getSizeLegendDescriptor(), noLegend,
                 maxMode);

         if(info instanceof RelationChartInfo relation) {
            collect(found, "node-color", relation.getNodeColorField(), null, noLegend, maxMode);
            collect(found, "node-size", relation.getNodeSizeField(), null, noLegend, maxMode);
         }
      }

      return found.isEmpty() ? HiddenLegends.NONE : new HiddenLegends(
         List.copyOf(found.values()));
   }

   /**
    * The automatic colour legend (the "Measures" legend of a chart with several measures and no
    * colour field) when it is switched off, or null (bug #80505 item 3).
    *
    * <p>That legend has no bound field, so {@link #hiddenLegends} never sees it, and a hidden
    * legend is not built into the graph, so an empty legend list read as "no legends". Whether
    * the chart WOULD draw one is decided by the renderer, not by a rule copied here: the laid-out
    * graph is cloned, the colour frames whose legend descriptor is off (found with the same
    * {@code GraphUtil.getLegendDescriptor} call the renderer makes) have their legend switched on
    * in the clone, and {@link EGraph#getVisualFrames()} - the one place that chooses which frames
    * become legends - is asked what it would draw. A field-less colour frame coming back is the
    * hidden legend; its title is the name, the one the visible legend shows.
    *
    * <p>The user's hide is the evidence, as for a bound legend (descriptor off, or the NO_LEGEND
    * layout), and a field-less colour legend that is already drawn means nothing is hidden.
    * Sparkline and donut charts are left alone: the renderer switches their legend off whatever
    * the descriptor says, so the clone would name a legend that is never drawn.
    *
    * @param egraph      the laid-out graph the legends were read from, or null
    * @param drawnLegend true when a colour legend with no field is already in the laid-out list
    */
   static HiddenLegend hiddenAutomaticColorLegend(EGraph egraph, VSChartInfo info,
                                                  ChartDescriptor chartDesc, boolean maxMode,
                                                  boolean drawnLegend)
   {
      LegendsDescriptor legendsDesc = chartDesc == null ? null : chartDesc.getLegendsDescriptor();

      if(egraph == null || info == null || legendsDesc == null || drawnLegend ||
         info.isDonut() || chartDesc.isSparkline())
      {
         return null;
      }

      boolean noLegend = legendsDesc.getLayout() == LegendsDescriptor.NO_LEGEND;
      EGraph what = (EGraph) egraph.clone();

      if(what == null) {
         return null;
      }

      boolean hiddenByUser = false;

      for(int i = 0; i < what.getElementCount(); i++) {
         GraphElement elem = what.getElement(i);
         ColorFrame color = elem.getColorFrame();

         if(color == null || color.getField() != null) {
            continue;
         }

         LegendDescriptor desc = GraphUtil.getLegendDescriptor(
            info, legendsDesc, null, Arrays.asList(elem.getVars()), ChartArea.COLOR_LEGEND);
         boolean off = noLegend ||
            desc != null && (maxMode ? !desc.isMaxModeVisible() : !desc.isVisible());

         if(off) {
            hiddenByUser = true;

            for(VisualFrame frame : LegendGroup.getLegendFrames(color)) {
               for(VisualFrame f = frame; f != null;
                   f = f instanceof CompositeVisualFrame c ? c.getGuideFrame() : null)
               {
                  f.getLegendSpec().setVisible(true);
               }
            }
         }
      }

      if(!hiddenByUser) {
         return null;
      }

      for(VisualFrame frame : what.getVisualFrames()) {
         if(frame instanceof ColorFrame && frame.getField() == null) {
            return new HiddenLegend("color", frame.getTitle());
         }
      }

      return null;
   }

   /**
    * @param shared the chart-wide descriptor for the channel, or null to use the aesthetic's own
    *               (a measure's, or a relation chart's node aesthetic)
    */
   private static void collect(Map<String, HiddenLegend> found, String channel,
                               AestheticRef aref, LegendDescriptor shared, boolean noLegend,
                               boolean maxMode)
   {
      if(aref == null || aref.getDataRef() == null) {
         return;
      }

      LegendDescriptor desc = shared != null ? shared : aref.getLegendDescriptor();
      boolean off = desc != null && (maxMode ? !desc.isMaxModeVisible() : !desc.isVisible());

      if(!noLegend && !off) {
         return;
      }

      DataRef data = aref.getDataRef();
      String field = data instanceof VSDataRef v ? v.getFullName() : data.getName();
      found.putIfAbsent(channel + "|" + field, new HiddenLegend(channel, field));
   }

   /**
    * Per axis (x, y, x2, y2): {@code visible}, {@code hidden} (label and line both off, which is
    * what hiding writes) or {@code partial} (exactly one off). A shelf with several measures on
    * separate axes is {@code partial} when they disagree.
    *
    * <p>This is <b>descriptor state</b>, not what was drawn: {@code requireDrawnAxis} refuses a
    * read when the laid-out area is empty, and the coordinates rewrite descriptor flags for facet
    * and inner axes, so an axis can read {@code hidden} here and still be drawn. Axes with no
    * field on their shelf are left out.
    */
   static Map<String, String> axisVisibility(VSChartInfo info, List<String> axes,
                                             boolean maxMode)
   {
      Map<String, String> out = new LinkedHashMap<>();

      if(info == null) {
         return out;
      }

      ChartRegionHandler handler = new ChartRegionHandler();

      for(String axis : axes) {
         ChartRef[] candidates = ChartRegionPropertyService.axisCandidates(info, axis);
         Set<String> states = new LinkedHashSet<>();

         for(ChartRef ref : candidates) {
            AxisDescriptor desc = handler.getAxisDescriptor(
               info, ref.getFullName(), ChartRegionPropertyService.axisType(axis),
               new AtomicBoolean());

            if(desc != null) {
               boolean label = maxMode ? desc.isMaxModeLabelVisible() : desc.isLabelVisible();
               boolean line = maxMode ? desc.isMaxModeLineVisible() : desc.isLineVisible();
               states.add(label && line ? "visible" : !label && !line ? "hidden" : "partial");
            }
         }

         if(states.size() == 1) {
            out.put(axis, states.iterator().next());
         }
         else if(states.size() > 1) {
            out.put(axis, "partial");
         }
      }

      return out;
   }

   /** Per axis title (x, y, x2, y2): true when it is shown. */
   static Map<String, Boolean> titleVisibility(ChartDescriptor chartDesc, List<String> axes,
                                               boolean maxMode)
   {
      Map<String, Boolean> out = new LinkedHashMap<>();
      TitlesDescriptor titles = chartDesc == null ? null : chartDesc.getTitlesDescriptor();

      if(titles == null) {
         return out;
      }

      for(String axis : axes) {
         TitleDescriptor desc = switch(axis) {
            case "x" -> titles.getXTitleDescriptor();
            case "x2" -> titles.getX2TitleDescriptor();
            case "y" -> titles.getYTitleDescriptor();
            case "y2" -> titles.getY2TitleDescriptor();
            default -> null;
         };

         if(desc != null) {
            out.put(axis, maxMode ? desc.isMaxModeVisible() : desc.isVisible());
         }
      }

      return out;
   }
}
