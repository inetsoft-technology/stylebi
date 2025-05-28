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
package inetsoft.uql;

import inetsoft.sree.internal.SUtil;
import inetsoft.sree.security.*;
import inetsoft.uql.util.Identity;
import inetsoft.uql.util.XSessionService;
import inetsoft.util.Catalog;
import inetsoft.util.Tool;
import inetsoft.util.script.JavaScriptEngine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.io.*;
import java.security.Principal;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A Principal implementation used to identify a user to the query engine.
 *
 * @author  InetSoft Technology
 * @since   6.1
 */
public class XPrincipal implements Principal, Serializable, Cloneable {
   /**
    * Property name for user locale. The user locale information can be
    * set or accessed using this property name and getProperty or setProperty
    * methods.  For example, <code>XPrincipal</code> can be localized for
    * US-English as follows:
    * <code>setProperty(XPrincipal.LOCALE,"en_US")</code>.
    */
   public static final String LOCALE = "locale";

   /**
    * Anonymous user name.
    */
   public static final String ANONYMOUS = "anonymous";

   /**
    * The property name for alias.
    */
   public static final String ALIAS = "__alias__";

   /**
    * The built-in system account.
    */
   public static final String SYSTEM = "INETSOFT_SYSTEM";

   /**
    * Creates a new instance of XPrincipal.
    *
    * @param identityID the name of the user.
    */
   public XPrincipal(IdentityID identityID) {
      this(identityID, new IdentityID[0], new String[0], null);
   }

   /**
    * Creates a new instance of XPrincipal.
    *
    * @param identityID the name of the user.
    * @param roles      the roles assigned to the user.
    */
   public XPrincipal(IdentityID identityID, IdentityID[] roles, String[] groups, String orgId) {
      this.name = identityID != null ? identityID.convertToKey() : null;
      this.roles = roles == null ? new IdentityID[0] : roles;
      this.groups = groups == null ? new String[0] : groups;

      if(orgId == null) {
         this.orgId = identityID != null ? identityID.getOrgID() : null;
      }
      else {
         this.orgId = orgId;
      }

      this.sessionID =
         XSessionService.createSessionID(XSessionService.USER,
                                         identityID != null ? identityID.convertToKey() : null);
      this.prop = new ConcurrentHashMap<>();
      this.params = new ConcurrentHashMap<>();
   }

   /**
    * Create a principal from with the same name, roles, groups, and properties.
    */
   public XPrincipal(XPrincipal principal) {
      this.name = principal.name;
      this.roles = principal.roles.clone();
      this.groups = principal.groups.clone();
      this.orgId = principal.orgId;
      this.prop = new ConcurrentHashMap<>(principal.prop);
      this.params = new ConcurrentHashMap<>(principal.params);
      this.paramTS = new ConcurrentHashMap<>(principal.paramTS);
      this.ignoreLogin = principal.ignoreLogin;
      this.sessionID = principal.sessionID;
   }

   /**
    * Gets the name of the user.
    *
    * @return the name of the user.
    */
   @Override
   public String getName() {
      return name;
   }

   public IdentityID getIdentityID() { return IdentityID.getIdentityIDFromKey(name);}

   /**
    * Sets roles assigned to the user.
    *
    * @param roles an array of the names of the roles assigned to the user.
    */
   public void setRoles(IdentityID[] roles) {
      if(!Arrays.equals(this.roles, roles)) {
         this.roles = roles == null ? new IdentityID[0] : roles;
         this.allRoles = null;
      }
   }

   /**
    * Sets the groups assigned to the user.
    *
    * @param groups an array of the names of the groups assigned to the user.
    */
   public void setGroups(String[] groups) {
      if(!Arrays.equals(this.groups, groups)) {
         this.groups = groups == null ? new String[0] : groups;
         this.allGroups = null;
      }
   }

   /**
    * Sets the id of the organization assigned to the user
    *
    * @param orgId the id of the organization assigned to the user.
    */
   public void setOrgId(String orgId) {
      if(orgId != null && !orgId.isEmpty()) {
         this.orgId = orgId;
      }
   }

