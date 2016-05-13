/*
 * Copyright (c) 2016, 2016, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.r.nodes.function.call;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.r.nodes.RRootNode;
import com.oracle.truffle.r.nodes.function.ArgumentMatcher;
import com.oracle.truffle.r.nodes.function.ArgumentMatcher.MatchPermutation;
import com.oracle.truffle.r.nodes.function.CallArgumentsNode;
import com.oracle.truffle.r.nodes.function.FormalArguments;
import com.oracle.truffle.r.nodes.function.RCallNode;
import com.oracle.truffle.r.nodes.function.UnmatchedArguments;
import com.oracle.truffle.r.runtime.ArgumentsSignature;
import com.oracle.truffle.r.runtime.RError;
import com.oracle.truffle.r.runtime.data.RArgsValuesAndNames;
import com.oracle.truffle.r.runtime.data.RFunction;
import com.oracle.truffle.r.runtime.nodes.RNode;

/**
 * This class provides the infrastructure to reorder the arguments based on R's argument matching
 * rules. It implements two different paths: one for arguments provided as an
 * {@link CallArgumentsNode}, i.e., unevaluated arguments, and another path for evaluated arguments.
 */
public abstract class PrepareArguments extends Node {

    private static final int CACHE_SIZE = 4;

    public abstract Object[] execute(VirtualFrame frame, RArgsValuesAndNames varArgs, RCallNode call);

    public static PrepareArguments create(RFunction function, CallArgumentsNode args, boolean noOpt) {
        return new UninitializedPrepareArguments(function, args, noOpt);
    }

    public static PrepareArguments createExplicit(RFunction function) {
        return new UninitializedExplicitPrepareArguments(function);
    }

    private static final class UninitializedPrepareArguments extends PrepareArguments {

        private final RFunction function;
        private final CallArgumentsNode sourceArguments; // not used as a node
        private final boolean noOpt;
        private int depth = CACHE_SIZE;

        UninitializedPrepareArguments(RFunction function, CallArgumentsNode sourceArguments, boolean noOpt) {
            this.function = function;
            this.sourceArguments = sourceArguments;
            this.noOpt = noOpt;
        }

        @Override
        public Object[] execute(VirtualFrame frame, RArgsValuesAndNames varArgs, RCallNode call) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            PrepareArguments next;
            if (depth-- > 0) {
                next = new CachedPrepareArguments(this, function, call, sourceArguments, varArgs == null ? null : varArgs.getSignature(), noOpt);
            } else {
                next = new GenericPrepareArguments(function, sourceArguments);
            }
            return replace(next).execute(frame, varArgs, call);
        }
    }

    private static final class CachedPrepareArguments extends PrepareArguments {

        @Child private PrepareArguments next;
        @Children private final RNode[] matchedArguments;
        private final ArgumentsSignature cachedVarArgSignature;

        CachedPrepareArguments(PrepareArguments next, RFunction function, RCallNode call, CallArgumentsNode args, ArgumentsSignature varArgSignature, boolean noOpt) {
            this.next = next;
            cachedVarArgSignature = varArgSignature;
            matchedArguments = ArgumentMatcher.matchArguments(function, args.unrollArguments(varArgSignature), call, noOpt);
        }

        @Override
        @ExplodeLoop
        public Object[] execute(VirtualFrame frame, RArgsValuesAndNames varArgs, RCallNode call) {
            assert (cachedVarArgSignature != null) == (varArgs != null);
            if (cachedVarArgSignature == null || cachedVarArgSignature == varArgs.getSignature()) {
                Object[] result = new Object[matchedArguments.length];
                for (int i = 0; i < matchedArguments.length; i++) {
                    result[i] = matchedArguments[i].execute(frame);
                }
                return result;
            }
            return next.execute(frame, varArgs, call);
        }
    }

    private static final class GenericPrepareArguments extends PrepareArguments {

        private final RFunction function;
        private final CallArgumentsNode args; // not used as a node

        GenericPrepareArguments(RFunction function, CallArgumentsNode args) {
            this.function = function;
            this.args = args;
        }

        @Override
        public Object[] execute(VirtualFrame frame, RArgsValuesAndNames varArgs, RCallNode call) {
            CompilerDirectives.transferToInterpreter();
            ArgumentsSignature varArgSignature = varArgs == null ? null : varArgs.getSignature();
            UnmatchedArguments argsValuesAndNames = args.unrollArguments(varArgSignature);
            RNode[] matchedArgs = ArgumentMatcher.matchArguments(function, argsValuesAndNames, RError.ROOTNODE, true);
            Object[] result = new Object[matchedArgs.length];
            for (int i = 0; i < matchedArgs.length; i++) {
                result[i] = matchedArgs[i].execute(frame);
            }
            return result;
        }
    }

    private static final class UninitializedExplicitPrepareArguments extends PrepareArguments {

        private final RFunction function;
        private int depth = CACHE_SIZE;

        UninitializedExplicitPrepareArguments(RFunction function) {
            this.function = function;
        }

        @Override
        public Object[] execute(VirtualFrame frame, RArgsValuesAndNames explicitArgs, RCallNode call) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            PrepareArguments next;
            if (depth-- > 0) {
                next = new CachedExplicitPrepareArguments(this, function, call, explicitArgs == null ? null : explicitArgs.getSignature());
            } else {
                next = new GenericExplicitPrepareArguments(function);
            }
            return replace(next).execute(frame, explicitArgs, call);
        }
    }

    private static final class CachedExplicitPrepareArguments extends PrepareArguments {

        @Child private PrepareArguments next;

        private final MatchPermutation permutation;
        private final ArgumentsSignature cachedExplicitArgSignature;
        private final FormalArguments formals;

        CachedExplicitPrepareArguments(PrepareArguments next, RFunction function, RCallNode call, ArgumentsSignature explicitArgSignature) {
            this.next = next;
            formals = ((RRootNode) function.getTarget().getRootNode()).getFormalArguments();
            permutation = ArgumentMatcher.matchArguments(explicitArgSignature, formals.getSignature(), call, false, function.getRBuiltin());
            cachedExplicitArgSignature = explicitArgSignature;
        }

        @Override
        public Object[] execute(VirtualFrame frame, RArgsValuesAndNames explicitArgs, RCallNode call) {
            if (cachedExplicitArgSignature == explicitArgs.getSignature()) {
                return ArgumentMatcher.matchArgumentsEvaluated(permutation, explicitArgs.getArguments(), formals);
            }
            return next.execute(frame, explicitArgs, call);
        }
    }

    private static final class GenericExplicitPrepareArguments extends PrepareArguments {

        private final RFunction function;

        GenericExplicitPrepareArguments(RFunction function) {
            this.function = function;
        }

        @Override
        public Object[] execute(VirtualFrame frame, RArgsValuesAndNames explicitArgs, RCallNode call) {
            CompilerDirectives.transferToInterpreter();
            // Function and arguments may change every call: Flatt'n'Match on SlowPath! :-/
            return ArgumentMatcher.matchArgumentsEvaluated(function, explicitArgs, RError.ROOTNODE, false).getArguments();
        }
    }
}