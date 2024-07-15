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
package inetsoft.report;

import java.awt.*;

/**
 * Presenter interface defines the API for rendering an object into
 * a graphical presentation. The two main function of a presenter
 * is to calculate the size requirement of an object, and draw
 * a graphical representation of the object. A Presenter can be
 * registered for a class of objects, by calling the
 * ReportSheet.addPresenter() function. Or a Presenter can be register
 * with a column in a table, by calling the setPresenter() method
 * on a table lens (table lens derived from AttributeTableLens).
 * <p>
 * If a presenter is found for an object, the presenter is used
 * in rendering the object during printing.
 *
 * @version 5.1, 9/20/2003
 * @author InetSoft Technology Corp
 */
public interface Presenter extends java.io.Serializable {
   /**
    * Paint an object at the specified location.
    * @param g graphical context.
    * @param v object value.
    * @param x x coordinate of the left edge of the paint area.
    * @param y y coordinate of the upper edge of the paint area.
    * @param w area width.
    * @param h area height.
    */
   public void paint(Graphics g, Object v, int x, int y, int w, int h);

   /**
    * Calculate the preferred size of the object representation.
    * @param v object value.
    * @return preferred size.
    */
   public Dimension getPreferredSize(Object v);

   /**
    * Check if the presenter can handle this type of objects.
    * @param type object type.
    * @return true if the presenter can handle this type.
    */
   public boolean isPresenterOf(Class type);

   /**
    * Check if the presenter can handle this particular object. Normally
    * a presenter handles a class of objects, which is checked by the
    * isPresenterOf(Class) method. If this presenter does not care about
    * the value in the object, it can just call the isPresenterOf() with
    * the class of the object, e.g.<pre>
    *   if(type == null) {
    *      return false;
    *   }
    *   return isPresenterOf(obj.getClass());
    * </pre>
    * @param obj object type.
    * @return true if the presenter can handle this type.
    */
   public boolean isPresenterOf(Object obj);

   /**
    * Check if this presenter should always fill the entire area of a cell.
    */
   public boolean isFill();

   /**
    * Set the font to use for this presenter. A table calls this function
    * before the cell is printed when a presenter is used.
    */
   public void setFont(Font font);

   /**
    * Set the background to use for this presenter. A table calls this function
    * before the cell is printed when a presenter is used.
    */
   public void setBackground(Color bg);

   /**
    * Get the display name of this presenter.
    *
    * @return a user-friendly name for this presenter.
    *
    * @since 5.1
    */
   public String getDisplayName();

   /**
    * Determine if this Presenter requires raw (unformatted) data.
    *
    * @return <code>true</code> if the presenter requires raw data.
    */
   public boolean isRawDataRequired();

   /**
    * Set the calculated alignment offset for the presenter
    *
    * @param offset the amount of offset
    */
   default void setAlignmentOffset(Dimension offset) {
      // no-op
   }
}

