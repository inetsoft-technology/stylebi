/*
 * This file is part of StyleBI.
 * Copyright (C) 2026  InetSoft Technology
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

import org.junit.jupiter.api.*;
import org.quartz.JobKey;
import org.quartz.SchedulerException;
import org.quartz.impl.matchers.GroupMatcher;
import org.quartz.simpl.RAMJobStore;

import java.time.Duration;
import java.util.*;

import static inetsoft.sree.schedule.Scheduler.Status.FINISHED;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

/**
 * Bug #79262: a key listed in the job group index with no job behind it (an orphan left by nodes
 * replacing the same task at once) used to make {@code JobCompletionListener} throw on the null
 * job, which ended the search for dependant tasks, so a dependant whose key came after the orphan
 * was never triggered. The listener skips such a key. The orphan is placed before and after the
 * dependant in the order the store returns the keys.
 */
@Tag("core")
class JobCompletionListenerOrphanKeyTest {
   @BeforeEach
   void setUp() throws Exception {
      OrphanJobStore.ORPHANS.clear();
      harness = new SchedulerTestHarness(OrphanJobStore.class.getName());
   }

   @AfterEach
   void tearDown() {
      harness.close();
      OrphanJobStore.ORPHANS.clear();
   }

   @Test
   void orphanKeyIteratedBeforeDependantDoesNotStopIt() throws Exception {
      runWithOrphan(true);
   }

   @Test
   void orphanKeyIteratedAfterDependantStillTriggersIt() throws Exception {
      runWithOrphan(false);
   }

   private void runWithOrphan(boolean before) throws Exception {
      ScheduleTask parent = new ScheduleTask("orphan-parent-" + before);
      parent.setOwner(SchedulerTestHarness.TEST_OWNER);
      parent.setEnabled(true);
      parent.addAction(mock(ScheduleAction.class));
      ScheduleTask dependant = new ScheduleTask("orphan-dependant-" + before);
      dependant.setOwner(SchedulerTestHarness.TEST_OWNER);
      dependant.setEnabled(true);
      dependant.addAction(mock(ScheduleAction.class));
      dependant.addCondition(new CompletionCondition(parent.getTaskId()));
      harness.registerTask(parent);
      harness.registerTask(dependant);

      JobKey dependantKey = new JobKey(dependant.getTaskId(), Scheduler.GROUP_NAME);
      JobKey orphan = findOrphan(dependantKey, before);
      OrphanJobStore.ORPHANS.add(orphan);
      assertNull(harness.getQuartz().getJobDetail(orphan));

      harness.triggerNow(parent.getTaskId());
      harness.waitForStatus(parent.getTaskId(), FINISHED, Duration.ofSeconds(10));

      // the dependant is only triggered by the completion of the parent
      harness.waitForStatus(dependant.getTaskId(), FINISHED, Duration.ofSeconds(10));
      assertNotNull(harness.getDao().getStatus(dependant.getTaskId()));
   }

   /**
    * Finds an orphan key that the store returns before (or after) the dependant, so the test does
    * not depend on how the keys happen to hash.
    */
   private JobKey findOrphan(JobKey dependantKey, boolean before) throws SchedulerException {
      for(int i = 0; i < 500; i++) {
         JobKey candidate = new JobKey("orphan-" + i, Scheduler.GROUP_NAME);
         OrphanJobStore.ORPHANS.clear();
         OrphanJobStore.ORPHANS.add(candidate);
         List<JobKey> order = new ArrayList<>(
            harness.getQuartz().getJobKeys(GroupMatcher.jobGroupEquals(Scheduler.GROUP_NAME)));
         int orphanIndex = order.indexOf(candidate);
         int dependantIndex = order.indexOf(dependantKey);

         if(orphanIndex >= 0 && dependantIndex >= 0 &&
            (orphanIndex < dependantIndex) == before)
         {
            OrphanJobStore.ORPHANS.clear();
            return candidate;
         }
      }

      throw new IllegalStateException("no orphan key found for the requested order");
   }

   /**
    * A job store whose group listing also returns keys that have no job, as the cluster store
    * does with an orphan key in its group index.
    */
   public static class OrphanJobStore extends RAMJobStore {
      @Override
      public Set<JobKey> getJobKeys(GroupMatcher<JobKey> matcher) {
         Set<JobKey> keys = new HashSet<>(super.getJobKeys(matcher));

         for(JobKey orphan : ORPHANS) {
            if(matcher.getCompareWithOperator().evaluate(
                  orphan.getGroup(), matcher.getCompareToValue()))
            {
               keys.add(orphan);
            }
         }

         return keys;
      }

      static final Set<JobKey> ORPHANS = Collections.synchronizedSet(new LinkedHashSet<>());
   }

   private SchedulerTestHarness harness;
}
