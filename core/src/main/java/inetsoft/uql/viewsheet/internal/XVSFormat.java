/*
 * inetsoft-core - StyleBI is a business intelligence web application.
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
package inetsoft.uql.viewsheet.internal;

import inetsoft.report.internal.table.PresenterRef;
import inetsoft.uql.asset.AssetObject;
import inetsoft.uql.viewsheet.BorderColors;
import inetsoft.uql.viewsheet.GradientColor;

import java.awt.*;

/**
 * Format interface.
 *
 * @version 10.1
 * @author InetSoft Technology Corp
 */
public interface XVSFormat extends AssetObject {
   /**
    * Get the run time alignment (horizontal and vertical).
    * @return the alignment of this format.
    */
   public int getAlignment();

   /**
    * Get the design time alignment (horizontal and vertical).
    * @return the alignment of this format.
    */
   public int getAlignmentValue();

   /**
    * Get the background color.
    * @return the background of this format.
    */
   public Color getBackground();

   /**
    * Get the background value (expression or RGB number).
    * @return the background value of this format.
    */
   public String getBackgroundValue();

   /**
    * Get the GradientColor value.
    * @return the GradientColor value of this format.
    */
   public GradientColor getGradientColor();

   /**
    * Get the GradientColor value.
    * @return the GradientColor value of this format.
    */
   public GradientColor getGradientColorValue();

   /**
    * Get the border colors.
    * @return the border colors of this format.
    */
   public BorderColors getBorderColors();

   /**
    * Get the border colors value.
    * @return the border colors of this format.
    */
   public BorderColors getBorderColorsValue();

   /**
    * Get the borders.
    * @return the borders of this format.
    */
   public Insets getBorders();

   /**
    * Get the borders value.
    * @return the borders of this format.
    */
   public Insets getBordersValue();

   /**
    * Get the font.
    * @return the font of this format.
    */
   public Font getFont();

   /**
    * Get the font value.
    * @return the font of this format.
    */
   public Font getFontValue();

   /**
    * Get the foreground.
    * @return the foreground of this format.
    */
   public Color getForeground();

   /**
    * Get the foreground value (expression or RGB number).
    * @return the foreground value of this format.
    */
   public String getForegroundValue();

   /**
    * Get the format option.
    * @return the format option of this format.
    */
   public String getFormat();

   /**
    * Get the format option value.
    * @return the format option of this format.
    */
   public String getFormatValue();

   /**
    * Get the format extent (pattern or predefined extent type).
    * @return the format extent of this format.
    */
   public String getFormatExtent();

   /**
    * Get the format extent value(pattern or predefined extent type).
    * @return the format extent of this format.
    */
   public String getFormatExtentValue();

   /**
    * Get the cell span.
    */
   public Dimension getSpan();

   /**
    * Get the transparentcy.
    */
   public int getAlpha();

   /**
    * Get the transparentcy value.
    */
   public int getAlphaValue();

   /**
    * Check if should wrap text.
    * @return true if should wrap text, false otherwise.
    */
   public boolean isWrapping();

   /**
    * Check if should wrap text at design time.
    * @return true if should wrap text, false otherwise.
    */
   public boolean getWrappingValue();

   public PresenterRef getPresenter();

   public PresenterRef getPresenterValue();

   public int getRoundCorner();

   public int getRoundCornerValue();

   /**
    * Check if run time alignment is defined.
    */
   public boolean isAlignmentDefined();

   /**
    * Check if background color is defined.
    */
   public boolean isBackgroundDefined();

   /**
    * Check if GradientColor color is defined.
    */
   public boolean isGradientColorDefined();

   /**
    * Check if border colors is defined.
    */
   public boolean isBorderColorsDefined();

   /**
    * Check if borders is defined.
    */
   public boolean isBordersDefined();

   /**
    * Check if font is defined.
    */
   public boolean isFontDefined();

   /**
    * Check if foreground color is defined.
    */
   public boolean isForegroundDefined();

   /**
    * Check if text wrap is defined.
    */
   public boolean isWrappingDefined();

   /**
    * Check if alpha value is defined.
    */
   public boolean isAlphaValueDefined();

   /**
    * Check if background value is defined.
    */
   public boolean isBackgroundValueDefined();

   /**
    * Check if gradient color value is defined.
    */
   public boolean isGradientColorValueDefined();

   /**
    * Check if foreground value is defined.
    */
   public boolean isForegroundValueDefined();

   /**
    * Check if design time alignment is defined.
    */
   public boolean isAlignmentValueDefined();

   /**
    * Check if border colors value is defined.
    */
   public boolean isBorderColorsValueDefined();

   /**
    * Check if borders value is defined.
    */
   public boolean isBordersValueDefined();

   /**
    * Check if font value is defined.
    */
   public boolean isFontValueDefined();

   /**
    * Check if text wrap value is defined.
    */
   public boolean isWrappingValueDefined();

   /**
    * Check if alpha is defined.
    */
   public boolean isAlphaDefined();

   public boolean isPresenterDefined();

   public boolean isPresenterValueDefined();

   public boolean isRoundCornerDefined();

   public boolean isRoundCornerValueDefined();
}
