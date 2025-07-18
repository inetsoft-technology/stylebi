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

import inetsoft.report.internal.license.LicenseManager;
import inetsoft.sree.RepletRequest;
import inetsoft.sree.SreeEnv;
import inetsoft.sree.internal.DataCycleManager.CycleInfo;
import inetsoft.sree.internal.*;
import inetsoft.sree.internal.cluster.Cluster;
import inetsoft.sree.security.*;
import inetsoft.uql.XPrincipal;
import inetsoft.uql.util.Identity;
import inetsoft.util.*;
import org.slf4j.*;
import org.w3c.dom.*;

import java.io.*;
import java.security.Principal;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

/**
 * ScheduleTask defines a scheduled task. Each task is consisted of one
 * or more conditions, and one or more actions. The conditions are checked
 * to see when and whehter a task will be started. When any condition is
 * met, the task actions are executed in order. If a task is a repeated
 * task, it will be rescheduled to run at the next cycle.
 *
 * @version 7.0
 * @author InetSoft Technology Corp
 */
public class ScheduleTask implements Serializable, Cloneable, XMLSerializable {
   public ScheduleTask() {
   }

   /**
    * Create a ScheduleTask.
    * @param name task name. It should be unique in each scheduler.
    */
   public ScheduleTask(String name) {
      this();
      this.name = name;
   }

   /**
    * Create a ScheduleTask.
    * @param name task name. It should be unique in each scheduler.
    */
   public ScheduleTask(String name, Type type) {
      this();
      this.name = name;
      this.type = type;
   }

   /**
    * Get the task name.
    */
   public String getName() {
      return name;
   }

   /**
    * Set the task name.
    */
   public void setName(String name) {
      this.name = name;
      id = null;
   }

   /**
    * Get the task path.
    */
   public String getPath() {
      return path;
   }

   /**
    * Set the task path.
    */
   public void setPath(String path) {
      this.path = path;
   }

   public long getLastModified() {
      return lastModified;
   }

   public void setLastModified(long lastModified) {
      this.lastModified = lastModified;
   }

   /**
    * Check if this task is currently enabled.
    * @return true if this task is enabled.
    */
   public boolean isEnabled() {
      return enabled;
   }

   /**
    * Enables or disables this task.
    * @param enabled true to enable and false to disable.
    */
   public void setEnabled(boolean enabled) {
      this.enabled = enabled;
   }

   /**
    * Check if this task is editable.
    * @return true if this task is editable.
    */
   public boolean isEditable() {
      return editable;
   }

   /**
    * Makes the task editable or uneditable.
    * @param editable true for editable and false for uneditable.
    */
   public void setEditable(boolean editable) {
      this.editable = editable;
   }

   /**
    * Check if the task is removable.
    */
   public boolean isRemovable() {
      return removable;
   }

   /**
    * Set the removable option.
    */
   public void setRemovable(boolean removable) {
      this.removable = removable;
   }

   /**
    * Check if the task should be deleted if not scheduled to run again.
    */
   public boolean isDeleteIfNoMoreRun() {
      return delNotRun;
   }

   /**
    * Set the delete if not scheduled to run again option.
    */
   public void setDeleteIfNoMoreRun(boolean delNotRun) {
      this.delNotRun = delNotRun;
   }

   /**
    * Set this task as durable or not.
    */
   public void setDurable(boolean durable) {
      this.durable = durable;
   }

   /**
    * Check whether this task is durable or not.
    */
   public boolean isDurable() {
      return durable;
   }

   /**
    * A task is complete when any its conditions are met.
    * Check all conditions of this task to see if any condition is met.
    * The relationship between multiple conditions is a logical OR
    * so the method returns true at the first occurrence of a fulfilled
    * condition.
    * @param time current time.
    * @return true if any condition is met.
    */
   public boolean check(long time) {
      boolean ok = false;

      for(int i = 0; i < conds.size(); i++) {
         ScheduleCondition cond = conds.elementAt(i);
         // @by mikec, check all condition so that the completion condition
         // will be reset at this check
         if(cond.check(time)) {
            ok = true;
         }
      }

      return ok;
   }

   /**
    * Get the time to run the task. Multiple conditions
    * are Ored together. The earliest condition will trigger the task.
    * The task may not run at this time because of other conditions.
    * For example, if a task is repeatitive and is long running in. Second
    * schedule will not run if the first invocation is still running.
    * @param time current time.
    * @return time to retry the conditions.
    */
   public long getRetryTime(long time) {
      // if disabled, don't run
      if(!isEnabled()) {
         return -1;
      }

      long retry = Long.MAX_VALUE;
      Scheduler scheduler = Scheduler.getScheduler();
      long lastRun = scheduler.getLastScheduledRunTime(getTaskId());

      for(int i = 0; i < conds.size(); i++) {
         ScheduleCondition cond = conds.elementAt(i);
         long r0;

         // if a task is run every two days, and the task just ran. if we use the
         // current time to check, it will be scheduled for tomorrow but it should
         // actually be scheduled two days from last run time
         if(lastRun > 0 && cond instanceof TimeCondition) {
            r0 = ((TimeCondition) cond).getRetryTime(time, lastRun);
         }
         else {
            r0 = cond.getRetryTime(time);
         }

         if(r0 < 0) {
            continue;
         }

         retry = Math.min(r0, retry);
      }

      return (retry == Long.MAX_VALUE) ? -1 : retry;
   }

