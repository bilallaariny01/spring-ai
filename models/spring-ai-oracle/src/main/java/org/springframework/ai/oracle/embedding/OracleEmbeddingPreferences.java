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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.json.JsonMapper;
import oracle.jdbc.provider.oson.OsonFactory;
import org.jspecify.annotations.Nullable;

import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

/**
 * Structured Oracle embedding preferences for DBMS_VECTOR_CHAIN embedding calls.
 *
 * @author Spring AI Contributors
 */
public final class OracleEmbeddingPreferences {

	private static final OsonFactory OSON_FACTORY = new OsonFactory();

	private static final JsonMapper LC4J_MAPPER = new JsonMapper();

	/** Embedding service provider Oracle invokes. */
	@JsonProperty("provider")
	private final String provider;

	/** Provider-specific embedding model to use. */
	@JsonProperty("model")
	private final String model;

	/** Name of the Oracle credential used to authenticate with the provider. */
	@JsonProperty("credential_name")
	private final @Nullable String credentialName;

	/** Provider endpoint URL, when the selected provider requires one. */
	@JsonProperty("url")
	private final @Nullable String url;

	/** Maximum time Oracle waits for the provider transfer to complete. */
	@JsonProperty("transfer_timeout")
	private final @Nullable Integer transferTimeout;

	/** Maximum number of input texts sent to the provider in one request. */
	@JsonProperty("max_count")
	private final @Nullable Integer maxCount;

	/** Number of input texts Oracle groups into each embedding batch. */
	@JsonProperty("batch_size")
	private final @Nullable Integer batchSize;

	/**
	 * Create immutable embedding preferences.
	 * @param provider embedding provider
	 * @param model embedding model
	 * @param credentialName Oracle credential name
	 * @param url endpoint URL
	 * @param transferTimeout transfer timeout
	 * @param maxCount maximum input count
	 * @param batchSize batch size
	 */
	private OracleEmbeddingPreferences(String provider, String model, @Nullable String credentialName,
			@Nullable String url, @Nullable Integer transferTimeout, @Nullable Integer maxCount,
			@Nullable Integer batchSize) {
		this.provider = provider;
		this.model = model;
		this.credentialName = credentialName;
		this.url = url;
		this.transferTimeout = transferTimeout;
		this.maxCount = maxCount;
		this.batchSize = batchSize;
	}

	/**
	 * Create a builder for Oracle embedding preferences.
	 * @return preferences builder
	 */
	public static Builder builder() {
		return new Builder();
	}

	/**
	 * Return embedding provider.
	 * @return provider name
	 */
	public String getProvider() {
		return this.provider;
	}

	/**
	 * Return embedding model.
	 * @return model name
	 */
	public String getModel() {
		return this.model;
	}

	/**
	 * Return optional Oracle credential name.
	 * @return credential name
	 */
	public @Nullable String getCredentialName() {
		return this.credentialName;
	}

	/**
	 * Return optional endpoint URL.
	 * @return endpoint URL
	 */
	public @Nullable String getUrl() {
		return this.url;
	}

	/**
	 * Return optional transfer timeout.
	 * @return transfer timeout
	 */
	public @Nullable Integer getTransferTimeout() {
		return this.transferTimeout;
	}

	/**
	 * Return optional maximum count.
	 * @return maximum count
	 */
	public @Nullable Integer getMaxCount() {
		return this.maxCount;
	}

	/**
	 * Return optional batch size.
	 * @return batch size
	 */
	public @Nullable Integer getBatchSize() {
		return this.batchSize;
	}

	/**
	 * Serialize preferences to Oracle OSON bytes.
	 * @return serialized preferences
	 */
	public byte[] toByteArray() {
		try {
			ByteArrayOutputStream out = new ByteArrayOutputStream();
			try (JsonGenerator gen = OSON_FACTORY.createGenerator(out)) {
				LC4J_MAPPER.writeValue(gen, this);
				gen.flush();
			}
			return out.toByteArray();
		}
		catch (IOException ex) {
			throw new IllegalStateException("Failed to serialize preferences to Oracle OSON", ex);
		}
	}

	public static final class Builder {

		/** Embedding service provider Oracle invokes. */
		private @Nullable String provider;

		/** Provider-specific embedding model to use. */
		private @Nullable String model;

		/** Name of the Oracle credential used to authenticate with the provider. */
		private @Nullable String credentialName;

		/** Provider endpoint URL, when the selected provider requires one. */
		private @Nullable String url;

		/** Maximum time Oracle waits for the provider transfer to complete. */
		private @Nullable Integer transferTimeout;

		/** Maximum number of input texts sent to the provider in one request. */
		private @Nullable Integer maxCount;

		/** Number of input texts Oracle groups into each embedding batch. */
		private @Nullable Integer batchSize;

		/**
		 * Set provider name.
		 * @param provider provider name
		 * @return this builder
		 */
		public Builder provider(String provider) {
			Assert.hasText(provider, "provider must not be empty");
			this.provider = provider;
			return this;
		}

		/**
		 * Set model name.
		 * @param model model name
		 * @return this builder
		 */
		public Builder model(String model) {
			Assert.hasText(model, "model must not be empty");
			this.model = model;
			return this;
		}

		/**
		 * Set credential name.
		 * @param credentialName Oracle credential name
		 * @return this builder
		 */
		public Builder credentialName(String credentialName) {
			Assert.hasText(credentialName, "credentialName must not be empty");
			this.credentialName = credentialName;
			return this;
		}

		/**
		 * Set endpoint URL.
		 * @param url endpoint URL
		 * @return this builder
		 */
		public Builder url(String url) {
			Assert.hasText(url, "url must not be empty");
			this.url = url;
			return this;
		}

		/**
		 * Set transfer timeout.
		 * @param transferTimeout transfer timeout
		 * @return this builder
		 */
		public Builder transferTimeout(@Nullable Integer transferTimeout) {
			this.transferTimeout = transferTimeout;
			return this;
		}

		/**
		 * Set maximum count.
		 * @param maxCount maximum count
		 * @return this builder
		 */
		public Builder maxCount(@Nullable Integer maxCount) {
			this.maxCount = maxCount;
			return this;
		}

		/**
		 * Set batch size.
		 * @param batchSize batch size
		 * @return this builder
		 */
		public Builder batchSize(@Nullable Integer batchSize) {
			this.batchSize = batchSize;
			return this;
		}

		/**
		 * Build validated preferences.
		 * @return immutable embedding preferences
		 */
		public OracleEmbeddingPreferences build() {
			Assert.isTrue(StringUtils.hasText(this.provider), "provider must not be empty");
			Assert.isTrue(StringUtils.hasText(this.model), "model must not be empty");
			String provider = Objects.requireNonNull(this.provider, "provider must not be null");
			String model = Objects.requireNonNull(this.model, "model must not be null");
			return new OracleEmbeddingPreferences(provider, model, this.credentialName, this.url, this.transferTimeout,
					this.maxCount, this.batchSize);
		}

	}

}
