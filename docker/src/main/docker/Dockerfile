#
# inetsoft-docker-community - StyleBI is a business intelligence web application.
# Copyright © 2024 InetSoft Technology (info@inetsoft.com)
#
# This program is free software: you can redistribute it and/or modify
# it under the terms of the GNU General Public License as published by
# the Free Software Foundation, either version 3 of the License, or
# (at your option) any later version.
#
# This program is distributed in the hope that it will be useful,
# but WITHOUT ANY WARRANTY; without even the implied warranty of
# MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
# GNU General Public License for more details.
#
# You should have received a copy of the GNU General Public License
# along with this program. If not, see <http://www.gnu.org/licenses/>.
#

FROM eclipse-temurin:21.0.7_6-jdk

ENV JAVA_MEMORY_OPTS='-XX:MaxRAMPercentage=50.0'
ENV JAVA_GC_OPTS='-XX:+AlwaysPreTouch -XX:+UseG1GC -XX:+ScavengeBeforeFullGC -XX:+DisableExplicitGC'
ENV JAVA_LOCALE_OPTS='-Duser.language=en -Duser.country=US -Djava.locale.providers=COMPAT,SPI'
ENV JAVA_CODECACHE_OPTS='-XX:InitialCodeCacheSize=100M -XX:ReservedCodeCacheSize=200m -XX:+UseCodeCacheFlushing'
ENV JAVA_NETWORK_OPTS='-Djava.net.preferIPv4Stack=true'
ENV JAVA_OPTS='-server $JAVA_MEMORY_OPTS $JAVA_GC_OPTS $JAVA_CODECACHE_OPTS $JAVA_LOCALE_OPTS $JAVA_NETWORK_OPTS -Djava.io.tmpdir=/var/lib/inetsoft/temp -Dinetsoft.log.dir=/var/lib/inetsoft/logs -Dinetsoft.metadata.dir=/var/lib/inetsoft/metadata'
ENV INETSOFTENV_FONT_TRUETYPE_PATH='/usr/share/fonts/truetype/;$(sree.home)/fonts'
ENV JAVA_CLASSPATH=''

RUN apt-get update \
  && DEBIAN_FRONTEND=noninteractive apt-get install -y --no-install-recommends fonts-dejavu \
  fonts-liberation2 fonts-croscore fonts-freefont-ttf fonts-roboto gettext-base fontconfig \
  && DEBIAN_FRONTEND=noninteractive apt-get upgrade -y ca-certificates \
  && rm -rf /var/cache/apt/* \
  && rm -rf /var/lib/apt/lists/* \
  && userdel -rf ubuntu \
  && groupadd -g 1000 inetsoft \
  && useradd -u 1000 -g 1000 -d /var/lib/inetsoft -s /bin/bash -m inetsoft \
  && mkdir -p /usr/local/inetsoft/classes \
  && chown -R inetsoft:inetsoft /usr/local/inetsoft \
  && mkdir -p /var/lib/inetsoft/config \
  && mkdir -p /var/lib/inetsoft/temp \
  && chown -R inetsoft:inetsoft /var/lib/inetsoft \
  && fc-cache

EXPOSE 1099
EXPOSE 5701
EXPOSE 8080
EXPOSE 8081

WORKDIR /var/lib/inetsoft
USER inetsoft
