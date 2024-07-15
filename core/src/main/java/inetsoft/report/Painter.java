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
 * The Painter interface defines the API of a graphical representation 
 * of an object. A painter is a self contained object that is capable
 * of drawing its own graphical representation, and calculating its
 * own size. A Painter can be thought of a combination of a Presenter
 * and an object presented by the Presenter.
 *
 * @version 5.1, 9/20/2003
 * @author InetSoft Technology Corp
 */
public interface Painter extends java.io.Serializable {
   /**
    * Paint contents at the specified location.
    * @param g graphical context.
    * @param x x coordinate of the left edge of the paint area.
    * @param y y coordinate of the upper edge of the paint area.
    * @param w area width.
    * @param h area height.
    */
   public void paint(Graphics g, int x, int y, int w, int h);
   
   /**
    * Return the preferred size of this painter. If the width and height
    * are negative, the preferred size is specified as 1/1000 of the 
    * available page width (minus margins). For example, (-900, -500) 
    * generates a size of 90% of the page width as the preferred width, 
    * and 1/2 of the page width as the preferred height.
    * @return size.
    */
   public Dimension getPreferredSize();
   
   /**
    * If scalable is false, the painter is always sized to the preferred
    * size. If the size on the page is different from the preferred 
    * size, the painter image is scaled(by pixels) to fit the page area. 
    * If scalable is true, the painter will be printed in the actual size
    * on page, which may or may not be the same as the preferred size. 
    * The painter needs to check the width and height in the paint() 
    * method to know the actual size, and do the scaling by itself in paint().
    * @return scalable option.
    */
   public boolean isScalable();
}

