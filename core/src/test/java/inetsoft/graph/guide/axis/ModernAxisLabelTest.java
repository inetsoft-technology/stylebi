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
package inetsoft.graph.guide.axis;

import inetsoft.graph.AxisSpec;
import inetsoft.graph.EGraph;
import inetsoft.graph.Plotter;
import inetsoft.graph.VGraph;
import inetsoft.graph.coord.RectCoord;
import inetsoft.graph.data.DefaultDataSet;
import inetsoft.graph.element.IntervalElement;
import inetsoft.graph.guide.VLabel;
import inetsoft.graph.internal.GTool;
import inetsoft.graph.scale.CategoricalScale;
import inetsoft.graph.aesthetic.DefaultTextFrame;
import inetsoft.graph.scale.LinearScale;
import inetsoft.graph.scale.TimeScale;
import inetsoft.sree.SreeEnv;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import inetsoft.util.ExtendedDateFormat;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The axis label length limit: labels are cut with an ellipsis at the limit and thinned
 * without overlap, and a classic axis (no limit) is unchanged.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = BaseTestConfiguration.class,
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class ModernAxisLabelTest {
   private static final String LONG = "Northeast Regional Distribution Hub";
   private static final double CLASSIC_SMALL_45_WIDTH = 103;
   private static final List<Integer> CLASSIC_STORES_SHOWN =
      java.util.stream.IntStream.range(0, 30).boxed().toList();
   // BarVO's minimum bar width
   private static final double BAR_MIN_WIDTH = 10;
   // 105 categories at 90 degrees reserve one line each, measured before the label limit
   private static final double CLASSIC_105_MIN_PLOT = 1470;

   @AfterEach
   void clearProperties() {
      SreeEnv.setProperty("viewsheet.modernVisualization", null);
      SreeEnv.setProperty("viewsheet.darkMode", null);
      SreeEnv.setProperty("viewsheet.density", null);
   }

   @Test
   void aLimitedLabelEndsInAnEllipsisAt66px() {
      DefaultAxis axis = xAxis(eightWithLong(), 66, 0, 0, 800, 300);
      VLabel[] labels = axis.getLabels();

      assertTrue(text(labels[3]).endsWith("…"), text(labels[3]));
      assertTrue(width(labels[3]) <= 66 + 0.5, "width " + width(labels[3]));
      assertEquals("Store 1", text(labels[0]));
   }

   @Test
   void theLimitHoldsOnASmallCard() {
      DefaultAxis axis = xAxis(eightWithLong(), 66, 0, 0, 300, 180);
      VLabel label = axis.getLabels()[3];

      assertTrue(text(label).endsWith("…"), text(label));
      assertTrue(width(label) <= 66 + 0.5, "width " + width(label));
   }

   @Test
   void theLimitAppliesAt45Degrees() {
      DefaultAxis axis = xAxis(eightWithLong(), 66, 45, 0, 800, 300);
      VLabel label = axis.getLabels()[3];
      FontMetrics fm = GTool.getFontMetrics(label.getFont());

      assertTrue(text(label).endsWith("…"), text(label));
      assertTrue(width(label) <= 66 + 0.5, "width " + width(label));

      double extent = (66 + fm.getHeight()) * Math.sin(Math.PI / 4);
      assertEquals(extent, label.getBounds().getHeight(), 2, "rotated label extent");
      assertTrue(axis.getBounds().getHeight() >= extent - 2, "axis height");
   }

   @Test
   void limitedLabelsNeverOverlap() {
      DefaultAxis axis = xAxis(stores(30), 66, 0, 0, 400, 240);
      List<Rectangle2D> shown = shownBounds(axis);

      assertTrue(shown.size() > 1);

      for(int i = 0; i < shown.size() - 1; i++) {
         for(int j = i + 1; j < shown.size(); j++) {
            assertFalse(shown.get(i).intersects(shown.get(j)),
                        "labels " + i + " and " + j + " overlap " + shown + " " + shownIndices(axis));
         }
      }
   }

   @Test
   void limitedLabelsNeverOverlapAt45Degrees() {
      DefaultAxis axis = xAxis(stores(30), 66, 45, 0, 400, 240);
      List<Integer> idx = shownIndices(axis);
      List<java.awt.geom.Area> areas = new ArrayList<>();

      for(int i : idx) {
         VLabel label = axis.getLabels()[i];
         Font font = label.getFont();
         double x = label.getTextPosition().getX();
         double y = label.getTextPosition().getY();
         Rectangle2D box = new Rectangle2D.Double(
            x, y, GTool.stringWidth(label.getDisplayLabel(), font),
            GTool.getFontMetrics(font).getHeight());
         areas.add(new java.awt.geom.Area(java.awt.geom.AffineTransform
            .getRotateInstance(Math.toRadians(45), x, y).createTransformedShape(box)));
      }

      assertTrue(idx.size() > 1, "shown " + idx);

      for(int i = 0; i < areas.size() - 1; i++) {
         for(int j = i + 1; j < areas.size(); j++) {
            java.awt.geom.Area a = (java.awt.geom.Area) areas.get(i).clone();
            a.intersect(areas.get(j));
            assertTrue(a.isEmpty(), "labels " + idx.get(i) + " and " + idx.get(j) + " overlap");
         }
      }

      int step = idx.get(1) - idx.get(0);

      for(int i = 1; i < idx.size(); i++) {
         assertEquals(step, idx.get(i) - idx.get(i - 1), "shown " + idx);
      }
   }

   @Test
   void shownLabelsKeepARegularStep() {
      DefaultAxis axis = xAxis(stores(30), 66, 0, 0, 400, 240);
      List<Integer> idx = shownIndices(axis);

      assertTrue(idx.size() > 1, "shown " + idx);
      int step = idx.get(1) - idx.get(0);

      for(int i = 1; i < idx.size(); i++) {
         assertEquals(step, idx.get(i) - idx.get(i - 1), "shown " + idx);
      }
   }

   @Test
   void aFixedAxisSizeBeatsTheLimit() {
      double auto = xAxis(eightWithLong(), 66, 45, 0, 800, 300).getBounds().getHeight();
      double fixed = xAxis(eightWithLong(), 66, 45, 30, 800, 300).getBounds().getHeight();

      assertEquals(30, fixed, 0.5);
      assertTrue(auto > 30 + 5, "auto " + auto);
   }

   @Test
   void classicLabelsKeepTwoDotsAndTheFloor() {
      VLabel wide = xAxis(eightWithLong(), 0, 0, 0, 800, 300).getLabels()[3];
      assertTrue(text(wide).endsWith(".."), text(wide));
      assertFalse(text(wide).contains("…"));

      VLabel small = xAxis(eightWithLong(), 0, 45, 0, 300, 180).getLabels()[3];
      assertTrue(text(small).endsWith(".."), text(small));
      assertEquals(CLASSIC_SMALL_45_WIDTH, width(small), 0.5);
   }

   @Test
   void classicCategoryAxisStillToleratesOverlap() {
      DefaultAxis axis = xAxis(stores(30), 0, 0, 0, 400, 240);
      assertEquals(CLASSIC_STORES_SHOWN, shownIndices(axis));
   }

   @Test
   void monthsShowTheYearOnlyWhereItChanges() {
      DefaultAxis axis = dateAxis(months(2025, Calendar.JANUARY, 24),
                                  new CompactDates(TimeScale.MONTH, Locale.US), null, 1600, 0);
      List<String> texts = shownTexts(axis);

      assertEquals(24, texts.size(), texts.toString());
      assertEquals("Jan 2025", texts.get(0));
      assertEquals("Feb", texts.get(1));
      assertEquals("Dec", texts.get(11));
      assertEquals("Jan 2026", texts.get(12));
      assertEquals("Feb", texts.get(13));
      assertEquals("Dec", texts.get(23));
   }

   @Test
   void daysShowTheMonthOnEveryLabel() {
      Date[] days = new Date[10];

      for(int i = 0; i < days.length; i++) {
         days[i] = date(2025, Calendar.DECEMBER, 29 + i);
      }

      List<String> texts = shownTexts(dateAxis(days, new CompactDates(TimeScale.DAY, Locale.US),
                                               null, 1600, 0));

      assertEquals(List.of("Dec 29, 2025", "Dec 30", "Dec 31", "Jan 1, 2026", "Jan 2",
                           "Jan 3", "Jan 4", "Jan 5", "Jan 6", "Jan 7"), texts);
   }

   @Test
   void theYearMovesToTheFirstShownLabel() {
      DefaultAxis axis = dateAxis(months(2025, Calendar.FEBRUARY, 24),
                                  new CompactDates(TimeScale.MONTH, Locale.US), null, 400, 66);
      assertTrue(axis.getScale().getAxisSpec().getLabelLimit() > 0);
      List<Integer> idx = shownIndices(axis);
      List<String> texts = shownTexts(axis);

      assertTrue(idx.size() < 24, "shown " + idx);
      assertFalse(idx.contains(11), "Jan 2026 should be thinned out " + idx);

      int firstOf2026 = -1;

      for(int k = 0; k < idx.size(); k++) {
         if(idx.get(k) > 11) {
            firstOf2026 = k;
            break;
         }
      }

      assertTrue(firstOf2026 > 0, "shown " + idx);
      assertTrue(texts.get(firstOf2026).contains("2026"), texts.toString());

      for(int k = 0; k < texts.size(); k++) {
         if(k != 0 && k != firstOf2026) {
            assertFalse(texts.get(k).contains("20"), texts.toString());
         }
      }

      List<Rectangle2D> shown = shownBounds(axis);

      for(int i = 0; i < shown.size() - 1; i++) {
         for(int j = i + 1; j < shown.size(); j++) {
            assertFalse(shown.get(i).intersects(shown.get(j)), "labels overlap " + texts);
         }
      }
   }

   @Test
   void aTimeScaleTakesItsTickUnit() {
      TimeScale scale = new TimeScale("Date");
      scale.setMin(date(2026, Calendar.JANUARY, 1));
      scale.setMax(date(2026, Calendar.JUNE, 30));
      scale.setType(TimeScale.MONTH);
      scale.getAxisSpec().setCompactDates(new CompactDates(CompactDates.FROM_SCALE, Locale.US));

      EGraph graph = new EGraph();
      graph.addElement(new IntervalElement("Date", "Sales"));
      graph.setCoordinate(new RectCoord(scale, new LinearScale("Sales")));
      Object[][] rows = { { "Date", "Sales" }, { date(2026, Calendar.JANUARY, 1), 1 },
                          { date(2026, Calendar.JUNE, 30), 2 } };
      VGraph vgraph = Plotter.getPlotter(graph)
         .plotAndLayout(new DefaultDataSet(rows), 0, 0, 1600, 300);
      DefaultAxis axis = ((RectCoord) vgraph.getCoordinate()).getXAxis1();
      axis.prepareLabels();
      List<String> texts = shownTexts(axis);

      assertTrue(texts.size() >= 3, texts.toString());
      assertEquals("Jan 2026", texts.get(0));
      assertEquals("Feb", texts.get(1));
      assertEquals("Mar", texts.get(2));
   }

   @Test
   void anAliasedValueKeepsItsAlias() {
      DefaultTextFrame alias = new DefaultTextFrame("Date");
      Date[] dates = months(2025, Calendar.JANUARY, 4);
      alias.setText(dates[2], "Spring");
      List<String> texts = shownTexts(dateAxis(dates, new CompactDates(TimeScale.MONTH, Locale.US),
                                               alias, 1600, 0));

      assertEquals(List.of("Jan 2025", "Feb", "Spring", "Apr"), texts);
   }

   @Test
   void classicDatesUnchanged() {
      List<String> texts = shownTexts(dateAxis(months(2025, Calendar.JANUARY, 3), null, null, 1600, 0));

      assertEquals(3, texts.size());
      assertEquals(List.of("2025-01-01", "2025-02-01", "2025-03-01"), texts);
   }

   @Test
   void aYearLabelNeverGrowsIntoItsPreviousNeighbour() {
      // thinned months at many widths: the first label of a year grows both ways
      assertCleanAcrossWidths(months(2025, Calendar.FEBRUARY, 30),
                              new CompactDates(TimeScale.MONTH, Locale.US), 1);
   }

   @Test
   void aYearLabelNeverReachesPastTheNextShownLabel() {
      Date[] days = new Date[60];

      for(int i = 0; i < days.length; i++) {
         days[i] = date(2025, Calendar.DECEMBER, 20 + i);
      }

      assertCleanAcrossWidths(days, new CompactDates(TimeScale.DAY, Locale.US), 2);
   }

   @Test
   void anHourlyCategoryAxisDropsTheRepeatedYear() {
      Object[][] rows = new Object[9][];
      rows[0] = new Object[] { "Date", "Sales" };

      for(int i = 1; i < 9; i++) {
         rows[i] = new Object[] { new java.sql.Timestamp(126, 0, 5, 8 + i, 0, 0, 0), i * 10.0 };
      }

      CategoricalScale xscale = new CategoricalScale("Date");
      xscale.getAxisSpec().getTextSpec().setFormat(new ExtendedDateFormat("yyyy-MM-dd HH"));
      xscale.getAxisSpec().setAbbreviate(true);
      xscale.getAxisSpec().setLabelLimit(66);
      List<String> texts = shownTexts(layout(xscale, rows, 800, 300));

      assertEquals(8, texts.size(), texts.toString());
      assertEquals("2026-01-05 09", texts.get(0));
      assertEquals("01-05 10", texts.get(1));
      assertEquals("01-05 16", texts.get(7));
   }

   @Test
   void aYearLabelKeepsTheThinningGap() {
      // one month every 15px: a grown year label would otherwise touch its neighbour
      assertThinningGap(400, 240);

      for(int width = 200; width <= 900; width += 7) {
         assertThinningGap(width, 240);
      }
   }

   @Test
   void theFirstLabelStaysMovedInsideAfterTheYearMoves() {
      // with the Y labels hidden the first label starts left of the graph
      CategoricalScale xscale = new CategoricalScale("Date");
      xscale.getAxisSpec().setCompactDates(new CompactDates(TimeScale.MONTH, Locale.US));
      xscale.getAxisSpec().setLabelLimit(66);
      LinearScale yscale = new LinearScale("Sales");
      yscale.getAxisSpec().setLabelVisible(false);
      DefaultAxis axis = layout(xscale, yscale, dateRows(months(2025, Calendar.FEBRUARY, 24)),
                                400, 240);
      List<String> texts = shownTexts(axis);
      VLabel first = axis.getLabels()[0];
      Rectangle2D bounds = axis.getBounds();
      double centred = bounds.getX() + bounds.getWidth() / 24 / 2 - width(first) / 2;

      assertTrue(texts.contains("Feb 2026") && !shownIndices(axis).contains(11),
                 "premise: the 2026 year moved to Feb " + texts);
      assertTrue(first.getZIndex() >= 0, "premise: the first label shows");
      assertTrue(centred < 0, "premise: centred under its tick it starts at " + centred);
      assertTrue(first.getTextPosition().getX() > centred + 0.5,
                 "first label at " + first.getTextPosition().getX() + ", centred at " + centred);
   }

   @Test
   void monthsThinInOneStepWithTheirYears() {
      Date[] dates = months(2024, Calendar.JANUARY, 24);
      DefaultAxis axis = dateAxis(dates, new CompactDates(TimeScale.MONTH, Locale.US), null,
                                  400, 240, 66);

      assertTrue(shownIndices(axis).size() < 24, "premise: thinned " + shownTexts(axis));
      assertOneStep(axis, "400x240");
      assertYearsWhereTheyChange(axis, dates, "400x240");
      assertApart(axis, "400x240");
   }

   @Test
   void aLongDayAxisThinsInOneStep() {
      Date[] dates = days(2026, Calendar.JANUARY, 5, 120);
      DefaultAxis axis = dateAxis(dates, new CompactDates(TimeScale.DAY, Locale.US), null,
                                  400, 240, 66);

      assertEquals("Jan 5, 2026", shownTexts(axis).get(0));
      assertOneStep(axis, "400x240");
      assertApart(axis, "400x240");
   }

   @Test
   void datesThinInOneStepAtEveryWidth() {
      Date[][] axes = { months(2024, Calendar.JANUARY, 24), months(2025, Calendar.FEBRUARY, 30),
                        days(2026, Calendar.JANUARY, 5, 120), days(2025, Calendar.DECEMBER, 20, 60) };
      int[] units = { TimeScale.MONTH, TimeScale.MONTH, TimeScale.DAY, TimeScale.DAY };

      for(int a = 0; a < axes.length; a++) {
         for(int width = 200; width <= 900; width += 7) {
            DefaultAxis axis = dateAxis(axes[a], new CompactDates(units[a], Locale.US), null,
                                        width, 240, 66);
            String where = "axis " + a + " at " + width;

            assertOneStep(axis, where);
            assertYearsWhereTheyChange(axis, axes[a], where);
            assertApart(axis, where);
         }
      }
   }

   // the year changes in the last few labels, whose full text runs past the right edge
   @Test
   void daysEndingInANewYearKeepTheStepAndTheEdge() {
      assertCleanEndAcrossWidths(days(2025, Calendar.NOVEMBER, 20, 45), TimeScale.DAY);
   }

   @Test
   void daysEndingSoonAfterANewYearKeepTheStepAndTheEdge() {
      assertCleanEndAcrossWidths(days(2025, Calendar.DECEMBER, 1, 33), TimeScale.DAY);
   }

   @Test
   void monthsEndingInANewYearKeepTheEdge() {
      assertCleanEndAcrossWidths(months(2025, Calendar.FEBRUARY, 25), TimeScale.MONTH);
   }

   private static void assertCleanEndAcrossWidths(Date[] dates, int unit) {
      for(int width = 160; width <= 900; width += 3) {
         DefaultAxis axis = dateAxis(dates, new CompactDates(unit, Locale.US), null, width, 240, 66);
         String where = "at " + width;

         assertOneStep(axis, where);
         assertInsideTheGraph(axis, where);
         assertYearsWhereTheyChange(axis, dates, where);
      }
   }

   @Test
   void aHiddenYAxisKeepsTheStartInside() {
      // without Y labels the first labels run past the left edge of the graph
      Date[] dates = days(2026, Calendar.JANUARY, 5, 120);

      for(int width = 160; width <= 900; width += 3) {
         LinearScale yscale = new LinearScale("Sales");
         yscale.getAxisSpec().setLabelVisible(false);
         yscale.getAxisSpec().setAxisStyle(AxisSpec.AXIS_NONE);
         CategoricalScale xscale = new CategoricalScale("Date");
         xscale.getAxisSpec().setCompactDates(new CompactDates(TimeScale.DAY, Locale.US));
         xscale.getAxisSpec().setLabelLimit(66);
         DefaultAxis axis = layout(xscale, yscale, dateRows(dates), width, 240);
         String where = "no Y labels at " + width;

         assertOneStep(axis, where);
         assertInsideTheGraph(axis, where);
         assertYearsWhereTheyChange(axis, dates, where);
      }
   }

   @Test
   void hiddenYMonthsKeepTheirStep() {
      // Jan 2024 slides inside but crowds the next label in step, so it is hidden; the step
      // stays the one the labels on their ticks need
      Date[] dates = months(2024, Calendar.JANUARY, 24);
      DefaultAxis axis = layout(monthScale(), hiddenY(), dateRows(dates), 300, 180);

      assertStep(axis, 3, "300x180");
      assertInsideTheGraph(axis, "300x180");
      assertYearsWhereTheyChange(axis, dates, "300x180");
      assertApart(axis, "300x180");
   }

   @Test
   void theLastLabelInStepSlidesInside() {
      // 30 days from Jan 5, 2026: the label in step at the far end slides in
      Date[] dates = days(2026, Calendar.JANUARY, 5, 30);
      DefaultAxis axis = dateAxis(dates, new CompactDates(TimeScale.DAY, Locale.US), null,
                                  400, 240, 66);
      List<Integer> idx = shownIndices(axis);

      assertStep(axis, 4, "400x240");
      assertEquals((dates.length - 1) / 4 * 4, idx.get(idx.size() - 1),
                   idx + " " + shownTexts(axis));
      assertInsideTheGraph(axis, "400x240");
   }

   @Test
   void a45DegreeEndLabelStaysInsideTheChart() {
      // the first label slides inside; slid it crowds the next one in step, so it is hidden
      DefaultAxis axis = xAxis(campaigns(105), 66, 45, 0, 400, 240);

      assertStep(axis, 7, "105 campaigns at 45");
      assertEquals(7, shownIndices(axis).get(0), shownTexts(axis).toString());
      assertInsideTheGraph(axis, "105 campaigns at 45");
      assertApart(axis, "105 campaigns at 45");
   }

   @Test
   void a45DegreeEndLabelWithoutYLabelsKeepsTheStep() {
      CategoricalScale xscale = new CategoricalScale("Name");
      xscale.getAxisSpec().setLabelLimit(66);
      xscale.getAxisSpec().getTextSpec().setRotation(45);
      DefaultAxis axis = layout(xscale, hiddenY(), campaigns(105), 400, 240);

      assertStep(axis, 6, "105 campaigns at 45 without Y labels");
      assertInsideTheGraph(axis, "105 campaigns at 45 without Y labels");
      assertApart(axis, "105 campaigns at 45 without Y labels");
   }

   @Test
   void drawnGapsKeep4pxOnASmallCard() {
      // the text draws wider than its whole-pixel width
      DefaultAxis axis = dateAxis(months(2024, Calendar.JANUARY, 24),
                                  new CompactDates(TimeScale.MONTH, Locale.US), null, 300, 180,
                                  66);

      assertOneStep(axis, "300x180");
      assertApart(axis, "300x180");
   }

   @Test
   void largerLabelsThinInOneStepFromTheFirst() {
      // at 14px and 45 degrees the first labels reach left past the axis start
      DefaultAxis axis = xAxis(stores(30), 92.4, 45, 0, 400, 240, 14);
      List<Integer> idx = shownIndices(axis);

      assertEquals(0, idx.get(0), "shown " + idx);
      assertTrue(idx.size() > 2, "shown " + idx);
      assertOneStep(axis, "font 14");
      assertApart(axis, "font 14");
   }

   @Test
   void textLabelsKeepTheirGapAtEveryWidth() {
      Object[][] products = names("Muffin", "Pretzel", "Raisins", "Tortilla", "Bagel", "Scone",
                                  "Waffle", "Crumpet");

      for(int width = 200; width <= 900; width += 7) {
         for(int rotation : new int[] { 0, 45 }) {
            for(Object[][] rows : new Object[][][] { products, stores(30) }) {
               DefaultAxis axis = xAxis(rows, 66, rotation, 0, width, 240);
               String where = rows.length - 1 + " labels at " + rotation + " and " + width;

               assertOneStep(axis, where);
               assertApart(axis, where);
            }
         }
      }
   }

   @Test
   void aLimitedAxisReservesNoLabelWidth() {
      Object[][] rows = campaigns(105);
      double bars = 105 * BAR_MIN_WIDTH;

      assertEquals(bars, plot(rows, 66, 45, 400, 240).getMinPlotWidth(), 0.5);
      assertEquals(bars, plot(rows, 66, 0, 400, 240).getMinPlotWidth(), 0.5);
   }

   @Test
   void aClassicAxisStillReservesItsLabels() {
      assertEquals(CLASSIC_105_MIN_PLOT, plot(campaigns(105), 0, 90, 400, 240).getMinPlotWidth(),
                   0.5);
   }

   // adjacent shown labels keep the gap of a limited axis
   private static void assertThinningGap(int width, int height) {
      DefaultAxis axis = dateAxis(months(2025, Calendar.FEBRUARY, 24),
                                  new CompactDates(TimeScale.MONTH, Locale.US), null, width,
                                  height, 66);
      String where = width + "x" + height;

      assertOneStep(axis, where);
      assertApart(axis, where);
   }

   // the shown labels are every k-th label, the step the labels on their ticks need
   private static void assertStep(DefaultAxis axis, int step, String where) {
      List<Integer> idx = shownIndices(axis);

      assertTrue(idx.size() > 1, "shown " + idx + " at " + where);
      assertEquals(0, idx.get(0) % step, "on the grid " + idx + " at " + where);

      for(int i = 1; i < idx.size(); i++) {
         assertEquals(step, idx.get(i) - idx.get(i - 1),
                      "step at " + where + " " + idx + " " + shownTexts(axis));
      }
   }

   // the shown labels are every k-th from the first candidate
   private static void assertOneStep(DefaultAxis axis, String where) {
      List<Integer> idx = shownIndices(axis);

      if(idx.size() < 2) {
         return;
      }

      int step = idx.get(1) - idx.get(0);

      for(int i = 1; i < idx.size(); i++) {
         assertEquals(step, idx.get(i) - idx.get(i - 1),
                      "step at " + where + " " + idx + " " + shownTexts(axis));
      }
   }

   // every shown label's drawn text, rotated with it, stays in the graph
   private static void assertInsideTheGraph(DefaultAxis axis, String where) {
      Rectangle2D graph = axis.vgraph.getBounds();
      List<Integer> idx = shownIndices(axis);
      List<String> texts = shownTexts(axis);

      for(int i = 0; i < idx.size(); i++) {
         Rectangle2D box = textArea(axis.getLabels()[idx.get(i)], 0).getBounds2D();

         assertTrue(box.getX() >= graph.getX() - 0.01 && box.getMaxX() <= graph.getMaxX() + 0.01,
                    texts.get(i) + " at " + box.getX() + ".." + box.getMaxX() + " runs out of " +
                    graph.getX() + ".." + graph.getMaxX() + " at " + where + " " + texts);
      }
   }

   // the first shown label of each year carries the year, the others don't
   private static void assertYearsWhereTheyChange(DefaultAxis axis, Date[] dates, String where) {
      List<Integer> idx = shownIndices(axis);
      List<String> texts = shownTexts(axis);
      Calendar cal = Calendar.getInstance();
      int prevYear = -1;

      for(int i = 0; i < idx.size(); i++) {
         cal.setTime(dates[idx.get(i)]);
         int year = cal.get(Calendar.YEAR);

         if(year != prevYear) {
            assertTrue(texts.get(i).contains(String.valueOf(year)),
                       "no year on " + texts.get(i) + " at " + where + " " + texts);
         }
         else {
            assertFalse(texts.get(i).contains(String.valueOf(year)),
                        "year repeated on " + texts.get(i) + " at " + where + " " + texts);
         }

         prevYear = year;
      }
   }

   // flat labels keep 4px along the axis, rotated labels 2px across their text
   private static void assertApart(DefaultAxis axis, String where) {
      List<Integer> idx = shownIndices(axis);
      List<String> texts = shownTexts(axis);

      for(int i = 0; i < idx.size() - 1; i++) {
         VLabel label = axis.getLabels()[idx.get(i)];
         boolean flat = label.getTextSpec().getRotation() % 180 == 0;
         java.awt.geom.Area a = textArea(label, flat ? 0 : 2 - 1e-3);

         if(flat) {
            a.transform(java.awt.geom.AffineTransform.getTranslateInstance(4 - 1e-3, 0));
         }

         a.intersect(textArea(axis.getLabels()[idx.get(i + 1)], 0));

         assertTrue(area(a) < 1e-2, texts.get(i) + " and " + texts.get(i + 1) +
                    " are too close at " + where + " " + texts);
      }
   }

   // the text box of a label, grown across the text by `across`, rotated with the text
   private static java.awt.geom.Area textArea(VLabel label, double across) {
      Font font = label.getFont();
      double x = label.getTextPosition().getX();
      double y = label.getTextPosition().getY();
      Rectangle2D box = new Rectangle2D.Double(
         x, y - across, drawnWidth(label),
         GTool.getFontMetrics(font).getHeight() * label.getDisplayLabel().length + across * 2);
      double angle = Math.toRadians(label.getTextSpec().getRotation());
      return new java.awt.geom.Area(java.awt.geom.AffineTransform.getRotateInstance(angle, x, y)
                                       .createTransformedShape(box));
   }

   private static double area(java.awt.geom.Area shape) {
      double sum = 0;
      double[] pt = new double[6];
      double sx = 0, sy = 0, px = 0, py = 0;

      for(java.awt.geom.PathIterator it = shape.getPathIterator(null); !it.isDone(); it.next()) {
         int type = it.currentSegment(pt);

         if(type == java.awt.geom.PathIterator.SEG_MOVETO) {
            sx = px = pt[0];
            sy = py = pt[1];
         }
         else if(type == java.awt.geom.PathIterator.SEG_LINETO) {
            sum += px * pt[1] - pt[0] * py;
            px = pt[0];
            py = pt[1];
         }
         else if(type == java.awt.geom.PathIterator.SEG_CLOSE) {
            sum += px * sy - sx * py;
            px = sx;
            py = sy;
         }
      }

      return Math.abs(sum) / 2;
   }

   private static CategoricalScale monthScale() {
      CategoricalScale xscale = new CategoricalScale("Date");
      xscale.getAxisSpec().setCompactDates(new CompactDates(TimeScale.MONTH, Locale.US));
      xscale.getAxisSpec().setLabelLimit(66);
      return xscale;
   }

   // a Y scale with no labels and no axis line
   private static LinearScale hiddenY() {
      LinearScale yscale = new LinearScale("Sales");
      yscale.getAxisSpec().setLabelVisible(false);
      yscale.getAxisSpec().setAxisStyle(AxisSpec.AXIS_NONE);
      return yscale;
   }

   private static Date[] days(int year, int month, int day, int count) {
      Date[] dates = new Date[count];

      for(int i = 0; i < count; i++) {
         dates[i] = date(year, month, day + i);
      }

      return dates;
   }

   private static Object[][] names(String... names) {
      Object[][] rows = new Object[names.length + 1][];
      rows[0] = new Object[] { "Name", "Sales" };

      for(int i = 0; i < names.length; i++) {
         rows[i + 1] = new Object[] { names[i], (i + 1) * 100 };
      }

      return rows;
   }

   private static Object[][] campaigns(int n) {
      Object[][] rows = new Object[n + 1][];
      rows[0] = new Object[] { "Name", "Sales" };

      for(int i = 1; i <= n; i++) {
         rows[i] = new Object[] { String.format("Campaign %03d", i), i * 100 };
      }

      return rows;
   }

   private static VGraph plot(Object[][] rows, double limit, int rotation, int width, int height) {
      CategoricalScale xscale = new CategoricalScale("Name");
      AxisSpec spec = xscale.getAxisSpec();
      spec.setLabelLimit(limit);
      spec.getTextSpec().setRotation(rotation);

      EGraph graph = new EGraph();
      graph.addElement(new IntervalElement("Name", "Sales"));
      graph.setCoordinate(new RectCoord(xscale, new LinearScale("Sales")));
      return Plotter.getPlotter(graph).plotAndLayout(new DefaultDataSet(rows), 0, 0, width, height);
   }

   private static DefaultAxis layout(CategoricalScale xscale, Object[][] rows, int width,
                                     int height)
   {
      return layout(xscale, new LinearScale("Sales"), rows, width, height);
   }

   private static DefaultAxis layout(CategoricalScale xscale, LinearScale yscale, Object[][] rows,
                                     int width, int height)
   {
      EGraph graph = new EGraph();
      graph.addElement(new IntervalElement(xscale.getFields()[0], "Sales"));
      graph.setCoordinate(new RectCoord(xscale, yscale));
      VGraph vgraph = Plotter.getPlotter(graph)
         .plotAndLayout(new DefaultDataSet(rows), 0, 0, width, height);
      DefaultAxis axis = ((RectCoord) vgraph.getCoordinate()).getXAxis1();
      axis.prepareLabels();
      return axis;
   }

   // every shown label pair up to `reach` apart is apart, and a year starts each year's first
   private static void assertCleanAcrossWidths(Date[] dates, CompactDates compact, int reach) {
      for(int width = 200; width <= 900; width += 7) {
         DefaultAxis axis = dateAxis(dates, compact, null, width, 66);
         List<Integer> idx = shownIndices(axis);
         List<Rectangle2D> boxes = new ArrayList<>();

         for(Rectangle2D box : shownBounds(axis)) {
            boxes.add(new Rectangle2D.Double(box.getX(), box.getY() + 1, box.getWidth(),
                                             box.getHeight() - 2));
         }

         List<String> texts = shownTexts(axis);
         String where = "width " + width + " " + texts;
         Calendar cal = Calendar.getInstance();
         int prevYear = -1;

         for(int i = 0; i < boxes.size(); i++) {
            for(int j = i + 1; j < boxes.size() && j <= i + reach + 1; j++) {
               assertFalse(boxes.get(i).intersects(boxes.get(j)), "overlap " + where);
            }

            cal.setTime(dates[idx.get(i)]);
            int year = cal.get(Calendar.YEAR);

            if(year != prevYear) {
               assertTrue(texts.get(i).contains(String.valueOf(year)),
                          "no year on " + texts.get(i) + " at " + where);
            }

            prevYear = year;
         }
      }
   }

   private static Date date(int year, int month, int day) {
      Calendar cal = Calendar.getInstance();
      cal.clear();
      cal.set(year, month, day);
      return cal.getTime();
   }

   private static Date[] months(int year, int month, int count) {
      Date[] dates = new Date[count];

      for(int i = 0; i < count; i++) {
         dates[i] = date(year, month + i, 1);
      }

      return dates;
   }

   private static DefaultAxis dateAxis(Date[] dates, CompactDates compact, DefaultTextFrame alias,
                                       int width, double limit)
   {
      return dateAxis(dates, compact, alias, width, 300, limit);
   }

   private static DefaultAxis dateAxis(Date[] dates, CompactDates compact, DefaultTextFrame alias,
                                       int width, int height, double limit)
   {
      CategoricalScale xscale = new CategoricalScale("Date");
      AxisSpec spec = xscale.getAxisSpec();
      spec.setCompactDates(compact);
      spec.setTextFrame(alias);
      spec.setLabelLimit(limit);
      return layout(xscale, dateRows(dates), width, height);
   }

   private static Object[][] dateRows(Date[] dates) {
      Object[][] rows = new Object[dates.length + 1][];
      rows[0] = new Object[] { "Date", "Sales" };

      for(int i = 0; i < dates.length; i++) {
         rows[i + 1] = new Object[] { dates[i], (i + 1) * 100 };
      }

      return rows;
   }

   private static List<String> shownTexts(DefaultAxis axis) {
      List<String> texts = new ArrayList<>();

      for(int i : shownIndices(axis)) {
         texts.add(text(axis.getLabels()[i]));
      }

      return texts;
   }

   private static Object[][] stores(int n) {
      Object[][] rows = new Object[n + 1][];
      rows[0] = new Object[] { "Name", "Sales" };

      for(int i = 1; i <= n; i++) {
         rows[i] = new Object[] { String.format("Store %02d", i), i * 100 };
      }

      return rows;
   }

   private static Object[][] eightWithLong() {
      Object[][] rows = new Object[9][];
      rows[0] = new Object[] { "Name", "Sales" };

      for(int i = 1; i <= 8; i++) {
         rows[i] = new Object[] { i == 4 ? LONG : "Store " + i, i * 100 };
      }

      return rows;
   }

   private static DefaultAxis xAxis(Object[][] rows, double limit, int rotation,
                                    double axisSize, int width, int height)
   {
      return xAxis(rows, limit, rotation, axisSize, width, height, 0);
   }

   private static DefaultAxis xAxis(Object[][] rows, double limit, int rotation,
                                    double axisSize, int width, int height, float fontSize)
   {
      CategoricalScale xscale = new CategoricalScale("Name");
      AxisSpec spec = xscale.getAxisSpec();
      spec.setLabelLimit(limit);
      spec.getTextSpec().setRotation(rotation);
      spec.setAxisSize(axisSize);

      if(fontSize > 0) {
         spec.getTextSpec().setFont(spec.getTextSpec().getFont().deriveFont(fontSize));
      }

      EGraph graph = new EGraph();
      graph.addElement(new IntervalElement("Name", "Sales"));
      graph.setCoordinate(new RectCoord(xscale, new LinearScale("Sales")));
      VGraph vgraph = Plotter.getPlotter(graph)
         .plotAndLayout(new DefaultDataSet(rows), 0, 0, width, height);
      DefaultAxis axis = ((RectCoord) vgraph.getCoordinate()).getXAxis1();
      axis.prepareLabels();
      return axis;
   }

   private static String text(VLabel label) {
      return String.join("", label.getDisplayLabel());
   }

   private static double width(VLabel label) {
      return GTool.stringWidth(label.getDisplayLabel(), label.getFont());
   }

   // the width the text draws at: its fractional advance, wider than the whole-pixel width
   private static double drawnWidth(VLabel label) {
      java.awt.font.FontRenderContext frc = new java.awt.font.FontRenderContext(null, true, true);
      double w = width(label);

      for(String line : label.getDisplayLabel()) {
         w = Math.max(w, label.getFont().getStringBounds(line, frc).getWidth());
      }

      return w;
   }

   private static List<Integer> shownIndices(DefaultAxis axis) {
      List<Integer> idx = new ArrayList<>();
      VLabel[] labels = axis.getLabels();

      for(int i = 0; i < labels.length; i++) {
         if(labels[i].getZIndex() >= 0) {
            idx.add(i);
         }
      }

      return idx;
   }

   private static List<Rectangle2D> shownBounds(DefaultAxis axis) {
      List<Rectangle2D> list = new ArrayList<>();

      for(int i : shownIndices(axis)) {
         VLabel label = axis.getLabels()[i];
         Font font = label.getFont();
         list.add(new Rectangle2D.Double(label.getTextPosition().getX(),
                                         label.getTextPosition().getY(), drawnWidth(label),
                                         GTool.getFontMetrics(font).getHeight()));
      }

      return list;
   }
}
