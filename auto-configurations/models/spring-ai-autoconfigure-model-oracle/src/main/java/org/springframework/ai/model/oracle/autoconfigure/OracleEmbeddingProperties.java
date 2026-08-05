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

import org.jspecify.annotations.Nullable;

import org.springframework.ai.oracle.embedding.OracleEmbeddingOptions;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;

/**
 * Configuration properties for Oracle embedding.
 *
 * @author Spring AI Contributors
 */
@ConfigurationProperties(OracleEmbeddingProperties.CONFIG_PREFIX)
public class OracleEmbeddingProperties {

	public static final String CONFIG_PREFIX = "spring.ai.oracle.embedding";

	private boolean initializeOnStartup;

	private @Nullable String onnxDirectoryAlias;

	private @Nullable String onnxFile;

	private @Nullable String onnxModelName;

	private @Nullable String onnxCredential;

	private @Nullable String onnxUri;

	@NestedConfigurationProperty
	private final OracleEmbeddingOptions options = OracleEmbeddingOptions.builder().build();

	@NestedConfigurationProperty
	private final OracleEmbeddingPreferencesProperties preferences = new OracleEmbeddingPreferencesProperties();

	public OracleEmbeddingOptions getOptions() {
		return this.options;
	}

	public OracleEmbeddingPreferencesProperties getPreferences() {
		return this.preferences;
	}

	public boolean isInitializeOnStartup() {
		return this.initializeOnStartup;
	}

	public void setInitializeOnStartup(boolean initializeOnStartup) {
		this.initializeOnStartup = initializeOnStartup;
	}

	public @Nullable String getOnnxDirectoryAlias() {
		return this.onnxDirectoryAlias;
	}

	public void setOnnxDirectoryAlias(@Nullable String onnxDirectoryAlias) {
		this.onnxDirectoryAlias = onnxDirectoryAlias;
	}

	public @Nullable String getOnnxFile() {
		return this.onnxFile;
	}

	public void setOnnxFile(@Nullable String onnxFile) {
		this.onnxFile = onnxFile;
	}

	public @Nullable String getOnnxModelName() {
		return this.onnxModelName;
	}

	public void setOnnxModelName(@Nullable String onnxModelName) {
		this.onnxModelName = onnxModelName;
	}

	public @Nullable String getOnnxCredential() {
		return this.onnxCredential;
	}

	public void setOnnxCredential(@Nullable String onnxCredential) {
		this.onnxCredential = onnxCredential;
	}

	public @Nullable String getOnnxUri() {
		return this.onnxUri;
	}

	public void setOnnxUri(@Nullable String onnxUri) {
		this.onnxUri = onnxUri;
	}

}
