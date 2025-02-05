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
package inetsoft.web.security;

import inetsoft.report.internal.LicenseException;
import inetsoft.report.internal.UnlicensedUserNameException;
import inetsoft.sree.*;
import inetsoft.sree.internal.SUtil;
import inetsoft.sree.security.*;
import inetsoft.sree.web.SessionLicenseManager;
import inetsoft.sree.web.SessionLicenseService;
import inetsoft.util.Catalog;
import inetsoft.util.Tool;
import inetsoft.web.viewsheet.service.LinkUriArgumentResolver;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.apache.hc.core5.net.InetAddressUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.AntPathMatcher;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.lang.reflect.Method;
import java.net.URI;
import java.security.Principal;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Base class for filters that handle authenticating the remote user against the security
 * provider.
 */
public abstract class AbstractSecurityFilter
   implements Filter, SessionAccessDispatcher.SessionAccessListener
{
   @Override
   public void init(FilterConfig config) throws ServletException {
      try {
         Method method = getClass().getDeclaredMethod(
            "sessionAccessed", SessionAccessDispatcher.SessionAccessEvent.class);

         if(method != null) { // overridden, add listener
            SessionAccessDispatcher.addListener(this);
            sessionAccessRegistered = true;
         }
      }
      catch(NoSuchMethodException ignore) { // NOSONAR not overridden, valid state
      }
   }

   @Override
   public void destroy() {
      if(sessionAccessRegistered) {
         SessionAccessDispatcher.removeListener(this);
      }
   }

   /**
    * Authenticates a user.
    *
    * @param request      the HTTP request object.
    * @param userName     the login of the user.
    * @param password     the password of the user.
    * @param loginAsUser  user being logged in as
    * @param locale       the locale for the user.
    *
    * @return a principal identifying the authenticated user.
    *
    * @throws AuthenticationFailureException if the user could not be authenticated.
    */
   protected SRPrincipal authenticate(ServletRequest request, IdentityID userName, String password,
                                      IdentityID loginAsUser, String locale)
      throws AuthenticationFailureException
   {
      return authenticate(request, userName, password, loginAsUser, locale, true);
   }

   protected SRPrincipal authenticate(ServletRequest request, IdentityID userID, String password,
                                      IdentityID loginAsUser, String locale, boolean createSession)
      throws AuthenticationFailureException
   {
      SRPrincipal principal;
      logout(request, false);

      HttpServletRequest httpRequest = (HttpServletRequest) request;
      RequestUriInfo uriInfo = new RequestUriInfo(httpRequest);

      try {
         principal = (SRPrincipal) AuthenticationService.getInstance().authenticate(
            userID, loginAsUser, password, request.getRemoteHost(), uriInfo.getRemoteIp(),
            httpRequest.getServerName(), locale, request.getLocale(), false, true, httpRequest.getSession(true).getId(),
            uriInfo.getRequestedUri());
      }
      catch(Exception e) {
         LOG.error("Failed to authenticate user", e);
         throw new AuthenticationFailureException(
            AuthenticationFailureReason.GENERIC_ERROR,
            "Failed to authenticate user", e);
      }

      if(principal != null && createSession) {
         String token = principal.getProperty(SUtil.TICKET);
         principal.setProperty(SUtil.TICKET, null);
         principal.setProperty(SUtil.LONGON_TIME, System.currentTimeMillis() + "");
         request.setAttribute(SUtil.TICKET, DefaultTicket.parse(token));
         principal.setProperty("showGettingStated", "true");
         createSession(request, principal);
      }

      return principal;
   }

   /**
    * Authenticates the remote user as an anonymous user.
    *
    * @param request the HTTP request object.
    *
    * @return a principal that identifies the anonymous user.
    *
    * @throws AuthenticationFailureException if the anonymous user session could not be
    *                                        created.
    */
   @SuppressWarnings({ "RedundantThrows", "WeakerAccess" })
   protected SRPrincipal authenticateAnonymous(ServletRequest request)
      throws AuthenticationFailureException
   {

      IdentityID anonID = new IdentityID(ClientInfo.ANONYMOUS,
         getCookieRecordedOrgID((HttpServletRequest) request));
      ClientInfo info = createClientInfo(anonID, request);
      return (SRPrincipal) AuthenticationService.getInstance().authenticate(info, null);
   }

   protected String getCookieRecordedOrgID(HttpServletRequest request) {
      Cookie[] cookies = request.getCookies();

      return cookies == null ? Organization.getDefaultOrganizationID() :
         Arrays.stream(cookies).filter(c -> c.getName().equals(ORG_COOKIE))
            .map(Cookie::getValue).findFirst().orElse(Organization.getDefaultOrganizationID());
   }

   protected String[] getSSODefaultRole() {
      return SUtil.isMultiTenant() ? new String[0] :
         Tool.split(SreeEnv.getProperty("sso.default.roles"), ',');
   }

   /**
    * Create a session and add an audit login record when logging in with SSO
    */
   protected void createSSOSession(HttpServletRequest request,
                                   SRPrincipal principal) throws AuthenticationFailureException
   {
      final IdentityID pId = principal == null ? null : IdentityID.getIdentityIDFromKey(principal.getName());
      final IdentityID[] currentRoles = principal.getRoles();
      final String[] roles = getSSODefaultRole();
      final IdentityID[] newRoles =
         Stream.concat(Arrays.stream(roles).map(IdentityID::getIdentityIDFromKey), Arrays.stream(currentRoles)).toArray(IdentityID[]::new);
      principal.setRoles(newRoles);
      createSession(request, principal);
      final ClientInfo info = createClientInfo(principal.getIdentityID(), request);
      AuthenticationService.getInstance().authenticate(info, principal);
      SUtil.loginRecord(request, pId, true, null);
   }

   /**
    * Creates a session for the specified user and request.
    *
    * @param request   the HTTP request object.
    * @param principal a principal that identifies the remote user.
    *
    * @throws AuthenticationFailureException if a session could not be created.
    */
   protected void createSession(ServletRequest request, SRPrincipal principal)
      throws AuthenticationFailureException
   {
      HttpServletRequest httpRequest = (HttpServletRequest) request;
      HttpSession session = httpRequest.getSession(true);
      AuthenticationService authentication = AuthenticationService.getInstance();
      SessionLicenseManager sessionLicenseManager =
         SessionLicenseService.getSessionLicenseService();

      try {
         if(sessionLicenseManager != null) {
            int userSessionTimeout = SUtil.getUserSessionTimeout();

            // @by stephenwebster, For Bug #27464
            // log out timed out sessions prior to adding a new session to prevent old
            // user sessions from being orphaned inside the license manager.
            // The RequestPrincipalFilter keeps the current sessions principal access time updated
            if(userSessionTimeout > 0) {
               Set<SRPrincipal> activeSessions = sessionLicenseManager.getActiveSessions();

               for(SRPrincipal userSession : activeSessions) {
                  long userLastAccess = userSession.getLastAccess();

                  if(System.currentTimeMillis() - userLastAccess > userSessionTimeout) {
                     authentication.logout(userSession, request.getRemoteHost(), "", true);
                  }
               }
            }
         }

         authentication.addSession(principal);
         session.setAttribute(RepletRepository.PRINCIPAL_COOKIE, principal);
      }
      catch(UnlicensedUserNameException e) {
         throw new AuthenticationFailureException(
            AuthenticationFailureReason.NOT_NAMED_USER,
            // message is already localized in SessionLicenseService
            e.getMessage());
      }
      catch(LicenseException e) {
         // swallow license exception to avoid leaking details to SSO filters
         throw new AuthenticationFailureException(
            AuthenticationFailureReason.SESSION_EXCEEDED,
            Catalog.getCatalog(principal).getString("login.error.sessions.exceeded"));
      }
      catch(Exception thrown) {
         throw new AuthenticationFailureException(
            AuthenticationFailureReason.SESSION_EXCEEDED,
            Catalog.getCatalog(principal).getString("login.error.sessions.exceeded"), thrown);
      }

      if(isNonlocalClient(principal)) {
         int maxSessions = 5;

         String property = SreeEnv.getProperty("http.session.maxPerClient");

         if(property != null) {
            try {
               maxSessions = Integer.parseInt(property);
            }
            catch(NumberFormatException e) {
               LOG.error("Invalid value for http.session.maxPerClient: {}", property, e);
            }
         }

         List<SRPrincipal> sameClientPrincipals = SecurityEngine.getSecurity().getActivePrincipalList().stream()
            .filter(p -> p != principal)
            .filter(p -> isSameClient(principal, p))
            .sorted(Comparator.comparing(SRPrincipal::getLastAccess)) // Oldest accessed first
            .collect(Collectors.toList());

         if(sameClientPrincipals.size() >= maxSessions) {
            sameClientPrincipals.subList(0, sameClientPrincipals.size() - (maxSessions - 1))
               .forEach(authentication::logout);
         }
      }
   }

   /**
    * Check if the SRPrincipal exists in the session attributes
    */
   protected boolean hasSession(HttpServletRequest request) {
      if(request == null) {
         return false;
      }

      final HttpSession session = request.getSession();

      if(session != null) {
         try {
            final SRPrincipal principal = (SRPrincipal) SUtil.getPrincipal(request);
            return principal != null;
         }
         catch(IllegalStateException | ClassCastException e) {
            LOG.info("SRPrincipal not found", e);
         }
      }

      return false;
   }

   /**
    * Logs out the user associated with the specified request.
    *
    * @param request the HTTP request object.
    */
   protected void logout(ServletRequest request) {
      logout(request, true);
   }

   /**
    * Logs out the user associated with the specified request.
    *
    * @param request    the HTTP request object.
    * @param invalidate <tt>true</tt> to invalidate the HTTP session.
    */
   private void logout(ServletRequest request, boolean invalidate) {
      HttpSession session = ((HttpServletRequest) request).getSession(false);
      RequestUriInfo uriInfo = new RequestUriInfo((HttpServletRequest) request);

      if(session != null) {
         Principal principal =
            (Principal) session.getAttribute(RepletRepository.PRINCIPAL_COOKIE);

         if(principal != null) {
            AuthenticationService.getInstance().logout(principal, uriInfo.getRemoteIp(), "");
         }

         if(invalidate) {
            session.invalidate();
         }
      }
   }

   /**
    * Determines if security is enabled.
    *
    * @return <tt>true</tt> if security is enabled; <tt>false</tt> otherwise.
    */
   protected boolean isSecurityEnabled() {
      boolean result = false;

      try {
         SecurityEngine engine = SecurityEngine.getSecurity();

         if(engine != null) {
            SecurityProvider provider = engine.getSecurityProvider();
            result = provider != null && !provider.getAuthenticationProvider().isVirtual();
         }
      }
      catch(Exception e) {
         LOG.error("Failed to get security provider", e);
      }

      return result;
   }

   /**
    * Gets the configured security provider.
    *
    * @return the security provider.
    */
   protected SecurityProvider getSecurityProvider() {
      SecurityProvider provider = null;

      SecurityEngine engine = getSecurityEngine();

      if(engine != null) {
         provider = engine.getSecurityProvider();
      }

      return provider;
   }

   protected SecurityEngine getSecurityEngine() {
      return SecurityEngine.getSecurity();
   }

   protected Map<String, String[]> getQueryParameters(HttpServletRequest request) {
      return Tool.parseQueryString(request.getQueryString());
   }

   /**
    * Gets the path of the requested page.
    *
    * @param request the HTTP request object.
    *
    * @return the path, relative to the server root, for the requested page.
    */
   @SuppressWarnings("WeakerAccess")
   protected String getRequestedPage(HttpServletRequest request) {
      String requested = request.getContextPath() + request.getServletPath();

      if(request.getPathInfo() != null) {
         requested += request.getPathInfo();
      }

      return requested;
   }

   /**
    * Gets the client info for a remote user.
    *
    * @param userID  the user name.
    * @param request the HTTP request object.
    *
    * @return the client info.
    */
   @SuppressWarnings("WeakerAccess")
   protected ClientInfo createClientInfo(IdentityID userID, ServletRequest request) {
      HttpServletRequest httpRequest = (HttpServletRequest) request;
      String remoteAddress = httpRequest.getHeader("X-Forwarded-For");
      remoteAddress = remoteAddress == null ? httpRequest.getRemoteAddr() : remoteAddress;
      return new ClientInfo(userID, remoteAddress,
                            httpRequest.getSession(true).getId(), request.getLocale());
   }

   /**
    * Determines if an HTTP request is for the specified page.
    *
    * @param page    the context-relative path to the page.
    * @param request the HTTP request object.
    *
    * @return {@code true} if the page is being requested; {@code false} otherwise.
    */
   protected boolean isPageRequested(String page, HttpServletRequest request) {
      return pathMatcher.match(request.getContextPath() + page, getRequestedPage(request));
   }

   /**
    * Determines if an HTTP request is for the Enterprise Manager.
    *
    * @param request the HTTP request object.
    *
    * @return {@code true} if the Enterprise Manager is being requested; {@code false}
    *         otherwise.
    */
   protected boolean isEnterpriseManager(HttpServletRequest request) {
      return isPageRequested(emPath, request);
   }

   /**
    * Determines if an HTTP request is for the main application.
    *
    * @param request the HTTP request object.
    *
    * @return {@code true} if the Enterprise Manager is being requested; {@code false}
    *         otherwise.
    */
   protected boolean isApp(HttpServletRequest request) {
      return isPageRequested(appPath, request);
   }

   /**
    * Determines if an HTTP request is for a public resource that does not required authorization.
    *
    * @param request the HTTP request object.
    *
    * @return {@code true} if a public resource is being requested; {@code false} otherwise.
    */
   @SuppressWarnings("BooleanMethodIsAlwaysInverted")
   protected boolean isPublicResource(HttpServletRequest request) {
      return Arrays.stream(publicResources).anyMatch(path -> isPageRequested(path, request));
   }

   /**
    * Determines if an HTTP request is for a public web API endpoint.
    *
    * @param request the HTTP request object.
    *
    * @return {@code true} if a public web API is being requested; {@code false} otherwise.
    */
   protected boolean isPublicApi(HttpServletRequest request) {
      return isPageRequested(publicApiPath, request);
   }

   /**
    * Determines if an HTTP request is for the team websocket endpoint.
    *
    * @param request the HTTP request
    *
    * @return {@code true} if the team websocket endpoint is being requested;
    * {@code false} otherwise.
    */
   protected boolean isTeamWebsocketEndpoint(HttpServletRequest request) {
      return isPageRequested(teamWebsocketEndpointPath, request);
   }

   /**
    * Determines if an HTTP request is for a web API endpoint.
    *
    * @param request the HTTP request object.
    *
    * @return {@code true} if a web API is being requested; {@code false} otherwise.
    */
   @SuppressWarnings("WeakerAccess")
   protected boolean isApi(HttpServletRequest request) {
      return isPageRequested(apiPath, request);
   }

   protected boolean isApiImage(HttpServletRequest request) {
      return isPageRequested(apiImagePath, request);
   }

   protected boolean isApiTableExport(HttpServletRequest request) {
      return isPageRequested(apiTableExportPath, request);
   }

   @Override
   public void sessionAccessed(SessionAccessDispatcher.SessionAccessEvent event) {
      // NO-OP
   }

   /**
    * Gets all groups inherited by the specified groups.
    *
    * @param groups the list of group names.
    *
    * @return the list of all group names, including those specified in <i>groups</i>.
    */
   @SuppressWarnings("unused")
   protected String[] getAllGroups(String[] groups) {
      List<String> result = new ArrayList<>(Arrays.asList(groups));

      try {
         SecurityProvider security = SecurityEngine.getSecurity().getSecurityProvider();
         Set<String> visited = new HashSet<>(result);
         Deque<String> queue = new ArrayDeque<>(result);

         while(!queue.isEmpty()) {
            String groupName = queue.removeFirst();
            IdentityID groupID = new IdentityID(groupName, OrganizationManager.getInstance().getCurrentOrgID());

            for(String parentGroup : security.getGroupParentGroups(groupID)) {
               if(!visited.contains(groupName)) {
                  result.add(groupName);
                  visited.add(groupName);
                  queue.addLast(groupName);
               }
            }
         }
      }
      catch(Exception e) {
         LOG.error("Failed to get inherited groups", e);
      }

      return result.toArray(new String[0]);
   }

   /**
    * Gets all roles inherited by the specified groups and roles.
    *
    * @param groups the list of group names.
    * @param roles  the list of role names.
    *
    * @return the list of all role names, including those specified in <i>roles</i>.
    */
   @SuppressWarnings("unused")
   protected IdentityID[] getAllRoles(String[] groups, IdentityID[] roles) {
      List<IdentityID> result = new ArrayList<>(Arrays.asList(roles));

      try {
         SecurityProvider security = SecurityEngine.getSecurity().getSecurityProvider();
         Set<IdentityID> visited = new HashSet<>(result);

         for(String groupName : groups) {
            IdentityID groupID = new IdentityID(groupName, OrganizationManager.getInstance().getCurrentOrgID());
            Group group = security.getGroup(groupID);

            if(group != null && group.getRoles() != null) {
               for(IdentityID groupRole : group.getRoles()) {
                  if(!visited.contains(groupRole)) {
                     result.add(groupRole);
                     visited.add(groupRole);
                  }
               }
            }
         }

         Deque<IdentityID> queue = new ArrayDeque<>(result);

         while(!queue.isEmpty()) {
            IdentityID roleID = queue.removeFirst();
            Role role = security.getRole(roleID);

            if(role != null && role.getRoles() != null) {
               for(IdentityID roleRole : role.getRoles()) {
                  if(!visited.contains(roleRole)) {
                     result.add(roleRole);
                     visited.add(roleRole);
                     queue.addLast(roleRole);
                  }
               }
            }
         }
      }
      catch(Exception e) {
         LOG.error("Failed to get inherited roles", e);
      }

      return result.toArray(new IdentityID[0]);
   }

   /**
    * Determines if a authorization redirect should be sent for the specified request. If
    * {@code false} is returned, this method will send a 401 (unauthorized) response.
    *
    * @param request  the HTTP request object.
    * @param response the HTTP response object.
    *
    * @return {@code true} if the redirect response should be sent or {@code false} otherwise.
    *
    * @throws IOException if an I/O error occurs while sending the 401 response.
    */
   protected boolean shouldSendAuthenticationRedirect(HttpServletRequest request,
                                                      HttpServletResponse response)
      throws IOException
   {
      if("XMLHttpRequest".equalsIgnoreCase(request.getHeader("X-Requested-With")) ||
         "Upgrade".equalsIgnoreCase(request.getHeader("Connection")) &&
         "websocket".equalsIgnoreCase(request.getHeader("Upgrade")))
      {
         SUtil.sendError(response, HttpServletResponse.SC_UNAUTHORIZED);
         return false;
      }

      return true;
   }

   protected String getLoginOrganization(HttpServletRequest request) {
      String orgID = null;

      if(SUtil.isMultiTenant()) {
         String type = SreeEnv.getProperty("security.login.orgLocation", "domain");

         if("path".equals(type)) {
            URI uri = URI.create(LinkUriArgumentResolver.getLinkUri(request));
            String requestedPath = request.getPathInfo();

            if(requestedPath == null) {
               requestedPath = uri.getRawPath();
            }

            if(requestedPath != null) {
               if(requestedPath.startsWith("/")) {
                  requestedPath = requestedPath.substring(1);
               }

               int index = requestedPath.indexOf('/');

               if(index < 0) {
                  orgID = requestedPath;
               }
               else {
                  orgID = requestedPath.substring(0, index);
               }
            }
         }
         else {
            // get the lowest level subdomain, of the form "http://orgID.somehost.com/"
            String host = LinkUriArgumentResolver.getRequestHost(request);

            if(host != null && !isIpHost(host)) {
               int index = host.indexOf('.');

               if(index >= 0) {
                  orgID = host.substring(0, index);
               }
            }
         }

         if(orgID != null) {
            boolean matched = false;

            for(String org : SecurityEngine.getSecurity().getOrganizations()) {
               if(orgID.equalsIgnoreCase(org)) {
                  matched = true;
                  orgID = org;
               }
            }

            if(!matched) {
               orgID = null;
            }
         }
      }

      return orgID;
   }

   private boolean isIpHost(String host) {
      if(host == null) {
         return false;
      }

      int index = host.lastIndexOf(":");
      String hostName = host;

      if(index > 0) {
         String port = host.substring(index + 1);

         if(!org.apache.commons.lang.StringUtils.isNumeric(port)) {
            return false;
         }

         hostName = host.substring(0, index - 1);
      }


      return InetAddressUtils.isIPv4Address(hostName);
   }

   private boolean isNonlocalClient(SRPrincipal principal) {
      if(principal == null || principal.getUser() == null) {
         return false;
      }

      String ip = principal.getUser().getIPAddress();
      return StringUtils.hasText(ip) && !"localhost".equals(ip) && !"127.0.0.1".equals(ip) &&
         !"0:0:0:0:0:0:0:1".equals(ip) && !"0:0:0:0:0:0:0:0".equals(ip) && !"::1".equals(ip) &&
         !"::".equals(ip);
   }

   private boolean isSameClient(SRPrincipal principal1, SRPrincipal principal2) {
      if(principal1 == null || principal2 == null) {
         return false;
      }

      if(!Objects.equals(principal1.getName(), principal2.getName())) {
         return false;
      }

      if(principal1.getUser() == null || principal2.getUser() == null) {
         return false;
      }

      return Objects.equals(
         principal1.getUser().getIPAddress(), principal2.getUser().getIPAddress());
   }

   boolean isSecurityAllowIframe() {
      return "true".equals(securityAllowIframe.get());
   }

   private final AntPathMatcher pathMatcher = new AntPathMatcher();
   private boolean sessionAccessRegistered = false;

   static final SreeEnv.Value securityAllowIframe =
      new SreeEnv.Value("security.allow.iframe", 10000);
   private static final String emPath = "/em/**"; // NOSONAR not applicable
   private static final String appPath = "/app/**"; // NOSONAR not applicable
   private static final String apiImagePath = "/api/image/**"; // NOSONAR not applicable
   private static final String apiTableExportPath = "/api/table-export/**"; // NOSONAR not applicable
   private static final String apiPath = "/api/**"; // NOSONAR not applicable
   private static final String publicApiPath = "/api/public/**"; // NOSONAR not applicable
   private static final String teamWebsocketEndpointPath = "/reports/**"; // NOSONAR not applicable
   private static final String[] publicResources = {
      "/", "/index.html", "/add-license.html",
      "/error/**",
      "/api/i18n/**",
      "/login.html",
      "/add-license.html",
      "/signup.html",
      "/signupDetail.html",
      "/signup/**",
      "/portal/logo",
      "/portal/favicon",
      "/app/*.js",
      "/app/*.css",
      "/app/*.cur",
      "/app/*.eot",
      "/app/*.ico",
      "/app/*.png",
      "/app/*.svg",
      "/app/*.ttf",
      "/app/*.woff",
      "/app/assets/**",
      "/ping",
      "/css/**",
      "/images/**",
      "/js/**",
      "/webjars/**",
   };
   protected static final String ORG_COOKIE = "X-INETSOFT-ORGID";
   private static final Logger LOG = LoggerFactory.getLogger(AbstractSecurityFilter.class);
}
