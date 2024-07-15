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

import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.sree.internal.Mailer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.security.Principal;
import java.util.Collection;
import java.util.List;

/**
 * Default implementation of the ScheduleMailService that uses the Mailer to send
 * the emails.
 *
 * @author InetSoft Technology Corp
 * @version 12.1
 */
public class DefaultScheduleMailService implements ScheduleMailService {
   public DefaultScheduleMailService() {
      this.mailer = new Mailer();
   }

   @Override
   public void emailViewsheet(Principal principal, RuntimeViewsheet rvs, String format,
      boolean compress, String to, String from, String subject, File file,
      String message, boolean htmlMessage) throws Exception
   {
      mailer.send(to, null, null, from, subject, message, file, null, false, htmlMessage);
   }

   @Override
   public void emailViewsheet(Principal principal, RuntimeViewsheet rvs, String format,
                              boolean compress, String to, String from, String cc, String bcc,
                              String subject, File file, String message, boolean htmlMessage)
      throws Exception
   {
      mailer.send(to, cc, bcc, from, subject, message, file, null, false,
         htmlMessage);
   }

   @Override
   public void emailViewsheet(Principal principal, RuntimeViewsheet rvs, String format,
                              boolean compress, String to, String from, String subject, File file,
                              Collection<?> images, String message, boolean htmlMessage) throws Exception
   {
      mailer.send(to, null, null, from, subject, message, file, images, images != null, htmlMessage);
   }

   @Override
   public void emailViewsheet(Principal principal, RuntimeViewsheet rvs, String format,
                              boolean compress, String to, String from, String cc, String bcc,
                              String subject, File file, Collection<?> images, String message,
                              boolean htmlMessage)
      throws Exception
   {
      mailer.send(to, cc, bcc, from, subject, message, file, images, images != null, htmlMessage);
   }

   @Override
   public void emailViewsheet(Principal principal, RuntimeViewsheet rvs, String format,
                              boolean compress, String to, String from, String subject,
                              List<File> files, Collection<?> images, String message,
                              boolean htmlMessage)
      throws Exception
   {
      File[] attach = files.toArray(new File[0]);
      mailer.send(to, null, null, from, subject, message, attach, images, images != null, htmlMessage);
   }

   private Mailer mailer;

   private static final Logger LOG =
      LoggerFactory.getLogger(DefaultScheduleMailService.class);
}
