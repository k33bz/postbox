package com.k33bz.postbox;

import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.network.Filterable;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.WritableBookContent;
import net.minecraft.world.item.component.WrittenBookContent;

import java.util.ArrayList;
import java.util.List;

/**
 * Letters are WRITTEN_BOOK / WRITABLE_BOOK item stacks — nothing else mails. Message length
 * (for postage) is the total characters across pages.
 */
public final class Letters {
    private Letters() {
    }

    /** Characters per page when a typed postcard message is folded into a book. */
    private static final int PAGE_CHARS = 640;

    public static boolean isLetter(ItemStack stack) {
        return stack.is(Items.WRITTEN_BOOK) || stack.is(Items.WRITABLE_BOOK);
    }

    /** Total characters across all pages (0 for non-books). */
    public static int chars(ItemStack stack) {
        WrittenBookContent written = stack.get(DataComponents.WRITTEN_BOOK_CONTENT);
        if (written != null) {
            int n = 0;
            for (Component page : written.getPages(false)) {
                n += page.getString().length();
            }
            return n;
        }
        WritableBookContent writable = stack.get(DataComponents.WRITABLE_BOOK_CONTENT);
        if (writable != null) {
            return writable.getPages(false).mapToInt(String::length).sum();
        }
        return 0;
    }

    /** A typed postcard: message folded into a signed book authored by the sender. */
    public static ItemStack postcard(String senderName, String message) {
        List<String> pages = new ArrayList<>();
        for (int i = 0; i < message.length(); i += PAGE_CHARS) {
            pages.add(message.substring(i, Math.min(message.length(), i + PAGE_CHARS)));
        }
        if (pages.isEmpty()) {
            pages.add("");
        }
        return writtenBook("Postcard", senderName, pages);
    }

    /** Build a signed written book. */
    public static ItemStack writtenBook(String title, String author, List<String> pages) {
        ItemStack book = new ItemStack(Items.WRITTEN_BOOK);
        List<Filterable<Component>> pgs = new ArrayList<>();
        for (String p : pages) {
            pgs.add(Filterable.passThrough(Component.literal(p)));
        }
        book.set(DataComponents.WRITTEN_BOOK_CONTENT,
                new WrittenBookContent(Filterable.passThrough(title), author, 0, pgs, true));
        return book;
    }
}
