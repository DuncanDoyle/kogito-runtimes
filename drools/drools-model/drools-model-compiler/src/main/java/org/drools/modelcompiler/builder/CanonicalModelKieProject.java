/*
 * Copyright 2005 JBoss Inc
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.drools.modelcompiler.builder;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.drools.compiler.commons.jci.compilers.CompilationResult;
import org.drools.compiler.compiler.io.File;
import org.drools.compiler.compiler.io.memory.MemoryFile;
import org.drools.compiler.compiler.io.memory.MemoryFileSystem;
import org.drools.compiler.kie.builder.impl.InternalKieModule;
import org.drools.compiler.kie.builder.impl.KieModuleKieProject;
import org.drools.compiler.kie.builder.impl.ResultsImpl;
import org.drools.compiler.kproject.models.KieBaseModelImpl;
import org.drools.core.util.Drools;
import org.drools.modelcompiler.CanonicalKieModule;
import org.kie.api.builder.Message;
import org.kie.api.builder.ReleaseId;
import org.kie.internal.builder.KnowledgeBuilder;
import org.kie.internal.jci.CompilationProblem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static java.util.stream.Collectors.groupingBy;

import static org.drools.modelcompiler.CanonicalKieModule.MODEL_VERSION;
import static org.drools.modelcompiler.CanonicalKieModule.createFromClassLoader;
import static org.drools.modelcompiler.CanonicalKieModule.getModelFileWithGAV;
import static org.drools.modelcompiler.builder.JavaParserCompiler.getCompiler;

public class CanonicalModelKieProject extends KieModuleKieProject {

    Logger logger = LoggerFactory.getLogger(CanonicalModelKieProject.class);

    public static final String PROJECT_RUNTIME_CLASS = "org.drools.project.model.ProjectRuntime";
    public static final String PROJECT_RUNTIME_RESOURCE_CLASS = PROJECT_RUNTIME_CLASS.replace('.', '/') + ".class";
    protected static final String PROJECT_RUNTIME_SOURCE = PROJECT_RUNTIME_CLASS.replace('.', '/') + ".java";

    public static final String PROJECT_MODEL_CLASS = "org.drools.project.model.ProjectModel";
    public static final String PROJECT_MODEL_RESOURCE_CLASS = PROJECT_MODEL_CLASS.replace('.', '/') + ".class";
    protected static final String PROJECT_MODEL_SOURCE = PROJECT_MODEL_CLASS.replace('.', '/') + ".java";    

    private final boolean isPattern;

    public static BiFunction<InternalKieModule, ClassLoader, KieModuleKieProject> create(boolean isPattern) {
        return (internalKieModule, classLoader) -> new CanonicalModelKieProject(isPattern, internalKieModule, classLoader);
    }

    protected List<ModelBuilderImpl> modelBuilders = new ArrayList<>();

    public CanonicalModelKieProject(boolean isPattern, InternalKieModule kieModule, ClassLoader classLoader) {
        super(kieModule instanceof CanonicalKieModule ? kieModule : createFromClassLoader(classLoader, kieModule), classLoader);
        this.isPattern = isPattern;
    }

    @Override
    protected KnowledgeBuilder createKnowledgeBuilder(KieBaseModelImpl kBaseModel, InternalKieModule kModule) {
        ModelBuilderImpl modelBuilder = new ModelBuilderImpl(getBuilderConfiguration(kBaseModel, kModule), kModule.getReleaseId(), isPattern, isOneClassPerRule());
        modelBuilders.add(modelBuilder);
        return modelBuilder;
    }

    protected boolean isOneClassPerRule() {
        return false;
    }

    @Override
    public void writeProjectOutput(MemoryFileSystem trgMfs, ResultsImpl messages) {
        MemoryFileSystem srcMfs = new MemoryFileSystem();
        ModelWriter modelWriter = new ModelWriter();
        Map<String, String> modelFiles = new HashMap<>();
        Collection<String> sourceFiles = new HashSet<>();

        for (ModelBuilderImpl modelBuilder : modelBuilders) {
            final ModelWriter.Result result = modelWriter.writeModel(srcMfs, trgMfs, modelBuilder.getPackageSources());
            modelFiles.putAll(result.getModelFiles());
            sourceFiles.addAll(result.getSources());
        }

        KieModuleModelMethod modelMethod = new KieModuleModelMethod(kBaseModels);
        ModelSourceClass modelSourceClass = new ModelSourceClass( getInternalKieModule().getReleaseId(), modelMethod, modelFiles);
        String modelSource = modelSourceClass.generate();
        logger.debug(modelSource);
        srcMfs.write(modelSourceClass.getName(), modelSource.getBytes());

        if (!sourceFiles.isEmpty()) {
            String[] sources = sourceFiles.toArray(new String[sourceFiles.size() + 2]);
            sources[sources.length - 2] = PROJECT_MODEL_SOURCE;

            String projectSourceClass = new ProjectSourceClass(modelMethod).generate();
            logger.debug(projectSourceClass);
            srcMfs.write(PROJECT_RUNTIME_SOURCE, projectSourceClass.getBytes());
            sources[sources.length - 1] = PROJECT_RUNTIME_SOURCE;

            CompilationResult res = getCompiler().compile(sources, srcMfs, trgMfs, getClassLoader());

            Stream.of(res.getErrors()).collect(groupingBy(CompilationProblem::getFileName))
                    .forEach((name, errors) -> {
                        errors.forEach(messages::addMessage);
                        File srcFile = srcMfs.getFile(name);
                        if (srcFile instanceof MemoryFile) {
                            String src = new String(srcMfs.getFileContents((MemoryFile) srcFile));
                            messages.addMessage(Message.Level.ERROR, name, "Java source of " + name + " in error:\n" + src);
                        }
                    });

            for (CompilationProblem problem : res.getWarnings()) {
                messages.addMessage(problem);
            }
        } else {
            CompilationResult res = getCompiler().compile(new String[]{PROJECT_MODEL_SOURCE}, srcMfs, trgMfs, getClassLoader());
            System.out.println(res.getErrors());
        }

        writeModelFile(modelFiles.values(), trgMfs, getInternalKieModule().getReleaseId());
    }

    public void writeModelFile(Collection<String> modelSources, MemoryFileSystem trgMfs, ReleaseId releaseId) {
        String pkgNames = MODEL_VERSION + Drools.getFullVersion() + "\n";
        if(!modelSources.isEmpty()) {
            pkgNames += modelSources.stream().collect(Collectors.joining("\n"));
        }
        trgMfs.write(getModelFileWithGAV(releaseId), pkgNames.getBytes() );
    }

    @Override
    protected boolean compileIncludedKieBases() {
        return false;
    }
}