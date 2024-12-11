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

public class LocalSecurityTokenCredential extends LocalPasswordCredential implements SecurityTokenCredential {
   public LocalSecurityTokenCredential() {
      super();
   }

   @Override
   public String getSecurityToken() {
      return securityToken;
   }

   @Override
   public void setSecurityToken(String securityToken) {
      this.securityToken = securityToken;
   }

   @Override
   public boolean isEmpty() {
      return super.isEmpty() && StringUtils.isEmpty(securityToken);
   }

   @Override
   public void reset() {
      super.reset();
      securityToken = null;
   }

   @Override
   public boolean equals(Object obj) {
      if(!super.equals(obj)) {
         return false;
      }

      if(!(obj instanceof LocalSecurityTokenCredential)) {
         return false;
      }

      return Tool.equals(((LocalSecurityTokenCredential) obj).securityToken, securityToken);
   }

   @Override
   protected void writeContent(PrintWriter writer) {
      super.writeContent(writer);

      if(getSecurityToken() != null) {
         writer.format(
            "<securityToken><![CDATA[%s]]></securityToken>%n", Tool.encryptPassword(getSecurityToken()));
      }
   }

   @Override
   public void parseXML(Element elem) throws Exception {
      if(elem == null) {
         return;
      }

      super.parseXML(elem);
      Element node = Tool.getChildNodeByTagName(elem, "securityToken");

      if(node != null) {
         setSecurityToken(Tool.decryptPassword(Tool.getValue(node)));
      }
   }

   private String securityToken;
}
