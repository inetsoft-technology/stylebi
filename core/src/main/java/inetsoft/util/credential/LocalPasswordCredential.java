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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Element;

import java.io.PrintWriter;

public class LocalPasswordCredential extends AbstractLocalCredential implements PasswordCredential {
   public LocalPasswordCredential() {
      super();
   }

   @Override
   public String getUser() {
      return user;
   }

   @Override
   public void setUser(String user) {
      this.user = user;
   }

   @Override
   public String getPassword() {
      return password;
   }

   @Override
   public void setPassword(String password) {
      if(password == null) {
         password = "";
      }

      this.password = password;
   }

   @Override
   public boolean isEmpty() {
      return super.isEmpty() && Tool.isEmptyString(getUser()) && Tool.isEmptyString(getPassword());
   }

   @Override
   public void reset() {
      super.reset();
      user = "";
      password = "";
   }

   @Override
   public boolean equals(Object obj) {
      if(!super.equals(obj) || !(obj instanceof LocalPasswordCredential)) {
         return false;
      }

      return Tool.equals(((LocalPasswordCredential) obj).user, user) &&
         Tool.equals(((LocalPasswordCredential) obj).password, password);
   }

   @Override
   protected void writeContent(PrintWriter writer) {
      if(getUser() != null) {
         writer.println("<user><![CDATA[" + getUser() + "]]></user>");
      }

      if(getPassword() != null) {
         writer.format(
            "<password><![CDATA[%s]]></password>%n", Tool.encryptPassword(getPassword()));
      }
   }

   @Override
   public void parseXML(Element elem) throws Exception {
      if(elem == null) {
         return;
      }

      setUser(Tool.getChildValueByTagName(elem, "user"));
      setPassword(Tool.decryptPassword(Tool.getChildValueByTagName(elem, "password")));
   }

   private String user;
   private String password;
}
