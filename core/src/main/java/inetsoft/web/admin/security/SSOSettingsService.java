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
package inetsoft.web.admin.security;

import com.onelogin.saml2.settings.Saml2Settings;
import com.onelogin.saml2.settings.SettingsBuilder;
import groovy.lang.GroovyClassLoader;
import inetsoft.sree.SreeEnv;
import inetsoft.sree.internal.SUtil;
import inetsoft.sree.internal.cluster.Cluster;
import inetsoft.sree.security.*;
import inetsoft.util.MessageException;
import inetsoft.util.Tool;
import inetsoft.util.log.LogLevel;
import inetsoft.web.admin.model.NameLabelTuple;
import jakarta.servlet.Filter;
import org.codehaus.groovy.control.CompilationFailedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Modifier;
import java.security.Principal;
import java.util.*;

@Service
public class SSOSettingsService {
   @Autowired
   public SSOSettingsService(SecurityEngine engine, OpenIDConfig openIDConfig,
                             CustomSSOConfig customConfig, SSOFilterPublisher publisher,
                             Cluster cluster)
   {
      this.engine = engine;
      this.openIDConfig = openIDConfig;
      this.customConfig = customConfig;
      this.publisher = publisher;
      this.cluster = cluster;
   }

   /**
    * @return the type of the filter to use as specified in the sree.properties file
    */
   public SSOType getActiveFilterType() {
      final String property = SreeEnv.getProperty("sso.protocol.type");
      return SSOType.forName(property);
   }

   public String getLogoutUrl() {
      return SreeEnv.getProperty("sso.logout.url");
   }

   public String getLogoutPath() {
      return SreeEnv.getProperty("sso.logout.path");
   }

   public boolean isFallbackLogin() {
      return "true".equals(SreeEnv.getProperty("double.sso"));
   }

   public SAMLAttributesModel buildSAMLModel() {
      final Properties sree = SreeEnv.getProperties();
      final Saml2Settings settings = new SettingsBuilder().fromProperties(sree).build();

      try {
         final String spEntityId = settings.getSpEntityId();
         final String assertionUrl = settings.getSpAssertionConsumerServiceUrl() != null ?
            settings.getSpAssertionConsumerServiceUrl().toString() : null;
         final String idpEntityId = settings.getIdpEntityId();
         final String idpLogoutUrl = settings.getIdpSingleLogoutServiceUrl() != null ?
            settings.getIdpSingleLogoutServiceUrl().toString() : null;
         final String idpPublicKey = SreeEnv.getProperty("onelogin.saml2.idp.x509cert");
         final String idpSignOnUrl = settings.getIdpSingleSignOnServiceUrl() != null ?
            settings.getIdpSingleSignOnServiceUrl().toString() : null;

         final String rolesClaim = SreeEnv.getProperty("saml.roles.attribute", "");
         final String groupsClaim = SreeEnv.getProperty("saml.groups.attribute", "");
         final String orgIDClaim = SreeEnv.getProperty("saml.orgID.attribute", "");

         return new SAMLAttributesModel.Builder()
            .spEntityId(spEntityId)
            .assertionUrl(assertionUrl)
            .idpEntityId(idpEntityId)
            .idpLogoutUrl(idpLogoutUrl)
            .idpPublicKey(idpPublicKey)
            .idpSignOnUrl(idpSignOnUrl)
            .roleClaim(rolesClaim)
            .groupClaim(groupsClaim)
            .orgIDClaim(orgIDClaim)
            .build();
      }
      catch(Exception e) {
         LOG.info("SAML attributes not set", e);
      }

      return new SAMLAttributesModel.Builder()
         .spEntityId("")
         .assertionUrl("")
         .idpEntityId("")
         .idpLogoutUrl("")
         .idpPublicKey("")
         .idpSignOnUrl("")
         .build();
   }

   public OpenIdAttributesModel buildOpenIdModel() {
      try {
         return new OpenIdAttributesModel.Builder()
            .secretId(openIDConfig.getSecretId())
            .clientId(openIDConfig.getClientId())
            .clientSecret(openIDConfig.getClientSecret())
            .scopes(openIDConfig.getScopes())
            .issuer(openIDConfig.getIssuer())
            .audience(openIDConfig.getAudienceValue())
            .tokenEndpoint(openIDConfig.getTokenEndpoint())
            .authorizationEndpoint(openIDConfig.getAuthorizationEndpoint())
            .jwksUri(openIDConfig.getJwksUri())
            .jwkCertificate(openIDConfig.getJwkCertificate())
            .nameClaim(openIDConfig.getNameClaim())
            .roleClaim(openIDConfig.getRoleClaim())
            .groupClaim(openIDConfig.getGroupClaim())
            .orgIDClaim(openIDConfig.getOrgIDClaim())
            .build();
      }
      catch(Exception e) {
         LOG.info("OpenID attributes not set", e);
      }

      return new OpenIdAttributesModel.Builder()
         .secretId("")
         .clientId("")
         .clientSecret("")
         .issuer("")
         .tokenEndpoint("")
         .authorizationEndpoint("")
         .build();
   }

