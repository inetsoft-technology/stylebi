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
 * Regression test for bug #76574 (VTB-010): a crosstab's grand-total-of-grand-totals corner
 * cell rendered as "450115.549999997" instead of "17450115.549999997" -- the leading "17"
 * silently disappeared.
 *
 * Root cause: the corner cell's underlying double is numerically correct (confirmed by
 * arithmetic tracing in CrossTabFilter/SumFormula), and this specific crosstab never gets a
 * user-set number format (the VS-TABLE-BIND-3 repro only calls set_table_fields/
 * set_table_options, never a format tool, and neither the wiz binding path
 * (TableBindingMutator) nor the native Composer binding handlers (VSCrosstabBindingHandler)
 * ever assign one to a newly bound aggregate), so its raw, unrounded double is rendered as-is.
 * Because the corner cell sums many more detail rows directly than either sibling grand total
 * (which each sum only a subset), it accumulates more floating-point noise and so its
 * unformatted string is longer than its siblings' -- long enough to overflow this crosstab's
 * column width, where the siblings' shorter strings do not.
 *
 * Common.processText() computed a negative line offset for a right-aligned string wider than
 * its cell (nbound.width - w - 1 < 0 when w > nbound.width), which paintText() then used as
 * the string's starting x -- to the left of the Graphics2D clip rect it sets to the cell's
 * bounds. The clip silently discarded whatever fell to its left, i.e. the string's leading
 * (most-significant) characters, while its trailing (least-significant/decimal) characters
 * stayed inside the clip and were shown -- turning a truncated string into a shorter, still
 * plausible-looking, but numerically wrong value.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
public class CommonRightAlignOverflowTest {
   /**
    * The exact numbers from the bug report: 10147193.75 (USA East) + 7302921.8 (USA West)
    * should render as 17450115.55; the corner cell's raw, unformatted, FP-noisy double prints
    * as this longer string.
    */
   private static final String CORNER_CELL_TEXT = "17450115.549999997";

   @Test
   public void rightAlignedOverflowingTextIsNotOffsetPastLeftEdge() {
      Font font = new Font(Font.SANS_SERIF, Font.PLAIN, 11);
      FontMetrics fm = Common.getFractionalFontMetrics(font);
      float fullWidth = Common.stringWidth(CORNER_CELL_TEXT, font, fm);

      // A column comfortably wide enough for the two sibling totals ("10147193.75",
      // "7302921.8") but narrower than the corner cell's full unformatted string --
      // the same shape as the bug report (every other cell renders fine; only the corner,
      // longest cell overflows).
      float narrowWidth = fullWidth - 40;
      assertTrue(narrowWidth > Common.stringWidth("10147193.75", font, fm),
                 "test bound should still fit the shorter sibling total, to match the repro");

      Bounds bound = new Bounds(0, 0, narrowWidth, 20);
      Bounds outbound = new Bounds();
      Vector<Float> lineoff = new Vector<>();

      Common.processText(CORNER_CELL_TEXT, bound, StyleConstants.H_RIGHT, false, font, outbound,
                          lineoff, 0, fm, 0);

      assertEquals(1, lineoff.size());
      float offset = lineoff.elementAt(0);

      // Before the fix: offset = nbound.width - fullWidth - 1, which is negative here (the
      // text is wider than the bound) -- paintText() would then draw the string starting at
      // nbound.x + offset, i.e. to the LEFT of the clip rect it sets to nbound, silently
      // dropping the string's leading characters (bug #76574's "missing leading 17").
      assertTrue(offset >= 0,
                 "a right-aligned string wider than its cell must not get a negative line " +
                 "offset -- that draws it starting left of the clip rect and silently drops " +
                 "its leading (most-significant) characters instead of its trailing ones");
   }

   @Test
   public void rightAlignedTextThatFitsIsUnaffected() {
      Font font = new Font(Font.SANS_SERIF, Font.PLAIN, 11);
      FontMetrics fm = Common.getFractionalFontMetrics(font);
      String text = "7302921.8";
      float w = Common.stringWidth(text, font, fm);

      // Plenty of room -- the normal, non-overflowing case must still right-align as before.
      Bounds bound = new Bounds(0, 0, w + 40, 20);
      Bounds outbound = new Bounds();
      Vector<Float> lineoff = new Vector<>();

      Common.processText(text, bound, StyleConstants.H_RIGHT, false, font, outbound, lineoff,
                          0, fm, 0);

      assertEquals(1, lineoff.size());
      float offset = lineoff.elementAt(0);
      float expected = bound.width - w - 1;

      assertEquals(expected, offset, 0.01f,
                   "non-overflowing right-aligned text should be unaffected by the overflow fix");
   }
}
