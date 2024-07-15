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
package inetsoft.web.binding.model.graph.aesthetic;

import inetsoft.graph.aesthetic.CategoricalColorFrame;
import inetsoft.graph.aesthetic.VisualFrame;
import inetsoft.uql.viewsheet.graph.aesthetic.CategoricalColorFrameWrapper;
import inetsoft.util.Tool;
import inetsoft.web.binding.model.ColorMapModel;

import java.awt.*;
import java.util.List;
import java.util.*;

public class CategoricalColorModel extends ColorFrameModel {
   public CategoricalColorModel() {
   }

   public CategoricalColorModel(CategoricalColorFrameWrapper wrapper) {
      super(wrapper);
      Map<Integer, Color> cssmap = wrapper.getCSSColors();
      int num = wrapper.getColorCount();
      String[] colors = new String[num];
      String[] cssColors = new String[num];
      String[] defaultColors = new String[num];

      for(int i = 0; i < num; i++) {
         colors[i] = Tool.toString(wrapper.getColor(i));
         cssColors[i] = Tool.toString(cssmap.get(i));
         defaultColors[i] = Tool.toString(wrapper.getDefaultColor(i));
      }

      setColors(colors);
      setCssColors(cssColors);
      setDefaultColors(defaultColors);
      setColorValueFrame(wrapper.isColorValueFrame());
      useGlobal = wrapper.isUseGlobal();
      List<ColorMapModel> models = new ArrayList<>();

      if(useGlobal) {
         final Map<String, Color> dimensionColors = wrapper.getDimensionColors();

         for(Map.Entry<String, Color> entry : dimensionColors.entrySet()) {
            ColorMapModel colormap = new ColorMapModel();
            colormap.setOption(entry.getKey() == null ? null : Tool.toString(entry.getKey()));
            colormap.setColor(Tool.toString(entry.getValue()));
            models.add(colormap);
         }

         globalColorMaps = models.toArray(new ColorMapModel[0]);
      }
      else {
         for(Object key : wrapper.getStaticValues()) {
            ColorMapModel colormap = new ColorMapModel();
            colormap.setOption(key == null ? null : Tool.toString(key));
            colormap.setColor(Tool.toString(wrapper.getColor(key)));
            models.add(colormap);
         }

         colorMaps = models.toArray(new ColorMapModel[0]);
      }

      shareColors = wrapper.isShareColors();
      setDateFormat(wrapper.getDateFormat());
   }

   /**
    * Set the current using colors.
    */
   public void setColors(String[] colors) {
      this.colors = colors;
   }

   /**
    * Get the current using colors.
    */
   public String[] getColors() {
      return colors;
   }

   /**
    * Set the current using colors.
    */
   public void setCssColors(String[] colors) {
      this.cssColors = colors;
   }

   /**
    * Get the current using colors.
    */
   public String[] getCssColors() {
      return cssColors;
   }

   /**
    * Set the current using colors.
    */
   public void setDefaultColors(String[] colors) {
      this.defaultColors = colors;
   }

   /**
    * Get the current using colors.
    */
   public String[] getDefaultColors() {
      return defaultColors;
   }

   public ColorMapModel[] getColorMaps() {
      return colorMaps;
   }

   public void setColorMaps(ColorMapModel[] colorMaps) {
      this.colorMaps = colorMaps;
   }

   public ColorMapModel[] getGlobalColorMaps() {
      return globalColorMaps;
   }

   public void setGlobalColorMaps(ColorMapModel[] globalColorMaps) {
      this.globalColorMaps = globalColorMaps;
   }

   public boolean isUseGlobal() {
      return useGlobal;
   }

   public void setUseGlobal(boolean useGlobal) {
      this.useGlobal = useGlobal;
   }

   public boolean isShareColors() {
      return shareColors;
   }

   public void setShareColors(boolean shareColors) {
      this.shareColors = shareColors;
   }

   public Integer getDateFormat() {
      return dateFormat;
   }

   public void setDateFormat(Integer dateFormat) {
      this.dateFormat = dateFormat;
   }

   @Override
   public VisualFrame createVisualFrame() {
      return new CategoricalColorFrame();
   }

   public boolean isColorValueFrame() {
      return colorValueFrame;
   }

   public void setColorValueFrame(boolean colorValueFrame) {
      this.colorValueFrame = colorValueFrame;
   }

   private String[] colors;
   private String[] cssColors;
   private String[] defaultColors;
   private ColorMapModel[] colorMaps = new ColorMapModel[0];
   private ColorMapModel[] globalColorMaps = new ColorMapModel[0];
   private boolean useGlobal = true;
   private boolean shareColors = true;
   private boolean colorValueFrame;
   private Integer dateFormat;
}
