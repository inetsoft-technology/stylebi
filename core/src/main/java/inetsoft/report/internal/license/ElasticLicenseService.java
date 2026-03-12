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

import inetsoft.util.ConfigurationContext;

import java.util.*;

public interface ElasticLicenseService {
   long getRemainingHours(License license);

   long getGracePeriodHours(License license);

   void removeLicense(String licenseKey);

   void startElasticPolling(License license);

   void addNotificationListener(NotificationListener listener);

   void removeNotificationListener(NotificationListener listener);

   static ElasticLicenseService getInstance() {
      return ConfigurationContext.getContext().getSpringBean(ElasticLicenseService.class);
   }

   final class NotificationEvent extends EventObject {
      public NotificationEvent(Object source, String licenseKey) {
         super(source);
         this.licenseKey = licenseKey;
      }

      public String getLicenseKey() {
         return licenseKey;
      }

      private final String licenseKey;
   }

   interface NotificationListener extends EventListener {
      void onGracePeriodStarted(NotificationEvent event);
      void onGracePeriodEnded(NotificationEvent event);
      void onHoursAdded(NotificationEvent event);
   }

}
