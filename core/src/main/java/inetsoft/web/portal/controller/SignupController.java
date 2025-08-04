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

import inetsoft.sree.SreeEnv;
import inetsoft.sree.internal.SUtil;
import inetsoft.sree.portal.CustomThemesManager;
import inetsoft.sree.portal.PortalThemesManager;
import inetsoft.sree.security.*;
import inetsoft.util.Catalog;
import inetsoft.util.Tool;
import inetsoft.web.portal.model.SignupResponseModel;
import inetsoft.web.portal.service.UserSignupService;
import inetsoft.web.viewsheet.service.LinkUri;
import jakarta.servlet.http.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.ModelAndView;

import java.util.Date;
import java.util.Objects;

@Controller
public class SignupController {
   public SignupController(UserSignupService userSignupService) {
      this.userSignupService = userSignupService;
   }

   /**
    * Shows the login page.
    *
    * @return the login page model and view.
    */
   @GetMapping("/signup.html")
   public ModelAndView showSignUpPage(HttpServletResponse response, @LinkUri String linkUri) {
      ModelAndView model = new ModelAndView("signup");;

      PortalThemesManager manager = PortalThemesManager.getManager();
      CustomThemesManager themes = CustomThemesManager.getManager();
      boolean customLogo = manager.hasCustomLogo(OrganizationManager.getInstance().getCurrentOrgID());

      model.addObject("customLogo", customLogo);
      boolean isCustomTheme = !Tool.isEmptyString(themes.getSelectedTheme()) &&
                              !"default".equals(themes.getSelectedTheme());
      model.addObject("customTheme", isCustomTheme);

      boolean googleSignInEnabled = SreeEnv.getBooleanProperty("security.googleSignIn.enabled");

      if(googleSignInEnabled) {
         model.addObject("gClientId", SreeEnv.getProperty("styleBI.google.openid.client.id"));
         model.addObject("gLoginUri", linkUri + "login/googleSSO");
         model.addObject("gScopes",
            SreeEnv.getProperty("styleBI.google.openid.scopes", "openid email profile"));
      }

      String header = CacheControl.noCache()
         .cachePrivate()
         .mustRevalidate()
         .getHeaderValue();
      response.setHeader(HttpHeaders.CACHE_CONTROL, header);

      return model;
   }

   /**
    * Shows the login page.
    *
    * @return the login page model and view.
    */
   @GetMapping("/signupDetail.html")
   public ModelAndView showSignUpDetailPage(
      HttpServletRequest request, HttpServletResponse response, @LinkUri String linkUri)
   {
      HttpSession session = request.getSession(false);
      Object emailObj = session.getAttribute(SIGNUP_USER_EMAIL);
      Object codeObj = session.getAttribute(SIGNUP_EMAIL_CODE);

      if(!(emailObj instanceof String) || !Tool.matchEmail((String) emailObj) ||
         !(codeObj instanceof String) || !userSignupService.isValidEmailCode((String) codeObj))
      {
         return showSignUpPage(response, linkUri);
      }

      ModelAndView model = new ModelAndView("signupDetail");
      PortalThemesManager manager = PortalThemesManager.getManager();
      CustomThemesManager themes = CustomThemesManager.getManager();
      boolean customLogo = manager.hasCustomLogo(OrganizationManager.getInstance().getCurrentOrgID());

      model.addObject("customLogo", customLogo);
      model.addObject("linkUri", linkUri);
      boolean isCustomTheme = !Tool.isEmptyString(themes.getSelectedTheme()) &&
         !"default".equals(themes.getSelectedTheme());
      model.addObject("customTheme", isCustomTheme);

      String header = CacheControl.noCache()
         .cachePrivate()
         .mustRevalidate()
         .getHeaderValue();
      response.setHeader(HttpHeaders.CACHE_CONTROL, header);

      return model;
   }

   /**
    * Shows the login page.
    *
    * @return the login page model and view.
    */
   @GetMapping("/signup/withEmail")
   @ResponseBody
   public SignupResponseModel signupWithEmail(@RequestParam(name = "email") String signupEmail,
                                               HttpServletRequest request)
   {
      SignupResponseModel result = new SignupResponseModel();
      Catalog catalog = Catalog.getCatalog();

      if(!Tool.matchEmail(signupEmail)) {
         result.setSuccess(false);
         result.setErrorMessage(catalog.getString("signup.email.required"));

         return result;
      }

      if(!userSignupService.existAuthenticationChain()) {
         result.setSuccess(false);
         result.setErrorMessage(catalog.getString("signup.not.support"));

         return result;
      }

      if(userSignupService.emailExist(signupEmail)) {
         result.setSuccess(false);
         result.setErrorMessage(catalog.getString("signup.email.taken"));

         return result;
      }

      HttpSession session = request.getSession(false);
      String code = userSignupService.generateVerificationCode();

      try {
         userSignupService.sendEmailVerifyCode(code, signupEmail);
      }
      catch(Exception e) {
         LOG.warn(e.getMessage(), e);
         result.setSuccess(false);
         result.setErrorMessage(catalog.getString("signup.email.code.error"));
      }

      session.setAttribute(SIGNUP_USER_EMAIL, signupEmail);
      session.setAttribute(SIGNUP_EMAIL_CODE, code);
      session.setAttribute(SIGNUP_EMAIL_CODE_TIME, new Date().getTime());

      return result;
   }

