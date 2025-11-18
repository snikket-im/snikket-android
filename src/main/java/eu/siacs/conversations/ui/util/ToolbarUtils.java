/*
 * Copyright (C) 2020 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package eu.siacs.conversations.ui.util;

import static java.util.Collections.max;
import static java.util.Collections.min;

import android.graphics.drawable.Drawable;
import android.text.TextUtils;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.ActionMenuView;
import androidx.appcompat.widget.Toolbar;

import com.google.android.material.appbar.MaterialToolbar;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class ToolbarUtils {

    private static final Comparator<View> VIEW_TOP_COMPARATOR =
            new Comparator<View>() {
                @Override
                public int compare(View view1, View view2) {
                    return view1.getTop() - view2.getTop();
                }
            };

    private ToolbarUtils() {
        // Private constructor to prevent unwanted construction.
    }

    public static void resetActionBarOnClickListeners(@NonNull MaterialToolbar view) {
        final TextView title = getTitleTextView(view);
        final TextView subtitle = getSubtitleTextView(view);
        if (title != null) {
            title.setOnClickListener(null);
        }
        if (subtitle != null) {
            subtitle.setOnClickListener(null);
        }
    }

    public static void setActionBarOnClickListener(
            @NonNull MaterialToolbar view, @NonNull final View.OnClickListener onClickListener) {
        final TextView title = getTitleTextView(view);
        final TextView subtitle = getSubtitleTextView(view);
        if (title != null) {
            title.setOnClickListener(onClickListener);
        }
        if (subtitle != null) {
            subtitle.setOnClickListener(onClickListener);
        }
    }

    @Nullable
    public static TextView getTitleTextView(@NonNull Toolbar toolbar) {
        List<TextView> textViews = getTextViewsWithText(toolbar, toolbar.getTitle());
        return textViews.isEmpty() ? null : min(textViews, VIEW_TOP_COMPARATOR);
    }

    @Nullable
    public static TextView getSubtitleTextView(@NonNull Toolbar toolbar) {
        List<TextView> textViews = getTextViewsWithText(toolbar, toolbar.getSubtitle());
        return textViews.isEmpty() ? null : max(textViews, VIEW_TOP_COMPARATOR);
    }

    private static List<TextView> getTextViewsWithText(
            @NonNull Toolbar toolbar, CharSequence text) {
        List<TextView> textViews = new ArrayList<>();
        for (int i = 0; i < toolbar.getChildCount(); i++) {
            View child = toolbar.getChildAt(i);
            if (child instanceof TextView textView) {
                if (TextUtils.equals(textView.getText(), text)) {
                    textViews.add(textView);
                }
            }
        }
        return textViews;
    }

    @Nullable
    public static ImageView getLogoImageView(@NonNull Toolbar toolbar) {
        return getImageView(toolbar, toolbar.getLogo());
    }

    @Nullable
    private static ImageView getImageView(@NonNull Toolbar toolbar, @Nullable Drawable content) {
        if (content == null) {
            return null;
        }
        for (int i = 0; i < toolbar.getChildCount(); i++) {
            View child = toolbar.getChildAt(i);
            if (child instanceof ImageView imageView) {
                Drawable drawable = imageView.getDrawable();
                if (drawable != null
                        && drawable.getConstantState() != null
                        && drawable.getConstantState().equals(content.getConstantState())) {
                    return imageView;
                }
            }
        }
        return null;
    }

    @Nullable
    public static View getSecondaryActionMenuItemView(@NonNull Toolbar toolbar) {
        ActionMenuView actionMenuView = getActionMenuView(toolbar);
        if (actionMenuView != null) {
            // Only return the first child of the ActionMenuView if there is more than one child
            if (actionMenuView.getChildCount() > 1) {
                return actionMenuView.getChildAt(0);
            }
        }
        return null;
    }

    @Nullable
    public static ActionMenuView getActionMenuView(@NonNull Toolbar toolbar) {
        for (int i = 0; i < toolbar.getChildCount(); i++) {
            View child = toolbar.getChildAt(i);
            if (child instanceof ActionMenuView) {
                return (ActionMenuView) child;
            }
        }
        return null;
    }

    @Nullable
    public static ImageButton getNavigationIconButton(@NonNull Toolbar toolbar) {
        Drawable navigationIcon = toolbar.getNavigationIcon();
        if (navigationIcon == null) {
            return null;
        }
        for (int i = 0; i < toolbar.getChildCount(); i++) {
            View child = toolbar.getChildAt(i);
            if (child instanceof ImageButton imageButton) {
                if (imageButton.getDrawable() == navigationIcon) {
                    return imageButton;
                }
            }
        }
        return null;
    }
}
