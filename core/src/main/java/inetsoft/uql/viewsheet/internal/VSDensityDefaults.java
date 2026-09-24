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
import inetsoft.uql.viewsheet.ViewsheetInfo;

import java.awt.Insets;

/**
 * Resolves the default row/header/control height for viewsheet assemblies from the org-scoped
 * modern-visualization density mode. Applied only where the assembly still carries the legacy
 * default; user-set heights always win and must be checked by the caller.
 *
 * The height matrix matches the browser-DOM density tokens in _viz-tokens.scss so the live
 * model, export, and non-assembly DOM surfaces agree. Table row and header heights are STORED
 * values that the cell padding is added to at render (VSTableLens.getRowPadding), so it is the
 * sum that matches a token, not the matrix entry - see rowHeightForMode. Dense renders at
 * AssetUtil.defh for row and title height, so enabling modern at the default mode reflows
 * nothing for a data-surface type whose legacy default is AssetUtil.defh. A marked calendar is
 * the exception - its legacy title lane has always been taller, so it shrinks to the dense
 * height. Control height is the one exception to dense parity by design: a standalone form input
 * reads as cramped at the tightest data-row height, so it steps up even at dense (see
 * controlHeight()).
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
    * The density mode for one dashboard: its own value when set, else the org's. Never null, and
    * always one of the three valid modes.
    */
   public static String mode(ViewsheetInfo info) {
      String density = info == null ? null : info.getVizDensity();
      return normalizeMode(density == null || density.isEmpty() ? mode() : density);
   }

   /**
    * Clamp a density mode to a recognized value, falling back to dense. Single source of truth for
    * the valid modes, shared by the EM density control and the browser body-class whitelist.
    */
   public static String normalizeMode(String mode) {
      return isValidMode(mode) ? mode : DENSE;
   }

   /**
    * Whether this is one of the three recognized modes. For a writer that must reject an unknown
    * value rather than clamp it - normalizeMode's fallback to dense would pin a dashboard to a
    * tier nobody chose, where rejecting leaves it inheriting the org.
    */
   public static boolean isValidMode(String mode) {
      return COMFORTABLE.equals(mode) || COMPACT.equals(mode) || DENSE.equals(mode);
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
    * Default selection-list cell height. Selection cells are a data surface, but not a table one:
    * they have no additive cell padding, so they keep the matrix table rows used before the
    * padding was introduced rather than following rowHeightForMode down.
    */
   public static int cellHeight(VizContext ctx) {
      return ctx.modern ? selectionCellHeightForMode(ctx.density) : AssetUtil.defh;
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
    * tightest density, matching the browser's --inet-viz-control-height token. Applied at
    * creation and re-applied on a density change, as long as the stored height is still the
    * type's legacy default or a prior density tier - never to a control resized off those values.
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
    * STORED data-row height for a density mode - not the height a reader sees. The cell padding
    * is added on top (VSTableLens.getRowPadding), so stored + 2 * padding-y is what renders, and
    * that sum is the contract: 28 / 24 / 20. Unrecognized modes fall back to dense.
    */
   static int rowHeightForMode(String mode) {
      switch(mode) {
      case COMFORTABLE:
         return 16;
      case COMPACT:
         return 16;
      default:
         return 14;
      }
   }

   /**
    * STORED header-row height for a density mode, on the same terms as rowHeightForMode: the
    * rendered sum is 30 / 26 / 22.
    */
   static int headerRowHeightForMode(String mode) {
      switch(mode) {
      case COMFORTABLE:
         return 18;
      case COMPACT:
         return 18;
      default:
         return 16;
      }
   }

   /**
    * Selection-list cell height for a density mode. Deliberately a separate matrix from
    * rowHeightForMode, which it used to share: the two were only ever equal because no padding
    * sat between a table's stored row height and its rendered one. Do not re-merge them.
    */
   static int selectionCellHeightForMode(String mode) {
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

   /**
    * The chart's card inset for the context's mode, or the legacy inset when not modern. The
    * legacy branch matters as much as the modern one: Revert calls the seed with an unmarked
    * context and needs the legacy value written, not left alone.
    */
   public static Insets chartPadding(VizContext ctx) {
      return ctx.modern ? chartPaddingForMode(ctx.density) : new Insets(10, 10, 10, 10);
   }

   /**
    * A table's card inset for the context's mode. Legacy is zero on all four edges - a table has
    * never drawn a card inset, so that is the value Revert has to restore.
    *
    * The seed and the padding reset both read this, so the two cannot drift apart.
    */
   public static Insets tablePadding(VizContext ctx) {
      return ctx.modern ? tablePaddingForMode(ctx.density) : new Insets(0, 0, 0, 0);
   }

   /**
    * A table cell's content padding, or null when not modern. Null rather than a zero Insets
    * because null is what the cell pipeline already reads as "nothing defined here", falling
    * through to the 1px/2px in vs-table-cell.component.scss and to no inset at all in export.
    */
   public static Insets cellPadding(VizContext ctx) {
      return ctx.modern ? cellPaddingForMode(ctx.density) : null;
   }

   /**
    * Card inset for a density mode, uniform on all four edges. Unrecognized modes fall back to
    * dense. Compact holds the value the flat modern inset shipped at, so the org default mode
    * reflows nothing. A fresh Insets every call: the type is mutable.
    */
   static Insets chartPaddingForMode(String mode) {
      int inset;

      switch(mode) {
      case COMFORTABLE:
         inset = 16;
         break;
      case COMPACT:
         inset = 12;
         break;
      default:
         inset = 8;
      }

      return new Insets(inset, inset, inset, inset);
   }

   /**
    * A table's card inset for a density mode. Deliberately the chart's matrix rather than a
    * second one: a table card and a chart card beside it are the same object with different
    * contents, and two matrices would drift.
    */
   static Insets tablePaddingForMode(String mode) {
      return chartPaddingForMode(mode);
   }

   /**
    * Cell content padding for a density mode. The two axes carry different values because they
    * do different work - vertical sets the scan rhythm, horizontal the column rhythm.
    * Unrecognized modes fall back to dense. A fresh Insets every call.
    */
   static Insets cellPaddingForMode(String mode) {
      switch(mode) {
      case COMFORTABLE:
         return new Insets(6, 8, 6, 8);
      case COMPACT:
         return new Insets(4, 6, 4, 6);
      default:
         return new Insets(3, 4, 3, 4);
      }
   }

   private static final String COMFORTABLE = "comfortable";
   private static final String COMPACT = "compact";
   private static final String DENSE = "dense";
}
