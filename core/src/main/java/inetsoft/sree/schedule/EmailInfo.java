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
package inetsoft.sree.schedule;

import inetsoft.report.io.csv.CSVConfig;
import inetsoft.sree.internal.HttpXMLSerializable;
import inetsoft.uql.asset.SourceInfo;
import inetsoft.util.Tool;
import org.w3c.dom.Element;

import java.io.PrintWriter;
import java.io.Serializable;

/**
 * EmailInfo stores information related to emailing reports.
 *
 * @version 8.5, 6/26/2006
 * @author InetSoft Technology Corp
 */
class EmailInfo implements Cloneable, Serializable, HttpXMLSerializable {
   /**
    * Create an empty object.
    */
   public EmailInfo() {
   }

   /**
    * Set the emails to send the report.
    * @param emails comma separated email list.
    */
   public void setEmails(String emails) {
      this.emails = emails;
   }

   /**
    * Get all emails to send the report.
    */
   public String getEmails() {
      return emails;
   }

   /**
    * Set the from text of the email that is sent by this replet action.
    * @param from the from text of the email header.
    */
   public void setFrom(String from) {
      if(from != null && !from.equals("")) {
         this.from = from;
      }
   }

   /**
    * Get the from text of the email header.
    * @return from text of the email header.
    */
   public String getFrom() {
      return from;
   }

   /**
    * Set email message.
    */
   public void setMessage(String msg) {
      this.message = msg;
   }

   /**
    * Get the email text message.
    */
   public String getMessage() {
      return message;
   }

   /**
    * Gets the flag that indicates if the email message body is formatted as
    * HTML.
    *
    * @return <tt>true</tt> if HTML; <tt>false</tt> otherwise.
    */
   public boolean isMessageHtml() {
      return messageHtml;
   }

   /**
    * Sets the flag that indicates if the email message body is formatted as
    * HTML.
    *
    * @param messageHtml <tt>true</tt> if HTML; <tt>false</tt> otherwise.
    */
   public void setMessageHtml(boolean messageHtml) {
      this.messageHtml = messageHtml;
   }

   /**
    * Set email attachment's name.
    */
   public void setAttachmentName(String attachmentName) {
      this.attachmentName = attachmentName;
   }

   /**
    * Get the email attachment's name.
    */
   public String getAttachmentName() {
      return attachmentName;
   }

   /**
    * Set the email subject.
    */
   public void setSubject(String subject) {
      this.subject = subject;
   }

   /**
    * Get the email subject.
    */
   public String getSubject() {
      return subject;
   }

   /**
    * Set the email file format.
    */
   public void setFileFormat(String format) {
      this.format = format;
   }

   /**
    * Get the email file format.
    */
   public String getFileFormat() {
      return format;
   }

   /**
    * Set the csv file configuration.
    */
   public void setCSVConfig(CSVConfig csvConfig) {
      this.csvConfig = csvConfig;
   }

   /**
    * Get the csv file configuration.
    */
   public CSVConfig getCSVConfig() {
      return this.csvConfig;
   }

   /**
    * Set email binding query.
    */
   public void setQuery(SourceInfo query) {
      this.query = query;
   }

   /**
    * Get the email binding query.
    */
   public SourceInfo getQuery() {
      return query;
   }

   /**
    * Set email user column.
    */
   public void setUserColumn(String userColumn) {
      this.userColumn = userColumn;
   }

   /**
    * Get the email user column.
    */
   public String getUserColumn() {
      return userColumn;
   }

   /**
    * Set email column in the query.
    */
   public void setEmailColumn(String emailColumn) {
      this.emailColumn = emailColumn;
   }

   /**
    * Get email column in the query.
    */
   public String getEmailColumn() {
      return emailColumn;
   }

