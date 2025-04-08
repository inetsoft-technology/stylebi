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

import com.fasterxml.jackson.databind.ObjectMapper;
import inetsoft.sree.RepletRepository;
import inetsoft.sree.SreeEnv;
import inetsoft.sree.internal.SUtil;
import inetsoft.sree.security.*;
import inetsoft.uql.XPrincipal;
import inetsoft.util.Catalog;
import inetsoft.util.Tool;
import inetsoft.web.admin.model.ImmutableNameLabelTuple;
import inetsoft.web.admin.model.NameLabelTuple;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.apache.commons.codec.binary.Base64;
import org.owasp.encoder.Encode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.PrintWriter;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.Principal;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Filter that handles HTTP basic authentication.
 */
public class BasicAuthenticationFilter extends AbstractSecurityFilter {
   private static boolean userCountExceed(Principal user) {
      int max = getOrganizationMaxUser();

      if(max == 0) {
         return false;
      }

      if(user instanceof SRPrincipal) {
         String orgId = ((SRPrincipal) user).getOrgId();
         List<SRPrincipal> users = SecurityEngine.getSecurity().getActivePrincipalList();
         List<String> userNames = new ArrayList<String>();

         for(SRPrincipal principal : users) {
            if(Tool.equals(orgId, principal.getOrgId())) {
               IdentityID pId = IdentityID.getIdentityIDFromKey(principal.getName());

               if(!userNames.contains(pId.name)) {
                  userNames.add(pId.name);
               }

               if(userNames.size() > max) {
                  return true;
               }
            }
         }
      }

      return false;
   }

   private static int getOrganizationMaxUser() {
      int maxUser = 0;
      String userCount = SreeEnv.getProperty("max.user.count", false, true);

      if(userCount != null) {
         maxUser = Integer.parseInt(userCount);
      }

      return maxUser;
   }

