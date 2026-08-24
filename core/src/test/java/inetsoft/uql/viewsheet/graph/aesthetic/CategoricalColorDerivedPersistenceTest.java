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
package inetsoft.uql.viewsheet.graph.aesthetic;

import inetsoft.graph.aesthetic.CategoricalColorFrame;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.io.PrintWriter;
import java.io.StringWriter;

import static org.junit.jupiter.api.Assertions.*;

/**
 * A render assigns a color per value from the palette by index. Those assignments must not reach
 * the saved asset: once written they outrank the palette, so the old colors keep rendering after
 * the palette changes and no amount of re-seeding the palette shows through. An assignment a person
 * made must still be saved.
 */
@Tag("core")
class CategoricalColorDerivedPersistenceTest {
   @Test
   void aDerivedColorIsNotSaved() {
      CategoricalColorFrame frame = new CategoricalColorFrame();
      frame.setDerivedColor("Business", new Color(0x00D4E8));

      assertEquals(new Color(0x00D4E8), frame.getColor("Business"),
                   "precondition: it resolves like any assignment at runtime");
      assertTrue(frame.isDerived("Business"));
      assertFalse(persist(frame).contains("Business"),
                  "a render-derived color must not reach the saved asset");
   }

   @Test
   void anAssignedColorIsStillSaved() {
      CategoricalColorFrame frame = new CategoricalColorFrame();
      frame.setColor("Business", Color.RED);

      assertFalse(frame.isDerived("Business"));
      assertTrue(persist(frame).contains("Business"),
                 "a color someone chose is still saved");
   }

   @Test
   void assigningOverADerivedColorMakesItSaved() {
      CategoricalColorFrame frame = new CategoricalColorFrame();
      frame.setDerivedColor("Business", new Color(0x00D4E8));
      frame.setColor("Business", Color.RED);

      assertFalse(frame.isDerived("Business"), "an explicit pick supersedes the derived one");
      assertTrue(persist(frame).contains("Business"));
   }

   @Test
   void clearingADerivedColorDropsTheMark() {
      CategoricalColorFrame frame = new CategoricalColorFrame();
      frame.setDerivedColor("Business", new Color(0x00D4E8));
      frame.setColor("Business", null);

      assertFalse(frame.isDerived("Business"));
      assertNull(frame.getColor("Business"), "and the value is unassigned again");
   }

   // the mark has to survive cloning: the render stores a clone as the sheet's shared frame and
   // clones it back on the next render, so a lost mark would make the color saveable again
   @Test
   void theDerivedMarkSurvivesAClone() {
      CategoricalColorFrame frame = new CategoricalColorFrame();
      frame.setDerivedColor("Business", new Color(0x00D4E8));

      CategoricalColorFrame clone = (CategoricalColorFrame) frame.clone();

      assertTrue(clone.isDerived("Business"), "a clone keeps the mark");
      assertFalse(persist(clone).contains("Business"));
   }

   @Test
   void aMixedFrameSavesOnlyWhatWasAssigned() {
      CategoricalColorFrame frame = new CategoricalColorFrame();
      frame.setDerivedColor("Business", new Color(0x00D4E8));
      frame.setColor("Office", Color.RED);
      frame.setDerivedColor("Games", new Color(0xF59E0B));

      String xml = persist(frame);

      assertTrue(xml.contains("Office"), "the assigned value is saved");
      assertFalse(xml.contains("Business"), "the derived ones are not");
      assertFalse(xml.contains("Games"));
   }

   // ---- clearing on a palette change --------------------------------------------------------

   /**
    * A per-value color takes precedence over the palette, so a derived one left in place keeps
    * rendering the old color even after the palette is replaced. This is what a live composer
    * session shows after Revert when only the palette is re-seeded.
    */
   @Test
   void aDerivedColorShadowsANewPaletteUntilCleared() {
      CategoricalColorFrame frame = new CategoricalColorFrame();
      frame.setDerivedColor("Business", new Color(0x00D4E8));
      frame.setDefaultColors(CategoricalColorFrame.COLOR_PALETTE);

      assertEquals(new Color(0x00D4E8), frame.getColor("Business"),
                   "precondition: re-seeding the palette alone does not dislodge it");

      frame.clearDerivedColors();

      assertNotEquals(new Color(0x00D4E8), frame.getColor("Business"),
                      "clearing the derived color lets the palette resolve again");
      assertFalse(frame.isDerived("Business"));
   }

   @Test
   void clearingDerivedColorsKeepsWhatSomebodyAssigned() {
      CategoricalColorFrame frame = new CategoricalColorFrame();
      frame.setDerivedColor("Business", new Color(0x00D4E8));
      frame.setColor("Office", Color.RED);

      frame.clearDerivedColors();

      assertEquals(Color.RED, frame.getColor("Office"), "an assigned color is not collateral");
      assertNotEquals(new Color(0x00D4E8), frame.getColor("Business"));
   }

   @Test
   void clearingDerivedColorsOnAFrameWithNoneIsANoOp() {
      CategoricalColorFrame frame = new CategoricalColorFrame();
      frame.setColor("Office", Color.RED);

      frame.clearDerivedColors();

      assertEquals(Color.RED, frame.getColor("Office"));
   }

   /** The dimensionColors element the wrapper writes for this frame. */
   private String persist(CategoricalColorFrame frame) {
      CategoricalColorFrameWrapper wrapper = new CategoricalColorFrameWrapper();
      wrapper.setVisualFrame(frame);
      StringWriter buf = new StringWriter();

      try(PrintWriter writer = new PrintWriter(buf)) {
         wrapper.writeXML(writer);
      }

      String xml = buf.toString();
      int start = xml.indexOf("<dimensionColors>");
      int end = xml.indexOf("</dimensionColors>");
      assertTrue(start >= 0 && end > start, "the wrapper writes a dimensionColors element");
      return xml.substring(start, end);
   }
}
