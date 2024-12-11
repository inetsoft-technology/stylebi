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

public class LocalPasswordAndApiTokenCredential extends LocalPasswordCredential
   implements PasswordAndApiTokenCredential
{
   public LocalPasswordAndApiTokenCredential() {
      super();
   }

   @Override
   public String getApiToken() {
      return apiToken;
   }

   @Override
   public void setApiToken(String apiToken) {
      this.apiToken = apiToken;
   }

   @Override
   public boolean isEmpty() {
      return super.isEmpty() && Tool.isEmptyString(getApiToken());
   }

   @Override
   public void reset() {
      super.reset();
      apiToken = "";
   }

   @Override
   public boolean equals(Object obj) {
      if(!super.equals(obj) || !(obj instanceof LocalPasswordAndApiTokenCredential)) {
         return false;
      }

      return Tool.equals(((LocalPasswordAndApiTokenCredential) obj).apiToken, apiToken);
   }

   @Override
   protected void writeContent(PrintWriter writer) {
      super.writeContent(writer);

      if(getApiToken() != null) {
         writer.println("<apiToken><![CDATA[" + Tool.encryptPassword(getApiToken()) + "]]></apiToken>");
      }
   }

   @Override
   public void parseXML(Element elem) throws Exception {
      if(elem == null) {
         return;
      }

      super.parseXML(elem);
      setApiToken(Tool.decryptPassword(Tool.getChildValueByTagName(elem, "apiToken")));
   }

   private String apiToken;
}