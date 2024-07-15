/*
 * inetsoft-core - StyleBI is a business intelligence web application.
 * Copyright © 2024 InetSoft Technology (info@inetsoft.com)
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
 * You should have received a copy of the GNU Affrero General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package inetsoft.graph.internal.text;

import inetsoft.graph.VGraph;
import inetsoft.graph.Visualizable;
import inetsoft.graph.element.GraphElement;
import inetsoft.graph.element.PointElement;
import inetsoft.graph.guide.VLabel;
import inetsoft.graph.visual.VOText;

import java.awt.geom.*;
import java.util.*;

/**
 * Manage the layout of word cloud labels for one point.
 *
 * @version 12.2
 * @author InetSoft Technology Corp
 */
public class WordCloudLayout {
   public WordCloudLayout(VGraph vgraph) {
      this.vgraph = vgraph;

      for(int i = 0; i < vgraph.getVisualCount(); i++) {
         Visualizable visual = vgraph.getVisual(i);

         if(visual instanceof VOText) {
            VOText vlabel = (VOText) visual;
            GraphElement elem = vlabel.getGraphElement();

            if(!(elem instanceof PointElement)) {
               continue;
            }

            if(!((PointElement) elem).isWordCloud()) {
               continue;
            }

            if(vlabel.getCollisionModifier() != VLabel.MOVE_FREE) {
               continue;
            }

            Rectangle2D box = vlabel.getBounds();
            Point2D center = new Point2D.Double(box.getCenterX(), box.getCenterY());
            List<VOText> labels = clouds.get(center);

            if(labels == null) {
               clouds.put(center, labels = new ArrayList<>());
            }

            // word cloud laid out here, moving it again may cause overlapping
            vlabel.setCollisionModifier(VLabel.MOVE_NONE);
            labels.add(vlabel);
         }
      }
   }

   // layout labels in word clouds
   public void layout() {
      Rectangle2D plotBounds = vgraph.getPlotBounds();

      for(Point2D center : clouds.keySet()) {
         layout(clouds.get(center), center, plotBounds);
      }
   }

   // layout the labels within the bounds
   private void layout(List<VOText> labels, Point2D center, Rectangle2D plotBounds) {
      Area occupied = null;

      // place large text in center
      Collections.sort(labels, new Comparator<VOText>() {
         public int compare(VOText t1, VOText t2) {
            return t2.getTextSpec().getFont().getSize() -
               t1.getTextSpec().getFont().getSize();
         }
      });

      for(int i = 0; i < labels.size() && !vgraph.isCancelled(); i++) {
         VOText label = labels.get(i);
         Rectangle2D box = addPadding(label.getBounds());

         if(occupied == null) {
            occupied = new Area(box);
            continue;
         }

         box = move(box, occupied, center, i, plotBounds);
         label.setPosition(new Point2D.Double(box.getX(), box.getY()));
      }
   }

   // add some padding so words next to each other don't appear as one phrase. (58319)
   private Rectangle2D addPadding(Rectangle2D bounds) {
      double padding = Math.min(bounds.getHeight() / 8, 3);
      return new Rectangle2D.Double(bounds.getX() - padding, bounds.getY(),
                                    bounds.getWidth() + padding * 2, bounds.getHeight());
   }

   // move label until it's on a correct spot or no space left
   private Rectangle2D move(Rectangle2D label, Area occupied, Point2D center, int idx,
                            Rectangle2D plotBounds)
   {
      final double STEP = 0.05 + idx / 1000.0;
      final double ANGLE_STEP = Math.PI / 180;
      final double moe = 1;
      double dist = 5 + idx / 10;
      double angle = Math.PI / 2;
      double centerX = plotBounds.getCenterX();
      double centerY = plotBounds.getCenterY();
      double w2 = plotBounds.getWidth() + label.getWidth();
      double h2 = plotBounds.getHeight() + label.getHeight();
      // max distance between center to center of label (if greater than this, the label
      // is outside of the bounds of plot area).
      double maxDist = Math.sqrt(w2 * w2 + h2 * h2) / 2;

      label = new Rectangle2D.Double(label.getX() + moe, label.getY() + moe,
                                     label.getWidth() - moe * 2,
                                     label.getHeight() - moe * 2);

      do {
         dist += STEP;
         double xinc = Math.cos(angle) * dist;
         double yinc = Math.sin(angle) * dist;
         double nxcenter = center.getX() + xinc;
         double nycenter = center.getY() + yinc;
         double newDist = Point2D.distance(centerX, centerY, nxcenter, nycenter);

         label = new Rectangle2D.Double(nxcenter - label.getWidth() / 2,
                                        nycenter - label.getHeight() / 2,
                                        label.getWidth(), label.getHeight());

         if(newDist > maxDist) {
            return label;
         }

         angle += ANGLE_STEP;
      }
      while(occupied.intersects(label) && !vgraph.isCancelled());

      occupied.add(new Area(label));
      return label;
   }

   private VGraph vgraph;
   // center to list of labels
   private Map<Point2D, List<VOText>> clouds = new HashMap<>();
}
