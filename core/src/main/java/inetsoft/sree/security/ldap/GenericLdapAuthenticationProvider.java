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

import javax.naming.*;
import javax.naming.directory.*;
import javax.naming.ldap.*;
import javax.net.ssl.SSLSession;
import java.io.IOException;
import java.lang.reflect.*;
import java.util.Hashtable;

/**
 * Authentication module that obtains information from a directory server.
 *
 * @author InetSoft Technology
 * @since  5.1
 */
public class GenericLdapAuthenticationProvider extends LdapAuthenticationProvider {
   /**
    * Creates a new instance of GenericLdapAuthenticationProvider.
    */
   public GenericLdapAuthenticationProvider() {
      super();
   }

   /**
    * Create a directory context that can be used to query the LDAP server.
    *
    * @return a directory context.
    *
    * @throws NamingException if an error occured during the creation of the
    *                         context.
    */
   @Override
   protected LdapContext createContext() throws NamingException {
      Hashtable<Object, Object> env = new Hashtable<>();

      env.put(Context.INITIAL_CONTEXT_FACTORY, "com.sun.jndi.ldap.LdapCtxFactory");

      String url = getLdapProtocol() + getHost() + ':' + getPort() + '/' + getRootDn();
      env.put(Context.PROVIDER_URL, url);

      if(startTls) {
         LdapContext context = new InitialLdapContext(env, null);
         StartTlsResponse tls =
            (StartTlsResponse) context.extendedOperation(new StartTlsRequest());

         try {
            LOG.debug("Starting StartTLS session");
            SSLSession session = tls.negotiate();
            setContextAuthProperties(context::addToEnvironment);
            InvocationHandler handler = new StartTlsHandler(tls, session, context);

            return (LdapContext) Proxy.newProxyInstance(
               getClass().getClassLoader(),
               new Class[] { Context.class, DirContext.class, LdapContext.class, StartTlsContext.class },
               handler);
         }
         catch(IOException e) {
            NamingException ne =
               new CommunicationException("Failed to negotiate StartTLS session");
            ne.setRootCause(e);
            throw ne;
         }
      }
      else {
         setContextAuthProperties(env::put);
         return new InitialLdapContext(env, null);
      }
   }

   private void setContextAuthProperties(ContextPropertyConsumer fn)
      throws NamingException
   {
      String user = getLdapAdministrator();
      String password = getPassword();

      if(password == null) {
         password = "";
      }

      fn.add(Context.SECURITY_AUTHENTICATION, "simple");
      fn.add(Context.SECURITY_PRINCIPAL, user);
      fn.add(Context.SECURITY_CREDENTIALS, password);
   }

   /**
    * Get the name of the attribute that contains the group name.
    *
    * @return an attribute name.
    */
   @Override
   public String getGroupAttribute() {
      return groupAttribute;
   }

   /**
    * Get the search filter to use when searching for groups.
    *
    * @return a search filter.
    */
   @Override
   public String getGroupSearch() {
      return groupSearch;
   }

   /**
    * Get the name of the attribute that contains the role name.
    *
    * @return an attribute name.
    */
   @Override
   public String getRoleAttribute() {
      return roleAttribute;
   }

   /**
    * Get the search filter to use when searching for roles.
    *
    * @return a search filter.
    */
   @Override
   public String getRoleSearch() {
      return roleSearch;
   }

   /**
    * Get the name of the attribute that contains the user ID.
    *
    * @return an attribute name.
    */
   @Override
   public String getUserAttribute() {
      return userAttribute;
   }

   /**
    * Get the search filter to use when searching for the roles of a specific
    * identity.
    *
    * @return a search filter.
    */
   @Override
   protected String getRolesSearch(int type) {
      return switch(type) {
         case Identity.USER -> getUserRolesSearch();
         case Identity.ROLE -> roleRolesSearch;
         case Identity.GROUP -> groupRolesSearch;
         default -> null;
      };
   }

   /**
    * Initialize the LDAP security provider.
    *
    * @param context the directory context.
    */
   @Override
   protected void initInstance(LdapContext context) {
   }

   /**
    * Get the name of the attribute that contains the user's email address.
    *
    * @return an attribute name.
    */
   @Override
   public String getMailAttribute() {
      return mailAttribute;
   }

   /**
    * Get the common name through user attribute before user-role search.
    *
    * @return a common name.
    */
   @Override
   protected String getUserCommonName(String name, int type) {
      if(type != Identity.USER) {
         return name;
      }

      return getUserCommonName(name);
   }

   public void setUserAttribute(String userAttribute) {
      this.userAttribute = userAttribute;
   }

   public void setMailAttribute(String mailAttribute) {
      this.mailAttribute = mailAttribute;
   }

   public void setGroupSearch(String groupSearch) {
      this.groupSearch = groupSearch;
   }

   public void setGroupAttribute(String groupAttribute) {
      this.groupAttribute = groupAttribute;
   }

   public void setRoleSearch(String roleSearch) {
      this.roleSearch = roleSearch;
   }

   public void setRoleAttribute(String roleAttribute) {
      this.roleAttribute = roleAttribute;
   }

   public String getRoleRolesSearch() {
      return roleRolesSearch;
   }

   public void setRoleRolesSearch(String roleRolesSearch) {
      this.roleRolesSearch = roleRolesSearch;
   }

