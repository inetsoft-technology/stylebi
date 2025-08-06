/*
 * This file is part of StyleBI.
 * Copyright (C) 2025  InetSoft Technology
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

import inetsoft.sree.security.*;
import inetsoft.uql.util.Identity;
import inetsoft.util.Tool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.naming.*;
import javax.naming.directory.*;
import javax.naming.ldap.*;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

class LdapAuthenticationClient {
   public LdapAuthenticationClient(LdapAuthenticationProvider provider) {
      this.provider = provider;
   }

   public Map<String, String> getUsers() {
      Map<String, String> users = new HashMap<>();
      String filter = provider.getUserSearch();
      String attr = provider.getUserAttribute();

      for(String base: provider.getUserBases(provider.getUserBase())) {
         users.putAll(searchDirectoryDns(base, filter, provider.getUserScope(), attr));
      }

      if(users.isEmpty()) {
         LOG.warn(
            "Zero users found. This may indicate a problem with your configuration.");
      }

      return users;
   }

   public Map<String, String> getRoles() {
      Map<String, String> roles = new HashMap<>();
      String filter = provider.getRoleSearch();
      String attr = provider.getRoleAttribute();

      for(String base : provider.getRoleBases(provider.getRoleBase())) {
         roles.putAll(searchDirectoryDns(base, filter, SearchControls.SUBTREE_SCOPE, attr));
      }

      return roles;
   }

   public String[] getEmails(String user) {
      String filter = "(&(" +
         provider.getUserSearch() + ")(" + provider.getUserAttribute() +
         '=' + Tool.encodeForLDAP(user) + "))";
      String attr = provider.getMailAttribute();
      return Arrays.stream(provider.getUserBases(provider.getUserBase()))
         .flatMap(u -> searchDirectory(u, filter, SearchControls.SUBTREE_SCOPE, attr).stream())
         .toArray(String[]::new);
   }

   public String[] getIndividualUsers() {
      String filter = provider.getUserSearch();
      String attr = provider.getUserAttribute();
      return Arrays.stream(provider.getUserBases(provider.getUserBase()))
         .flatMap(u -> searchDirectory(u, filter, SearchControls.ONELEVEL_SCOPE, attr).stream())
         .toArray(String[]::new);
   }

   public String[] getIndividualEmailAddresses() {
      String filter = provider.getUserSearch();
      String attr = provider.getMailAttribute();
      return Arrays.stream(provider.getUserBases(provider.getUserBase()))
         .flatMap(u -> searchDirectory(u, filter, SearchControls.ONELEVEL_SCOPE, attr).stream())
         .toArray(String[]::new);
   }

   public Role getRole(String name) {
      IdentityID id = new IdentityID(name, Organization.getDefaultOrganizationID());
      IdentityID[] roles = Arrays.stream(provider.searchRoles(name, Identity.ROLE))
         .map(r -> new IdentityID(r, Organization.getDefaultOrganizationID()))
         .toArray(IdentityID[]::new);
      return new Role(id, roles);
   }

   /**
    * Get a list of all groups defined in the system.
    */
   public Map<String, String> getGroups() {
      String entryDn = provider.getEntryDNAttribute();
      Map<String, String> values = new HashMap<>();
      String[] bases = provider.getGroupBases(provider.getGroupBase());
      String groupAttr = provider.getGroupAttribute();

      try {
         for(String base : bases) {
            LdapName baseDn = new LdapName(base);
            Set<Rdn> rdns = new HashSet<>(baseDn.getRdns());
            List<SearchResult> results = searchDirectory(
               base, provider.getGroupSearch(), SearchControls.SUBTREE_SCOPE,
               new String[]{ provider.getGroupAttribute(), entryDn });

            for(SearchResult result : results) {
               Attribute name = result.getAttributes().get(groupAttr);
               Attribute id = result.getAttributes().get(entryDn);

               if(name != null && name.size() > 0) {
                  String value = name.get(0).toString();

                  if(!rdns.contains(new Rdn(groupAttr + "=" + value))) {
                     String dn = null;

                     if(id != null && id.size() > 0) {
                        dn = id.get(0).toString();
                     }

                     if(dn == null) {
                        dn = getDNFromSearchResult(result, base);
                     }

                     values.put(value, dn);
                  }
               }
            }
         }
      }
      catch(NamingException e) {
         throw new RuntimeException("Failed to list groups", e);
      }

      return values;
   }

   public Group getGroup(String name) {
      IdentityID[] roles = Arrays.stream(provider.searchRoles(name, Identity.GROUP))
         .map(r -> new IdentityID(r, Organization.getDefaultOrganizationID()))
         .toArray(IdentityID[]::new);
      ArrayList<String> list = new ArrayList<>();
      IdentityID[] groups = provider.getGroups();

      for(IdentityID group : groups) {
         List<String> sgroups = provider.searchSubIdentities(group.getName(), Identity.GROUP);

         for(String sgroup : sgroups) {
            if(name.equals(sgroup) && !group.getName().equals(name)) {
               list.add(group.getName());
               break;
            }
         }
      }

      String[] pgroups = list.toArray(new String[0]);
      return new Group(new IdentityID(
         name, Organization.getDefaultOrganizationID()), null, pgroups, roles);
   }

   @SuppressWarnings("deprecation")
   public User getUser(IdentityID name) {
      final String realName = getRealUserName(name.getName());
      IdentityID realId = new IdentityID(realName, name.getOrgID());
      IdentityID[] roles = Arrays.stream(provider.searchRoles(realName, Identity.USER))
         .map(r -> new IdentityID(r, name.getOrgID()))
         .toArray(IdentityID[]::new);
      String[] pgroups = provider.getUserGroups(realId);
      return new User(realId, provider.getEmails(name), pgroups, roles, "", "");
   }

   /**
    * Do the detail work of authenticate.
    */
   public boolean authenticate(String userBase, String filter, String passwd,
                                  SearchControls controls, LdapContext context)
      throws NamingException
   {
      // @by stephenwebster, fix bug1393861418055
      // while doing a search, must escape forward slash
      NamingEnumeration<?> e =
         context.search(Tool.replaceAll(userBase, "/", "\\/"), filter, controls);

      if(!e.hasMore()) {
         return false;
      }

      SearchResult result = (SearchResult) e.next();
      String root = provider.getRootDn();
      String dn;

      // @by stephenwebster, fix bug139386141805
      // The result name could be a composite name (in which case it is quoted).
      // This throws off the creation of the dn string.  Only expecting 1.
      String cdn = new CompositeName(result.getName()).get(0);

      if(!root.isEmpty() && userBase.endsWith(root)) {
         dn = cdn + ',' + userBase;
      }
      else {
         StringBuilder buf = new StringBuilder();
         buf.append(cdn);

         if(!userBase.isEmpty()) {
            buf.append(',');
            buf.append(userBase);
         }

         if(!root.isEmpty()) {
            buf.append(',');
            buf.append(root);
         }

         dn = buf.toString();
      }

      context.addToEnvironment(Context.SECURITY_PRINCIPAL, dn);
      context.addToEnvironment(Context.SECURITY_CREDENTIALS, passwd);

      try {
         context.getAttributes("");
      }
      catch(AuthenticationException ae) {
         LOG.debug("Unable to authenticate user due to an authentication error ", ae);
         return false;
      }

      return true;
   }

   /**
    * Determines if the connection to a directory context is valid.
    *
    * @param context the context to test.
    *
    * @return <tt>true</tt> if valid; <tt>false</tt> otherwise.
    */
   protected boolean testContext(LdapContext context) {
      long start = System.currentTimeMillis();
      LOG.debug("Validating directory context");
      boolean valid = false;

      SearchControls controls = new SearchControls();
      controls.setSearchScope(SearchControls.OBJECT_SCOPE);
      // Set a time limit to ensure fast failure with network issues. 2 seconds appears to
      // be plenty of time, since testing over a WAN from the U.S. to Europe with StartTLS
      // enabled shows an average time for this search to complete is 300ms.
      controls.setTimeLimit(2000);
      controls.setCountLimit(1);
      controls.setReturningAttributes(new String[] { provider.getValidateContextAttr() });

      try {
         NamingEnumeration<SearchResult> results =
            context.search(provider.getValidateContextBaseDn(), provider.getValidateContextSearch(), controls);

         if(results.hasMore()) {
            valid = true;
         }
         else {
            LOG.debug("Directory context invalid due to no results");
         }
      }
      catch(Throwable exc) {
         LOG.debug("Directory context invalid", exc);
      }
      finally {
         LOG.debug(
            "Directory context validated in {}ms", System.currentTimeMillis() - start);
      }

      return valid;
   }

   Map<String, String> searchDirectoryDns(String name, String filter, int scope,
                                                    String attr)
   {
      try {
         String entryDn = provider.getEntryDNAttribute();
         String[] attrNames = { entryDn, attr };
         Map<String, String> map = new HashMap<>();

         for(SearchResult result : searchDirectory(name, filter, scope, attrNames)) {
            Attributes attrs = result.getAttributes();
            Attribute values = attrs.get(attr);

            if(values != null) {
               Attribute dnAttr = attrs.get(entryDn);
               String dn = null;

               if(dnAttr != null && dnAttr.size() > 0) {
                  dn = String.valueOf(dnAttr.get(0));
               }

               if(dn == null) {
                  dn = getDNFromSearchResult(result, name);
               }

               for(NamingEnumeration<?> e = values.getAll(); e.hasMore();) {
                  Object value = e.next();

                  if(value != null) {
                     map.put(String.valueOf(value), dn);
                  }
               }
            }
         }

         return map;
      }
      catch(NamingException e) {
         throw new RuntimeException("Failed to search directory", e);
      }
   }

   /**
    * Search the LDAP directory.
    *
    * @param name the name of the subcontext to search.
    * @param filter the search filter.
    * @param scope the scope of the search. This parameter must be one of the
    *              scope constants define in
    *              <code>javax.naming.directory.SearchControls</code>.
    * @param attr the name of the attribute containing the value to be
    *             retrieved.
    *
    * @return the values of the specified attribute.
    */
   List<String> searchDirectory(String name, String filter, int scope, String attr) {
      try {
         return searchDirectory(name, filter, scope, new String[] { attr }).stream()
            .map(r -> r.getAttributes().get(attr))
            .filter(Objects::nonNull)
            .flatMap(this::getAttributeStream)
            .map(String::valueOf)
            .collect(Collectors.toList());
      }
      catch(NamingException e) {
         throw new RuntimeException("Failed to search directory", e);
      }
   }

   private Stream<Object> getAttributeStream(Attribute attribute) {
      try {
         return Tool.toStream(attribute.getAll()).map(v -> (Object) v);
      }
      catch(NamingException e) {
         throw new RuntimeException("Failed to list attribute values", e);
      }
   }

   /**
    * Search the LDAP directory.
    *
    * @param name the name of the subcontext to search.
    * @param filter the search filter.
    * @param scope the scope of the search. This parameter must be one of the
    *              scope constants define in
    *              <code>javax.naming.directory.SearchControls</code>.
    * @param attrs the names of the attributes containing the value to be
    *             retrieved.
    *
    * @return a list of Strings containing the value of the specified
    *         attribute.
    *
    * @throws NamingException if an error occurs while querying the LDAP
    *                         server.
    */
   List<SearchResult> searchDirectory(String name, String filter, int scope,
                                      String[] attrs) throws NamingException
   {
      SearchControls scontrols = new SearchControls();
      scontrols.setSearchScope(scope);
      scontrols.setReturningAttributes(attrs);
      long totalStart = System.currentTimeMillis();
      LOG.debug("LDAP searching in {} for {}", name, filter);
      List<SearchResult> results = new ArrayList<>();

      try {
         LdapContext context = provider.borrowContext();

         try {
            // @see customer bug: bug1283178929388
            setPagedControl(context, true, null);
            byte[] cookie = null;

            do {
               long pageStart = System.currentTimeMillis();
               LOG.debug("LDAP search result page requested in {} for {}", name, filter);
               // @by stephenwebster, fix bug1393861418055
               // while doing a search, must escape forward slash in name
               NamingEnumeration<SearchResult> enumeration =
                  context.search(Tool.replaceAll(name, "/", "\\/"), filter, scontrols);

               while(enumeration != null && enumeration.hasMoreElements()) {
                  SearchResult entry = enumeration.nextElement();
                  results.add(entry);
               }

               Control[] arr = context.getResponseControls();

               for(int i = 0; arr != null && i < arr.length; i++) {
                  if(arr[i] instanceof PagedResultsResponseControl paged) {
                     cookie = paged.getCookie();
                  }
               }

               setPagedControl(context, true, cookie);
               LOG.debug(
                  "LDAP search result page returned in {} for {}, {} total results, " +
                     "took {}ms", name, filter, results.size(),
                  System.currentTimeMillis() - pageStart);
            }
            while (cookie != null);

            return results;
         }
         finally {
            LOG.debug(
               "LDAP search complete in {} for {}, took {}ms",
               name, filter, System.currentTimeMillis() - totalStart);
            setPagedControl(context, false, null);
            provider.returnContext(context);
         }
      }
      catch(NamingException exc) {
         // Redundant logging for IGC customer issue.
         LOG.debug("Redundant NamingException log", exc);

         // should be all cases
         throw exc;
      }
      catch(Exception exc) {
         // Redundant logging for IGC customer issue.
         LOG.debug("Redundant Exception log", exc);

         throw new RuntimeException("Unexpected error", exc);
      }
   }

   /**
    * Set the paged control.
    */
   private void setPagedControl(LdapContext context, boolean paged, byte[] cookie) {
      try {
         if(paged) {
            context.setRequestControls(new Control[] {
               new PagedResultsControl(50, cookie, Control.CRITICAL)});
         }
         else {
            context.setRequestControls(null);
         }
      }
      catch(Exception ex) {
         LOG.error("Failed to set paged results option for LDAP queries", ex);
      }
   }

   protected final List<String> searchSubIdentities(String group, String groupDn, int type) {
      String entrydn = provider.getDNString(groupDn);
      StringBuilder name = new StringBuilder();
      String filter = type == Identity.USER ? provider.getUserSearch() : provider.getGroupSearch();
      String attr = type == Identity.USER ? provider.getUserAttribute() : provider.getGroupAttribute();
      int scope = type == Identity.USER ? provider.getUserScope() : SearchControls.SUBTREE_SCOPE;

      if(entrydn != null) {
         name.append(entrydn);
      }
      else {
         name.append(provider.getGroupAttribute()).append('=').append(group);

         if(provider.getGroupBase() != null && !provider.getGroupBase().isEmpty()) {
            String middleGroup = getGroups().get(group);

            if(middleGroup != null) {
               int baseGroupIndex = Math.max(0, middleGroup.indexOf(provider.getGroupBase()));

               if(middleGroup.startsWith(name.toString()) && baseGroupIndex > name.length() + 1) {
                  middleGroup = middleGroup.substring(name.length() + 1, baseGroupIndex - 1);
               }
               else {
                  middleGroup = null;
               }

               if(!Tool.isEmptyString(middleGroup)) {
                  name.append(",").append(middleGroup);
               }
            }

            return Arrays.stream(provider.getGroupBases(provider.getGroupBase()))
               .map(g -> name.append(",").append(g).toString())
               .flatMap(g -> searchDirectory(g, filter, scope, attr).stream())
               .collect(Collectors.toList());
         }
      }

      return searchDirectory(name.toString(), filter, provider.getUserScope(), attr);
   }

   String getDNFromSearchResult(SearchResult result, String searchBase) {
      String dn;

      if(result.isRelative()) {
         dn = result.getName() + "," + searchBase;

         if(!dn.endsWith("," + provider.getRootDn())) {
            dn = dn + "," + provider.getRootDn();
         }
      }
      else {
         dn = result.getName();
      }

      return dn;
   }

   /**
    * Returns the real user account name for this user.
    * @param userid The userid to search for in the directory
    */
   private String getRealUserName(String userid) {
      // @by stephenwebster, this is the same logic used to authenticate a user.
      // For now, this will be a stop-gap solution to get the exact user id from
      // LDAP that the userid represents.  For Bug #6240
      String filter = "(&(" + provider.getUserSearch() +
         ")(" + provider.getUserAttribute() + '=' + Tool.encodeForLDAP(userid) +
         "))";
      SearchControls controls = new SearchControls();
      controls.setSearchScope(SearchControls.SUBTREE_SCOPE);
      controls.setReturningAttributes(new String[] {provider.getUserAttribute()});
      String[] userBases = provider.getUserBases(provider.getUserBase());
      String userName = userid;

      try {
         LdapContext context = provider.borrowContext();

         try {
            for(String userBase : userBases) {
               long start = System.currentTimeMillis();
               LOG.debug("LDAP lookup user name in {} for {}", userBase, filter);
               NamingEnumeration<?> e =
                  context.search(
                     Tool.replaceAll(userBase, "/", "\\/"), filter, controls);

               if(!e.hasMore()) {
                  continue;
               }

               SearchResult result = (SearchResult) e.next();
               userName =
                  (String) result.getAttributes().get(provider.getUserAttribute()).get(0);
               LOG.debug(
                  "LDAP lookup user name complete in {} for {}, took {}ms",
                  userBase, filter, System.currentTimeMillis() - start);
               break;
            }
         }
         catch(Exception e) {
            LOG.warn("Problem finding account name for user", e);
         }
         finally {
            provider.returnContext(context);
         }
      }
      catch(Exception e) {
         LOG.warn("Problem finding account name for user, " +
                     "context is not available", e);
      }

      return userName;
   }

   private final LdapAuthenticationProvider provider;
   private static final Logger LOG = LoggerFactory.getLogger(LdapAuthenticationClient.class);
}
