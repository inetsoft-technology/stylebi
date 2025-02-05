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
package inetsoft.sree.internal;

import inetsoft.sree.SreeEnv;
import inetsoft.sree.internal.cluster.Cluster;
import inetsoft.sree.schedule.ScheduleClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Thread to monitor the scheduler and notify if it fails.
 */
public class ScheduleThread implements Runnable {
   public ScheduleThread(boolean stopped) {
      super();
      this.stopped = stopped;
   }

   @Override
   public void run() {
      boolean sendEmail = true;

      while(!stopped) {
         if(!client.isReady() && !stopped) {
            if(sendEmail) {
               sendScheduleOffEmail();
               sendEmail = false;
            }
         }
         else {
            sendEmail = true;
         }

         try {
            Thread.sleep(600000);
         }
         catch(Exception e) {
         }
      }
   }

   public static void sendEmail(String subject, String message) {
      Mailer mailer = new Mailer();
      String emailAddress = SreeEnv.getProperty(
         "schedule.status.check.email");
      String mailServers = SreeEnv.getProperty("mail.smtp.host");
      String jndiUrl = SreeEnv.getProperty("mail.jndi.url");

      if(isEmpty(emailAddress) || (isEmpty(mailServers) && isEmpty(jndiUrl))) {
         return;
      }

      emailAddress = SUtil.convertEmailsString(emailAddress);

      try {
         mailer.send(emailAddress, "", subject, message, null);
      }
      catch(Exception e) {
         LOG.warn("Failed to send email message", e);
      }
   }

   public static void sendScheduleOffEmail() {
      if(SreeEnv.getBooleanProperty("schedule.options.scheduleIsDown", "true", "CHECKED")) {
         String subject = SreeEnv.getProperty("schedule.status.check.email.subject");
         String message = SreeEnv.getProperty("schedule.status.check.email.message");

         if(SUtil.isCluster()) {
            String currentNode = Cluster.getInstance().getLocalMember();
            String hostName = SUtil.computeServerClusterNode(currentNode);

            if(hostName != null) {
               message = message + ". This message was sent from " + hostName;
            }
         }

         sendEmail(subject, message);
      }
   }

   /**
    * Sets the flag that tells this thread that it should stop at the next
    * opportunity.
    *
    * @param stopped <code>true</code> if the thread is stopped.
    */
   public void setStopped(boolean stopped) {
      this.stopped = stopped;
   }

   /**
    * Check if the schedule server stopped.
    */
   public boolean isStopped() {
      return this.stopped;
   }

   /**
    * Check if is empty string.
    */
   private static boolean isEmpty(String str) {
      return str == null || str.trim().length() == 0;
   }

   private boolean stopped = false;
   private ScheduleClient client = ScheduleClient.getScheduleClient();

   private static final Logger LOG =
      LoggerFactory.getLogger(ScheduleThread.class);
}
