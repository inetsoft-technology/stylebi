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
package inetsoft.uql.viewsheet.internal;

import inetsoft.graph.aesthetic.*;
import inetsoft.report.composition.graph.GraphTarget;
import inetsoft.uql.CompositeValue;
import inetsoft.uql.erm.DataRef;
import inetsoft.uql.viewsheet.VSDataRef;
import inetsoft.uql.viewsheet.graph.*;
import inetsoft.uql.viewsheet.graph.aesthetic.VisualFrameWrapper;

import java.awt.Color;
import java.awt.Font;
import java.util.*;
import java.util.function.*;

/**
 * Builds the look slots of a chart's design descriptors: text formats, plot and legend values,
 * axis lines, the shape flags the mark seeds, and the author's colours. Keys name the descriptor,
 * or a ref's list, index and name, so a clone yields the same keys - except after a separate or
 * multi-style toggle, where shared descriptors can give the clone extra keys. Callers that
 * compare a clone iterate the original's slots and apply each to the clone.
 */
final class ChartChromeSlots {
   enum TextProperty {
      FONT, COLOR, BACKGROUND, ALPHA
   }

   private ChartChromeSlots() {
   }

   static List<ChromeSlot> of(ChartVSAssemblyInfo info) {
      Walk walk = new Walk(info);
      walk.descriptor();
      walk.chartInfo();
      walk.flags();
      return walk.slots;
   }

   /**
    * Reaches a property from a chart info; null anywhere along the way gives null.
    */
   @FunctionalInterface
   private interface Loc<T> extends Function<ChartVSAssemblyInfo, T> {
      default <R> Loc<R> then(Function<? super T, ? extends R> step) {
         return info -> {
            T value = apply(info);
            return value == null ? null : step.apply(value);
         };
      }

      default T on(VSAssemblyInfo info) {
         return info instanceof ChartVSAssemblyInfo ? apply((ChartVSAssemblyInfo) info) : null;
      }
   }

   private static final Loc<ChartDescriptor> DESC = ChartVSAssemblyInfo::getChartDescriptor;
   private static final Loc<VSChartInfo> CHART = ChartVSAssemblyInfo::getVSChartInfo;
   private static final Loc<PlotDescriptor> PLOT = DESC.then(ChartDescriptor::getPlotDescriptor);
   private static final String[] PLOT_VALUES = {
      "xGridColor", "yGridColor", "xGridStyle", "yGridStyle", "diagonalColor", "quadrantColor",
      "facetColor", "bgColor"
   };
   private static final String[] LEGENDS_VALUES = { "borderColor", "border", "gap", "padding" };

   private static final class Walk {
      Walk(ChartVSAssemblyInfo source) {
         this.source = source;
      }

      void descriptor() {
         Loc<LegendsDescriptor> legends = DESC.then(ChartDescriptor::getLegendsDescriptor);

         if(legends.apply(source) != null) {
            text("chart:legends:title", legends.then(LegendsDescriptor::getTitleTextFormat), true);
            text("chart:legends:color", legends.then(LegendsDescriptor::getColorLegendDescriptor)
               .then(LegendDescriptor::getContentTextFormat), true);
            text("chart:legends:shape", legends.then(LegendsDescriptor::getShapeLegendDescriptor)
               .then(LegendDescriptor::getContentTextFormat), true);
            text("chart:legends:size", legends.then(LegendsDescriptor::getSizeLegendDescriptor)
               .then(LegendDescriptor::getContentTextFormat), true);

            for(String field : LEGENDS_VALUES) {
               slots.add(valueSlot("chart:legends:" + field,
                                   legends.then(l -> l.chromeValue(field))));
            }

            slots.add(ChromeSlot.plain("chart:legends:layout", copy(legends,
               (to, from) -> to.setLayout(from.getLayout()))));
            slots.add(ChromeSlot.plain("chart:legends:roundCorners", copy(legends,
               (to, from) -> to.setRoundCorners(from.isRoundCorners()))));
         }

         Loc<TitlesDescriptor> titles = DESC.then(ChartDescriptor::getTitlesDescriptor);
         text("chart:title:x", titles.then(TitlesDescriptor::getXTitleDescriptor)
            .then(TitleDescriptor::getTextFormat), true);
         text("chart:title:x2", titles.then(TitlesDescriptor::getX2TitleDescriptor)
            .then(TitleDescriptor::getTextFormat), true);
         text("chart:title:y", titles.then(TitlesDescriptor::getYTitleDescriptor)
            .then(TitleDescriptor::getTextFormat), true);
         text("chart:title:y2", titles.then(TitlesDescriptor::getY2TitleDescriptor)
            .then(TitleDescriptor::getTextFormat), true);

         if(PLOT.apply(source) != null) {
            text("chart:plot:text", PLOT.then(PlotDescriptor::getTextFormat), false);

            for(String field : PLOT_VALUES) {
               slots.add(valueSlot("chart:plot:" + field, PLOT.then(p -> p.chromeValue(field))));
            }

            slots.add(ChromeSlot.plain("chart:plot:trendLineColor", copy(PLOT,
               (to, from) -> to.setTrendLineColor(from.getTrendLineColor()))));
            slots.add(ChromeSlot.plain("chart:plot:borderColor", copy(PLOT,
               (to, from) -> to.setBorderColor(from.getBorderColor()))));
         }

         ChartDescriptor desc = DESC.apply(source);

         for(int i = 0; desc != null && i < desc.getTargetCount(); i++) {
            int index = i;
            Loc<GraphTarget> target =
               DESC.then(d -> index < d.getTargetCount() ? d.getTarget(index) : null);
            text("chart:target:" + i, target.then(GraphTarget::getTextFormat), false);
         }
      }

