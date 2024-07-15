--
-- inetsoft-cassandra - StyleBI is a business intelligence web application.
-- Copyright © 2024 InetSoft Technology (info@inetsoft.com)
--
-- This program is free software: you can redistribute it and/or modify
-- it under the terms of the GNU Affero General Public License as published by
-- the Free Software Foundation, either version 3 of the License, or
-- (at your option) any later version.
--
-- This program is distributed in the hope that it will be useful,
-- but WITHOUT ANY WARRANTY; without even the implied warranty of
-- MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
-- GNU Affero General Public License for more details.
--
-- You should have received a copy of the GNU Affrero General Public License
-- along with this program. If not, see <http://www.gnu.org/licenses/>.
--

/*
 * This file is part of StyleBI.
 *
 * Copyright (c) 2024, InetSoft Technology Corp, All Rights Reserved.
 *
 * The software and information contained herein are copyrighted and
 * proprietary to InetSoft Technology Corp. This software is furnished
 * pursuant to a written license agreement and may be used, copied,
 * transmitted, and stored only in accordance with the terms of such
 * license and with the inclusion of the above copyright notice. Please
 * refer to the file "COPYRIGHT" for further copyright and licensing
 * information. This software and information or any other copies
 * thereof may not be provided or otherwise made available to any other
 * person.
 */

CREATE KEYSPACE mykeyspace WITH REPLICATION = { 'class' : 'SimpleStrategy', 'replication_factor' : 1 };
USE mykeyspace;
CREATE TABLE users (user_id int PRIMARY KEY, fname text, lname text);
INSERT INTO users (user_id,  fname, lname) VALUES (1745, 'john', 'smith');
INSERT INTO users (user_id,  fname, lname) VALUES (1744, 'john', 'doe');
INSERT INTO users (user_id,  fname, lname) VALUES (1746, 'john', 'smith');
SELECT COUNT(*) from users;
