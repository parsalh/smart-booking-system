package com.hua.smartbooking.util;

import com.hua.smartbooking.config.EncryptionConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.security.SecureRandom;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StringCryptoConverterTest {

    private StringCryptoConverter converter;

    @BeforeEach
    void setUp() {
        // EncryptionConfig.SECRET_KEY is only populated by Spring's @PostConstruct
        // in a real application context, so a plain unit test has to set it
        // directly — it's a public static field precisely so tests can do this
        // without needing to bootstrap Spring.
        byte[] key = new byte[32];
        new SecureRandom().nextBytes(key);
        EncryptionConfig.SECRET_KEY = key;

        converter = new StringCryptoConverter();
    }

    @Test
    void encryptThenDecryptReturnsOriginalValue() {
        String original = "guest@hua.gr";

        String encrypted = converter.convertToDatabaseColumn(original);
        String decrypted = converter.convertToEntityAttribute(encrypted);

        assertThat(decrypted).isEqualTo(original);
    }

    @Test
    void encryptedValueDoesNotContainThePlaintext() {
        String original = "Sensitive Meeting Title";

        String encrypted = converter.convertToDatabaseColumn(original);

        assertThat(encrypted).doesNotContain(original);
    }

    @Test
    void encryptingTheSameValueTwiceProducesDifferentCiphertext() {
        // This is the exact property that made the old participant-email Map key
        // encryption unreliable: a fresh random IV each call means the same
        // plaintext never round-trips to the same ciphertext, so anything that
        // depends on re-deriving the same encrypted value for a lookup (like a
        // WHERE clause keyed on an encrypted column) can't work. Documenting
        // this here so nobody re-introduces encryption on a lookup key without
        // realizing why it broke last time.
        String original = "guest@hua.gr";

        String firstEncryption = converter.convertToDatabaseColumn(original);
        String secondEncryption = converter.convertToDatabaseColumn(original);

        assertThat(firstEncryption).isNotEqualTo(secondEncryption);
        // ...yet both still decrypt back to the same original value.
        assertThat(converter.convertToEntityAttribute(firstEncryption)).isEqualTo(original);
        assertThat(converter.convertToEntityAttribute(secondEncryption)).isEqualTo(original);
    }

    @Test
    void convertToDatabaseColumnReturnsNullForNullInput() {
        assertThat(converter.convertToDatabaseColumn(null)).isNull();
    }

    @Test
    void convertToEntityAttributeReturnsNullForNullInput() {
        assertThat(converter.convertToEntityAttribute(null)).isNull();
    }

    @Test
    void handlesGreekAndUnicodeText() {
        String original = "Συνάντηση με τον καθηγητή 📅";

        String encrypted = converter.convertToDatabaseColumn(original);
        String decrypted = converter.convertToEntityAttribute(encrypted);

        assertThat(decrypted).isEqualTo(original);
    }

    @Test
    void decryptingMalformedDataThrows() {
        assertThatThrownBy(() -> converter.convertToEntityAttribute("not-valid-base64!!"))
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    void decryptingWithADifferentKeyThanItWasEncryptedWithFails() {
        // AES-GCM's authentication tag should reject this outright rather than
        // silently returning garbage — worth pinning down since it's the whole
        // point of using an authenticated cipher mode here.
        String encrypted = converter.convertToDatabaseColumn("guest@hua.gr");

        byte[] differentKey = new byte[32];
        new SecureRandom().nextBytes(differentKey);
        EncryptionConfig.SECRET_KEY = differentKey;

        assertThatThrownBy(() -> converter.convertToEntityAttribute(encrypted))
                .isInstanceOf(RuntimeException.class);
    }
}