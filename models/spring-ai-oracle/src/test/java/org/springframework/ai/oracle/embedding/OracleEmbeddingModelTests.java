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

import java.sql.SQLTransientException;

import javax.sql.DataSource;

import org.junit.jupiter.api.Test;

import org.springframework.ai.document.Document;
import org.springframework.ai.document.MetadataMode;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.jdbc.datasource.AbstractDataSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * @author Spring AI Contributors
 */
public class OracleEmbeddingModelTests {

	/**
	 * Verify embedding content honors configured metadata mode.
	 */
	@Test
	void getEmbeddingContentUsesConfiguredMetadataMode() {
		DataSource dataSource = new NoOpDataSource();
		OracleEmbeddingModel model = new OracleEmbeddingModel(dataSource,
				OracleEmbeddingOptions.builder().metadataMode(MetadataMode.NONE).build());

		Document document = new Document("hello", java.util.Map.of("source", "test"));
		String content = model.getEmbeddingContent(document);

		assertThat(content).contains("hello");
		assertThat(content).doesNotContain("source");
	}

	/**
	 * Verify empty call input fails fast before touching JDBC.
	 */
	@Test
	void callWithEmptyInputThrowsAndDoesNotTouchDataSource() {
		DataSource dataSource = new NoOpDataSource();
		OracleEmbeddingModel model = new OracleEmbeddingModel(dataSource);

		assertThatThrownBy(
				() -> model.call(new EmbeddingRequest(java.util.List.of(), OracleEmbeddingOptions.builder().build())))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessage("No embedding input is provided - instructions list is empty");
	}

	/**
	 * Verify blank call input fails fast before touching JDBC.
	 */
	@Test
	void callWithBlankInputThrowsAndDoesNotTouchDataSource() {
		DataSource dataSource = new NoOpDataSource();
		OracleEmbeddingModel model = new OracleEmbeddingModel(dataSource);

		assertThatThrownBy(() -> model
			.call(new EmbeddingRequest(java.util.List.of("valid", " "), OracleEmbeddingOptions.builder().build())))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessage("No embedding input is provided - text must not be null or empty");
	}

	/**
	 * Verify constructor default setup allows request validation path execution.
	 */
	@Test
	void constructorUsesDefaultsAndBuildsModel() {
		DataSource dataSource = new NoOpDataSource();
		OracleEmbeddingModel model = new OracleEmbeddingModel(dataSource);

		assertThatThrownBy(
				() -> model.call(new EmbeddingRequest(java.util.List.of(), OracleEmbeddingOptions.builder().build())))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessage("No embedding input is provided - instructions list is empty");
	}

	/**
	 * Verify startup initialization uses the loaded ONNX model when embedding preferences
	 * were not explicitly configured.
	 */
	@Test
	void constructorUsesOnnxModelForDefaultPreferences() {
		DataSource dataSource = new NoOpDataSource();
		OracleEmbeddingModel model = new OracleEmbeddingModel(dataSource, OracleEmbeddingOptions.builder().build(),
				null, null, true, "MODEL_DIR", "model.onnx", "MY_MODEL");

		assertThat(model.getOptions().getPreferences()).containsExactly(
				OracleEmbeddingPreferences.builder().provider("database").model("MY_MODEL").build().toByteArray());
	}

	/**
	 * Verify explicit embedding preferences take priority over the startup ONNX model
	 * name.
	 */
	@Test
	void constructorPreservesExplicitPreferencesWhenOnnxModelIsConfigured() {
		DataSource dataSource = new NoOpDataSource();
		OracleEmbeddingPreferences preferences = OracleEmbeddingPreferences.builder()
			.provider("database")
			.model("EXPLICIT_MODEL")
			.build();
		OracleEmbeddingModel model = new OracleEmbeddingModel(dataSource,
				OracleEmbeddingOptions.builder().preferences(preferences).build(), null, null, true, "MODEL_DIR",
				"model.onnx", "MY_MODEL");

		assertThat(model.getOptions().getPreferences()).containsExactly(preferences.toByteArray());
	}

	/**
	 * Verify initialization no-ops when startup loading is disabled.
	 */
	@Test
	void afterPropertiesSetDoesNothingWhenInitializationDisabled() {
		DataSource dataSource = new NoOpDataSource();
		OracleEmbeddingModel model = new OracleEmbeddingModel(dataSource);

		model.afterPropertiesSet();
	}

