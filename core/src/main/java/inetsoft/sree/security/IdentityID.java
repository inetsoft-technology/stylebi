/*
 * inetsoft-core - StyleBI is a business intelligence web application.
 * Copyright © 2024 InetSoft Technology (info@inetsoft.com)
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
 * You should have received a copy of the GNU Affrero General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package inetsoft.sree.security;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import inetsoft.uql.XPrincipal;
import inetsoft.util.*;
import org.w3c.dom.Element;

import java.io.PrintWriter;
import java.io.Serializable;
import java.util.Objects;

/**
 * This class defines the identity info
 *
 * @version 8.5, 6/29/2006
 * @author InetSoft Technology Corp
 */
@JsonSerialize(as = IdentityID.class)
@JsonDeserialize(as = IdentityID.class)
public class IdentityID implements Comparable<IdentityID>, Serializable, XMLSerializable {

   /**
    * @param name   the identity name.
    * @param organization the organization name of the identity.
    */
   public IdentityID(String name, String organization) {
      this.name = name;
      this.organization = organization;
   }

   public IdentityID() {
      //default
   }

   public String getName(){
      return this.name;
   }

   public void setName(String name){
      this.name = name;
   }

   /**
    * @return organization name of the identity.
    */
   public String getOrganization(){
      return this.organization;
   }

   /**
    * Set organization name of the identity.
    * @param organization
    */
   public void setOrganization(String organization){
      this.organization = organization;
   }

   @Override
   public String toString() {
      return convertToKey();
   }

   public String convertToKey() {
      String org = this.organization;

      if(org == null) {
         org = GLOBAL_ORG_KEY;
      }

      return this.name + KEY_DELIMITER + org;
   }

   public static IdentityID getIdentityIDFromKey(String key) {
      if(key == null) {
         return null;
      }
      else if(key.isEmpty()) {
         return new IdentityID("", "");
      }

      int deliminator = key.indexOf(KEY_DELIMITER);
      if(deliminator > 0) {
         String name = key.substring(0, deliminator);
         String org = key.substring(deliminator + KEY_DELIMITER.length());

         if(org.equals(GLOBAL_ORG_KEY) || org.equals("null")) {
            org = null;
         }
         return new IdentityID(name, org);
      }
      else {
         XPrincipal principal = (XPrincipal) ThreadContext.getPrincipal();
         principal = principal == null ? (XPrincipal) ThreadContext.getContextPrincipal() : principal;
         String pOrg = principal == null ? Organization.getDefaultOrganizationName() :
                        IdentityID.getIdentityIDFromKey(principal.getName()).organization;
         return new IdentityID(key, pOrg);
      }
   }

   @Override
   public boolean equals(Object o) {
      if(this == o) {
         return true;
      }

      if(!(o instanceof IdentityID that)) {
         return false;
      }

      return Objects.equals(name, that.name) && Objects.equals(organization, that.organization);
   }

   @Override
   public int hashCode() {
      return Objects.hash(name, organization);
   }

   public boolean equalsIgnoreCase(IdentityID other) {
      return Tool.equals(this.name, other.name, false) &&
         Tool.equals(this.organization, other.organization, false);
   }

   @Override
   public int compareTo(IdentityID o) {
      int nameComp = this.name.compareTo(o.name);
      return nameComp != 0 ? nameComp : Tool.compare(this.organization, o.organization);
   }

   @Override
   public void writeXML(PrintWriter writer) {
      writer.println(
              "<IdentityID class=\"" + getClass().getName() + "\"");
      writer.println(">");

      if(name != null) {
         writer.print("<name>");
         writer.print("<![CDATA[" + name + "]]>");
         writer.print("</name>");
      }

      if(organization != null) {
         writer.print("<organization>");
         writer.print("<![CDATA[" + organization + "]]>");
         writer.print("</organization>");
      }

      writer.println("</IdentityID>");
   }

   @Override
   public void parseXML(Element tag) throws Exception {
      if(tag == null) {
         return;
      }
      this.name = Tool.getChildValueByTagName(tag, "name");
      this.organization = Tool.getChildValueByTagName(tag, "organization");
   }

   public String name;
   public String organization;
   public final static String KEY_DELIMITER = "~;~";
   private final static String GLOBAL_ORG_KEY = "__GLOBAL__";

}
