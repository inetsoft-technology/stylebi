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
package inetsoft.web.admin.content.database;

import inetsoft.util.Tool;

/**
* Created by Jason Shobe on 2/20/2015.
*/
public final class AuthenticationDetails {
   /**
    * Gets the flag that indicates if the database requires authentication.
    *
    * @return <tt>true</tt> if required; <tt>false</tt> otherwise.
    */
   public boolean isRequired() {
      return required;
   }

   /**
    * Sets the flag that indicates if the database requires authentication.
    *
    * @param required <tt>true</tt> if required; <tt>false</tt> otherwise.
    */
   public void setRequired(boolean required) {
      this.required = required;
   }

   /**
    * Gets the user name used to authenticate with the database.
    *
    * @return the user name.
    */
   public String getUserName() {
      return userName;
   }

   /**
    * Sets the user name used to authenticate with the database.
    *
    * @param userName the user name.
    */
   public void setUserName(String userName) {
      this.userName = userName;
   }

   /**
    * Gets the password used to authenticate with the database.
    *
    * @return the password.
    */
   public String getPassword() {
      return password;
   }

   /**
    * Sets the password used to authenticate with the database.
    *
    * @param password the password.
    */
   public void setPassword(String password) {
      this.password = password;
   }

   /**
    * Gets the flag that indicates if the authentication credential is being used.
    *
    * @return <tt>true</tt> if the credential id is being used; <tt>false</tt> otherwise.
    */
   public boolean isUseCredentialId() {
      return useCredentialId;
   }

   /**
    * Sets the flag that indicates if the authentication credential should be used.
    *
    * @param useCredentialId <tt>true</tt> to use the credential id; <tt>false</tt> otherwise.
    */
   public void setUseCredentialId(boolean useCredentialId) {
      this.useCredentialId = useCredentialId;
   }

   /**
    * Gets the credential id used to authenticate with the database.
    *
    * @return the credential.
    */
   public String getCredentialId() {
      return credentialId;
   }

   /**
    * Sets the credential id used to authenticate with the database.
    *
    * @param credentialId the password.
    */
   public void setCredentialId(String credentialId) {
      this.credentialId = credentialId;
   }

   /**
    * Gets the flag that indicates if the credential is visible.
    *
    * @return <tt>true</tt> if the credential is visible; <tt>false</tt> otherwise.
    */
   public boolean isCredentialVisible() {
      return credentialVisible;
   }

   /**
    * Set the flag that indicates if the credential is visible.
    *
    * @param credentialVisible <tt>true</tt> if the credential is visible; <tt>false</tt> otherwise.
    */
   public void setCredentialVisible(boolean credentialVisible) {
      this.credentialVisible = credentialVisible;
   }

   private boolean required;
   private String userName;
   private String password;
   private boolean useCredentialId;
   private String credentialId;
   private boolean credentialVisible = Tool.isCloudSecrets();
}