   /**
    * Determines if this task has a future runtime
    * by accounting for the parent tasks if it has chained conditions.
    * @param time current time.
    * @return <code>true</code> if has a potential next runtime,
    *         <code>false</code> otherwise.
    */
   public boolean hasNextRuntime(long time) {
      for(int i = 0; i < conds.size(); i++) {
         ScheduleCondition cond = conds.elementAt(i);

         if(cond instanceof CompletionCondition) {
            String parentName = ((CompletionCondition) cond).getTaskName();
            ScheduleManager manager = ScheduleManager.getScheduleManager();
            ScheduleTask parent = manager.getScheduleTask(parentName);

            if(parent != null && parent.hasNextRuntime(time)) {
               return true;
            }
         }
         else if(cond.getRetryTime(time) > 0) {
            return true;
         }
      }

      return false;
   }

   /**
    * Add a condition to the task.
    * This will add the condition to the internal Vectors that keep track
    * of conditions associated with a task
    * @param cond The ScheduleCondition we are adding.
    */
   public void addCondition(ScheduleCondition cond) {
      conds.addElement(cond);

      // complete condition is considered second class condition.
      // Tasks containing only complete condition shouldn't be retried
      // on its own. Rather it will be triggered by the depended task
      if(cond instanceof CompletionCondition) {
         dependency.add(((CompletionCondition) cond).getTaskName());
      }
   }

   /**
    * Add an action to the task.
    */
   public void addAction(ScheduleAction action) {
      acts.addElement(action);

      if(action instanceof BatchAction) {
         dependency.add(((BatchAction) action).getTaskId());
      }
   }

   public void addAction(ScheduleAction action, String linkUrl) {
      if(action instanceof AbstractAction) {
         ((AbstractAction) action).setLinkURI(linkUrl);
      }

      addAction(action);
   }

   /**
    * Get the number of conditions.
    */
   public int getConditionCount() {
      return conds.size();
   }

   /**
    * Get the specified condition.
    * @param idx condition index.
    */
   public ScheduleCondition getCondition(int idx) {
      return conds.elementAt(idx);
   }

   /**
    * Set a condition.
    * @param idx condition index.
    * @param cond new condition.
    */
   public void setCondition(int idx, ScheduleCondition cond) {
      ScheduleCondition condition = conds.get(idx);

      if(condition instanceof CompletionCondition) {
         dependency.remove(((CompletionCondition) condition).getTaskName());
      }

      // complete condition is considered second class condition.
      // Tasks containing only complete condition shouldn't be retried
      // on its own. Rather it will be triggered by the depended task
      if(cond instanceof CompletionCondition) {
         dependency.add(((CompletionCondition) cond).getTaskName());
      }

      conds.set(idx, cond);
   }

   public Stream<ScheduleCondition> getConditionStream() {
      return conds.stream();
   }

   /**
    * Remove the specified condition from the task.
    */
   public void removeCondition(int idx) {
      // avoid deleting a condition that has not yet been saved causing the deletion request to fail
      if(idx < 0 || idx >= conds.size()) {
         return;
      }

      ScheduleCondition cond = conds.elementAt(idx);

      if(cond instanceof CompletionCondition) {
         dependency.removeElement(((CompletionCondition) cond).getTaskName());
      }

      conds.removeElementAt(idx);
   }

   /**
    * Get the number of actions.
    */
   public int getActionCount() {
      return acts.size();
   }

   /**
    * Get the specified action.
    * @param idx action index.
    * @return schedule action.
    */
   public ScheduleAction getAction(int idx) {
      return acts.elementAt(idx);
   }

   /**
    * Set the specified action.
    * @param idx action index.
    * @param action new action.
    */
   public void setAction(int idx, ScheduleAction action) {
      ScheduleAction existingAction = acts.get(idx);

      if(existingAction instanceof BatchAction) {
         dependency.remove(((BatchAction) existingAction).getTaskId());
      }

      if(action instanceof BatchAction) {
         dependency.add(((BatchAction) action).getTaskId());
      }

      acts.setElementAt(action, idx);
   }

   /**
    * Remove the specified action.
    */
   public void removeAction(int idx) {
      ScheduleAction action = acts.elementAt(idx);

      if(action instanceof BatchAction) {
         dependency.removeElement(((BatchAction) action).getTaskId());
      }

      acts.removeElementAt(idx);
   }

   public Stream<ScheduleAction> getActionStream() {
      return acts.stream();
   }

   /**
    * Set the complete condition status.
    */
   public void setComplete(String taskname, boolean complete) {
      // if already running(triggered by other conditions),
      // ignore complete condition
      synchronized(this) {
         if(isRunning()) {
            notifyAll();
            return;
         }

         notifyAll();
      }

      for(int i = 0; i < conds.size(); i++) {
         ScheduleCondition cond = conds.elementAt(i);

         if(cond instanceof CompletionCondition) {
            CompletionCondition ccond = (CompletionCondition) cond;

            if(taskname.equals(ccond.getTaskName())) {
               ccond.setComplete(complete);
            }
         }
      }
   }

   /**
    * Get the dependency list.
    */
   public Enumeration<String> getDependency() {
      return dependency.elements();
   }

   /**
    * Determines if this task is currently running.
    *
    * @return <code>true</code> if this task is running.
    */

   public boolean isRunning() {
      return running;
   }

