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

import inetsoft.sree.ClientInfo;
import inetsoft.sree.internal.SUtil;
import inetsoft.uql.XPrincipal;
import inetsoft.uql.util.Identity;
import inetsoft.util.*;
import org.apache.ignite.binary.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.io.*;
import java.lang.ref.WeakReference;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * This class implements <code>java.security.Principal</code> to represent
 * any entity, such as an individual, a corporation, and a login id. It
 * wraps the user id and a secure id, which is a secure random number
 *
 * @author Helen Chen
 * @version 5.1, 9/20/2003
 */
public class SRPrincipal extends XPrincipal implements Serializable, Externalizable, LogPrincipal {
   /**
    * Construct a <code>SRPrincipal</code> instance
    */
   public SRPrincipal() {
      this(new ClientInfo(), new IdentityID[0], new String[0], null, 0);
   }

   /**
    * Construct a <code>SRPrincipal</code> instance
    *
    * @param user the client information of the user.
    */
   public SRPrincipal(IdentityID user) {
      this(user, new IdentityID[0], new String[0], null, 0);
      IdentityID userId = IdentityID.getIdentityIDFromKey(name);

      if(!Identity.UNKNOWN_USER.equals(userId.name)) {
         try {
            SecurityProvider provider =
               SecurityEngine.getSecurity().getSecurityProvider();

            if(provider != null) {
               User identity = provider.getUser(userId);

               if(identity != null) {
                  SRIdentityFinder finder = new SRIdentityFinder();
                  setRoles(finder.getRoles(identity));
                  setGroups(finder.getGroups(identity));
                  setOrgId(identity.getOrganizationID());

                  if(identity.getLocale() != null) {
                     String locale = identity.getLocale();
                     String defaultLocale = Catalog.getCatalog().getString("Default");

                     //overwrite default user locale with set organization locale
                     if((locale == null || "".equals(locale) || defaultLocale.equals(locale)) && identity.getOrganizationID() != null) {
                        Organization userOrg = provider.getOrganization(identity.getOrganizationID());

                        if(userOrg.getLocale() != null && !"".equals(userOrg.getLocale())) {
                           locale = userOrg.getLocale();
                        }
                     }
                     this.setProperty(LOCALE, locale);
                  }
               }
            }
         }
         catch (Exception ex) {
            LOG.error("Failed to initialize principal for user: " + user, ex);
         }
      }
   }

   /**
    * Construct a <code>SRPrincipal</code> instance
    *
    * @param user the client information of the user.
    * @param roles roles assigned to the user.
    * @param secureID a secure random number.
    */
   public SRPrincipal(IdentityID user, IdentityID[] roles, long secureID) {
      this(user, roles, new String[0], null, secureID);
   }

   /**
    * Construct a <code>SRPrincipal</code> instance
    *
    * @param user the client information of the user.
    * @param roles roles assigned to the user.
    * @param groups groups the user belongs to.
    * @param orgID the organization id assigned to the user.
    * @param secureID a secure random number.
    */
   public SRPrincipal(IdentityID user, IdentityID[] roles, String[] groups, String orgID, long secureID) {
      this(new ClientInfo(user, null), roles, groups, orgID, secureID);
   }

   /**
    * Construct a <code>SRPrincipal</code> instance
    *
    * @param client the client information of the user.
    * @param roles roles assigned to the user.
    * @param secureID a secure random number.
    */
   public SRPrincipal(ClientInfo client, IdentityID[] roles, long secureID) {
      this(client, roles, new String[0], null, secureID);
   }

   /**
    * Construct a <code>SRPrincipal</code> instance
    *
    * @param client the client information of the user.
    * @param roles roles assigned to the user.
    * @param groups groups the user belongs to.
    * @param secureID a secure random number.
    */
   public SRPrincipal(ClientInfo client, IdentityID[] roles, String[] groups, String orgID,
                      long secureID)
   {
      super(client.getUserIdentity(), roles, groups, orgID);
      this.client = client;
      this.secureID = secureID;
      age = new Date();
      accessed = System.currentTimeMillis();

      // @by larryl, optimization
      if(localHost == null) {
         // record where this ticket is created
         try {
            localHost = Tool.getIP();
         }
         catch(Exception ex) {
            LOG.error("Failed to get local IP address", ex);
         }
      }

      host = localHost;
   }

