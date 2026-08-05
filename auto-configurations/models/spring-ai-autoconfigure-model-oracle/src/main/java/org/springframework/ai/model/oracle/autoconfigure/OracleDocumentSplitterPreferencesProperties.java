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

import java.util.List;

import org.jspecify.annotations.Nullable;

import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

/**
 * Configuration properties for Oracle document splitter preferences.
 *
 * @author Spring AI Contributors
 */
public class OracleDocumentSplitterPreferencesProperties {

	/**
	 * Unit used to measure chunk size, such as words, sentences, or vocabulary entries.
	 */
	private @Nullable String by;

	/** Maximum size of each chunk, measured in the selected {@code by} unit. */
	private @Nullable Integer max;

	/** Number of units shared by consecutive chunks to preserve context. */
	private @Nullable Integer overlap;

	/** Boundary type used when splitting text into chunks. */
	private @Nullable String split;

	/** Custom delimiters used when {@code split} is {@code custom}. */
	private @Nullable List<String> customList;

	/** Oracle vocabulary name used when {@code by} is {@code vocabulary}. */
	private @Nullable String vocabulary;

	/** Language hint Oracle uses to identify suitable chunk boundaries. */
	private @Nullable String language;

	/** Text normalization mode applied before chunking. */
	private @Nullable String normalize;

	/** Normalization options used when {@code normalize} is {@code options}. */
	private @Nullable List<String> normOptions;

	/** Whether Oracle returns extended chunk metadata. */
	private @Nullable Boolean extended;

	public @Nullable String getBy() {
		return this.by;
	}

	public void setBy(@Nullable String by) {
		this.by = by;
	}

	public @Nullable Integer getMax() {
		return this.max;
	}

	public void setMax(@Nullable Integer max) {
		this.max = max;
	}

	public @Nullable Integer getOverlap() {
		return this.overlap;
	}

	public void setOverlap(@Nullable Integer overlap) {
		this.overlap = overlap;
	}

	public @Nullable String getSplit() {
		return this.split;
	}

	public void setSplit(@Nullable String split) {
		this.split = split;
	}

	public @Nullable List<String> getCustomList() {
		return this.customList;
	}

	public void setCustomList(@Nullable List<String> customList) {
		this.customList = customList;
	}

	public @Nullable String getVocabulary() {
		return this.vocabulary;
	}

	public void setVocabulary(@Nullable String vocabulary) {
		this.vocabulary = vocabulary;
	}

	public @Nullable String getLanguage() {
		return this.language;
	}

	public void setLanguage(@Nullable String language) {
		this.language = language;
	}

	public @Nullable String getNormalize() {
		return this.normalize;
	}

	public void setNormalize(@Nullable String normalize) {
		this.normalize = normalize;
	}

	public @Nullable List<String> getNormOptions() {
		return this.normOptions;
	}

	public void setNormOptions(@Nullable List<String> normOptions) {
		this.normOptions = normOptions;
	}

	public @Nullable Boolean getExtended() {
		return this.extended;
	}

	public void setExtended(@Nullable Boolean extended) {
		this.extended = extended;
	}

	public boolean isConfigured() {
		return StringUtils.hasText(this.by) || this.max != null || this.overlap != null
				|| StringUtils.hasText(this.split) || !CollectionUtils.isEmpty(this.customList)
				|| StringUtils.hasText(this.vocabulary) || StringUtils.hasText(this.language)
				|| StringUtils.hasText(this.normalize) || !CollectionUtils.isEmpty(this.normOptions)
				|| this.extended != null;
	}

}
