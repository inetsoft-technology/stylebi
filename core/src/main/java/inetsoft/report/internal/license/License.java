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

import inetsoft.sree.internal.SUtil;
import org.apache.commons.lang.StringUtils;
import org.immutables.value.Value;

import javax.annotation.Nullable;
import java.text.NumberFormat;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Set;

/**
 * {@code License} represents a InetSoft software license.
 */
@Value.Immutable
@Value.Style(visibility = Value.Style.ImplementationVisibility.PUBLIC, overshadowImplementation = true)
public abstract class License {
   /**
    * Creates a new instance of {@code License}.
    */
   License() {
      // prevent external sub-classing
   }

   /**
    * The license key.
    */
   @Nullable
   public abstract String key();

   /**
    * The type of license.
    */
   @Nullable
   public abstract LicenseType type();

   /**
    * The date and time at which the license expires.
    */
   @Nullable
   public abstract LocalDateTime expires();

   /**
    * The software product that the license is for.
    */
   @Nullable
   public abstract String productName();

   /**
    * A flag indicating if the license is a standalone server license.
    */
   @Value.Default
   public boolean standalone() {
      return true;
   }

   /**
    * A flag indicating if the license is a master key license.
    */
   @Value.Default
   public boolean master() {
      return true;
   }

   /**
    * A flag indicating if the license is only for use in a test or evaluation environment.
    */
   @Value.Default
   public boolean test() {
      return false;
   }

   /**
    * A flag indicating if data write-back forms are permitted by the license.
    */
   @Value.Default
   public boolean formLicensed() {
      return true;
   }

   /**
    * A flag indicating if the license is already in use elsewhere on the network.
    */
   @Value.Default
   public boolean duplicate() {
      return false;
   }

   /**
    * A flag indicating if the license is for a different version of the software.
    */
   @Value.Default
   public boolean incorrectVersion() {
      return false;
   }

   /**
    * The number of threads permitted by the license. If not a thread license, this will be
    * {@code 0}.
    */
   @Value.Default
   public int threadCount() {
      return 0;
   }

   /**
    * The number CPU cores permitted by the license. If not a CPU license, this will be {@code 0}.
    */
   @Value.Default
   public int cpuCount() {
      return 0;
   }

   /**
    * The number of concurrent sessions permitted by the license. If not a session license, this
    * will be {@code 0}.
    */
   @Value.Default
   public int concurrentSessionCount() {
      return 0;
   }

   /**
    * The number of named users permitted by the license. If not a named user license, this will be
    * {@code 0}.
    */
   @Value.Default
   public int namedUserCount() {
      return 0;
   }

   /**
    * The number of viewer-only sessions permitted by the license. If not a viewer license, this
    * will be {@code 0}.
    */
   @Value.Default
   public int viewerSessionCount() {
      return 0;
   }

   /**
    * The number of viewer-only named user session permitted by the license. If not a named user
    * viewer license, this will be {@code 0}.
    */
   @Value.Default
   public int namedUserViewerSessionCount() {
      return 0;
   }

   /**
    * The number of instances permitted by the license. If not a Spark worker or scheduler instance
    * license, this will be {@code 0}.
    */
   @Value.Default
   public int instanceCount() {
      return 0;
   }

   @Value.Default
   public boolean cloudDevelopment() {
      return false;
   }

   @Value.Default
   public boolean cloudTest() {
      return false;
   }

   /**
    * The licensed software components.
    */
   public abstract Set<LicenseComponent> components();

   /**
    * A flag indicating if the license is valid and has not expired.
    */
   public final boolean valid(boolean ignoreDuplicate) {
      if(type() == LicenseType.NAMED_USER && SUtil.isMultiTenant()) {
         return false;
      }

      if(type() == LicenseType.ELASTIC) {
         ElasticLicenseService service = ElasticLicenseService.getInstance();
         return service.getRemainingHours(this) > 0 || service.getGracePeriodHours(this) > 0;
      }

      return type() != LicenseType.INVALID && (ignoreDuplicate || !duplicate()) &&
         LocalDateTime.now().isBefore(expires());
   }

   /**
    * A flag indicating if the license is valid and has not expired.
    */
   public final boolean valid() {
      return valid(false);
   }

   /**
    * A human-readable description of this license.
    */
   public String description() {
      StringBuilder description = new StringBuilder();
      LicenseType type = type();

      if(type == null) {
         return null;
      }

      switch(type) {
      case CPU:
         description.append(cpuCount()).append(" CPU(s)");
         break;
      case CONCURRENT_SESSION:
         description.append(concurrentSessionCount()).append(" Session(s)");
         break;
      case NAMED_USER:
         description.append(namedUserCount()).append(" Named Session(s)");
         break;
      case VIEWER:
         description.append(viewerSessionCount()).append(" Viewer Session(s)");
         break;
      case NAMED_USER_VIEWER:
         description.append(namedUserViewerSessionCount()).append(" Named Viewer Session(s)");
         break;
      case ELASTIC:
         appendElasticDescription(description);
         break;
      case HOSTED:
         description.append("Per-User Hosted");
         break;
      }

      Duration expires = Duration.between(LocalDateTime.now(), expires());

      if(expires.isNegative()) {
         if(!description.isEmpty()) {
            description.append(", ");
         }

         description.append("Expired");
      }
      else if(expires.toDays() < 364) {
         if(!description.isEmpty()) {
            description.append(", ");
         }

         description.append(expires.toDays() + 1).append(" Day(s)");
      }
      else if(expires.toDays() < 3649) {
         if(!description.isEmpty()) {
            description.append(", ");
         }

         NumberFormat format = NumberFormat.getNumberInstance();
         format.setMaximumFractionDigits(2);
         double years = (expires.toDays() + 1) / 365D;
         description.append(format.format(years)).append(" Year(s)");
      }

      if(!valid()) {
         if(!description.isEmpty()) {
            description.append(", ");
         }

         description.append("Invalid");
      }

      if(standalone()) {
         if(!description.isEmpty()) {
            description.append(", ");
         }

         description.append("Standalone");
      }

      if(!StringUtils.isEmpty(productName())) {
         if(!description.isEmpty()) {
            description.append(", ");
         }

         description.append(productName());
      }

      return description.toString();
   }

   private void appendElasticDescription(StringBuilder description) {
      ElasticLicenseService service = ElasticLicenseService.getInstance();
      int remainingHours = 0;
      int graceHours = 0;
      String key = key();

      if(key != null) {
         remainingHours = Math.max(0, (int) service.getRemainingHours(this));
         graceHours = Math.max(0, (int) service.getGracePeriodHours(this));
      }

      description.append("Elastic vCPU ").append(remainingHours).append(" hours remaining");

      if(remainingHours == 0 && graceHours > 0) {
         description.append(", ").append(graceHours).append(" grace period hours remaining");
      }
   }

   public static Builder builder() {
      return new Builder();
   }

   public static final class Builder extends ImmutableLicense.Builder {
   }
}
