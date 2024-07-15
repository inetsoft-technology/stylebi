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
package inetsoft.staging;

import org.immutables.value.Value;

import java.nio.file.Path;
import java.util.Optional;

/**
 * {@code StagingOptions} contains information about an external configuration source and the
 * staging target.
 */
@Value.Immutable
public interface StagingOptions {
   /**
    * The URL of the external configuration to install.
    */
   String url();

   /**
    * The username for the external configuration URL.
    */
   Optional<String> username();

   /**
    * The password for the external configuration URL.
    */
   @Value.Auxiliary
   Optional<char[]> password();

   /**
    * The location of the SSH keyfile for the external configuration connection. If specified,
    * {@link #password()} should be the password for the key file, if required.
    */
   Optional<Path> keyFile();

   /**
    * The branch or tag containing the configuration if using Git.
    */
   Optional<String> branch();

   /**
    * The path, relative to the URL, containing the configuration. If not specified, the URL will be
    * used as the base of the configuration.
    */
   Optional<String> path();

   /**
    * If true, use the root directory instead of the user's home directory when using SFTP. The
    * default value is false.
    */
   @Value.Default
   default boolean sftpUseRootDir() {
      return false;
   }

   /**
    * The path to the directory into which the configuration will be staged.
    */
   Path stagingDirectory();

   /**
    * The path to a file whose presence indicates that the external configuration is ready to copy.
    * If set, the staging will block until this file exists.
    */
   Optional<Path> readyFile();

   static Builder builder() {
      return new Builder();
   }

   final class Builder extends ImmutableStagingOptions.Builder {
   }
}
