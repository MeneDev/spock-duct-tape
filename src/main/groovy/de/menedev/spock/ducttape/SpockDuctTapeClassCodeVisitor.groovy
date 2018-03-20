package de.menedev.spock.ducttape

import groovy.transform.CompileStatic
import org.codehaus.groovy.ast.*
import org.codehaus.groovy.ast.expr.*
import org.codehaus.groovy.ast.stmt.*
import org.codehaus.groovy.control.SourceUnit
import org.codehaus.groovy.runtime.typehandling.GroovyCastException
import org.codehaus.groovy.syntax.Token
import org.codehaus.groovy.syntax.Types
import org.rnorth.ducttape.RetryCountExceededException
import org.rnorth.ducttape.TimeoutException
import org.spockframework.compiler.*
import org.spockframework.compiler.model.Block
import org.spockframework.compiler.model.Method
import org.spockframework.compiler.model.Spec

import java.util.concurrent.CompletableFuture

@CompileStatic
class SpockDuctTapeClassCodeVisitor extends ClassCodeVisitorSupport implements IRewriteResources {

    private final Stack<VariableScope> scopeStack = new Stack<>()
    private final Stack<TryCatchStatement> tryCatchStack = new Stack<>()
    private final Stack<VariableExpression> valueRecorder = new Stack<>()
    private final Stack<VariableExpression> errorCollector = new Stack<>()
    private final Stack<VariableExpression> catcher = new Stack<>()

    private final AstNodeCache astNodeCache = new AstNodeCache()

    private final SourceUnit sourceUnit
    private final SourceLookup lookup
    private final String CATCHER_NAME = '$spock_ducttape_catcher'

    SpockDuctTapeClassCodeVisitor(SourceUnit sourceUnit) {
        this.sourceUnit = sourceUnit
        this.lookup = new SourceLookup(sourceUnit)
    }

    @Override
    protected SourceUnit getSourceUnit() {
        return sourceUnit
    }

    @Override
    void visitBlockStatement(BlockStatement block) {
        scopeStack.push(block.variableScope)
        super.visitBlockStatement(block)
        scopeStack.pop()
    }

    @Override
    void visitClosureExpression(ClosureExpression expression) {
        scopeStack.push(expression.variableScope)
        try {
            addSharedVariablesToClosure(expression)
        } finally {
            super.visitClosureExpression(expression)
            scopeStack.pop()
        }
    }

    @Override
    void visitClosureListExpression(ClosureListExpression cle) {
        scopeStack.push(cle.variableScope)
        super.visitClosureListExpression(cle)
        scopeStack.pop()
    }

    @Override
    void visitForLoop(ForStatement forLoop) {
        scopeStack.push(forLoop.variableScope)
        super.visitForLoop(forLoop)
        scopeStack.pop()
    }

    List<VariableExpression> findDeclaredVariableExpressions(List<Statement> statements) {
        return statements.collect {
            try {
                def expressionStatement = it as ExpressionStatement
                def declarationExpression = expressionStatement.expression as DeclarationExpression
                def variableExpression = declarationExpression.leftExpression as VariableExpression
                return variableExpression
            } catch (Throwable ignored) {
                return null
            }
        }.findAll { it }
    }

    @Override
    void visitMethod(MethodNode node) {
        // isSpockFeature -> store recorder / collector

        boolean foundValueRecorder = false
        boolean foundErrorCollector = false
        boolean catcherDeclared = false
        try {
            def blockStatement = node.code as BlockStatement
            def declaredVariables = findDeclaredVariableExpressions(blockStatement.statements)
            def currentValueRecorder = declaredVariables.find { it.name == '$spock_valueRecorder' }
            def currentErrorCollector = declaredVariables.find { it.name == '$spock_errorCollector' }

            // coerces to true if not null
            foundErrorCollector = errorCollector.push(currentErrorCollector)
            foundValueRecorder = valueRecorder.push(currentValueRecorder)

            // only feature method when both present
            assert foundErrorCollector
            assert foundValueRecorder

            def catcherVariable = declareCatcher(blockStatement)
            catcherDeclared = catcher.push(catcherVariable)

            assert catcherDeclared
            super.visitMethod(node)
        } catch (GroovyCastException | AssertionError ignored) {
        } finally {
            if (foundErrorCollector) {
                errorCollector.pop()
            }

            if (foundValueRecorder) {
                valueRecorder.pop()
            }

            if (catcherDeclared) {
                catcher.pop()
            }
        }
    }

