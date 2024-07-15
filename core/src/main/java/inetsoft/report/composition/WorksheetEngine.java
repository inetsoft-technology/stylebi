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

import inetsoft.analytic.AnalyticAssistant;
import inetsoft.analytic.composition.SheetLibraryEngine;
import inetsoft.report.ReportSheet;
import inetsoft.report.composition.command.*;
import inetsoft.report.composition.event.*;
import inetsoft.report.composition.execution.BoundTableHelper;
import inetsoft.report.internal.RuntimeAssetEngine;
import inetsoft.sree.SreeEnv;
import inetsoft.sree.security.ResourceAction;
import inetsoft.sree.security.SRPrincipal;
import inetsoft.uql.*;
import inetsoft.uql.asset.*;
import inetsoft.uql.asset.internal.AssetUtil;
import inetsoft.uql.asset.sync.*;
import inetsoft.uql.erm.DataRef;
import inetsoft.uql.util.XUtil;
import inetsoft.util.*;
import inetsoft.util.log.LogLevel;
import inetsoft.util.log.LogManager;
import inetsoft.web.admin.monitoring.MonitorLevelService;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.*;
import java.rmi.RemoteException;
import java.security.Principal;
import java.sql.SQLException;
import java.util.List;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * The default worksheet service implementation, implements all the methods
 * of <tt>WorksheetService</tt> to provide common functions. If necessary,
 * we may chain sub <tt>WorksheetService</tt>s to serve advanced purposes.
 *
 * @version 8.0
 * @author InetSoft Technology Corp
 */
public class WorksheetEngine extends SheetLibraryEngine implements WorksheetService {
   /**
    * Constructor.
    */
   public WorksheetEngine() throws RemoteException {
      this((AssetRepository) AnalyticAssistant.getAnalyticAssistant()
         .getAnalyticRepository());
      setServer(true);
   }

   /**
    * Constructor.
    * throws RemoteException
    */
   public WorksheetEngine(AssetRepository engine) throws RemoteException {
      String maxstr = SreeEnv.getProperty("asset.worksheet.max");
      int max = 500;

      try {
         max = Integer.parseInt(maxstr);
      }
      catch(Exception ex) {
         LOG.warn("Invalid value for maximum number of open " +
               "worksheets (asset.worksheet.max): " + maxstr, ex);
      }

      amap = new RuntimeAssetMap<>(max);
      emap = new ConcurrentHashMap<>();
      executionMap = new ExecutionMap();
      cmap = new Hashtable<>();

      singlePreviewEnabled = "true".equals(SreeEnv.getProperty("single.preview.enabled"));
      setAssetRepository(engine);
   }

   /**
    * Get date range provider.
    * @param rep the specified asset repository.
    * @param report the specified report sheet.
    */
   public static DateRangeProvider getDateRangeProvider(AssetRepository rep,
                                                        ReportSheet report) {
      AssetRepository engine = new RuntimeAssetEngine(rep, report);
      DateRangeProvider provider = new DateRangeProvider();

      try {
         provider = (DateRangeProvider) dcache.get(engine);
      }
      catch(Exception ex) {
         LOG.error("Failed to get date range provider", ex);
         provider.setBuiltinDateConditions(
            DateCondition.getBuiltinDateConditions());
      }

      return provider;
   }

   /**
    * Get the asset repository.
    * @return the associated asset repository.
    */
   @Override
   public AssetRepository getAssetRepository() {
      return engine;
   }

   /**
    * Set the asset repository.
    * @param engine the specified asset repository.
    */
   @Override
   public void setAssetRepository(AssetRepository engine) {
      this.engine = engine;
   }

   /**
    * Get thread definitions of executing event according the id.
    */
   @Override
   public Vector getExecutingThreads(String id) {
      Vector threads = emap == null ? null : emap.get(id);
      return threads != null ? threads : new Vector();
   }

   /**
    * Check if the specified entry is duplicated.
    * @param engine the specified engine.
    * @param entry the specified entry.
    * @return <tt>true</tt> if duplicated, <tt>false</tt> otherwise.
    */
   @Override
   public boolean isDuplicatedEntry(AssetRepository engine, AssetEntry entry)
      throws Exception
   {
      return AssetUtil.isDuplicatedEntry(engine, entry);
   }

   /**
    * Localize the specified asset entry.
    */
   @Override
   public String localizeAssetEntry(String path, Principal principal,
                                    boolean isReplet, AssetEntry entry,
                                    boolean isUserScope)
   {
      if(isUserScope) {
         path = Tool.MY_DASHBOARD + "/" + path;
      }

      String newPath = AssetUtil.localize(path, principal, entry);
      return isUserScope ?
         newPath.substring(newPath.indexOf('/') + 1) : newPath;
   }

   /**
    * Get the cached propery by providing the specified key.
    */
   @Override
   public Object getCachedProperty(Principal user, String key) {
      // do not cache property
      return null;
   }

   /**
    * Set the cached property by providing the specified key-value pair.
    */
   @Override
   public void setCachedProperty(Principal user, String key, Object val) {
      // do not cache property
   }

   /**
    * Remove command from queue.
    * @param eid the specified event id.
    * @return the removed asset command if any, <tt>null</tt> not found.
    */
   @Override
   public AssetCommand dequeueCommand(int eid) {
      CommandEntry entry = cmap.remove(eid);

      if(entry != null) {
         entry.getCommand().setEnqueued(false);
         return entry.getCommand();
      }

      return null;
   }

   /**
    * Get the asset command in queue.
    * @param eid the specified event id.
    * @return the asset command if any, <tt>null</tt> not found.
    */
   @Override
   public AssetCommand getCommand(int eid) {
      CommandEntry entry = cmap.get(eid);
      return entry == null ? null : entry.getCommand();
   }

