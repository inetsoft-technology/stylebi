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
package inetsoft.web.binding.service;

import inetsoft.uql.ColumnSelection;
import inetsoft.uql.erm.DataRef;
import inetsoft.uql.viewsheet.ChartVSAssembly;
import inetsoft.uql.viewsheet.internal.VSUtil;
import inetsoft.web.binding.model.*;
import inetsoft.web.binding.service.graph.ChartAestheticService;
import inetsoft.web.binding.service.graph.ChartRefModelFactoryService;
import inetsoft.web.binding.service.graph.aesthetic.VisualFrameModelFactoryService;

import java.util.Objects;

public class VSChartInfoModelBuilder extends ChartInfoModelBuilder{
   public VSChartInfoModelBuilder(ChartRefModelFactoryService refService,
                                  ChartAestheticService aesService,
                                  DataRefModelFactoryService dataRefService,
                                  VisualFrameModelFactoryService visualService,
                                  ChartVSAssembly assembly)
   {
      super(refService, aesService, dataRefService, visualService);

      this.assembly = assembly;
      initColumnSelection(assembly);
   }

   @Override
   protected SortOptionModel createSortOptionModel() {
      return new SortOptionModel(getAggregateRefs(assembly.getVSChartInfo()));
   }

   @Override
   protected FormulaOptionModel createFormulaOptionModel(BAggregateRefModel cmodel) {
      boolean aggregateStatus = cmodel.getRefType() == DataRef.AGG_EXPR;

      if(!aggregateStatus) {
         for(int i = cols.getAttributeCount() - 1; i >= 0; i--) {
            DataRef col = cols.getAttribute(i);

            if(col != null && VSUtil.isAggregateCalc(col) &&
               Objects.equals(cmodel.getName(), col.getName()))
            {
               aggregateStatus = true;
               break;
            }
         }
      }

      return new FormulaOptionModel(aggregateStatus);
   }

   private void initColumnSelection(ChartVSAssembly assembly) {
      this.cols = VSUtil.getColumnsForCalc(assembly);
   }

   private ChartVSAssembly assembly;
   private ColumnSelection cols;
}
