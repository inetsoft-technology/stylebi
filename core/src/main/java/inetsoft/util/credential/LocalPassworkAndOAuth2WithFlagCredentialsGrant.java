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

public class LocalPassworkAndOAuth2WithFlagCredentialsGrant extends LocalPassworkAndOAuth2CredentialsGrant
   implements PassworkAndOAuth2WithFlagCredentialsGrant
{
   public LocalPassworkAndOAuth2WithFlagCredentialsGrant() {
      super();
   }

   @Override
   public String getOauthFlags() {
      return oauthFlags;
   }

   @Override
   public void setOauthFlags(String oauthFlags) {
      this.oauthFlags = oauthFlags;
   }

   @Override
   public boolean isEmpty() {
      return super.isEmpty() && StringUtils.isEmpty(oauthFlags);
   }

   @Override
   public void reset() {
      super.reset();
      oauthFlags = null;
   }

   @Override
   public boolean equals(Object obj) {
      if(!super.equals(obj)) {
         return false;
      }

      if(!(obj instanceof LocalPassworkAndOAuth2WithFlagCredentialsGrant)) {
         return false;
      }

      return Tool.equals(((LocalPassworkAndOAuth2WithFlagCredentialsGrant) obj).oauthFlags, oauthFlags);
   }

   @Override
   protected void writeContent(PrintWriter writer) {
      super.writeContent(writer);

      if(getOauthFlags() != null) {
         writer.format("<oauthFlags><![CDATA[%s]]></oauthFlags>%n", getOauthFlags());
      }
   }

   @Override
   public void parseXML(Element elem) throws Exception {
      if(elem == null) {
         return;
      }

      super.parseXML(elem);
      Element oauthFlagsNode = Tool.getChildNodeByTagName(elem, "oauthFlags");

      if(oauthFlagsNode != null) {
         setOauthFlags(Tool.getValue(oauthFlagsNode));
      }
   }

   private String oauthFlags;
}
