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
package inetsoft.report.painter;

import inetsoft.report.Presenter;
import inetsoft.report.ReportElement;
import inetsoft.report.internal.ExpandablePainter;
import inetsoft.report.internal.ExpandablePresenter;

import java.awt.*;

/**
 * The PersenterPainter combines a Presenter object with an object the
 * presenter presents into a self-contained painter object.
 *
 * @version 5.1, 9/20/2003
 * @author InetSoft Technology Corp
 */
public class PresenterPainter implements ExpandablePainter, Cloneable {
   /**
    * Create a painter from a presenter and a value it presents.
    * @param p presenter defaults how the value is painted.
    */
   public PresenterPainter(Presenter p) {
      this.presenter = p;
   }

   /**
    * Create a painter from a presenter and a value it presents.
    * @param v value to paint.
    * @param p presenter defaults how the value is painted.
    */
   public PresenterPainter(Object v, Presenter p) {
      this(p);
      setObject(v);
   }

   /**
    * Get the presenter used in this painter.
    */
   public Presenter getPresenter() {
      return presenter;
   }

   /**
    * Set the object to render in this painter.
    */
   public void setObject(Object v) {
      this.obj = v;
   }

   /**
    * Get the object to render in this painter.
    */
   public Object getObject() {
      return obj;
   }

   /**
    * Check if the presenter can handle this type of objects.
    * @param type object type.
    * @return true if the presenter can handle this type.
    */
   public boolean isPresenterOf(Class type) {
      return presenter.isPresenterOf(type);
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
      presenter.paint(g, obj, x, y, w, h);
   }

   /**
    * Paint contents at the specified location.
    * @param g graphical context.
    * @param x x coordinate of the left edge of the paint area.
    * @param y y coordinate of the upper edge of the paint area.
    * @param w area width.
    * @param h area height.
    * @param bufy if the painter is drawn across pages, bufy is the height
    * already consumed in previous pages.
    * @param bufh is the height available on the current page.
    */
   @Override
   public void paint(Graphics g, int x, int y, int w, int h,
                     float bufy, float bufh) {
      if(presenter instanceof ExpandablePresenter) {
         ((ExpandablePresenter) presenter).paint(g, obj, x, y, w, h,bufy,bufh);
      }
      else {
         presenter.paint(g, obj, x, y, w, h);
      }
   }

   /**
    * Get the adjustment on height if the height is adjusted to line boundary.
    */
   @Override
   public float getHeightAdjustment(ReportElement elem, Dimension pd,
                                    float bufy, float bufw, float bufh) {
      if(presenter instanceof ExpandablePresenter) {
         presenter.setFont(elem.getFont());
         return ((ExpandablePresenter) presenter).getHeightAdjustment(
            obj, elem, pd, bufy, bufw, bufh);
      }

      return 0;
   }

   /**
    * Return the preferred size of this painter.
    * @return size.
    */
   @Override
   public Dimension getPreferredSize() {
      return presenter.getPreferredSize(obj);
   }

   /**
    * Calculate the preferred size of the object representation.
    * @param width the maximum width of the painter.
    * @return preferred size.
    */
   @Override
   public Dimension getPreferredSize(float width) {
      if(presenter instanceof ExpandablePresenter) {
         return ((ExpandablePresenter) presenter).getPreferredSize(obj, width);
      }
      else {
         return presenter.getPreferredSize(obj);
      }
   }

   /**
    * Presenter can be scaled.
    */
   @Override
   public boolean isScalable() {
      return true;
   }

   /**
    * Check if this painter is expandable. A painter may implement the
    * ExpandablePainter interface but not expandable at rendering time.
    */
   @Override
   public boolean isExpandable() {
      return presenter instanceof ExpandablePresenter;
   }

   /**
    * Clone the painter.
    */
   @Override
   public Object clone() {
      return new PresenterPainter(obj, presenter);
   }

   /**
    * This is necessary if the presenter is used in the header row so the
    * original value would be returned as a string.
    */
   public String toString() {
      if(obj != null) {
         if(obj instanceof Object[]) {
            return inetsoft.util.Tool.arrayToString(obj);
         }

         return obj.toString();
      }

      return super.toString();
   }

   /**
    * Get hash code of the presenter painter.
    */
   public int hashCode() {
      // @by billh, presenter painter will be used as key when a table header
      // is a presenter painter, as presenter painter is mutable, here we use
      // its value's hash code if possible
      if(obj != null) {
         return obj.hashCode();
      }

      return super.hashCode();
   }

   /**
    * Check if equals another object
    */
   public boolean equals(Object obj) {
      // @by billh, presenter painter will be used as key when a table header
      // is a presenter painter, as presenter painter is mutable, here we
      // compare there values if possible
      if(!(obj instanceof PresenterPainter)) {
         return false;
      }

      PresenterPainter p2 = (PresenterPainter) obj;

      if(this.obj != null) {
         return this.obj.equals(p2.obj);
      }

      return super.equals(obj);
   }

   /**
    * Presenter can be scaled when write image on the portal side.
    */
   public boolean isScaleImage() {
      return presenter instanceof HeaderPresenter ||
         presenter instanceof Bar2Presenter ||
         presenter instanceof BarPresenter ||
         presenter instanceof ButtonPresenter ||
         presenter instanceof ShadowPresenter ||
         presenter instanceof BulletGraphPresenter;
   }

   /**
    * Presenter can be exported as value in excel
    */
   public boolean isExportedValue() {
      return //presenter instanceof RotatePresenter ||
             presenter instanceof HTMLPresenter ||
             presenter instanceof HeaderPresenter && !isMultiHeader() ||
             presenter instanceof ButtonPresenter ||
             presenter instanceof ShadowPresenter;

   }

   private boolean isMultiHeader() {
      if(obj instanceof Object[]) {
         Object[] values = (Object[]) obj;

         return values != null && values.length > 1;
      }

      return false;
   }

   private Presenter presenter;
   private Object obj;
}
