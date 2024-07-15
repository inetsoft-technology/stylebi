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
package inetsoft.report.io.viewsheet.svg;

import inetsoft.report.StyleConstants;
import inetsoft.report.TableDataPath;
import inetsoft.report.io.viewsheet.*;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.internal.SelectionListVSAssemblyInfo;
import inetsoft.util.Tool;

import java.awt.*;
import java.awt.geom.Rectangle2D;
import java.util.List;

/**
 * Selection list helper used when exporting to SVG.
 */
public class SVGSelectionListHelper extends VSSelectionListHelper {
   /**
    * Creates a new instance of <tt>SVGSelectionListHelper</tt>.
    *
    * @param helper the coordinate helper
    */
   public SVGSelectionListHelper(SVGCoordinateHelper helper) {
      this.cHelper = helper;
   }

   @Override
   protected void writeTitleInContainer(SelectionListVSAssembly assembly,
                                        VSCompositeFormat format, String title,
                                        double titleRatio, Insets padding)
   {
      Rectangle2D bounds = cHelper.getBounds(assembly, CoordinateHelper.TITLE);

      if(bounds == null) {
         return;
      }

      ((SVGCoordinateHelper) cHelper).drawTextBox(
         bounds, bounds, format, ExportUtil.getTextInTitleCell(
         Tool.localize(assembly.getTitle()), title, (int) bounds.getWidth(),
         format.getFont(), titleRatio), true, padding);
   }

   @Override
   protected void writeObjectBackground(SelectionListVSAssembly assembly,
                                        VSCompositeFormat format) {
      Rectangle2D bounds = cHelper.getBounds(assembly, CoordinateHelper.ALL);
      SelectionListVSAssemblyInfo info =
         (SelectionListVSAssemblyInfo) assembly.getVSAssemblyInfo();
      boolean incs =
         assembly.getContainer() instanceof CurrentSelectionVSAssembly;

      if(!info.isTitleVisible()) {
         Rectangle2D title =
            cHelper.getBounds(assembly, CoordinateHelper.TITLE);
         double y = bounds.getY() + (incs ? 0 : title.getHeight());
         double h = bounds.getHeight() - title.getHeight();

         bounds.setRect(bounds.getX(), y, bounds.getWidth(), h);
      }

      ((SVGCoordinateHelper) cHelper).drawTextBox(bounds, format, "");
   }

   @Override
   protected void writeTitle(SelectionListVSAssemblyInfo info, List<SelectionValue> values) {
      Rectangle2D bounds;

      // set the default selection list's border.
      bounds = cHelper.getBounds(info);
      bounds.setFrame(bounds.getX(), bounds.getY(), bounds.getWidth(),
                      totalHeight[0]);

      VSCompositeFormat format = info.getFormat();
      ((SVGCoordinateHelper) cHelper).drawTextBox(bounds, format, null, false);

      bounds = (Rectangle2D) boundsList.get(0);

      String title = Tool.localize(info.getTitle());
      FormatInfo finfo = info.getFormatInfo();

      if(finfo != null) {
         format = finfo.getFormat(
            new TableDataPath(-1, TableDataPath.TITLE), false);
      }

      bounds.setFrame(bounds.getX(), bounds.getY(), bounds.getWidth(),
         !info.isTitleVisible() ? 0 : info.getTitleHeight());

      ((SVGCoordinateHelper) cHelper).drawTextBox(
         bounds, bounds, format, title,
         SVGVSExporter.isBackgroundNeed(format, info.getFormat()), info.getTitlePadding());
   }

   @Override
   protected void writeList(SelectionListVSAssembly assembly, List<SelectionValue> values) {
      SelectionListVSAssemblyInfo info =
         (SelectionListVSAssemblyInfo) assembly.getVSAssemblyInfo();
      SelectionValue value;
      String valueLabel;
      double height = getInvisibleTitleHeight(assembly);

      VSCompositeFormat pFormat = info.getFormat() == null ?
         new VSCompositeFormat() : (VSCompositeFormat) info.getFormat().clone();
      Insets pBorders = pFormat.getBorders();

      if(pBorders != null) {
         pBorders = (Insets) pBorders.clone();
      }
      else {
         pBorders = new Insets(0, 0, 0, 0);
      }

      Rectangle2D.Double border = new Rectangle2D.Double(-1, -1, -1, -1);

      for(Object boundsObject : boundsList) {
         Rectangle2D bounds = (Rectangle2D) boundsObject;
         border.x = border.x == -1 || border.x > bounds.getX()
            ? bounds.getX() : border.x;
         border.y = border.y == -1 || border.y > bounds.getY()
            ? bounds.getY() : border.y;
         border.width = border.width == -1 || border.width < bounds.getX()
            ? bounds.getX() : border.x;
         border.height = border.height == -1 || border.height < bounds.getY()
            ? bounds.getY() : border.y;
      }

      CompositeSelectionValue root = new CompositeSelectionValue();
      root.setSelectionList(info.getSelectionList());
      boolean hasSelected =
         root.getSelectionValues(-1, SelectionValue.STATE_SELECTED, 0).size() > 0;

      for(int i = 0; i < values.size() && i < boundsList.size() - 1; i++) {
         valueLabel = "";
         value = values.get(i);
         VSCompositeFormat vsformat = value == null ? null : value.getFormat();
         VSCompositeFormat format = (vsformat == null) ?
            new VSCompositeFormat() : vsformat.clone();

         format = VSSelectionListHelper.getValueFormat(value, format, hasSelected);

         if(i >= boundsList.size() - 2 && i < values.size() - 1) {
            // last cell but still got more elements
            valueLabel = catalog.getString("More") + "...";
         }
         else if(value != null) {
            valueLabel = value.getLabel();
         }

         Rectangle2D bounds = (Rectangle2D) boundsList.get(i + 1);

         bounds.setFrame(bounds.getX(), bounds.getY() - height,
                         bounds.getWidth(), bounds.getHeight());

         // merge the obj border and cell's border
         if((format.getBorders() == null || format.getBorderColors() == null)) {
            pBorders.top = 0;
            pBorders.bottom = 0;

            if(bounds.getX() != border.x) {
               pBorders.left = 0;
            }

            if(bounds.getX() != border.width) {
               pBorders.right = 0;
            }

            format.getUserDefinedFormat().setBorders(pBorders);
         }

         if(format.getAlignment() == StyleConstants.NONE) {
            format = (VSCompositeFormat) format.clone();
            format.getUserDefinedFormat().setAlignment(StyleConstants.LEFT);
         }

         double topBorder = ((Rectangle2D) boundsList.get(0)).getY();

         if(bounds.getY() + bounds.getHeight() - topBorder > totalHeight[0]) {
            bounds.setFrame(bounds.getX(), bounds.getY(), bounds.getWidth(),
                            topBorder + totalHeight[0] - bounds.getY());
         }

         ((SVGCoordinateHelper) cHelper).drawTextBox(bounds,
            getValueLabelBounds(info, bounds), format, valueLabel,
            SVGVSExporter.isBackgroundNeed(format, pFormat), info.getCellPadding());

         paintMeasure(value, bounds, info.getSelectionList(), info);
      }
   }

   @Override
   protected void writePicture(Image img, Rectangle2D bounds) {
      ((SVGCoordinateHelper) cHelper).drawImage(img, bounds);
   }

   @Override
   protected void writeText(String txt, Rectangle2D bounds,
                            VSCompositeFormat format)
   {
      ((SVGCoordinateHelper) cHelper).drawTextBox(bounds, format, txt);
   }
}
