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
package inetsoft.sree.schedule.jobstore;

import inetsoft.sree.internal.SUtil;
import inetsoft.sree.internal.cluster.*;
import inetsoft.sree.schedule.ScheduleManager;
import inetsoft.sree.schedule.ScheduleTask;
import inetsoft.sree.schedule.cloudrunner.ScheduleTaskCloudJob;
import inetsoft.util.ThreadContext;
import inetsoft.util.Tool;
import org.quartz.Calendar;
import org.quartz.*;
import org.quartz.impl.matchers.GroupMatcher;
import org.quartz.impl.matchers.StringMatcher;
import org.quartz.spi.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Serializable;
import java.security.Principal;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.function.LongSupplier;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static inetsoft.sree.schedule.jobstore.TriggerState.*;
import static inetsoft.sree.schedule.jobstore.TriggerWrapper.newOwnedTriggerWrapper;
import static inetsoft.sree.schedule.jobstore.TriggerWrapper.newTriggerWrapper;

public class ClusterJobStore implements JobStore, Serializable {
   @Override
   public void initialize(ClassLoadHelper loadHelper, SchedulerSignaler signaler)
      throws SchedulerConfigException
   {
      LOG.debug("Initializing Cluster Job Store..");

      this.schedSignaler = signaler;

      // initializing Cluster maps - use replicated maps so all nodes have local copies
      cluster = Cluster.getInstance();
      LOG.debug("Initializing Cluster maps...");
      jobsByKey = cluster.getReplicatedMap("jobstore.jobsByKey");
      triggersByKey = cluster.getReplicatedMap("jobstore.triggersByKey");
      jobsByGroup = cluster.getReplicatedMultiMap("jobstore.jobsByGroup");
      triggersByGroup = cluster.getReplicatedMultiMap("jobstore.triggersByGroup");
      triggersByJob = cluster.getReplicatedMultiMap("jobstore.triggersByJob");
      pausedTriggerGroups = cluster.getSet("jobstore.pausedTriggerGroups");
      pausedJobGroups = cluster.getSet("jobstore.pausedJobGroups");
      calendarsByName = cluster.getReplicatedMap("jobstore.calendarsByName");
      runningJobs = cluster.getReplicatedMap("jobstore.runningJobs");

      // only shutdown the cluster when running in a separate process
      shutdownClusterOnShutdown = "true".equals(System.getProperty("ScheduleServer"));
      LOG.debug("Cluster Job Store Initialized.");
   }

   @Override
   public void schedulerStarted()
      throws SchedulerException {

      LOG.info("Cluster Job Store started successfully");
      schedulerRunning = true;
   }

   @Override
   public void schedulerPaused() {
      schedulerRunning = false;
   }

   @Override
   public void schedulerResumed() {
      schedulerRunning = true;
   }

   @Override
   public void shutdown() {
      releaseOwnAcquiredTriggers();

      if(shutdownClusterOnShutdown) {
         try {
            Cluster.getInstance().close();
         }
         catch(Exception ex) {
            LOG.debug("Failed to shut down cluster instance", ex);
         }
      }
   }

   /**
    * Releases the triggers this store acquired but did not fire, so that another node can fire
    * them. Quartz does not release them when the scheduler shuts down.
    */
   private void releaseOwnAcquiredTriggers() {
      try {
         for(TriggerWrapper tw : triggersByKey.values()) {
            String fireInstanceId = tw.trigger.getFireInstanceId();

            if(tw.getState() != ACQUIRED || fireInstanceId == null ||
               !fireInstanceId.startsWith(fireInstanceIdPrefix))
            {
               continue;
            }

            try {
               inTransaction(RELEASE_ON_SHUTDOWN_TIMEOUT, () -> {
                  TriggerWrapper current = triggersByKey.getForUpdate(tw.key);

                  if(current != null && isAcquiredBy(current, tw.trigger)) {
                     storeTriggerWrapper(newTriggerWrapper(current, WAITING));
                  }

                  return null;
               });
            }
            catch(DistributedTransactionException ex) {
               LOG.warn("Failed to release trigger {}", tw.key, ex);
            }
         }
      }
      catch(RuntimeException ex) {
         LOG.warn("Failed to release the acquired triggers on shutdown", ex);
      }
   }

   @Override
   public boolean supportsPersistence() {
      return true;
   }

   @Override
   public long getEstimatedTimeToReleaseAndAcquireTrigger() {
      return 25;
   }

   @Override
   public boolean isClustered() {
      return true;
   }

   @Override
   public void storeJobAndTrigger(final JobDetail newJob,
                                  final OperableTrigger newTrigger)
      throws JobPersistenceException
   {
      storeJob(newJob, false);
      storeTrigger(newTrigger, false);
   }

   @Override
   public void storeJob(final JobDetail job, boolean replaceExisting)
      throws JobPersistenceException
   {
      final JobDetail newJob = (JobDetail) job.clone();
      final JobKey newJobKey = newJob.getKey();

      if(jobsByKey.containsKey(newJobKey) && !replaceExisting) {
         throw new ObjectAlreadyExistsException(newJob);
      }

      setPrincipal(job);

      inTransaction(TX_TIMEOUT, () -> {
         jobsByKey.getForUpdate(newJobKey);
         jobsByKey.set(newJobKey, newJob);
         jobsByGroup.put(newJobKey.getGroup(), newJobKey);
         return null;
      });
   }

   @Override
   public void storeJobsAndTriggers(
      final Map<JobDetail, Set<? extends Trigger>> triggersAndJobs, boolean replace)
      throws JobPersistenceException
   {
      if(!replace) {
         // validate if anything already exists
         for(final Map.Entry<JobDetail, Set<? extends Trigger>> e : triggersAndJobs
            .entrySet())
         {
            final JobDetail jobDetail = e.getKey();

            if(checkExists(jobDetail.getKey())) {
               throw new ObjectAlreadyExistsException(jobDetail);
            }

            for(final Trigger trigger : e.getValue()) {
               if(checkExists(trigger.getKey())) {
                  throw new ObjectAlreadyExistsException(trigger);
               }
            }
         }
      }
      // do bulk add...
      for(final Map.Entry<JobDetail, Set<? extends Trigger>> e : triggersAndJobs
         .entrySet()) {
         storeJob(e.getKey(), true);
         for(final Trigger trigger : e.getValue()) {
            storeTrigger((OperableTrigger) trigger, true,
               !Tool.equals(e.getKey().getKey(), trigger.getJobKey()));
         }
      }
   }

   @Override
   public boolean removeJob(final JobKey jobKey)
      throws JobPersistenceException
   {
      boolean removed = false;

      if(jobsByKey.containsKey(jobKey)) {
         final List<OperableTrigger> triggersForJob = getTriggersForJob(jobKey);

         for(final OperableTrigger trigger : triggersForJob) {
            if(!removeTrigger(trigger.getKey(), false)) {
               LOG.warn("Error deleting trigger [{}] of job [{}] .", trigger, jobKey);
               return false;
            }
         }

         removed = inTransaction(TX_TIMEOUT, () -> {
            jobsByKey.getForUpdate(jobKey);
            jobsByGroup.remove(jobKey.getGroup(), jobKey);
            return jobsByKey.remove(jobKey) != null;
         });
      }

      return removed;
   }

