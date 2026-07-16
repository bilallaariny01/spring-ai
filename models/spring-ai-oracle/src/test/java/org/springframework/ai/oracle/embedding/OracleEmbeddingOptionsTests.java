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

package org.springframework.ai.oracle.embedding;

import javax.sql.DataSource;

import org.junit.jupiter.api.Test;

import org.springframework.ai.document.MetadataMode;
import org.springframework.ai.embedding.EmbeddingOptions;
import org.springframework.jdbc.datasource.AbstractDataSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Spring AI Contributors
 */
public class OracleEmbeddingOptionsTests {

	/**
	 * Verify default option values.
	 */
	@Test
	void defaultOptions() {
		OracleEmbeddingOptions options = OracleEmbeddingOptions.builder().build();

		assertThat(options.getModel()).isEqualTo("database");
		assertThat(options.getDimensions()).isNull();
		assertThat(options.getPreferences()).containsExactly(OracleEmbeddingOptions.DEFAULT_PREFERENCES.toByteArray());
		assertThat(options.getProxy()).isNull();
		assertThat(options.isBatching()).isTrue();
		assertThat(options.getMetadataMode()).isEqualTo(MetadataMode.EMBED);
	}

	/**
	 * Verify all configurable option values are applied.
	 */
	@Test
	void newOptions() {
		OracleEmbeddingOptions options = OracleEmbeddingOptions.builder()
			.model("cohere.embed-english-light-v3.0")
			.dimensions(1024)
			.preferences(OracleEmbeddingPreferences.builder()
				.provider("ocigenai")
				.credentialName("OCI_CRED")
				.url("https://example-embeddings")
				.model("cohere.embed-english-light-v3.0")
				.transferTimeout(30)
				.maxCount(10)
				.batchSize(64)
				.build())
			.proxy("http://my-proxy:8080")
			.batching(false)
			.metadataMode(MetadataMode.ALL)
			.build();

		assertThat(options.getModel()).isEqualTo("cohere.embed-english-light-v3.0");
		assertThat(options.getDimensions()).isEqualTo(1024);
		assertThat(options.getProxy()).isEqualTo("http://my-proxy:8080");
		assertThat(options.isBatching()).isFalse();
		assertThat(options.getMetadataMode()).isEqualTo(MetadataMode.ALL);
		assertThat(options.getPreferences()).containsExactly(OracleEmbeddingPreferences.builder()
			.provider("ocigenai")
			.credentialName("OCI_CRED")
			.url("https://example-embeddings")
			.model("cohere.embed-english-light-v3.0")
			.transferTimeout(30)
			.maxCount(10)
			.batchSize(64)
			.build()
			.toByteArray());
	}

	/**
	 * Verify merging base embedding options keeps Oracle-specific defaults.
	 */
	@Test
	void mergeWithBaseEmbeddingOptionsKeepsOracleDefaults() {
		DataSource dataSource = new NoOpDataSource();
		OracleEmbeddingModel model = new OracleEmbeddingModel(dataSource,
				OracleEmbeddingOptions.builder()
					.preferences(OracleEmbeddingPreferences.builder().provider("database").model("database").build())
					.build());

		OracleEmbeddingOptions merged = model
			.mergeOptions(EmbeddingOptions.builder().model("base-model").dimensions(384).build());

		assertThat(merged.getModel()).isEqualTo("base-model");
		assertThat(merged.getDimensions()).isEqualTo(384);
		assertThat(merged.getPreferences()).containsExactly(
				OracleEmbeddingPreferences.builder().provider("database").model("database").build().toByteArray());
		assertThat(merged.isBatching()).isTrue();
	}

	/**
	 * Verify Oracle option merge overrides default fields.
	 */
	@Test
	void mergeWithOracleOptionsOverridesFields() {
		DataSource dataSource = new NoOpDataSource();
		OracleEmbeddingModel model = new OracleEmbeddingModel(dataSource,
				OracleEmbeddingOptions.builder()
					.model("database")
					.batching(true)
					.metadataMode(MetadataMode.EMBED)
					.build());

		OracleEmbeddingOptions merged = model.mergeOptions(OracleEmbeddingOptions.builder()
			.model("cohere.embed-english-light-v3.0")
			.proxy("http://proxy:3128")
			.preferences(OracleEmbeddingPreferences.builder()
				.provider("ocigenai")
				.model("cohere.embed-english-light-v3.0")
				.build())
			.batching(false)
			.metadataMode(MetadataMode.NONE)
			.build());

		assertThat(merged.getModel()).isEqualTo("cohere.embed-english-light-v3.0");
		assertThat(merged.getProxy()).isEqualTo("http://proxy:3128");
		assertThat(merged.getPreferences()).containsExactly(OracleEmbeddingPreferences.builder()
			.provider("ocigenai")
			.model("cohere.embed-english-light-v3.0")
			.build()
			.toByteArray());
		assertThat(merged.isBatching()).isFalse();
		assertThat(merged.getMetadataMode()).isEqualTo(MetadataMode.NONE);
	}