   /**
    * Run the actions in this task.
    * @param principal represents an entity.
    */
   public void run(Principal principal) throws Throwable {
      synchronized(this) {
         if(isRunning()) {
            throw new Exception("Task is still running: " + getTaskId());
         }

         running = true;

         // @by jasons, expeditors: need to clone the task and copy the
         // actions so that runtime parameters set during replet
         // generation are not saved back to schedule.xml

         // @by stephenwebster, for bug1381475823376
         // move logic jasons had in SUtil to here, his comment precedes this one.
         runtimeTask = copyScheduleTask(this);
         runtimeTask.running = true;
      }

      // @by stephenwebster, due to the cloning, we will now delegate the execution
      // of the task to the cloned copy.  Thus, all state information regarding this
      // task will be delegated also to the runtime copy of the scheduled task.
      try {
         runtimeTask.doRun(principal);
      }
      finally {
         runtimeTask = null;
         running = false;
      }
   }

   /**
    * Run the actions in this task.
    * @param principal represents an entity.
    */
   private void doRun(final Principal principal) throws Throwable {
      String prop = SreeEnv.getProperty("schedule.task.listener");
      TaskListener listener = null;

      if(prop != null) {
         try {
            listener = (TaskListener) Class.forName(
               Tool.convertUserClassName(prop)).newInstance();
            listener.taskStarted(this, principal);
         }
         catch(Exception ex) {
            LOG.error("Failed to create and notify task listener " +
               "(schedule.task.listener): " + prop, ex);
         }
      }

      final AtomicInteger counter = new AtomicInteger(acts.size());
      final List<Throwable> exceptions = new ArrayList<>();
      List<Future> futures = new ArrayList<>();
      boolean waited = false;
      long startTime = System.currentTimeMillis();

      for(int i = 0; i < acts.size(); i++) {
         final ScheduleAction act = acts.elementAt(i);

         if(!waited && act instanceof MVAction && ((MVAction) act).isSequenced()) {
            for(Future future : futures) {
               future.get();
            }

            waited = true;
         }

         Runnable r = new ThreadPool.AbstractContextRunnable() {
            { addRecord("ScheduleTask:" + ScheduleTask.this.getTaskId()); }

            @Override
            public void run() {
               try {
                  if(principal instanceof XPrincipal) {
                     ((XPrincipal) principal).setProperty("__TASK_NAME__",
                        ScheduleTask.this.getTaskId());
                  }

                  if(principal != null) {
                     ThreadContext.setContextPrincipal(principal);
                  }

                  String taskId = getTaskId();
                  taskId = SUtil.getTaskNameForLogging(taskId);
                  MDC.put("SCHEDULE_TASK", taskId);
                  act.run(principal);
                  MDC.remove("SCHEDULE_TASK");
               }
               catch(Throwable ex) {
                  exceptions.add(ex);
               }
               finally {
                  synchronized(ScheduleTask.this) {
                     counter.decrementAndGet();
                     ScheduleTask.this.notifyAll();
                  }
               }
            }

            @Override
            public Principal getPrincipal() {
               Principal result = super.getPrincipal();

               if(result == null) {
                  result = principal;
               }

               return result;
            }
         };

         futures.add(getThreadPool().submit(r));
      }

      if(cycleInfo != null && cycleInfo.isStartNotify()) {
         mailStart();
      }

      int threshold = (cycleInfo != null && cycleInfo.isExceedNotify())
         ? cycleInfo.getThreshold() : 0;
      int time = 0;
      long timeout = Long.parseLong(SreeEnv.getProperty("schedule.task.timeout"));

      // only after all the replet actions are over, should the task be over
      synchronized(this) {
         while(counter.get() > 0) {
            if(threshold > 0 && time++ == threshold) {
               mailExceed();
            }

            if(timeout > 0 && System.currentTimeMillis() - startTime > timeout) {
               cancel();
               throw new RuntimeException("Schedule Task Timeout exceeded! Cancelling the task: " +
                                             getTaskId());
            }

            wait(1000);
         }

         // All task actions should be done at this point
         if(listener != null) {
            listener.taskCompleted(this, principal, exceptions);
         }

         if(cycleInfo != null && (cycleInfo.isEndNotify() || cycleInfo.isFailureNotify())) {
            mailCompletion(exceptions, cycleInfo.isEndNotify(), cycleInfo.isFailureNotify());
         }

         try {
            // here we throw the last exception and log the other
            // exceptions, in most cases, the feedback is enough
            for(int i = 0; i < exceptions.size(); i++) {
               if(i == exceptions.size() - 1) {
                  // send task failed mail
                  if(SreeEnv.getBooleanProperty("schedule.options.taskFailed", "true", "CHECKED"))
                  {
                     boolean isMultiTenant = SUtil.isMultiTenant();
                     String subject = Catalog.getCatalog().getString(
                        "em.scheduler.notification.taskFailedSub.community", getName(),
                        (new SimpleDateFormat("hh:mma yyyy-MM-dd"))
                           .format(new Date()));

                     if(isMultiTenant) {
                        subject = Catalog.getCatalog().getString(
                           "em.scheduler.notification.taskFailedSub",
                           SUtil.getTaskNameWithoutOrg(getTaskId()),
                           SUtil.getTaskOrgName(getTaskId(), principal),
                           (new SimpleDateFormat("hh:mma yyyy-MM-dd"))
                              .format(new Date()));
                     }

                     String body = Catalog.getCatalog().getString(
                        "em.scheduler.notification.taskFailedBody",
                        exceptions.get(i));

                     if(SUtil.isCluster()) {
                        String currentNode = Cluster.getInstance().getLocalMember();
                        String hostName = SUtil.computeServerClusterNode(currentNode);

                        if(hostName != null) {
                           body = body + "\n\n. This mail has been sent from " + hostName;
                        }
                     }

                     ScheduleThread.sendEmail(subject, body);
                  }

                  throw exceptions.get(i);
               }
               else {
                  Throwable ex = exceptions.get(i);
                  LOG.warn("Exception occurred during " +
                     "execution of schedule task " +
                     ScheduleTask.this.getTaskId() + ": " + ex.getMessage(),
                     ex);
               }
            }
         }
         finally {
            running = false;
         }
      }
   }

