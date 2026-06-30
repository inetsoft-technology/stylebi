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
package inetsoft.web.portal.service;

/*
 * UserSignupService coverage map — [unit] tier, Mockito stubs AuthenticationProviderService
 *
 * Accepted risk — validPassword(null) / validUserName(null) NPE at Pattern.matcher() (lines 143, 150):
 * Not reachable in the Portal signup flow — SignupController passes a session-validated email as the
 * user name and a required @RequestParam password (missing param → HTTP 400; empty → "").
 * Parameterized validation tests intentionally omit null; fix only if a new non-UI caller is added.
 *
 * Cases deferred — require integration context or live I/O:
 *
 * [UserSignupService] sendEmailVerifyCode() -> needs SreeEnv mail properties + Mailer.send()
 * [UserSignupService] checkAutoRegisterUser (former integration test) -> SecurityEngine + SreeHome
 *
 * Mixed tier note: UserSignupService eagerly constructs Mailer (SreeEnv in constructor).
 * SreeEnv is stubbed via mockStatic in setUp so the rest of the suite stays [unit].
 */

import inetsoft.sree.SreeEnv;
import inetsoft.sree.internal.SUtil;
import inetsoft.sree.security.*;
import inetsoft.util.audit.Audit;
import inetsoft.util.audit.IdentityInfoRecord;
import inetsoft.web.admin.security.AuthenticationProviderService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@Tag("core")
class UserSignupServiceTest {

   private static final String SIGNUP_EMAIL = "user@example.com";
   private static final String VALID_CODE = "Ab12Cd";
   private static final String VALID_PASSWORD = "Password1!";
   private static final String GOOGLE_USER_ID = "google-user-123";
   private static final IdentityID DEFAULT_ORG_USER =
      new IdentityID(SIGNUP_EMAIL, Organization.getDefaultOrganizationID());

   @Mock private AuthenticationProviderService authenticationProviderService;
   @Mock private AuthenticationChain authenticationChain;

   private UserSignupService userSignupService;
   private MockedStatic<SreeEnv> sreeEnvMock;

   @BeforeEach
   void setUp() {
      sreeEnvMock = Mockito.mockStatic(SreeEnv.class);
      sreeEnvMock.when(() -> SreeEnv.getProperty(anyString())).thenReturn(null);
      sreeEnvMock.when(() -> SreeEnv.getProperty(anyString(), anyString()))
         .thenAnswer(invocation -> invocation.getArgument(1));
      userSignupService = new UserSignupService(authenticationProviderService);
   }

   @AfterEach
   void tearDown() {
      if(sreeEnvMock != null) {
         sreeEnvMock.close();
      }
   }

   // -------------------------------------------------------------------------
   // Validation — pure logic
   // -------------------------------------------------------------------------

   @Nested
   class Validation {

      @ParameterizedTest
      @ValueSource(strings = { VALID_PASSWORD })
      void validPassword_acceptedPassword_returnsTrue(String password) {
         assertTrue(userSignupService.validPassword(password));
      }

      static Stream<Arguments> invalidPasswordCases() {
         return Stream.of(
            // empty
            Arguments.of(""),
            // too short
            Arguments.of("Pass1!"),
            // too long (73 chars)
            Arguments.of("A1!" + "a".repeat(70)),
            // missing uppercase
            Arguments.of("password1!"),
            // missing lowercase
            Arguments.of("PASSWORD1!"),
            // missing digit
            Arguments.of("Password!!"),
            // missing special character
            Arguments.of("Password12")
         );
      }

      @ParameterizedTest
      @MethodSource("invalidPasswordCases")
      void validPassword_rejectedPassword_returnsFalse(String password) {
         assertFalse(userSignupService.validPassword(password));
      }

      static Stream<Arguments> validUserNameCases() {
         return Stream.of(
            Arguments.of("alice"),
            Arguments.of("user@example.com"),
            Arguments.of("a".repeat(38))
         );
      }

      @ParameterizedTest
      @MethodSource("validUserNameCases")
      void validUserName_acceptedName_returnsTrue(String userName) {
         assertTrue(userSignupService.validUserName(userName));
      }

      static Stream<Arguments> invalidUserNameCases() {
         return Stream.of(
            Arguments.of(""),
            // '!' is in the excluded set; '@' is permitted (emails are valid usernames)
            Arguments.of("user@name!"),
            // length must be strictly less than 39
            Arguments.of("a".repeat(39))
         );
      }

      @ParameterizedTest
      @MethodSource("invalidUserNameCases")
      void validUserName_rejectedName_returnsFalse(String userName) {
         assertFalse(userSignupService.validUserName(userName));
      }

