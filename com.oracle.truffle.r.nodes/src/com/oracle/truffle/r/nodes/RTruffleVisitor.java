/*
 * Copyright (c) 2013, 2014, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.r.nodes;

import java.util.*;

import com.oracle.truffle.api.*;
import com.oracle.truffle.api.source.*;
import com.oracle.truffle.r.nodes.access.*;
import com.oracle.truffle.r.nodes.access.ReadVariableNode.ReadVariableSuperMaterializedNode;
import com.oracle.truffle.r.nodes.access.array.*;
import com.oracle.truffle.r.nodes.access.array.ArrayPositionCast.OperatorConverterNode;
import com.oracle.truffle.r.nodes.access.array.ArrayPositionCastFactory.OperatorConverterNodeFactory;
import com.oracle.truffle.r.nodes.access.array.read.*;
import com.oracle.truffle.r.nodes.access.array.write.*;
import com.oracle.truffle.r.nodes.binary.*;
import com.oracle.truffle.r.nodes.control.*;
import com.oracle.truffle.r.nodes.function.*;
import com.oracle.truffle.r.nodes.unary.*;
import com.oracle.truffle.r.options.*;
import com.oracle.truffle.r.parser.ast.*;
import com.oracle.truffle.r.parser.ast.Constant.ConstantType;
import com.oracle.truffle.r.parser.ast.Symbol;
import com.oracle.truffle.r.parser.tools.*;
import com.oracle.truffle.r.runtime.*;
import com.oracle.truffle.r.runtime.data.*;
import com.oracle.truffle.r.runtime.env.*;

public final class RTruffleVisitor extends BasicVisitor<RNode> {

    private REnvironment environment;

    public RTruffleVisitor(REnvironment enclosing) {
        this.environment = enclosing;
    }

    public RNode transform(ASTNode ast) {
        return ast.accept(this);
    }

    @Override
    public RNode visit(Constant c) {
        SourceSection src = c.getSource();
        if (c.getType() == ConstantType.NULL) {
            return ConstantNode.create(src, RNull.instance);
        }
        if (c.getValues().length != 1) {
            throw new UnsupportedOperationException();
        }
        switch (c.getType()) {
            case INT:
                return ConstantNode.create(src, RRuntime.string2int(c.getValues()[0]));
            case DOUBLE:
                return ConstantNode.create(src, RRuntime.string2double(c.getValues()[0]));
            case BOOL:
                switch (c.getValues()[0]) {
                    case "NA":
                        return ConstantNode.create(src, RRuntime.LOGICAL_NA);
                    case "1":
                        return ConstantNode.create(src, true);
                    case "0":
                        return ConstantNode.create(src, false);
                    default:
                        throw new AssertionError();
                }
            case STRING:
                return ConstantNode.create(src, c.getValues()[0]);
            case COMPLEX:
                return ConstantNode.create(src, RDataFactory.createComplex(0, RRuntime.string2double(c.getValues()[0])));
            default:
                throw new UnsupportedOperationException();
        }
    }

    @Override
    public RNode visit(Formula formula) {
        return ConstantNode.create(new RFormula(formula.getSource(), formula.getResponse().accept(this), formula.getModel().accept(this)));
    }

    @Override
    public RNode visit(FunctionCall callParam) {
        FunctionCall call = callParam;
        Symbol callName = call.isSymbol() ? call.getName() : null;
        SourceSection callSource = call.getSource();

        int index = 0;
        String[] argumentNames = new String[call.getArguments().size()];
        RNode[] nodes = new RNode[call.getArguments().size()];
        for (ArgNode e : call.getArguments()) {
            Symbol argName = e.getName();
            argumentNames[index] = (argName == null ? null : RRuntime.toString(argName));
            ASTNode val = e.getValue();
            if (val != null) {
                // the source must include a value assignment (if there is one) - this is ensured by
                // assigning the source section of the argument node
                val.setSource(e.getSource());
                nodes[index] = val.accept(this);
            }
            index++;
        }
        final CallArgumentsNode aCallArgNode = CallArgumentsNode.create(!call.isReplacement(), false, nodes, argumentNames);

        if (callName != null) {
            final String functionName = RRuntime.toString(callName);
            if (!FastROptions.DisableGroupGenerics.getValue() && RGroupGenerics.isGroupGeneric(functionName)) {
                return GroupDispatchCallNode.create(functionName, RGroupGenerics.getGroup(functionName), aCallArgNode, callSource);
            }
            return RCallNode.createCall(callSource, ReadVariableNode.create(functionName, RType.Function, false, true, false, true), aCallArgNode);
        } else {
            RNode lhs = call.getLhsNode().accept(this);
            return RCallNode.createCall(callSource, lhs, aCallArgNode);
        }
    }

    @Override
    public RNode visit(Function func) {
        // Introduce new environment for this function
        REnvironment.FunctionDefinition funcEnvironment = new REnvironment.FunctionDefinition(environment);
        this.environment = funcEnvironment; // Update the visitors state...

        RootCallTarget callTarget = null;
        try {
            // Parse function statements
            ASTNode astBody = func.getBody();
            FunctionStatementsNode statements;
            if (astBody != null) {
                // TODO fix
                statements = new FunctionStatementsNode((astBody.accept(this)));
            } else {
                statements = new FunctionStatementsNode(RNode.EMTPY_RNODE_ARRAY);
            }

            // Parse argument list
            List<ArgNode> argumentsList = func.getSignature();
            String[] argumentNames = new String[argumentsList.size()];
            RNode[] defaultValues = new RNode[argumentsList.size()];
            SaveArgumentsNode saveArguments;
            if (!argumentsList.isEmpty()) {
                RNode[] init = new RNode[argumentsList.size()];
                int index = 0;
                for (ArgNode arg : argumentsList) {
                    // Parse argument's default value
                    RNode defaultValue;
                    ASTNode defaultValNode = arg.getValue();
                    if (defaultValNode != null) {
                        defaultValue = arg.getValue().accept(this);
                    } else {
                        defaultValue = null;
                    }

                    // Create an initialization statement
                    init[index] = WriteVariableNode.create(RRuntime.toString(arg.getName()), AccessArgumentNode.create(index), true, false);

                    // Store formal arguments
                    argumentNames[index] = RRuntime.toString(arg.getName());
                    defaultValues[index] = defaultValue;

                    index++;
                }

                saveArguments = new SaveArgumentsNode(init);
            } else {
                saveArguments = new SaveArgumentsNode(RNode.EMTPY_RNODE_ARRAY);
            }

            // Maintain SourceSection
            if (astBody != null && statements.getSourceSection() == null) {
                statements.assignSourceSection(astBody.getSource());
            }
            FormalArguments formals = FormalArguments.create(argumentNames, defaultValues);

            String functionBody = func.getSource().getCode();
            FunctionDefinitionNode rootNode = new FunctionDefinitionNode(func.getSource(), funcEnvironment, new FunctionBodyNode(saveArguments, statements), formals, functionBody.substring(0,
                            Math.min(functionBody.length(), 50)).replace("\n", "\\n"), false);
            callTarget = Truffle.getRuntime().createCallTarget(rootNode);
        } catch (Throwable err) {
            err.printStackTrace();
        } finally {
            this.environment = environment.getParent(); // ... and be sure to reset it!
        }
        return FunctionExpressionNode.create(callTarget);
    }

    @Override
    public RNode visit(UnaryOperation op) {
        RNode operand = op.getLHS().accept(this);
        final String functionName = op.getPrettyOperator();
        final CallArgumentsNode aCallArgNode = CallArgumentsNode.createUnnamed(false, true, operand);
        if (!FastROptions.DisableGroupGenerics.getValue() && RGroupGenerics.isGroupGeneric(functionName)) {
            return GroupDispatchCallNode.create(functionName, RGroupGenerics.GROUP_OPS, aCallArgNode, op.getSource());
        }
        return RCallNode.createStaticCall(op.getSource(), functionName, aCallArgNode);
    }

    @Override
    public RNode visit(BinaryOperation op) {
        RNode left = op.getLHS().accept(this);
        RNode right = op.getRHS().accept(this);
        final String functionName = op.getPrettyOperator();
        final CallArgumentsNode aCallArgNode = CallArgumentsNode.createUnnamed(false, true, left, right);
        if (!FastROptions.DisableGroupGenerics.getValue() && RGroupGenerics.isGroupGeneric(functionName)) {
            return GroupDispatchCallNode.create(functionName, RGroupGenerics.getGroup(functionName), aCallArgNode, op.getSource());
        }
        return RCallNode.createStaticCall(op.getSource(), functionName, aCallArgNode);
    }

    @Override
    public RNode visit(Sequence seq) {
        ASTNode[] exprs = seq.getExpressions();
        RNode[] rexprs = new RNode[exprs.length];
        for (int i = 0; i < exprs.length; i++) {
            rexprs[i] = exprs[i].accept(this);
        }
        // For (deparse) consistency we do not special case a sequence of length 1
        return new SequenceNode(seq.getSource(), rexprs);
    }

    @Override
    public RNode visit(ASTNode n) {
        throw new UnsupportedOperationException("Unsupported AST Node " + n.getClass().getName());
    }

    @Override
    public RNode visit(ArgNode n) {
        assert n.getValue() != null;
        return n.getValue().accept(this);
    }

    private RNode createPositions(List<ArgNode> argList, int argLength, boolean isSubset, RNode castContainer, RNode dropDim, RNode tmpVarAccess, RNode rhsAccess, CoerceVector coerceVector,
                    boolean isAssignment) {
        RNode[] positions;
        OperatorConverterNode[] operatorConverters;
        MultiDimPosConverterNode[] multiDimConverters = null;
        MultiDimPosConverterValueNode[] multiDimConvertersValue = null;
        ArrayPositionCast[] castPositions;
        RNode exact = ConstantNode.create(RRuntime.LOGICAL_TRUE);
        if (argLength == 0) {
            positions = new RNode[]{ConstantNode.create(RMissing.instance)};
            operatorConverters = new OperatorConverterNode[]{OperatorConverterNodeFactory.create(0, 1, isAssignment, isSubset, null, null, null)};
            castPositions = new ArrayPositionCast[]{ArrayPositionCastFactory.create(0, 1, isAssignment, isSubset, null, null)};
        } else {
            int newArgLength = argLength;
            for (int i = 0; i < argLength; i++) {
                ArgNode argNode = argList.get(i);
                ASTNode node = argNode.getValue();
                Symbol name = argNode.getName();
                if (name != null && name.toString() != null && name.toString().equals("exact")) {
                    exact = (node == null ? ConstantNode.create(RMissing.instance) : node.accept(this));
                    newArgLength--;
                    break;
                }
            }
            positions = new RNode[newArgLength];
            operatorConverters = new OperatorConverterNode[newArgLength];
            if (isAssignment) {
                multiDimConvertersValue = new MultiDimPosConverterValueNode[newArgLength];
            } else {
                multiDimConverters = new MultiDimPosConverterNode[newArgLength];
            }
            castPositions = new ArrayPositionCast[newArgLength];
            int ind = 0;
            for (int i = 0; i < argLength; i++) {
                ArgNode argNode = argList.get(i);
                Symbol name = argNode.getName();
                ASTNode node = argNode.getValue();
                if (name != null && name.toString() != null && name.toString().equals("exact")) {
                    continue;
                }
                positions[ind] = (node == null ? ConstantNode.create(RMissing.instance) : node.accept(this));
                if (isAssignment) {
                    multiDimConvertersValue[ind] = MultiDimPosConverterValueNodeFactory.create(isSubset, null, null, null);
                } else {
                    multiDimConverters[ind] = MultiDimPosConverterNodeFactory.create(isSubset, null, null);
                }
                operatorConverters[ind] = OperatorConverterNodeFactory.create(ind, positions.length, isAssignment, isSubset, null, null, null);
                castPositions[ind] = ArrayPositionCastFactory.create(ind, positions.length, isAssignment, isSubset, null, null);
                ind++;
            }
        }
        if (!isAssignment) {
            assert castContainer != null;
            assert dropDim != null;
            PositionsArrayNode posArrayNode = new PositionsArrayNode(castPositions, positions, operatorConverters, multiDimConverters);
            return AccessArrayNode.create(isSubset, castContainer, exact, posArrayNode, dropDim);
        } else {
            PositionsArrayNodeValue posArrayNodeValue = new PositionsArrayNodeValue(castPositions, positions, operatorConverters, multiDimConvertersValue);
            assert tmpVarAccess != null;
            assert rhsAccess != null;
            assert coerceVector != null;
            return UpdateArrayHelperNodeFactory.create(isSubset, tmpVarAccess, rhsAccess, ConstantNode.create(0), posArrayNodeValue, coerceVector);
        }
    }

    @Override
    public RNode visit(AccessVector a) {
        RNode vector = a.getVector().accept(this);
        RNode dropDim = ConstantNode.create(true);
        List<ArgNode> args = a.getArguments();
        int argLength = args.size();
        if (argLength > 0) {
            for (ArgNode e : args) {
                if (e.getName() != null && e.getName().toString().equals(RRuntime.DROP_DIM_ARG_NAME)) {
                    argLength--;
                    break;
                }
            }
            if (argLength < args.size()) {
                dropDim = null;
                List<ArgNode> newArgs = new ArrayList<>();
                for (ArgNode e : args) {
                    if (e.getName() != null && e.getName().toString().equals(RRuntime.DROP_DIM_ARG_NAME) && dropDim == null) {
                        // first occurence of "drop" argument counts - the others are treated as
                        // indexes
                        dropDim = CastLogicalNodeFactory.create(e.getValue().accept(this), false, false, false);
                    } else {
                        newArgs.add(e);
                    }
                }
                args = newArgs;
            }
        }
        RNode castContainer = CastToContainerNodeFactory.create(vector, false, false, false);
        RNode access = createPositions(args, argLength, a.isSubset(), castContainer, dropDim, null, null, null, false);
        access.assignSourceSection(a.getSource());
        return access;
    }

    /**
     * The sequence created for a {@linkplain #visit(Replacement) replacement} consists of the
     * following elements:
     * <ol>
     * <li>(prefix) store the right-hand side in an anonymous slot,
     * <li>(prefix) assign the left-hand side to {@code *tmp*},
     * <li>(suffix) assign from the replacement call,
     * <li>(suffix) remove *tmp*,
     * <li>(suffix) remove the anonymous right-hand side slot and answer its value.
     * <ol>
     */
    private static RNode[] createReplacementSequence() {
        return new RNode[5];
    }

    private static final String varSymbol = "*tmp*";

    private static Object constructReplacementPrefix(RNode[] seq, RNode rhs, RNode replacementArg, WriteVariableNode.Mode rhsWriteMode) {
        //@formatter:off
        // store a - need to use temporary, otherwise there is a failure in case multiple calls to
        // the replacement form are chained:
        // x<-c(1); y<-c(1); dim(x)<-1; dim(y)<-1; attr(x, "dimnames")<-(attr(y, "dimnames")<-list("b"))
        //@formatter:on
        final Object rhsSymbol = new Object();

        WriteVariableNode rhsAssign = WriteVariableNode.create(rhsSymbol.toString(), rhs, false, false, rhsWriteMode);
        WriteVariableNode varAssign = WriteVariableNode.create(varSymbol, replacementArg, false, false, WriteVariableNode.Mode.TEMP);

        seq[0] = rhsAssign;
        seq[1] = varAssign;

        return rhsSymbol;
    }

    private static ReplacementNode constructReplacementSuffix(RNode[] seq, RNode assignFromTemp, Object rhsSymbol, SourceSection source) {
        // remove var and rhs, returning rhs' value
        RemoveAndAnswerNode rmVar = RemoveAndAnswerNode.create(varSymbol);
        RemoveAndAnswerNode rmRhs = RemoveAndAnswerNode.create(rhsSymbol);

        // assemble
        seq[2] = assignFromTemp;
        seq[3] = rmVar;
        seq[4] = rmRhs;
        ReplacementNode replacement = new ReplacementNode(source, seq);
        return replacement;
    }

    private RNode constructRecursiveVectorUpdateSuffix(RNode[] seq, RNode updateOp, AccessVector vecAST, SourceSection source, boolean isSuper) {
        seq[2] = updateOp;

        SequenceNode vecUpdate = new SequenceNode(seq);
        vecUpdate.assignSourceSection(source);

        return createVectorUpdate(vecAST, vecUpdate, isSuper, source, true);
    }

    private RNode constructRecursiveFieldUpdateSuffix(RNode[] seq, RNode updateOp, FieldAccess accessAST, SourceSection source, boolean isSuper) {
        seq[2] = updateOp;

        SequenceNode fieldUpdate = new SequenceNode(seq);
        fieldUpdate.assignSourceSection(source);

        return createFieldUpdate(accessAST, fieldUpdate, isSuper, source);
    }

    private static SimpleAccessVariable getVectorVariable(AccessVector v) {
        if (v.getVector() instanceof SimpleAccessVariable) {
            return (SimpleAccessVariable) v.getVector();
        } else if (v.getVector() instanceof AccessVector) {
            return getVectorVariable((AccessVector) v.getVector());
        } else {
            Utils.nyi();
            return null;
        }
    }

    private static SimpleAccessVariable getFieldAccessVariable(FieldAccess a) {
        if (a.getLhs() instanceof SimpleAccessVariable) {
            return (SimpleAccessVariable) a.getLhs();
        } else {
            Utils.nyi();
            return null;
        }
    }

    private RNode createVectorUpdate(AccessVector a, RNode rhs, boolean isSuper, SourceSection source, boolean recursive) {
        int argLength = a.getArguments().size();
        if (!recursive) {
            argLength--; // last argument == RHS
        }
        if (a.getVector() instanceof SimpleAccessVariable) {
            SimpleAccessVariable varAST = (SimpleAccessVariable) a.getVector();
            String vSymbol = RRuntime.toString(varAST.getVariable());

            RNode[] seq = createReplacementSequence();
            ReadVariableNode v = isSuper ? ReadVariableSuperMaterializedNode.create(varAST.getSource(), vSymbol, RType.Any) : ReadVariableNode.create(varAST.getSource(), vSymbol, RType.Any,
                            varAST.shouldCopyValue());
            final Object rhsSymbol = constructReplacementPrefix(seq, rhs, v, WriteVariableNode.Mode.INVISIBLE);
            String rhsSymbolString = RRuntime.toString(rhsSymbol);
            RNode rhsAccess = ReadVariableNode.create(null, rhsSymbolString, RType.Any, false);
            RNode tmpVarAccess = ReadVariableNode.create(null, varSymbol, RType.Any, false);

            CoerceVector coerceVector = CoerceVectorFactory.create(null, null, null);
            RNode updateOp = createPositions(a.getArguments(), argLength, a.isSubset(), null, null, tmpVarAccess, rhsAccess, coerceVector, true);
            RNode assignFromTemp = WriteVariableNode.create(vSymbol, updateOp, false, isSuper, WriteVariableNode.Mode.TEMP);
            return constructReplacementSuffix(seq, assignFromTemp, rhsSymbol, source);
        } else if (a.getVector() instanceof AccessVector) {
            // assign value to the outermost dimension and then the result (recursively) to
            // appropriate position in the lower dimension
            // TODO: it works but perhaps should be revisited

            AccessVector vecAST = (AccessVector) a.getVector();
            SimpleAccessVariable varAST = getVectorVariable(vecAST);
            String vSymbol = RRuntime.toString(varAST.getVariable());
            RNode[] seq = new RNode[3];

            ReadVariableNode v = isSuper ? ReadVariableSuperMaterializedNode.create(varAST.getSource(), vSymbol, RType.Any) : ReadVariableNode.create(varAST.getSource(), vSymbol, RType.Any,
                            varAST.shouldCopyValue());
            final Object rhsSymbol = constructReplacementPrefix(seq, rhs, v, WriteVariableNode.Mode.INVISIBLE);

            RNode rhsAccess = AccessVariable.create(null, rhsSymbol).accept(this);

            CoerceVector coerceVector = CoerceVectorFactory.create(null, null, null);
            RNode updateOp = createPositions(a.getArguments(), argLength, a.isSubset(), null, null, vecAST.accept(this), rhsAccess, coerceVector, true);
            return constructRecursiveVectorUpdateSuffix(seq, updateOp, vecAST, source, isSuper);
        } else if (a.getVector() instanceof FieldAccess) {
            FieldAccess accessAST = (FieldAccess) a.getVector();
            SimpleAccessVariable varAST = getFieldAccessVariable(accessAST);

            String vSymbol = RRuntime.toString(varAST.getVariable());
            RNode[] seq = new RNode[3];
            ReadVariableNode v = isSuper ? ReadVariableSuperMaterializedNode.create(varAST.getSource(), vSymbol, RType.Any) : ReadVariableNode.create(varAST.getSource(), vSymbol, RType.Any,
                            varAST.shouldCopyValue());
            final Object rhsSymbol = constructReplacementPrefix(seq, rhs, v, WriteVariableNode.Mode.INVISIBLE);
            RNode rhsAccess = AccessVariable.create(null, rhsSymbol).accept(this);

            CoerceVector coerceVector = CoerceVectorFactory.create(null, null, null);
            RNode updateOp = createPositions(a.getArguments(), argLength, a.isSubset(), null, null, accessAST.accept(this), rhsAccess, coerceVector, true);
            return constructRecursiveFieldUpdateSuffix(seq, updateOp, accessAST, source, isSuper);
        } else if (a.getVector() instanceof FunctionCall) {
            FunctionCall callAST = (FunctionCall) a.getVector();
            CoerceVector coerceVector = CoerceVectorFactory.create(null, null, null);
            return createPositions(a.getArguments(), argLength, a.isSubset(), null, null, callAST.accept(this), rhs, coerceVector, true);
        } else {
            Utils.nyi();
            return null;
        }
    }

    @Override
    public RNode visit(UpdateVector u) {
        return createVectorUpdate(u.getVector(), u.getRHS().accept(this), u.isSuper(), u.getSource(), false);
    }

    @Override
    public RNode visit(Colon c) {
        // TODO convert to function call
        RNode left = c.getLHS().accept(this);
        RNode right = c.getRHS().accept(this);
        return ColonNode.create(c.getSource(), left, right);
    }

    @Override
    public RNode visit(SimpleAssignVariable n) {
        RNode expression = n.getExpr().accept(this);
        return WriteVariableNode.create(n.getSource(), RRuntime.toString(n.getVariable()), expression, false, n.isSuper());
    }

    private RCallNode prepareReplacementCall(FunctionCall f, List<ArgNode> args, final Object rhsSymbol, boolean simpleReplacement) {
        // massage arguments to replacement function call (replace v with tmp, append a)
        List<ArgNode> rfArgs = new ArrayList<>();
        rfArgs.add(ArgNode.create(null, (Symbol) null, AccessVariable.create(null, varSymbol, false)));
        if (args.size() > 1) {
            for (int i = 1; i < args.size(); ++i) {
                rfArgs.add(args.get(i));
            }
        }
        rfArgs.add(ArgNode.create(null, (Symbol) null, AccessVariable.create(null, rhsSymbol)));

        // replacement function call (use visitor for FunctionCall)
        FunctionCall rfCall = new FunctionCall(f.getSource(), f.getName(), rfArgs, simpleReplacement);
        return (RCallNode) visit(rfCall);
    }

    //@formatter:off
    /**
     * Handle an assignment of the form {@code xxx(v) <- a} (or similar, with additional arguments).
     * These are called "replacements".
     *
     * According to the R language specification, this corresponds to the following code:
     * <pre>
     * '*tmp*' <- v
     * v <- `xxx<-`('*tmp*', a)
     * rm('*tmp*')
     * </pre>
     *
     * We take an anonymous object to store a, as the anonymous object is unique to this
     * replacement. This value must be stored as it is the result of the entire replacement expression.
     */
    //@formatter:on
    @Override
    public RNode visit(Replacement replacement) {
        // preparations
        ASTNode rhsAst = replacement.getExpr();
        RNode rhs = rhsAst.accept(this);
        FunctionCall f = replacement.getReplacementFunctionCall();
        List<ArgNode> args = f.getArguments();
        ASTNode val = args.get(0).getValue();
        if (val instanceof SimpleAccessVariable) {
            SimpleAccessVariable callArg = (SimpleAccessVariable) val;
            String vSymbol = RRuntime.toString(callArg.getVariable());
            RNode[] seq = createReplacementSequence();
            ReadVariableNode replacementCallArg = createReplacementForVariableUsing(callArg, vSymbol, replacement);
            final Object rhsSymbol = constructReplacementPrefix(seq, rhs, replacementCallArg, WriteVariableNode.Mode.COPY);
            RNode replacementCall = prepareReplacementCall(f, args, rhsSymbol, true);
            RNode assignFromTemp = WriteVariableNode.create(vSymbol, replacementCall, false, replacement.isSuper(), WriteVariableNode.Mode.TEMP);
            return constructReplacementSuffix(seq, assignFromTemp, rhsSymbol, replacement.getSource());
        } else if (val instanceof AccessVector) {
            AccessVector callArgAst = (AccessVector) val;
            RNode replacementArg = callArgAst.accept(this);
            RNode[] seq = createReplacementSequence();
            final Object rhsSymbol = constructReplacementPrefix(seq, rhs, replacementArg, WriteVariableNode.Mode.COPY);
            RNode replacementCall = prepareReplacementCall(f, args, rhsSymbol, false);
            // see AssignVariable.writeVector (number of args must match)
            callArgAst.getArguments().add(ArgNode.create(rhsAst.getSource(), "value", rhsAst));
            RNode assignFromTemp = createVectorUpdate(callArgAst, replacementCall, replacement.isSuper(), replacement.getSource(), false);
            return constructReplacementSuffix(seq, assignFromTemp, rhsSymbol, replacement.getSource());
        } else {
            FieldAccess callArgAst = (FieldAccess) val;
            RNode replacementArg = callArgAst.accept(this);
            RNode[] seq = createReplacementSequence();
            final Object rhsSymbol = constructReplacementPrefix(seq, rhs, replacementArg, WriteVariableNode.Mode.COPY);
            RNode replacementCall = prepareReplacementCall(f, args, rhsSymbol, false);
            RNode assignFromTemp = createFieldUpdate(callArgAst, replacementCall, replacement.isSuper(), replacement.getSource());
            return constructReplacementSuffix(seq, assignFromTemp, rhsSymbol, replacement.getSource());
        }
    }

    private static ReadVariableNode createReplacementForVariableUsing(SimpleAccessVariable simpleAccessVariable, String variableSymbol, Replacement replacement) {
        SourceSection argSourceSection = simpleAccessVariable.getSource();
        boolean replacementInSuperEnvironment = replacement.isSuper();
        if (replacementInSuperEnvironment) {
            return ReadVariableSuperMaterializedNode.create(argSourceSection, variableSymbol, RType.Any);
        } else {
            return ReadVariableNode.create(argSourceSection, variableSymbol, RType.Any, simpleAccessVariable.shouldCopyValue());
        }
    }

    @Override
    public RNode visit(SimpleAccessVariable n) {
        return ReadVariableNode.create(n.getSource(), RRuntime.toString(n.getVariable()), RType.Any, n.shouldCopyValue());
    }

    @Override
    public RNode visit(SimpleAccessTempVariable n) {
        String symbol = RRuntime.toString(n.getSymbol());
        return ReadVariableNode.create(n.getSource(), symbol, RType.Any, false);
    }

    @Override
    public RNode visit(SimpleAccessVariadicComponent n) {
        int ind = n.getIndex();
        return new ReadVariadicComponentNode(ind > 0 ? ind - 1 : ind);
    }

    @Override
    public RNode visit(If n) {
        RNode condition = n.getCondition().accept(this);
        RNode thenPart = n.getTrueCase().accept(this);
        RNode elsePart = n.getFalseCase() != null ? n.getFalseCase().accept(this) : null;
        return IfNode.create(n.getSource(), condition, SequenceNode.ensureSequence(thenPart), SequenceNode.ensureSequence(elsePart));
    }

    @Override
    public RNode visit(While loop) {
        RNode condition = loop.getCondition().accept(this);
        RNode body = SequenceNode.ensureSequence(loop.getBody().accept(this));
        return WhileNode.create(loop.getSource(), condition, body, false);
    }

    @Override
    public RNode visit(Break n) {
        return new BreakNode();
    }

    @Override
    public RNode visit(Next n) {
        return new NextNode();
    }

    @Override
    public RNode visit(Repeat loop) {
        RNode body = loop.getBody().accept(this);
        return WhileNode.create(loop.getSource(), ConstantNode.create(true), SequenceNode.ensureSequence(body), true);
    }

    @Override
    public RNode visit(For loop) {
        WriteVariableNode cvar = WriteVariableNode.create(RRuntime.toString(loop.getVariable()), null, false, false);
        RNode range = loop.getRange().accept(this);
        RNode body = loop.getBody().accept(this);
        return ForNode.create(cvar, range, SequenceNode.ensureSequence(body));
    }

    @Override
    public RNode visit(FieldAccess n) {
        AccessFieldNode afn = AccessFieldNodeFactory.create(n.getLhs().accept(this), RRuntime.toString(n.getFieldName()));
        afn.assignSourceSection(n.getSource());
        return afn;
    }

    private RNode createFieldUpdate(FieldAccess a, RNode rhs, boolean isSuper, SourceSection source) {
        if (a.getLhs() instanceof SimpleAccessVariable) {
            SimpleAccessVariable varAST = (SimpleAccessVariable) a.getLhs();
            String vSymbol = RRuntime.toString(varAST.getVariable());

            RNode[] seq = createReplacementSequence();
            ReadVariableNode v = isSuper ? ReadVariableSuperMaterializedNode.create(varAST.getSource(), vSymbol, RType.Any) : ReadVariableNode.create(varAST.getSource(), vSymbol, RType.Any,
                            varAST.shouldCopyValue());
            final Object rhsSymbol = constructReplacementPrefix(seq, rhs, v, WriteVariableNode.Mode.INVISIBLE);
            String rhsSymbolString = RRuntime.toString(rhsSymbol);
            RNode rhsAccess = ReadVariableNode.create(null, rhsSymbolString, RType.Any, false);
            RNode tmpVarAccess = ReadVariableNode.create(null, varSymbol, RType.Any, false);
            UpdateFieldNode ufn = UpdateFieldNodeFactory.create(tmpVarAccess, rhsAccess, RRuntime.toString(a.getFieldName()));
            RNode assignFromTemp = WriteVariableNode.create(vSymbol, ufn, false, isSuper, WriteVariableNode.Mode.TEMP);
            return constructReplacementSuffix(seq, assignFromTemp, rhsSymbol, source);
        } else if (a.getLhs() instanceof AccessVector) {
            AccessVector vecAST = (AccessVector) a.getLhs();
            SimpleAccessVariable varAST = getVectorVariable(vecAST);
            String vSymbol = RRuntime.toString(varAST.getVariable());
            RNode[] seq = new RNode[3];

            ReadVariableNode v = isSuper ? ReadVariableSuperMaterializedNode.create(varAST.getSource(), vSymbol, RType.Any) : ReadVariableNode.create(varAST.getSource(), vSymbol, RType.Any,
                            varAST.shouldCopyValue());
            final Object rhsSymbol = constructReplacementPrefix(seq, rhs, v, WriteVariableNode.Mode.INVISIBLE);

            RNode rhsAccess = AccessVariable.create(null, rhsSymbol).accept(this);

            List<ArgNode> arguments = new ArrayList<>(2);
            arguments.add(ArgNode.create(null, (String) null, Constant.createStringConstant(null, new String[]{a.getFieldName().toString()})));
            CoerceVector coerceVector = CoerceVectorFactory.create(null, null, null);
            RNode updateOp = createPositions(arguments, arguments.size(), false, null, null, vecAST.accept(this), rhsAccess, coerceVector, true);
            return constructRecursiveVectorUpdateSuffix(seq, updateOp, vecAST, source, isSuper);
        } else if (a.getLhs() instanceof FieldAccess) {
            FieldAccess accessAST = (FieldAccess) a.getLhs();
            SimpleAccessVariable varAST = getFieldAccessVariable(accessAST);

            String vSymbol = RRuntime.toString(varAST.getVariable());
            RNode[] seq = new RNode[3];
            ReadVariableNode v = isSuper ? ReadVariableSuperMaterializedNode.create(varAST.getSource(), vSymbol, RType.Any) : ReadVariableNode.create(varAST.getSource(), vSymbol, RType.Any,
                            varAST.shouldCopyValue());
            final Object rhsSymbol = constructReplacementPrefix(seq, rhs, v, WriteVariableNode.Mode.INVISIBLE);
            RNode rhsAccess = AccessVariable.create(null, rhsSymbol).accept(this);
            UpdateFieldNode ufn = UpdateFieldNodeFactory.create(accessAST.accept(this), rhsAccess, RRuntime.toString(a.getFieldName()));
            return constructRecursiveFieldUpdateSuffix(seq, ufn, accessAST, source, isSuper);
        } else {
            Utils.nyi();
            return null;
        }
    }

    @Override
    public RNode visit(UpdateField u) {
        FieldAccess a = u.getVector();
        RNode rhs = u.getRHS().accept(this);
        return createFieldUpdate(a, rhs, u.isSuper(), u.getSource());
    }

}
