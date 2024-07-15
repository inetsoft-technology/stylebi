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
package inetsoft.web.viewsheet.event;

/**
 * Class that encapsulates the parameters for opening a preview viewsheet.
 *
 * @since 12.3
 */
public class OpenPreviewViewsheetEvent extends OpenViewsheetEvent {
   /**
    * Gets the name of the current layout ot preview, if available.
    *
    * @return the name of the layout.
    */
   public String getLayoutName() {
      return layoutName;
   }

   /**
    * Sets the name of the current layout ot preview, if available.
    *
    * @param layoutName the name of the layout.
    */
   public void setLayoutName(String layoutName) {
      this.layoutName = layoutName;
   }

   @Override
   public String toString() {
      return "OpenPreviewViewsheetEvent{" +
         "layoutName='" + layoutName + '\'' +
         '}';
   }

   private String layoutName;
}
