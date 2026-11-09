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

import inetsoft.graph.aesthetic.CategoricalColorFrame;
import inetsoft.graph.internal.DimensionD;
import inetsoft.graph.aesthetic.SpectralColorFrame;
import inetsoft.graph.aesthetic.StaticColorFrame;
import inetsoft.graph.aesthetic.VisualFrame;
import inetsoft.report.StyleConstants;
import inetsoft.report.StyleFont;
import inetsoft.sree.SreeEnv;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.LibManagerTestConfiguration;
import inetsoft.test.SreeHome;
import inetsoft.uql.CompositeValue;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.graph.*;
import inetsoft.uql.viewsheet.graph.aesthetic.CategoricalColorFrameWrapper;
import inetsoft.uql.viewsheet.graph.aesthetic.StaticColorFrameWrapper;
import inetsoft.util.css.CSSDictionary;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.awt.Color;
import java.awt.Font;
import java.awt.geom.Point2D;
import java.util.HashSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, LibManagerTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class ChartChromeSlotsTest {
   @AfterEach
   void reset() {
      SreeEnv.setProperty("viewsheet.modernVisualization", null);
      SreeEnv.setProperty("viewsheet.darkMode", null);
      CSSDictionary.resetDictionaryCache();
   }

   private static ChartVSAssemblyInfo chart(String name) {
      Viewsheet vs = new Viewsheet();
      ChartVSAssembly chart = new ChartVSAssembly(vs, name);
      ChartVSAssemblyInfo info = (ChartVSAssemblyInfo) chart.getVSAssemblyInfo();
      info.initDefaultFormat();
      vs.addAssembly(chart);
      return info;
   }

   private static ChartVSAssemblyInfo chart() {
      ChartVSAssemblyInfo info = chart("Chart1");
      info.setVizMark(null);
      return info;
   }

   private static ChartVSAssemblyInfo fresh(VizMark mark) {
      ChartVSAssemblyInfo info = chart("Fresh");
      info.setVizMark(mark);
      return info;
   }

   private static void resetAll(VSAssemblyInfo info, VSAssemblyInfo fresh) {
      for(ChromeSlot slot : info.chromeSlots()) {
         slot.reset(info, fresh);
      }
   }

   private static ChromeSlot find(VSAssemblyInfo info, String key) {
      return info.chromeSlots().stream().filter(s -> s.key().equals(key)).findFirst()
         .orElseThrow(() -> new AssertionError("no slot " + key));
   }

   private static VSChartDimensionRef dimension(String column) {
      VSChartDimensionRef dim = new VSChartDimensionRef();
      dim.setGroupColumnValue(column);
      return dim;
   }

   private static VSChartAggregateRef aggregate(String column) {
      VSChartAggregateRef aggr = new VSChartAggregateRef();
      aggr.setColumnValue(column);
      return aggr;
   }

   private static VSAestheticRef colorBinding(ChartVSAssemblyInfo info, ChartRef field,
                                              VisualFrame frame)
   {
      VSAestheticRef colorRef = new VSAestheticRef();
      colorRef.setDataRef(field);
      colorRef.setVisualFrame(frame);
      info.getVSChartInfo().setColorField(colorRef);
      return colorRef;
   }

   @Test
   void labelFontAndColourAreNeverResidue() {
      ChartVSAssemblyInfo info = chart();

      for(String site : new String[] { "chart:plot:text", "chart:axis:labels" }) {
         boolean label = site.equals("chart:plot:text");

         for(ChartChromeSlots.TextProperty prop : ChartChromeSlots.TextProperty.values()) {
            boolean setBranch = label && (prop == ChartChromeSlots.TextProperty.FONT ||
               prop == ChartChromeSlots.TextProperty.COLOR);
            assertEquals(!setBranch, find(info, site + ":" + prop).residueEligible(),
                         site + ":" + prop);
         }
      }
   }

   @Test
   void aUserGridColorIsATieredSlot() {
      ChartVSAssemblyInfo info = chart();
      PlotDescriptor plot = info.getChartDescriptor().getPlotDescriptor();
      Color grey = new Color(0xd2d2d2);
      plot.setXGridColor(grey, CompositeValue.Type.USER);

      ChromeSlot slot = find(info, "chart:plot:xGridColor");
      assertTrue(slot.isAuthorSet(info));
      assertFalse(slot.objectLevel());
      assertTrue(slot.residueEligible());
      assertEquals(grey, slot.authorValue(info));
      assertEquals(grey, slot.resolvedValue(info));

      slot.clearAuthor(info);
      assertFalse(plot.chromeValue("xGridColor").hasUserValue());
      assertThrows(IllegalArgumentException.class, () -> plot.chromeValue("trendline"));
   }

   @Test
   void authorReferenceLinesSurviveButGridlineStylesReset() {
      ChartVSAssemblyInfo info = chart();
      PlotDescriptor plot = info.getChartDescriptor().getPlotDescriptor();
      plot.setDiagonalStyle(StyleConstants.THIN_LINE, CompositeValue.Type.USER);
      plot.setQuadrantStyle(StyleConstants.DOT_LINE, CompositeValue.Type.USER);
      plot.setXGridStyle(StyleConstants.THIN_LINE, CompositeValue.Type.USER);

      resetAll(info, fresh(VizMark.MODERN_LIGHT));

      assertEquals(StyleConstants.THIN_LINE, plot.getDiagonalStyle());
      assertEquals(StyleConstants.DOT_LINE, plot.getQuadrantStyle());
      assertFalse(plot.chromeValue("xGridStyle").hasUserValue());
      assertThrows(IllegalArgumentException.class, () -> plot.chromeValue("diagonalStyle"));
      assertThrows(IllegalArgumentException.class, () -> plot.chromeValue("quadrantStyle"));
   }

   @Test
   void axisLabelFontsResetWhole() {
      ChartVSAssemblyInfo info = chart();
      Font author = new StyleFont("Arial", Font.BOLD, 16);
      AxisDescriptor axis = info.getVSChartInfo().getAxisDescriptor();
      CompositeTextFormat labels = axis.getAxisLabelTextFormat();
      labels.getUserDefinedFormat().setFont(author);
      CompositeTextFormat column = new CompositeTextFormat();
      column.getUserDefinedFormat().setFont(author);
      axis.setColumnLabelTextFormat("Sales", column);
      VSChartDimensionRef state = dimension("State");
      state.getAxisDescriptor().getAxisLabelTextFormat().getUserDefinedFormat().setFont(author);
      info.getVSChartInfo().addXField(state);
      TitleDescriptor xTitle = info.getChartDescriptor().getTitlesDescriptor()
         .getXTitleDescriptor();
      xTitle.getTextFormat().getUserDefinedFormat().setFont(author);

      resetAll(info, fresh(VizMark.MODERN_LIGHT));

      assertFalse(labels.getUserDefinedFormat().isFontDefined());
      assertFalse(column.getUserDefinedFormat().isFontDefined());
      assertFalse(state.getAxisDescriptor().getAxisLabelTextFormat().getUserDefinedFormat()
                     .isFontDefined());
      assertFalse(xTitle.getTextFormat().getUserDefinedFormat().isFontDefined());
   }

   @Test
   void dataLabelFontsKeepSizeAndStyle() {
      ChartVSAssemblyInfo info = chart();
      Font author = new StyleFont("Arial", Font.BOLD, 16);
      CompositeTextFormat text =
         info.getChartDescriptor().getPlotDescriptor().getTextFormat();
      text.getUserDefinedFormat().setFont(author);
      text.getUserDefinedFormat().setColor(Color.RED);
      VSChartAggregateRef sales = aggregate("Sales");
      sales.getTextFormat().getUserDefinedFormat().setFont(author);
      info.getVSChartInfo().addYField(sales);
      String family = FormatChromeSlots.familyOf(text.getDefaultFormat().getFont());
      assertNotEquals("Arial", family, "fixture: the default family must differ");

      resetAll(info, fresh(VizMark.MODERN_LIGHT));

      for(CompositeTextFormat fmt : new CompositeTextFormat[] { text, sales.getTextFormat() }) {
         TextFormat user = fmt.getUserDefinedFormat();
         assertTrue(user.isFontDefined());
         assertEquals(16, user.getFont().getSize());
         assertEquals(Font.BOLD, user.getFont().getStyle());
         assertEquals(FormatChromeSlots.familyOf(fmt.getDefaultFormat().getFont()),
                      FormatChromeSlots.familyOf(user.getFont()));
      }

      assertFalse(text.getUserDefinedFormat().isColorDefined());
   }

   @Test
   void legendPositionResetsToTheFreshLayout() {
      ChartVSAssemblyInfo info = chart();
      LegendsDescriptor legends = info.getChartDescriptor().getLegendsDescriptor();
      legends.setLayout(LegendsDescriptor.BOTTOM);
      legends.setRoundCorners(false);
      legends.setBorderColor(Color.RED, CompositeValue.Type.USER);

      resetAll(info, null);
      assertEquals(LegendsDescriptor.BOTTOM, legends.getLayout(),
                   "a plain slot does nothing without a fresh instance");

      resetAll(info, fresh(VizMark.MODERN_LIGHT));
      assertEquals(LegendsDescriptor.RIGHT, legends.getLayout());
      assertTrue(legends.isRoundCorners());
      assertFalse(legends.chromeValue("borderColor").hasUserValue());
   }

   // a size set for a bottom legend squeezes the plot once the legend moves right
   @Test
   void legendSizesResetToANewChartsWithThePosition() {
      ChartVSAssemblyInfo info = chart();
      LegendsDescriptor legends = info.getChartDescriptor().getLegendsDescriptor();
      legends.setLayout(LegendsDescriptor.BOTTOM);
      legends.setPreferredSize(new DimensionD(0.47, 0.08));
      LegendDescriptor color = legends.getColorLegendDescriptor();
      color.setPreferredSize(new DimensionD(0.12, 0.22));
      color.setPosition(new Point2D.Double(0.87, 0.01));
      color.setPlotPosition(new Point2D.Double(0.5, 0.5));
      LegendDescriptor bound = colorBinding(info, dimension("Region"), new CategoricalColorFrame())
         .getLegendDescriptor();
      bound.setPreferredSize(new DimensionD(0.2, 0.3));
      bound.setPosition(new Point2D.Double(0.4, 0.4));
      ChartVSAssemblyInfo fresh = fresh(VizMark.MODERN_LIGHT);
      LegendsDescriptor created = fresh.getChartDescriptor().getLegendsDescriptor();
      assertNull(created.getPreferredSize(), "fixture: a new chart sets no legend size");
      assertNull(created.getColorLegendDescriptor().getPreferredSize());

      resetAll(info, fresh);

      assertEquals(LegendsDescriptor.RIGHT, legends.getLayout());
      assertNull(legends.getPreferredSize());

      for(LegendDescriptor legend : new LegendDescriptor[] { color, bound }) {
         assertNull(legend.getPreferredSize());
         assertNull(legend.getPosition());
         assertNull(legend.getPlotPosition());
      }
   }

   @Test
   void measureTitleFontsResetWhole() {
      ChartVSAssemblyInfo info = chart();
      VSChartAggregateRef sales = aggregate("Sales");
      sales.getTitleDescriptor().getTextFormat().getUserDefinedFormat()
         .setFont(new StyleFont("SansSerif", Font.BOLD, 12));
      info.getVSChartInfo().addYField(sales);

      resetAll(info, fresh(VizMark.MODERN_LIGHT));

      assertFalse(sales.getTitleDescriptor().getTextFormat().getUserDefinedFormat()
                     .isFontDefined());
   }

   // a loaded or cloned chart holds its runtime group fields as separate copies
   @Test
   void runtimeGroupFieldsLoseTheirAuthorFonts() {
      ChartVSAssemblyInfo source = chart();
      VSChartAggregateRef group = aggregate("Population");
      group.getAxisDescriptor().getAxisLabelTextFormat().getUserDefinedFormat()
         .setFont(new StyleFont("Arial", Font.BOLD, 10));
      source.getVSChartInfo().addGroupField(group);
      ChartVSAssemblyInfo info = (ChartVSAssemblyInfo) source.clone();
      VSChartInfo cinfo = info.getVSChartInfo();
      ChartRef runtime = cinfo.getRTGroupFields()[0];
      assertNotSame(cinfo.getGroupFields()[0], runtime, "fixture: a separate runtime copy");

      resetAll(info, fresh(VizMark.MODERN_LIGHT));

      for(ChartRef ref : new ChartRef[] { cinfo.getGroupFields()[0], runtime }) {
         assertFalse(ref.getAxisDescriptor().getAxisLabelTextFormat().getUserDefinedFormat()
                        .isFontDefined(), String.valueOf(ref));
      }
   }

   @Test
   void authorPaletteEntriesAndValueColorsClear() {
      ChartVSAssemblyInfo info = chart();
      CategoricalColorFrame frame = new CategoricalColorFrame();
      frame.setUserColor(0, Color.RED);
      frame.setColor("West", Color.RED);
      colorBinding(info, dimension("Region"), frame);

      ChromeSlot entry = info.chromeSlots().stream()
         .filter(s -> s.key().endsWith(":palette:0")).findFirst().orElseThrow();
      assertTrue(entry.isAuthorSet(info));
      assertTrue(entry.residueEligible());
      assertEquals(Color.RED, entry.authorValue(info));

      resetAll(info, fresh(VizMark.MODERN_LIGHT));

      CategoricalColorFrame after = (CategoricalColorFrame)
         info.getVSChartInfo().getColorField().getVisualFrame();
      assertTrue(after.getUserColors().isEmpty());
      assertFalse(after.isStatic("West"));
   }

   @Test
   void aStaticAuthorColorClears() {
      ChartVSAssemblyInfo info = chart();
      StaticColorFrame chartColor = (StaticColorFrame) info.getVSChartInfo().getColorFrame();
      chartColor.setUserColor(Color.RED);
      VSChartAggregateRef sales = aggregate("Sales");
      ((StaticColorFrame) sales.getColorFrame()).setUserColor(Color.RED);
      info.getVSChartInfo().addYField(sales);
      StaticColorFrame bound = new StaticColorFrame();
      bound.setUserColor(Color.RED);
      colorBinding(info, dimension("Region"), bound);

      resetAll(info, fresh(VizMark.MODERN_LIGHT));

      assertNull(chartColor.getUserColor());
      assertNull(((StaticColorFrame) sales.getColorFrame()).getUserColor());
      assertNull(bound.getUserColor());
   }

   private record AuthoredColors(ChartVSAssemblyInfo info, StaticColorFrameWrapper chartColor,
                                 StaticColorFrameWrapper measureColor,
                                 CategoricalColorFrameWrapper palette, List<ChromeSlot> slots)
   {
   }

   // author colours set through the wrappers, as the binding pane does
   private static AuthoredColors authoredColors() {
      ChartVSAssemblyInfo info = chart();
      StaticColorFrameWrapper chartColor =
         (StaticColorFrameWrapper) info.getVSChartInfo().getColorFrameWrapper();
      chartColor.setUserColor(Color.RED);
      VSChartAggregateRef sales = aggregate("Sales");
      StaticColorFrameWrapper measureColor = (StaticColorFrameWrapper) sales.getColorFrameWrapper();
      measureColor.setUserColor(Color.RED);
      info.getVSChartInfo().addYField(sales);
      VSAestheticRef colorRef =
         colorBinding(info, dimension("Region"), new CategoricalColorFrame());
      CategoricalColorFrameWrapper palette =
         (CategoricalColorFrameWrapper) colorRef.getVisualFrameWrapper();
      palette.setColor(2, Color.RED);
      List<ChromeSlot> slots = info.chromeSlots().stream()
         .filter(s -> s.key().endsWith(":frame:static") || s.key().endsWith(":frame:palette:2"))
         .toList();

      assertEquals(3, slots.size(), "chart, measure and palette slots");
      assertTrue(chartColor.isChanged());
      assertTrue(measureColor.isChanged());
      assertTrue(palette.isChanged());
      return new AuthoredColors(info, chartColor, measureColor, palette, slots);
   }

   @Test
   void aResetLeavesTheColourFramesUnchanged() {
      AuthoredColors authored = authoredColors();

      for(ChromeSlot slot : authored.slots()) {
         slot.reset(authored.info(), fresh(VizMark.MODERN_LIGHT));
      }

      assertNull(authored.chartColor().getUserColor());
      assertNull(authored.measureColor().getUserColor());
      assertNull(authored.palette().getUserColors().get(2));
      assertFalse(authored.chartColor().isChanged());
      assertFalse(authored.measureColor().isChanged());
      assertFalse(authored.palette().isChanged());
   }

   @Test
   void aResidueClearKeepsTheChangedFlag() {
      AuthoredColors authored = authoredColors();

      for(ChromeSlot slot : authored.slots()) {
         slot.clearAuthor(authored.info());
      }

      assertNull(authored.chartColor().getUserColor());
      assertNull(authored.measureColor().getUserColor());
      assertNull(authored.palette().getUserColors().get(2));
      assertTrue(authored.chartColor().isChanged());
      assertTrue(authored.measureColor().isChanged());
      assertTrue(authored.palette().isChanged());
   }

   @Test
   void barCornerAndSmoothLineFlagsClear() {
      ChartVSAssemblyInfo info = chart();
      info.setUserBarCornerRadius(true);
      info.setUserSmoothLines(true);

      resetAll(info, fresh(VizMark.MODERN_LIGHT));

      assertFalse(info.isUserBarCornerRadius());
      assertFalse(info.isUserSmoothLines());
   }

   @Test
   void anAuthorRampIsReplacedByTheMarksRamp() {
      ChartVSAssemblyInfo info = chart();
      colorBinding(info, aggregate("Sales"), new SpectralColorFrame());

      resetAll(info, fresh(VizMark.MODERN_LIGHT));

      assertEquals(
         VSChartPaletteDefaults.defaultLinearFrame(VizContext.of(VizMark.MODERN_LIGHT)).getClass(),
         info.getVSChartInfo().getColorField().getVisualFrame().getClass());
   }

   @Test
   void aSlotAddressesTheInfoItIsGiven() {
      ChartVSAssemblyInfo info = chart();
      CompositeTextFormat labels =
         info.getVSChartInfo().getAxisDescriptor().getAxisLabelTextFormat();
      labels.getUserDefinedFormat().setColor(Color.RED);
      info.getVSChartInfo().addXField(dimension("State"));
      info.getVSChartInfo().addYField(aggregate("Sales"));
      CategoricalColorFrame palette = new CategoricalColorFrame();
      palette.setUserColor(1, Color.RED);
      colorBinding(info, dimension("Region"), palette);
      ChartVSAssemblyInfo copy = (ChartVSAssemblyInfo) info.clone();
      ChromeSlot slot = find(info, "chart:axis:labels:COLOR");
      List<String> keys = info.chromeSlots().stream().map(ChromeSlot::key).toList();

      assertEquals(keys.size(), new HashSet<>(keys).size(), "keys are unique");
      List<String> copyKeys = copy.chromeSlots().stream().map(ChromeSlot::key).toList();
      // a clone holds runtime copies of the fields this info shares with its design fields
      assertEquals(keys, copyKeys.stream().filter(k -> !k.startsWith("chart:rt:")).toList());
      assertTrue(copyKeys.stream().anyMatch(k -> k.startsWith("chart:rt:")),
                 "fixture: the clone's runtime copies get their own slots");

      slot.clearAuthor(copy);

      assertFalse(copy.getVSChartInfo().getAxisDescriptor().getAxisLabelTextFormat()
                     .getUserDefinedFormat().isColorDefined());
      assertTrue(labels.getUserDefinedFormat().isColorDefined());
   }
}
