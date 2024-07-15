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
package inetsoft.util.audit;

import inetsoft.report.ReportElement;
import inetsoft.report.ReportSheet;
import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.report.internal.BaseElement;
import inetsoft.sree.internal.SUtil;
import inetsoft.sree.security.*;
import inetsoft.sree.security.db.DatabaseAuthenticationProvider;
import inetsoft.sree.security.ldap.LdapAuthenticationProvider;
import inetsoft.uql.asset.AssetEntry;
import inetsoft.uql.asset.AssetRepository;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.internal.VSUtil;
import inetsoft.util.ThreadContext;
import inetsoft.util.Tool;

import java.security.Principal;
import java.sql.Timestamp;
import java.util.Arrays;
import java.util.Objects;

/**
 * Audit record utilities.
 *
 * @author InetSoft Technology Corp.
 * @version 13.1, 10/11/2018
 */
public final class AuditRecordUtils {
   /**
    * @return     return the report path or viewsheet path.
    */
   public static String getObjectName(Object obj) {
    if(obj == null) {
         return null;
      }

      if(obj instanceof Viewsheet) {
         return getViewsheetName((Viewsheet) obj);
      }

      return getReportSheetName(obj);
   }

   public static String getObjectType(Object obj) {
      if(obj == null) {
         return null;
      }

      if(obj instanceof Viewsheet) {
         return ExecutionBreakDownRecord.OBJECT_TYPE_VIEWSHEET;
      }

      if(obj instanceof ReportSheet || obj instanceof ReportElement) {
         return ExecutionBreakDownRecord.OBJECT_TYPE_REPORT;
      }

      return null;
   }

   public static String getViewsheetName(Viewsheet vs) {
      if(vs == null) {
         return null;
      }

      if(vs.isEmbedded()) {
         return getViewsheetName(vs.getViewsheet());
      }

      AssetEntry entry = vs.getRuntimeEntry();

      if(entry != null) {
         String name = entry.getScope() == AssetRepository.TEMPORARY_SCOPE ?
            null : vs.getRuntimeEntry().getPath();

         if(name != null && entry.getScope() != AssetRepository.GLOBAL_SCOPE) {
            name = "My Viewsheet/" + name;
         }

         return name;
      }

      if(vs.getName() != null) {
         return vs.getName();
      }

      return null;
   }

   public static String getReportSheetName(Object obj) {
      ReportSheet sheet = null;

      if(obj instanceof ReportSheet) {
         sheet = (ReportSheet) obj;
      }

      if(obj instanceof ReportElement) {
         BaseElement elem = (BaseElement) obj;
         sheet = elem.getReport();
      }

      if(sheet == null) {
         return null;
      }

      String name = sheet.getProperty("reportName");

      if(name == null) {
         name = sheet.getContextName();
      }

      if(name != null && name.lastIndexOf(":") != -1) {
         int idx = name.lastIndexOf(":");
         name = name.substring(idx + 1).trim();
      }

      // don't add record for temporary preview report.
      if(name != null && name.indexOf(SUtil.ANALYTIC_REPORT) != -1) {
         return null;
      }

      return name;
   }

   public static void executeEditBookmarkRecord(RuntimeViewsheet rvs, VSBookmarkInfo origInfo,
                                                  String newName, IdentityID owner)
   {
      if(rvs == null || origInfo == null || Tool.isEmptyString(newName) ||
         rvs.getBookmarkInfo(newName, owner) == null)
      {
         return;
      }

      VSBookmarkInfo newInfo = rvs.getBookmarkInfo(newName, owner);

      if(!newName.equals(origInfo.getName())) {
         origInfo.setName(newName);
         AuditRecordUtils.executeBookmarkRecord(
            rvs.getViewsheet(), origInfo, BookmarkRecord.ACTION_TYPE_RENAME);
      }

      if(newInfo.getType() != origInfo.getType() || newInfo.isReadOnly() != origInfo.isReadOnly()) {
         AuditRecordUtils.executeBookmarkRecord(
            rvs.getViewsheet(), newInfo, BookmarkRecord.ACTION_TYPE_MODIFY);
      }
   }

