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
package inetsoft.report.internal;

import inetsoft.report.*;
import inetsoft.util.OrderedMap;

import java.awt.*;
import java.awt.geom.Ellipse2D;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.util.*;

/**
 * LinkedShapePainterPaintable encapsulates the printing of a
 * LinkedShapePainter.
 *
 * @author InetSoft Technology
 * @since  8.5
 */
public abstract class LinkedShapePainterPaintable extends PainterPaintable {
   /**
    * Creates a new instance of LinkedShapePainterPaintable.
    *
    * @param element the element that this paintable is responsible for
    *                printing.
    */
   public LinkedShapePainterPaintable(ReportElement element) {
      super(element);
   }

   /**
    * Creates a new instance of LinkedShapePainterPaintable.
    *
    * @param x the x-coordinate of the paintable.
    * @param y the y-coordinate of the paintable.
    * @param painterW the width of the painter.
    * @param painterH the height of the painter.
    * @param pd the preferred size of the painter.
    * @param prefW the preferred width the paintable.
    * @param prefH the preferred height of the paintable.
    * @param elem the context report element.
    * @param painter the painter that renders the map.
    * @param offsetX the x-offset distance.
    * @param offsetY the y-offset distance.
    * @param rotation the rotation of the map.
    */
   public LinkedShapePainterPaintable(float x, float y, float painterW,
                                      float painterH, Dimension pd, int prefW,
                                      int prefH, ReportElement elem,
                                      LinkedShapePainter painter, int offsetX,
                                      int offsetY, int rotation)
   {
      super(x, y, painterW, painterH, pd, prefW, prefH, elem, painter, offsetX,
            offsetY, rotation);
   }

   /**
    * Get the hyper link on this element for the specified area.
    */
   public Hyperlink.Ref getHyperlink(Shape shape) {
      if(map == null) {
         map = new Hashtable();
      }

      return (Hyperlink.Ref) map.get(shape);
   }

   /**
    * Set the hyper link of this element for the specified area.
    */
   public void setHyperlink(Shape shape, Hyperlink.Ref link) {
      if(map == null) {
         map = new Hashtable();
      }

      if(link == null) {
         map.remove(shape);
      }
      else {
         map.put(shape, link);
      }
   }

   /**
    * Get the drill hyperlinks on this element for the specified area.
    */
   protected Hyperlink.Ref[] getDrillHyperlinks(Shape shape) {
      if(dmap == null) {
         dmap = new Hashtable();
      }

      Hyperlink.Ref[] refs = dmap.get(shape) == null ?
         new Hyperlink.Ref[0] : (Hyperlink.Ref[]) dmap.get(shape);

      return refs;
   }

   /**
    * Set the drill hyperlinks of this element for the specified area.
    */
   protected void setDrillHyperlinks(Shape shape, Hyperlink.Ref[] links) {
      if(shape == null) {
         return;
      }

      if(dmap == null) {
         dmap = new Hashtable();
      }

      if(links == null) {
         dmap.remove(shape);
      }
      else {
         dmap.put(shape, links);
      }
   }

   /**
    * Get all hyperlinks of this element for the specified area, including
    * hyperlink and drill hyperlinks.
    */
   public Hyperlink.Ref[] getHyperlinks(Shape shape) {
      if(dmap == null) {
         dmap = new Hashtable();
      }

      Hyperlink.Ref href = getHyperlink(shape);
      Hyperlink.Ref[] drefs = getDrillHyperlinks(shape);

      Map map = new OrderedMap();

      if(href != null) {
         map.put(href.getName(), href);
      }

      for(int i = 0; i < drefs.length; i++) {
         if(!map.containsKey(drefs[i].getName())) {
            map.put(drefs[i].getName(), drefs[i]);
         }
      }

      Hyperlink.Ref[] links = new Hyperlink.Ref[map.size()];
      map.values().toArray(links);

      return links;
   }

   /**
    * Get the all drill hyperlinks on this element for specified location.
    */
   @Override
   public Hyperlink.Ref[] getHyperlinks(Point loc) {
      Enumeration shapes = getHyperlinkAreas();

      while(shapes.hasMoreElements()) {
         Shape shape = (Shape) shapes.nextElement();

         if(shape.contains(loc.x, loc.y)) {
            return getHyperlinks(shape);
         }
      }

      return null;
   }

