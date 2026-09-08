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
package inetsoft.web.api.schedule;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import inetsoft.sree.RepletRequest;
import inetsoft.sree.schedule.ServerPathInfo;
import inetsoft.sree.security.IdentityID;
import inetsoft.uql.viewsheet.FileFormatInfo;
import inetsoft.uql.viewsheet.VSBookmarkInfo;
import io.swagger.v3.oas.annotations.extensions.Extension;
import io.swagger.v3.oas.annotations.extensions.ExtensionProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import org.springframework.validation.annotation.Validated;

import java.util.*;
import java.util.stream.Collectors;

/**
 * ViewsheetAction describes an action that opens a viewsheet and performs one or more sub-actions
 * on it.
 */
@Validated
@Schema(description = "An action that opens a viewsheet and performs one or more sub-actions on it.")
public class ViewsheetAction extends ScheduleAction {
   /**
    * Creates a new instance of {@code ViewsheetAction}.
    */
   public ViewsheetAction() {
      setActionType(ActionType.VIEWSHEET);
   }

   /**
    * Creates a new instance of {@code ViewsheetAction}.
    *
    * @param action the action object being represented.
    */
   public ViewsheetAction(inetsoft.sree.schedule.ViewsheetAction action) {
      setActionType(ActionType.VIEWSHEET);
      setViewsheet(action.getViewsheet());

      setBookmarkName(action.getBookmarks()[0]);
      setBookmarkNames(Arrays.asList(action.getBookmarks()));
      setBookmarkUsers(Arrays.asList(action.getBookmarkUsers()));

      // defensive: bookmarkTypes should always be populated parallel to bookmarks/bookmarkUsers
      // by the write side (see convertAction(ViewsheetAction)), but guard against a null/empty
      // array from a task persisted before that fix, or created some other way.
      if(action.getBookmarkTypes() != null && action.getBookmarkTypes().length > 0) {
         setBookmarkType(BookmarkType.fromCode(action.getBookmarkTypes()[0]));
         setBookmarkTypes(Arrays.stream(action.getBookmarkTypes())
                             .mapToObj(BookmarkType::fromCode)
                             .collect(Collectors.toList()));
      }

      populateSaveToServerFormats(action);

      if(action.getEmails() != null && !action.getEmails().isEmpty()) {
         getEmails().addAll(Arrays.asList(action.getEmails().split(",")));
      }

      if(action.getNotifications() != null && !action.getNotifications().isEmpty()) {
         getNotifies().addAll(Arrays.asList(action.getNotifications().split(",")));
         setNotifyIfFailed(action.isNotifyError());
         setNotifyLink(action.isLink());
      }
      if(action.getFileFormat() != null && !action.getFileFormat().isEmpty()) {
         setFormat(Format.valueOf(action.getFileFormat().toUpperCase()));
      }

      if(action.getFileFormat() != null && !action.getFileFormat().isEmpty()) {
         setFormat(Format.valueOf(action.getFileFormat().toUpperCase()));
      }

      if(action.getRepletRequest() != null) {
         RepletRequest request = action.getViewsheetRequest();

         for(Enumeration<?> e = request.getParameterNames(); e.hasMoreElements();) {
            String name = (String) e.nextElement();
            getParameters().put(name, request.getParameter(name));
         }
      }

      setSender(action.getFrom());
      setSubject(action.getSubject());
      setMessage(action.getMessage());
      setHtmlMessage(action.isMessageHtml());
      setCCAddresses(action.getCCAddresses());
      setBCCAddresses(action.getBCCAddresses());
      setEmailMatch(action.isMatchLayout());
      setEmailExpandSelections(action.isExpandSelections());
      setEmailOnlyDataComponents(action.isOnlyDataComponents());
      setExportAllTabbedTables(action.isExportAllTabbedTables());
      setEmailZip(action.isCompressFile());
      setAttachmentName(action.getAttachmentName());
      setEmailLink(action.isDeliverLink());
      populateAlerts(action);

      if(action.getEmailCSVConfig() != null) {
         setEmailCSVConfig(new CSVConfig(action.getEmailCSVConfig()));
      }

      populateSaveToServerFormats(action);
      setSaveToServerMatch(action.isSaveToServerMatch());
      setSaveToServerExpandSelections(action.isSaveToServerExpandSelections());
      setSaveToServerOnlyDataComponents(action.isSaveToServerOnlyDataComponents());
      setSaveExportAllTabbedTables(action.isSaveExportAllTabbedTables());

      if(action.getSaveCSVConfig() != null) {
         setSaveToServerCsvConfig(new CSVConfig(action.getSaveCSVConfig()));
      }

      if(action.getViewsheetRequest() != null) {
         RepletRequest request = action.getViewsheetRequest();

         for(Enumeration<?> e = request.getParameterNames(); e.hasMoreElements();) {
            String name = (String) e.nextElement();
            getParameters().put(name, request.getParameter(name));
         }
      }
   }

