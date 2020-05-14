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

package org.kie.kogito.codegen.process;

import static com.github.javaparser.StaticJavaParser.parse;
import static org.kie.kogito.codegen.CodegenUtils.interpolateTypes;
import static org.kie.kogito.codegen.CodegenUtils.interpolateArguments;
import static org.kie.kogito.codegen.CodegenUtils.isProcessField;
import static org.kie.kogito.codegen.CodegenUtils.isApplicationField;

import java.util.ArrayList;
import java.util.List;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.expr.StringLiteralExpr;
import com.github.javaparser.ast.type.ClassOrInterfaceType;

import org.drools.core.util.StringUtils;
import org.jbpm.compiler.canonical.TriggerMetaData;
import org.kie.api.definition.process.WorkflowProcess;
import org.kie.kogito.codegen.BodyDeclarationComparator;
import org.kie.kogito.codegen.GeneratorContext;
import org.kie.kogito.codegen.di.DependencyInjectionAnnotator;

public class MessageConsumerGenerator {
    private final String relativePath;

    private final GeneratorContext context;
    private WorkflowProcess process;
    private final String packageName;
    private final String resourceClazzName;
    private final String processClazzName;
    private String processId;
    private String dataClazzName;
    private String modelfqcn;
    private final String processName;
    private final String appCanonicalName;
    private final String messageDataEventClassName;
    private DependencyInjectionAnnotator annotator;
    
    private TriggerMetaData trigger;
    
    public MessageConsumerGenerator(
            GeneratorContext context,
            WorkflowProcess process,
            String modelfqcn,
            String processfqcn,
            String appCanonicalName,
            String messageDataEventClassName,
            TriggerMetaData trigger) {
        this.context = context;
        this.process = process;
        this.trigger = trigger;
        this.packageName = process.getPackageName();
        this.processId = process.getId();
        this.processName = processId.substring(processId.lastIndexOf('.') + 1);
        String classPrefix = StringUtils.capitalize(processName);
        this.resourceClazzName = classPrefix + "MessageConsumer_" + trigger.getOwnerId();
        this.relativePath = packageName.replace(".", "/") + "/" + resourceClazzName + ".java";
        this.modelfqcn = modelfqcn;
        this.dataClazzName = modelfqcn.substring(modelfqcn.lastIndexOf('.') + 1);
        this.processClazzName = processfqcn;
        this.appCanonicalName = appCanonicalName;
        this.messageDataEventClassName = messageDataEventClassName;
    }

    public MessageConsumerGenerator withDependencyInjection(DependencyInjectionAnnotator annotator) {
        this.annotator = annotator;
        return this;
    }

    public String className() {
        return resourceClazzName;
    }
    
    public String generatedFilePath() {
        return relativePath;
    }
    
    protected boolean useInjection() {
        return this.annotator != null;
    }
    
    public String generate() {
        //TODO: ddoyle: ConnectorType only seems relevant for SmallRye implementations atm ....
        String smallRyeConnectorType = getConnectorType();

        CompilationUnit clazz = getMessageConsumerTemplate(smallRyeConnectorType);
        String payloadType = getPayloadType(smallRyeConnectorType);

        List<String> payloadTypeImports = getPayloadTypeImports(smallRyeConnectorType);


        clazz.setPackageDeclaration(process.getPackageName());
        clazz.addImport(modelfqcn);
        for (String nextPayloadTypeImport: payloadTypeImports) {
            clazz.addImport(nextPayloadTypeImport);
        }

        ClassOrInterfaceDeclaration template = clazz.findFirst(ClassOrInterfaceDeclaration.class).get();
        template.setName(resourceClazzName);       
        
        template.findAll(ClassOrInterfaceType.class).forEach(cls -> interpolateTypes(cls, dataClazzName));
        template.findAll(MethodDeclaration.class).stream().filter(md -> md.getNameAsString().equals("configure")).forEach(md -> md.addAnnotation("javax.annotation.PostConstruct"));
        
        String consumeMethodArgumentType = getConsumeMethodArgumentType(smallRyeConnectorType, payloadType);
        template.findAll(MethodDeclaration.class).stream().filter(md -> md.getNameAsString().equals("consume")).forEach(md -> { 
            interpolateArguments(md, consumeMethodArgumentType);
            md.findAll(StringLiteralExpr.class).forEach(str -> str.setString(str.asString().replace("$Trigger$", trigger.getName())));
            md.findAll(ClassOrInterfaceType.class).forEach(t -> t.setName(t.getNameAsString().replace("$DataEventType$", messageDataEventClassName)));
            md.findAll(ClassOrInterfaceType.class).forEach(t -> t.setName(t.getNameAsString().replace("$DataType$", trigger.getDataType())));
            md.findAll(ClassOrInterfaceType.class).forEach(t -> t.setName(t.getNameAsString().replace("$PayloadType$", payloadType)));
        });
        template.findAll(MethodCallExpr.class).forEach(this::interpolateStrings);
        
        if (useInjection()) {
            annotator.withApplicationComponent(template);
            
            template.findAll(FieldDeclaration.class,
                             fd -> isProcessField(fd)).forEach(fd -> annotator.withNamedInjection(fd, processId));
            template.findAll(FieldDeclaration.class,
                             fd -> isApplicationField(fd)).forEach(fd -> annotator.withInjection(fd));

            template.findAll(FieldDeclaration.class,
                    fd -> fd.getVariable(0).getNameAsString().equals("useCloudEvents")).forEach(fd -> annotator.withConfigInjection(fd, "kogito.messaging.as-cloudevents"));
            
            template.findAll(MethodDeclaration.class).stream().filter(md -> md.getNameAsString().equals("consume")).forEach(md -> annotator.withIncomingMessage(md, trigger.getName()));
        } else {
            template.findAll(FieldDeclaration.class,
                             fd -> isProcessField(fd)).forEach(fd -> initializeProcessField(fd, template));
            
            template.findAll(FieldDeclaration.class,
                             fd -> isApplicationField(fd)).forEach(fd -> initializeApplicationField(fd, template));
        }
        template.getMembers().sort(new BodyDeclarationComparator());
        return clazz.toString();
    }

