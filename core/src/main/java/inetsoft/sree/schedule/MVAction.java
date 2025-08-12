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

import inetsoft.mv.*;
import inetsoft.mv.fs.FSService;
import inetsoft.mv.fs.internal.ClusterUtil;
import inetsoft.sree.internal.Mailer;
import inetsoft.sree.internal.cluster.Cluster;
import inetsoft.sree.internal.cluster.SimpleMessage;
import inetsoft.sree.security.OrganizationManager;
import inetsoft.uql.asset.AssetEntry;
import inetsoft.util.*;
import inetsoft.web.admin.content.repository.MVSupportService;
import org.apache.ignite.IgniteInterruptedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Element;

import java.io.*;
import java.security.Principal;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.locks.*;

/**
 * A schedule action to generate materialized view.
 *
 * @version 10.2
 * @author InetSoft Technology Corp
 */
public class MVAction implements AssetSupport, Cloneable, XMLSerializable, CancelableAction {
   /**
    * Create an empty action.
    */
   public MVAction() {
      super();
   }

   /**
    * A generation action for the specified materialized view.
    */
   public MVAction(MVDef mv) {
      this(mv, null);
   }

   /**
    * A generation action for the specified materialized view.
    */
   public MVAction(MVDef mv, String email) {
      this();
      this.email = email;
      this.mv = mv;

      if(mv != null) {
         mvname = mv.getName();
      }
   }

   /**
    * Get the asset entry.
    */
   @Override
   public AssetEntry getEntry() {
      return getOptionalMV().map(MVDef::getEntry).orElse(null);
   }

   /**
    * Set the asset entry.
    */
   @Override
   public void setEntry(AssetEntry entry) {
      getOptionalMV().ifPresent(m -> m.setEntry(entry));
   }

   // Association MV must be created after regular MV to make sure
   // incremental conditions are applied first
   boolean isSequenced() {
      return getOptionalMV().map(MVDef::isAssociationMV).orElse(false);
   }

   /**
    * Clone the action.
    */
   @Override
   public Object clone() {
      try {
         return super.clone();
      }
      catch(Exception e) {
         LOG.error("Failed to clone object", e);
      }

      return null;
   }

   /**
    * Get the string representation.
    */
   @Override
   public String toString() {
      return "MVAction: " + getOptionalMV().orElse(null);
   }

   /**
    * Get the mv.
    */
   public MVDef getMV() {
      return getOptionalMV().orElse(null);
   }

   private Optional<MVDef> getOptionalMV() {
      return getOptionalMV(false);
   }

   private synchronized Optional<MVDef> getOptionalMV(boolean forceRefresh) {
      if(reloadMv || forceRefresh) {
         if(mv == null && mvname != null) {
            MVManager manager = MVManager.getManager();
            manager.refresh();
            mv = manager.get(mvname);
         }

         reloadMv = false;
      }

      return Optional.ofNullable(mv);
   }

   /**
    * Run this MV internally.
    */
   @Override
   public void run(Principal principal) throws Exception {
      Optional<MVDef> optMv = getOptionalMV(true);

      if(!optMv.isPresent()) {
         LOG.warn("MV definition not found, creation task ignored: {}", mvname);
         return;
      }

      MVDef mv = optMv.get();
      Exception reason = null;
      Map<String, String> statusMap = Cluster.getInstance().getMap("inetsoft.mv.status.map");

      statusMap.put(mv.getName(), "Generating");
      sendChangeMessage(OrganizationManager.getInstance().getCurrentOrgID(principal));

      try {
         createMV(principal, mv);
         postCreate(mv);
      }
      catch(CancelledException ex) {
         // cancelled, ignore error
      }
      catch(Exception ex) {
         reason = ex;
      }
      catch(Throwable ex) {
         reason = new RuntimeException(ex);
      }
      finally {
         statusMap.remove(mv.getName());
      }

      if(email != null) {
         String info;
         String mvname = mv.getName();

         if(reason == null) {
            info = "create " + mvname + " successfully.\n";
         }
         else {
            info = "create " + mvname + " failed, " + reason + "\n";
         }

         try {
            Mailer mailer = new Mailer();
            mailer.send(email, "", "Create MVs Reports", info, null);
         }
         catch(Exception ex) {
            LOG.error(
               "Failed to send MV creation report to " + email, ex);
         }
      }

      if(reason != null) {
         throw reason;
      }
   }

