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

import inetsoft.uql.asset.internal.AssetUtil;
import inetsoft.uql.viewsheet.CurrentSelectionVSAssembly;
import inetsoft.uql.viewsheet.VSCompositeFormat;
import inetsoft.uql.viewsheet.internal.CurrentSelectionVSAssemblyInfo;
import inetsoft.util.Catalog;

import java.awt.*;
import java.awt.geom.Rectangle2D;

/**
 * Current selection helper.
 *
 * @version 10.1
 * @author InetSoft Technology Corp
 */
public abstract class VSCurrentSelectionHelper extends ExporterHelper {
   /**
    * Write the current selection to powerpoint.
    * @param assembly the current selection assembly.
    */
   public void write(CurrentSelectionVSAssembly assembly) {
      CurrentSelectionVSAssemblyInfo info =
         (CurrentSelectionVSAssemblyInfo) assembly.getVSAssemblyInfo();

      if(info == null) {
         return;
      }

      writeObjectBackground(info);

      if(info.isTitleVisible()) {
         writeTitle(info);
      }

      writeSelections(info);
   }

   /**
    * Write the object background.
    * @param info the current selection assembly info.
    */
   protected void writeObjectBackground(CurrentSelectionVSAssemblyInfo info) {
      // the implementation just used for PPT, PDF, the excel will override it
      Point cellpos = info.getViewsheet().getPixelPosition(info);
      Rectangle2D bounds = cHelper.createBounds(cellpos, info.getPixelSize());

      if(!info.isTitleVisible()) {
         Rectangle2D title = getTitleBounds(info);
         double y = bounds.getY() + title.getHeight();
         double h = bounds.getHeight() - title.getHeight();

         bounds.setRect(bounds.getX(), y, bounds.getWidth(), h);
      }

      VSCompositeFormat format = info.getFormat();
      drawTextBox("", bounds, format, null);
   }

   /**
    * Draw text box.
    */
   protected void drawTextBox(String value, Rectangle2D bounds,
                              VSCompositeFormat format, Insets padding)
   {
      // do nothing, PPT/PDF will override it
   }

   /**
    * Write out selection title.
    */
   protected void writeOutTitle(String title, String value, Rectangle2D bounds,
                                VSCompositeFormat format, double titleRatio,
                                Insets padding)
   {
      // do nothing, PPT/PDF will override it
   }

   /**
    * Write the title. There are two type of the title.
    * @param info the current selection assembly info.
    */
   protected abstract void writeTitle(CurrentSelectionVSAssemblyInfo info);

   /**
    * Write the current selections.
    */
   protected void writeSelections(CurrentSelectionVSAssemblyInfo info) {
      if(!info.isShowCurrentSelection()) {
         return;
      }

      // the implemention just used for PDF/PPT, excel will override it
      String[] titles = info.getOutSelectionTitles();
      String[] values = info.getOutSelectionValues();

      if(titles.length <= 0) {
         return;
      }

      Point position = info.getViewsheet().getPixelPosition(info);
      Dimension size = info.getPixelSize();
      Rectangle2D cbounds = cHelper.createBounds(position, size);

      size = new Dimension(size.width, AssetUtil.defh);
      Point startPos = info.getViewsheet().getPixelPosition(info);
      startPos = new Point(startPos.x, startPos.y + info.getTitleHeight());
      Rectangle2D tbounds = cHelper.createBounds(startPos, size);
      double currentY = tbounds.getY();
      double theight = tbounds.getHeight();
      VSCompositeFormat format = info.getFormat() == null ?
         new VSCompositeFormat() : info.getFormat().clone();
      format.getUserDefinedFormat().setWrapping(false);
      format.getUserDefinedFormat().setWrapping(false);

      for(int i = 0; i < titles.length; i++) {
         Rectangle2D bounds = new Rectangle2D.Double(cbounds.getX(), currentY,
            cbounds.getWidth(), theight);

         if(bounds.getY() + bounds.getHeight() / 2 >
            cbounds.getY() + cbounds.getHeight())
         {
            break;
         }

         double maxH = cbounds.getHeight() + cbounds.getY() - bounds.getY();
         maxH = Math.min(maxH, bounds.getHeight());
         bounds.setFrame(bounds.getX(), bounds.getY(), bounds.getWidth(), maxH);
         String title = titles[i];
         String value = values[i] == null ? catalog.getString("(none)") : values[i];

         writeOutTitle(title, value, bounds, format, info.getTitleRatio(),
                       info.getTitlePadding());
         currentY += theight;
      }
   }

   /**
    * Get the title bounds.
    */
   private Rectangle2D getTitleBounds(CurrentSelectionVSAssemblyInfo info) {
      Point pos = info.getViewsheet().getPixelPosition(info);
      Dimension size = info.getPixelSize();
      size = new Dimension(size.width, info.getTitleHeight());

      return cHelper.createBounds(pos, size);
   }

   protected Catalog catalog = Catalog.getCatalog();
   protected CoordinateHelper cHelper;
}
