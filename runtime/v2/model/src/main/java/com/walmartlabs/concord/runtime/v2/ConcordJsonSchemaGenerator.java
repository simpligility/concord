package com.walmartlabs.concord.runtime.v2;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2020 Walmart Inc.
 * -----
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
 * =====
 */

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.jsonFormatVisitors.JsonFormatVisitorWrapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
import com.google.common.collect.ImmutableMap;
import com.kjetland.jackson.jsonSchema.JsonSchemaConfig;
import com.kjetland.jackson.jsonSchema.JsonSchemaGenerator;
import com.kjetland.jackson.jsonSchema.SubclassesResolver;
import com.kjetland.jackson.jsonSchema.SubclassesResolverImpl;
import com.walmartlabs.concord.imports.Imports;
import com.walmartlabs.concord.runtime.v2.model.Form;
import com.walmartlabs.concord.runtime.v2.model.ProcessDefinition;
import com.walmartlabs.concord.runtime.v2.model.ProcessDefinitionConfiguration;
import com.walmartlabs.concord.runtime.v2.model.Step;
import com.walmartlabs.concord.runtime.v2.model.Trigger;
import com.walmartlabs.concord.runtime.v2.schema.ImportsMixIn;
import com.walmartlabs.concord.runtime.v2.schema.ProcessDefinitionConfigurationMixIn;
import com.walmartlabs.concord.runtime.v2.schema.ProcessDefinitionMixIn;
import com.walmartlabs.concord.runtime.v2.schema.StepMixIn;
import com.walmartlabs.concord.runtime.v2.schema.TriggerMixIn;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.util.*;
import java.util.function.Supplier;

public class ConcordJsonSchemaGenerator {

    public static JsonNode generate() {
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JsonSchemaModule());

        JsonSchemaGenerator jsonSchemaGenerator = new JsonSchemaGenerator(objectMapper, config());
        JsonNode jsonSchema = jsonSchemaGenerator.generateJsonSchema(ProcessDefinition.class);

        // remove type attribute for entities with `@JsonTypeInfo`
        clearAllProperty(jsonSchema, "@type");
        clearProperty(path(jsonSchema, "definitions/ImmutableMvnDefinition"), "type");
        clearProperty(path(jsonSchema, "definitions/ImmutableGitDefinition"), "type");
        clearProperty(path(jsonSchema, "definitions/ImmutableDirectoryDefinition"), "type");

        clearAllProperty(jsonSchema, "removeMe");

        // SwitchStep
        JsonNode switchDefault = path(jsonSchema, "definitions/SwitchStep/properties/default");
        ObjectNode switchStep = (ObjectNode) path(jsonSchema, "definitions/SwitchStep");
        switchStep.set("additionalProperties", switchDefault);

        // remove invalid required primitive attributes
        removeRequired(path(jsonSchema, "definitions/ProcessDefinitionConfiguration"), "debug");
        removeRequired(path(jsonSchema, "definitions/EventConfiguration"), "recordEvents", "recordTaskInVars", "truncateInVars", "truncateMaxStringLength", "truncateMaxArrayLength", "truncateMaxDepth", "recordTaskOutVars", "truncateOutVars", "recordTaskMeta", "truncateMeta");