      void chartInfo() {
         VSChartInfo cinfo = CHART.apply(source);

         if(cinfo == null) {
            return;
         }

         axis("chart:axis", CHART.then(VSChartInfo::getAxisDescriptor));
         axis("chart:axis2", CHART.then(VSChartInfo::getAxisDescriptor2));
         axis("chart:radar", CHART.then(c -> c instanceof RadarChartInfo ?
            ((RadarChartInfo) c).getLabelAxisDescriptor() : null));

         refs("chart:x", VSChartInfo::getXFields);
         refs("chart:y", VSChartInfo::getYFields);
         refs("chart:group", VSChartInfo::getGroupFields);
         refs("chart:aes", c -> c.getAestheticRefs(false));
         refs("chart:node", c -> c instanceof RelationChartInfo ? new Object[] {
            ((RelationChartInfo) c).getNodeColorField(),
            ((RelationChartInfo) c).getNodeSizeField() } : null);
         refs("chart:geo", c -> c instanceof VSMapInfo ? ((VSMapInfo) c).getGeoFields() : null);
         refs("chart:field", VSChartInfo::getFields);

         frames("chart:frame", CHART.then(VSChartInfo::getColorFrameWrapper),
                (info, frame) -> info.getVSChartInfo().setColorFrame(frame));
         frames("chart:node:frame", CHART.then(c -> c instanceof RelationChartInfo ?
            ((RelationChartInfo) c).getNodeColorFrameWrapper() : null), null);
      }

      void flags() {
         slots.add(ChromeSlot.tiered(
            "chart:barCornerRadius", false, true,
            info -> info instanceof ChartVSAssemblyInfo &&
               ((ChartVSAssemblyInfo) info).isUserBarCornerRadius(),
            info -> false,
            info -> PLOT.then(PlotDescriptor::getBarCornerRadius).on(info),
            info -> PLOT.then(PlotDescriptor::getBarCornerRadius).on(info),
            info -> {
               if(info instanceof ChartVSAssemblyInfo) {
                  ((ChartVSAssemblyInfo) info).setUserBarCornerRadius(false);
               }
            }, null));
         slots.add(ChromeSlot.tiered(
            "chart:smoothLines", false, true,
            info -> info instanceof ChartVSAssemblyInfo &&
               ((ChartVSAssemblyInfo) info).isUserSmoothLines(),
            info -> false,
            info -> PLOT.then(PlotDescriptor::isSmoothLines).on(info),
            info -> PLOT.then(PlotDescriptor::isSmoothLines).on(info),
            info -> {
               if(info instanceof ChartVSAssemblyInfo) {
                  ((ChartVSAssemblyInfo) info).setUserSmoothLines(false);
               }
            }, null));
      }

      // every entry of a list, addressed by its index
      private void refs(String prefix, Function<VSChartInfo, Object[]> list) {
         Object[] items = list.apply(CHART.apply(source));

         for(int i = 0; items != null && i < items.length; i++) {
            int index = i;
            Loc<Object> item = CHART.then(c -> {
               Object[] all = list.apply(c);
               return all != null && index < all.length ? all[index] : null;
            });
            ref(prefix + "[" + i + "]=" + nameOf(items[i]), item);
         }
      }

