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
package inetsoft.web.vswizard.model;

public final class VSWizardConstants {
   /**
    * none str.
    */
   public static final String NONE = "none";

   /**
    * temp chart name of component wizard.
    */
   public static final String TEMP_CHART_NAME = "_temp_chart_";

   /**
    * temp crosstab name of component wizard.
    */
   public static final String TEMP_CROSSTAB_NAME = "_temp_crosstab_";

   /**
    * temp primary assembly separator of component wizard.
    */
   public static final String TEMP_ASSEMBLY_SEPARATOR = "__";

   /**
    * temp assembly expired time.
    */
   public static final long TEMP_ASSEMBLY_EXPIRED_TIME = 30000L;

   /**
    * prefix of temp primary assembly name.
    */
   public static final String TEMP_ASSEMBLY_PREFIX = "Recommender" +
      TEMP_ASSEMBLY_SEPARATOR;

   /**
    * The initial grid row count in wizard grid pane.
    */
   public static final int GRID_ROW = 24;

   /**
    * The initial grid column count in wizard grid pane.
    */
   public static final int GRID_COLUMN = 64;

   /**
    * The grid cell height in wizard grid pane.
    */
   public static final int GRID_CELL_HEIGHT = 20;

   /**
    * The grid cell width in wizard grid pane.
    */
   public static final int GRID_CELL_WIDTH = 20;

   /**
    * The column count of new object component in grid pane.
    */
   public static final int NEW_BLOCK_COLUMN_COUNT = 6;

   /**
    * The row count of new object component in grid pane.
    */
   public static final int NEW_BLOCK_ROW_COUNT = 6;

   /**
    * The row count of row padding.
    */
   public static final int GRID_ASSEMBLIES_ROW_PADDING = 1;

   /**
    * The col count of col padding.
    */
   public static final int GRID_ASSEMBLIES_COL_PADDING = 1;
    /**
     * Convert to measure.
     */
    public static final int CONVERT_TO_MEASURE = 1;
    /**
     * Convert to dimension.
     */
    public static final int CONVERT_TO_DIMENSION = 2;
}
