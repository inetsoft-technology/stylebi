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
package inetsoft.report.internal;

import inetsoft.report.Presenter;
import inetsoft.report.ReportElement;

import java.awt.*;

/**
 * This interface defines the API for presenters that can expand vertically.
 * If the content is wider than a page, the presenter will be limited to the
 * page width and expanded vertically.
 *
 * @version 6.1, 9/20/2004
 * @author InetSoft Technology Corp
 */
public interface ExpandablePresenter extends Presenter {
   /**
    * Calculate the preferred size of the object representation.
    * @param v object value.
    * @param width the maximum width of the presenter.
    * @return preferred size.
    */
   public Dimension getPreferredSize(Object v, float width);

   /**
    * Paint an object at the specified location.
    * @param g graphical context.
    * @param v object value.
    * @param x x coordinate of the left edge of the paint area.
    * @param y y coordinate of the upper edge of the paint area.
    * @param w area width.
    * @param h area height.
    * @param bufy if the painter is drawn across pages, bufy is the height
    * already consumed in previous pages.
    * @param bufh is the height available on the current page.
    */
   public void paint(Graphics g, Object v, int x, int y, int w, int h,
                     float bufy, float bufh);

   /**
    * Get the adjustment on height if the height is adjusted to line boundary.
    */
   public float getHeightAdjustment(Object obj, ReportElement elem, 
                                    Dimension pd, float bufy, float bufw,
                                    float bufh);
}

