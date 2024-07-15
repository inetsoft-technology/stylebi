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
package inetsoft.report.internal.license;

import inetsoft.sree.internal.cluster.*;

import java.lang.ref.WeakReference;
import java.util.*;

/**
 * {@code LicenseManager} manages the installed license keys and provides information about them.
 */
public abstract class LicenseStrategy implements AutoCloseable, MessageListener {
   /**
    * Determines if a valid license is installed.
    */
   public boolean isLicensed() {
      return true;
   }

   /**
    * Gets the CPU or thread key claimed by this server.
    *
    * @return the claimed license.
    */
   public abstract Optional<License> getClaimedLicense();

   /**
    * Gets the licenses claimed by this server. If a pooled license is installed, it returns all the
    * pooled licenses.
    *
    * @return the claimed licenses.
    */
   public abstract Set<License> getClaimedLicenses();

   /**
    * Gets all licenses installed on this server. This includes invalid licenses and licenses that
    * have not been claimed by this server.
    *
    * @return the installed licenses.
    */
   public abstract Set<License> getInstalledLicenses();

   /**
    * Gets the valid licenses being used on this server. This may include licenses not claimed by
    * this server. If incompatible licenses are installed, the unused licenses are not included even
    * if they are valid.
    *
    * @return the valid licenses.
    */
   public abstract Set<License> getValidLicenses();

   /**
    * Gets the licenses claimed by all servers in the cluster.
    *
    * @return a map of license keys to server addresses.
    */
   public abstract Map<String, String> getClaimedClusterLicenses();

   /**
    * Gets the licenses claimed by nodes in the cluster.
    *
    * @return a map of server addresses to license keys.
    */
   public abstract Map<String, String> getClaimedNodeLicenses();

   /**
    * Determines if a master license is being used by this server.
    *
    * @return {@code true} if a master license or {@code false} if not.
    */
   public abstract boolean isMasterLicense();

   /**
    * Gets the number of CPU cores allowed by the installed licenses. If a non-CPU license is being
    * used, this will return {@code 0}.
    *
    * @return the licensed CPU count.
    */
   public abstract int getLicensedCpuCount();

   /**
    * Gets the number of concurrent sessions allowed by the installed licenses. If a non-session
    * license is being used, this will return {@code 0}.
    *
    * @return the licensed session count.
    */
   public abstract int getConcurrentSessionCount();

   /**
    * Gets the number of named users allowed by the installed licenses. If a non-named user license
    * is being used, this will return {@code 0}.
    *
    * @return the licensed named user count.
    */
   public abstract int getNamedUserCount();

   /**
    * Gets the number of viewer-only sessions allowed by the installed licenses. If no viewer
    * licenses are being used, this will return {@code 0}.
    *
    * @return the licensed viewer session count.
    */
   public abstract int getViewerSessionCount();

   /**
    * Gets the number of viewer-only named user sessions allowed by the installed licenses. If no
    * named user viewer licenses are being used, this will return {@code 0}.
    *
    * @return the licensed named user viewer session count.
    */
   public abstract int getNamedUserViewerSessionCount();

   /**
    * Gets the installed scheduler instance licenses.
    *
    * @return the scheduler licenses.
    */
   public abstract Set<License> getSchedulerLicenses();

   /**
    * Gets the user names of the licensed named users.
    *
    * @return the named users or {@code null} if no named users are licensed.
    */
   public abstract Set<String> getNamedUsers();

   /**
    * Adds a server license key.
    *
    * @param key the key to add.
    */
   public abstract void addLicense(String key);

   /**
    * Removes an installed server license key.
    *
    * @param key the key to remove.
    */
   public abstract void removeLicense(String key);

   /**
    * Replaces an installed server license key.
    *
    * @param oldKey the key to replace.
    * @param newKey the new key.
    */
   public abstract void replaceLicense(String oldKey, String newKey);

   /**
    * Adds a scheduler instance license.
    *
    * @param key the key to add.
    */
   public abstract void addSchedulerLicense(String key);

   /**
    * Removes an installed scheduler instance license.
    *
    * @param key the key to remove.
    */
   public abstract void removeSchedulerLicense(String key);

   /**
    * Replaces an installed scheduler instance license.
    *
    * @param oldKey the key to replace.
    * @param newKey the new key.
    */
   public abstract void replaceSchedulerLicense(String oldKey, String newKey);

   /**
    * Reloads the licenses from the properties file.
    */
   public abstract void reload();

   /**
    * Gets the number of available CPU cores. If the number of physical cores is less than the
    * number of licensed cores, this method will attempt to set the CPU affinity to limit the
    * process to the licensed number of cores.
    *
    * @return the number of available CPU cores.
    */
   public abstract int getAvailableCpuCount();

   /**
    * Determines if the CPU affinity has been set by {@link #getAvailableCpuCount()}.
    *
    * @return {@code true} if the CPU affinity has been set or {@code false} if not.
    */
   public abstract boolean isAffinitySet();

   /**
    * Calculates the thread pool size based on the licensed threads or CPUs.
    *
    * @param softLimitCpuFactor the factor used to calculate the soft limit from the CPU core count.
    * @param hardLimitProperty  the name of the property containing the hard limit.
    * @param hardLimitFactor    the factor used to calculate the hard limit from the soft limit if
    *                           the property is not set.
    *
    * @return an array containing the soft limit and the hard limit.
    */
   public abstract int[] calculateThreadPoolSize(int softLimitCpuFactor, String hardLimitProperty,
                                                 int hardLimitFactor);

   /**
    * Gets a hash of the installed licenses so that it can be quickly checked if the licenses have
    * been changed.
    *
    * @return a hash of the licenses.
    */
   public abstract String getLicenseHash();

   /**
    * Starts the duplicate license server.
    */
   public abstract void startDuplicateLicenseServer();

   /**
    * Adds a license key violation to the log.
    *
    * @param message the error message.
    * @param id      an identifier of the source of the violation.
    */
   public abstract void addKeyViolation(String message, String id);

   /**
    * Parses a license key.
    * @param key the key to parse.
    * @return the parsed license.
    */
   public abstract License parseLicense(String key);

   /**
    * Adds a listener that is notified when a new license is claimed.
    *
    * @param l the listener to add.
    */
   public abstract void addClaimedLicenseListener(ClaimedLicenseListener l);

   /**
    * Removes a listener from the notification list.
    *
    * @param l the listener to remove.
    */
   public abstract void removeClaimedLicenseListener(ClaimedLicenseListener l);
}