   /**
    * Called after createMV to update MVManager.
    */
   private void postCreate(MVDef mv) {
      MVManager mgr = MVManager.getManager();
      final String name = mv.getName();
      final MVDef existingMv = mgr.get(name);

      if(existingMv != null) {
         mv.setCycle(existingMv.getCycle());
      }
      else {
         // mv deleted while generating
         mgr.remove(mv);
         LOG.debug("MV {} deleted", name);
         return;
      }

      mv.updateLastUpdateTime(); // update time stamp
      mgr.add(mv);

      sendChangeMessage(mv.handleUpdatingOrgID());
      SharedMVUtil.shareCreatedMV(mv);
   }

   private void sendChangeMessage(String orgID) {
      try {
         SimpleMessage message = new SimpleMessage(MVManager.MV_CHANGE_EVENT, orgID);
         Cluster.getInstance().sendMessage(message);
      }
      catch(Exception clusterMessageError) {
         LOG.error("Failed to send task message", clusterMessageError);
      }
   }

   /**
    * Create the MV in this JVM if there is no external scheduler.
    * Otherwise dispatch the job to the schedulers.
    */
   @SuppressWarnings("UnusedParameters")
   private void createMV(Principal principal, MVDef thisMv) throws Throwable {
      if(thisMv != null) {
         MVDef mv = (MVDef) thisMv.clone();
         assert mv != null;

         thisMv.setSuccess(false);
         boolean createInScheduler = Cluster.getInstance().isSchedulerRunning();
         boolean exists = mv.hasData();

         if(createInScheduler) {
            try {
               MVCallable creator = new MVCallable(mv, principal);
               mvFuture = Cluster.getInstance().submit(creator, true);
               String message = mvFuture.get();

               if(message != null) {
                  throw new RuntimeException(message);
               }
               else {
                  thisMv.setSuccess(true);
                  thisMv.setUpdated(exists);
               }
            }
            finally {
               mvFuture = null;
            }
         }
         else {
            FSService.refresh();

            if(createMV0(mv)) {
               thisMv.setSuccess(true);
               thisMv.setUpdated(exists);

               if(thisMv.isSuccess()) {
                  ClusterUtil.setUp();
               }
            }
         }
      }
   }

   /**
    * Create MV in this JVM.
    * @return true if created successfully.
    */
   private static boolean createMV0(MVDef mv) throws Exception {
      MVManager mgr = MVManager.getManager();

      // fill a cloned mv, so that the cloned mv could be gc-ed in time
      if(mgr.containsMV(mv)) {
         mgr.fill(mv);
      }

      String vs = mv.getVsId();
      boolean success;

      try {
         lock.lock();

         while(set.contains(vs)) {
            lockcnd.await(10, TimeUnit.SECONDS);
         }
      }
      finally {
         set.add(vs);
         lock.unlock();
      }

      Cluster.getInstance().lockKey("mv.create." + mv.getName());

      try {
         MVCreator creator = MVTool.newMVCreator(mv);
         runningCreators.put(mv.getName(), creator);
         creator.create();
         success = true;
      }
      catch(CancelledException ex) {
         // if association mv cancelled (due to hidden column in vpm), don't
         // leave an empty mv
         if(mv.isAssociationMV()) {
            MVSupportService support = MVSupportService.getInstance();
            List<String> name = new ArrayList<>();
            name.add(mv.getName());
            support.dispose(name);
         }

         LOG.debug("MV creation canceled: " + mv.getName(), ex);

         throw ex;
      }
      finally {
         Cluster.getInstance().unlockKey("mv.create." + mv.getName());
         lock.lock();
         set.remove(vs);
         lockcnd.signalAll();
         lock.unlock();

         if(runningCreators.containsKey(mv.getName())) {
            MVCreator creator = runningCreators.remove(mv.getName());
            creator.removeMessageListener();
         }
      }

      return success;
   }

   @Override
   public void parseXML(Element tag) throws Exception {
      Element elem = Tool.getChildNodeByTagName(tag, "MVDef");

      if(elem != null) {
         MVDef mv = new MVDef();
         mv.parseXML(elem);
         this.mv = mv;
         mvname = mv.getName();
      }

      email = Tool.getValue(Tool.getChildNodeByTagName(tag, "email"));
   }

