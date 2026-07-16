/*
 * Copyright 2023-present the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.ai.model.oracle.autoconfigure;

import java.util.Objects;

import javax.sql.DataSource;

import org.springframework.ai.model.SpringAIModelProperties;
import org.springframework.ai.model.SpringAIModels;
import org.springframework.ai.oracle.loader.OracleDocumentPreferences;
import org.springframework.ai.oracle.loader.OracleDocumentReader;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.core.io.ResourceLoader;
import org.springframework.util.StringUtils;

/**
 * {@link AutoConfiguration Auto-configuration} for Oracle document loader.
 *
 * @author Spring AI Contributors
 */
@AutoConfiguration
@EnableConfigurationProperties(OracleDocumentLoaderProperties.class)
@ConditionalOnProperty(name = SpringAIModelProperties.EMBEDDING_MODEL, havingValue = SpringAIModels.ORACLE,
		matchIfMissing = true)
@ConditionalOnClass({ OracleDocumentReader.class, DataSource.class })
public class OracleDocumentLoaderAutoConfiguration {

	public OracleDocumentLoaderAutoConfiguration(OracleDocumentLoaderProperties properties) {
		validateSourceConfiguration(properties);
	}

	@Bean
	@ConditionalOnMissingBean
	@ConditionalOnProperty(prefix = OracleDocumentLoaderProperties.CONFIG_PREFIX, name = "resource")
	public OracleDocumentReader oracleResourceDocumentLoader(DataSource dataSource,
			OracleDocumentLoaderProperties properties, ResourceLoader resourceLoader) {
		String resource = properties.getResource();
		if (resource == null) {
			throw new IllegalArgumentException(
					OracleDocumentLoaderProperties.CONFIG_PREFIX + ".resource must not be null");
		}
		OracleDocumentReader.Builder builder = OracleDocumentReader.builder(dataSource,
				resourceLoader.getResource(resource));
		applyPreferences(builder, properties.getPreferences());
		return builder.build();
	}

	@Bean
	@ConditionalOnMissingBean
	@ConditionalOnProperty(prefix = OracleDocumentLoaderProperties.CONFIG_PREFIX + ".table",
			name = { "owner", "table-name", "column-name" })
	public OracleDocumentReader oracleTableDocumentLoader(DataSource dataSource,
			OracleDocumentLoaderProperties properties) {
		OracleDocumentLoaderTableProperties tableProperties = properties.getTable();
		String owner = Objects.requireNonNull(tableProperties.getOwner());
		String tableName = Objects.requireNonNull(tableProperties.getTableName());
		String columnName = Objects.requireNonNull(tableProperties.getColumnName());
		OracleDocumentReader.Builder builder = OracleDocumentReader.builder(dataSource, owner, tableName, columnName);
		applyPreferences(builder, properties.getPreferences());
		return builder.build();
	}

	private static void applyPreferences(OracleDocumentReader.Builder builder,
			OracleDocumentLoaderPreferencesProperties preferenceProperties) {
		if (!preferenceProperties.isConfigured()) {
			return;
		}

		OracleDocumentPreferences.Builder preferencesBuilder = OracleDocumentPreferences.builder();
		if (preferenceProperties.getPlaintext() != null) {
			preferencesBuilder.plaintext(preferenceProperties.getPlaintext());
		}
		if (StringUtils.hasText(preferenceProperties.getCharset())) {
			preferencesBuilder.charset(preferenceProperties.getCharset());
		}
		if (StringUtils.hasText(preferenceProperties.getFormat())) {
			preferencesBuilder.format(preferenceProperties.getFormat());
		}
		builder.preferences(preferencesBuilder.build());
	}

	private static void validateSourceConfiguration(OracleDocumentLoaderProperties properties) {
		String resource = properties.getResource();
		OracleDocumentLoaderTableProperties table = properties.getTable();
		String owner = table.getOwner();
		String tableName = table.getTableName();
		String columnName = table.getColumnName();

		boolean resourceConfigured = resource != null;
		boolean tableConfigured = owner != null || tableName != null || columnName != null;
		if (resourceConfigured && tableConfigured) {
			throw new IllegalStateException(
					"Configure only one Oracle document-loader source: either resource or table");
		}
		if (resourceConfigured && !StringUtils.hasText(resource)) {
			throw new IllegalStateException("Oracle document-loader resource must not be blank");
		}
		if (tableConfigured && (!StringUtils.hasText(owner) || !StringUtils.hasText(tableName)
				|| !StringUtils.hasText(columnName))) {
			throw new IllegalStateException("Oracle document-loader table requires owner, table-name, and column-name");
		}
	}

}
