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

import inetsoft.sree.SreeEnv;
import inetsoft.sree.internal.cluster.MessageEvent;
import inetsoft.sree.internal.cluster.MessageListener;
import inetsoft.sree.security.IdentityID;
import inetsoft.util.SingletonManager;

import java.util.*;

/**
 * {@code LicenseManager} manages the installed license keys and provides information about them.
 */
public class LicenseManager implements AutoCloseable, MessageListener {
   public enum LicenseComponent {
      FORM("Form");

      private final String webExtName;

      LicenseComponent(String webExtName) {
         this.webExtName = webExtName;
      }

   }

   /**
    * Creates a new instance of {@code LicenseManager}.
    */
   public LicenseManager() {
      try {
         strategy = (LicenseStrategy)
            Class.forName("inetsoft.enterprise.license.EnterpriseLicenseStrategy")
               .getConstructor().newInstance();
      }
      catch(Exception ex) {
         strategy = new NoopLicenseStrategy();
      }
   }

   /**
    * Gets the singleton instance of {@code LicenseManager}.
    *
    * @return the manager instance.
    */
   public static LicenseManager getInstance() {
      return SingletonManager.getInstance(LicenseManager.class);
   }

   /**
    * Utility to check if a component is available.
    *
    * @param component component string.
    */
   public static boolean isComponentAvailable(LicenseComponent component) {
      try {
         if(Objects.requireNonNull(component) == LicenseComponent.FORM) {
            return !"false".equals(SreeEnv.getProperty("vs.form.enabled"));
         }

         return true;
      }
      catch(Exception ignore) {
         return false;
      }
   }

   /**
    * Determines if a valid license is installed.
    */
   public boolean isLicensed() {
      return strategy.isLicensed();
   }

   /**
    * Check if the enterprise features are included.
    */
   public boolean isEnterprise() {
      //return !"false".equals(SreeEnv.getProperty("inetsoft.enterprise"));
      try {
         Class.forName("inetsoft.enterprise.EnterpriseConfig");
         return true;
      }
      catch(Exception ex) {
         return false;
      }
   }

   /**
    * Gets the licenses claimed by this server. If a pooled license is installed, it returns all the
    * pooled licenses.
    *
    * @return the claimed licenses.
    */
   public Set<License> getClaimedLicenses() {
      return strategy.getClaimedLicenses();
   }

   /**
    * Gets all licenses installed on this server. This includes invalid licenses and licenses that
    * have not been claimed by this server.
    *
    * @return the installed licenses.
    */
   public Set<License> getInstalledLicenses() {
      return strategy.getInstalledLicenses();
   }

   /**
    * Gets the valid licenses being used on this server. This may include licenses not claimed by
    * this server. If incompatible licenses are installed, the unused licenses are not included even
    * if they are valid.
    *
    * @return the valid licenses.
    */
   public Set<License> getValidLicenses() {
      return strategy.getValidLicenses();
   }

   /**
    * Gets the licenses claimed by all servers in the cluster.
    *
    * @return a map of license keys to server addresses.
    */
   public Map<String, String> getClaimedClusterLicenses() {
      return strategy.getClaimedClusterLicenses();
   }

   /**
    * Gets the licenses claimed by nodes in the cluster.
    *
    * @return a map of server addresses to license keys.
    */
   public Map<String, String> getClaimedNodeLicenses() {
      return strategy.getClaimedNodeLicenses();
   }

   /**
    * Determines if a master license is being used by this server.
    *
    * @return {@code true} if a master license or {@code false} if not.
    */
   public boolean isMasterLicense() {
      return strategy.isMasterLicense();
   }

   /**
    * Gets the number of CPU cores allowed by the installed licenses. If a non-CPU license is being
    * used, this will return {@code 0}.
    *
    * @return the licensed CPU count.
    */
   public int getLicensedCpuCount() {
      return strategy.getLicensedCpuCount();
   }

   /**
    * Gets the number of concurrent sessions allowed by the installed licenses. If a non-session
    * license is being used, this will return {@code 0}.
    *
    * @return the licensed session count.
    */
   public int getConcurrentSessionCount() {
      return strategy.getConcurrentSessionCount();
   }

   /**
    * Gets the number of named users allowed by the installed licenses. If a non-named user license
    * is being used, this will return {@code 0}.
    *
    * @return the licensed named user count.
    */
   public int getNamedUserCount() {
      return strategy.getNamedUserCount();
   }

   /**
    * Gets the number of viewer-only sessions allowed by the installed licenses. If no viewer
    * licenses are being used, this will return {@code 0}.
    *
    * @return the licensed viewer session count.
    */
   public int getViewerSessionCount() {
      return strategy.getViewerSessionCount();
   }

   /**
    * Gets the number of viewer-only named user sessions allowed by the installed licenses. If no
    * named user viewer licenses are being used, this will return {@code 0}.
    *
    * @return the licensed named user viewer session count.
    */
   public int getNamedUserViewerSessionCount() {
      return strategy.getNamedUserViewerSessionCount();
   }

