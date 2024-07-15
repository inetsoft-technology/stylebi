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
package inetsoft.uql.viewsheet.vslayout;

import inetsoft.storage.*;
import inetsoft.util.SingletonManager;
import inetsoft.util.Tool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.*;

import java.io.InputStream;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.function.Supplier;

/**
 * Class that manages device descriptors.
 *
 * @since 12.1
 */
public final class DeviceRegistry {
   /**
    * Creates a new instance of <tt>DeviceRegistry</tt>.
    */
   public DeviceRegistry() {
      storage = SingletonManager
         .getInstance(KeyValueStorage.class, "devices",
                      (Supplier<LoadDevicesTask>)() -> new LoadDevicesTask("devices"));
   }

   /**
    * Gets the singleton registry instance.
    *
    * @return the registry.
    */
   public static synchronized DeviceRegistry getRegistry() {
      return SingletonManager.getInstance(DeviceRegistry.class);
   }


   /**
    * Gets the list of defined mobile devices.
    *
    * @return the mobile devices.
    */
   public synchronized DeviceInfo[] getDevices() {
      return storage.stream()
         .map(KeyValuePair::getValue)
         .toArray(DeviceInfo[]::new);
   }

   /**
    * Sets the list of defined mobile devices.
    *
    * @param devices the mobile devices.
    */
   public synchronized void setDevices(Collection<DeviceInfo> devices) {
      SortedMap<String, DeviceInfo> map = new TreeMap<>();

      for(DeviceInfo device : devices) {
         map.put(device.getId(), device);
      }

      try {
         storage.replaceAll(map).get();
      }
      catch(InterruptedException | ExecutionException e) {
         LOG.error("Failed to save devices", e);
      }
   }

   /**
    * Gets the mobile device with the specified identifier.
    *
    * @param id the device identifier.
    *
    * @return the matching device or <tt>null</tt> if not found.
    */
   public synchronized DeviceInfo getDevice(String id) {
      return storage.get(id);
   }

   private final KeyValueStorage<DeviceInfo> storage;

   private static final Logger LOG = LoggerFactory.getLogger(DeviceRegistry.class);

   private static final class LoadDevicesTask extends LoadKeyValueTask<DeviceInfo> {
      public LoadDevicesTask(String id) {
         super(id);
      }

      @Override
      protected Class<DeviceInfo> initialize(Map<String, DeviceInfo> map) {
         try(InputStream input = getClass().getResourceAsStream("devices.xml")) {
            Document document = Tool.parseXML(input);
            NodeList nodes = document.getDocumentElement().getElementsByTagName("deviceInfo");

            for(int i = 0; i < nodes.getLength(); i++) {
               DeviceInfo device = new DeviceInfo();
               device.parseXML((Element) nodes.item(i));
               map.put(device.getId(), device);
            }
         }
         catch(Exception e) {
            LOG.warn("Failed to initialize mobile device list", e);
         }

         return DeviceInfo.class;
      }
   }
}
