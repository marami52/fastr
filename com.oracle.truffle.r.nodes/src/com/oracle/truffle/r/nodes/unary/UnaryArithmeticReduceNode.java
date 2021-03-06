/*
 * Copyright (c) 2013, 2017, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */
package com.oracle.truffle.r.nodes.unary;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.dsl.TypeSystemReference;
import com.oracle.truffle.api.profiles.ConditionProfile;
import com.oracle.truffle.r.runtime.RError;
import com.oracle.truffle.r.runtime.RRuntime;
import com.oracle.truffle.r.runtime.data.RComplex;
import com.oracle.truffle.r.runtime.data.RComplexVector;
import com.oracle.truffle.r.runtime.data.RDoubleSequence;
import com.oracle.truffle.r.runtime.data.RDoubleVector;
import com.oracle.truffle.r.runtime.data.RIntSequence;
import com.oracle.truffle.r.runtime.data.RIntVector;
import com.oracle.truffle.r.runtime.data.RLogicalVector;
import com.oracle.truffle.r.runtime.data.RNull;
import com.oracle.truffle.r.runtime.data.RRaw;
import com.oracle.truffle.r.runtime.data.RRawVector;
import com.oracle.truffle.r.runtime.data.RStringVector;
import com.oracle.truffle.r.runtime.data.RTypes;
import com.oracle.truffle.r.runtime.nodes.RBaseNode;
import com.oracle.truffle.r.runtime.ops.BinaryArithmetic;
import com.oracle.truffle.r.runtime.ops.BinaryArithmeticFactory;
import com.oracle.truffle.r.runtime.ops.na.NACheck;

/**
 * This node is used at several places, but only 'range' actually uses the 'finite' parameter,
 * others should typically use {@code false} as its value. The 'finite' parameter is not handled
 * consistently in GnuR: the documentation reads ‘finite = TRUE’ _includes_ ‘na.rm = TRUE’, but this
 * only applies to some types (e.g. double or integer), for other types 'finite' seems to be ignored
 * (e.g. logical). The only situation where semantics of finite is different to na.rm is double
 * values: na.rm removes NA and NaN, but not -/+Inf.
 */
@TypeSystemReference(RTypes.class)
public abstract class UnaryArithmeticReduceNode extends RBaseNode {

    public abstract Object executeReduce(Object value, boolean naRm, boolean finite);

    @Child private MultiElemStringHandlerNode stringHandler;
    @Child private BinaryArithmetic arithmetic;

    private final BinaryArithmeticFactory factory;
    protected final ReduceSemantics semantics;

    private final NACheck na = NACheck.create();
    private final ConditionProfile naRmProfile = ConditionProfile.createBinaryProfile();

    protected UnaryArithmeticReduceNode(ReduceSemantics semantics, BinaryArithmeticFactory factory) {
        this.factory = factory;
        this.semantics = semantics;
        this.arithmetic = factory.createOperation();
    }

    private String handleString(RStringVector operand, boolean naRm, boolean finite, int offset) {
        if (stringHandler == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            stringHandler = insert(new MultiElemStringHandlerNode(semantics, factory, na));
        }
        return stringHandler.executeString(operand, naRm, finite, offset);
    }

    private void emptyWarning() {
        if (semantics.getEmptyWarning() != null) {
            warning(semantics.emptyWarning);
        }
    }

    private void naResultWarning() {
        if (semantics.getNAResultWarning() != null) {
            warning(semantics.getNAResultWarning());
        }
    }

    @SuppressWarnings("unused")
    @Specialization(guards = "semantics.isNullInt()")
    protected int doInt(RNull operand, boolean naRm, boolean finite) {
        emptyWarning();
        return semantics.getIntStart();
    }

    @SuppressWarnings("unused")
    @Specialization(guards = "!semantics.isNullInt()")
    protected double doDouble(RNull operand, boolean naRm, boolean finite) {
        emptyWarning();
        return semantics.getDoubleStart();
    }

    @Specialization
    protected int doInt(int operand, boolean naRm, @SuppressWarnings("unused") boolean finite) {
        na.enable(operand);
        if (naRmProfile.profile(naRm)) {
            if (na.check(operand)) {
                emptyWarning();
                return semantics.getIntStart();
            } else {
                return operand;
            }
        } else {
            return na.check(operand) ? RRuntime.INT_NA : operand;
        }
    }

