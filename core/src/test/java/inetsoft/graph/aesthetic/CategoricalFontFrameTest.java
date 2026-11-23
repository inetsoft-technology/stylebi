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

import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.awt.Font;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.*;

/**
 * CategoricalFontFrame: only fonts assigned to values (no data driven mapping).
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(
   classes = { BaseTestConfiguration.class },
   initializers = ConfigurationContextInitializer.class
)
@SreeHome
@Tag("core")
class CategoricalFontFrameTest {
   private static final Font BOLD = new Font("Dialog", Font.BOLD, 14);
   private static final Font ITALIC = new Font("Dialog", Font.ITALIC, 10);

   @Test
   void unassignedValueHasNoFont() {
      CategoricalFontFrame frame = new CategoricalFontFrame();

      assertNull(frame.getFont("A"));

      frame.setFont("B", BOLD);

      assertNull(frame.getFont("A"));
   }

   @Test
   void assignedFontIsReturnedForItsValueOnly() {
      CategoricalFontFrame frame = new CategoricalFontFrame();
      frame.setFont("A", BOLD);

      assertSame(BOLD, frame.getFont("A"));
      assertNull(frame.getFont("B"));
      assertTrue(frame.isStatic("A"));
      assertFalse(frame.isStatic("B"));
   }

   @Test
   void numberKeysMatchByTheirTextForm() {
      CategoricalFontFrame frame = new CategoricalFontFrame();
      frame.setFont(1, BOLD);

      assertSame(BOLD, frame.getFont(1));
      assertSame(BOLD, frame.getFont("1"));
      assertSame(BOLD, frame.getFont(1L));
   }

   @Test
   void valueIsFoundByItsFormattedLegendText() {
      CategoricalFontFrame frame = new CategoricalFontFrame();
      frame.getLegendSpec().getTextSpec().setFormat(
         new DecimalFormat("0.00", DecimalFormatSymbols.getInstance(Locale.US)));
      frame.setFont("1.00", BOLD);

      assertSame(BOLD, frame.getFont(1));
      assertTrue(frame.isStatic(1));
   }

   @Test
   void removingAndClearingAssignments() {
      CategoricalFontFrame frame = new CategoricalFontFrame();
      frame.setFont("A", BOLD);
      frame.setFont("B", ITALIC);
      frame.setFont("A", null);

      assertFalse(frame.isStatic("A"));
      assertTrue(frame.isStatic("B"));

      frame.clearStatic();

      assertFalse(frame.isStatic("B"));
      assertFalse(frame.isScripted("B"));
      assertTrue(frame.getStaticValues().isEmpty());
   }

   @Test
   void cloneIsIndependentAndEqualityFollowsTheAssignments() {
      CategoricalFontFrame frame = new CategoricalFontFrame();
      frame.setFont("A", BOLD);
      CategoricalFontFrame copy = (CategoricalFontFrame) frame.clone();

      assertEquals(frame, copy);

      copy.setFont("A", ITALIC);
      copy.setFont("B", BOLD);

      assertSame(BOLD, frame.getFont("A"));
      assertNull(frame.getFont("B"));
      assertNotEquals(frame, copy);

      CategoricalFontFrame otherKey = new CategoricalFontFrame();
      otherKey.setFont("Z", BOLD);

      assertNotEquals(frame, otherKey);
   }

   @Test
   void framesWithDifferentAssignedFontsAreNotEqual() {
      CategoricalFontFrame a = new CategoricalFontFrame();
      CategoricalFontFrame b = new CategoricalFontFrame();
      a.setFont("A", BOLD);
      b.setFont("A", ITALIC);

      assertNotEquals(a, b);
   }

}
