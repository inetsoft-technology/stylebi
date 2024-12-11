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
package inetsoft.web.portal.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import inetsoft.report.internal.license.LicenseManager;
import inetsoft.sree.RepletRepository;
import inetsoft.sree.SreeEnv;
import inetsoft.sree.internal.SUtil;
import inetsoft.sree.portal.*;
import inetsoft.sree.security.*;
import inetsoft.util.Tool;
import inetsoft.web.portal.model.LocaleModel;
import inetsoft.web.portal.model.LoginBannerModel;
import inetsoft.web.viewsheet.service.LinkUri;
import jakarta.servlet.http.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.ModelAndView;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.Principal;
import java.util.*;

@Controller
public class LoginController {
   /**
    * Shows the login page.
    *
    * @return the login page model and view.
    */
   @GetMapping("/login.html")
   public ModelAndView showLoginPage(
      @RequestParam(name = "requestedUrl", required = false) String requestedUrl,
      HttpServletRequest request, HttpServletResponse response, @LinkUri String linkUri)
      throws UnsupportedEncodingException
   {
      ModelAndView model = new ModelAndView("login");
      model.addObject("requestedUrl", requestedUrl);

      PortalThemesManager manager = PortalThemesManager.getManager();
      CustomThemesManager themes = CustomThemesManager.getManager();
      String recordedOrgID = getRecordedOrgId(request);
      recordedOrgID = recordedOrgID == null ? Organization.getDefaultOrganizationID() : recordedOrgID;
      PortalWelcomePage welcomePage = manager.getWelcomePage();

      if(SUtil.isMultiTenant() && recordedOrgID != null &&
         manager.getWelcomePage(recordedOrgID) != null)
      {
         welcomePage = manager.getWelcomePage(recordedOrgID);
      }

      boolean customLogo = manager.hasCustomLogo(OrganizationManager.getInstance().getCurrentOrgID());

      model.addObject("customLogo", customLogo);
      model.addObject("linkUri", linkUri);
      // Bug #53246, the thread context principal may still be set after a log out when rendering
      // this page, but the subsequent requests will be unauthenticated, so if the current user has
      // a theme assigned, it will end up trying to load theme-variables.css from the "default"
      // theme, which doesn't exist. To avoid this, just check if the global theme is custom.
      model.addObject("customTheme", !"default".equals(themes.getSelectedTheme()));

      if(welcomePage != null) {
         LoginBannerModel loginBanner = new LoginBannerModel();
         loginBanner.setType(welcomePage.getBannerType());
         loginBanner.setBannerText(welcomePage.getBanner());
         model.addObject("loginBanner", loginBanner);
      }

      HttpSession session = request.getSession(false);
      Principal principal = session != null
         ? (Principal) session.getAttribute(RepletRepository.PRINCIPAL_COOKIE) : null;
      String userName = principal != null ? principal.getName() : "";
      userName = userName != null ? IdentityID.getIdentityIDFromKey(userName).getName() : "";
      model.addObject("currentUser",  userName);

      String error =  null;

      if(session != null) {
         error = (String) session.getAttribute(LoginController.LOGIN_ONLOAD_ERROR);
         session.removeAttribute(LoginController.LOGIN_ONLOAD_ERROR);
      }

      String locales = SreeEnv.getProperty("locale.available");
      List<LocaleModel> localeModels = new ArrayList<>();

      if(locales != null && !locales.isEmpty()) {
         Properties localeProperties = SUtil.loadLocaleProperties();

         for(String localeValue : Tool.split(locales, ':')) {
            String localeLabel = localeProperties.getProperty(localeValue, localeValue);
            localeModels.add(LocaleModel.builder()
                                .value(localeValue)
                                .label(localeLabel)
                                .build());
         }

      }

      SecurityEngine security = SecurityEngine.getSecurity();
      model.addObject("locales", localeModels);

      model.addObject("loginAs", "on".equals(SreeEnv.getProperty("login.loginAs")));
      model.addObject("selfSignUpEnabled",
                      security.isSecurityEnabled() && security.isSelfSignupEnabled() &&
                      LicenseManager.getInstance().isEnterprise());
      model.addObject("isNotTenantServer", isNotTenantServer(request));

      boolean googleSignInEnabled = SreeEnv.getBooleanProperty("security.googleSignIn.enabled");

      if(googleSignInEnabled) {
         model.addObject("gClientId", getGoogleClientId());
         model.addObject("gLoginUri", linkUri + "login/googleSSO");
         model.addObject("gScopes", getGoogleScopes());

         try {
            String encodedUrl = requestedUrl == null ? null :
               URLEncoder.encode(requestedUrl, StandardCharsets.UTF_8);

            if(!Tool.isEmptyString(encodedUrl)) {
               Map<String, String> stateMap = new HashMap<>();
               stateMap.put("requestedUrl", linkUri + "login/googleSSO?requestedUrl=" + encodedUrl);
               String stateJson = new ObjectMapper().writeValueAsString(stateMap);
               String encodedState = Base64.getEncoder().encodeToString(stateJson.getBytes());
               model.addObject("gState", encodedState);
            }
         }
         catch(JsonProcessingException e) {
            LOG.warn("Failed to generate google oauth state attribute", e);
         }
      }


      if(!Tool.isEmptyString(error)) {
         model.addObject("onloadError", error);
      }

      String header = CacheControl.noCache()
         .cachePrivate()
         .mustRevalidate()
         .getHeaderValue();
      response.setHeader(HttpHeaders.CACHE_CONTROL, header);
      return model;
   }

   private boolean isNotTenantServer(HttpServletRequest request) {
      String recordedOrgID = getRecordedOrgId(request);
      String recordedOrgName = recordedOrgID == null ? null : SecurityEngine.getSecurity().getSecurityProvider().getOrgNameFromID(recordedOrgID);

      return recordedOrgName == null  || Tool.equals(recordedOrgName, Organization.getDefaultOrganizationName());
   }

   private String getRecordedOrgId(HttpServletRequest request) {
      Cookie[] cookies = request.getCookies();

      if(cookies == null) {
         return null;
      }

      return Arrays.stream(cookies).filter(c -> c.getName().equals(ORG_COOKIE))
         .map(Cookie::getValue).findFirst().orElse(null);
   }

   private String getGoogleClientId() {
      return Tool.getClientSecretRealValue(
         SreeEnv.getProperty("styleBI.google.openid.client.id"), "client_id");
   }

   private String getGoogleScopes() {
      return SreeEnv.getProperty("styleBI.google.openid.scopes", "openid email profile");
   }

   private static final String ORG_COOKIE = "X-INETSOFT-ORGID";
   public static final String LOGIN_ONLOAD_ERROR = "LOGIN_ONLOAD_ERROR";
   private static final Logger LOG = LoggerFactory.getLogger(LoginController.class);
}
