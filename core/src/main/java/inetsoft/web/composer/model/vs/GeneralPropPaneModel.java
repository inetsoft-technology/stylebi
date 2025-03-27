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
package inetsoft.web.composer.model.vs;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import inetsoft.uql.viewsheet.internal.PopVSAssemblyInfo.PopLocation;

import java.io.Serializable;

@JsonIgnoreProperties(ignoreUnknown = true)
public class GeneralPropPaneModel implements Serializable {
   public BasicGeneralPaneModel getBasicGeneralPaneModel() {
      if(basicGeneralPaneModel == null) {
         basicGeneralPaneModel = new BasicGeneralPaneModel();
      }

      return basicGeneralPaneModel;
   }

   public void setBasicGeneralPaneModel(
      BasicGeneralPaneModel basicGeneralPaneModel)
   {
      this.basicGeneralPaneModel = basicGeneralPaneModel;
   }

   public boolean isSubmitOnChange() {
      return submitOnChange;
   }

   public void setSubmitOnChange(boolean submitOnChange) {
      this.submitOnChange = submitOnChange;
   }

   public boolean isShowEnabledGroup() {
      return showEnabledGroup;
   }

   public void setShowEnabledGroup(boolean showEnabledGroup) {
      this.showEnabledGroup = showEnabledGroup;
   }

   public String getEnabled() {
      return enabled;
   }

   public void setEnabled(String enabled) {
      this.enabled = enabled;
   }

   public boolean isShowSubmitCheckbox() {
      return showSubmitCheckbox;
   }

   public void setShowSubmitCheckbox(boolean showSubmitCheckbox) {
      this.showSubmitCheckbox = showSubmitCheckbox;
   }

   public void setPopLocation(PopLocation popLocation) {this.popLocation = popLocation;}

   public PopLocation getPopLocation() {return popLocation;}

   private boolean submitOnChange, showEnabledGroup, showSubmitCheckbox;
   private BasicGeneralPaneModel basicGeneralPaneModel;
   private String enabled;
   private PopLocation popLocation;
}