   /**
    * Gets the roles assigned to the user.
    *
    * @return an array of the names of the roles assigned to the user.
    */
   public IdentityID[] getRoles() {
      return roles == null ? new IdentityID[0] : roles;
   }

   /**
    * Gets the groups assigned to the user.
    *
    * @return an array of the names of the groups assigned to the user.
    */
   public String[] getGroups() {
      return groups == null ? new String[0] : groups;
   }

   /**
    * Gets the organization id assigned to the user
    *
    * @return the organization id.
    */
   public String getOrgId() {
      return orgId;
   }

   /**
    * Get the session id.
    * @return the session id if any, <tt>null</tt> otherwise.
    */
   public String getSessionID() {
      return sessionID;
   }

   /**
    * Set a property.
    *
    * @param name property name.
    * @param val property value.
    */
   public void setProperty(String name, String val) {
      if(val == null || val.equals("")) {
         prop.remove(name);
      }
      else {
         if(LOCALE.equals(name)) {
            try {
               Catalog.parseLocale(val);
            }
            catch(Exception e) {
               LOG.warn("Invalid locale for principal: {}", val, e);
            }
         }

         prop.put(name, val);
      }
   }

   /**
    * Get a property value.
    * @param name property name.
    * @return property value.
    */
   public String getProperty(String name) {
      return prop.get(name);
   }

   /**
    * Get all attribute names.
    */
   public Set<String> getPropertyNames() {
      // Bug #57296, use JDK enumeration and prevent concurrent modification
      return new HashSet<>(prop.keySet());
   }

   /**
    * Determines if this XPrincipal is equivelent to another object.
    *
    * @param another the object to compare.
    *
    * @return <code>true</code> if the objects are equivelent; <code>false</code>
    *         otherwise.
    */
   public boolean equals(Object another) {
      if(!(another instanceof Principal principal)) {
         return false;
      }

      if(another instanceof XPrincipal p) {
         if(Identity.UNKNOWN_USER.equals(name)) {
            return Tool.equals(p.getName(), name) &&
               Tool.equals(p.roles, roles) &&
               Tool.equals(p.groups, groups);
         }
      }

      return Objects.equals(getName(), principal.getName());
   }

   /**
    * Gets a hash code for this object.
    *
    * @return a hash code.
    */
   public int hashCode() {
      if(Identity.UNKNOWN_USER.equals(name)) {
         return toString().hashCode();
      }
      else {
         return name.hashCode();
      }
   }

   /**
    * Gets a string representation of this object.
    *
    * @return a string representation of this object.
    */
   public String toString() {
      IdentityID[] r = getRoles();
      StringBuilder rs = new StringBuilder();

      if(r != null) {
         for(int i = 0; i < r.length; i++) {
            rs.append(r[i]);
            rs.append(",");
         }
      }

      String[] g = getGroups();
      StringBuilder gs = new StringBuilder();

      if(g != null) {
         for(int i = 0; i < g.length; i++) {
            gs.append(g[i]);
            gs.append(",");
         }
      }

      return "XPrincipal[" + name + "]" +
         "Roles:[" + rs + "] Groups:[" + gs + "]";
   }

   /**
    * Returns the original hashCode.
    */
   public int addr() {
      return super.hashCode();
   }

   /**
    * Write xml element representation to a print writer.
    *
    * @param writer the specified print writer
    */
   public void writeXML(PrintWriter writer) {
      writer.print("<xprincipal>");
      writer.print("<user>");
      writer.print("<![CDATA[" + name + "]]>");
      writer.print("</user>");

      writer.print("<roles>");

      for(int i = 0; i < roles.length; i++) {
         writer.print("<role>");
         writer.print("<![CDATA[" + Tool.byteEncode(roles[i].convertToKey()) + "]]>");
         writer.print("</role>");
      }

      writer.print("</roles>");

      writer.print("<groups>");

      for(int i = 0; i < groups.length; i++) {
         writer.print("<group>");
         writer.print("<![CDATA[" + Tool.byteEncode(groups[i]) + "]]>");
         writer.print("</group>");
      }

      writer.print("</groups>");

      writer.print("<properties>");

      for(String key : prop.keySet()) {
         String val = prop.get(key);

         writer.print("<property>");
         writer.print("<key>");
         writer.print("<![CDATA[" + key + "]]>");
         writer.print("</key>");
         writer.print("<value>");
         writer.print("<![CDATA[" + val + "]]>");
         writer.print("</value>");
         writer.print("</property>");
      }

      writer.print("</properties>");
      writer.println("</xprincipal>");
   }

