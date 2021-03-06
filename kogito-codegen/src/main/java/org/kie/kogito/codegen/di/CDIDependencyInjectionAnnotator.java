/*
 * Copyright 2019 Red Hat, Inc. and/or its affiliates.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */


package org.kie.kogito.codegen.di;

import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.Modifier.Keyword;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.BinaryExpr;
import com.github.javaparser.ast.expr.BooleanLiteralExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MemberValuePair;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.Name;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.NormalAnnotationExpr;
import com.github.javaparser.ast.expr.SingleMemberAnnotationExpr;
import com.github.javaparser.ast.expr.StringLiteralExpr;
import com.github.javaparser.ast.nodeTypes.NodeWithAnnotations;
import com.github.javaparser.ast.stmt.BlockStmt;


public class CDIDependencyInjectionAnnotator implements DependencyInjectionAnnotator {

    @Override
    public void withApplicationComponent(NodeWithAnnotations<?> node) {
        node.addAnnotation("javax.enterprise.context.ApplicationScoped");
    }
    
    @Override
    public void withNamedApplicationComponent(NodeWithAnnotations<?> node, String name) {
        node.addAnnotation("javax.enterprise.context.ApplicationScoped");
        node.addAnnotation(new SingleMemberAnnotationExpr(new Name("javax.inject.Named"), new StringLiteralExpr(name)));
    }

    @Override
    public void withSingletonComponent(NodeWithAnnotations<?> node) {
        node.addAnnotation("javax.inject.Singleton");
    }
    
    @Override
    public void withNamedSingletonComponent(NodeWithAnnotations<?> node, String name) {
        node.addAnnotation("javax.inject.Singleton");
        node.addAnnotation(new SingleMemberAnnotationExpr(new Name("javax.inject.Named"), new StringLiteralExpr(name)));
    }

    @Override
    public void withInjection(NodeWithAnnotations<?> node) {
        node.addAnnotation("javax.inject.Inject");
    }

    @Override
    public void withNamedInjection(NodeWithAnnotations<?> node, String name) {
        node.addAnnotation("javax.inject.Inject");
        node.addAnnotation(new SingleMemberAnnotationExpr(new Name("javax.inject.Named"), new StringLiteralExpr(name)));
    }
    
    @Override
    public void withOptionalInjection(NodeWithAnnotations<?> node) {
        withInjection(node);
    }
    
    @Override
    public void withIncomingMessage(NodeWithAnnotations<?> node, String channel) {
        node.addAnnotation(new SingleMemberAnnotationExpr(new Name("org.eclipse.microprofile.reactive.messaging.Incoming"), new StringLiteralExpr(channel)));
    }
    
    @Override
    public void withOutgoingMessage(NodeWithAnnotations<?> node, String channel) {
        node.addAnnotation(new SingleMemberAnnotationExpr(new Name("io.smallrye.reactive.messaging.annotations.Stream"), new StringLiteralExpr(channel)));
    }
    
    @Override
    public void withMessageProducer(MethodCallExpr produceMethod, String channel, Expression event) {
        produceMethod.addArgument(event);
    }
    
    @Override
    public MethodDeclaration withInitMethod(Expression... expression) {
        BlockStmt body = new BlockStmt();
        for (Expression exp : expression) {
            body.addStatement(exp);
        }
        MethodDeclaration method = new MethodDeclaration()
                .addModifier(Keyword.PUBLIC)
                .setName("init")
                .setType(void.class)
                .setBody(body);
        
        method.addAndGetParameter("io.quarkus.runtime.StartupEvent", "event").addAnnotation("javax.enterprise.event.Observes");
        
        return method;
    }

    @Override
    public Expression optionalInstanceExists(String fieldName) {
        MethodCallExpr condition = new MethodCallExpr(new NameExpr(fieldName), "isUnsatisfied");
        return new BinaryExpr(condition, new BooleanLiteralExpr(false), BinaryExpr.Operator.EQUALS);
    }
    
    @Override
    public String multiInstanceInjectionType() {
        return "javax.enterprise.inject.Instance";
    }

    @Override
    public String optionalInstanceInjectionType() {
        return "javax.enterprise.inject.Instance";
    }
    
    @Override
    public String applicationComponentType() {
        return "javax.enterprise.context.ApplicationScoped";
    }

    @Override
    public String emitterType(String dataType) {
        return "io.smallrye.reactive.messaging.annotations.Emitter<" + dataType + ">";
    }

    @Override
    public void withConfigInjection(String configKey, NodeWithAnnotations<?> node) {
        node.addAnnotation(new NormalAnnotationExpr(new Name("org.eclipse.microprofile.config.inject.ConfigProperty"), NodeList.nodeList(new MemberValuePair("name", new StringLiteralExpr(configKey)))));
    }

    @Override
    public void withConfigInjection(String configKey, String defaultValue, NodeWithAnnotations<?> node) {
        node.addAnnotation(new NormalAnnotationExpr(new Name("org.eclipse.microprofile.config.inject.ConfigProperty"), NodeList.nodeList(new MemberValuePair("name", new StringLiteralExpr(configKey)),
                                                                                                                                         new MemberValuePair("defaultValue", new StringLiteralExpr(defaultValue)))));
        
    }

}
