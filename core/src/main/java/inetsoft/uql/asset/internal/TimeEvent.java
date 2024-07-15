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
package inetsoft.uql.asset.internal;

import inetsoft.util.Tool;
import inetsoft.util.XMLSerializable;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * The time event record a event information in viewsheet.
 * @version 10.3
 * @author InetSoft Technology Corp.
 */
public class TimeEvent implements XMLSerializable {
   /**
    * Constructor.
    */
   public TimeEvent() {
   }

   /**
    * Write the xml segment to print writer.
    * @param writer the destination print writer.
    */
   @Override
   public void writeXML(PrintWriter writer) {
      writer.print("<TimeEvent");

      if(pid != null) {
         writer.print(" pid=\"" + pid + "\"");
      }

      if(eventClassName != null) {
         writer.print(" eventClassName=\"" + eventClassName + "\"");
      }

      if(startTime != null) {
         writer.print(" startTime=\"" + df.format(startTime) + "\"");
      }

      if(endTime != null) {
         writer.print(" endTime=\"" + df.format(endTime) + "\"");
      }

      if(startProcessTime != null) {
         writer.print(" startProcessTime=\"" + df.format(startProcessTime) + "\"");
      }

      if(endProcessTime != null) {
         writer.print(" endProcessTime=\"" + df.format(endProcessTime) + "\"");
      }

      if(startDownloadTime != null) {
         writer.print(" startDownloadTime=\"" + df.format(startDownloadTime) +
            "\"");
      }

      if(endDownloadTime != null) {
         writer.print(" endDownloadTime=\"" + df.format(endDownloadTime) + "\"");
      }

      if(uploadSize != -1) {
         writer.print(" uploadSize=\"" + uploadSize + "\"");
      }

      if(downloadSize != -1) {
         writer.print(" downloadSize=\"" + downloadSize + "\"");
      }

      writer.print(" processTimeDiff=\"" + getProcessTime()  + "\"");
      writer.print(" downloadTimeDiff=\"" + downloadTimeDiff + "\"");
      writer.print(" timeDiff=\"" + timeDiff + "\"");

      writer.println(">");

      for(TimeCommand command : commands) {
         String cmdName = command.getCommandName();
         long timeDiff = command.getTimeDiff();
         Date startTime = command.getStartTime();
         Date endTime = command.getEndTime();
         int size = command.getSize();
         writer.println("<TimeCommand cmdName=\"" + cmdName + 
            "\" startTime=\"" + df.format(startTime) + 
            "\" endTime=\"" + df.format(endTime) + 
            "\" timeDiff=\"" + timeDiff + "\" size=\"" + size + "\"/>");
      }

      writer.println("</TimeEvent>");
   }

   /**
    * Method to parse an xml segment.
    */
   @Override
   public void parseXML(Element tag) throws Exception {
      eventClassName = Tool.getAttribute(tag, "eventClassName");
      String start = Tool.getAttribute(tag, "startTime");

      // parse from file
      if(start.indexOf(":") != -1) {
         startTime = df.parse(start);
         endTime = df.parse(Tool.getAttribute(tag, "endTime"));
         startDownloadTime = df.parse(Tool.getAttribute(tag, "startDownloadTime"));
         endDownloadTime = df.parse(Tool.getAttribute(tag, "endDownloadTime"));
         startProcessTime = df.parse(Tool.getAttribute(tag, "startProcessTime"));
         endProcessTime = df.parse(Tool.getAttribute(tag, "endProcessTime"));
      }
      // parse from client
      else {
         startTime = new Date(Long.parseLong(
            Tool.getAttribute(tag, "startTime")));
         endTime = new Date(Long.parseLong(Tool.getAttribute(tag, "endTime")));
         startDownloadTime = new Date(Long.parseLong(
            Tool.getAttribute(tag, "startDownloadTime")));
         endDownloadTime = new Date(Long.parseLong(
            Tool.getAttribute(tag, "endDownloadTime")));
      }

      timeDiff = endTime.getTime() - startTime.getTime();
      downloadTimeDiff = endDownloadTime.getTime() - startDownloadTime.getTime();
      uploadSize = Integer.parseInt(Tool.getAttribute(tag, "uploadSize"));
      downloadSize = Integer.parseInt(Tool.getAttribute(tag, "downloadSize"));

      NodeList nodes = Tool.getChildNodesByTagName(tag, "TimeCommand");

      for(int i = 0; i < nodes.getLength(); i++) {
         Element node = (Element) nodes.item(i);
         String cmdName = Tool.getAttribute(node, "cmdName");
         Date startTime;
         Date endTime;
         String sstr = Tool.getAttribute(node, "startTime");
         String estr = Tool.getAttribute(node, "endTime");

         // parse from file
         if(sstr.indexOf(":") != -1) {
            startTime = df.parse(sstr);
            endTime = df.parse(estr);
         }
         else {
            startTime = new Date(Long.parseLong(sstr));
            endTime = new Date(Long.parseLong(estr));
         }

         int size = Integer.parseInt(Tool.getAttribute(node, "size"));
         commands.add(new TimeCommand(cmdName, startTime, endTime, size));
      }
   }