   /**
    * Parse xml element representation.
    *
    * @param elem the specified xml element representation
    */
   public void parseXML(Element elem) throws Exception {
      NodeList users = Tool.getChildNodesByTagName(elem, "user");

      if(users != null && users.getLength() > 0) {
         name = Tool.getValue(users.item(0));
         orgId = IdentityID.getIdentityIDFromKey(name).orgID;
      }

      Element rsnode = Tool.getChildNodeByTagName(elem, "roles");
      NodeList rnodes = Tool.getChildNodesByTagName(rsnode, "role");
      ArrayList list = new ArrayList();

      for(int i = 0; rnodes != null && i < rnodes.getLength(); i++) {
         list.add(IdentityID.getIdentityIDFromKey(Tool.byteDecode(Tool.getValue(rnodes.item(i)))));
      }

      roles = new IdentityID[list.size()];
      list.toArray(roles);

      Element gsnode = Tool.getChildNodeByTagName(elem, "groups");
      NodeList gnodes = Tool.getChildNodesByTagName(gsnode, "group");
      list = new ArrayList();

      for(int i = 0; gnodes != null && i < gnodes.getLength(); i++) {
         list.add(Tool.byteDecode(Tool.getValue(gnodes.item(i))));
      }

      groups = new String[list.size()];
      list.toArray(groups);

      NodeList psnodes = Tool.getChildNodesByTagName(elem, "properties");
      prop.clear();

      if(psnodes != null) {
         NodeList pnodes =
            Tool.getChildNodesByTagName((Element) psnodes.item(0), "property");

         for(int i = 0; i < pnodes.getLength(); i++) {
            NodeList knodes = Tool.getChildNodesByTagName(pnodes.item(i), "key");
            NodeList vnodes = Tool.getChildNodesByTagName(pnodes.item(i), "value");

            if(knodes.getLength() == 0 || vnodes.getLength() == 0) {
               throw new IOException("Missing key or value node");
            }

            setProperty(Tool.getValue(knodes.item(0)), Tool.getValue(vnodes.item(0)));
         }
      }
   }

   /**
    * Clone the object.
    */
   @Override
   public Object clone() {
      try {
         XPrincipal p = (XPrincipal) super.clone();
         p.prop = new ConcurrentHashMap<>(prop);
         return p;
      }
      catch(Exception ex) {
         LOG.error("Failed to clone object", ex);
      }

      return null;
   }

   /**
    * Set ignore login status.
    * @param ignoreLogin true if should not check login status of this
    * principal, false otherwise.
    */
   public void setIgnoreLogin(boolean ignoreLogin) {
      this.ignoreLogin = ignoreLogin;
   }

   /**
    * Check if should not check login status.
    * @return true if hould not check login status of this principal,
    * false otherwise.
    */
   public boolean isIgnoreLogin() {
      return ignoreLogin;
   }

   /**
    * Set the value of a request parameter.
    * @param name the name of the parameter.
    * @param value the value of the parameter.
    */
   public void setParameter(String name, Object value) {
      setParameter(name, value, System.currentTimeMillis());
   }

   protected void setParameter(String name, Object value, long ts) {
      if(value == null) {
         params.remove(name);
      }
      else {
         params.put(name, JavaScriptEngine.unwrap(value));
         paramTS.put(name, ts);
      }
   }

   /**
    * Get the value of a request parameter.
    * @param name the name of the parameter.
    * @return the value of the parameter, or <code>null</code> if the parameter
    * does not exist.
    */
   public Object getParameter(String name) {
      return params.get(name);
   }

   /**
    * Get the timestamp of the parameter change.
    * @hidden
    */
   public long getParameterTS(String name) {
      Long ts = paramTS.get(name);
      return ts != null ? ts.longValue() : 0;
   }

   /**
    * Get the parameter names.
    * @return all the parameter names.
    * @hidden
    */
   public Set<String> getParameterNames() {
      return params.keySet();
   }

