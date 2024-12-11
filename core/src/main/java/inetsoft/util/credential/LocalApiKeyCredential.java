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

public class LocalApiKeyCredential extends AbstractLocalCredential
   implements ApiKeyCredential
{
   public LocalApiKeyCredential() {
      super();
   }

   public String getApiKey() {
      return apiKey;
   }

   public void setApiKey(String apiKey) {
      this.apiKey = apiKey;
   }

   @Override
   public boolean isEmpty() {
      return super.isEmpty() && StringUtils.isEmpty(apiKey);
   }

   @Override
   public void reset() {
      super.reset();
      apiKey = null;
   }

   @Override
   public boolean equals(Object obj) {
      if(!super.equals(obj)) {
         return false;
      }

      if(!(obj instanceof LocalApiKeyCredential)) {
         return false;
      }

      return Tool.equals(((LocalApiKeyCredential) obj).apiKey, apiKey);
   }

   @Override
   protected void writeContent(PrintWriter writer) {
      if(getApiKey() != null) {
         writer.format("<apiKey><![CDATA[%s]]></apiKey>%n", Tool.encryptPassword(getApiKey()));
      }
   }

   @Override
   public void parseXML(Element elem) throws Exception {
      if(elem == null) {
         return;
      }

      super.parseXML(elem);
      Element apiKeyNode = Tool.getChildNodeByTagName(elem, "apiKey");

      if(apiKeyNode != null) {
         setApiKey(Tool.decryptPassword(Tool.getValue(apiKeyNode)));
      }
   }

   private String apiKey;
}
