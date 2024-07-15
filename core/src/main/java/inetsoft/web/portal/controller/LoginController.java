/*
 * inetsoft-core - StyleBI is a business intelligence web application.
 * Copyright © 2024 InetSoft Technology (info@inetsoft.com)
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
 * You should have received a copy of the GNU Affrero General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package inetsoft.web.portal.controller;

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
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.ModelAndView;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
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
      PortalWelcomePage welcomePage = manager.getWelcomePage();
      boolean customLogo = PortalThemesManager.CUSTOM_LOGO.equals(manager.getLogoStyle());

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
         model.addObject("gClientId", SreeEnv.getProperty("styleBI.google.openid.client.id"));
         String encodeUrl = requestedUrl == null ? null :
            URLEncoder.encode(requestedUrl, "UTF-8");
         model.addObject("gLoginUri", linkUri +
            "login/googleSSO?requestedUrl=" + encodeUrl);
      }

      String header = CacheControl.noCache()
         .cachePrivate()
         .mustRevalidate()
         .getHeaderValue();
      response.setHeader(HttpHeaders.CACHE_CONTROL, header);
      return model;
   }

   private boolean isNotTenantServer(HttpServletRequest request) {
      Cookie[] cookies = request.getCookies();

      if(cookies == null) {
         return true;
      }

      String recordedOrgID = Arrays.stream(cookies).filter(c -> c.getName().equals(ORG_COOKIE))
         .map(Cookie::getValue).findFirst().orElse(null);
      String recordedOrgName = recordedOrgID == null ? null : SecurityEngine.getSecurity().getSecurityProvider().getOrgNameFromID(recordedOrgID);

      return recordedOrgName == null  || Tool.equals(recordedOrgName, Organization.getDefaultOrganizationName());
   }

   private static final String ORG_COOKIE = "X-INETSOFT-ORGID";
}
