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

import inetsoft.sree.internal.HttpXMLSerializable;
import inetsoft.sree.internal.SUtil;
import inetsoft.sree.security.SecurityEngine;
import inetsoft.util.Catalog;
import inetsoft.util.Tool;
import org.w3c.dom.Element;

/**
 * Schedule condition to check if a specified task has completed.
 *
 * @version 5.1, 9/20/2003
 * @author InetSoft Technology Corp
 */
public class CompletionCondition implements ScheduleCondition,
                                            HttpXMLSerializable
{
   /**
    * Create an empty CompletionCondition.
    */
   public CompletionCondition() {
   }

   /**
    * Create a CompletionCondition for specified task.
    * @param taskname the name of the task to check.
    */
   public CompletionCondition(String taskname) {
      this.taskname = taskname;
   }

   /**
    * Check the condition immediately before execution
    * @param time current time.
    * @return true if the condition is met.
    */
   @Override
   public boolean check(long time) {
      boolean d = depencyCompleted;

      // completion condition should be checked for only once
      depencyCompleted = false;

      return d;
   }

   /**
    * set the complete status.
    * @param complete current time.
    */
   public void setComplete(boolean complete) {
      depencyCompleted = complete;
   }

   /**
    * Get the next time to retry the condition. Complete Condition
    * should not be retried. It waits for the depencyComplted to be set.
    * @param time current time.
    * @return the next time to retry. Negative value to stop retry.
    */
   @Override
   public long getRetryTime(long time) {
      // completion condition only trigger once
      if(depencyCompleted) {
         return time;
      }

      return -1;
   }

   public String getTaskName() {
      return taskname;
   }

   public void setTaskName(String taskname) {
      this.taskname = taskname;
   }

   public String toString() {
      String taskLabel;

      if(SecurityEngine.getSecurity().isSecurityEnabled()) {
         taskLabel = SUtil.getTaskNameWithoutOrg(taskname);
      }
      else {
         int index = taskname == null ? -1 : taskname.indexOf(':');
         taskLabel = index == -1 ? taskname : taskname.substring(index + 1);
      }

      return Catalog.getCatalog().getString("CompletionCondition") + ": " + taskLabel;
   }

   /**
    * Write itself to an xml file.
    */
   @Override
   public void writeXML(java.io.PrintWriter writer) {
      writer.println("<Condition type=\"Completion\" task=\"" +
         Tool.escape(taskname) + "\"/>");
   }

   /**
    * Parse itself from an xml file.
    */
   @Override
   public void parseXML(Element tag) throws Exception {
      taskname = byteDecode(Tool.getAttribute(tag, "task"));
   }

   public boolean equals(Object val) {
      return (val instanceof CompletionCondition) &&
         ((CompletionCondition) val).getTaskName().equals(taskname);
   }

   /**
    * Encode non-ascii characters to unicode enclosed in '[]'.
    * @param source source string.
    * @return encoded string.
    */
   @Override
   public String byteEncode(String source) {
      return encoding ? Tool.byteEncode(source) : source;
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

   private String taskname;
   private boolean depencyCompleted = false;
   private boolean encoding = false;
}
