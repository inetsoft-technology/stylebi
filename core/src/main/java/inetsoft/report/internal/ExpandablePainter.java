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

import inetsoft.report.Painter;
import inetsoft.report.ReportElement;

import java.awt.*;

/**
 * This interface defines the API for painters that can expand vertically.
 * If the content is wider than a page, the painter will be limited to the
 * page width and expanded vertically.
 *
 * @version 6.1, 9/20/2004
 * @author InetSoft Technology Corp
 */
public interface ExpandablePainter extends Painter {
   /**
    * Check if this painter is expandable. A painter may implement the 
    * ExpandablePainter interface but not expandable at rendering time.
    */
   public boolean isExpandable();
   
   /**
    * Calculate the preferred size of the object representation.
    * @param width the maximum width of the painter.
    * @return preferred size.
    */
   public Dimension getPreferredSize(float width);

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
   public void paint(Graphics g, int x, int y, int w, int h, 
                     float bufy, float bufh);

   /**
    * Get the adjustment on height if the height is adjusted to line boundary.
    */
   public float getHeightAdjustment(ReportElement elem, Dimension pd, 
                                    float bufy, float bufw, float bufh);
}