   /**
    * Construct a <code>SRPrincipal</code> instance
    *
    * @param client the client information of the user.
    * @param roles roles assigned to the user.
    * @param groups groups the user belongs to.
    * @param secureID a secure random number.
    * @param alias the alias of the user.
    */
   public SRPrincipal(ClientInfo client, IdentityID[] roles, String[] groups, String orgID,
                      long secureID, String alias)
   {
      this(client, roles, groups, orgID, secureID);
      setAlias(alias);
   }

   /**
    * Create a principal from with the same name, roles, groups, and properties.
    */
   public SRPrincipal(SRPrincipal principal) {
      this(principal.getIdentityID(), principal.getRoles(), principal.getGroups(), principal.getOrgId(),
           principal.getSecureID());

      this.host = principal.getHost();
      this.locale = principal.getLocale();
      this.accessed = principal.getLastAccess();
      this.age = new Date(principal.getAge());
      this.client = principal.getUser();

      principal.getParameterNames().forEach(
         item -> this.setParameter(item, principal.getParameter(item)));
      principal.getPropertyNames().forEach(
         item -> this.setProperty(item, principal.getProperty(item)));
   }

   public SRPrincipal(SRPrincipal principal, ClientInfo client) {
      this(principal);
      this.client = client;
   }

   /**
    * Get the principal name for the given identifier.
    */
   public static String getNameFromID(String id) {
      String[] parts = Tool.split(id, SEP);
      return parts[0];
   }

   /**
    * Create the principal for the given identifier.
    */
   public static SRPrincipal createFromID(String id) {
      String[] parts = Tool.split(id, SEP);
      String name = parts[0];
      String addr = null;
      String session = null;
      long secureId = 0L;

      if(name.indexOf(SEP2) >= 0) {
         int index1 = name.indexOf(SEP2);
         int index2 = name.indexOf(SEP2, index1 + 1);

         if(index2 > 0) {
            int index3 = name.indexOf(SEP2, index2 + 1);
            secureId = Long.parseLong(name.substring(index1 + 1, index2));

            if(index3 > 0) {
               addr = name.substring(index2 + 1, index3);
               session = name.substring(index3 + 1);
            }
            else {
               addr = name.substring(index2 + 1);
            }
         }
         else {
            secureId = Long.parseLong(name.substring(index1 + 1));
         }

         name = name.substring(0, index1);
      }

      if(parts.length == 1) {
         return SUtil.getPrincipal(new IdentityID(name, OrganizationManager.getInstance().getCurrentOrgID()), addr, true);
      }

      ClientInfo clientInfo = new ClientInfo(IdentityID.getIdentityIDFromKey(name), addr, session);
      SRPrincipal user = new SRPrincipal(clientInfo, null, null, null, secureId);

      // SSO
      user.setProperty("__internal__", null);
      String gpart = parts[1];
      String rpart = parts[2];

      if(gpart.length() > 1) {
         gpart = gpart.substring(1);
         String[] groups = Tool.split(gpart, SEP2);
         user.setGroups(groups);
      }
      else {
         user.setGroups(new String[0]);
      }

      if(rpart.length() > 1) {
         rpart = rpart.substring(1);
         IdentityID[] roles = Arrays.stream(Tool.split(rpart, SEP2)).map(IdentityID::getIdentityIDFromKey)
                                                .toArray(IdentityID[]::new);
         user.setRoles(roles);
      }
      else {
         user.setRoles(new IdentityID[0]);
      }

      for(int i = 3; i < parts.length; i++) {
         String[] ppair = Tool.split(parts[i], SEP3);
         String[] ntpair = Tool.split(ppair[0], SEP2);
         String pname = ntpair[0];
         String ptype = ntpair[1];
         String ptext = ppair[1];
         Object pval = Tool.getData(ptype, ptext);
         user.setParameter(pname, pval);
      }

      return user;
   }

