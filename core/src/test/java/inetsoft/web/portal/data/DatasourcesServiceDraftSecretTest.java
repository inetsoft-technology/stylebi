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
import inetsoft.uql.service.DataSourceRegistry;
import inetsoft.uql.tabular.*;
import inetsoft.uql.util.Config;
import inetsoft.util.AbstractSecretsManager;
import inetsoft.util.ThreadContext;
import inetsoft.util.Tool;
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

      service = new DatasourcesService(null, securityEngine, null, registry, config);
   }

   @AfterEach
   void tearDown() {
      ThreadContext.setContextPrincipal(null);
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
      root.addTabularView(view("accessToken", null, true));
      return definition(type, root);
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