   /**
    * Process an asset event.
    * @param event the specified asset event to process.
    * @param user the specified user.
    * @return the associated asset command.
    */
   @Override
   public final AssetCommand process(AssetEvent event, Principal user) {
      AssetCommand command = new AssetCommand(event);
      String id = null;

      try {
         if(event instanceof GridEvent) {
            GridEvent gevent = (GridEvent) event;
            id = gevent.getID();

            if(id != null) {
               synchronized(amap) {
                  // in case that sheet is closed
                  if(!amap.containsKey(id)) {
                     if(!gevent.isSecondary() && !id.startsWith(PREVIEW_WORKSHEET)) {
                        ReloadSheetCommand cmd = new ReloadSheetCommand(id);
                        cmd.put("expired", id);
                        command.addCommand(cmd);
                     }

                     return command;
                  }

                  Vector<ThreadDef> threads = emap.get(id);

                  if(threads == null) {
                     threads = new Vector<>();
                  }

                  if(event.get("touchAsset") == null) {
                     ThreadDef def = new ThreadDef();
                     def.setStartTime(System.currentTimeMillis());
                     def.setThread(Thread.currentThread());
                     threads.addElement(def);
                     emap.put(id, threads);

                     if(isValidExecutingObject(id)) {
                        executionMap.addObject(id);
                     }
                  }
               }
            }
         }

         WorksheetService.ASSET_EXCEPTIONS.set(new ArrayList<>());
         AssetRepository.ASSET_ERRORS.set(new ArrayList());
         XUtil.QUERY_INFOS.set(new ArrayList<>());

         event.setWorksheetEngine(this);
         event.setUser(user);
         command.setWorksheetEngine(this);
         process0(event, user, command);
      }
      catch(CancelledException ex) {
         dequeueCommand(event.getEventID());
      }
      catch(Throwable ex) {
         // it's not too safe to clear command here, for some sub commands
         // might already be sent to client side. Let's see what's inconvenient.
         // don't clear the command if showing preparing data progress dialog
         if(!(ex instanceof ConfirmException)) {
            dequeueCommand(event.getEventID());
            command.clear();
         }

         if(ex instanceof ConfirmException) {
            ConfirmException ex2 = (ConfirmException) ex;
            String msg = ex.getMessage();

            if(ex2.getLevel() == ConfirmException.CONFIRM) {
               msg += "! " +
                  Catalog.getCatalog().getString(
                     "designer.composition.worksheetEngine.goOnAnyway");
            }

            MessageCommand mcmd = new MessageCommand(msg, ex2.getLevel());
            AssetEvent event2 = (AssetEvent) ex2.getEvent();
            event2 = event2 == null ? event : event2;

            if(!event2.isConfirmed()) {
               if(ex instanceof ConfirmDataException &&
                  event2.get("name") == null &&
                  ((ConfirmDataException) ex).getName() != null)
               {
                  event2.put("name", ((ConfirmDataException) ex).getName());
               }

               mcmd.addEvent(event2);
               command.addCommand(mcmd);
            }
         }
         else if(event.get("touchAsset") == null) {
            LogLevel level = (ex instanceof LogException) ?
               ((LogException) ex).getLogLevel() : LogLevel.ERROR;
            LogManager.getInstance().logException(LOG, level, "Failed to process event: " + event, ex);

            int warningLvl = (ex instanceof MessageException) ?
               ((MessageException) ex).getWarningLevel() : MessageCommand.ERROR;

            AssetCommand scmd = new MessageCommand(ex, warningLvl);

            if(ex instanceof ViewsheetException) {
               scmd.put("sheetnotfound", "true");
            }

            command.addCommand(scmd);
         }

         scheduleEx.set(ex);
      }
      finally {
         command.complete();

         if(id != null) {
            synchronized(amap) {
               Vector<ThreadDef> threads = emap.get(id);

               if(threads != null) {
                  if(threads.size() > 0) {
                     threads.remove(threads.size() - 1);
                  }

                  if(threads.size() == 0) {
                     emap.remove(id);

                     if(isValidExecutingObject(id)) {
                        executionMap.setCompleted(id);
                     }
                  }
                  else {
                     emap.put(id, threads);
                  }
               }
            }
         }
      }

      return command;
   }

   /**
    * Dispose the worksheet service.
    */
   @Override
   public void dispose() {
      engine.dispose();
   }

