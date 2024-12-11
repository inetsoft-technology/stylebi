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


public class LocalClientKeyPasswordCredential extends LocalPasswordCredential
   implements ClientKeyPasswordCredential
{
   public LocalClientKeyPasswordCredential() {
      super();
   }

   public String getClientKey() {
      return clientKey;
   }

   public void setClientKey(String clientKey) {
      this.clientKey = clientKey;
   }

   @Override
   public boolean isEmpty() {
      return super.isEmpty() && StringUtils.isEmpty(clientKey);
   }

   @Override
   public void reset() {
      super.reset();
      clientKey = null;
   }

   @Override
   public boolean equals(Object obj) {
      if(!super.equals(obj)) {
         return false;
      }

      if(!(obj instanceof LocalClientKeyPasswordCredential)) {
         return false;
      }

      return Tool.equals(((LocalClientKeyPasswordCredential) obj).clientKey, clientKey);
   }

   @Override
   protected void writeContent(PrintWriter writer) {
      super.writeContent(writer);

      if(getClientKey() != null) {
         writer.format(
            "<clientKey><![CDATA[%s]]></clientKey>%n", Tool.encryptPassword(getClientKey()));
      }
   }

   @Override
   public void parseXML(Element elem) throws Exception {
      if(elem == null) {
         return;
      }

      super.parseXML(elem);
      Element clientKeyNode = Tool.getChildNodeByTagName(elem, "clientKey");

      if(clientKeyNode != null) {
         setClientKey(Tool.decryptPassword(Tool.getValue(clientKeyNode)));
      }
   }

   private String clientKey;
}
