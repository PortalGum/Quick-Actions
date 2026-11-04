package com.quickactions.ui;

import com.quickactions.ui.theme.Theme;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.function.Consumer;

public class TextInputScreen extends Screen {
    private static final int SIMPLE_WIDTH = 200;
    private static final int RICH_WIDTH = 340;
    private static final int PREVIEW_LINES = 3;
    private static final int PREVIEW_DEFAULT_COLOR = 0xFFAAAAAA;

    private static final int COLOR_TITLE = 0xFFFFFFFF;

    private final Screen parent;
    private final String initialValue;
    private final int maxLength;
    private final @Nullable Component hint;
    private final boolean richPreview;
    private final Consumer<String> onConfirm;
    private @Nullable Runnable onCancel;

    private EditBox input;
    private Button confirmButton;

    private List<String> hintLines = List.of();
    private int fieldLeft;
    private int fieldWidth;
    private int titleY;
    private int hintY;
    private int previewY;
    private int previewHeight;

    private @Nullable String cachedInput;
    private List<List<RichText.Run>> cachedLines = List.of();

    public TextInputScreen(Screen parent, Component title, String initialValue, int maxLength,
                           Consumer<String> onConfirm) {
        this(parent, title, initialValue, maxLength, null, false, onConfirm);
    }

    public TextInputScreen(Screen parent, Component title, String initialValue, int maxLength,
                           @Nullable Component hint, boolean richPreview, Consumer<String> onConfirm) {
        super(title);
        this.parent = parent;
        this.initialValue = initialValue;
        this.maxLength = maxLength;
        this.hint = hint;
        this.richPreview = richPreview;
        this.onConfirm = onConfirm;
    }

    public TextInputScreen onCancel(Runnable action) {
        this.onCancel = action;
        return this;
    }

    @Override
    protected void init() {
        boolean rich = hint != null || richPreview;
        fieldWidth = rich ? RICH_WIDTH : SIMPLE_WIDTH;
        fieldLeft = this.width / 2 - fieldWidth / 2;
        int lineHeight = this.font.lineHeight;
        cachedInput = null;

        hintLines = hint != null ? UiText.wrap(this.font, hint.getString(), fieldWidth) : List.of();
        previewHeight = richPreview ? PREVIEW_LINES * (lineHeight + 2) + 8 : 0;

        // vertical layout relative to field top
        int relative = 20;
        int relHintY = 0;
        int relPreviewY = 0;
        if (!hintLines.isEmpty()) {
            relHintY = relative + 6;
            relative = relHintY + hintLines.size() * (lineHeight + 2) + 2;
        }
        if (richPreview) {
            relPreviewY = relative + 6;
            relative = relPreviewY + previewHeight;
        }
        int relButtonsY = relative + 10;

        int top = rich
                ? Math.max(34, (this.height - (relButtonsY + 20)) / 2)
                : this.height / 2 - 20;
        titleY = top - 25;
        hintY = top + relHintY;
        previewY = top + relPreviewY;

        input = new EditBox(this.font, fieldLeft, top, fieldWidth, 20,
                Component.translatable("quickactions.input.field"));
        input.setMaxLength(maxLength);
        input.setValue(initialValue);
        input.setResponder(text -> {
            if (confirmButton != null) {
                confirmButton.active = !text.isBlank();
            }
        });
        addRenderableWidget(input);
        setInitialFocus(input);

        int buttonWidth = (fieldWidth - 4) / 2;
        confirmButton = Button.builder(Component.translatable("quickactions.input.save"), button -> confirm())
                .bounds(fieldLeft, top + relButtonsY, buttonWidth, 20)
                .build();
        addRenderableWidget(confirmButton);

        addRenderableWidget(Button.builder(CommonComponents.GUI_CANCEL, button -> onClose())
                .bounds(fieldLeft + buttonWidth + 4, top + relButtonsY, buttonWidth, 20)
                .build());
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        super.extractRenderState(graphics, mouseX, mouseY, delta);

        graphics.text(this.font, this.title,
                (this.width - this.font.width(this.title)) / 2, titleY, COLOR_TITLE, true);

        int lineHeight = this.font.lineHeight;

        int y = hintY;
        for (String line : hintLines) {
            graphics.text(this.font, Component.literal(line), fieldLeft, y, Theme.muted(), true);
            y += lineHeight + 2;
        }

        if (richPreview) {
            graphics.fill(fieldLeft - 1, previewY - 1, fieldLeft + fieldWidth + 1, previewY + previewHeight + 1,
                    Theme.border());
            graphics.fill(fieldLeft, previewY, fieldLeft + fieldWidth, previewY + previewHeight, Theme.card());

            String value = input.getValue();
            if (!value.equals(cachedInput)) {
                cachedInput = value;
                cachedLines = RichText.wrap(this.font, RichText.parse(value), fieldWidth - 12);
            }
            RichText.draw(graphics, this.font, cachedLines, fieldLeft + 6, previewY + 4,
                    PREVIEW_LINES, PREVIEW_DEFAULT_COLOR);
        }
    }

    private void confirm() {
        String value = input.getValue().trim();
        if (value.isEmpty()) return;

        Minecraft.getInstance().setScreenAndShow(parent);
        onConfirm.accept(value);
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreenAndShow(parent);
        if (onCancel != null) onCancel.run();
    }
}