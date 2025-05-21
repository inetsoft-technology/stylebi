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
package inetsoft.sree;

import inetsoft.report.internal.license.LicenseManager;
import inetsoft.sree.security.IdentityID;
import inetsoft.sree.security.Organization;
import inetsoft.uql.XPrincipal;
import inetsoft.util.*;
import org.apache.commons.lang3.StringUtils;
import org.w3c.dom.*;

import java.io.*;
import java.util.Locale;

/**
 * Client side information required by report server for authentication.
 * Include user name, client side address and session id.
 */
public class ClientInfo implements Cloneable, Serializable, XMLSerializable {
   /**
    * Anonymous user name.
    */
   public static final String ANONYMOUS = XPrincipal.ANONYMOUS;

   /**
    * Local host.
    */
   public static final String LOCALHOST = "localhost";

   /**
    * Construct a client side information object.
    */
   public ClientInfo() {
      this(null, null);
   }

   /**
    * Construct a client side information object.
    *
    * @param userID the user name.
    * @param address the client ip address.
    */
   public ClientInfo(IdentityID userID, String address) {
      this(userID, address, null);
   }

   /**
    * Construct a client side information object.
    *
    * @param userID the user name.
    * @param address the client ip address.
    * @param session the session id.
    */
   public ClientInfo(IdentityID userID, String address, String session) {
      this(userID, address, session, null);
   }

   public ClientInfo(IdentityID userID, String address, String session, Locale locale) {
      this.userID = userID;
      this.addr = (address == null) ? "" : address;
      this.session = session == null ? "" : session;
      this.locale = locale;
   }

   /**
    * Returns the user name.
    *
    * @return the user name.
    */
   public IdentityID getUserIdentity() {
      return userID;
   }

   /**
    * Set the user name.
    *
    * @param user the user name.
    * @hidden
    */
   public void setUserName(IdentityID user) {
      this.userID = user;
   }

   /**
    * Returns the login user name.
    *
    * @return the login user name.
    */
   public IdentityID getLoginUserID() {
      // fix bug1269329062133, default loginUser is null,
      // if loginUser is null, return user as default
      return loginUser == null ? userID : loginUser;
   }

   /**
    * Set login user name.
    *
    * @param loginUser the specified login user name.
    */
   public void setLoginUserName(IdentityID loginUser) {
      this.loginUser = loginUser;
   }

   /**
    * Returns the client side ip address.
    *
    * @return the ip address.
    */
   public String getIPAddress() {
      return addr;
   }

   /**
    * Returns the client session id.
    */
   public String getSession() {
      return session;
   }

   public Locale getLocale() {
      return locale;
   }

   public void setLocale(Locale locale) {
      this.locale = locale;
   }

   /**
    * Compare this client info with the specified object. Returns true if the
    * object passed in matches the client info represented by this object.
    *
    * @param obj another object to comapred.
    *
    * @return true if the object passed in matches the client info
    * represented by this object.
    */
   public boolean equals(Object obj) {
      if(obj == null || !(obj instanceof ClientInfo)) {
         return false;
      }

      if(this == obj) {
         return true;
      }

      ClientInfo that = (ClientInfo) obj;

      if(Tool.equals(this.getUserIdentity(), that.getUserIdentity()) &&
         Tool.equals(this.getIPAddress(), that.getIPAddress()) &&
         Tool.equals(this.getSession(), that.getSession()) &&
         Tool.equals(this.getLocale(), that.getLocale()))
      {
         return true;
      }

      return false;
   }

   /**
    * Returns a hashcode for this object.
    *
    * @return a hashcode for this object.
    */
   public int hashCode() {
      int hash = 0;

      if(userID != null) {
         hash += userID.convertToKey().hashCode();
      }

      if(addr != null) {
         hash += addr.hashCode();
      }

      if(session != null) {
         hash += session.hashCode();
      }

      if(locale != null) {
         hash += locale.hashCode();
      }

      return hash;
   }

   /**
    * Returns a string representation of this object.
    */
   @Override
   public String toString() {
      return toString(true);
   }

   public String toString(boolean includeOrg) {
      String user = includeOrg ? userID.toString() : userID.getName();
      return "Client[" + user + "@" + addr + "@" + session +  "@" + locale + "]";
   }

   /**
    * Get the view.
    */
   public String toView() {
      return userID + "@" + addr + "@" + session + "@" + locale;
   }

   /**
    * Clone this object.
    */
   @Override
   public Object clone() {
      return new ClientInfo(userID, addr, session, locale);
   }

   /**
    * Write xml element representation to a print writer.
    *
    * @param writer the specified print writer.
    */
   @Override
   public void writeXML(PrintWriter writer) {
      writer.print("<clientInfo>");
      writer.print("<user>");
      writer.print("<![CDATA[" + Tool.byteEncode(userID.convertToKey()) + "]]>");
      writer.print("</user>");
      writer.print("<addr>");
      writer.print("<![CDATA[" + addr + "]]>");
      writer.print("</addr>");
      writer.print("<session>");
      writer.print("<![CDATA[" + session + "]]>");
      writer.print("</session>");

      if(locale != null) {
         writer.print("<locale>");
         writer.print("<![CDATA[" + locale.getLanguage());

         if(!StringUtils.isBlank(locale.getCountry())) {
            writer.print("_" + locale.getCountry());
         }

         writer.print("]]>");
         writer.print("</locale>");
      }

      writer.println("</clientInfo>");
   }

   /**
    * Parse xml element representation.
    *
    * @param elem the specified xml element representation.
    */
   @Override
   public void parseXML(Element elem) throws Exception {
      NodeList users = Tool.getChildNodesByTagName(elem, "user");
      NodeList addrs = Tool.getChildNodesByTagName(elem, "addr");
      NodeList sessions = Tool.getChildNodesByTagName(elem, "session");

      if(users.getLength() == 0 || addrs.getLength() == 0) {
         throw new IOException("Missing user or addr tag");
      }

      userID = IdentityID.getIdentityIDFromKey(Tool.byteDecode(Tool.getValue(users.item(0))));
      addr = Tool.getValue(addrs.item(0));

      if(addr == null) {
         addr = "";
      }

      session = sessions == null ? null : Tool.getValue(sessions.item(0));
      session = session == null ? "" : session;

      Node localeNode = Tool.getChildNodeByTagName(elem, "locale");

      if(localeNode != null) {
         String localeName = Tool.getValue(localeNode);
         locale = Catalog.parseLocale(localeName);
      }
   }

   // for backward compatibility
   private static final long serialVersionUID = 5676785387536905773L;
   private IdentityID userID = new IdentityID(ANONYMOUS, Organization.getDefaultOrganizationID());
   private IdentityID loginUser = null;
   private String addr = LOCALHOST;
   private Locale locale = null;
   private String session;
}