/*
 * Copyright 2020 the original author or authors.
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
package org.gradle.api.plugins.internal.support;

import com.google.common.collect.Lists;
import org.gradle.api.Task;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.ConfigurationContainer;
import org.gradle.api.capabilities.Capability;
import org.gradle.api.component.AdhocComponentWithVariants;
import org.gradle.api.component.SoftwareComponent;
import org.gradle.api.component.SoftwareComponentContainer;
import org.gradle.api.internal.artifacts.dsl.LazyPublishArtifact;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.plugins.BasePlugin;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.api.plugins.JavaPluginExtension;
import org.gradle.api.plugins.JvmEcosystemUtilities;
import org.gradle.api.plugins.internal.JavaConfigurationVariantMapping;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.SourceSetContainer;
import org.gradle.api.tasks.TaskContainer;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.api.tasks.bundling.Jar;
import org.gradle.internal.component.external.model.ImmutableCapability;
import org.gradle.internal.component.external.model.ProjectDerivedCapability;
import org.gradle.language.base.plugins.LifecycleBasePlugin;
import org.gradle.util.TextUtil;

import javax.annotation.Nullable;
import java.util.List;

import static org.gradle.api.attributes.DocsType.JAVADOC;
import static org.gradle.api.attributes.DocsType.SOURCES;
import static org.gradle.api.plugins.internal.JvmPluginsHelper.configureJavaDocTask;

class DefaultJavaComponentBuilder implements JavaComponentBuilderInternal {
    private final String name;
    private final JavaPluginExtension javaPluginExtension;
    private final JvmEcosystemUtilities jvmEcosystemUtilities;
    private final SourceSetContainer sourceSets;
    private final ConfigurationContainer configurations;
    private final TaskContainer tasks;
    private final SoftwareComponentContainer components;
    private final ProjectInternal project;
    private String displayName;
    private SourceSet sourceSet;
    private boolean exposeApi;
    private boolean jar;
    private boolean javadocJar;
    private boolean sourcesJar;
    private boolean published;
    private boolean overrideDefaultCapability = true;
    private final List<Capability> capabilities = Lists.newArrayListWithExpectedSize(2);

    public DefaultJavaComponentBuilder(String name,
                                       Capability defaultCapability,
                                       JavaPluginExtension javaPluginExtension,
                                       JvmEcosystemUtilities jvmEcosystemUtilities,
                                       SourceSetContainer sourceSets,
                                       ConfigurationContainer configurations,
                                       TaskContainer tasks,
                                       SoftwareComponentContainer components,
                                       // Ideally we should use project but more specific components
                                       // like the previous parameters. However for project derived
                                       // capabilities we need a handle on the project coordinates
                                       // which can't be externalized. So we use Project but must
                                       // reduce the scope to this usage only so that we can replace
                                       // it later
                                       ProjectInternal project) {
        this.name = name;
        this.javaPluginExtension = javaPluginExtension;
        this.jvmEcosystemUtilities = jvmEcosystemUtilities;
        this.sourceSets = sourceSets;
        this.configurations = configurations;
        this.tasks = tasks;
        this.components = components;
        this.project = project;
        this.capabilities.add(defaultCapability);
    }

    @Override
    public JvmEcosystemUtilities.JavaComponentBuilder withDisplayName(String displayName) {
        this.displayName = displayName;
        return this;
    }

    @Override
    public JvmEcosystemUtilities.JavaComponentBuilder exposesApi() {
        exposeApi = true;
        return this;
    }

    @Override
    public JvmEcosystemUtilities.JavaComponentBuilder withJar() {
        jar = true;
        return this;
    }

    @Override
    public JvmEcosystemUtilities.JavaComponentBuilder withJavadocJar() {
        javadocJar = true;
        return this;
    }

    @Override
    public JvmEcosystemUtilities.JavaComponentBuilder withSourcesJar() {
        sourcesJar = true;
        return this;
    }

    @Override
    public JvmEcosystemUtilities.JavaComponentBuilder usingSourceSet(SourceSet sourceSet) {
        this.sourceSet = sourceSet;
        return this;
    }

    @Override
    public JvmEcosystemUtilities.JavaComponentBuilder capability(Capability capability) {
        if (overrideDefaultCapability) {
            capabilities.clear();
            overrideDefaultCapability = false;
        }
        capabilities.add(capability);
        return this;
    }

    @Override
    public JvmEcosystemUtilities.JavaComponentBuilder capability(String group, String name, @Nullable String version) {
        return capability(new ImmutableCapability(group, name, version));
    }

    @Override
    public JvmEcosystemUtilities.JavaComponentBuilder secondaryComponent() {
        return capability(new ProjectDerivedCapability(project, name));
    }

    @Override
    public JvmEcosystemUtilities.JavaComponentBuilder published() {
        jar = true;
        published = true;
        return this;
    }

    void build() {
        SourceSet sourceSet = this.sourceSet == null ? sourceSets.maybeCreate(name) : this.sourceSet;
        boolean mainSourceSet = SourceSet.isMain(sourceSet);
        String apiConfigurationName;
        String implementationConfigurationName;
        String apiElementsConfigurationName;
        String runtimeElementsConfigurationName;
        String compileOnlyConfigurationName;
        String runtimeOnlyConfigurationName;
        if (mainSourceSet) {
            apiConfigurationName = name + "Api";
            implementationConfigurationName = name + "Implementation";
            apiElementsConfigurationName = apiConfigurationName + "Elements";
            runtimeElementsConfigurationName = name + "RuntimeElements";
            compileOnlyConfigurationName = name + "CompileOnly";
            runtimeOnlyConfigurationName = name + "RuntimeOnly";
        } else {
            apiConfigurationName = sourceSet.getApiConfigurationName();
            implementationConfigurationName = sourceSet.getImplementationConfigurationName();
            apiElementsConfigurationName = sourceSet.getApiElementsConfigurationName();
            runtimeElementsConfigurationName = sourceSet.getRuntimeElementsConfigurationName();
            compileOnlyConfigurationName = sourceSet.getCompileOnlyConfigurationName();
            runtimeOnlyConfigurationName = sourceSet.getRuntimeOnlyConfigurationName();
        }

        String displayName = this.displayName == null ? name : this.displayName;
        // In the general case, the following configurations are already created
        // but if we're using the "main" source set, it means that the component we're creating shares
        // the same source set (main) but declares its dependencies in its own buckets, so we need
        // to create them
        Configuration implementation = bucket("Implementation", implementationConfigurationName, displayName);
        Configuration compileOnly = bucket("Compile-Only", compileOnlyConfigurationName, displayName);
        Configuration runtimeOnly = bucket("Runtime-Only", runtimeOnlyConfigurationName, displayName);

        TaskProvider<Task> jarTask = jar ? registerOrGetJarTask(sourceSet, displayName) : null;
        Configuration api = exposeApi ? bucket("API", apiConfigurationName, displayName) : null;
        Configuration apiElements = exposeApi ? jvmEcosystemUtilities.createOutgoingElements(apiElementsConfigurationName, builder -> {
            builder.fromSourceSet(sourceSet)
                .providesApi()
                .withDescription("API elements for " + displayName)
                .extendsFrom(api)
                .withCapabilities(capabilities)
                .withClassDirectoryVariant();
            if (jarTask != null) {
                builder.addArtifact(jarTask);
            }
        }) : null;
        if (exposeApi) {
            implementation.extendsFrom(api);
        }

        Configuration runtimeElements = jvmEcosystemUtilities.createOutgoingElements(runtimeElementsConfigurationName, builder -> {
            builder.fromSourceSet(sourceSet)
                .providesRuntime()
                .withDescription("Runtime elements for " + displayName)
                .extendsFrom(implementation, runtimeOnly)
                .withCapabilities(capabilities);
            if (jarTask != null) {
                builder.addArtifact(jarTask);
            }
        });
        if (mainSourceSet) {
            // we need to wire the compile only and runtime only to the classpath configurations
            configurations.getByName(sourceSet.getCompileClasspathConfigurationName()).extendsFrom(implementation, compileOnly);
            configurations.getByName(sourceSet.getRuntimeClasspathConfigurationName()).extendsFrom(implementation, runtimeOnly);
            // and we also want the feature dependencies to be available on the test classpath
            configurations.getByName(JavaPlugin.TEST_COMPILE_CLASSPATH_CONFIGURATION_NAME).extendsFrom(implementation);
            configurations.getByName(JavaPlugin.TEST_RUNTIME_CLASSPATH_CONFIGURATION_NAME).extendsFrom(implementation, runtimeOnly);
        }

        final AdhocComponentWithVariants component = findJavaComponent();
        configureJavaDocTask(name, sourceSet, tasks, javaPluginExtension);
        if (javadocJar) {
            configureDocumentationVariantWithArtifact(sourceSet.getJavadocElementsConfigurationName(), mainSourceSet ? null : name, displayName, JAVADOC, sourceSet.getJavadocJarTaskName(), tasks.named(sourceSet.getJavadocTaskName()), component);
        }
        if (sourcesJar) {
            configureDocumentationVariantWithArtifact(sourceSet.getSourcesElementsConfigurationName(), mainSourceSet ? null : name, displayName, SOURCES, sourceSet.getSourcesJarTaskName(), sourceSet.getAllSource(), component);
        }

        if (published && component != null) {
            if (apiElements != null) {
                component.addVariantsFromConfiguration(apiElements, new JavaConfigurationVariantMapping("compile", true));
            }
            component.addVariantsFromConfiguration(runtimeElements, new JavaConfigurationVariantMapping("runtime", true));
        }
    }

    private Configuration bucket(String kind, String configName, String displayName) {
        Configuration configuration = configurations.maybeCreate(configName);
        configuration.setDescription(kind + " dependencies for " + displayName);
        configuration.setCanBeResolved(false);
        configuration.setCanBeConsumed(false);
        return configuration;
    }

    private TaskProvider<Task> registerOrGetJarTask(SourceSet sourceSet, String displayName) {
        String jarTaskName = sourceSet.getJarTaskName();
        if (!tasks.getNames().contains(jarTaskName)) {
            tasks.register(jarTaskName, Jar.class, jar -> {
                jar.setDescription("Assembles a jar archive containing the classes of the '" + displayName + "'.");
                jar.setGroup(BasePlugin.BUILD_GROUP);
                jar.from(sourceSet.getOutput());
                jar.getArchiveClassifier().set(TextUtil.camelToKebabCase(name));
            });
        }
        return tasks.named(jarTaskName);
    }

    public void configureDocumentationVariantWithArtifact(String variantName, @Nullable String name, @Nullable String displayName, String docsType, String jarTaskName, Object artifactSource, @Nullable AdhocComponentWithVariants component) {
        Configuration variant = configurations.maybeCreate(variantName);
        variant.setVisible(false);
        variant.setDescription(docsType + " elements for " + (displayName == null ? "main" : displayName) + ".");
        variant.setCanBeResolved(false);
        variant.setCanBeConsumed(true);
        jvmEcosystemUtilities.configureAttributes(variant, attributes -> attributes.documentation(docsType)
            .runtimeUsage()
            .withExternalDependencies());
        capabilities.forEach(variant.getOutgoing()::capability);

        if (!tasks.getNames().contains(jarTaskName)) {
            TaskProvider<Jar> jarTask = tasks.register(jarTaskName, Jar.class, jar -> {
                jar.setDescription("Assembles a jar archive containing the " + (displayName == null ? "main " + docsType + "." : (docsType + " of the '" + displayName + "'.")));
                jar.setGroup(BasePlugin.BUILD_GROUP);
                jar.from(artifactSource);
                jar.getArchiveClassifier().set(TextUtil.camelToKebabCase(name == null ? docsType : (name + "-" + docsType)));
            });
            if (tasks.getNames().contains(LifecycleBasePlugin.ASSEMBLE_TASK_NAME)) {
                tasks.named(LifecycleBasePlugin.ASSEMBLE_TASK_NAME).configure(task -> task.dependsOn(jarTask));
            }
        }
        TaskProvider<Task> jar = tasks.named(jarTaskName);
        variant.getOutgoing().artifact(new LazyPublishArtifact(jar));
        if (published && component != null) {
            component.addVariantsFromConfiguration(variant, new JavaConfigurationVariantMapping("runtime", true));
        }
    }

    @Nullable
    public AdhocComponentWithVariants findJavaComponent() {
        SoftwareComponent component = components.findByName("java");
        if (component instanceof AdhocComponentWithVariants) {
            return (AdhocComponentWithVariants) component;
        }
        return null;
    }

}
