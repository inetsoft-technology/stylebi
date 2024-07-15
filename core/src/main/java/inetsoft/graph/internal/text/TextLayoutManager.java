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
import inetsoft.graph.guide.VLabel;
import inetsoft.graph.guide.axis.Axis;
import inetsoft.graph.internal.GTool;
import inetsoft.graph.visual.*;

import java.awt.*;
import java.awt.geom.Rectangle2D;
import java.util.List;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Manages text label layout. Including functions to move labels to avoid
 * overlapping.
 *
 * @version 10.0
 * @author InetSoft Technology Corp
 */
public class TextLayoutManager {
   /**
    * Create a layout manager.
    * @param vgraph the base vgraph.
    */
   public TextLayoutManager(VGraph vgraph) {
      this.vgraph = vgraph;
   }

   private void init() {
      vtexts = new ArrayList<>();
      fixed = new ArrayList<>();
      vos = new ArrayList<>();

      boolean moveUp = true;
      boolean moveRight = true;

      // create helpers (which manages label location) for texts
      for(int i = 0; i < vgraph.getVisualCount(); i++) {
         Visualizable visual = vgraph.getVisual(i);

         // optimization
         if(visual instanceof DensityFormVO) {
            continue;
         }

         Rectangle2D bounds = visual.getBounds();

         if(bounds != null && (Double.isNaN(bounds.getX()) || Double.isNaN(bounds.getY()))) {
            continue;
         }

         if(visual instanceof VOText) {
            VLabel vlabel = (VOText) visual;
            LayoutHelper holder = createHelper(visual);
            holder.reset();

            if(vlabel.getCollisionModifier() == VLabel.MOVE_NONE) {
               fixed.add(holder);
            }
            else {
               if(vlabel.getCollisionModifier() != VLabel.MOVE_UP) {
                  moveUp = false;
               }
               else if(vlabel.getCollisionModifier() != VLabel.MOVE_RIGHT) {
                  moveRight = false;
               }

               vtexts.add(holder);
            }
         }
         else if(visual instanceof VLabel) {
            fixed.add(new FixedHelper(visual, vgraph));
         }
         else if(visual instanceof LabelFormVO) {
            VLabel vlabel = ((LabelFormVO) visual).getVLabel();

            if(vlabel.getCollisionModifier() == VLabel.MOVE_NONE) {
               fixed.add(new FixedHelper(vlabel, vgraph));
            }
            else {
               LayoutHelper holder = createHelper(visual);
               holder.reset();
               vtexts.add(holder);
               moveUp = false;
               moveRight = false;
            }
         }
         else if(visual instanceof ElementVO) {
            Shape[] shapes = ((ElementVO) visual).getShapes();

            for(int j = 0; j < shapes.length; j++) {
               vos.add(new VOHelper(visual, vgraph, shapes[j]));
            }
         }
      }

      // add helpers for axis labels
      for(int i = 0; i < vgraph.getAxisCount(); i++) {
         Axis axis = vgraph.getAxis(i);
         VLabel[] labels = axis.getLabels();

         for(int j = 0; labels != null && j < labels.length; j++) {
            fixed.add(new FixedHelper(labels[j], vgraph));
         }
      }

      moveComparator = createMoveComparator(vgraph, moveUp, moveRight);

      all = new ArrayList<>(vtexts);
      all.addAll(fixed);

      allvos = new ArrayList<>(all);
      allvos.addAll(vos);
   }

   protected Comparator createMoveComparator(VGraph vgraph, boolean moveUp, boolean moveRight) {
      // if moving up, move the labels at the top-most first
      if(moveUp) {
         moveComparator = new UpComparator();
      }
      // if moving right, move the labels at the right-most first
      else if(moveRight) {
         moveComparator = new RightComparator();
      }

      return null;
   }

   /**
    * Create a layout helper.
    */
   private LayoutHelper createHelper(Visualizable vo) {
      VLabel text;
      boolean inPlot = true;

      if(vo instanceof LabelFormVO) {
         text = ((LabelFormVO) vo).getVLabel();
         inPlot = ((LabelFormVO) vo).isInPlot();
      }
      else {
         text = (VLabel) vo;
      }

      int collision = text.getCollisionModifier();

      if(text instanceof ArcVOText && collision == VLabel.MOVE_ARC) {
         return new ArcHelper((ArcVOText) text, vgraph);
      }

      FreeHelper helper = new FreeHelper(text, vgraph);
      // fixed pos label should not be removed, it's placed there explicitly
      helper.setRemovable(text.isRemovable() && collision != VLabel.MOVE_NONE);
      helper.setCheckVO(vo instanceof TagFormVO);
      helper.setInPlot(inPlot && forceInPlot);

      // restrict the movement so the label is not too far from the point
      if((vo instanceof VOText || vo instanceof TagFormVO) && collision == VLabel.MOVE_FREE) {
         String[] lines = text.getDisplayLabel();
         helper.setMaxSteps(vgraph.getCoordinate().getMaxSteps(lines));
      }

      return helper;
   }