   private void populateSaveToServerFormats(inetsoft.sree.schedule.ViewsheetAction action) {
      final int[] saveFormats = action.getSaveFormats();

      if(saveFormats.length > 0) {
         saveToServerFilePaths = new ArrayList<>(saveFormats.length);
      }

      for(int saveFormat : saveFormats) {
         final String format = FileFormatInfo.EXPORT_ALL_NAMES[saveFormat];

         if(format != null) {
            final ServerPathInfo info = action.getFilePathInfo(saveFormat);
            saveToServerFilePaths.add(new SaveToServerFilePath(Format.valueOf(format.toUpperCase()), info));
         }
      }
   }

   private void populateAlerts(inetsoft.sree.schedule.ViewsheetAction action) {
      inetsoft.sree.schedule.ScheduleAlert[] alerts = action.getAlerts();

      if(alerts != null) {
         this.alerts = new ArrayList<>(alerts.length);

         for(inetsoft.sree.schedule.ScheduleAlert alert : alerts) {
            this.alerts.add(new ScheduleAlert(alert.getElementId(), alert.getHighlightName()));
         }
      }
   }

   /**
    * Gets the asset identifier of the viewsheet.
    *
    * @return the asset identifier.
    */
   @NotNull
   @Schema(
      description = "The asset identifier of the viewsheet.",
      example = "1^128^__NULL__^Examples/Sales Summary^host-org")
   public String getViewsheet() {
      return viewsheet;
   }

   /**
    * Sets the asset identifier of the viewsheet.
    *
    * @param viewsheet the asset identifier.
    */
   public void setViewsheet(String viewsheet) {
      this.viewsheet = viewsheet;
   }

   /**
    * Gets the name of the bookmark, if any, to open the viewsheet to.
    *
    * @return the bookmark name.
    */
   @Schema(description = "The name of the bookmark to open the viewsheet to.", example = "(Home)")
   public String getBookmarkName() {
      return bookmarkName;
   }

   /**
    * Sets the name of the bookmark, if any, to open the viewsheet to.
    *
    * @param bookmarkName the bookmark name.
    */
   public void setBookmarkName(String bookmarkName) {
      this.bookmarkName = bookmarkName;
   }

   /**
    * Gets the names of the bookmarks, if any, to open the viewsheet to.
    *
    * @return the bookmark name.
    */
   @Schema(description = "The names of the bookmarks to open the viewsheet to.", example = "[ \"(Home)\" ]")
   public List<String> getBookmarkNames() {
      return bookmarkNames;
   }

   /**
    * Sets the names of the bookmarks, if any, to open the viewsheet to.
    *
    * @param bookmarkNames the bookmark names.
    */
   public void setBookmarkNames(List<String> bookmarkNames) {
      this.bookmarkNames = bookmarkNames;
   }

   /**
    * Gets the name of the user that owns the bookmark.
    *
    * @return the owner.
    */
   @Schema(
      description = "The name of the user that owns the bookmark. Required if `bookmarkName` is set.",
      example = "admin")
   public IdentityID getBookmarkUser() {
      return bookmarkUser;
   }

   /**
    * Sets the name of the user that owns the bookmark.
    *
    * @param bookmarkUser the owner.
    */
   public void setBookmarkUser(IdentityID bookmarkUser) {
      this.bookmarkUser = bookmarkUser;
   }

   /**
    * Gets the names of the users that own the bookmarks.
    *
    * @return the owners.
    */
   @Schema(
      description = "The names of the users that own the bookmarks. Required if `bookmarkNames` is set.",
      example = "[ admin ]")
   public List<IdentityID> getBookmarkUsers() {
      return bookmarkUsers;
   }

   /**
    * Sets the names of the users that own the bookmarks.
    *
    * @param bookmarkUsers the owners.
    */
   public void setBookmarkUsers(List<IdentityID> bookmarkUsers) {
      this.bookmarkUsers = bookmarkUsers;
   }

   /**
    * Gets the type of bookmark.
    *
    * @return the bookmark type.
    */
   @Schema(
      description = "The type of bookmark. Required if `bookmarkName` is set.",
      example = "all_share")
   public BookmarkType getBookmarkType() {
      return bookmarkType;
   }

   /**
    * Sets the type of bookmark.
    *
    * @param bookmarkType the bookmark type.
    */
   public void setBookmarkType(BookmarkType bookmarkType) {
      this.bookmarkType = bookmarkType;
   }

   /**
    * Gets the types of the bookmarks.
    *
    * @return the bookmark types.
    */
   @Schema(
      description = "The types of the bookmarks. Required if `bookmarkNames` is set.",
      example = "[ \"all_share\" ]")
   public List<BookmarkType> getBookmarkTypes() {
      return bookmarkTypes;
   }

   /**
    * Sets the types of the bookmarks.
    *
    * @param bookmarkTypes the bookmark type.
    */
   public void setBookmarkTypes(List<BookmarkType> bookmarkTypes) {
      this.bookmarkTypes = bookmarkTypes;
   }

