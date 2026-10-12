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

package inetsoft.util.credential;

import inetsoft.util.Tool;
import org.w3c.dom.Element;

import java.io.PrintWriter;

public class LocalResourceOwnerPasswordCredentials extends LocalPasswordCredential
   implements ResourceOwnerPasswordCredentials
{
   public LocalResourceOwnerPasswordCredentials() {
      super();
   }

   @Override
   public String getClientId() {
      return clientId;
   }

   @Override
   public void setClientId(String clientId) {
      this.clientId = clientId;
   }

   @Override
   public String getClientSecret() {
      return clientSecret;
   }

   @Override
   public void setClientSecret(String clientSecret) {
      this.clientSecret = clientSecret;
   }

   @Override
   public String getTenantId() {
      return tenantId;
   }

   @Override
   public void setTenantId(String tenantId) {
      this.tenantId = tenantId;
   }

   @Override
   public String getAccessToken() {
      return accessToken;
   }

   @Override
   public void setAccessToken(String accessToken) {
      this.accessToken = accessToken;
   }

   @Override
   public String getRefreshToken() {
      return refreshToken;
   }

   @Override
   public void setRefreshToken(String refreshToken) {
      this.refreshToken = refreshToken;
   }

   @Override
   public void reset() {
      super.reset();
      clientId = "";
      clientSecret = "";
      tenantId = "";
      accessToken = null;
      refreshToken = null;
   }

   @Override
   public boolean equals(Object obj) {
      if(!super.equals(obj) || !(obj instanceof LocalResourceOwnerPasswordCredentials)) {
         return false;
      }

      return Tool.equals(((LocalResourceOwnerPasswordCredentials) obj).clientId, clientId) &&
         Tool.equals(((LocalResourceOwnerPasswordCredentials) obj).clientSecret, clientSecret) &&
         Tool.equals(((LocalResourceOwnerPasswordCredentials) obj).tenantId, tenantId) &&
         Tool.equals(((LocalResourceOwnerPasswordCredentials) obj).accessToken, accessToken) &&
         Tool.equals(((LocalResourceOwnerPasswordCredentials) obj).refreshToken, refreshToken);
   }

   @Override
   protected void writeContent(PrintWriter writer) {
      super.writeContent(writer);

      if(getClientId() != null) {
         writer.format("<clientId><![CDATA[%s]]></clientId>%n", getClientId());
      }

      if(getClientSecret() != null) {
         writer.format(
            "<clientSecret><![CDATA[%s]]></clientSecret>%n", Tool.encryptPassword(getClientSecret()));
      }

      if(getTenantId() != null) {
         writer.format("<tenantId><![CDATA[%s]]></tenantId>%n", getTenantId());
      }

      if(getAccessToken() != null) {
         writer.format(
            "<accessToken><![CDATA[%s]]></accessToken>%n", Tool.encryptPassword(getAccessToken()));
      }

      if(getRefreshToken() != null) {
         writer.format(
            "<refreshToken><![CDATA[%s]]></refreshToken>%n",
            Tool.encryptPassword(getRefreshToken()));
      }
   }

   @Override
   public void parseXML(Element elem) throws Exception {
      if(elem == null) {
         return;
      }

      super.parseXML(elem);
      setClientId(Tool.getChildValueByTagName(elem, "clientId"));
      setTenantId(Tool.getChildValueByTagName(elem, "tenantId"));
      setClientSecret(Tool.decryptPassword(Tool.getChildValueByTagName(elem, "clientSecret")));
      setAccessToken(getDecryptPassword(Tool.getChildNodeByTagName(elem, "accessToken")));
      setRefreshToken(getDecryptPassword(Tool.getChildNodeByTagName(elem, "refreshToken")));
   }

   private String clientId;
   private String clientSecret;
   private String tenantId;
   private String accessToken;
   private String refreshToken;
}
