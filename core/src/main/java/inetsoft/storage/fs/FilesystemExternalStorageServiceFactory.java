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
package inetsoft.storage.fs;

import com.google.auto.service.AutoService;
import inetsoft.storage.ExternalStorageService;
import inetsoft.storage.ExternalStorageServiceFactory;
import inetsoft.util.config.*;

import java.nio.file.Paths;
import java.util.Objects;

@AutoService(ExternalStorageServiceFactory.class)
public class FilesystemExternalStorageServiceFactory implements ExternalStorageServiceFactory {
   @Override
   public String getType() {
      return "filesystem";
   }

   @Override
   public ExternalStorageService createExternalStorageService(InetsoftConfig config) {
      ExternalStorageConfig external = config.getExternalStorage();
      Objects.requireNonNull(external, "The external storage must be configured");
      FilesystemConfig filesystem = external.getFilesystem();
      Objects.requireNonNull(filesystem, "The external storage filesystem must be configured");
      String basePath = filesystem.getDirectory();
      Objects.requireNonNull(filesystem, "The external storage filesystem directory is required");
      return new FilesystemExternalStorageService(Paths.get(basePath));
   }
}
