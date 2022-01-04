package neko.caffeine.sts.fix.core;

import java.util.List;
import java.util.regex.Pattern;

import com.megacrit.cardcrawl.cards.AbstractCard;
import com.megacrit.cardcrawl.cards.DescriptionLine;
import com.megacrit.cardcrawl.ui.DialogWord;
import com.megacrit.cardcrawl.ui.SpeechWord;
import com.megacrit.cardcrawl.ui.buttons.LargeDialogOptionButton;

public class HookMethods {
    
    public static final Pattern hasZN = Pattern.compile("[\u4e00-\u9fa5]");
    
    public static boolean hasZN(final String text) = text != null && hasZN.matcher(text).find();
    
    public static boolean hasZN(final List<?> text) {
        for (final Object obj : text) {
            if (obj instanceof SpeechWord word && hasZN(word.word))
                return true;
            if (obj instanceof DialogWord word && hasZN(word.word))
                return true;
            if (obj instanceof DescriptionLine line && hasZN(line.text))
                return true;
            if (obj instanceof LargeDialogOptionButton button && hasZN(button.msg))
                return true;
        }
        return false;
    }
    
    public static boolean hasZN(final Object text) = text instanceof AbstractCard card && hasZN(card.description);
    
}
