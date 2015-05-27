/*
 * This material is distributed under the GNU General Public License
 * Version 2. You may review the terms of this license at
 * http://www.gnu.org/licenses/gpl-2.0.html
 *
 * Copyright (c) 2014, Purdue University
 * Copyright (c) 2014, 2015, Oracle and/or its affiliates
 *
 * All rights reserved.
 */
package com.oracle.truffle.r.test.builtins;

import org.junit.*;

import com.oracle.truffle.r.test.*;

// Checkstyle: stop line length check

public class TestBuiltin_extract_parentasis_dataframe extends TestBase {

    @Test
    public void testextract_parentasis_dataframe1() {
        assertEval(Ignored.Unknown,
                        "argv <- structure(list(x = structure(list(ID = c(13, 41, 121,     202, 247, 292, 415, 492), Location = c(0.15998329123474,     0.533277637449134, 1.5998329123474, 2.6797201281819, 3.27965747031217,     3.87959481244245, 5.51942354759854, 6.54598299968812), Peak_Value = c(0.997547264684804,     0.949162789397664, 0.990440013891923, 0.973478735915337,     0.93861267739627, 0.957347289323235, 0.924803043529451, 0.968307855031101)),     .Names = c('ID', 'Location', 'Peak_Value'), row.names = c(NA,         -8L), class = 'data.frame'), i = 2), .Names = c('x',     'i'));"
                                        + "do.call('[.data.frame', argv)");
    }

