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
package inetsoft.web.admin.security;

import inetsoft.sree.SreeEnv;
import inetsoft.sree.internal.SUtil;
import inetsoft.sree.security.*;
import inetsoft.sree.security.ldap.LdapAuthenticationProvider;
import inetsoft.uql.XPrincipal;
import inetsoft.uql.service.DataSourceRegistry;
import inetsoft.util.*;
import inetsoft.util.audit.ActionRecord;
import inetsoft.web.admin.security.action.ActionPermissionService;
import inetsoft.web.portal.model.CurrentUserModel;
import inetsoft.web.viewsheet.Audited;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.*;

@RestController
public class SecurityConfigController {
   public SecurityConfigController(SecurityEngine securityEngine, ActionPermissionService actionPermissionService) {
      this.securityEngine = securityEngine;
      this.actionPermissionService = actionPermissionService;
   }

   @Audited(
      actionName = ActionRecord.ACTION_NAME_EDIT,
      objectName = "Security-Security",
      objectType = ActionRecord.OBJECT_TYPE_EMPROPERTY
   )
   @PostMapping("/api/em/security/set-enable-security")
   public SecurityEnabledEvent setEnableSecurity(@RequestBody SecurityEnabledEvent event,
                                                 Principal principal)
      throws Exception
   {
      if(event.enable()) {
         securityEngine.enableSecurity();
      }
      else {
         securityEngine.disableSecurity();
      }

      SecurityEngine.touch();
      return getEnableSecurity(principal);
   }

   @PostMapping("/api/em/security/set-multi-tenancy")
   public SecurityEnabledEvent setEnableMultiTenancy(@RequestBody SecurityEnabledEvent event, Principal principal)
   {
      String warning = "";

      if(event.enable()) {
         SUtil.setMultiTenant(true);
      }
      else {
         if(hasAddedOrganizations()) {
            warning = Catalog.getCatalog().getString("em.security.addedOrganizations");
         }
         else {
            SUtil.setMultiTenant(false);
         }
      }

      return SecurityEnabledEvent.builder()
         .enable(getMultiTenancy(principal).enable())
         .warning(warning)
         .build();
   }

   private boolean hasAddedOrganizations() {
      SecurityProvider provider = SecurityEngine.getSecurity().getSecurityProvider();
      for(String orgName : provider.getOrganizations()) {
         if(!orgName.equals(Organization.getDefaultOrganizationName()) &&
            !orgName.equals(Organization.getSelfOrganizationName()) &&
            !orgName.equals(Organization.getTemplateOrganizationName())) {
            return true;
         }
      }
      return false;
   }

   @GetMapping("/api/em/security/get-enable-security")
   public SecurityEnabledEvent getEnableSecurity(Principal principal) {
      return SecurityEnabledEvent.builder()
         .enable(securityEngine.isSecurityEnabled())
         .toggleDisabled(securityEngine.isSecurityEnabled() &&
            !OrganizationManager.getInstance().isSiteAdmin(principal))
         .build();
   }

   @GetMapping("/api/em/security/get-multi-tenancy")
   public SecurityEnabledEvent getMultiTenancy(Principal principal) {
      boolean securityEnabled = securityEngine.isSecurityEnabled();
      boolean siteAdmin = OrganizationManager.getInstance().isSiteAdmin(principal);
      boolean isOrgAdminOnly = securityEnabled && !siteAdmin;
      boolean ldapProviderUsed = false;

      if(securityEnabled && securityEngine.getAuthenticationChain().isPresent()) {
         ldapProviderUsed = securityEngine.getAuthenticationChain().get()
            .stream()
            .anyMatch(p -> p instanceof LdapAuthenticationProvider);
      }

      return SecurityEnabledEvent.builder()
         .enable(SUtil.isMultiTenant())
         .passOrgIdAs(SreeEnv.getProperty("security.login.orgLocation", "domain"))
         .toggleDisabled(securityEnabled && !siteAdmin)
         .ldapProviderUsed(ldapProviderUsed)
         .warning(isOrgAdminOnly ? "isOrgAdmin" : null)
         .build();
   }

   @GetMapping("/api/em/security/get-current-user")
   public CurrentUserModel getCurrentUser(Principal principal) {
      String localeLanguage = null;
      String localeCountry = null;

      if(principal instanceof XPrincipal) {
         String localeName = ((XPrincipal) principal).getProperty(XPrincipal.LOCALE);
         Locale locale = Catalog.parseLocale(localeName);

         if(locale != null) {
            localeLanguage = locale.getLanguage();
            localeCountry = locale.getCountry();
         }
      }
      IdentityID pId = principal == null ? null : IdentityID.getIdentityIDFromKey(principal.getName());

      return CurrentUserModel.builder()
         .anonymous(principal == null || principal.getName().equals(XPrincipal.ANONYMOUS))
         .name(principal == null ? new IdentityID(XPrincipal.ANONYMOUS, OrganizationManager.getCurrentOrgName()) : pId)
         .orgID(principal instanceof XPrincipal ? ((XPrincipal)principal).getOrgId() : OrganizationManager.getInstance().getCurrentOrgID())
         .isSysAdmin(principal == null ? false : OrganizationManager.getInstance().isSiteAdmin(principal))
         .localeLanguage(localeLanguage)
         .localeCountry(localeCountry)
         .build();
   }

   @PostMapping("/api/em/security/set-enable-self-signup")
   public SecurityEnabledEvent setEnableSelfSignup(@RequestBody SecurityEnabledEvent event)
      throws Exception
   {
      if(event.enable()) {
         securityEngine.enableSelfSignup();
      }
      else {
         securityEngine.disableSelfSignup();
      }

      return getEnableSelfSignup();
   }

   @PostMapping("/api/em/security/updatePassOrgIdOption")
   public void updatePassOrgIdOption(@RequestBody String passOption)
      throws Exception
   {
      SreeEnv.setProperty("security.login.orgLocation", passOption);
   }

   @GetMapping("/api/em/security/get-enable-self-signup")
   public SecurityEnabledEvent getEnableSelfSignup() {
      return SecurityEnabledEvent.builder()
         .enable(securityEngine.isSelfSignupEnabled())
         .build();
   }

   private String getDataSourceResourceName(String resourcePath) {
      if(resourcePath.contains("/")) {
         for(String ds : DataSourceRegistry.getRegistry().getDataSourceFullNames()) {
            if(resourcePath.startsWith(ds + "/")) {
               resourcePath = ds + "::" + resourcePath.substring(ds.length() + 1);
               break;
            }
         }
      }

      return resourcePath;
   }

   private SecurityEngine securityEngine;
   private ActionPermissionService actionPermissionService;
   private final Logger LOG = LoggerFactory.getLogger(SecurityConfigController.class);
}