   /**
    * Gets the email address to which the viewsheet should be sent.
    *
    * @return the email list.
    */
   @Schema(
      description = "The email addresses to which the viewsheet should be sent.",
      example = "[ \"sales@example.com\", \"marketing@example.com\" ]")
   public List<String> getEmails() {
      if(emails == null) {
         emails = new ArrayList<>();
      }

      return emails;
   }

   /**
    * Sets the email address to which the report should be sent.
    *
    * @param emails the email list.
    */
   public void setEmails(List<String> emails) {
      if(emails == null) {
         emails = new ArrayList<>();
      }

      this.emails = emails;
   }

   /**
    * Gets the email address from which the delivery and/or notification emails are sent.
    *
    * @return the sender.
    */
   @Schema(
      description = "The email address from which the delivery and notification emails are sent. Required if `emails` or `notifies` are not empty.",
      example = "report@example.com")
   public String getSender() {
      return sender;
   }

   /**
    * Sets the email address from which the delivery and/or notification emails are sent.
    *
    * @param sender the sender.
    */
   public void setSender(String sender) {
      this.sender = sender;
   }

   /**
    * Gets the email addresses to which a notification should be sent that the action has been
    * performed.
    *
    * @return the notify email list.
    */
   @Schema(
      description = "The email addresses to which a notification should be when the action has been performed.",
      example = "[ \"sales@example.com\", \"marketing@example.com\" ]")
   public List<String> getNotifies() {
      if(notifies == null) {
         notifies = new ArrayList<>();
      }

      return notifies;
   }

   /**
    * Sets the email addresses to which a notification should be sent that the action has been
    * performed.
    *
    * @param notifies the notify email list.
    */
   public void setNotifies(List<String> notifies) {
      this.notifies = notifies;
   }

   /**
    * Sets a flag that indicates that a notification email should only be sent if the task fails.
    *
    * @return the flag that indicates that a notification email should be sent on fail.
    */
   @Schema(
      description = "A flag that indicates that a notification email should only be sent if the task fails.",
      example = "true",
      extensions = @Extension(properties = @ExtensionProperty(name = "x-since", value = "1.0")))
   public Boolean isNotifyIfFailed() {
      return notifyIfFailed;
   }

   /**
    * Gets a flag that indicates that a notification email should only be sent if the task fails.
    *
    * @param notifyIfFailed the flag that indicates that a notification email should be sent on fail.
    */
   public void setNotifyIfFailed(Boolean notifyIfFailed) {
      this.notifyIfFailed = notifyIfFailed;
   }

   /**
    * Sets a flag that indicates that a notification email should include a link.
    *
    * @return the flag that indicates that a notification email should be sent on fail.
    */
   @Schema(
      description = "A flag that indicates that a notification email should include a link.",
      example = "true",
      extensions = @Extension(properties = @ExtensionProperty(name = "x-since", value = "1.0")))
   public Boolean isNotifyLink() {
      return notifyLink;
   }

   /**
    * Gets a flag that indicates that a notification email should include a link.
    *
    * @param notifyLink the flag that indicates that a notification email should be sent on fail.
    */
   public void setNotifyLink(Boolean notifyLink) {
      this.notifyLink = notifyLink;
   }

   /**
    * Gets the format in which the viewsheet should be sent in the delivery emails.
    *
    * @return the viewsheet file format.
    */
   @Schema(
      description = "The format in which the viewsheet should be sent in the delivery emails. Required if `emails` is not empty.",
      example = "PDF")
   public Format getFormat() {
      return format;
   }

   /**
    * Sets the format in which the viewsheet should be sent in the delivery emails.
    *
    * @param format the viewsheet file format.
    */
   public void setFormat(Format format) {
      this.format = format;
   }

   /**
    * Gets the subject of the delivery and/or notification emails.
    *
    * @return the subject line.
    */
   @Schema(
      description = "The subject line of the delivery email. Required if `emails` is not empty.",
      example = "Weekly Summary")
   public String getSubject() {
      return subject;
   }

   /**
    * Sets the subject of the delivery and/or notification emails.
    *
    * @param subject the subject line.
    */
   public void setSubject(String subject) {
      this.subject = subject;
   }

   /**
    * Gets the message body of the delivery and/or notification emails.
    *
    * @return the message body.
    */
   @Schema(
      description = "The body of the delivery message. Required if `emails` is not empty.",
      example = "<p>See the attached report for the monthly sales summary.</p>")
   public String getMessage() {
      return message;
   }

   /**
    * Sets the message body of the delivery and/or notification emails.
    *
    * @param message the message body.
    */
   public void setMessage(String message) {
      this.message = message;
   }

   /**
    * Gets a flag indicating if the message body is formatted as HTML.
    *
    * @return {@code true} if HTML; {@code false} otherwise.
    */
   @Schema(
      description = "A flag indicating if the message body is formatted as HTML.",
      example = "true")
   public boolean isHtmlMessage() {
      return htmlMessage;
   }