   /**
    * Process an asset event internally.
    * @param event the specified asset event to process.
    * @param command the associated asset command.
    */
   protected void process0(AssetEvent event, Principal user,
                           AssetCommand command)
      throws Throwable
   {
      String id = null;

      if(event instanceof GridEvent) {
         GridEvent gevent = (GridEvent) event;
         id = gevent.getID();
         RuntimeSheet rs0 = amap.get(id);

         if(rs0 == null && gevent.isCloseExpired()) {
            gevent.process(null, command);
            return;
         }

         // @by stephenwebster, For bug1420584760134
         // The real access time flag is set to true when there is any action
         // taken in Visual Composer (including heartbeat), or if any
         // non-TouchAssetEvent occurs.
         boolean raccessed = event.get("touchAsset") == null ||
            (event.get("design") != null && "true".equals(event.get("design")));

         if("true".equals(SreeEnv.getProperty("event.debug")) &&
            !amap.containsKey(id))
         {
            System.err.println("Sheet not found in event: " + event +", " + id);
         }

         RuntimeSheet rs = getSheet(id, user, raccessed);
         AbstractSheet sheet = gevent.isUndoable() && !gevent.isDefault() ?
            rs.getSheet() : null;

         // set asset name in the thread local variable
         if(MonitorLevelService.getMonitorLevel() > 0 &&
            rs.getEntry() != null)
         {
            @SuppressWarnings("unchecked")
            List<String> infos = XUtil.QUERY_INFOS.get();
            infos.add(0, rs.getEntry().getSheetName());
         }

         int lsize = rs.size();
         int lcurrent = rs.getCurrent();
         int lsave = rs.getSavePoint();
         boolean processed = false;

         try {
            if(gevent.get("isSafariOniOS") != null) {
               rs.setProperty("isSafariOniOS", "true");
            }

            gevent.process(rs, command);

            if(sheet != null) {
               sheet.checkDependencies();
            }

            processed = true;
         }
         catch(Throwable ex) {
            if(ex instanceof StackOverflowError) {
               ex = new InvalidDependencyException(Catalog.getCatalog()
                  .getString("common.dependencyCycle"));
            }

            if(ex instanceof ConfirmException) {
               processed = true;
               throw ex;
            }

            if(ex instanceof InvalidDependencyException) {
               rs.rollback();
            }

            if(!rs.isDisposed()) {
               throw ex;
            }
         }
         finally {
            if(processed) {
               // update undoable after grid event is processed
               sheet = sheet != null && !gevent.isUndoable() ? null : sheet;

               // process undo/redo
               if(sheet != null && !rs.isDisposed() &&
                  (!gevent.requiresReturn() || !command.isEmpty() ||
                   command.isEnqueued() && command.isSuccessful()))
               {
                  if(rs.max == rs.size()) {
                     command.put("canRedoUndo", "true");
                  }

                  rs.addCheckpoint(sheet.prepareCheckpoint(), gevent);
               }

               // update save point if a save sheet event is processed successfully
               if(event instanceof SaveSheetEvent && command.isSuccessful()) {
                  rs.setSavePoint(rs.getCurrent());
               }

               if(!rs.isDisposed()) {
                  int csize = rs.size();
                  int ccurrent = rs.getCurrent();
                  int csave = rs.getSavePoint();
                  command.put("point.count", csize + "");
                  command.put("point.current", ccurrent + "");
                  command.put("point.save", csave + "");
                  command.put("undo.name", rs.getUndoName());
                  command.put("redo.name", rs.getRedoName());

                  if(sheet != null || lsize != csize || lcurrent != ccurrent ||
                     lsave != csave)
                  {
                     // add a need auto refresh status for flex
                     command.put("autorefresh.value", "true");
                  }
               }
            }
         }

         // add grid id information
         for(int i = 0; i < command.getCommandCount(); i++) {
            AssetCommand tcmd = command.getCommand(i);

            if(tcmd instanceof GridCommand) {
               GridCommand gcmd = (GridCommand) tcmd;

               if(gcmd.getID() == null) {
                  gcmd.setID(gevent.getID());
               }
            }
         }

         // as command might be sent for times, here we make sure it's not
         // empty, then we could support undo/redo on client side properly
         if(command.isEnqueued() && command.isSuccessful() &&
            command.getCommandCount() == command.getCurrentPosition())
         {
            command.addCommand(new MessageCommand("", MessageCommand.OK),
                               false);
         }

         //noinspection ConstantConditions
         if((rs instanceof RuntimeWorksheet) &&
            (((GridEvent) event).isUndoable() || (event instanceof UndoEvent) ||
               (event instanceof RedoEvent)))
         {
            AssemblyTreeModelBuilder builder
               = new AssemblyTreeModelBuilder((RuntimeWorksheet) rs);
            AssemblyTreeModel model = builder.createAssemblyTreeModel();
            AssemblyTreeModel model0 = (AssemblyTreeModel)
               rs.getProperty("_assemblyTreeModel");

            if(!Tool.equals(model, model0) ||
               event instanceof RefreshAssemblyTreeEvent)
            {
               command.addCommand(new RefreshAssemblyTreeCommand(model));
               rs.setProperty("_assemblyTreeModel", model);
            }
         }
      }
      else if(event instanceof AssetRepositoryEvent) {
         AssetRepositoryEvent aevent = (AssetRepositoryEvent) event;
         aevent.process(engine, command);
      }
      else {
         event.process(command);
      }

      // process errors
      List<Exception> errors = WorksheetEngine.ASSET_EXCEPTIONS.get();

      if(errors == null || errors.size() == 0) {
         if(event.getConfirmExceptionCount() > 0) {
            addConfirmCommand(event, command);
         }
      }
      else {
         MessageCommand confirm2 = new MessageCommand();
         String msg2 = "";
         int lvl2 = MessageCommand.CONFIRM;
         AssetEvent assetEvent2;

         // process all ConfirmDataException, get events from all
         // ConfirmDataException, and put the events into one message conmmand
         for(Exception error : errors) {
            if(error instanceof ConfirmDataException) {
               ConfirmDataException cdex = (ConfirmDataException) error;
               msg2 = cdex.getMessage();
               lvl2 = cdex.getLevel();
               assetEvent2 = (AssetEvent) cdex.getEvent();
               assetEvent2 = (assetEvent2 == null) ? event : assetEvent2;

               //noinspection ConstantConditions
               if(assetEvent2 != null && assetEvent2.get("name") == null &&
                  cdex.getName() != null) {
                  assetEvent2.put("name", cdex.getName());
               }

               confirm2.addEvent(assetEvent2);
            }
         }

         if(confirm2.getEventCount() > 0) {
            confirm2.put("message", msg2);
            confirm2.put("level", "" + lvl2);
            command.addCommand(confirm2);
         }

         StringBuilder sb = new StringBuilder();
         Set<String> mset = new HashSet<>();

         for(Exception ex : errors) {
            String msg = ex instanceof SQLException ?
               "SQL " + ex.getMessage() : ex.getMessage();

            if(ex instanceof MessageException) {
               MessageException me = (MessageException) ex;
               command.addCommand(new MessageCommand(me.getMessage(), me.getWarningLevel()));
               continue;
            }

            // do not construct a message command for a cancelled exception,
            // for it's always per user's request
            if(ex instanceof CancelledException) {
               continue;
            }

            if(ex instanceof ConfirmDataException) {
               continue;
            }

            if(ex instanceof ConfirmException) {
               ConfirmException cex2 = (ConfirmException) ex;
               MessageCommand confirm =
                  new MessageCommand(msg, cex2.getLevel());
               confirm.addEvent((AssetEvent) cex2.getEvent());
               command.addCommand(confirm);
               continue;
            }

            ExceptionKey key = new ExceptionKey(ex, id);
            ExceptionKey key2 = exceptionMap.get(key);

            if(key2 != null && !key2.isTimeout()) {
               msg = null;
            }
            else {
               exceptionMap.put(key, key);
            }

            if(msg == null) {
               continue;
            }

            int index = msg.indexOf("\n");
            msg = index > 0 ? msg.substring(0, index).trim() : msg;

            if(!mset.contains(msg)) {
               if(sb.length() > 0) {
                  sb.append(", ");
               }

               sb.append(msg);
               mset.add(msg);
            }
         }

         errors.clear();

         if(sb.length() != 0) {

            // @by: ChrisSpagnoli bug1414093660974 2014-10-29
            // Avoid creating a second ERROR message if one is already pending.
            for(int i = 0; i < command.getCommandCount(); i++) {
               if(command.getCommand(i) instanceof MessageCommand) {
                  MessageCommand mc = (MessageCommand)command.getCommand(i);
                  String level = (String)mc.get("level");

                  if(level.equals(""+MessageCommand.ERROR)) {
                     return;
                  }
               }
            }

            String msg = Catalog.getCatalog().getString(sb.toString());
            MessageCommand mcmd =
               new MessageCommand(msg, MessageCommand.ERROR);
            command.addCommand(mcmd);
         }
      }
   }

   /**
    * Adds the confirm command for any confirm exceptions logged by the event.
    *
    * @param event   the event.
    * @param command the command.
    */
   @SuppressWarnings("ThrowableResultOfMethodCallIgnored")
   public static void addConfirmCommand(AssetEvent event, AssetCommand command) {
      MessageCommand confirm = new MessageCommand();
      String msg = "";
      int lvl = MessageCommand.CONFIRM;
      AssetEvent assetEvent;

      // get events from all ConfirmDataException, and put the events
      // into one message command
      for(int i = 0; i < event.getConfirmExceptionCount(); i++) {
         ConfirmException cex = event.getConfirmException(i);
         msg = cex.getMessage();
         lvl = cex.getLevel();
         assetEvent = (AssetEvent) cex.getEvent();

         if(cex instanceof ConfirmDataException && assetEvent != null &&
            assetEvent.get("name") == null &&
            ((ConfirmDataException) cex).getName() != null)
         {

            assetEvent.put("name", ((ConfirmDataException) cex).getName());
         }

         confirm.addEvent(assetEvent);
      }

      confirm.put("message", msg);
      confirm.put("level", "" + lvl);
      command.addCommand(confirm);
   }

   /**
    * Open a temporary worksheet.
    * @param user the specified user.
    * @param aentry the specified AssetEntry.
    * @return the worksheet id.
    */
   @Override
   public String openTemporaryWorksheet(Principal user, AssetEntry aentry) throws Exception {
      AssetEntry entry = aentry != null ? aentry :
         getTemporaryAssetEntry(user, AssetEntry.Type.WORKSHEET);
      Worksheet ws = new Worksheet();

      RuntimeWorksheet rws = new RuntimeWorksheet(entry, ws, user, true);
      rws.setEditable(false);
      return createTemporarySheetId(entry, rws, user);
   }

