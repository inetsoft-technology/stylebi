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
package inetsoft.report.internal.license;

import inetsoft.sree.SreeEnv;
import inetsoft.sree.internal.cluster.*;
import inetsoft.util.*;
import org.springframework.util.DigestUtils;

import java.io.IOException;
import java.lang.ref.WeakReference;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.locks.Lock;
import java.util.function.Predicate;
import java.util.function.ToIntFunction;
import java.util.stream.Collectors;

/**
 * {@code LicenseManager} manages the installed license keys and provides information about them.
 */
public class NoopLicenseStrategy extends LicenseStrategy {
   @Override
   public Optional<License> getClaimedLicense() {
      License license = License.builder().build();
      return Optional.ofNullable(license);
   }

   public Set<License> getClaimedLicenses() {
      return Collections.singleton(getClaimedLicense().orElse(null));
   }

   public Set<License> getInstalledLicenses() {
      return getClaimedLicenses();
   }

   public Set<License> getValidLicenses() {
      return getClaimedLicenses();
   }

   public Map<String, String> getClaimedClusterLicenses() {
      return Collections.emptyMap();
   }

   public Map<String, String> getClaimedNodeLicenses() {
      return Collections.emptyMap();
   }

   public boolean isMasterLicense() {
      return true;
   }

   public int getLicensedCpuCount() {
      return 0;
   }

   public int getConcurrentSessionCount() {
      return 0;
   }

   public int getNamedUserCount() {
      return 0;
   }

   public int getViewerSessionCount() {
      return 0;
   }

   public int getNamedUserViewerSessionCount() {
      return 0;
   }

   public Set<License> getSchedulerLicenses() {
      return getClaimedLicenses();
   }

   public Set<String> getNamedUsers() {
      return null;
   }

   public void addLicense(String key) {
   }

   public void removeLicense(String key) {
   }

   public void replaceLicense(String oldKey, String newKey) {
   }

   public void addSchedulerLicense(String key) {
   }

   public void removeSchedulerLicense(String key) {
   }

   public void replaceSchedulerLicense(String oldKey, String newKey) {
   }

   public void reload() {
   }

   public int getAvailableCpuCount() {
      return Math.max(2, Tool.getAvailableCPUCores());
   }

   public boolean isAffinitySet() {
      return false;
   }

   public int[] calculateThreadPoolSize(int softLimitCpuFactor, String hardLimitProperty,
                                        int hardLimitFactor)
   {
      int soft = getAvailableCpuCount() * softLimitCpuFactor;
      int hard = soft * hardLimitFactor;

      return new int[]{ soft, hard };
   }

   @Override
   public void close() throws Exception {
   }

   @Override
   public void messageReceived(MessageEvent event) {
   }

   public String getLicenseHash() {
      return "noop";
   }

   public void startDuplicateLicenseServer() {
   }

   public void addKeyViolation(String message, String id) {
   }

   public License parseLicense(String key) {
      return License.builder().build();
   }

   public void addClaimedLicenseListener(ClaimedLicenseListener l) {
   }

   public void removeClaimedLicenseListener(ClaimedLicenseListener l) {
   }
}