   /**
    * Sets a flag indicating if the message body is formatted as HTML.
    *
    * @param htmlMessage {@code true} if HTML; {@code false} otherwise.
    */
   public void setHtmlMessage(boolean htmlMessage) {
      this.htmlMessage = htmlMessage;
   }

   /**
    * Get CC addresses.
    */
   @Schema(description = "The CC email addresses.")
   public String getCCAddresses() {
      return ccAddresses;
   }

   /**
    * Set CC addresses.
    *
    * @param ccAddresses CC mails list.
    */
   public void setCCAddresses(String ccAddresses) {
      this.ccAddresses = ccAddresses;
   }

   /**
    * Get BCC addresses.
    */
   @Schema(description = "The CC email addresses.")
   public String getBCCAddresses() {
      return bccAddresses;
   }

   /**
    * Set BCC addresses.
    *
    * @param bccAddresses BCC mails list
    */
   public void setBCCAddresses(String bccAddresses) {
      this.bccAddresses = bccAddresses;
   }

   /**
    * Gets a flag indicating if the email exports match the original layout.
    * If this is false, the export will expand its components.
    *
    * @return {@code true} if match layout; {@code false} if it expands the components.
    */
   @Schema(
      description = "A flag indicating if the email exports match the original layout.",
      example = "true")
   public Boolean isEmailMatch() {
      return emailMatch;
   }

   /**
    * Sets a flag indicating if the email exports match the original layout.
    * If this is false, the export will expand its components.
    *
    * @param emailMatch {@code true} if match layout; {@code false} if it expands the components.
    */
   public void setEmailMatch(Boolean emailMatch) {
      this.emailMatch = emailMatch;
   }

   /**
    * Sets a flag indicating if the email exports expand their selections.
    * emailMatch must be false for this to apply
    *
    * @return {@code true} if it expands selection components; {@code false} otherwise.
    */
   @Schema(
      description = "A flag indicating if the email exports expand their selection lists/trees.",
      example = "false")
   public Boolean isEmailExpandSelections() {
      return emailExpandSelections;
   }

   /**
    * Sets a flag indicating if the email exports expand their selections.
    * emailMatch must be false for this to apply
    *
    * @param emailExpandSelections {@code true} if it expands selection components; {@code false} otherwise.
    */
   public void setEmailExpandSelections(Boolean emailExpandSelections) {
      this.emailExpandSelections = emailExpandSelections;
   }

   /**
    * Gets a flag indicating if the email exports only expand the data components.
    * emailMatch must be false for this to apply
    *
    * @return {@code true} if expand data components only; {@code false} otherwise.
    */
   @Schema(
      description = "A flag indicating if the email exports only expand the data components.",
      example = "false")
   public Boolean isEmailOnlyDataComponents() {
      return emailOnlyDataComponents;
   }

   /**
    * Sets a flag indicating if the email exports only expand the data components.
    * emailMatch must be false for this to apply
    *
    * @param emailOnlyDataComponents {@code true} if expand data components only; {@code false} otherwise.
    */
   public void setEmailOnlyDataComponents(Boolean emailOnlyDataComponents) {
      this.emailOnlyDataComponents = emailOnlyDataComponents;
   }

   /**
    *  Gets a flag indicating if export all tab tables.
    * @return
    */
   @Schema(
      description = "A flag indicating if export all tab tables.",
      example = "false")
   public Boolean isExportAllTabbedTables() {
      return exportAllTabbedTables;
   }

   /**
    * Sets a flag indicating if export all tab tables.
    * @param exportAllTabbedTables
    */
   public void setExportAllTabbedTables(Boolean exportAllTabbedTables) {
      this.exportAllTabbedTables = exportAllTabbedTables;
   }

   /**
    * Check if the exported file needs to be zipped up.
    *
    * @return the flag that indicates that a file should be zipped.
    */
   @Schema(
      description = "A flag indicating that a file should be zipped.",
      example = "false")
   public Boolean isEmailZip() {
      return emailZip;
   }

   /**
    * Set the flag indicating if the exported file needs to be zipped up.
    *
    * @param emailZip the flag indicating if the exported file needs to be zipped up.
    */
   public void setEmailZip(Boolean emailZip) {
      this.emailZip = emailZip;
   }

   /**
    * Gets the csv configuration properties used for csv exports to email exports.
    *
    * @return the csv configuration properties used for csv exports to  email exports.
    */
   @Schema(
      description = "The csv configuration properties used for csv exports to email exports.")
   public CSVConfig getEmailCSVConfig() {
      return emailCSVConfig;
   }

   /**
    * Sets the csv configuration properties used for csv exports to the email exports.
    *
    * @param emailCSVConfig the csv configuration properties used for csv exports to the email exports.
    */
   public void setEmailCSVConfig(CSVConfig emailCSVConfig) {
      this.emailCSVConfig = emailCSVConfig;
   }

