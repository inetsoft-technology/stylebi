/*
 * This file is part of StyleBI.
 * Copyright (C) 2026  InetSoft Technology
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
package inetsoft.web.portal.data;

import inetsoft.report.internal.license.LicenseManager;
import inetsoft.sree.SreeEnv;
import inetsoft.sree.security.*;
import inetsoft.uql.XPrincipal;
import inetsoft.uql.service.DataSourceRegistry;
import inetsoft.uql.tabular.*;
import inetsoft.uql.util.Config;
import inetsoft.util.AbstractSecretsManager;
import inetsoft.util.Catalog;
import inetsoft.util.CoreTool;
import inetsoft.util.ThreadContext;
import inetsoft.util.Tool;
import inetsoft.util.UserMessage;
import inetsoft.util.credential.*;
import inetsoft.web.composer.model.ws.TabularOAuthParams;
import org.junit.jupiter.api.*;
import org.mockito.MockedStatic;
import org.w3c.dom.Element;

import java.security.Principal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Bug #77141: the draft data source endpoints (refresh-view, oauth-params, oauth-tokens) build a
 * data source from a client-supplied definition. A secret id named by that definition may only be
 * resolved from the secrets manager if the caller can write a saved data source that already
 * references it, and OAuth parameters may only be read through the names declared by the data
 * source's own OAuth buttons.
 */
@Tag("core")
class DatasourcesServiceDraftSecretTest {
   @BeforeEach
   void setUp() throws Exception {
      sreeEnv = mockStatic(SreeEnv.class);
      sreeEnv.when(() -> SreeEnv.getProperty("license.key")).thenReturn("TEST-KEY");
      licenseManager = mockStatic(LicenseManager.class);
      licenseManager.when(LicenseManager::isEnterprise).thenReturn(true);

      secretsManager = mock(AbstractSecretsManager.class);
      when(secretsManager.getCredential(any())).thenAnswer(inv -> {
         CloudClientCredentials stored = new CloudClientCredentials();
         stored.setClientId(STORED_CLIENT_ID);
         stored.setClientSecret(STORED_SECRET);
         return stored;
      });
      SECRETS.set(secretsManager);

      Config config = mock(Config.class);
      when(config.getDataSourceClass(CLOUD)).thenReturn(CloudTestDataSource.class.getName());
      doReturn(CloudTestDataSource.class)
         .when(config).getClass(CLOUD, CloudTestDataSource.class.getName());
      when(config.getDataSourceClass(LOCAL)).thenReturn(LocalTestDataSource.class.getName());
      doReturn(LocalTestDataSource.class)
         .when(config).getClass(LOCAL, LocalTestDataSource.class.getName());
      uqlConfig = mockStatic(Config.class);
      uqlConfig.when(Config::getConfig).thenReturn(config);

      securityEngine = mock(SecurityEngine.class);
      registry = mock(DataSourceRegistry.class);
      when(registry.getDataSourceFullNames()).thenReturn(new String[0]);
      principal = mock(Principal.class);
      ThreadContext.setContextPrincipal(principal);
      CoreTool.clearUserMessage();

      service = new DatasourcesService(null, securityEngine, null, registry, config);
   }

   @AfterEach
   void tearDown() {
      ThreadContext.setContextPrincipal(null);
      CoreTool.clearUserMessage();
      SECRETS.remove();
      uqlConfig.close();
      sreeEnv.close();
      licenseManager.close();
   }

   @Test
   void refreshViewDoesNotResolveSecretIdTheCallerCannotManage() throws Exception {
      // a data source the caller cannot write references the secret id
      when(registry.getDataSourceFullNames()).thenReturn(new String[] { "other" });
      when(registry.getDataSource("other")).thenReturn(savedSource(SECRET_ID));
      grantWrite("other", false);

      DataSourceDefinition result = service.refreshTabularView(draft(CLOUD, SECRET_ID));

      verify(secretsManager, never()).getCredential(any());
      assertNull(editorValue(result.getTabularView(), "testClientSecret"));
      assertNull(editorValue(result.getTabularView(), "testClientId"));
      assertEquals(SECRET_ID, editorValue(result.getTabularView(), "credentialId"));
   }