   public String getGroupRolesSearch() {
      return groupRolesSearch;
   }

   public void setGroupRolesSearch(String groupRolesSearch) {
      this.groupRolesSearch = groupRolesSearch;
   }

   public boolean isStartTls() {
      return startTls;
   }

   public void setStartTls(boolean startTls) {
      this.startTls = startTls;
   }

   @Override
   public void readConfiguration(JsonNode configuration) {
      super.readConfiguration(configuration);
      ObjectNode config = (ObjectNode) configuration;
      setUserSearch(config.get("userSearch").asText(""));
      setUserBase(config.get("userBase").asText(""));
      userAttribute = config.get("userAttribute").asText("");
      mailAttribute = config.get("mailAttribute").asText("");
      groupSearch = config.get("groupSearch").asText("");
      setGroupBase(config.get("groupBase").asText(""));
      groupAttribute = config.get("groupAttribute").asText("");
      roleSearch = config.get("roleSearch").asText("");
      setRoleBase(config.get("roleBase").asText(""));
      roleAttribute = config.get("roleAttribute").asText("");
      setUserRolesSearch(config.get("userRolesSearch").asText());
      roleRolesSearch = config.get("roleRolesSearch").asText();
      groupRolesSearch = config.get("groupRolesSearch").asText();
      startTls = config.hasNonNull("startTls") &&
         config.get("startTls").asBoolean(false);
   }

   @Override
   public JsonNode writeConfiguration(ObjectMapper mapper) {
      ObjectNode config = (ObjectNode) super.writeConfiguration(mapper);
      config.put("userAttribute", userAttribute);
      config.put("mailAttribute", mailAttribute);
      config.put("groupSearch", groupSearch);
      config.put("groupAttribute", groupAttribute);
      config.put("roleSearch", roleSearch);
      config.put("roleAttribute", roleAttribute);
      config.put("roleRolesSearch", roleRolesSearch);
      config.put("groupRolesSearch", groupRolesSearch);
      config.put("startTls", startTls);
      return config;
   }

   @Override
   protected boolean testContext(LdapContext context) {
      if(context instanceof StartTlsContext &&
         !((StartTlsContext) context).getSslSession().isValid())
      {
         return false;
      }

      return super.testContext(context);
   }

   private String getUserCommonName(String name) {
      try {
         LdapContext context = borrowContext();

         try {
            String filter = "(&(" +
               getUserSearch() + ")(" + getUserAttribute() +
               '=' + Tool.encodeForLDAP(name) + "))";
            SearchControls controls = new SearchControls();
            controls.setSearchScope(SearchControls.SUBTREE_SCOPE);
            String[] userBases = getUserBases(getUserBase());

            return getUserCommonName0(name, userBases[0], filter, controls, context);
         }
         catch(Exception e) {
            return name;
         }
         finally {
            returnContext(context);
         }
      }
      catch(Exception ex) {
         return name;
      }
   }

   private String getUserCommonName0(String name, String userBase,
      String filter, SearchControls controls, LdapContext context)
      throws NamingException
   {
      NamingEnumeration<?> e = context.search(userBase, filter, controls);

      if(!e.hasMore()) {
         return name;
      }

      SearchResult result = (SearchResult) e.next();
      return getUserCommonName1(result.getName());
   }

   private String getUserCommonName1(String result) {
      String temp = result;
      int index = temp.indexOf(",");

      if(index > 0) {
         temp = temp.substring(0, index);
      }

      index = temp.indexOf("=");

      if(index > 0) {
         return temp.substring(index + 1);
      }
      else {
         return temp;
      }
   }

   private String userAttribute = "";
   private String mailAttribute = "";
   private String groupSearch = "";
   private String groupAttribute = "";
   private String roleSearch = "";
   private String roleAttribute = "";
   private String roleRolesSearch = null;
   private String groupRolesSearch = null;
   private boolean startTls = false;

   private static final Logger LOG =
      LoggerFactory.getLogger(GenericLdapAuthenticationProvider.class);

   @FunctionalInterface
   private interface ContextPropertyConsumer {
      void add(String name, Object value) throws NamingException;
   }

   interface StartTlsContext {
      StartTlsResponse getStartTlsResponse();
      SSLSession getSslSession();
      LdapContext getDelegateContext();
   }

   private static final class StartTlsHandler implements InvocationHandler {
      public StartTlsHandler(StartTlsResponse startTlsResponse, SSLSession sslSession,
                             LdapContext delegateContext)
      {
         this.startTlsResponse = startTlsResponse;
         this.sslSession = sslSession;
         this.delegateContext = delegateContext;
      }

      @Override
      public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
         switch(method.getName()) {
         case "getStartTlsResponse":
            return startTlsResponse;
         case "getSslSession":
            return sslSession;
         case "getDelegateContext":
            return delegateContext;
         case "close":
            try {
               startTlsResponse.close();
            }
            catch(Exception e) {
               LOG.warn("Failed to close StartTLS session", e);
            }
            finally {
               LOG.debug("StartTLS session closed");
            }
            break;
         }

         return method.invoke(delegateContext, args);
      }

      private final StartTlsResponse startTlsResponse;
      private final SSLSession sslSession;
      private final LdapContext delegateContext;
   }
}
