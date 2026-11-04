package com.quickactions.ui;

import com.quickactions.ui.theme.Theme;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.List;

public class ChoiceScreen extends Screen {

    public record Choice(Component label, Runnable action) {
    }

    private static final int TEXT_WIDTH = 300;
    private static final int BUTTON_WIDTH = 220;
    private static final int BUTTON_HEIGHT = 20;
    private static final int BUTTON_GAP = 4;
    private static final int COLOR_TITLE = 0xFFFFFFFF;
    private static final int COLOR_TITLE_DANGER = 0xFFFF7B7B;

    private final Screen parent;
    private final Component message;
    private final List<Choice> choices;
    private final boolean danger;

    private List<String> lines = List.of();
    private int titleY;
    private int messageY;

    public ChoiceScreen(Screen parent, Component title, Component message, List<Choice> choices) {
        this(parent, title, message, choices, false);
    }

    public ChoiceScreen(Screen parent, Component title, Component message, List<Choice> choices, boolean danger) {
        super(title);
        this.parent = parent;
        this.message = message;
        this.choices = choices;
        this.danger = danger;
    }

    @Override
    protected void init() {
        lines = UiText.wrap(this.font, message.getString(), TEXT_WIDTH);

        int lineHeight = this.font.lineHeight;
        int messageHeight = lines.size() * (lineHeight + 2);
        int buttonsHeight = choices.size() * (BUTTON_HEIGHT + BUTTON_GAP) - BUTTON_GAP;
        int total = lineHeight + 10 + messageHeight + 14 + buttonsHeight;

        int top = Math.max(12, (this.height - total) / 2);
        titleY = top;
        messageY = top + lineHeight + 10;
        int buttonTop = messageY + messageHeight + 14;
        int x = this.width / 2 - BUTTON_WIDTH / 2;

        for (int i = 0; i < choices.size(); i++) {
            Choice choice = choices.get(i);
            addRenderableWidget(Button.builder(choice.label(), button -> {
                        Minecraft.getInstance().setScreenAndShow(parent);
                        choice.action().run();
                    })
                    .bounds(x, buttonTop + i * (BUTTON_HEIGHT + BUTTON_GAP), BUTTON_WIDTH, BUTTON_HEIGHT)
                    .build());
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        super.extractRenderState(graphics, mouseX, mouseY, delta);

        int titleColor = danger ? COLOR_TITLE_DANGER : COLOR_TITLE;
        int centerX = this.width / 2;

        graphics.text(this.font, this.title, centerX - this.font.width(this.title) / 2,
                titleY, titleColor, true);
        if (danger) {
            int lineY = titleY + this.font.lineHeight + 3;
            graphics.fill(centerX - 30, lineY, centerX + 30, lineY + 1, COLOR_TITLE_DANGER);
        }

        int y = messageY;
        for (String line : lines) {
            graphics.text(this.font, Component.literal(line), centerX - this.font.width(line) / 2,
                    y, Theme.textSoft(), true);
            y += this.font.lineHeight + 2;
        }
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreenAndShow(parent);
    }
}