   /**
    * Get the identifier.
    */
   public String toIdentifier() {
      String prop = getProperty("__internal__");

      if("true".equals(prop)) {
         return getName();
      }

      StringBuilder sb = new StringBuilder();
      sb.append(getName());

      sb.append(SEP2).append(secureID);

      if(getUser() != null && getUser().getIPAddress() != null) {
         sb.append(SEP2).append(getUser().getIPAddress());

         if(getUser().getSession() != null) {
            sb.append(SEP2).append(getUser().getSession());
         }
      }

      String[] groups = getGroups();
      sb.append(SEP);
      sb.append('g');

      for(int i = 0; groups != null && i < groups.length; i++) {
         if(i > 0) {
            sb.append(SEP2);
         }

         sb.append(groups[i]);
      }

      IdentityID[] roles = getRoles();
      sb.append(SEP);
      sb.append('r');

      for(int i = 0; roles != null && i < roles.length; i++) {
         if(i > 0) {
            sb.append(SEP2);
         }

         sb.append(roles[i]);
      }

      for(String name : getParameterNames()) {
         Object val = getParameter(name);

         if(name != null && !name.isEmpty() && val != null) {
            sb.append(SEP);
            sb.append(name);
            sb.append(SEP2);
            sb.append(Tool.getDataType(val));
            sb.append(SEP3);
            sb.append(Tool.getDataString(val));
         }
      }

      return sb.toString();
   }

   /**
    * Create user for SSO only. For non SSO case, it will always return null.
    */
   User createUser() {
      String prop = getProperty("__internal__");

      if("true".equals(prop)) {
         return null;
      }

      // @by billh, please refer to bug bug1269875133083
      String[] groups = getGroups();
      IdentityID[] roles = getRoles();
      String org = getOrgId();
      boolean existing = groups != null && groups.length > 0;

      if(!existing) {
         existing = roles != null && roles.length > 0;
      }

      if(!existing) {
         return null;
      }

      Locale locale = getLocale();
      String lstr = locale == null ? null : locale.toString();
      return new User(getIdentityID(), new String[0], groups, roles, lstr, "");
   }

   public boolean isSelfOrganization() {
      return OrganizationManager.getInstance().getCurrentOrgID(this).equals(Organization.getSelfOrganizationID());
   }

   /**
    * Compares this principal to the specified object.  Returns true
    * if the object passed in matches the principal represented by
    * the implementation of this interface.
    *
    * @param another principal to compare with.
    *
    * @return true if the principal passed in is the same as that
    * encapsulated by <code>SRPrincipal</code>, and false otherwise.
    */
   @Override
   public boolean equals(Object another) {
      if(another == null) {
         return false;
      }

      if(this == another) {
         return true;
      }

      if(!(another instanceof SRPrincipal)) {
         return false;
      }

      SRPrincipal that = (SRPrincipal) another;
      boolean this_fake = "true".equals(getProperty("__FAKE__"));
      boolean that_fake = "true".equals(that.getProperty("__FAKE__"));

      if(this_fake || that_fake) {
         return Tool.equals(getName(), that.getName());
      }

      if(super.equals(that) &&
         this.client.equals(that.client) &&
         this.getSecureID() == that.getSecureID())
      {
         return true;
      }

      return false;
   }

   /**
    * Returns a hashcode for this <code>SRPrincipal</code>.
    *
    * @return a hashcode for this <code>SRPrincipal</code>.
    */
   @Override
   public int hashCode() {
      int hash = client.hashCode();
      return super.hashCode() + hash;
   }

   /**
    * Returns the name of this principal.
    *
    * @return the name of this principal, it may be an empty string if no
    * security is provided.
    */
   @Override
   public String getName() {
      return client.getUserIdentity().convertToKey();
   }

   public IdentityID getIdentityID() {
      return IdentityID.getIdentityIDFromKey(getName());
   }

   /**
    * Return the login name of this principal.
    */
   @Override
   public IdentityID getClientUserID() {
      return client.getLoginUserID();
   }

   /**
    * Returns the client info.
    */
   public ClientInfo getUser() {
      return client;
   }