   @Override
   public boolean removeJobs(final List<JobKey> jobKeys) throws JobPersistenceException {
      boolean allRemoved = true;

      for(final JobKey key : jobKeys) {
         allRemoved = removeJob(key) && allRemoved;
      }

      return allRemoved;
   }

   @Override
   public JobDetail retrieveJob(final JobKey jobKey) {
      return jobKey != null && jobsByKey.containsKey(jobKey)
         ? (JobDetail) jobsByKey.get(jobKey).clone() : null;
   }

   @Override
   public void storeTrigger(OperableTrigger trigger, boolean replaceExisting)
      throws JobPersistenceException
   {
      storeTrigger(trigger, replaceExisting, true);
   }

   private void storeTrigger(OperableTrigger trigger, boolean replaceExisting, boolean checkJobExist)
      throws JobPersistenceException
   {
      final OperableTrigger newTrigger = (OperableTrigger) trigger.clone();
      final TriggerKey triggerKey = newTrigger.getKey();

      inTransaction(TX_TIMEOUT, () -> {
         boolean containsKey = triggersByKey.getForUpdate(triggerKey) != null;

         if(containsKey && !replaceExisting) {
            throw new ObjectAlreadyExistsException(newTrigger);
         }

         if(retrieveJob(newTrigger.getJobKey()) == null && checkJobExist) {
            throw new JobPersistenceException("The job (" + newTrigger.getJobKey()
                                                 + ") referenced by the trigger does not exist.");
         }

         boolean shouldBePaused = pausedJobGroups.contains(
            newTrigger.getJobKey().getGroup()) ||
            pausedTriggerGroups.contains(triggerKey.getGroup());
         final TriggerState state = shouldBePaused ? PAUSED : NORMAL;

         final TriggerWrapper newTriggerWrapper = newTriggerWrapper(newTrigger, state);
         triggersByKey.set(newTriggerWrapper.key, newTriggerWrapper);
         triggersByGroup.put(triggerKey.getGroup(), triggerKey);
         triggersByJob.put(newTriggerWrapper.jobKey, triggerKey);
         return null;
      });
   }

   @Override
   public boolean removeTrigger(TriggerKey triggerKey) throws JobPersistenceException {
      return removeTrigger(triggerKey, true);
   }

   @Override
   public boolean removeTriggers(final List<TriggerKey> triggerKeys)
      throws JobPersistenceException
   {
      boolean allRemoved = true;

      for(TriggerKey key : triggerKeys) {
         allRemoved = removeTrigger(key) && allRemoved;
      }

      return allRemoved;
   }

   @Override
   public boolean replaceTrigger(final TriggerKey triggerKey,
                                 final OperableTrigger newTrigger)
      throws JobPersistenceException
   {
      newTrigger.setKey(triggerKey);
      storeTrigger(newTrigger, true);
      return true;
   }

   @Override
   public OperableTrigger retrieveTrigger(final TriggerKey triggerKey)
      throws JobPersistenceException
   {
      return triggerKey != null && triggersByKey.containsKey(triggerKey)
         ? (OperableTrigger) triggersByKey.get(triggerKey).getTrigger().clone()
         : null;
   }

   @Override
   public boolean checkExists(JobKey jobKey) throws JobPersistenceException {
      return jobsByKey.containsKey(jobKey);
   }

   @Override
   public boolean checkExists(TriggerKey triggerKey) throws JobPersistenceException {
      return triggersByKey.containsKey(triggerKey);
   }

   @Override
   public void clearAllSchedulingData() throws JobPersistenceException {
      jobsByKey.clear();
      triggersByKey.clear();
      jobsByGroup.clear();
      triggersByGroup.clear();
      triggersByJob.clear();
      calendarsByName.clear();
      pausedTriggerGroups.clear();
      pausedJobGroups.clear();
      runningJobs.clear();
   }

   @Override
   public void storeCalendar(final String calName, final Calendar cal,
                             boolean replaceExisting, boolean updateTriggers)
      throws JobPersistenceException
   {
      final Calendar calendar = (Calendar) cal.clone();
      if(calendarsByName.containsKey(calName) && !replaceExisting) {
         throw new ObjectAlreadyExistsException("Calendar with name '" + calName
                                                   + "' already exists.");
      }
      else {
         calendarsByName.set(calName, calendar);
      }
   }

   @Override
   public boolean removeCalendar(String calName) throws JobPersistenceException {
      int numRefs = 0;

      for(TriggerWrapper trigger : triggersByKey.values()) {
         OperableTrigger trigg = trigger.trigger;

         if(trigg.getCalendarName() != null && trigg.getCalendarName().equals(calName)) {
            numRefs++;
         }
      }

      if(numRefs > 0) {
         throw new JobPersistenceException(
            "Calender cannot be removed if it referenced by a Trigger!");
      }

      return (calendarsByName.remove(calName) != null);
   }

   @Override
   public Calendar retrieveCalendar(String calName) throws JobPersistenceException {
      return calendarsByName.get(calName);
   }

   @Override
   public int getNumberOfJobs() {
      return jobsByKey.size();
   }

   @Override
   public int getNumberOfTriggers() throws JobPersistenceException {
      return triggersByKey.size();
   }

   @Override
   public int getNumberOfCalendars() throws JobPersistenceException {
      return calendarsByName.size();
   }

   @Override
   public Set<JobKey> getJobKeys(final GroupMatcher<JobKey> matcher)
      throws JobPersistenceException
   {
      Set<JobKey> outList = null;
      final StringMatcher.StringOperatorName operator = matcher
         .getCompareWithOperator();
      final String groupNameCompareValue = matcher.getCompareToValue();

      switch(operator) {
      case EQUALS:
         final Collection<JobKey> jobKeys = jobsByGroup.get(groupNameCompareValue);
         if(jobKeys != null) {
            outList = new HashSet<>();
            for(JobKey jobKey : jobKeys) {
               if(jobKey != null) {
                  outList.add(jobKey);
               }
            }
         }
         break;
      default:
         for(String groupName : jobsByGroup.keySet()) {
            if(operator.evaluate(groupName, groupNameCompareValue)) {
               if(outList == null) {
                  outList = new HashSet<>();
               }
               for(JobKey jobKey : jobsByGroup.get(groupName)) {
                  if(jobKey != null) {
                     outList.add(jobKey);
                  }
               }
            }
         }
      }
      return outList == null ? Collections.emptySet() : outList;
   }

   @Override
   public Set<TriggerKey> getTriggerKeys(GroupMatcher<TriggerKey> matcher)
      throws JobPersistenceException
   {
      Set<TriggerKey> outList = null;
      StringMatcher.StringOperatorName operator = matcher
         .getCompareWithOperator();
      String groupNameCompareValue = matcher.getCompareToValue();

      switch(operator) {
      case EQUALS:
         Collection<TriggerKey> triggerKeys = triggersByGroup.get(groupNameCompareValue);

         if(triggerKeys != null) {
            outList = new HashSet<>();

            for(TriggerKey triggerKey : triggerKeys) {
               if(triggerKey != null) {
                  outList.add(triggerKey);
               }
            }
         }
         break;
      default:
         for(String groupName : triggersByGroup.keySet()) {
            if(operator.evaluate(groupName, groupNameCompareValue)) {
               if(outList == null) {
                  outList = new HashSet<>();
               }
               for(TriggerKey triggerKey : triggersByGroup.get(groupName)) {
                  if(triggerKey != null) {
                     outList.add(triggerKey);
                  }
               }
            }
         }
      }

      return outList == null ? Collections.emptySet() : outList;
   }

