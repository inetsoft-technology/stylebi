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
 * Resolves the default row/header/control height for viewsheet assemblies from the org-scoped
 * modern-visualization density mode. Applied only where the assembly still carries the legacy
 * default; user-set heights always win and must be checked by the caller.
 *
 * The height matrix matches the browser-DOM density tokens in _viz-tokens.scss so the live
 * model, export, and non-assembly DOM surfaces agree. Dense equals AssetUtil.defh for row/header/
 * title height, so enabling modern at the default mode reflows nothing for a data-surface type
 * whose legacy default is AssetUtil.defh. A marked calendar is the exception - its legacy title
 * lane has always been taller, so it shrinks to the dense height. Control height is the one
 * exception to dense parity by design: a standalone form input reads as cramped at the tightest
 * data-row height, so it steps up even at dense (see controlHeight()).
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
    * Whether dark mode is on for the current org. Dark is a modifier of modern: it requires the
    * master modern gate and recolors only surfaces that are already modern.
    */
   public static boolean isDark() {
      return isDark(isModern());
   }

   /**
    * Same as isDark(), for a caller that already has the modern flag and would otherwise read the
    * gate property a second time.
    */
   static boolean isDark(boolean modern) {
      return modern && SreeEnv.getBooleanProperty("viewsheet.darkMode", false, true);
   }

   /**
    * The active density mode, defaulting to the shipped default when unset. Meaningful for any marked
    * assembly, whatever the org gate says: the mark decides whether an assembly honours density, not
    * which density is in force. That is why the browser's density body class is unconditional.
    */
   public static String mode() {
      String density = SreeEnv.getProperty("viewsheet.density", false, true);
      return density == null || density.isEmpty() ? COMPACT : density;
   }

   /**
    * Clamp a density mode to a recognized value, falling back to dense. Single source of truth for
    * the valid modes, shared by the EM density control and the browser body-class whitelist.
    */
   public static String normalizeMode(String mode) {
      return COMFORTABLE.equals(mode) || COMPACT.equals(mode) || DENSE.equals(mode) ? mode : DENSE;
   }

   /**
    * Default data-row height for the context's mode, or the legacy default when it is not modern.
    */
   public static int rowHeight(VizContext ctx) {
      return ctx.modern ? rowHeightForMode(ctx.density) : AssetUtil.defh;
   }

   /**
    * Default header-row height for the context's mode, or the legacy default when not modern.
    */
   public static int headerRowHeight(VizContext ctx) {
      return ctx.modern ? headerRowHeightForMode(ctx.density) : AssetUtil.defh;
   }

   /**
    * Default selection-list cell height. Selection cells are a data surface, so they share the
    * table row-height matrix.
    */
   public static int cellHeight(VizContext ctx) {
      return ctx.modern ? rowHeightForMode(ctx.density) : AssetUtil.defh;
   }

   /**
    * The title lane's height. Compact and comfortable borrow the header row's steps so the lane can
    * hold the 24px anchored strip with clearance; dense stays at defh, which is the one tier that
    * must equal legacy and the one where the strip does not anchor at all.
    */
   public static int titleHeight(VizContext ctx) {
      return ctx.modern ? titleHeightForMode(ctx.density) : AssetUtil.defh;
   }

   /**
    * Default height for a modern form-input control (checkbox, combo box, spinner, text input),
    * or the legacy default when not modern. Unlike row/header height, dense does not equal
    * AssetUtil.defh here: a standalone control needs a bit more room than a data row even at the
    * tightest density, matching the browser's --inet-viz-control-height token. Applied only at
    * creation, to the type's own legacy default dimension - never to an author-resized control.
    */
   public static int controlHeight(VizContext ctx) {
      return ctx.modern ? controlHeightForMode(ctx.density) : AssetUtil.defh;
   }

   /**
    * Whether height matches one of the three density-derived control heights (24/28/30) at any
    * tier, regardless of the org's current density. Used to revert a modernized control's height
    * back to AssetUtil.defh when its mark is cleared - unlike round corner, a control's Dimension
    * has no separate user-override tier to fall back on, so this is a best-effort substitute: a
    * control an author manually resized to exactly one of these three pixel values is also reset.
    * Accepted because leaving every modernized control's height permanently changed on Revert is
    * worse than that narrow false positive.
    */
   public static boolean isControlHeight(int height) {
      return height == controlHeightForMode(DENSE) || height == controlHeightForMode(COMPACT) ||
         height == controlHeightForMode(COMFORTABLE);
   }

   /**
    * Title-lane height for one assembly: the density row when the assembly is marked, its author
    * has not set a height, and the stored height is still the type's pre-density default;
    * otherwise the stored height unchanged. The stored height is a parameter so a composer dialog
    * can pass its design-time value and still get the substitution.
    *
    * The three cheap tests run before the context is built - VizContext reads the density
    * property, and an unmarked or author-set assembly must not pay for that.
    */
   public static <T extends VSAssemblyInfo & TitledVSAssemblyInfo> int titleHeight(T info, int stored) {
      if(info.getVizMark() == null || info.isUserTitleHeight() ||
         stored != info.getLegacyTitleHeight())
      {
         return stored;
      }

      return titleHeight(VizContext.of(info));
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

   /**
    * Title-lane height for a density mode. Unrecognized modes fall back to dense.
    */
   static int titleHeightForMode(String mode) {
      switch(mode) {
      case COMFORTABLE:
         return 30;
      case COMPACT:
         return 26;
      default:
         return AssetUtil.defh;
      }
   }

   /**
    * Form-input control height for a density mode. Unrecognized modes fall back to dense.
    */
   static int controlHeightForMode(String mode) {
      switch(mode) {
      case COMFORTABLE:
         return 30;
      case COMPACT:
         return 28;
      default:
         return 24;
      }
   }

   private static final String COMFORTABLE = "comfortable";
   private static final String COMPACT = "compact";
   private static final String DENSE = "dense";
}
