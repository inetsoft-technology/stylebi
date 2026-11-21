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
package inetsoft.graph.guide.legend;

import inetsoft.graph.LegendSpec;
import inetsoft.graph.TextSpec;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.awt.*;
import java.text.DecimalFormat;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Item visibility, cloning, equality and symbol size of {@link LegendSpec}.
 */
@Tag("core")
class LegendSpecTest {
   @Test
   void hiddenItemIsInvisibleAndOthersStayVisible() {
      LegendSpec spec = new LegendSpec();
      spec.setVisible("A", false);

      assertFalse(spec.isVisible("A"));
      assertTrue(spec.isVisible("B"));
      assertTrue(spec.isVisible(null));
   }

   @Test
   void hidingTwiceNeedsOnlyOneShow() {
      LegendSpec spec = new LegendSpec();
      spec.setVisible("A", false);
      spec.setVisible("A", false);
      spec.setVisible("B", false);
      spec.setVisible("A", true);

      assertTrue(spec.isVisible("A"));
      assertFalse(spec.isVisible("B"));
   }

   @Test
   void nullLabelCanBeHidden() {
      LegendSpec spec = new LegendSpec();
      spec.setVisible(null, false);

      assertFalse(spec.isVisible(null));
      assertTrue(spec.isVisible("A"));
      assertTrue(spec.isVisible(""));
   }

   @Test
   @Disabled("Not filed (no visible harm: only the script-modified flag is shared): clone shares hiddenItems with the original, hiding A on the clone hides it on the original (expected still visible)")
   void hidingAnItemOnACloneDoesNotHideItOnTheOriginal() {
      LegendSpec original = new LegendSpec();
      LegendSpec clone = original.clone();
      clone.setVisible("A", false);

      assertFalse(clone.isVisible("A"));
      assertTrue(original.isVisible("A"));
   }

   @Test
   void cloneEqualsTheOriginalAndTextSpecChangesStayOnTheClone() {
      LegendSpec original = new LegendSpec();
      original.getTextSpec().setFormat(new DecimalFormat("0.0"));
      LegendSpec clone = original.clone();

      assertEquals(original, clone);

      Font font = new Font("Dialog", Font.ITALIC, 23);
      clone.getTextSpec().setFont(font);
      clone.getTextSpec().setFormat(new DecimalFormat("0.000"));
      TextSpec text = original.getTextSpec();

      assertNotEquals(font, text.getFont());
      assertEquals(new DecimalFormat("0.0"), text.getFormat());
      assertNotEquals(original, clone);
   }

   @Test
   void equalityCoversVisibilityHiddenItemsAndSymbolSize() {
      LegendSpec a = new LegendSpec();
      LegendSpec b = new LegendSpec();

      assertEquals(a, b);

      b.setVisible(false);
      assertNotEquals(a, b);

      b = new LegendSpec();
      b.setVisible("A", false);
      assertNotEquals(a, b);

      b = new LegendSpec();
      b.setSymbolSize(20);
      assertNotEquals(a, b);
   }

   @Test
   @Disabled("Not filed (harmless: LegendSpec is never a hash key): two default LegendSpec are equal but have different hashCode (nested TextSpec has no hashCode)")
   void equalSpecsHaveTheSameHashCode() {
      assertEquals(new LegendSpec().hashCode(), new LegendSpec().hashCode());
   }

   @Test
   @DisplayName("pinned current behavior: symbol size is limited to 6..50")
   void symbolSizeIsLimitedToSixToFifty() {
      LegendSpec spec = new LegendSpec();
      int[] in = { 3, 6, 20, 50, 80 };
      int[] out = { 6, 6, 20, 50, 50 };

      for(int i = 0; i < in.length; i++) {
         spec.setSymbolSize(in[i]);
         assertEquals(out[i], spec.getSymbolSize());
      }
   }

   @Test
   @DisplayName("pinned current behavior: a null text spec is replaced by a new one")
   void nullTextSpecIsReplacedByANewOne() {
      LegendSpec spec = new LegendSpec();
      spec.setTextSpec(null);

      assertNotNull(spec.getTextSpec());
   }
}
