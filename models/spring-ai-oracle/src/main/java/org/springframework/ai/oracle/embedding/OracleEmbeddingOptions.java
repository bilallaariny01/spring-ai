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

import org.jspecify.annotations.Nullable;

import org.springframework.ai.document.MetadataMode;
import org.springframework.ai.embedding.EmbeddingOptions;
import org.springframework.util.Assert;

/**
 * Oracle embedding model options.
 *
 * @author Spring AI Contributors
 */
public class OracleEmbeddingOptions implements EmbeddingOptions {

	public static final OracleEmbeddingPreferences DEFAULT_PREFERENCES = OracleEmbeddingPreferences.builder()
		.provider("database")
		.model("database")
		.build();

	private @Nullable String model = "database";

	private boolean modelSet;

	private @Nullable Integer dimensions;

	private byte[] preferences = defaultPreferencesOson();

	private boolean preferencesSet;

	private @Nullable String proxy;

	private boolean batching = true;

	private boolean batchingSet;

	private MetadataMode metadataMode = MetadataMode.EMBED;

	private boolean metadataModeSet;

	/**
	 * Create a builder for embedding options.
	 * @return options builder
	 */
	public static Builder builder() {
		return new Builder();
	}

	/**
	 * Return configured model identifier.
	 * @return model name
	 */
	@Override
	public @Nullable String getModel() {
		return this.model;
	}

	/**
	 * Set model identifier.
	 * @param model model name
	 */
	public void setModel(@Nullable String model) {
		this.model = model;
		this.modelSet = true;

	}

	boolean isModelSet() {
		return this.modelSet;
	}

	/**
	 * Return configured embedding dimensions.
	 * @return dimensions value
	 */
	@Override
	public @Nullable Integer getDimensions() {
		return this.dimensions;
	}

	/**
	 * Set requested embedding dimensions.
	 * @param dimensions embedding dimensions
	 */
	public void setDimensions(@Nullable Integer dimensions) {
		this.dimensions = dimensions;

	}

	/**
	 * Return Oracle preferences as OSON bytes.
	 * @return cloned preferences bytes
	 */
	public byte[] getPreferences() {
		return this.preferences.clone();
	}

	/**
	 * Set Oracle preferences as OSON bytes.
	 * @param preferences preferences bytes
	 */
	public void setPreferences(byte[] preferences) {
		Assert.notNull(preferences, "preferences must not be null");
		this.preferences = preferences.clone();
		this.preferencesSet = true;

	}

	/**
	 * Set structured Oracle embedding preferences.
	 * @param preferences structured preferences
	 */
	public void setPreferences(OracleEmbeddingPreferences preferences) {
		Assert.notNull(preferences, "preferences must not be null");
		this.preferences = preferences.toByteArray();
		this.preferencesSet = true;

	}

	boolean isPreferencesSet() {
		return this.preferencesSet;
	}

	/**
	 * Return optional outbound proxy setting.
	 * @return proxy value
	 */
	public @Nullable String getProxy() {
		return this.proxy;
	}

	/**
	 * Set optional outbound proxy setting.
	 * @param proxy proxy value
	 */
	public void setProxy(@Nullable String proxy) {
		this.proxy = proxy;

	}

	/**
	 * Return whether request batching is enabled.
	 * @return batching flag
	 */
	public boolean isBatching() {
		return this.batching;
	}

	/**
	 * Enable or disable batching mode.
	 * @param batching batching flag
	 */
	public void setBatching(boolean batching) {
		this.batching = batching;
		this.batchingSet = true;

	}

	boolean isBatchingSet() {
		return this.batchingSet;
	}

	/**
	 * Return metadata mode used to build embedding content.
	 * @return metadata mode
	 */
	public MetadataMode getMetadataMode() {
		return this.metadataMode;
	}

	/**
	 * Set metadata mode used to build embedding content.
	 * @param metadataMode metadata mode
	 */
	public void setMetadataMode(MetadataMode metadataMode) {
		this.metadataMode = metadataMode;
		this.metadataModeSet = true;

	}

	boolean isMetadataModeSet() {
		return this.metadataModeSet;
	}

	/**
	 * Build default OSON preferences payload.
	 * @return default preferences bytes
	 */
	private static byte[] defaultPreferencesOson() {
		return DEFAULT_PREFERENCES.toByteArray();
	}

	public static final class Builder {

		private final OracleEmbeddingOptions options = new OracleEmbeddingOptions();

		/**
		 * Set model identifier.
		 * @param model model name
		 * @return this builder
		 */
		public Builder model(@Nullable String model) {
			this.options.setModel(model);
			return this;
		}

		/**
		 * Set embedding dimensions.
		 * @param dimensions dimensions value
		 * @return this builder
		 */
		public Builder dimensions(@Nullable Integer dimensions) {
			this.options.setDimensions(dimensions);
			return this;
		}

		/**
		 * Set Oracle preferences bytes.
		 * @param preferences preferences bytes
		 * @return this builder
		 */
		public Builder preferences(byte[] preferences) {
			this.options.setPreferences(preferences);
			return this;
		}

		/**
		 * Set structured Oracle preferences.
		 * @param preferences structured preferences
		 * @return this builder
		 */
		public Builder preferences(OracleEmbeddingPreferences preferences) {
			this.options.setPreferences(preferences);
			return this;
		}

		/**
		 * Set optional proxy configuration.
		 * @param proxy proxy value
		 * @return this builder
		 */
		public Builder proxy(@Nullable String proxy) {
			this.options.setProxy(proxy);
			return this;
		}

		/**
		 * Set batching mode.
		 * @param batching batching flag
		 * @return this builder
		 */
		public Builder batching(boolean batching) {
			this.options.setBatching(batching);
			return this;
		}

		/**
		 * Set metadata mode.
		 * @param metadataMode metadata mode
		 * @return this builder
		 */
		public Builder metadataMode(MetadataMode metadataMode) {
			this.options.setMetadataMode(metadataMode);
			return this;
		}

		/**
		 * Build embedding options.
		 * @return configured options
		 */
		public OracleEmbeddingOptions build() {
			return this.options;
		}

	}

}