    @Specialization
    protected double doDouble(double operand, boolean naRm, boolean finite,
                    @Cached("createBinaryProfile()") ConditionProfile finiteProfile,
                    @Cached("createBinaryProfile()") ConditionProfile isInfiniteProfile) {
        na.enable(operand);
        if (naRmProfile.profile(naRm || finite)) {
            boolean profiledFinite = finiteProfile.profile(finite);
            if (na.checkNAorNaN(operand) || (profiledFinite && isInfiniteProfile.profile(!RRuntime.isFinite(operand)))) {
                // the only value we have should be removed...
                emptyWarning();
                return semantics.getIntStart();
            } else {
                return operand;
            }
        } else {
            // since !naRm and !finite, NaN or +/-Inf can be valid results
            return na.check(operand) ? RRuntime.DOUBLE_NA : operand;
        }
    }

    @Specialization
    protected int doLogical(byte operand, boolean naRm, @SuppressWarnings("unused") boolean finite) {
        na.enable(operand);
        if (naRmProfile.profile(naRm)) {
            if (na.check(operand)) {
                emptyWarning();
                return semantics.getIntStart();
            } else {
                return operand;
            }
        } else {
            return na.check(operand) ? RRuntime.INT_NA : operand;
        }
    }

    @Specialization
    protected RComplex doComplex(RComplex operand, boolean naRm, @SuppressWarnings("unused") boolean finite) {
        if (semantics.supportComplex) {
            na.enable(operand);
            if (naRmProfile.profile(naRm)) {
                if (na.check(operand)) {
                    emptyWarning();
                    return RRuntime.double2complex(semantics.getDoubleStart());
                } else {
                    return operand;
                }
            } else {
                return na.check(operand) ? RComplex.createNA() : operand;
            }
        } else {
            throw error(RError.Message.INVALID_TYPE_ARGUMENT, "complex");
        }
    }

    @Specialization
    protected String doString(String operand, boolean naRm, @SuppressWarnings("unused") boolean finite) {
        if (semantics.supportString) {
            na.enable(operand);
            if (naRmProfile.profile(naRm)) {
                if (na.check(operand)) {
                    if (semantics.getEmptyWarning() != null) {
                        RError.warning(this, semantics.emptyWarningCharacter);
                    }
                    return semantics.getStringStart();
                } else {
                    return operand;
                }
            } else {
                return na.check(operand) ? RRuntime.STRING_NA : operand;
            }
        } else {
            throw error(RError.Message.INVALID_TYPE_ARGUMENT, "character");
        }
    }

    @SuppressWarnings("unused")
    @Specialization
    protected RRaw doString(RRaw operand, boolean naRm, boolean finite) {
        throw error(RError.Message.INVALID_TYPE_ARGUMENT, "raw");
    }

    @Specialization
    protected int doIntVector(RIntVector operand, boolean naRm, boolean finite) {
        RBaseNode.reportWork(this, operand.getLength());
        boolean profiledNaRm = naRmProfile.profile(naRm || finite);
        int result = semantics.getIntStart();
        na.enable(operand);
        int opCount = 0;
        int[] data = operand.getDataWithoutCopying();
        for (int i = 0; i < operand.getLength(); i++) {
            int d = data[i];
            if (na.check(d)) {
                if (profiledNaRm) {
                    continue;
                } else {
                    return RRuntime.INT_NA;
                }
            } else {
                result = arithmetic.op(result, d);
                if (RRuntime.isNA(result)) {
                    naResultWarning();
                    return RRuntime.INT_NA;
                }
            }
            opCount++;
        }
        if (opCount == 0) {
            emptyWarning();
        }
        return result;
    }

    @Specialization
    protected double doDoubleVector(RDoubleVector operand, boolean naRm, boolean finite,
                    @Cached("createBinaryProfile()") ConditionProfile finiteProfile,
                    @Cached("createBinaryProfile()") ConditionProfile isInfiniteProfile) {
        RBaseNode.reportWork(this, operand.getLength());
        boolean profiledNaRm = naRmProfile.profile(naRm || finite);
        boolean profiledFinite = finiteProfile.profile(finite);
        double result = semantics.getDoubleStart();
        na.enable(operand);
        int opCount = 0;
        double[] data = operand.getDataWithoutCopying();
        for (int i = 0; i < operand.getLength(); i++) {
            double d = data[i];
            if (na.checkNAorNaN(d)) {
                if (profiledNaRm) {
                    continue;   // ignore NA/NaN
                } else if (na.check(d)) {
                    // NA produces NA directly, but NaN should be handled by arithmetics.op to
                    // produce NaN. We cannot directly return NaN because if we encounter NA later
                    // on, we should return NA not NaN
                    return RRuntime.DOUBLE_NA;
                }
            } else if (profiledFinite && isInfiniteProfile.profile(!RRuntime.isFinite(d))) {
                // ignore -/+Inf if 'infinite == TRUE'
                continue;
            }

            result = arithmetic.op(result, d);
            opCount++;
        }
        if (opCount == 0) {
            emptyWarning();
        }
        return result;
    }