   /**
    * Send an email when cycle strat.
    */
   private void mailStart() {
      Catalog catalog = Catalog.getCatalog();
      String subject = catalog.getString("em.scheduler.cycle.startSubject",
         cycleInfo.getName());
      String body = catalog.getString("em.scheduler.cycle.startObject",
         cycleInfo.getName(),
         (new SimpleDateFormat("hh:mm:ssa yyyy-MM-dd")).format(new Date()));
      Mailer mailer = new Mailer();

      try {
         String addrs = getEmails(cycleInfo.getStartEmail());
         mailer.send(addrs, null, subject, body, null);
      }
      catch(Exception ex) {
         LOG.warn("Failed to set cycle start notification email to " +
            cycleInfo.getStartEmail(), ex);
      }
   }

   /**
    * Send an email when cycle completion.
    */
   private void mailCompletion(final List<Throwable> exceptions, boolean notifyEnd,
                               boolean notifyFailure)
   {
      Catalog catalog = Catalog.getCatalog();
      String subject;
      String body = catalog.getString("em.scheduler.cycle.completionObject",
            cycleInfo.getName(),
            (new SimpleDateFormat("hh:mm:ssa yyyy-MM-dd")).format(new Date()));
      boolean failed = exceptions.size() > 0;
      String email = notifyFailure && failed ? cycleInfo.getFailureEmail()
         : cycleInfo.getEndEmail();

      if(!failed && !notifyEnd || failed && !notifyFailure) {
         return;
      }
      else if(!failed) {
         subject = catalog.getString("em.scheduler.cycle.completionSubject",
                                     cycleInfo.getName());

      }
      else {
         subject = catalog.getString("em.scheduler.cycle.failedSubject",
                                     cycleInfo.getName());

         try {
            StringWriter str = new StringWriter();
            PrintWriter  out = new PrintWriter(str);
            out.append(body);
            out.append("\nFailed Reason:\n");

            for(Throwable ex : exceptions) {
               ex.printStackTrace(out);  //NOSONAR
            }

            out.flush();
            body = str.toString();
            out.close();
         }
         catch(Exception ex) {
            // ignore it
         }
      }

      Mailer mailer = new Mailer();

      try {
         String addrs = getEmails(email);
         mailer.send(addrs, null, subject, body, null);
      }
      catch(Exception ex) {
         LOG.warn("Failed to send cycle end notification email to " +
            cycleInfo.getEndEmail(), ex);
      }
   }

   /**
    * Send an email when cycle exceeds threshold.
    */
   private void mailExceed() {
      Catalog catalog = Catalog.getCatalog();
      String subject = catalog.getString("em.scheduler.cycle.exceedSubject",
                                         cycleInfo.getName());
      String body = catalog.getString("em.scheduler.cycle.exceedObject",
         cycleInfo.getName(), cycleInfo.getThreshold());
      Mailer mailer = new Mailer();

      try {
         String addrs = getEmails(cycleInfo.getExceedEmail());
         mailer.send(addrs, null, subject, body, null);
      }
      catch(Exception ex) {
         LOG.warn("Failed to set cycle threshold exceeded " +
            "notification email to " + cycleInfo.getExceedEmail(), ex);
      }
   }

   /**
    * Get emails address by user.
    */
   private String getEmails(String mails) throws Exception {
      List<String> userEmails = new ArrayList<>();
      String[] toAddrs = Tool.split(mails, ',');

      for(String toAddr : toAddrs) {
         if(!Tool.matchEmail(toAddr)) {
            String[] uemails = SUtil.getEmails(new IdentityID(toAddr, OrganizationManager.getInstance().getCurrentOrgID()));

            if(uemails != null) {
               for(String uemail : uemails) {
                  if(!userEmails.contains(uemail)) {
                     userEmails.add(uemail);
                  }
               }
            }
         }
         else {
            if(!userEmails.contains(toAddr)) {
               userEmails.add(toAddr);
            }
         }
      }

      return Tool.arrayToString(userEmails.toArray());
   }

   @Override
   public String toString() {
      return name;
   }

   /**
    * Set the owner of this task.
    */
   public void setOwner(IdentityID owner) {
      this.owner = owner;
      id = null;
   }

   /**
    * Get the owner name of this task.
    * @return the owner of this task, it may be null.
    */
   public IdentityID getOwner() {
      return owner;
   }

   /**
    * Set the start date of this task.
    * @param startDate the start date of this task.
    */
   public void setStartDate(Date startDate) {
      this.startDate = startDate;
   }

   /**
    * Get the start date of this task.
    * @return the start date of this task.
    */
   public Date getStartDate() {
      return startDate;
   }

