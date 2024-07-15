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
package inetsoft.graph.internal.text;

import inetsoft.graph.VGraph;
import inetsoft.graph.coord.Coordinate;
import inetsoft.graph.coord.PolarCoord;

import java.awt.geom.Rectangle2D;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Manages text label layout on a polar coordinate.
 *
 * @version 13.7
 * @author InetSoft Technology Corp
 */
public class PolarLayoutManager extends TextLayoutManager {
   public PolarLayoutManager(VGraph vgraph) {
      super(vgraph);

      Coordinate coord = vgraph.getCoordinate();
      boolean labelMovable = coord instanceof PolarCoord && ((PolarCoord) coord).isLabelMoveable();
      setForceInPlot(!labelMovable);
   }

   @Override
   protected Comparator createMoveComparator(VGraph vgraph, boolean moveUp, boolean moveRight) {
      return new PolarComparator();
   }

   @Override
   protected void preResolve(int i, List<LayoutHelper> vtexts) {
      if(i > 20) {
         trimUnimportantTexts(vtexts);
         trimOverlappedArcTexts(vtexts);
      }
   }

   @Override
   protected void postResolve(List<LayoutHelper> vtexts) {
      super.postResolve(vtexts);
      removeOutOfPlaceTexts(vtexts);
      moveTextInside(vtexts);
   }

   // remove the overlapping labels with less importance.
   private void trimOverlappedArcTexts(List<LayoutHelper> vtexts) {
      List<ArcHelper>[] quadrants = getQuadrants(vtexts);

      for(List<ArcHelper> quadrant : quadrants) {
         Collections.sort(quadrant, (a, b) -> Double.compare(a.getAngle(), b.getAngle()));

         for(int i = 0; i < quadrant.size() - 1; i++) {
            if(quadrant.get(i).overlaps(quadrant.get(i + 1), 1)) {
               if(quadrant.get(i).getImportance() < quadrant.get(i + 1).getImportance()) {
                  quadrant.get(i).getLabel().setZIndex(-1);
               }
               else {
                  quadrant.get(i + 1).getLabel().setZIndex(-1);
                  i++;
               }
            }
         }
      }
   }

   // remove the least important label in the direction where an out of bounds label
   // (with higher importance) can be moved to.
   private void trimUnimportantTexts(List<LayoutHelper> vtexts) {
      List<ArcHelper>[] quadrants = getQuadrants(vtexts);
      Rectangle2D bounds = vgraph.getPlotBounds();

      for(int i = 0; i < quadrants.length; i++) {
         List<ArcHelper> quadrant = quadrants[i];

         // sort by angle so more space is available down the list.
         if(i == 0 || i == 2) {
            Collections.sort(quadrant, (a, b) -> Double.compare(a.getAngle(), b.getAngle()));
         }
         else {
            Collections.sort(quadrant, (a, b) -> Double.compare(b.getAngle(), a.getAngle()));
         }

         for(int j = 0; j < quadrant.size() - 1; j++) {
            if(!quadrant.get(j).isContained(bounds)) {
               LabelHelper nobody = null;
               double importance = quadrant.get(j).getImportance();

               for(int k = j + 1; k < quadrant.size(); k++) {
                  if(importance > quadrant.get(k).getImportance()) {
                     nobody = quadrant.get(k);
                     importance = nobody.getImportance();
                  }
               }

               if(nobody != null) {
                  nobody.getLabel().setZIndex(-1);
                  return;
               }
            }
         }
      }
   }

   // remove a label if it's swap with another label in relation to the respective slices.
   private void removeOutOfPlaceTexts(List<LayoutHelper> vtexts) {
      List<ArcHelper> texts = getArcHelpers(vtexts);
      Collections.sort(texts, (a, b) -> Double.compare(a.getOrigAngle(), b.getOrigAngle()));

      for(int i = 0; i < texts.size(); i++) {
         double angle = texts.get(i).getAngle();

         for(int j = i + 1; j < texts.size(); j++) {
            if(texts.get(j).getAngle() < angle) {
               if(texts.get(i).getImportance() < texts.get(j).getImportance()) {
                  texts.get(i).getLabel().setZIndex(-1);
               }
               else {
                  texts.get(j).getLabel().setZIndex(-1);
               }

               break;
            }
         }
      }
   }

   // if text is moved out of bounds, showing the beginning of the text with '..' at end is
   // more meaningful than cutting off at the front with '..' at end. (61950)
   private void moveTextInside(List<LayoutHelper> vtexts) {
      List<ArcHelper> texts = getArcHelpers(vtexts);
      Rectangle2D plot = vgraph.getPlotBounds();

      for(int i = 0; i < texts.size(); i++) {
         Rectangle2D bounds = texts.get(i).getBounds();

         if(bounds.getMinX() < plot.getMinX()) {
            double diff = plot.getMinX() - bounds.getMinX();
            texts.get(i).getLabel().setBounds(plot.getMinX(), bounds.getY(),
                                              bounds.getWidth() - diff, bounds.getHeight());
         }
      }
   }

   // get votext into 4 lists corresponds to top-right, top-left, bottom-left, bottom-right.
   private static List<ArcHelper>[] getQuadrants(List<LayoutHelper> vtexts) {
      List<ArcHelper> texts = getArcHelpers(vtexts);
      List<ArcHelper>[] quadrants = new List[] {
         new ArrayList(), new ArrayList(), new ArrayList(), new ArrayList() };

      for(ArcHelper helper : texts) {
         int n = getN(helper);
         quadrants[n].add(helper);
      }
      return quadrants;
   }

   private static int getN(ArcHelper helper) {
      double angle = helper.getAngle();

      while(angle < 0) {
         angle += 360;
      }

      return (int) (angle % 360) / 90;
   }

   private static List<ArcHelper> getArcHelpers(List<LayoutHelper> vtexts) {
      List<ArcHelper> texts = vtexts.stream()
         .filter(a -> a instanceof ArcHelper && ((ArcHelper) a).getLabel().getZIndex() >= 0)
         .map(a -> (ArcHelper) a)
         .collect(Collectors.toList());
      return texts;
   }

   @Override
   protected boolean shortCircuit(double min, int i, List<LayoutHelper> overlapped) {
      return false;
   }

   /**
    * Sort Polar labels so the label with least resistance is moved first.
    */
   private static class PolarComparator implements Comparator<LayoutHelper> {
      @Override
      public int compare(LayoutHelper text1, LayoutHelper text2) {
         return (int) (text1.getMinResistance() - text2.getMinResistance());
      }
   }
}
