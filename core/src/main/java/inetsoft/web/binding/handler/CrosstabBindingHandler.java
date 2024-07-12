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
package inetsoft.web.binding.handler;

import inetsoft.uql.asset.DateRangeRef;
import inetsoft.uql.schema.XSchema;
import inetsoft.web.binding.model.BDimensionRefModel;
import inetsoft.web.binding.model.table.CrosstabBindingModel;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class CrosstabBindingHandler {

   @Autowired
   public CrosstabBindingHandler() {
   }

   /**
    * Update aggregate measure when the date level of the corresponding dimension changes
    */
   public static void applyDLevelChanges(CrosstabBindingModel model) {
      List<BDimensionRefModel> refs = new ArrayList<>(model.getRows());
      refs.addAll(model.getCols());

      for(BDimensionRefModel dimension : refs) {
         String name = dimension.getName();
         String dLevel = dimension.getDateLevel();

         if(!XSchema.isDateType(dimension.getDataType()) || dLevel == null || "".equals(dLevel)
            || "-1".equals(dLevel) || dLevel.startsWith("$") || dLevel.startsWith("="))
         {
            continue;
         }

         int level = Integer.parseInt(dLevel);
         String newName = DateRangeRef.getName(name, Integer.parseInt(dLevel));
         String dimensionName = dimension.getFullName();

         if(StringUtils.isEmpty(dimensionName)) {
            dimensionName = dimension.getView();
         }

         if(!newName.equals(dimensionName)) {
            CalculatorHandler.updateAggregateColNames(model.getAggregates(), dimensionName, newName, level);
         }
      }
   }
}
