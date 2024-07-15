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
package inetsoft.web.binding.service.graph;

import inetsoft.uql.viewsheet.graph.AllChartAggregateRef;
import inetsoft.uql.viewsheet.graph.ChartInfo;
import inetsoft.web.binding.model.graph.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class AllChartAggregateInfoFactory
   extends ChartRefModelFactory<AllChartAggregateRef, AllChartAggregateRefModel>
{
   @Autowired
   public AllChartAggregateInfoFactory
      (ChartAggregateInfoFactory.VSChartAggregateInfoFactory aggFactory)
   {
      this.aggFactory = aggFactory;
   }

   @Override
   public Class<AllChartAggregateRef> getChartRefClass() {
      return AllChartAggregateRef.class;
   }

   @Override
   protected AllChartAggregateRefModel createChartRefModel0(AllChartAggregateRef chartRef,
      ChartInfo cinfo, OriginalDescriptor des)
   {
      AllChartAggregateRefModel allInfo = new AllChartAggregateRefModel();
      allInfo.setOriginal(des);

      VisualPaneStatusModel visualPaneStatus = new VisualPaneStatusModel();
      visualPaneStatus.setTextFieldEditable(!chartRef.isMixedValue("getTextField"));
      allInfo.setVisualPaneStatus(visualPaneStatus);

      return allInfo;
   }

   @Override
   public void pasteChartRef(ChartInfo cinfo, AllChartAggregateRefModel model,
      AllChartAggregateRef ref)
   {
      //empty function.
   }

   private final ChartAggregateInfoFactory.VSChartAggregateInfoFactory aggFactory;
}