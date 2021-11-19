
package com.azure.spring;

import org.springframework.boot.configurationmetadata.ConfigurationMetadataProperty;
import org.springframework.boot.configurationmetadata.Deprecation;
import org.springframework.boot.configurationprocessor.json.JSONArray;
import org.springframework.boot.configurationprocessor.json.JSONObject;

import java.lang.reflect.Array;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

class JsonConverter {

	private static final ConfigurationMetadataPropertyComparator ITEM_COMPARATOR = new ConfigurationMetadataPropertyComparator();

	JSONArray toJsonArray(Collection<ConfigurationMetadataProperty> properties) throws Exception {
		JSONArray jsonArray = new JSONArray();
		List<ConfigurationMetadataProperty> items = properties.stream().sorted(ITEM_COMPARATOR).collect(Collectors.toList());
		for (ConfigurationMetadataProperty item : items) {
            jsonArray.put(toJsonObject(item));
		}
		return jsonArray;
	}

	JSONObject toJsonObject(ConfigurationMetadataProperty item) throws Exception {
		JSONObject jsonObject = new JSONObject();
		jsonObject.put("name", item.getId());
		jsonObject.putOpt("type", item.getType());
		jsonObject.putOpt("description", item.getDescription());
		Object defaultValue = item.getDefaultValue();
		if (defaultValue != null) {
			putDefaultValue(jsonObject, defaultValue);
		}
        Deprecation deprecation = item.getDeprecation();
		if (deprecation != null) {
//			jsonObject.put("deprecated", true); // backward compatibility
			JSONObject deprecationJsonObject = new JSONObject();
			if (deprecation.getLevel() != null) {
				deprecationJsonObject.put("level", deprecation.getLevel().name().toLowerCase(Locale.ROOT));
			}
			if (deprecation.getReason() != null) {
				deprecationJsonObject.put("reason", deprecation.getReason());
			}
			if (deprecation.getReplacement() != null) {
				deprecationJsonObject.put("replacement", deprecation.getReplacement());
			}
			jsonObject.put("deprecation", deprecationJsonObject);
		}
		return jsonObject;
	}



	private void putDefaultValue(JSONObject jsonObject, Object value) throws Exception {
		Object defaultValue = extractItemValue(value);
		jsonObject.put("defaultValue", defaultValue);
	}

	private Object extractItemValue(Object value) {
		Object defaultValue = value;
		if (value.getClass().isArray()) {
			JSONArray array = new JSONArray();
			int length = Array.getLength(value);
			for (int i = 0; i < length; i++) {
				array.put(Array.get(value, i));
			}
			defaultValue = array;

		}
		return defaultValue;
	}

	private static class ConfigurationMetadataPropertyComparator implements Comparator<ConfigurationMetadataProperty> {

		private static final Comparator<ConfigurationMetadataProperty> ITEM = Comparator.comparing(ConfigurationMetadataProperty::isDeprecated)
				.thenComparing(ConfigurationMetadataProperty::getId, Comparator.nullsFirst(Comparator.naturalOrder()));

		@Override
		public int compare(ConfigurationMetadataProperty o1, ConfigurationMetadataProperty o2) {
			return ITEM.compare(o1, o2);
		}

		private static boolean isDeprecated(ConfigurationMetadataProperty item) {
			return item.getDeprecation() != null;
		}

	}

}
