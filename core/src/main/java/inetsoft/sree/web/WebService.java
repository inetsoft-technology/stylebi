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
package inetsoft.sree.web;

import inetsoft.report.internal.license.LicenseManager;
import inetsoft.sree.RepletRepository;
import inetsoft.sree.security.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.security.Principal;
import java.util.HashSet;
import java.util.Set;

/**
 * Defines the common interface for all services provided to web clients of the
 * report server.
 * @author InetSoft Technology Corp.
 * @since  5.0
 */
public class WebService {
   /**
    * Response code indicating that a report page is available. The page can be
    * retrieved by calling <code>getDHTMLPage</code> on the service response
    * object.
    */
   public static final int REPORT_AVAILABLE = 1;

   /**
    * Response code indicating that a report page was requested, but is not yet
    * available. The client should provide some mechanism to request the page
    * again after a brief timeout.
    */
   public static final int REPORT_UNAVAILABLE = 2;

   /**
    * Response code indicating that a resource was written to the output stream
    * of the service response object. The mime-type of the resource can be
    * retrieved by calling <code>getContentType</code> on the service response
    * object.
    * @see inetsoft.sree.web.ServiceResponse#getContentType()
    */
   public static final int RESOURCE = 3;

   /**
    * Creates a new instance of <tt>WebService</tt>.
    */
   public WebService() {
   }

   /**
    * Check if a web extension is enabled, the web extension will be treated
    * as enabled if the extension is installed and the permission is granted
    * to a principal.
    * This method will normally be called from web viewer code.
    */
   public static boolean isWebExtEnabled(RepletRepository engine,
                                         Principal principal, String module) {
      Set<Resource> resources = new HashSet<>();

      if("Adhoc".equals(module)) {
         resources.add(new Resource(ResourceType.COMPOSER, "*"));
      }
      else if("Alert".equals(module)) {
         resources.add(new Resource(ResourceType.SCHEDULER, "*"));
      }
      else if("DataWorksheet".equals(module)) {
         resources.add(new Resource(ResourceType.WORKSHEET, "*"));
         resources.add(new Resource(ResourceType.VIEWSHEET, "*"));
      }
      else if("Form".equals(module) && !LicenseManager.isComponentAvailable(LicenseManager.LicenseComponent.FORM)) {
         return false;
      }

      if(resources.isEmpty()) {
         return true;
      }

      try {
         for(Resource resource : resources) {
            if(engine.checkPermission(
               principal, resource.getType(), resource.getPath(), ResourceAction.ACCESS))
            {
               return true;
            }
         }
      }
      catch(Exception ex) {
         LOG.error(
            "Failed to check for read permission on {} for user {}", resources, principal, ex);
      }

      return false;
   }

   private static final Logger LOG = LoggerFactory.getLogger(WebService.class);
}