   /**
    * Creates and sets the identifier of a temporary sheet.
    *
    * @param entry the asset entry for the sheet.
    * @param sheet the sheet.
    * @param user  the user creating the sheet.
    *
    * @return the sheet identifier.
    */
   protected String createTemporarySheetId(AssetEntry entry, RuntimeSheet sheet,
                                           Principal user)
   {
      synchronized(amap) {
         String id = getNextID(entry, user);
         sheet.setID(id);
         amap.put(id, sheet);
         return id;
      }
   }

   /**
    * Open a preview worksheet.
    * @param id the specified worksheet id.
    * @param name the specified table assembly name.
    * @param user the specified user.
    */
   @Override
   public String openPreviewWorksheet(String id, String name, Principal user)
      throws Exception
   {
      RuntimeWorksheet orws = getWorksheet(id, user);

      if(singlePreviewEnabled && orws.getProperty("__preview_target__") != null) {
         throw new RuntimeException("There is already a preview open!");
      }

      AssetEntry entry = orws.getEntry();
      Worksheet ws = orws.getWorksheet();

      String previewID = openPreviewWorksheet(ws, entry, name, user);

      if(singlePreviewEnabled) {
         RuntimeWorksheet previewWS = getWorksheet(previewID, user);

         if (previewWS != null) {
            previewWS.setProperty("__preview_source__", id);
         }

         orws.setProperty("__preview_target__", previewID);
      }

      return previewID;
   }

   /**
    * Create a preview worksheet.
    */
   protected String openPreviewWorksheet(Worksheet ws, AssetEntry oentry,
                                         String name, Principal user) {
      ws = (Worksheet) ws.clone();
      Assembly[] assemblies = ws.getAssemblies();

      for(Assembly assembly : assemblies) {
         if(assembly.getName().equals(name)) {
            TableAssembly table = (TableAssembly) assembly;
            int count = 30;

            table.setVisible(true);
            table.setPixelOffset(new Point(0, 0));
            table.setRuntime(true);
            table.setIconized(false);
            String pageCountStr =
               SreeEnv.getProperty("asset.preview.pageCount");

            try {
               count = Integer.parseInt(pageCountStr);
            }
            catch(Exception ex) {
               LOG.warn("Invalid value for the maximum preview " +
                     "page count (asset.preview.pageCount): " + pageCountStr, ex);
            }

            Dimension size = table.getPixelSize();
            table.setPixelSize(new Dimension(Math.max(size.width, 5 * AssetUtil.defw), (count + 2) * AssetUtil.defh));
         }
         else {
            ((WSAssembly) assembly).setVisible(false);
            assembly.setPixelOffset(new Point(AssetUtil.defw, AssetUtil.defh));
         }
      }

      ws.layout();

      AssetEntry entry = new AssetEntry(oentry.getScope(), oentry.getType(),
                                        oentry.getPath(), oentry.getUser());
      entry.copyProperties(oentry);
      entry.setProperty("preview", "true");

      if(oentry.getAlias() != null && oentry.getAlias().length() > 0) {
         entry.setAlias(oentry.getAlias());
      }

      RuntimeWorksheet rws = new RuntimeWorksheet(entry, ws, user, true);
      rws.setEditable(false);

      synchronized(amap) {
         String id = getNextID(PREVIEW_WORKSHEET);
         rws.setID(id);
         amap.put(id, rws);
         return id;
      }
   }

   /**
    * Create a runtime sheet.
    * @param entry the specified asset entry.
    * @param sheet the specified sheet.
    * @param user the specified user.
    */
   protected RuntimeSheet createRuntimeSheet(AssetEntry entry,
                                             AbstractSheet sheet,
                                             Principal user)
      throws Exception
   {
      if(entry.isWorksheet()) {
         return new RuntimeWorksheet(entry, (Worksheet) sheet, user, true);
      }

      return null;
   }

   /**
    * Open an exsitent worksheet.
    * @param entry the specified asset entry.
    * @param user the specified user.
    * @return the worksheet id.
    */
   @Override
   public String openWorksheet(AssetEntry entry, Principal user)
      throws Exception
   {
      return openSheet(entry, user);
   }

   /**
    * Open an existing sheet. If the sheet is already open for the user,
    * return the id of the existing sheet.
    * @param entry the specified asset entry.
    * @param user the specified user.
    * @return the sheet id.
    */
   @SuppressWarnings("UnnecessaryContinue")
   protected final String openSheet(AssetEntry entry, Principal user)
      throws Exception
   {
      boolean permission = !"true".equals(entry.getProperty("isDashboard"));
      entry.setProperty("isDashboard", null); // clear the temp property
      AbstractSheet sheet = engine.getSheet(entry, user, permission,
                                            AssetContent.ALL);

      if(sheet == null) {
         throw new ViewsheetException(Catalog.getCatalog().getString(
            "common.sheetCannotFount", entry.toString()));
      }

      // @by larryl, runtime should not be persistent, it's set at runtime.
      // bug1328565116718, it appears the preview view is saved as ws so
      // the primary is marked as runtime and others are hidden, not sure
      // how it got into this state but it should be safe to reset it
      // when opening a new ws
      // @by davyc, clear preview status, don't know why the status wrong,
      // need to reproduce and fix
      // fix bug1341215663394
      entry.setProperty("preview", null);

      for(Assembly obj : sheet.getAssemblies()) {
         if(obj instanceof TableAssembly) {
            ((TableAssembly) obj).setRuntime(false);
         }

         if(obj instanceof WSAssembly && (!(obj instanceof BoundTableAssembly) ||
            !"true".equals(((BoundTableAssembly) obj).getProperty(BoundTableHelper.LOGICAL_BOUND_COPY))))
         {
            ((WSAssembly) obj).setVisible(true);
         }

         if(obj instanceof AbstractWSAssembly) {
            ((AbstractWSAssembly) obj).setOldName(obj.getName());

            if(obj instanceof AbstractTableAssembly) {
               DependencyTransformer.initTableColumnOldNames((AbstractTableAssembly) obj, true);
            }
         }
      }

      sheet = (AbstractSheet) sheet.clone();

      String lockedBy = null;
      int mode0 = getEntryMode(entry);
      // try if this is a request to sync a vs with another
      // (from fullscreen or editing)
      boolean sync = "true".equals(entry.getProperty("sync"));

      if(entry.getProperty("drillfrom") == null && sync) {
         List list = amap.keyList();

         for(Object item : list) {
            String id = (String) item;
            RuntimeSheet rs2 = amap.get(id);

            if(rs2 == null) {
               continue;
            }

            AssetEntry entry2 = rs2.getEntry();
            Principal user2 = rs2.getUser();

            // ignore temporary sheet
            if(entry2.getScope() == AssetRepository.TEMPORARY_SCOPE) {
               continue;
            }

            if(entry2.getProperty("drillfrom") != null) {
               continue;
            }

            // ignore preview sheet
            if(id.startsWith(PREVIEW_PREFIX)) {
               continue;
            }

            // same sheet?
            if(entry2.equals(entry)) {
               // opened by self twice?
               if(Tool.equals(user2, user)) {
                  // only share runtime sheet when same mode, and the sheet has
                  // not been modified since
                  if(mode0 == rs2.getMode() &&
                     sheet.getLastModified(true) ==
                        rs2.getSheet().getLastModified(true)) {
                     return id;
                  }
                  else {
                     continue;
                  }
               }
               // opened by others?
               else if(rs2.isEditable()) {
                  if(!rs2.isRuntime()) {
                     lockedBy = user2 == null ? null :
                        ((XPrincipal) user2).getFullName();
                  }

                  continue;
               }
            }
         }
      }

      RuntimeSheet rs = createRuntimeSheet((AssetEntry) entry.clone(), sheet, user);

      if(lockedBy != null) {
         rs.setEditable(false);
         rs.setLockOwner(lockedBy);

         if(!rs.isRuntime()) {
            List<Exception> exs = WorksheetService.ASSET_EXCEPTIONS.get();

            if(exs != null) {
               Catalog catalog = Catalog.getCatalog();
               exs.add(new ConfirmException(
                          catalog.getString("common.AssetLockBy", lockedBy),
                          MessageCommand.WARNING));
            }
         }
      }
      else {
         try {
           engine.checkAssetPermission(user, entry, ResourceAction.WRITE);
         }
         catch(Exception ex) {
            rs.setEditable(false);
         }
      }

      synchronized(amap) {
         String id = getNextID(entry, user);
         rs.setID(id);
         amap.put(id, rs);
         return id;
      }
   }