    VariableExpression declareCatcher(BlockStatement blockStatement) {
        // adds
        // def catcher = new CompletableFuture()
        // as first statement
        def catcherVariable = new VariableExpression(CATCHER_NAME)
        blockStatement.statements.add(0,
                new ExpressionStatement(
                        new DeclarationExpression(
                                catcherVariable,
                                new Token(Types.ASSIGNMENT_OPERATOR, "=", 1, 1),
                                new ConstructorCallExpression(new ClassNode(CompletableFuture), new ArgumentListExpression()))))
        catcherVariable.accessedVariable = catcherVariable
        blockStatement.variableScope.putDeclaredVariable(catcherVariable)

        return catcherVariable
    }

    void addDuctTapeCatchStatements(TryCatchStatement statement, VariableScope parentScope, VariableExpression catcher) {
        def oldCatches = new ArrayList<CatchStatement>(statement.catchStatements)

        statement.catchStatements.clear()
        for (Class cls in [TimeoutException, RetryCountExceededException]) {
            def exceptionParameter = new Parameter(new ClassNode(cls), "e")

            statement.addCatch(new CatchStatement(
                    exceptionParameter,
                    new BlockStatement([
                            // wait for CompletableFuture
                            new ExpressionStatement(new DeclarationExpression(
                                    new VariableExpression("result"),
                                    Token.newSymbol(Types.EQUAL, statement.lineNumber, statement.columnNumber),
                                    new MethodCallExpression(catcher, "get", new ArgumentListExpression()))),

                            new IfStatement(new BooleanExpression(new VariableExpression("result")),
                                    new ThrowStatement(new VariableExpression("result")),
                                    new ThrowStatement(new VariableExpression(exceptionParameter))
                            )

                    ], new VariableScope(parentScope)),
            ))
        }

        // add original catch statements after ours
        oldCatches.each {
            statement.addCatch(it)
        }
    }

    @Override
    void visitTryCatchFinally(TryCatchStatement statement) {
        tryCatchStack.push(statement)
        try {
            super.visitTryCatchFinally(statement)
        } finally {
            tryCatchStack.pop()
        }
    }

    BlockStatement toBlockStatement(Statement statement, VariableScope parentScope) {
        if (statement instanceof BlockStatement) {
            return statement
        }

        return new BlockStatement([statement], new VariableScope(parentScope))
    }

    @Override
    void visitMethodCallExpression(MethodCallExpression call) {

        try {
            assert call.objectExpression.type == new ClassNode(org.spockframework.runtime.SpockRuntime)
            assert call.method instanceof ConstantExpression
            assert (call.method as ConstantExpression).value == "verifyMethodCondition"

            def arguments = call.arguments as ArgumentListExpression
            arguments[0].type == new ClassNode(org.spockframework.runtime.ErrorCollector)
            def errorCollector = arguments[0] as VariableExpression
            assert errorCollector.type == new ClassNode(org.spockframework.runtime.ErrorCollector)

            // parameters of verifyMethodCondition
            def recorder = arguments[1] as MethodCallExpression
            def text = arguments[2] as ConstantExpression
            def line = arguments[3] as ConstantExpression
            def column = arguments[4] as ConstantExpression
            def message = arguments[5] as ConstantExpression
            def target = arguments[6] as ClassExpression
            def method = arguments[7] as MethodCallExpression
            def args = arguments[8] as ArrayExpression
            def safe = arguments[9] as MethodCallExpression
            def explicit = arguments[10] as ConstantExpression
            def lastVariableNum = arguments[11] as ConstantExpression

            def methodName = unwrapMethodName(method)
            assert methodName
            def unwrapedArgs = unwrapArgs(args)

            assert target.type == new ClassNode(org.rnorth.ducttape.unreliables.Unreliables)
            assert methodName == "retryUntilTrue"

            def recordCallExpression = method
            def spock_valueRecorder = recordCallExpression.objectExpression as VariableExpression
            spock_valueRecorder.name == '$spock_valueRecorder'
            (recordCallExpression.method as ConstantExpression).value == "record"
            def unreliableClosure = unwrapedArgs.last() as ClosureExpression

            def unreliableBlockStatement = unreliableClosure.code as BlockStatement
            if (unreliableBlockStatement.statements.empty) {
                // just an empty closure
                return
            }
            addSharedVariablesToClosure(unreliableClosure)

            List<Statement> rewritten = unreliableBlockStatement.statements
                    .collect { it as ExpressionStatement }
                    .collect {
                        def statement = ConditionRewriter.rewriteImplicitCondition(it, this)
                        return statement
                    }
            def lastException = this.catcher.peek()
            def parentScope = scopeStack.peek()
            def self = this
            rewritten.each {
                try {
                    def tryCatchStatement = it as TryCatchStatement
                    BlockStatement tryBlockStatement = self.toBlockStatement(tryCatchStatement.tryStatement, parentScope)
                    tryBlockStatement.statements.add(new ExpressionStatement(new MethodCallExpression(lastException, "complete", new ArgumentListExpression(new ConstantExpression(null)))))

                    def scope = new VariableScope(parentScope)
                    List<Statement> catchStatements = []

                    // here we catch the exception that happens when a condition is not satisfied
                    // those will be of type class org.spockframework.runtime.ConditionNotSatisfiedError
                    catchStatements.add(new ExpressionStatement(new MethodCallExpression(lastException, "complete", new ArgumentListExpression(new VariableExpression(tryCatchStatement.catchStatements[0].variable)))))
                    catchStatements.add(new ReturnStatement(new ConstantExpression(false)))

                    tryCatchStatement.catchStatements[0].code = new BlockStatement(catchStatements, scope)
                } catch (Throwable t) {
                    throw t
                }
            }
            unreliableBlockStatement.statements.clear()
            unreliableBlockStatement.statements.addAll(rewritten)
            unreliableBlockStatement.statements.add(new ReturnStatement(new ConstantExpression(true)))

            // if we are in a call to verifyMethodCondition, Spock also has wraped it with a try catch.
            // we rewrite it to replace the outer TimeoutException with inner ConditionNotSatisfiedError
            addDuctTapeCatchStatements(tryCatchStack.peek(), parentScope, lastException)
        } catch (GroovyCastException | AssertionError ignored) {
            // this is not a verifyMethodCondition we're looking for, but maybe there is one beneath.
            super.visitMethodCallExpression(call)
        }
    }

