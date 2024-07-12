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
package inetsoft.staging;

/**
 * {@code StagingProvider} is an interface for classes that stage initial configurations using one
 * or more protocols.
 */
public interface StagingProvider {
   /**
    * Stages the initial configuration.
    *
    * @param options the staging options.
    *
    * @throws Exception if the configuration could not be staged.
    */
   void stage(StagingOptions options) throws Exception;

   /**
    * Determines if the specified URL is supported by this provider.
    *
    * @param url the URL to test.
    *
    * @return {@code true} if the URL is supported or {@code false} if not.
    */
   boolean isUrlSupported(String url);
}
