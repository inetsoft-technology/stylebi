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
package inetsoft.util.audit;

import com.fasterxml.jackson.annotation.JsonIgnore;
import org.apache.commons.lang3.StringUtils;

import java.sql.Timestamp;

public class BookmarkRecord implements AuditRecord {
   /**
    * Access action.
    */
   public static final String ACTION_TYPE_ACCESS = "access";

   /**
    * Create action.
    */
   public static final String ACTION_TYPE_CREATE = "create";

   /**
    * Delete action.
    */
   public static final String ACTION_TYPE_DELETE = "delete";

   /**
    * Modify action.
    */
   public static final String ACTION_TYPE_MODIFY = "modify";

   /**
    * Rename action.
    */
   public static final String ACTION_TYPE_RENAME = "rename";

   /**
    * Private type.
    */
   public static final String BOOKMARK_TYPE_PRIVATE = "Private";

   /**
    * Shared(All Users) type.
    */
   public static final String BOOKMARK_TYPE_SHARED_ALL_USERS = "Shared(All Users)";

   /**
    * Shared(Same Groups) type.
    */
   public static final String BOOKMARK_TYPE_SHARED_SAME_GROUPS = "Shared(Same Groups)";

   /**
    * Create an empty instance of BookmarkRecord.
    */
   public BookmarkRecord() {
      super();
   }

   /**
    * Create a new instance of BookmarkRecord and set data.
    * @param userName the specified user name.
    * @param userRole the specified user role.
    * @param userActiveStatus the specified user active status.
    * @param userEmail the specified user email.
    * @param userLastLogin the specified user last login time.
    * @param actionType the specified action type.
    * @param actionExecTimestamp the specified action exec timestamp.
    * @param dashboardName the specified dashboard name.
    * @param dashboardAlias the specified dashboard alias.
    * @param bookmarkName the specified bookmark name.
    * @param bookmarkType the specified bookmark type.
    * @param bookmarkReadOnly whether the specified bookmark read-only.
    * @param bookmarkCreateDate the specified bookmark create date.
    * @param bookmarkLastUpdateDate the specified bookmark last update date.
    */
   public BookmarkRecord(String userName, String userRole, String userActiveStatus,
                         String userEmail, Timestamp userLastLogin, String actionType,
                         Timestamp actionExecTimestamp, String dashboardName, String dashboardAlias,
                         String bookmarkName, String bookmarkType, String bookmarkReadOnly,
                         Timestamp bookmarkCreateDate, Timestamp bookmarkLastUpdateDate,
                         String serverHostName)
   {
      super();

      this.setUserName(userName);
      this.setUserRole(userRole);
      this.setUserActiveStatus(userActiveStatus);
      this.setUserEmail(userEmail);
      this.setUserLastLogin(userLastLogin);
      this.setActionType(actionType);
      this.setActionExecTimestamp(actionExecTimestamp);
      this.setDashboardName(dashboardName);
      this.setDashboardAlias(dashboardAlias);
      this.setBookmarkName(bookmarkName);
      this.setBookmarkType(bookmarkType);
      this.setBookmarkReadOnly(bookmarkReadOnly);
      this.setBookmarkCreateDate(bookmarkCreateDate);
      this.setBookmarkLastUpdateDate(bookmarkLastUpdateDate);
      this.setServerHostName(serverHostName);
   }

   /**
    * Check if the record is a valid one.
    * @return <tt>true</tt> if valid, <tt>false</tt> otherwise.
    */
   @JsonIgnore
   @Override
   public boolean isValid() {
      return !StringUtils.isEmpty(userName) && !StringUtils.isEmpty(userActiveStatus) &&
         !StringUtils.isEmpty(actionType) && !StringUtils.isEmpty(dashboardName) &&
         !StringUtils.isEmpty(bookmarkName) && !StringUtils.isEmpty(bookmarkType) &&
         userLastLogin != null && actionExecTimestamp != null &&
         bookmarkCreateDate != null && bookmarkLastUpdateDate != null;
   }

   /**
    * Get the user name.
    * @return the specified user name.
    */
   @AuditRecordProperty
   public String getUserName() {
      return userName;
   }

   /**
    * Set the user name.
    * @param userName the specified user name.
    */
   public void setUserName(String userName) {
      this.userName = userName;
   }

   /**
    * Get the user role.
    * @return the specified user role.
    */
   @AuditRecordProperty
   public String getUserRole() {
      return userRole;
   }

   /**
    * Set the user role.
    * @param userRole the specified user role.
    */
   public void setUserRole(String userRole) {
      this.userRole = userRole;
   }

   /**
    * Get the user active status.
    * @return the specified user active status.
    */
   @AuditRecordProperty
   public String getUserActiveStatus() {
      return userActiveStatus;
   }

   /**
    * Set the user active status.
    * @param userActiveStatus the specified user active status.
    */
   public void setUserActiveStatus(String userActiveStatus) {
      this.userActiveStatus = userActiveStatus;
   }

   /**
    * Get the user email.
    * @return the specified user email.
    */
   @AuditRecordProperty
   public String getUserEmail() {
      return userEmail;
   }

