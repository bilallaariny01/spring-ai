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

import io.micrometer.observation.ObservationRegistry;

import org.springframework.ai.embedding.observation.EmbeddingModelObservationConvention;
import org.springframework.ai.model.SpringAIModelProperties;
import org.springframework.ai.model.SpringAIModels;
import org.springframework.ai.oracle.embedding.OracleEmbeddingModel;
import org.springframework.ai.oracle.embedding.OracleEmbeddingPreferences;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.core.retry.RetryTemplate;
import org.springframework.util.StringUtils;

/**
 * {@link AutoConfiguration Auto-configuration} for Oracle Embedding Model.
 *
 * @author Spring AI Contributors
 */
@AutoConfiguration
@EnableConfigurationProperties(OracleEmbeddingProperties.class)
@ConditionalOnProperty(name = SpringAIModelProperties.EMBEDDING_MODEL, havingValue = SpringAIModels.ORACLE,
		matchIfMissing = true)
@ConditionalOnClass({ OracleEmbeddingModel.class, DataSource.class })
public class OracleEmbeddingAutoConfiguration {

	@Bean
	@ConditionalOnMissingBean
	public OracleEmbeddingModel oracleEmbeddingModel(DataSource dataSource, OracleEmbeddingProperties properties,
			ObjectProvider<ObservationRegistry> observationRegistry,
			ObjectProvider<EmbeddingModelObservationConvention> observationConvention,
			ObjectProvider<RetryTemplate> retryTemplate) {

		applyPreferences(properties);

		OracleEmbeddingModel embeddingModel = new OracleEmbeddingModel(dataSource, properties.getOptions(),
				observationRegistry.getIfUnique(() -> ObservationRegistry.NOOP), retryTemplate.getIfUnique(),
				properties.isInitializeOnStartup(), properties.getOnnxDirectoryAlias(), properties.getOnnxFile(),
				properties.getOnnxModelName(), properties.getOnnxCredential(), properties.getOnnxUri());

		observationConvention.ifAvailable(embeddingModel::setObservationConvention);

		return embeddingModel;
	}

	private static void applyPreferences(OracleEmbeddingProperties properties) {
		OracleEmbeddingPreferencesProperties preferenceProperties = properties.getPreferences();
		String onnxModelName = properties.getOnnxModelName();
		boolean useOnnxModel = properties.isInitializeOnStartup() && StringUtils.hasText(onnxModelName);
		if (!preferenceProperties.isConfigured() && !useOnnxModel) {
			return;
		}

		String model = StringUtils.hasText(preferenceProperties.getModel())
				? Objects.requireNonNull(preferenceProperties.getModel())
				: (useOnnxModel ? Objects.requireNonNull(onnxModelName) : "database");
		OracleEmbeddingPreferences.Builder builder = OracleEmbeddingPreferences.builder()
			.provider(StringUtils.hasText(preferenceProperties.getProvider()) ? preferenceProperties.getProvider()
					: "database")
			.model(model);

		if (StringUtils.hasText(preferenceProperties.getCredentialName())) {
			builder.credentialName(preferenceProperties.getCredentialName());
		}
		if (StringUtils.hasText(preferenceProperties.getUrl())) {
			builder.url(preferenceProperties.getUrl());
		}
		if (preferenceProperties.getTransferTimeout() != null) {
			builder.transferTimeout(preferenceProperties.getTransferTimeout());
		}
		if (preferenceProperties.getMaxCount() != null) {
			builder.maxCount(preferenceProperties.getMaxCount());
		}
		if (preferenceProperties.getBatchSize() != null) {
			builder.batchSize(preferenceProperties.getBatchSize());
		}

		properties.getOptions().setPreferences(builder.build());
	}

}
