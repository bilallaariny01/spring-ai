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

import javax.sql.DataSource;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link OracleDocumentSplitterProperties}.
 *
 * @author Spring AI Contributors
 */
public class OracleDocumentSplitterPropertiesTests {

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner().withBean(DataSource.class,
			() -> Mockito.mock(DataSource.class));

	@Test
	public void splitterProperties() {
		this.contextRunner.withPropertyValues(
		// @formatter:off
				"spring.ai.oracle.document-splitter.preferences.by=vocabulary",
				"spring.ai.oracle.document-splitter.preferences.max=200",
				"spring.ai.oracle.document-splitter.preferences.overlap=20",
				"spring.ai.oracle.document-splitter.preferences.split=custom",
				"spring.ai.oracle.document-splitter.preferences.custom-list[0]=,",
				"spring.ai.oracle.document-splitter.preferences.custom-list[1]=;",
				"spring.ai.oracle.document-splitter.preferences.vocabulary=my_vocab",
				"spring.ai.oracle.document-splitter.preferences.language=american",
				"spring.ai.oracle.document-splitter.preferences.normalize=options",
				"spring.ai.oracle.document-splitter.preferences.norm-options[0]=punctuation",
				"spring.ai.oracle.document-splitter.preferences.norm-options[1]=whitespace",
				"spring.ai.oracle.document-splitter.preferences.extended=true")
				// @formatter:on
			.withConfiguration(AutoConfigurations.of(OracleDocumentSplitterAutoConfiguration.class))
			.run(context -> {
				OracleDocumentSplitterProperties properties = context.getBean(OracleDocumentSplitterProperties.class);

				assertThat(properties.getPreferences().getBy()).isEqualTo("vocabulary");
				assertThat(properties.getPreferences().getMax()).isEqualTo(200);
				assertThat(properties.getPreferences().getOverlap()).isEqualTo(20);
				assertThat(properties.getPreferences().getSplit()).isEqualTo("custom");
				assertThat(properties.getPreferences().getCustomList()).containsExactly(",", ";");
				assertThat(properties.getPreferences().getVocabulary()).isEqualTo("my_vocab");
				assertThat(properties.getPreferences().getLanguage()).isEqualTo("american");
				assertThat(properties.getPreferences().getNormalize()).isEqualTo("options");
				assertThat(properties.getPreferences().getNormOptions()).containsExactly("punctuation", "whitespace");
				assertThat(properties.getPreferences().getExtended()).isTrue();
			});
	}

}
