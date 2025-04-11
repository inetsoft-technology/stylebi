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

import inetsoft.sree.SreeEnv;
import inetsoft.sree.internal.Mailer;
import inetsoft.sree.internal.SUtil;
import inetsoft.sree.security.*;
import inetsoft.uql.XPrincipal;
import inetsoft.util.Tool;
import inetsoft.util.audit.Audit;
import inetsoft.util.audit.IdentityInfoRecord;
import inetsoft.web.admin.security.AuthenticationProviderService;

import java.text.MessageFormat;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import javax.naming.NamingException;

import jakarta.mail.MessagingException;
import org.apache.commons.lang.ArrayUtils;
import org.springframework.stereotype.Service;

@Service
public class UserSignupService {
   public UserSignupService(AuthenticationProviderService authenticationProviderService) {
      this.authenticationProviderService = authenticationProviderService;
   }

   public boolean userExist(IdentityID userName) {
      AuthenticationChain authenticationChain = getAuthenticationChain();

      if(authenticationChain == null) {
         return false;
      }

      return authenticationChain.getUser(userName) != null;
   }

   public boolean emailExist(String email) {
      AuthenticationChain authenticationChain = getAuthenticationChain();

      if(authenticationChain == null) {
         return false;
      }

      return authenticationChain.getProviders()
         .stream()
         .anyMatch(provider -> {
            IdentityID[] users = provider.getUsers();

            if(users == null) {
               return false;
            }

            for(IdentityID userName : users) {
               User user = provider.getUser(userName);

               if(user == null) {
                  continue;
               }

               if(ArrayUtils.contains(user.getEmails(), email)) {
                  return true;
               }
            }

            return false;
         });
   }


   /**
    * generate the email code.
    *
    * @return
    */
   public String generateVerificationCode() {
      StringBuilder codeBuilder = new StringBuilder();
      Random random = new Random();

      for (int i = 0; i < CODE_LENGTH; i++) {
         int index = random.nextInt(CHARACTERS.length());
         codeBuilder.append(CHARACTERS.charAt(index));
      }

      return codeBuilder.toString();
   }

   /**
    * Check whether is a valid email code.
    *
    * @param code
    * @return
    */
   public boolean isValidEmailCode(String code) {
      if(code == null || code.length() != CODE_LENGTH) {
         return false;
      }

      for(char codeItem : code.toCharArray()) {
         if(CHARACTERS.indexOf(codeItem) < 0) {
            return false;
         }
      }

      return true;
   }

   public boolean existAuthenticationChain() {
      return authenticationProviderService.getAuthenticationChain().isPresent();
   }

   public AuthenticationChain getAuthenticationChain() {
      Optional<AuthenticationChain> authenticationChainOp =
         authenticationProviderService.getAuthenticationChain();

      if(authenticationChainOp.isPresent()) {
         return authenticationChainOp.get();
      }

      return null;
   }

   public boolean validPassword(String password) {
      Matcher matcher = PASSWORD_PATTERN.matcher(password);

      return !Tool.isEmptyString(password) && password.length() >= PASSWORD_MIN_LENGTH &&
         password.length() <= PASSWORD_MAX_LENGTH && matcher.matches();
   }

   public boolean validUserName(String userName) {
      Matcher matcher = USER_NAME_PATTERN.matcher(userName);

      return !Tool.isEmptyString(userName) && userName.length() < USER_NAME_MAX_LENGTH &&
         matcher.matches();
   }

   public void autoRegisterUser(String googleUserId, String userEmail) {
      autoRegisterUser(googleUserId, userEmail, null);
   }

   public void autoRegisterUser(String googleUserId, String userEmail, SRPrincipal principal) {
      AuthenticationChain authenticationChain = getAuthenticationChain();

      if(authenticationChain == null || Tool.isEmptyString(googleUserId)) {
         return;
      }

      User existUser = null;
      AuthenticationProvider userProvider = null;
      String autoRegisterOrg = SUtil.isMultiTenant() ? Organization.getSelfOrganizationID() :
         Organization.getDefaultOrganizationID();

      for(AuthenticationProvider provider : authenticationChain.getProviders()) {
         IdentityID[] users = provider.getUsers();

         if(users == null) {
            continue;
         }

         for(IdentityID userName : users) {
            User user = provider.getUser(userName);

            if((Tool.equals(user.getGoogleSSOId(), googleUserId) ||
               Tool.equals(user.getName(), userEmail)) &&
               Tool.equals(userName.getOrgID(), autoRegisterOrg))
            {
               existUser = user;
               break;
            }
         }

         if(existUser != null) {
            userProvider = provider;
            break;
         }
      }

      // Add the email to when the exist sso user do not have current login emial.
      if(existUser != null) {
         String[] emails = existUser.getEmails();

         if((emails == null || !ArrayUtils.contains(emails, userEmail)) &&
            existUser instanceof FSUser && userProvider instanceof EditableAuthenticationProvider)
         {
            FSUser fSUser = (FSUser) existUser;
            fSUser.setEmails((String[]) ArrayUtils.add(emails, userEmail));
            EditableAuthenticationProvider editableAuthenticationProvider =
               (EditableAuthenticationProvider) userProvider;
            editableAuthenticationProvider.setUser(existUser.getIdentityID(), fSUser);
         }
      }
      else {
         createUser(new IdentityID(userEmail, autoRegisterOrg), null, userEmail,
            true, googleUserId, principal);
      }
   }