   /**
    * Get the email attachment's name.
    *
    * @return the flag that indicates that a file should be zipped.
    */
   @Schema(
      description = "The email attachment's name.",
      example = "false")
   public String getAttachmentName() {
      return attachmentName;
   }

   /**
    * Set the email attachment's name.
    *
    * @param attachmentName the email attachment's name.
    */
   public void setAttachmentName(String attachmentName) {
      this.attachmentName = attachmentName;
   }

   /**
    * Sets a flag that indicates that the export email should include a link.
    *
    * @return the flag that indicates that a notification email should be sent on fail.
    */
   @Schema(
      description = "A flag that indicates that the export email should include a link.",
      example = "true",
      extensions = @Extension(properties = @ExtensionProperty(name = "x-since", value = "1.0")))
   public Boolean isEmailLink() {
      return emailLink;
   }

   /**
    * Gets a flag that indicates that the export email should include a link.
    *
    * @param emailLink the flag that indicates that the export email should be sent on fail.
    */
   public void setEmailLink(Boolean emailLink) {
      this.emailLink = emailLink;
   }

   /**
    * Gets the parameters to use when opening the viewsheet.
    *
    * @return the parameter map.
    */
   @Schema(
      description = "The parameters to use when opening the report.",
      example = "{ \"simpleParam\": 1, " +
         "\"detailedParam\": {\"value\": 5, \"dataType\": \"double\"}, " +
         "\"expressionParam\": {\"value\": \"=2+4\", \"type\": \"expression\", \"dataType\": \"string\"}, " +
         "\"arrayParam\": {\"value\": \"value1,value2\", \"array\": true, \"dataType\": \"string\"} }",
      extensions = @Extension(properties = @ExtensionProperty(name = "x-since", value = "1.0")))
   public Map<String, Object> getParameters() {
      if(parameters == null) {
         parameters = new HashMap<>();
      }

      return parameters;
   }

   /**
    * Sets the parameters to use when opening the viewsheet.
    *
    * @param parameters the parameter map.
    */
   public void setParameters(Map<String, Object> parameters) {
      this.parameters = parameters;
   }

   /**
    * Gets the highlight alerts.
    *
    * @return the highlight alerts.
    *
    * @since 2022
    */
   @Schema(
      description = "The highlight alerts.",
      extensions = @Extension(properties = @ExtensionProperty(name = "x-since", value = "1.0")))
   public List<ScheduleAlert> getAlerts() {
      if(alerts == null) {
         alerts = new ArrayList<>();
      }

      return alerts;
   }

   /**
    * Sets the highlight alerts.
    *
    * @param alerts the highlight alerts.
    *
    * @since 2022
    */
   public void setAlerts(List<ScheduleAlert> alerts) {
      this.alerts = alerts;
   }

   /**
    * Gets the save to server file paths.
    *
    * @return the save to server file paths.
    *
    * @since 2023
    */
   @Schema(
      description = "The list of files that the report should be exported to.",
      extensions = @Extension(properties = @ExtensionProperty(name = "x-since", value = "1.0")))
   public List<SaveToServerFilePath> getSaveToServerFilePaths() {
      if(saveToServerFilePaths == null) {
         saveToServerFilePaths = new ArrayList<>();
      }

      return saveToServerFilePaths;
   }

   /**
    * Sets the save to server file paths.
    *
    * @param saveToServerFilePaths the save to server file paths.
    *
    * @since 2023
    */
   public void setSaveToServerFilePaths(List<SaveToServerFilePath> saveToServerFilePaths) {
      this.saveToServerFilePaths = saveToServerFilePaths;
   }

   /**
    * Gets a flag indicating if the server saved exports match the original layout.
    * If this is false, the export will expand its components.
    *
    * @return {@code true} if match layout; {@code false} if it expands the components.
    */
   @Schema(
      description = "A flag indicating if the server saved exports match the original layout.",
      example = "true")
   public boolean isSaveToServerMatch() {
      return saveToServerMatch;
   }

   /**
    * Sets a flag indicating if the server saved exports match the original layout.
    * If this is false, the export will expand its components.
    *
    * @param saveToServerMatch {@code true} if match layout; {@code false} if it expands the components.
    */
   public void setSaveToServerMatch(boolean saveToServerMatch) {
      this.saveToServerMatch = saveToServerMatch;
   }

   /**
    * Sets a flag indicating if the server saved exports expand their selections.
    * saveToServerMatch must be false for this to apply
    *
    * @return {@code true} if it expands selection components; {@code false} otherwise.
    */
   @Schema(
      description = "A flag indicating if the server saved exports expand their selection lists/trees.",
      example = "false")
   public boolean isSaveToServerExpandSelections() {
      return saveToServerExpandSelections;
   }

   /**
    * Sets a flag indicating if the server saved exports expand their selections.
    * saveToServerMatch must be false for this to apply
    *
    * @param saveToServerExpandSelections {@code true} if it expands selection components; {@code false} otherwise.
    */
   public void setSaveToServerExpandSelections(boolean saveToServerExpandSelections) {
      this.saveToServerExpandSelections = saveToServerExpandSelections;
   }

