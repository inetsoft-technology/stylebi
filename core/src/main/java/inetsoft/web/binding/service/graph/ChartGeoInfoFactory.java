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

import inetsoft.uql.viewsheet.graph.*;
import inetsoft.web.binding.model.graph.ChartGeoRefModel;
import inetsoft.web.binding.model.graph.OriginalDescriptor;
import inetsoft.web.binding.service.DataRefModelFactoryService;
import inetsoft.web.binding.service.graph.ChartDimensionInfoFactory.VSChartDimensionInfoFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

public abstract class ChartGeoInfoFactory<G extends GeoRef> extends
   ChartRefModelFactory<G, ChartGeoRefModel>
{
   ChartGeoInfoFactory(DataRefModelFactoryService refModelService) {
      this.refModelService = refModelService;
   }

   @Override
   public void pasteChartRef(ChartInfo cinfo, ChartGeoRefModel model, G ref) {
      if(model.getOption() != null) {
         ref.setGeographicOption(model.getOption().toGeographicOptionInfo());
      }
   }

   @Override
   protected ChartGeoRefModel createChartRefModel0(G chartRef, ChartInfo cinfo,
      OriginalDescriptor des)
   {
      ChartGeoRefModel model = new ChartGeoRefModel(chartRef, cinfo);
      model.setOriginal(des);
      model.setDataRefModel(refModelService.createDataRefModel(chartRef.getDataRef()));

      return model;
   }

   private final DataRefModelFactoryService refModelService;

   @Component
   public static final class VSChartGeoInfoFactory extends ChartGeoInfoFactory<VSChartGeoRef> {
      @Autowired
      public VSChartGeoInfoFactory(DataRefModelFactoryService refModelService,
                                   VSChartDimensionInfoFactory dimFactory)
      {
         super(refModelService);
         this.dimFactory = dimFactory;
      }

      @Override
      public Class<VSChartGeoRef> getChartRefClass() {
         return VSChartGeoRef.class;
      }

      @Override
      protected ChartGeoRefModel createChartRefModel0(VSChartGeoRef ref,
         ChartInfo cinfo, OriginalDescriptor des)
      {
         ChartGeoRefModel model = super.createChartRefModel0(ref, cinfo, des);
         model.setColumnValue(ref.getGroupColumnValue());
         model.setCaption(ref.getCaption());

         return model;
      }

      @Override
      public void pasteChartRef(ChartInfo cinfo, ChartGeoRefModel model,
         VSChartGeoRef ref)
      {
         super.pasteChartRef(cinfo, model, ref);

         dimFactory.pasteChartRef(cinfo, model, ref);
      }

      private final VSChartDimensionInfoFactory dimFactory;
   }

}