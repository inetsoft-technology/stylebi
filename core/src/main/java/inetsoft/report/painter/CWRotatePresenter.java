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

import inetsoft.util.Catalog;

/**
 * This presenter rotates text 90 degrees, clockwise in a table cell.
 * @author InetSoft Technology Corp.
 * @since 4.5
 */
public class CWRotatePresenter extends RotatePresenter {
   /**
    * Get the angle to rotate the text.
    * @return 90.
    */
   @Override
   int getAngle() {
      return 90;
   }

   /**
    * Get the display name of this presenter.
    *
    * @return a user-friendly name for this presenter.
    *
    * @since 5.1
    */
   @Override
   public String getDisplayName() {
      return Catalog.getCatalog().getString("Clockwise Rotation");
   }
}

