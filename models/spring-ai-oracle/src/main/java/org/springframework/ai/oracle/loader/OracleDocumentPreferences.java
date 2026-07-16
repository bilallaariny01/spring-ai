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

package org.springframework.ai.oracle.loader;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Locale;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.json.JsonMapper;
import oracle.jdbc.provider.oson.OsonFactory;
import org.jspecify.annotations.Nullable;

import org.springframework.util.Assert;

/**
 * Structured document conversion preferences for DBMS_VECTOR_CHAIN.UTL_TO_TEXT.
 *
 * @author Spring AI Contributors
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public final class OracleDocumentPreferences {

	private static final OsonFactory OSON_FACTORY = new OsonFactory();

	private static final JsonMapper LC4J_MAPPER = new JsonMapper();

	/** Whether conversion returns plain text rather than structured output. */
	@JsonProperty("plaintext")
	private final @Nullable String plaintext;

	/** Character set used to decode the source document. */
	@JsonProperty("charset")
	private final @Nullable String charset;

	/** Source content format: {@code BINARY}, {@code TEXT}, or {@code IGNORE}. */
	@JsonProperty("format")
	private final @Nullable String format;

	/**
	 * Create immutable document conversion preferences.
	 * @param plaintext plaintext toggle
	 * @param charset source charset
	 * @param format source format
	 */
	private OracleDocumentPreferences(@Nullable Boolean plaintext, @Nullable String charset, @Nullable String format) {
		this.plaintext = (plaintext != null) ? plaintext.toString() : null;
		this.charset = charset;
		this.format = format;
	}

	/**
	 * Create a builder for document preferences.
	 * @return preferences builder
	 */
	public static Builder builder() {
		return new Builder();
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
			throw new IllegalStateException("Failed to serialize document preferences to Oracle OSON", ex);
		}
	}

	/**
	 * Check whether no preference fields are set.
	 * @return {@code true} when empty
	 */
	public boolean isEmpty() {
		return this.plaintext == null && this.charset == null && this.format == null;
	}

	/**
	 * Return plaintext preference.
	 * @return plaintext value
	 */
	public @Nullable Boolean getPlaintext() {
		return (this.plaintext != null) ? Boolean.valueOf(this.plaintext) : null;
	}

	/**
	 * Return charset preference.
	 * @return charset value
	 */
	public @Nullable String getCharset() {
		return this.charset;
	}

	/**
	 * Return format preference.
	 * @return format value
	 */
	public @Nullable String getFormat() {
		return this.format;
	}

	/**
	 * Normalize and validate format name.
	 * @param format raw format value
	 * @return normalized format
	 */
	static String normalizeFormat(String format) {
		Assert.hasText(format, "format must not be empty");
		String normalized = format.toUpperCase(Locale.ROOT);
		Assert.isTrue("BINARY".equals(normalized) || "TEXT".equals(normalized) || "IGNORE".equals(normalized),
				"format must be one of: BINARY, TEXT, IGNORE");
		return normalized;
	}

	public static final class Builder {

		/** Whether conversion returns plain text rather than structured output. */
		private @Nullable Boolean plaintext;

		/** Character set used to decode the source document. */
		private @Nullable String charset;

		/** Source content format: {@code BINARY}, {@code TEXT}, or {@code IGNORE}. */
		private @Nullable String format;

		/**
		 * Set plaintext preference.
		 * @param plaintext plaintext toggle
		 * @return this builder
		 */
		public Builder plaintext(@Nullable Boolean plaintext) {
			this.plaintext = plaintext;
			return this;
		}

		/**
		 * Set charset preference.
		 * @param charset charset value
		 * @return this builder
		 */
		public Builder charset(@Nullable String charset) {
			if (charset != null) {
				Assert.hasText(charset, "charset must not be empty");
			}
			this.charset = charset;
			return this;
		}

		/**
		 * Set format preference.
		 * @param format format value
		 * @return this builder
		 */
		public Builder format(@Nullable String format) {
			this.format = (format != null) ? normalizeFormat(format) : null;
			return this;
		}

		/**
		 * Build immutable document preferences.
		 * @return configured preferences
		 */
		public OracleDocumentPreferences build() {
			return new OracleDocumentPreferences(this.plaintext, this.charset, this.format);
		}

	}

}
