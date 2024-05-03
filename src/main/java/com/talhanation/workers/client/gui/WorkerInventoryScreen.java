package com.talhanation.workers.client.gui;

import com.mojang.blaze3d.vertex.PoseStack;
import com.talhanation.workers.Main;
import com.talhanation.workers.Translatable;
import com.talhanation.workers.entities.LumberjackEntity;
import com.talhanation.workers.entities.MinerEntity;
import com.talhanation.workers.inventory.WorkerInventoryContainer;
import com.talhanation.workers.entities.AbstractWorkerEntity;
import com.talhanation.workers.network.MessageLumberjackReplant;
import de.maxhenkel.corelib.inventory.ScreenBase;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;



public class WorkerInventoryScreen extends ScreenBase<WorkerInventoryContainer> {

    private static final ResourceLocation GUI_TEXTURE_3 = new ResourceLocation(Main.MOD_ID,
            "textures/gui/worker_gui.png");

    private final AbstractWorkerEntity worker;
    private final Inventory playerInventory;
    private boolean replantSaplings;

    public WorkerInventoryScreen(WorkerInventoryContainer container, Inventory playerInventory, Component title) {
        super(GUI_TEXTURE_3, container, playerInventory, new TextComponent(""));
        Main.LOGGER.info("WorkerInventoryScreen loaded");
        this.worker = container.getWorker();
        this.playerInventory = playerInventory;

        imageWidth = 176;
        imageHeight = 222;
    }

    @Override
    protected void init() {
        super.init();
        if(worker instanceof LumberjackEntity lumberjack) {
            this.replantSaplings = lumberjack.getReplantSaplings();
        }



        /*
        // HOME POS
        addRenderableWidget(new Button(leftPos + 60, topPos + 60, 12, 12, Component.literal("Home"), button -> {
            Main.SIMPLE_CHANNEL.sendToServer(
                    new MessageHomePos(playerInventory.player.getUUID(), worker.getUUID(), worker.getWorkerOnPos()));
            Main.LOGGER.debug("Screen: " + worker.getWorkerOnPos().toShortString());
        }));
         */
        this.setButtons();
    }

    private void setButtons() {
        this.clearWidgets();
        if(worker instanceof LumberjackEntity){
            String string = replantSaplings ? "True" : "False";
            this.setReplantSaplingsButton(string);
        }
    }

    private void setReplantSaplingsButton(String string) {
        addRenderableWidget(new Button(leftPos + 190, topPos + 57, 40, 20, new TextComponent(string), button1 -> {
            this.replantSaplings = !replantSaplings;

            Main.SIMPLE_CHANNEL.sendToServer(new MessageLumberjackReplant(worker.getUUID(), replantSaplings));
            this.setButtons();
        },
        (button1, poseStack, i, i1) -> {
            this.renderTooltip(poseStack, Translatable.TOOLTIP_LUMBER_REPLANT, i, i1);
        }));
    }

    @Override
    protected void renderLabels(PoseStack matrixStack, int mouseX, int mouseY) {
        super.renderLabels(matrixStack, mouseX, mouseY);
        font.draw(matrixStack, worker.getDisplayName().getVisualOrderText(), 8, 6, FONT_COLOR);
        font.draw(matrixStack, playerInventory.getDisplayName().getVisualOrderText(), 8, imageHeight - 152 + 3, FONT_COLOR);
        int offset = this.worker instanceof MinerEntity ? 10 : 0;
        if(worker.inventoryInputHelp() != null){
            for(int i=0; i < worker.inventoryInputHelp().size(); i++){
                renderItemAt(worker.inventoryInputHelp().get(i).getDefaultInstance(), 60 - offset + (15 * i), imageHeight - 165);
            }
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    protected void renderBg(PoseStack matrixStack, float partialTicks, int mouseX, int mouseY) {
        super.renderBg(matrixStack, partialTicks, mouseX, mouseY);
    }

    private void renderItemAt(ItemStack itemStack, int x, int y) {
        if(itemStack != null) itemRenderer.renderAndDecorateItem(itemStack, x, y);
    }
}
