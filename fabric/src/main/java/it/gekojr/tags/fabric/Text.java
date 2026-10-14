package it.gekojr.tags.fabric;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.*;
final class Text {
    static Component legacy(String value) {
        var result=Component.empty(); var style=Style.EMPTY; var text=new StringBuilder();
        for(int i=0;i<value.length();i++) {
            char c=value.charAt(i);
            if(c=='§' && i+1<value.length()) {
                var format=ChatFormatting.getByCode(value.charAt(++i));
                if(format!=null) { if(!text.isEmpty()) { result.append(Component.literal(text.toString()).withStyle(style));text.setLength(0); } style=style.applyLegacyFormat(format); }
            } else text.append(c);
        }
        if(!text.isEmpty())result.append(Component.literal(text.toString()).withStyle(style)); return result;
    }
}
