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
import inetsoft.uql.viewsheet.graph.aesthetic.*;
import inetsoft.web.binding.model.graph.*;
import inetsoft.web.binding.service.graph.aesthetic.VisualFrameModelFactoryService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;

@Component
public class ChartAestheticService {
   /**
    * Load visual frames.
    * @param model the chart aesthetic model.
    * @param cinfo the chart info.
    * @param bindable the chart bindable.
    */
   public void loadVisualFrames(ChartAestheticModel model, ChartInfo cinfo, ChartBindable bindable)
   {
      if(bindable == null || model == null) {
         return;
      }

      model.setChartType(bindable.getChartType());
      model.setRTChartType(bindable.getRTChartType());

      if(cinfo instanceof VSChartInfo && bindable instanceof ChartAggregateRef) {
         HashMap<String, Integer> typeMap = getAggregateRtType(cinfo);
         String fullName = ((ChartAggregateRef) bindable).getFullName(false);

         if(typeMap != null && typeMap.containsKey(fullName)) {
            model.setRTChartType(typeMap.get(fullName));
         }
      }

      OriginalDescriptor aggregateDesc = model.getAggregateDesc();
      AestheticRef colorRef = bindable.getColorField();
      AestheticRef colorField = colorRef != null && isVisibleAestheticRef(colorRef) ?
         colorRef : null;
      model.setColorField(afactoryService.createAestheticInfo(colorField , cinfo,
         new OriginalDescriptor(OriginalDescriptor.COLOR, aggregateDesc)));

      model.setSizeField(afactoryService.createAestheticInfo(
         bindable.getSizeField(), cinfo,
         new OriginalDescriptor(OriginalDescriptor.SIZE, aggregateDesc)));

      AestheticRef shapeRef = bindable.getShapeField();
      AestheticRef shapeField = shapeRef != null && isVisibleAestheticRef(shapeRef) ?
         shapeRef : null;
      model.setShapeField(afactoryService.createAestheticInfo(shapeField, cinfo,
         new OriginalDescriptor(OriginalDescriptor.SHAPE, aggregateDesc)));

      model.setTextField(afactoryService.createAestheticInfo(
         bindable.getTextField(), cinfo,
         new OriginalDescriptor(OriginalDescriptor.TEXT, aggregateDesc)));

      ColorFrameWrapper color = bindable.getColorFrameWrapper();

      if(color != null) {
         model.setColorFrame(vfactoryService.createVisualFrameModel(color));
      }

      ShapeFrameWrapper shape = bindable.getShapeFrameWrapper();

      if(shape != null) {
         model.setShapeFrame(vfactoryService.createVisualFrameModel(shape));
      }

      SizeFrameWrapper size = bindable.getSizeFrameWrapper();

      if(size != null) {
         model.setSizeFrame(vfactoryService.createVisualFrameModel(size));
      }

      LineFrameWrapper line = bindable.getLineFrameWrapper();

      if(line != null) {
         model.setLineFrame(vfactoryService.createVisualFrameModel(line));
      }

      TextureFrameWrapper texture = bindable.getTextureFrameWrapper();

      if(texture != null) {
         model.setTextureFrame(vfactoryService.createVisualFrameModel(texture));
      }

      if(bindable instanceof RelationChartInfo) {
         AestheticRef nodeColorField = ((RelationChartInfo) bindable).getNodeColorField();
         model.setNodeColorField(afactoryService.createAestheticInfo(
            nodeColorField , cinfo, new OriginalDescriptor(OriginalDescriptor.NODE_COLOR, aggregateDesc)));
         AestheticRef nodeSizeField = ((RelationChartInfo) bindable).getNodeSizeField();
         model.setNodeSizeField(afactoryService.createAestheticInfo(
            nodeSizeField , cinfo, new OriginalDescriptor(OriginalDescriptor.NODE_SIZE, aggregateDesc)));

         color = ((RelationChartInfo) bindable).getNodeColorFrameWrapper();

         if(color != null) {
            model.setNodeColorFrame(vfactoryService.createVisualFrameModel(color));
         }

         size = ((RelationChartInfo) bindable).getNodeSizeFrameWrapper();

         if(size != null) {
            model.setNodeSizeFrame(vfactoryService.createVisualFrameModel(size));
         }
      }
   }

