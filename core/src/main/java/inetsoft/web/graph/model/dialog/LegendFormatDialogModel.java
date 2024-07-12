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
package inetsoft.web.graph.model.dialog;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import inetsoft.report.composition.graph.GraphUtil;
import inetsoft.report.composition.region.ChartArea;
import inetsoft.uql.viewsheet.graph.*;
import inetsoft.util.Tool;

import java.awt.*;
import java.util.List;
import java.util.*;

@JsonIgnoreProperties(ignoreUnknown = true)
public class LegendFormatDialogModel {
   public LegendFormatDialogModel() {
   }

   public LegendFormatDialogModel(ChartInfo info, LegendsDescriptor legendsDesc,
      LegendDescriptor legendDesc, ChartArea area, List<String> targetFields,
      String aestheticType, String field, String titleName, boolean dimension, boolean time,
      boolean node)
   {
      // Create LegendFormatGeneralPaneModel.
      generalPaneModel = new LegendFormatGeneralPaneModel();
      final String title = legendDesc.getTitle();
      final String titleValue = legendDesc.getTitleValue();
      generalPaneModel.setTitle(title != null && !"".equals(title) ? title : titleName);
      generalPaneModel.setTitleValue(titleValue != null && !"".equals(titleValue) ?
         titleValue : titleName);
      generalPaneModel.setVisible(legendDesc.isTitleVisible());
      generalPaneModel.setStyle(legendsDesc.getBorder());
      generalPaneModel.setNotShowNull(legendDesc.isNotShowNull());
      generalPaneModel.setNotShowNullVisible(dimension && field != null);

      if(legendsDesc.getBorderColor() != null) {
         generalPaneModel.setFillColor(
            "#" + Tool.colorToHTMLString(legendsDesc.getBorderColor()));
      }

      if(legendsDesc.getLayout() >= 1) {
         generalPaneModel.setPosition(LEGEND_POSITIONS[legendsDesc.getLayout() - 1]);
      }

      // Create LegendScalePaneModel.
      scalePaneModel = new LegendScalePaneModel();
      scalePaneModel.setLogarithmic(legendDesc.isLogarithmicScale());
      scalePaneModel.setReverse(legendDesc.isReversed());
      scalePaneModel.setReverseVisible(!"Size".equals(aestheticType) ||
         !GraphTypes.isTreemap(info.getChartType()));
      scalePaneModel.setIncludeZero(legendDesc.isIncludeZero());
      scalePaneModel.setIncludeZeroVisible("Size".equals(aestheticType) &&
         !GraphTypes.isTreemap(info.getChartType()));

      // Create AliasPaneModel.
      aliasPaneModel = new AliasPaneModel();
      Map<String, String> legendItems = GraphUtil.getLegendItems(aestheticType,
         field, targetFields, area);
      Iterator<String> it = legendItems.keySet().iterator();
      List<ModelAlias> aliasList = new ArrayList<>();

      while (it.hasNext()) {
         String item = it.next();
         String alias = legendDesc.getLabelAlias((String) item);
         String text = legendItems.get(item);

         if(alias == null) {
            alias = text;
         }

         aliasList.add(new ModelAlias(text, item, alias));
      }

      aliasList.sort(Comparator.comparing(ModelAlias::getValue));

      aliasPaneModel.setAliasList(aliasList.toArray(new ModelAlias[0]));
      this.dimension = dimension;
      this.time = time;
      this.node = node;
   }

   public void updateLegendFormatDialogModel(ChartInfo cinfo, LegendsDescriptor legendsDesc,
                                             LegendDescriptor legendDesc, String titleName)
   {
      final String title = generalPaneModel.getTitleValue();

      if(title == null) {
         legendDesc.setTitleValue("");
      }
      else if(legendDesc.getTitleValue() != null || !Tool.equals(titleName, title)) {
         legendDesc.setTitleValue(title);
      }

      legendDesc.setTitleVisible(generalPaneModel.isVisible());
      legendsDesc.setBorder(generalPaneModel.getStyle(), false);
      Color color = Tool.getColorFromHexString(generalPaneModel.getFillColor());
      legendsDesc.setBorderColor(color, false);
      legendsDesc.setLayout(getIndexByName(LEGEND_POSITIONS, generalPaneModel.getPosition()) + 1);
      legendDesc.setNotShowNull(generalPaneModel.isNotShowNull());

      legendDesc.setReversed(scalePaneModel.isReverse());
      legendDesc.setLogarithmicScale(scalePaneModel.isLogarithmic());
      legendDesc.setIncludeZero(scalePaneModel.isIncludeZero());

      updateAlias(legendDesc);

      // keep the alias in sync. see GraphUtil.syncLabelAliases(). (60099)
      if(cinfo.getColorField() == null && cinfo.getShapeField() == null) {
         if(legendDesc == legendsDesc.getColorLegendDescriptor()) {
            updateAlias(legendsDesc.getShapeLegendDescriptor());
         }
         else if(legendDesc == legendsDesc.getShapeLegendDescriptor()) {
            updateAlias(legendsDesc.getColorLegendDescriptor());
         }
      }
   }

   private void updateAlias(LegendDescriptor legendDesc) {
      ModelAlias[] list = aliasPaneModel.getAliasList();

      for(ModelAlias item : list) {
         String alias = item.getAlias().trim();
         String label = item.getLabel();
         String value = item.getValue();
         legendDesc.setLabelAlias(value, ("".equals(alias) || alias.equals(label)) ? null : alias);
      }
   }

   /**
    * Gets array index by item name.
    *
    * @param arr  array name.
    * @param name element content of the string type array.
    *
    * @return the index which responded to the name in array.
    */
   private int getIndexByName(String[] arr, String name) {
      int index = 0;

      for(int i = 0; i < arr.length; i++) {
         if(name.equals(arr[i])) {
            index = i;
         }
      }

      return index;
   }

   public AliasPaneModel getAliasPaneModel() {
      return aliasPaneModel;
   }

   public void setAliasPaneModel(AliasPaneModel aliasPaneModel) {
      this.aliasPaneModel = aliasPaneModel;
   }

   public LegendFormatGeneralPaneModel getLegendFormatGeneralPaneModel() {
      return generalPaneModel;
   }

   public void setLegendFormatGeneralPaneModel(
      LegendFormatGeneralPaneModel generalPaneModel)
   {
      this.generalPaneModel = generalPaneModel;
   }

   public LegendScalePaneModel getLegendScalePaneModel() {
      return scalePaneModel;
   }

   public void setLegendScalePaneModel(LegendScalePaneModel scalePaneModel) {
      this.scalePaneModel = scalePaneModel;
   }

   public void setDimension(boolean dimension) {
      this.dimension = dimension;
   }

   public boolean isDimension() {
      return this.dimension;
   }

   public boolean isTime() {
      return time;
   }

   public void setTime(boolean time) {
      this.time = time;
   }

   public boolean isNode() {
      return node;
   }

   public void setNode(boolean node) {
      this.node = node;
   }

   private AliasPaneModel aliasPaneModel;
   private LegendFormatGeneralPaneModel generalPaneModel;
   private LegendScalePaneModel scalePaneModel;
   private boolean dimension;
   private boolean time;
   private boolean node;

   private static final String[] LEGEND_POSITIONS = {"Top", "Right", "Bottom", "Left", "In Place"};
}
