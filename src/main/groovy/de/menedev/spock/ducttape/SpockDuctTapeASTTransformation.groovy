package de.menedev.spock.ducttape

import groovy.transform.CompileStatic
import org.codehaus.groovy.ast.ASTNode
import org.codehaus.groovy.ast.ClassNode
import org.codehaus.groovy.ast.MethodNode
import org.codehaus.groovy.control.CompilePhase
import org.codehaus.groovy.control.SourceUnit
import org.codehaus.groovy.transform.ASTTransformation
import org.codehaus.groovy.transform.GroovyASTTransformation

@CompileStatic
@GroovyASTTransformation(phase = CompilePhase.INSTRUCTION_SELECTION)
class SpockDuctTapeASTTransformation implements ASTTransformation {

    @Override
    void visit(ASTNode[] nodes, SourceUnit sourceUnit) {

        def annotatedNode = nodes[1]
        if (annotatedNode instanceof ClassNode) {
            ClassNode classNode = annotatedNode as ClassNode
            classNode.visitContents(new SpockDuctTapeClassCodeVisitor(sourceUnit))
        } else if (annotatedNode instanceof MethodNode) {
            MethodNode methodNode = annotatedNode as MethodNode
            new SpockDuctTapeClassCodeVisitor(sourceUnit).visitMethod(methodNode)
        }

    }

}
