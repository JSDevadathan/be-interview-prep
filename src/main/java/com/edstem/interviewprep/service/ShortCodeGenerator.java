package com.edstem.interviewprep.service;

import java.security.SecureRandom;
import java.util.random.RandomGenerator;
import org.springframework.stereotype.Component;

@Component
public class ShortCodeGenerator {

    public static final int CODE_LENGTH = 7;

    private static final String ALPHABET = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";

    private final RandomGenerator random;

    public ShortCodeGenerator() {
        this(new SecureRandom());
    }

    ShortCodeGenerator(RandomGenerator random) {
        this.random = random;
    }

    public String generate() {
        StringBuilder code = new StringBuilder(CODE_LENGTH);
        for (int position = 0; position < CODE_LENGTH; position++) {
            code.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
        }
        return code.toString();
    }
}
