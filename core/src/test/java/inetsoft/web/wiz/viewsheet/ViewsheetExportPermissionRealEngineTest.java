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
package inetsoft.web.wiz.viewsheet;

import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.report.internal.license.LicenseManager;
import inetsoft.sree.SreeEnv;
import inetsoft.sree.internal.cluster.Cluster;
import inetsoft.sree.security.*;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import inetsoft.uql.viewsheet.FileFormatInfo;
import inetsoft.util.Tool;
import inetsoft.web.viewsheet.service.ExportResponse;
import inetsoft.web.viewsheet.service.VSExportService;
import inetsoft.web.wiz.WizUtil;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.WriteListener;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.ByteArrayOutputStream;
import java.security.Principal;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Redmine #80497 items 1 and 2: the Export and ExportExpandComponents toolbar actions on the wiz
 * agent export path, through a REAL {@link SecurityEngine} (so its login check runs), the real
 * {@link WizDelegatingCheckPermissionStrategy} over a real {@link DefaultCheckPermissionStrategy},
 * and the principal shape {@code WizServiceAuthenticationFilter} builds: an {@link SRPrincipal}
 * with roles and organization, property wiz=true, not registered as a logged-in user.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class },
   initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome()
@Tag("core")
class ViewsheetExportPermissionRealEngineTest {
   private static final String ORG = Organization.getDefaultOrganizationID();
   private static final IdentityID DENIED_ROLE = new IdentityID("noExportRole", ORG);
   private static final IdentityID GRANTED_ROLE = new IdentityID("exportRole", ORG);

   private String previousSecurity;
   private SecurityEngine engine;
   private SecurityProvider provider;
   private ViewsheetSessionService sessions;
   private VSExportService exportService;
   private ViewsheetAssemblyAgentController controller;

   @BeforeEach
   void setUp() {
      previousSecurity = SreeEnv.getProperty("security.enabled");
      SreeEnv.setProperty("security.enabled", "true");

      engine = new SecurityEngine(mock(LicenseManager.class), mock(Cluster.class));
      ReflectionTestUtils.setField(engine, "users", new ConcurrentHashMap<>());
      provider = mock(SecurityProvider.class);
      lenient().when(provider.getUser(any())).thenReturn(null);
      lenient().when(provider.getGroup(any())).thenReturn(null);
      lenient().when(provider.getRole(any())).thenReturn(null);
      lenient().when(provider.getRoles(any())).thenReturn(new IdentityID[0]);
      lenient().when(provider.getUserGroups(any())).thenReturn(new String[0]);
      lenient().when(provider.getAllGroups(any(IdentityID[].class)))
         .thenAnswer(inv -> inv.getArgument(0));
      lenient().when(provider.getAllRoles(any(IdentityID[].class)))
         .thenAnswer(inv -> inv.getArgument(0));
      lenient().when(provider.isSystemAdministratorRole(any())).thenReturn(false);
      lenient().when(provider.isOrgAdministratorRole(any())).thenReturn(false);
      lenient().when(provider.getOrgNameFromID(anyString())).thenReturn(ORG);
      AuthenticationProvider auth = mock(AuthenticationProvider.class);
      lenient().when(provider.getAuthenticationProvider()).thenReturn(auth);
      Organization org = mock(Organization.class);
      lenient().when(org.getOrganizationID()).thenReturn(ORG);
      lenient().when(org.getRoles()).thenReturn(new IdentityID[0]);
      lenient().when(provider.getOrganization(anyString())).thenReturn(org);

      // the real strategy chain, exactly as CompositeSecurityProvider wires it
      CheckPermissionStrategy strategy =
         new WizDelegatingCheckPermissionStrategy(new DefaultCheckPermissionStrategy(provider));
      lenient().when(provider.checkPermission(any(), any(), anyString(), any()))
         .thenAnswer(inv -> strategy.checkPermission(inv.getArgument(0), inv.getArgument(1),
                                                     inv.getArgument(2), inv.getArgument(3)));
      ReflectionTestUtils.setField(engine, "provider", provider);

      sessions = mock(ViewsheetSessionService.class);
      try {
         when(sessions.resolve(eq("tok"), any(Principal.class)))
            .thenReturn(mock(RuntimeViewsheet.class));
      }
      catch(Exception e) {
         throw new IllegalStateException(e);
      }

      exportService = mock(VSExportService.class);
      controller = ViewsheetAssemblyAgentControllerTest.controllerForExport(
         sessions, exportService, engine, mock(inetsoft.web.wiz.script.ScriptImageService.class));
   }

