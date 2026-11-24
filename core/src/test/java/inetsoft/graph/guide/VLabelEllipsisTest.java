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
package inetsoft.graph.guide;

import inetsoft.graph.internal.DimensionD;
import inetsoft.graph.internal.GTool;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.awt.*;
import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * A label with the ellipsis on ends a cut in "…"; without it a cut keeps today's dots.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = BaseTestConfiguration.class,
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class VLabelEllipsisTest {
   private static final String NAME = "Chief Financial Officer";

   @Test
   void aWrappedCutEndsInTheEllipsis() {
      VLabel label = twoLines(NAME, true);
      String[] lines = label.getDisplayLabel();
      String last = lines[lines.length - 1];

      assertEquals(2, lines.length, "premise: two lines " + Arrays.toString(lines));
      assertTrue(last.endsWith("…"), last);
      assertFalse(last.contains("."), last);
      assertTrue(label.getPreferredWidth(last) <= label.getSize().getWidth(),
                 last + " fits " + label.getSize().getWidth());
   }

   @Test
   void aClassicWrappedCutKeepsThreeDots() {
      String[] lines = twoLines(NAME, false).getDisplayLabel();

      assertEquals(2, lines.length, "premise: two lines " + Arrays.toString(lines));
      assertTrue(lines[1].endsWith("..."), lines[1]);
      assertFalse(lines[1].contains("…"), lines[1]);
   }

   @Test
   void aWrappedLabelThatFitsIsNotCut() {
      String[] lines = twoLines("Chief Financial", true).getDisplayLabel();

      assertEquals(Arrays.asList("Chief", "Financial"), Arrays.asList(lines));
   }

   @Test
   void theMinimumWidthIsMeasuredWithTheMark() {
      VLabel modern = new VLabel("Healthcare Provider Services");
      modern.setEllipsis(true);
      VLabel classic = new VLabel("Healthcare Provider Services");

      assertEquals(modern.getPreferredWidth("Heal…"), modern.getWidth(4), 1e-9);
      assertEquals(classic.getPreferredWidth("Heal.."), classic.getWidth(4), 1e-9);
   }

   // a word broken in two, its tail dropped, and the line cut next to the space joining them
   @Test
   void aWrapCutNextToASpaceEndsInTheEllipsis() {
      String text = "Account Executive";
      Font font = new VLabel(text).getFont();
      double from = GTool.stringWidth(new String[] { "Executiv" }, font);
      double to = GTool.stringWidth(new String[] { "Executive" }, font);
      List<String> unmarked = new ArrayList<>();

      for(double width = from; width < to; width += 0.25) {
         String classic = String.join(" ", twoLines(text, false, width).getDisplayLabel());
         String modern = String.join(" ", twoLines(text, true, width).getDisplayLabel());

         if(letters(classic) < letters(text) && !classic.endsWith("...")) {
            unmarked.add(classic);
         }

         if(letters(modern.replace("…", "")) < letters(text)) {
            assertTrue(modern.endsWith("…"), width + " " + modern);
         }
      }

      assertFalse(unmarked.isEmpty(), "premise: today the cut shows no mark");
   }

   // a one-line cut that lands just after a space
   @Test
   void aOneLineCutDropsTheSpaceBeforeTheEllipsis() {
      Font font = new VLabel(NAME).getFont();
      double from = GTool.stringWidth(new String[] { "Chief" }, font);
      double to = GTool.stringWidth(new String[] { "Chief Financial" }, font);
      boolean classicSpace = false;

      for(double width = from; width < to; width += 0.5) {
         String modern = oneLine(NAME, true, width).getDisplayLabel()[0];
         String classic = oneLine(NAME, false, width).getDisplayLabel()[0];

         assertFalse(modern.endsWith(" …"), width + " " + modern);
         classicSpace = classicSpace || classic.endsWith(" ..");
      }

      assertTrue(classicSpace, "premise: classic keeps the space before its dots");
   }

   private static VLabel oneLine(String text, boolean ellipsis, double width) {
      VLabel label = new VLabel(text);
      FontMetrics fm = GTool.getFontMetrics(label.getFont());
      label.setEllipsis(ellipsis);
      label.setPosition(new Point2D.Double(0, 0));
      label.setSize(new DimensionD(width, fm.getHeight() + 2));
      return label;
   }

   private static long letters(String text) {
      return text.chars().filter(c -> !Character.isWhitespace(c)).count();
   }

   // a wrapping label as wide as its longest word and two lines tall
   private static VLabel twoLines(String text, boolean ellipsis) {
      Font font = new VLabel(text).getFont();
      double width = 0;

      for(String word : text.split(" ")) {
         width = Math.max(width, GTool.stringWidth(new String[] { word }, font));
      }

      return twoLines(text, ellipsis, width + 2);
   }

   private static VLabel twoLines(String text, boolean ellipsis, double width) {
      VLabel label = new VLabel(text);
      FontMetrics fm = GTool.getFontMetrics(label.getFont());
      label.setWrapping(true);
      label.setEllipsis(ellipsis);
      label.setPosition(new Point2D.Double(0, 0));
      label.setSize(new DimensionD(width, fm.getHeight() * 2 + 2));
      return label;
   }
}
