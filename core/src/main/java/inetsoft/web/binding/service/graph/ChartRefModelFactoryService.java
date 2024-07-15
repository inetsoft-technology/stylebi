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
import inetsoft.web.binding.model.graph.ChartRefModel;
import inetsoft.web.binding.model.graph.OriginalDescriptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Class that acts as a facade for all registered instances of
 *
 * @since 12.3
 */
@Component
public class ChartRefModelFactoryService {
   @Autowired
   public ChartRefModelFactoryService(List<ChartRefModelFactory<?, ?>> factories) {
      factories.forEach((factory) -> registerFactory(factory.getChartRefClass(), factory));
   }

   /**
    * Registers a model factory instance.
    *
    * @param type the assembly class supported by the factory.
    * @param factory  the factory.
    */
   private void registerFactory(Class<?> type, ChartRefModelFactory<?, ?> factory) {
      factories.put(type, factory);
   }

   /**
    * Creates a DTO model for the specified viewsheet assembly.
    *
    * @param ref the assembly.
    * @param cinfo      the runtime viewsheet instance containing the assembly.
    *
    * @return the DTO model.
    */
   @SuppressWarnings("unchecked")
   public ChartRefModel createRefModel(ChartRef ref, ChartInfo cinfo,
      OriginalDescriptor desc)
   {
      ChartRefModelFactory factory = getFactory(ref);

      return factory.createChartRefModel(ref, cinfo, desc);
   }

   private ChartRefModelFactory getFactory(ChartRef ref) {
      Objects.requireNonNull(ref, "The chartRef type must not be null");
      ChartRefModelFactory factory = factories.get(ref.getClass());
      Objects.requireNonNull(
         factory,
         () -> "No model factory registered for chartRef type " + ref.getClass().getName());
      return factory;
   }

   @SuppressWarnings("unchecked")
   public ChartRef pasteChartRef(ChartInfo cinfo, ChartRefModel model) {
      if(model == null) {
         return null;
      }

      ChartRef ref = findChartRef(cinfo, model.getOriginal());
      ChartRef ref0 = model.createChartRef(cinfo);

      // don't reuse if the type of the refs don't match (47537).
      if(ref == null || ref.getClass() != ref0.getClass()) {
         ref = ref0;
      }

      ChartRefModelFactory factory = getFactory(ref);
      factory.pasteChartRef(cinfo, model, ref);
      return ref;
   }

   /**
    * Find the chart ref with original descriptor from ChartInfo.
    * @param cinfo    the ChartInfo.
    * @param original the descriptor of this chart ref.
    * @return the specified chart ref.
    */
   private ChartRef findChartRef(ChartInfo cinfo, OriginalDescriptor original) {
      if(original == null) {
         return null;
      }

      String source = original.getSource();
      int idx = original.getIndex();

      if(OriginalDescriptor.X_AXIS.equals(source)) {
         return cinfo.getXField(idx);
      }
      else if(OriginalDescriptor.Y_AXIS.equals(source)) {
         return cinfo.getYField(idx);
      }
      else if(OriginalDescriptor.GROUP.equals(source)) {
         return cinfo.getGroupField(idx);
      }
      else if(OriginalDescriptor.PATH.equals(source)) {
         return cinfo.getPathField();
      }
      else if(OriginalDescriptor.GEO_COL.equals(source) && cinfo instanceof VSChartInfo) {
         return (ChartRef) ((VSChartInfo) cinfo).getGeoColumns().getAttribute(idx);
      }
      else if(OriginalDescriptor.GEO.equals(source) && cinfo instanceof MapInfo)
      {
         return ((MapInfo) cinfo).getGeoFieldByName(idx);
      }
      else if(OriginalDescriptor.CLOSE.equals(source) && cinfo instanceof CandleChartInfo) {
         return ((CandleChartInfo) cinfo).getCloseField();
      }
      else if(OriginalDescriptor.OPEN.equals(source) && cinfo instanceof CandleChartInfo) {
         return ((CandleChartInfo) cinfo).getOpenField();
      }
      else if(OriginalDescriptor.HIGH.equals(source) && cinfo instanceof CandleChartInfo) {
         return ((CandleChartInfo) cinfo).getHighField();
      }
      else if(OriginalDescriptor.LOW.equals(source) && cinfo instanceof CandleChartInfo) {
         return ((CandleChartInfo) cinfo).getLowField();
      }
      else if(OriginalDescriptor.SOURCE.equals(source) && cinfo instanceof RelationChartInfo) {
         return ((RelationChartInfo) cinfo).getSourceField();
      }
      else if(OriginalDescriptor.TARGET.equals(source) && cinfo instanceof RelationChartInfo) {
         return ((RelationChartInfo) cinfo).getTargetField();
      }
      else if(OriginalDescriptor.START.equals(source) && cinfo instanceof GanttChartInfo) {
         return ((GanttChartInfo) cinfo).getStartField();
      }
      else if(OriginalDescriptor.END.equals(source) && cinfo instanceof GanttChartInfo) {
         return ((GanttChartInfo) cinfo).getEndField();
      }
      else if(OriginalDescriptor.MILESTONE.equals(source) && cinfo instanceof GanttChartInfo) {
         return ((GanttChartInfo) cinfo).getMilestoneField();
      }
      else if(OriginalDescriptor.ALL.equals(source)) {
         // use clone info insteadof cinfo, to avoid the bug like drag color
         // field to shape, the colorfield will be updated to null, when
         // findchartref for shapefield by orignal source('Color') will
         // return null.
         List aggreRefs = AllChartAggregateRef.getXYAggregateRefs(
            (ChartInfo) cinfo.clone(), true);
         return new AllChartAggregateRef(cinfo, aggreRefs);
      }

      ChartBindable bindable = getChartBindable(cinfo, original.getAggregateDesc());

      if(bindable == null) {
         return null;
      }

      AestheticRef aesRef = null;

      if(OriginalDescriptor.COLOR.equals(source)) {
         aesRef = bindable.getColorField();
      }
      else if(OriginalDescriptor.SHAPE.equals(source)) {
         aesRef = bindable.getShapeField();
      }
      else if(OriginalDescriptor.SIZE.equals(source)) {
         aesRef = bindable.getSizeField();
      }
      else if(OriginalDescriptor.TEXT.equals(source)) {
         aesRef = bindable.getTextField();
      }
      else if(OriginalDescriptor.NODE_COLOR.equals(source)) {
         aesRef = ((RelationChartInfo) bindable).getNodeColorField();
      }
      else if(OriginalDescriptor.NODE_SIZE.equals(source)) {
         aesRef = ((RelationChartInfo) bindable).getNodeSizeField();
      }

      return aesRef != null ? (ChartRef) aesRef.getDataRef() : null;
   }

    /**
    * Get the chart bindalbe.
    */
   private ChartBindable getChartBindable(ChartInfo cinfo, OriginalDescriptor desc) {
      if(desc == null) {
         return cinfo;
      }

      ChartRef ref = findChartRef(cinfo, desc);
      return ref instanceof ChartAggregateRef ? (ChartAggregateRef) ref : null;
   }

   private final Map<Class<?>, ChartRefModelFactory> factories = new HashMap<>();
}
