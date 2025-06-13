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
package inetsoft.util.dep;

import inetsoft.sree.security.*;
import inetsoft.uql.viewsheet.vslayout.DeviceInfo;
import inetsoft.uql.viewsheet.vslayout.DeviceRegistry;
import inetsoft.util.Tool;

import java.io.*;
import java.util.*;
import java.util.jar.JarOutputStream;
import java.util.zip.ZipEntry;

/**
 * Class that represents a device class definition.
 *
 * @since 12.1
 */
public class DeviceAsset extends AbstractXAsset {
   public static final String DEVICE = "DEVICE";
   public static final String DEVICE_ID_MAPPING = "xasset.device.idMapping";

   /**
    * Creates a new instance of <tt>DeviceAsset</tt>.
    */
   public DeviceAsset() {
   }

   /**
    * Creates a new instance of <tt>DeviceAsset</tt>.
    *
    * @param id the device identifier.
    */
   public DeviceAsset(String id)
   {
      this.id = id;
   }

   @Override
   public XAssetDependency[] getDependencies(List<XAssetDependency> list) {
      return new XAssetDependency[0];
   }

   @Override
   public String getPath() {
      return id;
   }

   @Override
   public String getType() {
      return DEVICE;
   }

   @Override
   public IdentityID getUser() {
      return null;
   }

   @Override
   public void parseIdentifier(String identifier) {
      String[] parts = identifier.split("\\^", -1);

      if(parts.length > 1 && getClass().getName().equals(parts[0])) {
         id = parts[1];
      }
   }

   @Override
   public void parseIdentifier(String path, IdentityID userIdentity) {
      this.id = path;
   }

   @Override
   public String toIdentifier() {
      return getClass().getName() + "^" + id;
   }

   @Override
   public synchronized void parseContent(InputStream input, XAssetConfig config, boolean isImport)
      throws Exception
   {
      if(id != null) {
         DeviceRegistry registry = DeviceRegistry.getRegistry();
         boolean overwriting = config != null && config.isOverwriting();
         DeviceInfo existing = registry.getDevice(id);

         if(overwriting || existing == null) {
            DeviceInfo device = new DeviceInfo();
            device.parseXML(Tool.parseXML(input).getDocumentElement());
            List<DeviceInfo> devices = new ArrayList<>(
               Arrays.asList(registry.getDevices()));

            if(existing == null) {
               for(DeviceInfo info : devices) {
                  if(info.getName().equals(device.getName())) {
                     existing = info;
                     break;
                  }
               }
            }

            if(overwriting || existing == null) {
               if(existing != null) {
                  // When overwriting existing devices, the existing ID is
                  // preserved so that existing viewsheet are not broken. The
                  // old ID of the imported device is replaced in the imported
                  // viewsheets.
                  Map<String, String> mapping =
                     config.getContextAttribute(DEVICE_ID_MAPPING);

                  if(mapping == null) {
                     mapping = new HashMap<>();
                     config.setContextAttribute(DEVICE_ID_MAPPING, mapping);
                  }

                  mapping.put(device.getId(), existing.getId());

                  existing.setName(device.getName());
                  existing.setDescription(device.getDescription());
                  existing.setMinWidth(device.getMinWidth());
                  existing.setMaxWidth(device.getMaxWidth());
                  existing.setCreated(device.getCreated());
                  existing.setLastModified(device.getLastModified());
                  existing.setCreatedBy(device.getCreatedBy());
                  existing.setLastModifiedBy(device.getLastModifiedBy());
               }
               else {
                  devices.add(device);
               }

               registry.setDevices(devices);
            }
         }
      }
   }

   @Override
   public synchronized boolean writeContent(OutputStream output) throws Exception {
      if(id != null) {
          DeviceInfo device = DeviceRegistry.getRegistry().getDevice(id);

         if(device != null) {
            JarOutputStream out = getJarOutputStream(output);
            ZipEntry zipEntry = new ZipEntry(
               getType() + "_" + replaceFilePath(toIdentifier()));
            out.putNextEntry(zipEntry);

            PrintWriter writer =
               new PrintWriter(new OutputStreamWriter(out, "UTF8"));
            device.writeXML(writer);
            writer.flush();
            return true;
         }
      }

      return false;
   }

   @Override
   public String toString() {
      if(id == null || DeviceRegistry.getRegistry().getDevice(id) == null) {
         return super.toString();
      }

      return DeviceRegistry.getRegistry().getDevice(id).getName();
   }

   public DeviceInfo getDeviceInfo() {
      return DeviceRegistry.getRegistry().getDevice(id);
   }

   @Override
   public boolean exists() {
      return getDeviceInfo() != null;
   }

   @Override
   public long getLastModifiedTime() {
      if(lastModifiedTime != 0) {
         return lastModifiedTime;
      }

      DeviceInfo info = getDeviceInfo();
      return info == null ? 0 : info.getLastModified();
   }

   @Override
   public Resource getSecurityResource() {
      return new Resource(ResourceType.DEVICE, "*");
   }

   private String id;
}
