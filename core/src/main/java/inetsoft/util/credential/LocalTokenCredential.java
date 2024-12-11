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

public class LocalTokenCredential extends AbstractLocalCredential
   implements TokenCredential
{
   public LocalTokenCredential() {
      super();
   }

   public String getToken() {
      return token;
   }

   public void setToken(String token) {
      this.token = token;
   }

   @Override
   public boolean isEmpty() {
      return super.isEmpty() && StringUtils.isEmpty(token);
   }

   @Override
   public void reset() {
      super.reset();
      token = null;
   }

   @Override
   public boolean equals(Object obj) {
      if(!super.equals(obj)) {
         return false;
      }

      if(!(obj instanceof LocalTokenCredential)) {
         return false;
      }

      return Tool.equals(((LocalTokenCredential) obj).token, token);
   }

   @Override
   protected void writeContent(PrintWriter writer) {
      if(getToken() != null) {
         writer.format(
            "<token><![CDATA[%s]]></token>%n", Tool.encryptPassword(getToken()));
      }
   }

   @Override
   public void parseXML(Element elem) throws Exception {
      if(elem == null) {
         return;
      }

      super.parseXML(elem);
      Element tokenNode = Tool.getChildNodeByTagName(elem, "token");

      if(tokenNode != null) {
         setToken(Tool.decryptPassword(Tool.getValue(tokenNode)));
      }
   }

   private String token;
}