   @Override
   public List<String> getJobGroupNames() {
      return new ArrayList<>(jobsByGroup.keySet());
   }

   @Override
   public List<String> getTriggerGroupNames() throws JobPersistenceException {
      return new ArrayList<>(triggersByGroup.keySet());
   }

   @Override
   public List<String> getCalendarNames() throws JobPersistenceException {
      return new ArrayList<>(calendarsByName.keySet());
   }

   @Override
   public List<OperableTrigger> getTriggersForJob(final JobKey jobKey)
      throws JobPersistenceException
   {
      if(jobKey == null) {
         return Collections.emptyList();
      }

      return getTriggerWrappersForJob(jobKey).stream().map(TriggerWrapper::getTrigger)
         .collect(Collectors.toList());
   }

   @Override
   public void pauseTrigger(TriggerKey triggerKey) throws JobPersistenceException {
      inTransaction(TX_TIMEOUT, () -> {
         TriggerWrapper newTrigger =
            newTriggerWrapper(triggersByKey.getForUpdate(triggerKey), PAUSED);
         triggersByKey.set(triggerKey, newTrigger);
         return null;
      });
   }

   @Override
   public org.quartz.Trigger.TriggerState getTriggerState(TriggerKey triggerKey)
      throws JobPersistenceException
   {
      // reading the committed state needs no lock (Bug #77879)
      TriggerWrapper tw = triggersByKey.get(triggerKey);
      return tw == null ?
         org.quartz.Trigger.TriggerState.NONE : toClassicTriggerState(tw.getState());
   }

   @Override
   public void resumeTrigger(TriggerKey triggerKey) throws JobPersistenceException {
      inTransaction(TX_TIMEOUT, () -> {
         TriggerWrapper oldTrigger = triggersByKey.getForUpdate(triggerKey);

         if(schedulerRunning && oldTrigger != null) {
            TriggerWrapper newTrigger = newTriggerWrapper(oldTrigger, NORMAL);
            triggersByKey.set(newTrigger.key, newTrigger);
         }

         return null;
      });
   }

   @Override
   public Collection<String> pauseTriggers(GroupMatcher<TriggerKey> matcher)
      throws JobPersistenceException
   {
      List<String> pausedGroups = new ArrayList<>();
      StringMatcher.StringOperatorName operator = matcher.getCompareWithOperator();
      switch(operator) {
      case EQUALS:
         if(pausedTriggerGroups.add(matcher.getCompareToValue())) {
            pausedGroups.add(matcher.getCompareToValue());
         }
         break;
      default:
         for(String group : triggersByGroup.keySet()) {
            if(operator.evaluate(group, matcher.getCompareToValue())) {
               if(pausedTriggerGroups.add(matcher.getCompareToValue())) {
                  pausedGroups.add(group);
               }
            }
         }
      }

      for(String pausedGroup : pausedGroups) {
         Set<TriggerKey> keys =
            getTriggerKeys(GroupMatcher.triggerGroupEquals(pausedGroup));

         for(TriggerKey key : keys) {
            pauseTrigger(key);
         }
      }

      return pausedGroups;
   }

   @Override
   public Collection<String> resumeTriggers(GroupMatcher<TriggerKey> matcher)
      throws JobPersistenceException
   {
      Set<String> resumeGroups = new HashSet<>();
      Set<TriggerKey> keys = getTriggerKeys(matcher);

      for(TriggerKey triggerKey : keys) {
         resumeGroups.add(triggerKey.getGroup());
         TriggerWrapper tw = triggersByKey.get(triggerKey);

         if(tw == null) {
            continue;
         }

         OperableTrigger trigger = tw.getTrigger();
         String jobGroup = trigger.getJobKey().getGroup();

         if(pausedJobGroups.contains(jobGroup)) {
            continue;
         }

         resumeTrigger(triggerKey);
      }

      for(String group : resumeGroups) {
         pausedTriggerGroups.remove(group);
      }

      return new ArrayList<>(resumeGroups);
   }

   @Override
   public void pauseJob(JobKey jobKey) throws JobPersistenceException {
      boolean found = jobsByKey.containsKey(jobKey);

      if(!found) {
         return;
      }

      // Bug #77879, each trigger in its own transaction: a trigger section reads, and so locks,
      // the job after the trigger, so the job must not be locked while locking its triggers
      List<OperableTrigger> triggersForJob = getTriggersForJob(jobKey);

      for(OperableTrigger trigger : triggersForJob) {
         pauseTrigger(trigger.getKey());
      }
   }

   @Override
   public void resumeJob(JobKey jobKey) throws JobPersistenceException {
      boolean found = jobsByKey.containsKey(jobKey);

      if(!found) {
         return;
      }

      // Bug #77879, each trigger in its own transaction: a trigger section reads, and so locks,
      // the job after the trigger, so the job must not be locked while locking its triggers
      List<OperableTrigger> triggersForJob = getTriggersForJob(jobKey);

      for(OperableTrigger trigger : triggersForJob) {
         resumeTrigger(trigger.getKey());
      }
   }

   @Override
   public Collection<String> pauseJobs(GroupMatcher<JobKey> groupMatcher)
      throws JobPersistenceException
   {
      List<String> pausedGroups = new ArrayList<>();
      StringMatcher.StringOperatorName operator = groupMatcher
         .getCompareWithOperator();

      switch(operator) {
      case EQUALS:
         if(pausedJobGroups.add(groupMatcher.getCompareToValue())) {
            pausedGroups.add(groupMatcher.getCompareToValue());
         }
         break;
      default:
         for(String jobGroup : jobsByGroup.keySet()) {
            if(operator.evaluate(jobGroup, groupMatcher.getCompareToValue())) {
               if(pausedJobGroups.add(jobGroup)) {
                  pausedGroups.add(jobGroup);
               }
            }
         }
      }

      for(String groupName : pausedGroups) {
         for(JobKey jobKey : getJobKeys(GroupMatcher.jobGroupEquals(groupName))) {
            pauseJob(jobKey);
         }
      }

      return pausedGroups;
   }

   @Override
   public Collection<String> resumeJobs(GroupMatcher<JobKey> matcher)
      throws JobPersistenceException
   {
      Set<String> resumeGroups = new HashSet<>();
      Set<JobKey> jobKeys = getJobKeys(matcher);

      for(JobKey jobKey : jobKeys) {
         resumeGroups.add(jobKey.getGroup());
         resumeJob(jobKey);
      }

      for(String group : resumeGroups) {
         pausedJobGroups.remove(group);
      }

      return new ArrayList<>(resumeGroups);
   }

   @Override
   public Set<String> getPausedTriggerGroups() throws JobPersistenceException {
      return new HashSet<>(pausedTriggerGroups);
   }

   @Override
   public void pauseAll() throws JobPersistenceException {
      for(String triggerGroup : triggersByGroup.keySet()) {
         pauseTriggers(GroupMatcher.triggerGroupEquals(triggerGroup));
      }
   }

