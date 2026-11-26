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
 * The cubes of an XMLA data source that the caller may read.
 *
 * @param datasource the data source path.
 * @param cubes      the readable cubes, in the stored domain's order.
 * @param note       a hint when the list is empty (no cube metadata loaded yet), else null.
 */
public record WizCubeList(String datasource, List<Entry> cubes, String note) {
   /**
    * One cube.
    *
    * @param name           the cube name, the value to pass back as {@code cube}.
    * @param caption        the display caption.
    * @param type           the cube type (e.g. Mondrian).
    * @param dimensionCount the dimensions as the Composer tree lists them.
    * @param measureCount   the measures as the Composer tree lists them.
    */
   public record Entry(String name, String caption, String type, int dimensionCount,
                       int measureCount)
   {
   }
}
