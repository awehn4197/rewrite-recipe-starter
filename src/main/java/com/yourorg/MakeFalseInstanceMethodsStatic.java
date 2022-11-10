/*
 * Copyright 2021 the original author or authors.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * https://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.yourorg;

import lombok.EqualsAndHashCode;
import lombok.Value;
import org.openrewrite.*;
import org.openrewrite.internal.lang.Nullable;
import org.openrewrite.java.ChangeMethodTargetToStatic;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaVisitor;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.tree.*;

import java.util.*;
import java.util.function.Predicate;

@Value
@EqualsAndHashCode(callSuper = true)
public class MakeFalseInstanceMethodsStatic extends Recipe {

    @Override
    public String getDisplayName() {
        return "Change method name";
    }

    @Override
    public String getDescription() {
        return "Rename a method.";
    }

    @Override
    public boolean causesAnotherCycle() {
        return true;
    }

    @Override
    public JavaVisitor<ExecutionContext> getVisitor() {
        return new ChangeMethodNameVisitor();
    }

    private class ChangeMethodNameVisitor extends JavaIsoVisitor<ExecutionContext> {

        private ChangeMethodNameVisitor() {

        }

        @Override
        public J.Block visitBlock(J.Block block, ExecutionContext p) {
            System.out.println("visiting block: " + block.getStatements().toString());

            J.Block b = super.visitBlock(block, p);
            return block;
        }

        @Override
        public J.CompilationUnit visitCompilationUnit(J.CompilationUnit cu, ExecutionContext p) {
            List<J.ClassDeclaration> classes = cu.getClasses(); // this was originally to account for nested classes, don't know if still needed

            List<J.VariableDeclarations> instanceVariables = new ArrayList<J.VariableDeclarations>();

            List<J.MethodDeclaration> allMethods = new ArrayList<J.MethodDeclaration>();
            List<J.MethodDeclaration> methodsEligibleForUpdate = new ArrayList<J.MethodDeclaration>();
            List<J.MethodDeclaration> instanceMethods = new ArrayList<J.MethodDeclaration>();

            for (J.ClassDeclaration clazz : classes) {
                for (Statement statement : clazz.getBody().getStatements()) {
                    // aggregate all variable declarations as instance data
                    if (statement instanceof J.VariableDeclarations) {
                        J.VariableDeclarations vd = (J.VariableDeclarations) statement;
                        if (!vd.hasModifier(J.Modifier.Type.Static)) {
                            instanceVariables.add(vd);
                        }
                    }
                    // aggregate all method declarations and all method declarations that could be marked static
                    if (statement instanceof J.MethodDeclaration) {
                        J.MethodDeclaration md = (J.MethodDeclaration) statement;
                        allMethods.add(md);
                        if ((md.hasModifier(J.Modifier.Type.Private) || md.hasModifier(J.Modifier.Type.Final))) {
                                // todo filter out serializable methods
//                            && md.) {
                            methodsEligibleForUpdate.add(md);
                        }
                    }
                }
            }

            // find any methods that reference instance variables and add them to the list of instance methods
            for (J.MethodDeclaration method : allMethods) {
                Cursor parentScope = getCursor(); // this is the wrong scope. need method body scope but am getting class scope
                for (J.VariableDeclarations instanceVariable : instanceVariables) {
                    J.Identifier variableName = instanceVariable.getVariables().get(0).getName(); // this isn't robust enough, i admit
                    List<J> readReferences = References.findRhsReferences(parentScope.getValue(), variableName);
                    List<Statement> assignmentReferences = References.findLhsReferences(parentScope.getValue(), variableName);
                    if (readReferences.size() > 0 || assignmentReferences.size() > 0) {
                        instanceMethods.add(method);
                        methodsEligibleForUpdate.remove(method);
                    }
                }
            }

            List<J.MethodDeclaration> newInstanceMethods = instanceMethods;

            // use this while loop to continue scanning the list of eligible methods to see if any of them call
            // an instance method in which case they'll be removed. each loop might add more instance methods to check.
            // once no new instance methods have been added, any remaining eligible methods can be marked static
            while (newInstanceMethods.size() > 0) {
                newInstanceMethods.clear(); // reset newInstanceMethods so that it will only contain this iteration's new methods
                for (J.MethodDeclaration method : methodsEligibleForUpdate) {
                    Cursor parentScope = getCursor();
                    for (J.MethodDeclaration instanceMethod : instanceMethods) {
                        J.Identifier instanceMethodName = instanceMethod.getName();
                        List<J> readReferences = References.findRhsReferences(parentScope.getValue(), instanceMethodName);
                        // need to check that these findRhs and findLhs do what I expect them to do for methods since they were
                        // written originally for variables
                        List<Statement> assignmentReferences = References.findLhsReferences(parentScope.getValue(), instanceMethodName);
                        if (readReferences.size() > 0 || assignmentReferences.size() > 0) {
                            newInstanceMethods.add(instanceMethod);
                            methodsEligibleForUpdate.remove(method);
                        }
                    }
                }
                instanceMethods.addAll(newInstanceMethods);
            }

            // TODO - modify methodsEligibleForUpdate to include static flag
            for (J.MethodDeclaration eligibleMethod : methodsEligibleForUpdate) {
                String fullyQualifiedTargetName = eligibleMethod.getMethodType().getDeclaringType().getFullyQualifiedName();
                doAfterVisit(new ChangeMethodTargetToStatic(MethodMatcher.methodPattern(eligibleMethod), fullyQualifiedTargetName, null, null));
            }

            return cu;
        }

        @Override
        public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration classDecl, ExecutionContext executionContext) {
            System.out.println("visiting compilation unit: " + classDecl);
            return classDecl;
        }


            // when visiting variable declarations, i need to make sure they belong to the parent class, not to a method
        @Override
        public J.VariableDeclarations visitVariableDeclarations(J.VariableDeclarations multiVariable, ExecutionContext ctx) {
            System.out.println("visiting variable declaration: " + multiVariable.getVariables().get(0).getSimpleName());
//            if (multiVariable.getTypeExpression() instanceof J.MultiCatch) {
//                return multiVariable;
//            }
//            if (multiVariable.getTypeExpression() != null &&
//                    hasElementType(multiVariable.getTypeExpression().getType(), fullyQualifiedTypeName) &&
//                    isField(getCursor())) {
//                return SearchResult.found(multiVariable);
//            }
            return multiVariable;
        }

        @Override
        public J.MethodDeclaration visitMethodDeclaration(J.MethodDeclaration method, ExecutionContext ctx) {
            System.out.println("visiting method declaration: " + method.getSimpleName());
            J.MethodDeclaration m = super.visitMethodDeclaration(method, ctx);
            J.ClassDeclaration classDecl = getCursor().firstEnclosing(J.ClassDeclaration.class);
            return m;
        }

        @Override
        public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
            J.MethodInvocation m = super.visitMethodInvocation(method, ctx);
            return m;
        }

        @Override
        public J.MemberReference visitMemberReference(J.MemberReference memberRef, ExecutionContext context) {
            J.MemberReference m = super.visitMemberReference(memberRef, context);
            return m;
        }

        /**
         * The only time field access should be relevant to changing method names is static imports.
         * This exists to turn
         * import static com.abc.B.static1;
         * into
         * import static com.abc.B.static2;
         */
        @Override
        public J.FieldAccess visitFieldAccess(J.FieldAccess fieldAccess, ExecutionContext ctx) {
            J.FieldAccess f = super.visitFieldAccess(fieldAccess, ctx);
            return f;
        }
    }

    // I borrowed this from RemoveUnusedLocalVariables. Would abstract it out to be shared given more time.
    private static class References {
        private static final J.Unary.Type[] incrementKinds = {
                J.Unary.Type.PreIncrement,
                J.Unary.Type.PreDecrement,
                J.Unary.Type.PostIncrement,
                J.Unary.Type.PostDecrement
        };
        private static final Predicate<Cursor> isUnaryIncrementKind = t -> t.getValue() instanceof J.Unary && isIncrementKind(t);

        private static boolean isIncrementKind(Cursor tree) {
            if (tree.getValue() instanceof J.Unary) {
                J.Unary unary = tree.getValue();
                return Arrays.stream(incrementKinds).anyMatch(kind -> kind == unary.getOperator());
            }
            return false;
        }

        private static @Nullable Cursor dropParentWhile(Predicate<Object> valuePredicate, Cursor cursor) {
            while (cursor != null && valuePredicate.test(cursor.getValue())) {
                cursor = cursor.getParent();
            }
            return cursor;
        }

        private static @Nullable Cursor dropParentUntil(Predicate<Object> valuePredicate, Cursor cursor) {
            while (cursor != null && !valuePredicate.test(cursor.getValue())) {
                cursor = cursor.getParent();
            }
            return cursor;
        }

        private static boolean isRhsValue(Cursor tree) {
            if (!(tree.getValue() instanceof J.Identifier)) {
                return false;
            }

            Cursor parent = dropParentWhile(J.Parentheses.class::isInstance, tree.getParent());
            assert parent != null;
            if (parent.getValue() instanceof J.Assignment) {
                if (dropParentUntil(J.ControlParentheses.class::isInstance, parent) != null) {
                    return true;
                }
                J.Assignment assignment = parent.getValue();
                return assignment.getVariable() != tree.getValue();
            }

            if (parent.getValue() instanceof J.VariableDeclarations.NamedVariable) {
                J.VariableDeclarations.NamedVariable namedVariable = parent.getValue();
                return namedVariable.getName() != tree.getValue();
            }

            if (parent.getValue() instanceof J.AssignmentOperation) {
                J.AssignmentOperation assignmentOperation = parent.getValue();
                if (assignmentOperation.getVariable() == tree.getValue()) {
                    J grandParent = parent.dropParentUntil(J.class::isInstance).getValue();
                    return (grandParent instanceof Expression || grandParent instanceof J.Return);
                }
            }

            return !(isUnaryIncrementKind.test(parent) && parent.dropParentUntil(J.class::isInstance).getValue() instanceof J.Block);
        }

        /**
         * An identifier is considered a right-hand side ("rhs") read operation if it is not used as the left operand
         * of an assignment, nor as the operand of a stand-alone increment.
         *
         * @param j      The subtree to search.
         * @param target A {@link J.Identifier} to check for usages.
         * @return found {@link J} locations of "right-hand" read calls.
         */
        private static List<J> findRhsReferences(J j, J.Identifier target) {
            final List<J> refs = new ArrayList<>();
            new JavaIsoVisitor<List<J>>() {
                @Override
                public J.Identifier visitIdentifier(J.Identifier identifier, List<J> ctx) {
                    if (identifier.getSimpleName().equals(target.getSimpleName()) && isRhsValue(getCursor())) {
                        ctx.add(identifier);
                    }
                    return super.visitIdentifier(identifier, ctx);
                }
            }.visit(j, refs);
            return refs;
        }

        /**
         * @param j      The subtree to search.
         * @param target A {@link J.Identifier} to check for usages.
         * @return found {@link Statement} locations of "left-hand" assignment write calls.
         */
        private static List<Statement> findLhsReferences(J j, J.Identifier target) {
            JavaIsoVisitor<List<Statement>> visitor = new JavaIsoVisitor<List<Statement>>() {
                @Override
                public J.Assignment visitAssignment(J.Assignment assignment, List<Statement> ctx) {
                    if (assignment.getVariable() instanceof J.Identifier) {
                        J.Identifier i = (J.Identifier) assignment.getVariable();
                        if (i.getSimpleName().equals(target.getSimpleName())) {
                            ctx.add(assignment);
                        }
                    }
                    return super.visitAssignment(assignment, ctx);
                }

                @Override
                public J.AssignmentOperation visitAssignmentOperation(J.AssignmentOperation assignOp, List<Statement> ctx) {
                    if (assignOp.getVariable() instanceof J.Identifier) {
                        J.Identifier i = (J.Identifier) assignOp.getVariable();
                        if (i.getSimpleName().equals(target.getSimpleName())) {
                            ctx.add(assignOp);
                        }
                    }
                    return super.visitAssignmentOperation(assignOp, ctx);
                }

                @Override
                public J.Unary visitUnary(J.Unary unary, List<Statement> ctx) {
                    if (unary.getExpression() instanceof J.Identifier) {
                        J.Identifier i = (J.Identifier) unary.getExpression();
                        if (i.getSimpleName().equals(target.getSimpleName())) {
                            ctx.add(unary);
                        }
                    }
                    return super.visitUnary(unary, ctx);
                }
            };

            List<Statement> refs = new ArrayList<>();
            visitor.visit(j, refs);
            return refs;
        }
    }

}