   @Override
   public void resumeAll() throws JobPersistenceException {
      List<String> triggerGroupNames = getTriggerGroupNames();

      for(String triggerGroup : triggerGroupNames) {
         resumeTriggers(GroupMatcher.triggerGroupEquals(triggerGroup));
      }
   }

   /**
    * @return @throws org.quartz.JobPersistenceException
    * @see org.quartz.spi.JobStore#acquireNextTriggers(long, int, long)
    *
    * @param noLaterThan
    *          highest value of <code>getNextFireTime()</code> of the
    *          triggers (exclusive)
    * @param timeWindow
    *          highest value of <code>getNextFireTime()</code> of the
    *          triggers (inclusive)
    * @param maxCount
    *          maximum number of trigger keys allow to acquired in the
    *          returning list.
    */
   @Override
   public List<OperableTrigger> acquireNextTriggers(long noLaterThan,
                                                    int maxCount, long timeWindow)
      throws JobPersistenceException
   {
      if(triggersByKey.isEmpty()) {
         return Collections.emptyList();
      }

      long limit = noLaterThan + timeWindow;

      List<OperableTrigger> result = new ArrayList<>();
      Set<JobKey> acquiredJobKeysForNoConcurrentExec = new HashSet<>();
      // the cluster topology, read once per pass and only if a held trigger or a running job is
      // found
      PassTopology topology = new PassTopology();
      long now = System.currentTimeMillis();

      // ordering triggers to try to ensure firetime order
      List<TriggerWrapper> orderedTriggers = triggersByKey.values().stream()
         .filter(new TriggersPredicate(limit))
         .sorted(Comparator.comparing(TriggerWrapper::getNextFireTime))
         .collect(Collectors.toList());

      Exception failure = null;

      for(TriggerWrapper tw : orderedTriggers) {
         boolean jobTaken = acquiredJobKeysForNoConcurrentExec.contains(tw.jobKey);
         OperableTrigger trig;

         try {
            // proceed after the other jobstore already not blocked, one transaction per trigger
            trig = inTransaction(TX_TIMEOUT, () -> acquireTrigger(
               tw.key, limit, now, acquiredJobKeysForNoConcurrentExec, topology));
         }
         catch(RuntimeException | JobPersistenceException ex) {
            // Bug #77879, the trigger's transaction was rolled back, so the trigger is left as it
            // was and is skipped. The triggers acquired before it must still reach the
            // scheduler, or they would stay acquired by this live node and never fire
            LOG.warn("Failed to acquire trigger {}, it is skipped in this pass", tw.key, ex);
            failure = ex;

            if(!jobTaken) {
               acquiredJobKeysForNoConcurrentExec.remove(tw.jobKey);
            }

            continue;
         }

         if(trig != null) {
            result.add(trig);

            if(result.size() == maxCount) {
               break;
            }
         }
      }

      // nothing was acquired, so the failure can be reported to the scheduler, which backs off
      if(result.isEmpty() && failure != null) {
         if(failure instanceof JobPersistenceException jpe) {
            throw jpe;
         }

         throw (RuntimeException) failure;
      }

      return result;
   }

   /**
    * Acquires a trigger for {@link #acquireNextTriggers} if it is due and may fire. Runs in the
    * trigger's transaction.
    *
    * @return the acquired copy of the trigger, or null if it was not acquired.
    */
   private OperableTrigger acquireTrigger(TriggerKey key, long limit, long now,
                                          Set<JobKey> acquiredJobKeysForNoConcurrentExec,
                                          PassTopology topology)
      throws JobPersistenceException
   {
      // lock the trigger and read it, so any change committed before is present
      TriggerWrapper tw = triggersByKey.getForUpdate(key);

      // trigger deleted since acquired
      if(tw == null) {
         return null;
      }

      // may have been changed between sort and get()
      if(tw.getState() == PAUSED || tw.getState() == PAUSED_BLOCKED) {
         return null;
      }

      // when the trigger was in acquired state for to much time
      /*
      if(tw.getState() == ACQUIRED &&
         (tw.getAcquiredAt() == null
          || tw.getAcquiredAt() + triggerReleaseThreshold + timeWindow < limit))
      {
         LOG.warn("Found a lost trigger [{}] that should be released at [{}]",
                  tw, limit);
         releaseAcquiredTrigger(tw.trigger);
         tw = triggersByKey.get(tw.key);
      }
      */

      // Bug #77245, a hold whose owner can no longer complete or release it would keep the
      // trigger from ever firing again
      if(tw.getState() == ACQUIRED || tw.getState() == BLOCKED) {
         final TriggerWrapper held = tw;

         if(isOrphaned(held, topology.get(), () -> getOrphanedRunHold(held),
                       System.currentTimeMillis()))
         {
            LOG.warn("Releasing trigger {} held {} by node {} ({}, store {}) since {}, " +
                        "that node is no longer in the cluster", tw.key, tw.getState(),
                     tw.getOwnerNode(), tw.getOwnerMember(), tw.getOwnerStore(),
                     tw.getOwnedSince() == null ? null :
                        Instant.ofEpochMilli(tw.getOwnedSince()));
            tw = newTriggerWrapper(tw, WAITING);
            storeTriggerWrapper(tw);
         }
         // Bug #77202, a sibling's completion no longer releases a blocked trigger, so a run
         // that outlasts the task timeout on a live node must not keep it blocked for good
         else if(tw.getState() == BLOCKED &&
            isStale(tw, getRunBound(tw.jobKey), now))
         {
            LOG.warn("Releasing trigger {} blocked by node {} ({}, store {}) since {}, " +
                        "its run outlasted the task timeout", tw.key, tw.getOwnerNode(),
                     tw.getOwnerMember(), tw.getOwnerStore(),
                     Instant.ofEpochMilli(tw.getOwnedSince()));
            tw = newTriggerWrapper(tw, WAITING);
            storeTriggerWrapper(tw);
         }
      }

      if(tw.getState() != NORMAL && tw.getState() != WAITING) {
         return null;
      }

      if(tw.trigger.getNextFireTime() == null) {
         return null;
      }

      // the job is read, and so locked, before the job's running record (see inTransaction)
      final JobDetail job = jobsByKey.get(tw.jobKey);

      // Bug #77202, a trigger of a job that is running on behalf of another trigger is left
      // as it is, so it fires once as soon as that run completes or is no longer honoured
      RunningJob run = runningJobs.get(tw.jobKey);

      if(run != null) {
         if(isRunning(tw.jobKey, run, topology.get(), now)) {
            return null;
         }
      }

      if(applyMisfire(tw)) {
         LOG.debug("Misfire applied {}", tw);
         if(tw.trigger.getNextFireTime() != null) {
            tw = newTriggerWrapper(tw, NORMAL);
         }
         else {
            return null;
         }
      }

      if(tw.getTrigger().getNextFireTime().getTime() > limit) {
         storeTriggerWrapper(newTriggerWrapper(tw, NORMAL));
         return null;
      }

      final JobKey jobKey = tw.trigger.getJobKey();

      // could be removed
      if(job == null) {
         return null;
      }

      // If trigger's job is set as @DisallowConcurrentExecution, and it has
      // already been added to result, then
      // put it back into the timeTriggers set and continue to search for next
      // trigger.
      if(job.isConcurrentExectionDisallowed()) {
         if(acquiredJobKeysForNoConcurrentExec.contains(jobKey)) {
            return null; // go to next trigger in queue.
         }
         else {
            acquiredJobKeysForNoConcurrentExec.add(jobKey);
         }
      }

      OperableTrigger trig = (OperableTrigger) tw.trigger.clone();
      trig.setFireInstanceId(getFiredTriggerRecordId());
      storeTriggerWrapper(newOwnedTriggerWrapper(
         trig, ACQUIRED, getLocalNodeId(), getLocalMember(), fireInstanceIdPrefix));

      return trig;
   }

