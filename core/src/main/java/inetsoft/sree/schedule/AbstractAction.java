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

import inetsoft.sree.SreeEnv;
import inetsoft.sree.internal.HttpXMLSerializable;
import inetsoft.sree.internal.SUtil;
import inetsoft.sree.security.IdentityID;
import inetsoft.util.Tool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This is the base class for action.
 *
 * @version 10.3, 5/3/2010
 * @author InetSoft Technology Corp
 */
public abstract class AbstractAction implements Cloneable, EmailSupport,
   HttpXMLSerializable, CancelableAction
{
   /**
    * Add a notification email to notify when this action is completed.
    * @param email comma separated email list.
    */
   public void notify(String email) {
      notifies = email;
   }

   public EmailInfo getEmailInfo() {
      return emailInfo;
   }

   /**
    * Set the emails to send the report notification.
    * @param emails comma separated email list.
    */
   @Override
   public void setEmails(String emails) {
      emailInfo.setEmails(emails);
   }

   /**
    * Get all emails to send the report.
    */
   @Override
   public String getEmails() {
      return emailInfo.getEmails();
   }

   @Override
   public String getCCAddresses() {
      return emailInfo.getCCAddresses();
   }

   @Override
   public void setCCAddresses(String ccAddresses) {
      emailInfo.setCCAddresses(ccAddresses);
   }

   @Override
   public String getBCCAddresses() {
      return emailInfo.getBCCAddresses();
   }

   @Override
   public void setBCCAddresses(String bccAddresses) {
      emailInfo.setBCCAddresses(bccAddresses);
   }

   /**
    * Get all notification emails.
    */
   public boolean isLink() {
      return this.link;
   }

   /**
    * Set whether to link report.
    */
   public void setLink(boolean link) {
      this.link = link;
   }

   /**
    * Get link uri.
    */
   public String getLinkURI() {
      return this.linkURI;
   }

   /**
    * Set link uri.
    */
   public void setLinkURI(String linkURI) {
      this.linkURI = linkURI;
   }

   /**
    * Set the notification list.
    */
   @Override
   public void setNotifications(String notifies) {
      this.notifies = notifies;
   }

   /**
    * Get all notification emails.
    */
   @Override
   public String getNotifications() {
      return notifies;
   }

   /**
    * Set the from text of the email that is sent by this replet action.
    * @param from the from text of the email header.
    */
   @Override
   public void setFrom(String from) {
      if(from != null && !from.equals("")) {
         emailInfo.setFrom(from);
      }
   }

   /**
    * Get the from text of the email header.
    * @return from text of the email header.
    */
   @Override
   public String getFrom() {
      return emailInfo.getFrom();
   }

   /**
    * Set email message.
    */
   @Override
   public void setMessage(String msg) {
      emailInfo.setMessage(msg);
   }

   /**
    * Get the email text message.
    */
   @Override
   public String getMessage() {
      return emailInfo.getMessage();
   }

   /**
    * Get deliver link.
    */
   public boolean isDeliverLink() {
      return this.deliverLink;
   }

   /**
    * Set whether to link report.
    */
   public void setDeliverLink(boolean link) {
      this.deliverLink = link;
   }

   /**
    * Sets the flag that indicates if the email message body is formatted as
    * HTML.
    *
    * @param messageHtml <tt>true</tt> if HTML; <tt>false</tt> otherwise.
    */
   public void setMessageHtml(boolean messageHtml) {
      emailInfo.setMessageHtml(messageHtml);
   }

   /**
    * Gets the flag that indicates if the email message body is formatted as
    * HTML.
    *
    * @return <tt>true</tt> if HTML; <tt>false</tt> otherwise.
    */
   public boolean isMessageHtml() {
      return emailInfo.isMessageHtml();
   }

   /**
    * Set the email subject.
    */
   @Override
   public void setSubject(String subject) {
      emailInfo.setSubject(subject);
   }

   /**
    * Get the email subject.
    */
   @Override
   public String getSubject() {
      return emailInfo.getSubject();
   }

   /**
    * Set the email file format.
    */
   @Override
   public void setFileFormat(String format) {
      emailInfo.setFileFormat(format);
   }

   /**
    * Get the email file format.
    */
   @Override
   public String getFileFormat() {
      return emailInfo.getFileFormat();
   }

   /**
    * Set the flag indicating whether or not the exported
    * file is to be zipped up before it is delivered.
    */
   @Override
   public void setCompressFile(boolean compress) {
      emailInfo.setCompressFile(compress);
   }

   /**
    * Check if the exported file needs to be zipped up.
    */
   @Override
   public boolean isCompressFile() {
      return emailInfo.isCompressFile();
   }

   /**
    * Set email attachmentName.
    */
   public void setAttachmentName(String attachmentName) {
      emailInfo.setAttachmentName(attachmentName);
   }

   /**
    * Get email attachmentName.
    */
   public String getAttachmentName() {
      return emailInfo.getAttachmentName();
   }

   public void setUseCredential(boolean useCredential) {
      emailInfo.setUseCredential(useCredential);
   }

   public boolean isUseCredential() {
      return emailInfo.isUseCredential();
   }

   public String getSecretId() {
      return emailInfo.getSecretId();
   }

   public void setSecretId(String secretId) {
      emailInfo.setSecretId(secretId);
   }

   /**
    * Set the encrypt zip file password.
    * @param password the encrypt file password.
    */
   public void setPassword(String password) {
      emailInfo.setPassword(password);
   }

   /**
    * Get the encrypt zip file password.
    * @return the encrypt file password.
    */
   public String getPassword() {
      return emailInfo.getPassword();
   }

   /**
    * Set the flag indicating whether or not send
    * a success email.
    * @param isNotifyError notify only if failed.
    */
   public void setNotifyError(boolean isNotifyError) {
      this.isNotifyError = isNotifyError;
   }

   /**
    * Notify only if failed.
    */
   public boolean isNotifyError() {
      return isNotifyError;
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
      emailInfo.setEncoding(encoding);
   }

   /**
    * Cancel the action.
    */
   @Override
   public void cancel() {
      isCanceled = true;
   }

   /**
    * Cancel the action.
    */
   protected boolean isCanceled() {
      if(isCanceled) {
         isCanceled = false;
         return true;
      }

      return false;
   }

   /**
    * Get notification emails.
    */
   protected String getEmails(String emailStr) {
      return SUtil.convertEmailsString(emailStr);
   }

   /**
    * Get the from text of the email header.
    * (1) get the action config email when from email is editable.
    * (2) get the owner email if from email is not editable and owner email is configured.
    * (3) default return the email is configured by mail.from.address property.
    * @return from text of the email header.
    */
   protected String getFrom(IdentityID user) {
      if(!fromEmailEdiable()) {
         String[] emails = new String[0];

         try {
            emails = SUtil.getEmails(user);
         } catch(Exception e) {
            // Do nothing
         }

         boolean useSelf = !"false".equals(SreeEnv.getProperty("em.mail.defaultEmailFromSelf"));
         String userEmail = useSelf && emails.length > 0 ? emails[0] : null;
         return userEmail != null && !userEmail.isEmpty() ?
            userEmail : SreeEnv.getProperty("mail.from.address");
      }
      else {
         return getFrom();
      }
   }

   /**
    * Check whether the form email can be edited.
    * @return
    */
   protected boolean fromEmailEdiable() {
      return "true".equals(SreeEnv.getProperty("mail.from.enabled", "false"));
   }

   protected transient boolean encoding = false;
   protected boolean isNotifyError = false;
   protected String notifies = "";
   protected EmailInfo emailInfo = new EmailInfo();
   protected boolean link = false;
   protected boolean deliverLink = false;
   protected String linkURI;

   private boolean isCanceled = false;

   private static final Logger LOG =
      LoggerFactory.getLogger(AbstractAction.class);
}
