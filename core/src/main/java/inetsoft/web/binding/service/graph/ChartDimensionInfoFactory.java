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
package inetsoft.web.binding.service.graph;

import inetsoft.uql.XConstants;
import inetsoft.uql.asset.SNamedGroupInfo;
import inetsoft.uql.erm.DataRef;
import inetsoft.uql.util.XNamedGroupInfo;
import inetsoft.uql.viewsheet.graph.*;
import inetsoft.web.binding.VSDataController;
import inetsoft.web.binding.model.NamedGroupInfoModel;
import inetsoft.web.binding.model.graph.ChartDimensionRefModel;
import inetsoft.web.binding.model.graph.OriginalDescriptor;
import inetsoft.web.binding.service.DataRefModelFactoryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

public abstract class ChartDimensionInfoFactory<M extends ChartDimensionRef>
   extends ChartRefModelFactory<M, ChartDimensionRefModel>
{
   ChartDimensionInfoFactory(DataRefModelFactoryService refModelService) {
      this.refModelService = refModelService;
   }

   @Override
   protected ChartDimensionRefModel createChartRefModel0(M chartRef, ChartInfo cinfo,
                                                         OriginalDescriptor des)
   {
      ChartDimensionRefModel dimInfo = new ChartDimensionRefModel(chartRef, cinfo);
      dimInfo.setOriginal(des);
      DataRef ref = chartRef.getDataRef();

      if(ref != null) {
         dimInfo.setDataRefModel(refModelService.createDataRefModel(ref));
      }

      NamedGroupInfoModel namedGroupInfoModel = dimInfo.getNamedGroupInfo();

      if(namedGroupInfoModel != null) {
         XNamedGroupInfo namedGroupInfo = chartRef.getNamedGroupInfo();

         if(namedGroupInfo instanceof SNamedGroupInfo) {
            DataRef dref = ((SNamedGroupInfo) namedGroupInfo).getDataRef();

            // old case may not exist right dataref.
            if(dref != null && dref.getAttribute() == null) {
               ((SNamedGroupInfo) namedGroupInfo).setDataRef(chartRef.getDataRef());
            }
         }

         namedGroupInfoModel.fixNamedGroupInfoModel(namedGroupInfo, this.refModelService);
      }

      return dimInfo;
   }

   @Override
   public void pasteChartRef(ChartInfo cinfo, ChartDimensionRefModel model, M ref) {
      if(model.getOrder() == XConstants.SORT_NONE) {
         ref.setNamedGroupInfo(null);
      }

      ref.setOrder(model.getOrder());
      ref.setTimeSeries(model.isTimeSeries());

      if(model.getDataRefModel() != null) {
         ref.setDataRef(model.getDataRefModel().createDataRef());
      }
   }

   private final DataRefModelFactoryService refModelService;

   @Component
   public static final class VSChartDimensionInfoFactory
      extends ChartDimensionInfoFactory<VSChartDimensionRef>
   {
      @Autowired
      public VSChartDimensionInfoFactory(DataRefModelFactoryService refModelService) {
         super(refModelService);
      }

      @Override
      public Class<VSChartDimensionRef> getChartRefClass() {
         return VSChartDimensionRef.class;
      }

      @Override
      protected ChartDimensionRefModel createChartRefModel0(VSChartDimensionRef ref,
         ChartInfo cinfo, OriginalDescriptor des)
      {
         ChartDimensionRefModel dimInfo = super.createChartRefModel0(ref, cinfo, des);
         dimInfo.setColumnValue(ref.getGroupColumnValue());
         dimInfo.setCaption(ref.getCaption());

         return dimInfo;
      }

      @Override
      public void pasteChartRef(ChartInfo cinfo,
         ChartDimensionRefModel model, VSChartDimensionRef ref)
      {
         super.pasteChartRef(cinfo, model, ref);

         ref.setSortByColValue(model.getSortByCol());
         ref.setRankingColValue(model.getRankingCol());
         ref.setRankingOptionValue(model.getRankingOption());
         ref.setRankingNValue(model.getRankingN());
         ref.setGroupOthersValue(model.isGroupOthers() + "");
         ref.setGroupColumnValue(model.getColumnValue());

         ref.setCaption(model.getCaption());
         ref.setManualOrderList(VSDataController.fixNull(model.getManualOrder()));
         ref.setDateLevelValue(model.getDateLevel());
      }
   }

   private static final Logger LOG = LoggerFactory.getLogger(ChartDimensionInfoFactory.class);
}
