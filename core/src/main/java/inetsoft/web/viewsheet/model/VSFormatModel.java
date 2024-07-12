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
package inetsoft.web.viewsheet.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import inetsoft.analytic.composition.VSCSSUtil;
import inetsoft.graph.internal.GTool;
import inetsoft.report.StyleFont;
import inetsoft.report.internal.Common;
import inetsoft.report.internal.Util;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.internal.*;
import inetsoft.util.Tool;
import inetsoft.util.css.CSSConstants;
import inetsoft.web.binding.model.BaseFormatModel;

import java.awt.*;
import java.util.Objects;

@JsonIgnoreProperties(ignoreUnknown = true)
public class VSFormatModel extends BaseFormatModel {
   public VSFormatModel() {
   }

   public VSFormatModel(XVSFormat compositeFormat, VSAssemblyInfo assemblyInfo) {
      this(compositeFormat, assemblyInfo, false);
   }

   public VSFormatModel(XVSFormat compositeFormat, VSAssemblyInfo assemblyInfo,
                        boolean scaleFont)
   {
      if(compositeFormat != null && assemblyInfo != null) {
         VSCompositeFormat objFmt = assemblyInfo.getFormat();
         String background2 = VSCSSUtil.getBackgroundRGBA(objFmt);

         alpha = VSCSSUtil.getAlpha(compositeFormat);
         roundCorner = compositeFormat.getRoundCorner();
         foreground = VSCSSUtil.getForeground(compositeFormat);
         background = VSCSSUtil.getBackgroundRGBA(compositeFormat);
         vAlign = VSCSSUtil.getvAlign(compositeFormat);
         alignItems = VSCSSUtil.getFlexAlignment(vAlign);
         hAlign = VSCSSUtil.gethAlign(compositeFormat);
         justifyContent = VSCSSUtil.getFlexAlignment(hAlign);
         setGradientColor(compositeFormat.getGradientColor());
         Font baseFont = compositeFormat.getFont();

         if(baseFont == null) {
            baseFont = Util.DEFAULT_FONT;
         }

         if(scaleFont) {
            double fontScale = getFontScale(assemblyInfo, baseFont);

            if(fontScale != 1.0) {
               float newSize = Math.max(10f, (float) (fontScale * baseFont.getSize()));
               Font scaled = baseFont.deriveFont(newSize);
               font = VSCSSUtil.getFont(scaled);

               if(compositeFormat.isWrapping()) {
                  lineHeight = Common.getHeight(scaled);
               }
            }
            else {
               font = VSCSSUtil.getFont(baseFont);

               if(compositeFormat.isWrapping()) {
                  lineHeight = Common.getHeight(baseFont);
               }
            }
         }
         else {
            font = VSCSSUtil.getFont(compositeFormat);

            if(compositeFormat.isWrapping()) {
               lineHeight = Common.getHeight(baseFont);
            }
         }

         decoration = VSCSSUtil.getDecoration(compositeFormat);
         getBorder().setBottom(VSCSSUtil.getBorder(compositeFormat, "bottom"));
         getBorder().setTop(VSCSSUtil.getBorder(compositeFormat, "top"));
         getBorder().setRight(VSCSSUtil.getBorder(compositeFormat, "right"));
         getBorder().setLeft(VSCSSUtil.getBorder(compositeFormat, "left"));
         setWrapping(new Wrapping(compositeFormat.isWrapping()));

         // bug 19943, if cell format is same as object format, ignore it so if
         // alpha is set, it won't overlap causes different shades of colors
         if(alpha != 1 && Tool.equals(background, background2) && objFmt != compositeFormat &&
            !(assemblyInfo instanceof TabVSAssemblyInfo))
         {
            background = null;
         }

         if(compositeFormat instanceof VSCompositeFormat &&
            assemblyInfo instanceof TitledVSAssemblyInfo)
         {
            VSCSSFormat cssFormat = ((VSCompositeFormat) compositeFormat).getCSSFormat();

            if(cssFormat.getCSSParam() != null && cssFormat.getCSSParam().getCSSType() != null &&
               cssFormat.getCSSParam().getCSSType().endsWith(CSSConstants.TITLE))
            {
               padding = ((TitledVSAssemblyInfo) assemblyInfo).getTitlePadding();
            }
         }
      }

      if(assemblyInfo instanceof TextVSAssemblyInfo ||
         assemblyInfo instanceof ImageVSAssemblyInfo)
      {
         OutputVSAssemblyInfo outputVSAssemblyInfo = (OutputVSAssemblyInfo) assemblyInfo;

         if(outputVSAssemblyInfo.getHighlightForeground() != null) {
            foreground = VSCSSUtil.getForegroundColor(
               outputVSAssemblyInfo.getHighlightForeground());
         }

         if(outputVSAssemblyInfo.getHighlightBackground() != null) {
            background = VSCSSUtil.getBackgroundColor(
               outputVSAssemblyInfo.getHighlightBackground());
         }

         if(outputVSAssemblyInfo.getHighlightFont() != null) {
            StyleFont styleFont;

            if(scaleFont) {
               Font base = outputVSAssemblyInfo.getHighlightFont();
               double fontScale = getFontScale(assemblyInfo, base);

               if(fontScale != 1.0) {
                  float newSize = Math.max(10f, (float) (fontScale * base.getSize()));
                  Font scaled = base.deriveFont(newSize);
                  styleFont = new StyleFont(scaled);
               }
               else {
                  styleFont = new StyleFont(base);
               }
            }
            else {
               styleFont = new StyleFont(outputVSAssemblyInfo.getHighlightFont());
            }

            font = VSCSSUtil.getFont(styleFont);
            decoration = VSCSSUtil.getFontDecoration(styleFont);
         }
      }
   }

