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
package inetsoft.storage;

import inetsoft.util.config.InetsoftConfig;

/**
 * {@code BlobEngineFactory} is the interface for classes that handle creating instances of
 * {@link BlobEngine} for different backends.
 */
public interface BlobEngineFactory {
   /**
    * Gets the type of storage backend.
    *
    * @return the storage backend type.
    */
   String getType();

   /**
    * Creates a new engine instance.
    *
    * @param config the system configuration.
    *
    * @return the new engine instance.
    */
   BlobEngine createEngine(InetsoftConfig config);
}
