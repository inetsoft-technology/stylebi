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
package inetsoft.uql.viewsheet.vslayout;

import inetsoft.report.internal.license.LicenseManager;
import inetsoft.sree.security.Organization;
import inetsoft.sree.security.OrganizationManager;
import inetsoft.storage.*;
import inetsoft.util.ConfigurationContext;
import inetsoft.util.Tool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.*;

import java.io.InputStream;
import java.security.Principal;
import java.util.*;
import java.util.concurrent.*;

/**
 * Class that manages device descriptors.
 *
 * @since 12.1
 */
public final class DeviceRegistry {
   /**
    * Creates a new instance of <tt>DeviceRegistry</tt>.
    */
   public DeviceRegistry(KeyValueStorageManager kvStorageManager) {
      this.kvStorageManager = kvStorageManager;
      storage = kvStorageManager.getStorage("devices", new LoadDevicesTask("devices"));
   }

   /**
    * Gets the "devices" storage, re-fetching it if the cached reference is null or has been
    * closed. The shared "devices" KeyValueStorage can be LRU-evicted and closed by
    * KeyValueStorageManager once more than 50 stores are open at once (easily reached in
    * multi-tenant setups after creating several organizations). A closed store's stream()/
    * keys() return empty while get()/put()/replaceAll() still mutate the underlying replicated
    * map, so holding on to a closed instance makes getDevices() silently read empty and the
    * next setDevices() wipe every device. All callers of this method are already
    * synchronized, so the closed instance could in theory be evicted again between this check
    * and its use by an unrelated thread's cache maintenance -- that only causes one transient
    * empty read (never data loss, since writes don't check isClosed), so it doesn't need
    * further guarding.
    */
   private KeyValueStorage<DeviceInfo> getStorage() {
      if(storage == null || storage.isClosed()) {
         storage = kvStorageManager.getStorage("devices", new LoadDevicesTask("devices"));
      }

      return storage;
   }

   /**
    * Gets the singleton registry instance.
    *
    * @return the registry.
    */
   public static synchronized DeviceRegistry getRegistry() {
      return ConfigurationContext.getContext().getSpringBean(DeviceRegistry.class);
   }


   /**
    * Gets the list of defined mobile devices.
    *
    * @return the mobile devices.
    */
   public synchronized DeviceInfo[] getDevices() {
      return getStorage().stream()
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
         getStorage().replaceAll(map).get(60L, TimeUnit.SECONDS);
      }
      catch(InterruptedException | ExecutionException | TimeoutException e) {
         LOG.error("Failed to save devices", e);
      }
   }

   public synchronized void setDevice(DeviceInfo device) {
      try {
         getStorage().put(device.getId(), device).get(10L, TimeUnit.SECONDS);
      }
      catch(InterruptedException | ExecutionException | TimeoutException e) {
         throw new RuntimeException(e);
      }
   }

   public synchronized void deleteDevice(String id) {
      try {
         getStorage().remove(id).get(10L, TimeUnit.SECONDS);
      }
      catch(InterruptedException | ExecutionException | TimeoutException e) {
         throw new RuntimeException(e);
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
      return getStorage().get(id);
   }

   /**
    * Determines whether the organization associated with the given principal is allowed to
    * manage device profiles. Device profiles are stored globally rather than per-organization,
    * so in multi-org enterprise deployments only site admins or users in the default
    * organization are allowed to edit them -- otherwise an org admin could modify device
    * profiles used by other organizations.
    *
    * @param principal the user attempting to manage device profiles.
    *
    * @return <tt>true</tt> if the principal's organization is allowed to manage device profiles.
    */
   public static boolean isOrgAllowedToEditDevices(Principal principal) {
      return !LicenseManager.isEnterprise() ||
         OrganizationManager.getInstance().isSiteAdmin(principal) ||
         OrganizationManager.getInstance().getCurrentOrgID(principal).toLowerCase()
            .equals(Organization.getDefaultOrganizationID());
   }

   private final KeyValueStorageManager kvStorageManager;
   private KeyValueStorage<DeviceInfo> storage;

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
