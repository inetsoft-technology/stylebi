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
package inetsoft.util.swap;

import inetsoft.util.FileSystemService;
import inetsoft.util.TimedQueue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;

/**
 * Bug #78245, re-runs {@link XSwapper}'s dead-seed-and-grace-period swap file sweep on a short,
 * fixed interval instead of only once per JVM (at construction) or on a manual EM Clean Up.
 * <p>
 * {@link XSwapper}'s constructor already sweeps the swap cache directory once, in a background
 * thread, deleting a <tt>*.tdat</tt> file only if its seed is registered in
 * {@link XSwapper#SWAP_SEED_MAP} and confirmed dead -- i.e. none of its owner nodes are in the
 * current cluster topology -- and the file is older than
 * {@link XSwapper#SWAP_FILE_GRACE_PERIOD}. A file written in the last seconds of a JVM that then
 * leaves the cluster (e.g. during a rolling restart) can still be inside that grace period the
 * one time the startup sweep evaluates it, and since that sweep never runs again for the life of
 * the JVM, such a file was never swept again automatically -- it sat on disk until an admin ran
 * EM Clean Up or the node restarted again.
 * <p>
 * This task re-applies the exact same, unmodified check -- it does not shorten, skip, or
 * otherwise weaken {@link XSwapper#SWAP_FILE_GRACE_PERIOD} -- just on a short recurring
 * schedule, so a file skipped once gets another look a few minutes later instead of never. It
 * deliberately does not call {@link FileSystemService#clearCacheFiles}, which also publishes a
 * {@code ClearCacheFilesEvent} that rebuilds the entire i18n cache directory under a write lock
 * on every call; that cost is fine once at startup or on a rare manual Clean Up, but would be a
 * real, unnecessary recurring cost if paid every few minutes forever.
 */
public class SwapFileSweepRunnable extends TimedQueue.TimedRunnable {
   public SwapFileSweepRunnable() {
      super(SWEEP_INTERVAL);
   }

   @Override
   public boolean isRecurring() {
      return true;
   }

   @Override
   public void run() {
      try {
         FileSystemService fileSystemService = FileSystemService.getInstance();
         File dir = fileSystemService.getFile(fileSystemService.getCacheDirectory());

         if(dir.isDirectory()) {
            XSwapper.getSwapper().sweepDeadSeedSwapFiles(dir);
         }
      }
      catch(IOException e) {
         LOG.debug("Unable to resolve swap cache directory for the recurring sweep", e);
      }
      catch(Exception e) {
         LOG.debug("Unable to run the recurring swap file sweep, cluster may be stopped", e);
      }
   }

   // Bug #78245, a few minutes: comfortably above SWAP_FILE_GRACE_PERIOD (60 s) so a file is
   // never evaluated and immediately re-evaluated for no reason, but short enough that a file
   // missed by the startup sweep is cleaned up in minutes instead of waiting for a manual EM
   // Clean Up or another restart.
   private static final long SWEEP_INTERVAL = 180000L; // 3 min

   private static final Logger LOG = LoggerFactory.getLogger(SwapFileSweepRunnable.class);
}