   /**
    * Resolve text overlapping.
    */
   public void resolve() {
      init();

      String mstr = GTool.getProperty("graph.textlayout.maxcount", "1000");
      int maxlayout = Integer.parseInt(mstr);
      // don't try to layout text labels if there are too many of them since it
      // could take a very long time
      final int MAX_LOOP = (vtexts.size() > maxlayout) ? 0 : 100;
      int maxoverlapped = 0;
      int last = Integer.MAX_VALUE;
      // get all overlapping labels for pie and move the one with most
      // free space on the side
      boolean both = GTool.isPolar(vgraph.getCoordinate());

      // main loop to move text to avoid overlapping
      for(int i = 0; i < MAX_LOOP; i++) {
         preResolve(i, vtexts);
         List<LayoutHelper> overlapped = getOverlappedTexts(false, both);

         if(overlapped.size() == 0 || vgraph.isCancelled()) {
            break;
         }

         // no more improvement, probably too crowded. Abandon.
         if(maxoverlapped > MAX_OVERLAP && i > 3 && overlapped.size() >= last) {
            break;
         }

         last = overlapped.size();
         maxoverlapped = Math.max(last, maxoverlapped);

         if(resolve0(overlapped)) {
            break;
         }
      }

      // move labels that have exceeded the max move distance back from fixed
      // to vtexts so the overlap removal and marking logic would see them
      for(int i = fixed.size() - 1; i >= 0; i--) {
         LayoutHelper helper = fixed.get(i);

         if(helper.getCollisionModifier() != VLabel.MOVE_NONE) {
            vtexts.add(helper);
            fixed.remove(i);
         }
      }

      postResolve(vtexts);
   }

   // called after the completion of resolve().
   protected void postResolve(List<LayoutHelper> vtexts) {
      if(forceInPlot) {
         removeOutOfBoundsTexts();
      }

      if(vgraph.isCancelled()) {
         return;
      }

      removeOverlappedTexts();
      markOverlappedAxisLabels();
   }

   /**
    * Called before every resolve iteration.
    */
   protected void preResolve(int i, List<LayoutHelper> vtexts) {
      if(i == 0) {
         flipIfOverlap();
      }
   }

   // flip labels to other side if one overlaps and the other side is empty.
   private void flipIfOverlap() {
      List<LayoutHelper> overlapped = getOverlappedTexts(true, true).stream()
         .filter(a -> a instanceof FreeHelper).collect(Collectors.toList());
      calcResistence(overlapped);
      overlapped.sort((a, b) -> Double.compare(b.getBounds().getY(), a.getBounds().getY()));

      for(LayoutHelper helper : overlapped) {
         ((FreeHelper) helper).flipIfOverlap();
      }
   }

   /**
    * One iteration of moving overlapped text.
    * @return true to terminate the resolution.
    */
   private boolean resolve0(List<LayoutHelper> overlapped) {
      calcResistence(overlapped);
      Collections.sort(overlapped, moveComparator);

      if(overlapped.size() > MAX_OVERLAP) {
         trimOverlappedTexts(overlapped);
      }

      for(int i = 0; i < overlapped.size(); i++) {
         LayoutHelper holder = overlapped.get(i);
         double min = holder.getMinResistance();

         if(min >= LayoutHelper.MAX_RESISTANCE) {
            return i == 0;
         }

         // moving half of the overlapped otherwise both would be moved
         if(shortCircuit(min, i, overlapped)) {
            break;
         }

         holder.move();

         // if the label can't be moved anymore, move it to fixed label list
         if(holder.getCollisionModifier() == VLabel.MOVE_NONE) {
            vtexts.remove(holder);
            fixed.add(holder);
         }
      }

      return false;
   }

   private void calcResistence(List<LayoutHelper> overlapped) {
      // calculate resistance
      for(int i = 0; i < overlapped.size(); i++) {
         LayoutHelper holder = overlapped.get(i);
         List<LayoutHelper> all0;

         holder.reset();

         if(holder.isCheckVO()) {
            all0 = allvos;
         }
         else {
            all0 = all;
         }

         for(int j = 0; j < all0.size(); j++) {
            LayoutHelper holder2 = all0.get(j);

            if(holder == holder2 || holder2.getVisualizable().getZIndex() < 0) {
               continue;
            }

            holder.calc(holder2);
         }

         holder.postCalc();
      }
   }

   protected boolean shortCircuit(double min, int i, List<LayoutHelper> overlapped) {
      int half = Math.max(1, overlapped.size() / 2);
      return i >= half && min >= LayoutHelper.OVERLAP_RESISTANCE;
   }