   /**
    * Gets a flag indicating if the exports only expand the data components.
    * saveToServerMatch must be false for this to apply
    *
    * @return {@code true} if expand data components only; {@code false} otherwise.
    */
   @Schema(
      description = "A flag indicating if the exports only expand the data components.",
      example = "false")
   public boolean isSaveToServerOnlyDataComponents() {
      return saveToServerOnlyDataComponents;
   }

   /**
    * Sets a flag indicating if the exports only expand the data components.
    * saveToServerMatch must be false for this to apply
    *
    * @param saveToServerOnlyDataComponents {@code true} if expand data components only; {@code false} otherwise.
    */
   public void setSaveToServerOnlyDataComponents(boolean saveToServerOnlyDataComponents) {
      this.saveToServerOnlyDataComponents = saveToServerOnlyDataComponents;
   }

   /**
    * Gets a flag indicating if export all tab table when save to server.
    *
    * @return {@code true} if export all tab table when save to server; {@code false} otherwise.
    */
   @Schema(
      description = "A flag indicating if export all tab table when save to server.",
      example = "false")
   public boolean isSaveExportAllTabbedTables() {
      return saveExportAllTabbedTables;
   }

   /**
    * Sets a flag indicating if export all tab table when save to server.
    *
    * @param saveExportAllTabbedTables {@code true} if export all tab table when save to server; {@code false} otherwise.
    */
   public void setSaveExportAllTabbedTables(boolean saveExportAllTabbedTables) {
      this.saveExportAllTabbedTables = saveExportAllTabbedTables;
   }

   /**
    * Gets the csv configuration properties used for csv exports to the server.
    *
    * @return the csv configuration properties used for csv exports to the server.
    */
   @Schema(
      description = "The csv configuration properties used for csv exports to the server.")
   public CSVConfig getSaveToServerCsvConfig() {
      return saveToServerCsvConfig;
   }

   /**
    * Sets the csv configuration properties used for csv exports to the server.
    *
    * @param saveToServerCsvConfig the csv configuration properties used for csv exports to the server.
    */
   public void setSaveToServerCsvConfig(CSVConfig saveToServerCsvConfig) {
      this.saveToServerCsvConfig = saveToServerCsvConfig;
   }

   @Override
   public boolean equals(Object o) {
      if(this == o) {
         return true;
      }

      if(o == null || getClass() != o.getClass()) {
         return false;
      }

      if(!super.equals(o)) {
         return false;
      }

      ViewsheetAction that = (ViewsheetAction) o;
      return htmlMessage == that.htmlMessage &&
         Objects.equals(viewsheet, that.viewsheet) &&
         Objects.equals(bookmarkName, that.bookmarkName) &&
         Objects.equals(bookmarkNames, that.bookmarkNames) &&
         Objects.equals(bookmarkTypes, that.bookmarkTypes) &&
         Objects.equals(bookmarkUser, that.bookmarkUser) &&
         bookmarkType == that.bookmarkType &&
         Objects.equals(emails, that.emails) &&
         Objects.equals(sender, that.sender) &&
         Objects.equals(notifies, that.notifies) &&
         Objects.equals(notifyIfFailed, that.notifyIfFailed) &&
         Objects.equals(notifyLink, that.notifyLink) &&
         format == that.format &&
         Objects.equals(subject, that.subject) &&
         Objects.equals(message, that.message) &&
         Objects.equals(ccAddresses, that.ccAddresses) &&
         Objects.equals(bccAddresses, that.bccAddresses) &&
         Objects.equals(emailMatch, that.emailMatch) &&
         Objects.equals(emailExpandSelections, that.emailExpandSelections) &&
         Objects.equals(emailOnlyDataComponents, that.emailOnlyDataComponents) &&
         Objects.equals(emailZip, that.emailZip) &&
         Objects.equals(attachmentName, that.attachmentName) &&
         Objects.equals(emailLink, that.emailLink) &&
         Objects.equals(alerts, that.alerts) &&
         Objects.equals(saveToServerFilePaths, that.saveToServerFilePaths) &&
         saveToServerMatch == that.saveToServerMatch &&
         saveToServerExpandSelections == that.saveToServerExpandSelections &&
         saveToServerOnlyDataComponents == that.saveToServerOnlyDataComponents &&
         Objects.equals(saveToServerCsvConfig, that.saveToServerCsvConfig) &&
         Objects.equals(parameters, that.parameters);
   }

   @Override
   public int hashCode() {
      return Objects.hash(
         super.hashCode(), viewsheet, bookmarkName, bookmarkNames, bookmarkUser, bookmarkType,
         bookmarkTypes, saveToServerFilePaths, emails, sender,
         notifies, notifyIfFailed, notifyLink, format, subject, message, htmlMessage,
         ccAddresses, bccAddresses, emailMatch, emailExpandSelections, emailOnlyDataComponents,
         emailZip, attachmentName, emailLink, saveToServerFilePaths, saveToServerMatch,
         saveToServerExpandSelections, saveToServerOnlyDataComponents, saveToServerCsvConfig, parameters, alerts);
   }