   public static void executeBookmarkRecord(Viewsheet vs, VSBookmarkInfo bookmarkInfo,
                                            String actionType)
   {
      // Ignore 'Initial' and 'Home' bookmarks.
      if(vs == null || vs.getRuntimeEntry() == null || bookmarkInfo == null ||
         VSBookmark.INITIAL_STATE.equals(bookmarkInfo.getName()) ||
         VSBookmark.HOME_BOOKMARK.equals(bookmarkInfo.getName()))
      {
         return;
      }

      SecurityProvider provider = SecurityEngine.getSecurity().getSecurityProvider();
      Principal principal = ThreadContext.getContextPrincipal();
      String userName = "";
      String userRole = "";
      String userEmail = "";
      User user = provider.getUser(IdentityID.getIdentityIDFromKey(principal.getName()));

      if(user != null) {
         userName = user.getName();
         userRole = String.join(", ", Arrays.stream(user.getRoles())
            .map(IdentityID::getName).toArray(String[]::new));
         userEmail = String.join(", ", user.getEmails());
      }
      else {
         userName = principal.getName();

         if(userName != null && userName.contains(IdentityID.KEY_DELIMITER)) {
            userName = IdentityID.getIdentityIDFromKey(userName).getName();
         }

         if(principal instanceof SRPrincipal) {
            userRole = String.join(", ", Arrays.stream(((SRPrincipal) principal).getRoles())
               .map(IdentityID::getName).toArray(String[]::new));
         }
      }

      String activeStatus = String.valueOf(getUserActive());
      Timestamp lastLogin = new Timestamp(((SRPrincipal) principal).getAge());
      String dashboardName = vs.getRuntimeEntry().getDescription();
      String dashboardAlias = vs.getRuntimeEntry().getAlias();
      String bookmarkName = bookmarkInfo.getName();
      IdentityID owner = bookmarkInfo.getOwner();
      String bkNameWithOwner = Tool.isEmptyString(owner.name) ?
         bookmarkName : bookmarkName + "(" + VSUtil.getUserAlias(owner) + ")";
      int bkType = bookmarkInfo.getType();
      String bookmarkType = bkType == VSBookmarkInfo.PRIVATE ? BookmarkRecord.BOOKMARK_TYPE_PRIVATE :
         bkType == VSBookmarkInfo.ALLSHARE ? BookmarkRecord.BOOKMARK_TYPE_SHARED_ALL_USERS :
         bkType == VSBookmarkInfo.GROUPSHARE ? BookmarkRecord.BOOKMARK_TYPE_SHARED_SAME_GROUPS :
         BookmarkRecord.BOOKMARK_TYPE_PRIVATE;
      String bookmarkReadOnly =
         bkType == VSBookmarkInfo.PRIVATE ? "" : String.valueOf(bookmarkInfo.isReadOnly());
      long currentTimeMillis = System.currentTimeMillis();
      Timestamp bookmarkCreateDate = new Timestamp(bookmarkInfo.getCreateTime());
      Timestamp bookmarkLastUpdateDate =
         BookmarkRecord.ACTION_TYPE_ACCESS.equals(actionType) ||
         BookmarkRecord.ACTION_TYPE_DELETE.equals(actionType) ?
         new Timestamp(bookmarkInfo.getLastModified()) : new Timestamp(currentTimeMillis);
      Timestamp actionExecTimestamp = new Timestamp(currentTimeMillis);
      String serverHostName = Tool.getHost();

      BookmarkRecord record = new BookmarkRecord(userName, userRole,activeStatus, userEmail,
         lastLogin, actionType, actionExecTimestamp, dashboardName, dashboardAlias, bkNameWithOwner,
         bookmarkType, bookmarkReadOnly, bookmarkCreateDate, bookmarkLastUpdateDate,
                                                 serverHostName);
      Audit.getInstance().auditBookmark(record, principal);
   }

   public static boolean getUserActive() {
      AuthenticationProvider provider = SecurityEngine.getSecurity().getSecurityProvider();
      Principal principal = ThreadContext.getContextPrincipal();
      IdentityID name = principal == null ? null : IdentityID.getIdentityIDFromKey(principal.getName());

      if(provider instanceof CompositeSecurityProvider) {
         provider = ((CompositeSecurityProvider) provider).getAuthenticationProvider();
      }

      User user = provider.getUser(name);

      if(provider instanceof AuthenticationChain) {
         boolean activeUser = ((AuthenticationChain) provider).stream()
            .anyMatch(p -> p.getUser(name) != null && (p instanceof LdapAuthenticationProvider ||
               p instanceof DatabaseAuthenticationProvider));
         return activeUser || ((AuthenticationChain) provider).stream()
            .map(p -> p.getUser(name))
            .filter(Objects::nonNull)
            .anyMatch(User::isActive);
      }
      else {
         return user.isActive();
      }
   }
}
