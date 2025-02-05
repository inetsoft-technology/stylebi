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
package inetsoft.sree.security;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import inetsoft.report.internal.license.LicenseManager;
import inetsoft.uql.XPrincipal;
import inetsoft.util.*;
import org.w3c.dom.Element;

import java.io.*;
import java.util.Objects;

/**
 * This class defines the identity info
 *
 * @version 8.5, 6/29/2006
 * @author InetSoft Technology Corp
 */
@JsonSerialize(as = IdentityID.class)
@JsonDeserialize(as = IdentityID.class)
@JsonIgnoreProperties(ignoreUnknown = true)
public class IdentityID implements Comparable<IdentityID>, Serializable, XMLSerializable, Cloneable {

   /**
    * @param name   the identity name.
    * @param orgID the organization id of the identity.
    */
   public IdentityID(String name, String orgID) {
      this.name = name;
      this.orgID = orgID;
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
   public String getOrgID(){
      return this.orgID;
   }

   /**
    * Set organization id of the identity.
    * @param orgID
    */
   public void setOrgID(String orgID){
      this.orgID = orgID;
   }

   public String convertToKey() {
      String org = this.orgID;

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
         String orgID = key.substring(deliminator + KEY_DELIMITER.length());

         if(orgID.equals(GLOBAL_ORG_KEY) || orgID.equals("null")) {
            orgID = null;
         }
         return new IdentityID(name, orgID);
      }
      else {
         XPrincipal principal = (XPrincipal) ThreadContext.getPrincipal();
         principal = principal == null ? (XPrincipal) ThreadContext.getContextPrincipal() : principal;
         String pOrgID = principal == null ? Organization.getDefaultOrganizationID() :
                        IdentityID.getIdentityIDFromKey(principal.getName()).orgID;
         return new IdentityID(key, pOrgID);
      }
   }

   public String getLabel() {
      boolean enterprise = LicenseManager.getInstance().isEnterprise();
      return enterprise ? (name + "(" + (orgID == null ? GLOBAL_ORG_KEY : orgID) + ")") : name;
   }

   @Override
   public boolean equals(Object o) {
      if(this == o) {
         return true;
      }

      if(!(o instanceof IdentityID that)) {
         return false;
      }

      return Objects.equals(name, that.name) && Objects.equals(orgID, that.orgID);
   }

   @Override
   public int hashCode() {
      return Objects.hash(name, orgID);
   }

   public boolean equalsIgnoreCase(IdentityID other) {
      return Tool.equals(this.name, other.name, false) &&
         Tool.equals(this.orgID, other.orgID, false);
   }

   @Override
   public Object clone() throws CloneNotSupportedException {
      return super.clone();
   }

   @Override
   public int compareTo(IdentityID o) {
      int nameComp = this.name.compareTo(o.name);
      return nameComp != 0 ? nameComp : Tool.compare(this.orgID, o.orgID);
   }

   @Override
   public String toString() {
      return "IdentityID{" +
         "name='" + name + '\'' +
         ", orgID='" + orgID + '\'' +
         '}';
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

      if(orgID != null) {
         writer.print("<organization>");
         writer.print("<![CDATA[" + orgID + "]]>");
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
      this.orgID = Tool.getChildValueByTagName(tag, "organization");
   }

   public static String getConvertKey(String idName) {
      return getConvertKey(idName, null);
   }

   public static String getConvertKey(String idName, String orgId) {
      if(orgId == null || orgId.isEmpty()) {
         orgId = OrganizationManager.getInstance().getCurrentOrgID();
      }

      idName = !idName.startsWith(idName + IdentityID.KEY_DELIMITER) ?
         idName + IdentityID.KEY_DELIMITER + orgId : idName;

      return idName;
   }

   public String name;
   public String orgID;
   public final static String KEY_DELIMITER = "~;~";
   private final static String GLOBAL_ORG_KEY = "__GLOBAL__";
}