   /**;
    * Gets the user names of the licensed named users.
    *
    * @return the named users or {@code null} if no named users are licensed.
    */
   public Set<IdentityID> getNamedUsers() {
      return strategy.getNamedUsers();
   }

   /**
    * Process the user, role, group changed, it may cause the count of the administrators is changed.
    * named user will sort administrators come first, so clear the cache.
    */
   public void userChanged() {
      strategy.userChanged();
   }


   /**
    * Adds a server license key.
    *
    * @param key the key to add.
    */
   public void addLicense(String key) {
      strategy.addLicense(key);
   }

   /**
    * Removes an installed server license key.
    *
    * @param key the key to remove.
    */
   public void removeLicense(String key) {
      strategy.removeLicense(key);
   }

   /**
    * Replaces an installed server license key.
    *
    * @param oldKey the key to replace.
    * @param newKey the new key.
    */
   public void replaceLicense(String oldKey, String newKey) {
      strategy.replaceLicense(oldKey, newKey);
   }

   /**
    * Reloads the licenses from the properties file.
    */
   public void reload() {
      strategy.reload();
   }

   /**
    * Gets the number of available CPU cores. If the number of physical cores is less than the
    * number of licensed cores, this method will attempt to set the CPU affinity to limit the
    * process to the licensed number of cores.
    *
    * @return the number of available CPU cores.
    */
   public int getAvailableCpuCount() {
      return strategy.getAvailableCpuCount();
   }

   /**
    * Determines if the CPU affinity has been set by {@link #getAvailableCpuCount()}.
    *
    * @return {@code true} if the CPU affinity has been set or {@code false} if not.
    */
   public boolean isAffinitySet() {
      return strategy.isAffinitySet();
   }

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
   public int[] calculateThreadPoolSize(int softLimitCpuFactor, String hardLimitProperty,
                                        int hardLimitFactor)
   {
      return strategy.calculateThreadPoolSize(softLimitCpuFactor, hardLimitProperty, hardLimitFactor);
   }

   @Override
   public void close() throws Exception {
      strategy.close();
   }

   @Override
   public void messageReceived(MessageEvent event) {
      strategy.messageReceived(event);
   }

   /**
    * Gets a hash of the installed licenses so that it can be quickly checked if the licenses have
    * been changed.
    *
    * @return a hash of the licenses.
    */
   public String getLicenseHash() {
      return strategy.getLicenseHash();
   }

   /**
    * Starts the duplicate license server.
    */
   public void startDuplicateLicenseServer() {
      strategy.startDuplicateLicenseServer();
   }

   /**
    * Adds a license key violation to the log.
    *
    * @param message the error message.
    * @param id      an identifier of the source of the violation.
    */
   public void addKeyViolation(String message, String id) {
      strategy.addKeyViolation(message, id);
   }

   /**
    * Parses a license key.
    *
    * @param key the key to parse.
    *
    * @return the parsed license.
    */
   public License parseLicense(String key) {
      return strategy.parseLicense(key);
   }

   /**
    * Adds a listener that is notified when a new license is claimed.
    *
    * @param l the listener to add.
    */
   public void addClaimedLicenseListener(ClaimedLicenseListener l) {
      strategy.addClaimedLicenseListener(l);
   }

   /**
    * Removes a listener from the notification list.
    *
    * @param l the listener to remove.
    */
   public void removeClaimedLicenseListener(ClaimedLicenseListener l) {
      strategy.removeClaimedLicenseListener(l);
   }

   /**
    * Update the named users key valid.
    */
   public void updateNamedUserKeys() {
      Set<License> installedLicenses = getInstalledLicenses();

      for(License license : installedLicenses) {
         if(LicenseType.NAMED_USER == license.type() || LicenseType.INVALID == license.type()) {
            replaceLicense(license.key(), license.key());
         }
      }
   }

   public boolean hasNamedUserKeys() {
      Set<License> installedLicenses = getInstalledLicenses();

      for(License license : installedLicenses) {
         if(LicenseType.NAMED_USER == license.type()) {
            return true;
         }
      }

      return false;
   }

   public boolean isElasticLicense() {
      return strategy.getValidLicenses().stream()
         .anyMatch(l -> l.type() == LicenseType.ELASTIC);
   }

   public void startElasticPolling() {
      strategy.startElasticPolling();
   }

   public int getElasticRemainingHours() {
      return strategy.getElasticRemainingHours();
   }

   public int getElasticGraceHours() {
      return strategy.getElasticGraceHours();
   }

   public boolean isHostedLicense() {
      return strategy.getValidLicenses().stream()
         .anyMatch(l -> l.type() == LicenseType.HOSTED);
   }

   public boolean startHostedSession(String orgId, String user) {
      return strategy.startHostedSession(orgId, user);
   }

   public void stopHostedSession(String orgId, String user) {
      strategy.stopHostedSession(orgId, user);
   }

   public int getHostedRemainingHours(String orgId, String user) {
      return strategy.getHostedRemainingHours(orgId, user);
   }

   public int getHostedGraceHours(String orgId, String user) {
      return strategy.getHostedGraceHours(orgId, user);
   }

   private LicenseStrategy strategy;
}