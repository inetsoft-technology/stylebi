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
 * One cube of an XMLA data source: dimensions, their hierarchies and levels, and the measures.
 *
 * <p>Names are the ones the Composer data tree and the binding tree carry: a hierarchy's
 * {@code entity} and a level's {@code name} are the {@code entity} and {@code attribute} of the
 * tree's column entry, a measure's {@code name} is its {@code attribute}.</p>
 *
 * @param datasource the data source path.
 * @param name       the cube name.
 * @param caption    the cube caption.
 * @param type       the cube type.
 * @param dimensions the dimensions, grouped as the Composer tree groups them.
 * @param measures   the measures.
 */
public record WizCubeDetails(String datasource, String name, String caption, String type,
                             List<Dimension> dimensions, List<Measure> measures)
{
   /**
    * A dimension (a Composer tree folder), holding one or more hierarchies.
    *
    * @param name          the dimension name.
    * @param caption       the dimension caption.
    * @param time          whether it is a time dimension.
    * @param originalOrder whether members keep the cube's original order.
    * @param hierarchies   the hierarchies; a dimension without hierarchies has exactly one.
    */
   public record Dimension(String name, String caption, boolean time, boolean originalOrder,
                           List<Hierarchy> hierarchies)
   {
   }

   /**
    * A hierarchy, from the top level down.
    *
    * @param entity      the tree entry's {@code entity} of every level of this hierarchy.
    * @param caption     the hierarchy caption.
    * @param userDefined whether the hierarchy is user defined in the cube.
    * @param originalOrder whether this hierarchy keeps the cube's original member order (the
    *                    Portal's "Keep Original Order").
    * @param levels      the levels, in level order.
    */
   public record Hierarchy(String entity, String caption, boolean userDefined,
                           boolean originalOrder, List<Level> levels)
   {
   }

   /**
    * A dimension level.
    *
    * @param name        the level name (the tree entry's {@code attribute}).
    * @param caption     the level caption.
    * @param dtype       the level data type.
    * @param levelNumber the zero-based position in the hierarchy.
    * @param asDate      the Portal's "As Date": the member captions are parsed as dates.
    * @param locale      the Portal's "Database Locale" used to parse them, or null.
    * @param datePattern the Portal's "Parse Pattern", or null.
    */
   public record Level(String name, String caption, String dtype, int levelNumber, boolean asDate,
                       String locale, String datePattern)
   {
   }

   /**
    * A measure.
    *
    * @param name       the measure name (the tree entry's {@code attribute}).
    * @param caption    the measure caption.
    * @param dtype      the measure data type.
    * @param calculated whether it is a calculated measure of the cube.
    * @param folder     the display folder, or null.
    */
   public record Measure(String name, String caption, String dtype, boolean calculated,
                         String folder)
   {
   }
}
