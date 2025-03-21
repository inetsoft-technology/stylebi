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
package inetsoft.web.viewsheet.controller.dialog;


import inetsoft.sree.UserEnv;
import inetsoft.sree.internal.SUtil;
import inetsoft.sree.security.*;
import inetsoft.uql.util.*;
import inetsoft.util.*;
import inetsoft.web.factory.RemainingPath;
import inetsoft.web.viewsheet.command.MessageCommand;
import inetsoft.web.viewsheet.model.dialog.*;
import inetsoft.web.viewsheet.service.LinkUri;
import inetsoft.web.viewsheet.service.VSEmailService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.util.ObjectUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import java.security.Principal;
import java.util.*;

@Controller
public class EmailDialogController {
   /**
    * Creates a new instance of EmailDialogController
    */
   @Autowired
   public EmailDialogController(EmailDialogServiceProxy emailDialogServiceProxy)
   {
      this.emailDialogServiceProxy = emailDialogServiceProxy;
   }

   /**
    * Gets the email dialog model.
    * @param runtimeId  the runetime id
    * @param principal  the principal user
    * @return  the email dialog model
    * @throws Exception if could not create the email dialog model
    */
   @RequestMapping(value="/api/vs/email-dialog-model/**", method = RequestMethod.GET)
   @ResponseBody
   public EmailDialogModel getEmailDialogModel(@RemainingPath String runtimeId,
                                                Principal principal)
      throws Exception
   {
      return emailDialogServiceProxy.getEmailDialogModel(Tool.byteDecode(runtimeId), principal);
   }

   /**
    * Check if emailing a viewsheet is valid.
    * @param toAddrs       the to addresses
    * @param ccAddrs       the cc addresses
    * @return  A Message command with type OK for valid email params
    * @throws Exception if could not check if the email is valid
    */
   @RequestMapping(value="/api/vs/check-email-valid", method = RequestMethod.GET)
   @ResponseBody
   public EmailDialogValidationResponse checkEmailValid(
      @RequestParam(value = "toAddrs", required = false) String toAddrs,
      @RequestParam(value = "ccAddrs", required = false) String ccAddrs,
      @RequestParam(value = "bccAddrs", required = false) String bccAddrs,
      @RequestParam(value = "emailDeliveryEnabled", defaultValue = "true") boolean emailDeliveryEnabled,
      Principal principal) throws Exception
   {
      Catalog catalog = Catalog.getCatalog(principal);
      final List<String> toAddresses = VSEmailService.getEmailsList(toAddrs, true, principal);
      final List<String> ccAddresses = VSEmailService.getEmailsList(ccAddrs, true, principal);
      final List<String> bccAddresses = VSEmailService.getEmailsList(bccAddrs, true, principal);
      toAddrs = VSEmailService.getEmailsString(toAddresses);
      boolean isEmptyCC = "".equals(ccAddrs) || ccAddrs == null;
      ccAddrs = VSEmailService.getEmailsString(ccAddresses);
      boolean isValidCC = isEmptyCC || !"".equals(ccAddrs);
      boolean isEmptyBCC = "".equals(bccAddrs) || bccAddrs == null;
      bccAddrs = VSEmailService.getEmailsString(bccAddresses);
      boolean isValidBCC = isEmptyBCC || !"".equals(bccAddrs);
      MessageCommand messageCommand = new MessageCommand();
      messageCommand.setType(MessageCommand.Type.OK);

      if(emailDeliveryEnabled) {
         if("".equals(toAddrs) || !isValidCC || !isValidBCC) {
            messageCommand.setMessage(catalog.getString("Test Mail No Address"));
            messageCommand.setType(MessageCommand.Type.ERROR);
         }

         if(ccAddresses != null) {
            toAddresses.addAll(ccAddresses);
         }

         if(bccAddresses != null) {
            toAddresses.addAll(bccAddresses);
         }

         for(String addr : toAddresses) {
            if(addr.endsWith(Identity.GROUP_SUFFIX) || addr.endsWith(Identity.USER_SUFFIX)){
               String[] emails = SUtil.getEmails(new IdentityID(addr, OrganizationManager.getInstance().getCurrentOrgID()));

               if(emails == null || emails.length == 0) {
                  messageCommand.setMessage(catalog.getString("Test Mail No Address"));
                  messageCommand.setType(MessageCommand.Type.ERROR);
                  break;
               }
            }
            else if(!Tool.matchEmail(addr)) {
               messageCommand.setMessage(
                       catalog.getString("viewer.mailto.invalidRecipients") + " " + addr);
               messageCommand.setType(MessageCommand.Type.ERROR);
               break;
            }
         }
      }

      return EmailDialogValidationResponse.builder()
                                          .messageCommand(messageCommand)
                                          .addressHistory(toAddresses == null ? new ArrayList<String>() : toAddresses)
                                          .build();
   }

   /**
    * @return all the usernames available for sending mail.
    */
   public static List<IdentityID> getUsers(Principal principal) {
      List<IdentityID> users = new ArrayList<>();
      SecurityEngine securityEngine = SecurityEngine.getSecurity();
      String orgID = OrganizationManager.getInstance().getCurrentOrgID(principal);
      String loginOrgID = ((SRPrincipal) principal).getOrgId();

      if(securityEngine.isSecurityEnabled()) {
         IdentityID[] orgUsers = orgID.equals(Organization.getSelfOrganizationID()) &&
            loginOrgID.equals(Organization.getSelfOrganizationID()) ?
            new IdentityID[]{IdentityID.getIdentityIDFromKey(principal.getName())} :
            securityEngine.getOrgUsers(orgID);
         Set<String> usersWithFile = UserEnv.getUsersWithFile();

         for(IdentityID user: orgUsers) {
            // Bug #61382, first check for emails in the security provider
            String[] emails = securityEngine.getEmails(user);

            if(!ObjectUtils.isEmpty(emails)) {
               users.add(user);
               continue;
            }

            // check for emails in the user file, this is a more expensive operation as it needs
            // to read from a file so do this as a last resort
            if(usersWithFile.contains(user.name)) {
               SRPrincipal userPrincipal = SUtil.getPrincipal(user, null, false);
               String userEmail = (String) UserEnv.getProperty(userPrincipal, "email");

               if(!StringUtils.isEmpty(userEmail)) {
                  users.add(user);
               }
            }
         }
      }

      return users;
   }


   /**
    * Copy of EmailEvent.java process()
    * Export a viewsheet
    * @param value               the Email Dialog Model
    * @param principal           the principal user
    * @throws Exception if could not Email the viewsheet
    */
   @RequestMapping(value="/api/vs/email-dialog-model/**", method = RequestMethod.POST)
   @ResponseBody
   public MessageDialogModel emailViewsheet(@RemainingPath String runtimeId,
                                            @RequestBody EmailDialogModel value,
                                            Principal principal,
                                            @LinkUri String linkUri)
      throws Exception
   {
      return emailDialogServiceProxy.emailViewsheet(runtimeId, value, principal, linkUri);
   }

   private final EmailDialogServiceProxy emailDialogServiceProxy;
}