   @AfterEach
   void tearDown() {
      SreeEnv.setProperty("security.enabled", previousSecurity == null ? "false" : previousSecurity);
   }

   /** Grants READ on the toolbar action to exactly {@code roles}; everyone else is denied. */
   private void grant(String resource, IdentityID... roles) {
      Set<String> names = new java.util.HashSet<>();

      for(IdentityID role : roles) {
         names.add(role.getName());
      }

      Permission perm = new Permission();
      perm.setRoleGrantsForOrg(ResourceAction.READ, names, ORG);
      perm.setOrgEditedGrantAll(java.util.Map.of(ORG, true));
      lenient().when(provider.getPermission(eq(ResourceType.VIEWSHEET_TOOLBAR_ACTION), eq(resource)))
         .thenReturn(perm);
      lenient().when(provider.getPermission(eq(ResourceType.VIEWSHEET_TOOLBAR_ACTION), eq(resource),
                                            anyString())).thenReturn(perm);
   }

   /** The JWT-shaped principal: roles and org, wiz=true, never registered as logged in. */
   private static SRPrincipal wizUser(String name, IdentityID... roles) {
      SRPrincipal p = new SRPrincipal(new IdentityID(name, ORG), roles, new String[0], ORG,
                                      Tool.getSecureRandom().nextLong());
      p.setProperty("wiz", "true");
      return p;
   }

   private static HttpServletResponse response() throws Exception {
      HttpServletResponse response = mock(HttpServletResponse.class);
      when(response.getOutputStream()).thenReturn(new ServletOutputStream() {
         private final ByteArrayOutputStream sink = new ByteArrayOutputStream();

         @Override
         public boolean isReady() {
            return true;
         }

         @Override
         public void setWriteListener(WriteListener writeListener) {
         }

         @Override
         public void write(int b) {
            sink.write(b);
         }
      });
      return response;
   }

   private void export(Principal user, String format, Boolean match) throws Exception {
      controller.export("tok", format, null, match, null, null, null, null, null, user, response());
   }

   /** Verifies one export call reached the service and returns the match and principal it got. */
   private Object[] reachedService(int formatType) throws Exception {
      ArgumentCaptor<Boolean> match = ArgumentCaptor.forClass(Boolean.class);
      ArgumentCaptor<Principal> principal = ArgumentCaptor.forClass(Principal.class);
      verify(exportService).exportViewsheet(any(), eq(formatType), match.capture(), anyBoolean(),
         anyBoolean(), anyBoolean(), anyBoolean(), any(String[].class), anyBoolean(), anyBoolean(),
         any(), anyBoolean(), any(ExportResponse.class), principal.capture());
      return new Object[] { match.getValue(), principal.getValue() };
   }

   // control: what the old check did. The wiz principal is approved for everything, so a user
   // an admin denied Export was never refused.
   @Test
   void control_oldCheckOnWizPrincipalAllowsDeniedUser() throws Exception {
      grant("Export", GRANTED_ROLE);
      SRPrincipal denied = wizUser("denied", DENIED_ROLE);

      assertTrue(engine.checkPermission(denied, ResourceType.VIEWSHEET_TOOLBAR_ACTION, "Export",
                                        ResourceAction.READ));
   }

   // control: the trap the fix avoids. A bare nonWizCopy of a user that is not in the logged-in
   // map fails the login check for a toolbar action, whatever the grant.
   @Test
   void control_bareNonWizCopyOfNotLoggedInUserThrowsLoginError() {
      grant("Export", GRANTED_ROLE);

      assertThrows(inetsoft.sree.security.SecurityException.class,
         () -> engine.checkPermission(WizUtil.nonWizCopy(wizUser("granted", GRANTED_ROLE)),
            ResourceType.VIEWSHEET_TOOLBAR_ACTION, "Export", ResourceAction.READ));
   }

   // the check copy used by the fix evaluates the real grants for both users, never a login error
   @Test
   void toolbarCheckPrincipal_evaluatesRealGrantsWithoutLoginError() throws Exception {
      grant("Export", GRANTED_ROLE);

      assertTrue(engine.checkPermission(WizUtil.toolbarCheckPrincipal(wizUser("g", GRANTED_ROLE)),
         ResourceType.VIEWSHEET_TOOLBAR_ACTION, "Export", ResourceAction.READ));
      assertFalse(engine.checkPermission(WizUtil.toolbarCheckPrincipal(wizUser("d", DENIED_ROLE)),
         ResourceType.VIEWSHEET_TOOLBAR_ACTION, "Export", ResourceAction.READ));
   }

