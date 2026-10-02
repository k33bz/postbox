package com.k33bz.postbox;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The mail store holds real player items. Whatever happens to the file, the bytes that were there
 * must survive somewhere: these pin down the backup-before-starting-over and the atomic save.
 */
class MailFilesTest {

    @Test
    void corruptStoreIsCopiedAsideByteForByte(@TempDir Path dir) throws IOException {
        Path store = dir.resolve("postbox_mail.json");
        String damaged = "{\"boxes\": [{\"id\": \"a\", \"owner\": \"x\"";  // truncated mid-write
        Files.writeString(store, damaged);

        Path backup = MailFiles.backUpCorrupt(store, 1234L);

        assertEquals(dir.resolve("postbox_mail.json.corrupt-1234"), backup);
        assertEquals(damaged, Files.readString(backup));
        assertEquals(damaged, Files.readString(store), "the original is left in place too");
    }

    @Test
    void backupFailureRefusesToContinue(@TempDir Path dir) {
        // Nothing to copy (or no permission): carrying on would later save an empty store over it
        Path missing = dir.resolve("nope").resolve("postbox_mail.json");
        assertThrows(IllegalStateException.class, () -> MailFiles.backUpCorrupt(missing, 1L));
    }

    @Test
    void atomicWriteReplacesTheFileAndLeavesNoTemp(@TempDir Path dir) throws IOException {
        Path store = dir.resolve("postbox_mail.json");
        Files.writeString(store, "old");

        MailFiles.writeAtomically(store, "{\"boxes\":[]}");

        assertEquals("{\"boxes\":[]}", Files.readString(store));
        assertFalse(Files.exists(dir.resolve("postbox_mail.json.tmp")));
    }

    @Test
    void atomicWriteCreatesAMissingFile(@TempDir Path dir) throws IOException {
        Path store = dir.resolve("postbox_mail.json");
        MailFiles.writeAtomically(store, "{}");
        assertTrue(Files.exists(store));
        assertEquals("{}", Files.readString(store));
    }
}
