package com.edstem.interviewprep.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.edstem.interviewprep.entity.ShortUrl;
import java.util.HashSet;
import java.util.Random;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ShortCodeGeneratorTest {

    private static final int SAMPLE_SIZE = 10_000;
    private static final long SEED = 42L;

    private final ShortCodeGenerator generator = new ShortCodeGenerator(new Random(SEED));

    @Test
    void generatesUrlSafeCodesWithinTheMaximumLength() {
        for (int sample = 0; sample < SAMPLE_SIZE; sample++) {
            assertThat(generator.generate())
                    .hasSize(ShortCodeGenerator.CODE_LENGTH)
                    .hasSizeLessThanOrEqualTo(ShortUrl.CODE_MAX_LENGTH)
                    .matches("[A-Za-z0-9]+");
        }
    }

    @Test
    void generatesDistinctCodes() {
        Set<String> codes = new HashSet<>();
        for (int sample = 0; sample < SAMPLE_SIZE; sample++) {
            codes.add(generator.generate());
        }

        assertThat(codes).hasSize(SAMPLE_SIZE);
    }
}