    private CompilationUnit getMessageConsumerTemplate(String smallRyeConnectorType) {
        CompilationUnit clazz;
        
        switch (smallRyeConnectorType) {
            //TODO: If we use the SmallRye Kafka connector, we expect StringDeserializer
            case "smallrye-kafka":
                clazz = parse(this.getClass().getResourceAsStream("/class-templates/MessageConsumerTemplate.java"));
                break;
            default:
                clazz = parse(this.getClass().getResourceAsStream("/class-templates/GenericMessageConsumerTemplate.java"));
                break;
        } 
        return clazz;
    }    

    private String getConnectorType() {
        final String srmConnectorPropertyPrefix = "mp.messaging.incoming.";
        final String srmConnectorPropertyPostfix = ".connector";
        final String srmConnectorProperty = new StringBuilder().append(srmConnectorPropertyPrefix).append(trigger.getName()).append(srmConnectorPropertyPostfix).toString();
        return context.getApplicationProperty(srmConnectorProperty).get();
    }

    private String getPayloadType(String smallRyeConnectorType) {
        String payloadType;
        switch (smallRyeConnectorType) {
            case "smallrye-kafka":
                payloadType = "String";
                break;
            case "smallrye-camel":
                //TODO: ddoyle: infer this from the "endpoint-uri" configuration.
                payloadType = "GenericFile<File>";
                break;
            default:
                payloadType = "String";
                break;
        }
        return payloadType;
    }

    private List<String> getPayloadTypeImports(String smallRyeConnectorType) {
        List<String> payloadTypeImports = new ArrayList<>();
        switch (smallRyeConnectorType) {
            case "smallrye-kafka":
                break;
            case "smallrye-camel":
                //TODO: ddoyle: infer this from the "endpoint-uri" configuration.
                payloadTypeImports.add("org.apache.camel.component.file.GenericFile");
                payloadTypeImports.add("java.io.File");
                break;
            default:
                break;
        }
        return payloadTypeImports;
    }
 
    
    private String getConsumeMethodArgumentType(String smallRyeConnectorType, String payloadType) {
        /*
        final String propertyPrefix = "kogito.mp.messaging.incoming.";
        String propertyName = new StringBuilder(propertyPrefix).append(trigger.getName()).append(".type").toString();

        System.out.println("Retrieving property with name: " + propertyName);
        //Find the property type if explicitly set. If not, we default to String.
        //TODO: We could do something a little smarter by inferring the type from combination of the connector, and the deserializer (kafka), or Camel rout (camel).
        String type = context.getApplicationProperty(propertyName).orElse("String");
        System.out.println("!!!Using consume method argument type:" + type);
        return type;
        */
        String consumeMethodArgumentType;
        switch (smallRyeConnectorType) {
            case "smallrye-kafka":
                consumeMethodArgumentType = "String";
                break;
            case "smallrye-camel":
                //consumeMethodArgumentType = new StringBuilder("Message").append("<").append(payloadType).append(">").toString();
                consumeMethodArgumentType = payloadType;
            default:
                consumeMethodArgumentType = payloadType;
                break;
        }
        return consumeMethodArgumentType;
    }
    
    private void initializeProcessField(FieldDeclaration fd, ClassOrInterfaceDeclaration template) {
        fd.getVariable(0).setInitializer(new ObjectCreationExpr().setType(processClazzName));
    }
    
    private void initializeApplicationField(FieldDeclaration fd, ClassOrInterfaceDeclaration template) {        
        fd.getVariable(0).setInitializer(new ObjectCreationExpr().setType(appCanonicalName));
    }
    
    private void interpolateStrings(MethodCallExpr vv) {
        String s = vv.getNameAsString();        
        String interpolated =
                s.replace("$ModelRef$", StringUtils.capitalize(trigger.getModelRef()));                
        vv.setName(interpolated);
    }
}
