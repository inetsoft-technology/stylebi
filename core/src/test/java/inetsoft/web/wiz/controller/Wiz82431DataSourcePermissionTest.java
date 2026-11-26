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
package inetsoft.web.wiz.controller;

import inetsoft.sree.ClientInfo;
import inetsoft.sree.SreeEnv;
import inetsoft.sree.security.CheckPermissionStrategy;
import inetsoft.sree.security.IdentityID;
import inetsoft.sree.security.ResourceAction;
import inetsoft.sree.security.ResourceType;
import inetsoft.sree.security.SRPrincipal;
import inetsoft.sree.security.SecurityEngine;
import inetsoft.sree.security.SecurityProvider;
import inetsoft.sree.security.WizDelegatingCheckPermissionStrategy;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import inetsoft.uql.XRepository;
import inetsoft.uql.asset.AssetRepository;
import inetsoft.uql.service.DataSourceRegistry;
import inetsoft.sree.AnalyticRepository;
import inetsoft.uql.asset.AssetEntry;
import inetsoft.uql.jdbc.JDBCDataSource;
import inetsoft.web.portal.controller.database.LogicalModelService;
import inetsoft.web.portal.controller.database.DataSourceService;
import inetsoft.web.portal.data.DataSourceBrowserService;
import inetsoft.web.security.PermissionPath;
import inetsoft.web.security.RequiredPermission;
import inetsoft.web.security.Secured;
import inetsoft.web.security.SecuredAspect;
import inetsoft.web.admin.authz.ComponentAuthorizationService;
import inetsoft.sree.internal.SUtil;
import jakarta.servlet.http.HttpServletRequest;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import java.lang.reflect.Method;
import java.security.Principal;
import java.util.List;
import inetsoft.web.wiz.WizUtil;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Redmine #82431: data source / data model permission checks of a plugin (wiz-tagged) request are
 * evaluated for the real user. Uses the real SecurityEngine with the real
 * WizDelegatingCheckPermissionStrategy over a delegate that grants role "roleX" everything and
 * role "adm" ADMIN only.
 *
 * [No grant, wiz]  wiz-tagged principal without roleX -> denied by every gate / service check
 * [Granted, wiz]   wiz-tagged principal with roleX    -> allowed
 * [Admin, wiz]     wiz-tagged principal with "adm"    -> allowed through the ADMIN fallback
 * [Non-wiz]        unchanged: the delegate decides
 * [Null principal] passes through untouched
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class },
   initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome()
@Tag("core")
class Wiz82431DataSourcePermissionTest {
   private static final IdentityID ROLE_X = new IdentityID("roleX", "org1");
   private static final IdentityID ROLE_ADM = new IdentityID("adm", "org1");
   private static final String DS = "secretDs";

   @BeforeEach
   void setUp() {
      ReflectionTestUtils.setField(engine, "vprovider", null);
      ReflectionTestUtils.setField(engine, "vpm_provider", null);
      ReflectionTestUtils.setField(engine, "users", new ConcurrentHashMap<ClientInfo, SRPrincipal>());
      SreeEnv.setProperty("security.enabled", "true");
      SreeEnv.setProperty("security.datasource.everyone", "false");
      SecurityEngine.updateSecurityDatasourceEveryoneValue();

      CheckPermissionStrategy delegate = mock(CheckPermissionStrategy.class);
      when(delegate.checkPermission(any(), any(), any(), any())).thenAnswer(inv -> {
         SRPrincipal p = inv.getArgument(0);
         ResourceAction action = inv.getArgument(3);
         IdentityID[] roles = p.getRoles();
         boolean x = Arrays.asList(roles).contains(ROLE_X);
         boolean adm = Arrays.asList(roles).contains(ROLE_ADM);
         return x || adm && action == ResourceAction.ADMIN;
      });
      WizDelegatingCheckPermissionStrategy strategy =
         new WizDelegatingCheckPermissionStrategy(delegate);
      SecurityProvider provider = mock(SecurityProvider.class);
      when(provider.checkPermission(any(), any(), any(), any())).thenAnswer(inv ->
         strategy.checkPermission(inv.getArgument(0), inv.getArgument(1), inv.getArgument(2),
                                  inv.getArgument(3)));
      ReflectionTestUtils.setField(engine, "provider", provider);
   }

   @AfterEach
   void tearDown() {
      SreeEnv.setProperty("security.enabled", "false");
      SreeEnv.remove("security.datasource.everyone");
      SecurityEngine.updateSecurityDatasourceEveryoneValue();
   }

