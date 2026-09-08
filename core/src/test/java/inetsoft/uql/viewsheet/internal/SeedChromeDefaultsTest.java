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
import inetsoft.report.StyleConstants;
import inetsoft.report.TableDataPath;
import inetsoft.sree.SreeEnv;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.LibManagerTestConfiguration;
import inetsoft.test.SreeHome;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.graph.PlotDescriptor;
import inetsoft.uql.viewsheet.graph.VSAestheticRef;
import inetsoft.uql.viewsheet.graph.VSChartAggregateRef;
import inetsoft.util.Tool;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.awt.Color;
import java.awt.Insets;
import java.io.PrintWriter;
import java.io.StringReader;
import java.io.StringWriter;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The creation seeds, pinned. Written before the seedChromeDefaults extraction as its safety net:
 * every assertion here describes behaviour that must survive the refactor unchanged.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, LibManagerTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class SeedChromeDefaultsTest {
   @AfterEach
   void reset() {
      SreeEnv.setProperty("viewsheet.modernVisualization", null);
      SreeEnv.setProperty("viewsheet.darkMode", null);
      SreeEnv.setProperty("viewsheet.density", null);
   }

   private void gateOn() {
      SreeEnv.setProperty("viewsheet.modernVisualization", "true");
   }

   private void gateOff() {
      SreeEnv.setProperty("viewsheet.modernVisualization", "false");
   }

   private static VSFormat objectDefault(VSAssemblyInfo info) {
      return info.getFormatInfo().getFormat(VSAssemblyInfo.OBJECTPATH).getDefaultFormat();
   }

   private static VSCompositeFormat titleFormat(VSAssemblyInfo info) {
      return info.getFormatInfo().getFormat(VSAssemblyInfo.TITLEPATH);
   }

   private TableVSAssemblyInfo newTable() {
      Viewsheet vs = new Viewsheet();
      TableVSAssembly table = new TableVSAssembly(vs, "Table1");
      table.getVSAssemblyInfo().initDefaultFormat();
      return (TableVSAssemblyInfo) table.getVSAssemblyInfo();
   }

   private ChartVSAssemblyInfo newChart() {
      Viewsheet vs = new Viewsheet();
      ChartVSAssembly chart = new ChartVSAssembly(vs, "Chart1");
      chart.getVSAssemblyInfo().initDefaultFormat();
      return (ChartVSAssemblyInfo) chart.getVSAssemblyInfo();
   }

   private TextVSAssemblyInfo newText() {
      Viewsheet vs = new Viewsheet();
      TextVSAssembly text = new TextVSAssembly(vs, "Text1");
      text.getVSAssemblyInfo().initDefaultFormat();
      return (TextVSAssemblyInfo) text.getVSAssemblyInfo();
   }

   private SelectionListVSAssemblyInfo newSelectionList() {
      Viewsheet vs = new Viewsheet();
      SelectionListVSAssembly list = new SelectionListVSAssembly(vs, "Selection1");
      list.getVSAssemblyInfo().initDefaultFormat();
      return (SelectionListVSAssemblyInfo) list.getVSAssemblyInfo();
   }

   private SelectionTreeVSAssemblyInfo newSelectionTree() {
      Viewsheet vs = new Viewsheet();
      SelectionTreeVSAssembly tree = new SelectionTreeVSAssembly(vs, "SelectionTree1");
      tree.getVSAssemblyInfo().initDefaultFormat();
      return (SelectionTreeVSAssemblyInfo) tree.getVSAssemblyInfo();
   }

   private TabVSAssemblyInfo newTab() {
      Viewsheet vs = new Viewsheet();
      TabVSAssembly tab = new TabVSAssembly(vs, "Tab1");
      tab.getVSAssemblyInfo().initDefaultFormat();
      return (TabVSAssemblyInfo) tab.getVSAssemblyInfo();
   }

   private TimeSliderVSAssemblyInfo newTimeSlider() {
      Viewsheet vs = new Viewsheet();
      TimeSliderVSAssembly slider = new TimeSliderVSAssembly(vs, "TimeSlider1");
      slider.getVSAssemblyInfo().initDefaultFormat();
      return (TimeSliderVSAssemblyInfo) slider.getVSAssemblyInfo();
   }

   private CurrentSelectionVSAssemblyInfo newCurrentSelection() {
      Viewsheet vs = new Viewsheet();
      CurrentSelectionVSAssembly container = new CurrentSelectionVSAssembly(vs, "Container1");
      container.getVSAssemblyInfo().initDefaultFormat();
      return (CurrentSelectionVSAssemblyInfo) container.getVSAssemblyInfo();
   }

   private SliderVSAssemblyInfo newSlider() {
      Viewsheet vs = new Viewsheet();
      SliderVSAssembly slider = new SliderVSAssembly(vs, "Slider1");
      slider.getVSAssemblyInfo().initDefaultFormat();
      return (SliderVSAssemblyInfo) slider.getVSAssemblyInfo();
   }

   private CheckBoxVSAssemblyInfo newCheckBox() {
      Viewsheet vs = new Viewsheet();
      CheckBoxVSAssembly checkBox = new CheckBoxVSAssembly(vs, "CheckBox1");
      checkBox.getVSAssemblyInfo().initDefaultFormat();
      return (CheckBoxVSAssemblyInfo) checkBox.getVSAssemblyInfo();
   }

   private RadioButtonVSAssemblyInfo newRadioButton() {
      Viewsheet vs = new Viewsheet();
      RadioButtonVSAssembly radio = new RadioButtonVSAssembly(vs, "RadioButton1");
      radio.getVSAssemblyInfo().initDefaultFormat();
      return (RadioButtonVSAssemblyInfo) radio.getVSAssemblyInfo();
   }

   private ComboBoxVSAssemblyInfo newComboBox() {
      Viewsheet vs = new Viewsheet();
      ComboBoxVSAssembly combo = new ComboBoxVSAssembly(vs, "ComboBox1");
      combo.getVSAssemblyInfo().initDefaultFormat();
      return (ComboBoxVSAssemblyInfo) combo.getVSAssemblyInfo();
   }

   private SpinnerVSAssemblyInfo newSpinner() {
      Viewsheet vs = new Viewsheet();
      SpinnerVSAssembly spinner = new SpinnerVSAssembly(vs, "Spinner1");
      spinner.getVSAssemblyInfo().initDefaultFormat();
      return (SpinnerVSAssemblyInfo) spinner.getVSAssemblyInfo();
   }

   private SubmitVSAssemblyInfo newSubmit() {
      Viewsheet vs = new Viewsheet();
      SubmitVSAssembly submit = new SubmitVSAssembly(vs, "Submit1");
      submit.getVSAssemblyInfo().initDefaultFormat();
      return (SubmitVSAssemblyInfo) submit.getVSAssemblyInfo();
   }

   private TextInputVSAssemblyInfo newTextInput() {
      Viewsheet vs = new Viewsheet();
      TextInputVSAssembly textInput = new TextInputVSAssembly(vs, "TextInput1");
      textInput.getVSAssemblyInfo().initDefaultFormat();
      return (TextInputVSAssemblyInfo) textInput.getVSAssemblyInfo();
   }

   private CalendarVSAssemblyInfo newCalendar() {
      Viewsheet vs = new Viewsheet();
      CalendarVSAssembly calendar = new CalendarVSAssembly(vs, "Calendar1");
      calendar.getVSAssemblyInfo().initDefaultFormat();
      return (CalendarVSAssemblyInfo) calendar.getVSAssemblyInfo();
   }

   private static VSFormat calendarPath(VSAssemblyInfo info, int type) {
      return info.getFormatInfo().getFormat(new TableDataPath(-1, type)).getDefaultFormat();
   }

   private static String colorValue(Color c) {
      return c == null ? null : String.format("0x%06x", c.getRGB() & 0xFFFFFF);
   }

   // ---- object border colour and card radius -------------------------------------------------

   @Test
   void gateOffTableTakesTheLegacyBorderAndNoRadius() {
      gateOff();
      VSFormat fmt = objectDefault(newTable());
      assertEquals(VSAssemblyInfo.DEFAULT_BORDER_COLOR,
                   fmt.getBorderColorsValue().topColor,
                   "gate off keeps the legacy 0xDADADA object border");
      assertEquals(0, fmt.getRoundCornerValue(), "gate off seeds square corners");
   }

   @Test
   void gateOnTableTakesTheModernBorderAndTheCardRadius() {
      gateOn();
      VizContext ctx = VizContext.ofGate();
      VSFormat fmt = objectDefault(newTable());
      assertEquals(VSObjectChromeDefaults.objectBorderColor(ctx),
                   fmt.getBorderColorsValue().topColor);
      assertEquals(VSObjectChromeDefaults.cardCornerRadius(), fmt.getRoundCornerValue());
   }

   @Test
   void aTextAssemblyIsNotACornerSeedTargetEvenUnderTheGate() {
      gateOn();
      VSFormat fmt = objectDefault(newText());
      assertEquals(0, fmt.getRoundCornerValue(),
                   "the entire base method is bypassed for Text, so isCornerSeedTarget() is " +
                   "never even reached");
   }

   @Test
   void aSelectionListIsACornerSeedTarget() {
      gateOn();
      assertEquals(VSObjectChromeDefaults.cardCornerRadius(),
                   objectDefault(newSelectionList()).getRoundCornerValue());
   }

   // ---- form-input round corner, seeded by each type's own override --------------------------
   // These six bypass the base chrome hook entirely (see VSAssemblyInfo.bypassesBaseChrome()) and
   // seed only round corner via their own seedChromeDefaults() override; border colour is left at
   // each type's pre-existing legacy value regardless of the gate, unlike the card types above.

   @Test
   void gateOffSliderTakesNoRadius() {
      gateOff();
      assertEquals(0, objectDefault(newSlider()).getRoundCornerValue());
   }

   @Test
   void gateOnSliderTakesTheCardRadius() {
      gateOn();
      assertEquals(VSObjectChromeDefaults.cardCornerRadius(),
                   objectDefault(newSlider()).getRoundCornerValue());
   }

   @Test
   void gateOffCheckBoxTakesNoRadius() {
      gateOff();
      assertEquals(0, objectDefault(newCheckBox()).getRoundCornerValue());
   }

   @Test
   void gateOnCheckBoxTakesTheCardRadius() {
      gateOn();
      assertEquals(VSObjectChromeDefaults.cardCornerRadius(),
                   objectDefault(newCheckBox()).getRoundCornerValue());
   }

   @Test
   void gateOffRadioButtonTakesNoRadius() {
      gateOff();
      assertEquals(0, objectDefault(newRadioButton()).getRoundCornerValue());
   }

   @Test
   void gateOnRadioButtonTakesTheCardRadius() {
      gateOn();
      assertEquals(VSObjectChromeDefaults.cardCornerRadius(),
                   objectDefault(newRadioButton()).getRoundCornerValue());
   }

   @Test
   void gateOffComboBoxTakesNoRadiusButKeepsItsLegacyBorder() {
      gateOff();
      VSFormat fmt = objectDefault(newComboBox());
      assertEquals(0, fmt.getRoundCornerValue());
      assertEquals(new Color(0xc0c0c0), fmt.getBorderColorsValue().topColor,
                   "the combo box's own legacy border colour is untouched by this work");
   }

   @Test
   void gateOnComboBoxTakesTheCardRadiusButKeepsItsLegacyBorder() {
      gateOn();
      VSFormat fmt = objectDefault(newComboBox());
      assertEquals(VSObjectChromeDefaults.cardCornerRadius(), fmt.getRoundCornerValue());
      assertEquals(new Color(0xc0c0c0), fmt.getBorderColorsValue().topColor,
                   "only round corner is gate-dependent for this type; border colour is a separate, " +
                   "not-yet-scoped follow-up");
   }

   @Test
   void gateOffSpinnerTakesNoRadius() {
      gateOff();
      assertEquals(0, objectDefault(newSpinner()).getRoundCornerValue());
   }

   @Test
   void gateOnSpinnerTakesTheCardRadius() {
      gateOn();
      assertEquals(VSObjectChromeDefaults.cardCornerRadius(),
                   objectDefault(newSpinner()).getRoundCornerValue());
   }

   @Test
   void gateOffSubmitKeepsItsLegacyThreePixelRadius() {
      gateOff();
      assertEquals(3, objectDefault(newSubmit()).getRoundCornerValue(),
                   "Submit's own legacy radius predates this work and is not 0 like the others");
   }

   @Test
   void gateOnSubmitTakesTheCardRadius() {
      gateOn();
      assertEquals(VSObjectChromeDefaults.cardCornerRadius(),
                   objectDefault(newSubmit()).getRoundCornerValue());
   }

   @Test
   void gateOffTextInputTakesNoRadiusButKeepsItsBugFixBorder() {
      gateOff();
      VSFormat fmt = objectDefault(newTextInput());
      assertEquals(0, fmt.getRoundCornerValue());
      // bug #23941 sets the RValue tier (setBorderColors), not the DValue tier the other seeded
      // types use (setBorderColorsValue) — getBorderColors() is the matching getter for this type.
      assertEquals(VSAssemblyInfo.DEFAULT_BORDER_COLOR, fmt.getBorderColors().topColor,
                   "bug #23941's border colour fix is untouched by this work");
   }

   @Test
   void gateOnTextInputTakesTheCardRadiusButKeepsItsBugFixBorder() {
      gateOn();
      VSFormat fmt = objectDefault(newTextInput());
      assertEquals(VSObjectChromeDefaults.cardCornerRadius(), fmt.getRoundCornerValue());
      assertEquals(VSAssemblyInfo.DEFAULT_BORDER_COLOR, fmt.getBorderColors().topColor);
   }

   @Test
   void theHookModernizesAPreExistingSubmitButtonToo() {
      // Modernize calls seedChromeDefaults directly on an assembly that already exists, exactly
      // like it does for the card types; this type's own override must respond the same way
      gateOff();
      SubmitVSAssemblyInfo info = newSubmit();
      assertEquals(3, objectDefault(info).getRoundCornerValue());

      gateOn();
      info.seedChromeDefaults(VizContext.ofGate());

      assertEquals(VSObjectChromeDefaults.cardCornerRadius(),
                   objectDefault(info).getRoundCornerValue());
   }

   @Test
   void theHookRevertsAPreviouslyModernizedCheckBoxToo() {
      gateOn();
      CheckBoxVSAssemblyInfo info = newCheckBox();
      assertEquals(VSObjectChromeDefaults.cardCornerRadius(),
                   objectDefault(info).getRoundCornerValue());

      info.seedChromeDefaults(VizContext.LEGACY);

      assertEquals(0, objectDefault(info).getRoundCornerValue());
   }

   @Test
   void aFreshTabUnderAnOpenGateKeepsItsOwnRadiusAndBorder() {
      // Tab writes its own round corner and border colour after super(), overwriting whatever
      // the hook wrote; a fresh tab must come out unchanged by the gate being open
      gateOn();
      VSFormat fmt = objectDefault(newTab());
      assertEquals(4, fmt.getRoundCornerValue());
      assertEquals(VSAssemblyInfo.DEFAULT_BORDER_COLOR, fmt.getBorderColorsValue().topColor);
   }

   @Test
   void theHookDoesNothingForATabEvenUnderTheGate() {
      gateOn();
      TabVSAssemblyInfo info = newTab();
      VSFormat fmt = objectDefault(info);
      int radiusBefore = fmt.getRoundCornerValue();
      Color borderBefore = fmt.getBorderColorsValue().topColor;

      info.seedChromeDefaults(VizContext.ofGate());

      assertEquals(radiusBefore, fmt.getRoundCornerValue());
      assertEquals(borderBefore, fmt.getBorderColorsValue().topColor);
   }

   // ---- the title border, and who wins the title composite -----------------------------------

   @Test
   void aTableTitleTakesTheModernBorderColour() {
      gateOn();
      VizContext ctx = VizContext.ofGate();
      VSCompositeFormat title = titleFormat(newTable());
      assertNotNull(title, "a table is titled, so a TITLEPATH composite is installed");
      assertNotNull(title.getDefaultFormat().getBordersValue(),
                    "a table creates with border = true, so the title carries border insets");
      assertEquals(VSObjectChromeDefaults.objectBorderColor(ctx),
                   title.getDefaultFormat().getBorderColorsValue().topColor);
   }

   @Test
   void aModernTableTitleIsUnfilledWithABottomRule() {
      gateOn();
      VizContext ctx = VizContext.ofGate();
      VSFormat def = titleFormat(newTable()).getDefaultFormat();

      assertNull(def.getBackgroundValue(), "the modern title lane carries no fill");
      assertNull(def.getBackground(), "and no runtime background behind it either");

      Insets borders = def.getBordersValue();
      assertNotNull(borders);
      assertEquals(StyleConstants.NONE, borders.top, "no top rule");
      assertEquals(StyleConstants.NONE, borders.left, "no left rule");
      assertEquals(StyleConstants.NONE, borders.right, "no right rule");
      assertEquals(StyleConstants.THIN_LINE, borders.bottom, "a bottom rule only");

      BorderColors colors = def.getBorderColorsValue();
      assertEquals(VSTitleChromeDefaults.titleBorderColor(ctx), colors.bottomColor);
      assertEquals(colors.bottomColor, colors.topColor,
                   "all four sides carry the rule colour; report text boxes keep only one");
   }

   @Test
   void aLegacyTableTitleKeepsTheFilledBandAndTheFourSideBox() {
      gateOff();
      VSFormat def = titleFormat(newTable()).getDefaultFormat();

      assertEquals(VSAssemblyInfo.DEFAULT_TITLE_BG, def.getBackgroundValue());

      Insets borders = def.getBordersValue();
      assertEquals(StyleConstants.THIN_LINE, borders.top);
      assertEquals(StyleConstants.THIN_LINE, borders.left);
      assertEquals(StyleConstants.THIN_LINE, borders.right);
      assertEquals(StyleConstants.THIN_LINE, borders.bottom);
   }

   @Test
   void revertingATableTitleRestoresAGateOffCreation() {
      gateOff();
      VSFormat expected = titleFormat(newTable()).getDefaultFormat();
      String expectedBg = expected.getBackgroundValue();
      Insets expectedBorders = expected.getBordersValue();

      gateOn();
      TableVSAssemblyInfo info = newTable();
      info.setVizMark(VizMark.fromGate());
      info.seedChromeDefaults(VizContext.of(info));

      info.setVizMark(null);
      info.seedChromeDefaults(VizContext.of((VizMark) null));

      VSFormat reverted = titleFormat(info).getDefaultFormat();
      assertEquals(expectedBg, reverted.getBackgroundValue(),
                   "Revert must match a table that was never modernized, not almost match it");
      assertEquals(expectedBorders, reverted.getBordersValue());
   }

   @Test
   void aModernRangeSliderTitleTakesTheRuleColour() {
      gateOn();
      VizContext ctx = VizContext.ofGate();
      TimeSliderVSAssemblyInfo info = newTimeSlider();
      VSFormat def = titleFormat(info).getDefaultFormat();

      assertEquals(VSTitleChromeDefaults.titleBorderColor(ctx),
                   def.getBorderColorsValue().bottomColor,
                   "the hook now owns this title composite");
      assertNull(def.getBackgroundValue(),
                 "the slider clears its title fill deliberately; modern keeps it cleared");
   }

   @Test
   void aLegacyRangeSliderTitleKeepsTheGreyRuleAndNoFill() {
      gateOff();
      VSFormat def = titleFormat(newTimeSlider()).getDefaultFormat();

      assertEquals(new Color(0xc0c0c0), def.getBorderColorsValue().bottomColor);
      assertNull(def.getBackgroundValue(),
                 "not DEFAULT_TITLE_BG: this type has always cleared its title fill");
      assertNull(def.getForegroundValue());
   }

   @Test
   void revertingARangeSliderTitleRestoresAGateOffCreation() {
      gateOff();
      VSFormat expected = titleFormat(newTimeSlider()).getDefaultFormat();
      BorderColors expectedColors = expected.getBorderColorsValue();

      gateOn();
      TimeSliderVSAssemblyInfo info = newTimeSlider();
      info.setVizMark(VizMark.fromGate());
      info.seedChromeDefaults(VizContext.of(info));

      info.setVizMark(null);
      info.seedChromeDefaults(VizContext.of((VizMark) null));

      VSFormat reverted = titleFormat(info).getDefaultFormat();
      assertEquals(expectedColors.bottomColor, reverted.getBorderColorsValue().bottomColor);
      assertNull(reverted.getBackgroundValue(),
                 "Revert must not hand the slider a fill it never had");
      assertNull(reverted.getForegroundValue());
   }

   @Test
   void aModernChartTitleCarriesTheBottomRule() {
      // The base seeds a title composite that ChartVSAssemblyInfo then replaces, so the chart
      // re-invokes the hook after its own install; without that the rule is written to a
      // composite that is thrown away.
      gateOn();
      VizContext ctx = VizContext.ofGate();
      VSFormat def = titleFormat(newChart()).getDefaultFormat();

      assertNull(def.getBackgroundValue(),
                 "the chart's title composite has never carried a fill, on either branch");

      Insets borders = def.getBordersValue();
      assertNotNull(borders, "the rule reaches the composite the chart actually keeps");
      assertEquals(StyleConstants.NONE, borders.top);
      assertEquals(StyleConstants.THIN_LINE, borders.bottom);
      assertEquals(VSTitleChromeDefaults.titleBorderColor(ctx),
                   def.getBorderColorsValue().bottomColor);
   }

   @Test
   void aLegacyChartTitleCarriesNoBorderAtAll() {
      gateOff();
      VSFormat def = titleFormat(newChart()).getDefaultFormat();
      assertNull(def.getBordersValue(), "a gate-off chart title has no rule");
      assertNull(def.getBackgroundValue(), "and no fill");
   }

   // The read-time substitution carried the title foreground as well as the background. A seeded
   // type is skipped there, so the colour has to be seeded or the title falls back to the
   // composite default - black, which is unreadable on a dark card.

   @Test
   void aModernTitleCarriesTheMutedForeground() {
      gateOn();
      VizContext ctx = VizContext.ofGate();
      assertEquals(VSTitleChromeDefaults.titleForeground(ctx),
                   titleFormat(newChart()).getDefaultFormat().getForeground(),
                   "a modern chart title carries the muted foreground");
      assertEquals(VSTitleChromeDefaults.titleForeground(ctx),
                   titleFormat(newTable()).getDefaultFormat().getForeground(),
                   "and so does a modern table title");
   }

   @Test
   void aDarkTitleForegroundIsLightEnoughToReadOnTheDarkCard() {
      SreeEnv.setProperty("viewsheet.modernVisualization", "true");
      SreeEnv.setProperty("viewsheet.darkMode", "true");
      assertEquals(new Color(0xCAC4D0),
                   titleFormat(newChart()).getDefaultFormat().getForeground(),
                   "dark mode seeds the light neutral, not the near-black default");
   }

   @Test
   void aLegacyTitleForegroundIsCleared() {
      gateOff();
      assertNull(titleFormat(newChart()).getDefaultFormat().getForeground(),
                 "a gate-off chart title keeps the composite default");
      assertNull(titleFormat(newTable()).getDefaultFormat().getForeground(),
                 "and so does a gate-off table title");
   }

   @Test
   void revertingClearsTheSeededForeground() {
      gateOn();
      ChartVSAssemblyInfo info = newChart();
      info.setVizMark(VizMark.fromGate());
      info.seedChromeDefaults(VizContext.of(info));
      assertNotNull(titleFormat(info).getDefaultFormat().getForeground(),
                    "precondition: the modern chart carries the seeded foreground");

      info.setVizMark(null);
      info.seedChromeDefaults(VizContext.of((VizMark) null));

      assertNull(titleFormat(info).getDefaultFormat().getForeground(),
                 "Revert restores a chart title with no foreground of its own");
   }

   // A chart draws its own title rule, so the object frame's colour must not reach it. The copy
   // that would overwrite it lives in FormatInfo.getFormat, which mutates the stored title format
   // in place, so the seeded colour has to survive a read through that path.

   @Test
   void aUserObjectBorderColourDoesNotReachAChartTitleRule() {
      gateOn();
      VizContext ctx = VizContext.ofGate();
      ChartVSAssemblyInfo info = newChart();
      info.getFormat().getUserDefinedFormat().setBorderColorsValue(
         new BorderColors(Color.RED, Color.RED, Color.RED, Color.RED));

      VSCompositeFormat title = info.getFormatInfo().getFormat(VSAssemblyInfo.TITLEPATH, false);

      assertEquals(VSTitleChromeDefaults.titleBorderColor(ctx),
                   title.getBorderColors().bottomColor,
                   "the chart's title rule keeps its own colour, not the author's frame colour");
   }

   @Test
   void aUserObjectBorderColourStillReachesATableTitle() {
      gateOn();
      TableVSAssemblyInfo info = newTable();
      info.getFormat().getUserDefinedFormat().setBorderColorsValue(
         new BorderColors(Color.RED, Color.RED, Color.RED, Color.RED));

      VSCompositeFormat title = info.getFormatInfo().getFormat(VSAssemblyInfo.TITLEPATH, false);

      assertEquals(Color.RED, title.getBorderColors().bottomColor,
                   "a table's title still follows the frame, which is long-standing behaviour");
   }

   @Test
   void aModernizedChartEqualsAFreshlyCreatedOne() {
      // the equality the re-invocation must preserve: creation and Modernize run the same hook
      // against the same composite, so they cannot diverge
      gateOn();
      VizContext ctx = VizContext.ofGate();
      VSFormat fresh = titleFormat(newChart()).getDefaultFormat();

      gateOff();
      ChartVSAssemblyInfo modernized = newChart();
      gateOn();
      modernized.setVizMark(VizMark.fromGate());
      modernized.seedChromeDefaults(VizContext.of(modernized));
      VSFormat after = titleFormat(modernized).getDefaultFormat();

      assertEquals(fresh.getBordersValue(), after.getBordersValue());
      assertEquals(fresh.getBorderColorsValue().bottomColor,
                   after.getBorderColorsValue().bottomColor);
      assertEquals(VSTitleChromeDefaults.titleBorderColor(ctx),
                   after.getBorderColorsValue().bottomColor);
   }

   @Test
   void revertingAChartTitleRemovesTheRule() {
      gateOn();
      ChartVSAssemblyInfo info = newChart();
      info.setVizMark(VizMark.fromGate());
      info.seedChromeDefaults(VizContext.of(info));
      assertNotNull(titleFormat(info).getDefaultFormat().getBordersValue(),
                    "precondition: the modern chart has the rule");

      info.setVizMark(null);
      info.seedChromeDefaults(VizContext.of((VizMark) null));

      assertNull(titleFormat(info).getDefaultFormat().getBordersValue(),
                 "Revert restores a chart title with no rule");
   }

   // ---- backgrounds ---------------------------------------------------------------------------

   @Test
   void aChartCardTakesTheCardBackground() {
      gateOn();
      assertEquals(VSObjectChromeDefaults.cardBackgroundCss(VizContext.ofGate()),
                   objectDefault(newChart()).getBackgroundValue());
   }

   @Test
   void aTableCardTakesTheCardBackground() {
      gateOn();
      assertEquals(VSObjectChromeDefaults.cardBackgroundCss(VizContext.ofGate()),
                   objectDefault(newTable()).getBackgroundValue());
   }

   @Test
   void theSheetTakesThePageBackgroundUnderTheGateAndTheLegacyGreyWithout() {
      gateOn();
      assertEquals(VSObjectChromeDefaults.pageBackgroundCss(VizContext.ofGate()),
                   objectDefault(new Viewsheet().getVSAssemblyInfo()).getBackgroundValue());

      gateOff();
      assertEquals("#f5f5f5",
                   objectDefault(new Viewsheet().getVSAssemblyInfo()).getBackgroundValue());
   }

   @Test
   void anUnmarkedTableGetsNoDefaultBackgroundFromTheHook() {
      // tables and crosstabs are created with fill=false precisely so they have no DEFAULT
      // background (see the "do not set default background" comment in initDefaultFormat), and
      // an unconditional write here would put one onto every pre-branch, unmarked table the
      // first time its chrome is re-seeded
      gateOff();
      TableVSAssemblyInfo info = newTable();
      assertNull(objectDefault(info).getBackgroundValue(),
                 "precondition: a fresh legacy table has no DEFAULT background");

      info.seedChromeDefaults(VizContext.of((VizMark) null));

      assertNull(objectDefault(info).getBackgroundValue(),
                 "an unmarked table must not gain a DEFAULT background from the hook");
   }

   // ---- the chart's plot seeds -----------------------------------------------------------------

   @Test
   void gateOnChartTakesItsPlotSeeds() {
      gateOn();
      PlotDescriptor plot = newChart().getChartDescriptor().getPlotDescriptor();
      assertEquals(0.3, plot.getBarCornerRadius(), 0.0001);
      assertTrue(plot.isSmoothLines());
   }

   @Test
   void gateOffChartTakesNoPlotSeedsButKeepsTheUnconditionalOnes() {
      gateOff();
      ChartVSAssemblyInfo info = newChart();
      PlotDescriptor plot = info.getChartDescriptor().getPlotDescriptor();
      assertEquals(0.0, plot.getBarCornerRadius(), 0.0001);
      assertFalse(plot.isSmoothLines());
      assertTrue(info.getChartDescriptor().getLegendsDescriptor().isRoundCorners(),
                 "round legend corners are unconditional and must stay in setDefaultFormat");
      assertEquals(10, info.getPadding().top,
                   "the inset is a gate-dependent seed now, and the legacy branch writes 10");
   }

   // ---- the hook, called a second time on an assembly that already exists ---------------------

   @Test
   void theHookModernizesAnAssemblyCreatedUnderAClosedGate() {
      gateOff();
      TableVSAssemblyInfo info = newTable();
      assertEquals(0, objectDefault(info).getRoundCornerValue());

      gateOn();
      VizContext ctx = VizContext.ofGate();
      info.seedChromeDefaults(ctx);

      assertEquals(VSObjectChromeDefaults.objectBorderColor(ctx),
                   objectDefault(info).getBorderColorsValue().topColor);
      assertEquals(VSObjectChromeDefaults.cardCornerRadius(),
                   objectDefault(info).getRoundCornerValue());
      assertEquals(VSObjectChromeDefaults.objectBorderColor(ctx),
                   titleFormat(info).getDefaultFormat().getBorderColorsValue().topColor,
                   "the title border colour follows too");
   }

   @Test
   void theHookMutatesInPlaceAndLeavesTheUserTierAlone() {
      gateOff();
      TableVSAssemblyInfo info = newTable();
      VSCompositeFormat objBefore = info.getFormatInfo().getFormat(VSAssemblyInfo.OBJECTPATH);
      VSCompositeFormat titleBefore = info.getFormatInfo().getFormat(VSAssemblyInfo.TITLEPATH);
      objBefore.getUserDefinedFormat().setBackgroundValue("0x123456");
      titleBefore.getUserDefinedFormat().setForegroundValue("0x654321");

      gateOn();
      info.seedChromeDefaults(VizContext.ofGate());

      assertSame(objBefore, info.getFormatInfo().getFormat(VSAssemblyInfo.OBJECTPATH),
                 "the object composite is mutated, never replaced");
      assertSame(titleBefore, info.getFormatInfo().getFormat(VSAssemblyInfo.TITLEPATH),
                 "the title composite is mutated, never replaced");
      assertEquals("0x123456", objBefore.getUserDefinedFormat().getBackgroundValue(),
                   "an author's object background survives");
      assertEquals("0x654321", titleBefore.getUserDefinedFormat().getForegroundValue(),
                   "an author's title foreground survives");
   }

   @Test
   void theHookIsIdempotent() {
      gateOn();
      TableVSAssemblyInfo info = newTable();
      int radius = objectDefault(info).getRoundCornerValue();
      Color border = objectDefault(info).getBorderColorsValue().topColor;

      info.seedChromeDefaults(VizContext.ofGate());

      assertEquals(radius, objectDefault(info).getRoundCornerValue());
      assertEquals(border, objectDefault(info).getBorderColorsValue().topColor);
   }

   @Test
   void theHookOwnsASelectionListTitleAndIsIdempotent() {
      // a selection list installs its own title composite after super, but re-runs the hook
      // against it, so a second call must not double-apply or drift
      gateOn();
      VizContext ctx = VizContext.ofGate();
      SelectionListVSAssemblyInfo info = newSelectionList();
      Color before = titleFormat(info).getDefaultFormat().getBorderColorsValue().bottomColor;
      assertEquals(VSTitleChromeDefaults.titleBorderColor(ctx), before);

      info.seedChromeDefaults(VizContext.ofGate());

      assertEquals(before, titleFormat(info).getDefaultFormat().getBorderColorsValue().bottomColor,
                   "a second call leaves the same colour in place");
   }

   @Test
   void theHookSeedsATextAssemblysOwnValuesAndNoneOfTheBases() {
      // Text builds its own object format, so the base seeds must not reach it — but its own
      // override does write the value emphasis, which is what the seed conversion moved here
      gateOn();
      TextVSAssemblyInfo info = newText();
      VizContext ctx = VizContext.ofGate();

      info.seedChromeDefaults(ctx);

      assertEquals(VSOutputChromeDefaults.valueBorderColor(ctx),
                   objectDefault(info).getBorderColorsValue().topColor,
                   "the type's own emphasis is seeded");
      assertEquals(0, objectDefault(info).getRoundCornerValue(),
                   "documents that a seeded text assembly has no card radius - true, but not a " +
                   "guard: isCornerSeedTarget() already excludes Text, so this holds whether or " +
                   "not the bypass fired. theHookDoesNothingForATabEvenUnderTheGate is where the " +
                   "bypass is actually observable, against a type with a radius of its own");
   }

   // ---- the per-type hook overrides, now that the seeds live there ---------------------------

   @Test
   void theHookModernizesALegacyChartCompletely() {
      gateOff();
      ChartVSAssemblyInfo info = newChart();
      info.setPadding(new Insets(3, 3, 3, 3));

      gateOn();
      VizContext ctx = VizContext.ofGate();
      info.seedChromeDefaults(ctx);

      assertEquals(VSObjectChromeDefaults.cardBackgroundCss(ctx),
                   objectDefault(info).getBackgroundValue());
      PlotDescriptor plot = info.getChartDescriptor().getPlotDescriptor();
      assertEquals(0.3, plot.getBarCornerRadius(), 0.0001, "the plot seeds are reachable");
      assertTrue(plot.isSmoothLines());
      assertEquals(new Insets(12, 12, 12, 12), info.getPadding(),
                   "an unflagged padding is not an author opinion, so Modernize seeds over it; " +
                   "anAuthorsInsetSurvivesModernize covers the flagged case");
   }

   @Test
   void theHookModernizesALegacyTableCard() {
      gateOff();
      TableVSAssemblyInfo info = newTable();
      String styleBefore = info.getTableStyleValue();

      gateOn();
      VizContext ctx = VizContext.ofGate();
      info.seedChromeDefaults(ctx);

      assertEquals(VSObjectChromeDefaults.cardBackgroundCss(ctx),
                   objectDefault(info).getBackgroundValue());
      assertEquals(styleBefore, info.getTableStyleValue(),
                   "the default table style is not a gate-dependent seed");
   }

   @Test
   void theHookModernizesALegacySheetBackground() {
      gateOff();
      Viewsheet vs = new Viewsheet();
      assertEquals("#f5f5f5", objectDefault(vs.getVSAssemblyInfo()).getBackgroundValue());

      gateOn();
      VizContext ctx = VizContext.ofGate();
      vs.getVSAssemblyInfo().seedChromeDefaults(ctx);

      assertEquals(VSObjectChromeDefaults.pageBackgroundCss(ctx),
                   objectDefault(vs.getVSAssemblyInfo()).getBackgroundValue());
   }

   @Test
   void theHookRevertsChromeWhenGivenALegacyContext() {
      // the ternaries live in the hook, so a legacy context writes the legacy values. Revert is
      // what calls it that way.
      gateOn();
      TableVSAssemblyInfo info = newTable();

      info.seedChromeDefaults(VizContext.LEGACY);

      assertEquals(VSAssemblyInfo.DEFAULT_BORDER_COLOR,
                   objectDefault(info).getBorderColorsValue().topColor);
      assertEquals(0, objectDefault(info).getRoundCornerValue());
   }

   @Test
   void chartPlotSeedsAreWrittenOnTheLegacyBranchToo() {
      // the hook has to be able to un-seed, not only seed: Revert calls it with an unmarked
      // context and expects the legacy values written rather than left alone
      ChartVSAssemblyInfo info = new ChartVSAssemblyInfo();
      info.initDefaultFormat();
      PlotDescriptor plot = info.getChartDescriptor().getPlotDescriptor();
      plot.setBarCornerRadius(0.4);
      plot.setSmoothLines(true);

      info.seedChromeDefaults(VizContext.LEGACY);

      assertEquals(0.0, plot.getBarCornerRadius(), 0.0001,
                   "an unmarked context writes the legacy radius");
      assertFalse(plot.isSmoothLines(), "and the legacy smoothing");
   }

   @Test
   void chartColorPaletteIsWrittenOnTheLegacyBranchToo() {
      // Revert calls this on a chart that was already rendered modern at least once, so the
      // frame holds modern colours coming in; the hook must overwrite them, not leave them alone
      ChartVSAssemblyInfo info = newChart();
      CategoricalColorFrame frame = new CategoricalColorFrame();
      VSAestheticRef colorRef = new VSAestheticRef();
      colorRef.setVisualFrame(frame);
      info.getVSChartInfo().setColorField(colorRef);

      VSChartPaletteDefaults.applyModernPalette(frame, VizContext.of(VizMark.MODERN_LIGHT));
      assertEquals(VSChartPaletteDefaults.modernPalette()[0], frame.getDefaultColor(0),
                   "the render-time applier leaves the modern palette on the frame");

      info.seedChromeDefaults(VizContext.of((VizMark) null));

      assertEquals(VSChartPaletteDefaults.legacyPalette()[0], frame.getDefaultColor(0),
                   "an unmarked context must re-seed the classic legacy palette onto the colour frame");
   }

   @Test
   void chartColorPaletteIsWrittenOnTheModernBranchToo() {
      // nothing else in this file exercises the hook writing the modern direction; a seed that
      // dropped the modern write (or had its ternary inverted) would still pass every other
      // palette test here
      ChartVSAssemblyInfo info = newChart();
      CategoricalColorFrame frame = new CategoricalColorFrame();
      VSAestheticRef colorRef = new VSAestheticRef();
      colorRef.setVisualFrame(frame);
      info.getVSChartInfo().setColorField(colorRef);

      info.seedChromeDefaults(VizContext.of(VizMark.MODERN_LIGHT));

      assertEquals(VSChartPaletteDefaults.modernPalette()[0], frame.getDefaultColor(0),
                   "a modern context must seed the modern palette onto the colour frame");
   }

   @Test
   void multiStylesChartColorPaletteIsWrittenOnTheLegacyBranchToo() {
      // a multi-styles chart stores its aesthetics on each aggregate, not on the info; the hook
      // must reach the same set AbstractChartInfo.getAggregateAestheticRefs() does, or an
      // aggregate's colour frame would keep rendering modern after Revert
      ChartVSAssemblyInfo info = newChart();
      info.getVSChartInfo().setMultiStyles(true);

      VSChartAggregateRef aggr = new VSChartAggregateRef();
      aggr.setColumnValue("Measure1");
      CategoricalColorFrame frame = new CategoricalColorFrame();
      VSAestheticRef colorRef = new VSAestheticRef();
      colorRef.setVisualFrame(frame);
      aggr.setColorField(colorRef);
      info.getVSChartInfo().addYField(aggr);

      VSChartPaletteDefaults.applyModernPalette(frame, VizContext.of(VizMark.MODERN_LIGHT));
      assertEquals(VSChartPaletteDefaults.modernPalette()[0], frame.getDefaultColor(0),
                   "the render-time applier leaves the modern palette on the aggregate's frame");

      info.seedChromeDefaults(VizContext.of((VizMark) null));

      assertEquals(VSChartPaletteDefaults.legacyPalette()[0], frame.getDefaultColor(0),
                   "an unmarked context must re-seed the legacy palette onto the aggregate-level " +
                   "frame too");
   }

   /**
    * A render derives a color per value from the palette, and a per-value color outranks the
    * palette - so re-seeding the palette alone leaves the old colors rendering for the rest of the
    * session. This is what a live composer session, its preview and its exports all showed.
    */
   @Test
   void revertAlsoDropsThePerValueColorsARenderDerived() {
      ChartVSAssemblyInfo info = newChart();
      CategoricalColorFrame frame = new CategoricalColorFrame();
      VSAestheticRef colorRef = new VSAestheticRef();
      colorRef.setVisualFrame(frame);
      info.getVSChartInfo().setColorField(colorRef);

      VSChartPaletteDefaults.applyModernPalette(frame, VizContext.of(VizMark.MODERN_LIGHT));
      frame.setDerivedColor("Business", frame.getColor(0));
      assertEquals(VSChartPaletteDefaults.modernPalette()[0], frame.getColor("Business"),
                   "precondition: the render derived the modern color for this value");

      info.seedChromeDefaults(VizContext.of((VizMark) null));

      assertFalse(frame.isDerived("Business"), "the derived color is dropped with the palette");
      assertNotEquals(VSChartPaletteDefaults.modernPalette()[0], frame.getColor("Business"),
                      "so it no longer shadows the legacy palette");
   }

   @Test
   void revertKeepsAPerValueColorSomebodyAssigned() {
      ChartVSAssemblyInfo info = newChart();
      CategoricalColorFrame frame = new CategoricalColorFrame();
      VSAestheticRef colorRef = new VSAestheticRef();
      colorRef.setVisualFrame(frame);
      info.getVSChartInfo().setColorField(colorRef);
      frame.setColor("Business", Color.RED);

      info.seedChromeDefaults(VizContext.of((VizMark) null));

      assertEquals(Color.RED, frame.getColor("Business"),
                   "an author's own per-value color is not collateral damage");
   }

   // ---- the card inset, seeded rather than resolved ------------------------------------------

   @Test
   void aModernChartSeedsTheCardInset() {
      gateOn();
      assertEquals(new Insets(12, 12, 12, 12), newChart().getPadding(),
                   "the card inset is stored at creation, not resolved at read time");
   }

   @Test
   void aLegacyChartSeedsTheLegacyInset() {
      gateOff();
      assertEquals(new Insets(10, 10, 10, 10), newChart().getPadding());
   }

   @Test
   void revertingAChartRestoresTheLegacyInset() {
      gateOn();
      ChartVSAssemblyInfo info = newChart();
      assertEquals(new Insets(12, 12, 12, 12), info.getPadding());

      info.setVizMark(null);
      info.seedChromeDefaults(VizContext.of((VizMark) null));

      assertEquals(new Insets(10, 10, 10, 10), info.getPadding(),
                   "the legacy branch writes the inset back; nothing else would");
   }

   @Test
   void aModernChartsInsetTravelsInTheAsset() {
      // THE defect this conversion closes, and the only assertion here that fails before it:
      // writeAttributes serializes the padding FIELD, not getPadding(), so a resolved-only value
      // is absent from an exported asset and an older build renders the card mixed
      gateOn();
      ChartVSAssemblyInfo info = newChart();
      StringWriter buf = new StringWriter();
      PrintWriter writer = new PrintWriter(buf);

      info.writeXML(writer);
      writer.flush();

      assertTrue(buf.toString().contains("paddingTop=\"12\""),
                 "the card inset has to be in the serialized asset, not only in the getter");
   }

   @Test
   void anAuthorsInsetSurvivesModernize() {
      gateOff();
      ChartVSAssemblyInfo info = newChart();
      info.setUserPadding(true);
      info.setPadding(new Insets(3, 3, 3, 3));

      gateOn();
      info.seedChromeDefaults(VizContext.ofGate());

      assertEquals(new Insets(3, 3, 3, 3), info.getPadding(),
                   "isUserPadding is the authorship record and the seed must not overwrite it");
   }

   // ---- the userPadding flag and the inset, across persistence --------------------------------
   // isUserPadding() parsing is unchanged by this conversion, and now matters more than before:
   // the seed's skip is gated entirely on that flag.

   @Test
   void theFlagSurvivesARoundTrip() throws Exception {
      ChartVSAssemblyInfo info = new ChartVSAssemblyInfo();
      info.initDefaultFormat();
      info.setUserPadding(true);

      ChartVSAssemblyInfo restored = roundTrip(info);

      assertTrue(restored.isUserPadding());
   }

   @Test
   void anAbsentFlagAttributeMeansNoOpinion() {
      // content saved before the flag existed: parse must not invent an author
      ChartVSAssemblyInfo info = new ChartVSAssemblyInfo();

      assertFalse(info.isUserPadding());
   }

   @Test
   void contentSavedBeforeTheFlagExistedCarriesNoOpinion() throws Exception {
      // the attribute is absent, so the flag must parse as false rather than inventing an author.
      // Nothing re-seeds on a plain parse, so the inset the seed wrote at creation comes back
      // unchanged - a plain parse has no resolution step left to substitute 12 in its place
      ChartVSAssemblyInfo info = new ChartVSAssemblyInfo();
      info.initDefaultFormat();
      info.setVizMark(VizMark.MODERN_LIGHT);

      String xml = writeXml(info).replaceAll(" userPadding=\"[a-z]*\"", "");
      ChartVSAssemblyInfo restored = new ChartVSAssemblyInfo();
      restored.parseXML(Tool.parseXML(new StringReader(xml)).getDocumentElement());

      assertFalse(restored.isUserPadding());
      assertEquals(new Insets(10, 10, 10, 10), restored.getPadding());
   }

   @Test
   void aModernChartsInsetSurvivesTheRoundTrip() throws Exception {
      // the round-trip counterpart to aModernChartsInsetTravelsInTheAsset: the seed already wrote
      // 12 into the field at creation, so a plain parse needs no resolution step to show it
      gateOn();
      ChartVSAssemblyInfo info = newChart();

      ChartVSAssemblyInfo restored = roundTrip(info);

      assertEquals(new Insets(12, 12, 12, 12), restored.getPadding(),
                   "the asset itself carries the seeded inset, not a resolved-away legacy one");
      assertFalse(restored.isUserPadding(),
                  "a seeded value must never come back flagged as the author's");
   }

   private static String writeXml(ChartVSAssemblyInfo info) {
      StringWriter buf = new StringWriter();
      PrintWriter writer = new PrintWriter(buf);
      info.writeXML(writer);
      writer.flush();
      return buf.toString();
   }

   private static ChartVSAssemblyInfo roundTrip(ChartVSAssemblyInfo info) throws Exception {
      ChartVSAssemblyInfo restored = new ChartVSAssemblyInfo();
      restored.parseXML(Tool.parseXML(new StringReader(writeXml(info))).getDocumentElement());
      return restored;
   }

   // ---- creation seeds from the assembly's own mark, not the org gate -------------------------

   @Test
   void creationOnAnUnmarkedHostSeedsNothingModern() {
      // the host is what decides: AbstractVSAssembly hands a new assembly the host's stored mark,
      // so an assembly added to a legacy dashboard must seed the values it will actually read
      gateOff();
      Viewsheet legacy = new Viewsheet();
      assertNull(legacy.getVSAssemblyInfo().getVizMark(), "gate off at construction: unmarked sheet");

      gateOn();
      TableVSAssembly table = new TableVSAssembly(legacy, "Table1");
      table.getVSAssemblyInfo().initDefaultFormat();
      VSFormat fmt = objectDefault(table.getVSAssemblyInfo());

      assertNull(table.getVSAssemblyInfo().getVizMark(), "it inherits the host's absent mark");
      assertEquals(VSAssemblyInfo.DEFAULT_BORDER_COLOR, fmt.getBorderColorsValue().topColor,
                   "so it seeds the legacy border, matching what it will read");
      assertEquals(0, fmt.getRoundCornerValue(), "and no card radius");
   }

   // ---- malformed restore data: the hook must not NPE on a missing OBJECTPATH format ---------

   @Test
   void theChartHookToleratesAMissingObjectFormat() {
      // the hook used to run only on freshly constructed objects, which always have an
      // OBJECTPATH format; it now runs on data parsed from a blob, where one malformed assembly
      // missing OBJECTPATH must not turn a restore into an NPE that aborts the whole sheet.
      // setFormat(null) is how OBJECTPATH actually goes missing (FormatInfo.setFormat removes
      // the map entry), standing in for a blob whose formatInfo node never carried one.
      ChartVSAssemblyInfo info = new ChartVSAssemblyInfo();
      info.setFormat(null);
      assertNull(info.getFormat(), "precondition: no OBJECTPATH format installed");
      assertDoesNotThrow(() -> info.seedChromeDefaults(VizContext.ofGate()));
   }

   @Test
   void theTableDataHookToleratesAMissingObjectFormatUnderTheGate() {
      gateOn();
      TableVSAssemblyInfo info = new TableVSAssemblyInfo();
      info.setFormat(null);
      assertNull(info.getFormat(), "precondition: no OBJECTPATH format installed");
      assertDoesNotThrow(() -> info.seedChromeDefaults(VizContext.ofGate()));
   }

   @Test
   void theSheetHookToleratesAMissingObjectFormat() {
      ViewsheetVSAssemblyInfo info = new ViewsheetVSAssemblyInfo();
      info.setFormat(null);
      assertNull(info.getFormat(), "precondition: no OBJECTPATH format installed");
      assertDoesNotThrow(() -> info.seedChromeDefaults(VizContext.ofGate()));
   }

   @Test
   void creationOnAMarkedHostStillSeedsModern() {
      gateOn();
      Viewsheet modern = new Viewsheet();
      assertEquals(VizMark.MODERN_LIGHT, modern.getVSAssemblyInfo().getVizMark());

      TableVSAssembly table = new TableVSAssembly(modern, "Table1");
      table.getVSAssemblyInfo().initDefaultFormat();
      VSFormat fmt = objectDefault(table.getVSAssemblyInfo());
      VizContext ctx = VizContext.ofGate();

      assertEquals(VizMark.MODERN_LIGHT, table.getVSAssemblyInfo().getVizMark());
      assertEquals(VSObjectChromeDefaults.objectBorderColor(ctx), fmt.getBorderColorsValue().topColor);
      assertEquals(VSObjectChromeDefaults.cardCornerRadius(), fmt.getRoundCornerValue());
   }

   // ---- the selection list and tree title rule -------------------------------------------------

   @Test
   void aModernSelectionListTitleTakesTheRuleColour() {
      gateOn();
      VizContext ctx = VizContext.ofGate();
      VSFormat def = titleFormat(newSelectionList()).getDefaultFormat();

      assertNull(def.getBackgroundValue(), "this type's title has never carried a fill");

      Insets borders = def.getBordersValue();
      assertEquals(StyleConstants.NONE, borders.top, "no top rule");
      assertEquals(StyleConstants.NONE, borders.left, "no left rule");
      assertEquals(StyleConstants.NONE, borders.right, "no right rule");
      assertEquals(StyleConstants.THIN_LINE, borders.bottom, "a bottom rule only");

      BorderColors colors = def.getBorderColorsValue();
      assertEquals(VSTitleChromeDefaults.titleBorderColor(ctx), colors.bottomColor);
      assertEquals(colors.bottomColor, colors.topColor,
                   "all four sides carry the rule colour; report text boxes keep only one");
   }

   @Test
   void aModernSelectionTreeTitleTakesTheRuleColour() {
      gateOn();
      VizContext ctx = VizContext.ofGate();
      VSFormat def = titleFormat(newSelectionTree()).getDefaultFormat();

      assertEquals(VSTitleChromeDefaults.titleBorderColor(ctx),
                   def.getBorderColorsValue().bottomColor,
                   "the tree takes the same seed through the shared base");
   }

   @Test
   void aLegacySelectionListTitleKeepsTheGreyRule() {
      gateOff();
      VSFormat def = titleFormat(newSelectionList()).getDefaultFormat();

      assertEquals(new Color(0xc0c0c0), def.getBorderColorsValue().bottomColor);
      assertNull(def.getForegroundValue(), "and no seeded text colour");
      assertNull(def.getBackgroundValue(), "and still no fill");
   }

   @Test
   void revertingASelectionListTitleRestoresAGateOffCreation() {
      gateOff();
      VSFormat expected = titleFormat(newSelectionList()).getDefaultFormat();
      BorderColors expectedColors = expected.getBorderColorsValue();
      Insets expectedBorders = expected.getBordersValue();

      gateOn();
      SelectionListVSAssemblyInfo info = newSelectionList();
      info.setVizMark(VizMark.fromGate());
      info.seedChromeDefaults(VizContext.of(info));

      info.setVizMark(null);
      info.seedChromeDefaults(VizContext.of((VizMark) null));

      VSFormat reverted = titleFormat(info).getDefaultFormat();
      assertEquals(expectedColors.bottomColor, reverted.getBorderColorsValue().bottomColor,
                   "Revert must match a list that was never modernized, not almost match it");
      assertEquals(expectedBorders, reverted.getBordersValue());
      assertNull(reverted.getForegroundValue());
      assertNull(reverted.getBackgroundValue());
   }

   // ---- the selection container title lane -----------------------------------------------------

   @Test
   void aModernSelectionContainerTitleIsUnfilledWithABottomRule() {
      gateOn();
      VizContext ctx = VizContext.ofGate();
      VSFormat def = titleFormat(newCurrentSelection()).getDefaultFormat();

      assertNull(def.getBackgroundValue(), "the modern title lane carries no fill");
      assertNull(def.getBackground(), "and no runtime background behind it either");

      Insets borders = def.getBordersValue();
      assertEquals(StyleConstants.NONE, borders.top, "no top rule");
      assertEquals(StyleConstants.NONE, borders.left, "no left rule");
      assertEquals(StyleConstants.NONE, borders.right, "no right rule");
      assertEquals(StyleConstants.THIN_LINE, borders.bottom, "a bottom rule only");

      assertEquals(VSTitleChromeDefaults.titleBorderColor(ctx),
                   def.getBorderColorsValue().bottomColor,
                   "the base's object border colour is already the rule colour");
   }

   @Test
   void aLegacySelectionContainerTitleKeepsTheFilledBandAndTheFourSideBox() {
      gateOff();
      VSFormat def = titleFormat(newCurrentSelection()).getDefaultFormat();

      assertEquals(VSAssemblyInfo.DEFAULT_TITLE_BG, def.getBackgroundValue());

      Insets borders = def.getBordersValue();
      assertEquals(StyleConstants.THIN_LINE, borders.top);
      assertEquals(StyleConstants.THIN_LINE, borders.left);
      assertEquals(StyleConstants.THIN_LINE, borders.right);
      assertEquals(StyleConstants.THIN_LINE, borders.bottom);
   }

   @Test
   void revertingASelectionContainerTitleRestoresAGateOffCreation() {
      gateOff();
      VSFormat expected = titleFormat(newCurrentSelection()).getDefaultFormat();
      String expectedBg = expected.getBackgroundValue();
      Insets expectedBorders = expected.getBordersValue();

      gateOn();
      CurrentSelectionVSAssemblyInfo info = newCurrentSelection();
      info.setVizMark(VizMark.fromGate());
      info.seedChromeDefaults(VizContext.of(info));

      info.setVizMark(null);
      info.seedChromeDefaults(VizContext.of((VizMark) null));

      VSFormat reverted = titleFormat(info).getDefaultFormat();
      assertEquals(expectedBg, reverted.getBackgroundValue(),
                   "Revert must match a container that was never modernized");
      assertEquals(expectedBorders, reverted.getBordersValue());
   }

   // ---- the checkbox and radio button title lane -----------------------------------------------

   @Test
   void aModernCheckBoxTitleIsUnfilledWithABottomRule() {
      gateOn();
      VizContext ctx = VizContext.ofGate();
      VSFormat def = titleFormat(newCheckBox()).getDefaultFormat();

      Insets borders = def.getBordersValue();
      assertEquals(StyleConstants.NONE, borders.top, "no top rule");
      assertEquals(StyleConstants.NONE, borders.left, "no left rule");
      assertEquals(StyleConstants.NONE, borders.right, "no right rule");
      assertEquals(StyleConstants.THIN_LINE, borders.bottom, "a bottom rule only");

      assertEquals(VSTitleChromeDefaults.titleBorderColor(ctx),
                   def.getBorderColorsValue().bottomColor);
      assertEquals(VSTitleChromeDefaults.titleForegroundValue(ctx), def.getForegroundValue());
      assertNull(def.getBackgroundValue(), "the modern input lane carries no fill");
      assertTrue(def.isBackgroundValueDefined(),
                 "defined, so the object background cannot copy down onto the lane");
   }

   @Test
   void aLegacyCheckBoxTitleHasNoRuleOnAnySide() {
      gateOff();
      VSFormat def = titleFormat(newCheckBox()).getDefaultFormat();

      assertEquals(new Insets(0, 0, 0, 0), def.getBordersValue(),
                   "a gate-off input title has never carried a rule");
      assertNull(def.getBorderColorsValue(),
                 "and no rule colour either: storing one would differ from a gate-off creation");
      assertNull(def.getForegroundValue());
      assertNull(def.getForeground(), "the fg field too, or a runtime colour survives Revert");
      assertFalse(def.isBackgroundValueDefined(),
                  "undefined, as a gate-off creation leaves it");
   }

   @Test
   void revertingACheckBoxTitleRestoresAGateOffCreation() {
      gateOff();
      VSFormat expected = titleFormat(newCheckBox()).getDefaultFormat();
      Insets expectedBorders = expected.getBordersValue();
      boolean expectedBgDefined = expected.isBackgroundValueDefined();

      gateOn();
      CheckBoxVSAssemblyInfo info = newCheckBox();
      info.setVizMark(VizMark.fromGate());
      info.seedChromeDefaults(VizContext.of(info));

      info.setVizMark(null);
      info.seedChromeDefaults(VizContext.of((VizMark) null));

      VSFormat reverted = titleFormat(info).getDefaultFormat();
      assertEquals(expectedBorders, reverted.getBordersValue());
      assertNull(reverted.getBorderColorsValue());
      assertNull(reverted.getForegroundValue());
      assertEquals(expectedBgDefined, reverted.isBackgroundValueDefined(),
                   "Revert must match a checkbox that was never modernized, not almost match it");
   }

   @Test
   void aModernRadioButtonTitleTakesTheSameLaneAsTheCheckBox() {
      gateOn();
      VizContext ctx = VizContext.ofGate();
      VSFormat def = titleFormat(newRadioButton()).getDefaultFormat();

      assertEquals(StyleConstants.THIN_LINE, def.getBordersValue().bottom);
      assertEquals(VSTitleChromeDefaults.titleForegroundValue(ctx), def.getForegroundValue());
      assertNull(def.getBackgroundValue());
   }

   @Test
   void aLegacyRadioButtonTitleHasNoRuleOnAnySide() {
      gateOff();
      VSFormat def = titleFormat(newRadioButton()).getDefaultFormat();

      assertEquals(new Insets(0, 0, 0, 0), def.getBordersValue());
      assertNull(def.getForegroundValue());
   }

   // ---- the input value text: black on the dark card until it is seeded -----------------------

   private static VSFormat detailFormat(VSAssemblyInfo info) {
      return info.getFormatInfo().getFormat(new TableDataPath(-1, TableDataPath.DETAIL))
         .getDefaultFormat();
   }

   @Test
   void aDarkCheckBoxValueTakesTheLightInk() {
      gateOn();
      SreeEnv.setProperty("viewsheet.darkMode", "true");

      assertEquals(VSObjectChromeDefaults.darkForegroundValue(),
                   detailFormat(newCheckBox()).getForegroundValue(),
                   "the item labels paint from DETAIL and were black on the dark card");
   }

   @Test
   void aDarkRadioButtonValueTakesTheLightInk() {
      gateOn();
      SreeEnv.setProperty("viewsheet.darkMode", "true");

      assertEquals(VSObjectChromeDefaults.darkForegroundValue(),
                   detailFormat(newRadioButton()).getForegroundValue());
   }

   @Test
   void aLightInputValueKeepsWhateverAGateOffCreationWrites() {
      gateOff();
      String gateOff = detailFormat(newCheckBox()).getForegroundValue();

      gateOn();
      assertEquals(gateOff, detailFormat(newCheckBox()).getForegroundValue(),
                   "only dark had a legibility bug; light modern must not move");
   }

   @Test
   void aLegacyInputValueKeepsTheBlackDefault() {
      gateOff();
      VSFormat def = detailFormat(newCheckBox());

      // VSFormat:168 seeds fgval "0" on every fresh format, so black is the pristine value here,
      // not an absent one - the legacy branch has to restore it rather than clear it
      assertEquals("0", def.getForegroundValue());
   }

   @Test
   void aDarkRangeSliderValueTakesTheLightInk() {
      gateOn();
      SreeEnv.setProperty("viewsheet.darkMode", "true");
      VSFormat def = objectDefault(newTimeSlider());

      assertEquals(VSObjectChromeDefaults.darkForegroundValue(), def.getForegroundValue(),
                   "VSTimeSlider paints its min/max labels from the object foreground");
   }

   @Test
   void aLegacyRangeSliderValueKeepsTheGateOffInk() {
      gateOff();
      VSFormat def = objectDefault(newTimeSlider());

      assertEquals("0", def.getForegroundValue(),
                   "black is the pristine value from VSFormat:168, so legacy restores it");
   }

   @Test
   void revertingADarkInputRestoresAGateOffCreation() {
      gateOff();
      String expected = detailFormat(newCheckBox()).getForegroundValue();

      gateOn();
      SreeEnv.setProperty("viewsheet.darkMode", "true");
      CheckBoxVSAssemblyInfo info = newCheckBox();
      info.setVizMark(VizMark.fromGate());
      info.seedChromeDefaults(VizContext.of(info));

      info.setVizMark(null);
      info.seedChromeDefaults(VizContext.of((VizMark) null));

      assertEquals(expected, detailFormat(info).getForegroundValue(),
                   "Revert must match a checkbox that was never modernized");
   }

   @Test
   void revertingADarkRangeSliderRestoresAGateOffCreation() {
      gateOff();
      String expected = objectDefault(newTimeSlider()).getForegroundValue();

      gateOn();
      SreeEnv.setProperty("viewsheet.darkMode", "true");
      TimeSliderVSAssemblyInfo info = newTimeSlider();
      info.setVizMark(VizMark.fromGate());
      info.seedChromeDefaults(VizContext.of(info));

      info.setVizMark(null);
      info.seedChromeDefaults(VizContext.of((VizMark) null));

      assertEquals(expected, objectDefault(info).getForegroundValue());
   }

   // ---- the calendar title, its month/year header and its body cells --------------------------

   @Test
   void aModernCalendarTitleIsUnfilledWithABottomRule() {
      gateOn();
      VizContext ctx = VizContext.ofGate();
      VSFormat def = titleFormat(newCalendar()).getDefaultFormat();

      assertNull(def.getBackgroundValue(), "the modern title lane carries no fill");
      assertEquals(StyleConstants.THIN_LINE, def.getBordersValue().bottom);
      assertEquals(VSTitleChromeDefaults.titleBorderColor(ctx),
                   def.getBorderColorsValue().bottomColor);
      assertEquals(VSTitleChromeDefaults.titleForegroundValue(ctx), def.getForegroundValue());
   }

   @Test
   void aLegacyCalendarTitleKeepsTheFilledBand() {
      gateOff();
      VSFormat def = titleFormat(newCalendar()).getDefaultFormat();

      assertEquals(VSAssemblyInfo.DEFAULT_TITLE_BG, def.getBackgroundValue(),
                   "a gate-off calendar title is filled, unlike an input title");
      assertNull(def.getForegroundValue());
   }

   @Test
   void aModernCalendarHeaderTakesTheTableHeaderNeutrals() {
      gateOn();
      VizContext ctx = VizContext.ofGate();
      VSFormat def = calendarPath(newCalendar(), TableDataPath.CALENDAR_TITLE);

      assertEquals(colorValue(VSTableStructureDefaults.headerForeground(ctx)),
                   def.getForegroundValue(),
                   "the month/year header is a header band, and tracks the table's");
      assertEquals(colorValue(VSTableStructureDefaults.headerBackground(ctx)),
                   def.getBackgroundValue());
   }

   @Test
   void aLegacyCalendarHeaderTakesNoHeaderNeutral() {
      gateOff();
      VSFormat def = calendarPath(newCalendar(), TableDataPath.CALENDAR_TITLE);

      assertNull(def.getForegroundValue(),
                 "a gate-off calendar header must not carry the modern header neutral");
      assertNull(def.getBackgroundValue());
   }

   @Test
   void aModernCalendarBodyTracksTheTableBodyInBothSchemes() {
      gateOn();
      VizContext light = VizContext.ofGate();
      VSFormat def = calendarPath(newCalendar(), TableDataPath.MONTH_CALENDAR);

      assertNull(VSTableStructureDefaults.bodyForeground(light),
                 "precondition: the table body is unthemed in light modern too");
      assertNull(def.getForegroundValue(), "so the calendar body is unthemed there as well");

      SreeEnv.setProperty("viewsheet.darkMode", "true");
      VizContext dark = VizContext.ofGate();
      VSFormat darkDef = calendarPath(newCalendar(), TableDataPath.YEAR_CALENDAR);

      assertEquals(colorValue(VSTableStructureDefaults.bodyForeground(dark)),
                   darkDef.getForegroundValue());
      assertEquals(colorValue(VSTableStructureDefaults.bodyBackground(dark)),
                   darkDef.getBackgroundValue());
      assertNotNull(darkDef.getForegroundValue(), "dark is the branch that has a value");
   }

   @Test
   void revertingACalendarRestoresAGateOffCreation() {
      gateOff();
      CalendarVSAssemblyInfo expected = newCalendar();
      String expectedTitleBg = titleFormat(expected).getDefaultFormat().getBackgroundValue();
      String expectedBodyFg =
         calendarPath(expected, TableDataPath.MONTH_CALENDAR).getForegroundValue();

      gateOn();
      CalendarVSAssemblyInfo info = newCalendar();
      info.setVizMark(VizMark.fromGate());
      info.seedChromeDefaults(VizContext.of(info));

      info.setVizMark(null);
      info.seedChromeDefaults(VizContext.of((VizMark) null));

      assertEquals(expectedTitleBg, titleFormat(info).getDefaultFormat().getBackgroundValue());
      assertEquals(expectedBodyFg,
                   calendarPath(info, TableDataPath.MONTH_CALENDAR).getForegroundValue());
   }

   /**
    * A dropdown calendar's title is not a normal calendar's: a boxed white field, not a filled band
    * with one hairline. The legacy branch has to restore whichever prototype applies.
    */
   @Test
   void aLegacyDropdownCalendarKeepsTheBoxedWhiteTitle() {
      gateOff();
      VSFormat def = titleFormat(newDropdownCalendar()).getDefaultFormat();

      assertEquals(new Insets(StyleConstants.THIN_LINE, StyleConstants.THIN_LINE,
                              StyleConstants.THIN_LINE, StyleConstants.THIN_LINE),
                   def.getBordersValue());
      assertEquals(VSAssemblyInfo.DEFAULT_BORDER_COLOR, def.getBorderColorsValue().bottomColor);
      assertEquals("0xffffff", def.getBackgroundValue());
   }

   /**
    * The dropdown prototype stores no cell formats, so the seed has to install them: a null lookup
    * would leave a dark dropdown calendar painting black date cells on the dark card.
    */
   @Test
   void aDarkDropdownCalendarBodyTakesTheTableNeutrals() {
      gateOn();
      SreeEnv.setProperty("viewsheet.darkMode", "true");
      VizContext dark = VizContext.ofGate();
      CalendarVSAssemblyInfo info = newDropdownCalendar();

      assertEquals(colorValue(VSTableStructureDefaults.bodyForeground(dark)),
                   calendarPath(info, TableDataPath.MONTH_CALENDAR).getForegroundValue());
      assertEquals(colorValue(VSTableStructureDefaults.bodyBackground(dark)),
                   calendarPath(info, TableDataPath.YEAR_CALENDAR).getBackgroundValue());
      assertEquals(colorValue(VSTableStructureDefaults.headerForeground(dark)),
                   calendarPath(info, TableDataPath.CALENDAR_TITLE).getForegroundValue());
   }

   /**
    * Modernize reaches a dropdown calendar that predates the seed, whose stored format carries the
    * object and title paths only.
    */
   @Test
   void modernizingAPreExistingDropdownCalendarSeedsItsCells() {
      gateOff();
      CalendarVSAssemblyInfo info = newDropdownCalendar();
      assertNull(info.getFormatInfo().getFormat(
                    new TableDataPath(-1, TableDataPath.MONTH_CALENDAR)),
                 "precondition: the dropdown prototype stores no cell format");

      gateOn();
      SreeEnv.setProperty("viewsheet.darkMode", "true");
      VizContext dark = VizContext.ofGate();
      info.setVizMark(VizMark.fromGate());
      info.seedChromeDefaults(VizContext.of(info));

      assertEquals(colorValue(VSTableStructureDefaults.bodyForeground(dark)),
                   calendarPath(info, TableDataPath.MONTH_CALENDAR).getForegroundValue());
   }

   /**
    * The whole stored format, not one value: Revert has to leave a dropdown calendar value-equal to
    * a gate-off creation, because that equality is what copyViewInfo's show-type guard compares.
    */
   @Test
   void revertingADropdownCalendarRestoresAGateOffCreation() {
      gateOff();
      FormatInfo expected = newDropdownCalendar().getFormatInfo();

      gateOn();
      SreeEnv.setProperty("viewsheet.darkMode", "true");
      CalendarVSAssemblyInfo info = newDropdownCalendar();
      assertNotEquals(expected, info.getFormatInfo(), "precondition: the seed changed something");

      info.setVizMark(null);
      info.seedChromeDefaults(VizContext.of((VizMark) null));

      assertEquals(expected, info.getFormatInfo());
   }

   /**
    * The gate-off body cells stay undefined rather than defined-null: getFormat(path, false) copies
    * the object format's own black down only onto an undefined foreground, and the year view paints
    * a grey fallback over any cell that resolves none.
    */
   @Test
   void aLegacyCalendarBodyResolvesTheObjectBlack() {
      gateOff();
      CalendarVSAssemblyInfo info = newCalendar();
      TableDataPath year = new TableDataPath(-1, TableDataPath.YEAR_CALENDAR);

      assertFalse(calendarPath(info, TableDataPath.YEAR_CALENDAR).isForegroundValueDefined(),
                  "a defined null would block the copy-down");
      assertEquals(Color.BLACK, info.getFormatInfo().getFormat(year, false).getForeground(),
                   "a gate-off calendar resolves black, as one created before the seed does");
   }

   /** And the modern seed stays defined, or the same copy-down would put black over it. */
   @Test
   void aDarkCalendarBodyKeepsItsSeededForegroundThroughTheCopyDown() {
      gateOn();
      SreeEnv.setProperty("viewsheet.darkMode", "true");
      VizContext dark = VizContext.ofGate();
      CalendarVSAssemblyInfo info = newCalendar();
      TableDataPath year = new TableDataPath(-1, TableDataPath.YEAR_CALENDAR);

      assertTrue(calendarPath(info, TableDataPath.YEAR_CALENDAR).isForegroundValueDefined());
      assertEquals(VSTableStructureDefaults.bodyForeground(dark),
                   info.getFormatInfo().getFormat(year, false).getForeground());
   }

   /** A dropdown calendar, reached the way the property dialog reaches it. */
   private CalendarVSAssemblyInfo newDropdownCalendar() {
      CalendarVSAssemblyInfo info = newCalendar();
      CalendarVSAssemblyInfo source = (CalendarVSAssemblyInfo) info.clone();
      source.setShowTypeValue(CalendarVSAssemblyInfo.DROPDOWN_SHOW_TYPE);
      info.copyInfo(source);
      return info;
   }

   // ---- the selection cell's foreground, seeded rather than substituted -----------------------

   private static VSFormat cellDefault(VSAssemblyInfo info) {
      return info.getFormatInfo()
         .getFormat(new TableDataPath(-1, TableDataPath.DETAIL)).getDefaultFormat();
   }

   private static VSFormat groupHeaderDefault(VSAssemblyInfo info) {
      return info.getFormatInfo()
         .getFormat(new TableDataPath(0, TableDataPath.GROUP_HEADER)).getDefaultFormat();
   }

   @Test
   void aDarkSelectionListSeedsTheLightCellForeground() {
      gateOn();
      SreeEnv.setProperty("viewsheet.darkMode", "true");
      assertEquals(VSObjectChromeDefaults.darkForegroundValue(),
                   cellDefault(newSelectionList()).getForegroundValue(),
                   "the light neutral is stored, not substituted at every render");
   }

   @Test
   void aLightSelectionListSeedsTheLegacyCellForeground() {
      gateOn();
      assertEquals(VSObjectChromeDefaults.legacyCellForegroundValue(),
                   cellDefault(newSelectionList()).getForegroundValue(),
                   "only dark moves the cell ink; light modern keeps the near-black");
   }

   @Test
   void aDarkSelectionTreeSeedsTheLightCellForeground() {
      gateOn();
      SreeEnv.setProperty("viewsheet.darkMode", "true");
      assertEquals(VSObjectChromeDefaults.darkForegroundValue(),
                   cellDefault(newSelectionTree()).getForegroundValue(),
                   "one seed on the shared base covers list and tree both");
   }

   @Test
   void revertingADarkSelectionListRestoresTheLegacyCellForeground() {
      gateOn();
      SreeEnv.setProperty("viewsheet.darkMode", "true");
      SelectionListVSAssemblyInfo info = newSelectionList();

      info.setVizMark(null);
      info.seedChromeDefaults(VizContext.of((VizMark) null));

      assertEquals(VSObjectChromeDefaults.legacyCellForegroundValue(),
                   cellDefault(info).getForegroundValue());
   }

   // A tree's non-leaf rows render through GROUP_HEADER composites, cloned from DETAIL by the
   // tree's own setDefaultFormat - which runs after creation's first seed, so a fresh tree looks
   // correct by accident. Modernize and Revert call the hook directly on an assembly that already
   // exists, and the base hook only ever wrote DETAIL, stranding every non-leaf row.

   @Test
   void modernizingAnUnmarkedDarkSelectionTreeSeedsTheLightGroupHeaderForeground() {
      gateOff();
      SelectionTreeVSAssemblyInfo info = newSelectionTree();
      assertEquals(VSObjectChromeDefaults.legacyCellForegroundValue(),
                   groupHeaderDefault(info).getForegroundValue());

      gateOn();
      SreeEnv.setProperty("viewsheet.darkMode", "true");
      info.seedChromeDefaults(VizContext.ofGate());

      assertEquals(VSObjectChromeDefaults.darkForegroundValue(),
                   groupHeaderDefault(info).getForegroundValue(),
                   "a non-leaf row must move to the light ink along with the leaf row");
   }

   @Test
   void revertingADarkSelectionTreeRestoresTheLegacyGroupHeaderForeground() {
      gateOn();
      SreeEnv.setProperty("viewsheet.darkMode", "true");
      SelectionTreeVSAssemblyInfo info = newSelectionTree();

      info.setVizMark(null);
      info.seedChromeDefaults(VizContext.of((VizMark) null));

      assertEquals(VSObjectChromeDefaults.legacyCellForegroundValue(),
                   groupHeaderDefault(info).getForegroundValue(),
                   "a non-leaf row must revert along with the leaf row");
   }

   @Test
   void aMeasureBarKeepsItsOwnForegroundWhenTheCellIsSeeded() {
      // the bar's foreground IS the bar colour; the seed must not reach it. Structural now: the
      // measure composites are separate paths, which is why the old predicate could be deleted
      gateOn();
      SreeEnv.setProperty("viewsheet.darkMode", "true");
      SelectionListVSAssemblyInfo info = newSelectionList();
      VSCompositeFormat bar = info.getFormatInfo().getFormat(info.getMeasureBarPath(0));

      assertNotEquals(VSObjectChromeDefaults.darkForegroundValue(),
                      bar.getDefaultFormat().getForegroundValue());
   }

   @Test
   void modernizingAnUnmarkedDarkSelectionListSeedsTheLightForeground() {
      gateOff();
      SelectionListVSAssemblyInfo info = newSelectionList();
      assertEquals(VSObjectChromeDefaults.legacyCellForegroundValue(),
                   cellDefault(info).getForegroundValue());

      gateOn();
      SreeEnv.setProperty("viewsheet.darkMode", "true");
      info.seedChromeDefaults(VizContext.ofGate());

      assertEquals(VSObjectChromeDefaults.darkForegroundValue(),
                   cellDefault(info).getForegroundValue());
   }

   @Test
   void anAuthorsCellForegroundOutranksTheSeed() {
      // the read-time resolver tested the USER and CSS tiers explicitly and skipped. The seed
      // writes the DEFAULT tier only, so the same property now rests on tier precedence — which is
      // why it needs asserting here rather than being assumed
      gateOn();
      SreeEnv.setProperty("viewsheet.darkMode", "true");
      SelectionListVSAssemblyInfo info = newSelectionList();
      VSCompositeFormat cell = info.getFormatInfo()
         .getFormat(new TableDataPath(-1, TableDataPath.DETAIL));
      cell.getUserDefinedFormat().setForegroundValue("0x123456");

      info.seedChromeDefaults(VizContext.of(info));

      assertEquals("0x123456", cell.getUserDefinedFormat().getForegroundValue(),
                   "the seed never touches the USER tier");
      assertEquals(new Color(0x123456), cell.getForeground(),
                   "and the composite still resolves the author's colour");
   }

   // ---- the slider's object foreground -------------------------------------------------------

   @Test
   void aDarkSliderSeedsTheLightForeground() {
      gateOn();
      SreeEnv.setProperty("viewsheet.darkMode", "true");
      assertEquals(VSObjectChromeDefaults.darkForegroundValue(),
                   objectDefault(newSlider()).getForegroundValue());
   }

   @Test
   void aLightSliderSeedsNoForegroundAtAll() {
      gateOn();
      VSFormat fmt = objectDefault(newSlider());
      assertNull(fmt.getForegroundValue(),
                 "the base setDefaultFormat writes no foreground, so light modern writes none " +
                 "either and the asset stays byte-identical to a legacy one");
      assertNull(fmt.getForeground(), "the fg field is nulled too, or a runtime value survives");
   }

   @Test
   void modernizingAnUnmarkedDarkSliderSeedsTheLightForeground() {
      gateOff();
      SliderVSAssemblyInfo info = newSlider();
      assertNull(objectDefault(info).getForegroundValue());

      gateOn();
      SreeEnv.setProperty("viewsheet.darkMode", "true");
      info.seedChromeDefaults(VizContext.ofGate());

      assertEquals(VSObjectChromeDefaults.darkForegroundValue(),
                   objectDefault(info).getForegroundValue());
   }

   @Test
   void anAuthorsSliderForegroundOutranksTheSeed() {
      gateOn();
      SreeEnv.setProperty("viewsheet.darkMode", "true");
      SliderVSAssemblyInfo info = newSlider();
      info.getFormat().getUserDefinedFormat().setForegroundValue("0x123456");

      info.seedChromeDefaults(VizContext.of(info));

      assertEquals(new Color(0x123456), info.getFormat().getForeground(),
                   "the seed writes the DEFAULT tier, so the author's colour still resolves");
   }

   @Test
   void revertingADarkSliderClearsTheSeededForeground() {
      gateOn();
      SreeEnv.setProperty("viewsheet.darkMode", "true");
      SliderVSAssemblyInfo info = newSlider();
      assertEquals(VSObjectChromeDefaults.darkForegroundValue(),
                   objectDefault(info).getForegroundValue());

      info.setVizMark(null);
      info.seedChromeDefaults(VizContext.of((VizMark) null));

      assertNull(objectDefault(info).getForegroundValue());
      assertNull(objectDefault(info).getForeground());
   }

   // ---- the text assembly's value emphasis ---------------------------------------------------

   @Test
   void aModernTextAssemblySeedsTheValueEmphasis() {
      gateOn();
      VizContext ctx = VizContext.ofGate();
      VSFormat fmt = objectDefault(newText());
      assertEquals(VSOutputChromeDefaults.valueForegroundValue(ctx), fmt.getForegroundValue());
      assertEquals(VSOutputChromeDefaults.valueBorderColor(ctx),
                   fmt.getBorderColorsValue().topColor);
   }

   @Test
   void aLegacyTextAssemblySeedsTheLegacyEmphasis() {
      gateOff();
      VizContext ctx = VizContext.ofGate();
      VSFormat fmt = objectDefault(newText());
      assertEquals(VSOutputChromeDefaults.valueForegroundValue(ctx), fmt.getForegroundValue());
      assertEquals(VSAssemblyInfo.DEFAULT_BORDER_COLOR, fmt.getBorderColorsValue().topColor);
   }

   @Test
   void revertingATextAssemblyRestoresTheLegacyEmphasis() {
      gateOn();
      TextVSAssemblyInfo info = newText();

      info.setVizMark(null);
      info.seedChromeDefaults(VizContext.of((VizMark) null));

      VSFormat fmt = objectDefault(info);
      assertEquals(VSOutputChromeDefaults.valueForegroundValue(VizContext.of((VizMark) null)),
                   fmt.getForegroundValue());
      assertEquals(VSAssemblyInfo.DEFAULT_BORDER_COLOR, fmt.getBorderColorsValue().topColor);
   }

   @Test
   void aTextAssemblyWithNoBorderGainsNoBorderColour() {
      // setDefaultFormat(false) writes no border colours, and the seed must not invent one on
      // either branch: an unmarked asset has to stay bit-for-bit unchanged, and a marked one
      // created with border == false must not gain a colour it never had either
      gateOn();
      TextVSAssemblyInfo legacy = new TextVSAssemblyInfo();
      legacy.setDefaultFormat(false);
      assertNull(objectDefault(legacy).getBorderColorsValue(),
                 "unmarked: new TextVSAssemblyInfo() has no vizMark, so this only ever proved " +
                 "the legacy branch without a mark set first");

      TextVSAssemblyInfo modern = new TextVSAssemblyInfo();
      modern.setVizMark(VizMark.MODERN_LIGHT);
      modern.setDefaultFormat(false);
      assertNull(objectDefault(modern).getBorderColorsValue(),
                 "marked: the modern branch must honour the same guard");
   }

   @Test
   void anAuthorsTextForegroundOutranksTheSeed() {
      gateOn();
      TextVSAssemblyInfo info = newText();
      info.getFormat().getUserDefinedFormat().setForegroundValue("0x123456");

      info.seedChromeDefaults(VizContext.ofGate());

      assertEquals(new Color(0x123456), info.getFormat().getForeground());
   }

   @Test
   void aRestoredStateReseedsAStaleValue() {
      // VizModernizeUtil.reseedAfterRestore delegates to the same hook, so one test covers the
      // mechanism for all four conversions rather than four near-identical ones. What it pins is
      // that a bookmark's stale DEFAULT tier is re-resolved rather than trusted
      gateOn();
      TextVSAssemblyInfo info = newText();
      info.getFormat().getDefaultFormat().setForegroundValue("0x2b2b2b");

      VizModernizeUtil.reseedAfterRestore(info);

      assertEquals(VSOutputChromeDefaults.valueForegroundValue(VizContext.of(info)),
                   objectDefault(info).getForegroundValue(),
                   "restore re-resolves the seed against the live mark");
   }

   @Test
   void aTextAssemblyStillTakesNoCardRadiusUnderTheGate() {
      // documents that a fresh text assembly has no card radius - true, but not a guard:
      // isCornerSeedTarget() already excludes Text, so the radius resolves to 0 whether or not
      // the override's super call actually returns at bypassesBaseChrome.
      // theHookDoesNothingForATabEvenUnderTheGate is where the bypass is actually observable,
      // against a type (Tab) whose own non-zero radius the base would otherwise clobber
      gateOn();
      assertEquals(0, objectDefault(newText()).getRoundCornerValue());
   }
}