   /**
    * The cluster topology, read at most once per acquire pass, and only when it is needed.
    */
   private final class PassTopology {
      Set<String> get() {
         if(!read) {
            nodes = getLiveNodes();
            read = true;
         }

         return nodes;
      }

      private boolean read;
      private Set<String> nodes;
   }

   @Override
   public void releaseAcquiredTrigger(OperableTrigger trigger) {
      TriggerKey triggerKey = trigger.getKey();

      try {
         inTransaction(TX_TIMEOUT, () -> {
            TriggerWrapper tw = triggersByKey.getForUpdate(triggerKey);

            // only release the acquisition made by the caller, another node may have acquired
            // the trigger since
            if(tw != null && isAcquiredBy(tw, trigger)) {
               storeTriggerWrapper(newTriggerWrapper(trigger, WAITING));
            }

            return null;
         });
      }
      catch(DistributedTransactionException ex) {
         // Bug #77879, Quartz releases triggers inside its loop over a fired batch, so a failure
         // here must not keep the other triggers of the batch from running
         LOG.warn("Failed to release trigger {}, it stays acquired until this node stops",
                  triggerKey, ex);
      }
   }

   @Override
   public List<TriggerFiredResult> triggersFired(
      List<OperableTrigger> firedTriggers)
      throws JobPersistenceException
   {
      List<TriggerFiredResult> results = new ArrayList<>();

      // Quartz pairs each result with the trigger at the same index, so every trigger gets one
      for(OperableTrigger trigger : firedTriggers) {
         TriggerFiredResult result;

         try {
            result = inTransaction(TX_TIMEOUT, () -> fireTrigger(trigger));
         }
         catch(RuntimeException | JobPersistenceException ex) {
            // Bug #77879, the trigger's transaction was rolled back, so it is still acquired by
            // this node. For a result with an exception (or without a bundle), Quartz releases
            // the trigger and still runs the other triggers of the batch
            LOG.warn("Failed to fire trigger {}", trigger.getKey(), ex);
            result = new TriggerFiredResult(ex);
         }

         // a trigger that is not fired gets a result without a bundle, which Quartz releases;
         // the release changes nothing unless the trigger is still acquired by the caller
         results.add(result != null ? result : new TriggerFiredResult((TriggerFiredBundle) null));
      }

      return results;
   }

   /**
    * Fires a trigger for {@link #triggersFired}. Runs in the trigger's transaction, which the
    * job's running record joins (see {@link #startRun}), so a failure rolls back both.
    *
    * @return the result, or null if the trigger is not fired.
    */
   private TriggerFiredResult fireTrigger(OperableTrigger trigger) throws JobPersistenceException {
      TriggerWrapper tw = triggersByKey.getForUpdate(trigger.getKey());

      // was the trigger deleted since being acquired?
      if(tw == null || tw.trigger == null) {
         return null;
      }
      // was the trigger completed, paused, blocked, etc. or acquired by another node since
      // being acquired?
      if(!isAcquiredBy(tw, trigger)) {
         return null;
      }

      // the job is read, and so locked, before the calendar and the job's running record (see
      // inTransaction)
      JobDetail job = retrieveJob(tw.jobKey);
      Calendar cal = null;

      if(tw.trigger.getCalendarName() != null) {
         cal = retrieveCalendar(tw.trigger.getCalendarName());

         if(cal == null) {
            return null;
         }
      }

      // Bug #77202, another trigger of the job may have fired since this one was acquired
      boolean runStarted = job != null && job.isConcurrentExectionDisallowed();

      if(runStarted && !startRun(job, trigger)) {
         // not fired: the trigger keeps its fire time and fires once the run is over. Quartz
         // pairs each result with the trigger at the same index, and releases the trigger of
         // a result without a bundle, which changes nothing since it is no longer acquired
         storeTriggerWrapper(newTriggerWrapper(tw, WAITING));
         return new TriggerFiredResult((TriggerFiredBundle) null);
      }

      // if this throws, the run did not start and its record is rolled back with the trigger, so
      // it does not hold back the other triggers
      Date prevFireTime = trigger.getPreviousFireTime();
      // call triggered on our copy, and the scheduler's copy
      tw.trigger.triggered(cal);

      if(tw.trigger != trigger) {
         trigger.triggered(cal);
      }

      if(trigger.getNextFireTime() == null) {
         // a trigger that will not fire again must not stay acquirable with its old fire
         // time
         tw = newTriggerWrapper(trigger, COMPLETE);
      }
      else if(job.isConcurrentExectionDisallowed()) {
         // keep the trigger from being acquired again until its execution completes
         tw = newOwnedTriggerWrapper(
            trigger, BLOCKED, getLocalNodeId(), getLocalMember(), fireInstanceIdPrefix);
      }
      else {
         tw = newTriggerWrapper(trigger, WAITING);
      }

      storeTriggerWrapper(tw);

      TriggerFiredBundle bndle = new TriggerFiredBundle(
         retrieveJob(tw.jobKey),
         trigger,
         cal,
         false,
         new Date(),
         trigger.getPreviousFireTime(),
         prevFireTime,
         trigger.getNextFireTime());

      return new TriggerFiredResult(bndle);
   }