   /**
    * Get the mode the entry is for.
    */
   private int getEntryMode(AssetEntry entry) {
      boolean viewer = "true".equals(entry.getProperty("viewer"));
      boolean preview = "true".equals(entry.getProperty("preview"));
      return (viewer || preview || entry.isVSSnapshot())
         ? RuntimeViewsheet.VIEWSHEET_RUNTIME_MODE
         : RuntimeViewsheet.VIEWSHEET_DESIGN_MODE;
   }

   /**
    * Get the runtime worksheet.
    * @param id the specified worksheet id.
    * @param user the specified user.
    * @return the runtime worksheet if any.
    */
   @Override
   public RuntimeWorksheet getWorksheet(String id, Principal user)
      throws Exception
   {
      return (RuntimeWorksheet) getSheet(id, user, false);
   }

   /**
    * Get the runtime sheet.
    * @param id the specified sheet id.
    * @param user the specified user.
    * @return the runtime sheet if any.
    */
   @Override
   public RuntimeSheet getSheet(String id, Principal user) {
      return getSheet(id, user, false);
   }

   /**
    * Get the runtime sheet.
    * @param id the specified sheet id.
    * @param user the specified user.
    * @param touch <code>true</code> to update the access time and heartbeat.
    * @return the runtime sheet if any.
    */
   protected final RuntimeSheet getSheet(String id, Principal user, boolean touch) {
      RuntimeSheet rs = amap.get(id);
      Catalog catalog = Catalog.getCatalog();

      if(rs == null) {
         LOG.debug("Worksheet/viewsheet has expired: " + id);
         throw new ExpiredSheetException(id, user);
      }

      if(rs.getUser() != null && user != null && !rs.matches(user)) {
         if(!(user instanceof SRPrincipal &&
            Boolean.TRUE.toString().equals(((SRPrincipal) user).getProperty("supportLogin"))))
         {
            throw new InvalidUserException(
               catalog.getString("common.invalidUser", user, rs.getUser()),
               LogLevel.INFO, false, rs.getUser());
         }
      }

      accessSheet(id, touch);
      return rs;
   }

   /**
    * Save the worksheet.
    * @param ws the specified worksheet.
    * @param entry the specified asset entry.
    * @param user the specified user.
    * @param force <tt>true</tt> to set worksheet forcely without
    * checking.
    */
   @Override
   public void setWorksheet(Worksheet ws, AssetEntry entry,
                            Principal user, boolean force, boolean updateDependency)
      throws Exception
   {
      setSheet(ws, entry, user, force, updateDependency);
   }

   /**
    * Save the runtime sheet.
    * @param sheet the specified abstract sheet.
    * @param entry the specified asset entry.
    * @param user the specified user.
    * @param force <tt>true</tt> to set worksheet forcely without
    * @param updateDependency <tt>true</tt> to update dependency or not
    * checking.
    */
   protected final void setSheet(AbstractSheet sheet, AssetEntry entry,
                                 Principal user, boolean force, boolean updateDependency)
      throws Exception
   {
      String owner = getLockOwner(entry);
      String uname = user == null ? null : user.getName();

      if(owner != null && !owner.equals(uname)) {
         throw new MessageException(Catalog.getCatalog().getString(
                                       "common.worksheetLocked", entry.getPath(),
                                       owner, uname), LogLevel.WARN, false);
      }

      ((AbstractAssetEngine) engine).setSheet(entry, sheet, user, force, true, updateDependency);
   }

   /**
    * Get the runtime sheets.
    * @return the runtime sheets.
    */
   @Override
   public RuntimeSheet[] getRuntimeSheets(Principal user) {
      List<String> keys = amap.keyList();
      List<RuntimeSheet> list = new ArrayList<>();

      for(String key : keys) {
         RuntimeSheet rvs = amap.get(key);

         if(user == null || rvs != null && rvs.matches(user)) {
            list.add(rvs);
         }
      }

      return list.toArray(new RuntimeSheet[0]);
   }

   /**
    * Get the lock owner.
    * @param entry the specified asset entry.
    */
   @Override
   public String getLockOwner(AssetEntry entry) {
      for(String key : amap.keyList()) {
         RuntimeSheet rs = amap.get(key);

         if(rs == null) {
            continue;
         }

         AssetEntry entry2 = rs.getEntry();

         // when the save viewsheet action is caused by save home bookmark
         // should check lock also
         if(Tool.equals(entry2, entry) && (rs.isEditable() ||
            "true".equals(entry.getProperty("homeBookmarkSaved"))))
         {
            entry.setProperty("homeBookmarkSaved", null);
            Principal user = rs.getUser();
            return user == null ? null : user.getName();
         }
      }

      entry.setProperty("homeBookmarkSaved", null);

      return null;
   }

   /**
    * Access a sheet.
    */
   private void accessSheet(String id, boolean touch) {
      synchronized(amap) {
         RuntimeSheet rs = amap.get(id);

         if(rs != null) {
            amap.append(id, rs);

            if(touch) {
               rs.access(true);
            }
         }
      }
   }

   public RuntimeWorksheet[] getAllRuntimeWorksheetSheets() {
      synchronized(amap) {
         return amap.values().stream()
            .filter(sheet -> sheet instanceof RuntimeWorksheet)
            .map(sheet -> (RuntimeWorksheet) sheet)
            .toArray(RuntimeWorksheet[]::new);
      }
   }

   private RuntimeWorksheet getRuntimeWorksheet(String rid) {
      RuntimeSheet runtimeSheet = getRuntimeSheet(rid);

      if(runtimeSheet instanceof RuntimeWorksheet) {
         return (RuntimeWorksheet) runtimeSheet;
      }
      else {
         return null;
      }
   }

