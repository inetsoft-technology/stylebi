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
package inetsoft.web.service;

import inetsoft.report.composition.graph.GraphTypeUtil;
import inetsoft.sree.security.ResourceAction;
import inetsoft.sree.security.ResourceType;
import inetsoft.uql.viewsheet.graph.GraphTypes;
import inetsoft.util.Catalog;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;
import java.util.*;

@RestController
public class ChartStylesController {
   @GetMapping("/api/chart/getAvailableChartStyles")
   public ChartStylesModel getChartStylesModel(Principal principal) {
      return getChartStyles(principal);
   }

   public ChartStylesModel getChartStyles(Principal principal) {
      Catalog catalog = Catalog.getCatalog();
      Map<String, String> chartTypes = GraphTypes.getAllChartTypes();
      Set<String> keys = chartTypes.keySet();
      ChartStylesModel model = new ChartStylesModel();
      List<ChartStyle> styles = new ArrayList<>();
      List<ChartStyle> stackStyles = new ArrayList<>();

      for(String key : keys) {
         String typeStr = chartTypes.get(key);
         int typeNum = Integer.parseInt(key);

         if(key.equals(String.valueOf(GraphTypes.CHART_AUTO))) {
            styles.add(new ChartStyle(catalog.getString(typeStr), GraphTypes.CHART_AUTO));
            stackStyles.add(new ChartStyle(catalog.getString(typeStr), GraphTypes.CHART_AUTO));
            continue;
         }

         boolean stack = GraphTypes.isStack(typeNum);
         String ntypeStr = typeStr;
         int ntypeNum = typeNum;

         if(stack) {
            String[] style = GraphTypeUtil.getChartNonStackStyle(typeNum);

            if(style != null) {
               ntypeNum = Integer.parseInt(style[0]);
               ntypeStr = style[1];
            }
         }

         String resource = GraphTypeUtil.getChartStylePath(ntypeNum, ntypeStr);
         ResourceType type = resource != null && resource.contains("/") ?
            ResourceType.CHART_TYPE : ResourceType.CHART_TYPE_FOLDER;
         boolean allowed = GraphTypeUtil.checkChartStylePermission(
            resource, type, ResourceAction.READ, principal);

         if(allowed) {
            typeStr = catalog.getString(typeStr);

            if(GraphTypes.isBar(typeNum) || GraphTypes.isArea(typeNum) ||
               GraphTypes.isPoint(typeNum) || GraphTypes.isStep(typeNum) ||
               typeNum == GraphTypes.CHART_LINE || typeNum == GraphTypes.CHART_LINE_STACK)
            {
               if(stack) {
                  stackStyles.add(new ChartStyle(typeStr, typeNum));
               }
               else {
                  styles.add(new ChartStyle(typeStr, typeNum));
               }
            }
            else {
               stackStyles.add(new ChartStyle(typeStr, typeNum));
               styles.add(new ChartStyle(typeStr, typeNum));
            }
         }
      }

      model.setStyles(styles.toArray(new ChartStyle[styles.size()]));
      model.setStackStyles(stackStyles.toArray(new ChartStyle[stackStyles.size()]));

      return model;
   }
}
