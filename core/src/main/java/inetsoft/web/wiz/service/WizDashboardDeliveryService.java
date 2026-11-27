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
package inetsoft.web.wiz.service;

import inetsoft.analytic.composition.ViewsheetService;
import inetsoft.uql.asset.AssetEntry;
import inetsoft.uql.asset.AssetRepository;
import inetsoft.uql.viewsheet.FileFormatInfo;
import inetsoft.uql.viewsheet.internal.VSUtil;
import inetsoft.util.Tool;
import inetsoft.web.share.ShareConfig;
import inetsoft.web.share.ShareController;
import inetsoft.web.share.ShareMessage;
import inetsoft.web.viewsheet.command.MessageCommand;
import inetsoft.web.viewsheet.controller.dialog.EmailDialogServiceProxy;
import inetsoft.web.viewsheet.model.dialog.EmailDialogModel;
import inetsoft.web.viewsheet.model.dialog.EmailPaneModel;
import inetsoft.web.viewsheet.model.dialog.FileFormatPaneModel;
import inetsoft.web.viewsheet.model.dialog.MessageDialogModel;
import org.slf4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.security.Principal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Emails and shares a saved dashboard for the composer-chat plugin without a paired Composer
 * session (Redmine #82413). The native endpoints need a runtime viewsheet id, which exists only
 * after a pairing; these open the saved dashboard for the call and close it again.
 *
 * <p>Nothing is reimplemented: the email goes through the same {@code EmailDialogService} the
 * Viewer Email dialog uses, a share through the same {@code ShareController} the wiz Share menu
 * uses. What this class adds is the permission re-check on the real user (see
 * {@link WizDashboardAccess}), because both of those services are approved for a wiz
 * principal.</p>
 *
 * <p>Only the dialog defaults for the layout options are used ({@code matchLayout},
 * {@code expandSelections}, {@code onlyDataComponents}); the Include Link option is not
 * exposed.</p>
 */
@Service
public class WizDashboardDeliveryService {
   @Autowired
   public WizDashboardDeliveryService(WizDashboardAccess access,
                                      ViewsheetService viewsheetService,
                                      EmailDialogServiceProxy emailDialogService,
                                      ShareController shareController)
   {
      this(access, viewsheetService, emailDialogService, shareController,
           VSUtil::getExportOptions);
   }

   /** @param exportOptions the configured Viewer export formats, as {@code VSUtil} reads them. */
   WizDashboardDeliveryService(WizDashboardAccess access, ViewsheetService viewsheetService,
                               EmailDialogServiceProxy emailDialogService,
                               ShareController shareController,
                               Supplier<String[]> exportOptions)
   {
      this.exportOptions = exportOptions;
      this.access = access;
      this.viewsheetService = viewsheetService;
      this.emailDialogService = emailDialogService;
      this.shareController = shareController;
   }

   /** The formats, bookmarks and sender the Email dialog offers for a dashboard. */
   public EmailOptions getEmailOptions(String path, String scope, Principal user)
      throws Exception
   {
      AssetEntry entry = access.requireReadableDashboard(path, scope, user);
      access.requireToolbarAction(user, "Email");
      String runtimeId = viewsheetService.openViewsheet(entry, user, true);

      try {
         EmailDialogModel model = emailDialogService.getEmailDialogModel(runtimeId, user);
         List<String> formats = emailFormats();
         List<Bookmark> bookmarks = new ArrayList<>();
         FileFormatPaneModel file = model.fileFormatPaneModel();
         String[] names = file.allBookmarks() == null ? new String[0] : file.allBookmarks();
         String[] labels = file.allBookmarkLabels() == null ? names : file.allBookmarkLabels();

         for(int i = 0; i < names.length; i++) {
            bookmarks.add(new Bookmark(names[i], i < labels.length ? labels[i] : names[i]));
         }

         return new EmailOptions(entry.getPath(), scopeName(entry), formats,
                                 defaultFormat(formats), bookmarks,
                                 model.emailPaneModel().fromAddress());
      }
      finally {
         close(runtimeId, user);
      }
   }

   /** Emails the dashboard like the Viewer Email dialog does. */
   public EmailResult email(EmailRequest request, Principal user, String linkUri) throws Exception {
      if(request == null) {
         throw new IllegalArgumentException("The request body is required.");
      }

      AssetEntry entry = access.requireReadableDashboard(request.path(), request.scope(), user);
      access.requireToolbarAction(user, "Email");
      String to = joinAddresses(request.to(), "to", true);
      String cc = joinAddresses(request.cc(), "cc", false);
      String bcc = joinAddresses(request.bcc(), "bcc", false);
      List<String> formats = emailFormats();
      String format = resolveFormat(request.format(), formats);
      String runtimeId = viewsheetService.openViewsheet(entry, user, true);

      try {
         EmailDialogModel defaults = emailDialogService.getEmailDialogModel(runtimeId, user);
         FileFormatPaneModel defaultFile = defaults.fileFormatPaneModel();
         List<String> bookmarks = resolveEmailBookmarks(request.bookmarks(), defaultFile);

         // ExportExpandComponents is a Viewer permission the wiz bypass would approve, so the
         // dialog's layout default is only kept for a user who really holds it
         boolean expandAllowed = access.hasToolbarAction(user, "ExportExpandComponents");
         boolean matchLayout = !expandAllowed ||
            !Boolean.FALSE.equals(defaultFile.matchLayout());

         FileFormatPaneModel file = FileFormatPaneModel.builder()
            .from(defaultFile)
            .formatType(formatType(format))
            .selectedBookmarks(bookmarks.toArray(new String[0]))
            .matchLayout(matchLayout)
            .expandSelections(false)
            .onlyDataComponents(false)
            .sendLink(false)
            .build();
         EmailPaneModel pane = EmailPaneModel.builder()
            .from(defaults.emailPaneModel())
            .toAddress(to)
            .ccAddress(cc)
            .bccAddress(bcc)
            .subject(request.subject() == null ? "" : request.subject())
            .message(request.message() == null ? "" : request.message())
            .build();
         EmailDialogModel model = EmailDialogModel.builder()
            .from(defaults)
            .fileFormatPaneModel(file)
            .emailPaneModel(pane)
            .build();
         MessageDialogModel result = emailDialogService.emailViewsheet(runtimeId, model, user,
                                                                       linkUri);
         boolean sent = result != null && result.success() &&
            result.type() != MessageCommand.Type.ERROR;
         return new EmailResult(sent, result == null ? "" : result.message(), format, bookmarks);
      }
      finally {
         close(runtimeId, user);
      }
   }

   /**
    * The share targets the server offers the real user. With a dashboard {@code path} it also
    * checks the dashboard can be read and returns the Viewer link a share would carry.
    */
   public ShareOptions getShareOptions(String path, String scope, Principal user, String linkUri)
      throws Exception
   {
      String link = null;

      if(path != null && !path.isBlank()) {
         link = viewerLink(access.requireReadableDashboard(path, scope, user), linkUri);
      }

      ShareConfig config = shareController.getConfig(null, WizDashboardAccess.realUser(user));
      // the Link channel is not offered by wiz (see WizShareController), and facebook, linkedin
      // and twitter are browser-side redirects with no server call
      Map<String, Boolean> targets = new LinkedHashMap<>();
      targets.put(TARGET_EMAIL, config.emailEnabled());
      targets.put(TARGET_SLACK, config.slackEnabled());
      targets.put(TARGET_GOOGLE_CHAT, config.googleChatEnabled());
      return new ShareOptions(targets, link);
   }

   /** Shares the dashboard's viewer link by email, Slack or Google Chat. */
   public void share(ShareRequest request, Principal user, String linkUri) throws Exception {
      if(request == null) {
         throw new IllegalArgumentException("The request body is required.");
      }

      String target = WizDashboardAccess.lower(request.target());

      if(!List.of(TARGET_EMAIL, TARGET_SLACK, TARGET_GOOGLE_CHAT).contains(target)) {
         throw new IllegalArgumentException(
            "'target' must be one of email, slack, google-chat (got \"" + request.target() +
            "\"). Link, Facebook, LinkedIn and Twitter sharing is a browser action and is not " +
            "available here.");
      }

      if(request.message() == null || request.message().isBlank()) {
         throw new IllegalArgumentException("'message' is required.");
      }

      AssetEntry entry = access.requireReadableDashboard(request.path(), request.scope(), user);
      // the same key ShareController checks, under the name the Share menu uses for it
      access.requireShareTarget(user, TARGET_GOOGLE_CHAT.equals(target) ? "googlechat" : target);
      Principal real = WizDashboardAccess.realUser(user);
      ShareMessage.Builder builder = ShareMessage.builder()
         .viewsheetId(entry.toIdentifier())
         .link(viewerLink(entry, linkUri))
         .message(request.message())
         .subject(request.subject());

      if(TARGET_EMAIL.equals(target)) {
         builder.recipients(requireList(request.recipients(), "recipients"))
            .ccs(request.ccs())
            .bccs(request.bccs());
         WizShareEmailSender.send(shareController, builder.build(), real);
      }
      else if(TARGET_SLACK.equals(target)) {
         shareController.sendSlackMessage(builder.build(), real);
      }
      else {
         shareController.sendGoogleChatMessage(builder.build(), real);
      }
   }

   /**
    * The Viewer link of a dashboard, built like {@code ShareService.getViewsheetLink()} and the
    * Email dialog's Include Link do.
    */
   static String viewerLink(AssetEntry entry, String linkUri) {
      StringBuilder url = new StringBuilder().append(linkUri).append("app/viewer/view/");

      if(entry.getScope() == AssetRepository.USER_SCOPE) {
         url.append("user/").append(entry.getUser().name).append('/');
      }
      else {
         url.append("global/");
      }

      url.append(entry.getPath());
      return Tool.encodeUriPath(url.toString());
   }

   /** The formats the Viewer Email dialog lists: the configured export formats, no Snapshot. */
   List<String> emailFormats() {
      List<String> formats = new ArrayList<>();

      for(String name : exportOptions.get()) {
         String trimmed = name.trim();

         if(!trimmed.isEmpty() && !FileFormatInfo.EXPORT_NAME_SNAPSHOT.equals(trimmed) &&
            Arrays.asList(FileFormatInfo.EXPORT_ALL_NAMES).contains(trimmed))
         {
            formats.add(trimmed);
         }
      }

      return formats;
   }

   /** Excel when it is offered, otherwise the first offered format, as the Email dialog does. */
   static String defaultFormat(List<String> formats) {
      if(formats.isEmpty()) {
         throw new IllegalStateException(
            "The server offers no export format for email (vsexport.menu.options is empty).");
      }

      return formats.contains(FileFormatInfo.EXPORT_NAME_EXCEL) ?
         FileFormatInfo.EXPORT_NAME_EXCEL : formats.get(0);
   }

   /** The format name for a requested one, matched ignoring case; the default when omitted. */
   static String resolveFormat(String requested, List<String> formats) {
      if(requested == null || requested.isBlank()) {
         return defaultFormat(formats);
      }

      for(String format : formats) {
         if(format.equalsIgnoreCase(requested.trim())) {
            return format;
         }
      }

      throw new IllegalArgumentException(
         "'format' \"" + requested + "\" is not offered for email; use one of " +
         String.join(", ", formats) + ".");
   }

   static int formatType(String format) {
      for(int i = 0; i < FileFormatInfo.EXPORT_ALL_NAMES.length; i++) {
         if(FileFormatInfo.EXPORT_ALL_NAMES[i].equals(format)) {
            return i;
         }
      }

      throw new IllegalArgumentException("Unknown format " + format);
   }

   private static List<String> resolveEmailBookmarks(List<String> requested,
                                                     FileFormatPaneModel defaults)
   {
      List<String> resolved = new ArrayList<>();

      if(requested == null || requested.isEmpty()) {
         return resolved;
      }

      List<String> offered = defaults.allBookmarks() == null ?
         List.of() : Arrays.asList(defaults.allBookmarks());

      for(String name : requested) {
         String match = null;

         for(String candidate : offered) {
            if(candidate.equals(name)) {
               match = candidate;
               break;
            }
         }

         if(match == null) {
            throw new IllegalArgumentException(
               "'bookmarks' has \"" + name + "\", which this dashboard does not offer you; " +
               "available: " + String.join(", ", offered) + ".");
         }

         resolved.add(match);
      }

      return resolved;
   }

   /** Joins the addresses of one recipient field; {@code required} refuses an empty list. */
   static String joinAddresses(List<String> addresses, String field, boolean required) {
      List<String> clean = new ArrayList<>();

      if(addresses != null) {
         for(String address : addresses) {
            if(address != null && !address.isBlank()) {
               clean.add(address.trim());
            }
         }
      }

      if(clean.isEmpty()) {
         if(required) {
            throw new IllegalArgumentException(
               "'" + field + "' needs at least one recipient address.");
         }

         return "";
      }

      return String.join(",", clean);
   }

   private static List<String> requireList(List<String> values, String field) {
      if(values == null || values.stream().noneMatch(v -> v != null && !v.isBlank())) {
         throw new IllegalArgumentException("'" + field + "' needs at least one entry.");
      }

      return values;
   }

   private static String scopeName(AssetEntry entry) {
      return entry.getScope() == AssetRepository.USER_SCOPE ? "user" : "global";
   }

   private void close(String runtimeId, Principal user) {
      try {
         viewsheetService.closeViewsheet(runtimeId, user);
      }
      catch(Exception e) {
         LOG.warn("Failed to close runtime [{}] after a dashboard delivery call", runtimeId, e);
      }
   }

   public record Bookmark(String name, String label) {
   }

   public record EmailOptions(String path, String scope, List<String> formats,
                              String defaultFormat, List<Bookmark> bookmarks,
                              String fromAddress)
   {
   }

   public record EmailRequest(String path, String scope, List<String> to, List<String> cc,
                              List<String> bcc, String subject, String message, String format,
                              List<String> bookmarks)
   {
   }

   public record EmailResult(boolean sent, String message, String format,
                             List<String> bookmarks)
   {
   }

   public record ShareOptions(Map<String, Boolean> targets, String link) {
   }

   public record ShareRequest(String path, String scope, String target, String message,
                              String subject, List<String> recipients, List<String> ccs,
                              List<String> bccs)
   {
   }

   public static final String TARGET_EMAIL = "email";
   public static final String TARGET_SLACK = "slack";
   public static final String TARGET_GOOGLE_CHAT = "google-chat";

   private final Supplier<String[]> exportOptions;
   private final WizDashboardAccess access;
   private final ViewsheetService viewsheetService;
   private final EmailDialogServiceProxy emailDialogService;
   private final ShareController shareController;
   private static final Logger LOG = LoggerFactory.getLogger(WizDashboardDeliveryService.class);
}
