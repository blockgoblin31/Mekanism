package mekanism.tools.common.item;

import java.util.List;
import mekanism.tools.common.material.MaterialCreator;
import mekanism.tools.common.util.ToolsUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.PickaxeItem;
import net.minecraft.world.item.TooltipFlag;
import org.jetbrains.annotations.NotNull;

public class ItemMekanismPickaxe extends PickaxeItem {

    public ItemMekanismPickaxe(MaterialCreator material, Item.Properties properties) {
        super(material, properties.attributes(createAttributes(material, material.getPickaxeDamage(), material.getPickaxeAtkSpeed())));
    }

    @Override
    public void appendHoverText(@NotNull ItemStack stack, @NotNull Item.TooltipContext context, @NotNull List<Component> tooltip, @NotNull TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        ToolsUtils.addDurability(tooltip, stack);
    }
}