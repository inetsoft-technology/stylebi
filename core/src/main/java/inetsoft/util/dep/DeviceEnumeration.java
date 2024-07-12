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
package inetsoft.util.dep;

import inetsoft.uql.viewsheet.vslayout.DeviceInfo;
import inetsoft.uql.viewsheet.vslayout.DeviceRegistry;

import java.util.Arrays;
import java.util.Iterator;

/**
 * Enumeration of the installed devices.
 *
 * @since 12.1
 */
public class DeviceEnumeration implements XAssetEnumeration<DeviceAsset> {
   /**
    * Creates a new instance of <tt>DeviceEnumeration</tt>.
    */
   public DeviceEnumeration() {
      devices = Arrays.asList(
         DeviceRegistry.getRegistry().getDevices()).iterator();
   }

   @Override
   public boolean hasMoreElements() {
      return devices.hasNext();
   }

   @Override
   public DeviceAsset nextElement() {
      return new DeviceAsset(devices.next().getId());
   }

   private final Iterator<DeviceInfo> devices;
}