   /**
    * Set the stop date of this task.
    * @param endDate the stop date of this task.
    */
   public void setEndDate(Date endDate) {
      this.endDate = endDate;
   }

   /**
    * Get the stop date of this task.
    * @return the stop date of this task.
    */
   public Date getEndDate() {
      return endDate;
   }

   /**
    * Get the locale used by this task.
    * @return Locale used by this task.
    */
   public String getLocale() {
      return locale;
   }

   /**
    * Set the locale to be used by this task.
    * @param locale to be used by this task.
    */
   public void setLocale(String locale) {
      this.locale = locale;
   }

   /**
    * Get the user to run this task.
    * @deprecated
    * @return the user to run this task.
    */
   @Deprecated
   public String getUser() {
      if(getIdentity() instanceof User) {
         return getIdentity().getName();
      }

      return null;
   }

   /**
    * Set the user to run this task.
    * @deprecated
    * @param user the user to run this task.
    */
   @Deprecated
   public void setUser(IdentityID user) {
      setIdentity(new User(user));
   }

   /**
    * Get the identity to run this task.
    * @return the identity to run this task.
    */
   public Identity getIdentity() {
      return identity;
   }

   /**
    * Set the identity(user, group) to run this task.
    * @param identity the identity to run this task.
    */
   public void setIdentity(Identity identity) {
      this.identity = identity;
   }

   /**
    * Get the task description.
    * @return the description of this task.
    */
   public String getDescription() {
      return description;
   }

   /**
    * Set the task description.
    * @param description the description.
    */
   public void setDescription(String description) {
      this.description = description;
   }

   /**
    * Set the cycle info.
    */
   public void setCycleInfo(CycleInfo cycleInfo) {
      this.cycleInfo = cycleInfo;
   }

   /**
    * Get the cycle info.
    */
   public CycleInfo getCycleInfo() {
      return cycleInfo;
   }

   /**
    * Get the task Id.
    */
   public String getTaskId() {
      if(id == null) {
         if(type == Type.CYCLE_TASK) {
            id = owner.convertToKey() + "__" + name;
         }
         else if(type == Type.INTERNAL_TASK) {
            id = name;
         }
         else {
            id = owner == null ? name : ScheduleManager.getTaskId(owner.convertToKey(), name);
         }
      }

      return id;
   }

   /**
    * Get the task label to display.
    *
    * @param containsOwner whether label contains owner.
    */
   public String toView(boolean containsOwner) {
      if(!containsOwner || owner == null) {
         return name;
      }

      return owner.name + ":" + name;
   }

   /**
    * Get the task label to display.
    *
    * @param containsOwner whether label contains owner.
    * @param useAlias if should pass user alias when possible.
    */
   public String toView(boolean containsOwner, boolean useAlias) {
      if(!containsOwner || owner == null) {
         return name;
      }

      if(containsOwner && useAlias) {
         User u = SecurityEngine.getSecurity().getSecurityProvider().getUser(owner);
         String alias = u != null && u.getAlias() != null && !u.getAlias().isEmpty() ? u.getAlias() : owner.name;

         return alias + ":" + name;
      }

      return owner.name + ":" + name;
   }

   /**
    * Two tasks are equal if they have same names.
    */
   public boolean equals(Object val) {
      if(!(val instanceof ScheduleTask)) {
         LOG.debug("Not a task: " + val);
         return false;
      }

      ScheduleTask st = (ScheduleTask) val;

      if(!Tool.equals(st.getName(), name)) {
         return false;
      }

      if(!Tool.equals(st.getLocale(), locale) ||
         !Tool.equals(st.getIdentity(), identity) ||
         !Tool.equals(st.getDescription(), getDescription()) ||
         !Tool.equals(st.getTimeZone(), getTimeZone()) ||
         !Tool.equals(st.getOwner(), owner) || st.isEnabled() != enabled ||
         st.isEditable() != editable || st.isRemovable() != removable ||
         st.isDeleteIfNoMoreRun() != delNotRun ||
         st.isDurable() != durable ||
         !Tool.equals(st.getStartDate(), startDate) ||
         !Tool.equals(st.getEndDate(), endDate))
      {
         LOG.debug(String.format(
            "Task compare, Name/Owner/Enabled do not match: [%s,%s,%b]",
            st.getName(), st.getOwner(), st.isEnabled()));
         return false;
      }

      HashSet<Integer> matched = new HashSet<>();

      for(int i = 0; i < st.getConditionCount(); i++) {
         ScheduleCondition cond = st.getCondition(i);
         boolean foundMatch = false;

         for(int j = 0; j < conds.size(); j++) {
            // each condition can be used to match once
            if(matched.contains(j)) {
               continue;
            }

            if(conds.elementAt(j).equals(cond)) {
               foundMatch = true;
               matched.add(j);
               break;
            }
         }

         if(!foundMatch) {
            LOG.debug("Task compare:No Condition matched.");
            return false;
         }
      }

      if(matched.size() != conds.size()) {
         LOG.debug("Task compare:Condition item count not matched.");
         return false;
      }

      matched = new HashSet<>();

      for(int i = 0; i < st.getActionCount(); i++) {
         ScheduleAction act = st.getAction(i);
         boolean foundMatch = false;

         for(int j = 0; j < acts.size(); j++) {
            // each action can be used to match once
            if(matched.contains(j)) {
               continue;
            }

            if(acts.elementAt(j).equals(act)) {
               foundMatch = true;
               matched.add(j);
               break;
            }
         }

         if(!foundMatch) {
            LOG.debug("Task compare:No Action matched.");
            return false;
         }
      }

      if(matched.size() != acts.size()) {
         LOG.debug("Task compare:Action item count not matched.");
         return false;
      }

      return true;
   }