   private RuntimeSheet getRuntimeSheet(String rid) {
      synchronized(amap) {
         return amap.get(rid);
      }
   }

   /**
    * Close a worksheet.
    * @param id the specified worksheet id.
    */
   @Override
   public void closeWorksheet(String id, Principal user) throws Exception {
      closeSheet(id, user);
   }

   /**
    * Close a sheet.
    * @param id the specified sheet id.
    */
   protected final void closeSheet(String id, Principal user) throws Exception {
      RuntimeSheet rsheet = amap.get(id);

      if(rsheet != null && user != null &&
         rsheet.getUser() != null && !rsheet.matches(user))
      {
         if(!(user instanceof SRPrincipal &&
            Boolean.TRUE.toString().equals(((SRPrincipal) user).getProperty("supportLogin"))))
         {
            throw new InvalidUserException(Catalog.getCatalog().
               getString("common.invalidUser", user, rsheet.getUser()),
                                           rsheet.getUser());
         }
      }

      synchronized(amap) {
         if(isValidExecutingObject(id)) {
            executionMap.setCompleted(id);
         }

         amap.remove(id);
         emap.remove(id);
         clearPreviewTarget(rsheet, id);
         renameInfoMap.remove(id);
      }

      if(rsheet != null) {
         rsheet.dispose();
      }
   }

   /**
    * Close sheets according to the specified user.
    * @param user the specified user.
    */
   public void closeSheets(Principal user) throws Exception {
      List<String> ids = new ArrayList<>();

      for(String key : amap.keyList()) {
         RuntimeSheet rsheet = amap.get(key);

         if(rsheet != null && rsheet.matches(user)) {
            ids.add(key);
         }
      }

      for(String id : ids) {
         closeSheet(id, user);
      }
   }

   /**
    * Get the next sheet id.
    * @param entry the specified entry.
    * @return the next sheet id.
    */
   protected String getNextID(AssetEntry entry, Principal user) {
      return getNextID(entry.getPath());
   }

   /**
    * Get the next sheet id.
    * @param path the specified path.
    * @return the next sheet id.
    */
   protected String getNextID(String path) {
      return path + "-" + nextId.getAndIncrement();
   }

   /**
    * Set the server flag.
    * @param server <tt>true</tt> if server, <tt>false</tt> otherwise.
    */
   @Override
   public void setServer(boolean server) {
      this.server = server;

      if(server) {
         TimedQueue.addSingleton(new RecycleTask(3 * 60000));
      }
      else {
         TimedQueue.remove(RecycleTask.class);
      }
   }

   /**
    * Check if server is turned on.
    * @return <tt>true</tt> if turned on, <tt>false</tt> turned off.
    */
   @Override
   public boolean isServer() {
      return server;
   }

   /**
    * Get the date ranges from root.
    */
   public static List<Assembly> getDateRanges(AssetRepository rep, Principal user) {
      String uname = user == null ? "null" : user.getName();
      List<Assembly> list = dranges.get(uname);

      if(list == null) {
         AssetEntry[] roots = {
            (user != null) ? AssetEntry.createUserRoot(user) : null,
            AssetEntry.createGlobalRoot()
         };

         list = new ArrayList<>();
         Set<AssetEntry> added = new HashSet<>();

         // add all date range assemblies from report, user, global scope
         for(AssetEntry root : roots) {
            if(root == null) {
               continue;
            }

            try {
               AssetEntry[] arr = AssetUtil.getEntries(
                  rep, root, user, AssetEntry.Type.WORKSHEET,
                  AbstractSheet.DATE_RANGE_ASSET, true);

               for(AssetEntry entry : arr) {
                  if(added.contains(entry)) {
                     continue;
                  }

                  Worksheet worksheet = (Worksheet) rep.getSheet(
                     entry, user, false, AssetContent.ALL);
                  added.add(entry);
                  Assembly assembly = worksheet.getPrimaryAssembly();

                  if(assembly instanceof MirrorAssembly) {
                     continue;
                  }

                  boolean contained = false;

                  for(Assembly assembly2 : list) {
                     String name = assembly.getName();

                     if(assembly2.getName().equals(name)) {
                        contained = true;
                        LOG.warn(
                           "Duplicate date range name found: " + name +
                              ", path: " + entry.getDescription(false));
                        break;
                     }
                  }

                  if(!contained) {
                     list.add(assembly);
                  }
               }
            }
            catch(Exception ex) {
               LOG.warn(ex.getMessage(), ex);
            }
         }

         dranges.put(uname, list);
      }

      return list;
   }

   @Override
   public boolean needRenameDep(String rid) {
      List<RenameDependencyInfo> renameDependencyInfos = renameInfoMap.get(rid);

      if(renameDependencyInfos == null || renameDependencyInfos.size() == 0) {
         return false;
      }

      return true;
   }

   @Override
   public void clearRenameDep(String rid) {
      renameInfoMap.remove(rid);
   }

   @Override
   public void rollbackRenameDep(String rid) {
      List<RenameDependencyInfo> renameDependencyInfos = renameInfoMap.get(rid);

      if(renameDependencyInfos == null) {
         return;
      }

      for(int i = renameDependencyInfos.size() - 1; i >= 0; i--) {
         RenameDependencyInfo info = renameDependencyInfos.get(i);
         RenameDependencyInfo causeRenameDepsInfo = new RenameDependencyInfo();

         AssetObject[] assetObjects = info.getAssetObjects();

         for(AssetObject assetObject : assetObjects) {
            if(!(assetObject instanceof AssetEntry) || !((AssetEntry) assetObject).isWorksheet()) {
               continue;
            }

            List<RenameInfo> infos = info.getRenameInfo(assetObject);
            List<RenameInfo> causeInfos = new ArrayList<>();

            for(int j = infos.size() - 1; j >= 0; j--) {
               RenameInfo renameInfo = infos.get(j);
               causeInfos.addAll(
                  createCauseWSColRenameInfos(renameInfo, (AssetEntry) assetObject, rid));
            }

            List<AssetObject> depAssets =
               DependencyTransformer.getDependencies(((AssetEntry) assetObject).toIdentifier());

            if(depAssets != null) {
               for(AssetObject depAsset : depAssets) {
                  causeRenameDepsInfo.setRenameInfo(depAsset, causeInfos);
               }

               return;
            }
         }

         if(causeRenameDepsInfo.getAssetObjects().length > 0) {
            RenameTransformHandler.getTransformHandler().addTransformTask(causeRenameDepsInfo);
         }
      }
   }

   @Override
   public void fixRenameDepEntry(String rid, AssetObject newEntry) {
      List<RenameDependencyInfo> renameDependencyInfos = renameInfoMap.get(rid);
      RuntimeSheet runtimeSheet = getRuntimeSheet(rid);

      if(renameDependencyInfos == null || runtimeSheet == null) {
         return;
      }

      AssetObject entry = runtimeSheet.getEntry();
      DependencyTransformer.fixRenameDepEntry(rid, entry, newEntry, renameInfoMap);
   }

