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
package inetsoft.sree.security.ldap;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import inetsoft.uql.util.Identity;
import inetsoft.util.Tool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.naming.Context;
import javax.naming.NamingException;
import javax.naming.directory.*;
import javax.naming.ldap.InitialLdapContext;
import javax.naming.ldap.LdapContext;
import java.text.MessageFormat;
import java.util.*;

/**
 * Authentication module that obtains information from a Microsoft Active
 * Directory server.
 *
 * @author InetSoft Technology
 * @since  8.5
 */
public class ADAuthenticationProvider extends LdapAuthenticationProvider {
   /**
    * Creates a new instance of ADSecurityProvider.
    */
   public ADAuthenticationProvider() {
      setUserSearch(USER_SEARCH);
      setUserRolesSearch(USER_ROLES_SEARCH);
      setLdapAdministrator("cn=Administrator,cn=Users");
   }

   /**
    * Create a directory context that can be used to query the LDAP server.
    *
    * @return a directory context.
    *
    * @throws NamingException if an error occurred during the creation of the
    *                         context.
    */
   @Override
   protected LdapContext createContext() throws NamingException {
      Hashtable<Object, Object> env = new Hashtable<>();

      env.put(Context.INITIAL_CONTEXT_FACTORY,
         "com.sun.jndi.ldap.LdapCtxFactory");
      env.put("java.naming.ldap.attributes.binary", "objectSid");

      String url = getLdapProtocol() + getHost() + ':' + getPort();
      env.put(Context.PROVIDER_URL, url);

      String user = getLdapAdministrator() + ',' + getRootDn();
      String password = getPassword();

      if(password == null) {
         password = "";
      }

      env.put(Context.SECURITY_AUTHENTICATION, "simple");
      env.put(Context.SECURITY_PRINCIPAL, user);
      env.put(Context.SECURITY_CREDENTIALS, password);

      return new InitialLdapContext(env, null);
   }

   /**
    * Merge the root dn into the base.
    */
   private String mergeRootDN(String base, String rootDN) {
      if(!base.contains(";")) {
         return base + (base.contains(rootDN) ? "" : "," + rootDN);
      }

      String[] bases = parseToUnits(base);
      StringBuilder sb = new StringBuilder();

      for(String each : bases) {
         each += each.contains(rootDN) ? ";" : "," + rootDN + ";";
         sb.append(each);
      }

      return sb.deleteCharAt(sb.length() - 1).toString();
   }

   /**
    * Initialize the ADS security provider. This converts any old entries so
    * that they are compatible with changes to the current implementation.
    *
    * @param context the directory context.
    *
    */
   @Override
   protected void initInstance(LdapContext context) {
   }

   /**
    * Get the name of the attribute that contains the user ID.
    *
    * @return an attribute name.
    */
   @Override
   public String getUserAttribute() {
      return USER_ATTRIBUTE;
   }

   /**
    * Get the search filter to use when searching for roles.
    *
    * @return a search filter.
    */
   @Override
   public String getRoleSearch() {
      return ROLE_SEARCH;
   }

   /**
    * Get the name of the attribute that contains the role name.
    *
    * @return an attribute name.
    */
   @Override
   public String getRoleAttribute() {
      return ROLE_ATTRIBUTE;
   }

   @Override
   protected String[] doSearchRoles(String name, String dn, int type) {
      String[] roles;
      String filter = MessageFormat.format(getRolesSearch(type), name, dn);

      ArrayList<String> rolesList = new ArrayList<>();
      String[] users = getUserBases(getUserBase());
      String[] roleBases = getRoleBases(getRoleBase());

      for(String user : users) {
         List<String> dirs =
            searchDirectory(user, filter, SearchControls.SUBTREE_SCOPE, "memberOf");

         for(String dir : dirs) {
            boolean matched = false;

            for(String roleBase : roleBases) {
               if(dir.toLowerCase().endsWith("," + roleBase.toLowerCase()) ||
                  dir.toLowerCase().endsWith(roleBase.toLowerCase()))
               {
                  matched = true;
                  break;
               }
            }

            if(matched) {
               rolesList.add(dir);
            }
         }
      }

      roles = rolesList.toArray(new String[0]);

      for(int i = 0; i < roles.length; i++) {
         roles[i] = roles[i].substring(3, roles[i].indexOf(',')).trim();
      }

      if(filter.contains("objectclass=user")) {
         String primary = searchPrimaryRole(name);

         if(primary != null) {
            String[] result = new String[roles.length + 1];
            System.arraycopy(roles, 0, result, 0, roles.length);
            result[result.length - 1] = primary;
            return result;
         }
      }

      return roles;
   }

