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

import inetsoft.sree.SreeEnv;
import inetsoft.uql.XPrincipal;
import inetsoft.uql.asset.*;
import inetsoft.util.*;
import inetsoft.util.swap.*;
import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import java.io.*;
import java.security.Principal;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * RuntimeSheet represents a abstract runtime sheet in editing time.
 *
 * @version 8.0
 * @author InetSoft Technology Corp
 */
public abstract class RuntimeSheet {
   /**
    * Get max idle time.
    * @return max idle time.
    */
   public static long getMaxIdleTime() {
      long maxIdle = Integer.parseInt(SreeEnv.getProperty("asset.max.idle"));
      String sessionTimeoutProperty = SreeEnv.getProperty("http.session.timeout");

      if(sessionTimeoutProperty != null && !sessionTimeoutProperty.isEmpty()) {
         try {
            long sessionTimeout = TimeUnit.SECONDS.toMillis(Long.parseLong(sessionTimeoutProperty));
            maxIdle = Math.min(sessionTimeout, maxIdle);
         }
         catch(NumberFormatException e) {
            if(LOG.isDebugEnabled()) {
               LOG.warn("Invalid value of http.session.timeout property: {}", sessionTimeoutProperty, e);
            }
            else {
               LOG.warn("Invalid value of http.session.timeout property: {}", sessionTimeoutProperty);
            }
         }
      }

      return maxIdle;
   }

   /**
    * Constructor.
    */
   public RuntimeSheet() {
      super();
      this.contextPrincipal = (XPrincipal) ThreadContext.getContextPrincipal();

      try {
         this.max = Integer.parseInt(SreeEnv.getProperty("asset.max.undoes"));
      }
      catch(Exception ex) {
         this.max = 10;
      }

      point = -1;
      savePoint = 0;
      editable = true;
      isLockProcessed = false;
      points = new XSwappableSheetList(this.contextPrincipal);

      access(true);
   }

   /**
    * Access the runtime sheet.
    *
    * @param updateAccessTime <code>true</code> to update the access time
    */
   public void access(boolean updateAccessTime) {
      heartbeat = System.currentTimeMillis();

      if(updateAccessTime) {
         accessed = heartbeat;
      }
   }

   /**
    * Get last accessed time.
    */
   public long getLastAccessed() {
      return accessed;
   }

   /**
    * Check if the runtime sheet is timeout.
    * @return <tt>true</tt> if time out, <tt>false</tt> otherwise.
    */
   public boolean isTimeout() {
      long now = System.currentTimeMillis();

      // timeout if no heartbeat in 3 minutes
      if(heartbeat < now - 180000) {
         return true;
      }

      long idle = now - accessed;
      return idle > getMaxIdleTime0();
   }

   /**
    * @expire
    */
   protected long getMaxIdleTime0() {
      return getMaxIdleTime();
   }

   /**
    * Get the asset entry.
    * @return the asset entry of the runtime sheet.
    */
   public AssetEntry getEntry() {
      return entry;
   }

   /**
    * Set the asset entry.
    * @param entry the specified asset entry.
    */
   public void setEntry(AssetEntry entry) {
      this.entry = entry;
   }

   /**
    * Get the user.
    * @return the user of the runtime sheet.
    */
   public Principal getUser() {
      return user;
   }

   /**
    * Check if is editable.
    * @return <tt>true</tt>if editable, <tt>false</tt> otherwise.
    */
   public boolean isEditable() {
      return editable;
   }

   /**
    * Set the editable option.
    * @param editable <tt>true</tt>if editable, <tt>false</tt> otherwise.
    */
   public void setEditable(boolean editable) {
      this.editable = editable;
   }

   /**
    * Check if is lock processed.
    * @return <tt>true</tt>if is lock processed, <tt>false</tt> otherwise.
    */
   public boolean isLockProcessed() {
      return isLockProcessed;
   }

   /**
    * Set the lock processed.
    */
   public void setLockProcessed() {
      isLockProcessed = true;
   }

   /**
    * Get the lock owner of the asset entry.
    * @return the lock owner of runtime sheet, <tt>null</tt> if not
    * locked by others.
    */
   public String getLockOwner() {
      return lowner;
   }

