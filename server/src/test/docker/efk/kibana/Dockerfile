#
# inetsoft-server - StyleBI is a business intelligence web application.
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

FROM docker.elastic.co/kibana/kibana-oss:7.10.2
COPY --chmod=755 kibana-docker /usr/local/bin/kibana-docker
COPY --chmod=644 restore.ndjson /usr/share/kibana/restore.ndjson
CMD ["/usr/local/bin/kibana-docker"]
