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
package inetsoft.util.log;

import inetsoft.report.internal.license.LicenseManager;
import inetsoft.sree.internal.SUtil;
import inetsoft.sree.security.IdentityID;
import inetsoft.sree.security.OrganizationManager;
import inetsoft.uql.XPrincipal;
import inetsoft.util.Tool;
import org.slf4j.MDC;

import java.security.Principal;
import java.util.Arrays;

/**
 * Enumeration of the categories of context-specific logging.
 *
 * @author InetSoft Techology
 * @since  12.0
 */
public enum LogContext {
   /**
    * Indicates that the logging context refers to a user.
    */
   USER(null),

   /**
    * Indicates that the logging context refers to a group.
    */
   GROUP(null),

   /**
    * Indicates that the logging context refers to a role.
    */
   ROLE(null),

   /**
    * Indicates that the logging context refers to a report.
    */
   REPORT("report:"),

   /**
    * Indicates that the logging context refers to a viewsheet.
    */
   DASHBOARD("view:"),

   /**
    * Indicates that the logging context refers to a query.
    */
   QUERY("query:"),

   /**
    * Indicates that the logging context refers to a model.
    */
   MODEL("model:"),

   /**
    * Indicates that the logging context refers to a worksheet.
    */
   WORKSHEET("worksheet:"),

   /**
    * Indicates that the logging context refers to a schedule task.
    */
   SCHEDULE_TASK("ScheduleTask:"),

   /**
    * Indicates that the logging context refers to a log category (logger).
    */
   CATEGORY(null),

   /**
    * Indicates that the logging context refers to a viewsheet assembly.
    */
   ASSEMBLY("assembly:"),

   /**
    * Indicates that the logging context refers to a worksheet table assembly.
    */
   TABLE("table:"),

   /**
    * Indicates that the logging context refers to an organization.
    */
   ORGANIZATION("organization:");

   private final String prefix;

   LogContext(String prefix) {
      this.prefix = prefix;
   }

   public String getPrefix() {
      return this.prefix;
   }

   public String getRecord(String name) {
      name = fixOrgScopeResourceName(name);
      return prefix + name;
   }

   private String fixOrgScopeResourceName(String name) {
      if(!LicenseManager.getInstance().isEnterprise() ||
         Tool.equals(ORGANIZATION.getPrefix(), getPrefix()) ||
         Tool.equals(CATEGORY.getPrefix(), getPrefix()))
      {
         return name;
      }

      return Tool.buildString(name, "^", OrganizationManager.getInstance().getCurrentOrgID());
   }

   public String getRecordName(Object record) {
      return String.valueOf(record).substring(prefix.length());
   }

   public static LogContext findMatchingContext(Object record) {
      if(record instanceof String) {
         String recordString = (String) record;

         for(LogContext context : values()) {
            if(context.prefix != null && recordString.startsWith(context.prefix)) {
               return context;
            }
         }
      }

      return null;
   }

   public static void setUser(Principal user) {
      if(user == null) {
         MDC.remove(LogContext.USER.name());
         MDC.remove(LogContext.GROUP.name());
         MDC.remove(LogContext.ROLE.name());

         if(LicenseManager.getInstance().isEnterprise()) {
            MDC.remove(LogContext.ORGANIZATION.name());
         }
      }
      else {
         if(LicenseManager.getInstance().isEnterprise()) {
            MDC.put(LogContext.ORGANIZATION.name(), ((XPrincipal) user).getOrgId());
         }

         boolean enterprise = LicenseManager.getInstance().isEnterprise();
         IdentityID userIdentity = IdentityID.getIdentityIDFromKey(user.getName());

         if(userIdentity == null) {
            userIdentity = IdentityID.getIdentityIDFromKey(user.getName());
         }

         MDC.put(LogContext.USER.name(), userIdentity.getLabelWithCaretDelimiter());

         if(user instanceof XPrincipal principal) {
            IdentityID finalUserIdentity = userIdentity;
            String groups = String.join(",",
                                        Arrays.stream(principal.getGroups())
                                           .map(g -> enterprise ?
                                              new IdentityID(g, finalUserIdentity.getOrgID())
                                                 .getLabelWithCaretDelimiter() : g)
                                           .toArray(String[]::new));
            String roles = String.join(",",
                                       Arrays.stream(principal.getRoles())
                                          .map(r -> enterprise ?
                                             r.getLabelWithCaretDelimiter() : r.getName())
                                          .toArray(String[]::new));

            if(!groups.equals("")) {
               MDC.put(LogContext.GROUP.name(), groups);
            }
            else {
               MDC.remove(LogContext.GROUP.name());
            }

            if(!roles.equals("")) {
               MDC.put(LogContext.ROLE.name(), roles);
            }
            else {
               MDC.remove(LogContext.ROLE.name());
            }
         }
      }
   }
}
