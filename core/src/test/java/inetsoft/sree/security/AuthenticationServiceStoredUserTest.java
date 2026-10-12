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
package inetsoft.sree.security;

import inetsoft.mv.MVManager;
import inetsoft.report.internal.license.LicenseManager;
import inetsoft.sree.ClientInfo;
import inetsoft.sree.SreeEnv;
import inetsoft.sree.internal.SUtil;
import inetsoft.sree.internal.cluster.Cluster;
import inetsoft.sree.security.db.DatabaseAuthenticationProvider;
import inetsoft.sree.security.db.DatabaseProviderTestSupport;
import inetsoft.sree.web.SessionLicenseServiceProvider;
import inetsoft.test.*;
import inetsoft.uql.service.DataSourceRegistry;
import inetsoft.uql.util.XSessionService;
import inetsoft.uql.XPrincipal;
import inetsoft.util.IndexedStorage;
import inetsoft.util.ThreadContext;
import inetsoft.util.audit.Audit;
import inetsoft.util.audit.SessionRecord;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.mockito.quality.Strictness;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.lang.reflect.Field;
import java.security.Principal;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Bug #77089: with the database provider and security.user.caseSensitive=false, a log in with a
 * case variant of a stored user name (<tt>BOB</tt> for stored <tt>bob</tt>) must use the stored
 * user id everywhere, not only for the session principal: the password checked, the session
 * id, the audit records and the ticket. Runs the real {@link AuthenticationService}, the real
 * {@link SecurityEngine} principal building and a {@link DatabaseAuthenticationProvider} whose
 * users query runs against an in-memory table.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class AuthenticationServiceStoredUserTest {
   private final String org = Organization.getDefaultOrganizationID();
   private MockedStatic<Audit> audit;
   private MockedStatic<SUtil> sutil;
   private Audit auditInstance;
   private XSessionService sessionService;
   private AuthenticationService service;
   private List<IdentityID> queried;
   private Principal savedPrincipal;

   @BeforeEach
   void savePrincipal() {
      // AuthenticationService.authenticate() sets the thread's principal, restored in tearDown()
      savedPrincipal = ThreadContext.getPrincipal();
   }

   @AfterEach
   void tearDown() {
      ThreadContext.setPrincipal(savedPrincipal);

      if(sutil != null) {
         sutil.close();
      }

      if(audit != null) {
         audit.close();
      }
   }

   // stored "bob", case-insensitive provider and collation: "BOB" is "bob" throughout
   @Test
   void caseInsensitive_caseVariantLogin_usesStoredIdEverywhere() throws Exception {
      setUp(false, true, "bob");
      IdentityID bob = new IdentityID("bob", org);

      Principal principal = login("BOB", "pw-bob");

      assertNotNull(principal);
      assertEquals(bob.convertToKey(), principal.getName());
      assertEquals(List.of(bob), queried, "users query bound");
      verify(sessionService).createSessionID(XSessionService.USER, "bob");
      assertEquals(bob + ":pw-bob", ((XPrincipal) principal).getProperty(SUtil.TICKET));
      assertEquals(bob.convertToKey(), auditedUser());
      sutil.verify(() -> SUtil.loginRecord(eq(bob), any(), any(), any(), any()));
   }

   // the users query of a case-sensitive collation finds only "bob": since the provider matches
   // ignoring case, "BOB" is checked against "bob"'s password (accepted behavior change)
   @Test
   void caseInsensitive_caseSensitiveCollation_caseVariantCheckedAgainstStoredUser()
      throws Exception
   {
      setUp(false, false, "bob");
      IdentityID bob = new IdentityID("bob", org);

      Principal principal = login("BOB", "pw-bob");
      assertNotNull(principal);
      assertEquals(bob.convertToKey(), principal.getName());

      assertNull(login("BOB", "wrong"));
      assertEquals(List.of(bob, bob), queried, "users query bound");
   }

   // both "bob" and "BOB" stored: an exact name logs in as itself, another variant is refused
   // before any password is checked. (Two such rows can only coexist under a case-sensitive
   // collation or a non-unique column; the latter under a case-insensitive collation is a
   // pre-existing gap of the users query, see Bug #77089 03-fix.md.)
   @Test
   void caseInsensitive_bothVariantsStored_exactOrRefused() throws Exception {
      setUp(false, false, "bob", "BOB");
      IdentityID upperBob = new IdentityID("BOB", org);

      assertNull(login("Bob", "pw-bob"));
      assertEquals(List.of(), queried, "users query bound");

      Principal upper = login("BOB", "pw-BOB");
      assertNotNull(upper);
      assertEquals(upperBob.convertToKey(), upper.getName());
      assertEquals(List.of(upperBob), queried, "users query bound");
   }

   // case-sensitive provider: "BOB" is not "bob", nothing is canonicalized
   @Test
   void caseSensitive_caseVariantLogin_isUnchanged() throws Exception {
      setUp(true, false, "bob");
      IdentityID upperBob = new IdentityID("BOB", org);

      assertNull(login("BOB", "pw-bob"));
      verify(sessionService).createSessionID(XSessionService.USER, "BOB");
      assertEquals(upperBob.convertToKey(), auditedUser());
      assertEquals(List.of(upperBob), queried, "users query bound");
   }

   // a stored "anonymous" user: a case variant is not turned into the anonymous log in, which
   // skips the password check, so its password is still checked
   @Test
   void caseInsensitive_anonymousCaseVariant_passwordStillChecked() throws Exception {
      setUp(false, true, ClientInfo.ANONYMOUS);

      assertNull(login("Anonymous", "wrong"));
      assertFalse(queried.isEmpty(), "users query bound");
   }

   // the local (shell) connect overload checks the stored user's password too
   @Test
   void caseInsensitive_localConnect_checksStoredUser() throws Exception {
      setUp(false, false, "bob");
      IdentityID bob = new IdentityID("bob", org);

      Principal principal = service.authenticate(
         new IdentityID("BOB", org), "pw-bob", null, "127.0.0.1", false);

      assertNotNull(principal);
      assertEquals(bob.convertToKey(), principal.getName());
      assertEquals(List.of(bob), queried, "users query bound");
   }

   private Principal login(String name, String password) throws Exception {
      return service.authenticate(
         new IdentityID(name, org), null, password, "host", "127.0.0.1", "server", null,
         Locale.US, false, true, "http-session", "/login");
   }

   private String auditedUser() {
      ArgumentCaptor<SessionRecord> record = ArgumentCaptor.forClass(SessionRecord.class);
      verify(auditInstance, atLeastOnce()).auditSession(record.capture(), any());
      return record.getValue().getUserID();
   }

   private void setUp(boolean caseSensitive, boolean caseInsensitiveCollation,
                      String... storedNames) throws Exception
   {
      DatabaseAuthenticationProvider db = createProvider(caseSensitive);
      IdentityID[] stored = Arrays.stream(storedNames)
         .map(n -> new IdentityID(n, org)).toArray(IdentityID[]::new);
      doReturn(stored).when(db).getUsers();
      doReturn(new String[] { org }).when(db).getOrganizationIDs();
      doReturn(new String[0]).when(db).getEmails(any(IdentityID.class));
      doReturn(new String[0]).when(db).getUserGroups(any(IdentityID.class));
      doReturn(new String[0]).when(db).getUserGroups(any(IdentityID.class), anyBoolean());
      doReturn(new IdentityID[0]).when(db).getRoles(any(IdentityID.class));

      Map<IdentityID, String> passwords = new LinkedHashMap<>();
      Arrays.stream(stored).forEach(id -> passwords.put(id, "pw-" + id.name));
      queried = DatabaseProviderTestSupport.installUsersQuery(
         db, passwords, caseInsensitiveCollation);

      SecurityProvider provider =
         CompositeSecurityProvider.create(db, mock(AuthorizationProvider.class));

      // the real principal-building code (SecurityEngine.authenticate(ClientInfo, ...))
      SecurityEngine engine =
         spy(new SecurityEngine(mock(LicenseManager.class), mock(Cluster.class)));
      Field users = SecurityEngine.class.getDeclaredField("users");
      users.setAccessible(true);
      users.set(engine, new HashMap<>());
      doReturn(provider).when(engine).getSecurityProvider();

      auditInstance = mock(Audit.class);
      audit = mockStatic(Audit.class, withSettings().strictness(Strictness.LENIENT));
      audit.when(Audit::getInstance).thenReturn(auditInstance);
      sutil = mockStatic(SUtil.class, withSettings().defaultAnswer(CALLS_REAL_METHODS));
      sutil.when(() -> SUtil.loginRecord(any(IdentityID.class), any(), any(), any(), any()))
         .thenAnswer(inv -> null);

      sessionService = mock(XSessionService.class, withSettings().lenient());
      when(sessionService.createSessionID(any(), any())).thenReturn("session");

      service = new AuthenticationService(
         engine, mock(MVManager.class), mock(DataSourceRegistry.class), sessionService,
         mock(LocaleService.class), mock(SessionLicenseServiceProvider.class),
         mock(ApplicationEventPublisher.class), mock(IndexedStorage.class));
   }

   private static DatabaseAuthenticationProvider createProvider(boolean caseSensitive) {
      String old = SreeEnv.getProperty("security.user.caseSensitive");

      try {
         SreeEnv.setProperty("security.user.caseSensitive", Boolean.toString(caseSensitive));
         return spy(new DatabaseAuthenticationProvider());
      }
      finally {
         if(old == null) {
            SreeEnv.remove("security.user.caseSensitive");
         }
         else {
            SreeEnv.setProperty("security.user.caseSensitive", old);
         }
      }
   }
}