   @Override
   public void triggeredJobComplete(
      OperableTrigger trigger, JobDetail jobDetail,
      Trigger.CompletedExecutionInstruction triggerInstCode)
   {
      TriggerWrapper tw = triggersByKey.get(trigger.getKey());

      if(jobDetail.isConcurrentExectionDisallowed()) {
         endRun(jobDetail.getKey(), trigger);
      }

      if(jobDetail.isPersistJobDataAfterExecution()) {
         JobKey jobKey = jobDetail.getKey();

         inTransaction(TX_TIMEOUT, () -> {
            jobsByKey.getForUpdate(jobKey);
            jobsByKey.set(jobKey, jobDetail);
            jobsByGroup.put(jobKey.getGroup(), jobKey);
            return null;
         });
      }
      else if(jobDetail.isConcurrentExectionDisallowed()) {
         ArrayList<TriggerWrapper> trigs = getTriggerWrappersForJob(jobDetail.getKey());

         for(TriggerWrapper ttw : trigs) {
            releaseBlockedTrigger(ttw.key, trigger);
         }

         schedSignaler.signalSchedulingChange(0L);
      }

      // check for trigger deleted during execution...
      if(tw != null) {
         if(triggerInstCode == Trigger.CompletedExecutionInstruction.DELETE_TRIGGER) {
            try {
               if(trigger.getNextFireTime() == null) {
                  // double check for possible reschedule within job
                  // execution, which would cancel the need to delete...
                  /* This caused by problem with runNow. Since hazelcast map
                     clones (serialize/deserialize) the objects when adding
                     to map, retrieving the simple trigger would get the
                     original trigger, which would never return null for
                     getNextFireTime(). This causes the runNow trigger to
                     be fired again and again.
                  if(tw.getTrigger().getNextFireTime() == null) {
                     removeTrigger(trigger.getKey());
                  }
                  */
                  removeTrigger(trigger.getKey());
               }
               else {
                  removeTrigger(trigger.getKey());
                  schedSignaler.signalSchedulingChange(0L);
               }
            }
            catch(JobPersistenceException ex) {
               LOG.error("Error removing trigger", ex);
            }
         }
         else if(triggerInstCode == Trigger.CompletedExecutionInstruction.SET_TRIGGER_COMPLETE) {
            storeTriggerWrapper(newTriggerWrapper(tw, STATE_COMPLETED));
            schedSignaler.signalSchedulingChange(0L);
         }
         else if(triggerInstCode == Trigger.CompletedExecutionInstruction.SET_TRIGGER_ERROR) {
            LOG.warn("Trigger " + trigger.getKey() + " set to ERROR state.");
            storeTriggerWrapper(newTriggerWrapper(tw, BLOCKED));
            schedSignaler.signalSchedulingChange(0L);
         }
         else if(triggerInstCode == Trigger.CompletedExecutionInstruction.SET_ALL_JOB_TRIGGERS_ERROR) {
            LOG.info("All triggers of Job "
                        + trigger.getJobKey() + " set to ERROR state.");
            storeTriggerWrapper(newTriggerWrapper(tw, BLOCKED));
            schedSignaler.signalSchedulingChange(0L);
         }
         else if(triggerInstCode == Trigger.CompletedExecutionInstruction.SET_ALL_JOB_TRIGGERS_COMPLETE) {
            storeTriggerWrapper(newTriggerWrapper(tw, STATE_COMPLETED));
            schedSignaler.signalSchedulingChange(0L);
         }
      }
   }

   @Override
   public void setInstanceName(final String instanceName) {
      this.instanceName = instanceName;
   }

   @Override
   public void setInstanceId(String instanceId) {
      this.instanceId = instanceId;
   }

   public void setShutdownClusterOnShutdown(boolean shutdownClusterOnShutdown) {
      this.shutdownClusterOnShutdown = shutdownClusterOnShutdown;
   }

   public long getMisfireThreshold() {
      return misfireThreshold;
   }

   public void setMisfireThreshold(long misfireThreshold) {
      this.misfireThreshold = misfireThreshold;
   }

   @Override
   public void setThreadPoolSize(int poolSize) {
      // not need
   }

   private ArrayList<TriggerWrapper> getTriggerWrappersForJob(JobKey jobKey) {
      Collection<TriggerKey> triggerKeys = triggersByJob.get(jobKey);
      ArrayList<TriggerWrapper> trigList = new ArrayList<>();

      if(triggerKeys != null) {
         for(TriggerKey key : triggerKeys) {
            TriggerWrapper tw = triggersByKey.get(key);

            if(tw != null) {
               trigList.add(tw);
            }
         }
      }

      return trigList;
   }

   /**
    * Releases a trigger of a non-concurrent job that is blocked while it runs or held by an
    * acquisition, so a hold left by a node that stopped or died does not strand it. A released
    * hold can only be fired by its next acquisition (see {@link #isAcquiredBy}). A trigger that
    * already fired for the last time is left alone, and so is a trigger blocked by another run
    * that is still going (Bug #77202): only the run itself, or its owner leaving the cluster,
    * releases it.
    *
    * @param completed the trigger whose run completed.
    */
   private void releaseBlockedTrigger(TriggerKey key, OperableTrigger completed) {
      try {
         inTransaction(TX_TIMEOUT, () -> {
            TriggerWrapper tw = triggersByKey.getForUpdate(key);

            if(tw == null) {
               return null;
            }

            if(tw.getState() == BLOCKED && !isBlockedBy(tw, completed)) {
               return null;
            }

            if(tw.getState() == BLOCKED || tw.getState() == ACQUIRED) {
               storeTriggerWrapper(newTriggerWrapper(tw, WAITING));
            }
            else if(tw.getState() == PAUSED_BLOCKED) {
               storeTriggerWrapper(newTriggerWrapper(tw, PAUSED));
            }

            return null;
         });
      }
      catch(DistributedTransactionException ex) {
         // the hold is released by the next acquire pass once its run is no longer honoured
         LOG.warn("Failed to release trigger {}", key, ex);
      }
   }

   /**
    * Checks if a BLOCKED entry was blocked by the run of the given trigger, or records no owner (an
    * older version, or an error state set on completion), in which case any completion of the job
    * releases it as before.
    */
   private static boolean isBlockedBy(TriggerWrapper tw, OperableTrigger trigger) {
      return tw.getOwnerNode() == null ||
         trigger.getFireInstanceId() != null &&
         trigger.getFireInstanceId().equals(tw.trigger.getFireInstanceId());
   }

   /**
    * Records that a trigger of a job that disallows concurrent execution is firing, unless another
    * run of the job is still honoured (Bug #77202). Quartz leaves @DisallowConcurrentExecution to
    * the store, and the store is shared by every node, so this is what keeps two conditions of a
    * task from running it at the same time on two worker threads or two nodes.
    *
    * Runs in, or joins, a transaction that locks the job's record (Bug #77879).
    *
    * @return true if the trigger may fire, false if another run is in the way.
    */
   private boolean startRun(JobDetail job, OperableTrigger trigger) {
      JobKey jobKey = job.getKey();

      return inTransaction(TX_TIMEOUT, () -> {
         RunningJob run = runningJobs.getForUpdate(jobKey);

         if(run != null && isRunning(jobKey, run, getLiveNodes(), System.currentTimeMillis())) {
            LOG.debug("Not firing trigger {}, job {} is still running: {}",
                      trigger.getKey(), jobKey, run);
            return false;
         }

         if(run != null) {
            LOG.warn("Firing trigger {} although job {} was not seen to complete, its run is " +
                        "no longer honoured: {}", trigger.getKey(), jobKey, run);
         }

         runningJobs.set(jobKey, new RunningJob(
            trigger.getFireInstanceId(), trigger.getKey(), getLocalNodeId(), getLocalMember(),
            System.currentTimeMillis()));
         return true;
      });
   }

   /**
    * Removes the running record of a job when the run that made it completes. The completion of a
    * run whose record was replaced after it stopped being honoured leaves the new one alone.
    */
   private void endRun(JobKey jobKey, OperableTrigger trigger) {
      try {
         inTransaction(TX_TIMEOUT, () -> {
            RunningJob run = runningJobs.getForUpdate(jobKey);

            if(run != null && trigger.getFireInstanceId() != null &&
               trigger.getFireInstanceId().equals(run.getFireInstanceId()))
            {
               runningJobs.remove(jobKey);
            }

            return null;
         });
      }
      catch(DistributedTransactionException ex) {
         // the record stops being honoured at the task timeout
         LOG.warn("Failed to remove the running record of job {}", jobKey, ex);
      }
   }