   @Override
   public void doFilter(ServletRequest request, ServletResponse response,
                        FilterChain chain) throws IOException, ServletException
   {
      HttpServletRequest httpRequest = (HttpServletRequest) request;
      String header = httpRequest.getHeader("Authorization");
      boolean authorized = true;
      String message = null;
      int status = HttpServletResponse.SC_UNAUTHORIZED;
      List<NameLabelTuple> loginAsUsers = null;
      boolean authenticationFailure = false;
      IdentityID loginUser = null;
      boolean login = false;

      if(header != null && header.toLowerCase().startsWith("basic ") &&
         !isPublicApi(httpRequest))
      {
         login = true;
         Catalog catalog = Catalog.getCatalog();
         authorized = false;
         header = new String(Base64.decodeBase64(header.substring(6)), StandardCharsets.US_ASCII);
         int index = header.indexOf(':');

         if(index >= 0) {
            String userKey = header.substring(0, index);
            String password = header.substring(index + 1);

            try {
               userKey = URLDecoder.decode(userKey, StandardCharsets.UTF_8);
               password = URLDecoder.decode(header.substring(index + 1), StandardCharsets.UTF_8);
            }
            catch(Exception ignore) {
            }

            String loginAsUserKey = httpRequest.getHeader("LoginAsUser");

            try {
               loginAsUserKey = URLDecoder.decode(loginAsUserKey, StandardCharsets.UTF_8);
            }
            catch(Exception ignore) {
            }

            boolean firstLogin = "true".equals(httpRequest.getHeader("FirstLogin"));

            HttpSession session = httpRequest.getSession(false);

            if(session != null) {
               Principal principal = SUtil.getPrincipal(httpRequest);

               IdentityID pId = principal == null ? null : IdentityID.getIdentityIDFromKey(principal.getName());
               loginUser = pId;

               if(pId != null && (pId.name.equals(userKey) &&
                  (loginAsUserKey == null || loginAsUserKey.isEmpty() ||
                     loginAsUserKey.equals(pId.convertToKey()) || loginAsUserKey.equals(pId.name))))
               {
                  authorized = true;
               }
               else if(principal != null) {
                  logout(request);
               }
            }

            if(!authorized) {
               try {
                  SecurityProvider provider = getSecurityProvider();
                  String locale = httpRequest.getHeader("Inetsoft-Locale");
                  boolean loginAs = "on".equals(SreeEnv.getProperty("login.loginAs"))
                     && !provider.isVirtual();
                  Cookie[] cookies = ((HttpServletRequest) request).getCookies();
                  String recordedOrgID = cookies == null ? Organization.getDefaultOrganizationID() :
                     Arrays.stream(cookies).filter(c -> c.getName().equals(ORG_COOKIE))
                        .map(Cookie::getValue).findFirst().orElse(Organization.getDefaultOrganizationID());

                  if(!SUtil.isMultiTenant()) {
                     recordedOrgID = Organization.getDefaultOrganizationID();
                  }

                  //check all equalsIgnoreCase to ensure found organization is correct
                  IdentityID properUserID = new IdentityID(userKey, recordedOrgID);
                  AuthenticationChain authc = SecurityEngine.getSecurity().getAuthenticationChain().orElse(null);
                  String providerName = "";

                  if(authc != null && SecurityEngine.getSecurity().isSecurityEnabled()) {
                     for(AuthenticationProvider p : authc.getProviders()) {
                        if(p.getUser(properUserID) != null) {
                           providerName = p.getProviderName();
                           break;
                        }

                        String originalOrg = properUserID.orgID;

                        for(String sameOrg : p.getOrganizationIDs()) {
                           if(sameOrg.equalsIgnoreCase(originalOrg)) {
                              IdentityID userID = new IdentityID(properUserID.name, sameOrg);

                              if(provider.getUser(userID) != null) {
                                 properUserID = userID;
                                 recordedOrgID = sameOrg;
                                 providerName = p.getProviderName();
                                 break;
                              }
                           }
                        }

                        if(!providerName.isEmpty()) {
                           break;
                        }
                     }
                  }

                  XPrincipal principal = null;

                  //check if user belongs to self org if not provided an organization via redirect
                  if(SecurityEngine.getSecurity().isSelfSignupEnabled() &&
                     recordedOrgID.equalsIgnoreCase(Organization.getDefaultOrganizationID()))
                  {
                     IdentityID selfUser = SUtil.isMultiTenant() ?
                        new IdentityID(userKey, Organization.getSelfOrganizationID()) :
                        new IdentityID(userKey, Organization.getDefaultOrganizationID());
                     loginUser = selfUser;

                     principal =
                        authenticate(request, selfUser, password, null, locale, true);
                  }

                  if(principal == null) {
                     principal =
                        authenticate(request, properUserID, password, null, locale, true);
                     loginUser = properUserID;
                  }

                  IdentityID loginAsUser = IdentityID.getIdentityIDFromKey(loginAsUserKey);
                  boolean isSelfUser = loginAsUser != null &&
                     Tool.equals(loginAsUser.orgID, Organization.getSelfOrganizationID());
                  boolean selfLogin = isSelfUser && SecurityEngine.getSecurity().isSelfSignupEnabled();

                  if(principal != null && (!isSelfUser || selfLogin)) {
                     authorized = true;
                     principal.setProperty("curr_provider_name", providerName);


                     if(provider.checkPermission(
                        principal, ResourceType.LOGIN_AS, "*", ResourceAction.ACCESS) &&
                        loginAs)
                     {
                        if(firstLogin) {
                           message = "showLoginAs";
                           status = HttpServletResponse.SC_OK;
                           authorized = false;
                           loginAsUsers = getLoginAsUsers(principal, provider, recordedOrgID);

                        }
                        else if(loginAsUserKey != null && !loginAsUserKey.trim().isEmpty() &&
                           provider.getUser(loginAsUser) == null)
                        {
                           message = catalog.getString("Invalid login name");
                           authorized = false;
                        }
                        else {
                           if(checkLoginAs(principal, provider, loginAsUser)) {
                              authenticate(
                                 request, new IdentityID(userKey, recordedOrgID), password,
                                 loginAsUser, locale, true);
                           }
                           else {
                              message = catalog.getString("viewer.securityexception");
                              authorized = false;
                           }
                        }
                     }

                     if(userCountExceed(principal)) {
                        logout(request);
                        authorized = false;
                        message = catalog.getString("common.limited.user",
                           getOrganizationMaxUser());
                     }
                  }
                  else {
                     message = catalog.getString("Invalid user name / password pair");
                  }
               }
               catch(AuthenticationFailureException e) {
                  message = Encode.forHtml(e.getMessage());
                  authenticationFailure = true;
                  // don't log, audited
               }
            }
         }
      }

      if(authorized) {
         if(login) {
            LOG.debug("user {} authorized", loginUser == null ? "" : loginUser.getLabel());
         }

         chain.doFilter(request, response);
      }
      else {
         HttpServletResponse httpResponse = (HttpServletResponse) response;
         LOG.debug("user {} Authentication failed: message\"{}\", code: {}", loginUser == null ? "" : loginUser.getLabel(), message, status);
         if(loginAsUsers != null) {
            httpResponse.setStatus(HttpServletResponse.SC_OK);
            httpResponse.setContentType("application/json");

            try(PrintWriter writer = httpResponse.getWriter()) {
               AuthenticationResponse body = AuthenticationResponse.builder()
                  .message(message)
                  .logInAs(true)
                  .users(loginAsUsers)
                  .build();
               new ObjectMapper().writeValue(writer, body);
            }
         }
         else if(!"XMLHttpRequest".equalsIgnoreCase(httpRequest.getHeader("X-Requested-With")))
         {
            httpResponse.setStatus(status);
            httpResponse.setCharacterEncoding("UTF-8");

            if(authenticationFailure) {
               httpResponse.setHeader("WWW-Authenticate", "Basic realm=\"InetSoft\"");
            }

            try(PrintWriter writer = httpResponse.getWriter()) {
               writer.print(Encode.forHtml(message));
            }
         }
         else {
            httpResponse.setStatus(status);
            httpResponse.setContentType("application/json");
            httpResponse.setCharacterEncoding("utf-8");

            try(PrintWriter writer = httpResponse.getWriter()) {
               AuthenticationResponse body = AuthenticationResponse.builder()
                  .message(message)
                  .build();
               new ObjectMapper().writeValue(writer, body);
            }
         }
      }
   }