   @Override
   public String toString() {
      return "ViewsheetAction{" +
         "viewsheet='" + viewsheet + '\'' +
         ", bookmarkName='" + bookmarkName + '\'' +
         ", bookmarkNames=" + bookmarkNames +
         ", bookmarkUser='" + bookmarkUser + '\'' +
         ", bookmarkUsers='" + (bookmarkUsers == null ? null : bookmarkUsers.toString()) + '\'' +
         ", bookmarkType=" + bookmarkType +
         ", bookmarkTypes=" + bookmarkTypes +
         ", saveToServerFilePaths=" + saveToServerFilePaths +
         ", emails=" + emails +
         ", sender='" + sender + '\'' +
         ", notifies=" + notifies +
         ", notifyIfFailed=" + notifyIfFailed +
         ", notifyLink=" + notifyLink +
         ", format=" + format +
         ", subject='" + subject + '\'' +
         ", message='" + message + '\'' +
         ", htmlMessage=" + htmlMessage +
         ", ccAddresses='" + ccAddresses + '\'' +
         ", bccAddresses='" + bccAddresses + '\'' +
         ", emailMatch=" + emailMatch +
         ", emailExpandSelections=" + emailExpandSelections +
         ", emailOnlyDataComponents=" + emailOnlyDataComponents +
         ", emailZip=" + emailZip +
         ", attachmentName='" + attachmentName + '\'' +
         ", emailLink=" + emailLink +
         ", alerts=" + alerts +
         ", saveToServerFilePaths=" + saveToServerFilePaths +
         ", saveToServerMatch=" + saveToServerMatch +
         ", saveToServerExpandSelections=" + saveToServerExpandSelections +
         ", saveToServerOnlyDataComponents=" + saveToServerOnlyDataComponents +
         ", saveToServerCsvConfig=" + saveToServerCsvConfig +
         ", parameters=" + parameters +
         '}';
   }

   /**
    * SaveToServerFilePath describes the format and file path this action will save to the server.
    */
   @Validated
   @Schema(description = "Describes the format and file path for a report export.")
   public static class SaveToServerFilePath {
      /**
       * Creates a new instance of {@code SaveToServerFilePath}.
       */
      public SaveToServerFilePath() {
      }

      /**
       * Creates a new instance of {@code SaveToServerFilePath}.
       *
       * @param format the save to server file format.
       * @param path   the save to server file path.
       */
      public SaveToServerFilePath(Format format, String path) {
         this.format = format;
         this.path = path;
      }

      /**
       * Creates a new instance of {@code SaveToServerFilePath}.
       *
       * @param format the save to server file format.
       * @param info   the save to server file path info.
       */
      public SaveToServerFilePath(Format format, ServerPathInfo info) {
         this.format = format;
         this.path = info.getPath();
         this.useCredential = info.isUseCredential();

         if(info.isUseCredential()) {
            this.secretId = info.getSecretId();
         }
         else {
            this.username = info.getUsername();
            this.password = info.getPassword();
         }
      }

      /**
       * Gets the format of the file that will be saved to the server.
       *
       * @return the format of the file that will be saved to the server.
       */
      @NotNull
      @Schema(description = "The format in which the file will be saved.", example = "PDF")
      public Format getFormat() {
         return format;
      }

      /**
       * Sets the format of the file that will be saved to the server.
       *
       * @param format the format of the file that will be saved to the server.
       */
      public void setFormat(Format format) {
         this.format = format;
      }

      /**
       * Gets the path of the file that will be saved to the server.
       *
       * @return the path of the file that will be saved to the server.
       */
      @NotNull
      @Schema(
         description = "The path at which the file will be saved.",
         example = "/tmp/weekly-summary.pdf")
      public String getPath() {
         return path;
      }

      /**
       * Sets the path of the file that will be saved to the server.
       *
       * @param path the path of the file that will be saved to the server.
       */
      public void setPath(String path) {
         this.path = path;
      }

      /**
       * Gets the username used to access the ftp server if applicable.
       *
       * @return the username used to access the ftp server.
       */
      @Schema(
         description = "The username used to access the ftp server.")
      public String getUsername() {
         return username;
      }

      /**
       * Sets the username used to access the ftp server.
       *
       * @param username the username used to access the ftp server.
       */
      public void setUsername(String username) {
         this.username = username;
      }

      /**
       * Gets the password used to access the ftp server if applicable.
       *
       * @return the password used to access the ftp server.
       */
      @Schema(
         description = "The password used to access the ftp server.")
      public String getPassword() {
         return password;
      }

      /**
       * Sets the password used to access the ftp server.
       *
       * @param password the password used to access the ftp server.
       */
      public void setPassword(String password) {
         this.password = password;
      }

