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
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import inetsoft.sree.internal.cluster.*;
import inetsoft.sree.security.*;
import inetsoft.uql.util.Identity;
import inetsoft.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.naming.*;
import javax.naming.directory.*;
import javax.naming.event.*;
import javax.naming.ldap.*;
import java.io.Serializable;
import java.text.MessageFormat;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Base class for authentication modules that retrieve information from an LDAP
 * directory server.
 *
 * @author InetSoft Technology
 * @since  8.5
 */
@SuppressWarnings("WeakerAccess")
public abstract class LdapAuthenticationProvider
   extends AbstractAuthenticationProvider
{
   /**
    * Creates a new instance of <tt>LdapAuthenticationProvider</tt>.
    */
   protected LdapAuthenticationProvider() {
      try {
         this.contextPool = new ContextPool();
      }
      catch(Exception exc) {
         throw new RuntimeException("Failed to create context pool", exc);
      }
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public void tearDown() {
      cacheLock.lock();

      try {
         if(cache != null) {
            cache.close();
         }
      }
      catch(Exception e) {
         LOG.warn("Failed to close LDAP security cache", e);
      }
      finally {
         cacheLock.unlock();
      }
   }

   /**
    * Create a directory context that can be used to query the LDAP server.
    *
    * @return a directory context.
    *
    * @throws NamingException if an error occured during the creation of the
    *                         context.
    */
   protected abstract LdapContext createContext() throws NamingException;

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
      controls.setReturningAttributes(new String[] { validateContextAttr });

      try {
         NamingEnumeration<SearchResult> results =
            context.search(validateContextBaseDn, validateContextSearch, controls);

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

   /**
    * Initialize this authentication provider instance.
    *
    * @param context the directory context.
    *
    * @throws NamingException if an error occured while communicating with the
    *                         LDAP server.
    */
   protected abstract void initInstance(LdapContext context) throws NamingException;

   /**
    * @return the protocol used to communicate with LDAP server.
    */
   protected String getLdapProtocol() {
      return protocol + "://";
   }

   @Override
   public Organization getOrganization(String id) {
      if(Organization.getDefaultOrganizationID().equals(id)) {
         return Organization.getDefaultOrganization();
      }

      return null;
   }

   @Override
   public String getOrgIdFromName(String name) {
      return Organization.getDefaultOrganizationName().equals(name) ?
         Organization.getDefaultOrganizationID() : null;
   }

   @Override
   public String getOrgNameFromID(String id) {
      return Organization.getDefaultOrganizationID().equals(id) ?
         Organization.getDefaultOrganizationName() : null;
   }

   @Override
   public String[] getOrganizationIDs() {
      return new String[] { Organization.getDefaultOrganizationID() };
   }

   @Override
   public String[] getOrganizationNames() {
      return new String[] { Organization.getDefaultOrganizationName() };
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public final IdentityID[] getUsers() {
      try {
         if((ignoreCache.get() == null || !ignoreCache.get()) && isCacheInitialized()) {
            return Arrays.stream(getCache().getUsers())
               .map(u -> new IdentityID(u, Organization.getDefaultOrganizationID()))
               .toArray(IdentityID[]::new);
         }

         return doGetUsers().keySet().stream()
            .map(u -> new IdentityID(u, Organization.getDefaultOrganizationID()))
            .toArray(IdentityID[]::new);
      }
      catch(Exception e) {
         LOG.error("Failed to list users", e);
         return new IdentityID[0];
      }
   }

   protected Map<String, String> doGetUsers() {
      Map<String, String> users = new HashMap<>();
      String filter = getUserSearch();
      String attr = getUserAttribute();

      for(String base: getUserBases(getUserBase())) {
         users.putAll(searchDirectoryDns(base, filter, userScope, attr));
      }

      if(users.isEmpty()) {
         LOG.warn(
            "Zero users found. This may indicate a problem with your configuration.");
      }

      return users;
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public final IdentityID[] getUsers(IdentityID group) {
      if(group == null) {
         return getIndividualUsers();
      }

      try {
         return searchSubIdentities(group.getName(), Identity.USER).stream()
            .map(u -> new IdentityID(u, Organization.getDefaultOrganizationID()))
            .toArray(IdentityID[]::new);
      }
      catch(Exception e) {
         LOG.error("Failed to get users in group \"{}\"", group, e);
         return new IdentityID[0];
      }
   }

   /**
    * Search sub identities.
    * @param group group name
    * @param type user or group
    */
   protected final List<String> searchSubIdentities(String group, int type) {
      return Arrays.asList(getCache().getSubIdentities(group, type));
   }

   protected final List<String> doSearchSubIdentities(String group, int type) {
      String entrydn = getDNString(cache.getGroupDn(group));
      StringBuilder name = new StringBuilder();
      String filter = type == Identity.USER ? getUserSearch() : getGroupSearch();
      String attr = type == Identity.USER ? getUserAttribute() : getGroupAttribute();
      int scope = type == Identity.USER ? userScope : SearchControls.SUBTREE_SCOPE;

      if(entrydn != null) {
         name.append(entrydn);
      }
      else {
         name.append(getGroupAttribute()).append('=').append(group);

         if(getGroupBase() != null && !getGroupBase().isEmpty()) {
            String middleGroup = doGetGroups().get(group);

            if(middleGroup != null) {
               int baseGroupIndex = Math.max(0, middleGroup.indexOf(getGroupBase()));

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

            return Arrays.stream(getGroupBases(getGroupBase()))
               .map(g -> name.append(",").append(g).toString())
               .flatMap(g -> searchDirectory(g, filter, scope, attr).stream())
               .collect(Collectors.toList());
         }
      }

      return searchDirectory(name.toString(), filter, userScope, attr);
   }

   /**
    * {@inheritDoc}
    */
   @Override
   @Deprecated
   @SuppressWarnings("deprecation")
   public final String[] getEmails(IdentityID user) {
      try {
         return getCache().getEmails(user.getName());
      }
      catch(Exception e) {
         LOG.error("Failed to get email addresses for user: {}", user, e);
         return new String[0];
      }
   }

   protected String[] doGetEmails(String user) {
      String filter = "(&(" +
         getUserSearch() + ")(" + getUserAttribute() +
         '=' + Tool.encodeForLDAP(user) + "))";
      String attr = getMailAttribute();
      return Arrays.stream(getUserBases(getUserBase()))
         .flatMap(u -> searchDirectory(u, filter, SearchControls.SUBTREE_SCOPE, attr).stream())
         .toArray(String[]::new);
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public final IdentityID[] getIndividualUsers() {
      try {
         if(isCacheInitialized()) {
            return Arrays.stream(getCache().getIndividualUsers())
               .map(u -> new IdentityID(u, Organization.getDefaultOrganizationID()))
               .toArray(IdentityID[]::new);
         }

         return Arrays.stream(doGetIndividualUsers())
            .map(u -> new IdentityID(u, Organization.getDefaultOrganizationID()))
            .toArray(IdentityID[]::new);
      }
      catch(Exception e) {
         LOG.error("Failed to get individual uses", e);
         return new IdentityID[0];
      }
   }

   protected String[] doGetIndividualUsers() {
      String filter = getUserSearch();
      String attr = getUserAttribute();
      return Arrays.stream(getUserBases(getUserBase()))
         .flatMap(u -> searchDirectory(u, filter, SearchControls.ONELEVEL_SCOPE, attr).stream())
         .toArray(String[]::new);
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public final String[] getIndividualEmailAddresses() {
      try {
         if(isCacheInitialized()) {
            return getCache().getIndividualEmails();
         }

         return doGetIndividualEmailAddresses();
      }
      catch(Exception e) {
         LOG.error("Failed to get individual email addresses", e);
         return new String[0];
      }
   }

   protected String[] doGetIndividualEmailAddresses() {
      String filter = getUserSearch();
      String attr = getMailAttribute();
      return Arrays.stream(getUserBases(getUserBase()))
         .flatMap(u -> searchDirectory(u, filter, SearchControls.ONELEVEL_SCOPE, attr).stream())
         .toArray(String[]::new);
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public final IdentityID[] getRoles() {
      try {
         if((ignoreCache.get() == null || !ignoreCache.get()) && isCacheInitialized()) {
            return Arrays.stream(getCache().getRoles())
               .map(r -> new IdentityID(r, Organization.getDefaultOrganizationID()))
               .toArray(IdentityID[]::new);
         }

         return doGetRoles().keySet().stream()
            .map(r -> new IdentityID(r, Organization.getDefaultOrganizationID()))
            .toArray(IdentityID[]::new);
      }
      catch(Exception e) {
         LOG.error("Failed to list the roles", e);
         return new IdentityID[0];
      }
   }

   protected Map<String, String> doGetRoles() {
      Map<String, String> roles = new HashMap<>();
      String filter = getRoleSearch();
      String attr = getRoleAttribute();

      for(String base : getRoleBases(getRoleBase())) {
         roles.putAll(searchDirectoryDns(base, filter, SearchControls.SUBTREE_SCOPE, attr));
      }

      return roles;
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public final IdentityID[] getRoles(IdentityID user) {
      return Arrays.stream(searchRoles(user.getName(), Identity.USER))
         .map(r -> new IdentityID(r, Organization.getDefaultOrganizationID()))
         .toArray(IdentityID[]::new);
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public final Role getRole(IdentityID roleid) {
      if(roleid != null) {
         if(!existRole(roleid)) {
            return null;
         }

         if(Organization.getDefaultOrganizationID().equals(roleid.getOrgID()) || roleid.getOrgID() == null) {
            return getCache().getRole(roleid.getName());
         }
      }

      return null;
   }

   protected Role doGetRole(String name) {
      IdentityID id = new IdentityID(name, Organization.getDefaultOrganizationID());
      IdentityID[] roles = Arrays.stream(searchRoles(name, Identity.ROLE))
         .map(r -> new IdentityID(r, Organization.getDefaultOrganizationID()))
         .toArray(IdentityID[]::new);
      return new Role(id, roles);
   }

   /**
    * Search parent roles.
    * @param name identity id
    * @param type identity type
    */
   protected final String[] searchRoles(String name, int type) {
      try {
         if(name == null || getRolesSearch(type) == null) {
            return new String[0];
         }

         String dn = getDistinguishedName(name, type);

         if(dn == null) {
            return new String[0];
         }

         return getCache().getRoles(getUserCommonName(name, type), dn, type);
      }
      catch(Exception e) {
         LOG.error(
            "Failed to list the roles assigned to the identity named {} of type {}", name, type, e);
         return new String[0];
      }
   }

   /**
    * Get the common name through user attribute before user-role search.
    * Override by GenericLdapAuthenticationProvider.
    *
    * @return a common name.
    */
   protected String getUserCommonName(String name, int type) {
      return name;
   }

   protected String[] doSearchRoles(String name, String dn, int type) {
      String filter = MessageFormat.format(getRolesSearch(type), name, dn);
      String attr = getRoleAttribute();
      return Arrays.stream(getRoleBases(getRoleBase()))
         .flatMap(r -> searchDirectory(r, filter, SearchControls.SUBTREE_SCOPE, attr).stream())
         .toArray(String[]::new);
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public final IdentityID[] getGroups() {
      try {
         if((ignoreCache.get() == null || !ignoreCache.get()) && isCacheInitialized()) {
            return Arrays.stream(getCache().getGroups())
               .map(g -> new IdentityID(g, Organization.getDefaultOrganizationID()))
               .toArray(IdentityID[]::new);
         }

         return doGetGroups().keySet().stream()
            .map(g -> new IdentityID(g, Organization.getDefaultOrganizationID()))
            .toArray(IdentityID[]::new);
      }
      catch(Exception e) {
         LOG.error("Failed to list the groups", e);
         return new IdentityID[0];
      }
   }

   /**
    * Get a list of all groups defined in the system.
    */
   private Map<String, String> doGetGroups() {
      String entryDn = getEntryDNAttribute();
      Map<String, String> values = new HashMap<>();
      String[] bases = getGroupBases(getGroupBase());
      String groupAttr = getGroupAttribute();

      try {
         for(String base : bases) {
            LdapName baseDn = new LdapName(base);
            Set<Rdn> rdns = new HashSet<>(baseDn.getRdns());
            List<SearchResult> results = searchDirectory(
               base, getGroupSearch(), SearchControls.SUBTREE_SCOPE,
               new String[]{ getGroupAttribute(), entryDn });

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

   /**
    * {@inheritDoc}
    */
   @Override
   public final Group getGroup(IdentityID name) {
      return getCache().getGroup(name.getName());
   }

   protected Group doGetGroup(String name) {
      IdentityID[] roles = Arrays.stream(searchRoles(name, Identity.GROUP))
         .map(r -> new IdentityID(r, Organization.getDefaultOrganizationID()))
         .toArray(IdentityID[]::new);
      ArrayList<String> list = new ArrayList<>();
      IdentityID[] groups = getGroups();

      for(IdentityID group : groups) {
        List<String> sgroups = searchSubIdentities(group.getName(), Identity.GROUP);

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

   /**
    * Gets the distinguished name of the LDAP administrator.
    *
    * @return the DN of the administrator.
    */
   public String getLdapAdministrator() {
      return ldapAdministrator;
   }

   public void setLdapAdministrator(String ldapAdministrator) {
      this.ldapAdministrator = ldapAdministrator;
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public boolean authenticate(IdentityID user, Object credential) {
      if(credential == null) {
         LOG.error("Ticket is null, cannot authenticate.");
         return false;
      }

      DefaultTicket ticket;

      if(!(credential instanceof DefaultTicket)) {
         ticket = DefaultTicket.parse(credential.toString());
      }
      else {
         ticket = (DefaultTicket) credential;
      }

      IdentityID userid = ticket.getName();
      String passwd = ticket.getPassword();

      if(userid == null || userid.name.isEmpty() || passwd == null) {
         return false;
      }

      try {
         LdapContext context = borrowContext();

         try {
            return authenticate(userid, passwd, (LdapContext) context.lookup(""));
         }
         finally {
            returnContext(context);
         }
      }
      catch(CommunicationException ce) {
         LOG.error(
            "A communication problem prevented the user from being authenticated: {}", user, ce);
         // throwing custom extension of runtimeException rather than ce so as to not change the
         // signature of authenticate()
         throw new AuthenticatorCommunicationException();
      }
      catch(Exception ne) {
         LOG.error("An error prevented the user from being authenticated: {}", user, ne);
      }

      return false;
   }

   /**
    * Authenticate the specified user.
    */
   protected boolean authenticate(IdentityID userid, String passwd,
      LdapContext context) throws NamingException
   {
      String filter = "(&(" + getUserSearch() +
         ")(" + getUserAttribute() + '=' + Tool.encodeForLDAP(userid.name) +
         "))";
      SearchControls controls = new SearchControls();
      controls.setSearchScope(SearchControls.SUBTREE_SCOPE);
      String[] userBases = getUserBases(getUserBase());

      for(String userBase : userBases) {
         if(doAuthenticate(userBase, filter, passwd, controls, context)) {
            return true;
         }
      }

      return false;
   }

   /**
    * Do the detail work of authenticate.
    */
   private boolean doAuthenticate(String userBase, String filter, String passwd,
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
      String root = getRootDn();
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
    * Get root.
    */
   public String getRootDn() {
      return rootDn;
   }

   public void setRootDn(String rootDn) {
      this.rootDn = rootDn;
   }

   public String getProtocol() {
      return protocol;
   }

   public void setProtocol(String protocol) {
      this.protocol = protocol;
   }

   public boolean isSearchSubtree() {
      return userScope == SearchControls.SUBTREE_SCOPE;
   }

   public void setSearchSubtree(boolean searchSubtree) {
      if(searchSubtree) {
         userScope = SearchControls.SUBTREE_SCOPE;
      }
      else {
         userScope = SearchControls.ONELEVEL_SCOPE;
      }
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public void checkParameters() throws SRSecurityException {
      flushContextPool();

      try {
         loadCredential();
         LdapContext context = createContext();

         try {
            if(!testContext(context)) {
               throw
                  new SRSecurityException("Failed to connect to LDAP server");
            }
         }
         finally {
            try {
               context.close();
            }
            catch(Exception exc) {
               LOG.warn("Failed to close directory context", exc);
            }
         }
      }
      catch(CommunicationException exc) {
         Throwable rootEx = exc.getRootCause();

         if(rootEx == null) {
            rootEx = exc;
         }

         String rootMessage = rootEx.toString();

         if(rootMessage.contains("java.net.ConnectException")) {
            throw new SRSecurityException("Invalid Port & Host Name combination", exc);
         }
         else if(rootMessage.contains("java.net.UnknownHostException")) {
            throw new SRSecurityException("Invalid Host Name", exc);
         }
      }
      catch(AuthenticationException exc) {
         throw new SRSecurityException("Invalid Administrator Credentials.", exc);
      }
      catch(NamingException exc) {
         throw new SRSecurityException("Failed to connect to LDAP server", exc);
      }
   }

   @Override
   public void loadCredential(String secretId) {
      JsonNode jsonNode = Tool.loadCredentials(secretId, !isUseCredential());

      if(jsonNode != null) {
         try {
            ldapAdministrator = jsonNode.get("administrator_id").asText();
            password = jsonNode.get("password").asText();
         }
         catch(Exception e) {
            throw new RuntimeException("Failed to load credentials!");
         }
      }
   }

   /**
    * Clear the provider and reiniliaze.
    */
   public void clear() {
      try {
         checkParameters();
      }
      catch(Exception ex) {
         LOG.error("Failed to reload LDAP security provider: " +
            ex.getMessage(), ex);
      }
   }

   protected DataChangeListener changeListener = e -> {
      LOG.debug(e.toString());
      clear();
   };

   /**
    * Returns the real user account name for this user.
    * @param userid The userid to search for in the directory
    */
   private String getRealUserName(String userid) {
      // @by stephenwebster, this is the same logic used to authenticate a user.
      // For now, this will be a stop-gap solution to get the exact user id from
      // LDAP that the userid represents.  For Bug #6240
      String filter = "(&(" + getUserSearch() +
         ")(" + getUserAttribute() + '=' + Tool.encodeForLDAP(userid) +
         "))";
      SearchControls controls = new SearchControls();
      controls.setSearchScope(SearchControls.SUBTREE_SCOPE);
      controls.setReturningAttributes(new String[] {getUserAttribute()});
      String[] userBases = getUserBases(getUserBase());
      String userName = userid;

      try {
         LdapContext context = borrowContext();

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
                  (String) result.getAttributes().get(getUserAttribute()).get(0);
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
            returnContext(context);
         }
      }
      catch(Exception e) {
         LOG.warn("Problem finding account name for user, " +
                        "context is not available", e);
      }

      return userName;
   }

   /**
    * Get the LDAP directory in which to start searching for users.
    *
    * @return the name of an LDAP entry.
    */
   public String getUserBase() {
      return userBase;
   }

   /**
    * Get the search filter to use when searching for users.
    *
    * @return a search filter.
    */
   public String getUserSearch() {
      return userSearch;
   }

   /**
    * Get the name of the attribute that contains the user ID.
    *
    * @return an attribute name.
    */
   public abstract String getUserAttribute();

   /**
    * Get the name of the attribute that contains the user's email address.
    *
    * @return an attribute name.
    */
   public abstract String getMailAttribute();

   /**
    * Get the LDAP directory in which to start searching for roles.
    *
    * @return the name of an LDAP entry.
    */
   public String getRoleBase() {
      return roleBase;
   }

   /**
    * Get the search filter to use when searching for roles.
    *
    * @return a search filter.
    */
   public abstract String getRoleSearch();

   /**
    * Get the name of the attribute that contains the role name.
    *
    * @return an attribute name.
    */
   public abstract String getRoleAttribute();

   /**
    * Get the search filter to identity when searching for the roles of a specific identity. This
    * should be formatted as specified by MessageFormat, where <code>{0}</code> will be replaced
    * with the identity ID and <code>{1}</code> will be replaced with the distinguished name (DN) of
    * the identity.
    *
    * @param type the identity type.
    *
    * @return a search filter.
    */
   protected abstract String getRolesSearch(int type);

   protected final String getDistinguishedName(String name, int type) {
      return switch(type) {
         case Identity.USER -> getUserDn(name);
         case Identity.GROUP -> getGroupDn(name);
         case Identity.ROLE -> getRoleDn(name);
         default -> null;
      };
   }

   protected final String getUserDn(String name) {
      if(isCacheInitialized()) {
         return getCache().getUserDn(name);
      }

      String filter = "(&(" + getUserSearch() + ")(" + getUserAttribute() + "=" + name + "))";
      String attr = getEntryDNAttribute();
      return Arrays.stream(getUserBases(getUserBase()))
         .flatMap(base -> searchDirectory(base, filter, userScope, attr).stream())
         .findFirst()
         .orElse(null);
   }

   protected final String getGroupDn(String name) {
      if(isCacheInitialized()) {
         return getCache().getGroupDn(name);
      }

      String filter = "(&(" + getGroupSearch() + ")(" + getGroupAttribute() + "=" + name + "))";
      String attr = getEntryDNAttribute();
      return Arrays.stream(getGroupBases(getGroupBase()))
         .flatMap(base -> searchDirectory(base, filter, userScope, attr).stream())
         .findFirst()
         .orElse(null);
   }

   protected final String getRoleDn(String name) {
      if(isCacheInitialized()) {
         String dn = getCache().getRoleDn(name);
         LOG.error("Got DN from cache for role {}: {}", name, dn);
         return dn;
      }

      String filter = "(&(" + getRoleSearch() + ")(" + getRoleAttribute() + "=" + name + "))";
      String attr = getEntryDNAttribute();
      String dn = Arrays.stream(getRoleBases(getRoleBase()))
         .flatMap(base -> searchDirectory(base, filter, SearchControls.SUBTREE_SCOPE, attr).stream())
         .findFirst()
         .orElse(null);
      LOG.error("Got DN from server for role {}: {}", name, dn);
      return dn;
   }

   /**
    * Get the LDAP directory in which to start searching for groups.
    *
    * @return the name of an LDAP entry.
    */
   public String getGroupBase() {
      return groupBase;
   }

   /**
    * Get the search filter to use when searching for groups.
    *
    * @return a search filter.
    */
   public abstract String getGroupSearch();

   /**
    * Get the name of the attribute that contains the group name.
    *
    * @return an attribute name.
    */
   public abstract String getGroupAttribute();

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
   protected List<String> searchDirectory(String name, String filter, int scope, String attr) {
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

   protected Map<String, String> searchDirectoryDns(String name, String filter, int scope,
                                                    String attr)
   {
      try {
         String entryDn = getEntryDNAttribute();
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
   protected List<SearchResult> searchDirectory(String name, String filter, int scope,
                                                String[] attrs) throws NamingException
   {
      SearchControls scontrols = new SearchControls();
      scontrols.setSearchScope(scope);
      scontrols.setReturningAttributes(attrs);
      long totalStart = System.currentTimeMillis();
      LOG.debug("LDAP searching in {} for {}", name, filter);
      List<SearchResult> results = new ArrayList<>();

      try {
         LdapContext context = borrowContext();

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
            returnContext(context);
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
   protected void setPagedControl(LdapContext context, boolean paged, byte[] cookie) {
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

   /**
    * Get the user bases.
    */
   protected String[] getUserBases(String users) {
      return parseToUnits(users);
   }

   /**
    * Get the group bases.
    */
   protected String[] getGroupBases(String groups) {
      return parseToUnits(groups);
   }

   /**
    * Get the role bases.
    */
   protected String[] getRoleBases(String roles) {
      return parseToUnits(roles);
   }

   /**
    * Parse the multi bases string into string array
    */
   protected String[] parseToUnits(String multiBases) {
      if(multiBases == null) {
         return new String[] {};
      }
      else if(!multiBases.contains(";")) {
         return new String[] {multiBases};
      }
      else {
         return multiBases.split(";");
      }
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public final User getUser(IdentityID userIdentity) {
      if(userIdentity == null) {
         return null;
      }

      final String lowerCaseName = userIdentity.name.toLowerCase();

      for(IdentityID userName : getUsers()) {
         if(userIdentity.equalsIgnoreCase(userName)) {
            return getCache().getUser(lowerCaseName);
         }
      }

      return null;
   }

   protected User doGetUser(IdentityID name) {
      final String realName = getRealUserName(name.getName());
      IdentityID realId = new IdentityID(realName, name.getOrgID());
      IdentityID[] roles = Arrays.stream(searchRoles(realName, Identity.USER))
         .map(r -> new IdentityID(r, name.getOrgID()))
         .toArray(IdentityID[]::new);
      String[] pgroups = getUserGroups(realId);
      return new User(realId, getEmails(name), pgroups, roles, "", "");
   }

   @Override
   public boolean isSystemAdministratorRole(IdentityID roleId) {
      for(String sysAdminRoles : systemAdministratorRoles) {
         if(sysAdminRoles.equals(roleId.getName())) {
            return true;
         }
      }

      return false;
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public void clearCache() {
      cacheLock.lock();

      try {
         if(cache != null) {
            cache.load();
         }
      }
      finally {
         cacheLock.unlock();
      }
   }

   @Override
   public boolean isCacheEnabled() {
      return true;
   }

   @Override
   public boolean isLoading() {
      return getCache().isLoading();
   }

   @Override
   public long getCacheAge() {
      return getCache().getAge();
   }

   boolean isCacheInitialized() {
      return getCache().isInitialized();
   }

   public String getHost() {
      return host;
   }

   public void setHost(String host) {
      this.host = host;
   }

   public int getPort() {
      return port;
   }

   public void setPort(int port) {
      this.port = port;
   }

   public void setUserSearch(String userSearch) {
      this.userSearch = userSearch;
   }

   public void setUserBase(String userBase) {
      this.userBase = userBase;
   }

   public void setGroupBase(String groupBase) {
      this.groupBase = groupBase;
   }

   public void setRoleBase(String roleBase) {
      this.roleBase = roleBase;
   }

   public String getUserRolesSearch() {
      return userRolesSearch;
   }

   public void setUserRolesSearch(String userRolesSearch) {
      this.userRolesSearch = userRolesSearch;
   }

   public String[] getSystemAdministratorRoles() {
      return systemAdministratorRoles;
   }

   public void setSystemAdministratorRoles(String[] systemAdministratorRoles) {
      this.systemAdministratorRoles = systemAdministratorRoles;
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public void readConfiguration(JsonNode configuration) {
      ObjectNode config = (ObjectNode) configuration;
      protocol = config.get("protocol").asText("ldap");
      setSearchSubtree(config.get("searchSubtree").asBoolean(false));
      validateContextAttr = config.get("validateContextAttr").asText("objectclass");
      validateContextBaseDn = config.get("validateContextBaseDn").asText("");
      validateContextSearch = config.get("validateContextSearch").asText("(objectclass=*)");
      rootDn = config.get("rootDn").asText("");
      host = config.get("host").asText("localhost");
      port = config.get("port").asInt(389);
      setUseCredential(config.get("useCredential").asBoolean(false));
      readCredential(config.get("credential").asText());

      ArrayNode sysAdminConfig = (ArrayNode) config.get("sysAdminRoles");
      systemAdministratorRoles = new String[sysAdminConfig.size()];

      for(int i = 0; i < sysAdminConfig.size(); i++) {
         systemAdministratorRoles[i] = sysAdminConfig.get(i).asText();
      }
   }

   private void readCredential(String secretId) {
      if(Tool.isEmptyString(secretId)) {
         return;
      }

      if(isUseCredential()) {
         setSecretId(secretId);
      }
      else {
         loadCredential(secretId);
      }
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public JsonNode writeConfiguration(ObjectMapper mapper) {
      ObjectNode config = mapper.createObjectNode();
      config.put("protocol", protocol);
      config.put("searchSubtree", isSearchSubtree());
      config.put("validateContextAttr", validateContextAttr);
      config.put("validateContextBaseDn", validateContextBaseDn);
      config.put("validateContextSearch", validateContextSearch);
      config.put("rootDn", rootDn);
      config.put("useCredential", isUseCredential());
      config.put("credential", writeCredential());
      config.put("host", host);
      config.put("port", port);
      config.put("userSearch", userSearch);
      config.put("userBase", userBase);
      config.put("groupBase", groupBase);
      config.put("roleBase", roleBase);
      config.put("userRolesSearch", userRolesSearch);

      ArrayNode sysAdminConfig = mapper.createArrayNode();

      for(String sysAdminRole : systemAdministratorRoles) {
         sysAdminConfig.add(sysAdminRole);
      }

      config.set("sysAdminRoles", sysAdminConfig);
      return config;
   }

   private String writeCredential() {
      try {
         if(isUseCredential()) {
            return getSecretId();
         }
         else {
            ObjectMapper mapper = new ObjectMapper();
            JsonNode credential = mapper.createObjectNode()
               .put("administrator_id", ldapAdministrator)
               .put("password", password);

            return Tool.encryptPassword(mapper.writeValueAsString(credential));
         }
      }
      catch(Exception e) {
         throw new RuntimeException("Failed to encrypt credential!");
      }
   }

   /**
    * Get the entrydn name.
    */
   protected String getEntryDNAttribute() {
      return ENTRYDN_ATTRIBUTE;
   }

   /**
    * Get the dn substring of a group.
    */
   protected String getDNString(String dn) {
      if(dn != null) {
         int idx = dn.toLowerCase().indexOf("," + getRootDn().toLowerCase());

         if(idx != -1) {
            return dn.substring(0, idx);
         }
      }

      return null;
   }

   private String getDNFromSearchResult(SearchResult result, String searchBase) {
      String dn;

      if(result.isRelative()) {
         dn = result.getName() + "," + searchBase;

         if(!dn.endsWith("," + getRootDn())) {
            dn = dn + "," + getRootDn();
         }
      }
      else {
         dn = result.getName();
      }

      return dn;
   }

   public String getPassword() {
      return password;
   }

   /**
    * Set the password.
    */
   public void setPassword(String password) {
      this.password = password;
   }

   /**
    * Borrows a directory context from the pool.
    *
    * @return a directory context.
    *
    * @throws Exception if a context could not be created.
    */
   protected LdapContext borrowContext() throws Exception {
      return contextPool.borrowObject();
   }

   /**
    * Returns a directory context to the pool.
    *
    * @param context the directory context.
    *
    * @return <tt>true</tt> if the context was borrowed from this provider.
    */
   @SuppressWarnings("UnusedReturnValue")
   protected boolean returnContext(LdapContext context) {
      return contextPool.returnObject(context);
   }

   /**
    * Flushes all pool directory contexts.
    */
   protected void flushContextPool() {
      cacheLock.lock();

      try {
         if(cache != null) {
            cache.reset();
         }
      }
      finally {
         cacheLock.unlock();
      }

      contextPool.flush();
   }

   @Override
   public void setProviderName(String providerName) {
      String oldName = getProviderName();
      super.setProviderName(providerName);

      if(!Objects.equals(providerName, oldName)) {
         cacheLock.lock();

         try {
            if(cache != null) {
               try {
                  cache.close();
               }
               catch(Exception e) {
                  LOG.warn("Failed to close security cache", e);
               }

               cache = null;
            }
         }
         finally {
            cacheLock.unlock();
         }
      }
   }

   @SuppressWarnings("unused")
   public boolean isIgnoreCache() {
      return ignoreCache.get() != null && ignoreCache.get();
   }

   public void setIgnoreCache(boolean ignoreCache) {
      this.ignoreCache.set(ignoreCache);
   }

   private LdapCache getCache() {
      cacheLock.lock();

      try {
         if(cache == null) {
            cache = new LdapCache();
         }

         cache.initialize();
         return cache;
      }
      finally {
         cacheLock.unlock();
      }
   }

   protected boolean supportsNamingListener() {
      return true;
   }

   private final ContextPool contextPool;
   private String protocol;
   private int userScope = SearchControls.ONELEVEL_SCOPE;
   private String validateContextAttr = "objectclass";
   private String validateContextBaseDn = "";
   private String validateContextSearch = "(objectclass=*)";
   private String rootDn = "";
   private String password;
   private String host = "localhost";
   private int port = 389;
   private String userSearch = "";
   private String userBase = "";
   private String groupBase = "";
   private String roleBase = "";
   private String userRolesSearch = null;
   private String ldapAdministrator = "cn=manager";
   private String[] systemAdministratorRoles = new String[0];
   private final Lock cacheLock = new ReentrantLock();
   private LdapCache cache;
   private final ThreadLocal<Boolean> ignoreCache = ThreadLocal.withInitial(() -> false);

   private static final String ENTRYDN_ATTRIBUTE = "entrydn";

   private static final Logger LOG =
      LoggerFactory.getLogger(LdapAuthenticationProvider.class);

   private final class ContextPool extends ObjectPool<LdapContext> {
      ContextPool() throws Exception {
         super(0, 4, 4, 0L);
      }

      @Override
      protected LdapContext create() throws Exception {
         LdapContext context;

         try {
            context = createContext();
         }
         catch(AuthenticationException exc) {
            LOG.error(
               "Failed to authenticate with the LDAP server using the " +
               "provided administrator credentials. The credentials may have " +
               "been changed on the server. If this is the case, you may log " +
               "into the Enterprise Manager using the distinguished name " +
               "(DN) of the administrator as the user name and the new " +
               "password as the password. This will update the LDAP " +
               "credentials. You may then log into the Enterprise Manager as " +
               "normal.");
            throw exc;
         }

         initInstance(context);
         return context;
      }

      @Override
      protected void destroy(LdapContext object) {
         try {
            object.close();
         }
         catch(Throwable exc) {
            LOG.warn("Failed to close directory context", exc);
         }
      }

      @Override
      protected boolean validate(LdapContext object) {
         return testContext(object);
      }
   }

   private final class LdapCache
      extends ClusterCache<NamingEvent, LoadData, SaveData>
      implements NamespaceChangeListener, ObjectChangeListener
   {
      public LdapCache() {
         super(true, 1500L, TimeUnit.MILLISECONDS, 1000L, TimeUnit.MILLISECONDS,
               getCacheInterval(), TimeUnit.MILLISECONDS,
               "LdapSecurityCache:" + getProviderName() + ".",
               LISTS_CACHE, EMAILS_CACHE, USERS_CACHE, GROUPS_CACHE, ROLES_CACHE,
               IDENTITY_CACHE, USER_ROLES_CACHE, USER_DNS_CACHE, GROUP_DNS_CACHE,
               ROLE_DNS_CACHE);
      }

      @Override
      public void reset() {
         removeListeners();
         super.reset();
      }

      @SuppressWarnings("rawtypes")
      @Override
      protected Map<String, Map> doLoad(boolean initializing, LoadData data) {
         Map<String, Map> maps = new HashMap<>();

         Map<String, String[]> lists = new HashMap<>();
         lists.put(INDIVIDUAL_USERS_LIST, doGetIndividualUsers());
         lists.put(INDIVIDUAL_EMAILS_LIST, doGetIndividualEmailAddresses());
         maps.put(LISTS_CACHE, lists);

         maps.put(USER_DNS_CACHE, doGetUsers());
         maps.put(GROUP_DNS_CACHE, doGetGroups());
         maps.put(ROLE_DNS_CACHE, doGetRoles());
         maps.put(EMAILS_CACHE, Collections.emptyMap());
         maps.put(USERS_CACHE, Collections.emptyMap());
         maps.put(ROLES_CACHE, Collections.emptyMap());
         maps.put(GROUPS_CACHE, Collections.emptyMap());
         maps.put(IDENTITY_CACHE, Collections.emptyMap());
         maps.put(USER_ROLES_CACHE, Collections.emptyMap());

         cacheLock.lock();

         try {
            if(!listenersAdded) {
               addListeners();
               listenersAdded = true;
            }
         }
         finally {
            cacheLock.unlock();
         }

         return maps;
      }

      @Override
      protected LoadData getLoadData(NamingEvent event) {
         return null;
      }

      @Override
      protected void close(boolean lastInstance) throws Exception {
         removeListeners();
         super.close(lastInstance);
      }

      @Override
      public void objectAdded(NamingEvent evt) {
         notify(evt);
      }

      @Override
      public void objectRemoved(NamingEvent evt) {
         notify(evt);
      }

      @Override
      public void objectRenamed(NamingEvent evt) {
         notify(evt);
      }

      @Override
      public void objectChanged(NamingEvent evt) {
         notify(evt);
      }

      @Override
      public void namingExceptionThrown(NamingExceptionEvent evt) {
         LOG.warn("LDAP exception occurred", evt.getException());
      }

      String[] getUsers() {
         validateListeners();
         return getUserDnsMap().keySet().toArray(new String[0]);
      }

      String[] getSubIdentities(String group, int type) {
         validateListeners();
         IdentityKey key = new IdentityKey(group, type);
         return getIdentityMap().computeIfAbsent(
            key, k -> doSearchSubIdentities(group, type).toArray(new String[0]));
      }

      String[] getEmails(String user) {
         validateListeners();
         return getEmailsMap().computeIfAbsent(user, LdapAuthenticationProvider.this::doGetEmails);
      }

      String[] getIndividualUsers() {
         validateListeners();
         return getListsMap().get(INDIVIDUAL_USERS_LIST);
      }

      String[] getIndividualEmails() {
         validateListeners();
         return getListsMap().get(INDIVIDUAL_EMAILS_LIST);
      }

      String[] getRoles() {
         validateListeners();
         return getRoleDnsMap().keySet().toArray(new String[0]);
      }

      String[] getGroups() {
         validateListeners();
         return getGroupDnsMap().keySet().toArray(new String[0]);
      }

      String getUserDn(String user) {
         validateListeners();
         return getUserDnsMap().get(user);
      }

      String getGroupDn(String group) {
         validateListeners();
         return getGroupDnsMap().get(group);
      }

      String getRoleDn(String role) {
         validateListeners();
         return getRoleDnsMap().get(role);
      }

      String[] getRoles(String name, String dn, int type) {
         validateListeners();
         IdentityKey key = new IdentityKey(name, type);
         return getIdentityRoleMap().computeIfAbsent(key, k -> doSearchRoles(name, dn, type));
      }

      User getUser(String name) {
         validateListeners();
         return getUsersMap().computeIfAbsent(
            name, k -> doGetUser(new IdentityID(name, Organization.getDefaultOrganizationID())));
      }

      Group getGroup(String name) {
         validateListeners();
         return getGroupsMap().computeIfAbsent(name, LdapAuthenticationProvider.this::doGetGroup);
      }

      Role getRole(String name) {
         validateListeners();
         return getRolesMap().computeIfAbsent(name, LdapAuthenticationProvider.this::doGetRole);
      }

      private Map<String, String[]> getListsMap() {
         return getMap(LISTS_CACHE);
      }

      private Map<String, String[]> getEmailsMap() {
         return getMap(EMAILS_CACHE);
      }

      private Map<String, User> getUsersMap() {
         return getMap(USERS_CACHE);
      }

      private Map<String, Group> getGroupsMap() {
         return getMap(GROUPS_CACHE);
      }

      private Map<String, Role> getRolesMap() {
         return getMap(ROLES_CACHE);
      }

      private Map<IdentityKey, String[]> getIdentityMap() {
         return getMap(IDENTITY_CACHE);
      }

      private Map<IdentityKey, String[]> getIdentityRoleMap() {
         return getMap(USER_ROLES_CACHE);
      }

      private Map<String, String> getUserDnsMap() {
         return getMap(USER_DNS_CACHE);
      }

      private Map<String, String> getGroupDnsMap() {
         return getMap(GROUP_DNS_CACHE);
      }

      private Map<String, String> getRoleDnsMap() {
         return getMap(ROLE_DNS_CACHE);
      }

      private void validateListeners() {
         validateLock.lock();

         try {
            Instant now = Instant.now();

            if(validateTimestamp == null || now.isAfter(validateTimestamp)) {
               LdapContext context = dirContext;

               if(dirContext != null && !testContext(context)) {
                  addListeners();
               }

               validateTimestamp = now.plus(5L, ChronoUnit.MINUTES);
            }
         }
         finally {
            validateLock.unlock();
         }
      }

      private void addListeners() {
         removeListeners();

         try {
            dirContext = createContext();
            eventContext = (EventDirContext) dirContext.lookup("");

            if(supportsNamingListener()) {
               eventContext.addNamingListener("", SearchControls.SUBTREE_SCOPE, this);
            }
         }
         catch(Exception e) {
            LOG.error("Failed to add LDAP listener, automatic cache invalidation disabled", e);

            if(eventContext != null) {
               try {
                  eventContext.close();
               }
               catch(NamingException ignore) {
               }
               finally {
                  eventContext = null;
               }
            }

            if(dirContext != null) {
               try {
                  dirContext.close();
               }
               catch(NamingException ignore) {
               }
               finally {
                  dirContext = null;
               }
            }
         }
      }

      private void removeListeners() {
         if(eventContext != null) {
            listenersAdded = false;

            try {
               //noinspection SynchronizeOnNonFinalField
               synchronized(eventContext) {
                  eventContext.removeNamingListener(this);
                  eventContext.close();
               }
            }
            catch(NamingException e) {
               LOG.warn("Failed to remove LDAP listener", e);
            }
            finally {
               eventContext = null;
            }
         }

         if(dirContext != null) {
            try {
               dirContext.close();
            }
            catch(NamingException e) {
               LOG.warn("Failed to close LDAP context", e);
            }
            finally {
               dirContext = null;
            }
         }
      }

      @Override
      public boolean isInitialized() {
         return super.isInitialized();
      }

      private LdapContext dirContext;
      private EventDirContext eventContext;
      private Instant validateTimestamp;
      private final Lock validateLock = new ReentrantLock();
      private boolean listenersAdded = false;

      private static final String LISTS_CACHE = "lists";
      private static final String IDENTITY_CACHE = "identities";
      private static final String EMAILS_CACHE = "emails";
      private static final String USERS_CACHE = "users";
      private static final String GROUPS_CACHE = "groups";
      private static final String ROLES_CACHE = "roles";
      private static final String USER_ROLES_CACHE = "userRoles";
      private static final String USER_DNS_CACHE = "userDnsCache";
      private static final String GROUP_DNS_CACHE = "groupDnsCache";
      private static final String ROLE_DNS_CACHE = "roleDnsCache";
      private static final String INDIVIDUAL_USERS_LIST = "individualUsers";
      private static final String INDIVIDUAL_EMAILS_LIST = "individualEmails";
   }

   private static final class IdentityKey implements Serializable {
      public IdentityKey(String name, int type) {
         this.name = name;
         this.type = type;
      }

      @Override
      public boolean equals(Object o) {
         if(this == o) {
            return true;
         }

         if(o == null || getClass() != o.getClass()) {
            return false;
         }

         IdentityKey that = (IdentityKey) o;
         return type == that.type && Objects.equals(name, that.name);
      }

      @Override
      public int hashCode() {
         return Objects.hash(name, type);
      }

      private final String name;
      private final int type;
   }

   private static final class LoadData implements Serializable {
      public LoadData(String dn, long timestamp) {
         this.dn = dn;
         this.timestamp = timestamp;
      }

      final String dn;
      final long timestamp;
   }

   private static final class SaveData implements Serializable {
      public SaveData(String dn, long timestamp) {
         this.dn = dn;
         this.timestamp = timestamp;
      }

      final String dn;
      final long timestamp;
   }
}
