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

public class LocalClientTokenCredential extends AbstractLocalCredential
   implements ClientTokenCredential
{
   public LocalClientTokenCredential() {
      super();
   }

   @Override
   void writeContent(PrintWriter writer) {
      if(getAccessToken() != null) {
         writer.format("<accessToken><![CDATA[%s]]></accessToken>%n", Tool.encryptPassword(getAccessToken()));
      }

      if(getClientId() != null) {
         writer.format("<clientId><![CDATA[%s]]></clientId>%n", getClientId());
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

      Element node = Tool.getChildNodeByTagName(elem, "accessToken");

      if(node != null) {
         setAccessToken(Tool.decryptPassword(Tool.getValue(node)));
      }
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
   public String getAccessToken() {
      return accessToken;
   }

   @Override
   public void setAccessToken(String accessToken) {
      this.accessToken = accessToken;
   }

   @Override
   public boolean isEmpty() {
      return super.isEmpty() && StringUtils.isEmpty(accessToken) && StringUtils.isEmpty(clientId);
   }

   @Override
   public void reset() {
      super.reset();
      accessToken = null;
      clientId = null;
   }

   @Override
   public boolean equals(Object obj) {
      if(!super.equals(obj)) {
         return false;
      }

      if(!(obj instanceof LocalClientTokenCredential)) {
         return false;
      }

      return Tool.equals(((LocalClientTokenCredential) obj).accessToken, accessToken) &&
         Tool.equals(((LocalClientTokenCredential) obj).clientId, clientId);
   }

   private String accessToken = "";
   private String clientId = "";
}