   /**
    * Write itself to a xml file
    */
   @Override
   public void writeXML(PrintWriter writer) {
      writer.print("<MailTo ");

      if(getEmails() != null) {
         writer.print(" email=\"" + Tool.escape(byteEncode(getEmails())) +
                      "\"");
      }

      if(getFrom() != null) {
         writer.print(" from=\"" + Tool.escape(byteEncode(getFrom())) + "\"");
      }

      if(getCCAddresses() != null) {
         writer.print(" ccAddresses=\"" + Tool.escape(byteEncode(getCCAddresses())) + "\"");
      }
      if(getBCCAddresses() != null) {
         writer.print(" bccAddresses=\"" + Tool.escape(byteEncode(getBCCAddresses())) + "\"");
      }


      if(getFileFormat() != null) {
         writer.print(" format=\"" + byteEncode(getFileFormat()) + "\"");
         writer.print(" compressFile=\"" + isCompressFile() + "\"");
         writer.print(" matched=\"" + isMatchLayout() + "\"");
         writer.print(" expandSelections=\"" + isExpandSelections() + "\"");
         writer.print(" onlyDataComponents=\"" + isOnlyDataComponents() + "\"");


         writer.print(" exportAllTabbedTables=\"" + isExportAllTabbedTables() + "\"");

         if(getPassword() != null && getPassword().length() > 0) {
            writer.print(" password=\"" +
               Tool.encryptPassword(byteEncode(getPassword())) + "\"");
         }
      }

      String message = getMessage();

      if(message != null) {
         message = isEncoding() ? message : Tool.escape(message);
         writer.print(" message=\"" + byteEncode(message) + "\"");
         writer.print(" messageHtml=\"" + Boolean.toString(messageHtml) + "\"");
      }

      String attachmentName = getAttachmentName();
      attachmentName = isEncoding() ? attachmentName :
                                      Tool.escape(attachmentName);

      if(attachmentName != null) {
         writer.print(" attachmentName=\"" + byteEncode(attachmentName) + "\"");
      }

      String subject = getSubject();
      subject = isEncoding() ? subject : Tool.escape(subject);

      if(subject != null) {
         writer.print(" subject=\"" + byteEncode(subject) + "\"");
      }

      if(userColumn != null) {
         writer.print(" userColumn=\"" + byteEncode(Tool.escape(userColumn)) +
            "\"");
      }

      if(emailColumn != null) {
         writer.print(" emailColumn=\"" + byteEncode(Tool.escape(emailColumn)) +
            "\"");
      }

      writer.println(">");

      if(query != null) {
         query.writeXML(writer);
      }

      if(getFileFormat() != null && getFileFormat().equals("CSV") && getCSVConfig() != null) {
         csvConfig.writeXML(writer);
      }

      writer.println("</MailTo>");
   }

   /**
    * Parse the replet action definition from xml.
    */
   @Override
   public void parseXML(Element tag) throws Exception {
      emails = tag.getAttribute("email");
      emails = byteDecode(emails);

      from = tag.getAttribute("from");
      from = byteDecode(from);

      format = tag.getAttribute("format");
      format = byteDecode(format);

      ccAddresses = tag.getAttribute("ccAddresses");
      ccAddresses = byteDecode(ccAddresses);

      bccAddresses = tag.getAttribute("bccAddresses");
      bccAddresses = byteDecode(bccAddresses);

      compressFile = "true".equals(tag.getAttribute("compressFile"));
      matched = "true".equals(tag.getAttribute("matched"));
      expandSelections = "true".equals(tag.getAttribute("expandSelections"));
      onlyDataComponents = "true".equals(tag.getAttribute("onlyDataComponents"));
      exportAllTabbedTables = "true".equals(tag.getAttribute("exportAllTabbedTables"));

      password = tag.getAttribute("password");

      if(password != null && password.length() > 0) {
         password = byteDecode(Tool.decryptPassword(password));
      }

      message = tag.getAttribute("message");
      message = byteDecode(message);

      messageHtml = "true".equals(tag.getAttribute("messageHtml"));

      attachmentName = tag.getAttribute("attachmentName");
      attachmentName = byteDecode(attachmentName);

      subject = tag.getAttribute("subject");
      subject = byteDecode(subject);

      if(tag.hasAttribute("userColumn")) {
         userColumn = tag.getAttribute("userColumn");
         userColumn = byteDecode(userColumn);
      }
      else {
         userColumn = null;
      }

      if(tag.hasAttribute("emailColumn")) {
         emailColumn = tag.getAttribute("emailColumn");
         emailColumn = byteDecode(emailColumn);
      }
      else {
         emailColumn = null;
      }

      Element node = Tool.getChildNodeByTagName(tag, "sourceInfo");

      if(node != null) {
         if(query == null) {
            query = new SourceInfo();
         }

         query.parseXML(node);
      }

      node = Tool.getChildNodeByTagName(tag, "CSVConfig");

      if(node != null) {
         if(csvConfig == null) {
            csvConfig = new CSVConfig();
         }

         csvConfig.parseXML(node);
      }
   }