   @Test
   void refreshViewDoesNotResolveSecretIdThroughVisiblePasswordView() throws Exception {
      // the client makes the password view visible and places it before the secret id
      TabularView root = new TabularView();
      root.addTabularView(view("useCredentialId", Boolean.TRUE, true));
      root.addTabularView(view("testClientSecret", null, true));
      root.addTabularView(view("credentialId", SECRET_ID, true));
      DataSourceDefinition definition = definition(CLOUD, root);

      DataSourceDefinition result = service.refreshTabularView(definition);

      verify(secretsManager, never()).getCredential(any());
      assertNull(editorValue(result.getTabularView(), "testClientSecret"));
   }

   @Test
   void oauthParamsDoNotResolveSecretIdTheCallerCannotManage() throws Exception {
      TabularOAuthParams params = service.getOAuthParams(oauthRequest(CLOUD, "clientSecret"));

      verify(secretsManager, never()).getCredential(any());
      assertNull(params.clientSecret());
      assertNull(params.clientId());
   }

   @Test
   void oauthTokensDoNotEchoSecretIdTheCallerCannotManage() throws Exception {
      DataSourceOAuthTokens tokens = DataSourceOAuthTokens.builder()
         .accessToken("NEW-ACCESS-TOKEN")
         .refreshToken("NEW-REFRESH-TOKEN")
         .method("updateTokens")
         .dataSource(draft(CLOUD, SECRET_ID))
         .build();

      DataSourceDefinition result = service.setOAuthTokens(tokens);

      verify(secretsManager, never()).getCredential(any());
      assertNull(editorValue(result.getTabularView(), "testClientSecret"));
      assertEquals("NEW-ACCESS-TOKEN", editorValue(result.getTabularView(), "accessToken"));
   }

   @Test
   void refreshViewResolvesSecretIdStoredOnWritableDataSource() throws Exception {
      when(registry.getDataSourceFullNames()).thenReturn(new String[] { "folder/other" });
      when(registry.getDataSource("folder/other")).thenReturn(savedSource(SECRET_ID));
      grantWrite("folder/other", true);

      DataSourceDefinition result = service.refreshTabularView(draft(CLOUD, SECRET_ID));

      verify(secretsManager).getCredential(any());
      assertEquals(STORED_SECRET, editorValue(result.getTabularView(), "testClientSecret"));
   }

   @Test
   void refreshViewResolvesSecretIdOfOwnSavedDataSourceWithoutScanning() throws Exception {
      DataSourceDefinition definition = draft(CLOUD, SECRET_ID);
      definition.setParentPath("folder");
      definition.setOldName("saved");
      definition.setName("renamed");
      when(registry.getDataSource("folder/saved")).thenReturn(savedSource(SECRET_ID));
      grantWrite("folder/saved", true);

      DataSourceDefinition result = service.refreshTabularView(definition);

      assertEquals(STORED_SECRET, editorValue(result.getTabularView(), "testClientSecret"));
      verify(registry, never()).getDataSourceFullNames();
   }

   @Test
   void refreshViewDoesNotResolveSecretIdOfOwnSavedDataSourceWithoutWrite() throws Exception {
      DataSourceDefinition definition = draft(CLOUD, SECRET_ID);
      definition.setName("saved");
      when(registry.getDataSource("saved")).thenReturn(savedSource(SECRET_ID));
      grantWrite("saved", false);

      DataSourceDefinition result = service.refreshTabularView(definition);

      verify(secretsManager, never()).getCredential(any());
      assertNull(editorValue(result.getTabularView(), "testClientSecret"));
   }

   @Test
   void refreshViewDoesNotResolveDifferentSecretIdThanTheWritableDataSourceStores() throws Exception {
      when(registry.getDataSourceFullNames()).thenReturn(new String[] { "other" });
      when(registry.getDataSource("other")).thenReturn(savedSource("another-secret"));
      grantWrite("other", true);

      DataSourceDefinition result = service.refreshTabularView(draft(CLOUD, SECRET_ID));

      verify(secretsManager, never()).getCredential(any());
      assertNull(editorValue(result.getTabularView(), "testClientSecret"));
   }

