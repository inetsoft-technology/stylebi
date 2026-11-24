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
import inetsoft.graph.scale.Scale;
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
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Comparator;
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
   private static final List<Integer> CLASSIC_STORES_SHOWN =
      java.util.stream.IntStream.range(0, 30).boxed().toList();
   // BarVO's minimum bar width
   private static final double BAR_MIN_WIDTH = 10;
   // a reversed axis without a modern property keeps today's walk from index 0
   private static final List<Integer> CLASSIC_REVERSED_STORES_SHOWN =
      java.util.stream.IntStream.range(0, 15).map(i -> i * 2).boxed().toList();
   private static final List<String> CLASSIC_REVERSED_HOURS = List.of(
      "2026-01-01 11", "01-01 09", "01-01 07", "01-01 05", "01-01 03", "01-01 01",
      "2025-12-31 23", "12-31 21", "12-31 19", "12-31 17", "12-31 15", "12-31 13");

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
      String cut = text(small);
      double cap = small.getMaxSize().getHeight();

      // cut to today's max(50, ...) band, not to the 66px limit
      assertTrue(cut.endsWith(".."), cut);
      assertTrue(cap >= 50, "cap " + cap);
      assertTrue(small.getPreferredHeight(cut) <= cap + 0.5, cut + " in " + cap);
      assertTrue(width(small) > 66, cut + " " + width(small));
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
      List<String> texts = shownFullTexts(layout(xscale, rows, 800, 300));

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
      // the first label slides inside; if slid it crowds the next one in step, it is hidden
      DefaultAxis axis = xAxis(campaigns(105), 66, 45, 0, 400, 240);
      List<Integer> idx = shownIndices(axis);
      int step = idx.get(1) - idx.get(0);

      assertStep(axis, step, "105 campaigns at 45");
      assertTrue(idx.get(0) <= step, "first " + idx + " " + shownTexts(axis));
      assertInsideTheGraph(axis, "105 campaigns at 45");
      assertApart(axis, "105 campaigns at 45");
   }

   @Test
   void a45DegreeEndLabelWithoutYLabelsKeepsTheStep() {
      CategoricalScale xscale = new CategoricalScale("Name");
      xscale.getAxisSpec().setLabelLimit(66);
      xscale.getAxisSpec().getTextSpec().setRotation(45);
      DefaultAxis axis = layout(xscale, hiddenY(), campaigns(105), 400, 240);
      List<Integer> idx = shownIndices(axis);

      assertStep(axis, idx.get(1) - idx.get(0), "105 campaigns at 45 without Y labels");
      assertInsideTheGraph(axis, "105 campaigns at 45 without Y labels");
      assertApart(axis, "105 campaigns at 45 without Y labels");
   }

   @Test
   void aSlidEndLabelNeverPassesItsNeighbour() {
      // long 45-degree labels slide in further than the step, past the labels after them
      Object[][] rows = new Object[61][];
      rows[0] = new Object[] { "Name", "Sales" };

      for(int i = 1; i <= 60; i++) {
         rows[i] = new Object[] { String.format("Northeast Distribution Center %02d", i), i };
      }

      CategoricalScale xscale = new CategoricalScale("Name");
      xscale.getAxisSpec().setLabelLimit(120);
      xscale.getAxisSpec().getTextSpec().setRotation(45);
      DefaultAxis axis = layout(xscale, hiddenY(), rows, 400, 240);
      String where = "60 long names at 45 without Y labels";

      assertInReadingOrder(axis, where);
      assertInsideTheGraph(axis, where);
      assertOneStep(axis, where);
      assertApart(axis, where);
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
      VGraph vgraph = plot(campaigns(105), 0, 90, 400, 240);
      Font font = ((RectCoord) vgraph.getCoordinate()).getXAxis1().getLabels()[0].getFont();

      // 105 categories at 90 degrees reserve one line each, measured before the label limit
      assertEquals(105 * GTool.getFontMetrics(font).getHeight(), vgraph.getMinPlotWidth(), 0.5);
   }

   @Test
   void aReversedMonthlyAxisPutsTheYearOnTheTopLabel() {
      List<String> expected = new ArrayList<>();

      for(int year = 2025; year <= 2026; year++) {
         expected.add("Jan " + year);
         expected.addAll(List.of("Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct",
                                 "Nov", "Dec"));
      }

      for(double limit : new double[] { 0, 66 }) {
         DefaultAxis axis = reversedDateAxis(months(2025, Calendar.JANUARY, 24),
                                             new CompactDates(TimeScale.MONTH, Locale.US), limit,
                                             800);
         List<Integer> idx = topDownIndices(axis);

         assertTrue(axis.isVertical(), "premise: the axis runs down the side");
         assertEquals(23, idx.get(0), "premise: the last index is at the top");
         assertEquals(expected, topDownTexts(axis), "limit " + limit);
      }
   }

   @Test
   void aReversedAxisMovesTheYearAfterThinning() {
      Date[] dates = months(2025, Calendar.JANUARY, 24);

      for(double limit : new double[] { 0, 66 }) {
         boolean moved = false;

         for(int height = 70; height <= 400; height += 5) {
            DefaultAxis axis = reversedDateAxis(dates, new CompactDates(TimeScale.MONTH, Locale.US),
                                                limit, height);
            String where = "limit " + limit + " at " + height + " " + topDownTexts(axis);
            Calendar cal = Calendar.getInstance();
            int prevYear = -1;

            for(int i : topDownIndices(axis)) {
               VDimensionLabel label = (VDimensionLabel) axis.getLabels()[i];
               // the drawn text: the column has room for a year that moves
               String text = text(label);
               cal.setTime((Date) label.getValue());
               int year = cal.get(Calendar.YEAR);

               if(year != prevYear) {
                  assertTrue(text.contains(String.valueOf(year)),
                             "no year on " + text + " " + where);
                  moved = moved || cal.get(Calendar.MONTH) != Calendar.JANUARY;
               }
               else {
                  assertFalse(text.contains("20"), "year repeated on " + text + " " + where);
               }

               prevYear = year;
            }

            if(limit > 0) {
               assertOneStep(axis, where);
            }
         }

         assertTrue(moved, "premise: a year moved past a hidden January at limit " + limit);
      }
   }

   @Test
   void aVerticalDateAxisDrawsAMovedYearInFull() {
      Date[][] monthly = { months(2024, Calendar.JANUARY, 24), months(2024, Calendar.JULY, 24) };
      Date[] daily = days(2025, Calendar.DECEMBER, 1, 62);
      int moved = 0;

      for(int height = 70; height <= 400; height += 5) {
         for(double limit : new double[] { 0, 66 }) {
            for(boolean reversed : new boolean[] { true, false }) {
               String where = "limit " + limit + (reversed ? " top-down" : " bottom-up") +
                  " at " + height;

               for(Date[] dates : monthly) {
                  DefaultAxis axis = sideDateAxis(dates, TimeScale.MONTH, limit, reversed, height);
                  assertTrue(axis.isVertical(), "premise: the axis runs down the side");
                  moved += assertYearsDrawnInFull(axis, "MMM yyyy", "month " + where);
               }

               DefaultAxis axis = sideDateAxis(daily, TimeScale.DAY, limit, reversed, height);
               moved += assertYearsDrawnInFull(axis, "MMM d, yyyy", "day " + where);
            }
         }

         for(int month : new int[] { Calendar.JANUARY, Calendar.FEBRUARY, Calendar.JULY }) {
            DefaultAxis axis = sideAxis(timeSeries(66, 0), dateRows(months(2024, month, 24)),
                                        height);
            assertTrue(axis.isVertical(), "premise: the time axis runs down the side");
            moved += assertYearsDrawnInFull(axis, "MMM yyyy",
                                            "time series from " + (month + 1) + " at " + height);
         }
      }

      assertTrue(moved > 0, "premise: a year moved off the date that starts it");
   }

   @Test
   void anAuthorRotatedDateAxisDrawsAMovedYearInFull() {
      Date[] dates = months(2024, Calendar.JULY, 24);
      // the Linux default font, in which the cut shows
      Font font = new Font("DejaVu Sans", Font.PLAIN, 10);
      int moved = 0;

      for(int rotation : new int[] { 45, 90 }) {
         for(int width = 100; width <= 400; width += 5) {
            CategoricalScale category = new CategoricalScale("Date");
            category.getAxisSpec().setCompactDates(new CompactDates(TimeScale.MONTH, Locale.US));
            category.getAxisSpec().getTextSpec().setRotation(rotation);

            for(Scale scale : new Scale[] { category, timeSeries(0, rotation) }) {
               scale.getAxisSpec().getTextSpec().setFont(font);
               DefaultAxis axis = layout(scale, dateRows(dates), width, 240);
               String where = scale.getClass().getSimpleName() + " at " + rotation +
                  " degrees, " + width + " wide";

               assertTrue(axis.isHorizontal(), "premise: the axis runs along the bottom");
               moved += assertYearsDrawnInFull(axis, "MMM yyyy", where);
            }
         }
      }

      assertTrue(moved > 0, "premise: a year moved off the date that starts it");
   }

   @Test
   void aFlatDateAxisReservesNoYearAlongTheAxis() {
      // a year from Jul 2024, and the texts its labels start with as names
      Date[] dates = months(2024, Calendar.JULY, 12);
      String[] texts = new String[dates.length];

      for(int i = 0; i < dates.length; i++) {
         String pattern = i == 0 || year(dates[i]) != year(dates[i - 1]) ? "MMM yyyy" : "MMM";
         texts[i] = new SimpleDateFormat(pattern, Locale.US).format(dates[i]);
      }

      for(double limit : new double[] { 0, 66 }) {
         CategoricalScale dateScale = new CategoricalScale("Date");
         dateScale.getAxisSpec().setCompactDates(new CompactDates(TimeScale.MONTH, Locale.US));
         dateScale.getAxisSpec().setLabelLimit(limit);
         CategoricalScale nameScale = new CategoricalScale("Name");
         nameScale.getAxisSpec().setLabelLimit(limit);
         DefaultAxis compact = layout(dateScale, dateRows(dates), 400, 240);
         DefaultAxis twin = layout(nameScale, names(texts), 400, 240);
         String where = "limit " + limit + " " + shownTexts(compact);

         assertTrue(compact.isHorizontal(), "premise: the axis runs along the bottom");
         assertEquals(twin.getAxisSize(), compact.getAxisSize(), 1e-9, "the axis " + where);
         assertEquals(twin.vgraph.getMinPlotWidth(), compact.vgraph.getMinPlotWidth(), 1e-9,
                      "the min plot width " + where);
      }
   }

   @Test
   void aClassicVerticalDateColumnFitsItsOwnTexts() {
      // a classic month axis without the year, and the same texts as names
      Date[] dates = months(2025, Calendar.JANUARY, 12);
      String[] texts = new String[dates.length];

      for(int i = 0; i < dates.length; i++) {
         texts[i] = new SimpleDateFormat("MMM", Locale.US).format(dates[i]);
      }

      for(double limit : new double[] { 0, 66 }) {
         for(boolean reversed : new boolean[] { true, false }) {
            CategoricalScale dateScale = new CategoricalScale("Date");
            dateScale.getAxisSpec().getTextSpec().setFormat(new SimpleDateFormat("MMM", Locale.US));
            dateScale.getAxisSpec().setLabelLimit(limit);
            dateScale.setReversed(reversed);
            CategoricalScale nameScale = new CategoricalScale("Name");
            nameScale.getAxisSpec().setLabelLimit(limit);
            nameScale.setReversed(reversed);
            DefaultAxis classic = sideAxis(dateScale, dateRows(dates), 300);
            DefaultAxis twin = sideAxis(nameScale, names(texts), 300);
            List<String> shown = topDownTexts(classic);
            String where = "limit " + limit + " reversed " + reversed + " " + shown;

            assertEquals(texts.length, shown.size(), "premise: every month shown " + where);
            assertEquals(twin.getAxisSize(), classic.getAxisSize(), 1e-9, "the column " + where);
         }
      }
   }

   @Test
   void aReversedLimitedAxisStepsFromItsFirstCategory() {
      boolean stepOfTwo = false;
      int top = -1;

      for(int height = 60; height <= 600; height += 5) {
         CategoricalScale xscale = new CategoricalScale("Name");
         xscale.getAxisSpec().setLabelLimit(66);
         DefaultAxis axis = reversedAxis(xscale, stores(30), height);
         List<Integer> idx = topDownIndices(axis);
         String where = "at " + height + " " + topDownTexts(axis);
         int step = idx.size() > 1 ? idx.get(0) - idx.get(1) : 1;
         // the top shown store, counted from the first store
         top = Integer.parseInt(text(axis.getLabels()[idx.get(0)]).substring(6)) - 1;

         // slid inside, the first store is hidden only when it crowds the next one in step
         assertTrue(top == 0 || top == step, "top " + top + " step " + step + " " + where);
         assertOneStep(axis, where);
         stepOfTwo = stepOfTwo || step == 2;
      }

      assertEquals(0, top, "the first store shows when its slot holds a line");
      assertTrue(stepOfTwo, "premise: a step of 2");
   }

   @Test
   void aReversedHourlyAxisCarriesTheDateFromTheTop() {
      List<String> expected = new ArrayList<>();

      for(int h = 12; h < 24; h++) {
         expected.add(String.format(h == 12 ? "2025-12-31 %02d" : "12-31 %02d", h));
      }

      for(int h = 0; h < 12; h++) {
         expected.add(String.format(h == 0 ? "2026-01-01 %02d" : "01-01 %02d", h));
      }

      DefaultAxis tall = reversedHourlyAxis(66, 800);
      List<String> tallTexts = new ArrayList<>();

      for(int i : topDownIndices(tall)) {
         tallTexts.add(tall.getLabels()[i].getText());
      }

      assertEquals(expected, tallTexts);
      boolean moved = false;

      for(int height = 70; height <= 400; height += 5) {
         DefaultAxis axis = reversedHourlyAxis(66, height);
         List<String> texts = topDownTexts(axis);
         String where = "at " + height + " " + texts;
         String prevDay = null;

         for(int i : topDownIndices(axis)) {
            // the text before the length limit cuts it
            String text = axis.getLabels()[i].getText();
            String day = String.format("%tF", (Date) ((VDimensionLabel) axis.getLabels()[i])
               .getValue());

            if(!day.equals(prevDay)) {
               assertTrue(text.startsWith(day + " "), "no date on " + text + " " + where);
               moved = moved || !text.endsWith(prevDay == null ? " 12" : " 00");
            }
            else {
               assertFalse(text.startsWith("20"), "date repeated on " + text + " " + where);
            }

            prevDay = day;
         }

         assertOneStep(axis, where);
      }

      assertTrue(moved, "premise: a date moved past a hidden first hour");
   }

   @Test
   void aReversedClassicAxisUnchanged() {
      DefaultAxis stores = reversedAxis(new CategoricalScale("Name"), stores(30), 240);
      assertEquals(CLASSIC_REVERSED_STORES_SHOWN, shownIndices(stores));

      DefaultAxis hours = reversedHourlyAxis(0, 240);
      assertEquals(CLASSIC_REVERSED_HOURS, shownTexts(hours));
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

   // the shown labels run along the axis in the order of their values
   private static void assertInReadingOrder(DefaultAxis axis, String where) {
      List<Integer> idx = shownIndices(axis);
      List<String> texts = shownTexts(axis);

      for(int i = 1; i < idx.size(); i++) {
         double prev = textArea(axis.getLabels()[idx.get(i - 1)], 0).getBounds2D().getCenterX();
         double next = textArea(axis.getLabels()[idx.get(i)], 0).getBounds2D().getCenterX();

         assertTrue(prev < next, texts.get(i - 1) + " at " + prev + " after " + texts.get(i) +
                    " at " + next + " at " + where + " " + texts);
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

   private static DefaultAxis layout(Scale xscale, Object[][] rows, int width, int height) {
      return layout(xscale, new LinearScale("Sales"), rows, width, height);
   }

   private static DefaultAxis layout(Scale xscale, LinearScale yscale, Object[][] rows,
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

   // a category axis down the side of a transposed chart, its scale reversed
   private static DefaultAxis reversedAxis(CategoricalScale xscale, Object[][] rows, int height) {
      xscale.setReversed(true);
      return sideAxis(xscale, rows, height);
   }

   // the X axis down the side of a transposed chart
   private static DefaultAxis sideAxis(Scale xscale, Object[][] rows, int height) {
      RectCoord coord = new RectCoord(xscale, new LinearScale("Sales"));
      coord.transpose();
      EGraph graph = new EGraph();
      graph.addElement(new IntervalElement(xscale.getFields()[0], "Sales"));
      graph.setCoordinate(coord);
      VGraph vgraph = Plotter.getPlotter(graph)
         .plotAndLayout(new DefaultDataSet(rows), 0, 0, 400, height);
      DefaultAxis axis = ((RectCoord) vgraph.getCoordinate()).getXAxis1();
      axis.prepareLabels();
      return axis;
   }

   // a monthly time axis in compact dates
   private static TimeScale timeSeries(double limit, int rotation) {
      TimeScale scale = new TimeScale("Date");
      scale.setType(TimeScale.MONTH);
      scale.getAxisSpec().setCompactDates(new CompactDates(CompactDates.FROM_SCALE, Locale.US));
      scale.getAxisSpec().setLabelLimit(limit);
      scale.getAxisSpec().getTextSpec().setRotation(rotation);
      return scale;
   }

   // compact dates down the side, from the top or from the bottom
   private static DefaultAxis sideDateAxis(Date[] dates, int unit, double limit, boolean reversed,
                                           int height)
   {
      CategoricalScale xscale = new CategoricalScale("Date");
      xscale.getAxisSpec().setCompactDates(new CompactDates(unit, Locale.US));
      xscale.getAxisSpec().setLabelLimit(limit);
      xscale.setReversed(reversed);
      return sideAxis(xscale, dateRows(dates), height);
   }

   // every shown label draws its own text, and the first shown label of each year draws the
   // year; returns how many of those labels took the year from an earlier date
   private static int assertYearsDrawnInFull(DefaultAxis axis, String pattern, String where) {
      SimpleDateFormat full = new SimpleDateFormat(pattern, Locale.US);
      VLabel[] labels = axis.getLabels();
      List<VDimensionLabel> byDate = new ArrayList<>();
      String at = where + " " + topDownTexts(axis);
      Integer prevYear = null;
      int moved = 0;

      for(VLabel label : labels) {
         byDate.add((VDimensionLabel) label);
      }

      byDate.sort(Comparator.comparing(label -> (Date) label.getValue()));

      for(int p = 0; p < byDate.size(); p++) {
         VDimensionLabel label = byDate.get(p);

         if(label.getZIndex() < 0) {
            continue;
         }

         Date date = (Date) label.getValue();
         int year = year(date);

         assertEquals(String.valueOf(label.getLabel()), text(label), "cut at " + at);

         if(prevYear == null || year != prevYear) {
            assertEquals(full.format(date), text(label), "the year at " + at);

            if(p > 0 && year((Date) byDate.get(p - 1).getValue()) == year) {
               moved++;
            }
         }

         prevYear = year;
      }

      return moved;
   }

   private static int year(Date date) {
      Calendar cal = Calendar.getInstance();
      cal.setTime(date);
      return cal.get(Calendar.YEAR);
   }

   private static DefaultAxis reversedDateAxis(Date[] dates, CompactDates compact, double limit,
                                               int height)
   {
      CategoricalScale xscale = new CategoricalScale("Date");
      xscale.getAxisSpec().setCompactDates(compact);
      xscale.getAxisSpec().setLabelLimit(limit);
      return reversedAxis(xscale, dateRows(dates), height);
   }

   // Dec 31, 2025 12:00 to Jan 1, 2026 11:00, the repeated year dropped
   private static DefaultAxis reversedHourlyAxis(double limit, int height) {
      Object[][] rows = new Object[25][];
      rows[0] = new Object[] { "Date", "Sales" };

      for(int i = 0; i < 24; i++) {
         Calendar cal = Calendar.getInstance();
         cal.clear();
         cal.set(2025, Calendar.DECEMBER, 31, 12 + i, 0, 0);
         rows[i + 1] = new Object[] { new java.sql.Timestamp(cal.getTimeInMillis()), i * 10.0 };
      }

      CategoricalScale xscale = new CategoricalScale("Date");
      xscale.getAxisSpec().getTextSpec().setFormat(new ExtendedDateFormat("yyyy-MM-dd HH"));
      xscale.getAxisSpec().setAbbreviate(true);
      xscale.getAxisSpec().setLabelLimit(limit);
      return reversedAxis(xscale, rows, height);
   }

   // the shown labels from the top of the axis down
   private static List<Integer> topDownIndices(DefaultAxis axis) {
      VLabel[] labels = axis.getLabels();
      List<Integer> idx = shownIndices(axis);
      idx.sort((a, b) -> Double.compare(labels[b].getBounds().getCenterY(),
                                        labels[a].getBounds().getCenterY()));
      return idx;
   }

   private static List<String> topDownTexts(DefaultAxis axis) {
      List<String> texts = new ArrayList<>();

      for(int i : topDownIndices(axis)) {
         texts.add(text(axis.getLabels()[i]));
      }

      return texts;
   }

   private static List<String> shownTexts(DefaultAxis axis) {
      List<String> texts = new ArrayList<>();

      for(int i : shownIndices(axis)) {
         texts.add(text(axis.getLabels()[i]));
      }

      return texts;
   }

   // the shown labels' texts before the length limit cuts them
   private static List<String> shownFullTexts(DefaultAxis axis) {
      List<String> texts = new ArrayList<>();

      for(int i : shownIndices(axis)) {
         texts.add(axis.getLabels()[i].getText());
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