   /**
    * Checks if a job's running record still keeps its other triggers from firing (Bug #77202). It
    * is honoured only while its run may still be executing, so a run whose owner died, was scaled
    * in or hangs cannot keep the task from ever running again.
    */
   boolean isRunning(JobKey jobKey, RunningJob run, Set<String> liveNodes, long now) {
      return isRunning(run, liveNodes, isCloudJob(jobKey), getRunBound(jobKey), now);
   }

   /**
    * Gets how long after it started a run of the job may still be executing: the effective task
    * timeout, plus the launch margin for a cloud runner job, whose container its platform stops at
    * the timeout after it was launched.
    */
   long getRunBound(JobKey jobKey) {
      return getEffectiveTaskTimeout() + (isCloudJob(jobKey) ? CLOUD_RUN_LAUNCH_MARGIN : 0);
   }

   private boolean isCloudJob(JobKey jobKey) {
      JobDetail job = jobsByKey.get(jobKey);
      return job != null && ScheduleTaskCloudJob.class.isAssignableFrom(job.getJobClass());
   }

   /**
    * Checks if a BLOCKED entry was taken longer ago than its run may still be executing (Bug
    * #77202). An entry with no recorded owner (an older version, or an error state) is never
    * stale.
    */
   static boolean isStale(TriggerWrapper tw, long bound, long now) {
      return tw.getOwnedSince() != null && now - tw.getOwnedSince() >= bound;
   }

   /**
    * The rule behind {@link #isRunning(JobKey, RunningJob, Set, long)}: a run is honoured until
    * the bound after it started has passed, and only while the node running it is in the cluster,
    * unless the run outlives its owner (a cloud runner container) or the topology cannot be read.
    *
    * @param liveNodes     the ids of the nodes in the cluster, or null if unknown.
    * @param outlivesOwner if the run keeps executing after its owner node is gone.
    * @param bound         how long after it started the run may still be executing.
    */
   static boolean isRunning(RunningJob run, Set<String> liveNodes, boolean outlivesOwner,
                            long bound, long now)
   {
      if(run == null || now - run.getStartTime() >= bound) {
         return false;
      }

      return outlivesOwner || liveNodes == null || liveNodes.contains(run.getOwnerNode());
   }

   /**
    * Checks if the stored trigger is still held by the acquisition that produced the given copy.
    */
   private static boolean isAcquiredBy(TriggerWrapper tw, OperableTrigger trigger) {
      return tw.getState() == ACQUIRED && trigger.getFireInstanceId() != null &&
         trigger.getFireInstanceId().equals(tw.trigger.getFireInstanceId());
   }

   /**
    * Checks if an ACQUIRED or BLOCKED entry is held by an owner that can no longer complete or
    * release it (Bug #77245): the cluster node that took the hold is no longer in the cluster. A
    * node id is unique across hosts and changes whenever the JVM (and so its Ignite node) is
    * restarted, but not when the scheduler is stopped and started in the same JVM, whose old run
    * may still be executing and whose completion releases the entry. An entry with no recorded
    * owner (an older version, or an error state) is never orphaned, nor is any entry when the
    * topology cannot be read.
    *
    * @param liveNodes       the ids of the nodes in the cluster, including clients, or null if
    *                        unknown.
    * @param orphanedRunHold how long after the owner took the hold its run may still be executing
    *                        although the owner is gone, 0 if the run cannot outlive its owner.
    *                        Only evaluated if the owner is gone.
    */
   static boolean isOrphaned(TriggerWrapper tw, Set<String> liveNodes,
                             LongSupplier orphanedRunHold, long now)
   {
      String node = tw.getOwnerNode();

      if(node == null || liveNodes == null || liveNodes.contains(node)) {
         return false;
      }

      long hold = orphanedRunHold.getAsLong();
      return hold <= 0 || tw.getOwnedSince() != null && now - tw.getOwnedSince() >= hold;
   }

   /**
    * Gets how long an orphaned hold must be kept after it was taken because its run may still be
    * executing without its owner. A cloud runner job runs the task in a container that the owner
    * launched and that is not stopped when the owner dies. Its platform stops it at the task timeout
    * (Kubernetes, Azure and Google cloud runners), so the hold is kept for the timeout the owner
    * waited with plus a margin for the container launch. An acquired trigger has not launched
    * anything, and any other job runs in the owner's JVM and dies with it.
    */
   long getOrphanedRunHold(TriggerWrapper tw) {
      if(tw.getState() != BLOCKED) {
         return 0;
      }

      JobDetail job = jobsByKey.get(tw.jobKey);

      if(job == null || !ScheduleTaskCloudJob.class.isAssignableFrom(job.getJobClass())) {
         return 0;
      }

      return getEffectiveTaskTimeout() + CLOUD_RUN_LAUNCH_MARGIN;
   }

   /**
    * Gets the task timeout with the same fallback as ScheduleTaskCloudJob.execute(). A local run is
    * cancelled by ScheduleTask at the configured timeout. If that is not positive, a local run is
    * not cancelled and may outlast the default this falls back to.
    */
   private static long getEffectiveTaskTimeout() {
      long timeout = ScheduleTask.getTaskTimeout();
      return timeout > 0 ? timeout : ScheduleTask.DEFAULT_TASK_TIMEOUT;
   }

   /**
    * Gets the ids of the nodes currently in the cluster, including client nodes, or null if the
    * topology cannot be read or does not contain this node (it is disconnected or the view is not
    * consistent), in which case nothing is released.
    */
   Set<String> getLiveNodes() {
      try {
         Set<String> nodes = Cluster.getInstance().getClusterNodeIds();
         String localNode = getLocalNodeId();

         if(nodes == null || !nodes.contains(localNode)) {
            LOG.debug("The cluster nodes {} do not contain this node {}, not checking for " +
                         "released triggers", nodes, localNode);
            return null;
         }

         return nodes;
      }
      catch(RuntimeException ex) {
         LOG.warn("Failed to get the cluster nodes to check for released triggers", ex);
         return null;
      }
   }

   String getLocalNodeId() {
      return Cluster.getInstance().getLocalNodeId();
   }

   String getLocalMember() {
      return Cluster.getInstance().getLocalMember();
   }

   private boolean applyMisfire(TriggerWrapper tw) throws JobPersistenceException {
      long misfireTime = DateBuilder.newDate().build().getTime();
      if(misfireThreshold > 0) {
         misfireTime -= misfireThreshold;
      }

      Date tnft = tw.trigger.getNextFireTime();

      if(tnft == null
         || tnft.getTime() > misfireTime
         || tw.trigger.getMisfireInstruction() == Trigger.MISFIRE_INSTRUCTION_IGNORE_MISFIRE_POLICY) {
         return false;
      }

      Calendar cal = null;
      if(tw.trigger.getCalendarName() != null) {
         cal = retrieveCalendar(tw.trigger.getCalendarName());
      }

      this.schedSignaler
         .notifyTriggerListenersMisfired((OperableTrigger) tw.trigger.clone());

      tw.trigger.updateAfterMisfire(cal);

      if(tw.trigger.getNextFireTime() == null) {
         storeTriggerWrapper(newTriggerWrapper(tw, STATE_COMPLETED));
         schedSignaler.notifySchedulerListenersFinalized(tw.trigger);

      }
      else if(tnft.equals(tw.trigger.getNextFireTime())) {
         return false;
      }

      return true;
   }

   private synchronized String getFiredTriggerRecordId() {
      return fireInstanceIdPrefix + ftrCtr++;
   }

