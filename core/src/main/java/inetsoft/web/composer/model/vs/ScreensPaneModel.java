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

import java.util.ArrayList;
import java.util.List;

public class ScreensPaneModel {
   public List<VSDeviceLayoutDialogModel> getDeviceLayouts() {
      if(deviceLayouts == null) {
         deviceLayouts = new ArrayList<>();
      }

      return deviceLayouts;
   }

   public void setDeviceLayouts(List<VSDeviceLayoutDialogModel> deviceLayouts) {
      this.deviceLayouts = deviceLayouts;
   }

   public List<ScreenSizeDialogModel> getDevices() {
      if(devices == null) {
         devices = new ArrayList<>();
      }

      return devices;
   }

   public void setDevices(List<ScreenSizeDialogModel> devices) {
      this.devices = devices;
   }

   public boolean isEditDevicesAllowed() {
      return editDevicesAllowed ;
   }

   public void setEditDevicesAllowed(boolean editDevicesAllowed) {
      this.editDevicesAllowed = editDevicesAllowed;
   }

   public boolean isTargetScreen() {
      return targetScreen;
   }

   public void setTargetScreen(boolean targetScreen) {
      this.targetScreen = targetScreen;
   }

   public boolean isScaleToScreen() {
      return scaleToScreen;
   }

   public void setScaleToScreen(boolean scaleToScreen) {
      this.scaleToScreen = scaleToScreen;
   }

   public boolean isFitToWidth() {
      return fitToWidth;
   }

   public void setFitToWidth(boolean fitToWidth) {
      this.fitToWidth = fitToWidth;
   }

   public int getTemplateWidth() {
      return templateWidth;
   }

   public void setTemplateWidth(int templateWidth) {
      this.templateWidth = templateWidth;
   }

   public int getTemplateHeight() {
      return templateHeight;
   }

   public void setTemplateHeight(int templateHeight) {
      this.templateHeight = templateHeight;
   }

   public VSPrintLayoutDialogModel getPrintLayout() {
      return printLayout;
   }

   public void setPrintLayout(VSPrintLayoutDialogModel printLayout) {
      this.printLayout = printLayout;
   }

   public boolean isBalancePadding() {
      return balancePadding;
   }

   public void setBalancePadding(boolean balancePadding) {
      this.balancePadding = balancePadding;
   }

   private List<VSDeviceLayoutDialogModel> deviceLayouts;
   private List<ScreenSizeDialogModel> devices;
   private boolean editDevicesAllowed;
   private boolean targetScreen;
   private boolean scaleToScreen;
   private boolean fitToWidth;
   private int templateWidth;
   private int templateHeight;
   private VSPrintLayoutDialogModel printLayout;
   private boolean balancePadding;
}
