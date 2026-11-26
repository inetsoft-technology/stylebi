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

package inetsoft.web.wiz.request;

import java.util.List;

/**
 * A partial edit of the "OLAP Data" settings of one cube of an XMLA data source: the Portal's Keep
 * Original Order per dimension, and As Date, Database Locale and Parse Pattern per dimension level.
 * Anything not named is left as it is.
 *
 * @param path       the XMLA data source path.
 * @param cube       the cube name (or caption).
 * @param dimensions the dimensions whose Keep Original Order is set.
 * @param levels     the levels whose date settings are set.
 */
public record WizCubeSettingsRequest(String path, String cube, List<Dimension> dimensions,
                                     List<Level> levels)
{
   /**
    * One dimension's Keep Original Order.
    *
    * @param dimension         the dimension name as {@code get_cube_details} reports it.
    * @param hierarchy         one of its hierarchies, or null for every hierarchy of the dimension.
    * @param keepOriginalOrder the new value.
    */
   public record Dimension(String dimension, String hierarchy, Boolean keepOriginalOrder) {
   }

   /**
    * One level's date settings. A null field is left unchanged; an empty {@code locale} or
    * {@code datePattern} clears it.
    *
    * @param dimension   the dimension name.
    * @param hierarchy   the hierarchy holding the level, needed when the level name is not unique
    *                    within the dimension.
    * @param level       the level name.
    * @param asDate      parse the member captions as dates.
    * @param locale      the database locale used to parse them: a language code or
    *                    {@code language_COUNTRY}.
    * @param datePattern the pattern the captions are parsed with.
    */
   public record Level(String dimension, String hierarchy, String level, Boolean asDate,
                       String locale, String datePattern)
   {
   }
}
