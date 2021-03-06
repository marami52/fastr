/*
 * This material is distributed under the GNU General Public License
 * Version 2. You may review the terms of this license at
 * http://www.gnu.org/licenses/gpl-2.0.html
 *
 * Copyright (c) 2014, Purdue University
 * Copyright (c) 2014, 2017, Oracle and/or its affiliates
 *
 * All rights reserved.
 */
package com.oracle.truffle.r.test.builtins;

import org.junit.Test;

import com.oracle.truffle.r.test.TestBase;

// Checkstyle: stop line length check
public class TestBuiltin_gamma extends TestBase {

    @Test
    public void testgamma1() {
        assertEval("argv <- list(c(0, 0.1, 0.2, 0.3, 0.4, 0.5, 0.6, 0.7, 0.8, 0.9, 1));gamma(argv[[1]]);");
    }

    @Test
    public void testgamma2() {
        assertEval("argv <- list(FALSE);gamma(argv[[1]]);");
    }

    @Test
    public void testgamma3() {
        // FIXME RInternalError: not implemented: .Internal beta
        assertEval(Ignored.Unimplemented, "argv <- list(structure(numeric(0), .Dim = c(0L, 0L)));gamma(argv[[1]]);");
    }

    @Test
    public void testgamma5() {
        assertEval("argv <- list(101);gamma(argv[[1]]);");
    }

    @Test
    public void testgamma6() {
        assertEval("argv <- list(c(-3.000001, -3, -3, -2.999999, -2.965, -2.93, -2.895, -2.86, -2.825, -2.79, -2.755, -2.72, -2.685, -2.65, -2.615, -2.58, -2.545, -2.51, -2.475, -2.44, -2.405, -2.37, -2.335, -2.3, -2.265, -2.23, -2.195, -2.16, -2.125, -2.09, -2.055, -2.02, -2.000001, -2, -1.999999, -1.985, -1.95, -1.915, -1.88, -1.845, -1.81, -1.775, -1.74, -1.705, -1.67, -1.635, -1.6, -1.565, -1.53, -1.495, -1.46, -1.425, -1.39, -1.355, -1.32, -1.285, -1.25, -1.215, -1.18, -1.145, -1.11, -1.075, -1.04, -1.005, -1.000001, -1, -0.999999, -0.97, -0.935, -0.9, -0.865, -0.83, -0.795, -0.76, -0.725, -0.69, -0.655, -0.62, -0.585, -0.55, -0.515, -0.48, -0.445, -0.41, -0.375, -0.34, -0.305, -0.27, -0.235, -0.2, -0.165, -0.13, -0.0949999999999998, -0.0599999999999996, -0.0249999999999999, -1e-06, 0, 1e-06, 0.0100000000000002, 0.0450000000000004, 0.0800000000000001, 0.115, 0.15, 0.185, 0.22, 0.255, 0.29, 0.325, 0.36, 0.395, 0.43, 0.465, 0.5, 0.535, 0.57, 0.605, 0.640000000000001, 0.675, 0.71, 0.745000000000001, 0.78, 0.815, 0.850000000000001, 0.885, 0.92, 0.955000000000001, 0.99, 1.025, 1.06, 1.095, 1.13, 1.165, 1.2, 1.235, 1.27, 1.305, 1.34, 1.375, 1.41, 1.445, 1.48, 1.515, 1.55, 1.585, 1.62, 1.655, 1.69, 1.725, 1.76, 1.795, 1.83, 1.865, 1.9, 1.935, 1.97, 2.005, 2.04, 2.075, 2.11, 2.145, 2.18, 2.215, 2.25, 2.285, 2.32, 2.355, 2.39, 2.425, 2.46, 2.495, 2.53, 2.565, 2.6, 2.635, 2.67, 2.705, 2.74, 2.775, 2.81, 2.845, 2.88, 2.915, 2.95, 2.985, 3.02, 3.055, 3.09, 3.125, 3.16, 3.195, 3.23, 3.265, 3.3, 3.335, 3.37, 3.405, 3.44, 3.475, 3.51, 3.545, 3.58, 3.615, 3.65, 3.685, 3.72, 3.755, 3.79, 3.825, 3.86, 3.895, 3.93, 3.965, 4));gamma(argv[[1]]);");
    }
}