   @Override
   public void writeXML(PrintWriter writer) {
      writeXML(writer, false);
   }

   public void writeXML(PrintWriter writer, boolean refreshMv) {
      writer.print("<Action type=\"MV\" class=\"");
      writer.print(getClass().getName());
      writer.print("\" ");
      writer.println(">");

      Optional<MVDef> optMv = getOptionalMV(refreshMv);
      optMv.ifPresent(mvDef -> mvDef.writeXML(writer));

      if(email != null) {
         writer.print("<email><![CDATA[" + email + "]]></email>");
      }

      writer.println("</Action>");
   }

   @Override
   public boolean equals(Object o) {
      if(this == o) {
         return true;
      }

      if(o == null || getClass() != o.getClass()) {
         return false;
      }

      MVAction mvAction = (MVAction) o;
      return Objects.equals(
         getOptionalMV().orElse(null),
         mvAction.getOptionalMV().orElse(null));
   }

   @Override
   public int hashCode() {
      return Objects.hash(getOptionalMV().orElse(null));
   }

   @Override
   public void cancel() {
      if(mvFuture != null) {
         MVCancelledMessage mvCancelledMessage = new MVCancelledMessage(mvname);

         try {
            Cluster.getInstance().sendMessage(mvCancelledMessage);
         }
         catch(Exception e){
            LOG.debug("Failed to send MV cancelled message", e);
         }

         mvFuture.cancel(true);
      }

      if(runningCreators.get(mvname) != null) {
         runningCreators.get(mvname).cancel();
      }
   }

   private void writeObject(ObjectOutputStream out) throws IOException {
      out.writeObject(email);
      getOptionalMV().ifPresent(mv -> mvname = mv.getName());
      out.writeObject(mvname);
   }

   private void readObject(ObjectInputStream in)
      throws IOException, ClassNotFoundException
   {
      email = (String) in.readObject();
      mvname = (String) in.readObject();
      reloadMv = true;
   }

   // executor to create a MV
   private static class MVCallable implements Callable<String>, Serializable {
      MVCallable(MVDef mv, Principal principal) {
         this.mv = mv;
         this.principal = principal;
      }

      // for serialization
      public MVCallable() {
      }

      @Override
      public String call() throws Exception {
         try {
            if(isCanceled()) {
               throw new InterruptedException("Task was cancelled.");
            }

            if(principal != null) {
               ThreadContext.setContextPrincipal(principal);
            }

            if(isCanceled()) {
               throw new InterruptedException("Task was cancelled.");
            }

            // make sure services are properly initialized
            Scheduler.getScheduler().initialize();

            // make sure new MV is loaded so it's accessible in createMV0
            MVManager.getManager().refresh();

            if(isCanceled()) {
               throw new InterruptedException("Task was cancelled.");
            }

            // make sure MV created in on-demand is loaded
            FSService.refresh();

            if(createMV0(mv)) {
               if(mv.isSuccess()) {
                  ClusterUtil.setUp();
               }

               return null;
            }
            else {
               return "MV Creation failed: " + mv.getName() +
                  ", check log for details";
            }
         }
         catch(InterruptedException | IgniteInterruptedException ex) {
            return "MV Creation cancelled: " + mv.getName();
         }
         catch(Exception ex) {
            LOG.error("Failed to create MV: {}", mv.getName(), ex);
            throw new Exception("MV Creation failed: " + mv.getName() + " [" + ex + "]");
         }
      }

      private boolean isCanceled() {
         return Thread.currentThread().isInterrupted();
      }

      private MVDef mv;
      private Principal principal;
   }

   private static final Set<String> set = new HashSet<>();
   private static final Lock lock = new ReentrantLock(); // wait lock
   private static final Condition lockcnd = lock.newCondition(); // lock condition

   private String mvname;
   // Do NOT access the mv field directly. It needs to be lazily refreshed after deserialization.
   // Only access this field via the getOptionalMV() and getOptionalMV(boolean) methods.
   private MVDef mv;
   private volatile boolean reloadMv = false;
   private String email;
   private Future<String> mvFuture;
   private static final Map<String, MVCreator> runningCreators = new ConcurrentHashMap<>();
   private static final Logger LOG = LoggerFactory.getLogger(MVAction.class);
}