   /**
    * Clone the object.
    */
   @Override
   @SuppressWarnings("unchecked")
   public ScheduleTask clone() {
      ScheduleTask st = null;

      try {
         st = (ScheduleTask) super.clone();
         st.conds = (Vector<ScheduleCondition>) conds.clone();
         st.acts = (Vector<ScheduleAction>) acts.clone();
         st.dependency = (Vector<String>) dependency.clone();
         st.startDate = startDate == null ? null : (Date) startDate.clone();
         st.endDate = endDate == null ? null : (Date) endDate.clone();
         st.timeZone = timeZone;
      }
      catch(Exception e) {
         LOG.error("Failed to clone schedule task", e);
      }

      return st;
   }

   /**
    * Copy to another task.
    */
   public void copyTo(ScheduleTask task) {
      try {
         task.conds = conds;
         task.acts = acts;
         task.dependency = dependency;
         task.name = name;
         task.owner = owner;
         task.locale = locale;
         task.description = description;
         task.timeZone = timeZone;
         task.enabled = enabled;
         task.editable = editable;
         task.removable = removable;
         task.delNotRun = delNotRun;
         task.durable = durable;
         task.startDate = startDate;
         task.endDate = endDate;
      }
      catch(Exception e) {
         LOG.error("Failed to copy schedule task", e);
      }
   }

   /**
    * Cancel the task.
    */
   public void cancel() {
      // @by stephenwebster for bug1381475823376
      // delegate cancel command when task is running.
      if(runtimeTask == null && running) {
         int cnt = getActionCount();

         for(int i = 0; i < cnt; i++) {
            ScheduleAction action = getAction(i);

            if(action instanceof CancelableAction) {
               ((CancelableAction) action).cancel();
            }
         }

         running = false;
      }
      else if(runtimeTask != null && runtimeTask.running) {
         runtimeTask.cancel();
      }
   }

   /**
    * Creates a deep copy for this task. Any modifications made to the actions
    * of the copy will be transient. For example, parameters added to the
    * request of a report action during the execution of the replet will not be
    * saved back to the schedule.xml file.
    *
    * @param task the task to copy.
    *
    * @return the duplicate task.
    */
   protected static ScheduleTask copyScheduleTask(ScheduleTask task) throws Exception {
      task = (ScheduleTask) task.clone();

      for(int j = 0; j < task.getActionCount(); j++) {
         task.setAction(j, copyScheduleAction(task.getAction(j)));
      }

      return task;
   }

   /**
    * Creates a copy of schedule action.
    *
    * @param action the action to copy.
    *
    * @return the new action instance.
    */
   private static ScheduleAction copyScheduleAction(ScheduleAction action) throws Exception {
      StringWriter buffer = new StringWriter();
      PrintWriter writer = new PrintWriter(buffer);
      writeScheduleAction(action, writer);
      writer.close();
      String xml = buffer.toString();

      if(!xml.isEmpty()) {
         try {
            Document document = Tool.parseXML(new StringReader(buffer.toString()));
            Element element = document.getDocumentElement();
            ScheduleAction copy = parseScheduleAction(element);

            if(copy != null) {

               if(copy instanceof ViewsheetAction) {
                  ViewsheetAction viewsheetAction = (ViewsheetAction) copy;
                  RepletRequest request = viewsheetAction.getViewsheetRequest();
                  request.setParameter("__is_scheduler__", true);
               }

               return copy;
            }
         }
         catch(Throwable exc) {
            LOG.warn("Failed to copy schedule action", exc);

            throw exc;
         }
      }

      return action;
   }

   @Override
   public void writeXML(PrintWriter writer) {
      writer.printf("<Task name=\"%s\"", Tool.escape(name));
      writer.printf(" owner=\"%s\"", Tool.escape(owner.convertToKey()));
      writer.printf(" enabled=\"%s\"", enabled);
      writer.printf(" editable=\"%s\"", editable);
      writer.printf(" removable=\"%s\"", removable);
      writer.printf(" delNotRun=\"%s\"", delNotRun);
      writer.printf(" durable=\"%s\"", durable);
      writer.printf(" path=\"%s\"", Tool.escape(path));
      writer.printf(" lastModified=\"%d\"", lastModified);
      writer.printf(" type=\"%s\"", type.name());

      if(startDate != null) {
         writer.print(" startDate=\"" + startDate.getTime() +
            "\"");
      }

      if(endDate != null) {
         writer.print(" endDate=\"" + endDate.getTime() + "\"");
      }

      if(locale != null) {
         writer.print(" locale=\"" + locale +"\"");
      }

      if(getIdentity() instanceof User) {
         writer.print(" user=\"" + Tool.escape(getIdentity().getIdentityID().convertToKey()) + "\"");
      }

      if(identity != null) {
         writer.print(" idname=\"" + Tool.escape(identity.getIdentityID().convertToKey()) + "\"");
         writer.print(" idtype=\"" + identity.getType() + "\"");
      }

      if(description != null) {
         writer.print(" description=\"" + Tool.escape(description) + "\"");
      }

      if(timeZone != null) {
         writer.print(" timeZone=\"" + Tool.escape(timeZone) + "\"");
      }

      writer.println(">");

      int cnt = getConditionCount();

      for(int i = 0; i < cnt; i++) {
         ScheduleCondition cond = getCondition(i);

         if(cond instanceof TimeCondition) {
            ((TimeCondition) cond).writeXML(writer);
         }
         else if(cond instanceof CompletionCondition) {
            ((CompletionCondition) cond).writeXML(writer);
         }
         else if(cond instanceof TaskBalancerCondition) {
            writer.println(
               "<Condition type=\"TaskBalancer\" " +
               "class=\"inetsoft.sree.schedule.TaskBalancerCondition\"/>");
         }
         else if(cond instanceof NeverRunCondition) {
            writer.println(
               "<Condition type=\"NeverRun\" " +
                  "class=\"inetsoft.sree.schedule.NeverRunCondition\"/>");
         }
         else {
            writer.println("<Condition type=\"User\" class=\"" +
               cond.getClass().getName() + "\"/>");
         }
      }

      cnt = getActionCount();

      for(int i = 0; i < cnt; i++) {
         writeScheduleAction(getAction(i), writer);
      }

      writer.println("</Task>");
   }