   /**
    * Return the areas that have a hyperlink defined. The hyperlink area is
    * similar to imagemap in HTML. Each sub-area in a painter/image can have
    * a different hyperlink.
    * @return enumeration of Shape objects.
    */
   @Override
   public Enumeration getHyperlinkAreas() {
      return new AreaEnumeration();
   }

   /**
    * Get the hyper link on this element for specified location.
    */
   @Override
   public Hyperlink.Ref getHyperlink(Point loc) {
      Enumeration shapes = getHyperlinkAreas();

      while(shapes.hasMoreElements()) {
         Shape shape = (Shape) shapes.nextElement();

         if(shape.contains(loc.x, loc.y)) {
            return getHyperlink(shape);
         }
      }

      return null;
   }

   /**
    *  Process hyperlink areas
    */
   protected void processHyperlinkAreas() {
      Enumeration shapes = ((PainterElement) elem).getHyperlinkAreas();

      while(shapes.hasMoreElements()) {
         Shape shape = (Shape) shapes.nextElement();

         setHyperlink(shape, new Hyperlink.Ref(((PainterElement) elem).getHyperlink(shape)));
      }
   }

   /**
    * Process Hyperlink.
    */
   @Override
   protected void processHyperlink() {
      Hyperlink link = ((PainterElement) elem).getHyperlink();

      if(link == null) {
         processHyperlinkAreas();
      }
   }

   /**
    * Read Object.
    */
   private void readObject(java.io.ObjectInputStream s)
      throws ClassNotFoundException, java.io.IOException {
      s.defaultReadObject();
      elem = (ReportElement) s.readObject();
      setElement(elem);
      Object obj = s.readObject();

      if(obj instanceof Integer) {
         map = new Hashtable();
         int linkcnt = ((Integer) obj).intValue();

         for(int i = 0; i < linkcnt; i++) {
            Object obj2 = s.readObject();

            if(obj2 instanceof String) {
               double x = s.readDouble();
               double y = s.readDouble();
               double w = s.readDouble();
               double h = s.readDouble();

               obj2 = new Ellipse2D.Double(x, y, w, h);
            }

           map.put(obj2, s.readObject());
         }
      }
   }

   /**
    * Write Object.
    */
   private void writeObject(ObjectOutputStream stream) throws IOException {
      if(map == null) {
         map = new Hashtable();
      }

      // @by jasons, if the background is transparent (null), we need to store
      // the background color of the parent section band for use later
      if(bg == null && (elem instanceof BaseElement)) {
         Object parent = ((BaseElement) elem).getParent();

         if(parent != null && parent instanceof SectionBand) {
            bg = ((SectionBand) parent).getBackground();
         }
      }

      stream.defaultWriteObject();
      stream.writeObject(elem);

      stream.writeObject(Integer.valueOf(map.size()));
      Enumeration keys = map.keys();

      while(keys.hasMoreElements()) {
         Object obj = keys.nextElement();

         if(obj instanceof Ellipse2D) {
            double x = 0, y = 0, w = 0, h = 0;

            if(obj instanceof Ellipse2D.Double) {
               Ellipse2D.Double e = (Ellipse2D.Double) obj;

               x = e.getX();
               y = e.getY();
               w = e.getWidth();
               h = e.getHeight();
            }
            else if(obj instanceof Ellipse2D.Float) {
               Ellipse2D.Float e = (Ellipse2D.Float) obj;

               x = (double) e.getX();
               y = (double) e.getY();
               w = (double) e.getWidth();
               h = (double) e.getHeight();
            }

            stream.writeObject("java.awt.geom.Ellipse2D");
            stream.writeDouble(x);
            stream.writeDouble(y);
            stream.writeDouble(w);
            stream.writeDouble(h);
         }
         else {
            stream.writeObject(obj);
         }

         stream.writeObject(map.get(obj));
      }
   }

   protected transient Hashtable map;
   protected transient Hashtable dmap;

   private class AreaEnumeration implements Enumeration {
      private AreaEnumeration() {
         if(map != null) {
            shapes.addAll(map.keySet());
         }

         if(dmap != null) {
            shapes.addAll(dmap.keySet());
         }
      }

      @Override
      public boolean hasMoreElements() {
         return idx < shapes.size();
      }

      @Override
      public Object nextElement() {
         return shapes.get(idx++);
      }

      private int idx = 0;
      ArrayList shapes = new ArrayList();
   }
}
