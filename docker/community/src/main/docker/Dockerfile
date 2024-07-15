#
# inetsoft-docker-community - StyleBI is a business intelligence web application.
# Copyright © 2024 InetSoft Technology (info@inetsoft.com)
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
# You should have received a copy of the GNU Affrero General Public License
# along with this program. If not, see <http://www.gnu.org/licenses/>.
#

FROM ${docker.base.image}

ENV JAVA_MEMORY_OPTS='-XX:MaxRAMPercentage=75.0'
ENV JAVA_GC_OPTS='-XX:+UseZGC -XX:+ZGenerational'
ENV JAVA_LOCALE_OPTS='-Duser.language=en -Duser.country=US -Djava.locale.providers=COMPAT,SPI'
ENV JAVA_CODECACHE_OPTS='-XX:ReservedCodeCacheSize=100m -XX:+UseCodeCacheFlushing'
ENV JAVA_NETWORK_OPTS='-Djava.net.preferIPv4Stack=true'
ENV JAVA_OPTS='$JAVA_MEMORY_OPTS $JAVA_GC_OPTS $JAVA_CODECACHE_OPTS $JAVA_LOCALE_OPTS $JAVA_NETWORK_OPTS'
ENV JAVA_CLASSPATH=''

RUN apt-get update \
  && apt-get install -y --no-install-recommends fonts-dejavu fonts-liberation2 \
  fonts-croscore fonts-freefont-ttf fonts-roboto gettext-base fontconfig \
  && rm -rf /var/cache/apt/* \
  && rm -rf /var/lib/apt/lists/* \
  && groupadd -g 1000 inetsoft \
  && useradd -u 1000 -g 1000 -d /usr/local/inetsoft -s /bin/bash -m inetsoft \
  && mkdir -p /var/lib/inetsoft/config \
  && chown -R inetsoft:inetsoft /var/lib/inetsoft \
  && fc-cache

COPY --chown=inetsoft:inetsoft files/ /usr/local/inetsoft/
RUN chmod +x /usr/local/inetsoft/bin/*

EXPOSE 1099
EXPOSE 5701
EXPOSE 8080
EXPOSE 8081

WORKDIR /var/lib/inetsoft
USER inetsoft

ENTRYPOINT ["/usr/local/inetsoft/bin/entrypoint.sh"]
