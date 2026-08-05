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

package org.springframework.ai.model.oracle.autoconfigure.loader;

import javax.sql.DataSource;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link OracleDocumentLoaderProperties}.
 *
 * @author Spring AI Contributors
 */
public class OracleDocumentLoaderPropertiesTests {

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner().withBean(DataSource.class,
			() -> Mockito.mock(DataSource.class));

	@Test
	public void loaderProperties() {
		this.contextRunner.withPropertyValues(
		// @formatter:off
				"spring.ai.oracle.document-loader.resource=classpath:/docs",
				"spring.ai.oracle.document-loader.preferences.plaintext=true",
				"spring.ai.oracle.document-loader.preferences.charset=UTF-8",
				"spring.ai.oracle.document-loader.preferences.format=ignore")
				// @formatter:on
			.withConfiguration(AutoConfigurations.of(OracleDocumentLoaderAutoConfiguration.class))
			.run(context -> {
				OracleDocumentLoaderProperties properties = context.getBean(OracleDocumentLoaderProperties.class);

				assertThat(properties.getResource()).isEqualTo("classpath:/docs");
				assertThat(properties.getPreferences().getPlaintext()).isTrue();
				assertThat(properties.getPreferences().getCharset()).isEqualTo("UTF-8");
				assertThat(properties.getPreferences().getFormat()).isEqualTo("ignore");
			});
	}

	@Test
	public void tableProperties() {
		this.contextRunner
			.withPropertyValues("spring.ai.oracle.document-loader.table.owner=APP",
					"spring.ai.oracle.document-loader.table.table-name=DOCS",
					"spring.ai.oracle.document-loader.table.column-name=TEXT")
			.withConfiguration(AutoConfigurations.of(OracleDocumentLoaderAutoConfiguration.class))
			.run(context -> {
				OracleDocumentLoaderProperties properties = context.getBean(OracleDocumentLoaderProperties.class);

				assertThat(properties.getTable().getOwner()).isEqualTo("APP");
				assertThat(properties.getTable().getTableName()).isEqualTo("DOCS");
				assertThat(properties.getTable().getColumnName()).isEqualTo("TEXT");
			});
	}

}