   /**
    * Update visual fields of the chart.
    */
   public void updateVisualFrames(ChartAestheticModel model, ChartInfo cinfo,
      ChartBindable bindable)
   {
      AestheticRef ncolor = getAestheticRef(model.getColorField(), bindable.getColorField(), cinfo);
      AestheticRef nshape = getAestheticRef(model.getShapeField(), bindable.getShapeField(), cinfo);
      AestheticRef nsize = getAestheticRef(model.getSizeField(), bindable.getSizeField(), cinfo);
      AestheticRef ntext = getAestheticRef(model.getTextField(), bindable.getTextField(), cinfo);

      bindable.setColorField(ncolor);
      bindable.setShapeField(nshape);
      bindable.setSizeField(nsize);
      bindable.setTextField(ntext);

      bindable.setRTChartType(model.getRTChartType());
      bindable.setChartType(model.getChartType());

      ColorFrameWrapper color = vfactoryService.updateVisualFrameWrapper(bindable.getColorFrameWrapper(),
                                               model.getColorFrame());
      ShapeFrameWrapper shape = vfactoryService.updateVisualFrameWrapper(bindable.getShapeFrameWrapper(),
                                               model.getShapeFrame());
      TextureFrameWrapper texture = vfactoryService.updateVisualFrameWrapper(bindable.getTextureFrameWrapper(),
                                               model.getTextureFrame());
      LineFrameWrapper line = vfactoryService.updateVisualFrameWrapper(bindable.getLineFrameWrapper(),
                                               model.getLineFrame());
      SizeFrameWrapper size = vfactoryService.updateVisualFrameWrapper(bindable.getSizeFrameWrapper(),
                                               model.getSizeFrame());

      if(color != null ) {
         bindable.setColorFrameWrapper(color);
      }

      if(shape != null) {
         bindable.setShapeFrameWrapper(shape);
      }

      if(texture != null) {
         bindable.setTextureFrameWrapper(texture);
      }

      if(line != null) {
         bindable.setLineFrameWrapper(line);
      }

      if(size != null) {
         bindable.setSizeFrameWrapper(size);
      }

      if(bindable instanceof RelationChartInfo) {
         AestheticRef nodecolor = getAestheticRef(
            model.getNodeColorField(), ((RelationChartInfo) bindable).getNodeColorField(), cinfo);
         ((RelationChartInfo) bindable).setNodeColorField(nodecolor);

         AestheticRef nodesize = getAestheticRef(
            model.getNodeSizeField(), ((RelationChartInfo) bindable).getNodeSizeField(), cinfo);
         ((RelationChartInfo) bindable).setNodeSizeField(nodesize);

         color = vfactoryService.updateVisualFrameWrapper(
            ((RelationChartInfo) bindable).getNodeColorFrameWrapper(), model.getNodeColorFrame());

         if(color != null) {
            ((RelationChartInfo) bindable).setNodeColorFrameWrapper(color);
         }

         size = vfactoryService.updateVisualFrameWrapper(
            ((RelationChartInfo) bindable).getNodeSizeFrameWrapper(), model.getNodeSizeFrame());

         if(size != null) {
            ((RelationChartInfo) bindable).setNodeSizeFrameWrapper(size);
         }
      }
   }

   /**
    * A runtime-flagged aesthetic ref is normally excluded from the design-time binding model
    * (e.g. an in-place RTDataRef swap on an existing design ref, or the raw XML persistence
    * filters elsewhere that must never save one of these). But date comparison
    * (ChartDcProcessor#createAestheticRef) installs a *new* color/shape ref this same way,
    * always with getDataRef() == getRTDataRef() since both are set from the identical object --
    * that one is the chart's real, currently-rendered binding, not stale runtime cruft, and
    * should still be visible here even though VSChartInfo#clearDateComparisonRuntimeRef() (and
    * the XML writers) still key off the same isRuntime() flag to discard it on the next
    * refresh/at persist time.
    */
   private boolean isVisibleAestheticRef(AestheticRef ref) {
      return !ref.isRuntime() || ref.getDataRef() == ref.getRTDataRef();
   }

   private AestheticRef getAestheticRef(AestheticInfo info, AestheticRef ref, ChartInfo cinfo) {
      AestheticRef oldref = ref != null ? (AestheticRef) ref.clone() : null;
      return afactoryService.pasteAestheticRef(cinfo, oldref, info);
   }

   public HashMap<String, Integer> getAggregateRtType(ChartInfo cinfo) {
      HashMap<String, Integer> typeMap = new HashMap<>();

      if(!(cinfo instanceof VSChartInfo) || !((VSChartInfo) cinfo).isAppliedDateComparison())  {
         return typeMap;
      }

      List<ChartAggregateRef> aggs = cinfo.getAestheticAggregateRefs(true);

      for(ChartAggregateRef ref : aggs) {
         if(ref == null) {
            continue;
         }

         typeMap.put(ref.getFullName(true), ref.getRTChartType());
      }

      return typeMap;
   }

   @Autowired
   private VisualFrameModelFactoryService vfactoryService;
   @Autowired
   private AestheticRefModelFactory afactoryService;
}
