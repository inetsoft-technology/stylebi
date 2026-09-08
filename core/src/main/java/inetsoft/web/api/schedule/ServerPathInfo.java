/*
 * This file is part of StyleBI.
 * Copyright (C) 2026  InetSoft Technology
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

package inetsoft.web.api.schedule;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "The server path info for backup action.")
public class ServerPathInfo {
   public ServerPathInfo() {
      super();
   }

   public ServerPathInfo(inetsoft.sree.schedule.ServerPathInfo info) {
      super();

      if(info != null) {
         this.path = info.getPath();
         this.useCredential = info.isUseCredential();

         if(info.isUseCredential()) {
            this.secretId = info.getSecretId();
         }
         else {
            this.userName = info.getUsername();
            this.password = info.getPassword();
         }
      }
   }

   public String getPath() {
      return path;
   }

   public void setPath(String path) {
      this.path = path;
   }

   @Schema(description = "Whether to use a vault secret ID for the credentials.")
   public boolean isUseCredential() {
      return useCredential;
   }

   public void setUseCredential(boolean useCredential) {
      this.useCredential = useCredential;
   }

   @Schema(description = "The vault secret ID for the credentials.")
   public String getSecretId() {
      return secretId;
   }

   public void setSecretId(String secretId) {
      this.secretId = secretId;
   }

   public String getUserName() {
      return userName;
   }

   public void setUserName(String userName) {
      this.userName = userName;
   }

   public String getPassword() {
      return password;
   }

   public void setPassword(String password) {
      this.password = password;
   }

   private String path;
   private String userName;
   private String password;
   private boolean useCredential;
   private String secretId;
}
