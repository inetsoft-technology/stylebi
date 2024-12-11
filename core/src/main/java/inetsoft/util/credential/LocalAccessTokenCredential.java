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

public class LocalAccessTokenCredential extends AbstractLocalCredential
   implements AccessTokenCredential
{
   public LocalAccessTokenCredential() {
      super();
   }

   public String getAccessToken() {
      return accessToken;
   }

   public void setAccessToken(String accessToken) {
      this.accessToken = accessToken;
   }

   @Override
   public boolean isEmpty() {
      return super.isEmpty() && StringUtils.isEmpty(accessToken);
   }

   @Override
   public void reset() {
      super.reset();
      accessToken = null;
   }

   @Override
   public boolean equals(Object obj) {
      if(!super.equals(obj)) {
         return false;
      }

      if(!(obj instanceof LocalAccessTokenCredential)) {
         return false;
      }

      return Tool.equals(((LocalAccessTokenCredential) obj).accessToken, accessToken);
   }

   @Override
   protected void writeContent(PrintWriter writer) {
      if(getAccessToken() != null) {
         writer.format("<accessToken><![CDATA[%s]]></accessToken>%n", Tool.encryptPassword(getAccessToken()));
      }
   }

   @Override
   public void parseXML(Element elem) throws Exception {
      if(elem == null) {
         return;
      }

      super.parseXML(elem);
      Element node = Tool.getChildNodeByTagName(elem, "accessToken");

      if(node != null) {
         setAccessToken(Tool.decryptPassword(Tool.getValue(node)));
      }
   }

   private String accessToken = "";
}
