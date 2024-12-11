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

import inetsoft.sree.internal.cluster.*;
import org.quartz.Calendar;
import org.quartz.*;
import org.quartz.impl.matchers.GroupMatcher;
import org.quartz.impl.matchers.StringMatcher;
import org.quartz.spi.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Serializable;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static inetsoft.sree.schedule.jobstore.TriggerState.*;
import static inetsoft.sree.schedule.jobstore.TriggerWrapper.newTriggerWrapper;

public class ClusterJobStore implements JobStore, Serializable {
   @Override
   public void initialize(ClassLoadHelper loadHelper, SchedulerSignaler signaler)
      throws SchedulerConfigException
   {
      LOG.debug("Initializing Cluster Job Store..");

      this.schedSignaler = signaler;

      // initializing Cluster maps
      final Cluster cluster = Cluster.getInstance();
      LOG.debug("Initializing Cluster maps...");
      jobsByKey = new LocalClusterMap<>("jobstore.jobsByKey", cluster,
                                        cluster.getMap("jobstore.jobsByKey"));
      triggersByKey = new LocalClusterMap<>("jobstore.triggersByKey", cluster,
                                            cluster.getMap("jobstore.triggersByKey"));
      jobsByGroup = new LocalClusterMultiMap<>(
         "jobstore.jobsByGroup", cluster, cluster.getMultiMap("jobstore.jobsByGroup"));
      triggersByGroup = new LocalClusterMultiMap<>(
         "jobstore.triggersByGroup", cluster, cluster.getMultiMap("jobstore.triggersByGroup"));
      triggersByJob = new LocalClusterMultiMap<>(
         "jobstore.triggersByJob", cluster, cluster.getMultiMap("jobstore.triggersByJob"));
      pausedTriggerGroups = cluster.getSet("jobstore.pausedTriggerGroups");
      pausedJobGroups = cluster.getSet("jobstore.pausedJobGroups");
      calendarsByName = new LocalClusterMap<>("jobstore.calendarsByName", cluster,
                                              cluster.getMap("jobstore.calendarsByName"));

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
      if(shutdownClusterOnShutdown) {
         try {
            Cluster.getInstance().close();
         }
         catch(Exception ex) {
            LOG.debug("Failed to shut down cluster instance", ex);
         }
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

      jobsByKey.lock(newJobKey, 5, TimeUnit.MINUTES);

      try {
         jobsByKey.set(newJobKey, newJob);
         jobsByGroup.put(newJobKey.getGroup(), newJobKey);
      }
      finally {
         try {
            jobsByKey.unlock(newJobKey);
         }
         catch(IllegalMonitorStateException ex) {
            LOG.warn("Error unlocking since it is already released.", ex);
         }
      }
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
            storeTrigger((OperableTrigger) trigger, true);
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

         jobsByKey.lock(jobKey, 5, TimeUnit.MINUTES);

         try {
            jobsByGroup.remove(jobKey.getGroup(), jobKey);
            removed = jobsByKey.remove(jobKey) != null;
         }
         finally {
            try {
               jobsByKey.unlock(jobKey);
            }
            catch(IllegalMonitorStateException | IllegalStateException ex ) {
               LOG.warn("Error unlocking since it is already released.", ex);
            }
         }
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
      final OperableTrigger newTrigger = (OperableTrigger) trigger.clone();
      final TriggerKey triggerKey = newTrigger.getKey();

      triggersByKey.lock(triggerKey, 5, TimeUnit.MINUTES);

      try {
         boolean containsKey = triggersByKey.containsKey(triggerKey);

         if(containsKey && !replaceExisting) {
            throw new ObjectAlreadyExistsException(newTrigger);
         }

         if(retrieveJob(newTrigger.getJobKey()) == null) {
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
      }
      finally {
         try {
            triggersByKey.unlock(triggerKey);
         }
         catch(IllegalMonitorStateException ex) {
            LOG.warn("Error unlocking since it is already released.", ex);
         }
      }
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
      triggersByKey.lock(triggerKey, 5, TimeUnit.MINUTES);

      try {
         TriggerWrapper newTrigger = newTriggerWrapper(triggersByKey.get(triggerKey), PAUSED);
         triggersByKey.set(triggerKey, newTrigger);
      }
      finally {
         try {
            triggersByKey.unlock(triggerKey);
         }
         catch(IllegalMonitorStateException ex) {
            LOG.warn("Error unlocking since it is already released.", ex);
         }
      }
   }

   @Override
   public org.quartz.Trigger.TriggerState getTriggerState(TriggerKey triggerKey)
      throws JobPersistenceException
   {
      triggersByKey.lock(triggerKey, 5, TimeUnit.MINUTES);
      org.quartz.Trigger.TriggerState result = org.quartz.Trigger.TriggerState.NONE;

      try {
         TriggerWrapper tw = triggersByKey.get(triggerKey);

         if(tw != null) {
            result = toClassicTriggerState(tw.getState());
         }
      }
      finally {
         try {
            triggersByKey.unlock(triggerKey);
         }
         catch(IllegalMonitorStateException ex) {
            LOG.warn("Error unlocking since it is already released.", ex);
         }
      }
      return result;
   }

   @Override
   public void resumeTrigger(TriggerKey triggerKey) throws JobPersistenceException {
      triggersByKey.lock(triggerKey, 5, TimeUnit.MINUTES);

      try {
         if(schedulerRunning) {
            TriggerWrapper newTrigger = newTriggerWrapper(triggersByKey.get(triggerKey), NORMAL);
            triggersByKey.set(newTrigger.key, newTrigger);
         }
      }
      finally {
         try {
            triggersByKey.unlock(triggerKey);
         }
         catch(IllegalMonitorStateException ex) {
            LOG.warn("Error unlocking since it is already released.", ex);
         }
      }
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

      jobsByKey.lock(jobKey, 5, TimeUnit.MINUTES);

      try {
         List<OperableTrigger> triggersForJob = getTriggersForJob(jobKey);

         for(OperableTrigger trigger : triggersForJob) {
            pauseTrigger(trigger.getKey());
         }
      }
      finally {
         try {
            jobsByKey.unlock(jobKey);
         }
         catch(IllegalMonitorStateException ex) {
            LOG.warn("Error unlocking since it is already released.", ex);
         }
      }
   }

   @Override
   public void resumeJob(JobKey jobKey) throws JobPersistenceException {
      boolean found = jobsByKey.containsKey(jobKey);

      if(!found) {
         return;
      }

      jobsByKey.lock(jobKey, 5, TimeUnit.MINUTES);

      try {
         List<OperableTrigger> triggersForJob = getTriggersForJob(jobKey);

         for(OperableTrigger trigger : triggersForJob) {
            resumeTrigger(trigger.getKey());
         }
      }
      finally {
         try {
            jobsByKey.unlock(jobKey);
         }
         catch(IllegalMonitorStateException ex) {
            LOG.warn("Error unlocking since it is already released.", ex);
         }
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

      // ordering triggers to try to ensure firetime order
      List<TriggerWrapper> orderedTriggers = triggersByKey.values().stream()
         .filter(new TriggersPredicate(limit))
         .sorted(Comparator.comparing(TriggerWrapper::getNextFireTime))
         .collect(Collectors.toList());

      for(TriggerWrapper tw : orderedTriggers) {
         // proceed after the other jobstore already not blocked
         TriggerKey lockKey = tw.key;
         triggersByKey.lock(lockKey, 5, TimeUnit.MINUTES);

         // get after lock() so any changes applied before lock is acquired
         // would be present
         tw = triggersByKey.get(tw.key);

         try {
            // trigger deleted since acquired
            if(tw == null) {
               continue;
            }

            // may have been changed between sort and get()
            if(tw.getState() == PAUSED || tw.getState() == PAUSED_BLOCKED) {
               continue;
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

            if(tw.getState() != NORMAL && tw.getState() != WAITING) {
               continue;
            }

            if(tw.trigger.getNextFireTime() == null) {
               continue;
            }

            if(applyMisfire(tw)) {
               LOG.debug("Misfire applied {}", tw);
               if(tw.trigger.getNextFireTime() != null) {
                  tw = newTriggerWrapper(tw, NORMAL);
               }
               else {
                  continue;
               }
            }

            if(tw.getTrigger().getNextFireTime().getTime() > limit) {
               storeTriggerWrapper(newTriggerWrapper(tw, NORMAL));
               continue;
            }

            final JobKey jobKey = tw.trigger.getJobKey();
            final JobDetail job = jobsByKey.get(tw.trigger.getJobKey());

            // could be removed
            if(job == null) {
               continue;
            }

            // If trigger's job is set as @DisallowConcurrentExecution, and it has
            // already been added to result, then
            // put it back into the timeTriggers set and continue to search for next
            // trigger.
            if(job.isConcurrentExectionDisallowed()) {
               if(acquiredJobKeysForNoConcurrentExec.contains(jobKey)) {
                  continue; // go to next trigger in queue.
               }
               else {
                  acquiredJobKeysForNoConcurrentExec.add(jobKey);
               }
            }

            OperableTrigger trig = (OperableTrigger) tw.trigger.clone();
            trig.setFireInstanceId(getFiredTriggerRecordId());
            storeTriggerWrapper(newTriggerWrapper(trig, ACQUIRED));

            result.add(trig);

            if(result.size() == maxCount) {
               break;
            }
         }
         finally {
            try {
               triggersByKey.unlock(lockKey);
            }
            catch(IllegalMonitorStateException ex) {
               LOG.warn("Error unlocking since it is already released.", ex);
            }
         }
      }

      return result;
   }

   @Override
   public void releaseAcquiredTrigger(OperableTrigger trigger) {
      TriggerKey triggerKey = trigger.getKey();
      triggersByKey.lock(triggerKey, 5, TimeUnit.MINUTES);

      try {
         TriggerWrapper tw = triggersByKey.get(triggerKey);

         if(tw != null && tw.getState() != TriggerState.PAUSED &&
            tw.getState() != TriggerState.PAUSED_BLOCKED)
         {
            storeTriggerWrapper(newTriggerWrapper(trigger, WAITING));
         }
      }
      finally {
         try {
            triggersByKey.unlock(triggerKey);
         }
         catch(IllegalMonitorStateException ex) {
            LOG.warn("Error unlocking since it is already released.", ex);
         }
      }
   }

   @Override
   public List<TriggerFiredResult> triggersFired(
      List<OperableTrigger> firedTriggers)
      throws JobPersistenceException
   {
      List<TriggerFiredResult> results = new ArrayList<>();

      for(OperableTrigger trigger : firedTriggers) {
         triggersByKey.lock(trigger.getKey(), 5, TimeUnit.MINUTES);

         try {
            TriggerWrapper tw = triggersByKey.get(trigger.getKey());

            // was the trigger deleted since being acquired?
            if(tw == null || tw.trigger == null) {
               continue;
            }
            // was the trigger completed, paused, blocked, etc. since being acquired?
            if(tw.getState() != ACQUIRED) {
               continue;
            }

            Calendar cal = null;

            if(tw.trigger.getCalendarName() != null) {
               cal = retrieveCalendar(tw.trigger.getCalendarName());

               if(cal == null) {
                  continue;
               }
            }

            Date prevFireTime = trigger.getPreviousFireTime();
            // call triggered on our copy, and the scheduler's copy
            tw.trigger.triggered(cal);

            if(tw.trigger != trigger) {
               trigger.triggered(cal);
            }

            JobDetail job = retrieveJob(tw.jobKey);

            if(job.isConcurrentExectionDisallowed()) {
               ArrayList<TriggerWrapper> trigs = getTriggerWrappersForJob(job.getKey());

               for(TriggerWrapper ttw : trigs) {
                  if(ttw.getState() == WAITING) {
                     ttw = newTriggerWrapper(ttw, BLOCKED);
                  }
                  else if(ttw.getState() == PAUSED) {
                     ttw = newTriggerWrapper(ttw, PAUSED_BLOCKED);
                  }
               }

               tw = newTriggerWrapper(trigger, ACQUIRED);
            }
            else {
               tw = newTriggerWrapper(trigger, WAITING);
            }

            if(tw.trigger.getNextFireTime() != null) {
               storeTriggerWrapper(tw);
            }

            TriggerFiredBundle bndle = new TriggerFiredBundle(
               retrieveJob(tw.jobKey),
               trigger,
               cal,
               false,
               new Date(),
               trigger.getPreviousFireTime(),
               prevFireTime,
               trigger.getNextFireTime());

            results.add(new TriggerFiredResult(bndle));
         }
         finally {
            try {
               triggersByKey.unlock(trigger.getKey());
            }
            catch(IllegalMonitorStateException ex) {
               LOG.warn("Error unlocking since it is already released.", ex);
            }
         }
      }

      return results;
   }

   @Override
   public void triggeredJobComplete(
      OperableTrigger trigger, JobDetail jobDetail,
      Trigger.CompletedExecutionInstruction triggerInstCode)
   {
      TriggerWrapper tw = triggersByKey.get(trigger.getKey());

      if(jobDetail.isPersistJobDataAfterExecution()) {
         JobKey jobKey = jobDetail.getKey();
         jobsByKey.lock(jobKey, 5, TimeUnit.MINUTES);

         try {
            jobsByKey.set(jobKey, jobDetail);
            jobsByGroup.put(jobKey.getGroup(), jobKey);
         }
         finally {
            try {
               jobsByKey.unlock(jobKey);
            }
            catch(IllegalMonitorStateException ex) {
               LOG.warn("Error unlocking since it is already released.", ex);
            }
         }
      }
      else if(jobDetail.isConcurrentExectionDisallowed()) {
         ArrayList<TriggerWrapper> trigs = getTriggerWrappersForJob(jobDetail.getKey());

         for(TriggerWrapper ttw : trigs) {
            if(ttw.getTrigger() instanceof SimpleTrigger) {
               continue;
            }

            if(ttw.getState() == BLOCKED || ttw.getState() == ACQUIRED) {
               storeTriggerWrapper(newTriggerWrapper(ttw, WAITING));
            }
            else if(ttw.getState() == PAUSED_BLOCKED) {
               storeTriggerWrapper(newTriggerWrapper(ttw, PAUSED));
            }
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

      for(TriggerKey key : triggerKeys) {
         TriggerWrapper tw = triggersByKey.get(key);

         if(tw != null) {
            trigList.add(tw);
         }
      }

      return trigList;
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
      return instanceId + ftrCtr++;
   }

   private boolean removeTrigger(TriggerKey key, boolean removeOrphanedJob)
      throws JobPersistenceException
   {
      boolean removed;

      // remove from triggers by FQN map
      triggersByKey.lock(key, 5, TimeUnit.MINUTES);

      try {
         final TriggerWrapper tw = triggersByKey.remove(key);
         removed = tw != null;

         if(removed) {
            // remove from triggers by group
            triggersByGroup.remove(key.getGroup(), key);
            triggersByJob.remove(tw.jobKey, key);
            //        triggers.remove(tw);

            if(removeOrphanedJob) {
               JobDetail job = jobsByKey.get(tw.jobKey);
               List<OperableTrigger> trigs = getTriggersForJob(tw.jobKey);

               if((trigs == null || trigs.isEmpty()) && job != null && !job.isDurable()) {
                  if(removeJob(job.getKey())) {
                     schedSignaler.notifySchedulerListenersJobDeleted(job.getKey());
                  }
               }
            }
         }
      }
      finally {
         try {
            triggersByKey.unlock(key);
         }
         catch(IllegalMonitorStateException ex) {
            LOG.warn("Error unlocking since it is already released.", ex);
         }
      }

      return removed;
   }

   private void storeTriggerWrapper(TriggerWrapper tw) {
      triggersByKey.set(tw.key, tw);
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

      triggersByKey.lock(triggerKey, 5, TimeUnit.MINUTES);

      try {
         TriggerWrapper newTw;

         if(pausedTriggerGroups.contains(triggerKey.getGroup())) {
            newTw = newTriggerWrapper(tw, PAUSED);
         }
         else {
            newTw = newTriggerWrapper(tw, WAITING);
         }

         triggersByKey.set(newTw.key, newTw);
      }
      finally {
         try {
            triggersByKey.unlock(triggerKey);
         }
         catch(IllegalMonitorStateException ex) {
            LOG.warn("Error unlocking since it is already released.", ex);
         }
      }
   }

   private SchedulerSignaler schedSignaler;
   private LocalClusterMap<JobKey, JobDetail> jobsByKey;
   private LocalClusterMap<TriggerKey, TriggerWrapper> triggersByKey;
   private LocalClusterMultiMap<String, JobKey> jobsByGroup;
   private LocalClusterMultiMap<String, TriggerKey> triggersByGroup;
   private LocalClusterMultiMap<JobKey, TriggerKey> triggersByJob;
   private LocalClusterMap<String, Calendar> calendarsByName;
   private Set<String> pausedTriggerGroups;
   private Set<String> pausedJobGroups;
   private volatile boolean schedulerRunning = false;
   private long misfireThreshold = 5000;
   private long triggerReleaseThreshold = 60000;

   private String instanceId;
   private String instanceName;
   private boolean shutdownClusterOnShutdown = true;
   private static long ftrCtr = System.currentTimeMillis();
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
