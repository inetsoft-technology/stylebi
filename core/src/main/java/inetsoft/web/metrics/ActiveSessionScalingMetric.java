/*
 * This file is part of StyleBI.
 * Copyright (C) 2025  InetSoft Technology
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

package inetsoft.web.metrics;

import inetsoft.sree.SreeEnv;
import inetsoft.web.session.IgniteSessionRepository;

public class ActiveSessionScalingMetric extends ScalingMetric {
   public ActiveSessionScalingMetric(IgniteSessionRepository sessionRepository) {
      super(false, 0);
      this.sessionRepository = sessionRepository;
   }

   @Override
   protected double calculate() {
      double activeValue = Double.parseDouble(
         SreeEnv.getProperty("metric.activeSession.metricValue", "0.5"));
      return Math.clamp(sessionRepository.getActiveSessions().isEmpty() ? 0D : activeValue, 0D, 1D);
   }

   private final IgniteSessionRepository sessionRepository;
}