   /**
    * The binary data is in the form:
    * byte[0] - revision level
    * byte[1] - count of sub-authorities
    * byte[2-7] - 48 bit authority (big-endian)
    * and then count x 32 bit sub authorities (little-endian)
    *
    * The String value is: S-Revision-Authority-SubAuthority[n]...
    *
    * Based on code from here -
    * http://forums.oracle.com/forums/thread.jspa?threadID=1155740&tstart=0
    */
   private String decodeSID(byte[] sid) {
      StringBuilder strSID = new StringBuilder("S");
      int version;
      long authority;
      int count;
      StringBuilder rid = new StringBuilder();

      // get version
      version = sid[0];
      strSID.append("-").append(Integer.toString(version));

      for(int i = 2; i <= 7; i++) {
         rid.append(byte2hex(sid[i]));
      }

      // get authority
      authority = Long.parseLong(rid.toString());
      strSID.append("-").append(Long.toString(authority));

      //next byte is the count of sub-authorities
      count = sid[1]&0xFF;

      //iterate all the sub-auths
      for(int i = 0; i < count; i++) {
         rid = new StringBuilder();

         for(int j = 11; j > 7; j--) {
            rid.append(byte2hex(sid[j + (i * 4)]));
         }

         strSID.append("-").append(Long.parseLong(rid.toString(), 16));
      }

      return strSID.toString();

   }

   private String byte2hex(byte b) {
      String ret = Integer.toHexString((int) b&0xFF);

      if (ret.length() < 2) {
         ret = "0" + ret;
      }
      return ret;
   }

   /**
    * Get user primary role.
    */
   protected String searchPrimaryRole(String userName) {
      try {
         String filter = new StringBuilder().append("(&(").
            append(getUserSearch()).append(")(").append(getUserAttribute()).
            append('=').append(Tool.encodeForLDAP(userName)).append("))").toString();

         String[] users = getUserBases(getUserBase());
         String[] primary = null;

         // @by stephenwebster, For bug1415832285046
         // Instead of getting the User's primary group id, and then
         // getting all groups and comparing each one, change it to get
         // the primary group id, and generate the exact objectSID for that
         // group so it can be directly queried from the LDAP server in one
         // query.  This significantly reduces the time required to lookup the
         // primary role.  In my scenario from around 650ms to 8ms on average.
         for(String user : users) {
            List<SearchResult> results = searchDirectory(user, filter,
               SearchControls.SUBTREE_SCOPE,
               new String[] {"objectSid","PrimaryGroupID"});

            for(SearchResult result : results) {
               byte[] objectSID = (byte[]) result.getAttributes().get("objectSid").get();

               Attribute primaryGroupIdAttr = result.getAttributes().get("primaryGroupID");

               // @by stephenwebster, For Bug #661
               // It is possible that some users may not have a primary group
               if(primaryGroupIdAttr == null) {
                  continue;
               }

               String strPrimaryGroupID = (String) primaryGroupIdAttr.get();
               String strObjectSid = decodeSID(objectSID);
               primary = new String[1];
               primary[0] =
                  strObjectSid.substring(0, strObjectSid.lastIndexOf('-') + 1) +
                     strPrimaryGroupID;
            }
         }

         if(primary != null && primary.length == 1) {
            String[] attrs = new String[] {getRoleAttribute()};
            String[] roleBases = getRoleBases(getRoleBase());

            for(String roleBase : roleBases) {
               String search =
                  "(&"+getRoleSearch()+"(objectSid=" + primary[0] + "))";
               List<SearchResult> results =
                  searchDirectory(roleBase, search, SearchControls.SUBTREE_SCOPE, attrs);

               for(SearchResult result : results) {
                  Attribute attribute = result.getAttributes().get(getRoleAttribute());

                  if(attribute != null) {
                     return attribute.get().toString();
                  }
               }
            }
         }
      }
      catch (Exception ex) {
         LOG.warn("Failed to find primary role for user " + userName, ex);
      }

      return null;
   }

   /**
    * Get the search filter to use when searching for the roles of a specific
    * identity.
    *
    * @return a search filter.
    */
   @Override
   protected String getRolesSearch(int type) {
      switch (type) {
      case Identity.USER:
         return getUserRolesSearch();
      case Identity.ROLE:
         return ROLE_ROLES_SEARCH;
      case Identity.GROUP:
         return GROUP_ROLES_SEARCH;
      default:
         return null;
      }
   }

