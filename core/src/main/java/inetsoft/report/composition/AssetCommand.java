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
package inetsoft.report.composition;

import inetsoft.analytic.composition.command.RefreshVSObjectCommand;
import inetsoft.report.composition.command.InitGridCommand;
import inetsoft.report.composition.command.MessageCommand;
import inetsoft.util.Tool;
import inetsoft.util.XMLTool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.*;
import java.util.ArrayList;
import java.util.HashMap;

/**
 * Asset command, represents an command as the event response. It may contain
 * multiple sub-asset commands.
 *
 * @version 10.0
 * @author InetSoft Technology Corp
 */
public class AssetCommand extends AssetContainer {
   /**
    * Constructor.
    */
   public AssetCommand() {
      this(null);
   }

   /**
    * Constructor.
    */
   public AssetCommand(AssetEvent event) {
      super();

      cmds = new ArrayList();

      if(event != null) {
         this.eid = event.getEventID();
         this.listener = event.getActionListener();
         this.rid = (event instanceof GridEvent) ?
            ((GridEvent) event).getID() : null;
      }
      else {
         this.eid = -1;
      }

      clear();
   }

   /**
    * Get the id.
    * @return the id.
    */
   public String getID() {
      return rid;
   }

   /**
    * Set the id.
    * @param id the specfied id.
    */
   public void setID(String id) {
      this.rid = id;
   }

   /**
    * Check if an instrucion or just a piece of information.
    * @return <tt>true</tt> if an instruction, <tt>false</tt> otherwise.
    */
   public boolean isInstruction() {
      return true;
   }

   /**
    * Check if is completed.
    * @return <tt>true</tt> if the command container is completed,
    * <tt>false</tt> otherwise.
    */
   public boolean isCompleted() {
      return completed;
   }

   /**
    * Complete this command container.
    */
   public synchronized void complete() {
      this.completed = true;
      notifyAll();
   }

   /**
    * Get the worksheet engine.
    * @return the worksheet engine.
    */
   public WorksheetService getWorksheetEngine() {
      return wsengine;
   }

   /**
    * Set the worksheet engine.
    * @param wsengine the specified worksheet engine.
    */
   public void setWorksheetEngine(WorksheetService wsengine) {
      this.wsengine = wsengine;
   }

   /**
    * Get the event id.
    * @return the event id associated with this command container.
    */
   public int getEventID() {
      return eid;
   }

   /**
    * Get the current available asset command.
    * @return the current available asset command, which are stored in a
    * command container.
    */
   public synchronized AssetCommand getCurrentCommand() {
      AssetCommand container = new AssetCommand();
      container.copyProperties(this);

      while(!completed && cpos >= cmds.size()) {
         try {
            wait(1000);
         }
         catch(Exception ex) {
            // ignore it
         }
      }

      for(int i = cpos; i < cmds.size(); i++) {
         AssetCommand cmd = (AssetCommand) cmds.get(i);

         if(cmd.getID() == null) {
            cmd.setID(rid);
         }

         container.cmds.add(cmd);
      }

      cpos = cmds.size();
      container.completed = completed;
      return container;
   }

   /**
    * Get the command.
    * @param index the specified command index.
    */
   public AssetCommand getCommand(int index) {
      return (AssetCommand) cmds.get(index);
   }

   /**
    * Get the number.
    * @return the number of all the commands.
    */
   public int getCommandCount() {
      return cmds.size();
   }

   /**
    * Add a sub-command to this command.
    * @param cmd the specified sub-command.
    */
   public synchronized AssetCommand addCommand(AssetCommand cmd) {
      return addCommand(cmd, true);
   }

   /**
    * Add a sub-command to this command.
    * @param cmd the specified sub-command.
    * @param event <tt>true</tt> to fire event, <tt>false</tt> otherwise.
    */
   public synchronized AssetCommand addCommand(AssetCommand cmd, boolean event)
   {
      if(cmd.getID() == null && rid != null) {
         cmd.setID(rid);
      }

      int index = cmds.lastIndexOf(cmd);

      // replace if exist
      if(index >= 0 && index >= cpos && index >= initpos) {
         // in case of replacing, maintain order of cmds since the refresh order
         // may be significant
         if(cmd instanceof RefreshVSObjectCommand) {
            cmds.remove(index);
            cmds.add(cmd);
         }
         else {
            cmds.set(index, cmd);
         }
      }
      else {
         cmds.add(cmd);
      }

      if(cmd instanceof MessageCommand &&
         !((MessageCommand) cmd).isSuccessful())
      {
         setSuccessful(false);
      }
      else if(cmd instanceof InitGridCommand) {
         initpos = cmds.size();
      }

      if(event) {
         notifyAll();
      }

      return this;
   }

   /**
    * Merge the sub-command to this command.
    * @param cmd the specified sub-command.
    */
   public synchronized AssetCommand mergeCommand(AssetCommand cmd) {
      if(getID() == null) {
         setID(cmd.getID());
      }

      for(int i = 0; i < cmd.getCommandCount(); i++) {
         addCommand(cmd.getCommand(i));
      }

      return this;
   }

   /**
    * Remove the sub-command at an index.
    * @param index the specified index.
    */
   public void removeCommand(int index) {
      cmds.remove(index);

      if(cpos > index) {
         cpos--;
      }

      if(initpos > index) {
         initpos--;
      }
   }

