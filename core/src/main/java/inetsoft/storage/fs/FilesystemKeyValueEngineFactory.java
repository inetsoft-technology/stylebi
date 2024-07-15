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
package inetsoft.storage.fs;

import com.google.auto.service.AutoService;
import inetsoft.storage.KeyValueEngine;
import inetsoft.storage.KeyValueEngineFactory;
import inetsoft.util.config.FilesystemConfig;
import inetsoft.util.config.InetsoftConfig;

import java.nio.file.Paths;
import java.util.Objects;

/**
 * {@code AWSKeyValueEngineFactory} is an implementation of {@link KeyValueEngineFactory} that
 * creates instances of {@link FilesystemKeyValueEngine}.
 */
@AutoService(KeyValueEngineFactory.class)
public class FilesystemKeyValueEngineFactory implements KeyValueEngineFactory {
   @Override
   public String getType() {
      return "filesystem";
   }

   @Override
   public KeyValueEngine createEngine(InetsoftConfig config) {
      FilesystemConfig filesystem = config.getKeyValue().getFilesystem();
      Objects.requireNonNull(filesystem, "The filesystem configuration cannot be null");
      Objects.requireNonNull(
         filesystem.getDirectory(), "The key-value directory cannot be null");
      return new FilesystemKeyValueEngine(Paths.get(filesystem.getDirectory()));
   }
}