   /**
    * Set the lock owner of the asset entry.
    * @param lowner the specified lock owner.
    */
   public void setLockOwner(String lowner) {
      this.lowner = lowner;
   }

   /**
    * Check if matches a user.
    * @param user the specified user.
    * @return <tt>true</tt> if yes, <tt>false</tt> otherwise.
    */
   public boolean matches(Principal user) {
      return Tool.equals(this.user, user);
   }

   /**
    * Get the container viewsheet id.
    * @returnt he container viewsheet id.
    */
   public String getEmbeddedID() {
      return eid;
   }

   /**
    * Set the embedded viewsheet id.
    * @param eid the specified original viewsheet id.
    */
   public void setEmbeddedID(String eid) {
      this.eid = eid;
   }

   /**
    * Get the sheet id.
    * @return the sheet id.
    */
   public String getID() {
      return id;
   }

   /**
    * Set the sheet id.
    * @param id the specified sheet id.
    */
   public void setID(String id) {
      this.id = id;
   }

   public String getSocketSessionId() {
      return socketSessionId;
   }

   public void setSocketSessionId(String socketSessionId) {
      this.socketSessionId = socketSessionId;
   }

   public String getSocketUserName() {
      return socketUserName;
   }

   public void setSocketUserName(String socketUserName) {
      this.socketUserName = socketUserName;
   }

   /**
    * Replace a check point for undo.
    * @param sheet the specified sheet.
    * @return current point.
    */
   public synchronized int replaceCheckpoint(AbstractSheet sheet) {
      if(disposed) {
         return -1;
      }

      if(points.size() == 0) {
         return addCheckpoint(sheet);
      }

      int index = points.size() - 1;
      sheet.reset();
      points.set(index, sheet);
      return index;
   }

   /**
    * Add a check point for undo.
    * @param sheet the specified sheet.
    * @return current point.
    */
   public synchronized int addCheckpoint(AbstractSheet sheet) {
      for(int i = points.size() - 1; point >= 0 && i > point; i--) {
         points.remove(i);

         // clear save point is required
         if(savePoint == i) {
            savePoint = -1;
         }
      }

      if(points.size() == max) {
         points.remove(0);
         savePoint -= 1;
      }

      sheet.reset();
      points.add(sheet);
      point = points.size() - 1;

      return point;
   }

   /**
    * Get the check point count.
    * @return the point count.
    */
   public int size() {
      return points == null ? 0 : points.size();
   }

   /**
    * Get current check point.
    * @return current check point index.
    */
   public int getCurrent() {
      return point;
   }

   /**
    * Get save point.
    * @return save point index.
    */
   public int getSavePoint() {
      return savePoint;
   }

   /**
    * set save point.
    * @param savePoint point index.
    */
   public void setSavePoint(int savePoint) {
      this.savePoint = savePoint;
   }

   /**
    * Dispose the runtime sheet.
    */
   public synchronized void dispose() {
      if(points != null) {
         points.dispose();
         points = null;
      }

      disposed = true;
   }

   /**
    * Check if is disposed.
    * @return <tt>true</tt> if disposed, <tt>false</tt> otherwise.
    */
   public boolean isDisposed() {
      return disposed;
   }

   /**
    * Get a property of the sheet.
    * @param key the name of the property.
    * @return the value of the property.
    */
   public Object getProperty(String key) {
      return prop.get(key);
   }

   /**
    * Set a property of the sheet.
    * @param key the name of the property.
    * @param value the value of the property, <tt>null</tt> to remove the
    * property.
    */
   public void setProperty(String key, Object value) {
      if(value == null) {
         prop.remove(key);
      }
      else {
         prop.put(key, value);
      }
   }

   /**
    * Set the previous url of a report opened by hyperlink.
    */
   public void setPreviousURL(String previousURL) {
      this.previousURL = previousURL;
   }

   /**
    * Get the previous url.
    */
   public String getPreviousURL() {
      return this.previousURL;
   }

   /**
    * Undo one point.
    * @param clist the specified changed assembly list.
    * @return true if sheet was undone, false otherwise
    */
   public abstract boolean undo(ChangedAssemblyList clist);

