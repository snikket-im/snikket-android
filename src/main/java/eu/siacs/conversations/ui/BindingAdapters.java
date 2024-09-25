package eu.siacs.conversations.ui;

import android.view.View;

import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.color.MaterialColors;
import com.google.common.collect.Collections2;
import com.google.common.collect.ImmutableSet;

import eu.siacs.conversations.R;
import eu.siacs.conversations.entities.Reaction;

import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;

public class BindingAdapters {

    public static void setReactionsOnReceived(
            final ChipGroup chipGroup,
            final Reaction.Aggregated reactions,
            final Consumer<Collection<String>> onModifiedReactions,
            final Runnable addReaction) {
        setReactions(chipGroup, reactions, true, onModifiedReactions, addReaction);
    }

    public static void setReactionsOnSent(
            final ChipGroup chipGroup,
            final Reaction.Aggregated reactions,
            final Consumer<Collection<String>> onModifiedReactions) {
        setReactions(chipGroup, reactions, false, onModifiedReactions, null);
    }

    private static void setReactions(
            final ChipGroup chipGroup,
            final Reaction.Aggregated aggregated,
            final boolean onReceived,
            final Consumer<Collection<String>> onModifiedReactions,
            final Runnable addReaction) {
        final var context = chipGroup.getContext();
        final List<Map.Entry<String, Integer>> reactions = aggregated.reactions;
        if (reactions == null || reactions.isEmpty()) {
            chipGroup.setVisibility(View.GONE);
        } else {
            chipGroup.removeAllViews();
            chipGroup.setVisibility(View.VISIBLE);
            for (final Map.Entry<String, Integer> reaction : reactions) {
                final var emoji = reaction.getKey();
                final var count = reaction.getValue();
                final Chip chip = new Chip(chipGroup.getContext());
                chip.setEnsureMinTouchTargetSize(false);
                chip.setChipStartPadding(0.0f);
                chip.setChipEndPadding(0.0f);
                if (count == 1) {
                    chip.setText(emoji);
                } else {
                    chip.setText(String.format(Locale.ENGLISH, "%s %d", emoji, count));
                }
                final boolean oneOfOurs = aggregated.ourReactions.contains(emoji);
                // received = surface; sent = surface high matches bubbles
                if (oneOfOurs) {
                    chip.setChipBackgroundColor(
                            MaterialColors.getColorStateListOrNull(
                                    context,
                                    com.google.android.material.R.attr
                                            .colorSurfaceContainerHighest));
                } else {
                    chip.setChipBackgroundColor(
                            MaterialColors.getColorStateListOrNull(
                                    context,
                                    com.google.android.material.R.attr.colorSurfaceContainerLow));
                }
                chip.setOnClickListener(
                        v -> {
                            if (oneOfOurs) {
                                onModifiedReactions.accept(
                                        ImmutableSet.copyOf(
                                                Collections2.filter(
                                                        aggregated.ourReactions,
                                                        r -> !r.equals(emoji))));
                            } else {
                                onModifiedReactions.accept(
                                        new ImmutableSet.Builder<String>()
                                                .addAll(aggregated.ourReactions)
                                                .add(emoji)
                                                .build());
                            }
                        });
                chipGroup.addView(chip);
            }
            if (onReceived) {
                final Chip chip = new Chip(chipGroup.getContext());
                chip.setChipIconResource(R.drawable.ic_add_reaction_24dp);
                chip.setChipStrokeColor(
                        MaterialColors.getColorStateListOrNull(
                                chipGroup.getContext(),
                                com.google.android.material.R.attr.colorTertiary));
                chip.setChipBackgroundColor(
                        MaterialColors.getColorStateListOrNull(
                                chipGroup.getContext(),
                                com.google.android.material.R.attr.colorTertiaryContainer));
                chip.setChipIconTint(
                        MaterialColors.getColorStateListOrNull(
                                chipGroup.getContext(),
                                com.google.android.material.R.attr.colorOnTertiaryContainer));
                chip.setEnsureMinTouchTargetSize(false);
                chip.setTextEndPadding(0.0f);
                chip.setTextStartPadding(0.0f);
                chip.setOnClickListener(v -> addReaction.run());
                chipGroup.addView(chip);
            }
        }
    }
}