   @Test
   void refreshViewDoesNotResolveSecretIdWithoutPrincipal() throws Exception {
      ThreadContext.setContextPrincipal(null);
      when(registry.getDataSourceFullNames()).thenReturn(new String[] { "other" });
      when(registry.getDataSource("other")).thenReturn(savedSource(SECRET_ID));
      grantWrite("other", true);

      service.refreshTabularView(draft(CLOUD, SECRET_ID));

      verify(secretsManager, never()).getCredential(any());
   }

   @Test
   void oauthParamsResolveSecretIdStoredOnWritableDataSource() throws Exception {
      when(registry.getDataSourceFullNames()).thenReturn(new String[] { "other" });
      when(registry.getDataSource("other")).thenReturn(savedSource(SECRET_ID));
      grantWrite("other", true);

      TabularOAuthParams params = service.getOAuthParams(oauthRequest(CLOUD, "clientSecret"));

      // getClientId() and getClientSecret() are not @Property getters, they are only read through
      // the names declared by the OAuth button
      assertNull(params.error());
      assertEquals(STORED_SECRET, params.clientSecret());
      assertEquals(STORED_CLIENT_ID, params.clientId());
   }

   @Test
   void oauthParamsRejectNamesNotDeclaredByAnOAuthButton() throws Exception {
      when(registry.getDataSourceFullNames()).thenReturn(new String[] { "other" });
      when(registry.getDataSource("other")).thenReturn(savedSource(SECRET_ID));
      grantWrite("other", true);

      TabularOAuthParams params = service.getOAuthParams(oauthRequest(CLOUD, "internalValue"));

      assertNull(params.clientSecret());
      assertNull(params.clientId());
   }

   @Test
   void oauthParamsReadClientSuppliedValuesThroughOverriddenGetters() throws Exception {
      TabularOAuthParams params = service.getOAuthParams(oauthRequest(LOCAL, "clientSecret"));

      assertEquals("client-secret", params.clientSecret());
      assertEquals("client-id", params.clientId());
      assertEquals(TOKEN_URI, params.tokenUri());
      verify(secretsManager, never()).getCredential(any());
   }

   @Test
   void oauthParameterNamesWithoutUserAndPasswordAreAccepted() {
      // the composer tabular query dialog does not send the user and password names
      LocalTestDataSource bean = new LocalTestDataSource();
      bean.setClientId("client-id");
      bean.setClientSecret("client-secret");

      assertNotNull(TabularUtil.getOAuthParameters(
         null, null, "clientId", "clientSecret", "scope", "authorizationUri", "tokenUri",
         "oauthFlags", bean));
      assertNull(TabularUtil.getOAuthParameters(
         null, null, "clientId", "internalValue", "scope", "authorizationUri", "tokenUri",
         "oauthFlags", bean));
   }

   @Test
   void localModeRefreshViewIsUnaffected() throws Exception {
      TabularView root = new TabularView();
      root.addTabularView(view("useCredentialId", Boolean.TRUE, true));
      root.addTabularView(view("credentialId", SECRET_ID, true));
      root.addTabularView(view("testClientId", "client-id", true));
      root.addTabularView(view("testClientSecret", "client-secret", true));

      DataSourceDefinition result = service.refreshTabularView(definition(LOCAL, root));

      verify(secretsManager, never()).getCredential(any());
      verifyNoInteractions(securityEngine);
      assertEquals("client-secret", editorValue(result.getTabularView(), "testClientSecret"));
      assertEquals("client-id", editorValue(result.getTabularView(), "testClientId"));
   }

