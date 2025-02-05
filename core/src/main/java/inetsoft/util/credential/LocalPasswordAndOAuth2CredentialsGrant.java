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

public class LocalPasswordAndOAuth2CredentialsGrant extends LocalOAuth2CredentialsGrant
   implements PasswordAndOAuth2CredentialsGrant
{
   public LocalPasswordAndOAuth2CredentialsGrant() {
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
      this.password = password;
   }

   @Override
   public boolean isEmpty() {
      return super.isEmpty() && StringUtils.isEmpty(user) && StringUtils.isEmpty(password);
   }

   @Override
   public void reset() {
      super.reset();
      user = null;
      password = null;
   }

   @Override
   public boolean equals(Object obj) {
      if(!super.equals(obj)) {
         return false;
      }

      if(!(obj instanceof LocalPasswordAndOAuth2CredentialsGrant)) {
         return false;
      }

      return Tool.equals(((LocalPasswordAndOAuth2CredentialsGrant) obj).user, user) &&
         Tool.equals(((LocalPasswordAndOAuth2CredentialsGrant) obj).password, password);
   }

   @Override
   protected void writeContent(PrintWriter writer) {
      super.writeContent(writer);

      if(getUser() != null) {
         writer.format("<user><![CDATA[%s]]></user>%n", getUser());
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

      super.parseXML(elem);

      Element userNode = Tool.getChildNodeByTagName(elem, "user");

      if(userNode != null) {
         setUser(Tool.getValue(userNode));
      }

      Element passwordNode = Tool.getChildNodeByTagName(elem, "password");

      if(passwordNode != null) {
         setPassword(Tool.decryptPassword(Tool.getValue(passwordNode)));
      }
   }

   private String user;
   private String password;
}