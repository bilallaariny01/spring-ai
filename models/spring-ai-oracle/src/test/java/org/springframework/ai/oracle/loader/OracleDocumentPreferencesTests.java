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

import java.util.Locale;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link OracleDocumentPreferences}.
 *
 * @author Spring AI Contributors
 */
class OracleDocumentPreferencesTests {

	/**
	 * Verify all preference fields can be set and normalized.
	 */
	@Test
	void buildWithAllValues() {
		OracleDocumentPreferences preferences = OracleDocumentPreferences.builder()
			.plaintext(true)
			.charset("UTF8")
			.format("text")
			.build();

		assertThat(preferences.isEmpty()).isFalse();
		assertThat(preferences.getPlaintext()).isTrue();
		assertThat(preferences.getCharset()).isEqualTo("UTF8");
		assertThat(preferences.getFormat()).isEqualTo("TEXT");
		assertThat(preferences.toByteArray()).isNotEmpty();
	}

	/**
	 * Verify default preferences remain empty.
	 */
	@Test
	void emptyPreferencesRemainEmpty() {
		OracleDocumentPreferences preferences = OracleDocumentPreferences.builder().build();

		assertThat(preferences.isEmpty()).isTrue();
		assertThat(preferences.getPlaintext()).isNull();
		assertThat(preferences.getCharset()).isNull();
		assertThat(preferences.getFormat()).isNull();
	}

	/**
	 * Verify blank charset is rejected.
	 */
	@Test
	void rejectBlankCharset() {
		assertThatThrownBy(() -> OracleDocumentPreferences.builder().charset(" ").build())
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("charset must not be empty");
	}

	/**
	 * Verify blank format is rejected.
	 */
	@Test
	void rejectBlankFormat() {
		assertThatThrownBy(() -> OracleDocumentPreferences.builder().format(" ").build())
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("format must not be empty");
	}

	/**
	 * Verify unsupported format values are rejected.
	 */
	@Test
	void rejectUnsupportedFormat() {
		assertThatThrownBy(() -> OracleDocumentPreferences.builder().format("JSON").build())
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("format must be one of: BINARY, TEXT, IGNORE");
	}

	/**
	 * Verify format normalization is independent of the JVM default locale.
	 */
	@Test
	void normalizeFormatUsesLocaleIndependentCaseConversion() {
		Locale previousLocale = Locale.getDefault();
		try {
			Locale.setDefault(Locale.forLanguageTag("tr-TR"));

			OracleDocumentPreferences preferences = OracleDocumentPreferences.builder().format("binary").build();

			assertThat(preferences.getFormat()).isEqualTo("BINARY");
		}
		finally {
			Locale.setDefault(previousLocale);
		}
	}

}
