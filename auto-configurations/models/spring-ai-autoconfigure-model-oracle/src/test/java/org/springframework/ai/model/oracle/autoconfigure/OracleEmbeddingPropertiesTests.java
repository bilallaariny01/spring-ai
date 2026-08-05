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

import org.springframework.ai.document.MetadataMode;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link OracleEmbeddingProperties}.
 *
 * @author Spring AI Contributors
 */
public class OracleEmbeddingPropertiesTests {

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner().withBean(DataSource.class,
			() -> Mockito.mock(DataSource.class));

	@Test
	public void embeddingProperties() {
		this.contextRunner.withPropertyValues(
		// @formatter:off
				"spring.ai.oracle.embedding.options.model=e5-small",
				"spring.ai.oracle.embedding.options.dimensions=384",
				"spring.ai.oracle.embedding.options.proxy=http://proxy.internal:8080",
				"spring.ai.oracle.embedding.options.batching=false",
				"spring.ai.oracle.embedding.options.metadata-mode=all",
				"spring.ai.oracle.embedding.preferences.provider=ocigenai",
				"spring.ai.oracle.embedding.preferences.model=cohere.embed-english-light-v3.0",
				"spring.ai.oracle.embedding.preferences.credential-name=OCI_CRED",
				"spring.ai.oracle.embedding.preferences.url=https://inference.generativeai.us-chicago-1.oci.oraclecloud.com",
				"spring.ai.oracle.embedding.preferences.transfer-timeout=30",
				"spring.ai.oracle.embedding.preferences.max-count=100",
				"spring.ai.oracle.embedding.preferences.batch-size=8",
				"spring.ai.oracle.embedding.onnx-credential=OCI_OSS_CRED",
				"spring.ai.oracle.embedding.onnx-uri=https://objectstorage.us-ashburn-1.oraclecloud.com/n/ns/b/bucket/o/model.onnx")
				// @formatter:on
			.withConfiguration(AutoConfigurations.of(OracleEmbeddingAutoConfiguration.class))
			.run(context -> {
				var properties = context.getBean(OracleEmbeddingProperties.class);

				assertThat(properties.isInitializeOnStartup()).isFalse();
				assertThat(properties.getOnnxDirectoryAlias()).isNull();
				assertThat(properties.getOnnxFile()).isNull();
				assertThat(properties.getOnnxModelName()).isNull();
				assertThat(properties.getOnnxCredential()).isEqualTo("OCI_OSS_CRED");
				assertThat(properties.getOnnxUri())
					.isEqualTo("https://objectstorage.us-ashburn-1.oraclecloud.com/n/ns/b/bucket/o/model.onnx");
				assertThat(properties.getOptions().getModel()).isEqualTo("e5-small");
				assertThat(properties.getOptions().getDimensions()).isEqualTo(384);
				assertThat(properties.getOptions().getProxy()).isEqualTo("http://proxy.internal:8080");
				assertThat(properties.getOptions().isBatching()).isFalse();
				assertThat(properties.getOptions().getMetadataMode()).isEqualTo(MetadataMode.ALL);
				assertThat(properties.getPreferences().getProvider()).isEqualTo("ocigenai");
				assertThat(properties.getPreferences().getModel()).isEqualTo("cohere.embed-english-light-v3.0");
				assertThat(properties.getPreferences().getCredentialName()).isEqualTo("OCI_CRED");
				assertThat(properties.getPreferences().getUrl())
					.isEqualTo("https://inference.generativeai.us-chicago-1.oci.oraclecloud.com");
				assertThat(properties.getPreferences().getTransferTimeout()).isEqualTo(30);
				assertThat(properties.getPreferences().getMaxCount()).isEqualTo(100);
				assertThat(properties.getPreferences().getBatchSize()).isEqualTo(8);
			});
	}

}
