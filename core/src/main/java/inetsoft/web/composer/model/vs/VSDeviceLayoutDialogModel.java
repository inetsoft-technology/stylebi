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

import java.io.Serializable;
import java.util.List;

public class VSDeviceLayoutDialogModel implements Serializable {
   public String getName() {
      return name;
   }

   public void setName(String name) {
      this.name = name;
   }

   public boolean isMobileOnly() {
      return mobileOnly;
   }

   public void setMobileOnly(boolean mobileOnly) {
      this.mobileOnly = mobileOnly;
   }

//   public float getScaleFont() {
//      return scaleFont;
//   }
//
//   public void setScaleFont(float scaleFont) {
//      this.scaleFont = scaleFont;
//   }

   public List<String> getSelectedDevices() {
      return selectedDevices;
   }

   public void setSelectedDevices(List<String> selectedDevices) {
      this.selectedDevices = selectedDevices;
   }

   public String getId() {
      return id;
   }

   public void setId(String id) {
      this.id = id;
   }

   private String name;
   private boolean mobileOnly;
//   private float scaleFont;
   private List<String> selectedDevices;
   private String id;
}
