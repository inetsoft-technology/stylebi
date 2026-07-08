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
package inetsoft.uql.viewsheet.internal;

import inetsoft.sree.SreeEnv;
import inetsoft.uql.asset.internal.AssetUtil;

/**
 * Resolves the default row/header height for viewsheet data-surface assemblies from the
 * org-scoped modern-visualization density mode. Applied only where the assembly still carries
 * the legacy default; user-set heights always win and must be checked by the caller.
 *
 * The height matrix matches the browser-DOM density tokens in _viz-tokens.scss so the live
 * model, export, and non-assembly DOM surfaces agree. Dense equals AssetUtil.defh, so enabling
 * modern at the default mode reflows nothing.
 */
public final class VSDensityDefaults {
   private VSDensityDefaults() {
   }

   /**
    * Whether the modern-visualization gate is on for the current org.
    */
   public static boolean isModern() {
      return SreeEnv.getBooleanProperty("viewsheet.modernVisualization", false, true);
   }

   /**
    * The active density mode, defaulting to dense when unset. Only meaningful when the gate is on.
    */
   public static String mode() {
      String density = SreeEnv.getProperty("viewsheet.density", false, true);
      return density == null || density.isEmpty() ? DENSE : density;
   }

   /**
    * Default data-row height for the active mode, or the legacy default when the gate is off.
    */
   public static int rowHeight() {
      return isModern() ? rowHeightForMode(mode()) : AssetUtil.defh;
   }

   /**
    * Default header-row height for the active mode, or the legacy default when the gate is off.
    */
   public static int headerRowHeight() {
      return isModern() ? headerRowHeightForMode(mode()) : AssetUtil.defh;
   }

   /**
    * Default selection-list cell height for the active mode, or the legacy default when the gate
    * is off. Selection cells are a data surface, so they share the table row-height matrix.
    */
   public static int cellHeight() {
      return isModern() ? rowHeightForMode(mode()) : AssetUtil.defh;
   }

   /**
    * Data-row height for a density mode. Unrecognized modes fall back to dense.
    */
   static int rowHeightForMode(String mode) {
      switch(mode) {
      case COMFORTABLE:
         return 28;
      case COMPACT:
         return 24;
      default:
         return 20;
      }
   }

   /**
    * Header-row height for a density mode. Unrecognized modes fall back to dense.
    */
   static int headerRowHeightForMode(String mode) {
      switch(mode) {
      case COMFORTABLE:
         return 30;
      case COMPACT:
         return 26;
      default:
         return 22;
      }
   }

   private static final String COMFORTABLE = "comfortable";
   private static final String COMPACT = "compact";
   private static final String DENSE = "dense";
}
