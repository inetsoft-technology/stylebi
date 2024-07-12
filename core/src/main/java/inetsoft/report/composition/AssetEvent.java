/*
 * inetsoft-core - StyleBI is a business intelligence web application.
 * Copyright © 2024 InetSoft Technology (info@inetsoft.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package inetsoft.report.composition;

import inetsoft.uql.asset.*;

import java.awt.event.ActionListener;
import java.security.Principal;
import java.util.ArrayList;

/**
 * Asset event, the event sent from exploratory analyzer to process, and an
 * asset command will be sent back as the response.
 *
 * @version 8.0
 * @author InetSoft Technology Corp
 */
public abstract class AssetEvent extends AssetContainer {
   /**
    * Grid row resize event.
    */
   public static final String GRID_ROW_RESIZE = "GRID_ROW_RESIZE";
   /**
    * Grid column resize event.
    */
   public static final String GRID_COL_RESIZE = "GRID_COLUMN_RESIZE";
   /**
    * Move in vstab.
    */
   public static final String MOVE_IN_TAB = "MOVE_IN_TAB";
   /**
    * Move out vstab.
    */
   public static final String MOVE_OUT_TAB = "MOVE_OUT_TAB";

   /**
    * Constructor.
    */
   public AssetEvent() {
      super();
      eid = -1;
   }

   /**
    * Get the name of the asset event. The name is used in undo/redo and other
    * fields, so please implement the method to provide  a localized and proper
    * name.
    * @return the name of the asset event.
    */
   public abstract String getName();

   /**
    * Set the user.
    * @param user the specified user.
    */
   public void setUser(Principal user) {
      this.user = user;
   }

   /**
    * Get the user.
    * @return the user.
    */
   public Principal getUser() {
      return user;
   }

   /**
    * Return true if the event will access storage heavily.
    */
   public boolean isStorageEvent() {
      return false;
   }

   /**
    * Add the confirm exception.
    */
   public void addConfirmException(ConfirmException ex) {
      if(MAIN.get() != null && MAIN.get() != this) {
         MAIN.get().addConfirmException(ex);
         return;
      }

      if(!exs.contains(ex) && ex != null) {
         exs.add(ex);
      }
   }

   /**
    * Get the count of the exceptions.
    * @return an number.
    */
   public int getConfirmExceptionCount() {
      return exs.size();
   }

   /**
    * Get the confirm exception by index.
    */
   public ConfirmException getConfirmException(int index) {
      return (ConfirmException) exs.get(index);
   }

   /**
    * Add all conform exceptions from the specified event to this event.
    */
   public void addConfirmExceptions(AssetEvent event) {
      if(MAIN.get() != null && MAIN.get() != this) {
         MAIN.get().addConfirmExceptions(event);
         return;
      }

      exs.addAll(event.exs);
   }

   /**
    * Set the confirmed flag.
    * @param confirmed <tt>true</tt> if confirmed, <tt>false</tt> otherwise.
    */
   public void setConfirmed(boolean confirmed) {
      put("confirmed", confirmed + "");
   }

   /**
    * Check if is confirmed.
    * @return <tt>true</tt> if confirmed, <tt>false</tt> otherwise.
    */
   public boolean isConfirmed() {
      return "true".equals(get("confirmed"));
   }

   /**
    * Check if is a default event trigged by system.
    * @return <tt>true</tt> if trigged by system, <tt>false</tt> otherwise.
    */
   public boolean isDefault() {
      return "true".equals(get("default_event"));
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
    * Set the uri.
    * @param uri the specified service request uri.
    */
   public void setLinkURI(String uri) {
      this.luri = uri;
   }

   /**
    * Get the specified service request uri.
    * @return the uri.
    */
   public String getLinkURI() {
      return this.luri;
   }

   /**
    * Get the asset repository.
    * @return the asset repository.
    */
   public AssetRepository getAssetRepository() {
      return wsengine == null ? null : wsengine.getAssetRepository();
   }

   /**
    * Check if is server.
    * @return <tt>true</tt> if is server, <tt>false</tt> otherwise.
    */
   public boolean isServer() {
      return wsengine == null ? false : wsengine.isServer();
   }

   /**
    * Process the event.
    */
   public void process(AssetCommand command) throws Exception {
      // do nothing
   }

   /**
    * Check if this event is from the web interface.
    */
   public boolean isWebEvent() {
      return webEvent;
   }

   /**
    * Set whether this event is from the web interface.
    */
   public void setWebEvent(boolean web) {
      this.webEvent = web;
   }

   /**
    * Check if equals another object.
    * @param obj the specified object.
    * @return <tt>true</tt> if equals, <tt>false</tt> otherwise.
    */
   public boolean equals(Object obj) {
      if(!(obj instanceof AssetEvent)) {
         return false;
      }

      AssetEvent event = (AssetEvent) obj;

      // same class?
      if(!getClass().equals(event.getClass())) {
         return false;
      }

      return true;
   }

   /**
    * Get the hash code.
    * @return the hash code of the asset event.
    */
   public int hashCode() {
      return getClass().hashCode();
   }

   /**
    * Get the string representaion.
    * @return the string representation.
    */
   public String toString() {
      String cls = getClass().getName();
      int index = cls.lastIndexOf(".");
      cls = index >= 0 ? cls.substring(index + 1) : cls;
      return cls;
   }

   /**
    * Get the event id.
    * @return the event id.
    */
   public int getEventID() {
      return eid;
   }

   /**
    * Set the event id.
    * @param eid the specified event id.
    */
   public void setEventID(int eid) {
      this.eid = eid;
   }

   /**
    * Get the action listener.
    * @return the action listener.
    */
   public ActionListener getActionListener() {
      return listener;
   }

   /**
    * Set the action listener.
    * @param listener the specified action listener.
    */
   public void setActionListener(ActionListener listener) {
      this.listener = listener;
   }

   /**
    * Asset event helper.
    */
   public static interface AssetEventHelper {
      /**
       * Refresh assembly.
       */
      public AssetEvent refreshAssembly();

      /**
       * Refresh sheet.
       */
      public AssetEvent refreshSheet();
   }

   public static final transient AssetEventHelper helper = new AssetEventHelper() {
      @Override
      public AssetEvent refreshAssembly() {
         return null;
      }

      @Override
      public AssetEvent refreshSheet() {
         return null;
      }
   };

   public static final ThreadLocal<AssetEvent> MAIN = new ThreadLocal<>();
   private transient String luri;
   private transient WorksheetService wsengine;
   private transient Principal user;
   private transient boolean webEvent;
   private transient int eid;
   private transient ActionListener listener;
   private transient ArrayList exs = new ArrayList();
}
