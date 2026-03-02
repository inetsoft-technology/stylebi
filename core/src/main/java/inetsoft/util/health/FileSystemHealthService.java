/*
 * This file is part of StyleBI.
 * Copyright (C) 2025  InetSoft Technology
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

package inetsoft.util.health;

import inetsoft.sree.SreeEnv;
import inetsoft.util.SingletonManager;
import inetsoft.util.config.InetsoftConfig;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;

public class FileSystemHealthService {
   public static FileSystemHealthService getInstance() {
      return SingletonManager.getInstance(FileSystemHealthService.class);
   }

   public FileSystemStatus getStatus() {
      FileSystemStatus status = null;
      boolean enabled = "true".equals(SreeEnv.getProperty("health.fileSystem.enabled"));

      if(enabled) {
         List<FileSystemState> states = new ArrayList<>();
         String pathsProperty = SreeEnv.getProperty("health.fileSystem.paths");

         if(StringUtils.isEmpty(pathsProperty)) {
            pathsProperty = InetsoftConfig.getConfigFile().getAbsolutePath();
         }

         String[] paths = pathsProperty.split(",");

         for(String path : paths) {
            boolean available = true;

            try {
               Path file = inetsoft.util.FileSystemService.getInstance().getFile(path).toPath();
               Files.readAttributes(file, BasicFileAttributes.class);
            }
            catch(Exception e) {
               LOG.error("Failed to access file {}", path, e);
               available = false;
            }

            states.add(new FileSystemState(path, available));
         }

         status = new FileSystemStatus(states.size(), states.toArray(new FileSystemState[0]));
      }

      if(status == null) {
         status = new FileSystemStatus(0, new FileSystemState[0]);
      }

      return status;
   }

   private static final Logger LOG = LoggerFactory.getLogger(FileSystemHealthService.class);
}