   /**
    * Set the alias to this specified user.
    */
   public void setAlias(String alias) {
      setProperty(ALIAS, alias);
   }

   /**
    * Get the alias of the specified user.
    */
   public String getAlias() {
      return getProperty(ALIAS);
   }

   public boolean isProfiling() {
      return profiling;
   }

   public void setProfiling(boolean profiling) {
      this.profiling = profiling;
   }

   public void updateRoles(AuthenticationProvider provider) {
      final HashSet<IdentityID> baseRoles = new HashSet<>();

      if(this.roles != null) {
         baseRoles.addAll(Arrays.asList(this.roles));
      }

      //check base roles of assigned Organization
      if(orgId != null && provider.getOrganization(orgId) != null) {
         IdentityID[] orgRoles = provider.getOrganization(orgId).getRoles();

         if(orgRoles != null) {
            baseRoles.addAll(Arrays.asList(orgRoles));
         }
      }

      Arrays.stream(getAllGroups(provider))
         .map(provider::getGroup)
         .filter(Objects::nonNull)
         .flatMap(g -> Arrays.stream(g.getRoles()))
         .forEach(baseRoles::add);

      this.allRoles = provider.getAllRoles(baseRoles.toArray(new IdentityID[0]));
   }

   /**
    * Get all roles include parent roles. Objects are cached for 10s.
    */
   public IdentityID[] getAllRoles(AuthenticationProvider provider) {
      IdentityID[] roles = this.allRoles;

      if(roles != null && System.currentTimeMillis() > allRolesTimeout) {
         roles = null;
      }

      if(roles == null) {
         updateRoles(provider);
         roles = this.allRoles;
         allRolesTimeout = System.currentTimeMillis() + TIMEOUT;
      }

      roles = Arrays.stream(roles).filter(role -> isRoleVisible(role)).toArray(IdentityID[]::new);

      return roles;
   }

   private boolean isRoleVisible(IdentityID role) {
      if(!SUtil.isMultiTenant() && "Organization Administrator".equals(role.name)) {
         return false;
      }

      return true;
   }

   /**
    * Get all groups include parent groups. Objects are cached for 10s.
    */
   public IdentityID[] getAllGroups(AuthenticationProvider provider) {
      IdentityID[] groups = this.allGroups;

      if(groups != null && System.currentTimeMillis() > allGroupsTimeout) {
         groups = null;
      }

      if(groups == null) {
         IdentityID[] groupIds = Arrays.stream(this.groups)
            .map(g -> new IdentityID(g, orgId)).toArray(IdentityID[]::new);
         groups = this.allGroups = provider.getAllGroups(groupIds);
         allGroupsTimeout = System.currentTimeMillis() + TIMEOUT;
      }

      return groups;
   }

   /**
    * Get the view of this specified user (alias will be used if any).
    */
   public String toView() {
      String alias = getAlias();
      return alias != null && alias.length() > 0 ? alias : getName();
   }

   /**
    * Get the full identifier of this specified user (ip, session, etc.)
    */
   public String getFullName() {
      return toView();
   }

   public String getCurrentOrgId() {
      String currentOrgId = OrganizationContextHolder.getCurrentOrgId();
      currentOrgId = currentOrgId == null ? getProperty("curr_org_id") : currentOrgId;
      currentOrgId = currentOrgId == null ? getOrgId() : currentOrgId;

      return currentOrgId;
   }

   // for backward compatibility
   private static final long serialVersionUID = 615300870728701542L;
   private static final Logger LOG = LoggerFactory.getLogger(XPrincipal.class);
   protected String name;
   protected IdentityID[] roles = {};
   protected String[] groups = {};
   protected String orgId;
   protected String sessionID;
   protected Map<String, String> prop;
   private Map<String, Object> params;
   private Map<String, Long> paramTS = new ConcurrentHashMap<>();
   private boolean ignoreLogin = false;
   private boolean profiling = false;

   private static long TIMEOUT = 10000;
   private transient IdentityID[] allRoles;
   private transient IdentityID[] allGroups;
   private transient long allRolesTimeout = 0;
   private transient long allGroupsTimeout = 0;
}