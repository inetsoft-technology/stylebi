/*
 * inetsoft-web - StyleBI is a business intelligence web application.
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
export class VSWizardConstants {
   /**
    * temp chart name of component wizard.
    */
   public static readonly TEMP_CHART_NAME = "_temp_chart_";

   /**
    * temp crosstab name of component wizard.
    */
   public static readonly TEMP_CROSSTAB_NAME = "_temp_crosstab_";

   /**
    * temp primary assembly flag of component wizard.
    */
   public static readonly TEMP_ASSEMBLY = "Recommender";

   /**
    * temp primary assembly separator of component wizard.
    */
   public static readonly TEMP_ASSEMBLY_SEPARATOR = "__";

   /**
    * prefix of temp primary assembly name.
    */
   public static readonly TEMP_ASSEMBLY_PREFIX
      = VSWizardConstants.TEMP_ASSEMBLY + VSWizardConstants.TEMP_ASSEMBLY_SEPARATOR;

   /**
    * The initial grid row count in wizard grid pane.
    */
   public static readonly GRID_ROW = 24;

   /**
    * The initial grid column count in wizard grid pane.
    */
   public static readonly GRID_COLUMN = 64;

   /**
    * The grid cell height in wizard grid pane.
    */
   public static readonly GRID_CELL_HEIGHT = 20;

   /**
    * The grid cell width in wizard grid pane.
    */
   public static readonly GRID_CELL_WIDTH = 20;

   /**
    * Convert to measure flag.
    */
   public static readonly CONVERT_TO_MEASURE = 1;

   /**
    * Convert to dimension flag.
    */
   public static readonly CONVERT_TO_DIMENSION = 2;

   /**
    *  New object width
    */
   public static readonly  NEW_OBJECT_WIDTH = VSWizardConstants.GRID_CELL_WIDTH * 6;

   /**
    *  New object height
    */
   public static readonly  NEW_OBJECT_HEIGHT = VSWizardConstants.GRID_CELL_HEIGHT * 6;
}
