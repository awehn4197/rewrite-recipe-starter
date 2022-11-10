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
import org.openrewrite.marker.Markers;

import java.util.*;
import java.util.function.Predicate;

import static java.util.Collections.emptyList;
import static org.openrewrite.Tree.randomId;
import static org.openrewrite.java.tree.Space.EMPTY;

@Value
@EqualsAndHashCode(callSuper = true)
public class MakeFalseInstanceMethodsStatic extends Recipe {

    private static final MethodMatcher SERIALIZABLE_WRITE_OBJECT = new MethodMatcher("java.io.Serializable writeObject(java.io.ObjectOutputStream)");
    private static final MethodMatcher SERIALIZABLE_READ_OBJECT = new MethodMatcher("java.io.Serializable readObject(java.io.ObjectInputStream)");
    private static final MethodMatcher SERIALIZABLE_READ_OBJECT_NO_DATA = new MethodMatcher("java.io.Serializable readObjectNoData()");

    @Override
    public String getDisplayName() {
        return "Make false instance methods static";
    }

    @Override
    public String getDescription() {
        return "Change methods to static if they are private or final, access no instance data, and are not one of the excluded serializable methods.";
    }

    @Override
    public JavaVisitor<ExecutionContext> getVisitor() {
        return new MakeFalseInstanceMethodsStaticVisitor();
    }

    private class MakeFalseInstanceMethodsStaticVisitor extends JavaIsoVisitor<ExecutionContext> {

        private MakeFalseInstanceMethodsStaticVisitor() {}

        @Override
        public J.CompilationUnit visitCompilationUnit(J.CompilationUnit cu, ExecutionContext p) {
            List<J.ClassDeclaration> classes = cu.getClasses(); // this was originally to account for nested classes, don't know if still needed

            // might need to make this for loop contain the above variables and extend over the for loop over eligible methods
            // in the case of multiple non-nested classes since they won't share scope
            for (J.ClassDeclaration clazz : classes) {
                List<J.VariableDeclarations> instanceVariables = new ArrayList<J.VariableDeclarations>();

                List<J.MethodDeclaration> allMethods = new ArrayList<J.MethodDeclaration>();
                List<J.MethodDeclaration> methodsEligibleForUpdate = new ArrayList<J.MethodDeclaration>();
                List<J.MethodDeclaration> instanceMethods = new ArrayList<J.MethodDeclaration>();

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
                        if ((md.hasModifier(J.Modifier.Type.Private) || md.hasModifier(J.Modifier.Type.Final))
                                && !SERIALIZABLE_WRITE_OBJECT.matches(md, clazz)
                                && !SERIALIZABLE_READ_OBJECT.matches(md, clazz)
                                && !SERIALIZABLE_READ_OBJECT_NO_DATA.matches(md, clazz)) {
                            methodsEligibleForUpdate.add(md);
                        }
                    }
                }

                // find any methods that reference instance variables and add them to the list of instance methods
                for (J.MethodDeclaration method : allMethods) {
                    setCursor(new Cursor(getCursor(), method));
                    Cursor methodScope = getCursor();
                    for (J.VariableDeclarations instanceVariable : instanceVariables) {
                        J.Identifier variableName = instanceVariable.getVariables().get(0).getName(); // this isn't robust enough, i admit
                        List<J> readReferences = References.findRhsReferences(methodScope.getValue(), variableName);
                        List<Statement> assignmentReferences = References.findLhsReferences(methodScope.getValue(), variableName);
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
                        setCursor(new Cursor(getCursor(), method));
                        Cursor methodScope = getCursor();
                        for (J.MethodDeclaration instanceMethod : instanceMethods) {
                            J.Identifier instanceMethodName = instanceMethod.getName();
                            List<J> readReferences = References.findRhsReferences(methodScope.getValue(), instanceMethodName);
                            List<Statement> assignmentReferences = References.findLhsReferences(methodScope.getValue(), instanceMethodName);
                            if (readReferences.size() > 0 || assignmentReferences.size() > 0) {
                                newInstanceMethods.add(instanceMethod);
                                methodsEligibleForUpdate.remove(method);
                            }
                        }
                    }
                    instanceMethods.addAll(newInstanceMethods);
                }

                // modify any eligible methods to include static flag
                for (J.MethodDeclaration eligibleMethod : methodsEligibleForUpdate) {
                    if (!eligibleMethod.hasModifier(J.Modifier.Type.Static)) {
                        List<J.Modifier> modifiers = eligibleMethod.getModifiers();
                        J.Modifier staticModifier = new J.Modifier(randomId(), Space.build(" ", emptyList()), Markers.EMPTY, J.Modifier.Type.Static, emptyList());
                        modifiers.add(staticModifier);
                        eligibleMethod.withModifiers(modifiers);
                    }
                }
            }

            return cu;
        }

//        @Override
//        public J.MethodDeclaration visitMethodDeclaration(J.MethodDeclaration method, ExecutionContext p) {
//            J.MethodDeclaration m = super.visitMethodDeclaration(method, p);
//            J.ClassDeclaration classDecl = getCursor().firstEnclosing(J.ClassDeclaration.class);
//            if (classDecl == null) {
//                return m;
//            }
//        }
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