      private void ref(String key, Loc<?> loc) {
         Object item = loc.apply(source);

         if(item instanceof ChartRef) {
            Loc<ChartRef> ref = loc.then(r -> r instanceof ChartRef ? (ChartRef) r : null);
            text(key + ":text", ref.then(ChartRef::getTextFormat), false);
            axis(key + ":axis", ref.then(ChartRef::getAxisDescriptor));
         }

         if(item instanceof ChartAggregateRef) {
            Loc<ChartAggregateRef> aggr =
               loc.then(r -> r instanceof ChartAggregateRef ? (ChartAggregateRef) r : null);
            text(key + ":textField", aggr.then(ChartAggregateRef::getTextField)
               .then(AestheticRef::getDataRef)
               .then(r -> r instanceof ChartRef ? ((ChartRef) r).getTextFormat() : null), false);
            frames(key + ":frame", aggr.then(ChartAggregateRef::getColorFrameWrapper), null);
         }

         if(item instanceof AestheticRef) {
            Loc<AestheticRef> aes =
               loc.then(r -> r instanceof AestheticRef ? (AestheticRef) r : null);
            text(key + ":legend", aes.then(AestheticRef::getLegendDescriptor)
               .then(LegendDescriptor::getContentTextFormat), true);
            ref(key + ":data", aes.then(AestheticRef::getDataRef));
            frames(key + ":frame", aes.then(AestheticRef::getVisualFrameWrapper), (info, frame) -> {
               AestheticRef target = aes.apply(info);

               if(target != null) {
                  target.setVisualFrame(frame);
               }
            });
         }
      }

      private void axis(String key, Loc<AxisDescriptor> loc) {
         AxisDescriptor axis = loc.apply(source);

         if(!first(axis)) {
            return;
         }

         text(key + ":labels", loc.then(AxisDescriptor::getAxisLabelTextFormat), true);
         List<String> columns = axis.getColumnLabelTextFormatColumns().stream()
            .filter(Objects::nonNull).sorted().toList();

         for(String column : columns) {
            text(key + ":labels:column=" + column,
                 loc.then(a -> a.getColumnLabelTextFormat(column)), true);
         }

         // a new chart's axis when the fresh chart lacks this one
         slots.add(ChromeSlot.plain(key + ":line", (info, fresh) -> {
            AxisDescriptor to = loc.on(info);
            AxisDescriptor from = loc.on(fresh);

            if(from == null) {
               from = CHART.then(VSChartInfo::getAxisDescriptor).on(fresh);
            }

            if(to != null && from != null) {
               to.setLineColor(from.getLineColor());
            }
         }));
      }

      private void frames(String key, Loc<? extends VisualFrameWrapper> wrapper,
                          BiConsumer<ChartVSAssemblyInfo, ColorFrame> replace)
      {
         Loc<VisualFrame> loc = wrapper.then(VisualFrameWrapper::getVisualFrame);
         VisualFrame frame = loc.apply(source);

         if(!first(frame)) {
            return;
         }

         if(frame instanceof CategoricalColorFrame) {
            Loc<CategoricalColorFrame> palette = loc.then(
               f -> f instanceof CategoricalColorFrame ? (CategoricalColorFrame) f : null);

            List<Integer> indexes = userColors((CategoricalColorFrame) frame).keySet().stream()
               .filter(Objects::nonNull).sorted().toList();

            for(int index : indexes) {
               slots.add(paletteSlot(key + ":palette:" + index, palette, wrapper, index));
            }

            // derived colours are recomputed at render
            slots.add(ChromeSlot.plain(key + ":values", (info, fresh) -> {
               CategoricalColorFrame target = palette.on(info);

               if(target != null) {
                  target.clearStatic();
                  unmarkChanged(target, wrapper.on(info));
               }
            }));
         }
         else if(frame instanceof StaticColorFrame) {
            slots.add(staticSlot(key + ":static", loc.then(
               f -> f instanceof StaticColorFrame ? (StaticColorFrame) f : null), wrapper));
         }
         else if(frame instanceof LinearColorFrame && replace != null) {
            slots.add(ChromeSlot.plain(key + ":ramp", (info, fresh) -> {
               VisualFrame current = loc.on(info);
               LinearColorFrame ramp =
                  VSChartPaletteDefaults.defaultLinearFrame(VizContext.of(fresh));

               if(current instanceof LinearColorFrame && current.getClass() != ramp.getClass()) {
                  replace.accept((ChartVSAssemblyInfo) info, ramp);
               }
            }));
         }
      }