   private SRPrincipal principal(String name, long id, boolean wiz, IdentityID... roles) {
      SRPrincipal p = new SRPrincipal(new IdentityID(name, "org1"), roles, new String[0], "org1", id);

      if(wiz) {
         p.setProperty("wiz", "true");
      }
      else {
         p.setProperty("login.user", "true");
         @SuppressWarnings("unchecked")
         Map<ClientInfo, SRPrincipal> users =
            (Map<ClientInfo, SRPrincipal>) ReflectionTestUtils.getField(engine, "users");
         users.put(p.getUser().getCacheKey(), p);
      }

      return p;
   }

   private DataSourceService service() {
      return new DataSourceService(mock(AssetRepository.class), engine, mock(XRepository.class),
                                   mock(DataSourceRegistry.class));
   }

   @Test
   void engineHelper_wizPrincipalWithoutGrant_isDenied() throws Exception {
      SRPrincipal p = principal("nogrant", 1L, true);

      // the bypass this bug is about: the engine itself approves a tagged principal
      assertTrue(engine.checkPermission(p, ResourceType.DATA_SOURCE, DS, ResourceAction.WRITE));
      assertFalse(WizUtil.checkPermission(engine, p, ResourceType.DATA_SOURCE, DS,
                                          ResourceAction.WRITE));
   }

   @Test
   void engineHelper_grantedAndAdminAndNonWiz_unchanged() throws Exception {
      assertTrue(WizUtil.checkPermission(engine, principal("granted", 2L, true, ROLE_X),
         ResourceType.DATA_SOURCE, DS, ResourceAction.WRITE));
      assertTrue(WizUtil.checkPermission(engine, principal("admin", 3L, true, ROLE_ADM),
         ResourceType.DATA_SOURCE, DS, ResourceAction.WRITE));
      assertTrue(WizUtil.checkPermission(engine, principal("plain", 4L, false, ROLE_X),
         ResourceType.DATA_SOURCE, DS, ResourceAction.WRITE));
      assertFalse(WizUtil.checkPermission(engine, principal("plain2", 5L, false),
         ResourceType.DATA_SOURCE, DS, ResourceAction.WRITE));
      assertFalse(WizUtil.checkPermission(engine, null, ResourceType.DATA_SOURCE, DS,
                                          ResourceAction.READ));
   }

   @Test
   void dataSourceService_wizPrincipalWithoutGrant_isDenied() throws Exception {
      DataSourceService service = service();

      assertFalse(service.checkPermission(DS, ResourceAction.READ, principal("n", 6L, true)));
      assertFalse(service.checkPermission(DS, "folder", ResourceAction.WRITE,
                                          principal("n2", 7L, true)));
      assertFalse(service.checkDataModelFolderPermission(DS + "/folder", ResourceAction.WRITE,
                                                         principal("n3", 8L, true)));
      assertTrue(service.checkPermission(DS, ResourceAction.READ,
                                         principal("g", 9L, true, ROLE_X)));
   }

   @Test
   void datasourceGates_wizPrincipalWithoutGrant_isRefusedAndGrantedPasses() throws Exception {
      WizDatasourceGates gates = new WizDatasourceGates(engine);
      SRPrincipal none = principal("n4", 10L, true);

      assertThrows(java.lang.SecurityException.class,
         () -> gates.requireDataSourcePermission(DS, ResourceAction.READ, none));
      assertThrows(java.lang.SecurityException.class,
         () -> gates.requireFolderPermission("/", ResourceAction.WRITE, none));
      assertThrows(java.lang.SecurityException.class, () -> gates.requireCreatePermission(null, none));

      SRPrincipal granted = principal("g2", 11L, true, ROLE_X);
      gates.requireDataSourcePermission(DS, ResourceAction.WRITE, granted);
      gates.requireFolderPermission("/", ResourceAction.WRITE, granted);
      gates.requireDataSourcePermission(DS, ResourceAction.WRITE,
                                        principal("a", 12L, true, ROLE_ADM));
   }

   /** Models the engine approving a wiz-tagged principal and refusing everyone else. */
   private AssetRepository bypassAssetRepository() throws Exception {
      AssetRepository assets = mock(AssetRepository.class);
      doAnswer(i -> {
         if(!SRPrincipal.isWizPrincipal(i.getArgument(0))) {
            throw new inetsoft.sree.security.SecurityException("denied");
         }

         return null;
      }).when(assets).checkAssetPermission(any(Principal.class), any(AssetEntry.class), any());
      return assets;
   }

