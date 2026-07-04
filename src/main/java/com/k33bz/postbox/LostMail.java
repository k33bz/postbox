package com.k33bz.postbox;

import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.BundleContents;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.util.RandomSource;

/**
 * "Lost Mail" — the bundle a slain wandering trader sometimes drops: 1-3 written books mixing
 * procedurally generated gibberish postcards (fictional senders) with excerpts from famous
 * public-domain letters, authored under their real names.
 */
public final class LostMail {
    private LostMail() {
    }

    // --- the public-domain corpus (short excerpts, all pre-1930 sources) ---

    private record Excerpt(String title, String author, String text) {
    }

    private static final List<Excerpt> CORPUS = List.of(
            new Excerpt("To Sarah, July 1861", "Sullivan Ballou",
                    "If I do not return, my dear Sarah, never forget how much I love you, nor that "
                            + "when my last breath escapes me on the battle field, it will whisper your name."),
            new Excerpt("On the Shortness of Life", "Seneca",
                    "It is not that we have a short time to live, but that we waste a lot of it. "
                            + "Life is long enough, if it were all well invested. Farewell, Lucilius."),
            new Excerpt("To Theo, 1880", "Vincent van Gogh",
                    "What am I in the eyes of most people — a nonentity, an eccentric. I should want "
                            + "my work to show what is in the heart of such an eccentric, of such a nobody."),
            new Excerpt("Immortal Beloved", "Ludwig van Beethoven",
                    "My angel, my all, my very self. Be calm — love me — today — yesterday — what "
                            + "tearful longings for you — you — you — my life — my all — farewell."),
            new Excerpt("Remember the Ladies", "Abigail Adams",
                    "In the new Code of Laws which I suppose it will be necessary for you to make, I "
                            + "desire you would Remember the Ladies, and be more generous to them than your ancestors."),
            new Excerpt("The Eruption of Vesuvius", "Pliny the Younger",
                    "You could hear the shrieks of women, the wailing of infants, and the shouting of "
                            + "men. Many besought the aid of the gods, but still more imagined there were no gods left."),
            new Excerpt("To My Father, 1778", "Wolfgang Amadeus Mozart",
                    "I never lie down at night without reflecting that — young as I am — I may not "
                            + "live to see another day. Yet no one who knows me can say that I am morose or disheartened."),
            new Excerpt("To Mr. Higginson", "Emily Dickinson",
                    "Mr. Higginson — are you too deeply occupied to say if my Verse is alive? "
                            + "The Mind is so near itself — it cannot see, distinctly — and I have none to ask."),
            new Excerpt("On the Progress of Science", "Benjamin Franklin",
                    "The rapid progress true science now makes occasions my regretting sometimes that "
                            + "I was born so soon. It is impossible to imagine the height to which may be carried "
                            + "the power of man over matter."),
            new Excerpt("To Fanny Brawne, 1819", "John Keats",
                    "I cannot exist without you. I am forgetful of every thing but seeing you again — "
                            + "my Life seems to stop there — I see no further. You have absorb'd me."));

    // --- gibberish postcard generator ---

    private static final String[] FIRST = {"Bartleby", "Petunia", "Cornelius", "Wilhelmina", "Mortimer",
            "Clementine", "Ignatius", "Prudence", "Thaddeus", "Marigold"};
    private static final String[] LAST = {"Quill", "Marmalade", "Fothergill", "Puddleworth", "Snodgrass",
            "Bumblethorpe", "Crabapple", "Winterbottom", "Piffle", "Haberdash"};
    private static final String[] OPENERS = {
            "Dearest cousin, the pigeons have unionized again.",
            "You will not BELIEVE what the llama ate this time.",
            "Greetings from the far side of the mushroom fields!",
            "The weather here is entirely made of bees.",
            "I have finally perfected my recipe for gravel soup.",
            "As foretold, the chickens have taken the west wing."};
    private static final String[] MIDDLES = {
            "Business is booming — I sold three maps to the same lost cartographer.",
            "Aunt Gertrude insists the moon is a very slow projectile. I no longer argue.",
            "The village elder traded me this postcard for a bucket of enthusiasm.",
            "My left boot has been elected mayor. We are cautiously optimistic.",
            "I planted an emerald to grow a bank. Early results are disappointing.",
            "Do not lend your fishing rod to a drowned. Lesson learned."};
    private static final String[] CLOSERS = {
            "Write back before the ink evaporates.", "Yours in mild peril,",
            "With regards and radishes,", "Until the beacons align,",
            "Apologies for the bite marks,", "Sent with the last stamp in the village,"};

    /** One random lost-mail book: gibberish postcard or a famous excerpt (about 50/50). */
    public static ItemStack randomBook(RandomSource random) {
        if (random.nextBoolean()) {
            Excerpt e = CORPUS.get(random.nextInt(CORPUS.size()));
            return Letters.writtenBook(e.title(), e.author(), List.of(e.text()));
        }
        String sender = FIRST[random.nextInt(FIRST.length)] + " " + LAST[random.nextInt(LAST.length)];
        String text = OPENERS[random.nextInt(OPENERS.length)] + "\n\n"
                + MIDDLES[random.nextInt(MIDDLES.length)] + "\n\n"
                + CLOSERS[random.nextInt(CLOSERS.length)] + "\n" + sender;
        return Letters.writtenBook("Weathered Postcard", sender, List.of(text));
    }

    /** The Lost Mail bundle: 1-3 random books. */
    public static ItemStack bundle(RandomSource random) {
        int count = 1 + random.nextInt(3);
        List<ItemStackTemplate> books = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            ItemStack book = randomBook(random);
            books.add(new ItemStackTemplate(book.getItem(), book.getComponentsPatch()));
        }
        ItemStack bundle = new ItemStack(Items.BUNDLE);
        bundle.set(DataComponents.BUNDLE_CONTENTS, new BundleContents(books));
        bundle.set(DataComponents.CUSTOM_NAME, Component.literal("Lost Mail")
                .withStyle(s -> s.withColor(ChatFormatting.GOLD).withItalic(false)));
        bundle.set(DataComponents.LORE, new net.minecraft.world.item.component.ItemLore(List.of(
                Component.literal(String.format(Locale.ROOT, "%d undelivered letter%s",
                        count, count == 1 ? "" : "s")).withStyle(ChatFormatting.GRAY))));
        return bundle;
    }
}
