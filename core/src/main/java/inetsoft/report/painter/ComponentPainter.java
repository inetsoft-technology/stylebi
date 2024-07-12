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
package inetsoft.report.painter;

import inetsoft.report.Painter;

import java.awt.*;

/**
 * The ComponentPainter wraps around an AWT Component and provides a
 * Painter object that is suitable to be used in the ReportSheet. By
 * default, the painter calls Component.paintAll() for lightweight
 * component to print itself, and calls Component.printAll() for
 * heavyweight component to print itself. However, whether these
 * methods work is dependent on the implementation of the components.
 * If the default method does not work for a component, applications
 * can choose a different printing method by supplying a printing
 * method option. The option specifies always using printAll(),
 * paintAll(), print(), or paint() to paint a component.
 *
 * @version 5.1, 9/20/2003
 * @author InetSoft Technology Corp
 */
public class ComponentPainter implements Painter {
   /**
    * Use default printing method.
    */
   public static final int DEFAULT = 0;
   /**
    * Use Component.printAll() to print component.
    */
   public static final int PRINTALL = 1;
   /**
    * Use Component.paintAll() to print component.
    */
   public static final int PAINTALL = 2;
   /**
    * Use Component.print() to print component.
    */
   public static final int PRINT = 3;
   /**
    * Use Component.paint() to print component.
    */
   public static final int PAINT = 4;
   /**
    * Use Component.paint() to print component.
    */
   public static final int UPDATE = 5;
   /**
    * Create a painter from a component.
    * @param comp component to paint.
    */
   public ComponentPainter(Component comp) {
      this.comp = comp;
   }

   /**
    * Create a painter from a component.
    * @param comp component to paint.
    * @param opt printing method.
    */
   public ComponentPainter(Component comp, int opt) {
      this.comp = comp;
      this.opt = opt;
   }

   /**
    * Return the preferred size of this painter.
    * @return size.
    */
   @Override
   public Dimension getPreferredSize() {
      Dimension d = comp.getSize();

      if(d.width > 0 && d.height > 0) {
         return d;
      }

      return comp.getPreferredSize();
   }

   /**
    * Paint contents at the specified location.
    * @param g graphical context.
    * @param x x coordinate of the left edge of the paint area.
    * @param y y coordinate of the upper edge of the paint area.
    * @param w area width.
    * @param h area height.
    */
   @Override
   public void paint(Graphics g, int x, int y, int w, int h) {
      Shape clip = g.getClip();

      g.translate(x, y);
      // jdk 1.2 bug, if we clip at 0,0, the region cuts off one pixel
      // on the top after rotate (RotatedPainter)
      g.clipRect(-1, -1, w, h);

      switch(opt) {
      case DEFAULT:
         try {
            comp.printAll(g); // jdk1.2 fails for image buffer
         }
         catch(Exception e) {
            comp.update(g);
         }

         break;
      case PRINTALL:
         comp.printAll(g);
         break;
      case PAINTALL:
         comp.paintAll(g);
         break;
      case PRINT:
         comp.print(g);
         break;
      case PAINT:
         comp.paint(g);
         break;
      case UPDATE:
         try {
            comp.paintAll(g);
         }
         catch(Exception e) {
            comp.update(g);
         }

         try {
            comp.printAll(g);
         }
         catch(Exception e) {
            comp.update(g);
         }
      }

      g.translate(-x, -y);
      g.setClip(clip);
   }

   /**
    * The component can be scaled.
    */
   @Override
   public boolean isScalable() {
      return false;
   }

   /**
    * Get the component instance.
    * @return component.
    */
   public Component getComponent() {
      return comp;
   }

   /**
    * Set the background.
    */
   public void setBackground(Color bg) {
      this.bg = bg;
   }

   /**
    * Get the background.
    */
   public Color getBackground() {
      return bg;
   }

   private Color bg = null;
   private Component comp;
   private int opt = DEFAULT;
}

