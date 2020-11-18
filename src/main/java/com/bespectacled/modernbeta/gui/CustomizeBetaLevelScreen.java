package com.bespectacled.modernbeta.gui;

import com.bespectacled.modernbeta.ModernBeta;
import com.bespectacled.modernbeta.gen.settings.OldGeneratorSettings;
import com.bespectacled.modernbeta.util.GUIUtil;
import com.bespectacled.modernbeta.util.WorldEnum;
import com.bespectacled.modernbeta.util.WorldEnum.BetaBiomeType;
import com.bespectacled.modernbeta.util.WorldEnum.PreBetaBiomeType;

import net.minecraft.client.gui.DrawableHelper;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ScreenTexts;
import net.minecraft.client.gui.screen.world.CreateWorldScreen;
import net.minecraft.client.gui.widget.ButtonListWidget;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.options.BooleanOption;
import net.minecraft.client.options.CyclingOption;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.Text;
import net.minecraft.text.TranslatableText;

public class CustomizeBetaLevelScreen extends Screen {
    private CreateWorldScreen parent;
    private OldGeneratorSettings generatorSettings;
    

    private int biomeType = BetaBiomeType.fromName(ModernBeta.BETA_CONFIG.betaBiomeType).getId();
    private boolean generateBetaOceans = ModernBeta.BETA_CONFIG.generateBetaOceans;
    
    private ButtonListWidget buttonList;

    public CustomizeBetaLevelScreen(CreateWorldScreen parent, OldGeneratorSettings generatorSettings) {
        super(new TranslatableText("createWorld.customize.beta.title"));
        
        this.parent = parent;
        this.generatorSettings = generatorSettings;
        
        if (generatorSettings.settings.contains("betaBiomeType"))
            BetaBiomeType.fromName(generatorSettings.settings.getString("betaBiomeType")).getId();
        if (generatorSettings.settings.contains("generateBetaOceans"))
            generateBetaOceans = generatorSettings.settings.getBoolean("generateBetaOceans");
        
        
        
    }
    
    @Override
    protected void init() {
        this.addButton(new ButtonWidget(
            this.width / 2 - 155, this.height - 28, 150, 20, 
            ScreenTexts.DONE, 
            (buttonWidget) -> {
                this.client.openScreen(parent);
                return;
            }
        ));

        this.addButton(new ButtonWidget(
            this.width / 2 + 5, this.height - 28, 150, 20, 
            ScreenTexts.CANCEL,
            (buttonWidget) -> {
                this.client.openScreen(parent);
            }
        ));
        
        this.buttonList = new ButtonListWidget(this.client, this.width, this.height, 32, this.height - 32, 25);
        
        this.buttonList.addSingleOptionEntry(
            new CyclingOption(
                "createWorld.customize.beta.typeButton",
                (gameOptions, value) -> {
                    this.biomeType++;
                    if (this.biomeType > WorldEnum.BetaBiomeType.values().length - 1) this.biomeType = 0;
                    generatorSettings.settings.putString("betaBiomeType", BetaBiomeType.fromId(this.biomeType).getName());
                    
                    return;
                },
                (gameOptions, cyclingOptions) -> {
                    Text typeText = GUIUtil.TEXT_CLASSIC;
                    BetaBiomeType type = BetaBiomeType.fromId(this.biomeType);
                    
                    switch(type) {
                        case CLASSIC:
                            typeText = GUIUtil.TEXT_CLASSIC;
                            break;
                        case ICE_DESERT:
                            typeText = GUIUtil.TEXT_ICE_DESERT;
                            break;
                        case SKY:
                            typeText = GUIUtil.TEXT_SKY;
                            break;
                        case VANILLA:
                            typeText = GUIUtil.TEXT_VANILLA;
                            break;
                    }
                    
                    return new TranslatableText(
                        "options.generic_value", 
                        new Object[] { 
                            GUIUtil.TEXT_BIOME_TYPE, 
                            typeText
                    });
                }
        ));
        
        this.buttonList.addSingleOptionEntry(
            new BooleanOption(
                "createWorld.customize.beta.generateOceans", 
                (gameOptions) -> { return generateBetaOceans; }, // Getter
                (gameOptions, value) -> { // Setter
                    generateBetaOceans = value;
                    generatorSettings.settings.putBoolean("generateBetaOceans", value);
                }
        ));
            
        
        this.children.add(this.buttonList);

    }
    
    @Override
    public void render(MatrixStack matrixStack, int mouseX, int mouseY, float tickDelta) {
        this.renderBackground(matrixStack);
        
        this.buttonList.render(matrixStack, mouseX, mouseY, tickDelta);
        DrawableHelper.drawCenteredText(matrixStack, this.textRenderer, this.title, this.width / 2, 16, 16777215);
        
        super.render(matrixStack, mouseX, mouseY, tickDelta);
    }
    
}