   private boolean removeTrigger(TriggerKey key, boolean removeOrphanedJob)
      throws JobPersistenceException
   {
      // remove from triggers by FQN map
      final TriggerWrapper tw = inTransaction(TX_TIMEOUT, () -> {
         triggersByKey.getForUpdate(key);
         TriggerWrapper removedTrigger = triggersByKey.remove(key);

         if(removedTrigger != null) {
            // remove from triggers by group
            triggersByGroup.remove(key.getGroup(), key);
            triggersByJob.remove(removedTrigger.jobKey, key);
         }

         return removedTrigger;
      });
      boolean removed = tw != null;

      // removing the job locks it, which must not happen while holding the trigger (Bug #77879),
      // so it is removed after the trigger's transaction
      if(removed && removeOrphanedJob) {
         JobDetail job = jobsByKey.get(tw.jobKey);
         List<OperableTrigger> trigs = getTriggersForJob(tw.jobKey);

         if((trigs == null || trigs.isEmpty()) && job != null && !job.isDurable()) {
            if(removeJob(job.getKey())) {
               schedSignaler.notifySchedulerListenersJobDeleted(job.getKey());
            }
         }
      }

      return removed;
   }

   private void storeTriggerWrapper(TriggerWrapper tw) {
      triggersByKey.set(tw.key, tw);
   }

   /**
    * Runs a section of the store in a pessimistic transaction, or joins the one the thread is
    * running (Bug #77879). A section locks its key with getForUpdate as its first operation, and
    * every other key it reads (except with containsKey) or writes is locked too, until the
    * transaction ends. To avoid deadlocks, a section locks at most one trigger and one job, in
    * this order: the trigger, the trigger's job, the job's running record and the calendar, then
    * the multimap keys (triggersByGroup before triggersByJob, or jobsByGroup). A job section
    * locks the job, then jobsByGroup, and never a trigger: pauseJob and resumeJob use a
    * transaction per trigger, and removeTrigger removes an orphaned job after its transaction.
    *
    * @param timeout the transaction timeout in milliseconds, which bounds waiting for a lock.
    *
    * @throws DistributedTransactionException if the transaction timed out or was rolled back.
    */
   private <T, E extends Exception> T inTransaction(long timeout, TransactionalAction<T, E> action)
      throws E
   {
      return cluster.runInTransaction(timeout, TimeUnit.MILLISECONDS, action);
   }

   /**
    * Set the max time which a acquired trigger must be released.
    * It should be > 30000, since quartz executes acquireNextTriggers in a 30000 interval
    */
   public void setTriggerReleaseThreshold(long triggerReleaseThreshold) {
      if(triggerReleaseThreshold > 30000) {
         LOG.warn(
            "Try to increase your trigger release time threashold since quartz " +
               "acquireNextTriggers in a 30000 interval");
      }

      this.triggerReleaseThreshold = triggerReleaseThreshold;
   }

   @Override
   public long getAcquireRetryDelay(int i) {
      return getEstimatedTimeToReleaseAndAcquireTrigger() * 10;
   }

   @Override
   public void resetTriggerFromErrorState(TriggerKey triggerKey) throws JobPersistenceException {
      TriggerWrapper tw = triggersByKey.get(triggerKey);

      // was the trigger deleted since being acquired?
      if(tw == null) {
         return;
      }

      // was the trigger completed, paused, blocked, etc. since being acquired?
      if(tw.getState() != ERROR) {
         return;
      }

      inTransaction(TX_TIMEOUT, () -> {
         TriggerWrapper current = triggersByKey.getForUpdate(triggerKey);

         // changed since the check above?
         if(current == null || current.getState() != ERROR) {
            return null;
         }

         TriggerWrapper newTw;

         if(pausedTriggerGroups.contains(triggerKey.getGroup())) {
            newTw = newTriggerWrapper(current, PAUSED);
         }
         else {
            newTw = newTriggerWrapper(current, WAITING);
         }

         triggersByKey.set(newTw.key, newTw);
         return null;
      });
   }

   private void setPrincipal(JobDetail job) {
      ScheduleTask task = (ScheduleTask) job.getJobDataMap().get(ScheduleTask.class.getName());

      if(task == null) {
         return;
      }

      String addr = Tool.getIP();
      String taskName = job.getKey().getName();
      Principal principal = null;

      if(!ScheduleManager.isInternalTask(taskName)) {
         // Bug #77168/#77452, the execute-as identity, or the owner when there is none or
         // security is disabled
         principal = SUtil.getScheduleTaskRunPrincipal(task, addr, true);
      }

      if(principal != null) {
         ThreadContext.setContextPrincipal(principal);
      }
   }

   private SchedulerSignaler schedSignaler;
   private Cluster cluster;
   private DistributedMap<JobKey, JobDetail> jobsByKey;
   private DistributedMap<TriggerKey, TriggerWrapper> triggersByKey;
   private MultiMap<String, JobKey> jobsByGroup;
   private MultiMap<String, TriggerKey> triggersByGroup;
   private MultiMap<JobKey, TriggerKey> triggersByJob;
   private DistributedMap<String, Calendar> calendarsByName;
   // Bug #77202, the run of each job that disallows concurrent execution
   private DistributedMap<JobKey, RunningJob> runningJobs;
   private Set<String> pausedTriggerGroups;
   private Set<String> pausedJobGroups;
   private volatile boolean schedulerRunning = false;
   private long misfireThreshold = 5000;
   private long triggerReleaseThreshold = 60000;

   private String instanceId;
   private String instanceName;
   private boolean shutdownClusterOnShutdown = true;
   private static long ftrCtr = System.currentTimeMillis();
   // the instance id is "AUTO" on every node, so a random part keeps fire instance ids unique
   // across the cluster, which the ownership check in isAcquiredBy() relies on
   private final String fireInstanceIdPrefix = UUID.randomUUID() + "-";
   private static final long CLOUD_RUN_LAUNCH_MARGIN = TimeUnit.MINUTES.toMillis(5);
   // the timeout of a section's transaction, which also bounds waiting for its locks
   private static final long TX_TIMEOUT = TimeUnit.MINUTES.toMillis(5);
   private static final long RELEASE_ON_SHUTDOWN_TIMEOUT = TimeUnit.SECONDS.toMillis(5);
   private static final Logger LOG = LoggerFactory.getLogger(ClusterJobStore.class);
}

/**
 * Filter triggers with a given fire start time, end time and state.
 */
class TriggersPredicate implements Predicate<TriggerWrapper> {
   private final long noLaterThanWithTimeWindow;

   public TriggersPredicate(long noLaterThanWithTimeWindow) {
      this.noLaterThanWithTimeWindow = noLaterThanWithTimeWindow;
   }

   @Override
   public boolean test(TriggerWrapper trigger) {
      if(trigger == null || trigger.getNextFireTime() == null) {
         return false;
      }

      if(trigger.getNextFireTime() > noLaterThanWithTimeWindow ||
         trigger.getEndTime() != null &&
            trigger.getEndTime() < noLaterThanWithTimeWindow)
      {
         return false;
      }

      if(trigger.getState() == TriggerState.PAUSED ||
         trigger.getState() == TriggerState.PAUSED_BLOCKED)
      {
         return false;
      }

      return true;
   }
}