   private List<RenameInfo> createCauseWSColRenameInfos(RenameInfo renameInfo, AssetEntry entry ,
                                                        String rid)
   {
      RuntimeWorksheet runtimeWorksheet = getRuntimeWorksheet(rid);

      if(runtimeWorksheet == null || !renameInfo.isColumn()) {
         return new ArrayList<>();
      }

      Worksheet ws = runtimeWorksheet.getWorksheet();
      Assembly[] assemblies = ws.getAssemblies();
      List<RenameInfo> causeInfos = new ArrayList<>();

      for(Assembly assembly : assemblies) {
         if(!(assembly instanceof AbstractTableAssembly)) {
            continue;
         }

         createCauseWSColRenameInfosByTable(renameInfo, entry, (AbstractTableAssembly) assembly,
            causeInfos);
      }

      return causeInfos;
   }

   private void createCauseWSColRenameInfosByTable(RenameInfo renameInfo, AssetEntry entry,
                                                   AbstractTableAssembly tableAssembly,
                                                   List<RenameInfo> causeInfos)
   {
      String assemblyName = tableAssembly.getName();
      ColumnSelection cols = tableAssembly.getColumnSelection(false);

      for(int i = 0; i < cols.getAttributeCount(); i++) {
         DataRef ref = cols.getAttribute(i);

         if(ref instanceof ColumnRef) {
            ColumnRef col = (ColumnRef) ref;

            if(col == null || !StringUtils.isEmpty(col.getAlias())) {
               continue;
            }

            String oldName = renameInfo.getOldName();
            String newName = renameInfo.getNewName();

            if(renameInfo.isLogicalModel()) {
               oldName = oldName.substring(oldName.indexOf(".") + 1);
               newName = newName.substring(newName.indexOf(".") + 1);
            }

            if(!StringUtils.equals(col.getOldName(), oldName)) {
               continue;
            }

            causeInfos.add(new RenameInfo(newName, oldName,
               RenameInfo.ASSET | RenameInfo.COLUMN,
               entry.toIdentifier(), assemblyName));
         }
      }
   }

   @Override
   public void renameDep(String rid) {
      List<RenameDependencyInfo> renameDependencyInfos = renameInfoMap.get(rid);

      if(renameDependencyInfos != null) {
         for(RenameDependencyInfo dinfo : renameDependencyInfos) {
            dinfo.setRecursive(false);

            // 1. Should not add task, we only rename dependency for current vs, only one case. And
            // we should refresh viewsheet after rename dependency. So we must do the refresh
            // after rename dependency.
            // 2. The place should only fix current vs, there is no need to update dependency key.
            // For the key is updated when the rename action. The rename action will call update
            // all dependency(no opened) to update and update key. The only action there should do
            // is to update current vs data.
            dinfo.setUpdateStorage(false);

            if(dinfo.getAssetObjects() != null &&
               dinfo.getAssetObjects()[0] instanceof AssetEntry)
            {
               RenameTransformHandler.getTransformHandler().addTransformTask(dinfo, true);
            }
         }

         renameInfoMap.remove(rid);
      }
   }

   public void updateRenameInfos(Object rid, AssetObject assetEntry,
                                    List<RenameInfo> renameInfos)
   {
      DependencyTransformer.updateRenameInfos(rid, assetEntry, renameInfos, renameInfoMap);
   }

   /**
    * Thread definition in emap.
    */
   public class ThreadDef {
      public void setStartTime(long time) {
         this.time = time;
      }

      public long getStartTime() {
         return time;
      }

      public void setThread(Thread thread) {
         this.thread = thread;
      }

      public Thread getThread() {
         return thread;
      }

      private long time;
      private Thread thread;
   }

   /**
    * Runtime asset map.
    */
   private class RuntimeAssetMap<K, V> extends OrderedMap<K, V> {
      public RuntimeAssetMap(int max) {
         this.max = max;
      }

      @Override
      public synchronized V put(K key, V value) {
         if(server && !containsKey(key) && size() >= max) {
            int index = 0;

            // remove preview first
            for(int i = 0; i < size(); i++) {
               String key2 = (String) getKey(i);

               if(key2.startsWith(PREVIEW_PREFIX)) {
                  index = i;
                  break;
               }
            }

            remove(index);
         }

         return super.put(key, value);
      }

      private int max;
   }

   /**
    * Recycle thread.
    */
   private class RecycleTask extends TimedQueue.TimedRunnable {
      public RecycleTask(long time) {
         super(time);
      }

      @Override
      public boolean isRecurring() {
         return true;
      }

      /**
       * Run process.
       */
      @Override
      public void run() {
         try {
            for(String id : amap.keyList()) {
               RuntimeSheet rs = amap.get(id);
               boolean timedout;
               boolean scheduler = rs != null && rs.getEntry() != null &&
                  "true".equals(rs.getEntry().getProperty("_scheduler_"));

               synchronized(amap) {
                  timedout = rs == null ||
                     (rs.isTimeout() && !emap.containsKey(id) && !scheduler);

                  if(timedout) {
                     if(isValidExecutingObject(id)) {
                        executionMap.setCompleted(id);
                     }

                     amap.remove(id);
                     emap.remove(id);
                     clearPreviewTarget(rs, id);
                  }
               }

               if(timedout && rs != null) {
                  rs.dispose();
               }
            }

            //noinspection SynchronizeOnNonFinalField
            synchronized(cmap) {
               List<CommandEntry> list = new ArrayList<>(cmap.values());

               for(CommandEntry entry : list) {
                  if(entry.isTimeout()) {
                     int eid = entry.getCommand().getEventID();
                     cmap.remove(eid);
                  }
               }
            }
         }
         catch(Exception ex) {
            LOG.error("An error occurred while cleaning up worksheets", ex);
         }
      }
   }

   private void clearPreviewTarget(RuntimeSheet runtimeSheet, String id) {
      if(singlePreviewEnabled && runtimeSheet != null && id != null && id.startsWith(PREVIEW_WORKSHEET))
      {
         String sourceWorksheetID = (String) runtimeSheet.getProperty("__preview_source__");

         if(sourceWorksheetID != null) {
            RuntimeSheet sourceWorksheet = amap.get(sourceWorksheetID);

            if(sourceWorksheet != null) {
               sourceWorksheet.setProperty("__preview_target__", null);
            }
         }
      }
   }

   /**
    * Command entry.
    */
   private static class CommandEntry {
      public CommandEntry(AssetCommand cmd) {
         super();

         this.cmd = cmd;
         this.ts = System.currentTimeMillis();
      }

      public void access() {
         ts = System.currentTimeMillis();
      }

      public AssetCommand getCommand() {
         access();
         return cmd;
      }

      public boolean isTimeout() {
         long now = System.currentTimeMillis();
         long idle = now - ts;
         return idle > RuntimeSheet.getMaxIdleTime();
      }

