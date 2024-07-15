/*
 * This file is part of StyleBI.
 * Copyright (C) 2024  InetSoft Technology
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

--
-- inetsoft-core - StyleBI is a business intelligence web application.
-- Copyright © 2024 InetSoft Technology (info@inetsoft.com)
--
-- This program is free software: you can redistribute it and/or modify
-- it under the terms of the GNU General Public License as published by
-- the Free Software Foundation, either version 3 of the License, or
-- (at your option) any later version.
--
-- This program is distributed in the hope that it will be useful,
-- but WITHOUT ANY WARRANTY; without even the implied warranty of
-- MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
-- GNU General Public License for more details.
--
-- You should have received a copy of the GNU General Public License
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

/*
 * #%L
 * This file is part of StyleBI.
 * %%
 * Copyright (c) 2024, InetSoft Technology Corp, All Rights Reserved.
 * %%
 * The software and information contained herein are copyrighted and
 * proprietary to InetSoft Technology Corp. This software is furnished
 * pursuant to a written license agreement and may be used, copied,
 * transmitted, and stored only in accordance with the terms of such
 * license and with the inclusion of the above copyright notice. Please
 * refer to the file "COPYRIGHT" for further copyright and licensing
 * information. This software and information or any other copies
 * thereof may not be provided or otherwise made available to any other
 * person.
 * #L%
 */

/*
 * #%L
 * Copyright (c) 2024, InetSoft Technology Corp, All Rights Reserved.
 * %%
 * The software and information contained herein are copyrighted and
 * proprietary to InetSoft Technology Corp. This software is furnished
 * pursuant to a written license agreement and may be used, copied,
 * transmitted, and stored only in accordance with the terms of such
 * license and with the inclusion of the above copyright notice. Please
 * refer to the file "COPYRIGHT" for further copyright and licensing
 * information. This software and information or any other copies
 * thereof may not be provided or otherwise made available to any other
 * person.
 * #L%
 */

