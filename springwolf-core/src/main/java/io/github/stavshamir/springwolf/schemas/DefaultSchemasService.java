package io.github.stavshamir.springwolf.schemas;

import io.github.stavshamir.springwolf.asyncapi.types.channel.operation.message.header.AsyncHeaders;
import io.github.stavshamir.springwolf.schemas.example.ExampleGenerator;
import io.swagger.v3.core.converter.ModelConverter;
import io.swagger.v3.core.converter.ModelConverters;
import io.swagger.v3.oas.models.media.MapSchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Slf4j
@Service
public class DefaultSchemasService implements SchemasService {

    private final ModelConverters converter = ModelConverters.getInstance();
    private final ExampleGenerator exampleGenerator;

    private final Map<String, Schema> definitions = new HashMap<>();
    private Map<String, Schema> finalizedDefinitions = null;

    public DefaultSchemasService(
            Optional<List<ModelConverter>> externalModelConverters, ExampleGenerator exampleGenerator) {
        externalModelConverters.ifPresent(converters -> converters.forEach(converter::addConverter));
        this.exampleGenerator = exampleGenerator;
    }

    @Override
    public synchronized Map<String, Schema> getDefinitions() {
        if (finalizedDefinitions == null) {
            finalizeDefinitions();
        }
        return finalizedDefinitions;
    }

    private void finalizeDefinitions() {
        definitions.forEach(this::generateExampleWhenMissing);
        definitions.forEach(this::removeSwaggerSchemaFields);
        finalizedDefinitions = definitions;
    }

    @Override
    public String register(AsyncHeaders headers) {
        log.debug("Registering schema for {}", headers.getSchemaName());

        MapSchema headerSchema = new MapSchema();
        headerSchema.properties(headers);

        this.definitions.put(headers.getSchemaName(), headerSchema);

        return headers.getSchemaName();
    }

    @Override
    public String register(Class<?> type) {
        log.debug("Registering schema for {}", type.getSimpleName());

        Map<String, Schema> schemas = converter.readAll(type);
        this.definitions.putAll(schemas);

        if (schemas.size() == 0 && type.equals(String.class)) {
            this.definitions.put("String", new StringSchema());
            return "String";
        }

        if (schemas.size() == 1) {
            return new ArrayList<>(schemas.keySet()).get(0);
        }

        Set<String> resolvedPayloadModelName = converter.read(type).keySet();
        if (!resolvedPayloadModelName.isEmpty()) {
            return new ArrayList<>(resolvedPayloadModelName).get(0);
        }

        return type.getSimpleName();
    }

    private void removeSwaggerSchemaFields(String schemaName, Schema schema) {
        schema.setAdditionalProperties(null);

        Map<String, Schema> properties = schema.getProperties();
        if (properties != null) {
            properties.forEach(this::removeSwaggerSchemaFields);
        }
    }

    private void generateExampleWhenMissing(String k, Schema schema) {
        if (schema.getExample() == null) {
            log.debug("Generate example for {}", schema.getName());

            Object example = exampleGenerator.fromSchema(schema, definitions);
            schema.setExample(example);
        }
    }
}