   @Test
   void savedDataSourceLoadingStillResolvesSecretId() throws Exception {
      // setting the secret id outside of the draft endpoints still fetches it
      CloudTestDataSource source = new CloudTestDataSource();
      source.setUseCredentialId(true);
      source.setCredentialId(SECRET_ID);
      assertEquals(STORED_SECRET, source.getClientSecret());

      // parsing a saved credential fetches it even while a draft is being refreshed
      Element element = Tool.parseXML(new java.io.StringReader(
         "<credential id=\"" + SECRET_ID + "\" dbType=\"\"/>")).getDocumentElement();
      TestCloudCredential parsed = new TestCloudCredential();
      TabularDataSource.withCredentialFetchGate(id -> false, () -> {
         try {
            parsed.parseXML(element);
         }
         catch(Exception e) {
            throw new RuntimeException(e);
         }

         return null;
      });
      assertEquals(STORED_SECRET, parsed.getClientSecret());

      // the gate does not outlive the draft refresh
      CloudTestDataSource after = new CloudTestDataSource();
      after.setUseCredentialId(true);
      after.setCredentialId(SECRET_ID);
      assertEquals(STORED_SECRET, after.getClientSecret());
   }

   @Test
   void gateIsClearedWhenTheGatedActionThrows() {
      assertThrows(IllegalStateException.class, () -> TabularDataSource.withCredentialFetchGate(
         id -> false, () -> {
            throw new IllegalStateException("refresh failed");
         }));

      // a later request on the same thread is not gated
      CloudTestDataSource after = new CloudTestDataSource();
      after.setUseCredentialId(true);
      after.setCredentialId(SECRET_ID);
      assertEquals(STORED_SECRET, after.getClientSecret());
   }

   @Test
   void nestedGateRestoresTheOuterGate() {
      TabularDataSource.withCredentialFetchGate(id -> false, () -> {
         TabularDataSource.withCredentialFetchGate(id -> true, () -> {
            CloudTestDataSource inner = new CloudTestDataSource();
            inner.setUseCredentialId(true);
            inner.setCredentialId(SECRET_ID);
            assertEquals(STORED_SECRET, inner.getClientSecret());
            return null;
         });

         CloudTestDataSource outer = new CloudTestDataSource();
         outer.setUseCredentialId(true);
         outer.setCredentialId(SECRET_ID);
         assertNull(outer.getClientSecret());
         return null;
      });

      CloudTestDataSource after = new CloudTestDataSource();
      after.setUseCredentialId(true);
      after.setCredentialId(SECRET_ID);
      assertEquals(STORED_SECRET, after.getClientSecret());
   }

   @Test
   void gateDoesNotOutliveADraftRefreshOnAReusedPoolThread() throws Exception {
      java.util.concurrent.ExecutorService executor =
         java.util.concurrent.Executors.newSingleThreadExecutor();

      try {
         // no writable data source stores the id, so the draft's secret id is rejected
         DataSourceDefinition result = executor.submit(() -> {
            SECRETS.set(secretsManager);
            return service.refreshTabularView(draft(CLOUD, SECRET_ID));
         }).get();
         assertNull(editorValue(result.getTabularView(), "testClientSecret"));

         // a later non-draft use of the same thread still resolves the secret
         String secret = executor.submit(() -> {
            SECRETS.set(secretsManager);
            CloudTestDataSource source = new CloudTestDataSource();
            source.setUseCredentialId(true);
            source.setCredentialId(SECRET_ID);
            return source.getClientSecret();
         }).get();
         assertEquals(STORED_SECRET, secret);
      }
      finally {
         executor.shutdownNow();
      }
   }

   @Test
   void refreshViewDoesNotResolveSecretIdWhenThePermissionCheckFails() throws Exception {
      when(registry.getDataSourceFullNames()).thenReturn(new String[] { "other" });
      when(registry.getDataSource("other")).thenReturn(savedSource(SECRET_ID));
      when(securityEngine.checkPermission(
         principal, ResourceType.DATA_SOURCE, "other", ResourceAction.WRITE))
         .thenThrow(new RuntimeException("security provider failed"));

      DataSourceDefinition result = service.refreshTabularView(draft(CLOUD, SECRET_ID));

      verify(secretsManager, never()).getCredential(any());
      assertNull(editorValue(result.getTabularView(), "testClientSecret"));
   }