   public VSFormatModel(VSCompositeFormat compositeFormat) {
      if(compositeFormat != null) {
         foreground = VSCSSUtil.getForeground(compositeFormat);
         background = VSCSSUtil.getBackgroundRGBA(compositeFormat);
         vAlign = VSCSSUtil.getvAlign(compositeFormat);
         hAlign = VSCSSUtil.gethAlign(compositeFormat);
         font = VSCSSUtil.getFont(compositeFormat);
         decoration = VSCSSUtil.getDecoration(compositeFormat);
         getBorder().setBottom(VSCSSUtil.getBorder(compositeFormat, "bottom"));
         getBorder().setTop(VSCSSUtil.getBorder(compositeFormat, "top"));
         getBorder().setRight(VSCSSUtil.getBorder(compositeFormat, "right"));
         getBorder().setLeft(VSCSSUtil.getBorder(compositeFormat, "left"));
         setWrapping(new Wrapping(compositeFormat.isWrapping()));
      }
   }

   public float getAlpha() {
      return alpha;
   }

   public int getRoundCorner() {
      return roundCorner;
   }

   public String gethAlign() {
      return hAlign;
   }

   public String getvAlign() {
      return vAlign;
   }

   public String getAlignItems() {
      return alignItems;
   }

   public String getJustifyContent() {
      return justifyContent;
   }

   public String getBackground() {
      return background;
   }

   public void setBackground(String background) {
      this.background = background;
   }

   public String getForeground() {
      return foreground;
   }

   public void setForeground(String foreground) {
      this.foreground = foreground;
   }

   public String getFont() {
      return font;
   }

   public String getDecoration() {
      return decoration;
   }

   public float getLineHeight() {
      return lineHeight;
   }

   private double getFontScale(VSAssemblyInfo info, Font font) {
      double ratio = 1;
      Dimension scaledSize = info.getLayoutSize(true);

      if(scaledSize != null && font.getSize() > 10) {
         Dimension baseSize = info.getPixelSize();
         double fontH = GTool.getFontMetrics(font).getHeight();

         // only scale text font down to avoid clipping. scaling up is problematic
         if(fontH * 0.8 > scaledSize.height && baseSize.height > scaledSize.height) {
            ratio = Math.min(1.0, scaledSize.getHeight() * 1.2 / baseSize.getHeight());
         }
      }

      return ratio;
   }

   public GradientColor getGradientColor() {
      return this.gradientColor;
   }

   public void setGradientColor(GradientColor gradientColor) {
      this.gradientColor = gradientColor;
   }

   public Insets getPadding() {
      return padding;
   }

   public void setPadding(Insets padding) {
      this.padding = padding;
   }

   @Override
   public String toString0() {
      return "hAlign:" + hAlign + " " +
         "vAlign:" + vAlign + " " +
         "background:" + background + " " +
         "foreground:" + foreground + " " +
         "font:" + font + " " +
         "decoration:" + decoration;
   }

   @Override
   public boolean equals(Object o) {
      if(this == o) {
         return true;
      }
      if(o == null || getClass() != o.getClass()) {
         return false;
      }
      if(!super.equals(o)) {
         return false;
      }
      VSFormatModel that = (VSFormatModel) o;
      return Float.compare(that.alpha, alpha) == 0 &&
         roundCorner == that.roundCorner &&
         lineHeight == that.lineHeight &&
         Objects.equals(hAlign, that.hAlign) &&
         Objects.equals(vAlign, that.vAlign) &&
         Objects.equals(alignItems, that.alignItems) &&
         Objects.equals(justifyContent, that.justifyContent) &&
         Objects.equals(background, that.background) &&
         Objects.equals(foreground, that.foreground) &&
         Objects.equals(font, that.font) &&
         Objects.equals(decoration, that.decoration) &&
         Objects.equals(gradientColor, that.gradientColor);
   }

   @Override
   public int hashCode() {
      return Objects.hash(super.hashCode(), alpha, roundCorner, hAlign, vAlign, alignItems,
                          justifyContent, background, foreground, font, decoration,
                          gradientColor, lineHeight);
   }

   private float alpha;
   private int roundCorner;
   private String hAlign; // contains css text-align values
   private String vAlign; // contains css vertical-align values
   private String alignItems; // flex align-items
   private String justifyContent; // flex justify-content
   private String background; // contains css background-color values
   private String foreground; // contains css color values
   private String font; // contains css font values
   private String decoration; // contains css text decoration value
   private GradientColor gradientColor;
   private float lineHeight; // line height for wrapped text
   private Insets padding;
}
