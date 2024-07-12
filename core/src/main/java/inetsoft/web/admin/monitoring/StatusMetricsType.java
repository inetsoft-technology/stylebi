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
package inetsoft.web.admin.monitoring;

public enum StatusMetricsType {
   CACHE_METRICS("inetsoft.monitoring.cache.metrics"),
   REPORT_METRICS("inetsoft.monitoring.report.metrics"),
   QUERY_METRICS("inetsoft.monitoring.query.metrics"),
   SERVER_METRICS("inetsoft.monitoring.server.metrics"),
   VIEWSHEET_METRICS("inetsoft.monitoring.viewsheet.metrics"),
   USER_METRICS("inetsoft.monitoring.user.metrics"),
   CLUSTER_METRICS("inetsoft.monitoring.cluster.metrics"),
   SCHEDULE_METRICS("inetsoft.monitoring.schedule.metrics");

   StatusMetricsType(String type) {
      this.type = type;
   }

   public String type() {
      return type;
   }

   private final String type;
}