   @Test
   void refreshViewDoesNotConsultWritableDataSourceOutsideTheCurrentOrgListing() throws Exception {
      // a data source in another organization is not listed for the current organization, even
      // if the caller has write on it there
      when(registry.getDataSource("elsewhere")).thenReturn(savedSource(SECRET_ID));
      grantWrite("elsewhere", true);

      DataSourceDefinition result = service.refreshTabularView(draft(CLOUD, SECRET_ID));

      verify(secretsManager, never()).getCredential(any());
      verify(registry, never()).getDataSource("elsewhere");
      assertNull(editorValue(result.getTabularView(), "testClientSecret"));
   }

   @Test
   void refreshViewResolvesSecretIdStoredOnAdditionalConnectionOfWritableDataSource()
      throws Exception
   {
      CloudTestDataSource parent = spy(savedSource("parent-secret"));
      doReturn(new String[] { "conn" }).when(parent).getDataSourceNames();
      doReturn(savedSource(SECRET_ID)).when(parent).getDataSource("conn");
      when(registry.getDataSourceFullNames()).thenReturn(new String[] { "parent" });
      when(registry.getDataSource("parent")).thenReturn(parent);
      grantWrite("parent", true);

      DataSourceDefinition result = service.refreshTabularView(draft(CLOUD, SECRET_ID));

      assertEquals(STORED_SECRET, editorValue(result.getTabularView(), "testClientSecret"));
   }

   @Test
   void draftAdditionalConnectionDoesNotResolveSecretIdTheCallerCannotManage() throws Exception {
      DataSourceDefinition additional = draft(CLOUD, SECRET_ID);
      additional.setName("conn");
      DataSourceDefinition definition = draft(CLOUD, "");
      definition.setAdditionalConnections(new java.util.ArrayList<>(java.util.List.of(additional)));

      service.refreshTabularView(definition);

      verify(secretsManager, never()).getCredential(any());
      assertNull(editorValue(additional.getTabularView(), "testClientSecret"));
      assertEquals(SECRET_ID, editorValue(additional.getTabularView(), "credentialId"));
   }

   @Test
   void oauthTokensEchoSecretIdWhenTheCallerCanWriteADataSourceThatStoresIt() throws Exception {
      when(registry.getDataSourceFullNames()).thenReturn(new String[] { "other" });
      when(registry.getDataSource("other")).thenReturn(savedSource(SECRET_ID));
      grantWrite("other", true);
      DataSourceOAuthTokens tokens = DataSourceOAuthTokens.builder()
         .accessToken("NEW-ACCESS-TOKEN")
         .refreshToken("NEW-REFRESH-TOKEN")
         .method("updateTokens")
         .dataSource(draft(CLOUD, SECRET_ID))
         .build();

      DataSourceDefinition result = service.setOAuthTokens(tokens);

      assertEquals(STORED_SECRET, editorValue(result.getTabularView(), "testClientSecret"));
      assertEquals("NEW-ACCESS-TOKEN", editorValue(result.getTabularView(), "accessToken"));
   }

   @Test
   void oauthParamsForUnsavedDraftWithUnmanagedSecretIdReturnNoClientCredentials()
      throws Exception
   {
      // Bug #77172: the draft must be saved first, and the caller is told so. Security is
      // disabled, so saving the new secret id succeeds.
      TabularOAuthParams params = service.getOAuthParams(oauthRequest(CLOUD, "clientSecret"));

      assertEquals(catalog("data.datasources.saveBeforeAuthorize"), params.error());
      assertNull(params.clientId());
      assertNull(params.clientSecret());
      assertNull(params.tokenUri());
   }