      private void text(String site, Loc<CompositeTextFormat> loc, boolean wholeFont) {
         if(first(loc.apply(source))) {
            for(TextProperty prop : TextProperty.values()) {
               slots.add(textSlot(site, loc, prop, wholeFont));
            }
         }
      }

      // the first path to an object owns its slots, so a shared one is not visited twice
      private boolean first(Object target) {
         return target != null && seen.add(target);
      }

      private final ChartVSAssemblyInfo source;
      private final List<ChromeSlot> slots = new ArrayList<>();
      private final Set<Object> seen = Collections.newSetFromMap(new IdentityHashMap<>());
   }

   private static ChromeSlot textSlot(String site, Loc<CompositeTextFormat> loc,
                                      TextProperty prop, boolean wholeFont)
   {
      Loc<TextFormat> user = loc.then(CompositeTextFormat::getUserDefinedFormat);
      BiConsumer<VSAssemblyInfo, VSAssemblyInfo> reset = prop == TextProperty.FONT && !wholeFont ?
         (info, fresh) -> keepSizeAndStyle(loc.on(info)) : null;

      // a label's set font or colour changes the render whatever its value
      boolean residueEligible = wholeFont ||
         prop != TextProperty.FONT && prop != TextProperty.COLOR;

      return ChromeSlot.tiered(
         site + ":" + prop, false, residueEligible,
         info -> {
            TextFormat fmt = user.on(info);
            return fmt != null && isDefined(fmt, prop);
         },
         info -> false,
         info -> {
            TextFormat fmt = user.on(info);
            return fmt == null ? null : authorValue(fmt, prop);
         },
         info -> {
            CompositeTextFormat fmt = loc.on(info);
            return fmt == null ? null : resolvedValue(fmt, prop);
         },
         info -> {
            TextFormat fmt = user.on(info);

            if(fmt != null) {
               clear(fmt, prop);
            }
         },
         reset);
   }

   private static boolean isDefined(TextFormat fmt, TextProperty prop) {
      switch(prop) {
      case FONT:
         return fmt.isFontDefined();
      case COLOR:
         return fmt.isColorDefined();
      case BACKGROUND:
         return fmt.isBackgroundDefined();
      default:
         return fmt.isAlphaDefined();
      }
   }

   private static Object authorValue(TextFormat fmt, TextProperty prop) {
      switch(prop) {
      case FONT:
         return fmt.getFont();
      case COLOR:
         return fmt.getColor();
      case BACKGROUND:
         return fmt.getBackground();
      default:
         return fmt.getAlpha();
      }
   }

   private static Object resolvedValue(CompositeTextFormat fmt, TextProperty prop) {
      switch(prop) {
      case FONT:
         return fmt.getFont();
      case COLOR:
         return fmt.getColor();
      case BACKGROUND:
         return fmt.getBackground();
      default:
         return fmt.getAlpha();
      }
   }

   private static void clear(TextFormat fmt, TextProperty prop) {
      switch(prop) {
      case FONT:
         fmt.setFont(null, false);
         break;
      case COLOR:
         fmt.setColor(null, false);
         break;
      case BACKGROUND:
         fmt.setBackground(null, false);
         break;
      default:
         fmt.setAlpha(100, false);
         break;
      }
   }

   // keeps the author's size and style bits in the format's default family
   private static void keepSizeAndStyle(CompositeTextFormat fmt) {
      TextFormat user = fmt == null ? null : fmt.getUserDefinedFormat();

      if(user == null || !user.isFontDefined()) {
         return;
      }

      Font author = user.getFont();

      if(author == null) {
         user.setFont(null, false);
         return;
      }

      TextFormat def = fmt.getDefaultFormat();
      String family = FormatChromeSlots.familyOf(def == null ? null : def.getFont());

      if(family != null) {
         user.setFont(FormatChromeSlots.withFamily(author, family), true);
      }
   }

