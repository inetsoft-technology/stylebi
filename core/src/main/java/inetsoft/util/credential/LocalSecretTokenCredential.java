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
import org.apache.commons.lang3.StringUtils;
import org.w3c.dom.Element;

import java.io.PrintWriter;

public class LocalSecretTokenCredential extends LocalPasswordCredential
   implements SecretTokenCredential
{
   public LocalSecretTokenCredential() {
      super();
   }

   public String getClientId() {
      return clientId;
   }

   public void setClientId(String clientId) {
      this.clientId = clientId;
   }

   public String getClientSecret() {
      return clientSecret;
   }

   public void setClientSecret(String clientSecret) {
      this.clientSecret = clientSecret;
   }

   public String getAccessToken() {
      return accessToken;
   }

   public void setAccessToken(String accessToken) {
      this.accessToken = accessToken;
   }

   public String getRefreshToken() {
      return refreshToken;
   }

   public void setRefreshToken(String refreshToken) {
      this.refreshToken = refreshToken;
   }

   @Override
   public boolean isEmpty() {
      return super.isEmpty() && StringUtils.isEmpty(clientId) &&
         StringUtils.isEmpty(clientSecret) && StringUtils.isEmpty(accessToken) &&
         StringUtils.isEmpty(refreshToken);
   }

   @Override
   public void reset() {
      super.reset();
      clientId = null;
      clientSecret = null;
      accessToken = null;
      refreshToken = null;
   }

   @Override
   public boolean equals(Object obj) {
      if(!super.equals(obj)) {
         return false;
      }

      if(!(obj instanceof LocalSecretTokenCredential)) {
         return false;
      }

      return Tool.equals(((LocalSecretTokenCredential) obj).clientId, clientId) &&
         Tool.equals(((LocalSecretTokenCredential) obj).clientSecret, clientSecret) &&
         Tool.equals(((LocalSecretTokenCredential) obj).accessToken, accessToken) &&
         Tool.equals(((LocalSecretTokenCredential) obj).refreshToken, refreshToken);
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

      if(getAccessToken() != null) {
         writer.format(
            "<accessToken><![CDATA[%s]]></accessToken>%n", Tool.encryptPassword(getAccessToken()));
      }

      if(getRefreshToken() != null) {
         writer.format(
            "<refreshToken><![CDATA[%s]]></refreshToken>%n", Tool.encryptPassword(getRefreshToken()));
      }
   }

   @Override
   public void parseXML(Element elem) throws Exception {
      if(elem == null) {
         return;
      }

      super.parseXML(elem);

      Element clientIdNode = Tool.getChildNodeByTagName(elem, "clientId");

      if(clientIdNode != null) {
         setClientId(Tool.getValue(clientIdNode));
      }

      Element clientSecretNode = Tool.getChildNodeByTagName(elem, "clientSecret");

      if(clientSecretNode != null) {
         setClientSecret(Tool.decryptPassword(Tool.getValue(clientSecretNode)));
      }

      Element accessTokenNode = Tool.getChildNodeByTagName(elem, "accessToken");

      if(accessTokenNode != null) {
         setAccessToken(Tool.decryptPassword(Tool.getValue(accessTokenNode)));
      }

      Element refreshTokenNode = Tool.getChildNodeByTagName(elem, "refreshToken");

      if(refreshTokenNode != null) {
         setRefreshToken(Tool.decryptPassword(Tool.getValue(refreshTokenNode)));
      }
   }

   private String clientId;
   private String clientSecret;
   private String accessToken;
   private String refreshToken;
}