	/**
	 * Verify local initialization requires directory alias.
	 */
	@Test
	void afterPropertiesSetRequiresDirectoryAliasWhenInitializationEnabled() {
		DataSource dataSource = new NoOpDataSource();
		OracleEmbeddingModel model = new OracleEmbeddingModel(dataSource, null, null, null, true, null, "model.onnx",
				null);

		assertThatThrownBy(model::afterPropertiesSet).isInstanceOf(IllegalStateException.class)
			.hasMessage("onnxDirectoryAlias must not be null or empty when initializeOnStartup is enabled");
	}

	/**
	 * Verify local initialization requires ONNX file name.
	 */
	@Test
	void afterPropertiesSetRequiresOnnxFileWhenInitializationEnabled() {
		DataSource dataSource = new NoOpDataSource();
		OracleEmbeddingModel model = new OracleEmbeddingModel(dataSource, null, null, null, true, "MODEL_DIR", null,
				null);

		assertThatThrownBy(model::afterPropertiesSet).isInstanceOf(IllegalStateException.class)
			.hasMessage("onnxFile must not be null or empty when initializeOnStartup is enabled");
	}

	/**
	 * Verify SQL errors during initialization are wrapped.
	 */
	@Test
	void afterPropertiesSetWrapsSqlErrors() {
		DataSource dataSource = new FailingDataSource();
		OracleEmbeddingModel model = new OracleEmbeddingModel(dataSource, null, null, null, true, "MODEL_DIR",
				"model.onnx", null);

		assertThatThrownBy(model::afterPropertiesSet).isInstanceOf(IllegalStateException.class)
			.hasMessage("Failed to load ONNX model during Oracle embedding model initialization")
			.hasRootCauseInstanceOf(SQLTransientException.class);
	}

	/**
	 * Verify cloud initialization allows URI without credential.
	 */
	@Test
	void afterPropertiesSetAllowsUriOnlyCloudInitialization() {
		DataSource dataSource = new FailingDataSource();
		OracleEmbeddingModel model = new OracleEmbeddingModel(dataSource, null, null, null, true, null, null, null,
				null, "https://objectstorage.example.com/n/model.onnx");

		assertThatThrownBy(model::afterPropertiesSet).isInstanceOf(IllegalStateException.class)
			.hasMessage("Failed to load ONNX model during Oracle embedding model initialization")
			.hasRootCauseInstanceOf(SQLTransientException.class);
	}

	/**
	 * Verify cloud initialization requires URI.
	 */
	@Test
	void afterPropertiesSetRequiresUriWhenCloudUriMissing() {
		DataSource dataSource = new NoOpDataSource();
		OracleEmbeddingModel model = new OracleEmbeddingModel(dataSource, null, null, null, true, null, null, null,
				"CLOUD_CREDENTIAL", null);

		assertThatThrownBy(model::afterPropertiesSet).isInstanceOf(IllegalStateException.class)
			.hasMessage("onnxUri must not be null or empty when cloud initialization is enabled");
	}

	/**
	 * Verify local and cloud initialization settings cannot be mixed.
	 */
	@Test
	void afterPropertiesSetRejectsMixedLocalAndCloudInitializationSettings() {
		DataSource dataSource = new NoOpDataSource();
		OracleEmbeddingModel model = new OracleEmbeddingModel(dataSource, null, null, null, true, "MODEL_DIR",
				"model.onnx", null, "CLOUD_CREDENTIAL", "https://objectstorage.example.com/n/model.onnx");

		assertThatThrownBy(model::afterPropertiesSet).isInstanceOf(IllegalStateException.class)
			.hasMessage(
					"Only one ONNX load strategy can be configured: local (onnxDirectoryAlias/onnxFile) or cloud (onnxCredential/onnxUri)");
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

	private static final class FailingDataSource extends AbstractDataSource {

		/**
		 * Always fails with transient SQL exception.
		 * @return never returns
		 * @throws SQLTransientException always thrown
		 */
		@Override
		public java.sql.Connection getConnection() throws SQLTransientException {
			throw new SQLTransientException("temporary db issue");
		}

		/**
		 * Always fails with transient SQL exception.
		 * @param username ignored
		 * @param password ignored
		 * @return never returns
		 * @throws SQLTransientException always thrown
		 */
		@Override
		public java.sql.Connection getConnection(String username, String password) throws SQLTransientException {
			throw new SQLTransientException("temporary db issue");
		}

	}

}
