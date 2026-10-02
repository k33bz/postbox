package com.k33bz.postbox;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * File handling for the mail store, kept free of Minecraft types so it unit-tests without a game.
 *
 * <p>The store holds real player items (letters are books), so two rules:
 * <ul>
 *   <li>A store that can't be parsed is never thrown away. It is copied aside to
 *       {@code postbox_mail.json.corrupt-<millis>} before postbox starts over, so an admin can
 *       repair and restore it. Previously the empty replacement was saved straight over it at boot,
 *       losing every mailbox, queue and in-transit letter.</li>
 *   <li>Saves are atomic: written to a temp file, then moved over the store. A crash mid-save can no
 *       longer leave a half-written file behind (which is how a store gets corrupted to begin with).</li>
 * </ul>
 */
public final class MailFiles {
    private MailFiles() {
    }

    /**
     * Copy an unreadable store aside and return the copy's path. Throws if the copy fails: refusing
     * to start is better than carrying on and later saving an empty store over the only copy.
     */
    public static Path backUpCorrupt(Path file, long nowMs) {
        Path backup = file.resolveSibling(file.getFileName() + ".corrupt-" + nowMs);
        try {
            Files.copy(file, backup, StandardCopyOption.REPLACE_EXISTING);
            return backup;
        } catch (IOException e) {
            throw new IllegalStateException("postbox: " + file + " could not be read and could not be backed up to "
                    + backup + "; refusing to continue rather than overwrite it", e);
        }
    }

    /** Write {@code content} to {@code file} via a temp file + atomic move (plain replace as fallback). */
    public static void writeAtomically(Path file, String content) throws IOException {
        Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
        Files.writeString(tmp, content);
        try {
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