   /**
    * Sets the client info.
    */
   public void setUser(ClientInfo client) {
      this.client = client;
   }

   /**
    * Return the secureID of this <code>SRPrincipal</code>
    *
    * @return the secureID of this <code>SRPrincipal</code>
    */
   public long getSecureID() {
      return secureID;
   }

   /**
    * Return the age in milliseconds since January 1, 1970, 00:00:00 GMT
    * of the <code>SRPrincipal</code> object
    *
    * @return the age in milliseconds of the <code>SRPrincipal</code> object
    */
   public long getAge() {
      return age.getTime();
   }

   /**
    * Set last access time.
    *
    * @param accessed the specified last access time.
    */
   public void setLastAccess(long accessed) {
      this.accessed = accessed;
   }

   /**
    * Return the last access time in milliseconds since January 1, 1970,
    * 00:00:00 GMT of the <code>SRPrincipal</code> object
    *
    * @return last access time in milliseconds of the
    * <code>SRPrincipal</code> object
    */
   public long getLastAccess() {
      return accessed;
   }

   /**
    * Returns a string representation of this <code>SRPrincipal</code>.
    */
   @Override
   public String toString() {
      return toString(SUtil.isMultiTenant());
   }

   public String toString(boolean includeOrg) {
      IdentityID[] r = getRoles();
      StringBuilder rs = new StringBuilder();

      if(r != null) {
         for(IdentityID role : r) {
            rs.append(includeOrg ? role.convertToKey() : role.getName());
            rs.append(",");
         }
      }

      String[] g = getGroups();
      StringBuilder gs = new StringBuilder();

      if(g != null) {
         for(String group : g) {
            IdentityID groupID = IdentityID.getIdentityIDFromKey(group);
            gs.append(!includeOrg && groupID != null ? groupID.getName() : group);
            gs.append(",");
         }
      }

      return "Principal[" + client.toString(includeOrg) + "," + host + "]" +
         "Roles:[" + rs + "] Groups:[" + gs + "]" + (includeOrg ? " Organization:" + getOrgId() : "");
   }

   /**
    * Get the full identifier of this specified user (ip, session, etc.)
    */
   @Override
   public String getFullName() {
      return client.toView();
   }

   /**
    * Clone the object.
    */
   @Override
   public Object clone() {
      try {
         return super.clone();
      }
      catch(Exception ex) {
         LOG.error("Failed to clone object", ex);
      }

      return null;
   }

   /**
    * Get the host this principal object is created.
    */
   public String getHost() {
      return host;
   }

   /**
    * Write xml element representation to a print writer.
    *
    * @param writer the specified print writer
    */
   @Override
   public void writeXML(PrintWriter writer) {
      writer.print("<principal>");
      client.writeXML(writer);

      writer.print("<roles>");

      for(IdentityID role : roles) {
         writer.print("<role>");
         writer.print("<![CDATA[" + Tool.byteEncode(role.convertToKey()) + "]]>");
         writer.print("</role>");
      }

      writer.print("</roles>");

      writer.print("<groups>");

      for(String group : groups) {
         writer.print("<group>");
         writer.print("<![CDATA[" + Tool.byteEncode(group) + "]]>");
         writer.print("</group>");
      }

      writer.print("</groups>");

      writer.print("<secureID>");
      writer.print(secureID);
      writer.print("</secureID>");
      writer.print("<age>");
      writer.print(age.getTime());
      writer.print("</age>");

      writer.print("<accessed>");
      writer.print(accessed);
      writer.print("</accessed>");

      if(host != null) {
         writer.print("<host>");
         writer.print("<![CDATA[" + host + "]]>");
         writer.print("</host>");
      }

      writer.print("<sessionID>");
      writer.print("<![CDATA[" + sessionID + "]]>");
      writer.print("</sessionID>");

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
      writer.println("</principal>");
   }