   public CustomSSOAttributesModel buildCustomModel() {
      String className = customConfig.getClassName();
      String inlineGroovy = customConfig.getInlineGroovyClass();
      return CustomSSOAttributesModel.builder()
         .useJavaClass(className != null)
         .useInlineGroovy(inlineGroovy != null)
         .javaClassName(className)
         .inlineGroovyClass(inlineGroovy)
         .build();
   }

   /**
    * Get the roles that the principal can assign
    */
   public NameLabelTuple[] getRoles(Principal principal) {
      boolean multiTenant = SUtil.isMultiTenant();
      SecurityProvider securityProvider = engine.getSecurityProvider();

      return Arrays.stream(engine.getRoles())
         .filter(role -> engine.getSecurityProvider().checkPermission(principal,
            ResourceType.SECURITY_ROLE, role.convertToKey(), ResourceAction.ASSIGN))
         .filter(r -> {
            if(multiTenant) {
               return true;
            }

            return r.getOrgID() == null && !securityProvider.isOrgAdministratorRole(r) ||
               Tool.equals(Organization.getDefaultOrganizationID(), r.getOrgID());
         })
         .map(role -> NameLabelTuple.builder()
            .name(role.getName())
            .label(role.getName()).build())
         .toArray(NameLabelTuple[]::new);
   }

   public String[] getSelectedRoles() {
      return Tool.split(SreeEnv.getProperty("sso.default.roles"), ',');
   }