   public User createUser(IdentityID userName, String password, String email, SRPrincipal principal) {
      return createUser(userName, password, email, false, null, principal);
   }

   public User createUser(IdentityID userID, String password, String email, boolean googleSSO,
                          String ssoUserId, SRPrincipal principal)
   {
      AuthenticationChain authenticationChain = getAuthenticationChain();

      if(authenticationChain == null) {
         return null;
      }

      Optional<AuthenticationProvider> providerOp = authenticationChain.getProviders()
         .stream()
         .filter(EditableAuthenticationProvider.class::isInstance)
         .findFirst();

      if(!providerOp.isPresent()) {
         return null;
      }

      EditableAuthenticationProvider editProvider =
         (EditableAuthenticationProvider) providerOp.get();
      List<IdentityID> defaultRoles = Arrays.stream(editProvider.getRoles())
         .filter(roleName -> {
            Role role = editProvider.getRole(roleName);
            return role != null && role.isDefaultRole() &&
               userID.orgID.equals(role.getOrganizationID());
         })
         .collect(Collectors.toList());

      FSUser identity = new FSUser(userID,
                                   new String[] { email }, new String[0], defaultRoles.toArray(new IdentityID[0]),
         "", "", null, null,
         false, true, null, ssoUserId);

      if(password != null) {
         SUtil.setPassword(identity, password);
      }

      String[] names = SUtil.parseSignUpUserNames(userID, principal);
      String cookies = principal.getProperty("SignupCookies");

      PostSignUpUserData postUserData = new PostSignUpUserData(email, names[0], names[1], cookies);
      postUserData.sendUserData();

      if(!names[0].contains("@")) {
         //alias cannot be email because no special characters
         identity.setAlias((names[0] + " " + names[1]).trim());
      }

      editProvider.addUser(identity);

      IdentityInfoRecord identityInfoRecord = SUtil.getIdentityInfoRecord(identity.getIdentityID(),
         identity.getType(), IdentityInfoRecord.ACTION_TYPE_CREATE, null,
         IdentityInfoRecord.STATE_ACTIVE);
      XPrincipal user = new SRPrincipal(identity.getIdentityID());
      user.setOrgId(identity.getIdentityID().orgID);

      Audit.getInstance().auditIdentityInfo(identityInfoRecord, user);

      return identity;
   }

   public void sendEmailVerifyCode(String code, String to)
      throws MessagingException, NamingException
   {
      String from = SreeEnv.getProperty("signup.mail.from.address");
      String subject = SreeEnv.getProperty("signup.mail.subject",
         "Your Inetsoft StyleBI verification code");
      String message = SreeEnv.getProperty("signup.mail.message",
         "Here''s your Inetsoft StyleBI verification code, {0} \n {1}");
      message = MessageFormat.format(message, to, code);

      mailer.send(to, from, subject, message, null);
   }

   public Optional<User> getUserByGoogleSSOId(String googleUserId) {
      AuthenticationChain authenticationChain = getAuthenticationChain();

      if(authenticationChain == null || Tool.isEmptyString(googleUserId)) {
         return Optional.empty();
      }

      return authenticationChain.stream()
         .map(provider -> {
            IdentityID[] users = provider.getUsers();

            if(users == null) {
               return null;
            }

            for(IdentityID userName : users) {
               User user = provider.getUser(userName);

               if(user == null) {
                  continue;
               }

               if(Objects.equals(user.getGoogleSSOId(), googleUserId)) {
                  return user;
               }
            }

            return null;
         })
         .filter(user -> user != null)
         .findFirst();
   }

   private final Mailer mailer = new Mailer();
   private final AuthenticationProviderService authenticationProviderService;
   private final static int USER_NAME_MAX_LENGTH = 39;
   private final static int PASSWORD_MIN_LENGTH = 8;
   private final static int PASSWORD_MAX_LENGTH = 72;
   private final static int CODE_LENGTH = 6;
   private final static Pattern USER_NAME_PATTERN =
      Pattern.compile("[^~`!#%^*=\\[\\]\\\\;,/{}|\":<>?()]+");
   private final static Pattern PASSWORD_PATTERN =
      Pattern.compile("^(?![0-9]+$)(?![a-zA-Z]+$).+$");
   private static final String CHARACTERS =
      "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
}
