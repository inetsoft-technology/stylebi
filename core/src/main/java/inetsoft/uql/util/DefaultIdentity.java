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
package inetsoft.uql.util;

import inetsoft.sree.security.IdentityID;
import inetsoft.uql.XPrincipal;
import inetsoft.util.Tool;
import org.w3c.dom.Element;

import java.io.PrintWriter;
import java.util.Objects;

/**
 * This class defines the DefaultIdentity.
 *
 * @version 8.5, 6/15/2006
 * @author InetSoft Technology Corp
 */
public class DefaultIdentity extends AbstractIdentity {
   /**
    * Constructor.
    */
   public DefaultIdentity() {
      super();
   }

   /**
    * Constructor.
    */
   public DefaultIdentity(Identity identity) {
      this(identity.getName(), identity.getOrganization(), identity.getType());

      if(identity instanceof DefaultIdentity) {
         isChanged = ((DefaultIdentity) identity).isChanged();
      }
   }

   /**
    * Constructor.
    */
   public DefaultIdentity(String name, int type) {
      this(name, null, type);
   }

   /**
    * Constructor.
    */
   public DefaultIdentity(IdentityID identityID, int type) {
      this(identityID.name, identityID.organization, type);
   }

   public DefaultIdentity(String name, String orgID, int type) {
      this.name = name;
      this.type = type;
      this.orgID = orgID;
   }

   @Override
   public IdentityID getIdentityID() {
      return new IdentityID(name, orgID);
   }

   /**
    * Write xml element representation to a print writer.
    * @param writer the specified print writer.
    */
   public void writeXML(PrintWriter writer) {
      writer.print("<defaultIdentity class=\"" + getClass().getName() +
         "\" type=\"" + type + "\" isChanged=\"" + isChanged + "\">");
      writer.print("<name><![CDATA[" + name + "]]></name>");

      if(orgID != null) {
         writer.format("<organization><![CDATA[%s]]></organization>", orgID);
      }

      writer.println("</defaultIdentity>");
   }

   /**
    * Parse xml element representation.
    * @param tag the specified xml element representation.
    */
   public void parseXML(Element tag) throws Exception {
      name = Tool.getChildValueByTagName(tag, "name");
      orgID = Tool.getChildValueByTagName(tag, "organization");
      type = Integer.parseInt(Objects.requireNonNull(Tool.getAttribute(tag, "type")));
      isChanged = Boolean.parseBoolean(Tool.getAttribute(tag, "isChanged"));
   }

   /**
    * Get the type of the default identity.
    */
   @Override
   public int getType() {
      return this.type;
   }

   /**
    * Set the type of the default identity.
    */
   public void setType(int type) {
      this.type = type;
   }

   /**
    * Get the name of the role.
    */
   @Override
   public String getName() {
      return this.name;
   }

   /**
    * Set the name of the role.
    */
   public void setName(String name) {
      this.name = name;
   }

   @Override
   public String getOrganization() {
      return orgID;
   }

   /**
    * Get the name of the role.
    */
   public boolean isChanged() {
      return this.isChanged;
   }

   /**
    * Get the name of the role.
    */
   public void setChanged(boolean isChanged) {
      this.isChanged = isChanged;
   }

   /**
    * Create one user.
    */
   @Override
   public XPrincipal create() {
      return XUtil.getXIdentityFinder().create(this);
   }

   private String name;
   private String orgID;
   private int type;
   private boolean isChanged;
}
