package mekanism.client.gui.element.filter;

import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nonnull;
import mekanism.api.text.EnumColor;
import mekanism.api.text.ILangEntry;
import mekanism.client.gui.IGuiWrapper;
import mekanism.client.gui.element.GuiInnerScreen;
import mekanism.client.gui.element.GuiWindow;
import mekanism.client.gui.element.button.MekanismImageButton;
import mekanism.client.gui.element.button.TranslationButton;
import mekanism.client.gui.element.slot.GuiSequencedSlotDisplay;
import mekanism.client.gui.element.slot.GuiSlot;
import mekanism.client.gui.element.slot.SlotType;
import mekanism.common.Mekanism;
import mekanism.common.MekanismLang;
import mekanism.common.content.filter.IFilter;
import mekanism.common.network.PacketEditFilter;
import mekanism.common.network.PacketNewFilter;
import mekanism.common.tile.base.TileEntityMekanism;
import mekanism.common.tile.interfaces.ITileFilterHolder;
import net.minecraft.item.ItemStack;
import net.minecraft.util.text.ITextComponent;

public abstract class GuiFilter<FILTER extends IFilter<FILTER>, TILE extends TileEntityMekanism & ITileFilterHolder<? super FILTER>> extends GuiWindow
      implements GuiFilterHelper<TILE> {

    private final ITextComponent filterName;
    protected final TILE tile;

    protected ITextComponent status = MekanismLang.STATUS_OK.translateColored(EnumColor.DARK_GREEN);
    protected GuiSequencedSlotDisplay slotDisplay;
    protected FILTER origFilter;
    protected FILTER filter;
    protected boolean isNew;
    protected int ticker;

    public GuiFilter(IGuiWrapper gui, int x, int y, int width, int height, ITextComponent filterName, TILE tile, FILTER origFilter) {
        super(gui, x, y, width, height);
        this.tile = tile;
        this.origFilter = origFilter;
        this.filterName = filterName;
        if (origFilter == null) {
            isNew = true;
            filter = createNewFilter();
        } else {
            filter = origFilter.clone();
        }
        init();
        if (!isFocusOverlay()) {
            if (isNew && getFilterSelect(gui, tile) != null) {
                //If it is a new filter and we have a filter select screen add a back button instead of a close button
                addChild(new MekanismImageButton(gui, this.x + 6, this.y + 6, 11, 14, getButtonLocation("back"), this::openFilterSelect));
            } else {
                super.addCloseButton();
            }
        }
        if (filter.hasFilter()) {
            slotDisplay.updateStackList();
        }
    }

    @Override
    protected void addCloseButton() {
        //No-op the super close button addition
    }

    protected int getSlotOffset() {
        return 18;
    }

    protected int getScreenHeight() {
        return 43;
    }

    protected int getScreenWidth() {
        return 116;
    }

    protected void init() {
        int screenTop = relativeY + 18;
        int screenBottom = screenTop + getScreenHeight();
        addChild(new GuiInnerScreen(guiObj, relativeX + 29, screenTop, getScreenWidth(), getScreenHeight(), this::getScreenText).clearFormat());
        addChild(new TranslationButton(guiObj, x + width / 2 - 61, guiObj.getTop() + screenBottom + 2, 60, 20,
              isNew ? MekanismLang.BUTTON_CANCEL : MekanismLang.BUTTON_DELETE, () -> {
            if (origFilter != null) {
                Mekanism.packetHandler.sendToServer(new PacketEditFilter(tile.getPos(), true, origFilter, null));
            }
            close();
        }));
        addChild(new TranslationButton(guiObj, x + width / 2 + 1, guiObj.getTop() + screenBottom + 2, 60, 20, MekanismLang.BUTTON_SAVE, this::validateAndSave));
        addChild(new GuiSlot(SlotType.NORMAL, guiObj, relativeX + 7, relativeY + getSlotOffset()).setRenderHover(true));
        addChild(slotDisplay = new GuiSequencedSlotDisplay(guiObj, relativeX + 8, relativeY + getSlotOffset() + 1, this::getRenderStacks));
    }

    private void openFilterSelect() {
        //Add the window for the filter select dialog to the parent gui
        getGuiObj().addWindow(getFilterSelect(getGuiObj(), tile));
        //And close the filter filter
        close();
    }

    protected List<ITextComponent> getScreenText() {
        List<ITextComponent> list = new ArrayList<>();
        list.add(MekanismLang.STATUS.translate(status));
        return list;
    }

    protected void validateAndSave() {
        if (filter.hasFilter()) {
            saveFilter();
        } else {
            filterSaveFailed(getNoFilterSaveError());
        }
    }

    protected void filterSaveFailed(ILangEntry reason) {
        status = reason.translateColored(EnumColor.DARK_RED);
        ticker = 20;
    }

    protected void saveFilter() {
        if (isNew) {
            Mekanism.packetHandler.sendToServer(new PacketNewFilter(tile.getPos(), filter));
        } else {
            Mekanism.packetHandler.sendToServer(new PacketEditFilter(tile.getPos(), false, origFilter, filter));
        }
        close();
    }

    protected abstract ILangEntry getNoFilterSaveError();

    @Nonnull
    protected abstract List<ItemStack> getRenderStacks();

    @Override
    public void renderForeground(int mouseX, int mouseY) {
        super.renderForeground(mouseX, mouseY);
        drawTextScaledBound((isNew ? MekanismLang.FILTER_NEW : MekanismLang.FILTER_EDIT).translate(filterName), relativeX + 30, relativeY + 6, titleTextColor(), 110);
    }

    @Override
    public void tick() {
        super.tick();
        if (ticker > 0) {
            ticker--;
        } else {
            status = MekanismLang.STATUS_OK.translateColored(EnumColor.DARK_GREEN);
        }
    }

    protected abstract FILTER createNewFilter();
}