   /**
    * Get overlapped VOText objects.
    * @param removeEq true to remove identical labels.
    * @param all true if includes all text.
    */
   private List<LayoutHelper> getOverlappedTexts(boolean removeEq, boolean all) {
      List<LayoutHelper> list = new ArrayList<>();
      BitSet processed = new BitSet();
      Rectangle2D pbounds = vgraph.getPlotBounds();

      for(int i = 0; i < vtexts.size(); i++) {
         LayoutHelper vtext = vtexts.get(i);

         if(vtext.getVisualizable().getZIndex() < 0) {
            continue;
         }

         for(int j = i + 1; j < vtexts.size(); j++) {
            LayoutHelper vtext2 = vtexts.get(j);

            if(processed.get(j) && !all || vtext2.getVisualizable().getZIndex() < 0) {
               continue;
            }

            // allow small overlapping for text (since it's not visible anyway)
            if(vtext.overlaps(vtext2, 1)) {
               if(!processed.get(i) && all) {
                  list.add(vtext);
               }

               if(!processed.get(j)) {
                  list.add(vtext2);
               }

               vtext.processOverlapped(vtext2);

               processed.set(i);
               processed.set(j);
            }
         }

         // if the label overlaps with the bounds, add to overlap so it will be
         // moved inside. The text may not be re-scale if it's at the top of
         // a bottom point, and rotated to the left after coord transformation.
         if(!processed.get(i) && !vtext.isContained(pbounds) && !vtext.isClipped()) {
            list.add(vtext);
            processed.set(i);
         }
      }

      if(removeEq) {
         for(int i = 0; i < list.size(); i++) {
            LabelHelper vtext = (LabelHelper) list.get(i);
            Object str1 = vtext.getLabel().getLabel();

            if(vtext.getLabel().getZIndex() < 0) {
               continue;
            }

            for(int j = i + 1; j < list.size(); j++) {
               LabelHelper vtext2 = (LabelHelper) list.get(j);

               if(vtext2.getLabel().getZIndex() < 0 || !vtext2.isRemovable()) {
                  continue;
               }

               Object str2 = vtext2.getLabel().getLabel();

               if(str1 instanceof Object[] && str2 instanceof Object[]) {
                  if(!Arrays.equals((Object[]) str1, (Object[]) str2)) {
                     continue;
                  }
               }
               else if(!Objects.equals(str1, str2)) {
                  continue;
               }

               double xdiff = Math.abs(vtext.getCenter().getX() - vtext2.getCenter().getX());
               double ydiff = Math.abs(vtext.getCenter().getY() - vtext2.getCenter().getY());
               double ymax = 2;
               double xmax = 1;

               if(vtext.getCollisionModifier() == VLabel.MOVE_FREE &&
                  vtext2.getCollisionModifier() == VLabel.MOVE_FREE)
               {
                  xmax = 10;
                  ymax = 10;
               }

               // don't remove if horizontal overlapped (common in bars)
               if(xdiff < xmax && ydiff < ymax) {
                  vtext2.getLabel().setZIndex(-1);
               }
            }
         }
      }

      for(int i = vtexts.size() - 1; i >= 0; i--) {
         LayoutHelper vtext = vtexts.get(i);

         if(vtext.getVisualizable().getZIndex() < 0) {
            vtexts.remove(i);
         }
      }

      for(int i = 0; i < vtexts.size(); i++) {
         if(!processed.get(i) && vtexts.get(i).needsMoveInside()) {
            list.add(vtexts.get(i));
            processed.set(i);
         }
      }

      // check overlapping with axis labels
      for(int i = 0; i < vtexts.size(); i++) {
         if(processed.get(i)) {
            continue;
         }

         LayoutHelper vtext = vtexts.get(i);
         List<LayoutHelper> fixed0 = fixed;

         if(vtext.isCheckVO()) {
            fixed0 = new ArrayList<>(fixed);
            fixed0.addAll(vos);
         }

         for(int j = 0; j < fixed0.size(); j++) {
            LayoutHelper vtext2 = fixed0.get(j);
            Visualizable label2 = vtext2.getVisualizable();

            if(label2 != null && label2.getZIndex() < 0) {
               continue;
            }

            if(vtext.overlaps(vtext2, 0) && !vtext.isClipped()) {
               list.add(vtext);
               processed.set(i);
            }
         }
      }

      return list;
   }