   private List<NameLabelTuple> getLoginAsUsers(Principal principal, SecurityProvider provider,
                                                String orgId)
   {
      boolean siteAdmin = OrganizationManager.getInstance().isSiteAdmin(principal);

      return Arrays.stream(provider.getUsers())
         .filter(u -> !u.equals(IdentityID.getIdentityIDFromKey(principal.getName())))
         .filter(u -> checkLoginAs(principal, provider, u))
         .map(u -> new NameLabelTuple.Builder()
            .label(siteAdmin ? (provider.getOrgNameFromID(u.getOrgID()) + ":" + u.getName()) : u.getName())
            .name(u.convertToKey())
            .build()
         )
         .sorted(Comparator.comparing(ImmutableNameLabelTuple::label))
         .collect(Collectors.toList());
   }

   private boolean checkLoginAs(Principal principal, SecurityProvider provider, IdentityID user) {
      boolean siteAdmin = OrganizationManager.getInstance().isSiteAdmin(principal);
      String userOrgId = OrganizationManager.getInstance().getUserOrgId(principal);

      if(!siteAdmin && !Tool.equals(userOrgId, user.getOrgID())) {
         return false;
      }

      if(!siteAdmin && OrganizationManager.getInstance().isSiteAdmin(user)) {
         return false;
      }

      return provider.checkPermission(
         principal, ResourceType.SECURITY_USER, user.convertToKey(), ResourceAction.ADMIN);
   }

   private SecurityProvider provider;
   private static final Logger LOG = LoggerFactory.getLogger(BasicAuthenticationFilter.class);
}
