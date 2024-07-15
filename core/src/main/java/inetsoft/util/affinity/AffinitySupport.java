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
package inetsoft.util.affinity;

import com.sun.jna.Platform;
import org.slf4j.LoggerFactory;

/**
 * Interface for classes that provide platform-specific support for CPU
 * affinity.
 */
public interface AffinitySupport {
   /**
    * Gets the CPU affinity for this process.
    *
    * @return the indexes of the processors that are associated with the current
    *         process.
    *
    * @throws Exception if the affinity could not be obtained.
    */
   int[] getAffinity() throws Exception;

   /**
    * Sets the CPU affinity for this process.
    *
    * @param processors the indexes of the processors that are associated with
    *                   the current process.
    *
    * @throws Exception if the affinity could not be set.
    */
   void setAffinity(int[] processors) throws Exception;

   default void setThreadAffinity(int[] processors) throws Exception {
      // no-op
   }

   /**
    * The factory for <tt>AffinitySupport</tt> instances.
    */
   Factory FACTORY = new Factory();

   /**
    * Class that provides the platform-specific instance of
    * <tt>AffinitySupport</tt>.
    */
   final class Factory {
      /**
       * Gets a platform-specific instance of <tt>AffinitySupport</tt>.
       *
       * @return the platform-specific instance.
       */
      public synchronized AffinitySupport getInstance() {
         if(instance == null) {
            String className;

            if(Platform.isWindows()) {
               className = "inetsoft.util.affinity.WindowsAffinitySupport";
            }
            else if(Platform.isMac()) {
               throw new IllegalStateException("Thread affinity is not supported on OSX");
            }
            else {
               className = "inetsoft.util.affinity.PosixAffinitySupport";
            }

            try {
               instance = (AffinitySupport) Class.forName(className).newInstance();
            }
            catch(Exception e) {
               LoggerFactory.getLogger(AffinitySupport.class).debug("Failed to create affinity support", e);
            }
         }

         return instance;
      }

      private AffinitySupport instance;
   }
}
