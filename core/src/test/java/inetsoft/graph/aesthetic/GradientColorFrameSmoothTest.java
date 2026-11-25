/*
 * This file is part of StyleBI.
 * Copyright (C) 2024  InetSoft Technology
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

import inetsoft.graph.internal.OKLab;
import inetsoft.uql.viewsheet.graph.aesthetic.GradientColorFrameWrapper;
import inetsoft.uql.viewsheet.graph.aesthetic.VisualFrameWrapper;
import inetsoft.util.Tool;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;

import java.awt.Color;
import java.io.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The Custom ramp blends in sRGB unless it is set smooth, and a smooth frame blends in OKLab.
 */
@Tag("core")
class GradientColorFrameSmoothTest {
   private static final Color PINK = new Color(0xFF99CC);
   private static final Color GREEN = new Color(0x008000);
   private static final Color BLUE = new Color(0x2B6CB0);
   private static final Color ORANGE = new Color(0xF08A24);

   private static GradientColorFrame frame(Color from, Color to, boolean smooth) {
      GradientColorFrame frame = new GradientColorFrame();
      frame.setDefaultFromColor(from);
      frame.setDefaultToColor(to);
      frame.setSmooth(smooth);
      return frame;
   }

   @Test
   void aFrameIsClassicUnlessSetSmooth() {
      assertFalse(new GradientColorFrame().isSmooth());
   }

   @Test
   void theClassicBlendIsTheChannelwiseSrgbBlend() {
      Color mid = frame(PINK, GREEN, false).getColor(0.5);

      // (255 + 0) / 2, (153 + 128) / 2 and (204 + 0) / 2, each truncated as the frame always has
      assertEquals(127, mid.getRed());
      assertEquals(140, mid.getGreen());
      assertEquals(102, mid.getBlue());
   }

   @Test
   void aSmoothFrameReturnsTheEndColorsAsGiven() {
      GradientColorFrame frame = frame(PINK, GREEN, true);

      assertEquals(PINK.getRGB(), frame.getColor(0).getRGB());
      assertEquals(GREEN.getRGB(), frame.getColor(1).getRGB());
   }

   @Test
   void aSmoothFrameChangesLightnessEvenlyAlongTheRamp() {
      assertTrue(lightnessDeviation(frame(PINK, GREEN, true)) < 0.01);
      assertTrue(lightnessDeviation(frame(BLUE, ORANGE, true)) < 0.01);
   }

   @Test
   void theClassicBlendDriftsFromEvenLightnessOnTheDefaultPair() {
      assertTrue(lightnessDeviation(frame(PINK, GREEN, false)) > 0.03);
   }

   @Test
   void aSmoothBlendIsADifferentRampFromTheClassicBlend() {
      assertNotEquals(frame(PINK, GREEN, false).getColor(0.5).getRGB(),
                      frame(PINK, GREEN, true).getColor(0.5).getRGB());
      assertNotEquals(frame(PINK, GREEN, false), frame(PINK, GREEN, true));
   }

   @Test
   void smoothnessSurvivesTheWrapperRoundTrip() throws Exception {
      assertTrue(roundTrip(frame(PINK, GREEN, true)).isSmooth());
   }

   /**
    * Every chart saved before the option existed has no attribute, and must stay on the sRGB blend.
    */
   @Test
   void aSavedFrameWithoutTheAttributeStaysClassic() throws Exception {
      GradientColorFrame parsed = roundTrip(frame(PINK, GREEN, false));

      assertFalse(parsed.isSmooth());
      assertEquals(frame(PINK, GREEN, false).getColor(0.5).getRGB(), parsed.getColor(0.5).getRGB());
   }

   @Test
   void theWrapperReportsAndMarksAChangeInSmoothness() throws Exception {
      GradientColorFrameWrapper wrapper =
         (GradientColorFrameWrapper) VisualFrameWrapper.wrap(frame(PINK, GREEN, false));
      wrapper.setChanged(false);

      wrapper.setSmooth(true);

      assertTrue(wrapper.isSmooth());
      assertTrue(wrapper.isChanged());
   }

   private static GradientColorFrame roundTrip(GradientColorFrame frame) throws Exception {
      VisualFrameWrapper wrapper = VisualFrameWrapper.wrap(frame);
      assertSame(GradientColorFrameWrapper.class, wrapper.getClass());

      StringWriter buf = new StringWriter();
      PrintWriter writer = new PrintWriter(buf);
      wrapper.writeXML(writer);
      writer.flush();

      Document doc = Tool.parseXML(new StringReader(buf.toString()));
      VisualFrameWrapper parsed = VisualFrameWrapper.createVisualFrame(doc.getDocumentElement());

      return (GradientColorFrame) parsed.getVisualFrame();
   }

   /**
    * The largest gap, in OKLab lightness, between the ramp and a straight line from one end's
    * lightness to the other's, over eleven samples.
    */
   private static double lightnessDeviation(GradientColorFrame frame) {
      double from = OKLab.fromColor(frame.getFromColor())[0];
      double to = OKLab.fromColor(frame.getToColor())[0];
      double worst = 0;

      for(int i = 0; i <= 10; i++) {
         double t = i / 10.0;
         double actual = OKLab.fromColor(frame.getColor(t))[0];
         worst = Math.max(worst, Math.abs(actual - (from + (to - from) * t)));
      }

      return worst;
   }
}
