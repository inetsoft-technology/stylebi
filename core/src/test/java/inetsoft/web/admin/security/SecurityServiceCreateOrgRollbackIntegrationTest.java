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
package inetsoft.web.admin.security;

import inetsoft.report.internal.license.ElasticLicenseService;
import inetsoft.sree.internal.SUtil;
import inetsoft.sree.portal.CustomTheme;
import inetsoft.sree.portal.CustomThemesManager;
import inetsoft.sree.security.*;
import inetsoft.sree.security.support.SecurityTestDataBuilder;
import inetsoft.test.*;
import inetsoft.util.MessageException;
import inetsoft.util.ThreadContext;
import inetsoft.util.audit.ActionRecord;
import inetsoft.util.audit.Audit;
import inetsoft.web.admin.general.LocalizationSettingsService;
import inetsoft.web.admin.security.action.ActionPermissionService;
import inetsoft.web.admin.security.user.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/*
 * Bug #77800: the REST plain org create wrote the org record (listing every requested member)
 * before any member, left everything behind when a member add failed, and audited SUCCESS
 * however it ended.
 *
 * SecurityServiceTest covers this against a mocked provider. A mocked provider cannot show
 * the property the rollback must keep: identities that already exist under the new org id
 * (orphans of an older org with the same id) survive a failed create, because the rollback
 * removes only this call's members and never calls removeOrganization(), which sweeps every
 * identity keyed to the org id. This drives the real SecurityService against the real
 * FileAuthenticationProvider; failures are injected through a delegating provider, and the
 * outcome is read back from the real provider.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class,
                                  PermissionMatrixOrgLifecycleTest.CopyOnReadClusterConfig.class,
                                  SecurityServiceCreateOrgRollbackIntegrationTest.TestBeans.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class SecurityServiceCreateOrgRollbackIntegrationTest {
   private static final String HOST = "host-org";
   private static final String OID = "p77800";
   private static final String NAME = "Name77800";
   private static final IdentityID ALICE = new IdentityID("alice", OID);
   private static final IdentityID BOB = new IdentityID("bob", OID);
   private static final IdentityID STAFF = new IdentityID("staff", OID);
   private static final IdentityID ANALYST = new IdentityID("analyst", OID);
   private static final IdentityID CAROL = new IdentityID("carol77800", OID);
   private static final IdentityID OLD_GROUP = new IdentityID("oldgroup77800", OID);
   private static final IdentityID OLD_ROLE = new IdentityID("oldrole77800", OID);

   private SecurityTestDataBuilder builder;
   private FileAuthenticationProvider fileProvider;
   private EditableAuthenticationProvider provider;
   private CustomThemesManager themes;
   private SecurityService service;
   private SRPrincipal siteAdmin;
   private Audit audit;
   private MockedStatic<Audit> auditStatic;
   private MockedStatic<SUtil> sUtilStatic;

   @BeforeEach
   void setUp() throws Exception {
      builder = SecurityTestDataBuilder.create()
         .addOrg(Organization.getDefaultOrganizationName(), HOST)
         .addSysAdminRole("SiteAdmin77800", HOST)
         .addUser("sa", HOST, "password")
         .addUserToRole("sa", "SiteAdmin77800", HOST)
         .setup();

      fileProvider = (FileAuthenticationProvider)
         ((AuthenticationChain) SecurityEngine.getSecurity().getSecurityProvider()
            .getAuthenticationProvider()).getProviders().get(0);
      removeAll();

      // orphans keyed to the new org id, with no org record
      FSUser carol = new FSUser(CAROL);
      carol.setGroups(new String[] { OLD_GROUP.name });
      fileProvider.addUser(carol);
      fileProvider.addGroup(new FSGroup(OLD_GROUP));
      fileProvider.addRole(new FSRole(OLD_ROLE));
      assertNull(fileProvider.getOrganization(OID), "precondition: no org " + OID);

      audit = mock(Audit.class);
      auditStatic = mockStatic(Audit.class);
      auditStatic.when(Audit::getInstance).thenReturn(audit);

      // the service writes through this provider, failures are injected on it
      provider = mock(EditableAuthenticationProvider.class, org.mockito.AdditionalAnswers.delegatesTo(fileProvider));
      sUtilStatic = mockStatic(SUtil.class, CALLS_REAL_METHODS);
      sUtilStatic.when(() -> SUtil.getEditableAuthenticationProvider(any(SecurityProvider.class)))
         .thenReturn(provider);

      themes = mock(CustomThemesManager.class);
      service = new SecurityService(
         SecurityEngine.getSecurity(), mock(IdentityService.class),
         mock(ActionPermissionService.class), mock(LocalizationSettingsService.class),
         mock(IdentityThemeService.class), mock(SystemAdminService.class),
         mock(UserTreeService.class), themes);

      siteAdmin = builder.principalOf("sa", HOST);
      ThreadContext.setContextPrincipal(siteAdmin);
   }

   @AfterEach
   void tearDown() {
      ThreadContext.setContextPrincipal(null);

      if(sUtilStatic != null) {
         sUtilStatic.close();
      }

      if(auditStatic != null) {
         auditStatic.close();
      }

      if(fileProvider != null) {
         removeAll();
      }

      if(builder != null) {
         builder.teardown();
         builder = null;
      }
   }

   @Test
   void userAddFailsNotStored_callMembersGoneOrphansSurvive() {
      MessageException failure = new MessageException("Failed to add user bob");
      doThrow(failure).when(provider).addUser(argThat(u -> u != null && BOB.equals(u.getIdentityID())));

      assertSame(failure, assertThrows(MessageException.class, this::create));

      assertRolledBack(failure);
   }

   @Test
   void groupAddFailsAfterBeingStored_thatGroupAlsoRemoved() {
      MessageException failure = new MessageException("Timed out adding group staff");
      // the write lands, then the add reports a failure
      doAnswer(inv -> {
         fileProvider.addGroup(inv.getArgument(0));
         throw failure;
      }).when(provider).addGroup(argThat(g -> g != null && STAFF.equals(g.getIdentityID())));

      assertSame(failure, assertThrows(MessageException.class, this::create));

      assertRolledBack(failure);
   }

   @Test
   void roleAddFails_callMembersGoneOrphansSurvive() {
      MessageException failure = new MessageException("Failed to add role analyst");
      doThrow(failure).when(provider).addRole(argThat(r -> r != null && ANALYST.equals(r.getIdentityID())));

      assertSame(failure, assertThrows(MessageException.class, this::create));

      assertRolledBack(failure);
   }

   @Test
   void orgAddFails_callMembersGoneOrphansSurvive() {
      MessageException failure = new MessageException("Failed to add organization");
      doThrow(failure).when(provider).addOrganization(any());

      assertSame(failure, assertThrows(MessageException.class, this::create));

      assertRolledBack(failure);
   }

   @Test
   void themeFailsAfterOrg_orgAndMembersKeptAuditedFailureWithIdentityInfo() {
      CustomTheme theme = new CustomTheme();
      theme.setId("t77800");
      theme.setName("Theme77800");
      when(themes.getCustomThemes()).thenReturn(Set.of(theme));
      RuntimeException failure = new RuntimeException("Failed to write themes");
      doThrow(failure).when(themes).updateCustomThemes(any());
      SecurityOrganization request = request();
      request.setTheme("t77800");

      assertSame(failure, assertThrows(RuntimeException.class,
         () -> service.createOrganization(request, null, siteAdmin)));

      Organization org = fileProvider.getOrganization(OID);
      assertNotNull(org, "a failure after the org write keeps the org");
      assertEquals(Set.of("alice", "bob", "staff", "analyst"), Set.of(org.getMembers()));
      assertAllCallMembers(true);
      assertOrphansIntact();
      ActionRecord record = auditedAction();
      assertEquals(ActionRecord.ACTION_STATUS_FAILURE, record.getActionStatus());
      assertTrue(record.getActionError().contains("was created"), record.getActionError());
      assertTrue(record.getActionError().contains("Failed to write themes"), record.getActionError());
      verify(audit, times(1)).auditIdentityInfo(any(), any());
   }

   @Test
   void success_everythingStoredAuditedSuccess() throws Exception {
      create();

      Organization org = fileProvider.getOrganization(OID);
      assertNotNull(org);
      assertEquals(NAME, org.getName());
      assertEquals(Set.of("alice", "bob", "staff", "analyst"), Set.of(org.getMembers()));
      assertAllCallMembers(true);
      assertNotNull(fileProvider.authenticate(ALICE, new DefaultTicket(ALICE, "Str0ng!Passw0rd77800")),
                    "a member user gets the default password");
      assertOrphansIntact();
      ActionRecord record = auditedAction();
      assertEquals(ActionRecord.ACTION_STATUS_SUCCESS, record.getActionStatus());
      verify(audit, times(1)).auditIdentityInfo(any(), any());
      verify(provider, never()).removeUser(any());
      verify(provider, never()).removeGroup(any());
      verify(provider, never()).removeRole(any());
   }

   private void create() throws Exception {
      service.createOrganization(request(), null, siteAdmin);
   }

   private SecurityOrganization request() {
      SecurityOrganization request = new SecurityOrganization();
      request.setId(OID);
      request.setName(NAME);
      request.setMemberUsers(new ArrayList<>(List.of("alice", "bob")));
      request.setMemberGroups(new ArrayList<>(List.of("staff")));
      request.setRoles(new ArrayList<>(List.of("analyst")));
      request.setDefaultPassword("Str0ng!Passw0rd77800");
      return request;
   }

   private void assertRolledBack(Exception failure) {
      assertNull(fileProvider.getOrganization(OID), "no org record after a failed member write");
      assertAllCallMembers(false);
      assertOrphansIntact();
      verify(provider, never()).removeOrganization(any());
      ActionRecord record = auditedAction();
      assertEquals(ActionRecord.ACTION_STATUS_FAILURE, record.getActionStatus());
      assertTrue(record.getActionError().contains(failure.getMessage()), record.getActionError());
      assertFalse(record.getActionError().contains("rollback incomplete"), record.getActionError());
      verify(audit, never()).auditIdentityInfo(any(), any());
   }

   private void assertAllCallMembers(boolean present) {
      assertEquals(present, fileProvider.getUser(ALICE) != null, "alice");
      assertEquals(present, fileProvider.getUser(BOB) != null, "bob");
      assertEquals(present, fileProvider.getGroup(STAFF) != null, "staff");
      assertEquals(present, fileProvider.getRole(ANALYST) != null, "analyst");
   }

   private void assertOrphansIntact() {
      User carol = fileProvider.getUser(CAROL);
      assertNotNull(carol, "a pre-existing user under the org id must survive");
      assertArrayEquals(new String[] { OLD_GROUP.name }, carol.getGroups());
      assertNotNull(fileProvider.getGroup(OLD_GROUP), "a pre-existing group must survive");
      assertNotNull(fileProvider.getRole(OLD_ROLE), "a pre-existing role must survive");
   }

   private ActionRecord auditedAction() {
      ArgumentCaptor<ActionRecord> captor = ArgumentCaptor.forClass(ActionRecord.class);
      verify(audit, times(1)).auditAction(captor.capture(), any());
      return captor.getValue();
   }

   private void removeAll() {
      for(IdentityID id : new IdentityID[] { ALICE, BOB, CAROL }) {
         if(fileProvider.getUser(id) != null) {
            fileProvider.removeUser(id);
         }
      }

      for(IdentityID id : new IdentityID[] { STAFF, OLD_GROUP }) {
         if(fileProvider.getGroup(id) != null) {
            fileProvider.removeGroup(id);
         }
      }

      for(IdentityID id : new IdentityID[] { ANALYST, OLD_ROLE }) {
         if(fileProvider.getRole(id) != null) {
            fileProvider.removeRole(id);
         }
      }

      if(fileProvider.getOrganization(OID) != null) {
         fileProvider.removeOrganization(OID);
      }
   }

   // beans the community test configuration does not define: the enterprise license strategy on
   // this classpath looks up ElasticLicenseService when SecurityEngine initializes
   @Configuration
   static class TestBeans {
      @Bean
      public ElasticLicenseService elasticLicenseService() {
         return mock(ElasticLicenseService.class);
      }

      @Bean
      public CustomThemesManager customThemesManager() {
         return mock(CustomThemesManager.class, RETURNS_DEEP_STUBS);
      }
   }
}
