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

public class LocalApiTokenCredential extends AbstractLocalCredential
   implements ApiTokenCredential
{
   public LocalApiTokenCredential() {
      super();
   }

   public String getApiToken() {
      return apiToken;
   }

   public void setApiToken(String apiToken) {
      this.apiToken = apiToken;
   }

   @Override
   public boolean isEmpty() {
      return super.isEmpty() && StringUtils.isEmpty(apiToken);
   }

   @Override
   public void reset() {
      super.reset();
      apiToken = null;
   }

   @Override
   public boolean equals(Object obj) {
      if(!super.equals(obj)) {
         return false;
      }

      if(!(obj instanceof LocalApiTokenCredential)) {
         return false;
      }

      return Tool.equals(((LocalApiTokenCredential) obj).apiToken, apiToken);
   }

   @Override
   protected void writeContent(PrintWriter writer) {
      if(getApiToken() != null) {
         writer.format("<apiToken><![CDATA[%s]]></apiToken>%n", Tool.encryptPassword(getApiToken()));
      }
   }

   @Override
   public void parseXML(Element elem) throws Exception {
      if(elem == null) {
         return;
      }

      super.parseXML(elem);
      Element apiTokenNode = Tool.getChildNodeByTagName(elem, "apiToken");

      if(apiTokenNode != null) {
         setApiToken(Tool.decryptPassword(Tool.getValue(apiTokenNode)));
      }
   }

   private String apiToken;
}
