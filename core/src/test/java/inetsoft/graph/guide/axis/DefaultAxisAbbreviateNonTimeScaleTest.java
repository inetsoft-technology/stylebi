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

import inetsoft.graph.*;
import inetsoft.graph.coord.RectCoord;
import inetsoft.graph.data.DefaultDataSet;
import inetsoft.graph.element.PointElement;
import inetsoft.graph.guide.VLabel;
import inetsoft.graph.internal.ICoordinate;
import inetsoft.graph.scale.*;
import inetsoft.test.*;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.text.SimpleDateFormat;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #82027: abbreviating the labels of a non-time axis that has a date format starting
 * with "yy" threw a ClassCastException (TimeScale cast in DefaultAxis.abbreviateDate).
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = BaseTestConfiguration.class,
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class DefaultAxisAbbreviateNonTimeScaleTest {
   private static final Object[][] DATE_ROWS = {
      { "x", "y" },
      { date(2024, 0, 1), 1 },
      { date(2024, 0, 2), 2 },
      { date(2024, 1, 3), 3 },
      { date(2025, 0, 1), 4 }
   };

   @Test
   void categoricalDatesWithYearFirstPatternDropTheCommonPrefix() {
      List<String> labels = bottomLabels(categorical("yyyy-MM-dd", true), DATE_ROWS);

      assertEquals(List.of("2024-01-01", "01-02", "02-03", "2025-01-01"), labels);
   }

   @Test
   void categoricalDatesWithYearMonthNamePattern() {
      List<String> labels = bottomLabels(categorical("yyyy MMM", true), DATE_ROWS);

      // 2024-01-01 and 2024-01-02 format the same, so the second label is blank
      assertEquals(4, labels.size());
      assertEquals("2024 Jan", labels.get(0));
      assertTrue(labels.get(1) == null || labels.get(1).isBlank(), labels.toString());
      assertEquals("Feb", labels.get(2));
      assertEquals("2025 Jan", labels.get(3));
   }

   @Test
   void linearScaleWithDateFormatKeepsTheFullLabels() {
      Object[][] rows = {
         { "x", "y" },
         { (double) date(2024, 0, 1).getTime(), 1 },
         { (double) date(2025, 0, 1).getTime(), 2 }
      };

      List<String> abbreviated = bottomLabels(linear("yyyy-MM", true), rows);
      List<String> full = bottomLabels(linear("yyyy-MM", false), rows);

      assertFalse(abbreviated.isEmpty());
      assertEquals(full, abbreviated);
   }

   @Test
   void timeScaleMonthAbbreviationIsUnchanged() {
      TimeScale scale = new TimeScale("x");
      scale.setType(TimeScale.MONTH);
      scale.getAxisSpec().getTextSpec().setFormat(new SimpleDateFormat("yyyy MMM", Locale.US));
      scale.getAxisSpec().setAbbreviate(true);

      List<String> labels = bottomLabels(scale, DATE_ROWS);

      assertEquals("2024", labels.get(0));
      assertEquals("Feb", labels.get(1));
      assertEquals("Dec", labels.get(labels.size() - 2));
      assertEquals("2025", labels.get(labels.size() - 1));
   }

   private static List<String> bottomLabels(Scale xscale, Object[][] rows) {
      EGraph graph = new EGraph();
      graph.addElement(new PointElement("x", "y"));
      graph.setScale("x", xscale);

      VGraph vgraph = Plotter.getPlotter(graph)
         .plotAndLayout(new DefaultDataSet(rows), 0, 0, 1600, 400);
      DefaultAxis axis = ((RectCoord) vgraph.getCoordinate()).getAxisAt(ICoordinate.BOTTOM_AXIS);
      List<String> labels = new ArrayList<>();

      for(VLabel label : axis.getLabels()) {
         labels.add(label.getText());
      }

      return labels;
   }

   private static CategoricalScale categorical(String pattern, boolean abbreviate) {
      CategoricalScale scale = new CategoricalScale("x");
      scale.getAxisSpec().getTextSpec().setFormat(new SimpleDateFormat(pattern, Locale.US));
      scale.getAxisSpec().setAbbreviate(abbreviate);
      return scale;
   }

   private static LinearScale linear(String pattern, boolean abbreviate) {
      LinearScale scale = new LinearScale("x");
      scale.getAxisSpec().getTextSpec().setFormat(new SimpleDateFormat(pattern, Locale.US));
      scale.getAxisSpec().setAbbreviate(abbreviate);
      return scale;
   }

   private static Date date(int year, int month, int day) {
      return new GregorianCalendar(year, month, day).getTime();
   }
}