   /**
    * Get the search filter to use when searching for groups.
    *
    * @return a search filter.
    */
   @Override
   public String getGroupSearch() {
      return GROUP_SEARCH;
   }

   /**
    * Get the name of the attribute that contains the group name.
    *
    * @return an attribute name.
    */
   @Override
   public String getGroupAttribute() {
      return GROUP_ATTRIBUTE;
   }

   /**
    * Get the name of the attribute that contains the user's email address.
    *
    * @return an attribute name.
    */
   @Override
   public String getMailAttribute() {
      return MAIL_ATTRIBUTE;
   }

   /**
    * Get the entrydn name.
    */
   @Override
   protected String getEntryDNAttribute() {
      return ENTRYDN_ATTRIBUTE;
   }

   /**
    * Get the dn substring of a group.
    */
   @Override
   protected String getDNString(String dn) {
      if(dn != null) {
         int idx = dn.toLowerCase().indexOf(",dc=");

         if(idx != -1) {
            return dn;
         }
      }

      return null;
   }

   @Override
   public void readConfiguration(JsonNode configuration) {
      super.readConfiguration(configuration);
      ObjectNode config = (ObjectNode) configuration;
      setUserSearch(config.get("userSearch").asText(USER_SEARCH));
      setUserRolesSearch(config.get("userRolesSearch").asText(USER_ROLES_SEARCH));
      setUserBase(config.get("userBase").asText());
      setGroupBase(config.get("groupBase").asText());
      setRoleBase(config.get("roleBase").asText());
   }

   @Override
   public JsonNode writeConfiguration(ObjectMapper mapper) {
      return super.writeConfiguration(mapper);
   }

   @Override
   public void setUserBase(String userBase) {
      if(userBase == null || "".equals(userBase)) {
         userBase = "cn=Users," + getRootDn();
      }
      // only ADSecurityProvider merges rootDn into userBase instead of url
      else {
         userBase = mergeRootDN(userBase, getRootDn());
      }

      super.setUserBase(userBase);
   }

   @Override
   public void setGroupBase(String groupBase) {
      if(groupBase == null || "".equals(groupBase)) {
         groupBase = "cn=Users," + getRootDn();
      }
      // only ADSecurityProvider merges rootDn into groupBase instead of url
      else {
         groupBase = mergeRootDN(groupBase, getRootDn());
      }

      super.setGroupBase(groupBase);
   }

   @Override
   public void setRoleBase(String roleBase) {
      if(roleBase == null || "".equals(roleBase)) {
         roleBase = "cn=Users," + getRootDn();
      }
      // only ADSecurityProvider merges rootDn into roleBase instead of url
      else {
         roleBase = mergeRootDN(roleBase, getRootDn());
      }

      super.setRoleBase(roleBase);
   }

   @Override
   public void setUserSearch(String userSearch) {
      if(userSearch == null || "".equals(userSearch)) {
         userSearch = USER_SEARCH;
      }

      super.setUserSearch(userSearch);
   }

   @Override
   public void setUserRolesSearch(String userRolesSearch) {
      if(userRolesSearch == null || "".equals(userRolesSearch)) {
         userRolesSearch = USER_ROLES_SEARCH;
      }

      super.setUserRolesSearch(userRolesSearch);
   }

   @Override
   protected boolean supportsNamingListener() {
      return false;
   }

   private static final String USER_SEARCH = "(objectclass=user)";
   private static final String USER_ATTRIBUTE = "sAMAccountName";
   private static final String ENTRYDN_ATTRIBUTE = "distinguishedName";
   private static final String MAIL_ATTRIBUTE = "mail";
   private static final String ROLE_SEARCH = "(objectclass=group)";
   private static final String ROLE_ATTRIBUTE = "cn";
   private static final String USER_ROLES_SEARCH = "(&(objectclass=user)(sAMAccountName={0}))";
   private static final String ROLE_ROLES_SEARCH = "(&(objectclass=group)(sAMAccountName={0}))";
   private static final String GROUP_ROLES_SEARCH =
      "(&(objectclass=organizationalunit)(sAMAccountName={0}))";
   private static final String GROUP_SEARCH = "(objectclass=organizationalunit)";
   private static final String GROUP_ATTRIBUTE = "ou";

   private static final Logger LOG = LoggerFactory.getLogger(ADAuthenticationProvider.class);
}