    @Test
    public void testextract_parentasis_dataframe2() {
        assertEval(Ignored.Unknown,
                        "argv <- structure(list(x = structure(list(Satellites = c(8L,     0L, 9L, 0L, 4L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 11L, 0L, 14L,     8L, 1L, 1L, 0L, 5L, 4L, 3L, 1L, 2L, 3L, 0L, 3L, 5L, 0L, 0L,     4L, 0L, 0L, 8L, 5L, 0L, 0L, 6L, 0L, 6L, 3L, 5L, 6L, 5L, 9L,     4L, 6L, 4L, 3L, 3L, 5L, 5L, 6L, 4L, 5L, 15L, 3L, 3L, 0L,     0L, 0L, 5L, 3L, 5L, 1L, 8L, 10L, 0L, 0L, 3L, 7L, 1L, 0L,     6L, 0L, 0L, 3L, 4L, 0L, 5L, 0L, 0L, 0L, 4L, 0L, 3L, 0L, 0L,     0L, 0L, 5L, 0L, 0L, 0L, 0L, 1L, 0L, 1L, 1L, 1L, 1L, 1L, 1L,     4L, 1L, 1L, 1L, 1L, 2L, 4L, 3L, 6L, 0L, 2L, 2L, 0L, 12L,     0L, 5L, 6L, 6L, 2L, 0L, 2L, 3L, 0L, 3L, 4L, 2L, 6L, 6L, 0L,     4L, 10L, 7L, 0L, 5L, 5L, 6L, 6L, 7L, 3L, 3L, 0L, 0L, 8L,     4L, 4L, 10L, 9L, 4L, 0L, 0L, 0L, 0L, 4L, 0L, 2L, 0L, 4L,     4L, 3L, 8L, 0L, 7L, 0L, 0L, 2L, 3L, 4L, 0L, 0L, 0L), Width = c(28.3,     22.5, 26, 24.8, 26, 23.8, 26.5, 24.7, 23.7, 25.6, 24.3, 25.8,     28.2, 21, 26, 27.1, 25.2, 29, 24.7, 27.4, 23.2, 25, 22.5,     26.7, 25.8, 26.2, 28.7, 26.8, 27.5, 24.9, 29.3, 25.8, 25.7,     25.7, 26.7, 23.7, 26.8, 27.5, 23.4, 27.9, 27.5, 26.1, 27.7,     30, 28.5, 28.9, 28.2, 25, 28.5, 30.3, 24.7, 27.7, 27.4, 22.9,     25.7, 28.3, 27.2, 26.2, 27.8, 25.5, 27.1, 24.5, 27, 26, 28,     30, 29, 26.2, 26.5, 26.2, 25.6, 23, 23, 25.4, 24.2, 22.9,     26, 25.4, 25.7, 25.1, 24.5, 27.5, 23.1, 25.9, 25.8, 27, 28.5,     25.5, 23.5, 24, 29.7, 26.8, 26.7, 28.7, 23.1, 29, 25.5, 26.5,     24.5, 28.5, 28.2, 24.5, 27.5, 24.7, 25.2, 27.3, 26.3, 29,     25.3, 26.5, 27.8, 27, 25.7, 25, 31.9, 23.7, 29.3, 22, 25,     27, 23.8, 30.2, 26.2, 24.2, 27.4, 25.4, 28.4, 22.5, 26.2,     24.9, 24.5, 25.1, 28, 25.8, 27.9, 24.9, 28.4, 27.2, 25, 27.5,     33.5, 30.5, 29, 24.3, 25.8, 25, 31.7, 29.5, 24, 30, 27.6,     26.2, 23.1, 22.9, 24.5, 24.7, 28.3, 23.9, 23.8, 29.8, 26.5,     26, 28.2, 25.7, 26.5, 25.8, 24.1, 26.2, 26.1, 29, 28, 27,     24.5), Dark = structure(c(1L, 2L, 1L, 2L, 2L, 1L, 1L, 2L,     1L, 2L, 2L, 1L, 1L, 2L, 1L, 1L, 1L, 1L, 2L, 1L, 1L, 1L, 1L,     2L, 2L, 2L, 1L, 1L, 2L, 1L, 1L, 1L, 1L, 1L, 1L, 2L, 1L, 1L,     2L, 1L, 2L, 1L, 1L, 1L, 2L, 2L, 1L, 1L, 1L, 1L, 2L, 1L, 1L,     1L, 1L, 1L, 1L, 2L, 1L, 2L, 2L, 2L, 2L, 1L, 1L, 1L, 1L, 1L,     1L, 1L, 2L, 2L, 2L, 1L, 2L, 1L, 2L, 1L, 2L, 1L, 2L, 2L, 2L,     2L, 1L, 2L, 1L, 2L, 2L, 1L, 1L, 1L, 2L, 1L, 2L, 1L, 2L, 2L,     2L, 2L, 1L, 1L, 1L, 1L, 1L, 2L, 1L, 1L, 1L, 1L, 1L, 1L, 2L,     1L, 1L, 2L, 2L, 2L, 1L, 2L, 2L, 1L, 2L, 1L, 1L, 1L, 2L, 2L,     1L, 1L, 1L, 1L, 1L, 2L, 1L, 1L, 1L, 2L, 1L, 1L, 1L, 1L, 2L,     1L, 1L, 2L, 1L, 1L, 2L, 1L, 1L, 1L, 1L, 1L, 2L, 1L, 1L, 1L,     2L, 2L, 1L, 1L, 1L, 2L, 1L, 1L, 2L, 2L, 2L, 2L, 1L, 2L, 1L),     .Label = c('no', 'yes'), class = 'factor'), GoodSpine = structure(c(1L,     1L, 2L, 1L, 1L, 1L, 2L, 2L, 2L, 1L, 1L, 1L, 1L, 2L, 2L, 2L,     1L, 1L, 1L, 1L, 2L, 2L, 2L, 1L, 1L, 1L, 1L, 2L, 1L, 1L, 2L,     1L, 2L, 2L, 2L, 1L, 1L, 1L, 1L, 1L, 1L, 2L, 2L, 2L, 2L, 1L,     1L, 1L, 1L, 2L, 1L, 1L, 2L, 1L, 2L, 1L, 1L, 1L, 2L, 1L, 1L,     1L, 2L, 1L, 1L, 1L, 1L, 1L, 2L, 1L, 1L, 1L, 1L, 1L, 1L, 2L,     2L, 1L, 1L, 1L, 2L, 1L, 1L, 2L, 1L, 1L, 1L, 2L, 1L, 2L, 2L,     2L, 1L, 2L, 1L, 2L, 1L, 1L, 1L, 1L, 1L, 1L, 1L, 2L, 2L, 1L,     1L, 1L, 1L, 1L, 1L, 1L, 1L, 1L, 1L, 1L, 1L, 1L, 1L, 1L, 1L,     2L, 1L, 1L, 1L, 2L, 1L, 1L, 1L, 2L, 2L, 1L, 2L, 1L, 1L, 1L,     2L, 1L, 2L, 1L, 2L, 1L, 1L, 2L, 1L, 1L, 2L, 1L, 1L, 1L, 1L,     1L, 2L, 2L, 1L, 1L, 1L, 1L, 1L, 2L, 1L, 1L, 1L, 1L, 1L, 1L,     1L, 1L, 1L, 1L, 2L, 1L, 2L), .Label = c('no', 'yes'), class = 'factor'),     Rep1 = c(2, 4, 5, 6, 6, 8, 9, 9, 10, 10, 11, 11, 13, 15,         15, 15, 15, 15, 17, 18, 19, 19, 19, 20, 20, 21, 21, 22,         23, 25, 25, 26, 27, 27, 28, 29, 29, 31, 33, 33, 36, 39,         40, 40, 41, 42, 43, 44, 45, 45, 49, 50, 51, 53, 55, 55,         56, 56, 56, 58, 59, 59, 60, 60, 62, 63, 64, 64, 64, 65,         66, 66, 67, 68, 70, 70, 71, 74, 75, 76, 76, 77, 79, 79,         79, 80, 80, 81, 82, 83, 83, 84, 87, 88, 88, 91, 92, 95,         97, 97, 97, 98, 98, 99, 100, 100, 101, 101, 103, 103,         103, 106, 107, 107, 111, 112, 112, 113, 113, 116, 116,         117, 117, 120, 122, 122, 122, 124, 125, 126, 127, 128,         128, 129, 130, 131, 133, 134, 134, 135, 141, 144, 146,         147, 147, 153, 153, 154, 154, 155, 155, 155, 156, 157,         157, 161, 163, 163, 164, 164, 164, 165, 167, 168, 168,         169, 170, 170, 170, 171, 171, 173, 173), Rep2 = c(2,         5, 6, 6, 8, 8, 9, 11, 12, 13, 13, 15, 15, 15, 16, 17,         17, 18, 19, 20, 23, 24, 24, 24, 25, 25, 26, 26, 27, 28,         29, 30, 30, 32, 33, 34, 38, 39, 39, 41, 42, 47, 48, 49,         49, 51, 54, 55, 55, 56, 57, 59, 59, 62, 63, 65, 67, 68,         69, 69, 70, 73, 75, 76, 76, 77, 78, 79, 81, 82, 83, 84,         85, 85, 85, 86, 87, 88, 89, 91, 92, 92, 92, 92, 96, 98,         98, 99, 100, 101, 101, 102, 103, 104, 104, 104, 105,         107, 107, 107, 108, 109, 109, 110, 111, 111, 111, 112,         112, 112, 113, 113, 115, 116, 117, 120, 122, 123, 123,         124, 124, 125, 125, 126, 128, 130, 131, 131, 131, 131,         132, 133, 133, 134, 134, 136, 137, 138, 139, 139, 141,         143, 144, 144, 145, 145, 150, 150, 150, 152, 152, 153,         154, 155, 155, 156, 157, 157, 158, 159, 160, 161, 163,         163, 166, 167, 169, 170, 172, 173, 173, 173, 173)), .Names = c('Satellites',     'Width', 'Dark', 'GoodSpine', 'Rep1', 'Rep2'), row.names = c(NA,     -173L), class = 'data.frame'), i = c(2, 4, 5, 6, 6, 8, 9,     9, 10, 10, 11, 11, 13, 15, 15, 15, 15, 15, 17, 18, 19, 19,     19, 20, 20, 21, 21, 22, 23, 25, 25, 26, 27, 27, 28, 29, 29,     31, 33, 33, 36, 39, 40, 40, 41, 42, 43, 44, 45, 45, 49, 50,     51, 53, 55, 55, 56, 56, 56, 58, 59, 59, 60, 60, 62, 63, 64,     64, 64, 65, 66, 66, 67, 68, 70, 70, 71, 74, 75, 76, 76, 77,     79, 79, 79, 80, 80, 81, 82, 83, 83, 84, 87, 88, 88, 91, 92,     95, 97, 97, 97, 98, 98, 99, 100, 100, 101, 101, 103, 103,     103, 106, 107, 107, 111, 112, 112, 113, 113, 116, 116, 117,     117, 120, 122, 122, 122, 124, 125, 126, 127, 128, 128, 129,     130, 131, 133, 134, 134, 135, 141, 144, 146, 147, 147, 153,     153, 154, 154, 155, 155, 155, 156, 157, 157, 161, 163, 163,     164, 164, 164, 165, 167, 168, 168, 169, 170, 170, 170, 171,     171, 173, 173), j = c(-5L, -6L)), .Names = c('x', 'i', 'j'));"
                                        + "do.call('[.data.frame', argv)");
    }

}
