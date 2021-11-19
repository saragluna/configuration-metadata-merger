package com.azure.spring;

import com.google.common.collect.Sets;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.configurationmetadata.ConfigurationMetadataGroup;
import org.springframework.boot.configurationmetadata.ConfigurationMetadataProperty;
import org.springframework.boot.configurationmetadata.ConfigurationMetadataRepository;
import org.springframework.boot.configurationmetadata.ConfigurationMetadataRepositoryJsonBuilder;
import org.springframework.boot.configurationmetadata.Deprecation;
import org.springframework.boot.configurationprocessor.json.JSONObject;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;


public class ConfigurationMetadataCollector {

    private static final Logger LOGGER = LoggerFactory.getLogger(ConfigurationMetadataCollector.class);
    private static final JsonMarshaller JSON_MARSHALLER = new JsonMarshaller();
    private static final Set<String> AZURE_GROUPS = new HashSet<>() {{

        add("azure");
        add("keyvault");
        add("servicebus");
        add("eventhub");
        add("cosmos");
    }};

    public static void main(String[] args) {
        try {
            ConfigurationMetadataRepository legacyRepository = loadRepositoryForLegacy();
            ConfigurationMetadataRepository modernRepository = loadRepositoryForModern();


            Map<String, ConfigurationMetadataGroup> legacyAzureGroups = findAzureGroups(legacyRepository);
            Map<String, ConfigurationMetadataGroup> modernAzureGroups = findAzureGroups(modernRepository);

            System.out.println();
            System.out.println("Below is groups from Spring Cloud Azure 3.x: ====");
            logGroups(legacyAzureGroups);
            System.out.println();
            System.out.println("Below is groups from Spring Cloud Azure 4.x: ====");
            logGroups(modernAzureGroups);

            Set<String> unchangedProperties = findUnchangedProperties(legacyAzureGroups, modernAzureGroups);

            Collection<ConfigurationMetadataProperty> legacyProperties = findAllProperties(legacyAzureGroups);
            List<ConfigurationMetadataProperty> changedLegacyProperties =
                legacyProperties.stream().filter(property -> !unchangedProperties.contains(property.getId())).collect(Collectors.toList());


            Deprecation deprecation = new Deprecation();
            deprecation.setLevel(Deprecation.Level.ERROR);
            deprecation.setReason("Todo: add deprecation reason");
            deprecation.setReplacement("Todo: add replacement if exists, or delete this entry if none replacement exists");
            changedLegacyProperties.forEach(property -> property.setDeprecation(deprecation));

            System.out.println();
            System.out.println("Begin to marshall all " + changedLegacyProperties.size() + " changed properties ");

            jsonMarshall(changedLegacyProperties);

        } catch (IOException e) {
            LOGGER.error("Failed to load repository", e);
        }
    }

    private static Set<String> findUnchangedProperties(Map<String, ConfigurationMetadataGroup> legacyAzureGroups,
                                         Map<String, ConfigurationMetadataGroup> modernAzureGroups) {

        Set<String> legacyProperties = legacyAzureGroups.values().stream()
                                                        .flatMap(group -> group.getProperties().values().stream())
                                                        .map(ConfigurationMetadataProperty::getId)
                                                        .collect(Collectors.toSet());

        Set<String> modernProperties = modernAzureGroups.values().stream()
                                                        .flatMap(group -> group.getProperties().values().stream())
                                                        .map(ConfigurationMetadataProperty::getId)
                                                        .collect(Collectors.toSet());

        System.out.println();
        System.out.println("There are " + legacyProperties.size() + " legacy properties");
        System.out.println("There are " + modernProperties.size() + " modern properties");
        System.out.println();

        Set<String> unchanged = Sets.newHashSet(legacyProperties);
        Set<String> changed = Sets.newHashSet(legacyProperties);
        unchanged.retainAll(modernProperties);
        System.out.println();
        System.out.println("There are " + unchanged.size() + " unchanged properties, they are: ");
        unchanged.stream().sorted().forEach(System.out::println);

        changed.removeAll(unchanged);
        System.out.println();
        System.out.println("There are " + changed.size() + " changed properties, they are: ");
        changed.stream().sorted().forEach(System.out::println);

        return unchanged;
    }

    private static Map<String, ConfigurationMetadataGroup> findAzureGroups(ConfigurationMetadataRepository repository) {
        return repository
            .getAllGroups()
            .entrySet().stream()
            .filter(entry -> AZURE_GROUPS.stream().anyMatch(g -> entry.getKey().contains(g)))
            .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    private static void jsonMarshall(Collection<ConfigurationMetadataProperty> properties) throws IOException {
        File file = new File(ConfigurationMetadataCollector.class
            .getClassLoader().getResource(".").getFile() + "/additional-spring-configuration-metadata.json");

        if (!file.exists()) {
            file.createNewFile();
        }

        JSON_MARSHALLER.write(properties, new FileOutputStream(file));
    }

    private static void logGroups(Map<String, ConfigurationMetadataGroup> groupMap) {
        groupMap.keySet().stream().sorted().forEach(System.out::println);
    }

    private static Collection<ConfigurationMetadataProperty> findAllProperties(Map<String, ConfigurationMetadataGroup> groupMap) {
        return groupMap.values().stream().flatMap(group -> group.getProperties().values().stream()).collect(Collectors.toList());
    }


    private static class JsonMarshaller {

        private static final int BUFFER_SIZE = 4098;

        public void write(Collection<ConfigurationMetadataProperty> properties, OutputStream outputStream) throws IOException {
            try {
                JSONObject object = new JSONObject();
                JsonConverter converter = new JsonConverter();
                object.put("properties", converter.toJsonArray(properties));
                outputStream.write(object.toString(2).getBytes(StandardCharsets.UTF_8));
            } catch (Exception ex) {
                if (ex instanceof IOException) {
                    throw (IOException) ex;
                }
                if (ex instanceof RuntimeException) {
                    throw (RuntimeException) ex;
                }
                throw new IllegalStateException(ex);
            }
        }
    }

    private static ConfigurationMetadataRepository loadRepositoryForModern() throws IOException {
        ConfigurationMetadataRepositoryJsonBuilder builder = ConfigurationMetadataRepositoryJsonBuilder.create();

        Resource[] resources = new PathMatchingResourcePatternResolver()
            .getResources("classpath*:spring-cloud-azure-4.0-configuration-metadata.json");
        for (Resource resource : resources) {
            try (InputStream inputStream = resource.getInputStream()) {
                builder.withJsonResource(inputStream);
            }
        }
        return builder.build();
    }


    private static ConfigurationMetadataRepository loadRepositoryForLegacy() throws IOException {
        ConfigurationMetadataRepositoryJsonBuilder builder = ConfigurationMetadataRepositoryJsonBuilder.create();

        Resource[] resources = new PathMatchingResourcePatternResolver()
            .getResources("classpath*:/META-INF/spring-configuration-metadata.json");
        for (Resource resource : resources) {
            try (InputStream inputStream = resource.getInputStream()) {
                builder.withJsonResource(inputStream);
            }
        }
        return builder.build();
    }
}