    @Specialization
    protected int doLogicalVector(RLogicalVector operand, boolean naRm, @SuppressWarnings("unused") boolean finite) {
        RBaseNode.reportWork(this, operand.getLength());
        boolean profiledNaRm = naRmProfile.profile(naRm);
        int result = semantics.getIntStart();
        na.enable(operand);
        int opCount = 0;
        byte[] data = operand.getDataWithoutCopying();
        for (int i = 0; i < operand.getLength(); i++) {
            byte d = data[i];
            if (na.check(d)) {
                if (profiledNaRm) {
                    continue;
                } else {
                    return RRuntime.INT_NA;
                }
            } else {
                result = arithmetic.op(result, d);
                if (RRuntime.isNA(result)) {
                    naResultWarning();
                    return RRuntime.INT_NA;
                }
            }
            opCount++;
        }
        if (opCount == 0) {
            emptyWarning();
        }
        return result;
    }

    @Specialization
    protected int doIntSequence(RIntSequence operand, @SuppressWarnings("unused") boolean naRm, @SuppressWarnings("unused") boolean finite) {
        RBaseNode.reportWork(this, operand.getLength());
        int result = semantics.getIntStart();
        int current = operand.getStart();
        for (int i = 0; i < operand.getLength(); i++) {
            result = arithmetic.op(result, current);
            if (RRuntime.isNA(result)) {
                naResultWarning();
                return RRuntime.INT_NA;
            }
            current += operand.getStride();
        }
        if (operand.getLength() == 0) {
            emptyWarning();
        }
        return result;
    }

    @Specialization
    protected double doDoubleSequence(RDoubleSequence operand, @SuppressWarnings("unused") boolean naRm, @SuppressWarnings("unused") boolean finite) {
        RBaseNode.reportWork(this, operand.getLength());
        double result = semantics.getDoubleStart();
        double current = operand.getStart();
        for (int i = 0; i < operand.getLength(); i++) {
            result = arithmetic.op(result, current);
            current += operand.getStride();
        }
        if (operand.getLength() == 0) {
            emptyWarning();
        }
        return result;
    }

    @Specialization
    protected RComplex doComplexVector(RComplexVector operand, boolean naRm, boolean finite) {
        RBaseNode.reportWork(this, operand.getLength());
        if (semantics.supportComplex) {
            boolean profiledNaRm = naRmProfile.profile(naRm || finite);
            RComplex result = RRuntime.double2complex(semantics.getDoubleStart());
            int opCount = 0;
            na.enable(operand);
            for (int i = 0; i < operand.getLength(); i++) {
                RComplex current = operand.getDataAt(i);
                if (na.check(current)) {
                    if (profiledNaRm) {
                        continue;
                    } else {
                        return RComplex.createNA();
                    }
                } else {
                    result = arithmetic.op(result.getRealPart(), result.getImaginaryPart(), current.getRealPart(), current.getImaginaryPart());
                }
                opCount++;
            }
            if (opCount == 0) {
                emptyWarning();
            }
            return result;
        } else {
            throw error(RError.Message.INVALID_TYPE_ARGUMENT, "complex");

        }
    }

    // the algorithm that works for other types (reducing a vector starting with the "start value")
    // does not work for String-s as, in particular, we cannot supply the (lexicographically)
    // "largest" String for the implementation of max function

    @SuppressWarnings("unused")
    private static String doStringVectorEmptyInternal(RStringVector operand, boolean naRm, boolean finite, ReduceSemantics semantics, RBaseNode invokingNode) {
        if (semantics.supportString) {
            if (semantics.getEmptyWarning() != null) {
                RError.warning(invokingNode, semantics.emptyWarningCharacter);
            }
            return semantics.getStringStart();
        } else {
            throw invokingNode.error(RError.Message.INVALID_TYPE_ARGUMENT, "character");
        }
    }

    @Specialization(guards = "operand.getLength() == 0")
    protected String doStringVectorEmpty(RStringVector operand, boolean naRm, boolean finite) {
        return doStringVectorEmptyInternal(operand, naRm, finite, semantics, this);
    }

    @Specialization(guards = "operand.getLength() == 1")
    protected String doStringVectorOneElem(RStringVector operand, boolean naRm, boolean finite) {
        if (semantics.supportString) {
            boolean profiledNaRm = naRmProfile.profile(naRm);
            String result = operand.getDataAt(0);
            if (profiledNaRm) {
                na.enable(result);
                if (na.check(result)) {
                    return doStringVectorEmpty(operand, naRm, finite);
                }
            }
            return result;
        } else {
            throw error(RError.Message.INVALID_TYPE_ARGUMENT, "character");
        }
    }

