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

public class LocalAuthorizationTokenCredential extends AbstractLocalCredential
   implements AuthorizationTokenCredential
{
   public LocalAuthorizationTokenCredential() {
      super();
   }

   public String getApplicationId() {
      return applicationId;
   }

   public void setApplicationId(String applicationId) {
      this.applicationId = applicationId;
   }

   public String getAuthorizationToken() {
      return authorizationToken;
   }

   public void setAuthorizationToken(String authorizationToken) {
      this.authorizationToken = authorizationToken;
   }

   @Override
   public boolean isEmpty() {
      return super.isEmpty() && StringUtils.isEmpty(applicationId) &&
         StringUtils.isEmpty(authorizationToken);
   }

   @Override
   public void reset() {
      super.reset();
      applicationId = null;
      authorizationToken = null;
   }

   @Override
   public boolean equals(Object obj) {
      if(!super.equals(obj)) {
         return false;
      }

      if(!(obj instanceof LocalAuthorizationTokenCredential)) {
         return false;
      }

      return Tool.equals(((LocalAuthorizationTokenCredential) obj).applicationId, applicationId) &&
         Tool.equals(((LocalAuthorizationTokenCredential) obj).authorizationToken, authorizationToken);
   }

   @Override
   protected void writeContent(PrintWriter writer) {
      if(getApplicationId() != null) {
         writer.format(
            "<applicationId><![CDATA[%s]]></applicationId>%n", getApplicationId());
      }

      if(getAuthorizationToken() != null) {
         writer.format(
            "<authorizationToken><![CDATA[%s]]></authorizationToken>%n", Tool.encryptPassword(getAuthorizationToken()));
      }
   }

   @Override
   public void parseXML(Element elem) throws Exception {
      if(elem == null) {
         return;
      }

      super.parseXML(elem);
      Element applicationIdNode = Tool.getChildNodeByTagName(elem, "applicationId");

      if(applicationIdNode != null) {
         setApplicationId(Tool.getValue(applicationIdNode));
      }

      Element authorizationTokenNode = Tool.getChildNodeByTagName(elem, "authorizationToken");

      if(authorizationTokenNode != null) {
         setAuthorizationToken(Tool.decryptPassword(Tool.getValue(authorizationTokenNode)));
      }
   }

   private String applicationId;
   private String authorizationToken;
}