   /**
    * @return {@code true} if the settings were valid and applied; {@code false} if validation
    *         failed and nothing was changed.
    */
   public boolean updateSSOSettings(SSOSettingsModel model) {
      final SSOType ssoType = model.activeFilterType();
      SSOType activeFilterType = getActiveFilterType();

      if(ssoType == SSOType.SAML) {
         final SAMLAttributesModel saml = model.samlAttributesModel();
         assert saml != null;

         if(validateSAMLAttributes(saml)) {
            SreeEnv.setProperty("onelogin.saml2.sp.entityid", saml.spEntityId());
            SreeEnv.setProperty("onelogin.saml2.sp.assertion_consumer_service.url", saml.assertionUrl());
            SreeEnv.setProperty("onelogin.saml2.idp.entityid", saml.idpEntityId());
            SreeEnv.setProperty("onelogin.saml2.idp.single_logout_service.url", saml.idpLogoutUrl());
            SreeEnv.setProperty("onelogin.saml2.idp.x509cert", saml.idpPublicKey());
            SreeEnv.setProperty("onelogin.saml2.idp.single_sign_on_service.url", saml.idpSignOnUrl());
            SreeEnv.setProperty("saml.roles.attribute", saml.roleClaim());
            SreeEnv.setProperty("saml.groups.attribute", saml.groupClaim());
            SreeEnv.setProperty("saml.orgID.attribute", saml.orgIDClaim());
            publisher.changeSSOFilterType(SSOType.SAML);
         }
         else {
            return false;
         }
      }
      else if(ssoType == SSOType.OPENID) {
         final OpenIdAttributesModel openIdAttributesModel = model.openIdAttributesModel();
         assert openIdAttributesModel != null;

         if(!validateOpenIdAttributes(openIdAttributesModel)) {
            return false;
         }

         openIDConfig.setScopes(openIdAttributesModel.scopes());
         // The EM form always submits issuer as "" (never null) unless OIDC Discovery is used, and
         // an empty string persists as-is (PropertiesEngine only treats a literal null as "remove").
         // OpenIDFilterBaseFilter treats a non-null issuer as "set" and requires an exact JWT claim
         // match, so a persisted "" would fail every login. Normalize blank to null so a
         // non-Discovery config stays "issuer not set" at runtime, matching the optional contract.
         openIDConfig.setIssuer(Tool.isEmptyString(openIdAttributesModel.issuer()) ? null : openIdAttributesModel.issuer());
         openIDConfig.setAudience(openIdAttributesModel.audience());
         openIDConfig.setAuthorizationEndpoint(openIdAttributesModel.authorizationEndpoint());
         openIDConfig.setTokenEndpoint(openIdAttributesModel.tokenEndpoint());
         openIDConfig.setJwksUri(openIdAttributesModel.jwksUri());
         openIDConfig.setJwkCertificate(openIdAttributesModel.jwkCertificate());
         openIDConfig.setNameClaim(openIdAttributesModel.nameClaim());
         openIDConfig.setRoleClaim(openIdAttributesModel.roleClaim());
         openIDConfig.setGroupClaim(openIdAttributesModel.groupClaim());
         openIDConfig.setOrgIDClaim(openIdAttributesModel.orgIDClaim());

         if(Tool.isCloudSecrets()) {
            openIDConfig.setSecretId(openIdAttributesModel.secretId());
         }
         else {
            openIDConfig.setClientId(openIdAttributesModel.clientId());
            openIDConfig.setClientSecret(openIdAttributesModel.clientSecret());
         }

         publisher.changeSSOFilterType(SSOType.OPENID);
      }
      else if(ssoType == SSOType.CUSTOM) {
         CustomSSOAttributesModel customModel = model.customAttributesModel();
         assert customModel != null;

         if(!validateCustomAttributes(customModel)) {
            return false;
         }

         customConfig.setClassName(customModel.useJavaClass() ? customModel.javaClassName() : null);
         customConfig.setInlineGroovyClass(
            customModel.useInlineGroovy() ? customModel.inlineGroovyClass() : null);
         publisher.changeSSOFilterType(SSOType.CUSTOM);
      }
      else if(ssoType == SSOType.NONE) {
         publisher.changeSSOFilterType(SSOType.NONE);
      }

      SreeEnv.setProperty("sso.protocol.type", ssoType.getName());
      SreeEnv.setProperty("sso.logout.url", model.logoutUrl());
      SreeEnv.setProperty("sso.logout.path", model.logoutPath());
      SreeEnv.setProperty("double.sso", Boolean.toString(model.fallbackLogin()));

      if(!SUtil.isMultiTenant()) {
         SreeEnv.setProperty("sso.default.roles", Tool.arrayToString(model.selectedRoles()));
      }

      try {
         SreeEnv.save();

         if(ssoType != activeFilterType) {
            try {
               cluster.sendMessage(new SSOTypeChangedMessage(ssoType, activeFilterType));
            }
            catch(Exception ex) {
               LOG.debug("Failed to send sso type changed message", ex);
            }
         }
      }
      catch(IOException e) {
         LOG.error("Failed to save properties", e);
         publisher.changeSSOFilterType(SSOType.NONE);
         SreeEnv.setProperty("sso.protocol.type", ssoType.getName());
      }

      return true;
   }

   /**
    * Check whether the SAML attributes are valid before applying changes so we don't get locked out
    */
   private boolean validateSAMLAttributes(SAMLAttributesModel model) {
      final HashMap<String, Object> settingsMap = new HashMap<>();
      settingsMap.put("onelogin.saml2.sp.assertion_consumer_service.url", model.assertionUrl());
      settingsMap.put("onelogin.saml2.idp.entityid", model.idpEntityId());
      settingsMap.put("onelogin.saml2.idp.single_sign_on_service.url", model.idpSignOnUrl());
      settingsMap.put("onelogin.saml2.sp.entityid", model.spEntityId());
      settingsMap.put("onelogin.saml2.idp.x509cert", model.idpPublicKey());
      settingsMap.put("onelogin.saml2.idp.single_logout_service.url", model.idpLogoutUrl());
      //the saml.*.attribute claim names are StyleBI's own and are not part of the java-saml
      //settings vocabulary, so they cannot be checked here -- fromValues drops keys it does
      //not recognise. Nothing validates a claim name anywhere; a wrong one saves cleanly and
      //silently maps nothing at login (see SAMLFilter.ClaimNames).
      final Saml2Settings settings = new SettingsBuilder().fromValues(settingsMap).build();
      final List<String> errors = settings.checkSettings();
      final boolean valid = errors.isEmpty();

      if(!valid) {
         final String allErrors = String.join(", ", errors);
         LOG.error("Invalid SAML properties: {}", allErrors);
      }

      return valid;
   }