    /**
     * Unwraps arguments from Spock's record calls
     * @throws AssertionError when not wrapped by Spock
     * @param args
     * @return
     */
    List<Expression> unwrapArgs(ArrayExpression args) {
        args.expressions.collect {
            assert it instanceof MethodCallExpression
            def objectExpression = it.objectExpression
            assert objectExpression instanceof VariableExpression
            assert objectExpression.getName() == '$spock_valueRecorder'
            def method = it.method
            assert method instanceof ConstantExpression
            assert method.getValue() == "record"
            it as MethodCallExpression
        }.collect {
            def arguments = it.arguments
            assert arguments instanceof ArgumentListExpression
            arguments.getExpression(1) as Expression
        }
    }


    /**
     * Unwraps arguments from Spock's record calls
     * @throws AssertionError when not wrapped by Spock
     * @param args
     * @return
     */
    String unwrapMethodName(MethodCallExpression methodCallExpression) {
        def arguments = methodCallExpression.arguments as ArgumentListExpression
        def nameExpression = arguments.getExpression(1) as ConstantExpression
        return nameExpression.text
    }

    void addSharedVariablesToClosure(ClosureExpression expression) {
        // we need to adjust the variable scope of all closures
        // closures are used quite heavily in spock: there are various calls to ValueRecorder.record
        // and of course the closure where the conditions we want to check are in.

        def valueRecorder = this.valueRecorder.peek()
        def errorCollector = this.errorCollector.peek()

        expression.variableScope.removeReferencedClassVariable(valueRecorder.name)
        def hasValueRecorder = true
        if (hasValueRecorder) {
            expression.variableScope.putReferencedLocalVariable(valueRecorder)
            valueRecorder.closureSharedVariable = true
        }

        expression.variableScope.removeReferencedClassVariable(errorCollector.name)
        def hasErrorCollector = true
        if (hasErrorCollector) {
            expression.variableScope.putReferencedLocalVariable(errorCollector)
            errorCollector.closureSharedVariable = true
        }

        def catcher = this.catcher.peek()
        expression.variableScope.putReferencedLocalVariable(catcher)
        catcher.closureSharedVariable = true
    }

    @Override
    Spec getCurrentSpec() {
        return null
    }

    @Override
    Method getCurrentMethod() {
        return null
    }

    @Override
    Block getCurrentBlock() {
        return null
    }

    @Override
    void defineRecorders(List<Statement> stats, boolean enableErrorCollector) {
    }

    @Override
    VariableExpression captureOldValue(Expression oldValue) {
        return null
    }

    @Override
    MethodCallExpression getMockInvocationMatcher() {
        return null
    }

    @Override
    AstNodeCache getAstNodeCache() {
        return astNodeCache
    }

    @Override
    public String getSourceText(ASTNode node) {
        return lookup.lookup(node)
    }

    @Override
    ErrorReporter getErrorReporter() {
        return null
    }
}
