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

import inetsoft.uql.erm.DataRef;
import inetsoft.uql.viewsheet.graph.*;
import inetsoft.uql.viewsheet.graph.aesthetic.VisualFrameWrapper;
import inetsoft.web.binding.model.graph.*;
import inetsoft.web.binding.model.graph.aesthetic.VisualFrameModel;
import inetsoft.web.binding.service.graph.aesthetic.VisualFrameModelFactoryService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class AestheticRefModelFactory {
   @Autowired
   public AestheticRefModelFactory(VisualFrameModelFactoryService vFactoryService,
                                   ChartRefModelFactoryService cFactoryService)
   {
      this.vFactoryService = vFactoryService;
      this.cFactoryService = cFactoryService;
   }

   public AestheticInfo createAestheticInfo(AestheticRef aref, ChartInfo cinfo,
                                            OriginalDescriptor original)
   {
      if(aref == null || cinfo == null) {
         return null;
      }

      VisualFrameModel frameModel =
         vFactoryService.createVisualFrameModel(aref.getVisualFrameWrapper());

      // textfield is edited by set and gettextformat request,
      // don't need to send model for textframe.
      if(frameModel != null) {
         frameModel.setField(aref.getFullName());
      }

      DataRef dataRef = aref.getDataRef();
      ChartRefModel dataModel = null;

      if(dataRef instanceof ChartRef) {
         dataModel = cFactoryService.createRefModel(
            (ChartRef) dataRef, cinfo, original);
      }

      AestheticInfo model = new AestheticInfo();
      model.setDataInfo(dataModel);
      model.setFrame(frameModel);

      return model;
   }

   public AestheticRef pasteAestheticRef(ChartInfo cinfo, AestheticRef ref, AestheticInfo model) {
      if(model == null) {
         return null;
      }

      if(ref == null) {
         ref = createAestheticRef(cinfo);
      }

      if(model.getDataInfo() != null) {
         DataRef dataRef = cFactoryService.pasteChartRef(cinfo, model.getDataInfo());
         ref.setDataRef(dataRef);
      }

      VisualFrameWrapper wrapper = ref.getVisualFrameWrapper();

      if(wrapper != null) {
         VisualFrameWrapper nwrapper =
            vFactoryService.updateVisualFrameWrapper(wrapper, model.getFrame());
         ref.setVisualFrameWrapper(nwrapper);
      }
      else if(model.getFrame() != null) {
         ref.setVisualFrame(model.getFrame().createVisualFrame());
      }

      return ref;
   }

   private AestheticRef createAestheticRef(ChartInfo cinfo) {
      return new VSAestheticRef();
   }

   private final VisualFrameModelFactoryService vFactoryService;
   private final ChartRefModelFactoryService cFactoryService;
}
