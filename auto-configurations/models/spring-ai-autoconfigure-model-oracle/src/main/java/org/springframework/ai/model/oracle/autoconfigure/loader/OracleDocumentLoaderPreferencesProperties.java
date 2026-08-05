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

import org.jspecify.annotations.Nullable;

import org.springframework.util.StringUtils;

/**
 * Configuration properties for Oracle document loader preferences.
 *
 * @author Spring AI Contributors
 */
public class OracleDocumentLoaderPreferencesProperties {

	/** Whether conversion returns plain text rather than structured output. */
	private @Nullable Boolean plaintext;

	/** Character set used to decode the source document. */
	private @Nullable String charset;

	/** Source content format: {@code BINARY}, {@code TEXT}, or {@code IGNORE}. */
	private @Nullable String format;

	public @Nullable Boolean getPlaintext() {
		return this.plaintext;
	}

	public void setPlaintext(@Nullable Boolean plaintext) {
		this.plaintext = plaintext;
	}

	public @Nullable String getCharset() {
		return this.charset;
	}

	public void setCharset(@Nullable String charset) {
		this.charset = charset;
	}

	public @Nullable String getFormat() {
		return this.format;
	}

	public void setFormat(@Nullable String format) {
		this.format = format;
	}

	public boolean isConfigured() {
		return this.plaintext != null || StringUtils.hasText(this.charset) || StringUtils.hasText(this.format);
	}

}