   @Test
   void oauthParamsTellSiteAdminToSaveTheDraftFirst() throws Exception {
      when(securityEngine.isSecurityEnabled()).thenReturn(true);
      XPrincipal admin = mock(XPrincipal.class);
      ThreadContext.setContextPrincipal(admin);
      OrganizationManager organizationManager = mock(OrganizationManager.class);
      when(organizationManager.isSiteAdmin(admin)).thenReturn(true);

      try(MockedStatic<OrganizationManager> organizations = mockStatic(OrganizationManager.class)) {
         organizations.when(OrganizationManager::getInstance).thenReturn(organizationManager);

         TabularOAuthParams params = service.getOAuthParams(oauthRequest(CLOUD, "clientSecret"));

         assertEquals(catalog("data.datasources.saveBeforeAuthorize"), params.error());
         assertNull(params.clientSecret());
      }
   }

   @Test
   void oauthParamsTellCallerWhoCannotIntroduceSecretIdsThatTheIdIsNotAllowed() throws Exception {
      // saving would be rejected too, so the caller is not asked to save first
      when(securityEngine.isSecurityEnabled()).thenReturn(true);

      TabularOAuthParams params = service.getOAuthParams(oauthRequest(CLOUD, "clientSecret"));

      assertEquals(catalog("data.datasources.secretIdNotAllowed"), params.error());
      assertNull(params.clientSecret());
   }

   @Test
   void oauthParamsReportMissingCommunityLicenseBeforeWithheldSecretId() throws Exception {
      sreeEnv.when(() -> SreeEnv.getProperty("license.key")).thenReturn("");
      licenseManager.when(LicenseManager::isEnterprise).thenReturn(false);

      TabularOAuthParams params = service.getOAuthParams(oauthRequest(CLOUD, "clientSecret"));

      assertEquals(catalog("em.license.communityAPIKeyMissing"), params.error());
   }

   @Test
   void oauthParamsIgnoreWithheldSecretIdOfAdditionalConnection() throws Exception {
      when(registry.getDataSourceFullNames()).thenReturn(new String[] { "other" });
      when(registry.getDataSource("other")).thenReturn(savedSource(SECRET_ID));
      grantWrite("other", true);
      DataSourceDefinition additional = draft(CLOUD, "another-secret");
      additional.setName("conn");
      DataSourceOAuthParamsRequest request = oauthRequest(CLOUD, "clientSecret");
      request.dataSource().setAdditionalConnections(
         new java.util.ArrayList<>(java.util.List.of(additional)));

      TabularOAuthParams params = service.getOAuthParams(request);

      assertNull(params.error());
      assertEquals(STORED_SECRET, params.clientSecret());
   }

   @Test
   void oauthParamsIgnoreWithheldSecretIdForHostedOAuthService() throws Exception {
      // a hosted OAuth service authorizes without the data source's client credentials
      DataSourceOAuthParamsRequest request = oauthRequest(CLOUD, "clientSecret");
      oauthButton(request.dataSource().getTabularView()).setOauthServiceName("hosted-service");

      TabularOAuthParams params = service.getOAuthParams(request);

      assertNull(params.error());
      assertNull(params.clientSecret());
   }

   @Test
   void refreshViewTellsCallerToSaveBeforeAuthorizingWithWithheldSecretId() throws Exception {
      service.refreshTabularView(draft(CLOUD, SECRET_ID));

      UserMessage message = CoreTool.getUserMessage();
      assertNotNull(message);
      assertEquals(catalog("data.datasources.saveBeforeAuthorize"), message.getMessage());
   }

   @Test
   void refreshViewDoesNotMentionAuthorizingWithoutVisibleOAuthButton() throws Exception {
      // e.g. a REST data source that does not use OAuth
      DataSourceDefinition definition = draft(CLOUD, SECRET_ID);

      for(TabularView view : definition.getTabularView().getViews()) {
         if(view.getType() == ViewType.PANEL) {
            view.setVisible(false);
         }
      }

      service.refreshTabularView(definition);

      assertNull(CoreTool.getUserMessage());
   }

   @Test
   void refreshViewSendsNoMessageWhenSecretIdIsResolved() throws Exception {
      when(registry.getDataSourceFullNames()).thenReturn(new String[] { "other" });
      when(registry.getDataSource("other")).thenReturn(savedSource(SECRET_ID));
      grantWrite("other", true);

      service.refreshTabularView(draft(CLOUD, SECRET_ID));

      assertNull(CoreTool.getUserMessage());
   }