   /**
    * Equals method.
    */
   public boolean equals(Object val) {
      if(!(val instanceof EmailInfo)) {
         return false;
      }

      EmailInfo ra = (EmailInfo) val;

      return
         Tool.equals(ra.getFrom(), from) &&
         Tool.equals(ra.getFileFormat(), format) &&
         ra.isCompressFile() == compressFile &&
         ra.isMatchLayout() == matched &&
            ra.isExpandSelections() == expandSelections && 
            ra.isOnlyDataComponents() == onlyDataComponents && 
         ra.isMessageHtml() == messageHtml &&
         Tool.equals(ra.getMessage(), message) &&
         Tool.equals(ra.getAttachmentName(), attachmentName) &&
         Tool.equals(ra.getSubject(), subject) &&
         Tool.equals(ra.getEmails(), emails) &&
         Tool.equals(ra.getPassword(), password);
   }

   /**
    * Encode non-ascii characters to unicode enclosed in '[]'.
    * @param source source string.
    * @return encoded string.
    */
   @Override
   public String byteEncode(String source) {
      return encoding ? Tool.byteEncode2(source) : source;
   }

   /**
    * Convert the encoded string to the original unencoded string.
    * @param encString a string encoded using the byteEncode method.
    * @return original string.
    */
   @Override
   public String byteDecode(String encString) {
      return encoding ? Tool.byteDecode(encString) : encString;
   }

   /**
    * Check if this object should encoded when writing.
    * @return <code>true</code> if should encoded, <code>false</code> otherwise.
    */
   @Override
   public boolean isEncoding() {
      return encoding;
   }

   /**
    * Set encoding flag.
    * @param encoding true to encode.
    */
   @Override
   public void setEncoding(boolean encoding) {
      this.encoding = encoding;
   }

   /**
    * Set the flag indicating whether or not the exported
    * file is to be zipped up before it is delivered.
    */
   public void setCompressFile(boolean compress) {
      this.compressFile = compress;
   }

   /**
    * Check if the exported file needs to be zipped up.
    */
   public boolean isCompressFile() {
      return compressFile;
   }

   /**
    * Set the selection of match layout.
    * @param matched the selection of match layout.
    */
   public void setMatchLayout(boolean matched) {
      this.matched = matched;
   }

   /**
    * If match the layout.
    * @return selection of match layout.
    */
   public boolean isMatchLayout() {
      return matched;
   }

   public void setExpandSelections(boolean expandSelections) {
      this.expandSelections = expandSelections;
   }

   public boolean isExpandSelections() {
      return expandSelections;
   }
   
   public void setOnlyDataComponents(boolean onlyDataComponents) {
      this.onlyDataComponents = onlyDataComponents;
   }

   public boolean isOnlyDataComponents() {
      return onlyDataComponents;
   }

   /**
    * Set the encrypt zip file password.
    * @param password the encrypt file password.
    */
   public void setPassword(String password) {
      this.password = password;
   }

   /**
    * Get the encrypt zip file password.
    * @return the encrypt file password.
    */
   public String getPassword() {
      return password;
   }

   /**
    * Get CC addresses.
    * @return
    */
   public String getCCAddresses() {
      return ccAddresses;
   }

   /**
    * Set CC addresses.
    * @param ccAddresses CC mails list.
    */
   public void setCCAddresses(String ccAddresses) {
      this.ccAddresses = ccAddresses;
   }

   /**
    * Get BCC addresses.
    * @return
    */
   public String getBCCAddresses() {
      return bccAddresses;
   }

   /**
    * Set BCC addresses.
    * @param bccAddresses BCC mails list
    */
   public void setBCCAddresses(String bccAddresses) {
      this.bccAddresses = bccAddresses;
   }

   public boolean isExportAllTabbedTables() {
      return exportAllTabbedTables;
   }

   public void setExportAllTabbedTables(boolean exportAllTabbedTables) {
      this.exportAllTabbedTables = exportAllTabbedTables;
   }

   private String emails = "";
   private String from = "stylereport@inetsoft.com";
   private String ccAddresses;
   private String bccAddresses;
   private String format = "PDF";
   private CSVConfig csvConfig = new CSVConfig();
   private String message = null;
   private boolean messageHtml = false;
   private String attachmentName = null;
   private String subject = null;
   private String userColumn = null;
   private String emailColumn = null;
   private SourceInfo query = null;
   private transient boolean encoding = false;

   // Indicates whether the file is to be zipped up
   // before deliverying it.
   private boolean compressFile = false;
   private String password = null;
   private boolean matched = false;
   private boolean expandSelections = false;
   private boolean onlyDataComponents = false;
   private boolean exportAllTabbedTables = false;
}