   @Override
   public void parseXML(Element elem) throws Exception {
      name = elem.getAttribute("name");
      owner = IdentityID.getIdentityIDFromKey(elem.getAttribute("owner"));
      path = elem.getAttribute("path");
      path = Tool.isEmptyString(path) ? "/" : path;
      // backward compatibility, null is administrator
      owner = Tool.equals("null", owner.name) ?
         new IdentityID(XPrincipal.SYSTEM, Organization.getDefaultOrganizationID()) : owner;
      enabled = "true".equals(elem.getAttribute("enabled"));
      // removable and editable will be missing if the xml is from a previous version.
      final String removableStr = Tool.getAttribute(elem, "removable");

      if(removableStr != null) {
         removable = "true".equals(removableStr);
      }

      final String editableStr = Tool.getAttribute(elem, "editable");

      if(editableStr != null) {
         editable = "true".equals(editableStr);
      }

      delNotRun = "true".equals(elem.getAttribute("delNotRun"));
      durable = "true".equals(elem.getAttribute("durable"));

      String lastModifiedStr = Tool.getAttribute(elem, "lastModified");

      if(lastModifiedStr != null) {
         lastModified = Long.parseLong(lastModifiedStr);
      }

      String startDateStr = Tool.getAttribute(elem, "startDate");

      if(startDateStr != null) {
         startDate = new Date(Long.parseLong(startDateStr));
      }

      String endDateStr = Tool.getAttribute(elem, "endDate");

      if(endDateStr != null) {
         endDate = new Date(Long.parseLong(endDateStr));
      }

      locale = Tool.getAttribute(elem, "locale");
      description = Tool.getAttribute(elem, "description");
      timeZone = Tool.getAttribute(elem, "timeZone");

      IdentityID idname = IdentityID.getIdentityIDFromKey(Tool.getAttribute(elem, "idname"));
      int idtype = 0;

      try {
         idtype = Integer.parseInt(Tool.getAttribute(elem, "idtype"));
      }
      catch(Exception ignore) {
      }

      try {
         identity = SUtil.getIdentity(idname, idtype);
      }
      catch(Exception exp) {
         LOG.error("Failed to set owner of task " + name + " to " + idname, exp);
      }

      String taskType = elem.getAttribute("type");

      if(!Tool.isEmptyString(taskType)) {
         type = Type.valueOf(elem.getAttribute("type"));
      }


      NodeList conds = Tool.getChildNodesByTagName(elem, "Condition");

      if(conds.getLength() == 0) {
         LOG.warn("No condition in task, ignored: " + name);
         return;
      }

      for(int j = 0; j < conds.getLength(); j++) {
         Element cond = (Element) conds.item(j);
         String type = cond.getAttribute("type");

         if(type == null) {
            throw new IOException("Condition type missing in task: " + name);
         }

         if(type.equals("TimeCondition")) {
            TimeCondition condition = new TimeCondition();
            condition.parseXML(cond);
            addCondition(condition);
         }
         else if(type.equals("Completion")) {
            CompletionCondition condition = new CompletionCondition();
            condition.parseXML(cond);
            addCondition(condition);
         }
         else if(type.equals("TaskBalancer")) {
            addCondition(new TaskBalancerCondition());
         }
         else if("NeverRun".equals(type)) {
            addCondition(new NeverRunCondition());
         }
         else {
            throw new IOException("Unknown condition type: " + type);
         }
      }

      NodeList actions = Tool.getChildNodesByTagName(elem, "Action");

      for(int j = 0; j < actions.getLength(); j++) {
         try {
            ScheduleAction action =
               parseScheduleAction((Element) actions.item(j));

            if(action instanceof MVAction) {
               editable = false;
            }

            if(action != null) {
               addAction(action);
            }
         }
         catch(Throwable exc) {
            LOG.error("Failed to parse schedule action from task '{}'", name, exc);
         }
      }
   }

