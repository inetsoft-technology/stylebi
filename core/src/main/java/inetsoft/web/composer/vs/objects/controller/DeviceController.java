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

package inetsoft.web.composer.vs.objects.controller;

import inetsoft.report.composition.event.AssetEventUtil;
import inetsoft.sree.internal.SUtil;
import inetsoft.uql.asset.*;
import inetsoft.uql.viewsheet.vslayout.DeviceInfo;
import inetsoft.uql.viewsheet.vslayout.DeviceRegistry;
import inetsoft.util.audit.ActionRecord;
import inetsoft.util.audit.Audit;
import inetsoft.web.composer.model.vs.ScreenSizeDialogModel;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.sql.Timestamp;
import java.util.UUID;

@RestController
public class DeviceController {
   @PostMapping("/api/composer/device/new")
   @ResponseBody
   public void newDevice(@RequestBody ScreenSizeDialogModel device, Principal principal) {
      String userName = SUtil.getUserName(principal);
      Timestamp actionTimestamp = new Timestamp(System.currentTimeMillis());
      ActionRecord actionRecord = new ActionRecord(userName, ActionRecord.ACTION_NAME_CREATE,
                                                   device.getLabel(), ActionRecord.OBJECT_TYPE_DEVICE,
                                                   actionTimestamp, ActionRecord.ACTION_STATUS_SUCCESS,
                                                   null);

      DeviceRegistry registry = DeviceRegistry.getRegistry();
      DeviceInfo deviceInfo = new DeviceInfo();
      deviceInfo.setId(device.getId());
      deviceInfo.setName(device.getLabel());
      deviceInfo.setDescription(device.getDescription());
      deviceInfo.setMinWidth(device.getMinWidth());
      deviceInfo.setMaxWidth(device.getMaxWidth());
      deviceInfo.setLastModified(System.currentTimeMillis());
      deviceInfo.setLastModifiedBy(principal.getName());
      registry.setDevice(deviceInfo);
      Audit.getInstance().auditAction(actionRecord, principal);
   }

   @PostMapping("/api/composer/device/edit")
   @ResponseBody
   public void editDevice(@RequestBody ScreenSizeDialogModel device, Principal principal) {
      String userName = SUtil.getUserName(principal);
      Timestamp actionTimestamp = new Timestamp(System.currentTimeMillis());
      ActionRecord actionRecord = new ActionRecord(userName, ActionRecord.ACTION_NAME_EDIT,
                                                   device.getLabel(), ActionRecord.OBJECT_TYPE_DEVICE,
                                                   actionTimestamp, ActionRecord.ACTION_STATUS_SUCCESS,
                                                   null);

      DeviceRegistry registry = DeviceRegistry.getRegistry();
      DeviceInfo deviceInfo = registry.getDevice(device.getId());
      deviceInfo.setId(deviceInfo.getId());
      deviceInfo.setName(device.getLabel());
      deviceInfo.setDescription(device.getDescription());
      deviceInfo.setMinWidth(device.getMinWidth());
      deviceInfo.setMaxWidth(device.getMaxWidth());
      deviceInfo.setLastModified(System.currentTimeMillis());
      deviceInfo.setLastModifiedBy(principal.getName());
      registry.setDevice(deviceInfo);
      Audit.getInstance().auditAction(actionRecord, principal);
   }

   @PostMapping("/api/composer/device/delete")
   @ResponseBody
   public void deleteDevice(@RequestBody ScreenSizeDialogModel device, Principal principal) {
      String userName = SUtil.getUserName(principal);
      Timestamp actionTimestamp = new Timestamp(System.currentTimeMillis());
      ActionRecord actionRecord = new ActionRecord(userName, ActionRecord.ACTION_NAME_DELETE,
                                                   device.getLabel(), ActionRecord.OBJECT_TYPE_DEVICE,
                                                   actionTimestamp, ActionRecord.ACTION_STATUS_SUCCESS,
                                                   null);

      DeviceRegistry registry = DeviceRegistry.getRegistry();
      registry.deleteDevice(device.getId());
      AssetEntry entry = new AssetEntry(AssetRepository.COMPONENT_SCOPE,
                                        AssetEntry.Type.DEVICE, device.getId(), null);
      DependencyHandler.getInstance().deleteDependenciesKey(entry);
      Audit.getInstance().auditAction(actionRecord, principal);
   }
}