   /**
    * Set the overlap background on axis labels. Add a semi-transparent
    * background to overlapping labels so they are visually distinguishable.
    */
   private void markOverlappedAxisLabels() {
      // check overlapping with axis labels
      for(int i = 0; i < fixed.size(); i++) {
         LayoutHelper vtext = fixed.get(i);

         if(!(vtext instanceof LabelHelper)) {
            continue;
         }

         VLabel label = (VLabel) vtext.getVisualizable();

         if(label != null && label.getZIndex() < 0) {
            continue;
         }

         for(int j = i + 1; j < fixed.size(); j++) {
            LayoutHelper vtext2 = fixed.get(j);

            if(!(vtext2 instanceof LabelHelper)) {
               continue;
            }

            VLabel label2 = (VLabel) vtext2.getVisualizable();

            if(label2 != null && label2.getZIndex() < 0) {
               continue;
            }

            if(vtext.overlaps(vtext2, 3) && label2.getTextSpec().getBackground() == null) {
               label2.setTextSpec(label2.getTextSpec().clone());
               label2.getTextSpec().setBackground(OVERLAP_BG);
               break;
            }
         }
      }
   }

   /**
    * Trim the overlapped labels for performance.
    */
   private void trimOverlappedTexts(List<LayoutHelper> overlapped) {
      int n = MAX_OVERLAP;

      for(int i = overlapped.size() - 1; i >= n; i--) {
         LayoutHelper helper = overlapped.get(i);

         if((i > MAX_LABELS ||
             helper.getMinResistance() >= LayoutHelper.OVERLAP_RESISTANCE) && helper.isRemovable())
         {
            overlapped.remove(i);
            helper.getVisualizable().setZIndex(-1);
         }
         else {
            break;
         }
      }
   }

   /**
    * Remove overlapped texts.
    */
   private void removeOverlappedTexts() {
      List<LayoutHelper> overlapped = getOverlappedTexts(false, true);

      // remove overlapping
      for(int i = 0; i < overlapped.size(); i++) {
         LayoutHelper helper = overlapped.get(i);

         for(int j = i + 1; j < overlapped.size(); j++) {
            LayoutHelper helper2 = overlapped.get(j);

            if(helper.overlaps(helper2, 3) && helper2.isRemovable()) {
               overlapped.remove(j);
               j--;
               helper2.getVisualizable().setZIndex(-1);
            }
         }
      }

      if(vgraph.isCancelled()) {
         return;
      }

      // remove overlapping vtexts with axis label
      for(int i = 0; i < overlapped.size(); i++) {
         LayoutHelper helper = overlapped.get(i);

         if(!helper.isRemovable()) {
            continue;
         }

         for(int j = 0; j < fixed.size(); j++) {
            LayoutHelper helper2 = fixed.get(j);
            Visualizable label = helper2.getVisualizable();

            if(label != null && label.getZIndex() < 0) {
               continue;
            }

            if(helper.overlaps(helper2, 0)) {
               overlapped.remove(i);
               i--;
               helper.getVisualizable().setZIndex(-1);
               break;
            }
         }
      }
   }

   private void removeOutOfBoundsTexts() {
      List<LayoutHelper> overlapped = getOverlappedTexts(false, true);
      Rectangle2D pbounds = vgraph.getPlotBounds();

      // tolerate one pixel overlapping between text and plot bounds
      pbounds = new Rectangle2D.Double(pbounds.getX() - 1, pbounds.getY() - 1,
                                       pbounds.getWidth() + 2, pbounds.getHeight() + 2);

      // remove out of bounds
      for(int i = 0; i < overlapped.size(); i++) {
         LayoutHelper helper = overlapped.get(i);

         if(!helper.isContained(pbounds) && helper.isRemovable()) {
            overlapped.remove(i);
            i--;
            helper.getVisualizable().setZIndex(-1);
         }
      }
   }

   /**
    * Set whether to force labels inside of the plot area (and remove any that are not
    * after resolving overlapping.
    */
   public void setForceInPlot(boolean forceInPlot) {
      this.forceInPlot = forceInPlot;
   }

   /**
    * Sort MOVE_UP objects.
    */
   private static class UpComparator implements Comparator<LayoutHelper> {
      @Override
      public int compare(LayoutHelper text1, LayoutHelper text2) {
         return Double.compare(text2.getCenter().getY(), text1.getCenter().getY());
      }
   }

   /**
    * Sort MOVE_RIGHT objects.
    */
   private static class RightComparator implements Comparator<LayoutHelper> {
      @Override
      public int compare(LayoutHelper text1, LayoutHelper text2) {
         return Double.compare(text2.getCenter().getX(), text1.getCenter().getX());
      }
   }

   // maximum overlapped labels to resolve
   private static final int MAX_OVERLAP = 200;
   // maximum overlapped labels to keep on on chart
   private static final int MAX_LABELS = 500;
   private static final Color OVERLAP_BG = new Color(255, 255, 255, 80);

   protected VGraph vgraph;
   private List<LayoutHelper> vtexts;
   private List<LayoutHelper> fixed;
   private List<LayoutHelper> vos;
   private List<LayoutHelper> all; // all labels and text
   private List<LayoutHelper> allvos; // all labels and text, and vos
   private Comparator moveComparator;
   private boolean forceInPlot = true;
}