      @ParameterizedTest
      @ValueSource(strings = { VALID_CODE, "000000", "zzZZ99" })
      void isValidEmailCode_validCode_returnsTrue(String code) {
         assertTrue(userSignupService.isValidEmailCode(code));
      }

      static Stream<Arguments> invalidEmailCodeCases() {
         return Stream.of(
            Arguments.of((String) null),
            Arguments.of(""),
            // wrong length
            Arguments.of("Ab12C"),
            Arguments.of("Ab12Cde"),
            // disallowed character
            Arguments.of("Ab12C!")
         );
      }

      @ParameterizedTest
      @MethodSource("invalidEmailCodeCases")
      void isValidEmailCode_invalidCode_returnsFalse(String code) {
         assertFalse(userSignupService.isValidEmailCode(code));
      }

      @Test
      void generateVerificationCode_returnsSixAlphanumericCharacters() {
         String code = userSignupService.generateVerificationCode();

         assertEquals(6, code.length());
         assertTrue(code.chars().allMatch(ch ->
            "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789"
               .indexOf(ch) >= 0));
      }
   }

   // -------------------------------------------------------------------------
   // Chain lookup — mocked AuthenticationProviderService / AuthenticationChain
   // -------------------------------------------------------------------------

   @Nested
   class ChainLookup {

      @Test
      void existAuthenticationChain_chainPresent_returnsTrue() {
         when(authenticationProviderService.getAuthenticationChain())
            .thenReturn(Optional.of(authenticationChain));

         assertTrue(userSignupService.existAuthenticationChain());
      }

      @Test
      void existAuthenticationChain_chainAbsent_returnsFalse() {
         when(authenticationProviderService.getAuthenticationChain()).thenReturn(Optional.empty());

         assertFalse(userSignupService.existAuthenticationChain());
      }

      @Test
      void getAuthenticationChain_chainPresent_returnsChain() {
         when(authenticationProviderService.getAuthenticationChain())
            .thenReturn(Optional.of(authenticationChain));

         assertSame(authenticationChain, userSignupService.getAuthenticationChain());
      }

      @Test
      void getAuthenticationChain_chainAbsent_returnsNull() {
         when(authenticationProviderService.getAuthenticationChain()).thenReturn(Optional.empty());

         assertNull(userSignupService.getAuthenticationChain());
      }

      @Test
      void userExist_userFound_returnsTrue() {
         User user = mock(User.class);
         when(authenticationProviderService.getAuthenticationChain())
            .thenReturn(Optional.of(authenticationChain));
         when(authenticationChain.getUser(DEFAULT_ORG_USER)).thenReturn(user);

         assertTrue(userSignupService.userExist(DEFAULT_ORG_USER));
      }

      @Test
      void userExist_userMissing_returnsFalse() {
         when(authenticationProviderService.getAuthenticationChain())
            .thenReturn(Optional.of(authenticationChain));
         when(authenticationChain.getUser(DEFAULT_ORG_USER)).thenReturn(null);

         assertFalse(userSignupService.userExist(DEFAULT_ORG_USER));
      }

      @Test
      void userExist_noChain_returnsFalse() {
         when(authenticationProviderService.getAuthenticationChain()).thenReturn(Optional.empty());

         assertFalse(userSignupService.userExist(DEFAULT_ORG_USER));
      }

      @Test
      void emailExist_matchingEmailAcrossProvider_returnsTrue() {
         AuthenticationProvider provider = mock(AuthenticationProvider.class);
         User user = mock(User.class);

         when(authenticationProviderService.getAuthenticationChain())
            .thenReturn(Optional.of(authenticationChain));
         when(authenticationChain.getProviders()).thenReturn(List.of(provider));
         when(provider.getUsers()).thenReturn(new IdentityID[] { DEFAULT_ORG_USER });
         when(provider.getUser(DEFAULT_ORG_USER)).thenReturn(user);
         when(user.getEmails()).thenReturn(new String[] { SIGNUP_EMAIL });

         assertTrue(userSignupService.emailExist(SIGNUP_EMAIL));
      }

      @Test
      void emailExist_noMatchingEmail_returnsFalse() {
         AuthenticationProvider provider = mock(AuthenticationProvider.class);
         User user = mock(User.class);

         when(authenticationProviderService.getAuthenticationChain())
            .thenReturn(Optional.of(authenticationChain));
         when(authenticationChain.getProviders()).thenReturn(List.of(provider));
         when(provider.getUsers()).thenReturn(new IdentityID[] { DEFAULT_ORG_USER });
         when(provider.getUser(DEFAULT_ORG_USER)).thenReturn(user);
         when(user.getEmails()).thenReturn(new String[] { "other@example.com" });

         assertFalse(userSignupService.emailExist(SIGNUP_EMAIL));
      }

