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

public class NoopHostedLicenseService implements HostedLicenseService {
   @Override
   public long getRemainingHours(String licenseKey, String orgId, String user) {
      return 0;
   }

   @Override
   public long getGracePeriodHours(String licenseKey, String orgId, String user) {
      return 0;
   }

   @Override
   public boolean startSession(License license, String orgId, String user) {
      return false;
   }

   @Override
   public void stopSession(String licenseKey, String orgId, String user) {
   }

   @Override
   public void removeLicense(String licenseKey) {
   }
}