   /**
    * Redo one point.
    * @param clist the specified changed assembly list.
    * @return true if sheet was redone, false otherwise
    */
   public abstract boolean redo(ChangedAssemblyList clist);

   /**
    * Rollback last changes.
    */
   public abstract void rollback();

   /**
    * Get the contained sheet.
    * @return the contained sheet.
    */
   public abstract AbstractSheet getSheet();

   /**
    * Get the mode of this runtime sheet.
    * @return the mode of this runtime sheet.
    */
   public abstract int getMode();

   /**
    * Check if is a runtime sheet.
    * @return <tt>true</tt> if a runtime sheet, <tt>false</tt>
    * otherwise.
    */
   public abstract boolean isRuntime();

   /**
    * Check if is a preview runtime sheet.
    * @return <tt>true</tt> if a preview runtime sheet, <tt>false</tt>
    * otherwise.
    */
   public abstract boolean isPreview();

   static final class XSwappableSheetList {
      public XSwappableSheetList(XPrincipal contextPrincipal) {
         this.values = new LinkedList<>();
         this.contextPrincipal = contextPrincipal;
      }

      public int size() {
         return values.size();
      }

      public void dispose() {
         disposed = true;
         clear();
      }

      public AbstractSheet get(int index) {
         AbstractSheet sheet = null;
         XSwappableSheet swappable = values.get(index);

         synchronized(swappable) {
            if(!disposed) {
               swappable.access();
            }

            sheet = swappable.get();
         }

         return sheet;
      }

      public void add(AbstractSheet element) {
         add(size(), element);
      }

      public void add(int index, AbstractSheet element) {
         if(index > values.size() || index < 0) {
            throw new IndexOutOfBoundsException(
               "Index: " + index + ", Size: " + values.size());
         }

         // shouldn't need to wait for memory since adding to values only
         // adds one item to the list and doesn't release increase the
         // amount of memory by much. allow it to proceed actually makes
         // the new sheet swappable immediately to free up memory faster.
         //XSwapper.getSwapper().waitForMemory();

         XSwappableSheet swappable = new XSwappableSheet(element, this.contextPrincipal);
         swappable.complete();
         values.add(index, swappable);
      }

      public void set(int index, AbstractSheet element) {
         remove(index);
         add(index, element);
      }

      public void remove(int index) {
         XSwappableSheet swappable = values.remove(index);
         swappable.dispose();
      }

      public void clear() {
         while(size() > 0) {
            remove(0);
         }
      }

      @Override
      protected void finalize() throws Throwable {
         dispose();
         super.finalize();
      }

      private final List<XSwappableSheet> values;
      private XPrincipal contextPrincipal;
      private boolean disposed;
   }

   private static final class XSwappableSheet extends XSwappable {
      public XSwappableSheet(AbstractSheet sheet, XPrincipal contextPrincipal) {
         this.sheet = sheet;
         this.contextPrincipal = contextPrincipal;
         this.valid = true;
         this.monitor = XSwapper.getMonitor();

         if(monitor != null) {
            isCountHM = monitor.isLevelQualified(XSwappableMonitor.HITS);
            isCountRW = monitor.isLevelQualified(XSwappableMonitor.READ);
         }
      }

      public void access() {
         if(isCountHM) {
            if(valid && !lastValid) {
               monitor.countHits(XSwappableMonitor.REPORT, 1);
               lastValid = true;
            }
            else if(!valid) {
               monitor.countMisses(XSwappableMonitor.REPORT, 1);
               lastValid = false;
            }
         }

         if(!valid) {
            DEBUG_LOG.debug("Validate swapped data: %s", this);
            validate(false);
         }
      }

      @Override
      public double getSwapPriority() {
         if(disposed || !completed || !valid || !isSwappable()) {
            return 0;
         }

         // sheet on the swappable sheet list is only used in undo/redo/rollback
         // it can be swapped immediately to conserve memory
         return 100;
      }

      @Override
      public void complete() {
         if(disposed || completed) {
            return;
         }

         completed = true;
         super.complete();
      }

      @Override
      public boolean isCompleted() {
         return completed;
      }