   /**
    * Set the user email.
    * @param userEmail the specified user email.
    */
   public void setUserEmail(String userEmail) {
      this.userEmail = userEmail;
   }

   /**
    * Get the user last login time.
    * @return the specified user last login time.
    */
   @AuditRecordProperty
   public Timestamp getUserLastLogin() {
      return userLastLogin;
   }

   /**
    * Set the user last login time.
    * @param userLastLogin the specified user last login time.
    */
   public void setUserLastLogin(Timestamp userLastLogin) {
      this.userLastLogin = userLastLogin;
   }

   /**
    * Get the action type.
    * @return the specified action type.
    */
   @AuditRecordProperty
   public String getActionType() {
      return actionType;
   }

   /**
    * Set the action type.
    * @param actionType the specified action type.
    */
   public void setActionType(String actionType) {
      this.actionType = actionType;
   }

   /**
    * Get the action exec timestamp.
    * @return the specified action exec timestamp.
    */
   @AuditRecordProperty
   public Timestamp getActionExecTimestamp() {
      return actionExecTimestamp;
   }

   /**
    * Set the action exec timestamp.
    * @param actionExecTimestamp the specified action exec timestamp.
    */
   public void setActionExecTimestamp(Timestamp actionExecTimestamp) {
      this.actionExecTimestamp = actionExecTimestamp;
   }

   /**
    * Get the dashboard name.
    * @return the specified dashboard name.
    */
   @AuditRecordProperty
   public String getDashboardName() {
      return dashboardName;
   }

   /**
    * Set the dashboard name.
    * @param dashboardName the specified dashboard name.
    */
   public void setDashboardName(String dashboardName) {
      this.dashboardName = dashboardName;
   }

   /**
    * Get the dashboard alias.
    * @return the specified dashboard alias.
    */
   @AuditRecordProperty
   public String getDashboardAlias() {
      return dashboardAlias == null ? "" : dashboardAlias;
   }

   /**
    * Set the dashboard alias.
    * @param dashboardAlias the specified dashboard alias.
    */
   public void setDashboardAlias(String dashboardAlias) {
      this.dashboardAlias = dashboardAlias;
   }

   /**
    * Get the bookmark name.
    * @return the specified bookmark name.
    */
   @AuditRecordProperty
   public String getBookmarkName() {
      return bookmarkName;
   }

   /**
    * Set the bookmark name.
    * @param bookmarkName the specified bookmark name.
    */
   public void setBookmarkName(String bookmarkName) {
      this.bookmarkName = bookmarkName;
   }

   /**
    * Get the bookmark type.
    * @return the specified bookmark type.
    */
   @AuditRecordProperty
   public String getBookmarkType() {
      return bookmarkType;
   }

   /**
    * Set the bookmark type.
    * @param bookmarkType the specified bookmark type.
    */
   public void setBookmarkType(String bookmarkType) {
      this.bookmarkType = bookmarkType;
   }

   /**
    * Get the bookmark read-only.
    * @return the specified bookmark read-only.
    */
   @AuditRecordProperty
   public String getBookmarkReadOnly() {
      return bookmarkReadOnly;
   }

   /**
    * Set the bookmark read-only.
    * @param bookmarkReadOnly the specified bookmark read-only.
    */
   public void setBookmarkReadOnly(String bookmarkReadOnly) {
      this.bookmarkReadOnly = bookmarkReadOnly;
   }

   /**
    * Get the bookmark create date.
    * @return the specified bookmark create date.
    */
   @AuditRecordProperty
   public Timestamp getBookmarkCreateDate() {
      return bookmarkCreateDate;
   }

   /**
    * Set the bookmark create date.
    * @param bookmarkCreateDate the specified bookmark create date.
    */
   public void setBookmarkCreateDate(Timestamp bookmarkCreateDate) {
      this.bookmarkCreateDate = bookmarkCreateDate;
   }

   /**
    * Get the bookmark last update date.
    * @return the specified bookmark last update date.
    */
   @AuditRecordProperty
   public Timestamp getBookmarkLastUpdateDate() {
      return bookmarkLastUpdateDate;
   }

   /**
    * Set the bookmark last update date.
    * @param bookmarkLastUpdateDate the specified bookmark last update date.
    */
   public void setBookmarkLastUpdateDate(Timestamp bookmarkLastUpdateDate) {
      this.bookmarkLastUpdateDate = bookmarkLastUpdateDate;
   }

   @AuditRecordProperty
   public String getServerHostName() {
      return serverHostName;
   }

   public void setServerHostName(String serverHostName) {
      this.serverHostName = serverHostName;
   }

   @AuditRecordProperty
   public String getOrganizationId() {
      return organizationId;
   }

   public void setOrganizationId(String organizationId) {
      this.organizationId = organizationId;
   }

   private String userName;
   private String userRole;
   private String userActiveStatus;
   private String userEmail;
   private Timestamp userLastLogin;
   private String actionType;
   private Timestamp actionExecTimestamp;
   private String dashboardName;
   private String dashboardAlias;
   private String bookmarkName;
   private String bookmarkType;
   private String bookmarkReadOnly;
   private Timestamp bookmarkCreateDate;
   private Timestamp bookmarkLastUpdateDate;
   private String serverHostName;
   private String organizationId;
}
