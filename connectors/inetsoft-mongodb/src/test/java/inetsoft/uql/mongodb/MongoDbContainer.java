/*
 * inetsoft-mongodb - StyleBI is a business intelligence web application.
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
package inetsoft.uql.mongodb;

import org.testcontainers.containers.GenericContainer;

public class MongoDbContainer extends GenericContainer<MongoDbContainer> {
   public static final int MONGODB_PORT = 27017;
   public static final String DEFAULT_IMAGE_AND_TAG = "mongo:4.0.10";

   public MongoDbContainer() {
      this(DEFAULT_IMAGE_AND_TAG);
   }

   public MongoDbContainer(String image) {
      super(image);
      addExposedPort(MONGODB_PORT);
   }

   public Integer getPort() {
      return getMappedPort(MONGODB_PORT);
   }
}
