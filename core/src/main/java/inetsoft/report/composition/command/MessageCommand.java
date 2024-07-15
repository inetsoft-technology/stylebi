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
package inetsoft.report.composition.command;

import inetsoft.report.composition.*;
import inetsoft.uql.asset.ConfirmException;
import inetsoft.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.io.*;
import java.sql.SQLException;
import java.util.*;

/**
 * Message command.
 *
 * @version 8.0, 07/27/2005
 * @author InetSoft Technology Corp
 */
public class MessageCommand extends AssetCommand {
   /**
    * OK level message.
    */
   public static final int OK = ConfirmException.OK;
   /**
    * Trace level message.
    */
   public static final int TRACE = ConfirmException.TRACE;
   /**
    * Debug level message.
    */
   public static final int DEBUG = ConfirmException.DEBUG;
   /**
    * Information level message.
    */
   public static final int INFO = ConfirmException.INFO;
   /**
    * Warning level message.
    */
   public static final int WARNING = ConfirmException.WARNING;
   /**
    * Error level message.
    */
   public static final int ERROR = ConfirmException.ERROR;
   /**
    * Confirm level message.
    */
   public static final int CONFIRM = ConfirmException.CONFIRM;
   /**
    * Progress level message.
    */
   public static final int PROGRESS = ConfirmException.PROGRESS;
   /**
    * Progress level message.
    */
   public static final int OVERRIDE = ConfirmException.OVERRIDE;

   /**
    * Unknown option.
    */
   public static final int UNKNOWN_OPTION = 0;
   /**
    * Yes option.
    */
   public static final int YES_OPTION = 1;
   /**
    * No option.
    */
   public static final int NO_OPTION = 2;

   /**
    * Constructor.
    */
   public MessageCommand() {
      super();
   }

   /**
    * Constructor.
    */
   public MessageCommand(Throwable ex) {
      this(ex, ERROR);
   }

   /**
    * Constructor.
    */
   public MessageCommand(Throwable ex, int level) {
      String msg = ex.getMessage();
      Catalog catalog = Catalog.getCatalog();
      String prefix = ex instanceof SQLException ? "SQL " : "";

      if((msg == null || msg.trim().length() == 0) &&
         ex.toString().startsWith("java.lang"))
      {
         switch(level) {
         case TRACE:
         case DEBUG:
            LOG.debug("Operation could not be completed", ex);
            break;
         case WARNING:
            LOG.warn("Operation could not be completed", ex);
            break;
         case ERROR:
            LOG.error("Operation could not be completed", ex);
            break;
         default:
            LOG.info("Operation could not be completed", ex);
         }

         msg = catalog.getString("failed.accomplish.operation");
      }
      else {
         msg = prefix + catalog.getString(msg);
      }

      put("message", msg);
      put("level", "" + level);
   }

   /**
    * Constructor.
    */
   public MessageCommand(String message) {
      this(message, ERROR);
   }

   /**
    * Constructor.
    */
   public MessageCommand(String message, int level) {
      if(message != null) {
         put("message", message);
      }

      put("level", "" + level);
   }

   /**
    * Check if an instrucion or just a piece of information.
    * @return <tt>true</tt> if an instruction, <tt>false</tt> otherwise.
    */
   @Override
   public boolean isInstruction() {
      return false;
   }

   /**
    * Get the count of the asset events.
    * @return an number.
    */
   public int getEventCount() {
      return events.size();
   }

   /**
    * Add the asset event.
    */
   public void addEvent(AssetEvent event) {
      if(event != null && !events.contains(event)) {
         events.add(event);
      }
   }

   /**
    * Gets all events that have been added to this Command.
    * @return an Enumeration of AssetEvent objects.
    */
   public Enumeration getEvents() {
      return Collections.enumeration(events);
   }

   /**
    * Check if the event process created this message command is successful.
    */
   @Override
   public boolean isSuccessful() {
      int level = Integer.parseInt((String) get("level"));
      return level != WARNING && level != ERROR && level != CONFIRM;
   }

   /**
    * Set observer.
    */
   public void setObserver(WSObserver observer) {
      this.observer = observer;
   }

   /**
    * Get observer.
    */
   public WSObserver getObserver() {
      return observer;
   }

   /**
    * Check if equals another object.
    * @param obj the specified object.
    * @return <tt>true</tt> if equals, </tt>false</tt> otherwise.
    */
   public boolean equals(Object obj) {
      if(!(obj instanceof MessageCommand)) {
         return false;
      }

      MessageCommand cmd = (MessageCommand) obj;
      ArrayList events2 = cmd.events;

      if(getEventCount() != events2.size()) {
         return false;
      }

      for(int i = 0; i < getEventCount(); i++) {
         if(!Tool.equals(events.get(i), events2.get(i))) {
            return false;
         }
      }

      if(!Tool.equals(get("message"), cmd.get("message"))) {
         return false;
      }

      if(!Tool.equals(get("level"), cmd.get("level"))) {
         return false;
      }

      return true;
   }

   /**
    * Write the data value to DataOutputStream.
    * @param dos the destination OutputStream.
    */
   @Override
   public void writeData(DataOutputStream dos) {
      super.writeData(dos);

      try {
         dos.writeInt(getEventCount());
         Enumeration iter = getEvents();

         while(iter.hasMoreElements()) {
            AssetEvent event = (AssetEvent) iter.nextElement();
            Class cls = event.getClass();
            dos.writeUTF(cls.getName());

            if(event instanceof XMLSerializable) {
               StringWriter writer = new StringWriter();
               ((XMLSerializable) event).writeXML(new PrintWriter(writer));
               dos.writeUTF(writer.toString());
            }
            else if(event instanceof DataSerializable) {
               ((DataSerializable) event).writeData(dos);
            }
         }
      }
      catch(IOException e) {
      }
   }

   /**
    * Write the contents of this object.
    * @param writer the output stream to which to write the XML data.
    */
   @Override
   protected void writeContents(PrintWriter writer) {
      super.writeContents(writer);

      if(getEventCount() > 0) {
         writer.print("<events>");

         Object[] events0 = events.toArray();

         for(int i = 0; i < events0.length; i++) {
            ((AssetEvent) events0[i]).writeXML(writer);
         }

         writer.print("</events>");
      }
   }

   /**
    * Read in the contents of this object from an xml tag.
    * @param tag the specified xml element.
    */
   @Override
   protected void parseContents(Element tag) throws Exception {
      super.parseContents(tag);
      Element node = Tool.getChildNodeByTagName(tag, "events");

      if(node != null) {
         events = new ArrayList();
         NodeList nodes =  node.getChildNodes();

         for(int i = 0; i < nodes.getLength(); i++) {
            Element elem = (Element) nodes.item(i);
            String cls = Tool.getAttribute(elem, "class");
            AssetEvent event = (AssetEvent) Class.forName(cls).newInstance();
            event.parseXML(elem);
            events.add(event);
         }
      }
   }

   @Override
   public void setID(String id) {
      super.setID(id);

      for(int i = 0; i < events.size(); i++) {
         if(events.get(i) instanceof GridEvent) {
            ((GridEvent) events.get(i)).setID(id);
         }
      }
   }

   private WSObserver observer;
   private ArrayList events = new ArrayList();
   private static final Logger LOG =
      LoggerFactory.getLogger(MessageCommand.class);
}
