/*
 * inetsoft-core - StyleBI is a business intelligence web application.
 * Copyright © 2024 InetSoft Technology (info@inetsoft.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package inetsoft.graph;

import inetsoft.graph.guide.VLabel;
import inetsoft.graph.internal.GTransform;

import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.geom.Rectangle2D;
import java.util.List;
import java.util.*;
import java.util.stream.Stream;

/**
 * This is a container of visualizable objects.
 *
 * @version 10.0
 * @author InetSoft Technology Corp
 */
public abstract class VContainer extends Visualizable {
   /**
    * Paint the container on the graphics output.
    */
   @Override
   public synchronized void paint(Graphics2D g) {
      paint(g, GraphPaintContext.getDefault());
   }

   /**
    * Paint the container on the graphics output.
    */
   public synchronized void paint(Graphics2D g, GraphPaintContext ctx) {
      if(getZIndex() < 0) {
         return;
      }

      // SVGGraphics2D is expensive to create, just save and restore instead of g.create()
      AffineTransform otrans = g.getTransform();
      Color ocolor = g.getColor();
      Font ofont = g.getFont();

      paintVisualizables(g, visuals, ctx);

      g.setTransform(otrans);
      g.setColor(ocolor);
      g.setFont(ofont);
   }

   /**
    * Paint all visual objects in the container. Graphics is already
    * transformed to be at the top-left corner of the container.
    */
   protected void paintVisualizables(Graphics2D g, List<Visualizable> visuals,
                                     GraphPaintContext ctx)
   {
      ArrayList<Visualizable> sorted = new ArrayList<>(visuals);
      Rectangle clip = g.getClipBounds();
      Collections.sort(sorted);

      for(int i = 0; i < sorted.size(); i++) {
         Visualizable visual = sorted.get(i);
         Rectangle2D bounds = visual.getBounds();
         Rectangle2D visualBounds = visual instanceof VLabel
            // label may be rotated (52259).
            ? ((VLabel) visual).getTransformedBounds().getBounds2D()
            : bounds;

         // check both label bounds and label text bounds since text may be draw spread
         // out to align with bars for date comparison. (60955)

         if(visual.getZIndex() < 0 || !insideClip(clip, visualBounds, visual) &&
            !insideClip(clip, bounds, visual) || !ctx.paintVisual(visual))
         {
            continue;
         }

         visual.paint(g);
      }
   }

   /**
    * Check if visualize should be ignored.
    * @hidden
    */
   protected boolean insideClip(Rectangle2D clip, Rectangle2D vbounds, Visualizable visual) {
      if(!visual.isPaintInBounds()) {
         return true;
      }

      return clip == null || vbounds == null ||
         vbounds.getWidth() == 0 || vbounds.getHeight() == 0 ||
         clip.intersects(vbounds.getX() - 1, vbounds.getY() - 1, vbounds.getWidth() + 2,
                         vbounds.getHeight() + 2);
   }

   /**
    * Set the transformation to map chart coordinate to screen coordinate.
    */
   @Override
   public void setScreenTransform(AffineTransform trans) {
      super.setScreenTransform(trans);

      for(int i = 0; i < getVisualCount(); i++) {
         getVisual(i).setScreenTransform(trans);
      }
   }

   /**
    * Apply the transformation to the screen transformation matrix of visual
    * objects.
    * @param concat true to concatenate and false to prepend the transform.
    */
   public void concat(AffineTransform trans, boolean concat) {
      if(concat) {
         getScreenTransform().concatenate(trans);
      }
      else {
         getScreenTransform().preConcatenate(trans);
      }

      for(int i = 0; i < getVisualCount(); i++) {
         if(concat) {
            getVisual(i).getScreenTransform().concatenate(trans);
         }
         else {
            getVisual(i).getScreenTransform().preConcatenate(trans);
         }
      }
   }

   /**
    * Apply a scale so the transformations performed in the chart coordinate
    * space (1000x1000) are transformed to the graphic output space.
    * @param sx the scale to transfrom 1000 to the physical width.
    * @param sy the scale to transfrom 1000 to the physical height.
    */
   public void scaleTo(double sx, double sy) {
      ((GTransform) getScreenTransform()).scaleTo(sx, sy);

      for(int i = 0; i < getVisualCount(); i++) {
         AffineTransform trans = getVisual(i).getScreenTransform();

         if(trans instanceof GTransform) {
            ((GTransform) trans).scaleTo(sx, sy);
         }
      }
   }

   /**
    * Add a visual object to this graph. Visual classes are defined in
    * inetsoft.graph.visual package.
    */
   public synchronized void addVisual(Visualizable visual) {
      if(visual == null) {
         return;
      }

      visuals.add(visual);
   }

   /**
    * Set a visual object to the specified position.
    */
   public synchronized void setVisual(int idx, Visualizable visual) {
      visuals.set(idx, visual);
   }

   /**
    * Get the specified visual object.
    */
   public Visualizable getVisual(int idx) {
      return visuals.get(idx);
   }

   /**
    * Get the number of visual objects defined in this graph.
    */
   public int getVisualCount() {
      return visuals.size();
   }

   /**
    * Remove the visual object at the specified position.
    */
   public synchronized void removeVisual(int idx) {
      visuals.remove(idx);
   }

   /**
    * Remove the visual object.
    */
   public synchronized void removeVisual(Visualizable visual) {
      visuals.remove(visual);
   }

   /**
    * Remove all visual objects.
    */
   public synchronized void removeAllVisuals() {
      visuals.clear();
   }

   /**
    * Get all visual objects in a stream.
    */
   public Stream<Visualizable> stream() {
      return visuals.stream();
   }

   @Override
   public void layoutCompleted() {
      super.layoutCompleted();
      this.visuals.trimToSize();
      this.visuals.stream().forEach(Visualizable::layoutCompleted);
   }

   /**
    * Make a copy of this object.
    */
   @Override
   public Object clone() {
      VContainer obj = (VContainer) super.clone();
      obj.visuals = new ArrayList<>();

      for(int i = 0; i < visuals.size(); i++) {
         obj.addVisual((Visualizable) getVisual(i).clone());
      }

      return obj;
   }

   ArrayList<Visualizable> visuals = new ArrayList<>();
}
