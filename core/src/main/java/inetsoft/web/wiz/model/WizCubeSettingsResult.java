/*
 * This file is part of StyleBI.
 * Copyright (C) 2026  InetSoft Technology
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

package inetsoft.web.wiz.model;

import java.util.List;

/**
 * The cube settings after an edit, read back from the saved domain: only the dimensions and levels
 * the request named.
 *
 * @param datasource the data source path.
 * @param cube       the cube name.
 * @param dimensions the hierarchies whose Keep Original Order was set.
 * @param levels     the levels whose date settings were set.
 */
public record WizCubeSettingsResult(String datasource, String cube, List<Dimension> dimensions,
                                    List<Level> levels)
{
   /**
    * @param dimension     the dimension name.
    * @param hierarchy     the hierarchy name (the tree entry's {@code entity}).
    * @param originalOrder Keep Original Order.
    */
   public record Dimension(String dimension, String hierarchy, boolean originalOrder) {
   }

   /**
    * @param dimension   the dimension name.
    * @param hierarchy   the hierarchy name.
    * @param level       the level name.
    * @param asDate      As Date.
    * @param locale      Database Locale, or null.
    * @param datePattern Parse Pattern, or null.
    */
   public record Level(String dimension, String hierarchy, String level, boolean asDate,
                       String locale, String datePattern)
   {
   }
}