   /**
    * Parse xml element representation.
    *
    * @param elem the specified xml element representation
    */
   @Override
   public void parseXML(Element elem) throws Exception {
      NodeList cnodes = Tool.getChildNodesByTagName(elem, "clientInfo");

      if(cnodes.getLength() == 0) {
         throw new IOException("Missing clientInfo tag");
      }

      client.parseXML((Element) cnodes.item(0));
      Element rsnode = Tool.getChildNodeByTagName(elem, "roles");
      NodeList rnodes = Tool.getChildNodesByTagName(rsnode, "role");
      ArrayList<IdentityID> roleList = new ArrayList<>();

      for(int i = 0; rnodes != null && i < rnodes.getLength(); i++) {
         roleList.add(IdentityID.getIdentityIDFromKey(Tool.byteDecode(Tool.getValue(rnodes.item(i)))));
      }

      roles = roleList.toArray(new IdentityID[0]);

      Element gsnode = Tool.getChildNodeByTagName(elem, "groups");
      NodeList gnodes = Tool.getChildNodesByTagName(gsnode, "group");
      ArrayList<String> list = new ArrayList<>();

      for(int i = 0; gnodes != null && i < gnodes.getLength(); i++) {
         list.add(Tool.byteDecode(Tool.getValue(gnodes.item(i))));
      }

      groups = list.toArray(new String[0]);

      NodeList snodes = Tool.getChildNodesByTagName(elem, "secureID");

      if(snodes.getLength() == 0) {
         throw new IOException("Missing secureID tag");
      }

      secureID = Long.parseLong(Tool.getValue(snodes.item(0)));

      NodeList anodes = Tool.getChildNodesByTagName(elem, "age");

      if(anodes.getLength() == 0) {
         throw new IOException("Missing age tag");
      }

      age.setTime(Long.parseLong(Tool.getValue(anodes.item(0))));

      NodeList mnodes = Tool.getChildNodesByTagName(elem, "accessed");

      if(mnodes.getLength() == 0) {
         throw new IOException("Missing accessed tag");
      }

      accessed = Long.parseLong(Tool.getValue(mnodes.item(0)));

      host = null;
      NodeList hnodes = Tool.getChildNodesByTagName(elem, "host");

      if(hnodes.getLength() > 0) {
         host = Tool.getValue(hnodes.item(0));
      }

      Element snode = Tool.getChildNodeByTagName(elem, "sessionID");
      sessionID = Tool.getValue(snode);

      NodeList psnodes = Tool.getChildNodesByTagName(elem, "properties");

      if(psnodes.getLength() == 0) {
         throw new IOException("Missing properties node");
      }

      prop.clear();
      NodeList pnodes =
         Tool.getChildNodesByTagName(psnodes.item(0), "property");

      for(int i = 0; i < pnodes.getLength(); i++) {
         NodeList knodes =
            Tool.getChildNodesByTagName(pnodes.item(i), "key");
         NodeList vnodes =
            Tool.getChildNodesByTagName(pnodes.item(i), "value");

         if(knodes.getLength() == 0 || vnodes.getLength() == 0) {
            throw new IOException("Missing key or value node");
         }

         setProperty(Tool.getValue(knodes.item(0)), Tool.getValue(vnodes.item(0)));
      }
   }

   /**
    * Parses the user's roles from an XML representation.
    *
    * @param elem the DOM element representing the user's roles.
    *
    * @throws Exception if the DOM element could not be parsed.
    */
   protected void parseRolesXML(Element elem) throws Exception {
      NodeList nodes = Tool.getChildNodesByTagName(elem, "role");
      ArrayList<IdentityID> list = new ArrayList<>();

      for(int i = 0; nodes != null && i < nodes.getLength(); i++) {
         list.add(IdentityID.getIdentityIDFromKey(Tool.byteDecode(Tool.getValue(nodes.item(i)))));
      }

      roles = list.toArray(new IdentityID[0]);
   }

   /**
    * Get the locale.
    */
   public Locale getLocale() {
      return locale;
   }

   /**
    * Set the locale.
    */
   public void setLocale(Locale locale) {
      this.locale = locale;

      // @by billh, fix customer bug bug1304367616048.
      // This is not required for this bug. However, for SSO, locale will
      // be passed by user, so I added logic here to avoid any possible problem
      if(locale != null && getProperty(XPrincipal.LOCALE) == null) {
         setProperty(XPrincipal.LOCALE, locale.toString());
      }
   }

