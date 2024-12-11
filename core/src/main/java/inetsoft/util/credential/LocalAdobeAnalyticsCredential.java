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

public class LocalAdobeAnalyticsCredential extends AbstractLocalCredential
   implements AdobeAnalyticsCredential
{
   public LocalAdobeAnalyticsCredential() {
      super();
   }

   @Override
   public String getApiKey() {
      return apiKey;
   }

   @Override
   public void setApiKey(String apiKey) {
      this.apiKey = apiKey;
   }

   @Override
   public String getGlobalCompanyId() {
      return globalCompanyId;
   }

   @Override
   public void setGlobalCompanyId(String globalCompanyId) {
      this.globalCompanyId = globalCompanyId;
   }

   @Override
   public boolean isEmpty() {
      return super.isEmpty() && StringUtils.isEmpty(apiKey) && StringUtils.isEmpty(globalCompanyId);
   }

   @Override
   public void reset() {
      super.reset();
      apiKey = null;
      globalCompanyId = null;
   }

   @Override
   public boolean equals(Object obj) {
      if(!super.equals(obj)) {
         return false;
      }

      if(!(obj instanceof LocalAdobeAnalyticsCredential)) {
         return false;
      }

      return Tool.equals(((LocalAdobeAnalyticsCredential) obj).apiKey, apiKey) &&
         Tool.equals(((LocalAdobeAnalyticsCredential) obj).globalCompanyId, globalCompanyId);
   }

   @Override
   protected void writeContent(PrintWriter writer) {
      if(getApiKey() != null) {
         writer.format("<apiKey><![CDATA[%s]]></apiKey>%n", Tool.encryptPassword(getApiKey()));
      }

      if(getGlobalCompanyId() != null) {
         writer.format("<globalCompanyId><![CDATA[%s]]></globalCompanyId>%n", getGlobalCompanyId());
      }
   }

   @Override
   public void parseXML(Element elem) throws Exception {
      if(elem == null) {
         return;
      }

      super.parseXML(elem);
      Element node = Tool.getChildNodeByTagName(elem, "apiKey");

      if(node != null) {
         setApiKey(Tool.decryptPassword(Tool.getValue(node)));
      }

      node = Tool.getChildNodeByTagName(elem, "globalCompanyId");

      if(node != null) {
         setGlobalCompanyId(Tool.getValue(node));
      }
   }

   private String apiKey;
   private String globalCompanyId;
}
