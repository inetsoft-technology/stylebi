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
package inetsoft.graph.visual;

import inetsoft.graph.element.TreemapElement;
import inetsoft.graph.geometry.ElementGeometry;
import inetsoft.graph.internal.GTool;
import inetsoft.graph.internal.PolarUtil;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import inetsoft.util.graphics.SVGSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedStatic;
import org.mockito.stubbing.Answer;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.awt.*;
import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.function.ToDoubleFunction;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * A ring label too long for its segment is cut to fit and ends with an ellipsis when its
 * element asks for it; otherwise it is clipped by the ring as before.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = BaseTestConfiguration.class,
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class CircularVOTextFitTest {
   // Batik is not on the core test classpath, but GTool.drawString asks SVGSupport whether
   // the graphics is SVG. Seed a stub (not SVG) and restore the cache after.
   private SVGSupport savedSvgSupport;

   @BeforeEach
   void stubSvgSupport() {
      savedSvgSupport = SVGSupport._CACHE[0];
      SVGSupport._CACHE[0] = mock(SVGSupport.class);
   }

   @AfterEach
   void restoreSvgSupport() {
      SVGSupport._CACHE[0] = savedSvgSupport;
   }

   // one unit per character, so "Field …" (7) fits a room of 7 and "Field S…" (8) does not
   @Test
   void aCutAfterASpaceEndsOnTheLastLetter() {
      assertEquals("Field…", PolarUtil.fitText("Field Services", 7, String::length));
      assertEquals("Field Services", PolarUtil.fitText("Field Services", 14, String::length));
   }

   @Test
   void aRadialLabelTooLongForItsRingIsCutWithAnEllipsis() {
      CircularVOText text = radial(label(LONG, true), RIGHT_HALF);
      List<Drawn> drawn = paint(text);

      assertEquals(1, drawn.size(), "one string is drawn");
      String s = drawn.get(0).text();
      assertCut(LONG, s, RADIAL_ROOM, radialWidth(text));
      assertInsideRing(text, drawn.get(0), false);
   }

   @Test
   void aRadialLabelOnTheLeftHalfIsFlippedCutAndInsideTheRing() {
      CircularVOText text = radial(label(LONG, true), LEFT_HALF);
      List<Drawn> drawn = paint(text);

      assertEquals(1, drawn.size(), "one string is drawn");
      assertCut(LONG, drawn.get(0).text(), RADIAL_ROOM, radialWidth(text));
      assertInsideRing(text, drawn.get(0), true);
   }

   @Test
   void aRadialLabelThatFitsIsUnchanged() {
      CircularVOText text = radial(label(SHORT, true), RIGHT_HALF);
      List<Drawn> drawn = paint(text);

      assertTrue(radialWidth(text).applyAsDouble(SHORT) <= RADIAL_ROOM, "premise: it fits");
      assertEquals(List.of(SHORT), texts(drawn));
   }

   @Test
   void anArcLabelTooLongForItsSegmentIsCutWithAnEllipsis() {
      CircularVOText text = arc(label(LONG, true));
      List<Drawn> drawn = paint(text);
      String s = String.join("", texts(drawn));

      assertTrue(s.endsWith(ELLIPSIS), "ends with an ellipsis: " + s);
      String head = s.substring(0, s.length() - 1);
      assertFalse(head.isEmpty(), "keeps at least one character");
      assertTrue(LONG.startsWith(head), "a head of the label: " + s);

      // every glyph, the ellipsis too, starts and ends inside the segment
      FontMetrics fm = metrics(text.getFont());
      double r = ARC_RADIUS - fm.getDescent();
      Drawn last = drawn.get(drawn.size() - 1);

      assertTrue(angle(drawn.get(0)) >= ARC_START - 1e-6, "starts inside the segment");
      assertTrue(angle(last) + fm.stringWidth(last.text()) / r <= ARC_START + ARC_EXTENT + 1e-6,
                 "ends inside the segment: " + s);
   }

   @Test
   void anArcLabelThatFitsIsUnchanged() {
      CircularVOText text = arc(label(SHORT, true));

      assertEquals(SHORT, String.join("", texts(paint(text))));
   }

   @Test
   void aSegmentTooThinForOneLetterAndTheEllipsisShowsNoLabel() {
      CircularVOText radial = radial(label(LONG, true), RIGHT_HALF);
      radial.setCircle(CX, CY, 200, 188, RIGHT_HALF, Math.PI / 4);
      CircularVOText arc = arc(label(LONG, true));
      arc.setCircle(CX, CY, ARC_OUTER, ARC_INNER, Math.PI / 2, 0.03);

      assertEquals(List.of(), texts(paint(radial)), "no lone ellipsis on a thin ring");
      assertEquals(List.of(), texts(paint(arc)), "no lone ellipsis on a thin segment");
   }

   @Test
   void withoutTheHintALongLabelIsDrawnWholeAndClippedAsBefore() {
      CircularVOText right = radial(label(LONG, false), RIGHT_HALF);
      CircularVOText left = radial(label(LONG, false), LEFT_HALF);
      String arc = String.join("", texts(paint(arc(label(LONG, false)))));

      assertEquals(List.of(LONG), texts(paint(right)));
      assertEquals(List.of(LONG), texts(paint(left)));
      assertFalse(arc.contains(ELLIPSIS), "no mark: " + arc);
      assertTrue(arc.length() < LONG.length() && LONG.startsWith(arc),
                 "the arc stops at the segment edge: " + arc);
   }

   @Test
   void aThinRingWithoutTheHintStillDrawsTheClippedLabel() {
      CircularVOText radial = radial(label(LONG, false), RIGHT_HALF);
      radial.setCircle(CX, CY, 200, 188, RIGHT_HALF, Math.PI / 4);

      assertEquals(List.of(LONG), texts(paint(radial)));
   }

   @Test
   void fitTextKeepsTheLongestHeadThatFitsWithTheEllipsis() {
      ToDoubleFunction<String> tenEach = s -> s.length() * 10.0;

      assertEquals("Accounts", PolarUtil.fitText("Accounts", 80, tenEach));
      assertEquals("Acco" + ELLIPSIS, PolarUtil.fitText("Accounts", 50, tenEach));
      assertEquals("A" + ELLIPSIS, PolarUtil.fitText("Accounts", 20, tenEach));
      assertNull(PolarUtil.fitText("Accounts", 19, tenEach), "no lone ellipsis");
      assertNull(PolarUtil.fitText("A", 5, tenEach), "a single letter is not cut");
   }

   @Test
   void fitTextKeepsASurrogatePairWhole() {
      String text = "A\uD83D\uDE00BC";

      assertEquals("A" + ELLIPSIS, PolarUtil.fitText(text, 30, s -> s.length() * 10.0));
   }

   // a ring label of a sunburst element, with the ellipsis hint when asked
   private static CircularVOText label(String text, boolean ellipsis) {
      TreemapElement elem = new TreemapElement("Div", "Name");
      elem.setMapType(TreemapElement.Type.SUNBURST);

      if(ellipsis) {
         elem.setHint(CircularVOText.HINT_ELLIPSIS, "true");
      }

      ElementGeometry gobj = mock(ElementGeometry.class);
      when(gobj.getElement()).thenReturn(elem);
      ElementVO vo = mock(ElementVO.class);
      when(vo.getGeometry()).thenReturn(gobj);

      return new CircularVOText(text, vo, null, null, -1, gobj);
   }

   // a radial label in a ring from 130 to 200, an eighth of a turn wide, centered on mid
   private static CircularVOText radial(CircularVOText text, double mid) {
      text.setCircle(CX, CY, RADIAL_OUTER, RADIAL_INNER, mid - Math.PI / 8, Math.PI / 4);
      text.setRadial(true);
      return text;
   }

   // an arc label at the top of a ring from 150 to 200
   private static CircularVOText arc(CircularVOText text) {
      text.setCircle(CX, CY, ARC_OUTER, ARC_INNER, ARC_START, ARC_EXTENT);
      text.setRadial(false);
      return text;
   }

   // the strings the label draws, each with the origin it is drawn at
   private static List<Drawn> paint(CircularVOText text) {
      BufferedImage img = new BufferedImage(600, 600, BufferedImage.TYPE_INT_ARGB);
      Graphics2D g = img.createGraphics();
      List<Drawn> drawn = new ArrayList<>();
      // every GTool call runs as is; drawString also records its string and origin
      Answer<Object> record = inv -> {
         if("drawString".equals(inv.getMethod().getName())) {
            Graphics2D g2 = inv.getArgument(0);
            Point2D origin = g2.getTransform().transform(new Point2D.Double(), null);
            drawn.add(new Drawn(inv.getArgument(1), origin));
         }

         return inv.callRealMethod();
      };

      try(MockedStatic<GTool> ignored = mockStatic(GTool.class, record)) {
         text.paint(g);
      }
      finally {
         g.dispose();
      }

      return drawn;
   }

   private static List<String> texts(List<Drawn> drawn) {
      return drawn.stream().map(Drawn::text).collect(Collectors.toList());
   }

   // s is the label cut to its longest head that fits the room with the ellipsis
   private static void assertCut(String label, String s, double room,
                                 ToDoubleFunction<String> width)
   {
      assertTrue(s.endsWith(ELLIPSIS), "ends with an ellipsis: " + s);
      String head = s.substring(0, s.length() - 1);
      assertFalse(head.isEmpty(), "keeps at least one character");
      assertTrue(label.startsWith(head), "a head of the label: " + s);
      assertTrue(width.applyAsDouble(s) <= room, "fits the room " + room + ": " + s);
      String longer = label.substring(0, head.length() + 1) + ELLIPSIS;
      assertTrue(width.applyAsDouble(longer) > room, "one more letter would not fit: " + s);
   }

   // the drawn string lies between the inner and outer edges of the ring, less the 2px insets
   private static void assertInsideRing(CircularVOText text, Drawn drawn, boolean flipped) {
      double mid = text.getAngle() + Math.PI / 8;
      // distance of the origin from the center along the label direction
      double along = (drawn.origin().getX() - CX) * Math.cos(-mid) +
         (drawn.origin().getY() - CY) * Math.sin(-mid);
      double w = radialWidth(text).applyAsDouble(drawn.text());
      // a flipped label is drawn from the outer end back toward the center
      double from = flipped ? along - w : along;
      double to = flipped ? along : along + w;

      assertTrue(from >= RADIAL_INNER + 2 - 0.5, "starts inside the ring: " + from);
      assertTrue(to <= RADIAL_OUTER - 2 + 0.5, "ends inside the ring: " + to);
   }

   // the width the radial painter measures a string with
   private static ToDoubleFunction<String> radialWidth(CircularVOText text) {
      Font font = text.getFont();
      return s -> GTool.stringWidth(new String[] { s }, font);
   }

   // the metrics the arc painter draws with
   private static FontMetrics metrics(Font font) {
      Graphics2D g = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB).createGraphics();

      try {
         g.setFont(font);
         return g.getFontMetrics();
      }
      finally {
         g.dispose();
      }
   }

   // the angle of a drawn glyph's origin around the center
   private static double angle(Drawn drawn) {
      return Math.atan2(CY - drawn.origin().getY(), drawn.origin().getX() - CX);
   }

   private record Drawn(String text, Point2D origin) {
   }

   private static final String ELLIPSIS = "…";
   private static final String LONG = "Regional Distribution Partners";
   private static final String SHORT = "Core";
   private static final double CX = 300;
   private static final double CY = 300;
   private static final double RADIAL_OUTER = 200;
   private static final double RADIAL_INNER = 130;
   // the room between the 2px insets of the ring
   private static final double RADIAL_ROOM = RADIAL_OUTER - 2 - (RADIAL_INNER + 2);
   private static final double RIGHT_HALF = 0;
   private static final double LEFT_HALF = Math.PI;
   private static final double ARC_OUTER = 200;
   private static final double ARC_INNER = 150;
   private static final double ARC_RADIUS = (ARC_OUTER + ARC_INNER) / 2;
   private static final double ARC_START = Math.PI / 2 - 0.25;
   private static final double ARC_EXTENT = 0.5;
}
