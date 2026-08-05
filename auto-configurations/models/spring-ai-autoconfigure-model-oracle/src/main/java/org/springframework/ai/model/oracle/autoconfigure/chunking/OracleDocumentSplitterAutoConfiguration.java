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

package org.springframework.ai.model.oracle.autoconfigure.chunking;

import javax.sql.DataSource;

import org.springframework.ai.model.SpringAIModelProperties;
import org.springframework.ai.model.SpringAIModels;
import org.springframework.ai.oracle.chunking.OracleChunkingPreferences;
import org.springframework.ai.oracle.chunking.OracleDocumentSplitter;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.util.StringUtils;

/**
 * {@link AutoConfiguration Auto-configuration} for Oracle document splitter.
 *
 * @author Spring AI Contributors
 */
@AutoConfiguration
@EnableConfigurationProperties(OracleDocumentSplitterProperties.class)
@ConditionalOnProperty(name = SpringAIModelProperties.EMBEDDING_MODEL, havingValue = SpringAIModels.ORACLE,
		matchIfMissing = true)
@ConditionalOnClass({ OracleDocumentSplitter.class, DataSource.class })
public class OracleDocumentSplitterAutoConfiguration {

	@Bean
	@ConditionalOnMissingBean
	public OracleDocumentSplitter oracleDocumentSplitter(DataSource dataSource,
			OracleDocumentSplitterProperties properties) {
		OracleDocumentSplitter.Builder builder = OracleDocumentSplitter.builder(dataSource);
		applyPreferences(builder, properties.getPreferences());
		return builder.build();
	}

	private static void applyPreferences(OracleDocumentSplitter.Builder builder,
			OracleDocumentSplitterPreferencesProperties preferenceProperties) {
		if (!preferenceProperties.isConfigured()) {
			return;
		}

		OracleChunkingPreferences.Builder preferencesBuilder = OracleChunkingPreferences.builder();
		if (StringUtils.hasText(preferenceProperties.getBy())) {
			preferencesBuilder.by(preferenceProperties.getBy());
		}
		if (preferenceProperties.getMax() != null) {
			preferencesBuilder.max(preferenceProperties.getMax());
		}
		if (preferenceProperties.getOverlap() != null) {
			preferencesBuilder.overlap(preferenceProperties.getOverlap());
		}
		if (StringUtils.hasText(preferenceProperties.getSplit())) {
			preferencesBuilder.split(preferenceProperties.getSplit());
		}
		if (preferenceProperties.getCustomList() != null) {
			preferencesBuilder.customList(preferenceProperties.getCustomList());
		}
		if (StringUtils.hasText(preferenceProperties.getVocabulary())) {
			preferencesBuilder.vocabulary(preferenceProperties.getVocabulary());
		}
		if (StringUtils.hasText(preferenceProperties.getLanguage())) {
			preferencesBuilder.language(preferenceProperties.getLanguage());
		}
		if (StringUtils.hasText(preferenceProperties.getNormalize())) {
			preferencesBuilder.normalize(preferenceProperties.getNormalize());
		}
		if (preferenceProperties.getNormOptions() != null) {
			preferencesBuilder.normOptions(preferenceProperties.getNormOptions());
		}
		if (preferenceProperties.getExtended() != null) {
			preferencesBuilder.extended(preferenceProperties.getExtended());
		}

		builder.preferences(preferencesBuilder.build());
	}

}