      @Test
      void emailExist_noChain_returnsFalse() {
         when(authenticationProviderService.getAuthenticationChain()).thenReturn(Optional.empty());

         assertFalse(userSignupService.emailExist(SIGNUP_EMAIL));
      }

      @Test
      void getUserByGoogleSSOId_matchFound_returnsUser() {
         AuthenticationProvider provider = mock(AuthenticationProvider.class);
         FSUser user = new FSUser(DEFAULT_ORG_USER);
         user.setGoogleSSOId(GOOGLE_USER_ID);

         when(authenticationProviderService.getAuthenticationChain())
            .thenReturn(Optional.of(authenticationChain));
         when(authenticationChain.stream()).thenReturn(Stream.of(provider));
         when(provider.getUsers()).thenReturn(new IdentityID[] { DEFAULT_ORG_USER });
         when(provider.getUser(DEFAULT_ORG_USER)).thenReturn(user);

         Optional<User> result = userSignupService.getUserByGoogleSSOId(GOOGLE_USER_ID);

         assertTrue(result.isPresent());
         assertSame(user, result.get());
      }

      @ParameterizedTest
      @NullAndEmptySource
      void getUserByGoogleSSOId_blankId_returnsEmpty(String googleUserId) {
         when(authenticationProviderService.getAuthenticationChain()).thenReturn(Optional.empty());

         assertTrue(userSignupService.getUserByGoogleSSOId(googleUserId).isEmpty());
      }

      @Test
      void getUserByGoogleSSOId_noChain_returnsEmpty() {
         when(authenticationProviderService.getAuthenticationChain()).thenReturn(Optional.empty());

         assertTrue(userSignupService.getUserByGoogleSSOId(GOOGLE_USER_ID).isEmpty());
      }
   }

   // -------------------------------------------------------------------------
   // autoRegisterUser — orchestration via mocked chain
   // -------------------------------------------------------------------------

   @Nested
   class AutoRegister {

      private MockedStatic<SUtil> sutilMock;

      @BeforeEach
      void stubMultiTenant() {
         sutilMock = Mockito.mockStatic(SUtil.class, Mockito.CALLS_REAL_METHODS);
         sutilMock.when(SUtil::isMultiTenant).thenReturn(false);
      }

      @AfterEach
      void closeSutilMock() {
         if(sutilMock != null) {
            sutilMock.close();
         }
      }

      @Test
      void autoRegisterUser_emptyGoogleUserId_isNoOp() {
         when(authenticationProviderService.getAuthenticationChain())
            .thenReturn(Optional.of(authenticationChain));

         userSignupService.autoRegisterUser("", SIGNUP_EMAIL);

         verify(authenticationChain, never()).getProviders();
      }

      @Test
      void autoRegisterUser_noChain_isNoOp() {
         when(authenticationProviderService.getAuthenticationChain()).thenReturn(Optional.empty());

         userSignupService.autoRegisterUser(GOOGLE_USER_ID, SIGNUP_EMAIL);

         verify(authenticationProviderService).getAuthenticationChain();
         verifyNoMoreInteractions(authenticationProviderService);
      }

      @Test
      void autoRegisterUser_existingSsoUserMissingEmail_updatesUser() {
         EditableAuthenticationProvider provider = mock(EditableAuthenticationProvider.class);
         FSUser existing = new FSUser(DEFAULT_ORG_USER);
         existing.setGoogleSSOId(GOOGLE_USER_ID);
         existing.setEmails(new String[] { "other@example.com" });

         when(authenticationProviderService.getAuthenticationChain())
            .thenReturn(Optional.of(authenticationChain));
         when(authenticationChain.getProviders()).thenReturn(List.of(provider));
         when(provider.getUsers()).thenReturn(new IdentityID[] { DEFAULT_ORG_USER });
         when(provider.getUser(DEFAULT_ORG_USER)).thenReturn(existing);

         userSignupService.autoRegisterUser(GOOGLE_USER_ID, SIGNUP_EMAIL);

         verify(provider).setUser(eq(DEFAULT_ORG_USER), same(existing));
         assertArrayEquals(
            new String[] { "other@example.com", SIGNUP_EMAIL },
            existing.getEmails());
      }

      @Test
      void autoRegisterUser_noExistingUser_delegatesToCreateUser() {
         UserSignupService spyService = spy(userSignupService);
         when(authenticationProviderService.getAuthenticationChain())
            .thenReturn(Optional.of(authenticationChain));
         when(authenticationChain.getProviders()).thenReturn(Collections.emptyList());

         spyService.autoRegisterUser(GOOGLE_USER_ID, SIGNUP_EMAIL);

         verify(spyService).createUser(
            eq(DEFAULT_ORG_USER),
            isNull(),
            eq(SIGNUP_EMAIL),
            eq(true),
            eq(GOOGLE_USER_ID),
            isNull());
      }
   }

