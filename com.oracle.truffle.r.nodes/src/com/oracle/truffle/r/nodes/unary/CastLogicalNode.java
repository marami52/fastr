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

import java.util.Arrays;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.r.nodes.helpers.InheritsCheckNode;
import com.oracle.truffle.r.runtime.RError;
import com.oracle.truffle.r.runtime.RRuntime;
import com.oracle.truffle.r.runtime.data.RComplexVector;
import com.oracle.truffle.r.runtime.data.RDataFactory;
import com.oracle.truffle.r.runtime.data.RList;
import com.oracle.truffle.r.runtime.data.RLogicalVector;
import com.oracle.truffle.r.runtime.data.RMissing;
import com.oracle.truffle.r.runtime.data.RNull;
import com.oracle.truffle.r.runtime.data.RRawVector;
import com.oracle.truffle.r.runtime.data.RStringVector;
import com.oracle.truffle.r.runtime.data.model.RAbstractDoubleVector;
import com.oracle.truffle.r.runtime.data.model.RAbstractIntVector;
import com.oracle.truffle.r.runtime.data.model.RAbstractListVector;
import com.oracle.truffle.r.runtime.data.model.RAbstractVector;
import com.oracle.truffle.r.runtime.interop.ForeignArray2R;
import com.oracle.truffle.r.runtime.interop.ForeignArray2RNodeGen;
import com.oracle.truffle.r.runtime.ops.na.NAProfile;

@ImportStatic(RRuntime.class)
public abstract class CastLogicalNode extends CastLogicalBaseNode {

    private final NAProfile naProfile = NAProfile.create();

    @Child private CastLogicalNode recursiveCastLogical;
    @Child private InheritsCheckNode inheritsFactorCheck;

    protected CastLogicalNode(boolean preserveNames, boolean preserveDimensions, boolean preserveAttributes) {
        super(preserveNames, preserveDimensions, preserveAttributes);
    }

    protected CastLogicalNode(boolean preserveNames, boolean preserveDimensions, boolean preserveAttributes, boolean forRFFI) {
        super(preserveNames, preserveDimensions, preserveAttributes, forRFFI);
    }

    protected Object castLogicalRecursive(Object o) {
        if (recursiveCastLogical == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            recursiveCastLogical = insert(CastLogicalNodeGen.create(preserveNames(), preserveDimensions(), preserveAttributes()));
        }
        return recursiveCastLogical.execute(o);
    }

    protected boolean isFactor(Object o) {
        if (inheritsFactorCheck == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            inheritsFactorCheck = insert(new InheritsCheckNode(RRuntime.CLASS_FACTOR));
        }
        return inheritsFactorCheck.execute(o);
    }

    @Specialization
    protected RNull doNull(@SuppressWarnings("unused") RNull operand) {
        return RNull.instance;
    }

    @FunctionalInterface
    private interface IntToByteFunction {
        byte apply(int value);
    }

    private RLogicalVector vectorCopy(RAbstractVector operand, byte[] bdata, boolean isComplete) {
        RLogicalVector ret = RDataFactory.createLogicalVector(bdata, isComplete, getPreservedDimensions(operand), getPreservedNames(operand));
        preserveDimensionNames(operand, ret);
        if (preserveAttributes()) {
            ret.copyRegAttributesFrom(operand);
        }
        return ret;
    }

    private RLogicalVector createResultVector(RAbstractVector operand, IntToByteFunction elementFunction) {
        naCheck.enable(operand);
        byte[] bdata = new byte[operand.getLength()];
        boolean seenNA = false;
        for (int i = 0; i < operand.getLength(); i++) {
            byte value = elementFunction.apply(i);
            bdata[i] = value;
            seenNA = seenNA || naProfile.isNA(value);
        }
        return vectorCopy(operand, bdata, !seenNA);
    }

    @Specialization
    protected RLogicalVector doLogicalVector(RLogicalVector operand) {
        return operand;
    }

    @Specialization(guards = "!isFactor(operand)")
    protected RLogicalVector doIntVector(RAbstractIntVector operand) {
        return createResultVector(operand, index -> naCheck.convertIntToLogical(operand.getDataAt(index)));
    }