   @Test
   void export_deniedUser_refusedNamingPermissionAndNothingExported() throws Exception {
      grant("Export", GRANTED_ROLE);
      grant("ExportExpandComponents", GRANTED_ROLE);

      IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
         () -> export(wizUser("denied", DENIED_ROLE), "PDF", true));

      assertTrue(e.getMessage().contains("Export permission"), e.getMessage());
      verifyNoInteractions(exportService);
      verify(sessions, never()).resolve(anyString(), any());
   }

   @Test
   void export_grantedNotLoggedInUser_proceedsAsOriginalWizPrincipal() throws Exception {
      grant("Export", GRANTED_ROLE);
      grant("ExportExpandComponents", GRANTED_ROLE);
      SRPrincipal granted = wizUser("granted", GRANTED_ROLE);

      export(granted, "PDF", true);

      Object[] reached = reachedService(FileFormatInfo.EXPORT_TYPE_PDF);
      assertSame(granted, reached[1]);
      assertTrue(SRPrincipal.isWizPrincipal((Principal) reached[1]));
   }

   @Test
   void export_expandDenied_matchFalseForcedTrue() throws Exception {
      grant("Export", GRANTED_ROLE, DENIED_ROLE);
      grant("ExportExpandComponents", GRANTED_ROLE);

      export(wizUser("noExpand", DENIED_ROLE), "PDF", false);

      assertEquals(true, reachedService(FileFormatInfo.EXPORT_TYPE_PDF)[0]);
   }

   // rule is applied after the CSV override (CSV always asks for match=false)
   @Test
   void export_expandDeniedCsv_matchForcedTrueAfterCsvOverride() throws Exception {
      grant("Export", GRANTED_ROLE, DENIED_ROLE);
      grant("ExportExpandComponents", GRANTED_ROLE);

      export(wizUser("noExpand", DENIED_ROLE), "CSV", true);

      assertEquals(true, reachedService(FileFormatInfo.EXPORT_TYPE_CSV)[0]);
   }

   @Test
   void export_expandGranted_matchFalsePreserved() throws Exception {
      grant("Export", GRANTED_ROLE);
      grant("ExportExpandComponents", GRANTED_ROLE);

      export(wizUser("granted", GRANTED_ROLE), "PDF", false);

      assertEquals(false, reachedService(FileFormatInfo.EXPORT_TYPE_PDF)[0]);
   }

   @Test
   void export_expandGrantedCsv_matchStaysFalse() throws Exception {
      grant("Export", GRANTED_ROLE);
      grant("ExportExpandComponents", GRANTED_ROLE);

      export(wizUser("granted", GRANTED_ROLE), "CSV", true);

      assertEquals(false, reachedService(FileFormatInfo.EXPORT_TYPE_CSV)[0]);
   }

   // match=true never needs the expand permission, even for a user without it
   @Test
   void export_expandDeniedButMatchTrue_unchanged() throws Exception {
      grant("Export", GRANTED_ROLE, DENIED_ROLE);
      grant("ExportExpandComponents", GRANTED_ROLE);

      export(wizUser("noExpand", DENIED_ROLE), "PDF", true);

      assertEquals(true, reachedService(FileFormatInfo.EXPORT_TYPE_PDF)[0]);
   }

   // no permission defined anywhere: the default is allow, so nothing changes for existing users
   @Test
   void export_noPermissionDefined_allowedAndMatchFalsePreserved() throws Exception {
      SRPrincipal user = wizUser("anyone", DENIED_ROLE);

      export(user, "PDF", false);

      Object[] reached = reachedService(FileFormatInfo.EXPORT_TYPE_PDF);
      assertEquals(false, reached[0]);
      assertSame(user, reached[1]);
   }

   // A principal with no roles, no groups and no provider record has no identity to evaluate, so
   // the real strategy grants it nothing beyond the My Dashboards default, even where no Export
   // permission is defined (DefaultCheckPermissionStrategyEmptySsoPrincipalTest.
   // otherDefaultGrantsStillDenied records this as intended). Such a plugin user is refused, as
   // the same user is in the Viewer. Pinned so a change to that default is noticed here.
   @Test
   void export_rolelessUnknownUser_noPermissionDefined_refusedLikeTheViewer() throws Exception {
      SRPrincipal user = wizUser("nobody");

      assertThrows(IllegalArgumentException.class, () -> export(user, "PDF", true));
      verifyNoInteractions(exportService);
   }
}