   // -------------------------------------------------------------------------
   // createUser — editable provider + Audit static
   // -------------------------------------------------------------------------

   @Nested
   class CreateUser {

      @Test
      void createUser_noChain_returnsNull() {
         when(authenticationProviderService.getAuthenticationChain()).thenReturn(Optional.empty());

         assertNull(userSignupService.createUser(
            DEFAULT_ORG_USER, VALID_PASSWORD, SIGNUP_EMAIL, mock(SRPrincipal.class)));
      }

      @Test
      void createUser_noEditableProvider_returnsNull() {
         AuthenticationProvider readOnlyProvider = mock(AuthenticationProvider.class);

         when(authenticationProviderService.getAuthenticationChain())
            .thenReturn(Optional.of(authenticationChain));
         when(authenticationChain.getProviders()).thenReturn(List.of(readOnlyProvider));

         assertNull(userSignupService.createUser(
            DEFAULT_ORG_USER, VALID_PASSWORD, SIGNUP_EMAIL, mock(SRPrincipal.class)));
      }

      @Test
      void createUser_withPrincipal_persistsUserAndAudits() {
         EditableAuthenticationProvider editProvider = mock(EditableAuthenticationProvider.class);
         SRPrincipal principal = mock(SRPrincipal.class);
         when(principal.getProperty("SignUpFirstName")).thenReturn("Alice");
         when(principal.getProperty("SignUpLastName")).thenReturn("Smith");
         when(principal.getProperty("SignupCookies")).thenReturn(null);

         when(authenticationProviderService.getAuthenticationChain())
            .thenReturn(Optional.of(authenticationChain));
         when(authenticationChain.getProviders()).thenReturn(List.of(editProvider));
         when(editProvider.getRoles()).thenReturn(new IdentityID[0]);

         try(MockedStatic<SUtil> sutilMock = Mockito.mockStatic(SUtil.class, Mockito.CALLS_REAL_METHODS);
             MockedStatic<Audit> auditMock = Mockito.mockStatic(Audit.class);
             MockedConstruction<SRPrincipal> ignored = Mockito.mockConstruction(SRPrincipal.class))
         {
            sutilMock.when(() -> SUtil.getIdentityInfoRecord(
                  any(IdentityID.class), anyInt(), anyString(), any(), anyString()))
               .thenReturn(mock(IdentityInfoRecord.class));

            Audit audit = mock(Audit.class);
            auditMock.when(Audit::getInstance).thenReturn(audit);

            User created = userSignupService.createUser(
               DEFAULT_ORG_USER, null, SIGNUP_EMAIL, true, GOOGLE_USER_ID, principal);

            assertNotNull(created);
            assertEquals(SIGNUP_EMAIL, created.getName());
            assertEquals(GOOGLE_USER_ID, created.getGoogleSSOId());
            verify(editProvider).addUser(any(FSUser.class));
            verify(audit).auditIdentityInfo(any(), any());
         }
      }

      @Test
      void createUser_nullPrincipal_persistsUser() {
         EditableAuthenticationProvider editProvider = mock(EditableAuthenticationProvider.class);

         when(authenticationProviderService.getAuthenticationChain())
            .thenReturn(Optional.of(authenticationChain));
         when(authenticationChain.getProviders()).thenReturn(List.of(editProvider));
         when(editProvider.getRoles()).thenReturn(new IdentityID[0]);

         try(MockedStatic<SUtil> sutilMock = Mockito.mockStatic(SUtil.class, Mockito.CALLS_REAL_METHODS);
             MockedStatic<Audit> auditMock = Mockito.mockStatic(Audit.class);
             MockedConstruction<SRPrincipal> ignored = Mockito.mockConstruction(SRPrincipal.class))
         {
            sutilMock.when(() -> SUtil.getIdentityInfoRecord(
                  any(IdentityID.class), anyInt(), anyString(), any(), anyString()))
               .thenReturn(mock(IdentityInfoRecord.class));

            Audit audit = mock(Audit.class);
            auditMock.when(Audit::getInstance).thenReturn(audit);

            User created = userSignupService.createUser(
               DEFAULT_ORG_USER, null, SIGNUP_EMAIL, true, GOOGLE_USER_ID, null);

            assertNotNull(created);
            verify(editProvider).addUser(any(FSUser.class));
            verify(audit).auditIdentityInfo(any(), any());
         }
      }
   }
}
