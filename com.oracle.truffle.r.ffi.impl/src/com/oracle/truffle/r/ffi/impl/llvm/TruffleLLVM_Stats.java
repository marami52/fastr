/*
 * Copyright (c) 2014, 2017, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.r.ffi.impl.llvm;

import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.interop.ForeignAccess;
import com.oracle.truffle.api.interop.Message;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.r.ffi.impl.interop.NativeDoubleArray;
import com.oracle.truffle.r.ffi.impl.interop.NativeIntegerArray;
import com.oracle.truffle.r.ffi.impl.llvm.TruffleLLVM_StatsFactory.ExecuteFactorNodeGen;
import com.oracle.truffle.r.ffi.impl.llvm.TruffleLLVM_StatsFactory.ExecuteWorkNodeGen;
import com.oracle.truffle.r.runtime.RInternalError;
import com.oracle.truffle.r.runtime.context.RContext;
import com.oracle.truffle.r.runtime.context.RContext.ContextState;
import com.oracle.truffle.r.runtime.ffi.DLL;
import com.oracle.truffle.r.runtime.ffi.RFFIFactory;
import com.oracle.truffle.r.runtime.ffi.DLL.DLLInfo;
import com.oracle.truffle.r.runtime.ffi.DLL.SymbolHandle;
import com.oracle.truffle.r.runtime.ffi.DLLRFFI;
import com.oracle.truffle.r.runtime.ffi.StatsRFFI;

public class TruffleLLVM_Stats implements StatsRFFI {

    public enum FFT_FUN {
        fft_work,
        fft_factor;
    }

    static class ContextStateImpl implements RContext.ContextState {
        @Override
        public ContextState initialize(RContext context) {
            /*
             * In the case of a SHARE_PARENT_RW context, there is no dlopen call for stats, so the
             * fft_work/fft_factor functions will not be added into the context symbol map, so we do
             * it here.
             */
            if (context.getKind() == RContext.ContextKind.SHARE_PARENT_RW) {
                TruffleLLVM_DLL.ContextStateImpl contextState = TruffleLLVM_RFFIContextState.getContextState().dllState;
                TruffleLLVM_DLL.ContextStateImpl parentDLLContextState = TruffleLLVM_RFFIContextState.getContextState(context.getParent()).dllState;
                TruffleLLVM_DLL.ParseStatus parseStatus = null;
                for (FFT_FUN f : FFT_FUN.values()) {
                    String funName = f.name();
                    TruffleLLVM_DLL.ParseStatus parentParseStatus = parentDLLContextState.parseStatusMap.get(funName);
                    if (parentParseStatus != null) {
                        if (parseStatus == null) {
                            parseStatus = new TruffleLLVM_DLL.ParseStatus("stats", parentParseStatus.ir, false);
                        }
                        contextState.parseStatusMap.put(f.name(), parseStatus);
                    }
                }
            }
            return this;
        }

    }

    public abstract static class LookupAdapter extends Node {
        @Child private DLLRFFI.DLSymNode dllSymNode = RFFIFactory.getRFFI().getDLLRFFI().createDLSymNode();

        public SymbolHandle lookup(String name) {
            DLLInfo dllInfo = DLL.findLibrary("stats");
            // cannot go through DLL because stats does not allow dynamic lookup
            // and these symbols are not registered (only fft)
            SymbolHandle result = dllSymNode.execute(dllInfo.handle, name);
            if (result == DLL.SYMBOL_NOT_FOUND) {
                @SuppressWarnings("unused")
                TruffleLLVM_RFFIContextState cs = TruffleLLVM_RFFIContextState.getContextState();
                throw RInternalError.shouldNotReachHere();
            }
            return result;
        }
    }

    @ImportStatic({RContext.class})
    public abstract static class ExecuteWork extends LookupAdapter {
        public abstract int execute(double[] a, int nseg, int n, int nspn, int isn, double[] work, int[] iwork, RContext context);

        @Specialization(guards = "context == cachedContext")
        protected int executeWorkCached(double[] a, int nseg, int n, int nspn, int isn, double[] work, int[] iwork, @SuppressWarnings("unused") RContext context,
                        @SuppressWarnings("unused") @Cached("getInstance()") RContext cachedContext,
                        @Cached("createMessageNode()") Node messageNode,
                        @Cached("lookupWork()") SymbolHandle fftWork) {
            return doWork(a, nseg, n, nspn, isn, work, iwork, messageNode, fftWork);
        }

        @Specialization(replaces = "executeWorkCached")
        protected int executeWorkNormal(double[] a, int nseg, int n, int nspn, int isn, double[] work, int[] iwork, @SuppressWarnings("unused") RContext context) {
            return doWork(a, nseg, n, nspn, isn, work, iwork, createMessageNode(), lookup("fft_work"));
        }

        private static int doWork(double[] a, int nseg, int n, int nspn, int isn, double[] work, int[] iwork, Node messageNode, SymbolHandle fftWork) {
            NativeDoubleArray na = new NativeDoubleArray(a);
            NativeDoubleArray nwork = new NativeDoubleArray(work);
            NativeIntegerArray niwork = new NativeIntegerArray(iwork);
            try {
                return (int) ForeignAccess.sendExecute(messageNode, fftWork.asTruffleObject(), na, nseg, n, nspn, isn, nwork, niwork);
            } catch (Throwable t) {
                throw RInternalError.shouldNotReachHere(t);
            }
        }

        public static Node createMessageNode() {
            return Message.createExecute(7).createNode();
        }

        public static ExecuteWork create() {
            return ExecuteWorkNodeGen.create();
        }

        public SymbolHandle lookupWork() {
            return lookup("fft_work");
        }
    }

    @ImportStatic({RContext.class})
    public abstract static class ExecuteFactor extends LookupAdapter {
        protected abstract void execute(int n, int[] pmaxf, int[] pmaxp, RContext context);

        @Specialization(guards = "context == cachedContext")
        protected void executeFactorCached(int n, int[] pmaxf, int[] pmaxp, @SuppressWarnings("unused") RContext context,
                        @SuppressWarnings("unused") @Cached("getInstance()") RContext cachedContext,
                        @Cached("createMessageNode()") Node messageNode,
                        @Cached("lookupFactor()") SymbolHandle fftFactor) {
            doFactor(n, pmaxf, pmaxp, messageNode, fftFactor);
        }

        @Specialization(replaces = "executeFactorCached")
        protected void executeFactorNormal(int n, int[] pmaxf, int[] pmaxp, @SuppressWarnings("unused") RContext context) {
            doFactor(n, pmaxf, pmaxp, createMessageNode(), lookup("fft_factor"));
        }

        private static void doFactor(int n, int[] pmaxf, int[] pmaxp, Node messageNode, SymbolHandle fftFactor) {
            NativeIntegerArray npmaxf = new NativeIntegerArray(pmaxf);
            NativeIntegerArray npmaxp = new NativeIntegerArray(pmaxp);

            try {
                ForeignAccess.sendExecute(messageNode, fftFactor.asTruffleObject(), n, npmaxf, npmaxp);
            } catch (Throwable t) {
                throw RInternalError.shouldNotReachHere(t);
            }
        }

        public static Node createMessageNode() {
            return Message.createExecute(3).createNode();
        }

        public static ExecuteFactor create() {
            return ExecuteFactorNodeGen.create();
        }

        public SymbolHandle lookupFactor() {
            return lookup("fft_factor");
        }
    }

    public static class Truffle_FactorNode extends FactorNode {
        @Child private ExecuteFactor executeFactor = ExecuteFactor.create();

        @Override
        public void execute(int n, int[] pmaxf, int[] pmaxp) {
            executeFactor.execute(n, pmaxf, pmaxp, RContext.getInstance());
        }
    }

    public static class Truffle_WorkNode extends WorkNode {
        @Child private ExecuteWork executeWork = ExecuteWork.create();

        @Override
        public int execute(double[] a, int nseg, int n, int nspn, int isn, double[] work, int[] iwork) {
            return executeWork.execute(a, nseg, n, nspn, isn, work, iwork, RContext.getInstance());
        }
    }

    @Override
    public FactorNode createFactorNode() {
        return new Truffle_FactorNode();
    }

    @Override
    public WorkNode createWorkNode() {
        return new Truffle_WorkNode();
    }
}
