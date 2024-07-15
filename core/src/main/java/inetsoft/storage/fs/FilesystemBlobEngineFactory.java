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
import inetsoft.storage.BlobEngine;
import inetsoft.storage.BlobEngineFactory;
import inetsoft.util.config.FilesystemConfig;
import inetsoft.util.config.InetsoftConfig;

import java.nio.file.Paths;
import java.util.Objects;

/**
 * {@code FilesystemBlobEngineFactory} is an implementation of {@link BlobEngineFactory} that
 * creates instances of {@link FilesystemBlobEngine}.
 */
@AutoService(BlobEngineFactory.class)
public class FilesystemBlobEngineFactory implements BlobEngineFactory {
   @Override
   public String getType() {
      return "filesystem";
   }

   @Override
   public BlobEngine createEngine(InetsoftConfig config) {
      FilesystemConfig filesystem = config.getBlob().getFilesystem();
      Objects.requireNonNull(filesystem, "The filesystem configuration cannot be null");
      Objects.requireNonNull(filesystem.getDirectory(), "The local directory cannot be null");
      return new FilesystemBlobEngine(Paths.get(filesystem.getDirectory()));
   }
}
