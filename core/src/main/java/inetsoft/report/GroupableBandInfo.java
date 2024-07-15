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
package inetsoft.report;

import java.util.List;

/**
 * GroupableBandInfo, defines all common functions for groupable element's
 * each band.
 *
 * @version 10.3
 * @author InetSoft Technology Corp
 */
public interface GroupableBandInfo {
   /**
    * Check if the band is visible.
    */
   boolean isVisible();

   /**
    * Get band type.
    */
   int getType();

   /**
    * Get band level.
    */
   int getLevel();

   /**
    * Get cell binding infos.
    * @param all false only return cells which on top-left of
    *  span or no span cells.
    */
   List<CellBindingInfo> getCellInfos(boolean all);

   /**
    * Get band object.
    */
   Object getBand();
}