   private static ChromeSlot valueSlot(String key, Loc<CompositeValue<?>> loc) {
      return ChromeSlot.tiered(
         key, false, true,
         info -> {
            CompositeValue<?> value = loc.on(info);
            return value != null && value.hasUserValue();
         },
         info -> false,
         info -> {
            CompositeValue<?> value = loc.on(info);
            return value == null ? null : value.get(CompositeValue.Type.USER);
         },
         info -> {
            CompositeValue<?> value = loc.on(info);
            return value == null ? null : value.get();
         },
         info -> {
            CompositeValue<?> value = loc.on(info);

            if(value != null) {
               value.resetValue(CompositeValue.Type.USER);
            }
         },
         null);
   }

   private static ChromeSlot paletteSlot(String key, Loc<CategoricalColorFrame> loc,
                                         Loc<? extends VisualFrameWrapper> wrapper, int index)
   {
      Consumer<VSAssemblyInfo> clear = info -> {
         CategoricalColorFrame frame = loc.on(info);

         if(frame != null && userColors(frame).containsKey(index)) {
            Map<Integer, Color> colors = new HashMap<>(userColors(frame));
            colors.remove(index);
            frame.setUserColors(colors);
         }
      };

      return ChromeSlot.tiered(
         key, false, true,
         info -> {
            CategoricalColorFrame frame = loc.on(info);
            return frame != null && userColors(frame).get(index) != null;
         },
         info -> false,
         info -> {
            CategoricalColorFrame frame = loc.on(info);
            return frame == null ? null : userColors(frame).get(index);
         },
         info -> {
            CategoricalColorFrame frame = loc.on(info);
            return frame == null ? null : paletteColor(frame, index);
         },
         clear,
         (info, fresh) -> {
            clear.accept(info);
            CategoricalColorFrame frame = loc.on(info);

            if(frame != null) {
               unmarkChanged(frame, wrapper.on(info));
            }
         });
   }

   // unchanged, as on a new chart, once no author colour is left
   private static void unmarkChanged(CategoricalColorFrame frame, VisualFrameWrapper wrapper) {
      boolean authored = !userColors(frame).isEmpty() ||
         frame.getStaticValues().stream().anyMatch(value -> !frame.isDerived(value));

      if(wrapper != null && !authored) {
         wrapper.setChanged(false);
      }
   }

   // as the render picks it: author, then CSS, then the palette
   private static Color paletteColor(CategoricalColorFrame frame, int index) {
      Color color = userColors(frame).get(index);
      Map<Integer, Color> css = frame.getCSSColors();

      if(color == null && css != null) {
         color = css.get(index);
      }

      if(color == null && index >= 0 && frame.getColorCount() > 0) {
         color = frame.getDefaultColor(index % frame.getColorCount());
      }

      return color;
   }

   private static Map<Integer, Color> userColors(CategoricalColorFrame frame) {
      Map<Integer, Color> colors = frame.getUserColors();
      return colors == null ? Map.of() : colors;
   }

   private static ChromeSlot staticSlot(String key, Loc<StaticColorFrame> loc,
                                        Loc<? extends VisualFrameWrapper> wrapper)
   {
      Consumer<VSAssemblyInfo> clear = info -> {
         StaticColorFrame frame = loc.on(info);

         if(frame != null) {
            frame.setUserColor(null);
         }
      };

      return ChromeSlot.tiered(
         key, false, true,
         info -> {
            StaticColorFrame frame = loc.on(info);
            return frame != null && frame.getUserColor() != null;
         },
         info -> false,
         info -> {
            StaticColorFrame frame = loc.on(info);
            return frame == null ? null : frame.getUserColor();
         },
         info -> {
            StaticColorFrame frame = loc.on(info);
            return frame == null ? null : frame.getColor();
         },
         clear,
         (info, fresh) -> {
            clear.accept(info);
            VisualFrameWrapper target = wrapper.on(info);

            if(target != null) {
               target.setChanged(false);
            }
         });
   }

   private static <T> BiConsumer<VSAssemblyInfo, VSAssemblyInfo> copy(Loc<T> loc,
                                                                      BiConsumer<T, T> copy)
   {
      return (info, fresh) -> {
         T to = loc.on(info);
         T from = loc.on(fresh);

         if(to != null && from != null) {
            copy.accept(to, from);
         }
      };
   }

   private static String nameOf(Object item) {
      if(item instanceof AestheticRef) {
         DataRef ref = ((AestheticRef) item).getDataRef();
         return ref instanceof VSDataRef ? ((VSDataRef) ref).getFullName() :
            ref == null ? "" : ref.getName();
      }

      return item instanceof VSDataRef ? ((VSDataRef) item).getFullName() : "";
   }
}