	/**
	 * Verify partially configured request options do not override unrelated default
	 * fields.
	 */
	@Test
	void mergeWithPartiallyConfiguredOracleOptionsKeepsUnspecifiedDefaults() {
		DataSource dataSource = new NoOpDataSource();
		OracleEmbeddingPreferences defaultPreferences = OracleEmbeddingPreferences.builder()
			.provider("ocigenai")
			.model("my-default-model")
			.build();
		OracleEmbeddingModel model = new OracleEmbeddingModel(dataSource,
				OracleEmbeddingOptions.builder()
					.model("my-default-model")
					.preferences(defaultPreferences)
					.batching(false)
					.metadataMode(MetadataMode.ALL)
					.build());

		OracleEmbeddingOptions merged = model.mergeOptions(OracleEmbeddingOptions.builder().dimensions(384).build());

		assertThat(merged.getDimensions()).isEqualTo(384);
		assertThat(merged.getModel()).isEqualTo("my-default-model");
		assertThat(merged.getPreferences()).containsExactly(defaultPreferences.toByteArray());
		assertThat(merged.isBatching()).isFalse();
		assertThat(merged.getMetadataMode()).isEqualTo(MetadataMode.ALL);
	}

	/**
	 * Verify explicitly configured framework defaults override different model defaults.
	 */
	@Test
	void mergeWithExplicitFrameworkDefaultsOverridesModelDefaults() {
		DataSource dataSource = new NoOpDataSource();
		OracleEmbeddingPreferences customPreferences = OracleEmbeddingPreferences.builder()
			.provider("ocigenai")
			.model("custom-model")
			.build();
		OracleEmbeddingModel model = new OracleEmbeddingModel(dataSource,
				OracleEmbeddingOptions.builder()
					.model("custom-model")
					.preferences(customPreferences)
					.batching(false)
					.metadataMode(MetadataMode.ALL)
					.build());

		OracleEmbeddingOptions merged = model.mergeOptions(OracleEmbeddingOptions.builder()
			.model("database")
			.preferences(OracleEmbeddingOptions.DEFAULT_PREFERENCES)
			.batching(true)
			.metadataMode(MetadataMode.EMBED)
			.build());

		assertThat(merged.getModel()).isEqualTo("database");
		assertThat(merged.getPreferences()).containsExactly(OracleEmbeddingOptions.DEFAULT_PREFERENCES.toByteArray());
		assertThat(merged.isBatching()).isTrue();
		assertThat(merged.getMetadataMode()).isEqualTo(MetadataMode.EMBED);
	}

	/**
	 * Verify merging null request options returns defaults.
	 */
	@Test
	void mergeWithNullReturnsDefaultOptions() {
		DataSource dataSource = new NoOpDataSource();
		OracleEmbeddingOptions defaults = OracleEmbeddingOptions.builder()
			.model("database")
			.preferences(OracleEmbeddingPreferences.builder().provider("database").model("database").build())
			.metadataMode(MetadataMode.EMBED)
			.build();
		OracleEmbeddingModel model = new OracleEmbeddingModel(dataSource, defaults);

		OracleEmbeddingOptions merged = model.mergeOptions(null);

		assertThat(merged).isSameAs(defaults);
	}

	private static final class NoOpDataSource extends AbstractDataSource {

		/**
		 * Unsupported in these tests.
		 * @return never returns
		 */
		@Override
		public java.sql.Connection getConnection() {
			throw new UnsupportedOperationException("Not needed in this test");
		}

		/**
		 * Unsupported in these tests.
		 * @param username ignored
		 * @param password ignored
		 * @return never returns
		 */
		@Override
		public java.sql.Connection getConnection(String username, String password) {
			throw new UnsupportedOperationException("Not needed in this test");
		}

	}

}