   private static String catalog(String key) {
      return Catalog.getCatalog().getString(key);
   }

   private static TabularButton oauthButton(TabularView root) {
      for(TabularView view : root.getViews()) {
         for(TabularView child : view.getViews()) {
            if(child.getButton() != null) {
               return child.getButton();
            }
         }
      }

      return null;
   }

   private void grantWrite(String path, boolean granted) throws Exception {
      when(securityEngine.checkPermission(
         principal, ResourceType.DATA_SOURCE, path, ResourceAction.WRITE)).thenReturn(granted);
   }

   private static CloudTestDataSource savedSource(String secretId) {
      CloudTestDataSource source = new CloudTestDataSource();
      source.setUseCredentialId(true);
      // set the id without fetching, the way a saved data source holds it
      source.getCredential().setId(secretId);
      return source;
   }

   private static DataSourceOAuthParamsRequest oauthRequest(String type, String clientSecretName) {
      return DataSourceOAuthParamsRequest.builder()
         .user("user")
         .password("password")
         .clientId("clientId")
         .clientSecret(clientSecretName)
         .scope("scope")
         .authorizationUri("authorizationUri")
         .tokenUri("tokenUri")
         .flags("oauthFlags")
         .dataSource(LOCAL.equals(type) ? localDraft() : draft(type, SECRET_ID))
         .build();
   }

   private static DataSourceDefinition localDraft() {
      TabularView root = new TabularView();
      root.addTabularView(view("testClientId", "client-id", true));
      root.addTabularView(view("testClientSecret", "client-secret", true));
      return definition(LOCAL, root);
   }

   private static DataSourceDefinition draft(String type, String secretId) {
      TabularView root = new TabularView();
      root.addTabularView(view("useCredentialId", Boolean.TRUE, true));
      root.addTabularView(view("credentialId", secretId, true));
      root.addTabularView(view("testClientId", null, false));
      root.addTabularView(view("testClientSecret", null, false));
      root.addTabularView(oauthPanel());
      root.addTabularView(view("accessToken", null, true));
      return definition(type, root);
   }

   private static TabularView oauthPanel() {
      TabularButton button = new TabularButton();
      button.setType(ButtonType.OAUTH);
      button.setMethod("updateTokens");
      button.setEnabledMethod("");
      TabularView buttonView = new TabularView();
      buttonView.setType(ViewType.BUTTON);
      buttonView.setVisible(true);
      buttonView.setButton(button);
      TabularView panel = new TabularView();
      panel.setType(ViewType.PANEL);
      panel.setVisible(true);
      panel.addTabularView(buttonView);
      return panel;
   }

   private static DataSourceDefinition definition(String type, TabularView root) {
      DataSourceDefinition definition = new DataSourceDefinition();
      definition.setType(type);
      definition.setName("draft");
      definition.setTabularView(root);
      return definition;
   }

   private static TabularView view(String property, Object value, boolean visible) {
      TabularView view = new TabularView();
      view.setValue(property);
      view.setVisible(visible);
      TabularEditor editor = new TabularEditor();
      editor.setValue(value);
      view.setEditor(editor);
      return view;
   }

   private static Object editorValue(TabularView root, String property) {
      for(TabularView view : root.getViews()) {
         if(property.equals(view.getValue())) {
            return view.getEditor().getValue();
         }
      }

      return null;
   }

   private static final String CLOUD = "CloudTest77141";
   private static final String LOCAL = "LocalTest77141";
   private static final String SECRET_ID = "some-secret-name";
   private static final String STORED_SECRET = "STORED-SECRET-VALUE-77141";
   private static final String STORED_CLIENT_ID = "stored-client-id";
   private static final String TOKEN_URI = "https://auth.example/token";
   private static final ThreadLocal<AbstractSecretsManager> SECRETS = new ThreadLocal<>();