   /**
    * Set whether the event process is successful.
    * @param successful <tt>true</tt> if successful, <tt>false</tt> otherwise.
    */
   public void setSuccessful(boolean successful) {
      put("successful", successful + "");
   }

   /**
    * Check if the event process is successful.
    * @return <tt>true</tt> if successful, <tt>false</tt> otherwise.
    */
   public boolean isSuccessful() {
      return !"false".equals(get("successful"));
   }

   /**
    * Check if is enqueued.
    * @return <tt>true</tt> if enqued, <tt>false</tt> otherwise.
    */
   public boolean isEnqueued() {
      return enq;
   }

   /**
    * Check whether this command is enqueued.
    * @param enq <tt>true</tt> if enqued, <tt>false</tt> otherwise.
    */
   public void setEnqueued(boolean enq) {
      this.enq = enq;
   }

   /**
    * Clear the asset command.
    */
   @Override
   public void clear() {
      super.clear();
      cmds.clear();
      cpos = 0;
      initpos = 0;
      completed = false;
   }

   /**
    * Check if the command is empty.
    * @return <tt>true</tt> if empty, <tt>false</tt> otherwise.
    */
   public boolean isEmpty() {
      for(int i = 0; i < getCommandCount(); i++) {
         AssetCommand cmd = getCommand(i);

         if(cmd.isInstruction()) {
            return false;
         }
      }

      return true;
   }

   /**
    * Clone the object.
    * @return the cloned object.
    */
   @Override
   public Object clone() {
      try {
         AssetCommand cmd = (AssetCommand) super.clone();
         cmd.cmds = (ArrayList) cmds.clone();
         return cmd;
      }
      catch(Exception ex) {
         return null;
      }
   }

   /**
    * Write the contents of this object.
    * @param writer the output stream to which to write the XML data.
    */
   @Override
   protected void writeContents(PrintWriter writer) {
      super.writeContents(writer);
      writer.println("<commands>");

      for(int i = 0; i < cmds.size(); i++) {
         getCommand(i).writeXML(writer);
      }

      writer.println("</commands>");
   }

   /**
    * Read in the contents of this object from an xml tag.
    * @param tag the specified xml element.
    */
   @Override
   protected void parseContents(Element tag) throws Exception {
      super.parseContents(tag);
      Element csnode = Tool.getChildNodeByTagName(tag, "commands");
      NodeList cnodes = Tool.getChildNodesByTagName(csnode, "assetContainer");

      for(int i = 0; i < cnodes.getLength(); i++) {
         Element cnode = (Element) cnodes.item(i);
         AssetCommand cmd = (AssetCommand) createAssetContainer(cnode);
         addCommand(cmd);
      }
   }

   /**
    * Write the data of this object.
    * !!! Subclass implementing this method should not call super.writeContents2()
    * @param dos the output stream to which to write data.
    */
   @Override
   protected void writeContents2(DataOutputStream dos) {
      super.writeContents2(dos);

      try {
         dos.writeInt(cmds.size());

         for(int i = 0; i < cmds.size(); i++) {
            AssetCommand cmd = getCommand(i);
            dos.writeUTF(cmd.getClassName());
            cmd.writeData(dos);
         }

         // not a container
         if(cmds.size() == 0) {
            HashMap omap = map;
            HashMap omap2 = map2;
            // don't write the entries XML as data again
            map = new HashMap();
            map2 = new HashMap();

            // default to write XML to output so classes not implementing the
            // writeContents2 method will still work with DataOutputStream
            XMLTool.writeXMLSerializableAsData(dos, this);

            // restore maps
            map = omap;
            map2 = omap2;
         }
      }
      catch(IOException ex) {
         LOG.error(
                     "Failed to write XML to DataOutputStream: " + 
                     getClass(), ex);
      }
   }

   /**
    * Get the string representaion.
    * @return the string representation.
    */
   public String toString() {
      String cls = getClass().getName();
      int index = cls.lastIndexOf(".");
      cls = index >= 0 ? cls.substring(index + 1) : cls;
      StringBuilder sb = new StringBuilder(cls);
      sb.append(map2);

      if(getCommandCount() == 0) {
         return sb.toString();
      }

      sb.append('[');

      for(int i = 0; i < getCommandCount(); i++) {
         AssetCommand cmd = getCommand(i);

         if(i > 0) {
            sb.append('\n');
         }

         sb.append(cmd);
      }

      sb.append(']');

      return sb.toString();
   }

   /**
    * Fire event.
    */
   public void fireEvent() {
      ActionEvent event = new ActionEvent(this, eid, rid);

      if(listener != null) {
         listener.actionPerformed(event);
      }
   }

   /**
    * Get the action listener.
    * @return the action listener.
    */
   public ActionListener getActionListener() {
      return listener;
   }

   /**
    * Get the current position.
    * @return the current position.
    */
   public int getCurrentPosition() {
      return cpos;
   }

   private ArrayList cmds;
   private transient int initpos;
   private transient int cpos;
   private transient boolean completed;
   private transient String rid;
   private transient int eid;
   private transient ActionListener listener;
   private transient boolean enq;
   private transient WorksheetService wsengine;

   private static final Logger LOG =
      LoggerFactory.getLogger(AssetCommand.class);
}
