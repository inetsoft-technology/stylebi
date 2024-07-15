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
package inetsoft.util.config;

import java.io.Serializable;
import java.util.Objects;

/**
 * {@code FirestoreConfig} contains the configuration for the Google Firestore key-value storage
 * provider.
 */
@InetsoftConfigBean
public class FirestoreConfig implements Serializable {
   /**
    * The path to the service account JSON file. This is required unless using an emulator.
    */
   public String getServiceAccountFile() {
      return serviceAccountFile;
   }

   public void setServiceAccountFile(String serviceAccountFile) {
      this.serviceAccountFile = serviceAccountFile;
   }

   /**
    * Base64 encoded service account json
    */
   public String getServiceAccountJson() {
      return serviceAccountJson;
   }

   public void setServiceAccountJson(String serviceAccountJson) {
      this.serviceAccountJson = serviceAccountJson;
   }

   /**
    * A flag indicating if the Firestore service is being served by an emulator.
    */
   public boolean isEmulated() {
      return emulated;
   }

   public void setEmulated(boolean emulated) {
      this.emulated = emulated;
   }

   /**
    * The 'hostname:port' for the emulator. This is required if <i>emulated</i> is true and ignored
    * otherwise.
    */
   public String getEmulatorHost() {
      return emulatorHost;
   }

   public void setEmulatorHost(String emulatorHost) {
      this.emulatorHost = emulatorHost;
   }

   /**
    * The name of the collection.
    */
   public String getCollection() {
      return collection;
   }

   public void setCollection(String collection) {
      Objects.requireNonNull(collection, "The Firestore collection is required");
      this.collection = collection;
   }

   private String serviceAccountFile;
   private String serviceAccountJson;
   private boolean emulated = false;
   private String emulatorHost;
   private String collection;
}
