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

import inetsoft.report.composition.graph.GraphTypeUtil;
import inetsoft.uql.viewsheet.graph.ChartInfo;
import inetsoft.uql.viewsheet.graph.ChartRef;
import inetsoft.util.Tool;
import inetsoft.web.binding.model.graph.ChartRefModel;
import inetsoft.web.binding.model.graph.OriginalDescriptor;

/**
 * Interface for classes that handle the creation of DTO models for viewsheet
 * assemblies.
 *
 * @param <A> the source assembly type.
 * @param <M> the target model type.
 */
public abstract class ChartRefModelFactory<A extends ChartRef, M extends ChartRefModel> {
   /**
    * Gets the source assembly class supported by this factory.
    *
    * @return the chart ref class.
    */
   public abstract Class<A> getChartRefClass();

   /**
    * Creates a new model instance for the specified assembly.
    *
    * @param chartRef the chartRef.
    * @param cinfo    the chart info.
    * @param des      the original descriptor.
    * @return a new model.
    */
   public M createChartRefModel(A chartRef, ChartInfo cinfo, OriginalDescriptor des) {
      M model = createChartRefModel0(chartRef, cinfo, des);
      model.setFullName(chartRef.getFullName());
      model.setView(Tool.localize(chartRef.toView()));
      model.setRefConvertEnabled(GraphTypeUtil.supportsInvertedChart(cinfo));

      return model;
   }

   /**
    * Creates a new model instance for the specified assembly.
    *
    * @param chartRef the chartRef.
    * @param cinfo      the chart info.
    * @return a new model.
    */
   protected abstract M createChartRefModel0(A chartRef, ChartInfo cinfo,
      OriginalDescriptor des);

   /**
    * Paste the chart ref model information to chartRef.
    * @param cinfo the chart info.
    * @param model the ref model.
    * @param ref   the chart ref.
    */
   public abstract void pasteChartRef(ChartInfo cinfo, M model, A ref);
}
