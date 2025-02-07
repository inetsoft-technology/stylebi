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

COMMAND=$1
shift

case "$COMMAND" in
  init)
    exec /usr/local/inetsoft/bin/init-storage.sh $@
    ;;
  server)
    exec /usr/local/inetsoft/bin/start-server.sh $@
    ;;
  scheduler)
    exec /usr/local/inetsoft/bin/start-scheduler.sh $@
    ;;
  shell)
    if [ ! -f /usr/local/inetsoft/bin/inetsoft-shell.sh ]; then
      echo "The InetSoft shell is only available in the enterprise version"
      exit 1
    fi
    exec /usr/local/inetsoft/bin/inetsoft-shell.sh $@
    ;;
  dsl)
    if [ ! -f /usr/local/inetsoft/bin/run-script.sh ]; then
      echo "The InetSoft DSL script is only available in the enterprise version"
      exit 1
    fi
    exec /usr/local/inetsoft/bin/run-script.sh $@
    ;;
  task)
    if [ ! -f /usr/local/inetsoft/bin/run-task.sh ]; then
      echo "The cloud task runner is only available in the enterprise version"
      exit 1
    fi
    exec /usr/local/inetsoft/bin/run-task.sh $@
    ;;
  *)
    echo "Command must be one of init, server, scheduler, shell, or task"
    exit 1
    ;;
esac