   /**
    * Writes the XML representation of a schedule action.
    *
    * @param action the action to write.
    * @param writer the print writer used to write the XML.
    */
   private static void writeScheduleAction(ScheduleAction action,
                                           PrintWriter writer)
   {
      if(action instanceof AbstractAction) {
         ((AbstractAction) action).writeXML(writer);
      }
      else if(action instanceof MVAction) {
         ((MVAction) action).writeXML(writer, true);
      }
      else if(action instanceof IndividualAssetBackupAction) {
         ((IndividualAssetBackupAction) action).writeXML(writer);
      }
      else if(action instanceof TaskBalancerAction) {
         writer.println(
            "<Action type=\"TaskBalancer\" class=\"inetsoft.sree.schedule.TaskBalancerAction\"/>");
      }
      else if(action instanceof AssetFileBackupAction) {
         writer.println(
            "<Action type=\"AssetFileBackup\" class=\"inetsoft.sree.schedule.AssetFileBackupAction\"/>");
      }
      else if(action instanceof UpdateAssetsDependenciesAction) {
         writer.print("<Action type=\"AssetsDependencies\" ");
         writer.println("class=\"inetsoft.sree.schedule.UpdateAssetsDependenciesAction\"/>");
      }
   }

   /**
    * Parses an XML representation of a schedule action.
    *
    * @param action the XML element to parse.
    *
    * @return the parsed action.
    *
    * @throws Exception if the action could not be parsed.
    */
   private static ScheduleAction parseScheduleAction(Element action)
      throws Exception
   {
      String type = action.getAttribute("type");

      if(type == null) {
         throw new IOException("Action type missing.");
      }

      AbstractAction action0 = null;

      if(type.equals("Viewsheet")) {
         action0 = new ViewsheetAction();
      }
      else if(type.equals("Backup")) {
         IndividualAssetBackupAction backupAction = new IndividualAssetBackupAction();
         backupAction.parseXML(action);
         return backupAction;
      }
      else if(type.equals("MV")) {
         MVAction actobj = new MVAction();
         actobj.parseXML(action);
         return actobj;
      }
      else if(type.equals("TaskBalancer")) {
         return new TaskBalancerAction();
      }
      else if(type.equals("AssetFileBackup")) {
         return new AssetFileBackupAction();
      }
      else if("AssetsDependencies".equals(type)) {
         return new UpdateAssetsDependenciesAction();
      }
      else if(type.equals("Batch")) {
         BatchAction batchAction = new BatchAction();
         batchAction.parseXML(action);
         return batchAction;
      }

      if(action0 != null) {
         action0.setEncoding(true);
         action0.parseXML(action);
         action0.setEncoding(false);
      }

      return action0;
   }

   /**
    * Used during application shutdown to cleanup thread pool in a timely manner
    */
   public static void shutdownThreadPool() {
      if(threadPool != null) {
         threadPool.shutdownNow();
      }
   }

   // execution pool for actions
   private static ExecutorService getThreadPool() {
      if(threadPool != null) {
         return threadPool;
      }

      synchronized(ScheduleTask.class) {
         if(threadPool != null) {
            return threadPool;
         }

         // default 3 threads per cpu
         LicenseManager licenseManager = LicenseManager.getInstance();
         int hcount = 0;
         int scount = licenseManager.getAvailableCpuCount() * 3;
         String val = SreeEnv.getProperty(
            "scheduleTask.thread.count", (scount * 2) + "");

         try {
            hcount = Integer.parseInt(val);
         }
         catch(Exception ex) {
            LOG.warn("Invalid integer value for schedule " +
                        "thread count property (scheduleTask.thread.count): " +
                        SreeEnv.getProperty("scheduleTask.thread.count") +
                        ", using default", ex);
         }

         threadPool = Executors.newFixedThreadPool(hcount, new GroupedThreadFactory());
         LOG.debug("Max number of ScheduleTask processsor: " + hcount);
      }

      return threadPool;
   }

   public void renameDependency(String oldName, String newName) {
      int index = dependency.indexOf(oldName);
      if(index >= 0) {
         dependency.set(index, newName);
      }
   }

   private static ExecutorService threadPool;

   private static final class GroupedThreadFactory implements ThreadFactory {
      @Override
      public Thread newThread(Runnable r) {
         GroupedThread thread = new GroupedThread(r);
         thread.setDaemon(true);
         return thread;
      }
   }

   public String getTimeZone() {
      return timeZone;
   }

   /**
    * Sets the condition time zone using the string ID.
    *
    * @param timeZone the time zone's string ID.
    *
    * @since 2023
    */
   public void setTimeZone(String timeZone) {
      this.timeZone = timeZone;
   }

   public enum Type {
      NORMAL_TASK,
      MV_TASK,
      CYCLE_TASK,
      INTERNAL_TASK
   }

   private Vector<ScheduleCondition> conds = new Vector<>();
   private Vector<ScheduleAction> acts = new Vector<>();
   private String name;
   private IdentityID owner;
   private String locale;
   private String path;
   private String description;
   private String timeZone;
   private long lastModified;
   private boolean enabled = true;
   private boolean editable = true;
   private boolean removable = true;
   private boolean delNotRun = false;
   private boolean durable = false;
   private Date startDate, endDate;
   private Vector<String> dependency = new Vector<>();
   private boolean running = false;
   private Identity identity = null;
   private CycleInfo cycleInfo;
   private ScheduleTask runtimeTask;
   private Type type = Type.NORMAL_TASK;
   private transient String id;

   private static final Logger LOG = LoggerFactory.getLogger(ScheduleTask.class);
}
