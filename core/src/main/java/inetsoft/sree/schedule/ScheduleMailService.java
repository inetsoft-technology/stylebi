/*
 * inetsoft-core - StyleBI is a business intelligence web application.
 * Copyright © 2024 InetSoft Technology (info@inetsoft.com)
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
 * You should have received a copy of the GNU Affrero General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package inetsoft.sree.schedule;

import inetsoft.report.composition.RuntimeViewsheet;

import java.io.File;
import java.security.Principal;
import java.util.Collection;
import java.util.List;

/**
 * ScheduleMailService interface that deals with sending viewsheets/replets over email
 *
 * @version 12.1
 * @author InetSoft Technology Corp
 */
public interface ScheduleMailService {
   /**
    * Emails the viewsheet given the information provided.
    *
    * @param principal a user that executed the scheduled task
    * @param rvs runtime viewsheet
    * @param format format in which the viewsheet has been exported
    * @param compress defines whether the file is zipped
    * @param to recipients of this email message
    * @param from sender of this email message
    * @param subject subject of this email message
    * @param file file to be attached
    * @param message message content
    * @param htmlMessage defines whether the message body is formatted as HTML
    * @throws Exception
    */
   void emailViewsheet(Principal principal, RuntimeViewsheet rvs, String format,
      boolean compress, String to, String from, String subject, File file,
      String message, boolean htmlMessage) throws Exception;

   /**
    * Emails the viewsheet given the information provided.
    *
    * @param principal a user that executed the scheduled task
    * @param rvs runtime viewsheet
    * @param format format in which the viewsheet has been exported
    * @param compress defines whether the file is zipped
    * @param to recipients of this email message
    * @param from sender of this email message
    * @param cc cc of this email message
    * @param bcc bcc of this email message
    * @param subject subject of this email message
    * @param file file to be attached
    * @param message message content
    * @param htmlMessage defines whether the message body is formatted as HTML
    * @throws Exception
    */
   void emailViewsheet(Principal principal, RuntimeViewsheet rvs, String format,
                       boolean compress, String to, String from, String cc, String bcc,
                       String subject, File file, String message,
                       boolean htmlMessage) throws Exception;

   /**
    * Emails the viewsheet given the information provided.
    *
    * @param principal a user that executed the scheduled task
    * @param rvs runtime viewsheet
    * @param format format in which the viewsheet has been exported
    * @param compress defines whether the file is zipped
    * @param to recipients of this email message
    * @param from sender of this email message
    * @param subject subject of this email message
    * @param file file to be attached
    * @param images images contained in the html
    * @param message message content
    * @param htmlMessage defines whether the message body is formatted as HTML
    * @throws Exception
    */
   void emailViewsheet(Principal principal, RuntimeViewsheet rvs, String format,
                       boolean compress, String to, String from, String subject, File file,
                       Collection<?> images, String message, boolean htmlMessage) throws Exception;

   /**
    * Emails the viewsheet given the information provided.
    *
    * @param principal a user that executed the scheduled task
    * @param rvs runtime viewsheet
    * @param format format in which the viewsheet has been exported
    * @param compress defines whether the file is zipped
    * @param to recipients of this email message
    * @param from sender of this email message
    * @param subject subject of this email message
    * @param file file to be attached
    * @param images images contained in the html
    * @param message message content
    * @param htmlMessage defines whether the message body is formatted as HTML
    * @throws Exception
    */
   void emailViewsheet(Principal principal, RuntimeViewsheet rvs, String format,
                       boolean compress, String to, String from,  String cc, String bcc,
                       String subject, File file, Collection<?> images, String message,
                       boolean htmlMessage) throws Exception;

   /**
    * Emails the viewsheet given the information provided.
    *
    * @param principal a user that executed the scheduled task
    * @param rvs runtime viewsheet
    * @param format format in which the viewsheet has been exported
    * @param compress defines whether the file is zipped
    * @param to recipients of this email message
    * @param from sender of this email message
    * @param subject subject of this email message
    * @param files files to be attached
    * @param images images contained in the html
    * @param message message content
    * @param htmlMessage defines whether the message body is formatted as HTML
    * @throws Exception
    */
   void emailViewsheet(Principal principal, RuntimeViewsheet rvs, String format,
                       boolean compress, String to, String from, String subject, List<File> files,
                       Collection<?> images, String message, boolean htmlMessage) throws Exception;

}
