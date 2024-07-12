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
package inetsoft.web.share;

import java.security.Principal;
import java.util.EventObject;

/**
 * Event that is sent when a user shares a viewsheet or report.
 */
public class ShareEvent extends EventObject {
   public ShareEvent(Object source, String viewsheetId, String reportPath, Principal user) {
      super(source);
      this.viewsheetId = viewsheetId;
      this.reportPath = reportPath;
      this.user = user;
   }

   public String getViewsheetId() {
      return viewsheetId;
   }

   public String getReportPath() {
      return reportPath;
   }

   public Principal getUser() {
      return user;
   }

   private final String viewsheetId;
   private final String reportPath;
   private final Principal user;
}
