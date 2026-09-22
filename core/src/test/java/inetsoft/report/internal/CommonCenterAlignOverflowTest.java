/*
 * This file is part of StyleBI.
 * Copyright (C) 2025  InetSoft Technology
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
package inetsoft.report.internal;

import inetsoft.report.StyleConstants;
import inetsoft.test.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.awt.*;
import java.util.Vector;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Regression test for bug #76880: a center-aligned string wider than its cell got a negative
 * line offset from Common.processText(), the same mechanism CommonRightAlignOverflowTest
 * documents for bug #76574 (VTB-010)'s H_RIGHT branch.
 *
 * Root cause: for the H_CENTER branch, the line offset is (nbound.width - w) / 2. When the
 * measured line width w exceeds nbound.width (the text overflows its cell), this goes
 * negative. paintText() uses this offset verbatim as the string's starting x, which lands to
 * the left of the Graphics2D clip rect it sets to nbound -- silently discarding whatever falls
 * outside that clip, i.e. characters from both ends of the string, including the leading,
 * most-significant ones for numeric text.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
public class CommonCenterAlignOverflowTest {
   // same repro number as VTB-010 / CommonRightAlignOverflowTest
   private static final String OVERFLOW_TEXT = "17450115.549999997";

   @Test
   public void centerAlignedOverflowingTextIsNotOffsetPastLeftEdge() {
      Font font = new Font(Font.SANS_SERIF, Font.PLAIN, 11);
      FontMetrics fm = Common.getFractionalFontMetrics(font);
      float fullWidth = Common.stringWidth(OVERFLOW_TEXT, font, fm);

      // Narrower than the full string's width, forcing an overflow, mirroring
      // CommonRightAlignOverflowTest's setup.
      float narrowWidth = fullWidth - 40;

      Bounds bound = new Bounds(0, 0, narrowWidth, 20);
      Bounds outbound = new Bounds();
      Vector<Float> lineoff = new Vector<>();

      Common.processText(OVERFLOW_TEXT, bound, StyleConstants.H_CENTER, false, font, outbound,
                          lineoff, 0, fm, 0);

      assertEquals(1, lineoff.size());
      float offset = lineoff.elementAt(0);

      // Before the fix: offset = (nbound.width - fullWidth) / 2, which is negative here (the
      // text is wider than the bound) -- paintText() would then draw the string starting at
      // nbound.x + offset, i.e. to the LEFT of the clip rect it sets to nbound, silently
      // dropping characters from both ends of the string, including its leading
      // (most-significant) ones.
      assertTrue(offset >= 0,
                 "a center-aligned string wider than its cell must not get a negative line " +
                 "offset -- that draws it starting left of the clip rect and silently drops " +
                 "its leading (most-significant) characters");
   }

   @Test
   public void centerAlignedTextThatFitsIsUnaffected() {
      Font font = new Font(Font.SANS_SERIF, Font.PLAIN, 11);
      FontMetrics fm = Common.getFractionalFontMetrics(font);
      String text = "7302921.8";
      float w = Common.stringWidth(text, font, fm);

      // Plenty of room -- the normal, non-overflowing case must still center as before.
      Bounds bound = new Bounds(0, 0, w + 40, 20);
      Bounds outbound = new Bounds();
      Vector<Float> lineoff = new Vector<>();

      Common.processText(text, bound, StyleConstants.H_CENTER, false, font, outbound, lineoff,
                          0, fm, 0);

      assertEquals(1, lineoff.size());
      float offset = lineoff.elementAt(0);
      float expected = (bound.width - w) / 2;

      assertEquals(expected, offset, 0.01f,
                   "non-overflowing center-aligned text should be unaffected by the overflow fix");
   }
}