    @Specialization(guards = "isFactor(factor)")
    protected RLogicalVector asLogical(RAbstractIntVector factor) {
        byte[] data = new byte[factor.getLength()];
        Arrays.fill(data, RRuntime.LOGICAL_NA);
        return RDataFactory.createLogicalVector(data, RDataFactory.INCOMPLETE_VECTOR);
    }

    @Specialization
    protected RLogicalVector doDoubleVector(RAbstractDoubleVector operand) {
        return createResultVector(operand, index -> naCheck.convertDoubleToLogical(operand.getDataAt(index)));
    }

    @Specialization
    protected RLogicalVector doStringVector(RStringVector operand) {
        return createResultVector(operand, index -> naCheck.convertStringToLogical(operand.getDataAt(index)));
    }

    @Specialization
    protected RLogicalVector doComplexVector(RComplexVector operand) {
        return createResultVector(operand, index -> naCheck.convertComplexToLogical(operand.getDataAt(index)));
    }

    @Specialization
    protected RLogicalVector doRawVectorDims(RRawVector operand) {
        return createResultVector(operand, index -> RRuntime.raw2logical(operand.getDataAt(index)));
    }

    @Specialization
    protected RLogicalVector doList(RAbstractListVector list) {
        int length = list.getLength();
        byte[] result = new byte[length];
        boolean seenNA = false;
        for (int i = 0; i < length; i++) {
            Object entry = list.getDataAt(i);
            if (entry instanceof RList) {
                result[i] = RRuntime.LOGICAL_NA;
                seenNA = true;
            } else {
                Object castEntry = castLogicalRecursive(entry);
                if (castEntry instanceof Byte) {
                    byte value = (Byte) castEntry;
                    result[i] = value;
                    seenNA = seenNA || RRuntime.isNA(value);
                } else if (castEntry instanceof RLogicalVector) {
                    RLogicalVector logicalVector = (RLogicalVector) castEntry;
                    if (logicalVector.getLength() == 1) {
                        byte value = logicalVector.getDataAt(0);
                        result[i] = value;
                        seenNA = seenNA || RRuntime.isNA(value);
                    } else if (logicalVector.getLength() == 0) {
                        result[i] = RRuntime.LOGICAL_NA;
                        seenNA = true;
                    } else {
                        throw throwCannotCoerceListError("logical");
                    }
                } else {
                    throw throwCannotCoerceListError("logical");
                }
            }
        }
        RLogicalVector ret = RDataFactory.createLogicalVector(result, !seenNA, getPreservedDimensions(list), getPreservedNames(list));
        if (preserveAttributes()) {
            ret.copyRegAttributesFrom(list);
        }
        return ret;
    }

    @Specialization
    protected RMissing doMissing(RMissing missing) {
        return missing;
    }

    @Specialization(guards = "isForeignObject(obj)")
    protected RLogicalVector doForeignObject(TruffleObject obj,
                    @Cached("createForeignArray2RNode()") ForeignArray2R foreignArray2R) {
        Object o = foreignArray2R.execute(obj);
        if (!RRuntime.isForeignObject(o)) {
            if (o instanceof RLogicalVector) {
                return (RLogicalVector) o;
            }
            o = castLogicalRecursive(o);
            if (o instanceof RLogicalVector) {
                return (RLogicalVector) o;
            }
        }
        throw error(RError.Message.CANNOT_COERCE_EXTERNAL_OBJECT_TO_VECTOR, "vector");
    }

    public static CastLogicalNode create() {
        return CastLogicalNodeGen.create(true, true, true);
    }

    public static CastLogicalNode createForRFFI(boolean preserveNames, boolean preserveDimensions, boolean preserveAttributes) {
        return CastLogicalNodeGen.create(preserveNames, preserveDimensions, preserveAttributes, true);
    }

    public static CastLogicalNode createNonPreserving() {
        return CastLogicalNodeGen.create(false, false, false);
    }

    protected ForeignArray2R createForeignArray2RNode() {
        return ForeignArray2RNodeGen.create();
    }
}