      @Schema(description = "Whether to use a vault secret ID for FTP credentials.")
      public boolean isUseCredential() {
         return useCredential;
      }

      public void setUseCredential(boolean useCredential) {
         this.useCredential = useCredential;
      }

      @Schema(description = "The vault secret ID for FTP credentials.")
      public String getSecretId() {
         return secretId;
      }

      public void setSecretId(String secretId) {
         this.secretId = secretId;
      }

      @Override
      public boolean equals(Object o) {
         if(this == o) {
            return true;
         }

         if(o == null || getClass() != o.getClass()) {
            return false;
         }

         SaveToServerFilePath that = (SaveToServerFilePath) o;
         return useCredential == that.useCredential &&
            Objects.equals(format, that.format) && Objects.equals(path, that.path) &&
            Objects.equals(username, that.username) && Objects.equals(password, that.password) &&
            Objects.equals(secretId, that.secretId);
      }

      @Override
      public int hashCode() {
         return Objects.hash(format, path, username, password, useCredential, secretId);
      }

      @Override
      public String toString() {
         StringBuilder authString = new StringBuilder();

         if(useCredential) {
            authString.append(", useCredential=true");

            if(secretId != null) {
               authString.append(", secretId='" + secretId + '\'');
            }
         }
         else {
            if(username != null) {
               authString.append(", username='" + username + '\'');
            }

            if(password != null) {
               authString.append(", password='" + password + '\'');
            }
         }

         return "SaveToServerFilePath{" +
            "format='" + format + '\'' +
            ", path='" + path + '\'' +
            authString +
            '}';
      }

      private Format format;
      private String path;
      private String username;
      private String password;
      private boolean useCredential;
      private String secretId;
   }

   private String viewsheet;
   private String bookmarkName; //@bc redundant field is kept for the sake of backwards compatibility
   private List<String> bookmarkNames;
   private IdentityID bookmarkUser; //@bc redundant field is kept for the sake of backwards compatibility
   private List<IdentityID> bookmarkUsers;
   private BookmarkType bookmarkType; //@bc redundant field is kept for the sake of backwards compatibility
   private List<BookmarkType> bookmarkTypes;
   private List<SaveToServerFilePath> saveToServerFilePaths;
   private List<String> emails;
   private String sender;
   private List<String> notifies;
   private Boolean notifyIfFailed;
   private Boolean notifyLink;
   private Format format;
   private String subject;
   private String message;
   private boolean htmlMessage;
   private String ccAddresses;
   private String bccAddresses;
   private Boolean emailMatch;
   private Boolean emailExpandSelections;
   private Boolean emailOnlyDataComponents;
   private Boolean exportAllTabbedTables;
   private Boolean emailZip;
   private CSVConfig emailCSVConfig;
   private String attachmentName;
   private Boolean emailLink;
   private Map<String, Object> parameters;
   private List<ScheduleAlert> alerts;
   private boolean saveToServerMatch = true;
   private boolean saveToServerExpandSelections = false;
   private boolean saveToServerOnlyDataComponents = false;
   private boolean saveExportAllTabbedTables = false;
   private CSVConfig saveToServerCsvConfig;

   /**
    * Enumeration of the types of bookmarks.
    */
   public enum BookmarkType {
      /**
       * A bookmark that is private to the owner.
       */
      PRIVATE("private", VSBookmarkInfo.PRIVATE),

      /**
       * A bookmark shared with all users.
       */
      ALL_SHARE("all_share", VSBookmarkInfo.ALLSHARE),

      /**
       * A bookmark shared with members of the same group as the owner.
       */
      GROUP_SHARE("group_share", VSBookmarkInfo.GROUPSHARE);

      private final String value;
      private final int code;

      BookmarkType(String value, int code) {
         this.value = value;
         this.code = code;
      }

      public int code() {
         return code;
      }

      @JsonValue
      @Override
      public String toString() {
         return value;
      }

      @SuppressWarnings("unused")
      @JsonCreator
      public static BookmarkType fromValue(String value) {
         for(BookmarkType type : values()) {
            if(Objects.equals(type.value, value)) {
               return type;
            }
         }

         return null;
      }

      public static BookmarkType fromCode(int code) {
         for(BookmarkType type : values()) {
            if(type.code == code) {
               return type;
            }
         }

         return null;
      }
   }

   /**
    * Enumeration of the types of viewsheet file formats.
    */
   public enum Format {
      /**
       * An MS Excel file.
       */
      EXCEL(0),

      /**
       * A MS Powerpoint file.
       */
      POWERPOINT(1),

      /**
       * A PDF file.
       */
      PDF(2),

      /**
       * A PNG file.
       */
      PNG(4),

      /**
       * An HTML file without pagination, embedded in the body of an email message.
       */
      HTML(5),

      /**
       * A delimited text file.
       */
      CSV(6);

      private int value;

      Format(int value) {
         this.value = value;
      }

      public int getTypeValue() {
         return value;
      }
   }
}
