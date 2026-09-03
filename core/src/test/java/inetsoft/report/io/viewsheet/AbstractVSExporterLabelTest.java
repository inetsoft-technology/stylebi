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
package inetsoft.report.io.viewsheet;

import inetsoft.report.internal.Common;
import inetsoft.test.*;
import inetsoft.uql.viewsheet.VSCompositeFormat;
import inetsoft.uql.viewsheet.internal.LabelInfo;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.awt.*;
import java.awt.geom.Rectangle2D;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifies that the input-label geometry shared by every export format is derived
 * from the label font rather than a fixed grid row height, so exports match the
 * browser preview (Bug: label differs between preview and export).
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class AbstractVSExporterLabelTest {
   private static final Font BIG_FONT = new Font("Dialog", Font.PLAIN, 24);
   private static final double WIDGET_W = 100;
   private static final double WIDGET_H = 20;

   private static LabelInfo labelInfo(String position, int gap, Font font) {
      LabelInfo info = new LabelInfo("label");
      info.setLabelPositionValue(position);
      info.setLabelGapValue(gap);

      if(font != null) {
         VSCompositeFormat fmt = new VSCompositeFormat();
         fmt.getUserDefinedFormat().setFont(font);
         info.setLabelFormat(fmt);
      }

      return info;
   }

   private static Rectangle2D widgetBounds() {
      return new Rectangle2D.Double(0, 0, WIDGET_W, WIDGET_H);
   }

   private static double expectedLabelHeight(Font font) {
      return Math.ceil(Common.getHeight(font));
   }

   @Test
   void labelHeightFollowsFontNotDefaultRowHeight() {
      LabelInfo info = labelInfo(LabelInfo.LEFT, 5, BIG_FONT);
      Rectangle2D[] split = AbstractVSExporter.splitInputBounds(widgetBounds(), info);
      double expected = expectedLabelHeight(BIG_FONT);

      assertEquals(expected, split[0].getHeight(), 0.001,
         "label box height should be the rendered height of the 24pt font");
      assertTrue(expected > WIDGET_H,
         "sanity: a 24pt label must be taller than the 20px widget row");
   }

   @Test
   void leftLabelIsCenteredOnWidgetAndOverhangsWhenTaller() {
      LabelInfo info = labelInfo(LabelInfo.LEFT, 5, BIG_FONT);
      Rectangle2D[] split = AbstractVSExporter.splitInputBounds(widgetBounds(), info);
      Rectangle2D label = split[0];
      Rectangle2D widget = split[1];

      // matches the browser's align-items:center — the taller label overhangs the row
      assertEquals((WIDGET_H - label.getHeight()) / 2.0, label.getY(), 0.001,
         "label should stay centered on the widget row, overhanging symmetrically");
      assertTrue(label.getY() < 0, "a 24pt label must overhang a 20px row");

      assertEquals(0.0, label.getX(), 0.001);
      assertEquals(label.getWidth() + info.getLabelGap(), widget.getX(), 0.001,
         "widget should start after the label plus the gap");
      assertEquals(WIDGET_W - label.getWidth() - info.getLabelGap(), widget.getWidth(), 0.001);
      assertEquals(WIDGET_H, widget.getHeight(), 0.001, "widget keeps the full row height");
   }

   @Test
   void rightLabelMirrorsLeft() {
      LabelInfo info = labelInfo(LabelInfo.RIGHT, 5, BIG_FONT);
      Rectangle2D[] split = AbstractVSExporter.splitInputBounds(widgetBounds(), info);
      Rectangle2D label = split[0];
      Rectangle2D widget = split[1];

      assertEquals(WIDGET_W - label.getWidth(), label.getX(), 0.001);
      assertEquals(0.0, widget.getX(), 0.001);
      assertEquals(WIDGET_W - label.getWidth() - info.getLabelGap(), widget.getWidth(), 0.001);
   }

   @Test
   void topLabelExpandsBoundsByFontHeightAndGap() {
      LabelInfo info = labelInfo(LabelInfo.TOP, 5, BIG_FONT);
      double labelH = expectedLabelHeight(BIG_FONT);
      Rectangle2D full = AbstractVSExporter.expandBoundsForLabel(widgetBounds(), info);

      assertEquals(WIDGET_H + labelH + 5, full.getHeight(), 0.001,
         "expanded height should reserve the font height plus the gap");

      Rectangle2D[] split = AbstractVSExporter.splitInputBounds(full, info);
      assertEquals(0.0, split[0].getY(), 0.001, "label sits at the top");
      assertEquals(labelH, split[0].getHeight(), 0.001);
      assertEquals(labelH + 5, split[1].getY(), 0.001);
      assertEquals(WIDGET_H, split[1].getHeight(), 0.001,
         "widget keeps its original height when the bounds are expanded");
   }

   @Test
   void bottomLabelExpandsBoundsByFontHeightAndGap() {
      LabelInfo info = labelInfo(LabelInfo.BOTTOM, 0, BIG_FONT);
      double labelH = expectedLabelHeight(BIG_FONT);
      Rectangle2D full = AbstractVSExporter.expandBoundsForLabel(widgetBounds(), info);

      assertEquals(WIDGET_H + labelH, full.getHeight(), 0.001);

      Rectangle2D[] split = AbstractVSExporter.splitInputBounds(full, info);
      assertEquals(full.getHeight() - labelH, split[0].getY(), 0.001, "label sits at the bottom");
      assertEquals(0.0, split[1].getY(), 0.001);
      assertEquals(WIDGET_H, split[1].getHeight(), 0.001);
   }

   @Test
   void leftAndRightLabelsDoNotExpandBounds() {
      for(String pos : new String[]{ LabelInfo.LEFT, LabelInfo.RIGHT }) {
         Rectangle2D full =
            AbstractVSExporter.expandBoundsForLabel(widgetBounds(), labelInfo(pos, 5, BIG_FONT));
         assertEquals(WIDGET_H, full.getHeight(), 0.001,
            pos + " label should shrink within the existing bounds, like flex-shrink");
      }
   }

   /**
    * adjustSizeForInputLabels() has to widen/heighten the page by the label box, not just
    * by expandBoundsForLabel(), because a LEFT/RIGHT label taller than the widget row
    * sticks out past the assembly on both sides.
    */
   @Test
   void tallSideLabelOverhangsTheAssemblyBounds() {
      Rectangle2D bounds = widgetBounds();
      LabelInfo info = labelInfo(LabelInfo.LEFT, 5, BIG_FONT);

      assertEquals(bounds, AbstractVSExporter.expandBoundsForLabel(bounds, info),
         "expandBoundsForLabel leaves LEFT/RIGHT alone, so it cannot report the overhang");

      Rectangle2D label = AbstractVSExporter.splitInputBounds(bounds, info)[0];

      assertTrue(label.getMaxY() > bounds.getMaxY(),
         "label box must be reported as extending below the assembly");
      assertTrue(label.getY() < bounds.getY(),
         "label box must be reported as extending above the assembly");
   }

   @Test
   void defaultFontStillProducesUsableLabelBox() {
      // no label format at all — falls back to the default PLAIN 11 font
      LabelInfo info = labelInfo(LabelInfo.LEFT, 5, null);
      Rectangle2D[] split = AbstractVSExporter.splitInputBounds(widgetBounds(), info);

      assertTrue(split[0].getHeight() > 0, "default-font label must have a positive height");
      assertTrue(split[0].getHeight() <= WIDGET_H,
         "an 11pt label should fit within the 20px row");
      assertTrue(split[0].getWidth() > 0);
      assertTrue(split[1].getWidth() > 0, "widget must retain some width");
   }
}