   /**
    * Set parent id.
    */
   public void setParentID(String pid) {
      this.pid = pid;
   }

   /**
    * Time command.
    */
   public class TimeCommand {
      /**
       * Constructor.
       */
      public TimeCommand(String cmdName, Date startTime, Date endTime, int size)
      {
         this.cmdName = cmdName;
         this.startTime = startTime;
         this.endTime = endTime;
         this.size = size;
      }

      /**
       * Get command name.
       */
      public String getCommandName() {
         return cmdName;
      }

      /**
       * Get time diff.
       */
      public long getTimeDiff() {
         return endTime.getTime() - startTime.getTime();
      }

      /**
       * Get start time.
       */
      public Date getStartTime() {
         return startTime;
      }

      /**
       * Get end time.
       */
      public Date getEndTime() {
         return endTime;
      }

      /**
       * Get size.
       */
      public int getSize() {
         return size;
      }

      private String cmdName;
      private Date startTime;
      private Date endTime;
      private int size;
   }

   /**
    * Get event class name.
    */
   public String getEventClassName() {
      return eventClassName;
   }

   /**
    * Get time diff.
    */
   public long getTimeDiff() {
      return timeDiff;
   }

   /**
    * Set process start time.
    */
   public void setStartProcessTime(long time) {
      this.startProcessTime = new Date(time);
   }

   /**
    * Set process end time.
    */
   public void setEndProcessTime(long time) {
      this.endProcessTime = new Date(time);
   }

   /**
    * Get process time.
    */
   public long getProcessTime() {
      return endProcessTime.getTime() - startProcessTime.getTime();
   }

   /**
    * Get start time.
    */
   public Date getStartTime() {
      return startTime;
   }

   /**
    * Get end time.
    */
   public Date getEndTime() {
      return endTime;
   }

   /**
    * Get start download  time.
    */
   public Date getStartDownloadTime() {
      return startDownloadTime;
   }

   /**
    * Get end download time.
    */
   public Date getEndDownloadTime() {
      return endDownloadTime;
   }

   /**
    * Get start process time.
    */
   public Date getStartProcessTime() {
      return startProcessTime;
   }

   /**
    * Get end process time.
    */
   public Date getEndProcessTime() {
      return endProcessTime;
   }

   /**
    * Get upload size.
    */
   public int getUploadSize() {
      return uploadSize;
   }

   /**
    * Get download size.
    */
   public int getDownloadSize() {
      return downloadSize;
   }

   /**
    * Get time commands.
    */
   public List<TimeCommand> getTimeCommands() {
      return commands;
   }

   private static final SimpleDateFormat df =
      new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS");
   private String eventClassName;
   private String pid;
   private Date startTime;
   private Date endTime;
   private Date startDownloadTime;
   private Date endDownloadTime;
   private Date startProcessTime;
   private Date endProcessTime;
   private int uploadSize = -1;
   private int downloadSize = -1;
   private long timeDiff; // millisecond
   private long downloadTimeDiff;
   private ArrayList<TimeCommand> commands = new ArrayList<>();
}