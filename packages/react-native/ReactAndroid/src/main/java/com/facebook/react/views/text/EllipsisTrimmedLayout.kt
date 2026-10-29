/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 *
 * This source code is licensed under the MIT license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.facebook.react.views.text

import android.text.Layout
import android.text.SpannableString
import android.text.Spanned
import android.text.TextDirectionHeuristics
import android.text.style.ClickableSpan
import android.text.style.ReplacementSpan
import com.facebook.react.common.annotations.UnstableReactNativeAPI
import com.facebook.react.views.text.internal.span.CustomLetterSpacingSpan
import com.facebook.react.views.text.internal.span.TextEffectSpan
import com.facebook.react.views.text.internal.span.TouchableSpan

/**
 * A delegating [Layout] over the text of a [delegate] ellipsized at the end, without the text
 * hidden after the ellipsis.
 *
 * The last line of an ellipsized layout keeps all the text after the ellipsis, as zero-width
 * characters, and Android shapes all of it every time the layout is drawn. This layout's text stops
 * right after the ellipsis character, and its line metrics are delegated to [delegate], like in
 * [MutableSpannableLayout], so it draws the same visible text without laying anything out again.
 */
internal class EllipsisTrimmedLayout
private constructor(
    private val delegate: Layout,
    trimmedText: SpannableString,
) :
    Layout(
        trimmedText,
        delegate.paint,
        delegate.width,
        delegate.alignment,
        delegate.spacingMultiplier,
        delegate.spacingAdd,
    ) {

  private val lastLine = delegate.lineCount - 1

  /** The layout this one draws, with the whole text. */
  val originalLayout: Layout
    get() = delegate

  companion object {
    /**
     * Returns an [EllipsisTrimmedLayout] if [layout], a layout of [source] that isn't justified or
     * hyphenated, is ellipsized at the end and hides text that only matters for drawing, else null.
     */
    @OptIn(UnstableReactNativeAPI::class)
    fun createIfNeeded(layout: Layout, source: Spanned): EllipsisTrimmedLayout? {
      val lastLine = layout.lineCount - 1
      if (lastLine < 0 || layout.getEllipsisCount(lastLine) <= 1) {
        return null
      }
      // The ellipsis character is the first ellipsized character; the rest of the last line is
      // hidden, and so are the paragraphs after it. Text after the ellipsized range (start or
      // middle ellipsis) is visible, so keep it.
      val ellipsisStart = layout.getLineStart(lastLine) + layout.getEllipsisStart(lastLine)
      if (ellipsisStart + layout.getEllipsisCount(lastLine) != layout.getLineEnd(lastLine)) {
        return null
      }
      val end = ellipsisStart + 1
      // Keep the text whole when its hidden part matters beyond drawing: line directions are
      // computed over the whole line, and text with right-to-left characters anywhere gets
      // bounds-based drawing on Android 15+, which this layout doesn't have; links are exposed to
      // accessibility, inline views are measured, replacement spans and text effects draw or work
      // on the range they were given, and so do touchable spans.
      if (
          TextDirectionHeuristics.ANYRTL_LTR.isRtl(source, 0, source.length) ||
              source.getSpans(end, source.length, ClickableSpan::class.java).isNotEmpty() ||
              source.getSpans(end, source.length, ReplacementSpan::class.java).isNotEmpty() ||
              source.getSpans(end, source.length, TextEffectSpan::class.java).isNotEmpty() ||
              source.getSpans(end, source.length, TouchableSpan::class.java).isNotEmpty()
      ) {
        return null
      }
      // Before Android 15, letter spacing also spaces the hidden characters, and a centered or
      // end-aligned line is drawn from a width that includes them.
      if (
          layout.alignment != Layout.Alignment.ALIGN_NORMAL &&
              (layout.paint.letterSpacing != 0f ||
                  source
                      .getSpans(
                          end,
                          layout.getLineEnd(lastLine),
                          CustomLetterSpacingSpan::class.java,
                      )
                      .isNotEmpty())
      ) {
        return null
      }
      // The layout's text already has the ellipsis character in place.
      val trimmedText = layout.text.subSequence(0, end)
      if (trimmedText[trimmedText.length - 1] != '\u2026') {
        return null
      }
      return EllipsisTrimmedLayout(
          layout,
          trimmedText as? SpannableString ?: SpannableString(trimmedText),
      )
    }
  }

  // --- Abstract methods: delegated, except where the last line ends ---

  override fun getLineCount(): Int = delegate.lineCount

  override fun getLineTop(line: Int): Int = delegate.getLineTop(line)

  override fun getLineDescent(line: Int): Int = delegate.getLineDescent(line)

  override fun getLineStart(line: Int): Int =
      if (line > lastLine) text.length else delegate.getLineStart(line)

  override fun getLineContainsTab(line: Int): Boolean = delegate.getLineContainsTab(line)

  override fun getLineDirections(line: Int): Directions = delegate.getLineDirections(line)

  override fun getTopPadding(): Int = delegate.topPadding

  override fun getBottomPadding(): Int = delegate.bottomPadding

  override fun getEllipsisStart(line: Int): Int = delegate.getEllipsisStart(line)

  override fun getEllipsisCount(line: Int): Int =
      if (line == lastLine) text.length - getLineStart(line) - getEllipsisStart(line)
      else delegate.getEllipsisCount(line)

  override fun getParagraphDirection(line: Int): Int = delegate.getParagraphDirection(line)

  // --- Non-abstract overrides: StaticLayout's fast paths, as in MutableSpannableLayout ---

  override fun getEllipsizedWidth(): Int = delegate.ellipsizedWidth

  override fun getLineMax(line: Int): Float = delegate.getLineMax(line)

  override fun getLineWidth(line: Int): Float = delegate.getLineWidth(line)

  override fun getLineLeft(line: Int): Float = delegate.getLineLeft(line)

  override fun getLineRight(line: Int): Float = delegate.getLineRight(line)

  // Only called by the framework on API 33+
  @android.annotation.SuppressLint("NewApi")
  override fun isFallbackLineSpacingEnabled(): Boolean = delegate.isFallbackLineSpacingEnabled
}