   public static class TestCloudCredential extends CloudClientCredentials {
      @Override
      public AbstractSecretsManager getSecretsManager() {
         return SECRETS.get();
      }
   }

   /**
    * Shaped like the OData connector: the client id and secret are exposed through differently
    * named @Property getters, and getClientId() / getClientSecret() are plain getters that are
    * only reached through the OAuth button's default property names.
    */
   @View(vertical = true, value = {
      @View1(value = "useCredentialId", visibleMethod = "supportToggleCredential"),
      @View1(value = "credentialId", visibleMethod = "isUseCredentialId"),
      @View1(value = "testClientId", visibleMethod = "useCredential"),
      @View1(value = "testClientSecret", visibleMethod = "useCredential"),
      @View1(type = ViewType.PANEL, elements = {
         @View2(type = ViewType.BUTTON, text = "Authorize", button = @Button(
            type = ButtonType.OAUTH, method = "updateTokens", oauth = @Button.OAuth))
      }),
      @View1("accessToken")
   })
   public abstract static class TestDataSourceBase<T extends TestDataSourceBase<T>>
      extends TabularDataSource<T>
   {
      TestDataSourceBase(String type, Class<T> cls) {
         super(type, cls);
      }

      @Override
      protected CredentialType getCredentialType() {
         return CredentialType.CLIENT;
      }

      @Property(label = "Client ID")
      public String getTestClientId() {
         return getClientId();
      }

      public void setTestClientId(String clientId) {
         setClientId(clientId);
      }

      @Property(label = "Client Secret", password = true)
      public String getTestClientSecret() {
         return getClientSecret();
      }

      public void setTestClientSecret(String clientSecret) {
         setClientSecret(clientSecret);
      }

      public String getClientId() {
         return ((ClientCredentials) getCredential()).getClientId();
      }

      public void setClientId(String clientId) {
         ((ClientCredentials) getCredential()).setClientId(clientId);
      }

      public String getClientSecret() {
         return ((ClientCredentials) getCredential()).getClientSecret();
      }

      public void setClientSecret(String clientSecret) {
         ((ClientCredentials) getCredential()).setClientSecret(clientSecret);
      }

      public String getAuthorizationUri() {
         return "https://auth.example/authorize";
      }

      public String getTokenUri() {
         return TOKEN_URI;
      }

      @Property(label = "Access Token", password = true)
      public String getAccessToken() {
         return accessToken;
      }

      public void setAccessToken(String accessToken) {
         this.accessToken = accessToken;
      }

      public void updateTokens(inetsoft.uql.tabular.oauth.Tokens tokens) {
         accessToken = tokens.accessToken();
      }

      public String getInternalValue() {
         return "NON-PROPERTY-GETTER";
      }

      private String accessToken;
   }

   /** Cloud secrets mode: the secret id toggle is supported and the credential is a cloud one. */
   public static class CloudTestDataSource extends TestDataSourceBase<CloudTestDataSource> {
      public CloudTestDataSource() {
         super(CLOUD, CloudTestDataSource.class);
      }

      @Override
      protected Credential createCredential(boolean forceLocal) {
         return forceLocal ? new LocalClientCredentials() : new TestCloudCredential();
      }

      @Override
      public boolean supportToggleCredential() {
         return true;
      }
   }

   /** Local secrets mode: the secret id toggle is not supported. */
   public static class LocalTestDataSource extends TestDataSourceBase<LocalTestDataSource> {
      public LocalTestDataSource() {
         super(LOCAL, LocalTestDataSource.class);
      }

      @Override
      protected Credential createCredential(boolean forceLocal) {
         return new LocalClientCredentials();
      }

      @Override
      public boolean supportToggleCredential() {
         return false;
      }
   }

   private DatasourcesService service;
   private SecurityEngine securityEngine;
   private DataSourceRegistry registry;
   private Principal principal;
   private AbstractSecretsManager secretsManager;
   private MockedStatic<SreeEnv> sreeEnv;
   private MockedStatic<LicenseManager> licenseManager;
   private MockedStatic<Config> uqlConfig;
}