    @Specialization(guards = "operand.getLength() > 1")
    protected String doStringVector(RStringVector operand, boolean naRm, boolean finite) {
        if (semantics.supportString) {
            return handleString(operand, naRm, finite, 0);
        } else {
            throw error(RError.Message.INVALID_TYPE_ARGUMENT, "character");
        }
    }

    @SuppressWarnings("unused")
    @Specialization
    protected RRaw doString(RRawVector operand, boolean naRm, boolean finite) {
        throw error(RError.Message.INVALID_TYPE_ARGUMENT, "raw");
    }

    public static final class ReduceSemantics {

        private final int intStart;
        private final double doubleStart;
        private final String stringStart = RRuntime.STRING_NA; // does not seem to change
        private final boolean nullInt;
        private final RError.Message emptyWarning;
        private final RError.Message emptyWarningCharacter;
        private final RError.Message naResultWarning;
        private final boolean supportComplex;
        private final boolean supportString;

        public ReduceSemantics(int intStart, double doubleStart, boolean nullInt, RError.Message emptyWarning, RError.Message emptyWarningCharacter, RError.Message naResultWarning,
                        boolean supportComplex, boolean supportString) {
            this.intStart = intStart;
            this.doubleStart = doubleStart;
            this.nullInt = nullInt;
            this.emptyWarning = emptyWarning;
            this.emptyWarningCharacter = emptyWarningCharacter;
            this.naResultWarning = naResultWarning;
            this.supportComplex = supportComplex;
            this.supportString = supportString;
        }

        public ReduceSemantics(int intStart, double doubleStart, boolean nullInt, RError.Message emptyWarning, RError.Message emptyWarningCharacter, boolean supportComplex, boolean supportString) {
            this(intStart, doubleStart, nullInt, emptyWarning, emptyWarningCharacter, null, supportComplex, supportString);
        }

        public int getIntStart() {
            return intStart;
        }

        public double getDoubleStart() {
            return doubleStart;
        }

        public String getStringStart() {
            return stringStart;
        }

        public boolean isNullInt() {
            return nullInt;
        }

        public RError.Message getEmptyWarning() {
            return emptyWarning;
        }

        public RError.Message getEmptyWarningCharacter() {
            return emptyWarningCharacter;
        }

        public RError.Message getNAResultWarning() {
            return naResultWarning;
        }
    }

    private static final class MultiElemStringHandlerNode extends RBaseNode {

        @Child private MultiElemStringHandlerNode recursiveStringHandler;
        @Child private BinaryArithmetic arithmetic;

        private final ReduceSemantics semantics;
        private final BinaryArithmeticFactory factory;
        private final NACheck na;
        private final ConditionProfile naRmProfile = ConditionProfile.createBinaryProfile();

        MultiElemStringHandlerNode(ReduceSemantics semantics, BinaryArithmeticFactory factory, NACheck na) {
            this.semantics = semantics;
            this.factory = factory;
            this.arithmetic = factory.createOperation();
            this.na = na;
        }

        private String handleString(RStringVector operand, boolean naRm, boolean finite, int offset) {
            if (recursiveStringHandler == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                recursiveStringHandler = insert(new MultiElemStringHandlerNode(semantics, factory, na));
            }
            return recursiveStringHandler.executeString(operand, naRm, finite, offset);
        }

        public String executeString(RStringVector operand, boolean naRm, boolean finite, int offset) {
            boolean profiledNaRm = naRmProfile.profile(naRm);
            na.enable(operand);
            String result = operand.getDataAt(offset);
            if (profiledNaRm) {
                if (na.check(result)) {
                    // the following is meant to eliminate leading NA-s
                    if (offset == operand.getLength() - 1) {
                        // last element - all other are NAs
                        return doStringVectorEmptyInternal(operand, naRm, finite, semantics, this);
                    } else {
                        return handleString(operand, naRm, finite, offset + 1);
                    }
                }
            } else {
                if (na.check(result)) {
                    return result;
                }
            }
            // when we reach here, it means that we have already seen one non-NA element
            assert !RRuntime.isNA(result);
            for (int i = offset + 1; i < operand.getLength(); i++) {
                String current = operand.getDataAt(i);
                if (na.check(current)) {
                    if (profiledNaRm) {
                        // skip NA-s
                        continue;
                    } else {
                        return RRuntime.STRING_NA;
                    }
                } else {
                    result = arithmetic.op(result, current);
                }
            }
            return result;
        }
    }
}
