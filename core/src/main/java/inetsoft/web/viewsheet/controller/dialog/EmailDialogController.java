/*
 * inetsoft-core - StyleBI is a business intelligence web application.
 * Copyright © 2024 InetSoft Technology (info@inetsoft.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package inetsoft.web.viewsheet.controller.dialog;

import inetsoft.analytic.composition.ViewsheetService;
import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.report.io.csv.CSVConfig;
import inetsoft.report.io.viewsheet.excel.CSVUtil;
import inetsoft.sree.SreeEnv;
import inetsoft.sree.UserEnv;
import inetsoft.sree.internal.SUtil;
import inetsoft.sree.security.*;
import inetsoft.uql.util.*;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.internal.VSUtil;
import inetsoft.util.*;
import inetsoft.util.log.LogLevel;
import inetsoft.web.admin.schedule.ScheduleService;
import inetsoft.web.admin.schedule.model.UsersModel;
import inetsoft.web.composer.model.TreeNodeModel;
import inetsoft.web.factory.RemainingPath;
import inetsoft.web.portal.model.CSVConfigModel;
import inetsoft.web.viewsheet.command.MessageCommand;
import inetsoft.web.viewsheet.model.dialog.*;
import inetsoft.web.viewsheet.service.LinkUri;
import inetsoft.web.viewsheet.service.VSEmailService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.util.ObjectUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.*;
import java.util.stream.Collectors;

@Controller
public class EmailDialogController {
   /**
    * Creates a new instance of EmailDialogController
    */
   @Autowired
   public EmailDialogController(ScheduleService scheduleService,
                                ViewsheetService viewsheetService,
                                VSEmailService emailService)
   {
      this.scheduleService = scheduleService;
      this.viewsheetService = viewsheetService;
      this.emailService = emailService;
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
      IdentityID pId = IdentityID.getIdentityIDFromKey(principal.getName());
      runtimeId = Tool.byteDecode(runtimeId);
      RuntimeViewsheet rvs = viewsheetService.getViewsheet(runtimeId, principal);
      List<String> allBookmarks = new ArrayList<>();

      for(VSBookmarkInfo vsBookmarkInfo : rvs.getBookmarks()) {
         if(vsBookmarkInfo.getName().equals(VSBookmark.HOME_BOOKMARK)) {
            allBookmarks.add(Catalog.getCatalog().getString(vsBookmarkInfo.getName()));
         }
         else if(vsBookmarkInfo.getOwner() == null ||
                 vsBookmarkInfo.getOwner().equals(pId))
         {
            allBookmarks.add(vsBookmarkInfo.getName());
         }
         else {
            allBookmarks.add(vsBookmarkInfo.getName() + "(" +  VSUtil.getUserAlias(vsBookmarkInfo.getOwner()) + ")");
         }
      }

      Viewsheet vs = rvs.getViewsheet();
      boolean hasPrintLayout = vs.getLayoutInfo().getPrintLayout() != null;
      List<String> tableDataAssemblies = new ArrayList<>();

      if(vs != null) {
         VSUtil.getTableDataAssemblies(rvs.getViewsheet(), true)
            .stream().forEach(assembly -> {
               if(CSVUtil.needExport(assembly)) {
                  tableDataAssemblies.add(assembly.getAbsoluteName());
               }
         });
      }

      boolean expandComponentEnabled = SreeEnv.getBooleanProperty("export.expandComponents");
      boolean expandComponentAllowed = SecurityEngine.getSecurity().checkPermission(principal, ResourceType.VIEWSHEET_TOOLBAR_ACTION,
                                                                                    "ExportExpandComponents", ResourceAction.READ);
      //by nickgovus 2023-10-26, matchLayout = !ExportComponents = false only if (ExportSecurityPermission and setExportComponent = true)
      boolean matchLayout = !(expandComponentEnabled && expandComponentAllowed);

      FileFormatPaneModel fileFormatPaneModel = FileFormatPaneModel.builder()
         .allBookmarks(allBookmarks.toArray(new String[0]))
         .linkVisible(true)
         .matchLayout(matchLayout)
         .expandEnabled(expandComponentAllowed)
         .hasPrintLayout(hasPrintLayout)
         .csvConfig(CSVConfigModel.builder().from(new CSVConfig()).build())
         .tableDataAssemblies(tableDataAssemblies.toArray(new String[0]))
         .build();

      String email = SreeEnv.getProperty("mail.from.address");
      boolean userDialogEnabled = SecurityEngine.getSecurity().isSecurityEnabled();
      boolean useSelf =
         !"false".equals(SreeEnv.getProperty("em.mail.defaultEmailFromSelf"));

      if(!"anonymous".equals(pId.name) && useSelf) {
         String[] emails = SUtil.getEmails(pId);
         email = emails.length > 0 ? emails[0] : email;
         userDialogEnabled = true;
      }

      List<TreeNodeModel> nodes = new ArrayList<>();
      TreeNodeModel userTree = TreeNodeModel.builder()
         .label(Catalog.getCatalog().getString("Users"))
         .data("")
         .type(IdentityNode.USERS + "")
         .leaf(false)
         .build();

      TreeNodeModel groupTree = TreeNodeModel.builder()
         .label(Catalog.getCatalog().getString("Groups"))
         .data("")
         .type(IdentityNode.GROUPS + "")
         .leaf(false)
         .build();
      nodes.add(userTree);
      nodes.add(groupTree);

      TreeNodeModel rootTree = TreeNodeModel.builder()
         .label(Catalog.getCatalog().getString("Root"))
         .data("")
         .type(IdentityNode.ROOT + "")
         .children(nodes)
         .leaf(false)
         .build();

      EmailAddrDialogModel emailAddrDialogModel = EmailAddrDialogModel.builder()
         .rootTree(rootTree)
         .build();

      boolean showFrom = "true".equals(SreeEnv.getProperty("mail.from.enabled", "false"));
      UsersModel usersModel = scheduleService.getUsersModel(principal);

      EmailPaneModel emailPaneModel = EmailPaneModel.builder()
         .fromAddress(email != null ? email : "")
         .fromAddressEnabled(showFrom)
         .fromAddressEnabled(showFrom)
         .userDialogEnabled(userDialogEnabled)
         .emailAddrDialogModel(emailAddrDialogModel)
         .users(usersModel == null ? new ArrayList<>() : usersModel.emailUsers())
         .groups(usersModel == null ? new ArrayList<>() : Arrays.asList(usersModel.groupBaseNames()))
         .emailGroups(usersModel == null ? new ArrayList<>() : usersModel.emailGroups())
         .build();
      boolean historyEnabled = "true".equalsIgnoreCase(SreeEnv.getProperty("mail.history.enabled"));

      return EmailDialogModel.builder()
         .historyEnabled(historyEnabled)
         .fileFormatPaneModel(fileFormatPaneModel)
         .emailPaneModel(emailPaneModel)
         .build();
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
               String[] emails = SUtil.getEmails(new IdentityID(addr, OrganizationManager.getCurrentOrgName()));

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

   private static List<String> getUserEmails(IdentityID user) {
      try {
         return Arrays.stream(SUtil.getEmails(user))
            .filter(e -> !e.isEmpty())
            .collect(Collectors.toList());
      }
      catch(Exception e) {
         LOG.warn("Failed to get emails for user: {}", user, e);
         return new ArrayList<>();
      }
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
      runtimeId = Tool.byteDecode(runtimeId);
      RuntimeViewsheet rvs = viewsheetService.getViewsheet(runtimeId, principal);
      MessageDialogModel message;

      FileFormatPaneModel fileFormatPaneModel = value.fileFormatPaneModel();
      EmailPaneModel emailPaneModel = value.emailPaneModel();
      int formatType = fileFormatPaneModel.formatType();
      String[] books = fileFormatPaneModel.selectedBookmarks();
      boolean matchLayout = fileFormatPaneModel.matchLayout();
      boolean expandSelections = fileFormatPaneModel.expandSelections();
      boolean exportAllTabbedTables = fileFormatPaneModel.exportAllTabbedTables();
      boolean onlyData = fileFormatPaneModel.onlyDataComponents();
      boolean includeCurrent = fileFormatPaneModel.includeCurrent();
      CSVConfig csvConfig = new CSVConfig(fileFormatPaneModel.csvConfig());
      String toaddrs = Tool.defaultIfNull(emailPaneModel.toAddress(), "");
      String ccaddrs = emailPaneModel.ccAddress();
      String bccaddrs = emailPaneModel.bccAddress();
      String from = emailPaneModel.fromAddress();
      String subject = Tool.defaultIfNull(emailPaneModel.subject(), "");
      String body = Tool.defaultIfNull(emailPaneModel.message(), "");
      boolean isSendLink = Tool.defaultIfNull(fileFormatPaneModel.sendLink(), false);
      Catalog catalog = Catalog.getCatalog(principal);

      if(formatType == FileFormatInfo.EXPORT_TYPE_CSV) {
         matchLayout = false;
      }

      try {
         emailService.emailViewsheet(
            rvs, formatType, books, matchLayout, expandSelections, onlyData, csvConfig,
            exportAllTabbedTables, includeCurrent, toaddrs, ccaddrs, bccaddrs, from, subject,
            body, isSendLink, linkUri, principal);
         message = MessageDialogModel.builder()
            .type(MessageCommand.Type.INFO)
            .success(true)
            .message(catalog.getString("viewer.viewsheet.email.successful"))
            .build();
      }
      catch(MessageException e) {
         LOG.warn("Failed to send email message", e);
         message = MessageDialogModel.builder()
            .type(toMessageCommandType(e.getLogLevel()))
            .success(false)
            .message(e.getMessage())
            .build();
      }
      catch(Exception ex) {
         LOG.warn("Failed to send email message", ex);
         message = MessageDialogModel.builder()
            .type(MessageCommand.Type.ERROR)
            .success(false)
            .message(catalog.getString(
               "viewer.viewsheet.email.failed"))
            .build();
      }

      return message;
   }

   private MessageCommand.Type toMessageCommandType(LogLevel level) {
      switch(level) {
      case ERROR:
         return MessageCommand.Type.ERROR;
      case WARN:
         return MessageCommand.Type.WARNING;
      case DEBUG:
         return MessageCommand.Type.DEBUG;
      default:
         return MessageCommand.Type.INFO;
      }
   }

   private final ScheduleService scheduleService;
   private final ViewsheetService viewsheetService;
   private final VSEmailService emailService;
   private static final Logger LOG =
      LoggerFactory.getLogger(EmailDialogController.class);
}