      public long getTimestamp() {
         return ts;
      }

      private AssetCommand cmd;
      private long ts;
   }

   // date range provider cache
   private static ResourceCache dcache = new ResourceCache(50) {
      @Override
      protected boolean checkTimeOut() {
         @SuppressWarnings("unchecked")
         List<Object> list = new ArrayList<>(map.keySet());
         boolean changed = false;

         for(Object item : list) {
            RuntimeAssetEngine engine = (RuntimeAssetEngine) item;

            if(!engine.isAvailable()) {
               map.remove(engine);
               changed = true;
            }
         }

         changed = super.checkTimeOut() || changed;
         return changed;
      }

      @Override
      protected Object create(Object key) throws Exception {
         final RuntimeAssetEngine engine = (RuntimeAssetEngine) key;
         Principal user = ThreadContext.getContextPrincipal();
         ReportSheet report = engine.getReport();
         AssetEntry root = report != null ?
            AssetEntry.createReportRoot() : null;
         DateRangeProvider provider = new DateRangeProvider();
         Set<AssetEntry> added = new HashSet<>();
         AssetChangeListener listener = new AssetChangeListener() {
            @Override
            public void assetChanged(AssetChangeEvent event) {
               if(event.getEntryType() == Worksheet.DATE_RANGE_ASSET) {
                  remove(engine);

                  if(!(event.getSource() instanceof ReportSheet)) {
                     dranges.clear();
                  }
               }
            }
         };

         engine.setAssetChangeListener(listener);
         engine.addAssetChangeListener(listener);

         // get global scope and user scope dateranges first,
         // which is cached for better performance
         List<Assembly> dateranges =
            new ArrayList<>(getDateRanges(engine.getRepository(), user));

         // add all date range assemblies from report
         if(root != null) {
            try {
               AssetEntry[] arr = AssetUtil.getEntries(
                  report, root, user, AssetEntry.Type.WORKSHEET,
                  AbstractSheet.DATE_RANGE_ASSET, true);

               for(AssetEntry entry : arr) {
                  if(added.contains(entry)) {
                     continue;
                  }

                  if(!entry.isSheet()) {
                     continue;
                  }

                  Worksheet worksheet = (Worksheet) report.getSheet(
                     entry, user, false, AssetContent.ALL);
                  added.add(entry);
                  Assembly assembly = worksheet.getPrimaryAssembly();

                  if(assembly instanceof MirrorAssembly) {
                     continue;
                  }

                  boolean contained = false;

                  for(Assembly daterange : dateranges) {
                     String name = assembly.getName();

                     if(daterange.getName().equals(name)) {
                        contained = true;
                        LOG.warn(
                           "Duplicate date range name found when creating " +
                              "data range provider: " + name + ", path: " +
                              entry.getDescription(false));
                        break;
                     }
                  }

                  if(!contained) {
                     dateranges.add(assembly);
                  }
               }
            }
            catch(Exception ex) {
               LOG.warn("Failed to get date range assemblies", ex);
            }
         }

         DateRangeAssembly[] assemblies = new DateRangeAssembly[dateranges.size()];
         //noinspection SuspiciousToArrayCall
         provider.setDateRangeAssemblies(dateranges.toArray(assemblies));
         provider.setBuiltinDateConditions(DateCondition.getBuiltinDateConditions());
         return provider;
      }
   };

   /**
    * Get the viewsheet data changed timestamp.
    * @param entry - the viewsheet entry.
    */
   @Override
   public long getDataChangedTime(AssetEntry entry) {
      return 0;
   }

   /**
    * Get the worksheet service.
    */
   public static WorksheetService getWorksheetService() {
      return SingletonManager.getInstance(WorksheetService.class);
   }

   /**
    * Print the current status.
    */
   public void print() {
      System.err.println("---WorksheetEngine: " + this);
      print0();
   }

   /**
    * Print the current status internally.
    */
   protected void print0() {
      System.err.println("--amap size: " + amap.size() + "<>" + amap);
      System.err.println("--cmap size: " + cmap.size() + "<>" + cmap.keySet());
   }

   /**
    * apply runtime condition to the specified worksheet.
    */
   @Override
   public void applyRuntimeCondition(String vid, RuntimeWorksheet rws) {
      // do nothing
   }

   /**
    * Check the id is a valid executing object which will be add to the
    * execution map. Only the viewsheet id will be count.
    */
   protected boolean isValidExecutingObject(String id) {
      return false;
   }

   /**
    * Exception key.
    */
   public static final class ExceptionKey {
      public ExceptionKey(Throwable ex, String id) {
        this.ex = ex;
        this.id = id;
        this.ts = System.currentTimeMillis();
      }

      public int hashCode() {
         return getExceptionString().hashCode();
      }

      public boolean equals(Object obj) {
         if(!(obj instanceof ExceptionKey)) {
            return false;
         }

         ExceptionKey key2 = (ExceptionKey) obj;
         return key2.getExceptionString().equals(getExceptionString()) &&
            Tool.equals(key2.id, id);
      }

      public boolean isTimeout() {
         return System.currentTimeMillis() - ts > 10000;
      }

      public String toString() {
         return getExceptionString() + "@" + id;
      }

      private String getExceptionString() {
         if(exString == null) {
            exString = ex.toString();
         }

         return exString;
      }

      private final Throwable ex;
      private final String id;
      private final long ts;
      private String exString; // cached string
   }

   /**
    * Get the command for exporting to web.
    */
   @Override
   public AssetCommand export(String url) {
      throw new RuntimeException("Incorrect WorksheetService is used!");
   }

   public Throwable getScheduleEx() {
      return scheduleEx.get();
   }

   public void removeScheduleEx() {
      scheduleEx.remove();
   }

   private class ViewsheetException extends MessageException {
      /**
       * Constructor.
       */
      public ViewsheetException(String message) {
         super(message, LogLevel.WARN, false);
      }
   }

   public static final String INVALID_VIEWSHEET =
      "Viewsheet not exist, please check the name.";

   // cached global scope and user scope date ranges: user name->date ranges
   private static DataCache<String, List<Assembly>> dranges =
      new DataCache<>(50, 1000 * 60 * 60);
   public static final WeakHashMap<Object, ExceptionKey> exceptionMap = new WeakHashMap<>();

   protected AssetRepository engine; // asset repository
   protected final OrderedMap<String, RuntimeSheet> amap; // runtime asset map
   protected Map<String,Vector<ThreadDef>> emap; // id -> event threads
   protected ExecutionMap executionMap; // the executing viewsheet
   protected int counter; // counter
   protected Map<Integer,CommandEntry> cmap; // command map
   private final static AtomicLong nextId = new AtomicLong(1);

   private boolean singlePreviewEnabled;
   private boolean server = false; // server flag
   private Map<Object, List<RenameDependencyInfo>> renameInfoMap = new HashMap<>();
   private ThreadLocal<Throwable> scheduleEx = new ThreadLocal<>();
   private static final Logger LOG =
      LoggerFactory.getLogger(WorksheetEngine.class);
}