        return jsonSchema;
    }

    public static void main(String[] args) throws Exception {
        JsonNode jsonSchema = generate();

        try (OutputStream os = outputStream(args)) {
            new ObjectMapper().writerWithDefaultPrettyPrinter()
                    .writeValue(os, jsonSchema);
        }
    }

    private static OutputStream outputStream(String[] args) throws IOException {
        if (args.length == 1) {
            Path schemaFile = Paths.get(args[0]);
            if (Files.notExists(schemaFile.getParent())) {
                Files.createDirectories(schemaFile.getParent());
            }
            return Files.newOutputStream(schemaFile, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        } else {
            return System.out;
        }
    }

    private static JsonSchemaConfig config() {
        boolean autoGenerateTitleForProperties = false;
        Optional<String> defaultArrayFormat = Optional.empty();
        boolean useOneOfForOption = false;
        boolean useOneOfForNullables = false;
        boolean usePropertyOrdering = false;
        boolean hidePolymorphismTypeProperty = false;
        boolean disableWarnings = false;
        boolean useMinLengthForNotNull = false;
        boolean useTypeIdForDefinitionName = false;
        Map<String, String> customType2FormatMapping = Collections.emptyMap();
        boolean useMultipleEditorSelectViaProperty = false;
        Set<Class<?>> uniqueItemClasses = Collections.emptySet();
        Map<Class<?>, Class<?>> classTypeReMapping = ImmutableMap.of(Imports.class, ImportsMixIn.class, Form.class, Object.class);
        Map<String, Supplier<JsonNode>> jsonSuppliers = Collections.emptyMap();
        SubclassesResolver subclassesResolver = new SubclassesResolverImpl();
        boolean failOnUnknownProperties = true;
        List<Class<?>> javaxValidationGroups = null;

        return JsonSchemaConfig.create(autoGenerateTitleForProperties, defaultArrayFormat, useOneOfForOption, useOneOfForNullables,
                usePropertyOrdering, hidePolymorphismTypeProperty, disableWarnings, useMinLengthForNotNull, useTypeIdForDefinitionName,
                customType2FormatMapping, useMultipleEditorSelectViaProperty, uniqueItemClasses, classTypeReMapping, jsonSuppliers,
                subclassesResolver, failOnUnknownProperties, javaxValidationGroups);

    }

    private static JsonNode path(JsonNode root, String path) {
        JsonNode n = root;
        for (String p : path.split("/")) {
            n = n.path(p);
        }
        return n;
    }

    private static void removeProperty(JsonNode node, String propName) {
        JsonNode propsNode = node.path("properties");
        if (propsNode instanceof ObjectNode) {
            ((ObjectNode) propsNode).remove(propName);
        }
    }

    private static void removeRequired(JsonNode node, String... fieldNames) {
        JsonNode requiredNode = node.path("required");
        if (requiredNode.isMissingNode()) {
            return;
        }

        for (Iterator<JsonNode> it = requiredNode.elements(); it.hasNext(); ) {
            JsonNode n = it.next();
            if (Arrays.stream(fieldNames).anyMatch(f -> f.equals(n.asText()))) {
                it.remove();
            }
        }
        if (!requiredNode.elements().hasNext()) {
            ((ObjectNode) node).remove("required");
        }
    }

    private static void clearProperty(JsonNode node, String propName) {
        removeProperty(node, propName);
        removeRequired(node, propName);
    }

    private static void clearAllProperty(JsonNode node, String propName) {
        for (Iterator<JsonNode> it = node.elements(); it.hasNext(); ) {
            JsonNode n = it.next();
            if (n instanceof ObjectNode) {
                clearProperty(n, propName);
                clearAllProperty(n, propName);
            }
        }
    }

    private static class JsonSchemaModule extends SimpleModule {

        public JsonSchemaModule() {
            setMixInAnnotation(ProcessDefinition.class, ProcessDefinitionMixIn.class);
            setMixInAnnotation(ProcessDefinitionConfiguration.class, ProcessDefinitionConfigurationMixIn.class);
            setMixInAnnotation(Trigger.class, TriggerMixIn.class);
            setMixInAnnotation(Step.class, StepMixIn.class);

            addSerializer(Duration.class, new StdSerializer<Duration>(Duration.class) {
                @Override
                public void serialize(Duration value, JsonGenerator gen, SerializerProvider provider) {
                    // do nothing
                }

                @Override
                public void acceptJsonFormatVisitor(JsonFormatVisitorWrapper visitor, JavaType typeHint) throws JsonMappingException {
                    visitor.expectStringFormat(typeHint);
                }
            });
        }
    }
}