   /**
    * Set the session object.
    */
   public void setSession(Object session) {
      if(session != null) {
         sref = new WeakReference<>(session);
      }
   }

   /**
    * Get the session object.
    */
   public Object getSession() {
      return sref == null ? null : sref.get();
   }

   /**
    * Check if this principal is valid.
    */
   public boolean isValid() {
      return sref == null || sref.get() != null;
   }

   @Override
   public void writeExternal(ObjectOutput out) throws IOException {
      writeStringExternal(name, out);
      out.writeInt(roles == null ? 0 : roles.length);

      if(roles != null) {
         for(IdentityID role : roles) {
            writeStringExternal(role.getName(), out);
            writeStringExternal(role.getOrgID(), out);
         }
      }

      out.writeInt(groups == null ? 0 : groups.length);

      if(groups != null) {
         for(String group : groups) {
            writeStringExternal(group, out);
         }
      }

      writeStringExternal(orgId, out);
      writeStringExternal(sessionID, out);
      out.writeInt(prop == null ? 0 : prop.size());

      if(prop != null) {
         for(Map.Entry<String, String> entry : prop.entrySet()) {
            writeStringExternal(entry.getKey(), out);
            writeStringExternal(entry.getValue(), out);
         }
      }

      Set<String> parameterNames = getParameterNames();
      out.writeInt(parameterNames.size());

      for(String name : parameterNames) {
         Object val = getParameter(name);
         long ts = getParameterTS(name);
         writeStringExternal(name, out);
         out.writeObject(val);
         out.writeLong(ts);
      }

      out.writeBoolean(isIgnoreLogin());
      out.writeBoolean(isProfiling());
      out.writeObject(client);
      out.writeLong(secureID);
      out.writeLong(age.getTime());
      out.writeLong(accessed);
      out.writeObject(host);
      out.writeObject(locale);
   }

   private void writeStringExternal(String s, ObjectOutput out) throws IOException {
      if(s == null) {
         out.writeUTF("__EXT_NULL_STR__");
      }
      else {
         out.writeUTF(s);
      }
   }

   @Override
   public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
      name = readStringExternal(in);
      int length = in.readInt();
      roles = new IdentityID[length];

      for(int i = 0; i < length; i++) {
         roles[i] = new IdentityID(readStringExternal(in), readStringExternal(in));
      }

      length = in.readInt();
      groups = new String[length];

      for(int i = 0; i < length; i++) {
         groups[i] = readStringExternal(in);
      }

      orgId = readStringExternal(in);
      sessionID = readStringExternal(in);
      length = in.readInt();

      prop = new ConcurrentHashMap<>();

      for(int i = 0; i < length; i++) {
         String key = readStringExternal(in);
         String val = readStringExternal(in);
         prop.put(key, val);
      }

      length = in.readInt();

      for(int i = 0; i < length; i++) {
         String name = readStringExternal(in);
         Object val = in.readObject();
         long ts = in.readLong();
         setParameter(name, val, ts);
      }

      setIgnoreLogin(in.readBoolean());
      setProfiling(in.readBoolean());
      client = (ClientInfo) in.readObject();
      secureID = in.readLong();
      age = new Date(in.readLong());
      accessed = in.readLong();
      host = (String) in.readObject();
      locale = (Locale) in.readObject();
   }

   private String readStringExternal(ObjectInput in) throws IOException {
      String s = in.readUTF();

      if(s.equals("__EXT_NULL_STR__")) {
         return null;
      }
      else {
         return s;
      }
   }

   // for backward compatibility
   private static final long serialVersionUID = 329619388094919499L;
   private static final char SEP = ';';
   private static final char SEP2 = '^';
   private static final char SEP3 = '`';
   private static String localHost;

   private ClientInfo client;
   private long secureID;
   private Date age;
   private long accessed; // last access timestamp
   // machine this principal object is created
   private String host = null;
   private transient Locale locale = null;
   private transient WeakReference<Object> sref = null;

   private static final Logger LOG =
      LoggerFactory.getLogger(SRPrincipal.class);
}
