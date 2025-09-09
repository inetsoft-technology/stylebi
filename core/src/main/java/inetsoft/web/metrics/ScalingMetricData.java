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

public record ScalingMetricData(double jvmCpu, double jvmCpuDetail,
                                double jvmMemory, double jvmMemoryDetail,
                                double systemCpu, double systemCpuDetail,
                                double systemMemory, double systemMemoryDetail,
                                double scheduler, double schedulerDetail,
                                double cacheSwapMemory, double cacheSwapMemoryDetail,
                                double cacheSwapWait, double cacheSwapWaitDetail,
                                double serverUtilization)
{
}
