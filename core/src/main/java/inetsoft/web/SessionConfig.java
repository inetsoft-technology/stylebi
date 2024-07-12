/*
 * inetsoft-core - StyleBI is a business intelligence web application.
 * Copyright © 2024 InetSoft Technology (info@inetsoft.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package inetsoft.web;

import inetsoft.sree.SreeEnv;
import inetsoft.sree.security.AuthenticationService;
import inetsoft.sree.security.SecurityEngine;
import inetsoft.web.admin.security.SSOSettingsService;
import inetsoft.web.admin.security.SSOType;
import inetsoft.web.security.SessionAccessFilter;
import jakarta.servlet.ServletContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.session.config.annotation.web.http.EnableSpringHttpSession;
import org.springframework.session.web.http.CookieSerializer;
import org.springframework.session.web.http.DefaultCookieSerializer;

/**
 * Class that is responsible for configuring the Spring session management.
 */
@EnableSpringHttpSession
@Configuration
public class SessionConfig {
   @SuppressWarnings("SpringJavaInjectionPointsAutowiringInspection")
   @Autowired
   public SessionConfig(ServletContext servletContext,
                        SecurityEngine securityEngine,
                        AuthenticationService authenticationService,
                        SSOSettingsService ssoSettingsService)
   {
      this.servletContext = servletContext;
      this.securityEngine = securityEngine;
      this.authenticationService = authenticationService;
      this.ssoSettingsService = ssoSettingsService;
   }

   /**
    * Cookie serializer that adds the SameSite and Secure attributes to cookies
    */
   @Bean
   public CookieSerializer cookieSerializer() {
      return new DynamicCookieSerializer();
   }

   @Bean
   public MapSessionRepository mapSessionRepository() {
      return new MapSessionRepository(servletContext, securityEngine, authenticationService);
   }

   @Bean
   public SessionAccessFilter sessionAccessFilter() {
      return new SessionAccessFilter();
   }

   /**
    * This class is used over the {@link DefaultCookieSerializer} so that changing the cookie properties immediately
    * changes the behavior without requiring a server restart.
    */
   private class DynamicCookieSerializer extends DefaultCookieSerializer {
      @Override
      public void writeCookieValue(CookieValue cookieValue) {
         updateCookieProperties();
         super.writeCookieValue(cookieValue);
      }

      private void updateCookieProperties() {
         final String sameSite = SreeEnv.getProperty("same.site", "Lax");

         if(SreeEnv.getBooleanProperty("security.allow.iframe") || "none".equalsIgnoreCase(sameSite) ||
            SSOType.SAML.equals(ssoSettingsService.getActiveFilterType()))
         {
            setSameSite("None");
            setUseSecureCookie(true);
         }
         else {
            setSameSite(sameSite);
            setUseSecureCookie(SreeEnv.getBooleanProperty("secure.cookie"));
         }
      }
   }

   private final ServletContext servletContext;
   private final SecurityEngine securityEngine;
   private final AuthenticationService authenticationService;
   private final SSOSettingsService ssoSettingsService;
}
