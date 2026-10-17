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
package inetsoft.graph.aesthetic;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #78752: a pinned colour is keyed by the value's default-formatted text ("2024"); giving the
 * legend text a display format ('FY'yyyy) must not orphan the pin.
 */
@Tag("core")
class CategoricalColorFrameKeyFormatTest {
   private static Date year(int y) {
      Calendar cal = Calendar.getInstance();
      cal.clear();
      cal.set(y, Calendar.JANUARY, 1);
      return cal.getTime();
   }

   private CategoricalColorFrame pinned() {
      CategoricalColorFrame frame = new CategoricalColorFrame();
      frame.setColor("2024", Color.RED);
      frame.getLegendSpec().getTextSpec().setFormat(new SimpleDateFormat("yyyy"));
      frame.setKeyFormat(new SimpleDateFormat("yyyy"));
      return frame;
   }

   @Test
   void aLegendDisplayFormatDoesNotOrphanAPinnedColour() {
      CategoricalColorFrame frame = pinned();
      frame.getLegendSpec().getTextSpec().setFormat(new SimpleDateFormat("'FY'yyyy"));

      assertTrue(frame.isStatic(year(2024)));
      assertEquals(Color.RED, frame.getColor(year(2024)));
   }

   @Test
   void withoutAKeyFormatTheLegendFormatStillKeysTheLookup() {
      CategoricalColorFrame frame = new CategoricalColorFrame();
      frame.setColor("FY2024", Color.RED);
      frame.getLegendSpec().getTextSpec().setFormat(new SimpleDateFormat("'FY'yyyy"));

      assertEquals(Color.RED, frame.getColor(year(2024)));
   }
}
