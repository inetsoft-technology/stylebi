/*
 * inetsoft-r - StyleBI is a business intelligence web application.
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
var conn = R.connect(rServerHost, rServerPort, rServerUsername, rServerPassword);
conn.orders_table = ordersTable;
conn.customers_table = customersTable;
customer_mean = conn.runScript("joined_table <- merge(orders_table, customers_table, by='CustomerID')\n" +
"mean(joined_table$OrderID)");
var result = {
   mean: customer_mean,
   joined: conn.joined_table
};
result;


