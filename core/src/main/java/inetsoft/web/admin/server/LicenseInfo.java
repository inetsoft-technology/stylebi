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
package inetsoft.web.admin.server;

import inetsoft.report.internal.license.*;

import java.time.Duration;
import java.time.LocalDateTime;

/**
 * Details about each license installed on the server.
 *
 * @version 10.2, 7/31/2009
 * @author InetSoft Technology Corp
 */
public class LicenseInfo {
   /**
    * The CPU type.
    */
   public static final String CPU = "CPU";

   /**
    * The session type.
    */
   public static final String SESSION = "Session";

   /**
    * The named user type.
    */
   public static final String NAMED_USER = "NamedUser";

   /**
    * The elastic vCPU type.
    */
   public static final String ELASTIC = "ElasticVCPU";

   /**
    * The per-user hosted type.
    */
   public static final String HOSTED = "PerUserHosted";

   /**
    * The invalid type.
    */
   public static final String INVALID = "Invalid";

   /**
    * Create a LicenseInfo with a license, and initialize the info.
    */
   public LicenseInfo(String license) {
      init(license);
   }

   /**
    * Get the number of CPUs or sessions included in the license.
    */
   public int getCount() {
      return count;
   }

   /**
    * Get the days to the expiry date. If the number is -1, it means dateless.
    */
   public int getValidDays() {
      License license = LicenseManager.getInstance().parseLicense(licenseKey);
      int days = (int) Duration.between(LocalDateTime.now(), license.expires()).toDays();
      return days <= 0 ? -1 : days;
   }

   /**
    * Get the type of license, CPU or session.
    */
   public String getType() {
      return type;
   }

   /**
    * Get license key.
    */
   public String getLicenseKey() {
      return licenseKey;
   }

   /**
    * Set the number of license's type and type of license.
    */
   public void init(String key) {
      if(key == null || "".equals(key)) {
         return;
      }

      licenseKey = key;
      int invalidKeys = 0;
      License license = LicenseManager.getInstance().parseLicense(key);

      if(license.type() == LicenseType.CPU) {
         count = license.cpuCount();
         type = CPU;
         return;
      }

      if(license.type() == LicenseType.CONCURRENT_SESSION) {
         count = license.concurrentSessionCount();
         type = SESSION;
         return;
      }

      if(license.type() == LicenseType.NAMED_USER) {
         count = license.namedUserCount();
         type = NAMED_USER;
         return;
      }

      if(license.type() == LicenseType.ELASTIC) {
         count = 1;
         type = ELASTIC;
         return;
      }

      if(license.type() == LicenseType.HOSTED) {
         count = 1;
         type = HOSTED;
         return;
      }

      invalidKeys++;
      count = invalidKeys;
      type = INVALID;
   }

   private int count;
   private String type;
   private String licenseKey;
}
