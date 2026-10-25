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
package inetsoft.report.io.viewsheet;

import inetsoft.uql.viewsheet.internal.SelectionBaseVSAssemblyInfo;
import inetsoft.uql.viewsheet.internal.VSAssemblyInfo;

import java.awt.Insets;
import java.awt.geom.Rectangle2D;

/**
 * Exporting helper.
 *
 * @version 9.6
 * @author InetSoft Technology Corp
 */
public class ExporterHelper {
   /**
    * Get exporter.
    */
   public VSExporter getExporter() {
      return exporter;
   }

   /**
    * Set exporter.
    */
   public void setExporter(VSExporter exporter) {
      this.exporter = exporter;
   }

   /**
    * The rect the rows draw into: the assembly bounds less the card inset. The card itself - its
    * border, background and round corner - keeps the full bounds, which is the same split the
    * chart and table card use.
    */
   protected Rectangle2D getContentBounds(VSAssemblyInfo info, Rectangle2D bounds) {
      return getContentBounds(info, bounds, 1);
   }

   /**
    * The content rect for bounds in an output unit: the pixel inset is scaled by the coordinate
    * helper's scale, which is points per pixel in PowerPoint and 1 elsewhere.
    */
   protected Rectangle2D getContentBounds(VSAssemblyInfo info, Rectangle2D bounds, double scale) {
      Insets inset = getCardInset(info);
      double top = inset.top * scale;
      double left = inset.left * scale;
      double bottom = inset.bottom * scale;
      double right = inset.right * scale;

      // clamped: an inset larger than the assembly must not hand a painter a negative size
      return new Rectangle2D.Double(bounds.getX() + left, bounds.getY() + top,
                                    Math.max(0, bounds.getWidth() - left - right),
                                    Math.max(0, bounds.getHeight() - top - bottom));
   }

   /**
    * The card inset this format draws a selection's rows inside, or zero without an exporter.
    */
   protected Insets getCardInset(VSAssemblyInfo info) {
      return exporter == null ? new Insets(0, 0, 0, 0) : exporter.getSelectionCardInset(info);
   }

   /**
    * The padding a selection's title text takes, in pixels.
    */
   protected Insets getTitlePadding(SelectionBaseVSAssemblyInfo info) {
      return getTitlePadding(info, 1);
   }

   /**
    * The padding a selection's title text takes, in an output unit: the exporter's indent, scaled
    * like the card inset, else the title's own padding as set.
    */
   protected Insets getTitlePadding(SelectionBaseVSAssemblyInfo info, double scale) {
      int indent = exporter == null ? 0 : exporter.getSelectionTitleIndent(info);
      return indent > 0 ? new Insets(0, (int) Math.round(indent * scale), 0, 0) :
         info.getTitlePadding();
   }

   private VSExporter exporter;
}