   /**
    * Shows the login page.
    *
    * @return the login page model and view.
    */
   @GetMapping("/signup/userDetail")
   @ResponseBody
   public SignupResponseModel submitSignupDetail(@RequestParam(name = "SignUpFirstName") String firstName,
                                                 @RequestParam(name = "SignUpLastName") String lastName,
                                                 @RequestParam(name = "password") String password,
                                                 @RequestParam(name = "code") String code,
                                                 HttpServletRequest request)
   {

      SignupResponseModel result = new SignupResponseModel();
      HttpSession session = request.getSession(false);
      Object currentEmailCode = session.getAttribute(SIGNUP_EMAIL_CODE);
      Object emailObj = session.getAttribute(SIGNUP_USER_EMAIL);

      if(!(emailObj instanceof String) || !Tool.matchEmail((String) emailObj)) {
         result.setRedirectUri("./signup.html");

         return result;
      }

      String email = (String) emailObj;
      result.setEmail(email);

      IdentityID userID;

      if(SUtil.isMultiTenant()) {
         userID = new IdentityID(email, Organization.getSelfOrganizationID());
      }
      else {
         userID = new IdentityID(email, Organization.getDefaultOrganizationID());
      }


      Catalog catalog = Catalog.getCatalog();

      if(!userSignupService.validUserName(userID.name)) {
         result.setSuccess(false);
         result.setErrorMessage(catalog.getString("signup.username.contains.special") + " " +
            catalog.getString("or") + catalog.getString("signup.username.tooLong"));

         return result;
      }

      if(!userSignupService.validPassword(password)) {
         result.setSuccess(false);
         result.setErrorMessage(catalog.getString("signup.password.invalid") + " " +
            catalog.getString("or") + catalog.getString("signup.password.tooLong"));

         return result;
      }

      Object codeTime = session.getAttribute(SIGNUP_EMAIL_CODE_TIME);

      if(!(codeTime instanceof Number) ||
         new Date().getTime() - ((Number) codeTime).longValue() > 3600000)
      {
         result.setSuccess(false);
         result.setErrorMessage(catalog.getString("signup.lunchCode.timeout"));

         return result;
      }

      if(!(currentEmailCode instanceof String) ||
         !userSignupService.isValidEmailCode((String) currentEmailCode) ||
         !Objects.equals(session.getAttribute(SIGNUP_EMAIL_CODE), code))
      {
         result.setSuccess(false);
         result.setErrorMessage(catalog.getString("signup.lunchCode.error"));

         return result;
      }

      if(!userSignupService.existAuthenticationChain()) {
         result.setSuccess(false);
         result.setErrorMessage(catalog.getString("signup.create.user.error"));
      }
      else {
         if(userSignupService.userExist(userID)) {
            result.setSuccess(false);
            result.setErrorMessage(catalog.getString("signup.username.exist"));
         }
         else {
            if(userSignupService.emailExist(email)) {
               result.setSuccess(false);
               result.setErrorMessage(catalog.getString("signup.email.taken"));

               return result;
            }

            String cookiesString = SUtil.writeCookiesString(request.getCookies());
            SRPrincipal principal = new SRPrincipal(userID);
            principal.setProperty("SignUpFirstName", firstName);
            principal.setProperty("SignUpLastName", lastName);
            principal.setProperty("SignupCookies", cookiesString);

            User newUser = userSignupService.createUser(userID, password, email, principal);

            if(newUser == null) {
               result.setSuccess(false);
               result.setErrorMessage(catalog.getString("signup.create.user.error"));
            }
            else {
               session.removeAttribute(SIGNUP_USER_EMAIL);
               session.removeAttribute(SIGNUP_EMAIL_CODE);
            }
         }
      }

      return result;
   }

   /**
    * Resend the email code.
    *
    */
   @GetMapping("/signup/resendEmailCode")
   @ResponseBody
   public SignupResponseModel resendEmailCode(HttpServletRequest request) {
      HttpSession session = request.getSession(false);
      SignupResponseModel responseModel = new SignupResponseModel();
      Object emailObj = session.getAttribute(SIGNUP_USER_EMAIL);
      Object codeTime = session.getAttribute(SIGNUP_EMAIL_CODE_TIME);

      if(session == null || !(emailObj instanceof String) ||
         session.getAttribute(SIGNUP_EMAIL_CODE) == null ||
         codeTime == null)
      {
         responseModel.setSuccess(false);
         responseModel.setErrorMessage("error request");

         return responseModel;
      }

      Catalog catalog = Catalog.getCatalog();

      if(codeTime instanceof Number &&
         new Date().getTime() - ((Number) codeTime).longValue() >= 60000)
      {
         String code = userSignupService.generateVerificationCode();
         String signupEmail = (String) emailObj;

         try {
            userSignupService.sendEmailVerifyCode(code, signupEmail);
            session.setAttribute(SIGNUP_EMAIL_CODE, code);
            session.setAttribute(SIGNUP_EMAIL_CODE_TIME, new Date().getTime());
         }
         catch(Exception e) {
            LOG.warn(e.getMessage(), e);
            responseModel.setSuccess(false);
            responseModel.setErrorMessage(catalog.getString("signup.email.verify.code.send.error"));
         }
      }
      else {
         responseModel.setSuccess(false);
         responseModel.setErrorMessage(catalog.getString("signup.email.verify.code.frequently"));
      }

      return responseModel;
   }

   private final UserSignupService userSignupService;
   private final static String SIGNUP_USER_EMAIL = "SIGNUP_USER_EMAIL";
   private final static String SIGNUP_EMAIL_CODE = "SIGNUP_EMAIL_CODE";
   private final static String SIGNUP_EMAIL_CODE_TIME = "SIGNUP_EMAIL_CODE_TIME";
   private static final Logger LOG = LoggerFactory.getLogger(SignupController.class);
}
