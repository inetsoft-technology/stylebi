/*
 * inetsoft-mongodb - StyleBI is a business intelligence web application.
 * Copyright © 2024 InetSoft Technology (info@inetsoft.com)
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
 * You should have received a copy of the GNU Affrero General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
db.createCollection("table1", {company: "AT&T", state: "NJ", web: "www.att.com", revenue: 123000, phone: "732-123-8899"});
db.table1.insert({company: "AT&T", state: "NJ", web: "www.att.com", revenue: 123000, phone: "732-123-8899"});
db.table1.insert({company: "IBM", state: "NY", web: "www.ibm.com", revenue: 21099, phone: "212-388-8211"});
// db.createUser({ user: "root", pwd: "password", roles: [ { role: "userAdminAnyDatabase", db: "admin" } ] });
db.createUser({ user: "test", pwd: "password", roles: [ { role: "readWrite", db: "test" } ] });
db.createUser({ user: "user1", pwd: "password", roles: [ { role: "readWrite", db: "test" } ] });