/*
 * Copyright (c) 2021, InetSoft Technology Corp, All Rights Reserved.
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

-- H2 SQL to produce inputs and expected output for JoinTableLensTest.Bug46758InnerJoin

CREATE TABLE MVORDERS (
    ORDERNO INTEGER PRIMARY KEY,
    CUSTOMER_ID INTEGER,
    DISCOUNT DOUBLE,
    ORDERDATE TIMESTAMP
);

INSERT INTO MVORDERS(ORDERNO, CUSTOMER_ID, DISCOUNT, ORDERDATE) VALUES
(1, 11, 0.0, TIMESTAMP '2001-12-15 00:00:00.0'),
(2, 2, 0.0, TIMESTAMP '2001-07-01 00:00:00.0'),
(3, 2, 0.0, TIMESTAMP '2002-06-21 00:00:00.0'),
(4, 8, 0.0, TIMESTAMP '2002-08-22 00:00:00.0'),
(5, 8, 0.05, TIMESTAMP '2003-03-25 00:00:00.0'),
(6, 6, 0.05, TIMESTAMP '2003-07-09 00:00:00.0'),
(7, NULL, 0.0, TIMESTAMP '2002-09-01 00:00:00.0'),
(8, 13, 0.0, TIMESTAMP '2001-06-03 00:00:00.0'),
(9, 3, 0.0, TIMESTAMP '2003-12-19 00:00:00.0'),
(10, 11, 0.0, TIMESTAMP '2003-01-25 00:00:00.0'),
(11, 3, 0.0, TIMESTAMP '2003-11-08 00:00:00.0'),
(12, 4, NULL, TIMESTAMP '2002-11-02 00:00:00.0'),
(13, 9, 0.0, TIMESTAMP '2002-05-08 00:00:00.0'),
(14, 16, 0.0, TIMESTAMP '2001-02-14 00:00:00.0'),
(15, 10, 0.0, TIMESTAMP '2003-08-25 00:00:00.0'),
(16, 17, 0.1, TIMESTAMP '2002-10-15 00:00:00.0'),
(17, 3, 0.08, TIMESTAMP '2003-02-18 00:00:00.0'),
(18, 19, 0.0, TIMESTAMP '2001-05-01 00:00:00.0'),
(19, 7, 0.02, TIMESTAMP '2003-12-27 00:00:00.0'),
(20, 8, 0.0, TIMESTAMP '2002-06-04 00:00:00.0'),
(21, 13, 0.0, TIMESTAMP '2001-08-23 00:00:00.0'),
(22, 8, 0.0, NULL),
(23, 4, 0.03, TIMESTAMP '2003-08-05 00:00:00.0'),
(24, 4, 0.02, TIMESTAMP '2002-01-11 00:00:00.0'),
(25, 19, 0.05, TIMESTAMP '2003-03-27 00:00:00.0'),
(26, 5, 0.0, TIMESTAMP '2002-09-26 00:00:00.0'),
(27, 13, 0.0, TIMESTAMP '2002-05-20 00:00:00.0'),
(28, 8, 0.0, TIMESTAMP '2003-05-16 00:00:00.0'),
(29, 10, 0.0, TIMESTAMP '2002-10-06 00:00:00.0'),
(30, 3, 0.3, TIMESTAMP '2002-09-14 00:00:00.0'),
(31, 14, NULL, TIMESTAMP '2002-10-20 00:00:00.0'),
(32, 3, 0.0, TIMESTAMP '2002-07-18 00:00:00.0'),
(33, 6, 0.0, TIMESTAMP '2001-05-12 00:00:00.0'),
(34, 4, 0.15, TIMESTAMP '2003-01-11 00:00:00.0'),
(35, 17, 0.01, TIMESTAMP '2001-03-12 00:00:00.0'),
(36, 6, 0.0, TIMESTAMP '2001-10-12 00:00:00.0'),
(37, 17, 0.0, TIMESTAMP '2001-10-15 00:00:00.0'),
(38, 4, 0.0, NULL),
(39, 5, 0.0, TIMESTAMP '2002-10-12 00:00:00.0'),
(40, 5, 0.15, TIMESTAMP '2001-06-04 00:00:00.0'),
(41, 9, 0.0, TIMESTAMP '2001-05-17 00:00:00.0'),
(42, 19, 0.0, TIMESTAMP '2001-07-05 00:00:00.0'),
(43, 6, 0.0, TIMESTAMP '2003-04-07 00:00:00.0'),
(44, 18, 0.0, TIMESTAMP '2003-09-26 00:00:00.0'),
(45, 10, 0.0, TIMESTAMP '2002-11-26 00:00:00.0'),
(46, 4, 0.0, TIMESTAMP '2002-04-12 00:00:00.0'),
(47, 14, 0.4, TIMESTAMP '2002-04-27 00:00:00.0'),
(48, 20, 0.0, TIMESTAMP '2003-10-28 00:00:00.0'),
(49, 15, 0.0, TIMESTAMP '2003-03-11 00:00:00.0'),
(50, 7, 0.0, TIMESTAMP '2003-12-16 00:00:00.0'),
(51, 15, 0.0, TIMESTAMP '2003-09-09 00:00:00.0'),
(52, 15, 0.0, TIMESTAMP '2003-04-21 00:00:00.0'),
(53, 10, 0.0, NULL),
(54, 18, 0.0, TIMESTAMP '2003-03-22 00:00:00.0'),
(55, 20, 0.05, TIMESTAMP '2003-07-28 00:00:00.0'),
(56, 16, 0.0, TIMESTAMP '2002-01-07 00:00:00.0'),
(57, 7, 0.0, TIMESTAMP '2001-06-09 00:00:00.0'),
(58, 13, NULL, TIMESTAMP '2001-02-22 00:00:00.0'),
(59, 7, 0.0, TIMESTAMP '2003-08-04 00:00:00.0'),
(60, 20, 0.0, TIMESTAMP '2003-09-23 00:00:00.0'),
(61, 9, 0.0, TIMESTAMP '2003-02-18 00:00:00.0'),
(62, 10, 0.0, TIMESTAMP '2001-06-22 00:00:00.0'),
(63, 9, 0.0, TIMESTAMP '2001-01-27 00:00:00.0'),
(64, 20, 0.0, TIMESTAMP '2002-12-04 00:00:00.0'),
(65, 9, 0.0, TIMESTAMP '2002-03-08 00:00:00.0'),
(66, 20, 0.0, TIMESTAMP '2003-08-13 00:00:00.0'),
(67, 10, 0.25, TIMESTAMP '2003-04-02 00:00:00.0'),
(68, 1, 0.0, TIMESTAMP '2001-01-13 00:00:00.0'),
(69, 6, 0.0, TIMESTAMP '2001-09-22 00:00:00.0'),
(70, 20, 0.0, TIMESTAMP '2002-04-08 00:00:00.0'),
(71, 9, 0.0, TIMESTAMP '2003-07-10 00:00:00.0'),
(72, 1, 0.0, TIMESTAMP '2002-06-24 00:00:00.0'),
(73, 18, 0.0, TIMESTAMP '2003-12-26 00:00:00.0'),
(74, 14, 0.0, TIMESTAMP '2002-02-06 00:00:00.0'),
(75, 6, 0.0, TIMESTAMP '2001-11-25 00:00:00.0'),
(76, 5, 0.35, TIMESTAMP '2002-07-05 00:00:00.0'),
(77, 10, 0.0, TIMESTAMP '2001-03-21 00:00:00.0'),
(78, 15, 0.0, TIMESTAMP '2002-01-09 00:00:00.0'),
(79, 18, 0.0, TIMESTAMP '2002-02-10 00:00:00.0'),
(80, 1, 0.0, TIMESTAMP '2001-10-24 00:00:00.0'),
(81, 13, 0.06, TIMESTAMP '2001-07-03 00:00:00.0'),
(82, 3, 0.15, TIMESTAMP '2002-12-02 00:00:00.0'),
(83, 13, 0.0, TIMESTAMP '2001-11-08 00:00:00.0');

INSERT INTO MVORDERS(ORDERNO, CUSTOMER_ID, DISCOUNT, ORDERDATE) VALUES
(84, 5, 0.0, TIMESTAMP '2001-04-02 00:00:00.0'),
(85, 18, 0.0, TIMESTAMP '2002-11-06 00:00:00.0'),
(86, 2, 0.0, TIMESTAMP '2001-12-15 00:00:00.0'),
(87, 15, 0.0, TIMESTAMP '2001-06-07 00:00:00.0'),
(88, 9, 0.3, TIMESTAMP '2002-06-11 00:00:00.0'),
(89, 7, 0.0, TIMESTAMP '2002-05-15 00:00:00.0'),
(90, 14, 0.0, TIMESTAMP '2001-07-02 00:00:00.0'),
(91, 13, 0.0, TIMESTAMP '2002-08-09 00:00:00.0'),
(92, 10, 0.0, TIMESTAMP '2002-07-07 00:00:00.0'),
(93, 14, 0.0, TIMESTAMP '2001-01-27 00:00:00.0'),
(94, 14, 0.0, TIMESTAMP '2001-11-27 00:00:00.0'),
(95, 9, 0.0, TIMESTAMP '2003-03-28 00:00:00.0'),
(96, 6, 0.35, TIMESTAMP '2001-03-03 00:00:00.0'),
(97, 16, 0.0, TIMESTAMP '2001-04-04 00:00:00.0'),
(98, 12, 0.0, TIMESTAMP '2002-01-19 00:00:00.0'),
(99, 3, 0.0, TIMESTAMP '2002-10-27 00:00:00.0'),
(100, 17, 0.0, TIMESTAMP '2001-07-13 00:00:00.0'),
(101, 1, 0.04, TIMESTAMP '2001-06-06 00:00:00.0'),
(102, 19, 0.0, TIMESTAMP '2003-05-04 00:00:00.0'),
(103, 8, 0.0, TIMESTAMP '2002-07-18 00:00:00.0'),
(104, 17, 0.0, TIMESTAMP '2001-02-13 00:00:00.0'),
(105, 7, 0.0, TIMESTAMP '2002-08-17 00:00:00.0'),
(106, 19, 0.0, TIMESTAMP '2002-08-09 00:00:00.0'),
(107, 6, 0.0, TIMESTAMP '2003-11-14 00:00:00.0'),
(108, 14, 0.0, TIMESTAMP '2001-06-14 00:00:00.0'),
(109, 15, 0.0, TIMESTAMP '2002-10-06 00:00:00.0'),
(110, 3, 0.0, TIMESTAMP '2002-06-28 00:00:00.0'),
(111, 13, 0.05, TIMESTAMP '2002-10-26 00:00:00.0'),
(112, 1, 0.03, TIMESTAMP '2003-05-06 00:00:00.0'),
(113, 12, 0.0, TIMESTAMP '2001-01-24 00:00:00.0'),
(114, 2, 0.0, TIMESTAMP '2003-03-25 00:00:00.0'),
(115, 1, 0.0, TIMESTAMP '2003-08-05 00:00:00.0'),
(116, 5, 0.0, TIMESTAMP '2003-07-20 00:00:00.0'),
(117, 14, 0.0, TIMESTAMP '2001-03-22 00:00:00.0'),
(118, 13, 0.0, TIMESTAMP '2003-01-28 00:00:00.0'),
(119, 2, 0.0, TIMESTAMP '2003-12-20 00:00:00.0'),
(120, 20, 0.2, TIMESTAMP '2002-10-28 00:00:00.0');

-- left table input

CREATE OR REPLACE VIEW INNER_JOIN_TEST_LEFT AS
SELECT CUSTOMER_ID, DISCOUNT, ORDERDATE, ORDERNO
FROM MVORDERS
WHERE ORDERDATE > '2001-10-01 00:00:00';

CALL CSVWRITE ('bug-46758-inner-left.csv', 'SELECT * FROM INNER_JOIN_TEST_LEFT');

-- right table input

CREATE OR REPLACE VIEW INNER_JOIN_TEST_RIGHT AS
SELECT CUSTOMER_ID, DISCOUNT, ORDERDATE, ORDERNO
FROM MVORDERS
WHERE ORDERDATE < '2003-010-01 00:00:00';

CALL CSVWRITE ('bug-46758-inner-right.csv', 'SELECT * FROM INNER_JOIN_TEST_RIGHT');

-- expected output

CREATE OR REPLACE VIEW INNER_JOIN_TEST AS
(
SELECT l.*, r.*
FROM (SELECT CUSTOMER_ID AS CUSTOMER_ID_1,
             DISCOUNT    AS DISCOUNT_1,
             ORDERDATE   AS ORDERDATE_1,
             ORDERNO     AS ORDERNO_1
      FROM MVORDERS
      WHERE ORDERDATE > '2001-10-01 00:00:00') l
         INNER JOIN (SELECT CUSTOMER_ID, DISCOUNT, ORDERDATE, ORDERNO
                     FROM MVORDERS
                     WHERE ORDERDATE < '2003-10-01 00:00:00') r
                    ON l.ORDERDATE_1 = r.ORDERDATE);

CALL CSVWRITE ('bug-46758-inner-expected.csv', 'SELECT * FROM INNER_JOIN_TEST');
