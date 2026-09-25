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
 * Regression test for bug #76948: a currency-aligned string wider than its cell got a negative
 * line offset from Common.processCurrencyText(), the same mechanism CommonRightAlignOverflowTest
 * and CommonCenterAlignOverflowTest document for bugs #76574 (VTB-010) and #76880.
 *
 * Root cause: alignCell() leaves nbound.width == bound.width for H_CURRENCY, and the line
 * offset is nbound.width - w. When the measured width w exceeds the cell width, this goes
 * negative. paintText() uses this offset verbatim as the string's starting x, which lands to
 * the left of the Graphics2D clip rect it sets to nbound -- silently discarding the leading,
 * most-significant digits.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
public class CommonCurrencyAlignOverflowTest {
   // same repro number as VTB-010 / CommonRightAlignOverflowTest
   private static final String OVERFLOW_TEXT = "17450115.549999997";

   @Test
   public void currencyAlignedOverflowingTextIsNotOffsetPastLeftEdge() {
      assertNonNegativeOffset(StyleConstants.H_CURRENCY, 0);
   }

   @Test
   public void currencyAlignedOverflowingTextWithRightPaddingIsNotOffsetPastLeftEdge() {
      Font font = new Font(Font.SANS_SERIF, Font.PLAIN, 11);
      FontMetrics fm = Common.getFractionalFontMetrics(font);
      float rw = Common.stringWidth(OVERFLOW_TEXT.substring(OVERFLOW_TEXT.lastIndexOf('.')),
                                    font, fm);

      // right > rw pads the measured width further, pushing the offset even more negative
      assertNonNegativeOffset(StyleConstants.H_CURRENCY, rw + 10);
   }

   @Test
   public void currencyAlignedOverflowingTextWithVerticalCenterIsNotOffsetPastLeftEdge() {
      assertNonNegativeOffset(StyleConstants.H_CURRENCY | StyleConstants.V_CENTER, 0);
   }

   @Test
   public void currencyAlignedTextThatFitsIsUnaffected() {
      Font font = new Font(Font.SANS_SERIF, Font.PLAIN, 11);
      FontMetrics fm = Common.getFractionalFontMetrics(font);
      String text = "7302921.8";
      float w = Common.stringWidth(text, font, fm);

      // Plenty of room -- the normal, non-overflowing case must still right-align as before.
      Bounds bound = new Bounds(0, 0, w + 40, 20);
      Bounds outbound = new Bounds();
      Vector<Float> lineoff = new Vector<>();

      Common.processText(text, bound, StyleConstants.H_CURRENCY, false, font, outbound, lineoff,
                          0, fm, 0);

      assertEquals(1, lineoff.size());
      float offset = lineoff.elementAt(0);

      assertEquals(bound.width - w, offset, 0.01f,
                   "non-overflowing currency-aligned text should be unaffected by the overflow fix");
   }

   private static void assertNonNegativeOffset(int align, float right) {
      Font font = new Font(Font.SANS_SERIF, Font.PLAIN, 11);
      FontMetrics fm = Common.getFractionalFontMetrics(font);
      float fullWidth = Common.stringWidth(OVERFLOW_TEXT, font, fm);

      // Narrower than the full string's width, forcing an overflow.
      Bounds bound = new Bounds(0, 0, fullWidth - 40, 20);
      Bounds outbound = new Bounds();
      Vector<Float> lineoff = new Vector<>();

      Common.processText(OVERFLOW_TEXT, bound, align, false, font, outbound, lineoff, 0, fm,
                          right);

      assertEquals(1, lineoff.size());
      float offset = lineoff.elementAt(0);

      // Before the fix: offset = nbound.width - w, negative here -- paintText() would draw the
      // string starting left of the clip rect, silently dropping its leading digits.
      assertTrue(offset >= 0,
                 "a currency-aligned string wider than its cell must not get a negative line " +
                 "offset -- that draws it starting left of the clip rect and silently drops " +
                 "its leading (most-significant) characters");
   }
}
