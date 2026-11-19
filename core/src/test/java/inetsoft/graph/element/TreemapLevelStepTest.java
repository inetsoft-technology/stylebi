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
package inetsoft.graph.element;

import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.awt.Color;

import static org.junit.jupiter.api.Assertions.*;

/**
 * A sunburst or icicle node below its base level is mixed toward the card colour.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = BaseTestConfiguration.class,
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class TreemapLevelStepTest {
   @Test
   void noBaseNoStep() {
      TreemapElement elem = new TreemapElement("Division", "Department");

      assertEquals(-1, elem.getLevelStepBase());
      assertNull(elem.getLevelStepCard());
      assertSame(BLUE, elem.getLevelColor(BLUE, 1));

      elem.setLevelStep(-1, LIGHT_CARD);
      assertSame(BLUE, elem.getLevelColor(BLUE, 3));

      elem.setLevelStep(0, null);
      assertSame(BLUE, elem.getLevelColor(BLUE, 3));
      assertNull(elem.getLevelColor(null, 3));
   }

   @Test
   void theBaseLevelKeepsItsColour() {
      TreemapElement elem = step(0, LIGHT_CARD);
      assertSame(BLUE, elem.getLevelColor(BLUE, 0));

      elem = step(1, LIGHT_CARD);
      assertSame(BLUE, elem.getLevelColor(BLUE, 0));
      assertSame(BLUE, elem.getLevelColor(BLUE, 1));
   }

   @Test
   void oneLevelDownMixes38Percent() {
      assertColor(new Color(0x77B6FA), step(0, LIGHT_CARD).getLevelColor(BLUE, 1));
      assertColor(new Color(0x77B6FA), step(1, LIGHT_CARD).getLevelColor(BLUE, 2));
   }

   @Test
   void threeLevelsDownMixes76Percent() {
      assertColor(new Color(0xC0D7F1), step(0, LIGHT_CARD).getLevelColor(BLUE, 3));
   }

   @Test
   void deeperLevelsStayAtTheThirdStep() {
      TreemapElement elem = step(0, LIGHT_CARD);
      assertEquals(elem.getLevelColor(BLUE, 3), elem.getLevelColor(BLUE, 5));
   }

   @Test
   void aDarkCardStepsDeeper() {
      assertColor(new Color(0x4877AC),
                  step(0, DARK_CARD).getLevelColor(new Color(0x4FA5FF), 1));
   }

   @Test
   void brightSlotsNeverTurnWhite() {
      assertColor(new Color(0xF2DEC3),
                  step(0, LIGHT_CARD).getLevelColor(new Color(0xFFB020), 3));
   }

   @Test
   void alphaIsKept() {
      Color translucent = new Color(0x04, 0x90, 0xFF, 128);
      Color stepped = step(0, LIGHT_CARD).getLevelColor(translucent, 1);

      assertEquals(128, stepped.getAlpha());
      assertColor(new Color(0x77B6FA), stepped);
   }

   @Test
   void equalsContentAndCloneCoverTheStep() {
      TreemapElement plain = new TreemapElement("Division", "Department");
      TreemapElement stepped = step(0, LIGHT_CARD);
      TreemapElement otherBase = step(1, LIGHT_CARD);
      TreemapElement otherCard = step(0, DARK_CARD);

      assertTrue(stepped.equalsContent(step(0, LIGHT_CARD)));
      assertFalse(stepped.equalsContent(plain));
      assertFalse(stepped.equalsContent(otherBase));
      assertFalse(stepped.equalsContent(otherCard));

      TreemapElement clone = (TreemapElement) stepped.clone();
      assertEquals(0, clone.getLevelStepBase());
      assertEquals(LIGHT_CARD, clone.getLevelStepCard());
      assertTrue(stepped.equalsContent(clone));
   }

   private static TreemapElement step(int base, Color card) {
      TreemapElement elem = new TreemapElement("Division", "Department");
      elem.setMapType(TreemapElement.Type.SUNBURST);
      elem.setLevelStep(base, card);
      return elem;
   }

   // each channel within 1 for rounding
   private static void assertColor(Color expected, Color actual) {
      String msg = String.format("expected #%06X, got #%06X", expected.getRGB() & 0xFFFFFF,
                                 actual.getRGB() & 0xFFFFFF);
      assertEquals(expected.getRed(), actual.getRed(), 1, msg);
      assertEquals(expected.getGreen(), actual.getGreen(), 1, msg);
      assertEquals(expected.getBlue(), actual.getBlue(), 1, msg);
   }

   private static final Color BLUE = new Color(0x0490FF);
   private static final Color LIGHT_CARD = new Color(0xECEBE9);
   private static final Color DARK_CARD = new Color(0x313034);
}
