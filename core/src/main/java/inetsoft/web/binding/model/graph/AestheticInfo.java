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
package inetsoft.web.binding.model.graph;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import inetsoft.web.binding.model.graph.aesthetic.VisualFrameModel;

/**
 * This class is used as a model for AestheticRef.
 *
 * @version 12.3
 * @author  InetSoft Technology Corp
 */
@JsonTypeInfo(
   use = JsonTypeInfo.Id.NAME,
   include = JsonTypeInfo.As.PROPERTY,
   property = "classType")
public class AestheticInfo {
   /**
    * Constructor.
    */
   public AestheticInfo() {
   }

   public void setFullName(String name) {
      this.fullName = name;
   }

   public String getFullName() {
      return fullName;
   }

   public void setFrame(VisualFrameModel frame) {
      this.frame = frame;
   }

   public VisualFrameModel getFrame() {
      return frame;
   }

   public void setDataInfo(ChartRefModel dataInfo) {
      this.dataInfo = dataInfo;
   }

   public ChartRefModel getDataInfo() {
      return dataInfo;
   }

   private String fullName;
   private VisualFrameModel frame;
   private ChartRefModel dataInfo;
}

