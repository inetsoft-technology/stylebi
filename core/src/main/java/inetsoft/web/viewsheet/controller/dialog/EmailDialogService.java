/*
 * This file is part of StyleBI.
 * Copyright (C) 2025  InetSoft Technology
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

import inetsoft.analytic.composition.ViewsheetService;
import inetsoft.cluster.*;
import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.report.composition.WorksheetEngine;
import inetsoft.report.io.csv.CSVConfig;
import inetsoft.report.io.viewsheet.excel.CSVUtil;
import inetsoft.sree.SreeEnv;
import inetsoft.sree.internal.SUtil;
import inetsoft.sree.security.*;
import inetsoft.uql.util.IdentityNode;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.internal.VSUtil;
import inetsoft.util.*;
import inetsoft.util.log.LogLevel;
import inetsoft.web.admin.schedule.ScheduleService;
import inetsoft.web.admin.schedule.model.UsersModel;
import inetsoft.web.composer.model.TreeNodeModel;
import inetsoft.web.portal.model.CSVConfigModel;
import inetsoft.web.viewsheet.command.MessageCommand;
import inetsoft.web.viewsheet.model.dialog.*;
import inetsoft.web.viewsheet.service.VSEmailService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.security.Principal;
import java.util.*;

@Service
@ClusterProxy
public class EmailDialogService {

   public EmailDialogService(ViewsheetService viewsheetService,
                             ScheduleService scheduleService,
                             VSEmailService emailService)
   {
      this.scheduleService = scheduleService;
      this.emailService = emailService;
      this.viewsheetService = viewsheetService;
   }

   @ClusterProxyMethod(WorksheetEngine.CACHE_NAME)
   public EmailDialogModel getEmailDialogModel(@ClusterProxyKey String runtimeId,
                                               Principal principal) throws Exception
   {
      IdentityID pId = IdentityID.getIdentityIDFromKey(principal.getName());
      runtimeId = Tool.byteDecode(runtimeId);
      RuntimeViewsheet rvs = viewsheetService.getViewsheet(runtimeId, principal);
      List<String> allBookmarks = new ArrayList<>();
      List<String> allBookmarkLabels = new ArrayList<>();

      for(VSBookmarkInfo vsBookmarkInfo : rvs.getBookmarks()) {
         if(vsBookmarkInfo.getName().equals(VSBookmark.HOME_BOOKMARK)) {
            allBookmarks.add(vsBookmarkInfo.getName());
            allBookmarkLabels.add(Catalog.getCatalog().getString(vsBookmarkInfo.getName()));
         }
         else if(vsBookmarkInfo.getOwner() == null ||  vsBookmarkInfo.getOwner().equals(pId)) {
            allBookmarks.add(vsBookmarkInfo.getName());
            allBookmarkLabels.add(vsBookmarkInfo.getName());
         }
         else {
            allBookmarks.add(vsBookmarkInfo.getName() + "(" +  vsBookmarkInfo.getOwner().getName() + ")");
            allBookmarkLabels.add(vsBookmarkInfo.getName()
                                     + "(" +  VSUtil.getUserAlias(vsBookmarkInfo.getOwner()) + ")");
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
         .allBookmarkLabels(allBookmarkLabels.toArray(new String[0]))
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
      nodes.add(userTree);

      // For users of SELF organization, should not show groups.
      if(!(principal instanceof SRPrincipal) || !((SRPrincipal) principal).isSelfOrganization()) {
         TreeNodeModel groupTree = TreeNodeModel.builder()
            .label(Catalog.getCatalog().getString("Groups"))
            .data("")
            .type(IdentityNode.GROUPS + "")
            .leaf(false)
            .build();
         nodes.add(groupTree);
      }

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

   @ClusterProxyMethod(WorksheetEngine.CACHE_NAME)
   public MessageDialogModel emailViewsheet(@ClusterProxyKey String runtimeId, EmailDialogModel value,
                                            Principal principal, String linkUri) throws Exception
   {
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

   private ViewsheetService viewsheetService;
   private final ScheduleService scheduleService;
   private final VSEmailService emailService;
   private static final Logger LOG =
      LoggerFactory.getLogger(EmailDialogService.class);
}
