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


/**
 * Defines the common API for actions that supports email delivery of reports.
 *
 * @version 7.0, 4/2/2005
 * @author InetSoft Technology Corp
 */
public interface EmailSupport extends ScheduleAction {
   /**
    * Set the emails to send the report.
    * @param emails comma separated email list.
    */
   public void setEmails(String emails);

   /**
    * Get all emails to send the report.
    */
   public String getEmails();

   /**
    * Get all emails to cc the report.
    * @return
    */
   public String getCCAddresses();

   /**
    * Set the emails to cc the report.
    * @param ccAddresses comma separated email list.
    */
   public void setCCAddresses(String ccAddresses);

   /**
    * Get all emails to bcc the report.
    * @return
    */
   public String getBCCAddresses();

   /**
    * Set the emails to bcc the report.
    * @param bccAddresses comma separated email list.
    */
   public void setBCCAddresses(String bccAddresses);

   /**
    * Set the notification list.
    */
   public void setNotifications(String notifies);
   
   /**
    * Get all notification emails.
    */
   public String getNotifications();

   /**
    * Set the from text of the email that is sent by this replet action.
    * @param from the from text of the email header.
    */
   public void setFrom(String from);

   /**
    * Get the from text of the email header.
    * @return from text of the email header.
    */
   public String getFrom();

   /**
    * Set email message.
    */
   public void setMessage(String msg);

   /**
    * Get the email text message.
    */
   public String getMessage();

   /**
    * Set the email subject.
    */
   public void setSubject(String subject);

   /**
    * Get the email subject.
    */
   public String getSubject();

   /**
    * Set the email file format.
    */
   public void setFileFormat(String format);

   /**
    * Get the email file format.
    */
   public String getFileFormat();
   
   /**
    * Set the flag indicating whether or not the exported
    * file is to be zipped up before it is delivered.
    */
   public void setCompressFile(boolean compress);
   
   /**
    * Check if the exported file needs to be zipped up.
    */
   public boolean isCompressFile();
}