   @Test
   void assetEntryAndModelChecks_wizPrincipal_areEvaluatedForRealUser() throws Exception {
      AssetRepository assets = bypassAssetRepository();
      DataSourceService service = new DataSourceService(assets, engine, mock(XRepository.class),
                                                        mock(DataSourceRegistry.class));
      AssetEntry entry = new AssetEntry(AssetRepository.QUERY_SCOPE, AssetEntry.Type.LOGIC_MODEL,
                                        DS + "/model", null);
      assertFalse(service.checkPermission(entry, ResourceAction.READ, principal("n5", 13L, true)));

      LogicalModelService models = mock(LogicalModelService.class, Mockito.CALLS_REAL_METHODS);
      ReflectionTestUtils.setField(models, "assetRepository", assets);
      assertFalse(models.checkPermission(DS, null, "model", null, ResourceAction.READ,
                                         principal("n6", 14L, true)));
   }

   @Test
   void browse_wizPrincipalWithoutGrant_filtersSourceOut() throws Exception {
      JDBCDataSource source = mock(JDBCDataSource.class);
      when(source.getName()).thenReturn(DS);
      when(source.getFullName()).thenReturn(DS);
      XRepository repository = mock(XRepository.class);
      when(repository.getDataSourceFullNames()).thenReturn(new String[] { DS });
      when(repository.getDataSource(DS)).thenReturn(source);
      DataSourceBrowserService browser = new DataSourceBrowserService(
         engine, null, repository, service(), mock(DataSourceRegistry.class), null, null);

      assertTrue(browser.getDatabaseDataSources(principal("n7", 15L, true))
                    .dataSources().isEmpty());
      assertEquals(1, browser.getDatabaseDataSources(principal("g3", 16L, true, ROLE_X))
                    .dataSources().size());
   }

   public static class SecuredFixture {
      @Secured({
         @RequiredPermission(resourceType = ResourceType.DATA_SOURCE, actions = ResourceAction.READ)
      })
      public String read(@PermissionPath String path, Principal principal) {
         return path;
      }
   }

   @Test
   void securedAspect_dataSourceRoute_wizPrincipalWithoutGrantIsRefused() throws Throwable {
      AnalyticRepository replet = mock(AnalyticRepository.class);
      when(replet.checkPermission(any(Principal.class), any(ResourceType.class), anyString(),
                                  any(ResourceAction.class))).thenAnswer(inv ->
         engine.checkPermission(inv.<Principal>getArgument(0), inv.<ResourceType>getArgument(1),
                                inv.<String>getArgument(2), inv.<ResourceAction>getArgument(3)));
      HttpServletRequest request = mock(HttpServletRequest.class);
      when(request.getRequestURI()).thenReturn("/api/wiz/databases/definition");
      SecuredAspect aspect = new SecuredAspect(mock(DataSourceRegistry.class),
                                               mock(ComponentAuthorizationService.class));
      Method method = SecuredFixture.class.getMethod("read", String.class, Principal.class);

      try(MockedStatic<SUtil> sutil = Mockito.mockStatic(SUtil.class,
                                                        Mockito.CALLS_REAL_METHODS))
      {
         sutil.when(SUtil::getRepletRepository).thenReturn(replet);
         RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

         try {
            for(boolean granted : new boolean[] { false, true }) {
               SRPrincipal p = granted ? principal("g4", 17L, true, ROLE_X)
                                       : principal("n8", 18L, true);
               when(request.getUserPrincipal()).thenReturn(p);
               MethodSignature signature = mock(MethodSignature.class);
               when(signature.getMethod()).thenReturn(method);
               ProceedingJoinPoint jp = mock(ProceedingJoinPoint.class);
               when(jp.getSignature()).thenReturn(signature);
               when(jp.getArgs()).thenReturn(new Object[] { DS, p });

               if(granted) {
                  aspect.authorize(jp);
                  verify(jp).proceed();
               }
               else {
                  assertThrows(java.lang.SecurityException.class, () -> aspect.authorize(jp));
                  verify(jp, never()).proceed();
               }
            }
         }
         finally {
            RequestContextHolder.resetRequestAttributes();
         }
      }
   }

   @Autowired
   private SecurityEngine engine;
}
