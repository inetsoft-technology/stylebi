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

import inetsoft.graph.internal.GTransform;
import inetsoft.graph.internal.ILayout;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.geom.Rectangle2D;

/**
 * This class defines the common interface for all visualizable objects.
 *
 * @version 10.0
 * @author InetSoft Technology Corp
 */
public abstract class Visualizable implements ILayout, Comparable {
   /**
    * Set the transformation to map chart coordinate to screen coordinate.
    * The transformation is only applied to the positions and not the text.
    */
   public void setScreenTransform(AffineTransform trans) {
      if(trans == null) {
         trans = new GTransform();
      }
      else {
         // avoid rounding error
         trans = GTransform.normalize(trans);
      }

      this.scntrans = trans;
   }

   /**
    * Get the transformation to map chart coordinate to screen coordinate.
    */
   public AffineTransform getScreenTransform() {
      if(scntrans == null) {
         scntrans = new GTransform();
      }

      return scntrans;
   }

   /**
    * Gets the bounds of this visual in the form of a Rectangle2D object.
    * The bounds specify this visual's width, height, and location relative to
    * the whole graph.
    */
   public abstract Rectangle2D getBounds();

   /**
    * Get z-index property.
    */
   public int getZIndex() {
      return zIndex;
   }

   /**
    * The z-index property sets the stack order of a visual. A visual with
    * greater stack order is always in front of another visual with lower stack
    * order.
    */
   public void setZIndex(int zIndex) {
      this.zIndex = zIndex;
   }

   /**
    * Get the preferred width of this visualizable.
    */
   @Override
   public final double getPreferredWidth() {
      if(pwidth < 0) {
         pwidth = getPreferredWidth0();
      }

      return pwidth;
   }

   /**
    * Get the preferred height of this visualizable.
    */
   @Override
   public final double getPreferredHeight() {
      if(pheight < 0) {
         pheight = getPreferredHeight0();
      }

      return pheight;
   }

   /**
    * Get the minimum width of this visualizable.
    */
   @Override
   public final double getMinWidth() {
      if(mwidth < 0) {
         mwidth = getMinWidth0();
      }

      return mwidth;
   }

   /**
    * Get the minimum height of this visualizable.
    */
   @Override
   public final double getMinHeight() {
      if(mheight < 0) {
         mheight = getMinHeight0();
      }

      return mheight;
   }

   /**
    * Get the preferred width of this visualizable.
    */
   protected abstract double getPreferredWidth0();

   /**
    * Get the preferred height of this visualizable.
    */
   protected abstract double getPreferredHeight0();

   /**
    * Get the minimum width of this visualizable.
    */
   protected abstract double getMinWidth0();

   /**
    * Get the minimum height of this visualizable.
    */
   protected abstract double getMinHeight0();

   /**
    * Paint the visual object on the graphics.
    */
   public abstract void paint(Graphics2D g);

   /**
    * Get the graphable object that produced this visualizable.
    */
   public Graphable getGraphable() {
      return null;
   }

   /**
    * Make a copy of this object.
    */
   @Override
   public Object clone() {
      try {
         Visualizable obj = (Visualizable) super.clone();

         if(scntrans != null) {
            obj.scntrans = (AffineTransform) scntrans.clone();
         }

         return obj;
      }
      catch(Exception ex) {
         LOG.error("Failed to clone visualizable", ex);
         return null;
      }
   }

   /**
    * Clear cached information.
    */
   public void invalidate() {
      pwidth = -1d;
      pheight = -1d;
      mwidth = -1d;
      mheight = -1d;
   }

   /**
    * Compare with another visualizable according to the drawing order.
    */
   @Override
   public int compareTo(Object obj) {
      if(!(obj instanceof Visualizable)) {
         return 0;
      }

      return getZIndex() - ((Visualizable) obj).getZIndex();
   }

   /**
    * Return true if this object only paint within bounds of itself (not outside).
    */
   public boolean isPaintInBounds() {
      return true;
   }

   /**
    * This method is called after all layout/transformation has been completed. Implementation
    * can cleanup storage used for layout at this point.
    */
   public void layoutCompleted() {
      // GTransform keeps history for transformation and is not necessary after layout
      if(scntrans instanceof GTransform) {
         scntrans = new AffineTransform(scntrans);
      }
   }

   private AffineTransform scntrans = null; // screen transform
   private int zIndex = 0; // the layer index of this visualizable

   // cached value
   private double pwidth = -1d; // preferred width
   private double pheight = -1d; // preferred height
   private double mwidth = -1d;  // min width
   private double mheight = -1d;  // min height

   private static final Logger LOG = LoggerFactory.getLogger(Visualizable.class);
}