      @Override
      public boolean isSwappable() {
         return !disposed;
      }

      private void validate(boolean reset) {
         valid = true;
         File file = getFile(prefix + ".tdat");

         if(!file.exists()) {
            return;
         }

         InputStream input = null;

         try {
            input = Tool.createUncompressInputStream(new FileInputStream(file));
            Document document = Tool.parseXML(input);

            if(isCountRW) {
               monitor.countRead(file.length(), XSwappableMonitor.DATA);
            }

            Element element = document.getDocumentElement();
            Class<?> clazz = Class.forName(Tool.getAttribute(element, "class"));
            AbstractSheet sheet = (AbstractSheet) clazz.newInstance();
            sheet.parseXML(element);
            this.sheet = sheet;
         }
         catch(Exception exc) {
            LOG.error("Failed to load swapped viewsheet", exc);
         }
         finally {
            IOUtils.closeQuietly(input);
         }

         if(reset) {
            file.delete();
         }
      }

      @Override
      public boolean isValid() {
         return valid;
      }

      @Override
      public synchronized boolean swap() {
         XPrincipal oldContextPrincipal = (XPrincipal) ThreadContext.getContextPrincipal();
         ThreadContext.setContextPrincipal(this.contextPrincipal);

         try {

            if(getSwapPriority() == 0) {
               return false;
            }

            valid = false;
            _swap();
            return true;
         }
         finally {
            ThreadContext.setContextPrincipal(oldContextPrincipal);
         }
      }

      private void _swap() {
         File file = getFile(prefix + ".tdat");
         OutputStream output = null;

         try {
            if(!file.exists()) {
               output = Tool.createCompressOutputStream(new FileOutputStream(file));
            }

            if(disposed) {
               return;
            }

            if(output != null) {
               PrintWriter writer = new PrintWriter(new OutputStreamWriter(output, "UTF-8"));
               writer.println("<?xml version=\"1.0\" encoding=\"UTF-8\"?>");
               Worksheet.setIsTEMP(true);
               sheet.writeXML(writer);
               writer.close();
            }

            sheet = null;

            if(isCountRW && output != null) {
               monitor.countWrite(file.length(), XSwappableMonitor.DATA);
            }

            output = null;
         }
         catch(Exception exc) {
            LOG.error("Failed to write swapped viewsheet", exc);
         }
         finally {
            Worksheet.setIsTEMP(false);
            IOUtils.closeQuietly(output);
         }
      }

      @Override
      public synchronized void dispose() {
         if(disposed) {
            return;
         }

         disposed = true;
         sheet = null;
         File file = getFile(prefix + ".tdat");

         if(file.exists() && !file.delete()) {
            FileSystemService.getInstance().remove(file, 30000);
         }
      }

      public AbstractSheet get() {
         return sheet;
      }

      private AbstractSheet sheet = null;
      private XPrincipal contextPrincipal;
      private boolean valid = false;
      private boolean lastValid = false;
      private boolean completed = false;
      private boolean disposed = false;
      private transient XSwappableMonitor monitor;
      private transient boolean isCountHM = false;
      private transient boolean isCountRW = false;
   }

   protected AssetEntry entry;      // asset entry
   protected long accessed;         // last accessed time
   protected Principal user;        // user who opened it
   protected XPrincipal contextPrincipal;
   protected boolean editable;      // editable flag
   protected XSwappableSheetList points; // total points
   protected int point;             // current point
   protected int max;               // max undoes
   protected int savePoint;         // save point
   private String eid;              // container runtime sheet id
   private String id;               // runtime sheet id
   private String socketSessionId;  // web socket session id
   private String socketUserName;   // web socket user name
   private String lowner;           // user who locked it
   private boolean isLockProcessed; // unlocked flag
   private boolean disposed;        // disposed flag
   private long heartbeat = System.currentTimeMillis(); // heartbeat timestamp
   private Map<String, Object> prop = new HashMap<>();
   private String previousURL;

   private static final Logger LOG =
      LoggerFactory.getLogger(RuntimeSheet.class);
   private static final Logger DEBUG_LOG =
      LoggerFactory.getLogger("inetsoft.swap_data");
}
