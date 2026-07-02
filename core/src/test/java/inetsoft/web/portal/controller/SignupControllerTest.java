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
package inetsoft.web.portal.controller;

/*
 * SignupController coverage map — [unit] tier, Mockito stubs UserSignupService (no Spring mail I/O)
 *
 * Intentional design notes (NOT bugs)
 *
 * [Design 1] signupWithEmail() catch path still writes session attributes after send failure;
 *            user cannot read the code without mail delivery. Verified by
 *            signupWithEmail_sendFailure_stillStoresSessionAttributes.
 *
 * Known bug — none under test here
 *
 * Out of scope — require live SMTP or GreenMail:
 *
 * [signupWithEmail] end-to-end send success with real Mailer.send()
 * [resendEmailCode] end-to-end send success with real Mailer.send()
 *
 * Cases deferred — need SreeEnv / OrganizationManager / HttpServletResponse:
 *
 * showSignUpPage() / showSignUpDetailPage() ModelAndView and cache headers
 */

import inetsoft.sree.internal.SUtil;
import inetsoft.sree.portal.CustomThemesManager;
import inetsoft.sree.portal.PortalThemesManager;
import inetsoft.sree.security.*;
import inetsoft.web.portal.model.SignupResponseModel;
import inetsoft.web.portal.service.UserSignupService;
import jakarta.mail.MessagingException;
import jakarta.servlet.http.*;
import inetsoft.test.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import javax.naming.NamingException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith({ SpringExtension.class, MockitoExtension.class })
@ContextConfiguration(classes = { BaseTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class SignupControllerTest {

   private static final String SIGNUP_EMAIL = "user@example.com";
   private static final String VALID_CODE = "Ab12Cd";
   private static final String VALID_PASSWORD = "Password1!";

   @Mock private UserSignupService userSignupService;
   @Mock private CustomThemesManager customThemesManager;
   @Mock private PortalThemesManager portalThemesManager;
   @Mock private HttpServletRequest request;
   @Mock private HttpSession session;

   private SignupController signupController;

   @BeforeEach
   void setUp() {
      SUtil.setMultiTenant(true);
      signupController = new SignupController(userSignupService, customThemesManager, portalThemesManager);
   }

   // -------------------------------------------------------------------------
   // signupWithEmail — decision tree (SMTP stubbed on UserSignupService)
   // -------------------------------------------------------------------------

   @Nested
   class SignupWithEmail {

      @Test
      void invalidEmail_returnsFailure() {
         SignupResponseModel result = signupController.signupWithEmail("not-an-email", request);

         assertFalse(result.isSuccess());
         assertNotNull(result.getErrorMessage());
         verifyNoInteractions(userSignupService);
      }

      @Test
      void noAuthenticationChain_returnsFailure() throws Exception {
         when(userSignupService.existAuthenticationChain()).thenReturn(false);

         SignupResponseModel result = signupController.signupWithEmail(SIGNUP_EMAIL, request);

         assertFalse(result.isSuccess());
         assertNotNull(result.getErrorMessage());
         verify(userSignupService, never()).sendEmailVerifyCode(anyString(), anyString());
      }

      @Test
      void emailAlreadyRegistered_returnsFailure() throws Exception {
         when(userSignupService.existAuthenticationChain()).thenReturn(true);
         when(userSignupService.emailExist(SIGNUP_EMAIL)).thenReturn(true);

         SignupResponseModel result = signupController.signupWithEmail(SIGNUP_EMAIL, request);

         assertFalse(result.isSuccess());
         assertNotNull(result.getErrorMessage());
         verify(userSignupService, never()).sendEmailVerifyCode(anyString(), anyString());
      }

      @Test
      void sendFailure_returnsFailure() throws Exception {
         when(request.getSession(false)).thenReturn(session);
         when(userSignupService.existAuthenticationChain()).thenReturn(true);
         when(userSignupService.emailExist(SIGNUP_EMAIL)).thenReturn(false);
         when(userSignupService.generateVerificationCode()).thenReturn(VALID_CODE);
         doThrow(new MessagingException("smtp unavailable"))
            .when(userSignupService).sendEmailVerifyCode(VALID_CODE, SIGNUP_EMAIL);

         SignupResponseModel result = signupController.signupWithEmail(SIGNUP_EMAIL, request);

         assertFalse(result.isSuccess());
         assertNotNull(result.getErrorMessage());
      }

      // [Design 1] session is populated even when Mailer.send() fails
      @Test
      void signupWithEmail_sendFailure_stillStoresSessionAttributes() throws Exception {
         when(request.getSession(false)).thenReturn(session);
         when(userSignupService.existAuthenticationChain()).thenReturn(true);
         when(userSignupService.emailExist(SIGNUP_EMAIL)).thenReturn(false);
         when(userSignupService.generateVerificationCode()).thenReturn(VALID_CODE);
         doThrow(new MessagingException("smtp unavailable"))
            .when(userSignupService).sendEmailVerifyCode(VALID_CODE, SIGNUP_EMAIL);

         signupController.signupWithEmail(SIGNUP_EMAIL, request);

         verify(session).setAttribute(eq("SIGNUP_USER_EMAIL"), eq(SIGNUP_EMAIL));
         verify(session).setAttribute(eq("SIGNUP_EMAIL_CODE"), eq(VALID_CODE));
         verify(session).setAttribute(eq("SIGNUP_EMAIL_CODE_TIME"), anyLong());
      }

      @Test
      void sendSuccess_storesSessionAttributes() throws Exception {
         when(request.getSession(false)).thenReturn(session);
         when(userSignupService.existAuthenticationChain()).thenReturn(true);
         when(userSignupService.emailExist(SIGNUP_EMAIL)).thenReturn(false);
         when(userSignupService.generateVerificationCode()).thenReturn(VALID_CODE);
         doNothing().when(userSignupService).sendEmailVerifyCode(VALID_CODE, SIGNUP_EMAIL);

         SignupResponseModel result = signupController.signupWithEmail(SIGNUP_EMAIL, request);

         assertTrue(result.isSuccess());
         verify(session).setAttribute("SIGNUP_USER_EMAIL", SIGNUP_EMAIL);
         verify(session).setAttribute("SIGNUP_EMAIL_CODE", VALID_CODE);
         verify(session).setAttribute(eq("SIGNUP_EMAIL_CODE_TIME"), anyLong());
      }
   }

   // -------------------------------------------------------------------------
   // submitSignupDetail — session + validation branches
   // -------------------------------------------------------------------------

   @Nested
   class SubmitSignupDetail {

      @Test
      void missingSessionEmail_redirectsToSignupPage() {
         when(request.getSession(false)).thenReturn(session);
         when(session.getAttribute("SIGNUP_EMAIL_CODE")).thenReturn(null);
         when(session.getAttribute("SIGNUP_USER_EMAIL")).thenReturn(null);

         SignupResponseModel result = signupController.submitSignupDetail(
            "First", "Last", VALID_PASSWORD, VALID_CODE, request);

         assertEquals("./signup.html", result.getRedirectUri());
      }

      @Test
      void invalidUserName_returnsFailure() {
         stubSessionWithEmail();
         when(userSignupService.validUserName(SIGNUP_EMAIL)).thenReturn(false);

         SignupResponseModel result = signupController.submitSignupDetail(
            "First", "Last", VALID_PASSWORD, VALID_CODE, request);

         assertFalse(result.isSuccess());
         assertNotNull(result.getErrorMessage());
      }

      @Test
      void invalidPassword_returnsFailure() {
         stubSessionWithEmail();
         when(userSignupService.validUserName(SIGNUP_EMAIL)).thenReturn(true);
         when(userSignupService.validPassword(VALID_PASSWORD)).thenReturn(false);

         SignupResponseModel result = signupController.submitSignupDetail(
            "First", "Last", VALID_PASSWORD, VALID_CODE, request);

         assertFalse(result.isSuccess());
         assertNotNull(result.getErrorMessage());
      }

      @Test
      void expiredVerificationCode_returnsFailure() {
         stubValidSession();
         when(userSignupService.validUserName(SIGNUP_EMAIL)).thenReturn(true);
         when(userSignupService.validPassword(VALID_PASSWORD)).thenReturn(true);
         when(session.getAttribute("SIGNUP_EMAIL_CODE_TIME"))
            .thenReturn(System.currentTimeMillis() - 3_600_001L);

         SignupResponseModel result = signupController.submitSignupDetail(
            "First", "Last", VALID_PASSWORD, VALID_CODE, request);

         assertFalse(result.isSuccess());
         assertNotNull(result.getErrorMessage());
      }

      @Test
      void wrongVerificationCode_returnsFailure() {
         stubValidSession();
         when(userSignupService.validUserName(SIGNUP_EMAIL)).thenReturn(true);
         when(userSignupService.validPassword(VALID_PASSWORD)).thenReturn(true);
         when(userSignupService.isValidEmailCode(VALID_CODE)).thenReturn(true);

         SignupResponseModel result = signupController.submitSignupDetail(
            "First", "Last", VALID_PASSWORD, "Zz99Yy", request);

         assertFalse(result.isSuccess());
         assertNotNull(result.getErrorMessage());
      }

      @Test
      void userAlreadyExists_returnsFailure() {
         stubValidSession();
         stubValidSignupFields();
         when(userSignupService.existAuthenticationChain()).thenReturn(true);
         when(userSignupService.userExist(any(IdentityID.class))).thenReturn(true);

         SignupResponseModel result = signupController.submitSignupDetail(
            "First", "Last", VALID_PASSWORD, VALID_CODE, request);

         assertFalse(result.isSuccess());
         assertNotNull(result.getErrorMessage());
      }

      @Test
      void createUserSuccess_clearsSessionAttributes() {
         stubValidSession();
         stubValidSignupFields();
         when(userSignupService.existAuthenticationChain()).thenReturn(true);
         when(userSignupService.userExist(any(IdentityID.class))).thenReturn(false);
         when(userSignupService.emailExist(SIGNUP_EMAIL)).thenReturn(false);
         when(userSignupService.createUser(any(IdentityID.class), eq(VALID_PASSWORD),
            eq(SIGNUP_EMAIL), any(SRPrincipal.class)))
            .thenReturn(new FSUser(new IdentityID(SIGNUP_EMAIL, Organization.getSelfOrganizationID())));

         SignupResponseModel result = signupController.submitSignupDetail(
            "First", "Last", VALID_PASSWORD, VALID_CODE, request);

         assertTrue(result.isSuccess());
         assertEquals(SIGNUP_EMAIL, result.getEmail());
         verify(session).removeAttribute("SIGNUP_USER_EMAIL");
         verify(session).removeAttribute("SIGNUP_EMAIL_CODE");
      }

      private void stubSessionWithEmail() {
         when(request.getSession(false)).thenReturn(session);
         when(session.getAttribute("SIGNUP_USER_EMAIL")).thenReturn(SIGNUP_EMAIL);
         when(session.getAttribute("SIGNUP_EMAIL_CODE")).thenReturn(VALID_CODE);
      }

      private void stubValidSession() {
         stubSessionWithEmail();
         when(session.getAttribute("SIGNUP_EMAIL_CODE_TIME")).thenReturn(System.currentTimeMillis());
      }

      private void stubValidSignupFields() {
         when(userSignupService.validUserName(SIGNUP_EMAIL)).thenReturn(true);
         when(userSignupService.validPassword(VALID_PASSWORD)).thenReturn(true);
         when(userSignupService.isValidEmailCode(VALID_CODE)).thenReturn(true);
      }
   }

   // -------------------------------------------------------------------------
   // resendEmailCode — session guard + rate limit + stubbed send
   // -------------------------------------------------------------------------

   @Nested
   class ResendEmailCode {

      @Test
      void missingSession_returnsFailure() {
         when(request.getSession(false)).thenReturn(null);

         SignupResponseModel result = signupController.resendEmailCode(request);

         assertFalse(result.isSuccess());
         assertEquals("error request", result.getErrorMessage());
      }

      @Test
      void resendTooSoon_returnsFailure() throws Exception {
         when(request.getSession(false)).thenReturn(session);
         when(session.getAttribute("SIGNUP_USER_EMAIL")).thenReturn(SIGNUP_EMAIL);
         when(session.getAttribute("SIGNUP_EMAIL_CODE")).thenReturn(VALID_CODE);
         when(session.getAttribute("SIGNUP_EMAIL_CODE_TIME")).thenReturn(System.currentTimeMillis());

         SignupResponseModel result = signupController.resendEmailCode(request);

         assertFalse(result.isSuccess());
         assertNotNull(result.getErrorMessage());
         verify(userSignupService, never()).sendEmailVerifyCode(anyString(), anyString());
      }

      @Test
      void sendFailure_returnsFailure() throws Exception {
         stubResendEligibleSession();
         when(userSignupService.generateVerificationCode()).thenReturn("Xy09Zw");
         doThrow(new NamingException("smtp unavailable"))
            .when(userSignupService).sendEmailVerifyCode("Xy09Zw", SIGNUP_EMAIL);

         SignupResponseModel result = signupController.resendEmailCode(request);

         assertFalse(result.isSuccess());
         assertNotNull(result.getErrorMessage());
      }

      @Test
      void sendSuccess_updatesSessionCode() throws Exception {
         stubResendEligibleSession();
         when(userSignupService.generateVerificationCode()).thenReturn("Xy09Zw");
         doNothing().when(userSignupService).sendEmailVerifyCode("Xy09Zw", SIGNUP_EMAIL);

         SignupResponseModel result = signupController.resendEmailCode(request);

         assertTrue(result.isSuccess());
         verify(session).setAttribute("SIGNUP_EMAIL_CODE", "Xy09Zw");
         verify(session).setAttribute(eq("SIGNUP_EMAIL_CODE_TIME"), anyLong());
      }

      private void stubResendEligibleSession() {
         when(request.getSession(false)).thenReturn(session);
         when(session.getAttribute("SIGNUP_USER_EMAIL")).thenReturn(SIGNUP_EMAIL);
         when(session.getAttribute("SIGNUP_EMAIL_CODE")).thenReturn(VALID_CODE);
         when(session.getAttribute("SIGNUP_EMAIL_CODE_TIME"))
            .thenReturn(System.currentTimeMillis() - 120_000L);
      }
   }
}