   /**
    * Check whether the OpenID attributes are valid before applying changes so we don't get
    * locked out. The issuer is intentionally not required here: it has no manual input in the EM
    * UI (it is only ever populated via the OIDC Discovery helper), and OpenIDFilterBaseFilter
    * treats it as optional at runtime (only adds an extra claim check when it is set), so
    * requiring it here would reject legitimate configurations that don't use Discovery.
    */
   private boolean validateOpenIdAttributes(OpenIdAttributesModel model) {
      final List<String> missing = new ArrayList<>();

      if(Tool.isEmptyString(model.authorizationEndpoint())) {
         missing.add("authorization endpoint");
      }

      if(Tool.isEmptyString(model.tokenEndpoint())) {
         missing.add("token endpoint");
      }

      if(Tool.isCloudSecrets()) {
         if(Tool.isEmptyString(model.secretId())) {
            missing.add("secret");
         }
      }
      else if(Tool.isEmptyString(model.clientId())) {
         missing.add("client id");
      }

      if(!missing.isEmpty()) {
         LOG.error("Invalid OpenID properties, missing: {}", String.join(", ", missing));
      }

      return missing.isEmpty();
   }

   /**
    * Check whether the Custom SSO attributes are valid before applying changes so we don't get
    * locked out.
    *
    * @throws MessageException if an inline Groovy script does not compile or is not a usable
    *                          servlet filter.
    */
   private boolean validateCustomAttributes(CustomSSOAttributesModel model) {
      boolean valid = false;

      if(model.useJavaClass() && !Tool.isEmptyString(model.javaClassName())) {
         valid = true;
      }

      // checked independently of the Java class branch: the inline script wins when the
      // configuration is stored, so a broken script must not hide behind a valid class name
      if(model.useInlineGroovy() && !Tool.isEmptyString(model.inlineGroovyClass())) {
         validateInlineGroovy(model.inlineGroovyClass());
         valid = true;
      }

      if(!valid) {
         LOG.error("Invalid Custom SSO properties: a Java class name or inline Groovy script " +
                   "is required");
      }

      return valid;
   }

   /**
    * Compiles the inline Groovy script the same way the custom SSO filter does when it is
    * initialized (a plain {@code GroovyClassLoader}, the first class in the script) and checks that
    * the result can be used as a filter. The class is parsed but never instantiated, so no
    * constructor or static initializer of the script runs. This only checks on the saving node;
    * the other nodes compile the script when the property change reaches them.
    */
   private void validateInlineGroovy(String source) {
      String problem;

      try(GroovyClassLoader loader = new GroovyClassLoader()) {
         Class<?> cls = loader.parseClass(source);
         problem = checkFilterShape(cls);
      }
      catch(CompilationFailedException e) {
         problem = "the script does not compile: " + firstErrorLine(e.getMessage());
      }
      catch(LinkageError e) {
         problem = "the script does not compile: " + firstErrorLine(e.toString());
      }
      catch(Exception e) {
         problem = "the script could not be loaded: " + firstErrorLine(e.toString());
      }

      if(problem != null) {
         LOG.warn("Invalid Custom SSO inline Groovy script: {}", problem);
         throw new MessageException("Invalid Custom SSO inline Groovy script: " + problem,
                                    LogLevel.WARN, false);
      }
   }

   private String checkFilterShape(Class<?> cls) {
      if(!Filter.class.isAssignableFrom(cls) || cls.isInterface()) {
         return "the first class in the script (" + cls.getSimpleName() +
            ") must implement jakarta.servlet.Filter";
      }

      if(Modifier.isAbstract(cls.getModifiers())) {
         return "the filter class " + cls.getSimpleName() + " must not be abstract";
      }

      try {
         cls.getConstructor();
      }
      catch(NoSuchMethodException | LinkageError e) {
         return "the filter class " + cls.getSimpleName() +
            " must have a public constructor without arguments";
      }

      return null;
   }

   private static String firstErrorLine(String message) {
      if(message == null) {
         return "unknown error";
      }

      for(String line : message.split("\\R")) {
         line = line.trim();

         if(line.isEmpty() || line.startsWith("startup failed:")) {
            continue;
         }

         // drop the generated "Script_<hash>.groovy: " prefix
         return line.replaceFirst("^Script_[0-9a-f]+\\.groovy: ?", "");
      }

      return "unknown error";
   }

   private static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
   private final SecurityEngine engine;
   private final OpenIDConfig openIDConfig;
   private final CustomSSOConfig customConfig;
   private final SSOFilterPublisher publisher;
   private final Cluster cluster;
}