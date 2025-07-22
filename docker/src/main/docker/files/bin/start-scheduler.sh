#!/usr/bin/env bash
#
# This file is part of StyleBI.
# Copyright (C) 2024  InetSoft Technology
#
# This program is free software: you can redistribute it and/or modify
# it under the terms of the GNU Affero General Public License as published by
# the Free Software Foundation, either version 3 of the License, or
# (at your option) any later version.
#
# This program is distributed in the hope that it will be useful,
# but WITHOUT ANY WARRANTY; without even the implied warranty of
# MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
# GNU Affero General Public License for more details.
#
# You should have received a copy of the GNU Affero General Public License
# along with this program.  If not, see <https://www.gnu.org/licenses/>.
#

set -e

echo "Starting InetSoft scheduler"

if [[ "$JAVA_OPTS" != "" ]]
then
    JAVA_OPTS=$(echo "$JAVA_OPTS" | envsubst)
fi

JAVA_CP="/usr/local/inetsoft/classes:/usr/local/inetsoft/libs/*"
JAVA_OPTS="$JAVA_OPTS \
-Dsree.home=/var/lib/inetsoft/config \
-Dlocal.ip.addr=$(hostname -i) \
-Drmi.localhost.ip=$(hostname -i) \
-Dinetsoft.host.ip="$INETSOFT_HOST_IP" \
-Dinetsoft.host.port="$INETSOFT_HOST_PORT" \
-Dinetsoft.host.outbound.port="$INETSOFT_HOST_OUTBOUND_PORT" \
-Djava.awt.headless=true \
-Djava.util.Arrays.useLegacyMergeSort=true \
-Dderby.system.home=/tmp \
-DinetsoftClusterDir=/var/lib/inetsoft/cluster \
--add-opens=java.base/jdk.internal.access=ALL-UNNAMED \
--add-opens=java.base/jdk.internal.misc=ALL-UNNAMED \
--add-opens=java.base/sun.nio.ch=ALL-UNNAMED \
--add-opens=java.base/sun.util.calendar=ALL-UNNAMED \
--add-opens=java.management/com.sun.jmx.mbeanserver=ALL-UNNAMED \
--add-opens=jdk.internal.jvmstat/sun.jvmstat.monitor=ALL-UNNAMED \
--add-opens=java.base/sun.reflect.generics.reflectiveObjects=ALL-UNNAMED \
--add-opens=jdk.management/com.sun.management.internal=ALL-UNNAMED \
--add-opens=java.base/java.io=ALL-UNNAMED \
--add-opens=java.base/java.nio=ALL-UNNAMED \
--add-opens=java.base/java.net=ALL-UNNAMED \
--add-opens=java.base/java.util=ALL-UNNAMED \
--add-opens=java.base/java.util.concurrent=ALL-UNNAMED \
--add-opens=java.base/java.util.concurrent.locks=ALL-UNNAMED \
--add-opens=java.base/java.util.concurrent.atomic=ALL-UNNAMED \
--add-opens=java.base/java.lang=ALL-UNNAMED \
--add-opens=java.base/java.lang.invoke=ALL-UNNAMED \
--add-opens=java.base/java.math=ALL-UNNAMED \
--add-opens=java.sql/java.sql=ALL-UNNAMED \
--add-opens=java.base/java.lang.reflect=ALL-UNNAMED \
--add-opens=java.base/java.time=ALL-UNNAMED \
--add-opens=java.base/java.text=ALL-UNNAMED \
--add-opens=java.management/sun.management=ALL-UNNAMED"

if [[ "$JAVA_CLASSPATH" != "" ]]
then
  JAVA_CP="$JAVA_CP:$JAVA_CLASSPATH"
fi

set -o noglob
exec java $JAVA_OPTS -classpath $JAVA_CP inetsoft.sree.schedule.ScheduleServer
