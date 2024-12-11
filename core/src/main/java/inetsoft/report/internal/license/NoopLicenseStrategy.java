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

import inetsoft.sree.internal.cluster.MessageEvent;
import inetsoft.sree.security.IdentityID;
import inetsoft.util.Tool;

import java.util.*;

/**
 * {@code LicenseManager} manages the installed license keys and provides information about them.
 */
public class NoopLicenseStrategy extends LicenseStrategy {
   @Override
   public Optional<License> getClaimedLicense() {
      License license = License.builder().build();
      return Optional.ofNullable(license);
   }

   @Override
   public Set<License> getClaimedLicenses() {
      return Collections.singleton(getClaimedLicense().orElse(null));
   }

   @Override
   public Set<License> getInstalledLicenses() {
      return getClaimedLicenses();
   }

   @Override
   public Set<License> getValidLicenses() {
      return getClaimedLicenses();
   }

   @Override
   public Map<String, String> getClaimedClusterLicenses() {
      return Collections.emptyMap();
   }

   @Override
   public Map<String, String> getClaimedNodeLicenses() {
      return Collections.emptyMap();
   }

   @Override
   public boolean isMasterLicense() {
      return true;
   }

   @Override
   public int getLicensedCpuCount() {
      return 0;
   }

   @Override
   public int getConcurrentSessionCount() {
      return 0;
   }

   @Override
   public int getNamedUserCount() {
      return 0;
   }

   @Override
   public int getViewerSessionCount() {
      return 0;
   }

   @Override
   public int getNamedUserViewerSessionCount() {
      return 0;
   }

   @Override
   public Set<IdentityID> getNamedUsers() {
      return null;
   }

   @Override
   public void addLicense(String key) {
   }

   @Override
   public void removeLicense(String key) {
   }

   @Override
   public void replaceLicense(String oldKey, String newKey) {
   }

   @Override
   public void reload() {
   }

   @Override
   public int getAvailableCpuCount() {
      return Math.max(2, Tool.getAvailableCPUCores());
   }

   @Override
   public boolean isAffinitySet() {
      return false;
   }

   @Override
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

   @Override
   public String getLicenseHash() {
      return "noop";
   }

   @Override
   public void startDuplicateLicenseServer() {
   }

   @Override
   public void addKeyViolation(String message, String id) {
   }

   @Override
   public License parseLicense(String key) {
      return License.builder().build();
   }

   @Override
   public void addClaimedLicenseListener(ClaimedLicenseListener l) {
   }

   @Override
   public void removeClaimedLicenseListener(ClaimedLicenseListener l) {
   }

   @Override
   public void userChanged() {
   }

   @Override
   public void startElasticPolling() {
   }

   @Override
   public int getElasticRemainingHours() {
      return 0;
   }

   @Override
   public int getElasticGraceHours() {
      return 0;
   }

   @Override
   public boolean startHostedSession(String orgId, String user) {
      return false;
   }

   @Override
   public void stopHostedSession(String orgId, String user) {
   }

   @Override
   public int getHostedRemainingHours(String orgId, String user) {
      return 0;
   }

   @Override
   public int getHostedGraceHours(String orgId, String user) {
      return 0;
   }
}
